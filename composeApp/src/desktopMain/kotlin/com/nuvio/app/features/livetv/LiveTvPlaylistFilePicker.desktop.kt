package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

private val log = Logger.withTag("LiveTvPlaylistPicker")

@Composable
internal actual fun rememberLiveTvPlaylistFilePicker(
    onPlaylistLoaded: (fileName: String?, content: String) -> Unit,
    onError: (String) -> Unit,
): LiveTvPlaylistFilePicker {
    val loaded = rememberUpdatedState(onPlaylistLoaded)
    val failed = rememberUpdatedState(onError)
    return LiveTvPlaylistFilePicker(canPickFiles = true) {
        SwingUtilities.invokeLater {
            val dialog = FileDialog(null as Frame?, "M3U", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.endsWith(".m3u", true) || name.endsWith(".m3u8", true) || name.endsWith(".txt", true) }
                file = "*.m3u;*.m3u8;*.txt"
                isVisible = true
            }
            val name = dialog.file ?: return@invokeLater
            val file = File(dialog.directory, name)
            runCatching { file.readText() }
                .onSuccess { content ->
                    log.i { "Playlist loaded from file (${content.length} chars)" }
                    loaded.value(file.name, content)
                }
                .onFailure { error ->
                    log.e(error) { "Playlist file read failed" }
                    failed.value(error.message ?: "Unable to read the file.")
                }
        }
    }
}
