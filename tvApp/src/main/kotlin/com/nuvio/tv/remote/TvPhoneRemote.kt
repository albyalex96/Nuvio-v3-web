package com.nuvio.tv.remote

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger
import com.nuvio.app.features.tvremote.TV_REMOTE_DEFAULT_PORT
import com.nuvio.app.features.tvremote.TV_REMOTE_MAX_CLOCK_SKEW_MS
import com.nuvio.app.features.tvremote.TV_REMOTE_MAX_WAIT_MS
import com.nuvio.app.features.tvremote.TV_REMOTE_NSD_ID_ATTRIBUTE
import com.nuvio.app.features.tvremote.TV_REMOTE_NSD_NAME_ATTRIBUTE
import com.nuvio.app.features.tvremote.TV_REMOTE_NSD_SERVICE_TYPE
import com.nuvio.app.features.tvremote.TV_REMOTE_PAIR_CLIENT_ID
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_COMMAND
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_FORGET
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_PAIR
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_STATE
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_TELEGRAM_ACTION
import com.nuvio.app.features.tvremote.TV_REMOTE_PATH_TELEGRAM_STATE
import com.nuvio.app.features.tvremote.TvRemotePairedPhones
import com.nuvio.app.features.tvremote.TvRemoteTelegramActionRequest
import com.nuvio.app.features.tvremote.TvRemoteTelegramActionType
import com.nuvio.app.features.tvremote.TvRemoteTelegramState
import com.nuvio.app.features.tvremote.TvRemoteTelegramStateRequest
import com.nuvio.app.features.tvremote.TvRemoteTelegramStep
import com.nuvio.app.features.telegram.TelegramAuthState
import com.nuvio.app.features.telegram.TelegramRepository
import com.nuvio.app.features.tvremote.TvRemoteCommandError
import com.nuvio.app.features.tvremote.TvRemoteCommandRequest
import com.nuvio.app.features.tvremote.TvRemoteCommandResponse
import com.nuvio.app.features.tvremote.TvRemoteCommandType
import com.nuvio.app.features.tvremote.TvRemoteCrypto
import com.nuvio.app.features.tvremote.TvRemoteEnvelope
import com.nuvio.app.features.tvremote.TvRemoteForgetRequest
import com.nuvio.app.features.tvremote.TvRemotePairRequest
import com.nuvio.app.features.tvremote.TvRemotePairResponse
import com.nuvio.app.features.tvremote.TvRemotePairingInfo
import com.nuvio.app.features.tvremote.TvRemotePlayback
import com.nuvio.app.features.tvremote.TvRemoteStateRequest
import com.nuvio.app.features.tvremote.TvRemoteStateResponse
import com.nuvio.app.features.tvremote.TvRemoteVolume
import com.nuvio.app.tv.TvPlayerRemoteRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer

/**
 * TV side of the phone remote: a local HTTP server (sealed bodies, see `TvRemoteProtocol`) that
 * publishes the in-app player state to the paired phones and runs their commands.
 *
 * The server runs while at least one phone is paired or the pairing page is open.
 */
object TvPhoneRemote {
    private val log = Logger.withTag("TvPhoneRemote")

    private const val PREFERRED_PORT = TV_REMOTE_DEFAULT_PORT
    private const val PAIRING_VALIDITY_MS = 5 * 60_000L
    private const val MAX_PAIRING_FAILURES = 5
    private const val MAX_BODY_CHARS = 64 * 1024
    private const val MAX_CONCURRENT_POLLS = 8
    private const val SAMPLE_INTERVAL_MS = 500L
    /** Position-only changes are published at most this often; the phone interpolates in between. */
    private const val POSITION_PUBLISH_INTERVAL_MS = 2_000L
    /** The sampler stops when no phone asked for the state for this long. */
    private const val SAMPLER_IDLE_MS = 60_000L
    private const val CONNECTED_WINDOW_MS = 30_000L
    private const val MAX_REMEMBERED_REQUESTS = 512
    private const val MAX_TELEGRAM_VALUE_CHARS = 256
    private const val TELEGRAM_CLIENT_START_MS = 10_000L
    private const val TELEGRAM_STEP_WAIT_MS = 6_000L

    data class UiState(
        val running: Boolean = false,
        val tvName: String = "",
        /** Pairing QR on screen; null when pairing is closed. */
        val pairing: TvRemotePairingInfo? = null,
        /** Code typed on the phone with the TV address (alternative to the QR). */
        val pairingCode: String? = null,
        val pairingExpiresAtMs: Long = 0L,
        val pairingError: Boolean = false,
        val phones: List<TvPairedPhone> = emptyList(),
        /** Phones that asked for the state in the last seconds. */
        val connectedPhoneIds: Set<String> = emptySet(),
        /** Name of the phone paired while the page was open (confirmation message). */
        val justPairedPhone: String? = null,
    )

    private data class Published(
        val revision: Long,
        val playback: TvRemotePlayback?,
        val volume: TvRemoteVolume?,
        val publishedAtMs: Long,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val bootId = TvRemoteCrypto.randomId()
    private val published = MutableStateFlow(Published(1L, null, null, 0L))
    private val activePolls = AtomicInteger(0)
    private val handledRequests = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_REMEMBERED_REQUESTS
    }
    private val lastSeen = mutableMapOf<String, Long>()

    private lateinit var appContext: Context
    private lateinit var store: TvRemoteStore
    @Volatile private var server: EmbeddedServer<*, *>? = null
    @Volatile private var port: Int = 0
    @Volatile private var pairingKey: ByteArray? = null
    /** Key derived from the pairing code; null while it is computed or pairing is closed. */
    @Volatile private var pairingCodeKey: ByteArray? = null
    private var pairingFailures = 0
    private var pairingJob: Job? = null
    private var samplerJob: Job? = null
    @Volatile private var lastPollAtMs = 0L
    private var nsdRegistration: NsdManager.RegistrationListener? = null

    fun initialize(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = TvRemoteStore(appContext)
        val phones = store.phones()
        _state.update { it.copy(tvName = store.tvName, phones = phones) }
        scope.launch { state.collect { TvRemotePairedPhones.count.value = it.phones.size } }
        log.i { "Phone remote initialized (${phones.size} paired phones)" }
        if (phones.isNotEmpty()) ensureServer()
    }

    /** Opens pairing: a new one-use secret shown as QR for a few minutes. */
    fun startPairing() {
        if (!::store.isInitialized) return
        if (!ensureServer()) {
            _state.update { it.copy(pairingError = true, pairing = null) }
            return
        }
        val host = localIpv4Address()
        if (host == null) {
            log.w { "Pairing not started: no local IPv4 address (TV offline?)" }
            _state.update { it.copy(pairingError = true, pairing = null) }
            return
        }
        val key = TvRemoteCrypto.randomKey()
        val code = TvRemoteCrypto.randomPairingCode()
        val info = TvRemotePairingInfo(
            tvId = store.tvId,
            tvName = store.tvName,
            host = host,
            port = port,
            secret = TvRemoteCrypto.encode(key),
        )
        val expiresAt = System.currentTimeMillis() + PAIRING_VALIDITY_MS
        synchronized(lock) {
            pairingKey = key
            pairingCodeKey = null
            pairingFailures = 0
        }
        _state.update {
            it.copy(
                pairing = info,
                pairingCode = code,
                pairingExpiresAtMs = expiresAt,
                pairingError = false,
                justPairedPhone = null,
            )
        }
        log.i { "Pairing opened on $host:$port" }
        pairingJob?.cancel()
        pairingJob = scope.launch {
            // Slow derivation: computed off the main thread while the QR is already usable.
            val codeKey = runCatching { TvRemoteCrypto.pairingCodeKey(code) }
                .onFailure { error -> log.e(error) { "Pairing code key derivation failed" } }
                .getOrNull()
            synchronized(lock) { if (pairingKey === key) pairingCodeKey = codeKey }
            delay(PAIRING_VALIDITY_MS)
            log.i { "Pairing code expired" }
            closePairing()
        }
    }

    /** Closes pairing (page left); the server stops when no phone is paired. */
    fun stopPairing() {
        closePairing()
        _state.update { it.copy(justPairedPhone = null) }
        stopServerIfUnused()
    }

    fun forgetPhone(clientId: String) {
        if (!::store.isInitialized) return
        store.removePhone(clientId)
        synchronized(lock) { lastSeen.remove(clientId) }
        _state.update { state ->
            state.copy(
                phones = store.phones(),
                connectedPhoneIds = state.connectedPhoneIds - clientId,
            )
        }
        log.i { "Phone forgotten on the TV" }
        stopServerIfUnused()
    }

    /** Refreshes the "connected" marks of the settings page. */
    fun refreshConnections() {
        val now = System.currentTimeMillis()
        val connected = synchronized(lock) {
            lastSeen.filterValues { now - it < CONNECTED_WINDOW_MS }.keys.toSet()
        }
        _state.update { it.copy(connectedPhoneIds = connected) }
    }

    private fun closePairing() {
        pairingJob?.cancel()
        pairingJob = null
        synchronized(lock) {
            pairingKey = null
            pairingCodeKey = null
        }
        _state.update { it.copy(pairing = null, pairingCode = null, pairingExpiresAtMs = 0L) }
    }

    private fun stopServerIfUnused() {
        if (_state.value.pairing != null || store.phones().isNotEmpty()) return
        synchronized(lock) {
            val running = server ?: return
            unregisterNsd()
            samplerJob?.cancel()
            samplerJob = null
            scope.launch { running.stop(500, 1_000) }
            server = null
        }
        _state.update { it.copy(running = false) }
        log.i { "Phone remote server stopped (no paired phone)" }
    }

    // region Server

    private fun ensureServer(): Boolean {
        synchronized(lock) {
            if (server != null) return true
            val selectedPort = pickPort()
            return try {
                val engine = embeddedServer(CIO, port = selectedPort, host = "0.0.0.0") {
                    routing {
                        post(TV_REMOTE_PATH_PAIR) { handleSealed(TV_REMOTE_PATH_PAIR) }
                        post(TV_REMOTE_PATH_STATE) { handleSealed(TV_REMOTE_PATH_STATE) }
                        post(TV_REMOTE_PATH_COMMAND) { handleSealed(TV_REMOTE_PATH_COMMAND) }
                        post(TV_REMOTE_PATH_FORGET) { handleSealed(TV_REMOTE_PATH_FORGET) }
                        post(TV_REMOTE_PATH_TELEGRAM_STATE) { handleSealed(TV_REMOTE_PATH_TELEGRAM_STATE) }
                        post(TV_REMOTE_PATH_TELEGRAM_ACTION) { handleSealed(TV_REMOTE_PATH_TELEGRAM_ACTION) }
                    }
                }
                engine.start(wait = false)
                server = engine
                port = selectedPort
                registerNsd(selectedPort)
                _state.update { it.copy(running = true) }
                log.i { "Phone remote server started on port $selectedPort" }
                true
            } catch (error: Exception) {
                log.e(error) { "Phone remote server failed to start" }
                false
            }
        }
    }

    private fun pickPort(): Int =
        runCatching { ServerSocket(PREFERRED_PORT).use { it.localPort } }
            .recoverCatching { ServerSocket(0).use { it.localPort } }
            .getOrDefault(PREFERRED_PORT)

    private suspend fun RoutingContext.handleSealed(path: String) {
        call.response.header("Cache-Control", "no-store")
        val body = runCatching { call.receiveText() }.getOrNull()
        if (body == null || body.length > MAX_BODY_CHARS) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val envelope = runCatching {
            TvRemoteCrypto.json.decodeFromString(TvRemoteEnvelope.serializer(), body)
        }.getOrNull()
        if (envelope == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        if (path == TV_REMOTE_PATH_PAIR) {
            handlePair(envelope)
            return
        }
        val key = store.clientKey(envelope.clientId)
        if (key == null) {
            log.w { "Request from an unknown phone on $path" }
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        when (path) {
            TV_REMOTE_PATH_STATE -> {
                val request = openOrNull(key, path, TvRemoteStateRequest.serializer(), envelope) ?: return
                markSeen(envelope.clientId)
                respondSealed(key, envelope.clientId, path, TvRemoteStateResponse.serializer(), waitForState(request))
            }
            TV_REMOTE_PATH_COMMAND -> {
                val request = openOrNull(key, path, TvRemoteCommandRequest.serializer(), envelope) ?: return
                markSeen(envelope.clientId)
                val error = runCommand(request)
                respondSealed(
                    key,
                    envelope.clientId,
                    path,
                    TvRemoteCommandResponse.serializer(),
                    TvRemoteCommandResponse(accepted = error == null, error = error),
                )
            }
            TV_REMOTE_PATH_FORGET -> {
                openOrNull(key, path, TvRemoteForgetRequest.serializer(), envelope) ?: return
                log.i { "Phone asked to be forgotten" }
                withContext(Dispatchers.Main) { forgetPhone(envelope.clientId) }
                call.respond(HttpStatusCode.NoContent)
            }
            TV_REMOTE_PATH_TELEGRAM_STATE -> {
                openOrNull(key, path, TvRemoteTelegramStateRequest.serializer(), envelope) ?: return
                markSeen(envelope.clientId)
                respondSealed(key, envelope.clientId, path, TvRemoteTelegramState.serializer(), telegramState())
            }
            TV_REMOTE_PATH_TELEGRAM_ACTION -> {
                val request = openOrNull(key, path, TvRemoteTelegramActionRequest.serializer(), envelope) ?: return
                markSeen(envelope.clientId)
                val duplicate = synchronized(lock) {
                    if (handledRequests.containsKey(request.requestId)) true else {
                        handledRequests[request.requestId] = System.currentTimeMillis()
                        false
                    }
                }
                if (!duplicate) runTelegramAction(request)
                respondSealed(key, envelope.clientId, path, TvRemoteTelegramState.serializer(), telegramState())
            }
            else -> call.respond(HttpStatusCode.NotFound)
        }
    }

    // region Telegram login from the phone

    private fun telegramState(): TvRemoteTelegramState =
        when (val auth = TelegramRepository.authState.value) {
            TelegramAuthState.Idle, TelegramAuthState.Initializing -> TvRemoteTelegramState(TvRemoteTelegramStep.Starting)
            TelegramAuthState.NotConfigured, TelegramAuthState.Unavailable -> TvRemoteTelegramState(TvRemoteTelegramStep.Unavailable)
            TelegramAuthState.WaitPhone -> TvRemoteTelegramState(TvRemoteTelegramStep.WaitPhone)
            is TelegramAuthState.WaitCode -> TvRemoteTelegramState(TvRemoteTelegramStep.WaitCode, codeLength = auth.codeLength)
            is TelegramAuthState.WaitQr -> TvRemoteTelegramState(TvRemoteTelegramStep.WaitQr, qrLink = auth.link)
            TelegramAuthState.WaitPassword -> TvRemoteTelegramState(TvRemoteTelegramStep.WaitPassword)
            is TelegramAuthState.Ready -> TvRemoteTelegramState(
                TvRemoteTelegramStep.Ready,
                accountName = auth.firstName.ifBlank { auth.userId.toString() },
            )
            is TelegramAuthState.Error -> TvRemoteTelegramState(TvRemoteTelegramStep.Error, error = auth.message)
        }

    private suspend fun runTelegramAction(request: TvRemoteTelegramActionRequest) {
        val value = request.value?.trim()?.take(MAX_TELEGRAM_VALUE_CHARS)
        log.i { "Telegram login step ${request.action} received from the phone" }
        val before = TelegramRepository.authState.value
        if (!TelegramRepository.isClientReady()) {
            TelegramRepository.startAuth()
            if (withTimeoutOrNull(TELEGRAM_CLIENT_START_MS) { TelegramRepository.awaitClient() } == null) {
                log.w { "Telegram client not started in time for ${request.action}" }
                return
            }
        }
        when (request.action) {
            TvRemoteTelegramActionType.Start -> Unit
            TvRemoteTelegramActionType.Retry -> TelegramRepository.retry()
            TvRemoteTelegramActionType.RequestQr -> TelegramRepository.requestQrLogin()
            TvRemoteTelegramActionType.SubmitPhone ->
                value?.takeIf { it.isNotEmpty() }?.let(TelegramRepository::submitPhone)
            TvRemoteTelegramActionType.SubmitCode ->
                value?.takeIf { it.isNotEmpty() }?.let(TelegramRepository::submitCode)
            TvRemoteTelegramActionType.SubmitPassword ->
                value?.takeIf { it.isNotEmpty() }?.let(TelegramRepository::submitPassword)
        }
        // Answers with the next step when TDLib moves on quickly (it usually does).
        withTimeoutOrNull(TELEGRAM_STEP_WAIT_MS) {
            TelegramRepository.authState.first { it != before && it != TelegramAuthState.Initializing }
        }
    }

    // endregion

    private suspend fun RoutingContext.handlePair(envelope: TvRemoteEnvelope) {
        val (qrKey, codeKey) = synchronized(lock) { pairingKey to pairingCodeKey }
        if (qrKey == null || envelope.clientId != TV_REMOTE_PAIR_CLIENT_ID) {
            log.w { "Pair request while pairing is closed" }
            call.respond(HttpStatusCode.Gone)
            return
        }
        // The phone sealed the request either with the QR secret or with the typed code.
        var key: ByteArray = qrKey
        var request = openPairRequest(qrKey, envelope)
        if (request == null && codeKey != null) {
            request = openPairRequest(codeKey, envelope)
            if (request != null) key = codeKey
        }
        if (request == null || !isFresh(request.timestampMs)) {
            val failures = synchronized(lock) { ++pairingFailures }
            log.w { "Invalid pair request ($failures/$MAX_PAIRING_FAILURES)" }
            if (failures >= MAX_PAIRING_FAILURES) {
                // Too many wrong attempts: a new code must be scanned.
                withContext(Dispatchers.Main) { startPairing() }
            }
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        // One use only: a second phone has to wait for a new code.
        val stillOpen = synchronized(lock) {
            if (pairingKey !== qrKey) false else {
                pairingKey = null
                pairingCodeKey = null
                true
            }
        }
        if (!stillOpen) {
            call.respond(HttpStatusCode.Gone)
            return
        }
        val clientId = TvRemoteCrypto.randomId()
        val clientKey = TvRemoteCrypto.randomKey()
        val phoneName = request.phoneName.trim().take(60).ifBlank { "Phone" }
        store.addPhone(TvPairedPhone(clientId, phoneName, System.currentTimeMillis()), clientKey)
        markSeen(clientId)
        pairingJob?.cancel()
        _state.update {
            it.copy(pairing = null, pairingCode = null, pairingExpiresAtMs = 0L, phones = store.phones(), justPairedPhone = phoneName)
        }
        log.i {
            "Phone paired with the ${if (key === qrKey) "QR code" else "pairing code"} " +
                "(key ${TvRemoteCrypto.fingerprint(clientKey)})"
        }
        respondSealed(
            key,
            TV_REMOTE_PAIR_CLIENT_ID,
            TV_REMOTE_PATH_PAIR,
            TvRemotePairResponse.serializer(),
            TvRemotePairResponse(
                clientId = clientId,
                clientKey = TvRemoteCrypto.encode(clientKey),
                tvId = store.tvId,
                tvName = store.tvName,
            ),
        )
    }

    private fun openPairRequest(key: ByteArray, envelope: TvRemoteEnvelope): TvRemotePairRequest? =
        runCatching {
            TvRemoteCrypto.open(key, TV_REMOTE_PATH_PAIR, TvRemotePairRequest.serializer(), envelope)
        }.getOrNull()

    private suspend fun <T> RoutingContext.openOrNull(
        key: ByteArray,
        path: String,
        serializer: KSerializer<T>,
        envelope: TvRemoteEnvelope,
    ): T? {
        val request = runCatching { TvRemoteCrypto.open(key, path, serializer, envelope) }.getOrNull()
        if (request == null) {
            log.w { "Unreadable request on $path" }
            call.respond(HttpStatusCode.Forbidden)
            return null
        }
        val timestamp = when (request) {
            is TvRemoteStateRequest -> request.timestampMs
            is TvRemoteCommandRequest -> request.timestampMs
            is TvRemoteForgetRequest -> request.timestampMs
            is TvRemoteTelegramStateRequest -> request.timestampMs
            is TvRemoteTelegramActionRequest -> request.timestampMs
            else -> 0L
        }
        if (!isFresh(timestamp)) {
            log.w { "Rejected request on $path: clock difference too large" }
            call.respond(HttpStatusCode.PreconditionFailed)
            return null
        }
        return request
    }

    private suspend fun <T> RoutingContext.respondSealed(
        key: ByteArray,
        clientId: String,
        path: String,
        serializer: KSerializer<T>,
        payload: T,
    ) {
        val envelope = TvRemoteCrypto.seal(key, clientId, path, serializer, payload)
        call.respondText(
            TvRemoteCrypto.json.encodeToString(TvRemoteEnvelope.serializer(), envelope),
            ContentType.Application.Json,
        )
    }

    private fun isFresh(timestampMs: Long): Boolean =
        abs(System.currentTimeMillis() - timestampMs) <= TV_REMOTE_MAX_CLOCK_SKEW_MS

    private fun markSeen(clientId: String) {
        val now = System.currentTimeMillis()
        lastPollAtMs = now
        synchronized(lock) { lastSeen[clientId] = now }
    }

    // endregion

    // region State

    private suspend fun waitForState(request: TvRemoteStateRequest): TvRemoteStateResponse {
        ensureSampler()
        val waitMs = request.waitMs.coerceIn(0L, TV_REMOTE_MAX_WAIT_MS)
        val current = published.value
        val result = if (current.revision > request.sinceRevision || waitMs == 0L) {
            current
        } else if (activePolls.incrementAndGet() > MAX_CONCURRENT_POLLS) {
            activePolls.decrementAndGet()
            current
        } else {
            try {
                withTimeoutOrNull(waitMs) {
                    published.first { it.revision > request.sinceRevision }
                } ?: published.value
            } finally {
                activePolls.decrementAndGet()
            }
        }
        return TvRemoteStateResponse(
            bootId = bootId,
            revision = result.revision,
            tvName = store.tvName,
            playback = result.playback,
            volume = result.volume,
        )
    }

    private fun ensureSampler() {
        synchronized(lock) {
            if (samplerJob?.isActive == true) return
            samplerJob = scope.launch {
                log.d { "State sampler started" }
                while (isActive) {
                    sample(force = false)
                    if (System.currentTimeMillis() - lastPollAtMs > SAMPLER_IDLE_MS) break
                    delay(SAMPLE_INTERVAL_MS)
                }
                log.d { "State sampler stopped (no phone polling)" }
            }
        }
    }

    private suspend fun sample(force: Boolean) {
        val playback = withContext(Dispatchers.Main) {
            TvPlayerRemoteRegistry.active?.phoneRemotePlayback()
        }
        val volume = readVolume()
        val now = System.currentTimeMillis()
        val previous = published.value
        val sameExceptPosition = previous.volume == volume &&
            previous.playback?.copy(positionMs = 0L) == playback?.copy(positionMs = 0L)
        val shouldPublish = when {
            force || !sameExceptPosition -> true
            playback == null || previous.playback == null -> false
            playback.positionMs == previous.playback.positionMs -> false
            else -> {
                val elapsed = now - previous.publishedAtMs
                val expected = if (playback.isPlaying) (elapsed * playback.playbackSpeed).toLong() else 0L
                val drift = abs(playback.positionMs - previous.playback.positionMs - expected)
                // A seek (or a stall) is published at once; normal progress every few seconds.
                drift > 1_500L || elapsed >= POSITION_PUBLISH_INTERVAL_MS
            }
        }
        if (shouldPublish) {
            published.value = Published(previous.revision + 1, playback, volume, now)
        }
    }

    // endregion

    // region Commands

    private suspend fun runCommand(request: TvRemoteCommandRequest): TvRemoteCommandError? {
        if (request.bootId != bootId) return TvRemoteCommandError.StaleBoot
        val duplicate = synchronized(lock) {
            if (handledRequests.containsKey(request.requestId)) true else {
                handledRequests[request.requestId] = System.currentTimeMillis()
                false
            }
        }
        if (duplicate) return TvRemoteCommandError.Duplicate
        val error = when (request.command) {
            TvRemoteCommandType.VolumeUp,
            TvRemoteCommandType.VolumeDown,
            TvRemoteCommandType.SetVolume,
            TvRemoteCommandType.ToggleMute,
            -> changeVolume(request)
            else -> withContext(Dispatchers.Main) {
                val remote = TvPlayerRemoteRegistry.active
                if (remote == null) TvRemoteCommandError.NoPlayback else remote.handlePhoneRemoteCommand(request)
            }
        }
        if (error == null) {
            log.d { "Command ${request.command} applied" }
        } else {
            log.d { "Command ${request.command} rejected: $error" }
        }
        // The phone sees the effect right away instead of at the next sample.
        delay(150)
        sample(force = true)
        return error
    }

    private fun audioManager(): AudioManager? = appContext.getSystemService(AudioManager::class.java)

    private fun readVolume(): TvRemoteVolume? {
        val audio = audioManager() ?: return null
        // Volume handled by the TV / receiver (HDMI-CEC): the phone hides its volume controls.
        if (audio.isVolumeFixed) return null
        return TvRemoteVolume(
            level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
        )
    }

    private fun changeVolume(request: TvRemoteCommandRequest): TvRemoteCommandError? {
        val audio = audioManager() ?: return TvRemoteCommandError.Unavailable
        if (audio.isVolumeFixed) return TvRemoteCommandError.Unavailable
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        runCatching {
            when (request.command) {
                TvRemoteCommandType.VolumeUp -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
                TvRemoteCommandType.VolumeDown -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
                TvRemoteCommandType.ToggleMute -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, flags)
                TvRemoteCommandType.SetVolume -> {
                    val level = (request.level ?: return TvRemoteCommandError.Unavailable)
                        .coerceIn(0, audio.getStreamMaxVolume(stream))
                    audio.setStreamVolume(stream, level, flags)
                }
                else -> Unit
            }
        }.onFailure { error ->
            log.w(error) { "Volume change failed" }
            return TvRemoteCommandError.Unavailable
        }
        return null
    }

    // endregion

    // region Network

    private fun localIpv4Address(): String? {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val fromActive = runCatching {
            connectivity?.getLinkProperties(connectivity.activeNetwork)
                ?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        if (fromActive != null) return fromActive
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun registerNsd(port: Int) {
        val nsd = appContext.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "Nuvio ${store.tvName}".take(60)
            serviceType = TV_REMOTE_NSD_SERVICE_TYPE
            setPort(port)
            setAttribute(TV_REMOTE_NSD_ID_ATTRIBUTE, store.tvId)
            setAttribute(TV_REMOTE_NSD_NAME_ATTRIBUTE, store.tvName.take(60))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                log.d { "Network discovery registered as ${serviceInfo.serviceName}" }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.w { "Network discovery registration failed ($errorCode)" }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsdRegistration = listener }
            .onFailure { error -> log.w(error) { "Network discovery not available" } }
    }

    private fun unregisterNsd() {
        val listener = nsdRegistration ?: return
        nsdRegistration = null
        runCatching { appContext.getSystemService(NsdManager::class.java)?.unregisterService(listener) }
    }

    // endregion
}
