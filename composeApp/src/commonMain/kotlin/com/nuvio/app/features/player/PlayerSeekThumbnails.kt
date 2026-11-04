package com.nuvio.app.features.player

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.features.player.seekthumb.SeekThumbnailConfig
import com.nuvio.app.features.player.seekthumb.SeekThumbnailProvider
import com.nuvio.app.features.player.seekthumb.SeekThumbnailRequest
import com.nuvio.app.features.player.seekthumb.createSeekThumbnailProvider
import com.nuvio.app.features.player.seekthumb.seekThumbnailsSupported
import com.nuvio.app.tv.LocalTvShell
import kotlinx.coroutines.delay

/**
 * What the seek bars need to show previews: the stream's provider, its picture width and whether a scrub is under
 * way. [isScrubbing] reads snapshot state, so only the preview recomposes when it changes.
 */
@Stable
internal class SeekThumbnailUi(
    val provider: SeekThumbnailProvider,
    val widthDp: Int,
    val isScrubbing: () -> Boolean,
)

internal val LocalSeekThumbnails = compositionLocalOf<SeekThumbnailUi?> { null }

/**
 * Keeps [PlayerScreenRuntime.seekThumbnailProvider] matched to the playing stream and the settings. No provider for
 * cast, P2P or live playback, nor before the duration is known; the old one is closed whenever the stream changes.
 */
@Composable
internal fun PlayerScreenRuntime.BindSeekThumbnails() {
    val settings = playerSettingsUiState
    val sourceUrl = playerSurfaceSourceUrl
    // The engines report no duration for a moment while they seek: once known, it stays known for this stream,
    // or every seek would close the provider and throw its index and pictures away.
    val knownDurationMs = remember(sourceUrl) { LongArray(1) }
    if (playbackSnapshot.durationMs > 0L) knownDurationMs[0] = playbackSnapshot.durationMs
    val eligible = seekThumbnailsSupported && settings.seekThumbnailsEnabled && sourceUrl != null &&
        castSessionId == null && activeTorrentInfoHash == null && !isLiveTvPlayback &&
        knownDurationMs[0] > 0L
    val config = SeekThumbnailConfig(settings.seekThumbnailSize, settings.seekThumbnailsWifiOnly)
    val headers = activeSourceHeaders
    val responseHeaders = activeSourceResponseHeaders
    val streamType = activeStreamType
    DisposableEffect(eligible, sourceUrl, headers, responseHeaders, streamType, config) {
        val provider = sourceUrl?.takeIf { eligible }?.let { url ->
            createSeekThumbnailProvider(
                SeekThumbnailRequest(url, headers, streamType, knownDurationMs[0], responseHeaders),
                config,
            )
        }
        seekThumbnailProvider = provider
        onDispose {
            provider?.close()
            if (seekThumbnailProvider === provider) seekThumbnailProvider = null
        }
    }
}

@Composable
internal fun PlayerScreenRuntime.rememberSeekThumbnailUi(): SeekThumbnailUi? {
    val provider = seekThumbnailProvider ?: return null
    val widthDp = playerSettingsUiState.seekThumbnailSize.widthDp
    return remember(provider, widthDp) { SeekThumbnailUi(provider, widthDp) { isScrubbingTimeline } }
}

private val PreviewGap = 10.dp

/** On TV the preview stays this long after the seek, while the player gets to the chosen picture. */
private const val TV_PREVIEW_LINGER_MS = 600L
private val PreviewShape = RoundedCornerShape(10.dp)

/**
 * The preview above a seek bar, centred on [positionMs] and kept inside the bar's width. Place it in a Box that holds
 * the bar: it takes no room of its own and draws above the Box's top edge.
 */
@Composable
internal fun BoxScope.SeekThumbnailPreview(
    positionMs: Long,
    durationMs: Long,
    /** Mouse over the bar without pressing (desktop): preview of that position. */
    hoverPositionMs: Long? = null,
) {
    val ui = LocalSeekThumbnails.current ?: return
    val provider = ui.provider
    val dragging = ui.isScrubbing()
    val hovering = !dragging && hoverPositionMs != null
    val scrubbing = dragging || hovering
    LaunchedEffect(provider, hoverPositionMs, dragging) {
        if (!dragging && hoverPositionMs != null) provider.request(hoverPositionMs)
    }
    val tv = LocalTvShell.current != null
    var lingering by remember { mutableStateOf(false) }
    // While it lingers the preview keeps the chosen position: the player may still report the old one.
    val lastScrubMs = remember { LongArray(1) }
    if (scrubbing) lastScrubMs[0] = if (hovering) hoverPositionMs!! else positionMs
    LaunchedEffect(provider, scrubbing) {
        if (scrubbing) {
            lingering = false
            return@LaunchedEffect
        }
        if (tv && provider.frame.value != null) {
            lingering = true
            delay(TV_PREVIEW_LINGER_MS)
            lingering = false
        }
        provider.clearFrame()
    }
    val frame by provider.frame.collectAsState()
    val current = frame
    val shownPositionMs = when {
        hovering -> hoverPositionMs!!
        scrubbing -> positionMs
        else -> lastScrubMs[0]
    }
    val fraction = if (durationMs > 0L) (shownPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                val width = constraints.maxWidth
                layout(width, constraints.maxHeight) {
                    val x = (width * fraction - placeable.width / 2f).toInt()
                        .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                    placeable.place(x, -placeable.height - PreviewGap.roundToPx())
                }
            },
    ) {
        AnimatedVisibility(
            visible = (scrubbing || lingering) && current != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
        ) {
            val shown = current ?: return@AnimatedVisibility
            val image = shown.image
            val widthDp = ui.widthDp
            val heightDp = if (image.width > 0) widthDp * image.height / image.width else widthDp * 9 / 16
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(widthDp.dp, heightDp.dp)
                        .clip(PreviewShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), PreviewShape),
                )
                Text(
                    text = formatPlaybackTime(shownPositionMs),
                    style = MaterialTheme.nuvioTypeScale.labelSm.copy(fontSize = 13.sp),
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Reports the position under the mouse while it moves over a seek bar without a button pressed
 * (null when it leaves): the preview then follows the pointer, like on video sites.
 */
internal fun Modifier.seekBarMouseHover(durationMs: Long, onHover: (Long?) -> Unit): Modifier =
    pointerInput(durationMs) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (change.type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Exit -> onHover(null)
                    PointerEventType.Enter, PointerEventType.Move -> {
                        if (change.pressed || size.width <= 0 || durationMs <= 0L) {
                            onHover(null)
                        } else {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onHover((durationMs * fraction).toLong())
                        }
                    }
                }
            }
        }
    }
