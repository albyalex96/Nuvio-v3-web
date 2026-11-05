package com.nuvio.app.core.auth

import com.nuvio.app.web.compat.jsUserAgent

internal actual fun currentDeviceClientMetadata(): DeviceClientMetadata {
    val agent = jsUserAgent()
    val browser = when {
        "Edg/" in agent -> "Edge"
        "Firefox/" in agent -> "Firefox"
        "Chrome/" in agent -> "Chrome"
        "Safari/" in agent -> "Safari"
        else -> "Browser"
    }
    val os = when {
        "Windows" in agent -> "Windows"
        "Android" in agent -> "Android"
        "iPhone" in agent || "iPad" in agent -> "iOS"
        "Mac OS" in agent -> "macOS"
        "Linux" in agent -> "Linux"
        else -> ""
    }
    return DeviceClientMetadata(
        deviceName = "Nuvio Web ($browser)",
        platform = "Web $browser $os".trim(),
    )
}
