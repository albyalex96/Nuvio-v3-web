package com.nuvio.app.features.player.skip

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object TimestampProviderStorage {
    private const val settingsKey = "timestamp_provider_settings"

    actual fun loadSettings(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(settingsKey))

    actual fun saveSettings(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(settingsKey))
    }
}
