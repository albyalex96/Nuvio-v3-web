package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onMouseMove] for every mouse movement over the player (desktop, or a mouse on
 * Android/tablets). Touch drags are ignored, and events are never consumed, so taps,
 * gestures and the controls keep working as before.
 */
internal fun Modifier.onPlayerMouseMove(onMouseMove: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            var lastPosition: androidx.compose.ui.geometry.Offset? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                if (event.type != PointerEventType.Move) continue
                // The window also sends synthetic moves when the content under a still pointer
                // changes (controls fading out, cursor hidden): only a real movement counts,
                // otherwise the controls would never hide while the mouse rests on the video.
                if (mouse.position == lastPosition) continue
                lastPosition = mouse.position
                onMouseMove()
            }
        }
    }

/**
 * Mouse right button = holding the video with the left one: speed-up while it stays pressed
 * (or the locked overlay when the controls are locked). The events are consumed so the
 * right click does not also count as a tap.
 */
internal fun Modifier.playerRightButtonHold(
    enabled: Boolean,
    playerControlsLockedState: androidx.compose.runtime.State<Boolean>,
    activateHoldToSpeedState: androidx.compose.runtime.State<() -> Unit>,
    deactivateHoldToSpeedState: androidx.compose.runtime.State<() -> Unit>,
    revealLockedOverlayState: androidx.compose.runtime.State<() -> Unit>,
): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            var holding = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                val secondary = event.buttons.isSecondaryPressed
                when {
                    event.type == PointerEventType.Press && secondary && !holding -> {
                        holding = true
                        mouse.consume()
                        if (playerControlsLockedState.value) revealLockedOverlayState.value()
                        else activateHoldToSpeedState.value()
                    }
                    holding && !secondary -> {
                        holding = false
                        mouse.consume()
                        deactivateHoldToSpeedState.value()
                    }
                    holding -> mouse.consume()
                }
            }
        }
    }

/** Pointer icon used while the controls are hidden during playback; null where the platform keeps the default. */
internal expect val hiddenPlayerPointerIcon: androidx.compose.ui.input.pointer.PointerIcon?
