package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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

/** Pointer icon used while the controls are hidden during playback; null where the platform keeps the default. */
internal expect val hiddenPlayerPointerIcon: androidx.compose.ui.input.pointer.PointerIcon?
