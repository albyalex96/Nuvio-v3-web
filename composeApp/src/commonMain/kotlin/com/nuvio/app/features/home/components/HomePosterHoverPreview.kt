package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.PendingTrackingMembershipRemoval
import com.nuvio.app.features.library.TrackingMembershipRemovalConfirmationHost
import com.nuvio.app.features.library.executeTrackingMembershipOperation
import com.nuvio.app.features.library.showTrackingMembershipRewriteFeedback
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.tracking.TrackingMembershipApplyResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import com.nuvio.app.features.watching.application.WatchingActions
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_mark_unwatched
import nuvio.composeapp.generated.resources.hero_mark_watched
import nuvio.composeapp.generated.resources.hero_remove_from_library
import nuvio.composeapp.generated.resources.home_view_details
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("PosterHoverPreview")

private const val HoverPreviewCloseDelayMillis = 120L
private const val HoverPreviewEnterDurationMillis = 260
private const val HoverPreviewExitDurationMillis = 170
private const val HoverTrailerExtractionDebounceMillis = 1_000L
private const val HoverTrailerFadeDurationMillis = 480
private val HoverPreviewWidth = 420.dp

/**
 * Desktop: after the mouse rests on a poster for the chosen delay, a card with artwork, logo,
 * details and quick actions opens over it (optionally playing the trailer). Elsewhere, or with
 * the option off, only [content] is drawn.
 */
@Composable
internal fun HomePosterHoverPreview(
    item: MetaPreview,
    isWatched: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val style = rememberPosterCardStyleUiState()
    if (!isDesktop || !style.hoverPreviewEnabled) {
        content(modifier)
        return
    }
    val trailerEnabled = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        style.hoverPreviewTrailerEnabled

    val anchorInteraction = remember { MutableInteractionSource() }
    val previewInteraction = remember { MutableInteractionSource() }
    val anchorHovered by anchorInteraction.collectIsHoveredAsState()
    val previewHovered by previewInteraction.collectIsHoveredAsState()
    var previewVisible by remember(item.type, item.id) { mutableStateOf(false) }
    var popupMounted by remember(item.type, item.id) { mutableStateOf(false) }
    var trailerSource by remember(item.type, item.id) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var isSaved by remember(item.type, item.id) { mutableStateOf(false) }
    var pendingRemoval by remember(item.type, item.id) { mutableStateOf<PendingTrackingMembershipRemoval?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        PosterPreviewPositionProvider(
            edgeMarginPx = with(density) { 16.dp.roundToPx() },
            topOverlapPx = with(density) { 24.dp.roundToPx() },
        )
    }

    LaunchedEffect(anchorHovered, previewHovered, style.hoverPreviewOpenDelayMillis) {
        if (anchorHovered || previewHovered) {
            if (!popupMounted) {
                delay(style.hoverPreviewOpenDelayMillis.toLong())
                popupMounted = true
                log.d { "Preview opened for ${item.type}/${item.id}" }
            }
            previewVisible = true
        } else if (popupMounted) {
            delay(HoverPreviewCloseDelayMillis)
            previewVisible = false
            delay(HoverPreviewExitDurationMillis.toLong())
            popupMounted = false
        }
    }

    // The trailer is looked up only after the pointer stays a moment, never while just passing by.
    LaunchedEffect(anchorHovered, trailerEnabled, item.type, item.id) {
        if (!anchorHovered || !trailerEnabled || trailerSource != null) return@LaunchedEffect
        delay(HoverTrailerExtractionDebounceMillis)
        trailerSource = resolveHoverTrailer(item)
    }

    LaunchedEffect(popupMounted, item.type, item.id) {
        if (popupMounted) isSaved = LibraryRepository.isSaved(item.id, item.type)
    }

    val failedMessage = "Library update failed"
    val onSaveClick: () -> Unit = {
        scope.launch {
            val libraryItem = item.toLibraryItem(savedAtEpochMs = 0L)
            val toggle: suspend (Set<TrackingProviderId>) -> TrackingMembershipApplyResult = { confirmed ->
                LibraryRepository.toggleSaved(item = libraryItem, confirmedRemovalProviders = confirmed)
            }
            val done: suspend (TrackingMembershipApplyResult) -> Unit = { result ->
                showTrackingMembershipRewriteFeedback(result)
                isSaved = LibraryRepository.isSaved(item.id, item.type)
            }
            val failure: suspend (Throwable) -> Unit = { error ->
                log.e(error) { "Library toggle failed" }
                com.nuvio.app.core.ui.NuvioToastController.show(error.message ?: failedMessage)
            }
            executeTrackingMembershipOperation(
                operation = { toggle(emptySet()) },
                onSuccess = { result ->
                    if (result.requiresRemovalConfirmation) {
                        pendingRemoval = PendingTrackingMembershipRemoval(
                            itemTitle = libraryItem.name,
                            confirmations = result.requiredRemovalConfirmations,
                            retry = toggle,
                            onApplied = done,
                            onFailure = failure,
                        )
                    } else {
                        done(result)
                    }
                },
                onFailure = failure,
            )
        }
    }
    val onWatchedClick: () -> Unit = { scope.launch { WatchingActions.togglePosterWatched(item) } }

    Box(modifier = modifier) {
        content(Modifier.hoverable(anchorInteraction))
        if (popupMounted) {
            Popup(popupPositionProvider = positionProvider, properties = PopupProperties(focusable = false)) {
                AnimatedVisibility(
                    visible = previewVisible,
                    enter = fadeIn(tween(HoverPreviewEnterDurationMillis)) +
                        scaleIn(tween(HoverPreviewEnterDurationMillis), initialScale = 0.9f),
                    exit = fadeOut(tween(HoverPreviewExitDurationMillis)) +
                        scaleOut(tween(HoverPreviewExitDurationMillis), targetScale = 0.97f),
                ) {
                    PosterPreviewCard(
                        item = item,
                        isWatched = isWatched,
                        isSaved = isSaved,
                        trailerSource = trailerSource.takeIf { trailerEnabled },
                        trailerSound = style.hoverPreviewTrailerSoundEnabled,
                        modifier = Modifier.hoverable(previewInteraction),
                        onClick = onClick,
                        onWatchedClick = onWatchedClick,
                        onSaveClick = onSaveClick,
                    )
                }
            }
        }
    }

    pendingRemoval?.let { pending ->
        TrackingMembershipRemovalConfirmationHost(pending = pending, onPendingChange = { pendingRemoval = it })
    }
}

@Composable
private fun PosterPreviewCard(
    item: MetaPreview,
    isWatched: Boolean,
    isSaved: Boolean,
    trailerSource: TrailerPlaybackSource?,
    trailerSound: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onWatchedClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(16.dp)
    var logoFailed by remember(item.logo) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(HoverPreviewWidth)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.borderStrong, shape)
            .clickable(enabled = onClick != null, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClick?.invoke()
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(colors.surface),
        ) {
            val artwork = item.banner ?: item.poster
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            trailerSource?.let { HoverTrailer(it, trailerSound, Modifier.fillMaxSize()) }
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.58f to colors.background.copy(alpha = 0.08f),
                        1f to colors.background.copy(alpha = 0.94f),
                    ),
                ),
            )
            val logo = item.logo?.takeIf { it.isNotBlank() && !logoFailed }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, bottom = 14.dp)
                        .width(180.dp)
                        .height(52.dp),
                    alignment = Alignment.CenterStart,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                )
            } else {
                Text(
                    text = item.name,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item.previewMetadataLine(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.genres.take(3).joinToString(" • ").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(MaterialTheme.nuvio.shapes.button)
                            .background(colors.accent)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.home_view_details), style = MaterialTheme.typography.labelLarge, color = colors.onAccent, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                    }
                    PreviewIconAction(
                        icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                        label = stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
                        active = isWatched,
                        onClick = onWatchedClick,
                    )
                    PreviewIconAction(
                        icon = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        label = stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
                        active = isSaved,
                        onClick = onSaveClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewIconAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) colors.accent else colors.surfaceElevated)
            .border(1.dp, colors.borderDefault, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) colors.onAccent else colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HoverTrailer(source: TrailerPlaybackSource, sound: Boolean, modifier: Modifier) {
    var ready by remember(source) { mutableStateOf(false) }
    var finished by remember(source) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready && !finished) 1f else 0f, tween(HoverTrailerFadeDurationMillis))
    if (finished && alpha == 0f) return
    HeroTrailerPlayerSurface(
        sourceUrl = source.videoUrl,
        sourceAudioUrl = source.audioUrl,
        playWhenReady = !finished,
        muted = !sound,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        onReady = { if (!finished) ready = true },
        onEnded = { ready = false; finished = true },
        onError = { ready = false; finished = true },
    )
}

private suspend fun resolveHoverTrailer(item: MetaPreview): TrailerPlaybackSource? {
    val meta = MetaDetailsRepository.peek(type = item.type, id = item.id)
        ?: MetaDetailsRepository.fetch(type = item.type, id = item.id)
    val trailer = selectHeroTrailer(meta?.trailers.orEmpty()) ?: return null
    return try {
        TrailerPlaybackResolver.resolveFromYouTubeUrl(trailer.youtubePlaybackUrl())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        log.w(error) { "Trailer for ${item.type}/${item.id} not resolved" }
        null
    }
}

private fun MetaPreview.previewMetadataLine(): String =
    buildList {
        add(type.replaceFirstChar(Char::uppercase))
        releaseInfo?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)?.takeIf { it.isNotBlank() }?.let(::add)
        imdbRating?.trim()?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
    }.joinToString(" • ")

/** Centred on the poster, overlapping its top a little, kept inside the window. */
private class PosterPreviewPositionProvider(
    private val edgeMarginPx: Int,
    private val topOverlapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val maxY = (windowSize.height - popupContentSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.top - topOverlapPx
        return IntOffset(x.coerceIn(edgeMarginPx, maxX), y.coerceIn(edgeMarginPx, maxY))
    }
}
