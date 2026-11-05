package com.nuvio.app.web.compat

/** Small JavaScript bridges shared by the web actuals. */
internal fun jsNowMs(): Double = js("Date.now()")

internal fun jsUserAgent(): String = js("navigator.userAgent || ''")

internal fun jsBrowserLanguage(): String = js("navigator.language || 'en'")

internal fun jsHardwareConcurrency(): Int = js("navigator.hardwareConcurrency || 4")

internal fun jsIsTouchTablet(): Boolean =
    js("(navigator.maxTouchPoints || 0) > 0 && Math.min(screen.width, screen.height) >= 600")

/** Local calendar date (yyyy-MM-dd) of the given epoch milliseconds. */
internal fun jsLocalIsoDate(epochMs: Double): String =
    js("(function(d){ var m=d.getMonth()+1, day=d.getDate(); return d.getFullYear()+'-'+(m<10?'0':'')+m+'-'+(day<10?'0':'')+day; })(new Date(epochMs))")

/** Epoch milliseconds of local midnight of an ISO date (yyyy-MM-dd), or -1 if invalid. */
internal fun jsLocalStartOfDayMs(isoDate: String): Double =
    js("(function(s){ var p=/^(\\d{4})-(\\d{2})-(\\d{2})$/.exec(s); if(!p) return -1; return new Date(+p[1], +p[2]-1, +p[3]).getTime(); })(isoDate)")

internal fun jsLocalYear(): Int = js("new Date().getFullYear()")
internal fun jsLocalMonth(): Int = js("new Date().getMonth() + 1")
internal fun jsLocalDay(): Int = js("new Date().getDate()")

/** Intl.DisplayNames lookup; type is "region" or "language". */
internal fun jsDisplayName(type: String, code: String, locale: String): String? =
    js("(function(){ try { return new Intl.DisplayNames([locale], { type: type }).of(code) || null; } catch (e) { return null; } })()")

internal fun jsOpenUrl(url: String) { js("window.open(url, '_blank', 'noopener')") }

internal fun jsHistoryBack() { js("window.history.back()") }

private fun jsRandomHex(size: Int): String =
    js("(function(){ var a = new Uint8Array(size); crypto.getRandomValues(a); var s=''; for (var i=0;i<a.length;i++){ s += (a[i] < 16 ? '0' : '') + a[i].toString(16); } return s; })()")

/** Cryptographically secure random bytes from `crypto.getRandomValues`. */
internal fun secureRandomBytes(size: Int): ByteArray =
    jsRandomHex(size).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
