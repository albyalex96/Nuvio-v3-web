package com.nuvio.app.features.ai

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AiAssistantStorage {
    private const val preferencesName = "nuvio_ai_assistant"
    private const val settingsKey = "settings_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSettingsPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(settingsKey), null)

    actual fun saveSettingsPayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(settingsKey), payload)
            ?.apply()
    }
}
