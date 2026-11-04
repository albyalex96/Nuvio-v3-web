package com.nuvio.app.features.downloads.hls

import co.touchlab.kermit.Logger
import com.nuvio.app.features.downloads.DownloadHttpException
import com.nuvio.app.features.downloads.DownloadItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hls_download_error_changed
import nuvio.composeapp.generated.resources.hls_download_error_convert
import nuvio.composeapp.generated.resources.hls_download_error_key
import nuvio.composeapp.generated.resources.hls_download_error_space
import nuvio.composeapp.generated.resources.hls_download_problem_drm
import nuvio.composeapp.generated.resources.hls_download_problem_empty
import nuvio.composeapp.generated.resources.hls_download_problem_live
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString

/** A definitive failure with a message for the user: retrying would not help. */
internal class HlsDownloadException(message: String) : Exception(message)

/** Saved between runs so a paused or interrupted HLS download resumes where it stopped. */
@Serializable
internal data class HlsWorkState(
    val specKey: String,
    val tracks: Map<String, HlsTrackProgress> = emptyMap(),
)

@Serializable
internal data class HlsTrackProgress(
    val format: HlsSegmentFormat? = null,
    val doneSegments: Int = 0,
    /** Length of each group file that is known to be complete. */
    val committedLengths: Map<Int, Long> = emptyMap(),
    val packedPts: Map<Int, Long> = emptyMap(),
    val bytes: Long = 0L,
    /** The track cannot be converted (e.g. IMSC subtitles) and is left out. */
    val skipped: Boolean = false,
)

private enum class HlsTrackRole { Video, Audio, Subtitle }

private class HlsTrackJob(
    val id: String,
    val role: HlsTrackRole,
    val playlistUrl: String,
    val track: HlsDownloadTrack?,
)

/**
 * Downloads the tracks chosen for an HLS download into [workDir] (one file per track and
 * discontinuity group, segments appended in order) and merges them with [HlsConverter].
 */
internal class HlsDownloadTask(
    private val item: DownloadItem,
    private val workDir: File,
    private val client: OkHttpClient,
) {
    private val log = Logger.withTag("HlsDownload")
    private val spec = checkNotNull(item.hls)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateFile = File(workDir, "state.json")
    private val keys = mutableMapOf<String, ByteArray>()
    private val initSegments = mutableMapOf<String, ByteArray>()
    private lateinit var state: HlsWorkState

    private val jobs: List<HlsTrackJob> = buildList {
        add(HlsTrackJob("v", HlsTrackRole.Video, spec.videoPlaylistUrl, null))
        spec.audio.forEachIndexed { index, track -> add(HlsTrackJob("a$index", HlsTrackRole.Audio, track.playlistUrl, track)) }
        spec.subtitles.forEachIndexed { index, track -> add(HlsTrackJob("s$index", HlsTrackRole.Subtitle, track.playlistUrl, track)) }
    }

    suspend fun run(
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onConverting: (Float) -> Unit,
    ): HlsConversionResult = withContext(Dispatchers.IO) {
        prepareWorkDir()
        val playlists = jobs.associate { job -> job.id to loadPlaylist(job, refresh = false) }
        val mediaDurations = jobs.filter { it.role != HlsTrackRole.Subtitle }.sumOf { playlists.getValue(it.id).durationSeconds }

        fun reportProgress() {
            val done = jobs.filter { it.role != HlsTrackRole.Subtitle }.sumOf { job ->
                val progress = state.tracks[job.id] ?: return@sumOf 0.0
                playlists.getValue(job.id).segments.take(progress.doneSegments).sumOf { it.durationSeconds }
            }
            val bytes = state.tracks.values.sumOf { it.bytes }
            val fraction = if (mediaDurations > 0.0) done / mediaDurations else 0.0
            val estimated = spec.bandwidth?.let { bandwidth ->
                val audioBits = spec.audio.size * 160_000L
                ((bandwidth + audioBits) * (spec.durationSeconds ?: mediaDurations) / 8.0).toLong()
            }
            val total = when {
                fraction >= 0.05 -> (bytes / fraction).toLong()
                estimated != null -> estimated
                else -> null
            }?.coerceAtLeast(bytes)
            onProgress(bytes, total)
        }

        reportProgress()
        for (job in jobs) {
            downloadTrack(job, playlists.getValue(job.id)) { reportProgress() }
        }

        val video = playlists.getValue("v")
        val groupCount = jobs.filter { it.role != HlsTrackRole.Subtitle }.maxOf { playlists.getValue(it.id).discontinuityCount }
        val groupOffsetsUs = (0 until groupCount).map { group ->
            (video.segments.filter { it.discontinuityIndex < group }.sumOf { it.durationSeconds } * 1_000_000.0).toLong()
        }
        val sources = jobs.filter { it.role != HlsTrackRole.Subtitle }.mapNotNull { job ->
            val progress = state.tracks[job.id] ?: return@mapNotNull null
            val format = progress.format?.takeIf { !progress.skipped } ?: return@mapNotNull null
            HlsMediaSource(
                kind = if (job.role == HlsTrackRole.Video) HlsSourceKind.Video else HlsSourceKind.Audio,
                track = job.track,
                format = format,
                groupFiles = (0 until groupCount).associateWith { group -> groupFile(job, group) }.filterValues { it.isFile },
                packedPtsByGroup = progress.packedPts,
            )
        }
        val subtitles = jobs.filter { it.role == HlsTrackRole.Subtitle && state.tracks[it.id]?.skipped != true }.map { job ->
            val playlist = playlists.getValue(job.id)
            HlsSubtitleSource(
                track = job.track!!,
                segments = playlist.segments.indices.mapNotNull { index ->
                    subtitleFile(job, index).takeIf { it.isFile }?.let { playlist.segments[index].discontinuityIndex to it }
                },
            )
        }

        val inputBytes = sources.sumOf { source -> source.groupFiles.values.sumOf { it.length() } }
        val required = inputBytes + inputBytes / 20 + 32L * 1024 * 1024
        if (workDir.usableSpace < required) {
            log.w { "Not enough space to convert: ${workDir.usableSpace} free, $required needed" }
            throw HlsDownloadException(getString(Res.string.hls_download_error_space, formatSize(required)))
        }

        log.i { "Segments downloaded (${state.tracks.values.sumOf { it.bytes }} bytes), converting" }
        onConverting(0f)
        val converter = HlsConverter(
            HlsConversionRequest(
                sources = sources,
                subtitles = subtitles,
                groupOffsetsUs = groupOffsetsUs,
                totalDurationUs = (video.durationSeconds * 1_000_000.0).toLong(),
                includeMuxedAudio = spec.includeMuxedAudio,
                muxedAudioLanguage = spec.muxedAudioLanguage,
                muxedAudioName = spec.muxedAudioName,
            ),
        )
        try {
            converter.convert(File(workDir, "output"), onConverting)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.e(error) { "HLS conversion failed" }
            throw HlsDownloadException(getString(Res.string.hls_download_error_convert, error.message ?: error::class.simpleName.orEmpty()))
        }
    }

    private fun prepareWorkDir() {
        val specKey = sha256(json.encodeToString(spec))
        val saved = runCatching { json.decodeFromString<HlsWorkState>(stateFile.readText()) }.getOrNull()
        if (saved?.specKey != specKey) {
            if (saved != null) log.w { "HLS download settings changed: starting over" }
            workDir.deleteRecursively()
        }
        check(workDir.isDirectory || workDir.mkdirs()) { "Cannot create the HLS work directory" }
        state = saved?.takeIf { it.specKey == specKey } ?: HlsWorkState(specKey)
        File(workDir, "output.mp4").delete()
        File(workDir, "output.mkv").delete()
    }

    private suspend fun loadPlaylist(job: HlsTrackJob, refresh: Boolean): HlsMediaPlaylist {
        val textFile = File(workDir, "${job.id}.m3u8")
        val urlFile = File(workDir, "${job.id}.url")
        if (refresh || !textFile.isFile || !urlFile.isFile) {
            val fetched = fetch(job.playlistUrl, null)
            val body = fetched.bytes.decodeToString()
            check(HlsPlaylistParser.isPlaylist(body)) { "Not an HLS playlist" }
            textFile.writeText(body)
            urlFile.writeText(fetched.url)
        }
        val playlist = HlsPlaylistParser.parseMedia(textFile.readText(), urlFile.readText())
        if (job.role != HlsTrackRole.Subtitle) {
            when (HlsStreamAnalyzer.problemOf(playlist)) {
                HlsStreamProblem.Live -> throw HlsDownloadException(getString(Res.string.hls_download_problem_live))
                HlsStreamProblem.Drm -> throw HlsDownloadException(getString(Res.string.hls_download_problem_drm))
                HlsStreamProblem.Empty -> throw HlsDownloadException(getString(Res.string.hls_download_problem_empty))
                HlsStreamProblem.Unreadable, null -> Unit
            }
        }
        log.d { "Track ${job.id}: ${playlist.segments.size} segments, ${playlist.discontinuityCount} group(s)" }
        return playlist
    }

    private suspend fun downloadTrack(job: HlsTrackJob, initialPlaylist: HlsMediaPlaylist, onSegment: () -> Unit) {
        var playlist = initialPlaylist
        var progress = state.tracks[job.id] ?: HlsTrackProgress()
        if (progress.skipped || progress.doneSegments >= playlist.segments.size) return
        if (job.role == HlsTrackRole.Subtitle && !playlist.looksLikeWebVtt()) {
            log.w { "Subtitle ${job.track?.label} is not WebVTT: skipped" }
            saveProgress(job, progress.copy(skipped = true))
            return
        }
        log.i { "Downloading track ${job.id} from segment ${progress.doneSegments + 1}/${playlist.segments.size}" }
        var refreshed = false
        while (progress.doneSegments < playlist.segments.size) {
            try {
                progress = downloadSegments(job, playlist, progress, onSegment)
            } catch (error: DownloadHttpException) {
                // Expired segment links: read the playlist again once and go on if it is the same stream.
                if (refreshed || error.statusCode !in setOf(401, 403, 404, 410)) throw error
                refreshed = true
                log.w { "Segment of ${job.id} answered HTTP ${error.statusCode}: refreshing the playlist" }
                val fresh = loadPlaylist(job, refresh = true)
                if (fresh.segments.size != playlist.segments.size) {
                    throw HlsDownloadException(getString(Res.string.hls_download_error_changed))
                }
                playlist = fresh
            }
        }
    }

    /** Downloads the remaining segments of [job], a few at a time, appending them in order. */
    private suspend fun downloadSegments(
        job: HlsTrackJob,
        playlist: HlsMediaPlaylist,
        start: HlsTrackProgress,
        onSegment: () -> Unit,
    ): HlsTrackProgress = coroutineScope {
        var progress = start
        val window = ArrayDeque<Pair<Int, Deferred<ByteArray?>>>()
        var next = progress.doneSegments
        while (progress.doneSegments < playlist.segments.size) {
            while (window.size < PARALLEL_SEGMENTS && next < playlist.segments.size) {
                val index = next++
                window.addLast(index to async { fetchSegment(playlist.segments[index]) })
            }
            val (index, deferred) = window.removeFirst()
            val bytes = deferred.await()
            val segment = playlist.segments[index]
            progress = if (job.role == HlsTrackRole.Subtitle) {
                if (bytes != null) subtitleFile(job, index).writeBytes(bytes)
                progress.copy(doneSegments = index + 1, bytes = progress.bytes + (bytes?.size ?: 0))
            } else {
                appendMediaSegment(job, segment, index, bytes, progress)
            }
            saveProgress(job, progress)
            onSegment()
        }
        progress
    }

    private suspend fun appendMediaSegment(
        job: HlsTrackJob,
        segment: HlsSegment,
        index: Int,
        data: ByteArray?,
        current: HlsTrackProgress,
    ): HlsTrackProgress {
        if (data == null) return current.copy(doneSegments = index + 1)
        val group = segment.discontinuityIndex
        var progress = current
        var bytes = data
        val format = progress.format ?: sniffSegmentFormat(bytes, segment.initSegment != null).also { format ->
            log.i { "Track ${job.id} segments are $format" }
            if (format == HlsSegmentFormat.Unsupported || format == HlsSegmentFormat.WebVtt) {
                throw HlsDownloadException(getString(Res.string.hls_download_error_convert, "unsupported segment format"))
            }
        }
        progress = progress.copy(format = format)
        val committed = progress.committedLengths[group] ?: 0L
        if (format in setOf(HlsSegmentFormat.PackedAac, HlsSegmentFormat.PackedAc3, HlsSegmentFormat.PackedMp3)) {
            val (pts, payload) = stripId3(bytes)
            bytes = payload
            if (committed == 0L && pts != null && group !in progress.packedPts) {
                progress = progress.copy(packedPts = progress.packedPts + (group to pts))
            }
        }
        val file = groupFile(job, group)
        RandomAccessFile(file, "rw").use { it.setLength(committed) }
        var written = 0L
        FileOutputStream(file, true).use { output ->
            if (committed == 0L && segment.initSegment != null) {
                val init = fetchInit(segment.initSegment)
                output.write(init)
                written += init.size
            }
            output.write(bytes)
            written += bytes.size
            output.fd.sync()
        }
        return progress.copy(
            doneSegments = index + 1,
            committedLengths = progress.committedLengths + (group to committed + written),
            bytes = progress.bytes + written,
        )
    }

    /** Segment bytes, decrypted; null for gaps. Transient errors are retried a few times. */
    private suspend fun fetchSegment(segment: HlsSegment): ByteArray? {
        if (segment.isGap) return null
        var attempt = 0
        while (true) {
            try {
                val raw = fetch(segment.uri, segment.byteRange).bytes
                val key = segment.key ?: return raw
                return decrypt(raw, key, segment.mediaSequence)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val transient = error is IOException && (error !is DownloadHttpException ||
                    error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500)
                if (!transient || ++attempt >= SEGMENT_ATTEMPTS) throw error
                log.w { "Segment ${segment.mediaSequence} failed (${error.message}), retry $attempt" }
                delay(1_000L * attempt * attempt)
            }
        }
    }

    private suspend fun fetchInit(init: HlsInitSegment): ByteArray {
        val cacheKey = "${init.uri}|${init.byteRange}"
        initSegments[cacheKey]?.let { return it }
        return fetch(init.uri, init.byteRange).bytes.also { initSegments[cacheKey] = it }
    }

    private suspend fun decrypt(data: ByteArray, key: HlsKey, mediaSequence: Long): ByteArray {
        val keyUri = key.uri ?: throw HlsDownloadException(getString(Res.string.hls_download_error_key))
        val keyBytes = keys[keyUri] ?: try {
            fetch(keyUri, null).bytes.also { keys[keyUri] = it }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.e(error) { "Could not get the HLS decryption key" }
            throw HlsDownloadException(getString(Res.string.hls_download_error_key))
        }
        if (keyBytes.size != 16) throw HlsDownloadException(getString(Res.string.hls_download_error_key))
        val iv = key.iv ?: ByteArray(16).also { iv ->
            for (index in 0 until 8) iv[15 - index] = (mediaSequence ushr (8 * index)).toByte()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private class Fetched(val url: String, val bytes: ByteArray)

    /** GET with the stream headers; [range] becomes a Range request (sliced by hand on a plain 200). */
    private suspend fun fetch(url: String, range: HlsByteRange?): Fetched = coroutineScope {
        val request = Request.Builder().url(url).apply {
            item.sourceHeaders.forEach { (name, value) ->
                if (!name.equals("Range", true) && !name.equals("Accept-Encoding", true)) header(name, value)
            }
            range?.let { header("Range", "bytes=${it.offset}-${it.offset + it.length - 1}") }
        }.build()
        val call = client.newCall(request)
        // The blocking call runs in a child so that cancelling the download cancels the request.
        val result = async(Dispatchers.IO) {
            call.execute().use { response ->
                if (!response.isSuccessful) throw DownloadHttpException(response.code)
                val body = response.body?.bytes() ?: ByteArray(0)
                val bytes = if (range != null && response.code == 200) {
                    val end = (range.offset + range.length).coerceAtMost(body.size.toLong()).toInt()
                    body.copyOfRange(range.offset.coerceAtMost(end.toLong()).toInt(), end)
                } else {
                    body
                }
                Fetched(response.request.url.toString(), bytes)
            }
        }
        try {
            result.await()
        } finally {
            call.cancel()
        }
    }

    private fun saveProgress(job: HlsTrackJob, progress: HlsTrackProgress) {
        state = state.copy(tracks = state.tracks + (job.id to progress))
        val temp = File(workDir, "state.json.tmp")
        temp.writeText(json.encodeToString(state))
        if (!temp.renameTo(stateFile)) {
            stateFile.delete()
            temp.renameTo(stateFile)
        }
    }

    private fun groupFile(job: HlsTrackJob, group: Int) = File(workDir, "${job.id}_g$group.bin")

    private fun subtitleFile(job: HlsTrackJob, index: Int) = File(workDir, "${job.id}_$index.vtt")

    private fun HlsMediaPlaylist.looksLikeWebVtt(): Boolean {
        if (segments.any { it.initSegment != null }) return false
        val extensions = segments.map { it.uri.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase() }
        return extensions.none { it == "mp4" || it == "m4s" || it == "cmft" }
    }

    private companion object {
        const val PARALLEL_SEGMENTS = 3
        const val SEGMENT_ATTEMPTS = 3
    }
}

/** Container of a media segment from its first bytes. */
internal fun sniffSegmentFormat(data: ByteArray, hasInitSegment: Boolean): HlsSegmentFormat {
    if (hasInitSegment) return HlsSegmentFormat.Fmp4
    if (data.size >= 188 && data[0].toInt() == 0x47) return HlsSegmentFormat.Ts
    if (data.size >= 6 && String(data, 0, 6, Charsets.US_ASCII) == "WEBVTT") return HlsSegmentFormat.WebVtt
    if (data.size >= 8) {
        val box = String(data, 4, 4, Charsets.US_ASCII)
        if (box == "ftyp" || box == "styp" || box == "moof" || box == "sidx") return HlsSegmentFormat.Fmp4
    }
    val payload = stripId3(data).second
    if (payload.size >= 2) {
        val first = payload[0].toInt() and 0xFF
        val second = payload[1].toInt() and 0xFF
        return when {
            first == 0xFF && second and 0xF6 == 0xF0 -> HlsSegmentFormat.PackedAac
            first == 0x0B && second == 0x77 -> HlsSegmentFormat.PackedAc3
            first == 0xFF && second and 0xE0 == 0xE0 -> HlsSegmentFormat.PackedMp3
            else -> HlsSegmentFormat.Unsupported
        }
    }
    return HlsSegmentFormat.Unsupported
}

private const val TRANSPORT_STREAM_TIMESTAMP_OWNER = "com.apple.streaming.transportStreamTimestamp"

/**
 * Removes the ID3 tags at the start of a packed audio segment and returns the MPEG-2 timestamp
 * of their `com.apple.streaming.transportStreamTimestamp` PRIV frame (RFC 8216 §3.4).
 */
internal fun stripId3(data: ByteArray): Pair<Long?, ByteArray> {
    var offset = 0
    var pts: Long? = null
    while (data.size - offset >= 10 && data[offset].toInt() == 'I'.code && data[offset + 1].toInt() == 'D'.code &&
        data[offset + 2].toInt() == '3'.code
    ) {
        val flags = data[offset + 5].toInt()
        val size = syncSafe(data, offset + 6)
        val end = offset + 10 + size + if (flags and 0x10 != 0) 10 else 0
        if (end > data.size) break
        pts = pts ?: findTransportStreamTimestamp(data, offset + 10, offset + 10 + size)
        offset = end
    }
    return pts to if (offset == 0) data else data.copyOfRange(offset, data.size)
}

private fun findTransportStreamTimestamp(data: ByteArray, start: Int, end: Int): Long? {
    var position = start
    while (position + 10 <= end) {
        val id = String(data, position, 4, Charsets.US_ASCII)
        val size = ((data[position + 4].toInt() and 0xFF) shl 24) or ((data[position + 5].toInt() and 0xFF) shl 16) or
            ((data[position + 6].toInt() and 0xFF) shl 8) or (data[position + 7].toInt() and 0xFF)
        if (size <= 0 || id.any { !it.isLetterOrDigit() }) return null
        val bodyStart = position + 10
        val bodyEnd = (bodyStart + size).coerceAtMost(end)
        if (id == "PRIV") {
            val ownerEnd = (bodyStart until bodyEnd).firstOrNull { data[it].toInt() == 0 } ?: return null
            val owner = String(data, bodyStart, ownerEnd - bodyStart, Charsets.ISO_8859_1)
            if (owner == TRANSPORT_STREAM_TIMESTAMP_OWNER && bodyEnd - ownerEnd - 1 >= 8) {
                var value = 0L
                for (index in 0 until 8) value = (value shl 8) or (data[ownerEnd + 1 + index].toLong() and 0xFF)
                return value and 0x1FFFFFFFFL
            }
        }
        position = bodyStart + size
    }
    return null
}

private fun syncSafe(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0x7F) shl 21) or ((data[offset + 1].toInt() and 0x7F) shl 14) or
        ((data[offset + 2].toInt() and 0x7F) shl 7) or (data[offset + 3].toInt() and 0x7F)

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private fun formatSize(bytes: Long): String {
    val gigabytes = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gigabytes >= 1.0) "%.1f GB".format(gigabytes) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
}
