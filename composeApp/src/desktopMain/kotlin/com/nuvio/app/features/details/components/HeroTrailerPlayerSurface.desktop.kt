package com.nuvio.app.features.details.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import co.touchlab.kermit.Logger
import com.nuvio.app.desktop.mpv.MpvPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val log = Logger.withTag("HeroTrailer")

/**
 * Background trailer of the details hero on desktop: a separate light mpv instance (no cache
 * tuning, cropped to fill the hero like the Android version) drawn like the player surface.
 */
@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)
    val player = remember(sourceUrl, sourceAudioUrl) {
        MpvPlayer.create { created ->
            // Fill the hero (crop), no subtitles, short cache: it is a muted preview.
            created.setProperty("panscan", "1.0")
            created.setProperty("sid", "no")
            created.setProperty("demuxer-max-bytes", "32MiB")
        }
    }
    if (player == null) {
        LaunchedEffect(Unit) { latestOnError.value() }
        return
    }

    var frameTick by remember(player) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        player.onFrame = { frameTick = player.frameNumber() }
        onDispose {
            player.onFrame = null
            player.release()
        }
    }

    LaunchedEffect(player) {
        withContext(Dispatchers.IO) {
            player.load(sourceUrl, sourceAudioUrl, emptyMap(), null) {
                log.i { "Hero trailer loaded" }
            }
        }
        var readyReported = false
        while (isActive) {
            delay(250L)
            val state = withContext(Dispatchers.IO) {
                Triple(player.lastError(), player.frameNumber() > 0, player.flagProperty("eof-reached"))
            }
            val (error, hasFrame, ended) = state
            when {
                error != null -> {
                    log.w { "Hero trailer failed: $error" }
                    latestOnError.value()
                    return@LaunchedEffect
                }
                ended -> {
                    latestOnEnded.value()
                    return@LaunchedEffect
                }
                hasFrame && !readyReported -> {
                    readyReported = true
                    latestOnReady.value()
                }
            }
        }
    }

    LaunchedEffect(player, playWhenReady) {
        player.setProperty("pause", if (playWhenReady) "no" else "yes")
    }
    LaunchedEffect(player, muted) {
        player.setProperty("mute", if (muted) "yes" else "no")
    }

    Canvas(modifier = modifier.onSizeChanged { player.setTargetSize(it.width, it.height) }) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick
        val frame = player.currentFrame() ?: return@Canvas
        if (frame.width <= 0 || frame.height <= 0) return@Canvas
        drawImage(
            image = frame.asComposeImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
