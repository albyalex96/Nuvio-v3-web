package com.nuvio.app.core.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import java.io.File

private val log = Logger.withTag("AppLogShare")

/** Desktop has no share sheet: exports go to the user's Downloads folder. */
private fun exportDirectory(): File =
    File(System.getProperty("user.home").orEmpty(), "Downloads").takeIf { it.isDirectory }
        ?: File(System.getProperty("user.home").orEmpty())

internal actual fun writeAppLogExportFile(fileName: String, text: String): String? = runCatching {
    File(exportDirectory(), fileName).apply { writeText(text) }.absolutePath
}.onFailure { log.e(it) { "Log export failed" } }.getOrNull()

@Composable
internal actual fun rememberAppLogFileSharer(): (path: String) -> Unit = remember {
    { path -> revealInFileManager(File(path)) }
}

internal fun revealInFileManager(file: File) {
    runCatching {
        if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
        } else {
            java.awt.Desktop.getDesktop().open(file.parentFile)
        }
    }.onFailure { log.e(it) { "Could not open ${file.parentFile}" } }
}
