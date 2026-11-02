package com.nuvio.app.features.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.tv.tvNotFocusable
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_ends_at
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val MILLIS_PER_MINUTE = 60_000L

/** Extra lift for bottom overlays (next-episode card, skip button) so they clear the "Ends at" label. */
internal val PlayerEndTimeLabelReservedHeight = 22.dp

/** Current local time in the player controls; refreshed at every minute change. */
@Composable
internal fun PlayerClock(
    use24Hour: Boolean,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_MINUTE - nowMs % MILLIS_PER_MINUTE)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }
    val time = remember(nowMs, use24Hour) {
        val local = kotlin.time.Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(TimeZone.currentSystemDefault())
        formatPlayerClockTime(local.hour, local.minute, use24Hour)
    }
    Text(
        text = time,
        style = MaterialTheme.nuvioTypeScale.titleLg.copy(
            fontSize = metrics.episodeInfoSize * 1.25f,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f),
        ),
        color = Color.White,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * "Ends at HH:MM": local time when playback reaches the end, from the remaining
 * time scaled by the playback speed. Hidden while the duration is unknown.
 */
@Composable
internal fun PlayerEndTimeLabel(
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    use24Hour: Boolean,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L) return
    var minuteTick by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_MINUTE - minuteTick % MILLIS_PER_MINUTE)
            minuteTick = Clock.System.now().toEpochMilliseconds()
        }
    }
    val positionSec = positionMs / 1_000L
    val time = remember(positionSec, durationMs, playbackSpeed, use24Hour, minuteTick) {
        val speed = playbackSpeed.takeIf { it > 0f } ?: 1f
        val remainingMs = ((durationMs - positionMs).coerceAtLeast(0L) / speed).toLong()
        val endMs = Clock.System.now().toEpochMilliseconds() + remainingMs
        val local = kotlin.time.Instant.fromEpochMilliseconds(endMs).toLocalDateTime(TimeZone.currentSystemDefault())
        formatPlayerClockTime(local.hour, local.minute, use24Hour)
    }
    Text(
        text = stringResource(Res.string.player_ends_at, time),
        style = MaterialTheme.nuvioTypeScale.labelSm.copy(
            fontSize = metrics.metadataSize,
            fontWeight = FontWeight.Medium,
        ),
        color = Color.White.copy(alpha = 0.9f),
        maxLines = 1,
        modifier = modifier
            .tvNotFocusable()
            .padding(horizontal = PlayerTimelineContentInset, vertical = 2.dp),
    )
}
