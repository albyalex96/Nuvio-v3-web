package com.nuvio.app.features.settings

// Alternate launcher icons are an Android/iOS feature.
internal actual object AppIconPlatform {
    actual val requiresCloseConfirmation: Boolean = false
    actual fun currentIconName(): String? = null
    actual suspend fun activateIcon(name: String?): Boolean = false
}
