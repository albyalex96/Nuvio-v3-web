package com.nuvio.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.logging.AppLogger
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.web.compat.WebCorsProxy
import com.nuvio.app.web.compat.jsUserAgent

private fun jsHideLoadingScreen() {
    js("(function(){ var el = document.getElementById('nuvio-loading'); if (el) el.remove(); })()")
}

private fun jsLaunchLink(): String? =
    js("(function(){ var p = new URLSearchParams(window.location.search).get('open'); return p && (p.indexOf('nuvio://') === 0 || p.indexOf('stremio://') === 0) ? p : null; })()")

private fun jsDebugEnabled(): Boolean =
    js("new URLSearchParams(window.location.search).has('debug')")

/** Web entry point: the whole app renders into a single full page canvas. */
@OptIn(ExperimentalComposeUiApi::class, coil3.annotation.ExperimentalCoilApi::class)
fun main() {
    AppLogger.install(debuggable = jsDebugEnabled())
    val log = Logger.withTag("WebApp")
    log.i { "Starting Nuvio web (${jsUserAgent()}), CORS proxy ${if (WebCorsProxy.isConfigured) "on" else "off"}" }

    jsLaunchLink()?.let { url ->
        log.i { "Opening launch link" }
        handleAppUrl(url)
    }

    ComposeViewport(viewportContainerId = "nuvio-root") {
        // Images use the same Ktor client as the API, so they can fall back to the CORS proxy.
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .components {
                    add(CustomPosterFallbackInterceptor())
                    add(SvgDecoder.Factory())
                    add(
                        KtorNetworkFetcherFactory(
                            httpClient = { createApiHttpClient().config { followRedirects = true } },
                            cacheStrategy = { CacheControlCacheStrategy() },
                        ),
                    )
                }
                .build()
        }
        App()
        jsHideLoadingScreen()
    }
}
