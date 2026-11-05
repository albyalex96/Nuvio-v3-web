package com.nuvio.app.core.storage

import com.nuvio.app.web.compat.NSUserDefaults
import com.nuvio.app.web.compat.WebFileStore
import com.nuvio.app.features.profiles.MAX_PROFILES

internal actual object PlatformLocalAccountDataCleaner {
    private val plainKeys = listOf(
        "profile_payload",
        "avatar_catalog_payload",
        "anonymous_user_id",
        "member_access_payload",
    )
    private val profilePinCachePrefixes = listOf("profile_pin_cache_")
    private val profileIndexedPrefixes = listOf(
        "installed_manifest_urls_",
        "plugins_state_",
        "library_payload_",
        "watched_payload_",
        "watch_progress_payload_",
    )
    private val profileScopedBaseKeys = listOf(
        // OpenSubtitles (OpenSubtitlesStorage.ios.kt)
        "opensubtitles_settings_payload",
        // SubDL (SubDlStorage.ios.kt)
        "subdl_settings_payload",
        // AI assistant (AiAssistantStorage.ios.kt)
        "ai_assistant_settings_payload",
        // Top 10 home rows (Top10Storage.ios.kt)
        "home_top10_settings_payload",
        "episode_shuffle",
        "catalog_settings_payload",
        "discover_catalog_key",
        "continue_watching_preferences_payload",
        "poster_card_style_payload",
        "episode_release_notifications_payload",
        "episode_release_notification_scheduled_ids",
        "selected_theme",
        "amoled_enabled",
        "amoled_surfaces_enabled",
        "date_format",
        "top_nav_bar_enabled",
        "top_nav_bar_style",
        "episode_code_format",
        "show_loading_overlay",
        "preferred_audio_language",
        "secondary_preferred_audio_language",
        "preferred_subtitle_language",
        "secondary_preferred_subtitle_language",
        "subtitle_text_color",
        "subtitle_outline_enabled",
        "subtitle_font_size_sp",
        "subtitle_bottom_offset",
        "stream_reuse_last_link_enabled",
        "stream_reuse_last_link_cache_hours",
        "stream_badge_rules",
        "show_file_size_badges",
        "stream_badge_placement",
        "stream_parser_enabled",
        "stream_parser_animations_enabled",
        "stream_parser_sort_by_quality",
        "stream_parser_hidden_qualities",
        "debrid_stream_badge_rules",
        "p2p_enabled",
        "enable_upload",
        "hide_torrent_stats",
        "mdblist_enabled",
        "mdblist_api_key",
        "mdblist_use_imdb",
        "mdblist_use_tmdb",
        "mdblist_use_tomatoes",
        "mdblist_use_metacritic",
        "mdblist_use_trakt",
        "mdblist_use_letterboxd",
        "mdblist_use_audience",
        "mdblist_use_mal",
        "trakt_auth_payload",
        "simkl_auth_metadata",
        "simkl_sync_snapshot",
        "trakt_library_payload",
        "trakt_settings_payload",
        "library_display_settings_payload",
        "pending_watch_progress_source",
        "collection_mobile_settings_payload",
        "collections_payload",
        // Live TV (LiveTvStorage.ios.kt)
        "playlist_url",
        "playlists_blob",
        "favorite_channel_ids_blob",
        "last_watched_channel_id",
        "navigation_enabled",
        "stalker_settings",
        "xtream_settings",
        "epg_sources_blob",
        "epg_auto_detect_enabled",
        "channel_preferences",
        // Discord Rich Presence (DiscordRpcStorage.ios.kt)
        "discord_rpc_settings_payload",
        "discord_rpc_user_payload",
    )

    actual fun wipe() {
        val defaults = NSUserDefaults.standardUserDefaults

        plainKeys.forEach(defaults::removeObjectForKey)

        (1..MAX_PROFILES).forEach { profileId ->
            profileIndexedPrefixes.forEach { prefix ->
                defaults.removeObjectForKey("$prefix$profileId")
            }
            profilePinCachePrefixes.forEach { prefix ->
                defaults.removeObjectForKey("$prefix$profileId")
            }
            profileScopedBaseKeys.forEach { baseKey ->
                defaults.removeObjectForKey("${baseKey}_$profileId")
            }
        }

        for (key in defaults.dictionaryRepresentation().keys) {
            val keyString = key as? String ?: continue
            if (
                keyString.startsWith("stream_link_") ||
                keyString.startsWith("cw_enrichment_cache_")
            ) {
                defaults.removeObjectForKey(keyString)
            }
        }

        WebFileStore.deleteTree("nuvio_plugin_scrapers")
        WebFileStore.deleteTree("membership")
    }
}
