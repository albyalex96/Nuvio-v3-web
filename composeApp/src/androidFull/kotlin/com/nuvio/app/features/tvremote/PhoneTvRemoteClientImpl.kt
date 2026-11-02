package com.nuvio.app.features.tvremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
            manualTvAddress = settings.getString(MANUAL_ADDRESS, null).orEmpty(),
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
        val key = runCatching { TvRemoteCrypto.decode(info.secret) }.getOrNull()
        if (key == null || key.size != TvRemoteCrypto.KEY_BYTES) {
            log.w { "Scanned code has an invalid pairing secret" }
            _state.update { it.copy(pairing = PhoneTvRemotePairing.Failed(PhoneTvRemotePairingError.InvalidCode)) }
            return
        }
        log.i { "Pairing with ${info.tvName} at ${info.host}:${info.port} (QR code)" }
        startPairing(
            host = info.host,
            port = info.port,
            fallbackName = info.tvName,
            tvIdForRediscovery = info.tvId,
            byCode = false,
        ) { key }
    }

    override fun pairWithCode(address: String, code: String) {
        val target = parseManualTvAddress(address, TV_REMOTE_DEFAULT_PORT)
        val normalizedCode = normalizeTvRemotePairingCode(code)
        if (target == null || normalizedCode == null) {
            log.w { "Pairing by code not started: invalid ${if (target == null) "address" else "code"}" }
            _state.update {
                it.copy(
                    pairing = PhoneTvRemotePairing.Failed(
                        if (target == null) PhoneTvRemotePairingError.Unreachable else PhoneTvRemotePairingError.WrongCode,
                    ),
                )
            }
            return
        }
        val typed = address.trim()
        settings.edit().putString(MANUAL_ADDRESS, typed).apply()
        _state.update { it.copy(manualTvAddress = typed) }
        log.i { "Pairing with the TV at ${target.first}:${target.second} (pairing code)" }
        startPairing(
            host = target.first,
            port = target.second,
            fallbackName = discoveredName(target.first, target.second) ?: target.first,
            tvIdForRediscovery = null,
            byCode = true,
        ) { TvRemoteCrypto.pairingCodeKey(normalizedCode) }
    }

    private fun discoveredName(host: String, port: Int): String? =
        _state.value.discoveredTvs.firstOrNull { it.host == host && it.port == port }?.name

    private fun startPairing(
        host: String,
        port: Int,
        fallbackName: String,
        tvIdForRediscovery: String?,
        byCode: Boolean,
        pairingKey: () -> ByteArray,
    ) {
        _state.update { it.copy(pairing = PhoneTvRemotePairing.InProgress) }
        scope.launch {
            var targetHost = host
            var targetPort = port
            var key: ByteArray? = null
            var result = runCatching {
                val derived = pairingKey().also { key = it }
                requestPairing(targetHost, targetPort, derived)
            }
            // The address in the QR code may be stale (TV on another interface, DHCP): look for
            // the same TV on the network and try once more.
            val derivedKey = key
            val failure = result.exceptionOrNull()
            if (failure is IOException && failure !is TvRemoteHttpException &&
                tvIdForRediscovery != null && derivedKey != null
            ) {
                val found = discoverTvAddress(tvIdForRediscovery)
                if (found != null && (found.first != targetHost || found.second != targetPort)) {
                    log.i { "TV not reachable at $targetHost:$targetPort, trying ${found.first}:${found.second}" }
                    targetHost = found.first
                    targetPort = found.second
                    result = runCatching { requestPairing(found.first, found.second, derivedKey) }
                }
            }
            result.onSuccess { response ->
                val clientKey = TvRemoteCrypto.decode(response.clientKey)
                val paired = PhoneTvRemotePairedTv(
                    tvId = response.tvId,
                    name = response.tvName.ifBlank { fallbackName },
                    host = targetHost,
                    port = targetPort,
                )
                savePairing(paired, response.clientId, clientKey)
                bootId = null
                revision = 0L
                _state.update {
                    it.copy(
                        pairedTv = paired,
                        connection = PhoneTvRemoteConnection.Connecting,
                        pairing = PhoneTvRemotePairing.Idle,
                        playback = null,
                        volume = null,
                        lastCommandError = null,
                    )
                }
                log.i { "Paired with ${paired.name} (key ${TvRemoteCrypto.fingerprint(clientKey)})" }
                restartPolling()
            }.onFailure { error ->
                val reason = when (error) {
                    is TvRemoteHttpException -> when (error.code) {
                        410 -> PhoneTvRemotePairingError.Expired
                        403 -> if (byCode) PhoneTvRemotePairingError.WrongCode else PhoneTvRemotePairingError.Rejected
                        412 -> PhoneTvRemotePairingError.Rejected
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

    private fun requestPairing(host: String, port: Int, key: ByteArray): TvRemotePairResponse = post(
        host = host,
        port = port,
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
        _state.update {
            PhoneTvRemoteUiState(
                backgroundControls = it.backgroundControls,
                manualTvAddress = it.manualTvAddress,
                discoveredTvs = it.discoveredTvs,
                discovering = it.discovering,
            )
        }
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

    private var networkCallbackRegistered = false

    /** Registered with the first observer (after construction, so every field is ready). */
    private fun registerNetworkCallbackOnce() {
        if (networkCallbackRegistered) return
        networkCallbackRegistered = true
        // Wi-Fi back (or changed): retry at once instead of waiting for the backoff.
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (_state.value.pairedTv != null &&
                            _state.value.connection != PhoneTvRemoteConnection.Connected
                        ) {
                            log.i { "Network available: reconnecting to the TV" }
                            wakeUp.complete(Unit)
                        }
                    }
                },
            )
        }.onFailure { error -> log.w(error) { "Network callback not available: reconnection only by retry" } }
    }

    override fun observe(): () -> Unit {
        synchronized(lock) {
            registerNetworkCallbackOnce()
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
                    val found = discoverTvAddress(paired.tvId)
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

    private suspend fun discoverTvAddress(tvId: String): Pair<String, Int>? {
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
                                if (id == tvId && host != null) result.complete(host to serviceInfo.port)
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

    // region Discovery (list of TVs on the settings page)

    override fun refreshDiscovery() {
        synchronized(lock) {
            if (discoveryHandles == 0) return
            discoveryJob?.cancel()
            discoveryJob = scope.launch { discoveryLoop() }
        }
        log.i { "TV discovery restarted by the user" }
        // The paired TV may be back too: retry at once instead of waiting for the backoff.
        if (_state.value.connection != PhoneTvRemoteConnection.Connected) wakeUp.complete(Unit)
    }

    /** The paired TV showed up on the network: follow its new address and reconnect at once. */
    private fun reconnectIfPaired(tv: PhoneTvRemoteDiscoveredTv) {
        val paired = _state.value.pairedTv ?: return
        if (tv.tvId != paired.tvId) return
        if (tv.host != paired.host || tv.port != paired.port) {
            log.i { "Paired TV found at a new address ${tv.address}: reconnecting" }
            updatePairedTv(paired.copy(host = tv.host, port = tv.port))
            bootId = null
            restartPolling()
        } else if (_state.value.connection != PhoneTvRemoteConnection.Connected) {
            log.i { "Paired TV is back on the network: reconnecting" }
            wakeUp.complete(Unit)
        }
    }

    private var discoveryHandles = 0
    private var discoveryJob: Job? = null

    override fun discover(): () -> Unit {
        synchronized(lock) {
            discoveryHandles += 1
            if (discoveryHandles == 1) {
                discoveryJob?.cancel()
                discoveryJob = scope.launch { discoveryLoop() }
            }
        }
        var released = false
        return {
            synchronized(lock) {
                if (!released) {
                    released = true
                    discoveryHandles = (discoveryHandles - 1).coerceAtLeast(0)
                    if (discoveryHandles == 0) {
                        discoveryJob?.cancel()
                        discoveryJob = null
                        _state.update { it.copy(discovering = false) }
                    }
                }
            }
        }
    }

    /**
     * Browses the network until cancelled. Services are resolved one at a time: older Android
     * versions refuse a second resolution while one is running.
     */
    private suspend fun discoveryLoop() {
        val nsd = appContext.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            log.w { "Network discovery not available on this device" }
            return
        }
        val pending = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val found = mutableMapOf<String, PhoneTvRemoteDiscoveredTv>()
        fun publish() {
            val list = synchronized(found) { found.values.sortedBy { it.name.lowercase() } }
            _state.update { it.copy(discoveredTvs = list) }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                log.d { "TV discovery started" }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log.w { "TV discovery failed to start ($errorCode)" }
                _state.update { it.copy(discovering = false) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pending.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val removed = synchronized(found) { found.remove(serviceInfo.serviceName) }
                if (removed != null) {
                    log.d { "TV left the network: ${removed.name}" }
                    publish()
                }
            }
        }
        _state.update { it.copy(discovering = true, discoveredTvs = emptyList()) }
        try {
            nsd.discoverServices(TV_REMOTE_NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            for (service in pending) {
                val resolved = resolveService(nsd, service) ?: continue
                @Suppress("DEPRECATION")
                val host = resolved.host?.hostAddress ?: continue
                val id = resolved.attributes[TV_REMOTE_NSD_ID_ATTRIBUTE]?.decodeToString() ?: continue
                val name = resolved.attributes[TV_REMOTE_NSD_NAME_ATTRIBUTE]?.decodeToString()
                    ?.takeIf { it.isNotBlank() }
                    ?: resolved.serviceName.removePrefix("Nuvio ").ifBlank { "Android TV" }
                val tv = PhoneTvRemoteDiscoveredTv(tvId = id, name = name, host = host, port = resolved.port)
                val isNew = synchronized(found) {
                    // The same TV can show up again with a new address: keep only the last one.
                    found.entries.removeAll { it.value.tvId == id && it.key != service.serviceName }
                    found.put(service.serviceName, tv) != tv
                }
                if (isNew) {
                    log.i { "TV found on the network: $name at ${tv.address}" }
                    publish()
                    reconnectIfPaired(tv)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "TV discovery stopped by an error" }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            pending.close()
            log.d { "TV discovery stopped" }
        }
    }

    private suspend fun resolveService(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? {
        val result = CompletableDeferred<NsdServiceInfo?>()
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        log.d { "Could not resolve ${serviceInfo.serviceName} ($errorCode)" }
                        result.complete(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        result.complete(serviceInfo)
                    }
                },
            )
        }.onFailure { return null }
        return withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { result.await() }
    }

    // endregion

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
    override fun QrScanner(onResult: (String?) -> Unit) {
        TvRemoteQrScanner(onResult = onResult)
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
        const val MANUAL_ADDRESS = "manual_tv_address"
        const val KEY_ALIAS = "com.nuvio.phone.tv_remote.v1"
        const val TV_ID = "tv_id"
        const val TV_NAME = "tv_name"
        const val HOST = "host"
        const val PORT = "port"
        const val CLIENT_ID = "client_id"
        const val CLIENT_KEY = "client_key"
        const val PAIRING_TIMEOUT_MS = 12_000L
        const val COMMAND_TIMEOUT_MS = 6_000L
        const val MAX_BACKOFF_MS = 8_000L
        const val DISCOVERY_TIMEOUT_MS = 6_000L
        const val RESOLVE_TIMEOUT_MS = 5_000L
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
