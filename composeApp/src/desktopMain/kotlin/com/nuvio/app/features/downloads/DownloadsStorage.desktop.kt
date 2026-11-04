package com.nuvio.app.features.downloads

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private const val preferencesName = "nuvio_downloads"
    private const val payloadKey = "downloads_payload"
    private const val seasonBatchesKey = "season_downloads_payload"
    private const val seasonConcurrencyKey = "season_downloads_concurrency"
    private const val organizeEpisodeFoldersKey = "downloads_organize_episode_folders"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }

    actual fun loadSeasonBatchesPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(seasonBatchesKey), null)

    actual fun saveSeasonBatchesPayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(seasonBatchesKey), payload)
            ?.apply()
    }

    actual fun loadSeasonConcurrency(): Int? =
        preferences?.takeIf { it.contains(seasonConcurrencyKey) }?.getInt(seasonConcurrencyKey, 0)

    actual fun saveSeasonConcurrency(value: Int) {
        preferences
            ?.edit()
            ?.putInt(seasonConcurrencyKey, value)
            ?.apply()
    }

    actual fun loadOrganizeEpisodeFolders(): Boolean? =
        preferences?.takeIf { it.contains(organizeEpisodeFoldersKey) }?.getBoolean(organizeEpisodeFoldersKey, true)

    actual fun saveOrganizeEpisodeFolders(value: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(organizeEpisodeFoldersKey, value)
            ?.apply()
    }
}
