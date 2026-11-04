package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.parser.GenericStreamParser
import com.nuvio.app.features.streams.parser.StreamParserInfo
import com.nuvio.app.features.streams.parser.StreamParserQualityBadge
import com.nuvio.app.features.streams.parser.StreamParserTechChip
import com.nuvio.app.features.streams.parser.StreamParserOutlinedChip
import com.nuvio.app.features.streams.parser.StreamParserDirectChipColor
import com.nuvio.app.features.streams.parser.StreamParserCachedBadge
import com.nuvio.app.features.streams.parser.StreamParserTorrentBadge
import com.nuvio.app.features.streams.parser.StreamParserProxiedBadge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_direct
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("PlayerStreamBadges")

/** Stream Parser badges shown under the title in the player controls. */
internal data class PlayerStreamBadges(
    val info: StreamParserInfo,
    val animationsEnabled: Boolean,
)

/** Logo height in the player controls, scaled with the title size of the layout. */
internal fun PlayerLayoutMetrics.titleLogoHeight(): Dp = (titleSize.value * 1.9f).dp

/**
 * Title logo of the movie/series, with the text title as fallback when there is no logo or it
 * fails to load.
 */
@Composable
internal fun PlayerTitleLogo(
    title: String,
    logo: String?,
    height: Dp,
    fallback: @Composable () -> Unit,
) {
    val logoUrl = logo?.takeIf { it.isNotBlank() }
    var logoLoadError by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl == null || logoLoadError) {
        fallback()
        return
    }
    AsyncImage(
        model = logoUrl,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = Modifier
            .height(height)
            .widthIn(max = height * 6),
        onError = { state ->
            log.w(state.result.throwable) { "Title logo failed to load, falling back to the text title" }
            logoLoadError = true
        },
    )
}

/** Quality badge followed by the technical chips of the playing stream, in a single row. */
@Composable
internal fun PlayerStreamBadgesRow(
    badges: PlayerStreamBadges,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val info = badges.info
        StreamParserQualityBadge(info.resolution, badges.animationsEnabled)
        info.tags.forEach { tag -> StreamParserTechChip(tag) }
        // Same badges as the stream card: "Direct" without tags, then the cached/torrent/proxied status.
        if (info.tags.isEmpty() && !info.isTorrent) {
            StreamParserOutlinedChip(
                label = stringResource(Res.string.stream_parser_direct),
                icon = Icons.Rounded.Link,
                color = StreamParserDirectChipColor,
            )
        }
        if (info.isCached) StreamParserCachedBadge(badges.animationsEnabled)
        if (info.isTorrent) StreamParserTorrentBadge(badges.animationsEnabled)
        if (info.isProxied) StreamParserProxiedBadge(badges.animationsEnabled)
    }
}

/**
 * Episode line ("S1E2 • Title") with the stream badges right after it on the same row. The text
 * gets the [Modifier] passed to [episodeLine] so it shrinks (with ellipsis) before the badges do.
 */
@Composable
internal fun PlayerEpisodeLineWithBadges(
    badges: PlayerStreamBadges?,
    modifier: Modifier = Modifier,
    episodeLine: @Composable (Modifier) -> Unit,
) {
    if (badges == null) {
        episodeLine(Modifier)
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        episodeLine(Modifier.weight(1f, fill = false))
        PlayerStreamBadgesRow(badges)
    }
}

/**
 * Parses the playing stream with the Stream Parser. Without a [StreamItem] (e.g. downloads) a
 * stand-in is built from the stream title, description and filename the player still knows.
 */
internal fun parsePlayerStream(
    stream: StreamItem?,
    streamTitle: String,
    streamSubtitle: String?,
    filename: String?,
    providerName: String,
    providerAddonId: String?,
    knownTitle: String,
): StreamParserInfo {
    val source = stream ?: StreamItem(
        name = streamTitle.takeIf { it.isNotBlank() },
        description = streamSubtitle?.takeIf { it.isNotBlank() },
        addonName = providerName,
        addonId = providerAddonId.orEmpty(),
        behaviorHints = StreamBehaviorHints(filename = filename?.takeIf { it.isNotBlank() }),
    )
    return GenericStreamParser.parse(source, knownTitle).also { info ->
        log.d {
            "Parsed ${if (stream == null) "stand-in" else "active"} stream: " +
                "${info.resolution.label} ${info.tags.joinToString { it.label }}"
        }
    }
}
