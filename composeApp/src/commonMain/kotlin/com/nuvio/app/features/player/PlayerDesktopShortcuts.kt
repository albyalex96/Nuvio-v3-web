package com.nuvio.app.features.player

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import co.touchlab.kermit.Logger
import com.nuvio.app.isDesktop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridge between the player and the desktop window, which owns the fullscreen state. */
object PlayerWindowFullscreen {
    private val state = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = state.asStateFlow()

    /** Set by the desktop window; null where the platform has no window to resize. */
    var handler: ((Boolean) -> Unit)? = null

    val isSupported: Boolean get() = handler != null

    fun set(fullscreen: Boolean) {
        handler?.invoke(fullscreen)
    }

    fun toggle() = set(!state.value)

    /** Called by the window when its placement changes (also from the OS or the title bar). */
    fun reportState(fullscreen: Boolean) {
        state.value = fullscreen
    }
}

private const val KEY_SEEK_STEP_MS = 10_000L
private const val KEY_VOLUME_STEP = 0.05f
private val shortcutLog = Logger.withTag("PlayerShortcuts")

private fun PlayerScreenRuntime.isAnyPanelOpen(): Boolean =
    showSourcesPanel || showEpisodesPanel || showChaptersPanel || showLiveChannelsPanel ||
        showSubmitIntroModal || showAudioModal || showSubtitleModal || showVideoSettingsModal ||
        showPartyPanel || showCastPicker

private fun PlayerScreenRuntime.changeVolume(delta: Float) {
    val controller = gestureController ?: return
    val current = controller.currentVolume() ?: return
    val base = if (current.isMuted) 0f else current.fraction
    controller.setVolume((base + delta).coerceIn(0f, 1f))?.let(::showVolumeFeedback)
}

private fun PlayerScreenRuntime.toggleMute() {
    val controller = gestureController ?: return
    val current = controller.currentVolume() ?: return
    playerController?.setMuted(!current.isMuted)
    controller.currentVolume()?.let(::showVolumeFeedback)
}

/**
 * Desktop keyboard and mouse wheel: Space/K play-pause, ←/→ (J/L) seek 10 s, ↑/↓ and the wheel
 * change the volume, M mute, F fullscreen, Esc leaves fullscreen. Off on mobile and TV, which
 * have their own input.
 */
@Composable
internal fun PlayerScreenRuntime.desktopPlayerInputModifier(): Modifier {
    if (!isDesktop) return Modifier
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    DisposableEffect(Unit) {
        onDispose { if (PlayerWindowFullscreen.isFullscreen.value) PlayerWindowFullscreen.set(false) }
    }
    return Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || isAnyPanelOpen()) return@onPreviewKeyEvent false
            val handled = when (event.key) {
                Key.Spacebar, Key.K, Key.MediaPlayPause -> { togglePlayback(); true }
                Key.DirectionLeft, Key.J -> { seekBy(-KEY_SEEK_STEP_MS); true }
                Key.DirectionRight, Key.L -> { seekBy(KEY_SEEK_STEP_MS); true }
                Key.DirectionUp -> { changeVolume(KEY_VOLUME_STEP); true }
                Key.DirectionDown -> { changeVolume(-KEY_VOLUME_STEP); true }
                Key.M -> { toggleMute(); true }
                Key.F -> { PlayerWindowFullscreen.toggle(); true }
                Key.Escape -> if (PlayerWindowFullscreen.isFullscreen.value) {
                    PlayerWindowFullscreen.set(false)
                    true
                } else {
                    false
                }
                else -> false
            }
            if (handled) {
                shortcutLog.d { "Key ${event.key}" }
                if (event.key != Key.F && event.key != Key.Escape) revealControlsForMouse()
            }
            handled
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.type != PointerEventType.Scroll || isAnyPanelOpen()) continue
                    if (event.changes.any { it.isConsumed }) continue
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (delta != 0f) changeVolume(if (delta < 0f) KEY_VOLUME_STEP else -KEY_VOLUME_STEP)
                }
            }
        }
}
