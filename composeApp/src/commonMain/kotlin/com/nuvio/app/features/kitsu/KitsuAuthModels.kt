package com.nuvio.app.features.kitsu

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val KITSU_TOKEN_URL = "https://kitsu.app/api/oauth/token"
internal const val KITSU_API_BASE_URL = "https://kitsu.app/api/edge"

/**
 * Public OAuth client of the Kitsu web and mobile apps. Kitsu has no developer registration:
 * every third-party client uses these values with the password grant.
 */
internal const val KITSU_CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
internal const val KITSU_CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

/** Access tokens are refreshed this long before they expire (Kitsu issues them for about 30 days). */
internal const val KITSU_TOKEN_REFRESH_MARGIN_MS = 24L * 60L * 60L * 1_000L

enum class KitsuAuthError {
    INVALID_CREDENTIALS,
    SIGN_IN_FAILED,
    AUTHORIZATION_REVOKED,
}

data class KitsuAuthUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val userId: String? = null,
    val syncLoginAcrossDevices: Boolean = false,
    /** Revision of the credentials (login, refresh or logout time); drives the profile blob push. */
    val credentialsRevision: Long = 0L,
    val error: KitsuAuthError? = null,
)

/**
 * Tokens of the Kitsu account. The password is never stored: only the tokens it was exchanged for.
 * [issuedAtEpochMs] is the revision used to decide which copy wins between devices; a logout is a
 * revision with blank tokens.
 */
@Serializable
internal data class KitsuCredentials(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_at") val expiresAtEpochMs: Long? = null,
    @SerialName("issued_at") val issuedAtEpochMs: Long = 0L,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("username") val username: String? = null,
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()
}

@Serializable
internal data class KitsuStoredAuthState(
    val syncLoginAcrossDevices: Boolean = false,
)

@Serializable
internal data class KitsuTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long? = null,
)

internal fun kitsuPasswordForm(username: String, password: String): String = formBody(
    "grant_type" to "password",
    "username" to username,
    "password" to password,
    "client_id" to KITSU_CLIENT_ID,
    "client_secret" to KITSU_CLIENT_SECRET,
)

internal fun kitsuRefreshTokenForm(refreshToken: String): String = formBody(
    "grant_type" to "refresh_token",
    "refresh_token" to refreshToken,
    "client_id" to KITSU_CLIENT_ID,
    "client_secret" to KITSU_CLIENT_SECRET,
)

private fun formBody(vararg fields: Pair<String, String>): String =
    fields.joinToString("&") { (key, value) -> "${key.encodeURLParameter()}=${value.encodeURLParameter()}" }

internal fun KitsuTokenResponse.toCredentials(
    nowEpochMs: Long,
    previous: KitsuCredentials? = null,
): KitsuCredentials? {
    if (accessToken.isBlank()) return null
    return KitsuCredentials(
        accessToken = accessToken,
        refreshToken = refreshToken.ifBlank { previous?.refreshToken.orEmpty() },
        expiresAtEpochMs = expiresIn?.takeIf { seconds -> seconds > 0L }?.let { seconds -> nowEpochMs + seconds * 1_000L },
        issuedAtEpochMs = nowEpochMs,
        userId = previous?.userId,
        username = previous?.username,
    )
}

internal fun KitsuCredentials.needsRefresh(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt - KITSU_TOKEN_REFRESH_MARGIN_MS
}

internal fun KitsuCredentials.isExpired(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt
}

/** A synced copy wins only when it is newer than the local one, so stale blobs never undo a refresh. */
internal fun shouldApplyRemoteKitsuCredentials(
    local: KitsuCredentials?,
    remote: KitsuCredentials,
): Boolean = remote.issuedAtEpochMs > (local?.issuedAtEpochMs ?: 0L)
