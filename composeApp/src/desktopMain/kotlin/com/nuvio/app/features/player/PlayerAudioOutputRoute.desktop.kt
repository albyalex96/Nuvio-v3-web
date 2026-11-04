package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

// The OS mixer owns output routing on desktop: a single route keeps one audio delay value.
private val desktopRoute = AudioOutputRoute(key = "desktop_default", label = "Default output")

@Composable
internal actual fun rememberAudioOutputRoute(): AudioOutputRoute? = desktopRoute
