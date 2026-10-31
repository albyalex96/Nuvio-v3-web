package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.downloads.downloadsPowerSettingsSupported
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.isIos
import com.nuvio.app.tv.LocalTvShell
import com.nuvio.app.features.discord.DiscordRpcPlatform
import com.nuvio.app.features.telegram.TelegramSourceResolver
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.features.ai.AiProvider
import com.nuvio.app.supportsPosterNavigationMotion
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.features.updater.AppUpdaterPlatform
import com.nuvio.app.features.updater.InstalledReleaseNotes
import com.nuvio.app.features.player.seekthumb.seekThumbnailsSupported
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal sealed class SettingsSearchTarget {
    data class Page(val page: SettingsPage) : SettingsSearchTarget()
    object Collections : SettingsSearchTarget()
    object WatchParty : SettingsSearchTarget()
    object SwitchProfile : SettingsSearchTarget()
    object CheckForUpdates : SettingsSearchTarget()
    object InstalledReleaseNotes : SettingsSearchTarget()
}

internal data class SettingsSearchEntry(
    val key: String,
    val title: String,
    val description: String,
    val page: String,
    val section: String,
    val category: String,
    val icon: ImageVector,
    val target: SettingsSearchTarget,
) {
    val searchableText: String = normalizeSettingsSearchText(
        listOf(title, description, page, section, category).joinToString(separator = " "),
    )

    val normalizedTitle: String = normalizeSettingsSearchText(title)

    val contextLabel: String = listOf(page, section)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(separator = " - ")
}

@Composable
internal fun settingsSearchEntries(
    isTablet: Boolean,
    pluginsEnabled: Boolean,
    supportersContributorsPageEnabled: Boolean,
    accountDeletionEnabled: Boolean,
    personalMediaAddonCopyEnabled: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    switchProfileAvailable: Boolean,
    checkForUpdatesAvailable: Boolean,
    watchPartyAvailable: Boolean = false,
): List<SettingsSearchEntry> {
    val accountCategory = stringResource(SettingsCategory.Account.labelRes)
    val generalCategory = stringResource(SettingsCategory.General.labelRes)
    val aboutCategory = stringResource(SettingsCategory.About.labelRes)
    val advancedCategory = stringResource(SettingsCategory.Advanced.labelRes)

    val accountPage = stringResource(Res.string.compose_settings_page_account)
    val trackingPage = stringResource(Res.string.compose_settings_page_tracking)
    val layoutPage = stringResource(Res.string.compose_settings_page_appearance)
    val advancedPage = stringResource(Res.string.compose_settings_page_advanced)
    val contentDiscoveryPage = stringResource(Res.string.compose_settings_page_content_discovery)
    val playbackPage = stringResource(Res.string.compose_settings_page_playback)
    val streamsPage = stringResource(Res.string.compose_settings_page_streams)
    val integrationsPage = stringResource(Res.string.compose_settings_page_integrations)
    val notificationsPage = stringResource(Res.string.compose_settings_page_notifications)
    val supportersPage = stringResource(Res.string.compose_settings_page_supporters_contributors)
    val licensesPage = stringResource(Res.string.compose_settings_page_licenses_attributions)
    val homeLayoutPage = stringResource(Res.string.compose_settings_page_homescreen)
    val detailPage = stringResource(Res.string.compose_settings_page_meta_screen)
    val continueWatchingPage = stringResource(Res.string.compose_settings_page_continue_watching)
    val posterStylePage = stringResource(Res.string.compose_settings_page_poster_customization)
    val addonsPage = stringResource(Res.string.compose_settings_page_addons)
    val pluginsPage = stringResource(Res.string.compose_settings_page_plugins)
    val collectionsPage = stringResource(Res.string.collections_header)
    val tmdbPage = stringResource(Res.string.compose_settings_page_tmdb_enrichment)
    val mdbListPage = stringResource(Res.string.compose_settings_page_mdblist_ratings)

    val entries = mutableListOf<SettingsSearchEntry>()

    fun add(
        key: String,
        title: String,
        description: String = "",
        page: String = title,
        section: String = "",
        category: String = generalCategory,
        icon: ImageVector,
        target: SettingsSearchTarget,
    ) {
        entries += SettingsSearchEntry(
            key = key,
            title = title,
            description = description,
            page = page,
            section = section,
            category = category,
            icon = icon,
            target = target,
        )
    }

    fun addPage(
        page: SettingsPage,
        key: String,
        title: String,
        description: String,
        category: String = generalCategory,
        icon: ImageVector,
    ) {
        add(
            key = key,
            title = title,
            description = description,
            page = title,
            category = category,
            icon = icon,
            target = SettingsSearchTarget.Page(page),
        )
    }

    fun addRow(
        page: SettingsPage,
        key: String,
        title: String,
        description: String = "",
        pageLabel: String,
        section: String,
        category: String = generalCategory,
        icon: ImageVector,
    ) {
        add(
            key = key,
            title = title,
            description = description,
            page = pageLabel,
            section = section,
            category = category,
            icon = icon,
            target = SettingsSearchTarget.Page(page),
        )
    }

    if (switchProfileAvailable) {
        add(
            key = "switch-profile",
            title = stringResource(Res.string.compose_settings_root_switch_profile_title),
            description = stringResource(Res.string.compose_settings_root_switch_profile_description),
            page = accountPage,
            section = stringResource(Res.string.compose_settings_root_account_section),
            category = accountCategory,
            icon = Icons.Rounded.People,
            target = SettingsSearchTarget.SwitchProfile,
        )
    }
    addPage(
        page = SettingsPage.Account,
        key = "account",
        title = accountPage,
        description = stringResource(Res.string.compose_settings_root_account_description),
        category = accountCategory,
        icon = Icons.Rounded.AccountCircle,
    )
    if (watchPartyAvailable) {
        add(
            key = "watch-party",
            title = stringResource(Res.string.party_title),
            description = stringResource(Res.string.party_settings_description),
            page = accountPage,
            section = stringResource(Res.string.compose_settings_root_account_section),
            category = accountCategory,
            icon = Icons.Rounded.Groups,
            target = SettingsSearchTarget.WatchParty,
        )
    }
    addPage(
        page = SettingsPage.TraktAuthentication,
        key = "tracking",
        title = trackingPage,
        description = stringResource(Res.string.compose_settings_root_tracking_description),
        category = accountCategory,
        icon = Icons.Default.Sync,
    )
    addPage(
        page = SettingsPage.Appearance,
        key = "layout",
        title = layoutPage,
        description = stringResource(Res.string.compose_settings_root_appearance_description),
        icon = Icons.Rounded.Palette,
    )
    addPage(
        page = SettingsPage.Advanced,
        key = "advanced",
        title = advancedPage,
        description = stringResource(Res.string.compose_settings_root_advanced_description),
        category = advancedCategory,
        icon = Icons.Rounded.Tune,
    )
    addPage(
        page = SettingsPage.ContentDiscovery,
        key = "content-discovery",
        title = contentDiscoveryPage,
        description = stringResource(Res.string.compose_settings_root_content_discovery_description),
        icon = Icons.Rounded.Extension,
    )
    addRow(
        page = SettingsPage.ContentDiscovery,
        key = "recent-searches",
        title = stringResource(Res.string.settings_content_discovery_recent_searches),
        description = stringResource(Res.string.settings_content_discovery_recent_searches_description),
        pageLabel = contentDiscoveryPage,
        section = stringResource(Res.string.settings_content_discovery_section_search),
        icon = Icons.Rounded.Search,
    )
    addPage(
        page = SettingsPage.Playback,
        key = "playback",
        title = playbackPage,
        description = stringResource(Res.string.settings_playback_subtitle),
        icon = Icons.Rounded.PlayArrow,
    )
    addPage(
        page = SettingsPage.Streams,
        key = "streams",
        title = streamsPage,
        description = stringResource(Res.string.compose_settings_root_streams_description),
        icon = Icons.Rounded.Style,
    )
    addPage(
        page = SettingsPage.Integrations,
        key = "integrations",
        title = integrationsPage,
        description = stringResource(Res.string.compose_settings_root_integrations_description),
        icon = Icons.Rounded.Link,
    )
    addPage(
        page = SettingsPage.Notifications,
        key = "notifications",
        title = notificationsPage,
        description = stringResource(Res.string.compose_settings_root_notifications_description),
        icon = Icons.Rounded.Notifications,
    )
    if (supportersContributorsPageEnabled) {
        addPage(
            page = SettingsPage.SupportersContributors,
            key = "supporters",
            title = supportersPage,
            description = stringResource(Res.string.about_supporters_contributors_subtitle),
            category = aboutCategory,
            icon = Icons.Rounded.Favorite,
        )
    }
    addPage(
        page = SettingsPage.LicensesAttributions,
        key = "licenses-attributions",
        title = licensesPage,
        description = stringResource(Res.string.about_licenses_attributions_subtitle),
        category = aboutCategory,
        icon = Icons.Rounded.Info,
    )
    listOfNotNull(
        PlaybackSearchRow("nuvio-license", stringResource(Res.string.settings_licenses_attributions_nuvio_title), stringResource(Res.string.settings_licenses_attributions_nuvio_license)),
        PlaybackSearchRow("tmdb-attribution", stringResource(Res.string.settings_licenses_attributions_tmdb_title), stringResource(Res.string.settings_licenses_attributions_tmdb_body)),
        PlaybackSearchRow("trakt-attribution", stringResource(Res.string.settings_licenses_attributions_trakt_title), stringResource(Res.string.settings_licenses_attributions_trakt_body)),
        PlaybackSearchRow("simkl-attribution", stringResource(Res.string.settings_licenses_attributions_simkl_title), stringResource(Res.string.settings_licenses_attributions_simkl_body)),
        PlaybackSearchRow("premiumize-attribution", stringResource(Res.string.settings_licenses_attributions_premiumize_title), stringResource(Res.string.settings_licenses_attributions_premiumize_body)),
        PlaybackSearchRow("torbox-attribution", stringResource(Res.string.settings_licenses_attributions_torbox_title), stringResource(Res.string.settings_licenses_attributions_torbox_body)),
        PlaybackSearchRow("mdblist-attribution", stringResource(Res.string.settings_licenses_attributions_mdblist_title), stringResource(Res.string.settings_licenses_attributions_mdblist_body)),
        PlaybackSearchRow("introdb-attribution", stringResource(Res.string.settings_licenses_attributions_introdb_title), stringResource(Res.string.settings_licenses_attributions_introdb_body)),
        PlaybackSearchRow("skipdb-attribution", stringResource(Res.string.settings_licenses_attributions_skipdb_title), stringResource(Res.string.settings_licenses_attributions_skipdb_body)),
        PlaybackSearchRow("imdb-datasets", stringResource(Res.string.settings_licenses_attributions_imdb_title), stringResource(Res.string.settings_licenses_attributions_imdb_body)),
        PlaybackSearchRow("myanimelist-attribution", stringResource(Res.string.settings_licenses_attributions_myanimelist_title), stringResource(Res.string.settings_licenses_attributions_myanimelist_body)),
        PlaybackSearchRow("kitsu-attribution", stringResource(Res.string.settings_licenses_attributions_kitsu_title), stringResource(Res.string.settings_licenses_attributions_kitsu_body)),
        PlaybackSearchRow("anilist-attribution", stringResource(Res.string.settings_licenses_attributions_anilist_title), stringResource(Res.string.settings_licenses_attributions_anilist_body)),
        PlaybackSearchRow("fribb-attribution", stringResource(Res.string.settings_licenses_attributions_fribb_title), stringResource(Res.string.settings_licenses_attributions_fribb_body)),
        PlaybackSearchRow("kometa-attribution", stringResource(Res.string.settings_licenses_attributions_kometa_title), stringResource(Res.string.settings_licenses_attributions_kometa_body)),
        PlaybackSearchRow("animeskip-attribution", stringResource(Res.string.settings_licenses_attributions_animeskip_title), stringResource(Res.string.settings_licenses_attributions_animeskip_body)),
        PlaybackSearchRow("aniskip-attribution", stringResource(Res.string.settings_licenses_attributions_aniskip_title), stringResource(Res.string.settings_licenses_attributions_aniskip_body)),
        PlaybackSearchRow("aniskip-logo-license", stringResource(Res.string.settings_licenses_attributions_aniskip_logo_title), stringResource(Res.string.settings_licenses_attributions_aniskip_logo_body) + " " + stringResource(Res.string.settings_licenses_attributions_aniskip_logo_license)),
        PlaybackSearchRow("opensubtitles-attribution", stringResource(Res.string.settings_licenses_attributions_opensubtitles_title), stringResource(Res.string.settings_licenses_attributions_opensubtitles_body)),
        PlaybackSearchRow("subdl-attribution", stringResource(Res.string.settings_licenses_attributions_subdl_title), stringResource(Res.string.settings_licenses_attributions_subdl_body)),
        PlaybackSearchRow("cloudstream-attribution", stringResource(Res.string.settings_licenses_attributions_cloudstream_title), stringResource(Res.string.settings_licenses_attributions_cloudstream_body))
            .takeIf { AppFeaturePolicy.pluginsEnabled },
        PlaybackSearchRow("discord-attribution", stringResource(Res.string.settings_licenses_attributions_discord_title), stringResource(Res.string.settings_licenses_attributions_discord_body))
            .takeIf { DiscordRpcPlatform.isSupported },
        PlaybackSearchRow("telegram-attribution", stringResource(Res.string.settings_licenses_attributions_telegram_title), stringResource(Res.string.settings_licenses_attributions_telegram_body))
            .takeIf { TelegramSourceResolver.isSupported },
        PlaybackSearchRow("tdlib-license", stringResource(Res.string.settings_licenses_attributions_tdlib_title), stringResource(Res.string.settings_licenses_attributions_tdlib_body))
            .takeIf { TelegramSourceResolver.isSupported },
        PlaybackSearchRow("doh-attribution", stringResource(Res.string.settings_licenses_attributions_doh_title), stringResource(Res.string.settings_licenses_attributions_doh_body))
            .takeIf { dnsOverHttpsSettingsVisible },
        PlaybackSearchRow("okhttp-license", stringResource(Res.string.settings_licenses_attributions_okhttp_title), stringResource(Res.string.settings_licenses_attributions_okhttp_license))
            .takeIf { dnsOverHttpsSettingsVisible },
        PlaybackSearchRow("nuvio-arc-license", stringResource(Res.string.settings_licenses_attributions_nuvio_arc_title), stringResource(Res.string.settings_licenses_attributions_nuvio_arc_body))
            .takeIf { seekThumbnailsSupported },
        PlaybackSearchRow("cerebras-attribution", stringResource(Res.string.settings_licenses_attributions_cerebras_title), stringResource(Res.string.settings_licenses_attributions_cerebras_body)),
        PlaybackSearchRow("groq-attribution", stringResource(Res.string.settings_licenses_attributions_groq_title), stringResource(Res.string.settings_licenses_attributions_groq_body)),
        PlaybackSearchRow("gemini-attribution", stringResource(Res.string.settings_licenses_attributions_gemini_title), stringResource(Res.string.settings_licenses_attributions_gemini_body)),
        PlaybackSearchRow("openrouter-attribution", stringResource(Res.string.settings_licenses_attributions_openrouter_title), stringResource(Res.string.settings_licenses_attributions_openrouter_body)),
        PlaybackSearchRow("tavily-attribution", stringResource(Res.string.settings_licenses_attributions_tavily_title), stringResource(Res.string.settings_licenses_attributions_tavily_body)),
        PlaybackSearchRow(
            if (isIos) "mpvkit-license" else "exoplayer-license",
            if (isIos) {
                stringResource(Res.string.settings_licenses_attributions_mpvkit_title)
            } else {
                stringResource(Res.string.settings_licenses_attributions_exoplayer_title)
            },
            if (isIos) {
                stringResource(Res.string.settings_licenses_attributions_mpvkit_license)
            } else {
                stringResource(Res.string.settings_licenses_attributions_exoplayer_license)
            },
        ),
    ).forEach { row ->
        addRow(
            page = SettingsPage.LicensesAttributions,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = licensesPage,
            section = stringResource(Res.string.compose_settings_root_about_section),
            category = aboutCategory,
            icon = Icons.Rounded.Info,
        )
    }
    if (checkForUpdatesAvailable) {
        add(
            key = "check-updates",
            title = stringResource(Res.string.compose_settings_root_check_updates_title),
            description = stringResource(Res.string.compose_settings_root_check_updates_description),
            page = if (supportersContributorsPageEnabled) supportersPage else licensesPage,
            section = stringResource(Res.string.compose_settings_root_about_section),
            category = aboutCategory,
            icon = Icons.Rounded.CloudDownload,
            target = SettingsSearchTarget.CheckForUpdates,
        )
        if (InstalledReleaseNotes.isAvailable) {
            add(
                key = "installed-release-notes",
                title = stringResource(Res.string.updates_installed_changelog_title),
                description = stringResource(
                    Res.string.updates_installed_changelog_description,
                    InstalledReleaseNotes.installedVersion,
                ),
                page = if (supportersContributorsPageEnabled) supportersPage else licensesPage,
                section = stringResource(Res.string.compose_settings_root_about_section),
                category = aboutCategory,
                icon = Icons.Rounded.NewReleases,
                target = SettingsSearchTarget.InstalledReleaseNotes,
            )
        }
    }

    addRow(
        page = SettingsPage.Account,
        key = "account-status",
        title = stringResource(Res.string.settings_account_status),
        pageLabel = accountPage,
        section = accountPage,
        category = accountCategory,
        icon = Icons.Rounded.AccountCircle,
    )
    addRow(
        page = SettingsPage.Account,
        key = "account-sign-out",
        title = stringResource(Res.string.settings_account_sign_out),
        pageLabel = accountPage,
        section = accountPage,
        category = accountCategory,
        icon = Icons.Rounded.AccountCircle,
    )
    if (accountDeletionEnabled) {
        addRow(
            page = SettingsPage.Account,
            key = "account-delete",
            title = stringResource(Res.string.settings_account_delete_account),
            description = stringResource(Res.string.settings_account_delete_account_description),
            pageLabel = accountPage,
            section = accountPage,
            category = accountCategory,
            icon = Icons.Rounded.AccountCircle,
        )
    }

    addRow(
        page = SettingsPage.Appearance,
        key = "theme",
        title = stringResource(Res.string.settings_appearance_section_theme),
        pageLabel = layoutPage,
        section = stringResource(Res.string.settings_appearance_section_theme),
        icon = Icons.Rounded.Palette,
    )
    addRow(
        page = SettingsPage.Appearance,
        key = "amoled",
        title = stringResource(Res.string.settings_appearance_amoled_black),
        description = stringResource(Res.string.settings_appearance_amoled_description),
        pageLabel = layoutPage,
        section = stringResource(Res.string.settings_appearance_section_display),
        icon = Icons.Rounded.Palette,
    )
    addRow(
        page = SettingsPage.Appearance,
        key = "amoled-surfaces",
        title = stringResource(Res.string.settings_appearance_amoled_surfaces),
        description = stringResource(Res.string.settings_appearance_amoled_surfaces_description),
        pageLabel = layoutPage,
        section = stringResource(Res.string.settings_appearance_section_display),
        icon = Icons.Rounded.Palette,
    )
    if (liquidGlassNativeTabBarSupported) {
        addRow(
            page = SettingsPage.Appearance,
            key = "liquid-glass",
            title = stringResource(Res.string.settings_appearance_liquid_glass),
            description = stringResource(Res.string.settings_appearance_liquid_glass_description),
            pageLabel = layoutPage,
            section = stringResource(Res.string.settings_appearance_section_display),
            icon = Icons.Rounded.Palette,
        )
    }
    addRow(
        page = SettingsPage.Appearance,
        key = "app-language",
        title = stringResource(Res.string.settings_appearance_app_language),
        pageLabel = layoutPage,
        section = stringResource(Res.string.settings_appearance_section_display),
        icon = Icons.Rounded.Language,
    )
    addRow(
        page = SettingsPage.Advanced,
        key = "remember-last-profile",
        title = stringResource(Res.string.settings_advanced_remember_last_profile),
        description = stringResource(Res.string.settings_advanced_remember_last_profile_description),
        pageLabel = advancedPage,
        section = stringResource(Res.string.settings_advanced_section_startup),
        category = advancedCategory,
        icon = Icons.Rounded.Tune,
    )
    addPage(
        page = SettingsPage.AppLogs,
        key = "app-logs",
        title = stringResource(Res.string.settings_app_logs_title),
        description = stringResource(Res.string.settings_app_logs_entry_description),
        category = advancedCategory,
        icon = Icons.Rounded.Tune,
    )
    if (AppFeaturePolicy.inAppUpdaterEnabled && AppUpdaterPlatform.isSupported) {
        addRow(
            page = SettingsPage.Advanced,
            key = "auto-download-updates",
            title = stringResource(Res.string.settings_advanced_auto_download_updates),
            description = stringResource(Res.string.settings_advanced_auto_download_updates_description),
            pageLabel = advancedPage,
            section = stringResource(Res.string.settings_advanced_section_updates),
            category = advancedCategory,
            icon = Icons.Rounded.Tune,
        )
    }
    if (SentrySettingsRepository.isSupported) {
        addRow(
            page = SettingsPage.Advanced,
            key = "sentry-crash-reports",
            title = stringResource(Res.string.settings_advanced_sentry_reports),
            description = stringResource(Res.string.settings_advanced_sentry_reports_subtitle),
            pageLabel = advancedPage,
            section = stringResource(Res.string.settings_advanced_section_diagnostics),
            category = advancedCategory,
            icon = Icons.Rounded.Tune,
        )
    }
    if (dnsOverHttpsSettingsVisible) {
        val networkSection = stringResource(Res.string.settings_advanced_section_network)
        addRow(
            page = SettingsPage.Advanced,
            key = "dns-over-https",
            title = stringResource(Res.string.settings_network_doh_title),
            description = stringResource(Res.string.settings_network_doh_description),
            pageLabel = advancedPage,
            section = networkSection,
            category = advancedCategory,
            icon = Icons.Rounded.Tune,
        )
        addRow(
            page = SettingsPage.Advanced,
            key = "dns-over-https-fallback",
            title = stringResource(Res.string.settings_network_doh_fallback_title),
            description = stringResource(Res.string.settings_network_doh_fallback_description),
            pageLabel = advancedPage,
            section = networkSection,
            category = advancedCategory,
            icon = Icons.Rounded.Tune,
        )
        addRow(
            page = SettingsPage.Advanced,
            key = "dns-over-https-sync",
            title = stringResource(Res.string.settings_network_doh_sync_title),
            description = stringResource(Res.string.settings_network_doh_sync_description),
            pageLabel = advancedPage,
            section = networkSection,
            category = advancedCategory,
            icon = Icons.Rounded.Tune,
        )
    }
    addRow(
        page = SettingsPage.Advanced,
        key = "clear-cw-cache",
        title = stringResource(Res.string.settings_advanced_clear_cw_cache),
        description = stringResource(Res.string.settings_advanced_clear_cw_cache_subtitle),
        pageLabel = advancedPage,
        section = stringResource(Res.string.settings_advanced_section_cache),
        category = advancedCategory,
        icon = Icons.Rounded.Tune,
    )
    addPage(
        page = SettingsPage.ContinueWatching,
        key = "continue-watching",
        title = continueWatchingPage,
        description = stringResource(Res.string.settings_appearance_continue_watching_description),
        icon = Icons.Rounded.Style,
    )
    addPage(
        page = SettingsPage.PosterCustomization,
        key = "poster-card-style",
        title = posterStylePage,
        description = stringResource(Res.string.settings_appearance_poster_customization_description),
        icon = Icons.Rounded.Tune,
    )

    addPage(
        page = SettingsPage.Addons,
        key = "addons",
        title = addonsPage,
        description = stringResource(
            if (personalMediaAddonCopyEnabled) {
                Res.string.settings_content_discovery_addons_description_appstore
            } else {
                Res.string.settings_content_discovery_addons_description
            },
        ),
        icon = Icons.Rounded.Extension,
    )
    if (pluginsEnabled) {
        addPage(
            page = SettingsPage.Plugins,
            key = "plugins",
            title = pluginsPage,
            description = stringResource(Res.string.settings_content_discovery_plugins_description),
            icon = Icons.Rounded.Hub,
        )
    }
    if (pluginsEnabled) {
        val cloudStreamPage = stringResource(Res.string.compose_settings_page_cloudstream)
        addPage(
            page = SettingsPage.Cloudstream,
            key = "cloudstream",
            title = cloudStreamPage,
            description = stringResource(Res.string.settings_content_discovery_cloudstream_description),
            icon = Icons.Rounded.Extension,
        )
        // The CloudStream page is a custom layout (not *Row calls): its entries are listed by hand.
        val cloudStreamOverview = stringResource(Res.string.cloudstream_section_overview)
        val cloudStreamPlugins = stringResource(Res.string.cloudstream_section_plugins)
        addRow(
            page = SettingsPage.Cloudstream,
            key = "cloudstream_group_by_repo",
            title = stringResource(Res.string.cloudstream_group_by_repo_title),
            description = stringResource(Res.string.cloudstream_group_by_repo_desc),
            pageLabel = cloudStreamPage,
            section = cloudStreamOverview,
            icon = Icons.Rounded.Extension,
        )
        addRow(
            page = SettingsPage.Cloudstream,
            key = "cloudstream_full_details",
            title = stringResource(Res.string.cloudstream_full_details_title),
            description = stringResource(Res.string.cloudstream_full_details_desc),
            pageLabel = cloudStreamPage,
            section = cloudStreamOverview,
            icon = Icons.Rounded.Extension,
        )
        addRow(
            page = SettingsPage.Cloudstream,
            key = "cloudstream_add_repo",
            title = stringResource(Res.string.cloudstream_section_add_repo),
            description = stringResource(Res.string.cloudstream_empty_repos_subtitle),
            pageLabel = cloudStreamPage,
            section = stringResource(Res.string.cloudstream_section_repos),
            icon = Icons.Rounded.Extension,
        )
        addRow(
            page = SettingsPage.Cloudstream,
            key = "cloudstream_install_all",
            title = stringResource(Res.string.cloudstream_install_all_title),
            description = stringResource(Res.string.cloudstream_install_all_desc),
            pageLabel = cloudStreamPage,
            section = cloudStreamPlugins,
            icon = Icons.Rounded.Extension,
        )
        addRow(
            page = SettingsPage.Cloudstream,
            key = "cloudstream_import_catalogs",
            title = stringResource(Res.string.cloudstream_import_catalogs_title),
            description = stringResource(Res.string.cloudstream_import_catalogs_desc),
            pageLabel = cloudStreamPage,
            section = cloudStreamPlugins,
            icon = Icons.Rounded.Extension,
        )
    }
    val top10Page = stringResource(Res.string.compose_settings_page_top10)
    addPage(
        page = SettingsPage.Top10,
        key = "top10",
        title = top10Page,
        description = stringResource(Res.string.top10_settings_description),
        icon = Icons.Rounded.Leaderboard,
    )
    addRow(
        page = SettingsPage.Top10,
        key = "top10-enabled",
        title = stringResource(Res.string.top10_enabled),
        description = stringResource(Res.string.top10_enabled_description),
        pageLabel = top10Page,
        section = stringResource(Res.string.top10_section_general),
        icon = Icons.Rounded.Leaderboard,
    )
    addRow(
        page = SettingsPage.Top10,
        key = "top10-movies-catalog",
        title = stringResource(Res.string.top10_movies_catalog),
        description = stringResource(Res.string.top10_movies_catalog_description),
        pageLabel = top10Page,
        section = stringResource(Res.string.top10_section_catalogs),
        icon = Icons.Rounded.Leaderboard,
    )
    addRow(
        page = SettingsPage.Top10,
        key = "top10-series-catalog",
        title = stringResource(Res.string.top10_series_catalog),
        description = stringResource(Res.string.top10_series_catalog_description),
        pageLabel = top10Page,
        section = stringResource(Res.string.top10_section_catalogs),
        icon = Icons.Rounded.Leaderboard,
    )
    addPage(
        page = SettingsPage.MediaServers,
        key = "media-servers",
        title = stringResource(Res.string.compose_settings_page_media_servers),
        description = stringResource(Res.string.settings_content_discovery_media_servers_description),
        icon = Icons.Rounded.Dns,
    )
    addPage(
        page = SettingsPage.Homescreen,
        key = "home-layout",
        title = homeLayoutPage,
        description = stringResource(Res.string.settings_content_discovery_homescreen_description),
        icon = Icons.Rounded.Home,
    )
    addPage(
        page = SettingsPage.MetaScreen,
        key = "detail-page",
        title = detailPage,
        description = stringResource(Res.string.settings_content_discovery_meta_screen_description),
        icon = Icons.Rounded.Tune,
    )
    add(
        key = "collections",
        title = collectionsPage,
        description = stringResource(Res.string.settings_content_discovery_collections_description),
        page = layoutPage,
        section = stringResource(Res.string.settings_appearance_section_home),
        category = generalCategory,
        icon = Icons.Rounded.CollectionsBookmark,
        target = SettingsSearchTarget.Collections,
    )

    val playbackPlayer = stringResource(Res.string.settings_playback_section_player)
    val playbackSubtitleAudio = stringResource(Res.string.settings_playback_section_subtitle_audio)
    val playbackStreamSelection = stringResource(Res.string.settings_playback_section_stream_selection)
    val playbackStreamAutoPlay = stringResource(Res.string.settings_playback_section_stream_auto_play)
    val playbackDecoder = stringResource(Res.string.settings_playback_section_decoder)
    val playbackSubtitleRendering = stringResource(Res.string.settings_playback_section_subtitle_rendering)
    val playbackSkipSegments = stringResource(Res.string.settings_playback_section_skip_segments)
    val playbackNextEpisode = stringResource(Res.string.settings_playback_section_next_episode)
    addRow(
        page = SettingsPage.Streams,
        key = "stream-addon-logo",
        title = stringResource(Res.string.settings_stream_addon_logo_title),
        description = stringResource(Res.string.settings_stream_addon_logo_description),
        pageLabel = streamsPage,
        section = stringResource(Res.string.settings_stream_display_section),
        icon = Icons.Rounded.Style,
    )
    if (!isTablet) {
        addRow(
            page = SettingsPage.Streams,
            key = "stream-background",
            title = stringResource(Res.string.settings_stream_background_title),
            description = stringResource(Res.string.settings_stream_background_description),
            pageLabel = streamsPage,
            section = stringResource(Res.string.settings_stream_display_section),
            icon = Icons.Rounded.Style,
        )
    }
    addRow(
        page = SettingsPage.Streams,
        key = "stream-size-badges",
        title = stringResource(Res.string.settings_stream_size_badges_title),
        description = stringResource(Res.string.settings_stream_size_badges_description),
        pageLabel = streamsPage,
        section = stringResource(Res.string.settings_stream_badges_section),
        icon = Icons.Rounded.Style,
    )
    addRow(
        page = SettingsPage.Streams,
        key = "stream-badge-position",
        title = stringResource(Res.string.settings_stream_badge_position_title),
        description = stringResource(Res.string.settings_stream_badge_position_description),
        pageLabel = streamsPage,
        section = stringResource(Res.string.settings_stream_badges_section),
        icon = Icons.Rounded.Style,
    )
    addRow(
        page = SettingsPage.Streams,
        key = "stream-badge-urls",
        title = stringResource(Res.string.settings_stream_badge_urls_title),
        description = stringResource(Res.string.settings_stream_badge_urls_search_description),
        pageLabel = streamsPage,
        section = stringResource(Res.string.settings_stream_badges_section),
        icon = Icons.Rounded.Style,
    )
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackPlayer,
        icon = Icons.Rounded.PlayArrow,
        rows = listOfNotNull(
            PlaybackSearchRow(
                "legacy-player-layout",
                stringResource(Res.string.settings_playback_legacy_layout),
                stringResource(Res.string.settings_playback_legacy_layout_description),
            ),
            PlaybackSearchRow(
                "player-clock",
                stringResource(Res.string.settings_playback_player_clock),
                stringResource(Res.string.settings_playback_player_clock_description),
            ),
            PlaybackSearchRow(
                "player-clock-format",
                stringResource(Res.string.settings_playback_player_clock_format),
                stringResource(Res.string.settings_playback_player_clock_format_description),
            ),
            PlaybackSearchRow(
                "player-clock-position",
                stringResource(Res.string.settings_playback_player_clock_position),
                stringResource(Res.string.settings_playback_player_clock_position_description),
            ),
            PlaybackSearchRow(
                "loading-overlay",
                stringResource(Res.string.settings_playback_show_loading_overlay),
                stringResource(Res.string.settings_playback_show_loading_overlay_description),
            ),
            PlaybackSearchRow(
                "pause-overlay",
                stringResource(Res.string.settings_playback_pause_overlay),
                stringResource(Res.string.settings_playback_pause_overlay_description),
            ),
            PlaybackSearchRow(
                "external-player",
                stringResource(Res.string.settings_playback_external_player),
                stringResource(Res.string.settings_playback_external_player_description_android),
            ),
            if (isIos) PlaybackSearchRow(
                "external-player-app",
                stringResource(Res.string.settings_playback_external_player_app),
            ) else null,
            PlaybackSearchRow(
                "hold-to-speed",
                stringResource(Res.string.settings_playback_hold_to_speed),
                stringResource(Res.string.settings_playback_hold_to_speed_description),
            ),
            PlaybackSearchRow(
                "touch-gestures",
                stringResource(Res.string.settings_playback_touch_gestures),
                stringResource(Res.string.settings_playback_touch_gestures_description),
            ),
            PlaybackSearchRow("hold-speed", stringResource(Res.string.settings_playback_hold_speed)),
        ),
    )
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackSubtitleAudio,
        icon = Icons.Rounded.PlayArrow,
        rows = listOf(
            PlaybackSearchRow("preferred-audio", stringResource(Res.string.settings_playback_preferred_audio_language)),
            PlaybackSearchRow("secondary-audio", stringResource(Res.string.settings_playback_secondary_audio_language)),
            PlaybackSearchRow("preferred-subtitles", stringResource(Res.string.settings_playback_preferred_subtitle_language)),
            PlaybackSearchRow("secondary-subtitles", stringResource(Res.string.settings_playback_secondary_subtitle_language)),
        ),
    )
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackStreamSelection,
        icon = Icons.Rounded.PlayArrow,
        rows = listOf(
            PlaybackSearchRow(
                "reuse-last-link",
                stringResource(Res.string.settings_playback_reuse_last_link),
                stringResource(Res.string.settings_playback_reuse_last_link_description),
            ),
            PlaybackSearchRow("last-link-cache", stringResource(Res.string.settings_playback_last_link_cache_duration)),
        ),
    )
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackStreamAutoPlay,
        icon = Icons.Rounded.PlayArrow,
        rows = buildList {
            add(PlaybackSearchRow("stream-mode", stringResource(Res.string.settings_playback_stream_selection_mode)))
            add(PlaybackSearchRow("regex-pattern", stringResource(Res.string.settings_playback_regex_pattern)))
            add(PlaybackSearchRow("stream-timeout", stringResource(Res.string.settings_playback_stream_timeout), stringResource(Res.string.settings_playback_stream_timeout_description)))
            add(PlaybackSearchRow("source-scope", stringResource(Res.string.settings_playback_source_scope)))
            add(PlaybackSearchRow("allowed-addons", stringResource(Res.string.settings_playback_allowed_addons)))
            if (pluginsEnabled) add(PlaybackSearchRow("allowed-plugins", stringResource(Res.string.settings_playback_allowed_plugins)))
            // Provider priority mode rows (shown when the mode is selected).
            add(PlaybackSearchRow("provider-priority-mode", stringResource(Res.string.settings_playback_stream_selection_mode_provider_priority), stringResource(Res.string.settings_playback_stream_selection_mode_provider_priority_description)))
            add(PlaybackSearchRow("provider-priority-order", stringResource(Res.string.settings_playback_provider_priority_order), stringResource(Res.string.settings_playback_provider_priority_order_hint)))
            add(PlaybackSearchRow("provider-priority-sources", stringResource(Res.string.settings_playback_provider_priority_sources), stringResource(Res.string.settings_playback_provider_priority_sources_description)))
            add(PlaybackSearchRow("provider-priority-timeout", stringResource(Res.string.settings_playback_provider_priority_timeout), stringResource(Res.string.settings_playback_provider_priority_timeout_description)))
            add(PlaybackSearchRow("provider-priority-first-playback", stringResource(Res.string.settings_playback_provider_priority_first_playback), stringResource(Res.string.settings_playback_provider_priority_first_playback_description)))
        },
    )
    if (!isIos) {
        addPlaybackRows(
            addRow = ::addRow,
            pageLabel = playbackPage,
            section = playbackDecoder,
            icon = Icons.Rounded.PlayArrow,
            rows = listOf(
                PlaybackSearchRow("decoder-priority", stringResource(Res.string.settings_playback_decoder_priority)),
                PlaybackSearchRow("dv7-hevc", stringResource(Res.string.settings_playback_map_dv7_to_hevc), stringResource(Res.string.settings_playback_map_dv7_to_hevc_description)),
                PlaybackSearchRow("tunneled-playback", stringResource(Res.string.settings_playback_tunneled_playback), stringResource(Res.string.settings_playback_tunneled_playback_description)),
                PlaybackSearchRow("exo-native-memory", stringResource(Res.string.settings_playback_exo_native_memory), stringResource(Res.string.settings_playback_exo_native_memory_description)),
                PlaybackSearchRow("custom-buffers", stringResource(Res.string.settings_playback_buffer_custom), stringResource(Res.string.settings_playback_buffer_custom_description)),
                PlaybackSearchRow("vod-cache", stringResource(Res.string.settings_playback_vod_cache), stringResource(Res.string.settings_playback_vod_cache_description)),
            ),
        )
        addPlaybackRows(
            addRow = ::addRow,
            pageLabel = playbackPage,
            section = playbackSubtitleRendering,
            icon = Icons.Rounded.PlayArrow,
            rows = listOf(
                PlaybackSearchRow("libass", stringResource(Res.string.settings_playback_enable_libass), stringResource(Res.string.settings_playback_enable_libass_description)),
                PlaybackSearchRow("libass-render-scale", stringResource(Res.string.settings_playback_render_scale), stringResource(Res.string.settings_playback_render_scale_description)),
            ),
        )
    }
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackSkipSegments,
        icon = Icons.Rounded.PlayArrow,
        rows = listOf(
            PlaybackSearchRow("skip-intro", stringResource(Res.string.settings_playback_skip_intro_outro_recap), stringResource(Res.string.settings_playback_skip_intro_outro_recap_description)),
        ),
    )
    addPlaybackRows(
        addRow = ::addRow,
        pageLabel = playbackPage,
        section = playbackNextEpisode,
        icon = Icons.Rounded.PlayArrow,
        rows = listOf(
            PlaybackSearchRow("auto-play-next", stringResource(Res.string.settings_playback_auto_play_next_episode), stringResource(Res.string.settings_playback_auto_play_next_episode_description)),
            PlaybackSearchRow("prefer-binge", stringResource(Res.string.settings_playback_prefer_binge_group), stringResource(Res.string.settings_playback_prefer_binge_group_description)),
            PlaybackSearchRow("threshold-mode", stringResource(Res.string.settings_playback_threshold_mode)),
            PlaybackSearchRow("threshold-percent", stringResource(Res.string.settings_playback_threshold_percentage), stringResource(Res.string.settings_playback_threshold_percentage_description)),
            PlaybackSearchRow("threshold-minutes", stringResource(Res.string.settings_playback_minutes_before_end), stringResource(Res.string.settings_playback_minutes_before_end_description)),
        ),
    )

    addContinueWatchingRows(
        addRow = ::addRow,
        pageLabel = continueWatchingPage,
        section = stringResource(Res.string.settings_continue_watching_section_visibility),
        icon = Icons.Rounded.Style,
        rows = listOf(
            PlaybackSearchRow(
                "show-continue-watching",
                stringResource(Res.string.settings_continue_watching_show_title),
                stringResource(Res.string.settings_continue_watching_show_description),
            ),
        ),
    )
    addContinueWatchingRows(
        addRow = ::addRow,
        pageLabel = continueWatchingPage,
        section = stringResource(Res.string.settings_continue_watching_section_up_next_behavior),
        icon = Icons.Rounded.Style,
        rows = listOf(
            PlaybackSearchRow("episode-thumbnails", stringResource(Res.string.settings_continue_watching_use_episode_thumbnails_title), stringResource(Res.string.settings_continue_watching_use_episode_thumbnails_description)),
            PlaybackSearchRow("up-next", stringResource(Res.string.settings_continue_watching_up_next_title), stringResource(Res.string.settings_continue_watching_up_next_description)),
            PlaybackSearchRow("unaired-next-up", stringResource(Res.string.settings_continue_watching_show_unaired_next_up_title), stringResource(Res.string.settings_continue_watching_show_unaired_next_up_description)),
            PlaybackSearchRow("blur-next-up", stringResource(Res.string.settings_continue_watching_blur_next_up_title), stringResource(Res.string.settings_continue_watching_blur_next_up_description)),
        ),
    )
    addContinueWatchingRows(
        addRow = ::addRow,
        pageLabel = continueWatchingPage,
        section = stringResource(Res.string.settings_continue_watching_section_on_launch),
        icon = Icons.Rounded.Style,
        rows = listOf(
            PlaybackSearchRow("resume-prompt", stringResource(Res.string.settings_continue_watching_resume_prompt_title), stringResource(Res.string.settings_continue_watching_resume_prompt_description)),
        ),
    )

    val posterSection = stringResource(Res.string.settings_poster_card_style)
    listOf(
        PlaybackSearchRow("poster-width", stringResource(Res.string.settings_poster_card_width)),
        PlaybackSearchRow("poster-radius", stringResource(Res.string.settings_poster_card_radius)),
        PlaybackSearchRow("poster-landscape", stringResource(Res.string.settings_poster_landscape_mode)),
        PlaybackSearchRow("poster-hide-labels", stringResource(Res.string.settings_poster_hide_labels)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.PosterCustomization,
            key = "poster-${row.key}",
            title = row.title,
            description = row.description,
            pageLabel = posterStylePage,
            section = posterSection,
            icon = Icons.Rounded.Tune,
        )
    }

    val cardDepthSection = stringResource(Res.string.settings_card_depth_title)
    listOf(
        PlaybackSearchRow("card-depth-effect", cardDepthSection, stringResource(Res.string.settings_card_depth_description)),
        PlaybackSearchRow("card-depth-edge", stringResource(Res.string.settings_card_depth_edge)),
        PlaybackSearchRow("card-depth-sheen", stringResource(Res.string.settings_card_depth_sheen)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.PosterCustomization,
            key = "poster-${row.key}",
            title = row.title,
            description = row.description,
            pageLabel = posterStylePage,
            section = cardDepthSection,
            icon = Icons.Rounded.Tune,
        )
    }

    val homeLayoutSection = stringResource(Res.string.settings_homescreen_section_hero)
    listOf(
        PlaybackSearchRow("home-hero", stringResource(Res.string.settings_homescreen_show_hero), stringResource(Res.string.settings_homescreen_show_hero_description)),
        PlaybackSearchRow("home-catalog-type", stringResource(Res.string.layout_catalog_type), stringResource(Res.string.layout_catalog_type_sub)),
        PlaybackSearchRow("home-hide-unreleased", stringResource(Res.string.layout_hide_unreleased), stringResource(Res.string.layout_hide_unreleased_sub)),
        PlaybackSearchRow("home-hero-sources", stringResource(Res.string.settings_homescreen_section_hero_sources)),
        PlaybackSearchRow("home-catalogs", stringResource(Res.string.settings_homescreen_section_catalogs)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.Homescreen,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = homeLayoutPage,
            section = homeLayoutSection,
            icon = Icons.Rounded.Home,
        )
    }

    val detailAppearanceSection = stringResource(Res.string.settings_meta_section_appearance)
    if (supportsPosterNavigationMotion) {
        addRow(
            page = SettingsPage.MetaScreen,
            key = "meta-poster-transition",
            title = stringResource(Res.string.settings_meta_poster_transition),
            description = stringResource(Res.string.settings_meta_poster_transition_description),
            pageLabel = detailPage,
            section = detailAppearanceSection,
            icon = Icons.Rounded.Tune,
        )
    }
    listOf(
        PlaybackSearchRow("meta-background-mode", stringResource(Res.string.settings_meta_background_mode), stringResource(Res.string.settings_meta_background_mode_description)),
        PlaybackSearchRow("meta-tabs", stringResource(Res.string.settings_meta_tab_layout), stringResource(Res.string.settings_meta_tab_layout_description)),
        PlaybackSearchRow(
            "meta-overall-ratings",
            stringResource(Res.string.layout_overall_ratings),
            stringResource(Res.string.layout_overall_ratings_sub_on) + " " +
                stringResource(Res.string.layout_overall_ratings_sub_off),
        ),
        PlaybackSearchRow(
            "meta-episode-ratings",
            stringResource(Res.string.layout_episode_ratings),
            listOf(
                stringResource(Res.string.layout_episode_ratings_sub),
                stringResource(Res.string.layout_ratings_show),
                stringResource(Res.string.layout_ratings_hide),
                stringResource(Res.string.layout_ratings_hide_unwatched),
            ).joinToString(" "),
        ),
        PlaybackSearchRow("meta-episode-cards", stringResource(Res.string.settings_meta_episode_cards), stringResource(Res.string.settings_meta_episode_cards_description)),
        PlaybackSearchRow("meta-shuffle", stringResource(Res.string.random_episode_title), stringResource(Res.string.layout_random_episode_sub)),
        PlaybackSearchRow("meta-blur-episodes", stringResource(Res.string.settings_meta_blur_unwatched_episodes), stringResource(Res.string.settings_meta_blur_unwatched_episodes_description)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.MetaScreen,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = detailPage,
            section = detailAppearanceSection,
            icon = Icons.Rounded.Tune,
        )
    }
    val detailSectionsSection = stringResource(Res.string.settings_meta_section_sections)
    listOf(
        PlaybackSearchRow("meta-overview", stringResource(Res.string.settings_meta_overview), stringResource(Res.string.settings_meta_overview_description)),
        PlaybackSearchRow("meta-actions", stringResource(Res.string.settings_meta_actions), stringResource(Res.string.settings_meta_actions_description)),
        PlaybackSearchRow("meta-details", stringResource(Res.string.settings_meta_details), stringResource(Res.string.settings_meta_details_description)),
        PlaybackSearchRow("meta-trailers", stringResource(Res.string.settings_meta_trailers), stringResource(Res.string.settings_meta_trailers_description)),
        PlaybackSearchRow("meta-cast", stringResource(Res.string.settings_meta_cast), stringResource(Res.string.settings_meta_cast_description)),
        PlaybackSearchRow("meta-episodes", stringResource(Res.string.settings_meta_episodes), stringResource(Res.string.settings_meta_episodes_description)),
        PlaybackSearchRow("meta-production", stringResource(Res.string.settings_meta_production), stringResource(Res.string.settings_meta_production_description)),
        PlaybackSearchRow("meta-more-like-this", stringResource(Res.string.settings_meta_more_like_this), stringResource(Res.string.settings_meta_more_like_this_description)),
        PlaybackSearchRow("meta-collection", stringResource(Res.string.settings_meta_collection), stringResource(Res.string.settings_meta_collection_description)),
        PlaybackSearchRow("meta-comments", stringResource(Res.string.settings_meta_comments), stringResource(Res.string.settings_meta_comments_description)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.MetaScreen,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = detailPage,
            section = detailSectionsSection,
            icon = Icons.Rounded.Tune,
        )
    }

    addPage(
        page = SettingsPage.TmdbEnrichment,
        key = "tmdb",
        title = tmdbPage,
        description = stringResource(Res.string.settings_integrations_tmdb_description),
        icon = Icons.Rounded.Link,
    )
    addPage(
        page = SettingsPage.MdbListRatings,
        key = "mdblist",
        title = mdbListPage,
        description = stringResource(Res.string.settings_integrations_mdblist_description),
        icon = Icons.Rounded.Link,
    )
    val tmdbModulesSection = stringResource(Res.string.settings_tmdb_section_modules)
    listOf(
        PlaybackSearchRow("tmdb-enable", stringResource(Res.string.settings_tmdb_enable_enrichment), stringResource(Res.string.settings_tmdb_enable_enrichment_description), stringResource(Res.string.settings_tmdb_section_title)),
        PlaybackSearchRow("tmdb-api-key", stringResource(Res.string.settings_tmdb_personal_api_key), stringResource(Res.string.settings_tmdb_api_key_override_description), stringResource(Res.string.settings_tmdb_section_title)),
        PlaybackSearchRow("tmdb-language", stringResource(Res.string.settings_tmdb_preferred_language), stringResource(Res.string.settings_tmdb_preferred_language_description), stringResource(Res.string.settings_tmdb_section_localization)),
        PlaybackSearchRow("tmdb-trailers", stringResource(Res.string.settings_tmdb_module_trailers), stringResource(Res.string.settings_tmdb_module_trailers_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-extra-images", stringResource(Res.string.settings_tmdb_module_extra_images), stringResource(Res.string.settings_tmdb_module_extra_images_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-artwork", stringResource(Res.string.settings_tmdb_module_artwork), stringResource(Res.string.settings_tmdb_module_artwork_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-basic-info", stringResource(Res.string.settings_tmdb_module_basic_info), stringResource(Res.string.settings_tmdb_module_basic_info_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-details", stringResource(Res.string.settings_tmdb_module_details), stringResource(Res.string.settings_tmdb_module_details_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-credits", stringResource(Res.string.settings_tmdb_module_credits), stringResource(Res.string.settings_tmdb_module_credits_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-companies", stringResource(Res.string.settings_tmdb_module_production_companies), stringResource(Res.string.settings_tmdb_module_production_companies_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-networks", stringResource(Res.string.settings_tmdb_module_networks), stringResource(Res.string.settings_tmdb_module_networks_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-episodes", stringResource(Res.string.settings_tmdb_module_episodes), stringResource(Res.string.settings_tmdb_module_episodes_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-season-posters", stringResource(Res.string.settings_tmdb_module_season_posters), stringResource(Res.string.settings_tmdb_module_season_posters_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-more-like-this", stringResource(Res.string.settings_tmdb_module_more_like_this), stringResource(Res.string.settings_tmdb_module_more_like_this_description), tmdbModulesSection),
        PlaybackSearchRow("tmdb-collections", stringResource(Res.string.settings_tmdb_module_collections), stringResource(Res.string.settings_tmdb_module_collections_description), tmdbModulesSection),
    ).forEach { row ->
        addRow(
            page = SettingsPage.TmdbEnrichment,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = tmdbPage,
            section = row.sectionOverride ?: tmdbModulesSection,
            icon = Icons.Rounded.Link,
        )
    }

    listOf(
        PlaybackSearchRow("mdb-enable", stringResource(Res.string.settings_mdb_enable_ratings), stringResource(Res.string.settings_mdb_enable_ratings_description), stringResource(Res.string.settings_mdb_section_title)),
        PlaybackSearchRow("mdb-show-on-hero", stringResource(Res.string.settings_mdb_show_on_hero), stringResource(Res.string.settings_mdb_show_on_hero_description), stringResource(Res.string.settings_mdb_section_title)),
        PlaybackSearchRow("mdb-api-key", stringResource(Res.string.settings_mdb_api_key_title), stringResource(Res.string.settings_mdb_api_key_description), stringResource(Res.string.settings_mdb_section_api_key)),
        PlaybackSearchRow("mdb-imdb", stringResource(Res.string.source_imdb), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-tmdb", stringResource(Res.string.source_tmdb), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-tomatoes", stringResource(Res.string.source_rotten_tomatoes), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-metacritic", stringResource(Res.string.source_metacritic), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-trakt", stringResource(Res.string.source_trakt), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-letterboxd", stringResource(Res.string.source_letterboxd), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
        PlaybackSearchRow("mdb-audience", stringResource(Res.string.source_audience_score), "", stringResource(Res.string.settings_mdb_section_rating_providers)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.MdbListRatings,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = mdbListPage,
            section = row.sectionOverride ?: stringResource(Res.string.settings_mdb_section_title),
            icon = Icons.Rounded.Link,
        )
    }

    val notificationsAlerts = stringResource(Res.string.settings_notifications_section_alerts)
    addRow(
        page = SettingsPage.Notifications,
        key = "episode-release-alerts",
        title = stringResource(Res.string.settings_notifications_episode_release_alerts),
        description = stringResource(Res.string.settings_notifications_episode_release_alerts_description),
        pageLabel = notificationsPage,
        section = notificationsAlerts,
        icon = Icons.Rounded.Notifications,
    )
    addRow(
        page = SettingsPage.Notifications,
        key = "notification-test",
        title = stringResource(Res.string.settings_notifications_test_title),
        pageLabel = notificationsPage,
        section = stringResource(Res.string.settings_notifications_section_test),
        icon = Icons.Rounded.Notifications,
    )

    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "trakt-authentication",
        title = stringResource(Res.string.trakt_library_source_trakt),
        description = stringResource(Res.string.settings_trakt_intro_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "simkl-authentication",
        title = stringResource(Res.string.tracking_source_simkl),
        description = stringResource(Res.string.settings_simkl_sign_in_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "mdblist-authentication",
        title = stringResource(Res.string.tracking_source_mdblist),
        description = stringResource(Res.string.settings_mdblist_sign_in_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "mal-authentication",
        title = stringResource(Res.string.tracking_source_mal),
        description = stringResource(Res.string.settings_mal_sign_in_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "kitsu-authentication",
        title = stringResource(Res.string.settings_kitsu_title),
        description = stringResource(Res.string.settings_kitsu_sign_in_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "anilist-authentication",
        title = stringResource(Res.string.settings_anilist_title),
        description = stringResource(Res.string.settings_anilist_sign_in_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    listOf(
        PlaybackSearchRow("trakt-library-source", stringResource(Res.string.trakt_library_source_title), stringResource(Res.string.trakt_library_source_subtitle)),
        PlaybackSearchRow("trakt-watch-progress", stringResource(Res.string.trakt_watch_progress_title), stringResource(Res.string.trakt_watch_progress_subtitle)),
        PlaybackSearchRow("trakt-continue-watching-window", stringResource(Res.string.trakt_continue_watching_window), stringResource(Res.string.trakt_continue_watching_subtitle)),
        PlaybackSearchRow("trakt-comments", stringResource(Res.string.settings_trakt_comments), stringResource(Res.string.settings_trakt_comments_description)),
        PlaybackSearchRow("trakt-more-like-this-source", stringResource(Res.string.trakt_more_like_this_source_title), stringResource(Res.string.trakt_more_like_this_source_subtitle)),
    ).forEach { row ->
        addRow(
            page = SettingsPage.TraktAuthentication,
            key = row.key,
            title = row.title,
            description = row.description,
            pageLabel = trackingPage,
            section = stringResource(Res.string.settings_tracking_features),
            category = accountCategory,
            icon = Icons.Rounded.Link,
        )
    }
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "simkl-anime-id",
        title = stringResource(Res.string.settings_tracking_anime_id_title),
        description = stringResource(Res.string.settings_tracking_anime_id_subtitle),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_anime_section),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "mdblist-library-lists",
        title = stringResource(Res.string.settings_mdblist_library_lists),
        description = stringResource(Res.string.settings_mdblist_library_lists_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_mdblist_section),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "anime-tracking-updates",
        title = stringResource(Res.string.settings_tracking_anime_updates_title),
        description = stringResource(Res.string.settings_tracking_anime_updates_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_anime_updates_title),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.TraktAuthentication,
        key = "mal-library-source",
        title = stringResource(Res.string.tracking_source_mal),
        description = stringResource(Res.string.settings_tracking_mal_library_description),
        pageLabel = trackingPage,
        section = stringResource(Res.string.settings_tracking_data_sources),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    val malPage = stringResource(Res.string.compose_settings_page_myanimelist)
    addRow(
        page = SettingsPage.MyAnimeList,
        key = "mal-account",
        title = stringResource(Res.string.tracking_source_mal),
        description = stringResource(Res.string.settings_mal_sign_in_description),
        pageLabel = malPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.MyAnimeList,
        key = "mal-anime-tracking-updates",
        title = stringResource(Res.string.settings_tracking_anime_updates_title),
        description = stringResource(Res.string.settings_tracking_anime_updates_description),
        pageLabel = malPage,
        section = stringResource(Res.string.settings_tracking_anime_updates_title),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.MyAnimeList,
        key = "mal-sync-login",
        title = stringResource(Res.string.settings_mal_sync_login_title),
        description = stringResource(Res.string.settings_mal_sync_login_description),
        pageLabel = malPage,
        section = stringResource(Res.string.settings_tracking_mal_section),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    val kitsuPage = stringResource(Res.string.compose_settings_page_kitsu)
    addRow(
        page = SettingsPage.Kitsu,
        key = "kitsu-account",
        title = stringResource(Res.string.settings_kitsu_title),
        description = stringResource(Res.string.settings_kitsu_sign_in_description),
        pageLabel = kitsuPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.Kitsu,
        key = "kitsu-sync-login",
        title = stringResource(Res.string.settings_kitsu_sync_login_title),
        description = stringResource(Res.string.settings_kitsu_sync_login_description),
        pageLabel = kitsuPage,
        section = stringResource(Res.string.settings_kitsu_section),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    val aniListPage = stringResource(Res.string.compose_settings_page_anilist)
    addRow(
        page = SettingsPage.AniList,
        key = "anilist-account",
        title = stringResource(Res.string.settings_anilist_title),
        description = stringResource(Res.string.settings_anilist_sign_in_description),
        pageLabel = aniListPage,
        section = stringResource(Res.string.settings_tracking_services),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )
    addRow(
        page = SettingsPage.AniList,
        key = "anilist-sync-login",
        title = stringResource(Res.string.settings_anilist_sync_login_title),
        description = stringResource(Res.string.settings_anilist_sync_login_description),
        pageLabel = aniListPage,
        section = stringResource(Res.string.settings_anilist_section),
        category = accountCategory,
        icon = Icons.Rounded.Link,
    )

    LocalTvShell.current?.layoutSettings?.let { tvLayout ->
        val tvLayoutPage = stringResource(Res.string.tv_layout_settings_title)
        addRow(
            page = SettingsPage.Appearance,
            key = "tv-layout",
            title = tvLayoutPage,
            description = stringResource(Res.string.tv_layout_settings_description),
            pageLabel = layoutPage,
            section = stringResource(Res.string.settings_appearance_section_home),
            category = generalCategory,
            icon = Icons.Rounded.Palette,
        )
        tvLayout.searchEntries().forEach { item ->
            addRow(
                page = SettingsPage.TvLayout,
                key = "tv-layout-${item.key}",
                title = item.title,
                description = item.description,
                pageLabel = tvLayoutPage,
                section = item.section,
                category = generalCategory,
                icon = Icons.Rounded.Palette,
            )
        }
    }

    addAdditionalSearchEntries(
        addPage = ::addPage,
        addRow = ::addRow,
        layoutPage = layoutPage,
        playbackPage = playbackPage,
        streamsPage = streamsPage,
        continueWatchingPage = continueWatchingPage,
        posterStylePage = posterStylePage,
        detailPage = detailPage,
        advancedCategory = advancedCategory,
    )

    return entries
}

/**
 * Settings rows that were missing from the search index. Every row shown in a settings page must have
 * an entry here or above: `SettingsSearchIndexCoverageTest` fails when a row title is not indexed.
 */
@Composable
private fun addAdditionalSearchEntries(
    addPage: (
        page: SettingsPage,
        key: String,
        title: String,
        description: String,
        category: String,
        icon: ImageVector,
    ) -> Unit,
    addRow: (
        page: SettingsPage,
        key: String,
        title: String,
        description: String,
        pageLabel: String,
        section: String,
        category: String,
        icon: ImageVector,
    ) -> Unit,
    layoutPage: String,
    playbackPage: String,
    streamsPage: String,
    continueWatchingPage: String,
    posterStylePage: String,
    detailPage: String,
    advancedCategory: String,
) {
    val generalCategory = stringResource(SettingsCategory.General.labelRes)
    fun row(
        page: SettingsPage,
        key: String,
        title: String,
        description: String = "",
        pageLabel: String,
        section: String,
        category: String = generalCategory,
        icon: ImageVector,
    ) = addRow(page, key, title, description, pageLabel, section, category, icon)

    // Appearance
    val displaySection = stringResource(Res.string.settings_appearance_section_display)
    listOf(
        PlaybackSearchRow("app-icon", stringResource(Res.string.settings_appearance_app_icon)),
        PlaybackSearchRow("date-format", stringResource(Res.string.settings_appearance_date_format)),
        PlaybackSearchRow("episode-code-format", stringResource(Res.string.settings_appearance_episode_code_format)),
    ).forEach { item ->
        row(SettingsPage.Appearance, item.key, item.title, item.description, layoutPage, displaySection, icon = Icons.Rounded.Palette)
    }
    if (!isIos) {
        row(
            page = SettingsPage.Appearance,
            key = "nav-bar-style",
            title = stringResource(Res.string.settings_appearance_nav_bar_style),
            pageLabel = layoutPage,
            section = displaySection,
            icon = Icons.Rounded.Palette,
        )
        row(
            page = SettingsPage.Appearance,
            key = "nav-bar-glow",
            title = stringResource(Res.string.settings_nav_bar_glow),
            description = stringResource(Res.string.settings_nav_bar_glow_description),
            pageLabel = layoutPage,
            section = stringResource(Res.string.settings_appearance_nav_bar_style),
            icon = Icons.Rounded.Palette,
        )
        if (TvAppRuntime.isTvApp) {
            row(
                page = SettingsPage.Appearance,
                key = "top-nav-bar",
                title = stringResource(Res.string.settings_top_nav_bar),
                description = stringResource(Res.string.settings_top_nav_bar_description),
                pageLabel = layoutPage,
                section = stringResource(Res.string.settings_appearance_nav_bar_style),
                icon = Icons.Rounded.Palette,
            )
            row(
                page = SettingsPage.Appearance,
                key = "top-nav-bar-layout",
                title = stringResource(Res.string.settings_top_nav_bar_layout),
                description = stringResource(Res.string.settings_top_nav_bar_expanded_description) + " " +
                    stringResource(Res.string.settings_top_nav_bar_compact_description),
                pageLabel = layoutPage,
                section = stringResource(Res.string.settings_appearance_nav_bar_style),
                icon = Icons.Rounded.Palette,
            )
        }
    }

    // Streams / Stream Parser
    val streamParserSection = stringResource(Res.string.stream_parser_section_title)
    listOf(
        PlaybackSearchRow("stream-parser-enabled", stringResource(Res.string.stream_parser_enabled_title), stringResource(Res.string.stream_parser_enabled_description)),
        PlaybackSearchRow("stream-parser-animations", stringResource(Res.string.stream_parser_animations_title), stringResource(Res.string.stream_parser_animations_description)),
        PlaybackSearchRow("stream-parser-player-badges", stringResource(Res.string.stream_parser_player_badges_title), stringResource(Res.string.stream_parser_player_badges_description)),
        PlaybackSearchRow("stream-parser-sort-quality", stringResource(Res.string.stream_parser_sort_by_quality_title), stringResource(Res.string.stream_parser_sort_by_quality_description)),
        PlaybackSearchRow("stream-parser-quality-filter", stringResource(Res.string.stream_parser_quality_filter_title), stringResource(Res.string.stream_parser_quality_filter_description)),
    ).forEach { item ->
        row(SettingsPage.Streams, item.key, item.title, item.description, streamsPage, streamParserSection, icon = Icons.Rounded.Style)
    }

    // Continue watching
    row(
        page = SettingsPage.ContinueWatching,
        key = "continue-watching-sort-mode",
        title = stringResource(Res.string.settings_continue_watching_sort_mode_title),
        pageLabel = continueWatchingPage,
        section = stringResource(Res.string.settings_continue_watching_section_sort_order),
        icon = Icons.Rounded.Style,
    )

    // Poster customization
    val cardDepthSection = stringResource(Res.string.settings_card_depth_title)
    listOf(
        PlaybackSearchRow("poster-card-depth-enabled", stringResource(Res.string.settings_card_depth_enabled), "", cardDepthSection),
        PlaybackSearchRow("poster-card-depth-coverage", stringResource(Res.string.settings_card_depth_edge_coverage), "", cardDepthSection),
        PlaybackSearchRow("poster-landscape-clearlogo", stringResource(Res.string.settings_poster_always_show_landscape_clearlogo), "", stringResource(Res.string.settings_poster_card_style)),
    ).forEach { item ->
        row(SettingsPage.PosterCustomization, item.key, item.title, item.description, posterStylePage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.Tune)
    }

    // Detail page
    if (AppFeaturePolicy.heroTrailerPlaybackSupported && AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP) {
        row(
            page = SettingsPage.MetaScreen,
            key = "meta-hero-trailer-playback",
            title = stringResource(Res.string.settings_meta_hero_trailer_playback),
            description = stringResource(Res.string.settings_meta_hero_trailer_playback_description),
            pageLabel = detailPage,
            section = stringResource(Res.string.settings_meta_section_appearance),
            icon = Icons.Rounded.Tune,
        )
    }

    // Playback
    val playerSection = stringResource(Res.string.settings_playback_section_player)
    val playerRows = buildList {
        add(PlaybackSearchRow("loading-status", stringResource(Res.string.playback_show_loading_status), stringResource(Res.string.playback_show_loading_status_sub)))
        add(PlaybackSearchRow("parental-guide", stringResource(Res.string.settings_playback_parental_guide), stringResource(Res.string.settings_playback_parental_guide_description)))
        add(PlaybackSearchRow("private-viewing", stringResource(Res.string.settings_playback_private_viewing), stringResource(Res.string.settings_playback_private_viewing_description)))
        add(PlaybackSearchRow("player-preference", stringResource(Res.string.settings_playback_player_preference)))
        if (!isIos) {
            add(PlaybackSearchRow("external-forward-subtitles", stringResource(Res.string.settings_playback_external_player_forward_subtitles), stringResource(Res.string.settings_playback_external_player_forward_subtitles_description)))
            add(PlaybackSearchRow("external-skip-segments", stringResource(Res.string.settings_playback_external_player_send_skip_segments), stringResource(Res.string.settings_playback_external_player_send_skip_segments_description)))
        }
        add(PlaybackSearchRow("seek-interval", stringResource(Res.string.settings_playback_seek_interval)))
    }
    val seekThumbnailSection = stringResource(Res.string.settings_playback_section_seek_thumbnails)
    val seekThumbnailRows = if (seekThumbnailsSupported) {
        listOf(
            PlaybackSearchRow("seek-thumbnails", stringResource(Res.string.settings_playback_seek_thumbnails), stringResource(Res.string.settings_playback_seek_thumbnails_description)),
            PlaybackSearchRow("seek-thumbnails-wifi-only", stringResource(Res.string.settings_playback_seek_thumbnails_wifi_only), stringResource(Res.string.settings_playback_seek_thumbnails_wifi_only_description)),
            PlaybackSearchRow("seek-thumbnails-size", stringResource(Res.string.settings_playback_seek_thumbnails_size)),
        )
    } else {
        emptyList()
    }
    val subtitleAudioSection = stringResource(Res.string.settings_playback_section_subtitle_audio)
    val subtitleAudioRows = buildList {
        add(
            PlaybackSearchRow(
                "remember-audio-delay-per-device",
                stringResource(Res.string.settings_playback_remember_audio_delay_per_device),
                stringResource(Res.string.settings_playback_remember_audio_delay_per_device_description),
            ),
        )
        if (!isIos) {
            add(PlaybackSearchRow("subtitle-strip-sdh", stringResource(Res.string.settings_playback_subtitle_strip_sdh), stringResource(Res.string.settings_playback_subtitle_strip_sdh_description)))
            add(PlaybackSearchRow("subtitle-use-forced", stringResource(Res.string.settings_playback_subtitle_use_forced), stringResource(Res.string.settings_playback_subtitle_use_forced_description)))
            add(PlaybackSearchRow("subtitle-preferred-only", stringResource(Res.string.settings_playback_subtitle_show_preferred_only), stringResource(Res.string.settings_playback_subtitle_show_preferred_only_description)))
            add(PlaybackSearchRow("subtitle-auto-sync", stringResource(Res.string.settings_playback_subtitle_auto_sync), stringResource(Res.string.settings_playback_subtitle_auto_sync_description)))
            add(
                PlaybackSearchRow(
                    "subtitle-auto-sync-mode",
                    stringResource(Res.string.settings_playback_auto_sync_mode_aggressive),
                    stringResource(Res.string.settings_playback_auto_sync_mode_aggressive_description) + " " +
                        stringResource(Res.string.settings_playback_auto_sync_mode_passive) + " " +
                        stringResource(Res.string.settings_playback_auto_sync_mode_passive_description),
                ),
            )
            add(
                PlaybackSearchRow(
                    "subtitle-auto-sync-tolerance",
                    stringResource(Res.string.settings_playback_auto_sync_tolerance, "").trimEnd(' ', ':'),
                    stringResource(Res.string.settings_playback_auto_sync_tolerance_description),
                ),
            )
        }
    }
    val subtitleRenderingSection = stringResource(Res.string.settings_playback_section_subtitle_rendering)
    val subtitleRenderingRows = listOf(
        PlaybackSearchRow("subtitle-size", stringResource(Res.string.settings_playback_subtitle_size)),
        PlaybackSearchRow("subtitle-size-mobile", stringResource(Res.string.settings_playback_subtitle_size_mobile), stringResource(Res.string.settings_playback_subtitle_size_per_device_description)),
        PlaybackSearchRow("subtitle-size-tablet", stringResource(Res.string.settings_playback_subtitle_size_tablet), stringResource(Res.string.settings_playback_subtitle_size_per_device_description)),
        PlaybackSearchRow("subtitle-size-tv", stringResource(Res.string.settings_playback_subtitle_size_tv), stringResource(Res.string.settings_playback_subtitle_size_per_device_description)),
        PlaybackSearchRow("subtitle-offset", stringResource(Res.string.settings_playback_subtitle_vertical_offset)),
        PlaybackSearchRow("subtitle-bold", stringResource(Res.string.settings_playback_subtitle_bold), stringResource(Res.string.settings_playback_subtitle_bold_description)),
        PlaybackSearchRow("subtitle-text-color", stringResource(Res.string.settings_playback_subtitle_text_color)),
        PlaybackSearchRow("subtitle-background-color", stringResource(Res.string.settings_playback_subtitle_background_color)),
        PlaybackSearchRow("subtitle-outline", stringResource(Res.string.settings_playback_subtitle_outline), stringResource(Res.string.settings_playback_subtitle_outline_description)),
        PlaybackSearchRow("subtitle-outline-color", stringResource(Res.string.settings_playback_subtitle_outline_color)),
    )
    val p2pRows = if (P2pSettingsRepository.isVisible) {
        listOf(
            PlaybackSearchRow("p2p", stringResource(Res.string.settings_p2p_title), stringResource(Res.string.settings_p2p_subtitle)),
            PlaybackSearchRow("p2p-hide-stats", stringResource(Res.string.settings_p2p_hide_stats_title), stringResource(Res.string.settings_p2p_hide_stats_subtitle)),
            PlaybackSearchRow("p2p-profile", stringResource(Res.string.settings_p2p_profile_title)),
            PlaybackSearchRow("p2p-cache-size", stringResource(Res.string.settings_p2p_cache_size_title)),
            PlaybackSearchRow("p2p-clear-cache", stringResource(Res.string.settings_p2p_clear_cache_title)),
        )
    } else {
        emptyList()
    }
    val decoderSection = stringResource(Res.string.settings_playback_section_decoder)
    val decoderRows = if (isIos) {
        emptyList()
    } else {
        listOf(
            PlaybackSearchRow("playback-engine", stringResource(Res.string.settings_playback_engine)),
            PlaybackSearchRow("libmpv-video-output", stringResource(Res.string.settings_playback_libmpv_video_output)),
            PlaybackSearchRow("libmpv-hw-decoding", stringResource(Res.string.settings_playback_libmpv_hardware_decoding), stringResource(Res.string.settings_playback_libmpv_hardware_decoding_description)),
            PlaybackSearchRow("libmpv-yuv420p", stringResource(Res.string.settings_playback_libmpv_yuv420p), stringResource(Res.string.settings_playback_libmpv_yuv420p_description)),
            PlaybackSearchRow("buffer-min", stringResource(Res.string.settings_playback_buffer_min)),
            PlaybackSearchRow("buffer-max", stringResource(Res.string.settings_playback_buffer_max)),
            PlaybackSearchRow("buffer-initial", stringResource(Res.string.settings_playback_buffer_initial)),
            PlaybackSearchRow("buffer-rebuffer", stringResource(Res.string.settings_playback_buffer_rebuffer)),
            PlaybackSearchRow("buffer-back", stringResource(Res.string.settings_playback_buffer_back)),
            PlaybackSearchRow("buffer-target", stringResource(Res.string.settings_playback_buffer_target)),
            PlaybackSearchRow("vod-cache-auto-size", stringResource(Res.string.settings_playback_vod_cache_auto_size), stringResource(Res.string.settings_playback_vod_cache_auto_size_description)),
            PlaybackSearchRow("vod-cache-size", stringResource(Res.string.settings_playback_vod_cache_size)),
        )
    }
    val iosRows = if (isIos) {
        val iosAudioSection = stringResource(Res.string.settings_playback_ios_audio_output_section)
        val iosVideoSection = stringResource(Res.string.settings_playback_ios_video_output)
        listOf(
            PlaybackSearchRow("ios-audio-output", stringResource(Res.string.settings_playback_ios_audio_output), "", iosAudioSection),
            PlaybackSearchRow("ios-hw-decoder", stringResource(Res.string.settings_playback_ios_hardware_decoder), "", iosVideoSection),
            PlaybackSearchRow("ios-edr", stringResource(Res.string.settings_playback_ios_extended_dynamic_range), stringResource(Res.string.settings_playback_ios_extended_dynamic_range_desc), iosVideoSection),
            PlaybackSearchRow("ios-color-hint", stringResource(Res.string.settings_playback_ios_display_color_hint), stringResource(Res.string.settings_playback_ios_display_color_hint_desc), iosVideoSection),
            PlaybackSearchRow("ios-target-primaries", stringResource(Res.string.settings_playback_ios_target_primaries), "", iosVideoSection),
            PlaybackSearchRow("ios-target-transfer", stringResource(Res.string.settings_playback_ios_target_transfer), "", iosVideoSection),
        )
    } else {
        emptyList()
    }
    val skipSection = stringResource(Res.string.settings_playback_section_skip_segments)
    val skipRows = listOf(
        PlaybackSearchRow("auto-skip-segments", stringResource(Res.string.settings_playback_auto_skip_segments)),
    )
    val nextEpisodeSection = stringResource(Res.string.settings_playback_section_next_episode)
    val nextEpisodeRows = listOf(
        PlaybackSearchRow("auto-play-next-fallback", stringResource(Res.string.settings_playback_auto_play_next_episode_fallback), stringResource(Res.string.settings_playback_auto_play_next_episode_fallback_description)),
        PlaybackSearchRow("reuse-binge", stringResource(Res.string.settings_playback_reuse_binge_group), stringResource(Res.string.settings_playback_reuse_binge_group_description)),
        PlaybackSearchRow("preload-next", stringResource(Res.string.settings_playback_preload_next_episode), stringResource(Res.string.settings_playback_preload_next_episode_description)),
        PlaybackSearchRow("still-watching", stringResource(Res.string.settings_playback_still_watching), stringResource(Res.string.settings_playback_still_watching_description)),
        PlaybackSearchRow("still-watching-night", stringResource(Res.string.settings_playback_still_watching_night_only), stringResource(Res.string.settings_playback_still_watching_night_only_description)),
        PlaybackSearchRow("still-watching-live-tv", stringResource(Res.string.settings_playback_still_watching_live_tv), stringResource(Res.string.settings_playback_still_watching_live_tv_description)),
    )
    listOf(
        playerSection to playerRows,
        seekThumbnailSection to seekThumbnailRows,
        subtitleAudioSection to subtitleAudioRows,
        subtitleRenderingSection to subtitleRenderingRows,
        stringResource(Res.string.settings_playback_section_p2p) to p2pRows,
        decoderSection to decoderRows,
        "" to iosRows,
        skipSection to skipRows,
        nextEpisodeSection to nextEpisodeRows,
    ).forEach { (section, rows) ->
        rows.forEach { item ->
            row(
                page = SettingsPage.Playback,
                key = "playback-${item.key}",
                title = item.title,
                description = item.description,
                pageLabel = playbackPage,
                section = item.sectionOverride ?: section,
                icon = Icons.Rounded.PlayArrow,
            )
        }
    }

    // Integrations: connected services (debrid)
    val debridPage = stringResource(Res.string.compose_settings_page_debrid)
    addPage(
        SettingsPage.Debrid,
        "debrid",
        debridPage,
        stringResource(Res.string.settings_integrations_debrid_description),
        generalCategory,
        Icons.Rounded.Cloud,
    )
    val debridGeneral = stringResource(Res.string.settings_debrid_section_title)
    val debridLinks = stringResource(Res.string.settings_debrid_section_instant_playback)
    val debridResults = stringResource(Res.string.settings_debrid_section_result_management)
    val debridFormatting = stringResource(Res.string.settings_debrid_section_formatting)
    listOf(
        PlaybackSearchRow("debrid-cloud-library", stringResource(Res.string.settings_debrid_cloud_library), stringResource(Res.string.settings_debrid_cloud_library_description), debridGeneral),
        PlaybackSearchRow("debrid-enable", stringResource(Res.string.settings_debrid_enable), stringResource(Res.string.settings_debrid_enable_description), debridGeneral),
        PlaybackSearchRow("debrid-resolve-with", stringResource(Res.string.settings_debrid_resolve_with), stringResource(Res.string.settings_debrid_resolve_with_description), debridGeneral),
        PlaybackSearchRow("debrid-instant-playback", stringResource(Res.string.settings_debrid_prepare_instant_playback), stringResource(Res.string.settings_debrid_prepare_instant_playback_description), debridLinks),
        PlaybackSearchRow("debrid-stream-count", stringResource(Res.string.settings_debrid_prepare_stream_count), "", debridLinks),
        PlaybackSearchRow("debrid-max-results", stringResource(Res.string.settings_debrid_max_results), stringResource(Res.string.settings_debrid_max_results_desc), debridResults),
        PlaybackSearchRow("debrid-sort-results", stringResource(Res.string.settings_debrid_sort_results), stringResource(Res.string.settings_debrid_sort_results_desc), debridResults),
        PlaybackSearchRow("debrid-per-resolution", stringResource(Res.string.settings_debrid_per_resolution_limit), stringResource(Res.string.settings_debrid_per_resolution_limit_desc), debridResults),
        PlaybackSearchRow("debrid-per-quality", stringResource(Res.string.settings_debrid_per_quality_limit), stringResource(Res.string.settings_debrid_per_quality_limit_desc), debridResults),
        PlaybackSearchRow("debrid-size-range", stringResource(Res.string.settings_debrid_size_range), stringResource(Res.string.settings_debrid_size_range_desc), debridResults),
        PlaybackSearchRow("debrid-name-template", stringResource(Res.string.settings_debrid_name_template), stringResource(Res.string.settings_debrid_name_template_description), debridFormatting),
        PlaybackSearchRow("debrid-description-template", stringResource(Res.string.settings_debrid_description_template), stringResource(Res.string.settings_debrid_description_template_description), debridFormatting),
        PlaybackSearchRow("debrid-formatter-reset", stringResource(Res.string.settings_debrid_formatter_reset_title), stringResource(Res.string.settings_debrid_formatter_reset_subtitle), debridFormatting),
    ).forEach { item ->
        row(SettingsPage.Debrid, item.key, item.title, item.description, debridPage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.Link)
    }

    // Integrations: Live TV (custom layout, not covered by the coverage test)
    val liveTvPage = stringResource(Res.string.compose_settings_page_live_tv)
    addPage(
        SettingsPage.LiveTv,
        "live-tv",
        liveTvPage,
        stringResource(Res.string.settings_integrations_live_tv_description),
        generalCategory,
        Icons.Rounded.LiveTv,
    )
    val liveTvProviders = stringResource(Res.string.live_tv_settings_section_providers)
    val liveTvEpg = stringResource(Res.string.live_tv_settings_section_epg)
    listOf(
        PlaybackSearchRow("live-tv-navigation", stringResource(Res.string.live_tv_settings_navigation_title), stringResource(Res.string.live_tv_settings_navigation_description), stringResource(Res.string.live_tv_settings_section_navigation)),
        PlaybackSearchRow("live-tv-playlists", stringResource(Res.string.live_tv_settings_playlist_label), stringResource(Res.string.live_tv_settings_description), stringResource(Res.string.live_tv_settings_section_playlist)),
        PlaybackSearchRow("live-tv-xtream", stringResource(Res.string.live_tv_xtream_title), stringResource(Res.string.live_tv_xtream_description), liveTvProviders),
        PlaybackSearchRow("live-tv-stalker", stringResource(Res.string.live_tv_stalker_title), stringResource(Res.string.live_tv_stalker_description), liveTvProviders),
        PlaybackSearchRow("live-tv-epg", stringResource(Res.string.live_tv_settings_epg_label), stringResource(Res.string.live_tv_settings_epg_description), liveTvEpg),
        PlaybackSearchRow("live-tv-epg-auto", stringResource(Res.string.live_tv_settings_epg_auto_title), stringResource(Res.string.live_tv_settings_epg_auto_description), liveTvEpg),
    ).forEach { item ->
        row(SettingsPage.LiveTv, item.key, item.title, item.description, liveTvPage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.LiveTv)
    }

    // Integrations: Live TV → Manage channels (custom layout)
    val liveTvChannelsPage = stringResource(Res.string.live_tv_channels_title)
    val liveTvChannelsSection = stringResource(Res.string.live_tv_settings_section_channels)
    addPage(
        SettingsPage.LiveTvChannels,
        "live-tv-channels",
        liveTvChannelsPage,
        stringResource(Res.string.live_tv_channels_description),
        generalCategory,
        Icons.Rounded.LiveTv,
    )
    row(SettingsPage.LiveTv, "live-tv-channels-row", liveTvChannelsPage, stringResource(Res.string.live_tv_channels_description), liveTvPage, liveTvChannelsSection, icon = Icons.Rounded.LiveTv)
    listOf(
        PlaybackSearchRow("live-tv-channels-select-all", stringResource(Res.string.live_tv_channels_select_all), stringResource(Res.string.live_tv_channels_select_all_description), liveTvChannelsSection),
        PlaybackSearchRow("live-tv-channels-deselect-all", stringResource(Res.string.live_tv_channels_deselect_all), stringResource(Res.string.live_tv_channels_deselect_all_description), liveTvChannelsSection),
        PlaybackSearchRow("live-tv-channels-show-categories", stringResource(Res.string.live_tv_channels_show_categories), stringResource(Res.string.live_tv_channels_show_categories_description), liveTvChannelsSection),
        PlaybackSearchRow("live-tv-channels-hide-categories", stringResource(Res.string.live_tv_channels_hide_categories), stringResource(Res.string.live_tv_channels_hide_categories_description), liveTvChannelsSection),
        PlaybackSearchRow("live-tv-channels-order", stringResource(Res.string.live_tv_channels_reorder), stringResource(Res.string.live_tv_channels_hint), liveTvChannelsSection),
        PlaybackSearchRow("live-tv-channels-reset-order", stringResource(Res.string.live_tv_channels_reset_order), stringResource(Res.string.live_tv_channels_reset_order_description), liveTvChannelsSection),
    ).forEach { item ->
        row(SettingsPage.LiveTvChannels, item.key, item.title, item.description, liveTvChannelsPage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.LiveTv)
    }

    // Integrations: Discord Rich Presence (Android only, same condition as the Integrations row)
    if (DiscordRpcPlatform.isSupported) {
        val discordPage = stringResource(Res.string.compose_settings_page_discord_rpc)
        addPage(
            SettingsPage.DiscordRpc,
            "discord-rpc",
            discordPage,
            stringResource(Res.string.settings_integrations_discord_description),
            generalCategory,
            Icons.Rounded.Forum,
        )
        val discordAccount = stringResource(Res.string.discord_rpc_section_account)
        val discordPresence = stringResource(Res.string.discord_rpc_section_presence)
        val discordDisplay = stringResource(Res.string.discord_rpc_section_display)
        val discordAdvanced = stringResource(Res.string.discord_rpc_section_advanced)
        listOf(
            PlaybackSearchRow("discord-rpc-login", stringResource(Res.string.discord_rpc_login_button), stringResource(Res.string.discord_rpc_login_description), discordAccount),
            PlaybackSearchRow("discord-rpc-test", stringResource(Res.string.discord_rpc_test_button), stringResource(Res.string.discord_rpc_sync_notice), discordAccount),
            PlaybackSearchRow("discord-rpc-enable", stringResource(Res.string.discord_rpc_enable_title), stringResource(Res.string.discord_rpc_enable_description), discordPresence),
            PlaybackSearchRow("discord-rpc-activity-type", stringResource(Res.string.discord_rpc_activity_type_title), stringResource(Res.string.discord_rpc_activity_type_description), discordPresence),
            PlaybackSearchRow("discord-rpc-status", stringResource(Res.string.discord_rpc_status_title), stringResource(Res.string.discord_rpc_status_description), discordPresence),
            PlaybackSearchRow("discord-rpc-title-as-name", stringResource(Res.string.discord_rpc_show_title_as_name_title), stringResource(Res.string.discord_rpc_show_title_as_name_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-poster", stringResource(Res.string.discord_rpc_show_poster_title), stringResource(Res.string.discord_rpc_show_poster_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-playback-state", stringResource(Res.string.discord_rpc_show_playback_state_title), stringResource(Res.string.discord_rpc_show_playback_state_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-timestamps", stringResource(Res.string.discord_rpc_show_timestamps_title), stringResource(Res.string.discord_rpc_show_timestamps_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-hide-on-pause", stringResource(Res.string.discord_rpc_hide_on_pause_title), stringResource(Res.string.discord_rpc_hide_on_pause_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-imdb-button", stringResource(Res.string.discord_rpc_show_imdb_button_title), stringResource(Res.string.discord_rpc_show_imdb_button_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-live-tv", stringResource(Res.string.discord_rpc_show_live_tv_title), stringResource(Res.string.discord_rpc_show_live_tv_description), discordDisplay),
            PlaybackSearchRow("discord-rpc-application-id", stringResource(Res.string.discord_rpc_application_id_title), stringResource(Res.string.discord_rpc_application_id_description), discordAdvanced),
        ).forEach { item ->
            row(SettingsPage.DiscordRpc, item.key, item.title, item.description, discordPage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.Forum)
        }
    }

    // Integrations: segment skipping (provider priority, submission targets, AnimeSkip, IntroDB, SkipDB)
    val integrationsPageLabel = stringResource(Res.string.compose_settings_page_integrations)
    val segmentSkippingSection = stringResource(Res.string.settings_integrations_section_timestamps)
    row(SettingsPage.Integrations, "timestamp-priority", stringResource(Res.string.timestamp_priority_title), stringResource(Res.string.timestamp_priority_dialog_message), integrationsPageLabel, segmentSkippingSection, icon = Icons.Rounded.SwapVert)
    row(SettingsPage.Integrations, "timestamp-merge-providers", stringResource(Res.string.timestamp_merge_providers), stringResource(Res.string.timestamp_merge_providers_description), integrationsPageLabel, segmentSkippingSection, icon = Icons.Rounded.SkipNext)
    row(SettingsPage.Integrations, "timestamp-submit-targets", stringResource(Res.string.timestamp_submit_targets_title), stringResource(Res.string.timestamp_submit_targets_dialog_message), integrationsPageLabel, segmentSkippingSection, icon = Icons.Rounded.CloudUpload)
    val animeSkipPage = stringResource(Res.string.compose_settings_page_anime_skip)
    addPage(
        SettingsPage.AnimeSkip,
        "anime-skip-page",
        animeSkipPage,
        stringResource(Res.string.settings_integrations_anime_skip_description),
        generalCategory,
        Icons.Rounded.SkipNext,
    )
    listOf(
        PlaybackSearchRow("anime-skip", stringResource(Res.string.settings_playback_anime_skip), stringResource(Res.string.settings_playback_anime_skip_description)),
        PlaybackSearchRow("anime-skip-client", stringResource(Res.string.settings_playback_anime_skip_client_id), stringResource(Res.string.settings_playback_anime_skip_client_id_description)),
    ).forEach { item ->
        row(SettingsPage.AnimeSkip, item.key, item.title, item.description, animeSkipPage, animeSkipPage, icon = Icons.Rounded.SkipNext)
    }
    val introDbPage = stringResource(Res.string.compose_settings_page_introdb)
    addPage(
        SettingsPage.IntroDb,
        "introdb-page",
        introDbPage,
        stringResource(Res.string.settings_integrations_introdb_description),
        generalCategory,
        Icons.Rounded.SkipNext,
    )
    listOf(
        PlaybackSearchRow("intro-submit", stringResource(Res.string.settings_playback_intro_submit_enabled), stringResource(Res.string.settings_playback_intro_submit_enabled_description)),
        PlaybackSearchRow("introdb-key", stringResource(Res.string.settings_playback_introdb_api_key), stringResource(Res.string.settings_playback_introdb_api_key_description)),
    ).forEach { item ->
        row(SettingsPage.IntroDb, item.key, item.title, item.description, introDbPage, introDbPage, icon = Icons.Rounded.SkipNext)
    }
    val aniSkipPage = stringResource(Res.string.compose_settings_page_aniskip)
    addPage(SettingsPage.AniSkip, "aniskip", aniSkipPage, stringResource(Res.string.settings_integrations_aniskip_description), generalCategory, Icons.Rounded.SkipNext)
    listOf(
        PlaybackSearchRow("aniskip-enabled", stringResource(Res.string.aniskip_enabled), stringResource(Res.string.aniskip_enabled_description)),
        PlaybackSearchRow("aniskip-send-duration", stringResource(Res.string.aniskip_send_duration), stringResource(Res.string.aniskip_send_duration_description)),
    ).forEach { item ->
        row(SettingsPage.AniSkip, item.key, item.title, item.description, aniSkipPage, aniSkipPage, icon = Icons.Rounded.SkipNext)
    }
    val skipDbPage = stringResource(Res.string.compose_settings_page_skipdb)
    addPage(SettingsPage.SkipDb, "skipdb", skipDbPage, stringResource(Res.string.settings_integrations_skipdb_description), generalCategory, Icons.Rounded.SkipNext)
    val skipDbLookupSection = stringResource(Res.string.skipdb_section_lookup)
    listOf(
        PlaybackSearchRow("skipdb-send-duration", stringResource(Res.string.skipdb_send_duration), stringResource(Res.string.skipdb_send_duration_description)),
        PlaybackSearchRow("skipdb-ignore-out-of-range", stringResource(Res.string.skipdb_ignore_out_of_range), stringResource(Res.string.skipdb_ignore_out_of_range_description)),
        PlaybackSearchRow("skipdb-min-confidence", stringResource(Res.string.skipdb_min_confidence), stringResource(Res.string.skipdb_min_confidence_description)),
        PlaybackSearchRow("skipdb-adjust-mode", stringResource(Res.string.skipdb_adjust_mode), stringResource(Res.string.skipdb_adjust_mode_description)),
    ).forEach { item ->
        row(SettingsPage.SkipDb, item.key, item.title, item.description, skipDbPage, skipDbLookupSection, icon = Icons.Rounded.SkipNext)
    }
    val skipDbSubmitSection = stringResource(Res.string.skipdb_section_submit)
    listOf(
        PlaybackSearchRow("skipdb-submit", stringResource(Res.string.skipdb_submit_enabled), stringResource(Res.string.skipdb_submit_enabled_description)),
        PlaybackSearchRow("skipdb-key", stringResource(Res.string.skipdb_api_key), stringResource(Res.string.skipdb_api_key_description)),
    ).forEach { item ->
        row(SettingsPage.SkipDb, item.key, item.title, item.description, skipDbPage, skipDbSubmitSection, icon = Icons.Rounded.SkipNext)
    }

    val openSubtitlesPage = stringResource(Res.string.compose_settings_page_opensubtitles)
    addPage(SettingsPage.OpenSubtitles, "opensubtitles", openSubtitlesPage, stringResource(Res.string.settings_integrations_opensubtitles_description), generalCategory, Icons.Rounded.Subtitles)
    val openSubtitlesSearchSection = stringResource(Res.string.opensubtitles_section_search)
    listOf(
        PlaybackSearchRow("opensubtitles-enabled", stringResource(Res.string.opensubtitles_enabled), stringResource(Res.string.opensubtitles_enabled_description)),
        PlaybackSearchRow("opensubtitles-key", stringResource(Res.string.opensubtitles_api_key), stringResource(Res.string.opensubtitles_api_key_description)),
    ).forEach { item ->
        row(SettingsPage.OpenSubtitles, item.key, item.title, item.description, openSubtitlesPage, openSubtitlesPage, icon = Icons.Rounded.Subtitles)
    }
    listOf(
        PlaybackSearchRow("opensubtitles-languages", stringResource(Res.string.opensubtitles_languages), stringResource(Res.string.opensubtitles_languages_description)),
        PlaybackSearchRow("opensubtitles-hearing-impaired", stringResource(Res.string.opensubtitles_hearing_impaired), stringResource(Res.string.opensubtitles_hearing_impaired_description)),
        PlaybackSearchRow("opensubtitles-machine-translated", stringResource(Res.string.opensubtitles_machine_translated), stringResource(Res.string.opensubtitles_machine_translated_description)),
    ).forEach { item ->
        row(SettingsPage.OpenSubtitles, item.key, item.title, item.description, openSubtitlesPage, openSubtitlesSearchSection, icon = Icons.Rounded.Subtitles)
    }

    val subDlPage = stringResource(Res.string.compose_settings_page_subdl)
    addPage(SettingsPage.SubDl, "subdl", subDlPage, stringResource(Res.string.settings_integrations_subdl_description), generalCategory, Icons.Rounded.Subtitles)
    val subDlSearchSection = stringResource(Res.string.subdl_section_search)
    listOf(
        PlaybackSearchRow("subdl-enabled", stringResource(Res.string.subdl_enabled), stringResource(Res.string.subdl_enabled_description)),
        PlaybackSearchRow("subdl-key", stringResource(Res.string.subdl_api_key), stringResource(Res.string.subdl_api_key_description)),
    ).forEach { item ->
        row(SettingsPage.SubDl, item.key, item.title, item.description, subDlPage, subDlPage, icon = Icons.Rounded.Subtitles)
    }
    listOf(
        PlaybackSearchRow("subdl-languages", stringResource(Res.string.subdl_languages), stringResource(Res.string.subdl_languages_description)),
        PlaybackSearchRow("subdl-hearing-impaired", stringResource(Res.string.subdl_hearing_impaired), stringResource(Res.string.subdl_hearing_impaired_description)),
        PlaybackSearchRow("subdl-ai-translated", stringResource(Res.string.subdl_ai_translated), stringResource(Res.string.subdl_ai_translated_description)),
        PlaybackSearchRow("subdl-season-packs", stringResource(Res.string.subdl_season_packs), stringResource(Res.string.subdl_season_packs_description)),
    ).forEach { item ->
        row(SettingsPage.SubDl, item.key, item.title, item.description, subDlPage, subDlSearchSection, icon = Icons.Rounded.Subtitles)
    }

    // AI assistant (the page cannot be opened on TV, see IntegrationsSettingsPage)
    if (!TvAppRuntime.isTvApp) {
        val aiPage = stringResource(Res.string.compose_settings_page_ai_assistant)
        addPage(SettingsPage.AiAssistant, "ai-assistant", aiPage, stringResource(Res.string.settings_integrations_ai_assistant_description), generalCategory, Icons.Rounded.AutoAwesome)
        val aiGeneralSection = stringResource(Res.string.ai_settings_section_general)
        val aiProviderSection = stringResource(Res.string.ai_settings_section_provider)
        val aiWebSection = stringResource(Res.string.ai_settings_section_web_search)
        row(SettingsPage.AiAssistant, "ai-enabled", stringResource(Res.string.ai_settings_enable), stringResource(Res.string.ai_settings_enable_description) + " " + stringResource(Res.string.ai_settings_sync_notice), aiPage, aiGeneralSection, icon = Icons.Rounded.AutoAwesome)
        row(SettingsPage.AiAssistant, "ai-provider", stringResource(Res.string.ai_settings_provider), stringResource(Res.string.ai_settings_provider_description) + " " + stringResource(Res.string.ai_settings_fallback_help), aiPage, aiProviderSection, icon = Icons.Rounded.AutoAwesome)
        row(SettingsPage.AiAssistant, "ai-api-key", stringResource(Res.string.ai_settings_api_key), stringResource(Res.string.ai_settings_api_key_help), aiPage, aiProviderSection, icon = Icons.Rounded.AutoAwesome)
        row(SettingsPage.AiAssistant, "ai-model", stringResource(Res.string.ai_settings_model), stringResource(Res.string.ai_settings_model_help, AiProvider.entries.joinToString { it.defaultModel }), aiPage, aiProviderSection, icon = Icons.Rounded.AutoAwesome)
        row(SettingsPage.AiAssistant, "ai-web-search", stringResource(Res.string.ai_settings_web_search), stringResource(Res.string.ai_settings_web_search_description), aiPage, aiWebSection, icon = Icons.Rounded.AutoAwesome)
        row(SettingsPage.AiAssistant, "ai-tavily-key", stringResource(Res.string.ai_settings_tavily_key), stringResource(Res.string.ai_settings_tavily_description), aiPage, aiWebSection, icon = Icons.Rounded.AutoAwesome)
    }

    addPage(SettingsPage.MyAnimeList, "myanimelist", stringResource(Res.string.compose_settings_page_myanimelist), stringResource(Res.string.settings_integrations_myanimelist_description), generalCategory, Icons.Rounded.ViewList)
    // Integrations: placeholder pages (work in progress)
    addPage(SettingsPage.Kitsu, "kitsu", stringResource(Res.string.compose_settings_page_kitsu), stringResource(Res.string.settings_integrations_kitsu_description), generalCategory, Icons.Rounded.ViewList)
    addPage(SettingsPage.AniList, "anilist", stringResource(Res.string.compose_settings_page_anilist), stringResource(Res.string.settings_integrations_anilist_description), generalCategory, Icons.Rounded.ViewList)
    addPage(SettingsPage.Telegram, "telegram", stringResource(Res.string.compose_settings_page_telegram), stringResource(Res.string.settings_integrations_telegram_description), generalCategory, Icons.Rounded.Send)
    if (TelegramSourceResolver.isSupported) {
        // The Telegram page has a custom layout (login steps): its rows are indexed by hand.
        row(
            page = SettingsPage.Telegram,
            key = "telegram-account",
            title = stringResource(Res.string.settings_telegram_section_account),
            description = stringResource(Res.string.settings_telegram_description),
            pageLabel = stringResource(Res.string.compose_settings_page_telegram),
            section = stringResource(Res.string.settings_telegram_section_account),
            category = generalCategory,
            icon = Icons.Rounded.Send,
        )
        row(
            page = SettingsPage.Telegram,
            key = "telegram-cache",
            title = stringResource(Res.string.settings_telegram_clear_cache),
            description = stringResource(Res.string.settings_telegram_connected_title),
            pageLabel = stringResource(Res.string.compose_settings_page_telegram),
            section = stringResource(Res.string.settings_telegram_section_account),
            category = generalCategory,
            icon = Icons.Rounded.Send,
        )
    }

    // Downloads
    val downloadsPage = stringResource(Res.string.compose_settings_root_downloads_title)
    addPage(
        SettingsPage.Downloads,
        "downloads",
        downloadsPage,
        stringResource(Res.string.compose_settings_root_downloads_description),
        generalCategory,
        Icons.Rounded.Download,
    )
    val downloadsFolderSection = stringResource(Res.string.downloads_settings_section_folder)
    val downloadsSummarySection = stringResource(Res.string.downloads_settings_section_summary)
    val downloadsFilesSection = stringResource(Res.string.downloads_settings_section_files)
    buildList {
        if (downloadsFolderOptionsVisible) {
            add(PlaybackSearchRow("downloads-folder", stringResource(Res.string.downloads_settings_folder_title), stringResource(Res.string.downloads_settings_folder_description), downloadsFolderSection))
            add(PlaybackSearchRow("downloads-use-app-storage", stringResource(Res.string.downloads_settings_use_internal_title), stringResource(Res.string.downloads_settings_use_internal_description), downloadsFolderSection))
        }
        if (downloadsPowerSettingsSupported) {
            val backgroundSection = stringResource(Res.string.downloads_settings_section_background)
            add(PlaybackSearchRow("downloads-battery", stringResource(Res.string.downloads_settings_battery_title), stringResource(Res.string.downloads_settings_battery_description), backgroundSection))
            add(PlaybackSearchRow("downloads-phone-settings", stringResource(Res.string.downloads_settings_battery_open_app_settings), stringResource(Res.string.downloads_settings_battery_hint), backgroundSection))
        }
        val seasonsSection = stringResource(Res.string.downloads_settings_section_seasons)
        add(PlaybackSearchRow("downloads-season-concurrency", stringResource(Res.string.downloads_settings_season_concurrency_title), stringResource(Res.string.downloads_settings_season_concurrency_description), seasonsSection))
        if (com.nuvio.app.features.downloads.DownloadsPlatformDownloader.supportsEpisodeFolders) {
            add(PlaybackSearchRow("downloads-episode-folders", stringResource(Res.string.downloads_settings_episode_folders_title), stringResource(Res.string.downloads_settings_episode_folders_description), stringResource(Res.string.downloads_settings_section_organization)))
        }
        add(PlaybackSearchRow("downloads-storage", downloadsSummarySection, stringResource(Res.string.downloads_settings_summary_description), downloadsSummarySection))
        add(PlaybackSearchRow("downloads-files", downloadsFilesSection, stringResource(Res.string.downloads_settings_files_description), downloadsFilesSection))
    }.forEach { item ->
        row(SettingsPage.Downloads, item.key, item.title, item.description, downloadsPage, item.sectionOverride.orEmpty(), icon = Icons.Rounded.Download)
    }

    // Advanced: app logs
    val appLogsPage = stringResource(Res.string.settings_app_logs_title)
    val appLogsRecording = stringResource(Res.string.app_logs_section_recording)
    val appLogsFilters = stringResource(Res.string.app_logs_section_filters)
    val appLogsActions = stringResource(Res.string.app_logs_section_actions)
    listOf(
        PlaybackSearchRow("app-logs-enabled", stringResource(Res.string.app_logs_enabled_title), stringResource(Res.string.app_logs_enabled_description), appLogsRecording),
        PlaybackSearchRow("app-logs-categories", stringResource(Res.string.app_logs_category_title), "", appLogsFilters),
        PlaybackSearchRow("app-logs-levels", stringResource(Res.string.app_logs_levels_title), stringResource(Res.string.app_logs_levels_description), appLogsFilters),
        PlaybackSearchRow("app-logs-live", stringResource(Res.string.app_logs_live_title), stringResource(Res.string.app_logs_live_description), appLogsFilters),
        PlaybackSearchRow("app-logs-copy", stringResource(Res.string.app_logs_copy_title), stringResource(Res.string.app_logs_copy_description), appLogsActions),
        PlaybackSearchRow("app-logs-share", stringResource(Res.string.app_logs_share_title), stringResource(Res.string.app_logs_share_description), appLogsActions),
        PlaybackSearchRow("app-logs-import", stringResource(Res.string.app_logs_import_title), stringResource(Res.string.app_logs_import_description), appLogsActions),
        PlaybackSearchRow("app-logs-clear", stringResource(Res.string.app_logs_clear_title), stringResource(Res.string.app_logs_clear_description), appLogsActions),
    ).forEach { item ->
        row(SettingsPage.AppLogs, item.key, item.title, item.description, appLogsPage, item.sectionOverride.orEmpty(), advancedCategory, Icons.Rounded.Tune)
    }

    // Advanced: media proxy (only where the proxy exists)
    if (LanMediaProxy.isSupported) {
        addPage(
            SettingsPage.MediaProxy,
            "media-proxy",
            stringResource(Res.string.media_proxy_title),
            stringResource(Res.string.media_proxy_entry_description),
            advancedCategory,
            Icons.Rounded.Tune,
        )
        val mediaProxyPage = stringResource(Res.string.media_proxy_title)
        val serverSection = stringResource(Res.string.media_proxy_section_server)
        val requestsSection = stringResource(Res.string.media_proxy_section_requests)
        val castSection = stringResource(Res.string.media_proxy_section_cast)
        buildList {
            add(PlaybackSearchRow("media-proxy-port", stringResource(Res.string.media_proxy_port_title), stringResource(Res.string.media_proxy_port_description), serverSection))
            add(PlaybackSearchRow("media-proxy-address", stringResource(Res.string.media_proxy_address_title), stringResource(Res.string.media_proxy_address_description), serverSection))
            add(PlaybackSearchRow("media-proxy-keep-awake", stringResource(Res.string.media_proxy_keep_awake_title), stringResource(Res.string.media_proxy_keep_awake_description), serverSection))
            add(PlaybackSearchRow("media-proxy-user-agent", stringResource(Res.string.media_proxy_user_agent_title), stringResource(Res.string.media_proxy_user_agent_description), requestsSection))
            add(PlaybackSearchRow("media-proxy-connect-timeout", stringResource(Res.string.media_proxy_connect_timeout_title), "", requestsSection))
            add(PlaybackSearchRow("media-proxy-read-timeout", stringResource(Res.string.media_proxy_read_timeout_title), "", requestsSection))
            add(PlaybackSearchRow("media-proxy-cors", stringResource(Res.string.media_proxy_cors_title), stringResource(Res.string.media_proxy_cors_description), requestsSection))
            add(PlaybackSearchRow("media-proxy-rewrite", stringResource(Res.string.media_proxy_rewrite_title), stringResource(Res.string.media_proxy_rewrite_description), requestsSection))
            if (mediaProxyCastOptionsVisible) {
                add(PlaybackSearchRow("media-proxy-cast-always", stringResource(Res.string.media_proxy_cast_always_title), stringResource(Res.string.media_proxy_cast_always_description), castSection))
                add(PlaybackSearchRow("media-proxy-cast-fallback", stringResource(Res.string.media_proxy_cast_fallback_title), stringResource(Res.string.media_proxy_cast_fallback_description), castSection))
            }
            add(PlaybackSearchRow("media-proxy-reset", stringResource(Res.string.media_proxy_reset_title), stringResource(Res.string.media_proxy_reset_description), stringResource(Res.string.media_proxy_section_reset)))
        }.forEach { item ->
            row(SettingsPage.MediaProxy, item.key, item.title, item.description, mediaProxyPage, item.sectionOverride.orEmpty(), advancedCategory, Icons.Rounded.Tune)
        }
    }
}

private data class PlaybackSearchRow(
    val key: String,
    val title: String,
    val description: String = "",
    val sectionOverride: String? = null,
)

private fun addPlaybackRows(
    addRow: (
        page: SettingsPage,
        key: String,
        title: String,
        description: String,
        pageLabel: String,
        section: String,
        category: String,
        icon: ImageVector,
    ) -> Unit,
    pageLabel: String,
    section: String,
    icon: ImageVector,
    rows: List<PlaybackSearchRow>,
) {
    rows.forEach { row ->
        addRow(
            SettingsPage.Playback,
            "playback-${row.key}",
            row.title,
            row.description,
            pageLabel,
            section,
            "",
            icon,
        )
    }
}

private fun addContinueWatchingRows(
    addRow: (
        page: SettingsPage,
        key: String,
        title: String,
        description: String,
        pageLabel: String,
        section: String,
        category: String,
        icon: ImageVector,
    ) -> Unit,
    pageLabel: String,
    section: String,
    icon: ImageVector,
    rows: List<PlaybackSearchRow>,
) {
    rows.forEach { row ->
        addRow(
            SettingsPage.ContinueWatching,
            "continue-watching-${row.key}",
            row.title,
            row.description,
            pageLabel,
            section,
            "",
            icon,
        )
    }
}

internal fun LazyListScope.settingsSearchRootContent(
    query: () -> String,
    queryBlank: Boolean,
    entries: @Composable () -> List<SettingsSearchEntry>,
    isTablet: Boolean,
    showSearchField: Boolean,
    animateSearchField: Boolean,
    onQueryChange: (String) -> Unit,
    onTargetClick: (SettingsSearchTarget) -> Unit,
) {
    if (showSearchField || !queryBlank) {
        item(key = "settings-search-field") {
            SettingsSearchRevealItem(animate = animateSearchField) {
                SettingsSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                )
            }
        }
    }

    if (queryBlank) return

    item(key = "settings-search-results") {
        // The index is resolved here, outside the scope that reads the query, so typing doesn't rebuild it.
        SettingsSearchResults(
            query = query,
            entries = entries(),
            isTablet = isTablet,
            onTargetClick = onTargetClick,
        )
    }
}

@Composable
private fun SettingsSearchResults(
    query: () -> String,
    entries: List<SettingsSearchEntry>,
    isTablet: Boolean,
    onTargetClick: (SettingsSearchTarget) -> Unit,
) {
    val currentQuery = query()
    val results = remember(currentQuery, entries) {
        settingsSearchResults(
            query = currentQuery,
            entries = entries,
        )
    }
    // The results live in one card (one lazy item): composing hundreds of rows for a one-letter query
    // blocked the main thread and the typed letters. Show the best matches first, the rest on request.
    var showAll by remember(currentQuery) { mutableStateOf(false) }
    val shownResults = if (showAll) results else results.take(SettingsSearchResultsLimit)
    if (results.isEmpty()) {
        SettingsSearchEmptyState(isTablet = isTablet)
    } else {
        SettingsSection(
            title = stringResource(Res.string.settings_search_results_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                shownResults.forEachIndexed { index, entry ->
                    key(entry.key) {
                        if (index > 0) {
                            SettingsGroupDivider(isTablet = isTablet)
                        }
                        SettingsNavigationRow(
                            title = entry.title,
                            description = entry.resultDescription(),
                            icon = entry.icon,
                            isTablet = isTablet,
                            onClick = {
                                entry.requestHighlight()
                                onTargetClick(entry.target)
                            },
                        )
                    }
                }
                if (shownResults.size < results.size) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_search_show_all, results.size),
                        description = stringResource(Res.string.settings_search_show_all_description),
                        icon = Icons.Rounded.ViewList,
                        isTablet = isTablet,
                        onClick = { showAll = true },
                    )
                }
            }
        }
    }
}

private const val SettingsSearchResultsLimit = 25

@Composable
private fun SettingsSearchRevealItem(
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    if (!animate) {
        content()
        return
    }

    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(
            animationSpec = tween(durationMillis = NuvioTokens.Motion.normalMillis),
            expandFrom = Alignment.Top,
        ) + fadeIn(
            animationSpec = tween(durationMillis = NuvioTokens.Motion.fastMillis),
        ) + slideInVertically(
            animationSpec = tween(durationMillis = NuvioTokens.Motion.normalMillis),
            initialOffsetY = { -it / 4 },
        ),
    ) {
        content()
    }
}

@Composable
private fun SettingsSearchField(
    query: () -> String,
    onQueryChange: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    // The field owns its text (TextFieldState): typed letters show up right away, without waiting for
    // the screen to recompose with the new value; the query follows from the field.
    val state = rememberTextFieldState(initialText = Snapshot.withoutReadObservation(query))
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { currentOnQueryChange(it) }
    }
    OutlinedTextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = tokens.shapes.compactCard,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = tokens.colors.textMuted,
            )
        },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(Res.string.compose_search_clear),
                        tint = tokens.colors.textMuted,
                    )
                }
            }
        } else {
            null
        },
        placeholder = {
            Text(
                text = stringResource(Res.string.settings_search_placeholder),
                color = tokens.colors.textMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.colors.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.colors.borderFocus,
            unfocusedBorderColor = tokens.colors.borderDefault,
            focusedContainerColor = tokens.colors.surfaceCard,
            unfocusedContainerColor = tokens.colors.surfaceCard,
            cursorColor = tokens.colors.accent,
        ),
    )
}

@Composable
private fun SettingsSearchEmptyState(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    SettingsSection(
        title = stringResource(Res.string.settings_search_results_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 18.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_search_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun settingsSearchResults(
    query: String,
    entries: List<SettingsSearchEntry>,
): List<SettingsSearchEntry> {
    val terms = normalizeSettingsSearchText(query.trim())
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (terms.isEmpty()) return emptyList()

    val normalizedQuery = terms.joinToString(separator = " ")
    // Stable sort: titles starting with the query, then titles containing every term, then the rest.
    return entries
        .filter { entry -> terms.all { term -> entry.searchableText.contains(term) } }
        .sortedBy { entry ->
            when {
                entry.normalizedTitle.startsWith(normalizedQuery) -> 0
                terms.all { term -> entry.normalizedTitle.contains(term) } -> 1
                else -> 2
            }
        }
}

/** Asks the destination page to scroll to and highlight this entry's row; whole-page entries have no row. */
private fun SettingsSearchEntry.requestHighlight() {
    val pageTarget = target as? SettingsSearchTarget.Page
    if (pageTarget == null || (section.isBlank() && title == page)) {
        SettingsSearchHighlight.clear()
    } else {
        SettingsSearchHighlight.request(pageTarget.page, title)
    }
}

private fun SettingsSearchEntry.resultDescription(): String {
    return description.ifBlank { contextLabel }
}

private val settingsSearchDiacritics: Map<Char, String> = buildMap {
    fun map(chars: String, replacement: String) = chars.forEach { put(it, replacement) }
    map("àáâãäåāăą", "a")
    map("çćĉċč", "c")
    map("ďđ", "d")
    map("èéêëēĕėęě", "e")
    map("ĝğġģ", "g")
    map("ĥħ", "h")
    map("ìíîïĩīĭįı", "i")
    map("ĵ", "j")
    map("ķ", "k")
    map("ĺļľŀł", "l")
    map("ñńņňŉ", "n")
    map("òóôõöøōŏő", "o")
    map("ŕŗř", "r")
    map("śŝşšș", "s")
    map("ţťŧț", "t")
    map("ùúûüũūŭůűų", "u")
    map("ŵ", "w")
    map("ýÿŷ", "y")
    map("źżž", "z")
    put('ß', "ss")
    put('æ', "ae")
    put('œ', "oe")
}

/** Lowercases [text] and strips the most common Latin diacritics, so "modalita" matches "modalità". */
internal fun normalizeSettingsSearchText(text: String): String {
    val lower = text.lowercase()
    if (lower.none { it in settingsSearchDiacritics }) return lower
    return buildString(lower.length) {
        lower.forEach { char -> append(settingsSearchDiacritics[char] ?: char) }
    }
}
