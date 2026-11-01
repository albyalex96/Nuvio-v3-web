package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_layout_settings_title
import nuvio.composeapp.generated.resources.tv_remote_settings_title
import nuvio.composeapp.generated.resources.live_tv_channels_title
import nuvio.composeapp.generated.resources.compose_settings_category_about
import nuvio.composeapp.generated.resources.media_proxy_title
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import nuvio.composeapp.generated.resources.compose_settings_category_general
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_cloudstream
import nuvio.composeapp.generated.resources.compose_settings_page_top10
import nuvio.composeapp.generated.resources.compose_settings_page_content_discovery
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.compose_settings_page_discord_rpc
import nuvio.composeapp.generated.resources.compose_settings_page_anime_skip
import nuvio.composeapp.generated.resources.compose_settings_page_introdb
import nuvio.composeapp.generated.resources.compose_settings_page_myanimelist
import nuvio.composeapp.generated.resources.compose_settings_page_skipdb
import nuvio.composeapp.generated.resources.compose_settings_page_aniskip
import nuvio.composeapp.generated.resources.compose_settings_page_kitsu
import nuvio.composeapp.generated.resources.compose_settings_page_anilist
import nuvio.composeapp.generated.resources.compose_settings_page_opensubtitles
import nuvio.composeapp.generated.resources.compose_settings_page_subdl
import nuvio.composeapp.generated.resources.compose_settings_page_ai_assistant
import nuvio.composeapp.generated.resources.compose_settings_page_telegram
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_live_tv
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_media_server
import nuvio.composeapp.generated.resources.compose_settings_page_media_servers
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.compose_settings_page_supporters_contributors
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.compose_settings_page_trakt
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.settings_account
import nuvio.composeapp.generated.resources.settings_app_logs_title
import org.jetbrains.compose.resources.StringResource

internal enum class SettingsCategory(
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    Account(Res.string.settings_account, Icons.Rounded.AccountCircle),
    General(Res.string.compose_settings_category_general, Icons.Rounded.Settings),
    About(Res.string.compose_settings_category_about, Icons.Rounded.Info),
    Advanced(Res.string.compose_settings_page_advanced, Icons.Rounded.Tune),
}

internal enum class SettingsPage(
    val titleRes: StringResource,
    val category: SettingsCategory,
    val parentPage: SettingsPage?,
) {
    Root(
        titleRes = Res.string.compose_settings_page_root,
        category = SettingsCategory.General,
        parentPage = null,
    ),
    Account(
        titleRes = Res.string.compose_settings_page_account,
        category = SettingsCategory.Account,
        parentPage = Root,
    ),
    SupportersContributors(
        titleRes = Res.string.compose_settings_page_supporters_contributors,
        category = SettingsCategory.About,
        parentPage = Root,
    ),
    LicensesAttributions(
        titleRes = Res.string.compose_settings_page_licenses_attributions,
        category = SettingsCategory.About,
        parentPage = Root,
    ),
    Playback(
        titleRes = Res.string.compose_settings_page_playback,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    Appearance(
        titleRes = Res.string.compose_settings_page_appearance,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    Streams(
        titleRes = Res.string.compose_settings_page_streams,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    Advanced(
        titleRes = Res.string.compose_settings_page_advanced,
        category = SettingsCategory.Advanced,
        parentPage = Root,
    ),
    AppLogs(
        titleRes = Res.string.settings_app_logs_title,
        category = SettingsCategory.Advanced,
        parentPage = Advanced,
    ),
    MediaProxy(
        titleRes = Res.string.media_proxy_title,
        category = SettingsCategory.Advanced,
        parentPage = Advanced,
    ),
    Downloads(
        titleRes = Res.string.compose_settings_root_downloads_title,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    Notifications(
        titleRes = Res.string.compose_settings_page_notifications,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    ContinueWatching(
        titleRes = Res.string.compose_settings_page_continue_watching,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    PosterCustomization(
        titleRes = Res.string.compose_settings_page_poster_customization,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    TvRemote(
        titleRes = Res.string.tv_remote_settings_title,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    TvLayout(
        titleRes = Res.string.tv_layout_settings_title,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    ContentDiscovery(
        titleRes = Res.string.compose_settings_page_content_discovery,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    Addons(
        titleRes = Res.string.compose_settings_page_addons,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    Plugins(
        titleRes = Res.string.compose_settings_page_plugins,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    MediaServers(
        titleRes = Res.string.compose_settings_page_media_servers,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    Cloudstream(
        titleRes = Res.string.compose_settings_page_cloudstream,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    Top10(
        titleRes = Res.string.compose_settings_page_top10,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    MediaServer(
        titleRes = Res.string.compose_settings_page_media_server,
        category = SettingsCategory.General,
        parentPage = MediaServers,
    ),
    Homescreen(
        titleRes = Res.string.compose_settings_page_homescreen,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    MetaScreen(
        titleRes = Res.string.compose_settings_page_meta_screen,
        category = SettingsCategory.General,
        parentPage = Appearance,
    ),
    Integrations(
        titleRes = Res.string.compose_settings_page_integrations,
        category = SettingsCategory.General,
        parentPage = Root,
    ),
    TmdbEnrichment(
        titleRes = Res.string.compose_settings_page_tmdb_enrichment,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    MdbListRatings(
        titleRes = Res.string.compose_settings_page_mdblist_ratings,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    Debrid(
        titleRes = Res.string.compose_settings_page_debrid,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    LiveTv(
        titleRes = Res.string.compose_settings_page_live_tv,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    LiveTvChannels(
        titleRes = Res.string.live_tv_channels_title,
        category = SettingsCategory.General,
        parentPage = LiveTv,
    ),
    DiscordRpc(
        titleRes = Res.string.compose_settings_page_discord_rpc,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    AnimeSkip(
        titleRes = Res.string.compose_settings_page_anime_skip,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    IntroDb(
        titleRes = Res.string.compose_settings_page_introdb,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    SkipDb(
        titleRes = Res.string.compose_settings_page_skipdb,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    AniSkip(
        titleRes = Res.string.compose_settings_page_aniskip,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    MyAnimeList(
        titleRes = Res.string.compose_settings_page_myanimelist,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    Kitsu(
        titleRes = Res.string.compose_settings_page_kitsu,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    AniList(
        titleRes = Res.string.compose_settings_page_anilist,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    OpenSubtitles(
        titleRes = Res.string.compose_settings_page_opensubtitles,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    SubDl(
        titleRes = Res.string.compose_settings_page_subdl,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    AiAssistant(
        titleRes = Res.string.compose_settings_page_ai_assistant,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    Telegram(
        titleRes = Res.string.compose_settings_page_telegram,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    TraktAuthentication(
        // Keep the enum name for saved navigation-state compatibility.
        titleRes = Res.string.compose_settings_page_tracking,
        category = SettingsCategory.Account,
        parentPage = Root,
    ),
}

internal val SettingsPage.opensInlineOnTablet: Boolean
    get() = parentPage != null

internal fun SettingsPage.previousPage(): SettingsPage? = parentPage
