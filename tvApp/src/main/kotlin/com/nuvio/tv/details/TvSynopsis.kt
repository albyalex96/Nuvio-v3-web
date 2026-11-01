package com.nuvio.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.nuvio
import com.nuvio.tv.R
import kotlinx.coroutines.launch

private val log = Logger.withTag("TvSynopsis")

/** NuvioTV `SynopsisDescription`: up to 8 lines; when cut it is focusable and OK opens the whole plot. */
private const val SYNOPSIS_MAX_LINES = 8
private val SynopsisHighlightInset = 12.dp
/** Pixels scrolled by one D-pad press in the full plot overlay (NuvioTV `SynopsisOverlay`). */
private const val SYNOPSIS_SCROLL_STEP = 260

@Composable
internal fun TvSynopsisDescription(
    description: String,
    onShowFullDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isTruncated by rememberSaveable(description) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.nuvio.colors

    Column(
        modifier = modifier.then(
            if (isTruncated) {
                Modifier
                    .offset(x = -SynopsisHighlightInset)
                    .onFocusChanged { isFocused = it.isFocused }
                    .background(
                        color = if (isFocused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onShowFullDescription)
                    .semantics { role = Role.Button }
                    .padding(horizontal = SynopsisHighlightInset, vertical = 8.dp)
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = SYNOPSIS_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow != isTruncated) {
                    isTruncated = result.hasVisualOverflow
                    log.d { "Plot ${if (isTruncated) "cut: read more enabled" else "fits"}" }
                }
            },
        )
        if (isTruncated) {
            Text(
                text = stringResource(R.string.tv_details_synopsis_read_more),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** NuvioTV `SynopsisOverlay`: the whole plot on the full screen, scrolled with the D-pad, closed with back. */
@Composable
internal fun TvSynopsisOverlay(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(description) {
        scrollState.scrollTo(0)
        repeat(10) {
            withFrameNanos { }
            if (runCatching { contentFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not focus the full plot" }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF070707), Color(0xFF101010), Color(0xFF151515))))
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .weight(1f)
                        .drawWithContent {
                            drawContent()
                            val maxScroll = scrollState.maxValue.toFloat()
                            if (maxScroll > 0f) {
                                val viewportHeight = size.height
                                val trackInset = 8.dp.toPx()
                                val trackHeight = (viewportHeight - trackInset * 2f).coerceAtLeast(0f)
                                val contentHeight = viewportHeight + maxScroll
                                val thumbHeight = (trackHeight * viewportHeight / contentHeight)
                                    .coerceAtLeast(36.dp.toPx())
                                    .coerceAtMost(trackHeight)
                                val thumbTop = trackInset + (trackHeight - thumbHeight) * (scrollState.value / maxScroll)
                                val x = size.width - 6.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.07f),
                                    start = Offset(x, trackInset),
                                    end = Offset(x, viewportHeight - trackInset),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.30f),
                                    start = Offset(x, thumbTop),
                                    end = Offset(x, thumbTop + thumbHeight),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                event.key == Key.DirectionDown && scrollState.value < scrollState.maxValue -> {
                                    scope.launch { scrollState.animateScrollTo(scrollState.value + SYNOPSIS_SCROLL_STEP) }
                                    true
                                }
                                event.key == Key.DirectionUp && scrollState.value > 0 -> {
                                    scope.launch { scrollState.animateScrollTo(scrollState.value - SYNOPSIS_SCROLL_STEP) }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusRequester(contentFocusRequester)
                        .focusable()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxWidth().padding(end = 24.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.tv_details_synopsis_dismiss_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}
