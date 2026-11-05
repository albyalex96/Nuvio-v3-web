package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object AnimeTrackingStorage {
    private const val excludedProvidersKey = "anime_tracking_excluded_providers"
    private const val libraryDestinationsKey = "anime_tracking_library_destinations"

    actual fun loadExcludedProviders(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(excludedProvidersKey))

    actual fun saveExcludedProviders(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(excludedProvidersKey))
    }

    actual fun loadLibraryDestinations(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(libraryDestinationsKey))

    actual fun saveLibraryDestinations(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(libraryDestinationsKey))
    }
}
