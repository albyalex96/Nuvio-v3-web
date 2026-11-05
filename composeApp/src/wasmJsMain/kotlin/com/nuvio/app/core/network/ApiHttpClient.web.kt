package com.nuvio.app.core.network

import com.nuvio.app.web.compat.WebCorsProxy
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout

internal actual fun createApiHttpClient(): HttpClient = HttpClient(Js) {
    // The browser always follows redirects (fetch API); the flag is kept for parity.
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}.also(WebCorsProxy::install)

internal actual fun createSupabaseHttpEngine(): HttpClientEngine? = null
