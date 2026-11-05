package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import com.nuvio.app.web.compat.pickTextFile

private val log = Logger.withTag("LiveTvPlaylistPicker")

@Composable
internal actual fun rememberLiveTvPlaylistFilePicker(
    onPlaylistLoaded: (fileName: String?, content: String) -> Unit,
    onError: (String) -> Unit,
): LiveTvPlaylistFilePicker {
    val loaded = rememberUpdatedState(onPlaylistLoaded)
    val failed = rememberUpdatedState(onError)
    return LiveTvPlaylistFilePicker(canPickFiles = true) {
        pickTextFile(
            accept = ".m3u,.m3u8,.txt",
            onLoaded = { name, content ->
                log.i { "Playlist loaded from file (${content.length} chars)" }
                loaded.value(name, content)
            },
            onError = { message ->
                log.e { "Playlist file read failed: $message" }
                failed.value(message)
            },
        )
    }
}
