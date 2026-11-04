package com.nuvio.app.core.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

private val NeverHovered: State<Boolean> = mutableStateOf(false)

/**
 * Animated GIF / WebP artwork plays always, only while the mouse is over it, or never (first
 * frame). Animated requests get their own memory-cache key so a still and an animated copy of
 * one URL never mix.
 */
@Composable
internal actual fun AnimatedArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    mode: AnimatedArtworkMode,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered = if (mode == AnimatedArtworkMode.OnHover) hoverSource.collectIsHoveredAsState() else NeverHovered
    val playing: () -> Boolean = remember(mode, hovered) {
        when (mode) {
            AnimatedArtworkMode.Always -> { { true } }
            AnimatedArtworkMode.OnHover -> { { hovered.value } }
            AnimatedArtworkMode.Off -> { { false } }
        }
    }
    val context = LocalPlatformContext.current
    val requestModel = remember(context, model, mode) {
        if (model == null || mode == AnimatedArtworkMode.Off) {
            model
        } else {
            val builder = if (model is ImageRequest) model.newBuilder() else ImageRequest.Builder(context).data(model)
            builder.apply {
                extras[NuvioAnimatedImageKey] = true
                memoryCacheKeyExtra("nuvio.animated", "true")
            }.build()
        }
    }
    val transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = remember(playing) {
        { state ->
            val image = (state as? AsyncImagePainter.State.Success)?.result?.image
            if (state is AsyncImagePainter.State.Success && image is AnimatedSkiaImage) {
                state.copy(painter = image.painter(playing))
            } else {
                state
            }
        }
    }
    AsyncImage(
        model = requestModel,
        contentDescription = contentDescription,
        modifier = if (mode == AnimatedArtworkMode.OnHover) modifier.hoverable(hoverSource) else modifier,
        transform = transform,
        contentScale = contentScale,
    )
}
