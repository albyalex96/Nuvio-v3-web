package com.nuvio.app.features.streams.parser

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncStringSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object StreamParserSettingsStorage {
    private val syncKeys = listOf(
        STREAM_PARSER_ENABLED_KEY,
        STREAM_PARSER_ANIMATIONS_ENABLED_KEY,
        STREAM_PARSER_SORT_BY_QUALITY_KEY,
        STREAM_PARSER_HIDDEN_QUALITIES_KEY,
        STREAM_PARSER_PLAYER_BADGES_KEY,
    )

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

    @Suppress("UNCHECKED_CAST")
    actual fun loadHiddenQualities(): Set<String>? {
        val array = NSUserDefaults.standardUserDefaults.arrayForKey(
            ProfileScopedKey.of(STREAM_PARSER_HIDDEN_QUALITIES_KEY)
        ) as? List<String> ?: return null
        return array.toSet()
    }

    actual fun saveHiddenQualities(qualities: Set<String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            qualities.toList(),
            forKey = ProfileScopedKey.of(STREAM_PARSER_HIDDEN_QUALITIES_KEY),
        )
    }

    private fun loadBoolean(key: String): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val scopedKey = ProfileScopedKey.of(key)
        return if (defaults.objectForKey(scopedKey) != null) {
            defaults.boolForKey(scopedKey)
        } else {
            null
        }
    }

    private fun saveBoolean(key: String, enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(key))
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(STREAM_PARSER_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadAnimationsEnabled()?.let { put(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadSortByQuality()?.let { put(STREAM_PARSER_SORT_BY_QUALITY_KEY, encodeSyncBoolean(it)) }
        loadPlayerBadgesEnabled()?.let { put(STREAM_PARSER_PLAYER_BADGES_KEY, encodeSyncBoolean(it)) }
        loadHiddenQualities()?.let { put(STREAM_PARSER_HIDDEN_QUALITIES_KEY, encodeSyncStringSet(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys.forEach { key ->
            NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
        }

        payload.decodeSyncBoolean(STREAM_PARSER_ENABLED_KEY)?.let(::saveEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)?.let(::saveAnimationsEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_SORT_BY_QUALITY_KEY)?.let(::saveSortByQuality)
        payload.decodeSyncBoolean(STREAM_PARSER_PLAYER_BADGES_KEY)?.let(::savePlayerBadgesEnabled)
        payload.decodeSyncStringSet(STREAM_PARSER_HIDDEN_QUALITIES_KEY)?.let(::saveHiddenQualities)
    }
}
