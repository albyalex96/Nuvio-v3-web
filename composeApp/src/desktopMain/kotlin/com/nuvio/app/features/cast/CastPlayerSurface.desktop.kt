package com.nuvio.app.features.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import co.touchlab.kermit.Logger
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.core.mediaproxy.MediaProxyHandle
import com.nuvio.app.core.mediaproxy.MediaProxyOptions
import com.nuvio.app.core.mediaproxy.MediaProxyScope
import com.nuvio.app.core.mediaproxy.MediaProxySettingsRepository
import com.nuvio.app.core.mediaproxy.MediaProxySource
import com.nuvio.app.desktop.cast.CastMessage
import com.nuvio.app.desktop.cast.NS_MEDIA
import com.nuvio.app.desktop.cast.string
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.languageMatchesPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cast_lan_unavailable
import nuvio.composeapp.generated.resources.cast_load_failed
import nuvio.composeapp.generated.resources.cast_load_failed_with_issues
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("Cast")

private const val PUBLISH_INTERVAL_MS = 500L
private const val STATUS_POLL_MS = 2_000L
private const val MIN_PLAYBACK_RATE = 0.5
private const val MAX_PLAYBACK_RATE = 2.0

@Composable
internal actual fun CastPlayerSurface(
    request: CastMediaRequest,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val loadFailed = if (request.compatibilityIssues.isEmpty()) {
        stringResource(Res.string.cast_load_failed)
    } else {
        stringResource(Res.string.cast_load_failed_with_issues, request.compatibilityIssues.joinToString(", "))
    }
    val lanUnavailable = stringResource(Res.string.cast_lan_unavailable)
    val scope = rememberCoroutineScope()
    val latestPlayWhenReady by rememberUpdatedState(playWhenReady)
    val latestOnSnapshot by rememberUpdatedState(onSnapshot)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnInitialPositionHandled by rememberUpdatedState(onInitialPositionHandled)
    val latestOnControllerReady by rememberUpdatedState(onControllerReady)
    var playback by remember { mutableStateOf<DesktopCastPlayback?>(null) }

    DisposableEffect(request.sourceUrl) {
        val session = CastPlatform.currentSession()
        if (session == null) {
            log.w { "No connected cast session for the cast surface" }
            latestOnError(loadFailed)
            return@DisposableEffect onDispose {}
        }
        val remote = DesktopCastPlayback(
            session = session,
            request = request,
            scope = scope,
            loadFailedMessage = loadFailed,
            lanUnavailableMessage = lanUnavailable,
            onSnapshot = { latestOnSnapshot(it) },
            onError = { latestOnError(it) },
            onLoaded = { initialPositionRequestKey?.let { key -> latestOnInitialPositionHandled(key, true) } },
        )
        playback = remote
        remote.start(initialPositionMs?.coerceAtLeast(0L) ?: 0L, latestPlayWhenReady)
        onDispose {
            remote.release()
            playback = null
        }
    }

    LaunchedEffect(playback) {
        val remote = playback ?: return@LaunchedEffect
        // Hand the controller over after the runtime cleared the previous one (see Android).
        withFrameNanos { }
        latestOnControllerReady(remote)
    }

    LaunchedEffect(playback, playWhenReady) {
        playback?.applyPlayWhenReady(playWhenReady)
    }
}

private data class ReceiverTrack(val id: Long, val language: String?, val name: String?)

/**
 * One stream on the Default Media Receiver, driven through the media namespace. The receiver
 * reports its position only on status changes, so it is extrapolated while playing.
 */
private class DesktopCastPlayback(
    private val session: DesktopCastSession,
    private val request: CastMediaRequest,
    private val scope: CoroutineScope,
    private val loadFailedMessage: String,
    private val lanUnavailableMessage: String,
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String) -> Unit,
    private val onLoaded: () -> Unit,
) : PlayerEngineController {
    private val channel = session.channel
    private val destination = session.transportId

    @Volatile private var released = false
    @Volatile private var loaded = false
    @Volatile private var errorReported = false
    @Volatile private var loadRequestId = -1
    @Volatile private var mediaSessionId: Int? = null
    @Volatile private var playerState = "IDLE"
    @Volatile private var idleReason: String? = null
    @Volatile private var positionSeconds = 0.0
    @Volatile private var positionAtNanos = System.nanoTime()
    @Volatile private var durationSeconds = 0.0
    @Volatile private var playbackRate = 1.0
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var tracks: List<ReceiverTrack> = emptyList()
    @Volatile private var activeTrackIds: Set<Long> = emptySet()
    private var preferredAudioLanguages: List<String> = emptyList()
    private var audioPreferenceApplied = false
    private var userSelectedAudio = false
    private var publisher: Job? = null
    @Volatile private var proxyHandle: MediaProxyHandle? = null

    /** What the receiver plays: the original link, or the LAN proxy one. */
    @Volatile private var loadedContentUrl: String = request.sourceUrl

    private val listener: (CastMessage) -> Unit = { message -> if (message.namespace == NS_MEDIA) onMediaMessage(message) }

    fun start(startPositionMs: Long, autoplay: Boolean) {
        channel.addListener(listener)
        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        load(startPositionMs, autoplay)
        publisher = scope.launch(Dispatchers.Default) {
            var sincePoll = 0L
            while (isActive && !released) {
                delay(PUBLISH_INTERVAL_MS)
                sincePoll += PUBLISH_INTERVAL_MS
                if (sincePoll >= STATUS_POLL_MS) {
                    sincePoll = 0L
                    mediaCommand("GET_STATUS", includeSession = false)
                }
                if (loaded) onSnapshot(snapshot())
            }
        }
    }

    fun release() {
        released = true
        publisher?.cancel()
        channel.removeListener(listener)
        if (loaded && channel.isOpen) {
            log.i { "Stopping the stream on the receiver" }
            mediaCommand("STOP")
        }
        releaseProxy()
    }

    /**
     * Local files and links that need headers cannot be opened by the receiver: this PC serves
     * them on the LAN (same proxy as Android), adding the headers.
     */
    private fun registerProxy(): MediaProxyHandle? {
        releaseProxy()
        val url = request.sourceUrl.trim()
        val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        val source = if (isHttp) {
            MediaProxySource.Remote(url, request.requestHeaders, request.responseHeaders)
        } else {
            MediaProxySource.LocalFile(url)
        }
        return LanMediaProxy.register(source, MediaProxyOptions(scope = MediaProxyScope.Lan, label = "Cast"))
            .also { proxyHandle = it }
    }

    private fun releaseProxy() {
        proxyHandle?.let(LanMediaProxy::unregister)
        proxyHandle = null
    }

    fun applyPlayWhenReady(playWhenReady: Boolean) {
        if (!loaded || released) return
        if (playWhenReady && playerState == "PAUSED") play()
        if (!playWhenReady && (playerState == "PLAYING" || playerState == "BUFFERING")) pause()
    }

    private fun load(startPositionMs: Long, autoplay: Boolean) {
        loaded = false
        errorReported = false
        scope.launch(Dispatchers.Default) {
            val contentType = castContentTypeFor(request) ?: probeCastContentType(request)
            val hlsFormats = if (contentType == "application/x-mpegurl") {
                runCatching { detectCastHlsFormats(request.sourceUrl, request.requestHeaders) }.getOrNull()
            } else {
                null
            }
            if (released) return@launch
            val useProxy = request.needsLanProxy || MediaProxySettingsRepository.settings.value.castAlwaysProxy
            val contentUrl = if (useProxy) {
                val handle = registerProxy()
                if (released) {
                    releaseProxy()
                    return@launch
                }
                if (handle == null) {
                    log.w { "The stream needs the LAN proxy but this PC has no local network address" }
                    errorReported = true
                    onError(lanUnavailableMessage)
                    return@launch
                }
                handle.url
            } else {
                request.sourceUrl
            }
            loadedContentUrl = contentUrl
            log.i { "Loading on the receiver: type=$contentType live=${request.isLive} proxied=$useProxy start=${startPositionMs}ms autoplay=$autoplay" }
            val requestId = channel.newRequestId()
            loadRequestId = requestId
            channel.send(
                NS_MEDIA,
                destination,
                buildJsonObject {
                    put("type", "LOAD")
                    put("requestId", requestId)
                    put("sessionId", session.sessionId)
                    put("autoplay", autoplay)
                    put("currentTime", if (request.isLive) 0.0 else startPositionMs / 1000.0)
                    putJsonObject("media") {
                        put("contentId", contentUrl)
                        put("contentUrl", contentUrl)
                        put("contentType", contentType)
                        put("streamType", if (request.isLive) "LIVE" else "BUFFERED")
                        hlsFormats?.let { formats ->
                            put("hlsVideoSegmentFormat", if (formats.video == CastHlsSegmentFormat.Fmp4) "fmp4" else "mpeg2_ts")
                            put(
                                "hlsSegmentFormat",
                                when (val audio = formats.audio) {
                                    null -> formats.video.castName()
                                    CastHlsSegmentFormat.Ts -> "ts_aac"
                                    else -> audio.castName()
                                },
                            )
                        }
                        putJsonObject("metadata") { putMetadata() }
                    }
                },
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putMetadata() {
        val isEpisode = request.seasonNumber != null && request.episodeNumber != null
        put("metadataType", if (isEpisode) 2 else 1)
        if (isEpisode) {
            put("seriesTitle", request.title)
            put("title", request.episodeTitle?.takeIf { it.isNotBlank() } ?: request.title)
            put("season", request.seasonNumber)
            put("episode", request.episodeNumber)
        } else {
            put("title", request.title)
        }
        request.subtitle?.takeIf { it.isNotBlank() }?.let { put("subtitle", it) }
        putJsonArray("images") {
            listOfNotNull(request.posterUrl, request.backdropUrl)
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .forEach { url -> addJsonObject { put("url", url) } }
        }
    }

    private fun onMediaMessage(message: CastMessage) {
        if (released) return
        when (message.type) {
            "MEDIA_STATUS" -> {
                val status = (message.payload["status"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return
                val contentId = ((status["media"] as? JsonObject)?.string("contentId"))
                if (contentId != null && contentId != loadedContentUrl) {
                    // Another sender replaced the stream.
                    loaded = false
                    onSnapshot(PlayerPlaybackSnapshot(isLoading = false, isPlaying = false))
                    return
                }
                applyStatus(status)
                if (!loaded && mediaSessionId != null && (message.requestId == loadRequestId || playerState != "IDLE")) {
                    loaded = true
                    log.i { "Receiver loaded the stream" }
                    onLoaded()
                }
                if (playerState == "IDLE" && idleReason == "ERROR") {
                    log.w { "Receiver reported a playback error" }
                    reportError()
                    return
                }
                applyAudioPreferenceIfNeeded()
                if (loaded) onSnapshot(snapshot())
            }
            "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST" -> if (message.requestId == loadRequestId) {
                log.w { "Receiver load failed: ${message.type} ${message.payload.string("reason").orEmpty()}" }
                reportError()
            }
        }
    }

    private fun applyStatus(status: JsonObject) {
        (status["mediaSessionId"] as? JsonPrimitive)?.intOrNull?.let { mediaSessionId = it }
        status.string("playerState")?.let { playerState = it }
        idleReason = status.string("idleReason")
        (status["currentTime"] as? JsonPrimitive)?.doubleOrNull?.let {
            positionSeconds = it
            positionAtNanos = System.nanoTime()
        }
        (status["playbackRate"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { playbackRate = it }
        (status["videoInfo"] as? JsonObject)?.let { info ->
            (info["width"] as? JsonPrimitive)?.intOrNull?.let { videoWidth = it }
            (info["height"] as? JsonPrimitive)?.intOrNull?.let { videoHeight = it }
        }
        (status["activeTrackIds"] as? JsonArray)?.let { ids ->
            activeTrackIds = ids.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.toSet()
        }
        (status["media"] as? JsonObject)?.let { media ->
            (media["duration"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { durationSeconds = it }
            (media["tracks"] as? JsonArray)?.let { list ->
                tracks = list.mapNotNull { it as? JsonObject }
                    .filter { it.string("type") == "AUDIO" }
                    .mapNotNull { track ->
                        val id = (track["trackId"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
                        ReceiverTrack(id, track.string("language"), track.string("name"))
                    }
            }
        }
    }

    private fun currentPositionMs(): Long {
        val extra = if (playerState == "PLAYING") (System.nanoTime() - positionAtNanos) / 1e9 * playbackRate else 0.0
        val seconds = positionSeconds + extra
        val capped = if (durationSeconds > 0) seconds.coerceAtMost(durationSeconds) else seconds
        return (capped * 1000).toLong().coerceAtLeast(0L)
    }

    private fun snapshot(): PlayerPlaybackSnapshot {
        val ended = playerState == "IDLE" && idleReason == "FINISHED"
        val durationMs = (durationSeconds * 1000).toLong()
        val positionMs = currentPositionMs()
        return PlayerPlaybackSnapshot(
            isLoading = playerState == "BUFFERING" || playerState == "LOADING",
            isPlaying = playerState == "PLAYING",
            isEnded = ended,
            durationMs = durationMs,
            positionMs = if (ended && durationMs > 0L) durationMs else positionMs,
            bufferedPositionMs = positionMs,
            playbackSpeed = playbackRate.toFloat(),
            videoWidth = videoWidth,
            videoHeight = videoHeight,
        )
    }

    private fun reportError() {
        loaded = false
        if (errorReported) return
        errorReported = true
        onError(loadFailedMessage)
    }

    private fun mediaCommand(type: String, includeSession: Boolean = true, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) {
        val sessionId = mediaSessionId
        if (includeSession && sessionId == null) return
        channel.send(
            NS_MEDIA,
            destination,
            buildJsonObject {
                put("type", type)
                put("requestId", channel.newRequestId())
                if (sessionId != null) put("mediaSessionId", sessionId)
                extra()
            },
        )
    }

    override fun play() {
        if (loaded) mediaCommand("PLAY")
    }

    override fun pause() {
        if (loaded) mediaCommand("PAUSE")
    }

    override fun seekTo(positionMs: Long) {
        if (!loaded) return
        val target = positionMs.coerceAtLeast(0L)
        log.d { "Seeking the receiver to ${target}ms" }
        positionSeconds = target / 1000.0
        positionAtNanos = System.nanoTime()
        mediaCommand("SEEK") { put("currentTime", target / 1000.0) }
    }

    override fun seekBy(offsetMs: Long) {
        val target = (currentPositionMs() + offsetMs).coerceAtLeast(0L)
        val durationMs = (durationSeconds * 1000).toLong()
        seekTo(if (durationMs > 0L) target.coerceAtMost(durationMs) else target)
    }

    override fun retry() {
        if (released) return
        log.i { "Retrying the stream on the receiver" }
        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        load(currentPositionMs(), autoplay = true)
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (loaded) mediaCommand("SET_PLAYBACK_RATE") { put("playbackRate", speed.toDouble().coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)) }
    }

    override fun setMuted(muted: Boolean) = CastPlatform.setMuted(muted)

    override fun getAudioTracks(): List<AudioTrack> =
        tracks.mapIndexed { index, track ->
            AudioTrack(
                index = index,
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: track.language ?: "Audio ${index + 1}",
                language = track.language,
                isSelected = track.id in activeTrackIds,
            )
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()

    override fun applyAudioLanguagePreferences(languages: List<String>) {
        preferredAudioLanguages = languages
        audioPreferenceApplied = false
        applyAudioPreferenceIfNeeded()
    }

    override fun selectAudioTrack(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        userSelectedAudio = true
        activateAudioTrack(track)
    }

    private fun applyAudioPreferenceIfNeeded() {
        if (audioPreferenceApplied || userSelectedAudio || preferredAudioLanguages.isEmpty() || tracks.isEmpty()) return
        audioPreferenceApplied = true
        val preferred = preferredAudioLanguages.firstNotNullOfOrNull { language ->
            tracks.firstOrNull { languageMatchesPreference(it.language, language) }
        } ?: return
        if (preferred.id !in activeTrackIds) {
            log.i { "Selecting the preferred audio language on the receiver: ${preferred.language}" }
            activateAudioTrack(preferred)
        }
    }

    private fun activateAudioTrack(track: ReceiverTrack) {
        if (!loaded) return
        val audioIds = tracks.map { it.id }.toSet()
        val keep = activeTrackIds.filter { it !in audioIds }
        mediaCommand("EDIT_TRACKS_INFO") {
            putJsonArray("activeTrackIds") { (keep + track.id).forEach { add(JsonPrimitive(it)) } }
        }
    }

    override fun selectSubtitleTrack(index: Int) = Unit
    override fun setSubtitleUri(url: String) = Unit
    override fun clearExternalSubtitle() = Unit
    override fun clearExternalSubtitleAndSelect(trackIndex: Int) = Unit
}

private fun CastHlsSegmentFormat.castName(): String = when (this) {
    CastHlsSegmentFormat.Ts -> "ts"
    CastHlsSegmentFormat.Fmp4 -> "fmp4"
    CastHlsSegmentFormat.Aac -> "aac"
    CastHlsSegmentFormat.Ac3 -> "ac3"
    CastHlsSegmentFormat.Eac3 -> "e_ac3"
    CastHlsSegmentFormat.Mp3 -> "mp3"
}

/**
 * Content type for the Default Media Receiver: adaptive formats keep their own type, everything
 * progressive goes as MP4/WebM (older Chromecasts refuse "video/x-matroska" even when they can play it).
 */
/** Type from the declared headers or the link's extension; null when only a probe can tell. */
private fun castContentTypeFor(request: CastMediaRequest): String? {
    val declared = (request.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
        ?: request.streamType).orEmpty().lowercase()
    val path = request.sourceUrl.substringBefore('?').substringBefore('#').lowercase()
    return when {
        "mpegurl" in declared || declared == "hls" || path.endsWith(".m3u8") -> "application/x-mpegurl"
        "dash" in declared || declared == "mpd" || path.endsWith(".mpd") -> "application/dash+xml"
        "webm" in declared || path.endsWith(".webm") -> "video/webm"
        declared.startsWith("audio/") -> declared
        declared.startsWith("video/") -> declared
        VIDEO_EXTENSIONS.any { path.endsWith(it) } -> "video/mp4"
        else -> null
    }
}

private val VIDEO_EXTENSIONS = listOf(".mp4", ".m4v", ".mkv", ".mov", ".avi", ".ts")

/**
 * Links without an extension (e.g. HLS playlists served from an API path) are fetched briefly with
 * their headers: announcing a playlist as video/mp4 makes the receiver fail right away.
 */
private fun probeCastContentType(request: CastMediaRequest): String {
    val url = request.sourceUrl
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return "video/mp4"
    val detected = runCatching {
        val builder = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-2047")
        request.requestHeaders.forEach { (name, value) ->
            if (!name.equals("Range", true) && !name.equals("Accept-Encoding", true)) builder.header(name, value)
        }
        com.nuvio.app.core.network.nuvioSharedOkHttpClient.newCall(builder.build()).execute().use { response ->
            val type = response.header("Content-Type").orEmpty().lowercase()
            val head = response.body?.source()?.let { source ->
                source.request(2048)
                source.buffer.snapshot().utf8().take(2048)
            }.orEmpty().trimStart { it == '\uFEFF' || it.isWhitespace() }
            when {
                "mpegurl" in type || head.startsWith("#EXTM3U") -> "application/x-mpegurl"
                "dash" in type || head.startsWith("<?xml") && "<MPD" in head || head.startsWith("<MPD") -> "application/dash+xml"
                "webm" in type -> "video/webm"
                type.startsWith("audio/") || type.startsWith("video/") -> type.substringBefore(';').trim()
                else -> null
            }
        }
    }.onFailure { error -> log.w(error) { "Could not probe the stream type" } }.getOrNull()
    log.i { "Probed stream type: ${detected ?: "unknown, using video/mp4"}" }
    return detected ?: "video/mp4"
}
