package com.nuvio.app.features.wetrakr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val WETRAKR_API_BASE_URL = "https://api.wetrakr.com"
internal const val WETRAKR_WEBSITE_URL = "https://wetrakr.com"
internal const val WETRAKR_API_VERSION = "1"

/** Access tokens are refreshed this long before they expire (WeTrakr issues them for 7 days). */
internal const val WETRAKR_TOKEN_REFRESH_MARGIN_MS = 24L * 60L * 60L * 1_000L

enum class WeTrakrAuthError {
    MISSING_CLIENT_ID,
    SIGN_IN_FAILED,
    CODE_EXPIRED,
    ACCESS_DENIED,
    TOO_MANY_REQUESTS,
    AUTHORIZATION_REVOKED,
}

/** A device code waiting for the user to approve it on the WeTrakr website. */
data class WeTrakrPendingAuthorization(
    val userCode: String,
    val verificationUrl: String,
    val expiresAtEpochMs: Long,
)

data class WeTrakrAuthUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val userId: String? = null,
    val pending: WeTrakrPendingAuthorization? = null,
    val syncLoginAcrossDevices: Boolean = false,
    /** Revision of the credentials (login, refresh or logout time); drives the profile blob push. */
    val credentialsRevision: Long = 0L,
    val error: WeTrakrAuthError? = null,
)

/**
 * Tokens of the WeTrakr account. [issuedAtEpochMs] is the revision used to decide which copy wins
 * between devices; a logout is a revision with blank tokens.
 */
@Serializable
internal data class WeTrakrCredentials(
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
internal data class WeTrakrStoredAuthState(
    val syncLoginAcrossDevices: Boolean = false,
)

@Serializable
internal data class WeTrakrDeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_url") val verificationUrl: String = "",
    @SerialName("expires_in") val expiresIn: Long = 600L,
    val interval: Long = 5L,
)

@Serializable
internal data class WeTrakrTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
internal data class WeTrakrErrorResponse(
    val error: String? = null,
    val message: String? = null,
)

internal fun WeTrakrTokenResponse.toCredentials(
    nowEpochMs: Long,
    previous: WeTrakrCredentials? = null,
): WeTrakrCredentials? {
    if (accessToken.isBlank()) return null
    return WeTrakrCredentials(
        accessToken = accessToken,
        refreshToken = refreshToken.ifBlank { previous?.refreshToken.orEmpty() },
        expiresAtEpochMs = expiresIn?.takeIf { seconds -> seconds > 0L }?.let { seconds -> nowEpochMs + seconds * 1_000L },
        issuedAtEpochMs = nowEpochMs,
        userId = previous?.userId,
        username = previous?.username,
    )
}

internal fun WeTrakrCredentials.needsRefresh(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt - WETRAKR_TOKEN_REFRESH_MARGIN_MS
}

internal fun WeTrakrCredentials.isExpired(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt
}

/** A synced copy wins only when it is newer than the local one, so stale blobs never undo a refresh. */
internal fun shouldApplyRemoteWeTrakrCredentials(
    local: WeTrakrCredentials?,
    remote: WeTrakrCredentials,
): Boolean = remote.issuedAtEpochMs > (local?.issuedAtEpochMs ?: 0L)
