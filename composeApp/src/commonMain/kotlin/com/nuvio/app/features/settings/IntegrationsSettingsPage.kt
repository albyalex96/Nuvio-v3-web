package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LiveTv
import com.nuvio.app.features.discord.DiscordRpcPlatform
import com.nuvio.app.tv.TvAppRuntime
import nuvio.composeapp.generated.resources.compose_settings_page_ai_assistant
import nuvio.composeapp.generated.resources.compose_settings_page_anilist
import nuvio.composeapp.generated.resources.compose_settings_page_anime_skip
import nuvio.composeapp.generated.resources.compose_settings_page_discord_rpc
import nuvio.composeapp.generated.resources.compose_settings_page_introdb
import nuvio.composeapp.generated.resources.compose_settings_page_kitsu
import nuvio.composeapp.generated.resources.compose_settings_page_wetrakr
import nuvio.composeapp.generated.resources.compose_settings_page_myanimelist
import nuvio.composeapp.generated.resources.compose_settings_page_opensubtitles
import nuvio.composeapp.generated.resources.compose_settings_page_skipdb
import nuvio.composeapp.generated.resources.compose_settings_page_aniskip
import nuvio.composeapp.generated.resources.settings_integrations_aniskip_description
import nuvio.composeapp.generated.resources.compose_settings_page_subdl
import nuvio.composeapp.generated.resources.compose_settings_page_telegram
import nuvio.composeapp.generated.resources.settings_integrations_ai_assistant_description
import nuvio.composeapp.generated.resources.settings_integrations_ai_assistant_tv_unavailable
import nuvio.composeapp.generated.resources.settings_integrations_anilist_description
import nuvio.composeapp.generated.resources.settings_integrations_anime_skip_description
import nuvio.composeapp.generated.resources.settings_integrations_discord_description
import nuvio.composeapp.generated.resources.settings_integrations_introdb_description
import nuvio.composeapp.generated.resources.settings_integrations_kitsu_description
import nuvio.composeapp.generated.resources.settings_integrations_wetrakr_description
import nuvio.composeapp.generated.resources.settings_integrations_myanimelist_description
import nuvio.composeapp.generated.resources.settings_integrations_opensubtitles_description
import nuvio.composeapp.generated.resources.settings_integrations_section_extra
import nuvio.composeapp.generated.resources.settings_integrations_section_lists
import nuvio.composeapp.generated.resources.settings_integrations_section_subtitles
import nuvio.composeapp.generated.resources.settings_integrations_section_timestamps
import nuvio.composeapp.generated.resources.settings_integrations_skipdb_description
import nuvio.composeapp.generated.resources.settings_integrations_subdl_description
import nuvio.composeapp.generated.resources.settings_integrations_telegram_description
import nuvio.composeapp.generated.resources.compose_settings_page_live_tv
import nuvio.composeapp.generated.resources.settings_integrations_live_tv_description
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.settings_integrations_mdblist_description
import nuvio.composeapp.generated.resources.settings_integrations_debrid_description
import nuvio.composeapp.generated.resources.settings_integrations_section_title
import nuvio.composeapp.generated.resources.settings_integrations_tmdb_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_tmdb_enrichment),
                    description = stringResource(Res.string.settings_integrations_tmdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Tmdb),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.TmdbEnrichment) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_mdblist_ratings),
                    description = stringResource(Res.string.settings_integrations_mdblist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MdbList),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.MdbListRatings) },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_timestamps),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                TimestampPreferenceRows(isTablet = isTablet)
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_anime_skip),
                    description = stringResource(Res.string.settings_integrations_anime_skip_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.AnimeSkip),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.AnimeSkip) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_introdb),
                    description = stringResource(Res.string.settings_integrations_introdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.IntroDb),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.IntroDb) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_skipdb),
                    description = stringResource(Res.string.settings_integrations_skipdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.SkipDb),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.SkipDb) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_aniskip),
                    description = stringResource(Res.string.settings_integrations_aniskip_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.AniSkip),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.AniSkip) },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_lists),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_myanimelist),
                    description = stringResource(Res.string.settings_integrations_myanimelist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MyAnimeList),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.MyAnimeList) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_kitsu),
                    description = stringResource(Res.string.settings_integrations_kitsu_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Kitsu),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.Kitsu) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_anilist),
                    description = stringResource(Res.string.settings_integrations_anilist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.AniList),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.AniList) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_wetrakr),
                    description = stringResource(Res.string.settings_integrations_wetrakr_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.WeTrakr),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.WeTrakr) },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_subtitles),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_opensubtitles),
                    description = stringResource(Res.string.settings_integrations_opensubtitles_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.OpenSubtitles),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.OpenSubtitles) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_subdl),
                    description = stringResource(Res.string.settings_integrations_subdl_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.SubDl),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.SubDl) },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_extra),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_live_tv),
                    description = stringResource(Res.string.settings_integrations_live_tv_description),
                    icon = Icons.Rounded.LiveTv,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.LiveTv) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_debrid),
                    description = stringResource(Res.string.settings_integrations_debrid_description),
                    icon = Icons.Rounded.Cloud,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.Debrid) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                // The assistant chat is not available on TV: the row stays visible but cannot be opened.
                val aiAssistantAvailable = !TvAppRuntime.isTvApp
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_ai_assistant),
                    description = if (aiAssistantAvailable) {
                        stringResource(Res.string.settings_integrations_ai_assistant_description)
                    } else {
                        stringResource(Res.string.settings_integrations_ai_assistant_tv_unavailable)
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = aiAssistantAvailable,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.AiAssistant) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_telegram),
                    description = stringResource(Res.string.settings_integrations_telegram_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Telegram),
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.Telegram) },
                )
                if (DiscordRpcPlatform.isSupported) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_discord_rpc),
                        description = stringResource(Res.string.settings_integrations_discord_description),
                        iconPainter = integrationLogoPainter(IntegrationLogo.Discord),
                        isTablet = isTablet,
                        onClick = { onPageClick(SettingsPage.DiscordRpc) },
                    )
                }
            }
        }
    }
}
