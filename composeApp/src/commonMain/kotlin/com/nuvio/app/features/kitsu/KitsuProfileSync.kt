package com.nuvio.app.features.kitsu

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
 * Profile sync of the Kitsu login, carried inside the `stream_badge_settings` payload of the
 * profile settings blob like the MyAnimeList one.
 *
 * The preference [SYNC_LOGIN_KEY] always travels. The tokens [CREDENTIALS_KEY] travel only while
 * it is on (the password is never stored). A copy is applied only when its revision is newer than
 * the local one, so logins, token refreshes and logouts propagate while stale blobs never undo them.
 */
internal object KitsuProfileSync {
    private const val SYNC_LOGIN_KEY = "kitsu_sync_login"
    private const val CREDENTIALS_KEY = "kitsu_credentials"
    private val log = Logger.withTag("KitsuProfileSync")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SYNC_LOGIN_KEY, encodeSyncBoolean(KitsuAuthRepository.isLoginSyncEnabled()))
        KitsuAuthRepository.exportedCredentials()?.let { credentials ->
            put(CREDENTIALS_KEY, encodeSyncString(json.encodeToString(credentials)))
        }
    }

    fun applyFromSyncPayload(payload: JsonObject) {
        val syncLogin = payload.decodeSyncBoolean(SYNC_LOGIN_KEY)
        val credentials = payload.decodeSyncString(CREDENTIALS_KEY)?.let { encoded ->
            runCatching { json.decodeFromString<KitsuCredentials>(encoded) }
                .onFailure { error -> log.w(error) { "Invalid Kitsu credentials in the profile blob" } }
                .getOrNull()
        }
        if (syncLogin == null && credentials == null) return
        log.d { "Kitsu login found in the profile blob (syncLogin=$syncLogin, credentials=${credentials != null})" }
        KitsuAuthRepository.applyRemote(syncLoginAcrossDevices = syncLogin, remote = credentials)
    }

    /** Changes that must push the profile blob; the tokens themselves never enter the signature. */
    fun syncSignature(state: KitsuAuthUiState): String =
        "kitsu_${state.syncLoginAcrossDevices}_${state.credentialsRevision}"
}
