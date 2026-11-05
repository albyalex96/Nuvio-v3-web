package com.nuvio.app.features.discord

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

// Rich Presence is not supported on iOS: only the (synced) settings are kept, never the token.
internal actual object DiscordRpcStorage {
    private const val settingsKey = "discord_rpc_settings_payload"
    private const val userKey = "discord_rpc_user_payload"

    actual fun loadSettingsPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(settingsKey))

    actual fun saveSettingsPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(settingsKey))
    }

    actual fun loadUserPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(userKey))

    actual fun saveUserPayload(payload: String?) {
        val key = ProfileScopedKey.of(userKey)
        if (payload == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(payload, forKey = key)
        }
    }

    actual fun loadToken(): String? = null

    actual fun saveToken(token: String?) = Unit

    actual fun loadRejectedTokenFingerprint(): String? = null

    actual fun saveRejectedTokenFingerprint(fingerprint: String?) = Unit

    actual fun loadSyncedTokenFingerprint(): String? = null

    actual fun saveSyncedTokenFingerprint(fingerprint: String?) = Unit

    // Memory only (never in NSUserDefaults): iOS just carries the synced token through the profile
    // blob, so pushing a blob from an iPhone does not drop the Android devices' Discord login.
    private val profileSyncTokens = mutableMapOf<String, String>()

    actual fun loadProfileSyncToken(): String? = profileSyncTokens[ProfileScopedKey.of(profileSyncTokenKey)]

    actual fun saveProfileSyncToken(token: String?) {
        val key = ProfileScopedKey.of(profileSyncTokenKey)
        if (token == null) profileSyncTokens.remove(key) else profileSyncTokens[key] = token.trim()
    }

    actual fun clearProfileSyncTokens() {
        profileSyncTokens.clear()
    }

    private const val profileSyncTokenKey = "discord_rpc_profile_sync_token"
}
