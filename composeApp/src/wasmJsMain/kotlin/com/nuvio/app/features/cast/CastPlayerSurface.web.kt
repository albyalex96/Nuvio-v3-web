package com.nuvio.app.features.cast

import androidx.compose.runtime.Composable
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot

// Never composed: CastPlatform reports Unavailable on iOS.
@Composable
internal actual fun CastPlayerSurface(
    request: CastMediaRequest,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) = Unit
