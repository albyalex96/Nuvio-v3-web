package com.nuvio.app.features.anilist

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val ANILIST_AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"
internal const val ANILIST_GRAPHQL_URL = "https://graphql.anilist.co"

/** A browser sign-in older than this is no longer accepted when its callback arrives. */
internal const val ANILIST_AUTHORIZATION_TIMEOUT_MS = 15L * 60L * 1_000L

enum class AniListAuthError {
    MISSING_CLIENT_ID,
    INVALID_CALLBACK,
    AUTHORIZATION_DENIED,
    AUTHORIZATION_EXPIRED,
    SIGN_IN_FAILED,
    AUTHORIZATION_REVOKED,
}

enum class AniListConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED,
}

data class AniListAuthUiState(
    val mode: AniListConnectionMode = AniListConnectionMode.DISCONNECTED,
    val credentialsConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val userId: Long? = null,
    val syncLoginAcrossDevices: Boolean = false,
    /** Revision of the credentials (login or logout time); drives the profile blob push. */
    val credentialsRevision: Long = 0L,
    val error: AniListAuthError? = null,
) {
    val isConnected: Boolean
        get() = mode == AniListConnectionMode.CONNECTED
}

/**
 * Token of the AniList account (implicit grant: one access token valid for a year, no refresh token).
 * [issuedAtEpochMs] is the revision used to decide which copy wins between devices; a logout is a
 * revision with a blank token.
 */
@Serializable
internal data class AniListCredentials(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_at") val expiresAtEpochMs: Long? = null,
    @SerialName("issued_at") val issuedAtEpochMs: Long = 0L,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("username") val username: String? = null,
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()
}

@Serializable
internal data class AniListStoredAuthState(
    val syncLoginAcrossDevices: Boolean = false,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
)

internal fun buildAniListAuthorizationUrl(clientId: String): String =
    "$ANILIST_AUTHORIZE_URL?client_id=${clientId.encodeURLParameter()}&response_type=token"

internal sealed interface AniListAuthCallback {
    /** Not an AniList redirect: another provider handles it. */
    data object NotAniList : AniListAuthCallback
    data object Invalid : AniListAuthCallback
    data object Denied : AniListAuthCallback
    data class Token(val accessToken: String, val expiresInSeconds: Long?) : AniListAuthCallback
}

/** Parses the implicit-grant redirect: `<redirect>#access_token=...&token_type=Bearer&expires_in=...`. */
internal fun parseAniListAuthCallback(url: String, redirectUri: String): AniListAuthCallback {
    val trimmed = url.trim()
    if (!trimmed.startsWith(redirectUri)) return AniListAuthCallback.NotAniList
    val rest = trimmed.removePrefix(redirectUri)
    if (rest.isNotEmpty() && rest.first() !in "#?/") return AniListAuthCallback.NotAniList
    val params = rest.trimStart('/')
        .split('#', '?')
        .filter(String::isNotBlank)
        .flatMap { part -> part.split('&') }
        .mapNotNull { pair ->
            val key = pair.substringBefore('=', "").takeIf(String::isNotBlank) ?: return@mapNotNull null
            key to pair.substringAfter('=', "").decodeURLQueryComponent(plusIsSpace = true)
        }
        .toMap()
    if (params["error"] != null) return AniListAuthCallback.Denied
    val token = params["access_token"]?.takeIf(String::isNotBlank) ?: return AniListAuthCallback.Invalid
    return AniListAuthCallback.Token(token, params["expires_in"]?.toLongOrNull())
}

internal fun AniListCredentials.isExpired(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt
}

/** A synced copy wins only when it is newer than the local one, so stale blobs never undo a login or logout. */
internal fun shouldApplyRemoteAniListCredentials(
    local: AniListCredentials?,
    remote: AniListCredentials,
): Boolean = remote.issuedAtEpochMs > (local?.issuedAtEpochMs ?: 0L)
