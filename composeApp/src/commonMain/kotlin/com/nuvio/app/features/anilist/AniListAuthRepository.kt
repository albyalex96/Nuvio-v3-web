package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * AniList account. The sign-in is the OAuth implicit grant in the browser: AniList redirects to
 * [AniListConfig.REDIRECT_URI] with an access token valid for a year (there is no refresh token).
 * Only the client ID of an AniList API client is needed, no secret.
 */
object AniListAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("AniListAuth")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val signInMutex = Mutex()

    private val _uiState = MutableStateFlow(AniListAuthUiState(credentialsConfigured = hasRequiredCredentials()))
    val uiState: StateFlow<AniListAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.ANILIST,
        displayName = "AniList",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
        ),
    )

    private var hasLoaded = false
    private var storedState = AniListStoredAuthState()
    private var credentials: AniListCredentials? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        storedState = AniListStoredAuthState()
        credentials = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        AniListAuthStorage.removeProfile(profileId)
    }

    fun hasRequiredCredentials(): Boolean = AniListConfig.CLIENT_ID.isNotBlank()

    /** Starts the browser sign-in and returns the AniList authorization page, or null without a client ID. */
    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            log.w { "AniList connect requested but ANILIST_CLIENT_ID is missing" }
            publish(error = AniListAuthError.MISSING_CLIENT_ID)
            return null
        }
        storedState = storedState.copy(pendingAuthorizationStartedAtEpochMs = nowEpochMs())
        persistMetadata()
        publish(error = null)
        log.i { "AniList authorization started" }
        return buildAniListAuthorizationUrl(AniListConfig.CLIENT_ID)
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val startedAt = storedState.pendingAuthorizationStartedAtEpochMs ?: return null
        if (nowEpochMs() - startedAt > ANILIST_AUTHORIZATION_TIMEOUT_MS) {
            clearPendingAuthorization()
            publish(error = AniListAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return buildAniListAuthorizationUrl(AniListConfig.CLIENT_ID)
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        clearPendingAuthorization()
        publish(error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        return when (val callback = parseAniListAuthCallback(url, AniListConfig.REDIRECT_URI)) {
            AniListAuthCallback.NotAniList -> false
            AniListAuthCallback.Invalid -> {
                log.w { "AniList callback without an access token" }
                clearPendingAuthorization()
                publish(error = AniListAuthError.INVALID_CALLBACK)
                true
            }
            AniListAuthCallback.Denied -> {
                log.w { "AniList authorization denied by the user" }
                clearPendingAuthorization()
                publish(error = AniListAuthError.AUTHORIZATION_DENIED)
                true
            }
            is AniListAuthCallback.Token -> {
                val startedAt = storedState.pendingAuthorizationStartedAtEpochMs
                if (startedAt == null || nowEpochMs() - startedAt > ANILIST_AUTHORIZATION_TIMEOUT_MS) {
                    log.w { "AniList callback without a pending sign-in (startedAt=$startedAt), ignored" }
                    clearPendingAuthorization()
                    publish(error = AniListAuthError.AUTHORIZATION_EXPIRED)
                } else {
                    scope.launch { completeAuthorization(callback) }
                }
                true
            }
        }
    }

    private suspend fun completeAuthorization(callback: AniListAuthCallback.Token) = signInMutex.withLock {
        clearPendingAuthorization()
        publish(isLoading = true, error = null)
        val now = nextRevision()
        saveCredentials(
            AniListCredentials(
                accessToken = callback.accessToken,
                expiresAtEpochMs = callback.expiresInSeconds?.takeIf { it > 0L }?.let { now + it * 1_000L },
                issuedAtEpochMs = now,
            ),
        )
        if (!fetchAndStoreViewer()) {
            log.w { "AniList user could not be loaded after sign-in" }
            saveCredentials(AniListCredentials(issuedAtEpochMs = nextRevision()))
            publish(isLoading = false, error = AniListAuthError.SIGN_IN_FAILED)
            return@withLock
        }
        log.i { "AniList account connected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
        AniListListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
    }

    /** Explicit logout: a new revision with a blank token, so synced devices log out as well. */
    fun onDisconnectRequested() {
        ensureLoaded()
        clearPendingAuthorization()
        saveCredentials(AniListCredentials(issuedAtEpochMs = nextRevision()))
        AniListListRepository.clearLocalState()
        log.i { "AniList disconnected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
    }

    fun setSyncLoginAcrossDevices(enabled: Boolean) {
        ensureLoaded()
        if (storedState.syncLoginAcrossDevices == enabled) return
        storedState = storedState.copy(syncLoginAcrossDevices = enabled)
        persistMetadata()
        log.i { "AniList login sync across devices set to $enabled" }
        publish()
    }

    /** AniList user ID of the connected account. */
    internal fun userId(): Long? {
        ensureLoaded()
        return credentials?.takeIf(AniListCredentials::isLoggedIn)?.userId
    }

    /** Current access token, or null when disconnected or expired. */
    internal fun validAccessToken(): String? {
        ensureLoaded()
        val current = credentials?.takeIf(AniListCredentials::isLoggedIn) ?: return null
        if (current.isExpired(nowEpochMs())) {
            log.w { "AniList access token expired" }
            invalidateLocally(AniListAuthError.AUTHORIZATION_REVOKED)
            return null
        }
        return current.accessToken
    }

    internal fun onUnauthorizedResponse() {
        log.w { "AniList authorization rejected, disconnecting this device" }
        invalidateLocally(AniListAuthError.AUTHORIZATION_REVOKED)
    }

    internal fun exportedCredentials(): AniListCredentials? {
        ensureLoaded()
        return credentials?.takeIf { storedState.syncLoginAcrossDevices && it.issuedAtEpochMs > 0L }
    }

    internal fun isLoginSyncEnabled(): Boolean {
        ensureLoaded()
        return storedState.syncLoginAcrossDevices
    }

    /** Applies the profile blob: the sync preference first, then credentials newer than the local ones. */
    internal fun applyRemote(syncLoginAcrossDevices: Boolean?, remote: AniListCredentials?) {
        ensureLoaded()
        if (syncLoginAcrossDevices != null && syncLoginAcrossDevices != storedState.syncLoginAcrossDevices) {
            storedState = storedState.copy(syncLoginAcrossDevices = syncLoginAcrossDevices)
            persistMetadata()
        }
        if (remote != null && storedState.syncLoginAcrossDevices && shouldApplyRemoteAniListCredentials(credentials, remote)) {
            log.i { "AniList credentials from another device applied (loggedIn=${remote.isLoggedIn})" }
            saveCredentials(remote)
            if (remote.isLoggedIn) {
                AniListListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
            } else {
                AniListListRepository.clearLocalState()
            }
        }
        publish(isLoading = false, error = if (credentials?.isLoggedIn == true) null else _uiState.value.error)
    }

    private suspend fun fetchAndStoreViewer(): Boolean {
        val viewer = try {
            AniListApi.client.query(operation = "Viewer", query = "query { Viewer { id name } }")
                ?.get("Viewer").objectOrNull()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to fetch the AniList user" }
            return false
        } ?: return false
        val id = viewer["id"]?.jsonPrimitive?.longOrNull ?: return false
        val name = viewer["name"]?.jsonPrimitive?.contentOrNull
        val current = credentials?.takeIf(AniListCredentials::isLoggedIn) ?: return false
        saveCredentials(current.copy(userId = id, username = name))
        log.d { "AniList user loaded (id=$id)" }
        return true
    }

    /**
     * Drops the token on this device only: the revision stays the same, so the other devices of
     * the profile do not treat this as a logout.
     */
    private fun invalidateLocally(error: AniListAuthError) {
        val revision = credentials?.issuedAtEpochMs ?: 0L
        saveCredentials(AniListCredentials(issuedAtEpochMs = revision))
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        if (storedState.pendingAuthorizationStartedAtEpochMs == null) return
        storedState = storedState.copy(pendingAuthorizationStartedAtEpochMs = null)
        persistMetadata()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = AniListAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<AniListStoredAuthState>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse AniList auth metadata" } }
                    .getOrNull()
            }
            ?: AniListStoredAuthState()
        credentials = AniListAuthStorage.loadCredentialsPayload()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<AniListCredentials>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse AniList credentials" } }
                    .getOrNull()
            }
        publish(isLoading = false, error = null)
    }

    private fun saveCredentials(value: AniListCredentials) {
        credentials = value
        AniListAuthStorage.saveCredentialsPayload(json.encodeToString(value))
    }

    private fun persistMetadata() {
        AniListAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    /** Revisions must grow even if the clock moved back, or a synced copy could win over a newer one. */
    private fun nextRevision(): Long = maxOf(nowEpochMs(), (credentials?.issuedAtEpochMs ?: 0L) + 1L)

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: AniListAuthError? = _uiState.value.error,
    ) {
        val current = credentials
        val authenticated = current?.isLoggedIn == true && current.userId != null
        _isAuthenticated.value = authenticated
        val mode = when {
            authenticated -> AniListConnectionMode.CONNECTED
            storedState.pendingAuthorizationStartedAtEpochMs != null -> AniListConnectionMode.AWAITING_APPROVAL
            else -> AniListConnectionMode.DISCONNECTED
        }
        _uiState.value = AniListAuthUiState(
            mode = mode,
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = current?.username?.takeIf { authenticated },
            userId = current?.userId?.takeIf { authenticated },
            syncLoginAcrossDevices = storedState.syncLoginAcrossDevices,
            credentialsRevision = current?.issuedAtEpochMs ?: 0L,
            error = error,
        )
    }
}
