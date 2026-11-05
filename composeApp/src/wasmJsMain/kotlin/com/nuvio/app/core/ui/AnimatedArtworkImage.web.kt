package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
internal actual fun AnimatedArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    mode: AnimatedArtworkMode,
) {
    AsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}
