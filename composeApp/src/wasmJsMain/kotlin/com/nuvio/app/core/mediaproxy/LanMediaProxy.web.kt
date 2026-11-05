package com.nuvio.app.core.mediaproxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// A browser cannot open a LAN server: callers fall back to playing the original link.
actual object LanMediaProxy {
    actual val isSupported: Boolean = false

    actual val status: StateFlow<MediaProxyStatus> = MutableStateFlow(MediaProxyStatus()).asStateFlow()

    actual fun lanAddress(): String? = null

    actual fun detectedLanAddress(): String? = null

    actual fun register(source: MediaProxySource, options: MediaProxyOptions): MediaProxyHandle? = null

    actual fun unregister(handle: MediaProxyHandle) = Unit
}

internal actual object MediaProxySettingsStorage {
    private const val SETTINGS_KEY = "media_proxy_settings"

    actual fun load(): String? = com.nuvio.app.web.compat.NSUserDefaults.standardUserDefaults.stringForKey(SETTINGS_KEY)

    actual fun save(json: String) {
        com.nuvio.app.web.compat.NSUserDefaults.standardUserDefaults.setObject(json, forKey = SETTINGS_KEY)
    }
}
