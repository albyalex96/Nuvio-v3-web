package com.nuvio.app.features.streams.parser

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncStringSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object StreamParserSettingsStorage {
    // Shares the stream badge preferences file so the account data cleaner already wipes it.
    private const val preferencesName = "nuvio_stream_badge_settings"
    private val syncKeys = listOf(
        STREAM_PARSER_ENABLED_KEY,
        STREAM_PARSER_ANIMATIONS_ENABLED_KEY,
        STREAM_PARSER_SORT_BY_QUALITY_KEY,
        STREAM_PARSER_HIDDEN_QUALITIES_KEY,
        STREAM_PARSER_PLAYER_BADGES_KEY,
    )

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ENABLED_KEY)

    actual fun saveEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ENABLED_KEY, enabled)
    }

    actual fun loadAnimationsEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)

    actual fun saveAnimationsEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, enabled)
    }

    actual fun loadSortByQuality(): Boolean? = loadBoolean(STREAM_PARSER_SORT_BY_QUALITY_KEY)

    actual fun saveSortByQuality(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_SORT_BY_QUALITY_KEY, enabled)
    }

    actual fun loadPlayerBadgesEnabled(): Boolean? = loadBoolean(STREAM_PARSER_PLAYER_BADGES_KEY)

    actual fun savePlayerBadgesEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_PLAYER_BADGES_KEY, enabled)
    }

    actual fun loadHiddenQualities(): Set<String>? =
        preferences?.getStringSet(ProfileScopedKey.of(STREAM_PARSER_HIDDEN_QUALITIES_KEY), null)

    actual fun saveHiddenQualities(qualities: Set<String>) {
        preferences
            ?.edit()
            ?.putStringSet(ProfileScopedKey.of(STREAM_PARSER_HIDDEN_QUALITIES_KEY), qualities)
            ?.apply()
    }

    private fun loadBoolean(key: String): Boolean? =
        preferences?.let { sharedPreferences ->
            val scopedKey = ProfileScopedKey.of(key)
            if (sharedPreferences.contains(scopedKey)) {
                sharedPreferences.getBoolean(scopedKey, false)
            } else {
                null
            }
        }

    private fun saveBoolean(key: String, enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(key), enabled)
            ?.apply()
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(STREAM_PARSER_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadAnimationsEnabled()?.let { put(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadSortByQuality()?.let { put(STREAM_PARSER_SORT_BY_QUALITY_KEY, encodeSyncBoolean(it)) }
        loadPlayerBadgesEnabled()?.let { put(STREAM_PARSER_PLAYER_BADGES_KEY, encodeSyncBoolean(it)) }
        loadHiddenQualities()?.let { put(STREAM_PARSER_HIDDEN_QUALITIES_KEY, encodeSyncStringSet(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            syncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncBoolean(STREAM_PARSER_ENABLED_KEY)?.let(::saveEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)?.let(::saveAnimationsEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_SORT_BY_QUALITY_KEY)?.let(::saveSortByQuality)
        payload.decodeSyncBoolean(STREAM_PARSER_PLAYER_BADGES_KEY)?.let(::savePlayerBadgesEnabled)
        payload.decodeSyncStringSet(STREAM_PARSER_HIDDEN_QUALITIES_KEY)?.let(::saveHiddenQualities)
    }
}
