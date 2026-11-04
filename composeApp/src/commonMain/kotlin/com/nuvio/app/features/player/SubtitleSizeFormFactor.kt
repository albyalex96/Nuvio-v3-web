package com.nuvio.app.features.player

import com.nuvio.app.tv.TvAppRuntime

/**
 * Device class with its own subtitle size: phones, tablets and TVs read subtitles from very
 * different distances, so each class keeps (and syncs with the profile) a separate size.
 */
enum class SubtitleSizeFormFactor(internal val storageSuffix: String) {
    Mobile("mobile"),
    Tablet("tablet"),
    Tv("tv"),
    Desktop("desktop"),
}

/** True on tablets (Android smallest width >= 600 dp, iPad). */
internal expect fun isTabletSubtitleFormFactor(): Boolean

/** Class of this device; the TV app always counts as TV. */
internal fun currentSubtitleSizeFormFactor(): SubtitleSizeFormFactor = when {
    TvAppRuntime.isTvApp -> SubtitleSizeFormFactor.Tv
    com.nuvio.app.isDesktop -> SubtitleSizeFormFactor.Desktop
    isTabletSubtitleFormFactor() -> SubtitleSizeFormFactor.Tablet
    else -> SubtitleSizeFormFactor.Mobile
}
