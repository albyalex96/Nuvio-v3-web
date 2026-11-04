package com.nuvio.app.core.ui

import coil3.ImageLoader

/** GIFs and animated WebPs are decoded with Skia ([SkiaGifDecoder]); Coil has no GIF decoder on the JVM. */
internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder =
    components { add(SkiaGifDecoder.Factory()) }

internal actual val platformProvidesImageLoader: Boolean = false
