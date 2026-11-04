package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** How animated GIF / WebP posters and episode thumbnails play (desktop; phones and TVs always animate). */
enum class AnimatedArtworkMode {
    Always,
    OnHover,
    Off,
}

/** Artwork image honouring [mode] for animated GIF / WebP. */
@Composable
internal expect fun AnimatedArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    mode: AnimatedArtworkMode,
)
