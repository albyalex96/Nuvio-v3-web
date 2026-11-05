package com.nuvio.app.features.opensubtitles

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object OpenSubtitlesStorage {
    private const val settingsKey = "opensubtitles_settings_payload"

    actual fun loadSettingsPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(settingsKey))

    actual fun saveSettingsPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(settingsKey))
    }
}
