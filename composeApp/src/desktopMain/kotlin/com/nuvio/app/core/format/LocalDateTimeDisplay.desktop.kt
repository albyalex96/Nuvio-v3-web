package com.nuvio.app.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal actual fun formatLocalDateTimeWithPatterns(
    epochMs: Long,
    localeTag: String,
    datePattern: String?,
    timePattern: String,
): String {
    val locale = Locale.forLanguageTag(localeTag)
    val dateTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val dateFormatter = if (datePattern != null) {
        DateTimeFormatter.ofPattern(datePattern, locale)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    return "${dateFormatter.format(dateTime)} ${DateTimeFormatter.ofPattern(timePattern, locale).format(dateTime)}"
}

internal actual fun formatLocalTimeWithPattern(
    epochMs: Long,
    localeTag: String,
    timePattern: String,
): String = DateTimeFormatter.ofPattern(timePattern, Locale.forLanguageTag(localeTag))
    .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
