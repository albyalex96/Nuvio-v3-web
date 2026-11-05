package com.nuvio.app.features.home

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object Top10Storage {
    private const val settingsKey = "home_top10_settings_payload"

    actual fun loadSettingsPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(settingsKey))

    actual fun saveSettingsPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(settingsKey))
    }
}
