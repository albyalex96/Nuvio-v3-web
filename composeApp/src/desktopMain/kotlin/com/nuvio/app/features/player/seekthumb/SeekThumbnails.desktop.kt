package com.nuvio.app.features.player.seekthumb

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.nuvio.app.desktop.compat.toNativeMediaLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Desktop previews are decoded with FFmpeg (already shipped for HLS downloads). */
internal actual val seekThumbnailsSupported: Boolean by lazy {
    runCatching {
        org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat::class.java)
        true
    }.onFailure { seekThumbLog.e(it) { "FFmpeg not available: no seek previews" } }.getOrDefault(false)
}

internal actual fun createSeekThumbnailProvider(
    request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
): SeekThumbnailProvider? {
    if (!seekThumbnailsSupported || request.durationMs <= 0L) return null
    if (!request.url.startsWith("http", ignoreCase = true) && !request.url.startsWith("file:", ignoreCase = true)) {
        return null
    }
    return FfmpegSeekThumbnailProvider(request, config)
}

/**
 * One FFmpeg input kept open for the whole playback: each request seeks to the keyframe before
 * the asked position and decodes one picture, scaled to the preview width. Pictures are cached
 * by keyframe so scrubbing back and forth costs nothing.
 */
private class FfmpegSeekThumbnailProvider(
    private val request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
) : SeekThumbnailProvider {
    private val state = MutableStateFlow<SeekThumbnailFrame?>(null)
    override val frame: StateFlow<SeekThumbnailFrame?> = state.asStateFlow()

    @Volatile
    private var failed = false
    override val available: Boolean get() = !failed

    /** Decoded at twice the preview width (HiDPI screens), never larger than 640 px. */
    private val targetWidth = (config.size.widthDp * 2).coerceAtMost(640)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "seek-thumbs").apply { isDaemon = true } }
    private val latest = AtomicLong(-1)
    private val working = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val cache = object : LinkedHashMap<Long, SeekThumbnailFrame>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SeekThumbnailFrame>?) = size > 120
    }

    private var input: AVFormatContext? = null
    private var decoder: AVCodecContext? = null
    private var videoStream = -1

    override fun request(positionMs: Long) {
        if (failed || closed.get()) return
        latest.set(positionMs.coerceIn(0L, request.durationMs))
        if (working.compareAndSet(false, true)) executor.execute(::drain)
    }

    private fun drain() {
        try {
            while (!closed.get()) {
                val position = latest.getAndSet(-1)
                if (position < 0) break
                cachedNear(position)?.let { state.value = it; continue }
                val decoded = runCatching { decodeAt(position) }
                    .onFailure { error ->
                        seekThumbLog.w(error) { "Preview decode failed, previews off for this stream" }
                        failed = true
                    }
                    .getOrNull() ?: continue
                synchronized(cache) { cache[decoded.positionMs] = decoded }
                state.value = decoded
            }
        } finally {
            working.set(false)
            if (latest.get() >= 0 && !closed.get() && working.compareAndSet(false, true)) executor.execute(::drain)
        }
    }

    /** A cached picture within 2 s of the asked position (previews land on keyframes anyway). */
    private fun cachedNear(positionMs: Long): SeekThumbnailFrame? = synchronized(cache) {
        cache.values.minByOrNull { kotlin.math.abs(it.positionMs - positionMs) }
            ?.takeIf { kotlin.math.abs(it.positionMs - positionMs) <= 2_000L }
    }

    private fun openIfNeeded() {
        if (input != null) return
        val context = AVFormatContext(null)
        val options = AVDictionary(null)
        val headers = request.headers.entries
            .filterNot { it.key.equals("Range", true) || it.key.equals("User-Agent", true) }
            .joinToString("") { "${it.key}: ${it.value}\r\n" }
        if (headers.isNotEmpty()) av_dict_set(options, "headers", headers, 0)
        request.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }
            ?.let { av_dict_set(options, "user_agent", it.value, 0) }
        av_dict_set(options, "rw_timeout", "15000000", 0)
        av_dict_set(options, "reconnect", "1", 0)
        try {
            check(avformat_open_input(context, request.url.toNativeMediaLocation(), null, options) >= 0) { "Cannot open the stream" }
        } finally {
            av_dict_free(options)
        }
        check(avformat_find_stream_info(context, null as PointerPointer<*>?) >= 0) { "Cannot read the stream info" }
        val stream = av_find_best_stream(context, AVMEDIA_TYPE_VIDEO, -1, -1, null as PointerPointer<*>?, 0)
        check(stream >= 0) { "No video track" }
        val parameters = context.streams(stream).codecpar()
        val codec = checkNotNull(avcodec_find_decoder(parameters.codec_id())) { "No decoder" }
        val codecContext = checkNotNull(avcodec_alloc_context3(codec))
        check(avcodec_parameters_to_context(codecContext, parameters) >= 0)
        codecContext.thread_count(2)
        check(avcodec_open2(codecContext, codec, null as AVDictionary?) >= 0) { "Cannot open the decoder" }
        input = context
        decoder = codecContext
        videoStream = stream
        seekThumbLog.i { "Desktop previews ready (${parameters.width()}x${parameters.height()})" }
    }

    private fun decodeAt(positionMs: Long): SeekThumbnailFrame? {
        openIfNeeded()
        val context = input ?: return null
        val codecContext = decoder ?: return null
        val stream = context.streams(videoStream)
        val target = positionMs * (AV_TIME_BASE / 1000)
        check(av_seek_frame(context, -1, target, AVSEEK_FLAG_BACKWARD) >= 0) { "Seek failed" }
        avcodec_flush_buffers(codecContext)
        val packet: AVPacket = av_packet_alloc()
        val decoded: AVFrame = av_frame_alloc()
        try {
            var packets = 0
            while (packets++ < 600 && !closed.get()) {
                if (av_read_frame(context, packet) < 0) return null
                if (packet.stream_index() != videoStream) {
                    av_packet_unref(packet)
                    continue
                }
                avcodec_send_packet(codecContext, packet)
                av_packet_unref(packet)
                if (avcodec_receive_frame(codecContext, decoded) == 0) {
                    val ts = decoded.best_effort_timestamp().takeIf { it != AV_NOPTS_VALUE } ?: decoded.pts()
                    val frameMs = if (ts == AV_NOPTS_VALUE) {
                        positionMs
                    } else {
                        ts * 1000L * stream.time_base().num() / stream.time_base().den()
                    }
                    return SeekThumbnailFrame(image = scale(decoded), positionMs = frameMs.coerceAtLeast(0L))
                }
            }
            return null
        } finally {
            av_packet_free(packet)
            av_frame_free(decoded)
        }
    }

    private fun scale(source: AVFrame): androidx.compose.ui.graphics.ImageBitmap {
        val width = targetWidth
        val height = ((source.height().toLong() * width / source.width().coerceAtLeast(1)).toInt() / 2 * 2).coerceAtLeast(2)
        val sws = checkNotNull(
            sws_getContext(
                source.width(), source.height(), source.format(),
                width, height, AV_PIX_FMT_BGRA, SWS_BILINEAR,
                null, null, null as DoublePointer?,
            ),
        ) { "No scaler" }
        val stride = width * 4
        val buffer = BytePointer((stride * height).toLong())
        try {
            // sws_scale reads four destination planes and strides: unused ones must exist and be empty.
            val planes = PointerPointer<BytePointer>(4L).put(0L, buffer)
            val strides = IntPointer(stride, 0, 0, 0)
            try {
                sws_scale(sws, source.data(), source.linesize(), 0, source.height(), planes, strides)
            } finally {
                planes.deallocate()
                strides.deallocate()
            }
            val bytes = ByteArray(stride * height)
            buffer.get(bytes)
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            return Image.makeRaster(info, bytes, stride).toComposeImageBitmap()
        } finally {
            buffer.deallocate()
            sws_freeContext(sws)
        }
    }

    override fun clearFrame() {
        latest.set(-1)
        state.value = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            decoder?.let { avcodec_free_context(it) }
            input?.let { avformat_close_input(it) }
            decoder = null
            input = null
        }
        executor.shutdown()
    }
}
