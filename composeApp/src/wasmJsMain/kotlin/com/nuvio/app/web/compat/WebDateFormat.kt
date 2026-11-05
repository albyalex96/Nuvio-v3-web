package com.nuvio.app.web.compat

private fun jsDateParts(epochMs: Double, utc: Boolean): String =
    js("(function(){ var d = new Date(epochMs); if (utc) { return [d.getUTCFullYear(), d.getUTCMonth()+1, d.getUTCDate(), d.getUTCHours(), d.getUTCMinutes(), d.getUTCSeconds(), d.getUTCMilliseconds(), d.getUTCDay()].join('|'); } return [d.getFullYear(), d.getMonth()+1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds(), d.getDay()].join('|'); })()")

private fun jsIntlPart(epochMs: Double, locale: String, field: String, style: String, utc: Boolean): String =
    js("(function(){ try { var o = {}; o[field] = style; if (utc) o.timeZone = 'UTC'; return new Intl.DateTimeFormat(locale, o).format(new Date(epochMs)); } catch (e) { return ''; } })()")

private fun jsShortDate(epochMs: Double, locale: String): String =
    js("(function(){ try { return new Intl.DateTimeFormat(locale, { dateStyle: 'short' }).format(new Date(epochMs)); } catch (e) { return new Date(epochMs).toLocaleDateString(); } })()")

private fun jsSkeletonDate(epochMs: Double, locale: String, includeYear: Boolean): String =
    js("(function(){ try { var o = { day: 'numeric', month: 'long', timeZone: 'UTC' }; if (includeYear) o.year = 'numeric'; return new Intl.DateTimeFormat(locale, o).format(new Date(epochMs)); } catch (e) { return ''; } })()")

private fun jsParseLocalDateTime(value: String): Double =
    js("(function(){ var p = /^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?$/.exec(value); if (!p) return -1; return new Date(+p[1], +p[2]-1, +p[3], +p[4], +p[5], +p[6], +(p[7]||0)).getTime(); })()")

internal fun webShortDate(epochMs: Long, localeTag: String): String = jsShortDate(epochMs.toDouble(), localeTag)

/** "d MMMM [y]" in UTC, localized by the browser. */
internal fun webLongCalendarDate(epochMs: Long, localeTag: String, includeYear: Boolean): String =
    jsSkeletonDate(epochMs.toDouble(), localeTag, includeYear)

/** Local "yyyy-MM-dd'T'HH:mm:ss.SSS" to epoch ms, or null. */
internal fun webParseLocalDateTime(value: String): Long? =
    jsParseLocalDateTime(value).takeIf { it >= 0 }?.toLong()

/**
 * Formats epoch milliseconds with a java.text / NSDateFormatter style pattern
 * (y, M, d, E, H, h, m, s, S, a and quoted literals).
 */
internal fun webFormatDate(epochMs: Long, pattern: String, localeTag: String = "en", utc: Boolean = false): String {
    val ms = epochMs.toDouble()
    val parts = jsDateParts(ms, utc).split('|').map { it.toInt() }
    val year = parts[0]
    val month = parts[1]
    val day = parts[2]
    val hour = parts[3]
    val minute = parts[4]
    val second = parts[5]
    val millis = parts[6]
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            val end = pattern.indexOf('\'', i + 1)
            if (end == i + 1) {
                out.append('\'')
                i += 2
                continue
            }
            val stop = if (end < 0) pattern.length else end
            out.append(pattern, i + 1, stop)
            i = stop + 1
            continue
        }
        if (!c.isLetter()) {
            out.append(c)
            i++
            continue
        }
        var n = 1
        while (i + n < pattern.length && pattern[i + n] == c) n++
        fun pad(v: Int) = v.toString().padStart(n, '0')
        out.append(
            when (c) {
                'y', 'Y', 'u' -> if (n == 2) (year % 100).toString().padStart(2, '0') else pad(year)
                'M', 'L' -> when {
                    n >= 4 -> jsIntlPart(ms, localeTag, "month", "long", utc)
                    n == 3 -> jsIntlPart(ms, localeTag, "month", "short", utc)
                    else -> pad(month)
                }
                'd' -> pad(day)
                'E', 'e', 'c' -> jsIntlPart(ms, localeTag, "weekday", if (n >= 4) "long" else "short", utc)
                'H', 'k' -> pad(hour)
                'h', 'K' -> pad(if (hour % 12 == 0) 12 else hour % 12)
                'm' -> pad(minute)
                's' -> pad(second)
                'S' -> millis.toString().padStart(3, '0').take(n).padEnd(n, '0')
                'a' -> if (hour < 12) "AM" else "PM"
                else -> ""
            },
        )
        i += n
    }
    return out.toString()
}
