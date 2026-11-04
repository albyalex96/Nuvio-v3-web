package com.nuvio.app.features.player

/**
 * Track id for an addon subtitle attached as a sideloaded track. libass (AssHandler) pairs the
 * selected track (`"1:" + id`) with the track it renders (`id`), so a sideloaded track without an
 * id is never rendered. Same scheme as NuvioTV's `buildAddonSubtitleTrackId`.
 */
internal fun addonSubtitleTrackId(url: String): String =
    "nuvio_addon_subtitle:${url.hashCode().toUInt().toString(16)}"
