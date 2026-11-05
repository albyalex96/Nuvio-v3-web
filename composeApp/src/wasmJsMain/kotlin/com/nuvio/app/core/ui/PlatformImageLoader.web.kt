package com.nuvio.app.core.ui

import coil3.ImageLoader

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder = this

/** The web entry point (main.kt) installs its own loader, with the CORS proxy aware HTTP client. */
internal actual val platformProvidesImageLoader: Boolean = true
