package com.nuvio.app.core.format

import com.nuvio.app.web.compat.webFormatDate
import com.nuvio.app.web.compat.webShortDate

internal actual fun formatLocalDateTimeWithPatterns(
    epochMs: Long,
    localeTag: String,
    datePattern: String?,
    timePattern: String,
): String {
    val dateText = if (datePattern != null) webFormatDate(epochMs, datePattern, localeTag) else webShortDate(epochMs, localeTag)
    return "$dateText ${webFormatDate(epochMs, timePattern, localeTag)}"
}

internal actual fun formatLocalTimeWithPattern(
    epochMs: Long,
    localeTag: String,
    timePattern: String,
): String = webFormatDate(epochMs, timePattern, localeTag)
