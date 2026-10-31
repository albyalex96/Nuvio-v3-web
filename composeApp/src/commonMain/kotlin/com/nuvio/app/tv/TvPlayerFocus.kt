package com.nuvio.app.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

private val log = Logger.withTag("TvPlayerFocus")

/** Frames left to a just-shown element to compose and attach before it takes the focus. */
private const val FOCUS_ATTEMPTS = 20

/** Pause after the last left/right press before the previewed position is applied. */
private const val SEEK_COMMIT_DELAY_MS = 800L

/** Presses closer than this count as a held key (auto-repeat): the step grows. */
private const val SEEK_REPEAT_WINDOW_MS = 220L

/** Held-key presses needed to add one interval to the step, and the step cap. */
private const val SEEK_PRESSES_PER_STEP = 6
private const val SEEK_MAX_MULTIPLIER = 6

/**
 * Focus targets of the in-app player on TV. When the controls appear the timeline takes the
 * focus; when a panel/prompt closes the controls get it back on the last used button.
 */
internal object TvPlayerFocus {
    val timeline = FocusRequester()
    val controls = FocusRequester()

    /** Requests the focus, retrying while the target attaches. True when it succeeded. */
    suspend fun focus(target: FocusRequester, reason: String): Boolean {
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            val done = try {
                target.requestFocus(FocusDirection.Enter)
            } catch (_: IllegalStateException) {
                false // Not attached yet.
            }
            if (done) {
                log.d { "Player focus: $reason" }
                return true
            }
        }
        log.w { "Player focus not placed: $reason" }
        return false
    }
}

/**
 * Seek steps asked while the timeline is not on screen yet (left/right with the controls hidden,
 * rewind/fast-forward keys): the timeline applies them as its own presses as soon as it is
 * composed, so the seek gets the same preview (seek thumbnail) and delayed commit.
 */
internal object TvTimelineSeek {
    /** Pending steps: positive forward, negative backward. */
    var pendingSteps by mutableIntStateOf(0)

    /** Steps the timeline has not taken yet: the next left/right presses must reach it too. */
    val pending: Boolean get() = pendingSteps != 0

    fun request(forward: Boolean) {
        pendingSteps += if (forward) 1 else -1
    }
}

/**
 * Player controls container on TV: Enter restores the last focused control (e.g. the button that
 * opened a panel), the timeline otherwise. No-op without a [TvShell].
 */
@Composable
internal fun Modifier.tvPlayerControls(): Modifier {
    if (LocalTvShell.current == null) return this
    return this
        .focusRequester(TvPlayerFocus.controls)
        .focusRestorer(TvPlayerFocus.timeline)
        .focusGroup()
}

/**
 * Timeline of the player on TV: target of the focus when the controls appear. Left/right move a
 * preview of the position by the configured seek interval (holding the key speeds it up); the
 * player seeks once the presses stop. OK plays/pauses. Up/down leave the timeline as usual. Steps
 * queued in [TvTimelineSeek] are applied the same way. No-op without a [TvShell].
 */
@Composable
internal fun Modifier.tvPlayerTimeline(
    seekIntervalSeconds: Int,
    positionMs: Long,
    durationMs: Long,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
): Modifier {
    if (LocalTvShell.current == null) return this
    val scope = rememberCoroutineScope()
    val seek = remember { SeekPreview() }
    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentInterval by rememberUpdatedState(seekIntervalSeconds)
    val currentOnScrubChange by rememberUpdatedState(onScrubChange)
    val currentOnScrubFinished by rememberUpdatedState(onScrubFinished)
    val currentOnTogglePlayback by rememberUpdatedState(onTogglePlayback)

    fun step(forward: Boolean) {
        val duration = currentDuration
        if (duration <= 0L) return
        val now = TimeSource.Monotonic.markNow()
        val held = seek.lastPress?.let { (now - it).inWholeMilliseconds < SEEK_REPEAT_WINDOW_MS } == true
        seek.heldPresses = if (held) seek.heldPresses + 1 else 0
        seek.lastPress = now
        val multiplier = (1 + seek.heldPresses / SEEK_PRESSES_PER_STEP).coerceAtMost(SEEK_MAX_MULTIPLIER)
        val stepMs = currentInterval.coerceAtLeast(1) * 1_000L * multiplier

        val base = seek.previewMs ?: currentPosition
        val target = (base + if (forward) stepMs else -stepMs).coerceIn(0L, duration)
        seek.previewMs = target
        currentOnScrubChange(target)

        seek.commitJob?.cancel()
        seek.commitJob = scope.launch {
            delay(SEEK_COMMIT_DELAY_MS)
            val position = seek.previewMs ?: return@launch
            seek.previewMs = null
            seek.heldPresses = 0
            log.d { "Timeline seek to ${position}ms" }
            currentOnScrubFinished(position)
        }
    }

    LaunchedEffect(seek) {
        snapshotFlow { TvTimelineSeek.pendingSteps }.collect { steps ->
            if (steps == 0) return@collect
            TvTimelineSeek.pendingSteps = 0
            log.d { "Timeline takes $steps queued seek step(s)" }
            repeat(abs(steps)) { step(forward = steps > 0) }
        }
    }

    return this
        .focusRequester(TvPlayerFocus.timeline)
        .onPreviewKeyEvent { event ->
            if (event.key in ConfirmKeys) {
                // OK on the timeline plays/pauses (a pending preview is applied first).
                if (event.type == KeyEventType.KeyDown) {
                    seek.commitJob?.cancel()
                    seek.previewMs?.let { position ->
                        seek.previewMs = null
                        seek.heldPresses = 0
                        currentOnScrubFinished(position)
                    }
                    log.d { "Timeline OK: toggle playback" }
                    currentOnTogglePlayback()
                }
                return@onPreviewKeyEvent true
            }
            val forward = when (event.key) {
                Key.DirectionRight -> true
                Key.DirectionLeft -> false
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) step(forward)
            true
        }
}

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private class SeekPreview {
    var previewMs: Long? = null
    var lastPress: TimeSource.Monotonic.ValueTimeMark? = null
    var heldPresses = 0
    var commitJob: Job? = null
}

/**
 * The element takes the focus as soon as it appears (TV): player panels, modals and prompts such
 * as "skip intro" or "next episode". Place it before the clickable it applies to, or before a
 * `focusGroup` to focus the first element of a container. With [onlyWithoutOverlay] (player
 * prompts) it never steals the focus from an open panel or from the visible player controls (the
 * controls keep it on the timeline / the button in use); it takes the focus again every time the
 * controls hide while it is still on screen. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvFocusOnAppear(onlyWithoutOverlay: Boolean = false): Modifier {
    if (LocalTvShell.current == null) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        if (!onlyWithoutOverlay) {
            TvPlayerFocus.focus(requester, "element shown")
            return@LaunchedEffect
        }
        // Player prompt: focus only while nothing else of the player is on screen. The flow emits
        // again when the controls or a panel close, so the prompt gets its highlight back.
        snapshotFlow {
            val remote = TvPlayerRemoteRegistry.active
            remote == null || (!remote.controlsVisible && !remote.overlayOpen)
        }.collect { free ->
            if (free) TvPlayerFocus.focus(requester, "prompt shown")
        }
    }
    return focusRequester(requester)
}

/**
 * [tvFocusOnAppear] for a container: its first element takes the focus. With [asFallback] it waits
 * a few frames and acts only if nothing inside took the focus already (e.g. a dialog whose
 * primary button focuses itself). No-op without a [TvShell].
 */
@Composable
fun Modifier.tvFocusGroupOnAppear(asFallback: Boolean = false): Modifier {
    if (LocalTvShell.current == null) return this
    val requester = remember { FocusRequester() }
    val state = remember { GroupFocusState() }
    LaunchedEffect(requester) {
        if (asFallback) {
            repeat(FALLBACK_FRAMES) { withFrameNanos { } }
            if (state.hasFocus) return@LaunchedEffect
        }
        TvPlayerFocus.focus(requester, if (asFallback) "container shown (fallback)" else "container shown")
    }
    return this
        .onFocusChanged { focusState -> state.hasFocus = focusState.hasFocus }
        .focusRequester(requester)
        .focusGroup()
}

private class GroupFocusState {
    var hasFocus = false
}

/** Frames a fallback container waits for its own elements to take the focus. */
private const val FALLBACK_FRAMES = 6
