package com.nuvio.app

class DesktopPlatform : Platform {
    override val name: String = "Desktop ${System.getProperty("os.name").orEmpty()}".trim()
}

actual fun getPlatform(): Platform = DesktopPlatform()

internal actual val isDesktop: Boolean = true
internal actual val isIos: Boolean = false
internal actual val isWeb: Boolean = false
internal actual val supportsPosterNavigationMotion: Boolean = false
