package com.nuvio.app.features.downloads

import com.nuvio.app.web.compat.WebFileStore

internal actual class DownloadSubtitleStorage actual constructor(localVideoUri: String) {
    private val directory = "downloads/" + localVideoUri.substringAfter("://") + ".subtitles"

    actual fun read(fileName: String): String? = WebFileStore.read(path(fileName))

    actual fun write(fileName: String, text: String) = WebFileStore.write(path(fileName), text)

    actual fun localFileUri(fileName: String): String? =
        path(fileName).takeIf { WebFileStore.exists(it) }?.let { WebFileStore.uri(it) }

    actual fun remove() = WebFileStore.deleteTree(directory)

    private fun path(fileName: String): String {
        require(fileName.isNotBlank() && '/' !in fileName && fileName != "." && fileName != "..")
        return "$directory/$fileName"
    }
}
