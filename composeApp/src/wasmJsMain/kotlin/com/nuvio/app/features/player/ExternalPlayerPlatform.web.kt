package com.nuvio.app.features.player

import com.nuvio.app.web.compat.openInNewTab

private data class WebExternalPlayerIntent(
    val request: ExternalPlayerPlaybackRequest,
    val playerId: String?,
)

/** Web: the only "external player" is a new browser tab with the stream URL. */
internal actual object ExternalPlayerPlatform {
    private const val browserTabId = "browser"

    actual fun defaultPlayerId(): String? = browserTabId

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        listOf(ExternalPlayerApp(browserTabId, "Browser tab"))

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult =
        if (openInNewTab(request.sourceUrl)) ExternalPlayerOpenResult.Opened else ExternalPlayerOpenResult.Failed

    actual fun buildIntent(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerIntentResult =
        ExternalPlayerIntentResult.Success(WebExternalPlayerIntent(request, playerId))

    internal fun launch(intent: Any): Boolean {
        val webIntent = intent as? WebExternalPlayerIntent ?: return false
        return open(webIntent.request, webIntent.playerId) == ExternalPlayerOpenResult.Opened
    }
}
