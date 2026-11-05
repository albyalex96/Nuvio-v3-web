package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.WebElementView
import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.web.compat.WebCorsProxy
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLVideoElement

private val log = Logger.withTag("WebPlayer")
private val trackJson = Json { ignoreUnknownKeys = true }

/** Bridge to `nuvio-player.js` (loaded by index.html). */
@JsName("NuvioPlayer")
private external object NuvioPlayerJs {
    fun create(): HTMLVideoElement
    fun load(video: HTMLVideoElement, url: String, streamType: String?, startSeconds: Double, autoplay: Boolean)
    fun release(video: HTMLVideoElement)
    fun play(video: HTMLVideoElement)
    fun pause(video: HTMLVideoElement)
    fun seekTo(video: HTMLVideoElement, seconds: Double)
    fun setRate(video: HTMLVideoElement, rate: Double)
    fun setMuted(video: HTMLVideoElement, muted: Boolean)
    fun setFit(video: HTMLVideoElement, fit: String)
    fun snapshot(video: HTMLVideoElement): String
    fun takeError(video: HTMLVideoElement): String?
    fun audioTracks(video: HTMLVideoElement): String
    fun selectAudioTrack(video: HTMLVideoElement, index: Int)
    fun subtitleTracks(video: HTMLVideoElement): String
    fun selectSubtitleTrack(video: HTMLVideoElement, index: Int)
    fun addVttSubtitle(video: HTMLVideoElement, vttText: String, label: String, lang: String): Int
    fun clearExternalSubtitle(video: HTMLVideoElement)
    fun shiftSubtitles(video: HTMLVideoElement, deltaMs: Int)
}

private fun jsPageIsHttps(): Boolean = js("location.protocol === 'https:'")

@Serializable
private data class JsTrack(
    val id: String,
    val label: String,
    val lang: String = "",
    val selected: Boolean = false,
)

/**
 * HTML5 <video> player for the web. HLS goes through hls.js where the browser has no native
 * support; streams that need headers, use plain http on an https page, or fail because of
 * CORS are played through the CORS proxy. Containers and codecs the browser cannot decode
 * (MKV, HEVC, AC3/DTS on most browsers) are reported as playback errors.
 */
private class WebPlayerController(
    private val video: HTMLVideoElement,
    private val scope: CoroutineScope,
    private val subtitleHeaders: () -> Map<String, String>,
) : PlayerEngineController {
    private var subtitleDelayMs = 0
    var onReload: (() -> Unit)? = null

    override fun play() = NuvioPlayerJs.play(video)
    override fun pause() = NuvioPlayerJs.pause(video)
    override fun seekTo(positionMs: Long) = NuvioPlayerJs.seekTo(video, positionMs / 1000.0)
    override fun seekBy(offsetMs: Long) {
        val current = parseSnapshot(NuvioPlayerJs.snapshot(video)).positionMs
        seekTo((current + offsetMs).coerceAtLeast(0L))
    }
    override fun retry() {
        onReload?.invoke()
    }
    override fun setPlaybackSpeed(speed: Float) = NuvioPlayerJs.setRate(video, speed.toDouble())
    override fun setMuted(muted: Boolean) = NuvioPlayerJs.setMuted(video, muted)

    override fun getAudioTracks(): List<AudioTrack> =
        decodeTracks(NuvioPlayerJs.audioTracks(video)).mapIndexed { index, track ->
            AudioTrack(index = index, id = track.id, label = track.label, language = track.lang.ifBlank { null }, isSelected = track.selected)
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        decodeTracks(NuvioPlayerJs.subtitleTracks(video)).map { track ->
            SubtitleTrack(
                index = track.id.toIntOrNull() ?: 0,
                id = track.id,
                label = track.label,
                language = track.lang.ifBlank { null },
                isSelected = track.selected,
            )
        }

    override fun applyAudioLanguagePreferences(languages: List<String>) {
        val tracks = getAudioTracks()
        val preferred = languages.firstNotNullOfOrNull { language ->
            tracks.firstOrNull { it.language.equals(language, ignoreCase = true) }
        } ?: return
        if (!preferred.isSelected) selectAudioTrack(preferred.index)
    }

    override fun selectAudioTrack(index: Int) = NuvioPlayerJs.selectAudioTrack(video, index)
    override fun selectSubtitleTrack(index: Int) = NuvioPlayerJs.selectSubtitleTrack(video, index)

    override fun setSubtitleUri(url: String) {
        scope.launch {
            val text = runCatching {
                val client = createApiHttpClient()
                try {
                    val response = client.get(url) { subtitleHeaders().forEach { (k, v) -> header(k, v) } }
                    if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
                    response.bodyAsText()
                } finally {
                    client.close()
                }
            }.onFailure { log.e(it) { "Subtitle download failed" } }.getOrNull() ?: return@launch
            val vtt = toWebVtt(text)
            if (vtt == null) {
                log.w { "Subtitle format not supported in the browser (only SRT and WebVTT)" }
                return@launch
            }
            NuvioPlayerJs.addVttSubtitle(video, vtt, "External", "und")
            if (subtitleDelayMs != 0) NuvioPlayerJs.shiftSubtitles(video, subtitleDelayMs)
            log.i { "External subtitle loaded" }
        }
    }

    override fun clearExternalSubtitle() = NuvioPlayerJs.clearExternalSubtitle(video)

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    override fun setSubtitleDelayMs(delayMs: Int) {
        val delta = delayMs - subtitleDelayMs
        if (delta == 0) return
        subtitleDelayMs = delayMs
        NuvioPlayerJs.shiftSubtitles(video, delta)
    }

    private fun decodeTracks(json: String): List<JsTrack> =
        runCatching { trackJson.decodeFromString<List<JsTrack>>(json) }.getOrDefault(emptyList())
}

private fun parseSnapshot(raw: String): PlayerPlaybackSnapshot {
    val parts = raw.split('|')
    fun long(i: Int) = parts.getOrNull(i)?.toDoubleOrNull()?.toLong() ?: 0L
    return PlayerPlaybackSnapshot(
        isLoading = parts.getOrNull(0) == "1",
        isPlaying = parts.getOrNull(1) == "1",
        isEnded = parts.getOrNull(2) == "1",
        durationMs = long(3),
        positionMs = long(4),
        bufferedPositionMs = long(5),
        playbackSpeed = parts.getOrNull(6)?.toFloatOrNull() ?: 1f,
        videoWidth = long(7).toInt(),
        videoHeight = long(8).toInt(),
    )
}

/** SRT or WebVTT text to WebVTT; null for formats the browser cannot render (ASS/SSA...). */
internal fun toWebVtt(text: String): String? {
    val body = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    val trimmed = body.trimStart()
    if (trimmed.startsWith("WEBVTT")) return body
    if (trimmed.startsWith("[Script Info]")) return null
    val timing = Regex("""(\d{1,2}:\d{2}:\d{2}),(\d{1,3})""")
    if (!timing.containsMatchIn(body)) return null
    return "WEBVTT\n\n" + body.replace(timing) { match -> "${match.groupValues[1]}.${match.groupValues[2].padEnd(3, '0')}" }
}

@OptIn(ExperimentalComposeUiApi::class)
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
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnControllerReady = rememberUpdatedState(onControllerReady)
    val latestOnInitialPositionHandled = rememberUpdatedState(onInitialPositionHandled)
    val headers = remember(sourceHeaders) { sanitizePlaybackHeaders(sourceHeaders) }
    val latestHeaders = rememberUpdatedState(headers)
    val video = remember { NuvioPlayerJs.create() }
    val scope = remember { MainScope() }
    val controller = remember(video) { WebPlayerController(video, scope) { latestHeaders.value } }
    var viaProxy by remember(sourceUrl) {
        mutableStateOf(
            headers.isNotEmpty() || (jsPageIsHttps() && sourceUrl.startsWith("http://")),
        )
    }
    var loadGeneration by remember(sourceUrl) { mutableStateOf(0) }

    DisposableEffect(video) {
        onDispose {
            NuvioPlayerJs.release(video)
            scope.cancel()
        }
    }

    LaunchedEffect(resizeMode) {
        NuvioPlayerJs.setFit(
            video,
            when (resizeMode) {
                PlayerResizeMode.Fit -> "contain"
                PlayerResizeMode.Fill -> "fill"
                PlayerResizeMode.Zoom -> "cover"
            },
        )
    }

    LaunchedEffect(sourceUrl, viaProxy, loadGeneration) {
        if (sourceAudioUrl != null) log.w { "Separate audio streams are not supported on the web" }
        val resumeMs = if (loadGeneration == 0) {
            initialPositionMs ?: 0L
        } else {
            parseSnapshot(NuvioPlayerJs.snapshot(video)).positionMs
        }
        val url = if (viaProxy) WebCorsProxy.proxiedUrl(sourceUrl, headers) else sourceUrl
        log.i { "Loading stream (proxy=$viaProxy, type=${streamType ?: "auto"}, start=${resumeMs}ms)" }
        NuvioPlayerJs.load(video, url, streamType, resumeMs / 1000.0, playWhenReady)
        if (loadGeneration == 0 && initialPositionRequestKey != null) {
            latestOnInitialPositionHandled.value(initialPositionRequestKey, (initialPositionMs ?: 0L) > 0L)
        }
        controller.onReload = { loadGeneration++ }
        latestOnControllerReady.value(controller)
        externalSubtitles.firstOrNull()?.let { subtitle ->
            log.d { "Stream provides ${externalSubtitles.size} subtitle(s); loading the first one" }
            controller.setSubtitleUri(subtitle.url)
        }

        while (isActive) {
            delay(250)
            val error = NuvioPlayerJs.takeError(video)
            if (error != null) {
                if (!viaProxy && WebCorsProxy.isConfigured) {
                    log.w { "Direct playback failed ($error): retrying through the CORS proxy" }
                    viaProxy = true
                    return@LaunchedEffect
                }
                log.e { "Playback error: $error" }
                latestOnError.value(
                    "$error. The browser may not support this format (MKV, HEVC, AC3/DTS); try another source.",
                )
            }
            latestOnSnapshot.value(parseSnapshot(NuvioPlayerJs.snapshot(video)))
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        WebElementView(
            factory = { video },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
