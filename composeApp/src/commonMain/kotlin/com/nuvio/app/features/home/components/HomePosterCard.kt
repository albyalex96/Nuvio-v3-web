package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
    /** Ignores the "landscape posters" setting (Top 10 rows keep the portrait posters next to the rank). */
    forcePortrait: Boolean = false,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = !forcePortrait && (useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled)
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else item.landscapePoster
    val landscapeArtwork = item.landscapeArtworkUrl(posterCardStyle.alwaysShowLandscapeClearlogo)
    // Without 16:9 artwork the portrait/square poster is letterboxed in the frame instead of cropped.
    val fitPosterInLandscape = isLandscapeMode && landscapeArtwork == null
    val imageUrl = if (isLandscapeMode) {
        landscapeArtwork ?: item.poster
    } else item.poster
    val fallbackImageUrl = if (isLandscapeMode && !effectiveLandscapePoster.isNullOrBlank()) {
        // Landscape custom poster -> fall back to original backdrop, then portrait
        item.banner ?: item.rawPosterUrl
    } else {
        item.rawPosterUrl
    }
    // A custom landscape poster already carries the title, a letterboxed poster too.
    val showOverlay = isLandscapeMode && showLandscapeOverlay && !fitPosterInLandscape && effectiveLandscapePoster.isNullOrBlank()

    HomePosterHoverPreview(item = item, isWatched = isWatched, onClick = onClick, modifier = modifier) { hoverModifier ->
    NuvioPosterCard(
        title = item.name,
        imageUrl = imageUrl,
        modifier = hoverModifier,
        fallbackImageUrl = fallbackImageUrl,
        shape = when {
            isLandscapeMode -> NuvioPosterShape.Landscape
            forcePortrait && item.posterShape == PosterShape.Landscape -> NuvioPosterShape.Poster
            else -> item.posterShape.toNuvioPosterShape()
        },
        fitImage = fitPosterInLandscape,
        detailLine = if (isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
        showTitleBelow = !posterCardStyle.hideLabelsEnabled,
        bottomLeftLogoUrl = if (showOverlay) item.logo else null,
        bottomLeftText = if (showOverlay && item.logo.isNullOrBlank() && !posterCardStyle.hideLabelsEnabled) item.name else null,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
    )
    }
}

/**
 * Artwork made for a 16:9 frame (custom landscape poster, backdrop, or the poster of a landscape
 * catalog), or null when the item only has portrait/square art, which must not be cropped to 16:9.
 */
fun MetaPreview.landscapeArtworkUrl(alwaysShowLandscapeClearlogo: Boolean): String? {
    val customLandscape = if (alwaysShowLandscapeClearlogo) null else landscapePoster
    return customLandscape?.takeIf { it.isNotBlank() }
        ?: banner?.takeIf { it.isNotBlank() }
        ?: poster?.takeIf { it.isNotBlank() && posterShape == PosterShape.Landscape }
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
