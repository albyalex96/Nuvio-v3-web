package com.nuvio.app.features.wetrakr

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * WeTrakr account, connected with the OAuth device flow: the app shows a short code, the user
 * approves it on wetrakr.com/activate and the app polls for the tokens. Only the app key
 * ([WeTrakrConfig.CLIENT_ID]) is needed: no client secret ships in the app.
 */
object WeTrakrAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("WeTrakrAuth")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val refreshMutex = Mutex()
    private const val DEVICE_CODE_WAIT_MS = 20_000L

    private val _uiState = MutableStateFlow(WeTrakrAuthUiState())
    val uiState: StateFlow<WeTrakrAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.WETRAKR,
        displayName = "WeTrakr",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
        ),
    )

    val isClientConfigured: Boolean
        get() = WeTrakrConfig.CLIENT_ID.isNotBlank()

    private var hasLoaded = false
    private var storedState = WeTrakrStoredAuthState()
    private var credentials: WeTrakrCredentials? = null
    private var pending: WeTrakrPendingAuthorization? = null
    private var pollJob: Job? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        cancelAuthorization()
        loadFromDisk()
    }

    override fun clearLocalState() {
        cancelAuthorization()
        hasLoaded = false
        storedState = WeTrakrStoredAuthState()
        credentials = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        WeTrakrAuthStorage.removeProfile(profileId)
    }

    /** Asks WeTrakr for a device code and starts polling until the user approves it on the website. */
    fun startAuthorization() {
        ensureLoaded()
        if (!isClientConfigured) {
            log.w { "WeTrakr connect requested but WETRAKR_CLIENT_ID is missing" }
            publish(error = WeTrakrAuthError.MISSING_CLIENT_ID)
            return
        }
        pollJob?.cancel()
        pending = null
        publish(isLoading = true, error = null)
        pollJob = scope.launch {
            try {
                runDeviceFlow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "WeTrakr sign-in failed" }
                pending = null
                publish(isLoading = false, error = WeTrakrAuthError.SIGN_IN_FAILED)
            }
        }
    }

    /**
     * Starts the device flow and waits for the code: returns the page where the user approves it,
     * or null when the code could not be obtained (the error is in [uiState]).
     */
    suspend fun requestAuthorizationUrl(): String? {
        startAuthorization()
        val state = withTimeoutOrNull(DEVICE_CODE_WAIT_MS) {
            uiState.first { state -> state.pending != null || (!state.isLoading && state.error != null) }
        }
        return state?.pending?.verificationUrl
    }

    /** The approval page of the code being polled, opened again from the card. */
    fun pendingAuthorizationUrl(): String? = pending?.verificationUrl

    fun cancelAuthorization() {
        if (pollJob?.isActive == true) log.i { "WeTrakr sign-in cancelled" }
        pollJob?.cancel()
        pollJob = null
        pending = null
        publish(isLoading = false)
    }

    private suspend fun runDeviceFlow() {
        log.i { "WeTrakr device flow started" }
        val codeResponse = weTrakrHttpEngine.execute(
            method = "POST",
            url = weTrakrApiUrl("/oauth/device/code"),
            headers = weTrakrBaseHeaders(withJsonBody = true),
            body = buildJsonObject { put("client_id", WeTrakrConfig.CLIENT_ID) }.toString(),
        )
        if (codeResponse.status == 429) {
            log.w { "WeTrakr device codes rate limited" }
            publish(isLoading = false, error = WeTrakrAuthError.TOO_MANY_REQUESTS)
            return
        }
        val code = codeResponse.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<WeTrakrDeviceCodeResponse>(it.body) }.getOrNull() }
            ?.takeIf { it.deviceCode.isNotBlank() && it.userCode.isNotBlank() }
        if (code == null) {
            log.w { "WeTrakr device code request failed: HTTP ${codeResponse.status}" }
            publish(isLoading = false, error = WeTrakrAuthError.SIGN_IN_FAILED)
            return
        }
        val expiresAt = nowEpochMs() + code.expiresIn.coerceAtLeast(60L) * 1_000L
        pending = WeTrakrPendingAuthorization(
            userCode = code.userCode,
            verificationUrl = code.verificationUrl.ifBlank { "$WETRAKR_WEBSITE_URL/activate" },
            expiresAtEpochMs = expiresAt,
        )
        publish(isLoading = false, error = null)
        var intervalMs = code.interval.coerceAtLeast(1L) * 1_000L
        while (nowEpochMs() < expiresAt) {
            delay(intervalMs)
            val response = weTrakrHttpEngine.execute(
                method = "POST",
                url = weTrakrApiUrl("/oauth/device/token"),
                headers = weTrakrBaseHeaders(withJsonBody = true),
                body = buildJsonObject {
                    put("code", code.deviceCode)
                    put("client_id", WeTrakrConfig.CLIENT_ID)
                }.toString(),
            )
            if (response.status in 200..299) {
                val token = runCatching { json.decodeFromString<WeTrakrTokenResponse>(response.body) }.getOrNull()
                    ?.toCredentials(nowEpochMs = nextRevision())
                if (token == null) {
                    log.w { "WeTrakr token response unreadable" }
                    finishFailed(WeTrakrAuthError.SIGN_IN_FAILED)
                    return
                }
                completeSignIn(token)
                return
            }
            val errorCode = runCatching { json.decodeFromString<WeTrakrErrorResponse>(response.body) }.getOrNull()?.error
            when {
                errorCode == "authorization_pending" -> Unit
                response.status == 429 || errorCode == "slow_down" -> intervalMs += 5_000L
                response.status == 410 || errorCode == "expired_token" -> {
                    log.w { "WeTrakr device code expired" }
                    finishFailed(WeTrakrAuthError.CODE_EXPIRED)
                    return
                }
                response.status == 418 || errorCode == "access_denied" -> {
                    log.w { "WeTrakr access denied by the user" }
                    finishFailed(WeTrakrAuthError.ACCESS_DENIED)
                    return
                }
                else -> {
                    log.w { "WeTrakr device token polling stopped: HTTP ${response.status} error=$errorCode" }
                    finishFailed(WeTrakrAuthError.SIGN_IN_FAILED)
                    return
                }
            }
        }
        log.w { "WeTrakr device code expired before approval" }
        finishFailed(WeTrakrAuthError.CODE_EXPIRED)
    }

    private fun finishFailed(error: WeTrakrAuthError) {
        pending = null
        publish(isLoading = false, error = error)
    }

    private suspend fun completeSignIn(token: WeTrakrCredentials) {
        pending = null
        saveCredentials(token)
        publish(isLoading = true, error = null)
        if (!fetchAndStoreUser()) {
            log.w { "WeTrakr account could not be loaded after sign-in" }
            saveCredentials(WeTrakrCredentials(issuedAtEpochMs = nextRevision()))
            publish(isLoading = false, error = WeTrakrAuthError.SIGN_IN_FAILED)
            return
        }
        log.i { "WeTrakr account connected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
        WeTrakrListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
    }

    /** Explicit logout: a new revision with blank tokens, so synced devices log out as well. */
    fun onDisconnectRequested() {
        ensureLoaded()
        cancelAuthorization()
        val refreshToken = credentials?.refreshToken
        val accessToken = credentials?.accessToken
        saveCredentials(WeTrakrCredentials(issuedAtEpochMs = nextRevision()))
        WeTrakrListRepository.clearLocalState()
        log.i { "WeTrakr disconnected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
        if (!refreshToken.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    weTrakrHttpEngine.execute(
                        method = "POST",
                        url = weTrakrApiUrl("/oauth/logout"),
                        headers = weTrakrBaseHeaders(withJsonBody = true) + ("Authorization" to "Bearer $accessToken"),
                        body = buildJsonObject { put("refresh_token", refreshToken) }.toString(),
                    )
                }.onFailure { error -> log.w(error) { "WeTrakr session revoke failed" } }
                    .onSuccess { response -> log.d { "WeTrakr session revoke: HTTP ${response.status}" } }
            }
        }
    }

    fun setSyncLoginAcrossDevices(enabled: Boolean) {
        ensureLoaded()
        if (storedState.syncLoginAcrossDevices == enabled) return
        storedState = storedState.copy(syncLoginAcrossDevices = enabled)
        persistMetadata()
        log.i { "WeTrakr login sync across devices set to $enabled" }
        publish()
    }

    /** Current access token, refreshed first when it is about to expire. */
    internal suspend fun validAccessToken(): String? {
        ensureLoaded()
        val current = credentials?.takeIf(WeTrakrCredentials::isLoggedIn) ?: return null
        if (!current.needsRefresh(nowEpochMs())) return current.accessToken
        return refreshCredentials(current)?.accessToken
            ?: current.accessToken.takeUnless { current.isExpired(nowEpochMs()) }
    }

    /** Called by the API client after a 401: returns a different token to retry with, or null. */
    internal suspend fun refreshAfterRejection(rejectedToken: String): String? {
        ensureLoaded()
        val current = credentials?.takeIf(WeTrakrCredentials::isLoggedIn) ?: return null
        if (current.accessToken != rejectedToken) return current.accessToken
        return refreshCredentials(current)?.accessToken?.takeIf { token -> token != rejectedToken }
    }

    internal fun onUnauthorizedResponse() {
        log.w { "WeTrakr authorization rejected, disconnecting this device" }
        invalidateLocally(WeTrakrAuthError.AUTHORIZATION_REVOKED)
    }

    internal fun exportedCredentials(): WeTrakrCredentials? {
        ensureLoaded()
        return credentials?.takeIf { storedState.syncLoginAcrossDevices && it.issuedAtEpochMs > 0L }
    }

    internal fun isLoginSyncEnabled(): Boolean {
        ensureLoaded()
        return storedState.syncLoginAcrossDevices
    }

    /** Applies the profile blob: the sync preference first, then credentials newer than the local ones. */
    internal fun applyRemote(syncLoginAcrossDevices: Boolean?, remote: WeTrakrCredentials?) {
        ensureLoaded()
        if (syncLoginAcrossDevices != null && syncLoginAcrossDevices != storedState.syncLoginAcrossDevices) {
            storedState = storedState.copy(syncLoginAcrossDevices = syncLoginAcrossDevices)
            persistMetadata()
        }
        if (remote != null && storedState.syncLoginAcrossDevices && shouldApplyRemoteWeTrakrCredentials(credentials, remote)) {
            log.i { "WeTrakr credentials from another device applied (loggedIn=${remote.isLoggedIn})" }
            saveCredentials(remote)
            if (remote.isLoggedIn) {
                WeTrakrListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
            } else {
                WeTrakrListRepository.clearLocalState()
            }
        }
        publish(isLoading = false, error = if (credentials?.isLoggedIn == true) null else _uiState.value.error)
    }

    private suspend fun fetchAndStoreUser(): Boolean {
        val account = try {
            val response = WeTrakrApi.client.execute(method = WeTrakrHttpMethod.GET, path = "/account/settings")
            json.parseToJsonElement(response.body).jsonObject
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to fetch the WeTrakr account" }
            return false
        }
        val id = account["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return false
        val info = account["info"] as? JsonObject
        val name = info?.get("display_name")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: info?.get("username")?.jsonPrimitive?.contentOrNull
        val current = credentials?.takeIf(WeTrakrCredentials::isLoggedIn) ?: return false
        saveCredentials(current.copy(userId = id, username = name))
        log.d { "WeTrakr account loaded (id=$id)" }
        return true
    }

    private suspend fun refreshCredentials(current: WeTrakrCredentials): WeTrakrCredentials? = refreshMutex.withLock {
        val latest = credentials?.takeIf(WeTrakrCredentials::isLoggedIn) ?: return@withLock null
        if (latest.issuedAtEpochMs != current.issuedAtEpochMs) {
            log.d { "WeTrakr credentials changed while waiting, skipping refresh" }
            return@withLock latest
        }
        if (latest.refreshToken.isBlank()) {
            log.w { "WeTrakr credentials have no refresh token" }
            return@withLock null
        }
        val response = try {
            weTrakrHttpEngine.execute(
                method = "POST",
                url = weTrakrApiUrl("/oauth/token/refresh"),
                headers = weTrakrBaseHeaders(withJsonBody = true),
                body = buildJsonObject { put("refresh_token", latest.refreshToken) }.toString(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "WeTrakr token refresh failed (network), keeping the current token" }
            return@withLock null
        }
        val refreshed = response.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<WeTrakrTokenResponse>(it.body) }.getOrNull() }
            ?.toCredentials(nowEpochMs = nextRevision(), previous = latest)
        if (refreshed != null) {
            saveCredentials(refreshed)
            log.i { "WeTrakr access token refreshed" }
            publish()
            return@withLock refreshed
        }
        if (response.status !in 400..401) {
            log.w { "WeTrakr token refresh failed: HTTP ${response.status}, keeping the current token" }
            return@withLock null
        }
        // The refresh token was rejected: another device may have rotated it already.
        log.w { "WeTrakr refresh token rejected: HTTP ${response.status}" }
        if (storedState.syncLoginAcrossDevices) {
            log.i { "Pulling the profile blob to look for newer WeTrakr credentials" }
            ProfileSettingsSync.pull(ProfileRepository.activeProfileId)
            val synced = credentials?.takeIf(WeTrakrCredentials::isLoggedIn)
            if (synced != null && synced.issuedAtEpochMs > latest.issuedAtEpochMs) {
                log.i { "Using the WeTrakr credentials refreshed by another device" }
                return@withLock synced
            }
        }
        invalidateLocally(WeTrakrAuthError.AUTHORIZATION_REVOKED)
        null
    }

    /**
     * Drops the tokens on this device only: the revision stays the same, so the other devices of
     * the profile do not treat this as a logout.
     */
    private fun invalidateLocally(error: WeTrakrAuthError) {
        val revision = credentials?.issuedAtEpochMs ?: 0L
        saveCredentials(WeTrakrCredentials(issuedAtEpochMs = revision))
        publish(isLoading = false, error = error)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = WeTrakrAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<WeTrakrStoredAuthState>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse WeTrakr auth metadata" } }
                    .getOrNull()
            }
            ?: WeTrakrStoredAuthState()
        credentials = WeTrakrAuthStorage.loadCredentialsPayload()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<WeTrakrCredentials>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse WeTrakr credentials" } }
                    .getOrNull()
            }
        publish(isLoading = false, error = null)
    }

    private fun saveCredentials(value: WeTrakrCredentials) {
        credentials = value
        WeTrakrAuthStorage.saveCredentialsPayload(json.encodeToString(value))
    }

    private fun persistMetadata() {
        WeTrakrAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    /** Revisions must grow even if the clock moved back, or a synced copy could win over a newer one. */
    private fun nextRevision(): Long = maxOf(nowEpochMs(), (credentials?.issuedAtEpochMs ?: 0L) + 1L)

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: WeTrakrAuthError? = _uiState.value.error,
    ) {
        val current = credentials
        val authenticated = current?.isLoggedIn == true && !current.userId.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = WeTrakrAuthUiState(
            isConnected = authenticated,
            isLoading = isLoading,
            username = current?.username?.takeIf { authenticated },
            userId = current?.userId?.takeIf { authenticated },
            pending = pending.takeUnless { authenticated },
            syncLoginAcrossDevices = storedState.syncLoginAcrossDevices,
            credentialsRevision = current?.issuedAtEpochMs ?: 0L,
            error = error,
        )
    }
}
