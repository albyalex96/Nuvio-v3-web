package com.nuvio.app.features.discord

import com.nuvio.app.core.storage.ProfileScopedKey

// Rich Presence is not wired on desktop yet: only the (synced) settings are kept, never the token.
internal actual object DiscordRpcStorage {
    private val prefs = com.nuvio.app.desktop.compat.DesktopContext.getSharedPreferences("nuvio_discord_rpc", 0)

    private const val settingsKey = "discord_rpc_settings_payload"
    private const val userKey = "discord_rpc_user_payload"

    actual fun loadSettingsPayload(): String? =
        prefs.getString(ProfileScopedKey.of(settingsKey), null)

    actual fun saveSettingsPayload(payload: String) {
        prefs.edit().putString(ProfileScopedKey.of(settingsKey), payload).apply()
    }

    actual fun loadUserPayload(): String? =
        prefs.getString(ProfileScopedKey.of(userKey), null)

    actual fun saveUserPayload(payload: String?) {
        val key = ProfileScopedKey.of(userKey)
        if (payload == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, payload).apply()
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
