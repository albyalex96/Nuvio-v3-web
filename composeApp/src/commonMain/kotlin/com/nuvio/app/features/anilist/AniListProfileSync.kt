package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync of the AniList login, carried inside the `stream_badge_settings` payload of the
 * profile settings blob like the MyAnimeList and Kitsu ones.
 *
 * The preference [SYNC_LOGIN_KEY] always travels. The token [CREDENTIALS_KEY] travels only while
 * it is on. A copy is applied only when its revision is newer than
 * the local one, so logins and logouts propagate while stale blobs never undo them.
 */
internal object AniListProfileSync {
    private const val SYNC_LOGIN_KEY = "anilist_sync_login"
    private const val CREDENTIALS_KEY = "anilist_credentials"
    private val log = Logger.withTag("AniListProfileSync")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SYNC_LOGIN_KEY, encodeSyncBoolean(AniListAuthRepository.isLoginSyncEnabled()))
        AniListAuthRepository.exportedCredentials()?.let { credentials ->
            put(CREDENTIALS_KEY, encodeSyncString(json.encodeToString(credentials)))
        }
    }

    fun applyFromSyncPayload(payload: JsonObject) {
        val syncLogin = payload.decodeSyncBoolean(SYNC_LOGIN_KEY)
        val credentials = payload.decodeSyncString(CREDENTIALS_KEY)?.let { encoded ->
            runCatching { json.decodeFromString<AniListCredentials>(encoded) }
                .onFailure { error -> log.w(error) { "Invalid AniList credentials in the profile blob" } }
                .getOrNull()
        }
        if (syncLogin == null && credentials == null) return
        log.d { "AniList login found in the profile blob (syncLogin=$syncLogin, credentials=${credentials != null})" }
        AniListAuthRepository.applyRemote(syncLoginAcrossDevices = syncLogin, remote = credentials)
    }

    /** Changes that must push the profile blob; the tokens themselves never enter the signature. */
    fun syncSignature(state: AniListAuthUiState): String =
        "anilist_${state.syncLoginAcrossDevices}_${state.credentialsRevision}"
}
