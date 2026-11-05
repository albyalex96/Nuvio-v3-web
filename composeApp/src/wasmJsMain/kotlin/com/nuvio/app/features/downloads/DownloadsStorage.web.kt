package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object DownloadsStorage {
    private const val payloadKey = "downloads_payload"
    private const val seasonBatchesKey = "season_downloads_payload"
    private const val seasonConcurrencyKey = "season_downloads_concurrency"
    private const val organizeEpisodeFoldersKey = "downloads_organize_episode_folders"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }

    actual fun loadSeasonBatchesPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(seasonBatchesKey))

    actual fun saveSeasonBatchesPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(seasonBatchesKey))
    }

    actual fun loadSeasonConcurrency(): Int? {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(seasonConcurrencyKey) == null) return null
        return defaults.integerForKey(seasonConcurrencyKey).toInt()
    }

    actual fun saveSeasonConcurrency(value: Int) {
        NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), forKey = seasonConcurrencyKey)
    }

    actual fun loadOrganizeEpisodeFolders(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(organizeEpisodeFoldersKey) == null) return null
        return defaults.boolForKey(organizeEpisodeFoldersKey)
    }

    actual fun saveOrganizeEpisodeFolders(value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = organizeEpisodeFoldersKey)
    }
}
