package com.nuvio.app.features.downloads.hls

import co.touchlab.kermit.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy
import org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE
import org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE
import org.bytedeco.ffmpeg.global.avformat.av_find_input_format
import org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_write_trailer
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_free_context
import org.bytedeco.ffmpeg.global.avformat.avformat_new_stream
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avformat.avformat_write_header
import org.bytedeco.ffmpeg.global.avformat.avio_closep
import org.bytedeco.ffmpeg.global.avformat.avio_open
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_strerror
import org.bytedeco.javacpp.PointerPointer
import java.io.File

internal enum class HlsSourceKind { Video, Audio }

/** One downloaded media track: its files per discontinuity group. */
internal class HlsMediaSource(
    val kind: HlsSourceKind,
    /** The audio rendition; null for the video stream. */
    val track: HlsDownloadTrack?,
    val format: HlsSegmentFormat,
    val groupFiles: Map<Int, File>,
    /** Packed audio: MPEG-2 timestamp (90 kHz) of the first segment of each group, from its ID3 tag. */
    val packedPtsByGroup: Map<Int, Long> = emptyMap(),
)

internal class HlsSubtitleSource(
    val track: HlsDownloadTrack,
    /** WebVTT segment files with their discontinuity group. */
    val segments: List<Pair<Int, File>>,
)

internal class HlsConversionRequest(
    val sources: List<HlsMediaSource>,
    val subtitles: List<HlsSubtitleSource>,
    /** Start of each discontinuity group on the output timeline (sum of the previous durations). */
    val groupOffsetsUs: List<Long>,
    val totalDurationUs: Long,
    val includeMuxedAudio: Boolean,
    val muxedAudioLanguage: String?,
    val muxedAudioName: String?,
)

internal class HlsConversionResult(
    val file: File,
    val container: HlsOutputContainer,
    /** Subtitles saved next to the video as SRT. */
    val sidecarSubtitles: List<Pair<HlsDownloadTrack, String>>,
)

internal class HlsConversionException(message: String) : Exception(message)

/**
 * Desktop HLS merge with FFmpeg (libavformat, stream copy, no re-encoding): every media track is
 * read through the concat demuxer (one file per discontinuity group) and written into one MKV,
 * which accepts every HLS codec. WebVTT tracks become SRT files next to the video.
 */
internal class HlsConverter(private val request: HlsConversionRequest) {
    private val log = Logger.withTag("HlsConverter")

    private class Input(
        val source: HlsMediaSource,
        val context: AVFormatContext,
        /** Input stream index -> output stream index (-1 = dropped). */
        val mapping: IntArray,
    ) {
        var pending: AVPacket? = null
        var pendingTimeUs: Long = Long.MAX_VALUE
        var finished = false
    }

    suspend fun convert(outputWithoutExtension: File, onProgress: (Float) -> Unit): HlsConversionResult {
        val output = File(outputWithoutExtension.path + "." + HlsOutputContainer.Mkv.extension)
        output.delete()
        val inputs = mutableListOf<Input>()
        val out = AVFormatContext(null)
        var headerWritten = false
        try {
            check(avformat_alloc_output_context2(out, null, "matroska", output.absolutePath), "output")
            val outStreams = mutableListOf<AVStream>()
            var defaultAudioAssigned = false

            request.sources.forEachIndexed { sourceIndex, source ->
                val context = openConcat(source, outputWithoutExtension.parentFile, sourceIndex)
                val mapping = IntArray(context.nb_streams()) { -1 }
                var videoTaken = false
                var audioTaken = false
                for (i in 0 until context.nb_streams()) {
                    val stream = context.streams(i)
                    val type = stream.codecpar().codec_type()
                    val keep = when (source.kind) {
                        HlsSourceKind.Video -> when (type) {
                            AVMEDIA_TYPE_VIDEO -> !videoTaken.also { videoTaken = true }
                            AVMEDIA_TYPE_AUDIO -> request.includeMuxedAudio && !audioTaken.also { audioTaken = true }
                            else -> false
                        }
                        HlsSourceKind.Audio -> type == AVMEDIA_TYPE_AUDIO && !audioTaken.also { audioTaken = true }
                    }
                    if (!keep) continue
                    val outStream = avformat_new_stream(out, null)
                        ?: throw HlsConversionException("Cannot create output stream")
                    check(avcodec_parameters_copy(outStream.codecpar(), stream.codecpar()), "copy codec parameters")
                    outStream.codecpar().codec_tag(0)
                    outStream.time_base(stream.time_base())
                    if (type == AVMEDIA_TYPE_AUDIO) {
                        val language = if (source.kind == HlsSourceKind.Video) request.muxedAudioLanguage else source.track?.language
                        val title = if (source.kind == HlsSourceKind.Video) request.muxedAudioName else (source.track?.name ?: source.track?.label)
                        val metadata = AVDictionary(null)
                        language?.takeIf { it.isNotBlank() }?.let { av_dict_set(metadata, "language", it, 0) }
                        title?.takeIf { it.isNotBlank() }?.let { av_dict_set(metadata, "title", it, 0) }
                        outStream.metadata(metadata)
                        outStream.disposition(if (!defaultAudioAssigned) 1 else 0)
                        defaultAudioAssigned = true
                    }
                    mapping[i] = outStreams.size
                    outStreams += outStream
                }
                inputs += Input(source, context, mapping)
            }
            if (outStreams.isEmpty()) throw HlsConversionException("No audio or video track to merge")

            if ((out.oformat().flags() and AVFMT_NOFILE) == 0) {
                val pb = AVIOContext(null)
                check(avio_open(pb, output.absolutePath, AVIO_FLAG_WRITE), "open output file")
                out.pb(pb)
            }
            check(avformat_write_header(out, null as PointerPointer<*>?), "write header")
            headerWritten = true
            log.i { "Merging ${inputs.size} source(s) into ${outStreams.size} track(s)" }

            inputs.forEach(::readNext)
            var lastReported = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                val next = inputs.filter { !it.finished }.minByOrNull { it.pendingTimeUs } ?: break
                val packet = checkNotNull(next.pending)
                val inStream = next.context.streams(packet.stream_index())
                val outIndex = next.mapping[packet.stream_index()]
                val outStream = outStreams[outIndex]
                av_packet_rescale_ts(packet, inStream.time_base(), outStream.time_base())
                packet.stream_index(outIndex)
                packet.pos(-1)
                val progressUs = next.pendingTimeUs
                check(av_interleaved_write_frame(out, packet), "write packet")
                if (request.totalDurationUs > 0L) {
                    val percent = (progressUs * 100 / request.totalDurationUs).toInt().coerceIn(0, 99)
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(percent / 100f)
                    }
                }
                readNext(next)
            }
            check(av_write_trailer(out), "write trailer")
            onProgress(1f)
        } finally {
            inputs.forEach { input ->
                input.pending?.let { av_packet_free(it) }
                avformat_close_input(input.context)
            }
            if (out.pb() != null && ((out.oformat()?.flags() ?: AVFMT_NOFILE) and AVFMT_NOFILE) == 0) avio_closep(out.pb())
            avformat_free_context(out)
            if (!headerWritten) output.delete()
        }

        val sidecars = subtitleSidecars()
        log.i { "Merged into MKV (${output.length()} bytes, ${sidecars.size} subtitle file(s))" }
        return HlsConversionResult(output, HlsOutputContainer.Mkv, sidecars)
    }

    /** The groups of one track as an ffconcat list, read as a single continuous input. */
    private fun openConcat(source: HlsMediaSource, workDir: File, index: Int): AVFormatContext {
        val list = File(workDir, "concat_$index.ffconcat")
        list.writeText(
            buildString {
                appendLine("ffconcat version 1.0")
                source.groupFiles.toSortedMap().values.forEach { file ->
                    appendLine("file '${file.absolutePath.replace('\\', '/').replace("'", "'\\''")}'")
                }
            },
        )
        val context = AVFormatContext(null)
        val options = AVDictionary(null)
        av_dict_set(options, "safe", "0", 0)
        try {
            check(avformat_open_input(context, list.absolutePath, av_find_input_format("concat"), options), "open ${source.kind}")
        } finally {
            av_dict_free(options)
        }
        check(avformat_find_stream_info(context, null as PointerPointer<*>?), "probe ${source.kind}")
        return context
    }

    private fun readNext(input: Input) {
        val packet = input.pending ?: av_packet_alloc().also { input.pending = it }
        while (true) {
            if (av_read_frame(input.context, packet) < 0) {
                input.finished = true
                return
            }
            val index = packet.stream_index()
            if (index !in input.mapping.indices || input.mapping[index] < 0) {
                av_packet_unref(packet)
                continue
            }
            val stream = input.context.streams(index)
            val ts = if (packet.dts() != AV_NOPTS_VALUE) packet.dts() else packet.pts()
            input.pendingTimeUs = if (ts == AV_NOPTS_VALUE) {
                input.pendingTimeUs.takeIf { it != Long.MAX_VALUE } ?: 0L
            } else {
                ts * 1_000_000L * stream.time_base().num() / stream.time_base().den()
            }
            return
        }
    }

    // ---- Subtitles (same timing rules as the Android converter) --------------------------

    private fun subtitleSidecars(): List<Pair<HlsDownloadTrack, String>> {
        if (request.subtitles.isEmpty()) return emptyList()
        val video = request.sources.firstOrNull { it.kind == HlsSourceKind.Video }
        val groupStarts = (0 until request.groupOffsetsUs.size.coerceAtLeast(1)).map { group ->
            video?.groupFiles?.get(group)?.let(::firstTimestampUs) ?: 0L
        }
        return request.subtitles.mapNotNull { source ->
            val cues = source.segments.flatMap { (group, file) ->
                val segment = runCatching { HlsWebVtt.parseSegment(file.readText()) }.getOrNull() ?: return@flatMap emptyList()
                val start = groupStarts.getOrElse(group) { 0L }
                val offset = request.groupOffsetsUs.getOrElse(group) { 0L }
                HlsWebVtt.mapCues(segment, referencePts = start * 9L / 100L) { mpegUs -> mpegUs - start + offset }
            }
            val merged = HlsWebVtt.mergeCues(cues)
            log.d { "Subtitle ${source.track.label}: ${merged.size} cues" }
            merged.takeIf { it.isNotEmpty() }?.let { source.track to HlsWebVtt.toSrt(it) }
        }
    }

    /** First packet time (µs) of a single group file, used to align the WebVTT cues. */
    private fun firstTimestampUs(file: File): Long? {
        val context = AVFormatContext(null)
        if (avformat_open_input(context, file.absolutePath, null, null as AVDictionary?) < 0) return null
        val packet = av_packet_alloc()
        try {
            if (avformat_find_stream_info(context, null as PointerPointer<*>?) < 0) return null
            repeat(200) {
                if (av_read_frame(context, packet) < 0) return null
                val stream = context.streams(packet.stream_index())
                val pts = packet.pts()
                val video = stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO
                av_packet_unref(packet)
                if (video && pts != AV_NOPTS_VALUE) {
                    return pts * 1_000_000L * stream.time_base().num() / stream.time_base().den()
                }
            }
            return null
        } finally {
            av_packet_free(packet)
            avformat_close_input(context)
        }
    }

    private fun check(result: Int, step: String) {
        if (result >= 0) return
        val buffer = ByteArray(256)
        av_strerror(result, buffer, buffer.size.toLong())
        val message = String(buffer).trimEnd('\u0000')
        throw HlsConversionException("FFmpeg $step failed: $message")
    }
}
