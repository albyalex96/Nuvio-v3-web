package com.nuvio.app.features.anime

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AnimeTrackingStorage {
    private const val preferencesName = "nuvio_anime_tracking"
    private const val excludedProvidersKey = "excluded_providers"
    private const val libraryDestinationsKey = "library_destinations"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadExcludedProviders(): String? =
        preferences?.getString(ProfileScopedKey.of(excludedProvidersKey), null)

    actual fun saveExcludedProviders(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(excludedProvidersKey), payload)
            ?.apply()
    }

    actual fun loadLibraryDestinations(): String? =
        preferences?.getString(ProfileScopedKey.of(libraryDestinationsKey), null)

    actual fun saveLibraryDestinations(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(libraryDestinationsKey), payload)
            ?.apply()
    }
}
