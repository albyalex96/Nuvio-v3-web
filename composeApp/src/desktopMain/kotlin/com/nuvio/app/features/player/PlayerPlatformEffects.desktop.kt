package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase

private val log = Logger.withTag("DesktopPlayerEffects")
private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

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
    if (!isWindows) return
    runCatching {
        val flags = if (required) {
            WinBase.ES_CONTINUOUS or WinBase.ES_DISPLAY_REQUIRED or WinBase.ES_SYSTEM_REQUIRED
        } else {
            WinBase.ES_CONTINUOUS
        }
        Kernel32.INSTANCE.SetThreadExecutionState(flags)
        log.d { "Display keep-awake ${if (required) "on" else "off"}" }
    }.onFailure { log.w(it) { "SetThreadExecutionState failed" } }
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
    remember { DesktopMpvGestureController }

private object DesktopMpvGestureController : PlayerGestureController {
    override fun currentBrightness(): Float? = null

    override fun setBrightness(level: Float): Float? = null

    override fun currentVolume(): PlayerAudioLevel? {
        val player = com.nuvio.app.desktop.mpv.DesktopActivePlayer.current ?: return null
        val volume = player.doubleProperty("volume") ?: return null
        return PlayerAudioLevel(fraction = (volume / 100.0).toFloat().coerceIn(0f, 1f), isMuted = player.flagProperty("mute"))
    }

    override fun setVolume(level: Float): PlayerAudioLevel? {
        val player = com.nuvio.app.desktop.mpv.DesktopActivePlayer.current ?: return null
        val target = level.coerceIn(0f, 1f)
        player.setProperty("volume", (target * 100f).toString())
        if (target > 0f) player.setProperty("mute", "no")
        com.nuvio.app.desktop.mpv.DesktopActivePlayer.rememberVolume(target)
        return currentVolume()
    }
}
