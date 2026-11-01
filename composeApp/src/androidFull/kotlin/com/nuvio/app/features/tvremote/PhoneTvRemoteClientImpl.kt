package com.nuvio.app.features.tvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Phone remote client (Android full distribution): pairs with a Nuvio TV through its QR code,
 * long-polls its playback state while a screen observes it and sends the commands.
 * Commands are never queued: they are sent only while the TV is reachable with a fresh state.
 */
internal class PhoneTvRemoteClientImpl(context: Context) : PhoneTvRemoteClient {
    private val log = Logger.withTag("PhoneTvRemote")
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val secrets = TvRemoteSecretStore(preferences, KEY_ALIAS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(TV_REMOTE_MAX_WAIT_MS + 10_000L, TimeUnit.MILLISECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /** Kept apart from the pairing preferences, which are cleared when the TV is forgotten. */
    private val settings = appContext.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        PhoneTvRemoteUiState(
            pairedTv = loadPairedTv(),
            backgroundControls = settings.getBoolean(BACKGROUND_CONTROLS, true),
        ),
    )
    override val state: StateFlow<PhoneTvRemoteUiState> = _state.asStateFlow()

    init {
        // Something started playing on the TV while the app watches it: keep the controls in
        // the notification so they survive when the app goes to background.
        scope.launch {
            state.collect { current ->
                val playback = current.playback
                if (
                    current.backgroundControls &&
                    current.connection == PhoneTvRemoteConnection.Connected &&
                    playback != null &&
                    PhoneTvRemoteService.shouldStartFor(playback.sessionKey)
                ) {
                    PhoneTvRemoteService.start(appContext)
                }
            }
        }
    }

    private var observers = 0
    private var pollJob: Job? = null
    @Volatile private var bootId: String? = null
    @Volatile private var revision = 0L
    @Volatile private var lastStateAtMs = 0L
    @Volatile private var wakeUp = CompletableDeferred<Unit>()

    // region Pairing

    override fun pair(scannedCode: String) {
        val info = TvRemotePairingInfo.parse(scannedCode)
        if (info == null || info.version != TV_REMOTE_PROTOCOL_VERSION) {
            log.w { "Scanned code is not a Nuvio TV pairing code" }
            _state.update { it.copy(pairing = PhoneTvRemotePairing.Failed(PhoneTvRemotePairingError.InvalidCode)) }
            return
        }
        _state.update { it.copy(pairing = PhoneTvRemotePairing.InProgress) }
        log.i { "Pairing with ${info.tvName} at ${info.host}:${info.port}" }
        scope.launch {
            val result = runCatching {
                val key = TvRemoteCrypto.decode(info.secret)
                require(key.size == TvRemoteCrypto.KEY_BYTES) { "Invalid pairing secret" }
                post(
                    host = info.host,
                    port = info.port,
                    path = TV_REMOTE_PATH_PAIR,
                    key = key,
                    clientId = TV_REMOTE_PAIR_CLIENT_ID,
                    requestSerializer = TvRemotePairRequest.serializer(),
                    request = TvRemotePairRequest(
                        phoneName = phoneName(),
                        timestampMs = System.currentTimeMillis(),
                        requestId = TvRemoteCrypto.randomId(),
                    ),
                    responseSerializer = TvRemotePairResponse.serializer(),
                    timeoutMs = PAIRING_TIMEOUT_MS,
                )
            }
            result.onSuccess { response ->
                val clientKey = TvRemoteCrypto.decode(response.clientKey)
                val paired = PhoneTvRemotePairedTv(
                    tvId = response.tvId,
                    name = response.tvName.ifBlank { info.tvName },
                    host = info.host,
                    port = info.port,
                )
                savePairing(paired, response.clientId, clientKey)
                bootId = null
                revision = 0L
                _state.value = PhoneTvRemoteUiState(
                    pairedTv = paired,
                    connection = PhoneTvRemoteConnection.Connecting,
                    backgroundControls = _state.value.backgroundControls,
                )
                log.i { "Paired with ${paired.name} (key ${TvRemoteCrypto.fingerprint(clientKey)})" }
                restartPolling()
            }.onFailure { error ->
                val reason = when (error) {
                    is TvRemoteHttpException -> when (error.code) {
                        410 -> PhoneTvRemotePairingError.Expired
                        403, 412 -> PhoneTvRemotePairingError.Rejected
                        else -> PhoneTvRemotePairingError.Unknown
                    }
                    is IOException -> PhoneTvRemotePairingError.Unreachable
                    is IllegalArgumentException -> PhoneTvRemotePairingError.InvalidCode
                    else -> PhoneTvRemotePairingError.Unknown
                }
                log.w(error) { "Pairing failed: $reason" }
                _state.update { it.copy(pairing = PhoneTvRemotePairing.Failed(reason)) }
            }
        }
    }

    override fun clearPairingError() {
        _state.update { if (it.pairing is PhoneTvRemotePairing.Failed) it.copy(pairing = PhoneTvRemotePairing.Idle) else it }
    }

    override fun forget() {
        val paired = _state.value.pairedTv ?: return
        val clientId = preferences.getString(CLIENT_ID, null)
        val key = loadClientKey()
        clearPairing()
        bootId = null
        revision = 0L
        _state.value = PhoneTvRemoteUiState(backgroundControls = _state.value.backgroundControls)
        synchronized(lock) {
            pollJob?.cancel()
            pollJob = null
        }
        log.i { "TV forgotten on the phone" }
        if (clientId != null && key != null) {
            // Best effort: the TV also drops this phone when it is reachable now.
            scope.launch {
                runCatching {
                    postNoContent(
                        host = paired.host,
                        port = paired.port,
                        path = TV_REMOTE_PATH_FORGET,
                        key = key,
                        clientId = clientId,
                        serializer = TvRemoteForgetRequest.serializer(),
                        request = TvRemoteForgetRequest(System.currentTimeMillis(), TvRemoteCrypto.randomId()),
                    )
                }.onFailure { log.d { "TV not reachable to forget this phone" } }
            }
        }
    }

    override fun setBackgroundControls(enabled: Boolean) {
        settings.edit().putBoolean(BACKGROUND_CONTROLS, enabled).apply()
        _state.update { it.copy(backgroundControls = enabled) }
        log.i { "Background controls ${if (enabled) "enabled" else "disabled"}" }
    }

    // endregion

    // region Observation

    override fun observe(): () -> Unit {
        synchronized(lock) {
            observers += 1
            if (observers == 1) restartPolling()
        }
        var released = false
        return {
            synchronized(lock) {
                if (!released) {
                    released = true
                    observers = (observers - 1).coerceAtLeast(0)
                    if (observers == 0) {
                        pollJob?.cancel()
                        pollJob = null
                        _state.update { state ->
                            if (state.connection == PhoneTvRemoteConnection.Revoked) state
                            else state.copy(connection = PhoneTvRemoteConnection.Idle)
                        }
                        log.d { "TV observation paused" }
                    }
                }
            }
        }
    }

    override fun reconnect() {
        if (_state.value.pairedTv == null) return
        restartPolling()
    }

    private fun restartPolling() {
        synchronized(lock) {
            if (observers == 0 || _state.value.pairedTv == null) return
            pollJob?.cancel()
            pollJob = scope.launch { pollLoop() }
        }
    }

    private suspend fun pollLoop() {
        var failures = 0
        var rediscovered = false
        log.d { "TV observation started" }
        while (scope.isActive) {
            val paired = _state.value.pairedTv ?: return
            val clientId = preferences.getString(CLIENT_ID, null)
            val key = loadClientKey()
            if (clientId == null || key == null) {
                log.w { "Stored pairing unreadable: pair the TV again" }
                _state.update { it.copy(connection = PhoneTvRemoteConnection.Revoked, playback = null, volume = null) }
                return
            }
            if (_state.value.connection != PhoneTvRemoteConnection.Connected) {
                _state.update { it.copy(connection = if (failures == 0) PhoneTvRemoteConnection.Connecting else it.connection) }
            }
            try {
                val firstRequest = bootId == null
                val response = post(
                    host = paired.host,
                    port = paired.port,
                    path = TV_REMOTE_PATH_STATE,
                    key = key,
                    clientId = clientId,
                    requestSerializer = TvRemoteStateRequest.serializer(),
                    request = TvRemoteStateRequest(
                        timestampMs = System.currentTimeMillis(),
                        requestId = TvRemoteCrypto.randomId(),
                        sinceRevision = if (firstRequest) 0L else revision,
                        waitMs = if (firstRequest) 0L else TV_REMOTE_MAX_WAIT_MS,
                    ),
                    responseSerializer = TvRemoteStateResponse.serializer(),
                    timeoutMs = TV_REMOTE_MAX_WAIT_MS + 8_000L,
                )
                if (bootId != null && bootId != response.bootId) log.i { "TV app restarted" }
                bootId = response.bootId
                revision = response.revision
                lastStateAtMs = System.currentTimeMillis()
                if (failures > 0) log.i { "TV reachable again" }
                failures = 0
                rediscovered = false
                if (response.tvName.isNotBlank() && response.tvName != paired.name) {
                    updatePairedTv(paired.copy(name = response.tvName))
                }
                _state.update {
                    it.copy(
                        connection = PhoneTvRemoteConnection.Connected,
                        playback = response.playback,
                        volume = response.volume,
                    )
                }
            } catch (error: TvRemoteHttpException) {
                if (error.code == 401 || error.code == 403) {
                    log.w { "The TV removed this phone (HTTP ${error.code})" }
                    _state.update { it.copy(connection = PhoneTvRemoteConnection.Revoked, playback = null, volume = null) }
                    return
                }
                failures += 1
                log.w { "TV state request failed (HTTP ${error.code})" }
                markUnreachable()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += 1
                if (failures == 1) log.w(error) { "TV not reachable" } else log.d { "TV still not reachable ($failures)" }
                markUnreachable()
                // The TV address may have changed (DHCP): look for it on the network once per outage.
                if (!rediscovered && failures >= 2) {
                    rediscovered = true
                    val found = discoverTvAddress(paired)
                    if (found != null && (found.first != paired.host || found.second != paired.port)) {
                        log.i { "TV found at a new address ${found.first}:${found.second}" }
                        updatePairedTv(paired.copy(host = found.first, port = found.second))
                        continue
                    }
                }
            }
            if (failures > 0) {
                val backoff = (1_000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MS)
                val signal = CompletableDeferred<Unit>().also { wakeUp = it }
                withTimeoutOrNull(backoff) { signal.await() }
            }
        }
    }

    private fun markUnreachable() {
        val stale = System.currentTimeMillis() - lastStateAtMs > STALE_STATE_MS
        _state.update {
            it.copy(
                connection = PhoneTvRemoteConnection.Unreachable,
                playback = if (stale) null else it.playback,
                volume = if (stale) null else it.volume,
            )
        }
    }

    private suspend fun discoverTvAddress(paired: PhoneTvRemotePairedTv): Pair<String, Int>? {
        val nsd = appContext.getSystemService(NsdManager::class.java) ?: return null
        val result = CompletableDeferred<Pair<String, Int>?>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                result.complete(null)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val id = serviceInfo.attributes[TV_REMOTE_NSD_ID_ATTRIBUTE]?.decodeToString()
                                @Suppress("DEPRECATION")
                                val host = serviceInfo.host?.hostAddress
                                if (id == paired.tvId && host != null) result.complete(host to serviceInfo.port)
                            }
                        },
                    )
                }
            }
        }
        return try {
            nsd.discoverServices(TV_REMOTE_NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { result.await() }
        } catch (error: Exception) {
            log.d { "Network discovery failed: ${error.message}" }
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    // endregion

    // region Commands

    override fun send(command: TvRemoteCommandType, positionMs: Long?, forward: Boolean?, level: Int?) {
        val current = _state.value
        val paired = current.pairedTv ?: return
        val boot = bootId
        if (current.connection != PhoneTvRemoteConnection.Connected || boot == null) {
            log.d { "Command $command dropped: TV not connected" }
            return
        }
        val clientId = preferences.getString(CLIENT_ID, null) ?: return
        val key = loadClientKey() ?: return
        val isVolume = command in VOLUME_COMMANDS
        val playback = current.playback
        if (!isVolume && playback == null) return
        // Immediate feedback for play/pause; the TV state confirms it right after.
        if (playback != null && command in PLAYBACK_COMMANDS) {
            val playing = when (command) {
                TvRemoteCommandType.Play -> true
                TvRemoteCommandType.Pause -> false
                else -> !playback.isPlaying
            }
            _state.update { it.copy(playback = playback.copy(isPlaying = playing), lastCommandError = null) }
        } else {
            _state.update { it.copy(lastCommandError = null) }
        }
        scope.launch {
            runCatching {
                post(
                    host = paired.host,
                    port = paired.port,
                    path = TV_REMOTE_PATH_COMMAND,
                    key = key,
                    clientId = clientId,
                    requestSerializer = TvRemoteCommandRequest.serializer(),
                    request = TvRemoteCommandRequest(
                        timestampMs = System.currentTimeMillis(),
                        requestId = TvRemoteCrypto.randomId(),
                        bootId = boot,
                        command = command,
                        sessionKey = if (isVolume) null else playback?.sessionKey,
                        positionMs = positionMs,
                        forward = forward,
                        level = level,
                    ),
                    responseSerializer = TvRemoteCommandResponse.serializer(),
                    timeoutMs = COMMAND_TIMEOUT_MS,
                )
            }.onSuccess { response ->
                if (!response.accepted) {
                    log.i { "TV refused $command: ${response.error}" }
                    _state.update { it.copy(lastCommandError = response.error) }
                    if (response.error == TvRemoteCommandError.StaleBoot) {
                        bootId = null
                        wakeUp.complete(Unit)
                    }
                } else {
                    log.d { "Command $command sent" }
                }
            }.onFailure { error ->
                log.w(error) { "Command $command not delivered" }
                markUnreachable()
                wakeUp.complete(Unit)
            }
        }
    }

    // endregion

    // region Scanner

    @Composable
    override fun rememberQrScanner(onResult: (String?) -> Unit): () -> Unit {
        val callback by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
            callback(result.contents)
        }
        return remember(launcher) {
            {
                launcher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .setPrompt(""),
                )
            }
        }
    }

    // endregion

    // region HTTP

    private fun <Req, Res> post(
        host: String,
        port: Int,
        path: String,
        key: ByteArray,
        clientId: String,
        requestSerializer: KSerializer<Req>,
        request: Req,
        responseSerializer: KSerializer<Res>,
        timeoutMs: Long,
    ): Res {
        val envelope = TvRemoteCrypto.seal(key, clientId, path, requestSerializer, request)
        val body = TvRemoteCrypto.json.encodeToString(TvRemoteEnvelope.serializer(), envelope)
            .toRequestBody(JSON_MEDIA_TYPE)
        val call = http.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newCall(Request.Builder().url(url(host, port, path)).post(body).build())
        call.execute().use { response ->
            if (!response.isSuccessful) throw TvRemoteHttpException(response.code)
            val text = response.body?.string() ?: throw IOException("Empty response")
            val answer = TvRemoteCrypto.json.decodeFromString(TvRemoteEnvelope.serializer(), text)
            return TvRemoteCrypto.open(key, path, responseSerializer, answer)
        }
    }

    private fun <Req> postNoContent(
        host: String,
        port: Int,
        path: String,
        key: ByteArray,
        clientId: String,
        serializer: KSerializer<Req>,
        request: Req,
    ) {
        val envelope = TvRemoteCrypto.seal(key, clientId, path, serializer, request)
        val body = TvRemoteCrypto.json.encodeToString(TvRemoteEnvelope.serializer(), envelope)
            .toRequestBody(JSON_MEDIA_TYPE)
        http.newCall(Request.Builder().url(url(host, port, path)).post(body).build()).execute().use { response ->
            if (!response.isSuccessful) throw TvRemoteHttpException(response.code)
        }
    }

    private fun url(host: String, port: Int, path: String): String {
        val safeHost = if (':' in host) "[$host]" else host
        return "http://$safeHost:$port$path"
    }

    // endregion

    // region Storage

    private fun loadPairedTv(): PhoneTvRemotePairedTv? {
        val tvId = preferences.getString(TV_ID, null) ?: return null
        val host = preferences.getString(HOST, null) ?: return null
        val port = preferences.getInt(PORT, 0).takeIf { it > 0 } ?: return null
        return PhoneTvRemotePairedTv(
            tvId = tvId,
            name = preferences.getString(TV_NAME, null).orEmpty(),
            host = host,
            port = port,
        )
    }

    private fun loadClientKey(): ByteArray? =
        secrets.load(CLIENT_KEY)?.let { runCatching { TvRemoteCrypto.decode(it) }.getOrNull() }

    private fun savePairing(tv: PhoneTvRemotePairedTv, clientId: String, key: ByteArray) {
        secrets.save(CLIENT_KEY, TvRemoteCrypto.encode(key))
        preferences.edit()
            .putString(TV_ID, tv.tvId)
            .putString(TV_NAME, tv.name)
            .putString(HOST, tv.host)
            .putInt(PORT, tv.port)
            .putString(CLIENT_ID, clientId)
            .commit()
    }

    private fun updatePairedTv(tv: PhoneTvRemotePairedTv) {
        preferences.edit()
            .putString(TV_NAME, tv.name)
            .putString(HOST, tv.host)
            .putInt(PORT, tv.port)
            .apply()
        _state.update { it.copy(pairedTv = tv) }
    }

    private fun clearPairing() {
        secrets.save(CLIENT_KEY, null)
        preferences.edit().clear().commit()
    }

    private fun phoneName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, Build.MODEL)
                .joinToString(" ")
                .ifBlank { "Android" }

    // endregion

    private class TvRemoteHttpException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val PREFERENCES = "nuvio_phone_tv_remote"
        const val SETTINGS_PREFERENCES = "nuvio_phone_tv_remote_settings"
        const val BACKGROUND_CONTROLS = "background_controls"
        const val KEY_ALIAS = "com.nuvio.phone.tv_remote.v1"
        const val TV_ID = "tv_id"
        const val TV_NAME = "tv_name"
        const val HOST = "host"
        const val PORT = "port"
        const val CLIENT_ID = "client_id"
        const val CLIENT_KEY = "client_key"
        const val PAIRING_TIMEOUT_MS = 12_000L
        const val COMMAND_TIMEOUT_MS = 6_000L
        const val MAX_BACKOFF_MS = 15_000L
        const val DISCOVERY_TIMEOUT_MS = 6_000L
        /** Playback older than this is hidden while the TV is unreachable. */
        const val STALE_STATE_MS = 30_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val VOLUME_COMMANDS = setOf(
            TvRemoteCommandType.VolumeUp,
            TvRemoteCommandType.VolumeDown,
            TvRemoteCommandType.SetVolume,
            TvRemoteCommandType.ToggleMute,
        )
        val PLAYBACK_COMMANDS = setOf(
            TvRemoteCommandType.Play,
            TvRemoteCommandType.Pause,
            TvRemoteCommandType.TogglePlayback,
        )
    }
}
