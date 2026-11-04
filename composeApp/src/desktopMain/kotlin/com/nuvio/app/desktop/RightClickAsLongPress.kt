package com.nuvio.app.desktop

import co.touchlab.kermit.Logger
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Timer

/**
 * Makes the right mouse button behave like holding the left one, in the whole app: posters
 * and episodes open their options, streams their menu, the player speeds up while it is held.
 *
 * Done once at AWT level, so every long-press handler in the shared UI works unchanged: a
 * right press becomes a left press, and the left release is sent when the right button is
 * released, but never before [MIN_HOLD_MS], so a quick right click still counts as a long press.
 */
internal object RightClickAsLongPress {
    private val log = Logger.withTag("DesktopInput")

    /** Long-press timeout of the desktop window (Compose default 500 ms): right click menus open sooner. */
    const val LONG_PRESS_TIMEOUT_MS = 200L

    /** A bit more than [LONG_PRESS_TIMEOUT_MS], so a quick right click still counts as a long press. */
    private const val MIN_HOLD_MS = 260

    fun install() {
        Toolkit.getDefaultToolkit().systemEventQueue.push(TranslatingQueue())
        log.i { "Right button mapped to long press" }
    }

    private class TranslatingQueue : EventQueue() {
        private var leftDown: MouseEvent? = null
        private var pressedAt = 0L
        private var pendingRelease: Timer? = null

        override fun dispatchEvent(event: AWTEvent) {
            if (event !is MouseEvent) return super.dispatchEvent(event)
            val right = event.button == MouseEvent.BUTTON3 ||
                (event.id == MouseEvent.MOUSE_DRAGGED && event.modifiersEx and InputEvent.BUTTON3_DOWN_MASK != 0)
            when {
                right && event.id == MouseEvent.MOUSE_PRESSED -> {
                    finishPendingRelease()
                    pressedAt = System.currentTimeMillis()
                    val press = translated(event, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK)
                    leftDown = press
                    super.dispatchEvent(press)
                }
                right && event.id == MouseEvent.MOUSE_DRAGGED -> {
                    if (leftDown != null) {
                        super.dispatchEvent(translated(event, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK))
                    }
                }
                right && event.id == MouseEvent.MOUSE_RELEASED -> {
                    leftDown ?: return
                    val release = translated(event, MouseEvent.MOUSE_RELEASED, 0)
                    val remaining = MIN_HOLD_MS - (System.currentTimeMillis() - pressedAt)
                    if (remaining <= 0) {
                        leftDown = null
                        super.dispatchEvent(release)
                    } else {
                        pendingRelease = Timer(remaining.toInt()) { finishPendingRelease(release) }
                            .apply { isRepeats = false; start() }
                    }
                }
                right -> Unit // MOUSE_CLICKED of the right button: the long press replaces it.
                // While the synthetic hold runs out, a plain move would end it early.
                pendingRelease != null && event.id == MouseEvent.MOUSE_MOVED -> Unit
                else -> {
                    if (event.id == MouseEvent.MOUSE_PRESSED) finishPendingRelease()
                    super.dispatchEvent(event)
                }
            }
        }

        private fun finishPendingRelease(release: MouseEvent? = null) {
            val timer = pendingRelease ?: return
            timer.stop()
            pendingRelease = null
            val down = leftDown ?: return
            leftDown = null
            super.dispatchEvent(release ?: translated(down, MouseEvent.MOUSE_RELEASED, 0))
        }

        private fun translated(source: MouseEvent, id: Int, buttonMask: Int): MouseEvent {
            val otherModifiers = source.modifiersEx and
                (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK).inv()
            return MouseEvent(
                source.source as Component,
                id,
                source.`when`,
                otherModifiers or buttonMask,
                source.x,
                source.y,
                source.xOnScreen,
                source.yOnScreen,
                1,
                false,
                MouseEvent.BUTTON1,
            )
        }
    }
}
