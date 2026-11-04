package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import co.touchlab.kermit.Logger
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.abs

private val log = Logger.withTag("MouseDragScroll")

/**
 * Desktop: a horizontal row scrolls by holding the left mouse button and dragging, with the
 * same inertia as a touch swipe. Once the drag passes the touch slop the events are consumed,
 * so releasing the button over a poster does not open it. No effect on touch devices.
 */
@Composable
fun Modifier.mouseDragScroll(state: ScrollableState): Modifier {
    if (!isDesktop) return this
    val fling = ScrollableDefaults.flingBehavior()
    return mouseDragScroll(state, fling)
}

private fun Modifier.mouseDragScroll(state: ScrollableState, fling: FlingBehavior): Modifier =
    pointerInput(state, fling) {
        val scope = CoroutineScope(currentCoroutineContext())
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull { it.type == PointerType.Mouse && it.pressed && !it.previousPressed }
                    ?: continue
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                var total = 0f
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            val velocity = -tracker.calculateVelocity().x
                            scope.launch {
                                state.scroll { with(fling) { performFling(velocity) } }
                            }
                        }
                        break
                    }
                    val dx = change.position.x - change.previousPosition.x
                    total += dx
                    if (!dragging && abs(total) > viewConfiguration.touchSlop) {
                        dragging = true
                        log.d { "Row drag started" }
                    }
                    if (dragging) {
                        state.dispatchRawDelta(-dx)
                        change.consume()
                    }
                }
            }
        }
    }
