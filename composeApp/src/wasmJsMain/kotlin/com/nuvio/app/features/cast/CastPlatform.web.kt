package com.nuvio.app.features.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Google Cast is not wired on iOS yet: no cast button or menu entry is shown.
internal actual object CastPlatform {
    actual val state: StateFlow<CastSessionState> =
        MutableStateFlow<CastSessionState>(CastSessionState.Unavailable).asStateFlow()

    actual val devices: StateFlow<List<CastDevice>> =
        MutableStateFlow<List<CastDevice>>(emptyList()).asStateFlow()

    actual fun startDiscovery() = Unit

    actual fun stopDiscovery() = Unit

    actual fun selectDevice(deviceId: String) = Unit

    actual fun disconnect() = Unit

    actual fun canServeLocalMedia(): Boolean = false
}
