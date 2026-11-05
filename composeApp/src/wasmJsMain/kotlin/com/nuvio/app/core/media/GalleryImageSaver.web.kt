package com.nuvio.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nuvio.app.web.compat.downloadBinaryFile

/** Web: images are saved through the browser's download flow. */
@Composable
actual fun rememberGalleryImageSaver(): GalleryImageSaver = remember {
    object : GalleryImageSaver {
        override suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
            if (downloadBinaryFile(fileName, bytes, mimeType)) GalleryImageSaveResult.SAVED else GalleryImageSaveResult.FAILED

        override suspend fun share(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
            if (downloadBinaryFile(fileName, bytes, mimeType)) GalleryImageSaveResult.SHARED else GalleryImageSaveResult.FAILED
    }
}
