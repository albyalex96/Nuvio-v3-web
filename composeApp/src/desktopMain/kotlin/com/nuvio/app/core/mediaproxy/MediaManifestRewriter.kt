package com.nuvio.app.core.mediaproxy

import java.net.URI

private val HLS_URI_ATTRIBUTE = Regex("URI=\"([^\"]*)\"")
private val DASH_BASE_URL = Regex("<BaseURL([^>]*)>([^<]*)</BaseURL>")
private val DASH_URL_ATTRIBUTE = Regex("\\b(media|initialization|sourceURL)=\"(https?://[^\"]*)\"")
private val DASH_MPD_OPEN_TAG = Regex("<MPD\\b[^>]*>")

/**
 * Resolves [reference] against [baseUrl] (RFC 3986). Returns null when either URL cannot be
 * parsed, so the caller keeps the original text.
 */
internal fun resolveUrl(baseUrl: String, reference: String): String? = runCatching {
    URI(baseUrl).resolve(URI(reference.trim())).toString()
}.getOrNull()

private fun String.isHttpUrl(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private val HLS_TS_EXTENSIONS = setOf("ts", "m2ts", "mts")
private val HLS_FMP4_EXTENSIONS = setOf("mp4", "m4s", "m4v", "m4a", "cmfv", "cmfa", "fmp4")
private val HLS_PACKED_AUDIO_EXTENSIONS = setOf("aac", "adts", "ac3", "ec3", "eac3", "mp3")

private fun String.urlExtension(): String =
    substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()

/**
 * Extension a segment of a media playlist really has. Segments disguised as pages or images
 * (".html", ".png") or labelled with the wrong container (".m4s" on MPEG-TS) confuse web players
 * that pick the demuxer from the URL, like the Cast receivers. Without `EXT-X-MAP` a segment is
 * MPEG-TS (or packed audio), with it fMP4. Null when the extension is already right.
 */
internal fun hlsSegmentExtensionHint(segmentUrl: String, hasInitSection: Boolean): String? {
    val extension = segmentUrl.urlExtension()
    return when {
        extension in HLS_PACKED_AUDIO_EXTENSIONS -> null
        hasInitSection -> if (extension in HLS_FMP4_EXTENSIONS) null else "m4s"
        extension in HLS_TS_EXTENSIONS -> null
        else -> "ts"
    }
}

/**
 * Routes every URI of an HLS playlist (variants, segments, keys, init sections, renditions)
 * through [proxify]. Relative URIs are resolved against [baseUrl], the final URL of the
 * playlist after redirects; non-HTTP URIs (data:, skd:) are left untouched.
 *
 * Segments and init sections whose extension does not match their container go through
 * [proxifyWithExtension] with the right one (see [hlsSegmentExtensionHint]).
 */
internal fun rewriteHlsPlaylist(
    text: String,
    baseUrl: String,
    proxify: (String) -> String,
    proxifyWithExtension: ((url: String, extension: String) -> String)? = null,
): String {
    fun route(reference: String, hint: (String) -> String? = { null }): String {
        val absolute = resolveUrl(baseUrl, reference) ?: return reference
        if (!absolute.isHttpUrl()) return reference
        val extension = proxifyWithExtension?.let { hint(absolute) }
        return if (extension != null) proxifyWithExtension(absolute, extension) else proxify(absolute)
    }
    val lines = text.lines()
    val isMediaPlaylist = lines.any { it.trim().startsWith("#EXTINF") }
    val hasInitSection = lines.any { it.trim().startsWith("#EXT-X-MAP") }
    return lines.joinToString("\n") { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> line
            trimmed.startsWith("#EXT-X-MAP") -> HLS_URI_ATTRIBUTE.replace(line) { match ->
                val uri = route(match.groupValues[1]) { url ->
                    if (url.urlExtension() in HLS_FMP4_EXTENSIONS) null else "mp4"
                }
                "URI=\"$uri\""
            }
            trimmed.startsWith("#") -> HLS_URI_ATTRIBUTE.replace(line) { match ->
                "URI=\"${route(match.groupValues[1])}\""
            }
            isMediaPlaylist -> route(trimmed) { url -> hlsSegmentExtensionHint(url, hasInitSection) }
            else -> route(trimmed)
        }
    }
}

/**
 * Routes the absolute URLs of a DASH manifest through [proxify]. Relative URLs keep working
 * because the receiver resolves them against the proxied manifest URL; when the manifest was
 * redirected ([baseUrl] differs from [requestedUrl]) and declares no BaseURL, one pointing to
 * the final location is added so they still resolve to the right server.
 */
internal fun rewriteDashManifest(
    text: String,
    requestedUrl: String,
    baseUrl: String,
    proxify: (String) -> String,
): String {
    var rewritten = DASH_BASE_URL.replace(text) { match ->
        val value = match.groupValues[2].trim()
        if (value.isHttpUrl()) "<BaseURL${match.groupValues[1]}>${proxify(value)}</BaseURL>" else match.value
    }
    rewritten = DASH_URL_ATTRIBUTE.replace(rewritten) { match ->
        "${match.groupValues[1]}=\"${proxify(match.groupValues[2])}\""
    }
    if (baseUrl != requestedUrl && !rewritten.contains("<BaseURL")) {
        val baseDirectory = resolveUrl(baseUrl, ".") ?: return rewritten
        rewritten = DASH_MPD_OPEN_TAG.replaceFirst(rewritten) { match ->
            "${match.value}<BaseURL>${proxify(baseDirectory)}</BaseURL>"
        }
    }
    return rewritten
}

private fun Regex.replaceFirst(input: String, transform: (MatchResult) -> String): String {
    val match = find(input) ?: return input
    return input.replaceRange(match.range, transform(match))
}
