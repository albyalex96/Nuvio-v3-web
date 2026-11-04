package com.nuvio.app.features.telegram

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TelegramApiException(message: String) : Exception(message)

/** Thin wrapper around the TDLib client: login state machine and suspending requests. */
object TelegramClient {
    internal const val TDLIB_DB_DIR = "tdlib"
    internal const val TDLIB_FILES_DIR = "tdlib_files"
    internal const val TDLIB_SESSION_MARKER = "tdlib_session_ok"

    private val log = Logger.withTag("Telegram")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    @Volatile private var client: Client? = null
    @Volatile private var initializing = false
    @Volatile private var appContext: Context? = null
    @Volatile private var clientReady = CountDownLatch(1)

    /** False when libtdjni.so is missing for this CPU (only arm64-v8a and armeabi-v7a are shipped). */
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("tdjni")
            log.i { "TDLib native library loaded" }
            true
        } catch (e: Throwable) {
            log.w(e) { "TDLib native library not available on this device" }
            false
        }
    }

    fun isClientReady(): Boolean = client != null

    suspend fun awaitClient() {
        val latch = clientReady
        withContext(Dispatchers.IO) { latch.await() }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        synchronized(lock) {
            if (client != null || initializing) return
            initializing = true
        }
        val latch = clientReady
        scope.launch {
            try {
                if (!TelegramConfig.isConfigured) {
                    log.w { "Telegram API ID / API hash missing from the build configuration" }
                    _authState.value = TelegramAuthState.NotConfigured
                    return@launch
                }
                if (!isAvailable) {
                    _authState.value = TelegramAuthState.Unavailable
                    return@launch
                }
                _authState.value = TelegramAuthState.Initializing
                log.i { "Starting TDLib client" }
                runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
                client = Client.create(
                    { update -> handleUpdate(update) },
                    { e -> log.e(e) { "TDLib update handler failed" } },
                    { e -> log.e(e) { "TDLib default handler failed" } },
                )
            } catch (e: Throwable) {
                log.e(e) { "TDLib client creation failed" }
                _authState.value = TelegramAuthState.Error("TDLib initialization failed: ${e.message}")
            } finally {
                initializing = false
                latch.countDown()
            }
        }
    }

    private fun sendTdlibParameters() {
        val ctx = appContext ?: return
        client?.send(TdApi.SetTdlibParameters().also { p ->
            p.apiId = TelegramConfig.API_ID
            p.apiHash = TelegramConfig.API_HASH
            p.databaseDirectory = File(ctx.filesDir, TDLIB_DB_DIR).absolutePath
            p.filesDirectory = File(ctx.filesDir, TDLIB_FILES_DIR).absolutePath
            p.useMessageDatabase = false
            p.useSecretChats = false
            p.systemLanguageCode = Locale.getDefault().language.ifBlank { "en" }
            p.deviceModel = "Android"
            p.applicationVersion = "Nuvio"
        }) { result ->
            if (result is TdApi.Error) {
                log.e { "SetTdlibParameters failed: ${result.code} ${result.message}" }
                _authState.value = TelegramAuthState.Error(result.message)
            }
        }
    }

    private fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) handleAuthState(obj.authorizationState)
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        log.d { "Authorization state: ${state::class.simpleName}" }
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> {
                val length = when (val type = state.codeInfo.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
                    is TdApi.AuthenticationCodeTypeSms -> type.length
                    else -> 5
                }
                _authState.value = TelegramAuthState.WaitCode(length)
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                _authState.value = TelegramAuthState.WaitQr(state.link)
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = TelegramAuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> scope.launch {
                val user = runCatching { sendRequest(TdApi.GetMe()) as? TdApi.User }
                    .onFailure { log.w(it) { "GetMe failed" } }
                    .getOrNull()
                appContext?.let { File(it.filesDir, TDLIB_SESSION_MARKER).createNewFile() }
                log.i { "Telegram login ready" }
                _authState.value = TelegramAuthState.Ready(
                    firstName = user?.firstName.orEmpty(),
                    userId = user?.id ?: 0L,
                )
            }
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed -> _authState.value = TelegramAuthState.Idle
            else -> Unit
        }
    }

    /** Sends a login step and shows its error (wrong code, invalid number...) on the page. */
    private fun sendAuthStep(name: String, function: TdApi.Function<out TdApi.Object>) {
        val c = client
        if (c == null) {
            log.w { "Login step '$name' ignored: TDLib client not started" }
            return
        }
        log.i { "Sending login step '$name'" }
        c.send(function) { result ->
            if (result is TdApi.Error) {
                log.w { "Login step '$name' failed: ${result.code} ${result.message}" }
                _authState.value = TelegramAuthState.Error(result.message)
            }
        }
    }

    fun submitPhone(phone: String) =
        sendAuthStep("phone number", TdApi.SetAuthenticationPhoneNumber(phone, null))

    fun submitCode(code: String) = sendAuthStep("verification code", TdApi.CheckAuthenticationCode(code))

    fun submitPassword(password: String) =
        sendAuthStep("2FA password", TdApi.CheckAuthenticationPassword(password))

    /** Switches the login to a QR link (approved by another logged-in Telegram device). */
    fun requestQrLogin() =
        sendAuthStep("QR login", TdApi.RequestQrCodeAuthentication(LongArray(0)))

    suspend fun sendRequest(
        function: TdApi.Function<out TdApi.Object>,
        timeoutMs: Long = 10_000L,
    ): TdApi.Object? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            c.send(function) { result ->
                if (cont.isActive) {
                    if (result is TdApi.Error) {
                        cont.resumeWithException(TelegramApiException(result.message))
                    } else {
                        cont.resume(result)
                    }
                }
            }
        }
    }

    /** Re-reads the current login step after an error, or starts the client when it is not running. */
    fun retry(context: Context) {
        val c = client
        if (c == null) {
            initialize(context)
            return
        }
        log.i { "Retrying from the current login step" }
        c.send(TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationState) handleAuthState(result)
        }
    }

    /** Logs out (when [logOut]) or closes the client, so a new one can be started. */
    fun reset(logOut: Boolean) {
        val c: Client?
        synchronized(lock) {
            c = client
            client = null
            clientReady = CountDownLatch(1)
        }
        c?.send(if (logOut) TdApi.LogOut() else TdApi.Close(), null)
        _authState.value = TelegramAuthState.Idle
        log.i { "Telegram client reset logOut=$logOut" }
    }
}
