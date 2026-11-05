package com.nuvio.app.core.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.nuvio.app.web.compat.WebFileStore
import com.nuvio.app.web.compat.downloadTextFile

private val log = Logger.withTag("AppLogShare")
private const val EXPORT_DIR = "app_log_exports"

/** Web: the export is kept in memory storage, then handed to the browser as a download. */
internal actual fun writeAppLogExportFile(fileName: String, text: String): String? {
    WebFileStore.deleteTree(EXPORT_DIR)
    val path = "$EXPORT_DIR/$fileName"
    WebFileStore.write(path, text)
    return path
}

@Composable
internal actual fun rememberAppLogFileSharer(): (path: String) -> Unit = remember {
    { path ->
        val text = WebFileStore.read(path)
        if (text == null) {
            log.w { "Log export not found: $path" }
        } else if (!downloadTextFile(path.substringAfterLast('/'), text)) {
            log.e { "Log export download failed" }
        }
    }
}
