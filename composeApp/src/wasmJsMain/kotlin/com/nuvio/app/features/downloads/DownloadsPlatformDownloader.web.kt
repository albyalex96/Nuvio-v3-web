package com.nuvio.app.features.downloads

import co.touchlab.kermit.Logger

/**
 * Web: offline downloads are not available (the browser cannot write large files to disk
 * in the background). Every request fails immediately with a clear message.
 */
internal actual object DownloadsPlatformDownloader {
    private val log = Logger.withTag("WebDownloads")

    actual val supportsHls: Boolean = false

    actual val supportsEpisodeFolders: Boolean = false

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?, conversionProgress: Float?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle {
        log.w { "Download requested on the web: not supported" }
        onFailure("Downloads are not available in the web version")
        return object : DownloadsTaskHandle {
            override fun cancel() = Unit
        }
    }

    actual fun restoreItem(item: DownloadItem): DownloadItem = item

    actual fun removeFile(localFileUri: String?): Boolean = true

    actual fun removePartialFile(item: DownloadItem): Boolean = true

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? = null

    actual fun subtitleAnchorUri(item: DownloadItem, localFileUri: String): String = localFileUri

    actual fun openDownloadsDirectory(): Boolean = false
}
