package com.nuvio.app

import com.nuvio.app.web.compat.jsUserAgent

class WebPlatform : Platform {
    override val name: String = "Web " + jsUserAgent()
}

actual fun getPlatform(): Platform = WebPlatform()

internal actual val isDesktop: Boolean = false
internal actual val isIos: Boolean = false
internal actual val isWeb: Boolean = true

internal actual val supportsPosterNavigationMotion: Boolean = false
