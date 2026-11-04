package com.nuvio.app.features.player

/**
 * Desktop: external players (mpv, VLC) read remote subtitle URLs directly, so no local copy is needed.
 */
actual object SubtitleCacheProvider {
    actual suspend fun cacheForExternalPlayer(subtitles: List<SubtitleInput>): List<SubtitleInput>? = subtitles
}
