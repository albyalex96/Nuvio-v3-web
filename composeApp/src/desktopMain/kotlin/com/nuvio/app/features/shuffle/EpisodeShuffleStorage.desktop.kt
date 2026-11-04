package com.nuvio.app.features.shuffle

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object EpisodeShuffleStorage {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("episode_shuffle", Context.MODE_PRIVATE)
    }

    actual fun load(profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of("episode_shuffle", profileId), null)

    actual fun save(profileId: Int, payload: String) {
        checkNotNull(preferences).edit()
            .putString(ProfileScopedKey.of("episode_shuffle", profileId), payload).apply()
    }
}
