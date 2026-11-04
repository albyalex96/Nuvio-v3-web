package com.nuvio.app.features.player.skip

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object TimestampProviderStorage {
    private const val preferencesName = "nuvio_timestamp_providers"
    private const val settingsKey = "settings"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSettings(): String? =
        preferences?.getString(ProfileScopedKey.of(settingsKey), null)

    actual fun saveSettings(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(settingsKey), payload)
            ?.apply()
    }
}
