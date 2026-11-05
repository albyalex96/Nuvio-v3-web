package com.nuvio.app.features.player.seekthumb

internal actual val seekThumbnailsSupported: Boolean = false

internal actual fun createSeekThumbnailProvider(
    request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
): SeekThumbnailProvider? = null
