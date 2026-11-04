package com.nuvio.app.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_error_mpv_unavailable
import org.jetbrains.compose.resources.getString

private const val TAG = "DesktopPlayer"

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
    streamType: String?,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    resizeMode: PlayerResizeMode,
    playbackEngine: AndroidPlaybackEngine?,
    useNativeController: Boolean,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val latestOnControllerReady = rememberUpdatedState(onControllerReady)
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnInitialPositionHandled = rememberUpdatedState(onInitialPositionHandled)
    val latestPlayWhenReady = rememberUpdatedState(playWhenReady)

    val player = remember { MpvPlayer.create() }
    if (player == null) {
        LaunchedEffect(Unit) {
            latestOnError.value(getString(Res.string.player_error_mpv_unavailable))
        }
        Box(modifier.background(Color.Black))
        return
    }

    var frameTick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        player.onFrame = { frameTick = player.frameNumber() }
        onDispose {
            player.onFrame = null
            player.release()
        }
    }

    val controller = remember(player) { DesktopMpvController(player) }

    LaunchedEffect(controller, sourceUrl, sourceAudioUrl, sourceHeaders) {
        latestOnControllerReady.value(controller)
    }

    LaunchedEffect(player, sourceUrl, sourceAudioUrl, sourceHeaders, externalSubtitles) {
        val requestKey = initialPositionRequestKey
        val startMs = initialPositionMs?.takeIf { it > 0L }
        player.setProperty("pause", if (latestPlayWhenReady.value) "no" else "yes")
        withContext(Dispatchers.IO) {
            player.load(
                url = sourceUrl,
                audioUrl = sourceAudioUrl,
                headers = sanitizePlaybackHeaders(sourceHeaders),
                startSeconds = startMs?.let { it / 1000.0 },
            ) {
                externalSubtitles.forEach { subtitle ->
                    player.command("sub-add", subtitle.url, "auto", subtitle.name ?: subtitle.language, subtitle.language)
                }
                if (requestKey != null) latestOnInitialPositionHandled.value(requestKey, startMs != null)
            }
        }
    }

    LaunchedEffect(player, playWhenReady) {
        player.setProperty("pause", if (playWhenReady) "no" else "yes")
    }

    LaunchedEffect(player, resizeMode) {
        when (resizeMode) {
            PlayerResizeMode.Fit -> {
                player.setProperty("keepaspect", "yes")
                player.setProperty("panscan", "0.0")
            }
            PlayerResizeMode.Fill -> {
                player.setProperty("keepaspect", "no")
                player.setProperty("panscan", "0.0")
            }
            PlayerResizeMode.Zoom -> {
                player.setProperty("keepaspect", "yes")
                player.setProperty("panscan", "1.0")
            }
        }
    }

    LaunchedEffect(player) {
        var lastReportedError: String? = null
        var chapters = emptyList<PlayerChapter>()
        var chaptersDurationMs = 0L
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val durationMs = ((player.doubleProperty("duration") ?: 0.0) * 1000).toLong()
                if (durationMs != chaptersDurationMs) {
                    chaptersDurationMs = durationMs
                    chapters = if (durationMs > 0L) {
                        runCatching { mpvChapters(player::getProperty) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
                val positionMs = ((player.doubleProperty("time-pos") ?: 0.0) * 1000).toLong()
                val cacheMs = ((player.doubleProperty("demuxer-cache-time") ?: 0.0) * 1000).toLong()
                val paused = player.flagProperty("pause")
                val buffering = player.flagProperty("paused-for-cache") || player.flagProperty("seeking")
                val idle = player.flagProperty("idle-active")
                PlayerPlaybackSnapshot(
                    isLoading = buffering || (durationMs <= 0L && !idle && player.lastError() == null),
                    isPlaying = !paused && !idle,
                    isEnded = player.flagProperty("eof-reached"),
                    durationMs = durationMs,
                    positionMs = positionMs,
                    bufferedPositionMs = maxOf(positionMs, cacheMs),
                    playbackSpeed = player.doubleProperty("speed")?.toFloat() ?: 1f,
                    videoWidth = player.getProperty("video-params/w")?.toIntOrNull() ?: 0,
                    videoHeight = player.getProperty("video-params/h")?.toIntOrNull() ?: 0,
                    chapters = chapters,
                )
            }
            latestOnSnapshot.value(snapshot)
            val errorMessage = player.lastError()
            if (errorMessage != lastReportedError) {
                lastReportedError = errorMessage
                latestOnError.value(errorMessage)
            }
            delay(250L)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> player.setTargetSize(size.width, size.height) },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            frameTick // read to redraw on each new frame
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
}

/** Maps the shared player controller API onto mpv commands and properties. */
private class DesktopMpvController(private val player: MpvPlayer) : PlayerEngineController {
    private val log = Logger.withTag(TAG)

    override fun play() {
        player.setProperty("pause", "no")
    }

    override fun pause() {
        player.setProperty("pause", "yes")
    }

    override fun seekTo(positionMs: Long) {
        player.command("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute")
    }

    override fun seekBy(offsetMs: Long) {
        player.command("seek", (offsetMs / 1000.0).toString(), "relative")
    }

    override fun retry() {
        val path = player.getProperty("path") ?: return
        val position = player.getProperty("time-pos")
        log.i { "Retrying playback" }
        player.setProperty("start", position?.let { "+$it" } ?: "none")
        player.command("loadfile", path, "replace")
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.setProperty("speed", speed.toString())
    }

    override fun setMuted(muted: Boolean) {
        player.setProperty("mute", if (muted) "yes" else "no")
    }

    private data class MpvTrack(
        val id: String,
        val type: String,
        val title: String?,
        val lang: String?,
        val selected: Boolean,
        val forced: Boolean,
        val external: Boolean,
        val codec: String?,
    )

    private fun tracks(type: String): List<MpvTrack> {
        val count = player.getProperty("track-list/count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val trackType = player.getProperty("track-list/$i/type") ?: return@mapNotNull null
            if (trackType != type) return@mapNotNull null
            MpvTrack(
                id = player.getProperty("track-list/$i/id") ?: return@mapNotNull null,
                type = trackType,
                title = player.getProperty("track-list/$i/title"),
                lang = player.getProperty("track-list/$i/lang"),
                selected = player.getProperty("track-list/$i/selected") == "yes",
                forced = player.getProperty("track-list/$i/forced") == "yes",
                external = player.getProperty("track-list/$i/external") == "yes",
                codec = player.getProperty("track-list/$i/codec"),
            )
        }
    }

    private fun MpvTrack.label(): String =
        listOfNotNull(title, lang?.uppercase(), codec?.uppercase())
            .distinct()
            .joinToString(" · ")
            .ifBlank { "Track $id" }

    override fun getAudioTracks(): List<AudioTrack> =
        tracks("audio").mapIndexed { index, track ->
            AudioTrack(index = index, id = track.id, label = track.label(), language = track.lang, isSelected = track.selected)
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        tracks("sub").mapIndexed { index, track ->
            SubtitleTrack(
                index = index,
                id = track.id,
                label = track.label(),
                language = track.lang,
                isSelected = track.selected,
                isForced = track.forced || inferForcedSubtitleTrack(label = track.label(), language = track.lang, trackId = track.id),
            )
        }

    override fun applyAudioLanguagePreferences(languages: List<String>) {
        if (languages.isNotEmpty()) player.setProperty("alang", languages.joinToString(","))
    }

    override fun selectAudioTrack(index: Int) {
        tracks("audio").getOrNull(index)?.let { player.setProperty("aid", it.id) }
    }

    override fun selectSubtitleTrack(index: Int) {
        if (index < 0) {
            player.setProperty("sid", "no")
        } else {
            tracks("sub").getOrNull(index)?.let { player.setProperty("sid", it.id) }
        }
    }

    override fun setSubtitleUri(url: String) {
        player.command("sub-add", url, "select")
    }

    override fun clearExternalSubtitle() {
        tracks("sub").filter { it.external }.forEach { player.command("sub-remove", it.id) }
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    override fun applySubtitleStyle(style: SubtitleStyleState) {
        player.setProperty("sub-color", style.textColor.toMpvColor())
        player.setProperty("sub-back-color", style.backgroundColor.toMpvColor())
        player.setProperty("sub-border-color", style.outlineColor.toMpvColor())
        player.setProperty("sub-border-size", if (style.outlineEnabled) style.outlineWidth.toString() else "0")
        player.setProperty("sub-bold", if (style.bold) "yes" else "no")
        player.setProperty("sub-font-size", (style.fontSizeSp * 3f).coerceIn(18f, 96f).toString())
        player.setProperty("sub-pos", (100 - style.bottomOffset / 2).coerceIn(0, 150).toString())
        player.setProperty("sub-filter-sdh", if (style.stripSdh) "yes" else "no")
    }

    override fun setSubtitleDelayMs(delayMs: Int) {
        player.setProperty("sub-delay", (delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS) / 1000.0).toString())
    }

    override fun setAudioDelayMs(delayMs: Int) {
        player.setProperty("audio-delay", (delayMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS) / 1000.0).toString())
    }

    override suspend fun getMediaInfo(): PlayerMediaInfo = withContext(Dispatchers.IO) {
        mpvMediaInfo { name -> player.getProperty(name)?.ifBlank { null } }
    }

    private fun Color.toMpvColor(): String {
        fun Float.hex() = (this * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
        return "#${alpha.hex()}${red.hex()}${green.hex()}${blue.hex()}"
    }
}
