package com.nuvio.app.features.anilist

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.DesktopSecretProtector
import com.nuvio.app.desktop.compat.SharedPreferences

import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AniListAuthStorage {
    private const val PREFERENCES_NAME = "nuvio_anilist_auth"
    private const val METADATA_KEY = "anilist_auth_metadata"
    private const val CREDENTIALS_KEY = "anilist_credentials"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadMetadataPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(METADATA_KEY), null)

    actual fun saveMetadataPayload(payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(METADATA_KEY), payload)?.apply()
    }

    actual fun loadCredentialsPayload(): String? = loadEncrypted(CREDENTIALS_KEY)

    actual fun saveCredentialsPayload(value: String?) = saveEncrypted(CREDENTIALS_KEY, value)

    actual fun removeProfile(profileId: Int) {
        preferences?.edit()
            ?.remove(ProfileScopedKey.of(METADATA_KEY, profileId))
            ?.remove(ProfileScopedKey.of(CREDENTIALS_KEY, profileId))
            ?.apply()
    }

    private fun loadEncrypted(key: String): String? {
        val scopedKey = ProfileScopedKey.of(key)
        val stored = preferences?.getString(scopedKey, null) ?: return null
        return runCatching { decrypt(stored) }
            .onFailure { preferences?.edit()?.remove(scopedKey)?.apply() }
            .getOrNull()
    }

    private fun saveEncrypted(key: String, value: String?) {
        val scopedKey = ProfileScopedKey.of(key)
        val editor = preferences?.edit() ?: return
        if (value.isNullOrBlank()) {
            editor.remove(scopedKey).apply()
        } else {
            editor.putString(scopedKey, encrypt(value)).apply()
        }
    }

    private fun encrypt(value: String): String = DesktopSecretProtector.protect(value)

    private fun decrypt(value: String): String = DesktopSecretProtector.unprotect(value)
}
