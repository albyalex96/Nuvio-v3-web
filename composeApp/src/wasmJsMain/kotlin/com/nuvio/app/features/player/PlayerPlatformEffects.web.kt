package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger

private val log = Logger.withTag("WebPlayerEffects")

// A desktop window has no orientation lock.
@Composable
actual fun LockPlayerToLandscape() = Unit

@Composable
actual fun FullscreenPlayerDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = content,
    )
}

@Composable
actual fun HidePlayerSystemBars() = Unit

/** Keeps the display on while the player is open (Windows `SetThreadExecutionState`). */
@Composable
actual fun EnterImmersivePlayerMode(keepScreenAwake: Boolean) {
    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) setDisplayRequired(true)
        onDispose { if (keepScreenAwake) setDisplayRequired(false) }
    }
}

private fun setDisplayRequired(required: Boolean) {
    com.nuvio.app.web.compat.setScreenWakeLock(required)
    log.d { "Screen wake lock ${if (required) "on" else "off"}" }
}

// Picture-in-picture is not offered on desktop (resize the window instead).
@Composable
actual fun ManagePlayerPictureInPicture(isPlaying: Boolean, videoSize: IntSize) = Unit

@Composable
actual fun rememberIsInPictureInPicture(): Boolean = false

@Composable
internal actual fun OnPlayerScreenOff(onScreenOff: () -> Unit) = Unit

// The JVM keeps running after the player closes: nothing to keep alive.
@Composable
internal actual fun rememberProgressUploadKeepAlive(): () -> Unit = remember { {} }

/** Volume of the active mpv player (keyboard arrows, mouse wheel, M); no screen brightness on desktop. */
@Composable
internal actual fun rememberPlatformPlayerGestureController(): PlayerGestureController? =
    remember { null }

