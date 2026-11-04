package com.nuvio.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

internal expect val isIos: Boolean

/** Compose Desktop build (Windows). */
internal expect val isDesktop: Boolean

internal expect val supportsPosterNavigationMotion: Boolean
