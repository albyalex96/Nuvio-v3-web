package com.nuvio.app.core.format

import com.nuvio.app.core.time.parseEpisodeReleaseEpochMs
import com.nuvio.app.web.compat.webFormatDate
import com.nuvio.app.web.compat.webLongCalendarDate

internal actual fun formatCalendarDate(isoDate: String, localeTag: String, includeYear: Boolean): String {
    val epochMs = parseEpisodeReleaseEpochMs(isoDate) ?: return isoDate
    return webLongCalendarDate(epochMs, localeTag, includeYear).ifBlank { isoDate }
}

internal actual fun formatCalendarDateWithPattern(isoDate: String, localeTag: String, pattern: String): String {
    val epochMs = parseEpisodeReleaseEpochMs(isoDate) ?: return isoDate
    return webFormatDate(epochMs, pattern, localeTag, utc = true)
}
