package com.nuvio.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.revealInFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val log = Logger.withTag("GalleryImageSave")

/** Desktop: images go to Pictures/Nuvio; "share" reveals the saved file in the file manager. */
@Composable
actual fun rememberGalleryImageSaver(): GalleryImageSaver = remember {
    object : GalleryImageSaver {
        override suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
            write(bytes, fileName)?.let { GalleryImageSaveResult.SAVED } ?: GalleryImageSaveResult.FAILED

        override suspend fun share(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult {
            val file = write(bytes, fileName) ?: return GalleryImageSaveResult.FAILED
            revealInFileManager(file)
            return GalleryImageSaveResult.SHARED
        }
    }
}

private suspend fun write(bytes: ByteArray, fileName: String): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(File(System.getProperty("user.home").orEmpty(), "Pictures"), "Nuvio").apply { mkdirs() }
        File(dir, fileName).apply { writeBytes(bytes) }
    }.onFailure { log.e(it) { "Image save failed" } }.getOrNull()
}
