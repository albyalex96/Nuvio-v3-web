package com.nuvio.app.features.kitsu

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * Kitsu account. Kitsu has no browser login for third-party apps: the account email and
 * password are exchanged once for OAuth tokens with the password grant, and only the tokens are kept.
 */
object KitsuAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("KitsuAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val signInMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(KitsuAuthUiState())
    val uiState: StateFlow<KitsuAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.KITSU,
        displayName = "Kitsu",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
        ),
    )

    private var hasLoaded = false
    private var storedState = KitsuStoredAuthState()
    private var credentials: KitsuCredentials? = null

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
        storedState = KitsuStoredAuthState()
        credentials = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        KitsuAuthStorage.removeProfile(profileId)
    }

    /** Exchanges the account [username] (Kitsu accepts only the email here) and [password] for tokens. Returns true when connected. */
    suspend fun signIn(username: String, password: String): Boolean = signInMutex.withLock {
        ensureLoaded()
        val login = username.trim()
        if (login.isBlank() || password.isEmpty()) {
            publish(error = KitsuAuthError.INVALID_CREDENTIALS)
            return@withLock false
        }
        publish(isLoading = true, error = null)
        log.i { "Kitsu sign-in started" }
        val response = try {
            kitsuHttpEngine.execute(
                method = "POST",
                url = KITSU_TOKEN_URL,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = kitsuPasswordForm(login, password),
            )
        } catch (error: CancellationException) {
            publish(isLoading = false)
            throw error
        } catch (error: Throwable) {
            log.e(error) { "Kitsu sign-in failed (network)" }
            publish(isLoading = false, error = KitsuAuthError.SIGN_IN_FAILED)
            return@withLock false
        }
        if (response.status in 400..401) {
            log.w { "Kitsu sign-in rejected: HTTP ${response.status}" }
            publish(isLoading = false, error = KitsuAuthError.INVALID_CREDENTIALS)
            return@withLock false
        }
        val token = response.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<KitsuTokenResponse>(it.body) }.getOrNull() }
            ?.toCredentials(nowEpochMs = nextRevision())
        if (token == null) {
            log.w { "Kitsu sign-in failed: HTTP ${response.status} or unreadable token" }
            publish(isLoading = false, error = KitsuAuthError.SIGN_IN_FAILED)
            return@withLock false
        }
        saveCredentials(token)
        publish(isLoading = true, error = null)
        if (!fetchAndStoreUser()) {
            log.w { "Kitsu user could not be loaded after sign-in" }
            saveCredentials(KitsuCredentials(issuedAtEpochMs = nextRevision()))
            publish(isLoading = false, error = KitsuAuthError.SIGN_IN_FAILED)
            return@withLock false
        }
        log.i { "Kitsu account connected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
        KitsuListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
        true
    }

    /** Explicit logout: a new revision with blank tokens, so synced devices log out as well. */
    fun onDisconnectRequested() {
        ensureLoaded()
        saveCredentials(KitsuCredentials(issuedAtEpochMs = nextRevision()))
        KitsuListRepository.clearLocalState()
        log.i { "Kitsu disconnected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
    }

    fun setSyncLoginAcrossDevices(enabled: Boolean) {
        ensureLoaded()
        if (storedState.syncLoginAcrossDevices == enabled) return
        storedState = storedState.copy(syncLoginAcrossDevices = enabled)
        persistMetadata()
        log.i { "Kitsu login sync across devices set to $enabled" }
        publish()
    }

    /** Kitsu user ID of the connected account. */
    internal fun userId(): String? {
        ensureLoaded()
        return credentials?.takeIf(KitsuCredentials::isLoggedIn)?.userId
    }

    /** Current access token, refreshed first when it is about to expire. */
    internal suspend fun validAccessToken(): String? {
        ensureLoaded()
        val current = credentials?.takeIf(KitsuCredentials::isLoggedIn) ?: return null
        if (!current.needsRefresh(nowEpochMs())) return current.accessToken
        return refreshCredentials(current)?.accessToken
            ?: current.accessToken.takeUnless { current.isExpired(nowEpochMs()) }
    }

    /** Called by the API client after a 401: returns a different token to retry with, or null. */
    internal suspend fun refreshAfterRejection(rejectedToken: String): String? {
        ensureLoaded()
        val current = credentials?.takeIf(KitsuCredentials::isLoggedIn) ?: return null
        if (current.accessToken != rejectedToken) return current.accessToken
        return refreshCredentials(current)?.accessToken?.takeIf { token -> token != rejectedToken }
    }

    internal fun onUnauthorizedResponse() {
        log.w { "Kitsu authorization rejected, disconnecting this device" }
        invalidateLocally(KitsuAuthError.AUTHORIZATION_REVOKED)
    }

    internal fun exportedCredentials(): KitsuCredentials? {
        ensureLoaded()
        return credentials?.takeIf { storedState.syncLoginAcrossDevices && it.issuedAtEpochMs > 0L }
    }

    internal fun isLoginSyncEnabled(): Boolean {
        ensureLoaded()
        return storedState.syncLoginAcrossDevices
    }

    /** Applies the profile blob: the sync preference first, then credentials newer than the local ones. */
    internal fun applyRemote(syncLoginAcrossDevices: Boolean?, remote: KitsuCredentials?) {
        ensureLoaded()
        if (syncLoginAcrossDevices != null && syncLoginAcrossDevices != storedState.syncLoginAcrossDevices) {
            storedState = storedState.copy(syncLoginAcrossDevices = syncLoginAcrossDevices)
            persistMetadata()
        }
        if (remote != null && storedState.syncLoginAcrossDevices && shouldApplyRemoteKitsuCredentials(credentials, remote)) {
            log.i { "Kitsu credentials from another device applied (loggedIn=${remote.isLoggedIn})" }
            saveCredentials(remote)
            if (remote.isLoggedIn) {
                KitsuListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
            } else {
                KitsuListRepository.clearLocalState()
            }
        }
        publish(isLoading = false, error = if (credentials?.isLoggedIn == true) null else _uiState.value.error)
    }

    private suspend fun fetchAndStoreUser(): Boolean {
        val user = try {
            val response = KitsuApi.client.execute(
                method = KitsuHttpMethod.GET,
                path = "/users",
                query = mapOf("filter[self]" to "true", "fields[users]" to "name"),
            )
            json.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray?.firstOrNull() as? JsonObject
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to fetch the Kitsu user" }
            return false
        } ?: return false
        val id = user["id"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: return false
        val name = (user["attributes"] as? JsonObject)?.get("name")?.jsonPrimitive?.content
        val current = credentials?.takeIf(KitsuCredentials::isLoggedIn) ?: return false
        saveCredentials(current.copy(userId = id, username = name))
        log.d { "Kitsu user loaded (id=$id)" }
        return true
    }

    private suspend fun refreshCredentials(current: KitsuCredentials): KitsuCredentials? = refreshMutex.withLock {
        val latest = credentials?.takeIf(KitsuCredentials::isLoggedIn) ?: return@withLock null
        if (latest.issuedAtEpochMs != current.issuedAtEpochMs) {
            log.d { "Kitsu credentials changed while waiting, skipping refresh" }
            return@withLock latest
        }
        if (latest.refreshToken.isBlank()) {
            log.w { "Kitsu credentials have no refresh token" }
            return@withLock null
        }
        val response = try {
            kitsuHttpEngine.execute(
                method = "POST",
                url = KITSU_TOKEN_URL,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = kitsuRefreshTokenForm(latest.refreshToken),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Kitsu token refresh failed (network), keeping the current token" }
            return@withLock null
        }
        val refreshed = response.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<KitsuTokenResponse>(it.body) }.getOrNull() }
            ?.toCredentials(nowEpochMs = nextRevision(), previous = latest)
        if (refreshed != null) {
            saveCredentials(refreshed)
            log.i { "Kitsu access token refreshed" }
            publish()
            return@withLock refreshed
        }
        if (response.status !in 400..401) {
            log.w { "Kitsu token refresh failed: HTTP ${response.status}, keeping the current token" }
            return@withLock null
        }
        // The refresh token was rejected: another device may have refreshed it already.
        log.w { "Kitsu refresh token rejected: HTTP ${response.status}" }
        if (storedState.syncLoginAcrossDevices) {
            log.i { "Pulling the profile blob to look for newer Kitsu credentials" }
            ProfileSettingsSync.pull(ProfileRepository.activeProfileId)
            val synced = credentials?.takeIf(KitsuCredentials::isLoggedIn)
            if (synced != null && synced.issuedAtEpochMs > latest.issuedAtEpochMs) {
                log.i { "Using the Kitsu credentials refreshed by another device" }
                return@withLock synced
            }
        }
        invalidateLocally(KitsuAuthError.AUTHORIZATION_REVOKED)
        null
    }

    /**
     * Drops the tokens on this device only: the revision stays the same, so the other devices of
     * the profile do not treat this as a logout.
     */
    private fun invalidateLocally(error: KitsuAuthError) {
        val revision = credentials?.issuedAtEpochMs ?: 0L
        saveCredentials(KitsuCredentials(issuedAtEpochMs = revision))
        publish(isLoading = false, error = error)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = KitsuAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<KitsuStoredAuthState>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse Kitsu auth metadata" } }
                    .getOrNull()
            }
            ?: KitsuStoredAuthState()
        credentials = KitsuAuthStorage.loadCredentialsPayload()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<KitsuCredentials>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse Kitsu credentials" } }
                    .getOrNull()
            }
        publish(isLoading = false, error = null)
    }

    private fun saveCredentials(value: KitsuCredentials) {
        credentials = value
        KitsuAuthStorage.saveCredentialsPayload(json.encodeToString(value))
    }

    private fun persistMetadata() {
        KitsuAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    /** Revisions must grow even if the clock moved back, or a synced copy could win over a newer one. */
    private fun nextRevision(): Long = maxOf(nowEpochMs(), (credentials?.issuedAtEpochMs ?: 0L) + 1L)

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: KitsuAuthError? = _uiState.value.error,
    ) {
        val current = credentials
        val authenticated = current?.isLoggedIn == true && !current.userId.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = KitsuAuthUiState(
            isConnected = authenticated,
            isLoading = isLoading,
            username = current?.username?.takeIf { authenticated },
            userId = current?.userId?.takeIf { authenticated },
            syncLoginAcrossDevices = storedState.syncLoginAcrossDevices,
            credentialsRevision = current?.issuedAtEpochMs ?: 0L,
            error = error,
        )
    }
}
