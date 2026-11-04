package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.cloud.PremiumizeCloudLibraryPosterUrl
import com.nuvio.app.features.cloud.TorboxCloudLibraryPosterUrl
import com.nuvio.app.features.cloud.cloudLibraryDisplayArtworkUrl
import com.nuvio.app.features.discord.DiscordRpcPlatform
import com.nuvio.app.features.telegram.TelegramSourceResolver
import com.nuvio.app.isDesktop
import com.nuvio.app.isIos
import com.nuvio.app.features.player.seekthumb.seekThumbnailsSupported
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import nuvio.composeapp.generated.resources.settings_licenses_attributions_zxing_body
import nuvio.composeapp.generated.resources.settings_licenses_attributions_zxing_license
import nuvio.composeapp.generated.resources.settings_licenses_attributions_zxing_title
import org.jetbrains.compose.resources.stringResource

private const val TmdbUrl = "https://www.themoviedb.org"
private const val ImdbDatasetsUrl = "https://developer.imdb.com/non-commercial-datasets/"
private const val TraktUrl = "https://trakt.tv"
private const val SimklUrl = "https://simkl.com"
private const val PremiumizeUrl = "https://www.premiumize.me"
private const val TorboxUrl = "https://torbox.app"
private const val MdbListUrl = "https://mdblist.com"
private const val IntroDbUrl = "https://introdb.app/"
private const val SkipDbUrl = "https://skipdb.tv"
private const val MyAnimeListUrl = "https://myanimelist.net"
private const val KitsuUrl = "https://kitsu.app"
private const val WeTrakrUrl = "https://wetrakr.com"
private const val AniListUrl = "https://anilist.co"
private const val TelegramUrl = "https://telegram.org"
private const val TdLibLicenseUrl = "https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt"
private const val AnimeSkipUrl = "https://anime-skip.com"
private const val AniSkipUrl = "https://aniskip.com"
private const val AniSkipLogoLicenseUrl = "https://github.com/aniskip/aniskip-extension/blob/main/LICENSE"
private const val OpenSubtitlesUrl = "https://www.opensubtitles.com"
private const val SubDlUrl = "https://subdl.com"
private const val DiscordUrl = "https://discord.com"
private const val CloudStreamUrl = "https://github.com/recloudstream/cloudstream"
private const val FribbAnimeListsUrl = "https://github.com/Fribb/anime-lists"
private const val KometaAnimeIdsUrl = "https://github.com/Kometa-Team/Anime-IDs"
private const val CerebrasUrl = "https://www.cerebras.ai"
private const val GroqUrl = "https://groq.com"
private const val GeminiUrl = "https://ai.google.dev"
private const val OpenRouterUrl = "https://openrouter.ai"
private const val TavilyUrl = "https://tavily.com"
private const val NuvioRepositoryUrl = "https://github.com/NuvioMedia/NuvioMobile"
private const val NuvioArcRepositoryUrl = "https://github.com/ysosrs123/NuvioTV-Fork"
private const val MpvKitUrl = "https://github.com/mpvkit/MPVKit"
private const val MpvUrl = "https://github.com/mpv-player/mpv"
private const val FfmpegPresetsUrl = "https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg"
private const val JmdnsUrl = "https://github.com/jmdns/jmdns"
private const val ApacheLicenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
private const val HazeLicenseUrl = "https://github.com/chrisbanes/haze/blob/1.7.2/LICENSE"
private const val DnsOverHttpsRfcUrl = "https://datatracker.ietf.org/doc/html/rfc8484"
private const val OkHttpLicenseUrl = "https://github.com/square/okhttp/blob/master/LICENSE.txt"
private const val ZxingLicenseUrl = "https://github.com/zxing/zxing/blob/master/LICENSE"

/** ZXing creates (TV) and reads (phone, Android full) the TV remote pairing QR code. */
internal fun tvRemoteLicenseVisible(): Boolean =
    com.nuvio.app.tv.TvAppRuntime.isTvApp || com.nuvio.app.features.tvremote.PhoneTvRemote.activeClient != null

private data class AttributionItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val logo: IntegrationLogo?,
    val logoUrl: String? = null,
    val link: String,
)

private data class LicenseItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val licenseRes: StringResource,
    val link: String,
)

@Composable
fun LicensesAttributionsSettingsScreen(
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    SettingsSearchHighlightHost(page = SettingsPage.LicensesAttributions, listState = listState) {
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            listState = listState,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_licenses_attributions),
                    onBack = onBack,
                )
            }
            licensesAttributionsContent(isTablet = false)
        }
    }
}

internal fun LazyListScope.licensesAttributionsContent(
    isTablet: Boolean,
) {
    item {
        LicensesAttributionsBody(isTablet = isTablet)
    }
}

@Composable
private fun LicensesAttributionsBody(
    isTablet: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 24.dp),
    ) {
        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_app),
            isTablet = isTablet,
        ) {
            LicenseRow(
                item = appLicenseItem(),
                isTablet = isTablet,
            )
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_data),
            isTablet = isTablet,
        ) {
            val items = attributionItems()
            items.forEachIndexed { index, item ->
                AttributionRow(
                    item = item,
                    isTablet = isTablet,
                )
                if (index != items.lastIndex) {
                    PlainStackDivider()
                }
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_ai),
            isTablet = isTablet,
        ) {
            val items = aiAttributionItems()
            items.forEachIndexed { index, item ->
                AttributionRow(
                    item = item,
                    isTablet = isTablet,
                )
                if (index != items.lastIndex) {
                    PlainStackDivider()
                }
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_playback),
            isTablet = isTablet,
        ) {
            LicenseRow(
                item = platformLicenseItem(),
                isTablet = isTablet,
            )
            if (isDesktop) {
                PlainStackDivider()
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_ffmpeg_title,
                        bodyRes = Res.string.settings_licenses_attributions_ffmpeg_body,
                        licenseRes = Res.string.settings_licenses_attributions_ffmpeg_license,
                        link = FfmpegPresetsUrl,
                    ),
                    isTablet = isTablet,
                )
                PlainStackDivider()
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_jmdns_title,
                        bodyRes = Res.string.settings_licenses_attributions_jmdns_body,
                        licenseRes = Res.string.settings_licenses_attributions_jmdns_license,
                        link = JmdnsUrl,
                    ),
                    isTablet = isTablet,
                )
            }
            // Nuvio Arc is the Android preview decoder; desktop previews use FFmpeg.
            if (seekThumbnailsSupported && !isDesktop) {
                PlainStackDivider()
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_nuvio_arc_title,
                        bodyRes = Res.string.settings_licenses_attributions_nuvio_arc_body,
                        licenseRes = Res.string.settings_licenses_attributions_nuvio_arc_license,
                        link = NuvioArcRepositoryUrl,
                    ),
                    isTablet = isTablet,
                )
            }
            if (TelegramSourceResolver.isSupported) {
                PlainStackDivider()
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_tdlib_title,
                        bodyRes = Res.string.settings_licenses_attributions_tdlib_body,
                        licenseRes = Res.string.settings_licenses_attributions_tdlib_license,
                        link = TdLibLicenseUrl,
                    ),
                    isTablet = isTablet,
                )
            }
            if (tvRemoteLicenseVisible()) {
                PlainStackDivider()
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_zxing_title,
                        bodyRes = Res.string.settings_licenses_attributions_zxing_body,
                        licenseRes = Res.string.settings_licenses_attributions_zxing_license,
                        link = ZxingLicenseUrl,
                    ),
                    isTablet = isTablet,
                )
            }
        }

        if (dnsOverHttpsSettingsVisible) {
            PlainSettingsStack(
                title = stringResource(Res.string.settings_licenses_attributions_section_network),
                isTablet = isTablet,
            ) {
                LicenseRow(
                    item = LicenseItem(
                        titleRes = Res.string.settings_licenses_attributions_okhttp_title,
                        bodyRes = Res.string.settings_licenses_attributions_okhttp_body,
                        licenseRes = Res.string.settings_licenses_attributions_okhttp_license,
                        link = OkHttpLicenseUrl,
                    ),
                    isTablet = isTablet,
                )
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_ui),
            isTablet = isTablet,
        ) {
            LicenseRow(
                item = LicenseItem(
                    titleRes = Res.string.settings_licenses_attributions_haze_title,
                    bodyRes = Res.string.settings_licenses_attributions_haze_body,
                    licenseRes = Res.string.settings_licenses_attributions_haze_license,
                    link = HazeLicenseUrl,
                ),
                isTablet = isTablet,
            )
            PlainStackDivider()
            LicenseRow(
                item = LicenseItem(
                    titleRes = Res.string.settings_licenses_attributions_aniskip_logo_title,
                    bodyRes = Res.string.settings_licenses_attributions_aniskip_logo_body,
                    licenseRes = Res.string.settings_licenses_attributions_aniskip_logo_license,
                    link = AniSkipLogoLicenseUrl,
                ),
                isTablet = isTablet,
            )
        }
    }
}

@Composable
private fun PlainSettingsStack(
    title: String,
    isTablet: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun AttributionRow(
    item: AttributionItem,
    isTablet: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val title = stringResource(item.titleRes)
    LinkedPlainRow(
        title = title,
        body = stringResource(item.bodyRes),
        link = item.link,
        isTablet = isTablet,
        leading = when {
            item.logo != null -> item.logo.let { logo ->
                {
                    IntegrationLogoImage(
                        painter = integrationLogoPainter(logo),
                        contentDescription = title,
                        isTablet = isTablet,
                    )
                }
            }
            item.logoUrl != null -> item.logoUrl.let { logoUrl ->
                {
                    ProviderLogoImage(
                        url = logoUrl,
                        contentDescription = title,
                        isTablet = isTablet,
                    )
                }
            }
            else -> null
        },
        onOpen = { uriHandler.openUri(item.link) },
    )
}

@Composable
private fun LicenseRow(
    item: LicenseItem,
    isTablet: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val itemBody = stringResource(item.bodyRes)
    val itemLicense = stringResource(item.licenseRes)
    val body = buildString {
        append(itemBody)
        append("\n")
        append(itemLicense)
    }
    LinkedPlainRow(
        title = stringResource(item.titleRes),
        body = body,
        link = item.link,
        isTablet = isTablet,
        onOpen = { uriHandler.openUri(item.link) },
    )
}

@Composable
private fun LinkedPlainRow(
    title: String,
    body: String,
    link: String,
    isTablet: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val verticalPadding = if (isTablet) 18.dp else 16.dp
    val horizontalPadding = if (isTablet) 4.dp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchHighlight(title)
            .clickable(onClick = onOpen)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = link,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(if (isTablet) 22.dp else 20.dp)
                .alpha(0.72f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntegrationLogoImage(
    painter: Painter,
    contentDescription: String,
    isTablet: Boolean,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(top = 2.dp)
            .size(if (isTablet) 46.dp else 40.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ProviderLogoImage(
    url: String,
    contentDescription: String,
    isTablet: Boolean,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(top = 2.dp)
            .size(if (isTablet) 46.dp else 40.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PlainStackDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    )
}

private fun attributionItems(): List<AttributionItem> = listOf(
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_tmdb_title,
        bodyRes = Res.string.settings_licenses_attributions_tmdb_body,
        logo = IntegrationLogo.Tmdb,
        link = TmdbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_trakt_title,
        bodyRes = Res.string.settings_licenses_attributions_trakt_body,
        logo = IntegrationLogo.Trakt,
        link = TraktUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_simkl_title,
        bodyRes = Res.string.settings_licenses_attributions_simkl_body,
        logo = IntegrationLogo.Simkl,
        link = SimklUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_premiumize_title,
        bodyRes = Res.string.settings_licenses_attributions_premiumize_body,
        logo = null,
        logoUrl = PremiumizeCloudLibraryPosterUrl,
        link = PremiumizeUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_torbox_title,
        bodyRes = Res.string.settings_licenses_attributions_torbox_body,
        logo = null,
        logoUrl = cloudLibraryDisplayArtworkUrl(TorboxCloudLibraryPosterUrl),
        link = TorboxUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_mdblist_title,
        bodyRes = Res.string.settings_licenses_attributions_mdblist_body,
        logo = IntegrationLogo.MdbList,
        link = MdbListUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_introdb_title,
        bodyRes = Res.string.settings_licenses_attributions_introdb_body,
        logo = IntegrationLogo.IntroDb,
        link = IntroDbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_skipdb_title,
        bodyRes = Res.string.settings_licenses_attributions_skipdb_body,
        logo = IntegrationLogo.SkipDb,
        link = SkipDbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_imdb_title,
        bodyRes = Res.string.settings_licenses_attributions_imdb_body,
        logo = null,
        link = ImdbDatasetsUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_myanimelist_title,
        bodyRes = Res.string.settings_licenses_attributions_myanimelist_body,
        logo = IntegrationLogo.MyAnimeList,
        link = MyAnimeListUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_kitsu_title,
        bodyRes = Res.string.settings_licenses_attributions_kitsu_body,
        logo = IntegrationLogo.Kitsu,
        link = KitsuUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_anilist_title,
        bodyRes = Res.string.settings_licenses_attributions_anilist_body,
        logo = IntegrationLogo.AniList,
        link = AniListUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_wetrakr_title,
        bodyRes = Res.string.settings_licenses_attributions_wetrakr_body,
        logo = IntegrationLogo.WeTrakr,
        link = WeTrakrUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_fribb_title,
        bodyRes = Res.string.settings_licenses_attributions_fribb_body,
        logo = null,
        link = FribbAnimeListsUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_kometa_title,
        bodyRes = Res.string.settings_licenses_attributions_kometa_body,
        logo = null,
        link = KometaAnimeIdsUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_animeskip_title,
        bodyRes = Res.string.settings_licenses_attributions_animeskip_body,
        logo = IntegrationLogo.AnimeSkip,
        link = AnimeSkipUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_aniskip_title,
        bodyRes = Res.string.settings_licenses_attributions_aniskip_body,
        logo = IntegrationLogo.AniSkip,
        link = AniSkipUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_opensubtitles_title,
        bodyRes = Res.string.settings_licenses_attributions_opensubtitles_body,
        logo = IntegrationLogo.OpenSubtitles,
        link = OpenSubtitlesUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_subdl_title,
        bodyRes = Res.string.settings_licenses_attributions_subdl_body,
        logo = IntegrationLogo.SubDl,
        link = SubDlUrl,
    ),
) + listOfNotNull(
    // Same visibility as the CloudStream and Discord entries in the settings.
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_cloudstream_title,
        bodyRes = Res.string.settings_licenses_attributions_cloudstream_body,
        logo = IntegrationLogo.Cloudstream,
        link = CloudStreamUrl,
    ).takeIf { AppFeaturePolicy.pluginsEnabled },
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_discord_title,
        bodyRes = Res.string.settings_licenses_attributions_discord_body,
        logo = IntegrationLogo.Discord,
        link = DiscordUrl,
    ).takeIf { DiscordRpcPlatform.isSupported },
    // Same visibility as the Telegram page (Android full distribution only).
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_telegram_title,
        bodyRes = Res.string.settings_licenses_attributions_telegram_body,
        logo = IntegrationLogo.Telegram,
        link = TelegramUrl,
    ).takeIf { TelegramSourceResolver.isSupported },
    // Same visibility as the DNS over HTTPS option (Settings → Advanced → Network).
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_doh_title,
        bodyRes = Res.string.settings_licenses_attributions_doh_body,
        logo = null,
        link = DnsOverHttpsRfcUrl,
    ).takeIf { dnsOverHttpsSettingsVisible },
)

private fun aiAttributionItems(): List<AttributionItem> = listOf(
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_cerebras_title,
        bodyRes = Res.string.settings_licenses_attributions_cerebras_body,
        logo = null,
        link = CerebrasUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_groq_title,
        bodyRes = Res.string.settings_licenses_attributions_groq_body,
        logo = null,
        link = GroqUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_gemini_title,
        bodyRes = Res.string.settings_licenses_attributions_gemini_body,
        logo = null,
        link = GeminiUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_openrouter_title,
        bodyRes = Res.string.settings_licenses_attributions_openrouter_body,
        logo = null,
        link = OpenRouterUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_tavily_title,
        bodyRes = Res.string.settings_licenses_attributions_tavily_body,
        logo = null,
        link = TavilyUrl,
    ),
)

private fun appLicenseItem(): LicenseItem =
    LicenseItem(
        titleRes = Res.string.settings_licenses_attributions_nuvio_title,
        bodyRes = Res.string.settings_licenses_attributions_nuvio_body,
        licenseRes = Res.string.settings_licenses_attributions_nuvio_license,
        link = NuvioRepositoryUrl,
    )

private fun platformLicenseItem(): LicenseItem =
    if (isDesktop) {
        LicenseItem(
            titleRes = Res.string.settings_licenses_attributions_libmpv_title,
            bodyRes = Res.string.settings_licenses_attributions_libmpv_body,
            licenseRes = Res.string.settings_licenses_attributions_libmpv_license,
            link = MpvUrl,
        )
    } else if (isIos) {
        LicenseItem(
            titleRes = Res.string.settings_licenses_attributions_mpvkit_title,
            bodyRes = Res.string.settings_licenses_attributions_mpvkit_body,
            licenseRes = Res.string.settings_licenses_attributions_mpvkit_license,
            link = MpvKitUrl,
        )
    } else {
        LicenseItem(
            titleRes = Res.string.settings_licenses_attributions_exoplayer_title,
            bodyRes = Res.string.settings_licenses_attributions_exoplayer_body,
            licenseRes = Res.string.settings_licenses_attributions_exoplayer_license,
            link = ApacheLicenseUrl,
        )
    }
