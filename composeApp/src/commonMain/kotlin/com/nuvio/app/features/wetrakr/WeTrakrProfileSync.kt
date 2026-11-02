package com.nuvio.app.features.wetrakr

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
 * Profile sync of the WeTrakr login, carried inside the `stream_badge_settings` payload of the
 * profile settings blob like the Kitsu one.
 *
 * The preference [SYNC_LOGIN_KEY] always travels. The tokens [CREDENTIALS_KEY] travel only while
 * it is on. A copy is applied only when its revision is newer than
 * the local one, so logins, token refreshes and logouts propagate while stale blobs never undo them.
 */
internal object WeTrakrProfileSync {
    private const val SYNC_LOGIN_KEY = "wetrakr_sync_login"
    private const val CREDENTIALS_KEY = "wetrakr_credentials"
    private val log = Logger.withTag("WeTrakrProfileSync")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SYNC_LOGIN_KEY, encodeSyncBoolean(WeTrakrAuthRepository.isLoginSyncEnabled()))
        WeTrakrAuthRepository.exportedCredentials()?.let { credentials ->
            put(CREDENTIALS_KEY, encodeSyncString(json.encodeToString(credentials)))
        }
    }

    fun applyFromSyncPayload(payload: JsonObject) {
        val syncLogin = payload.decodeSyncBoolean(SYNC_LOGIN_KEY)
        val credentials = payload.decodeSyncString(CREDENTIALS_KEY)?.let { encoded ->
            runCatching { json.decodeFromString<WeTrakrCredentials>(encoded) }
                .onFailure { error -> log.w(error) { "Invalid WeTrakr credentials in the profile blob" } }
                .getOrNull()
        }
        if (syncLogin == null && credentials == null) return
        log.d { "WeTrakr login found in the profile blob (syncLogin=$syncLogin, credentials=${credentials != null})" }
        WeTrakrAuthRepository.applyRemote(syncLoginAcrossDevices = syncLogin, remote = credentials)
    }

    /** Changes that must push the profile blob; the tokens themselves never enter the signature. */
    fun syncSignature(state: WeTrakrAuthUiState): String =
        "wetrakr_${state.syncLoginAcrossDevices}_${state.credentialsRevision}"
}
