package com.nuvio.app.features.downloads

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.NuvioDns
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.downloads.hls.HlsDownloadTask
import com.nuvio.app.features.downloads.hls.HlsOutputContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

private val desktopDownloadHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(60))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

private val hlsHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(NuvioDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * Desktop downloads: direct files over HTTP (with resume) and HLS streams (segments downloaded
 * by [HlsDownloadTask], merged with FFmpeg). Files go to the folder chosen in the settings
 * (default Videos\Nuvio), inside the series/season folders when that option is on.
 */
internal actual object DownloadsPlatformDownloader {
    private val log = Logger.withTag("DesktopDownloads")

    /** True when the FFmpeg natives load: without them HLS downloads stay hidden. */
    actual val supportsHls: Boolean by lazy {
        runCatching {
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat::class.java)
            true
        }.onFailure { log.e(it) { "FFmpeg not available: HLS downloads disabled" } }.getOrDefault(false)
    }

    actual val supportsEpisodeFolders: Boolean = true

    actual fun restoreItem(item: DownloadItem): DownloadItem =
        if (item.status == DownloadStatus.Downloading) {
            item.copy(status = DownloadStatus.Paused, errorMessage = null, conversionProgress = null)
        } else {
            item
        }

    private fun baseDirectory(item: DownloadItem): File =
        item.storageTreeUri?.let(DownloadsFolderPlatform::toDirectory)?.takeIf { it.isDirectory }
            ?: DownloadsFolderPlatform.defaultDirectory

    private fun targetDirectory(item: DownloadItem): File =
        item.folderPath.fold(baseDirectory(item)) { dir, part -> File(dir, part) }.apply { mkdirs() }

    private fun partialFile(item: DownloadItem): File =
        File(targetDirectory(item), "${item.savedFileName}.part")

    private fun hlsWorkDir(item: DownloadItem): File =
        DesktopStorage.cacheDir.resolve("download-hls").resolve(item.fileName.substringBeforeLast('.')).toFile()

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?, conversionProgress: Float?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val item = request.item

        scope.launch {
            try {
                val destination = if (item.hls != null) {
                    downloadHls(item, onProgress)
                } else {
                    downloadDirect(request, onProgress)
                }
                log.i { "Download ${item.id} completed (${destination.length()} bytes)" }
                onSuccess(destination.toURI().toString(), destination.length())
            } catch (error: CancellationException) {
                log.i { "Download ${item.id} paused" }
                onPaused()
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Download ${item.id} failed" }
                onFailure(error.message ?: "Download failed")
            }
        }

        return DesktopDownloadsTaskHandle(job)
    }

    private suspend fun downloadHls(
        item: DownloadItem,
        onProgress: (Long, Long?, Float?) -> Unit,
    ): File {
        check(supportsHls) { "FFmpeg is not available" }
        val directory = targetDirectory(item)
        val baseName = item.savedFileName.substringBeforeLast('.')
        HlsOutputContainer.entries.map { File(directory, "$baseName.${it.extension}") }
            .firstOrNull { it.isFile }
            ?.let { return it }

        val workDir = hlsWorkDir(item)
        var lastDownloaded = 0L
        var lastTotal: Long? = null
        val result = HlsDownloadTask(item, workDir, hlsHttpClient).run(
            onProgress = { bytes, total ->
                lastDownloaded = bytes
                lastTotal = total
                onProgress(bytes, total, null)
            },
            onConverting = { fraction -> onProgress(lastDownloaded, lastTotal, fraction) },
        )
        val destination = File(directory, "$baseName.${result.container.extension}")
        destination.delete()
        if (!result.file.renameTo(destination)) {
            result.file.copyTo(destination, overwrite = true)
            result.file.delete()
        }
        result.sidecarSubtitles.forEach { (track, text) ->
            runCatching {
                DownloadSubtitles.saveStreamTrack(
                    anchorUri = destination.toURI().toString(),
                    sourceKey = "hls:${track.playlistUrl}",
                    text = text,
                    extension = "srt",
                    language = track.language ?: "und",
                    name = track.name ?: track.label,
                )
            }.onFailure { error -> log.w(error) { "Could not save the subtitle ${track.label}" } }
        }
        workDir.deleteRecursively()
        return destination
    }

    private suspend fun downloadDirect(
        request: DownloadPlatformRequest,
        onProgress: (Long, Long?, Float?) -> Unit,
    ): File {
        val item = request.item
        val destination = File(targetDirectory(item), item.savedFileName)
        val tempFile = partialFile(item)

        var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
        var attemptedRangeRequest = resumeFromBytes > 0L
        var response = sendDownloadRequest(request, if (attemptedRangeRequest) resumeFromBytes else null)

        if (attemptedRangeRequest && response.statusCode() == 416) {
            tempFile.delete()
            resumeFromBytes = 0L
            attemptedRangeRequest = false
            response = sendDownloadRequest(request, null)
        }
        if (response.statusCode() !in 200..299) {
            error("Download failed with HTTP ${response.statusCode()}")
        }

        val isPartialResume = attemptedRangeRequest && response.statusCode() == 206 && resumeFromBytes > 0L
        val startingBytes = if (isPartialResume) resumeFromBytes else 0L
        if (!isPartialResume && tempFile.exists()) tempFile.delete()
        if (isPartialResume) log.i { "Resuming ${item.id} from $resumeFromBytes bytes" }

        val totalBytes = resolveTotalBytes(
            startingBytes = startingBytes,
            isPartialResume = isPartialResume,
            contentRangeHeader = response.headers().firstValue("Content-Range").orElse(null),
            contentLength = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull(),
        )
        var downloadedBytes = startingBytes
        var lastReportAt = 0L
        onProgress(downloadedBytes, totalBytes, null)

        response.body().use { input ->
            FileOutputStream(tempFile, isPartialResume).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read.toLong()
                    val now = System.currentTimeMillis()
                    if (now - lastReportAt >= 500L) {
                        lastReportAt = now
                        onProgress(downloadedBytes, totalBytes, null)
                    }
                }
                output.flush()
            }
        }

        if (destination.exists()) destination.delete()
        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }
        return destination
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val file = localFileUri.toLocalFileOrNull() ?: return false
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (deleted) removeEmptyFolders(file.parentFile)
        return deleted
    }

    /** Season and series folders left empty by the deleted episode. */
    private fun removeEmptyFolders(folder: File?) {
        val roots = setOfNotNull(
            DownloadsFolderPlatform.defaultDirectory.canonicalFile,
            DownloadsFolderPlatform.selectedFolder.value?.treeUri
                ?.let(DownloadsFolderPlatform::toDirectory)?.canonicalFile,
        )
        var current = folder?.canonicalFile ?: return
        while (current !in roots && roots.any { current.path.startsWith(it.path) } &&
            current.listFiles()?.isEmpty() == true && current.delete()
        ) {
            current = current.parentFile ?: return
        }
    }

    actual fun subtitleAnchorUri(item: DownloadItem, localFileUri: String): String = localFileUri

    actual fun removePartialFile(item: DownloadItem): Boolean {
        hlsWorkDir(item).takeIf { it.exists() }?.deleteRecursively()
        val tempFile = partialFile(item)
        if (!tempFile.exists()) return true
        return runCatching { tempFile.delete() }.getOrDefault(false)
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        localFileUri
            ?.toLocalFileOrNull()
            ?.takeIf { it.exists() }
            ?.let { return it.toURI().toString() }

        val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
            ?: localFileUri?.toLocalFileOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: return null
        // Downloads made by the first desktop builds lived in the app data folder.
        return sequenceOf(
            DownloadsFolderPlatform.defaultDirectory,
            DesktopStorage.rootDir.resolve("downloads").toFile(),
        ).map { File(it, fileName) }.firstOrNull { it.exists() }?.toURI()?.toString()
    }

    actual fun openDownloadsDirectory(): Boolean =
        DownloadsFolderPlatform.openFolder(DownloadsFolderPlatform.selectedFolder.value?.treeUri)

    private fun sendDownloadRequest(
        request: DownloadPlatformRequest,
        rangeStart: Long?,
    ): HttpResponse<java.io.InputStream> {
        val builder = HttpRequest.newBuilder()
            .uri(URI(request.sourceUrl))
            .timeout(Duration.ofSeconds(60))
            .GET()
        request.sourceHeaders.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.equals("Range", ignoreCase = true)) {
                runCatching { builder.header(key, value) }
            }
        }
        if (rangeStart != null && rangeStart > 0L) {
            builder.header("Range", "bytes=$rangeStart-")
        }
        return desktopDownloadHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    }
}

private class DesktopDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? =
    runCatching { if (startsWith("file:")) File(URI(this)) else File(this) }.getOrNull()

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    contentRangeHeader?.substringAfter('/', "")?.toLongOrNull()?.let { return it }
    val length = contentLength ?: return null
    return if (isPartialResume) startingBytes + length else length
}
