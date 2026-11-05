package com.nuvio.app.web.compat

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun jsConfigValue(name: String): String? =
    js("(function(){ var c = window.NUVIO_CONFIG || {}; var v = c[name]; return (typeof v === 'string' && v.length > 0) ? v : null; })()")

/**
 * Routes browser requests through the Cloudflare CORS proxy (`cloudflare-cors-proxy.js`).
 *
 * The proxy address comes from `window.NUVIO_CONFIG` in `index.html`
 * (`corsProxy`, optional `corsProxyToken`). Each host is first tried directly; when the
 * browser blocks it (CORS or mixed content), the request is retried through the proxy
 * and the host is remembered for the rest of the session.
 */
internal object WebCorsProxy {
    private val log = Logger.withTag("WebCorsProxy")

    private val proxyUrl: String? by lazy { jsConfigValue("corsProxy")?.trimEnd('/') }
    private val proxyToken: String? by lazy { jsConfigValue("corsProxyToken") }

    /** Hosts known to send CORS headers: never proxied. */
    private val directHosts = setOf(
        "api.themoviedb.org",
        "image.tmdb.org",
        "api.trakt.tv",
        "api.simkl.com",
        "graphql.anilist.co",
    )

    /** Headers a browser refuses to set: forwarded to the proxy as x-proxy-<name>. */
    private val forbiddenHeaders = setOf("referer", "user-agent", "origin", "cookie", "host")

    private val proxiedHosts = mutableSetOf<String>()

    val isConfigured: Boolean get() = proxyUrl != null

    fun install(client: HttpClient) {
        val proxy = proxyUrl
        if (proxy == null) {
            log.w { "No CORS proxy configured (window.NUVIO_CONFIG.corsProxy): requests go direct" }
            return
        }
        client.plugin(HttpSend).intercept { request ->
            val url = request.url.build()
            if (!shouldConsider(url, proxy)) return@intercept execute(request)
            val host = url.host
            if (host !in proxiedHosts) {
                val direct = runCatching { execute(request) }
                direct.getOrNull()?.let { return@intercept it }
                val error = direct.exceptionOrNull()
                log.i { "Direct request to $host failed (${error?.message}); using the CORS proxy" }
                proxiedHosts += host
            }
            rewriteToProxy(request, url, proxy)
            execute(request)
        }
        log.i { "CORS proxy enabled: $proxy" }
    }

    /**
     * Proxy URL for a media URL used outside Ktor (video element, hls.js). [headers] are
     * sent by the proxy to the origin, since a <video> element cannot set request headers.
     */
    fun proxiedUrl(url: String, headers: Map<String, String> = emptyMap()): String {
        val proxy = proxyUrl ?: return url
        if (url.startsWith(proxy) || !(url.startsWith("http://") || url.startsWith("https://"))) return url
        return buildProxyUrl(proxy, url, headers)
    }

    private fun shouldConsider(url: Url, proxy: String): Boolean {
        val scheme = url.protocol.name
        if (scheme != "http" && scheme != "https") return false
        if (url.toString().startsWith(proxy)) return false
        val host = url.host
        if (host in directHosts) return false
        if (host.endsWith(".supabase.co") || host.endsWith(".supabase.in")) return false
        if (host == "localhost" || host == "127.0.0.1") return false
        return true
    }

    private fun rewriteToProxy(request: HttpRequestBuilder, original: Url, proxy: String) {
        val forwarded = request.headers.entries()
            .filter { (name, _) -> name.lowercase() in forbiddenHeaders }
            .map { (name, values) -> name to values }
        forwarded.forEach { (name, values) ->
            request.headers.remove(name)
            values.forEach { request.headers.append("x-proxy-${name.lowercase()}", it) }
        }
        request.url.takeFrom(URLBuilder(buildProxyUrl(proxy, original.toString())))
    }

    private fun buildProxyUrl(proxy: String, target: String, headers: Map<String, String> = emptyMap()): String {
        val builder = URLBuilder("$proxy/")
        builder.parameters.append("url", target)
        proxyToken?.let { builder.parameters.append("token", it) }
        if (headers.isNotEmpty()) {
            builder.parameters.append("headers", JsonObject(headers.mapValues { JsonPrimitive(it.value) }).toString())
        }
        return builder.buildString()
    }
}
