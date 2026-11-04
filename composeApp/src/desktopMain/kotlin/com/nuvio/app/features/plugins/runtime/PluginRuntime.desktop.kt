package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal val pluginDispatcher: CoroutineDispatcher =
    Executors.newFixedThreadPool(MAX_CONCURRENT_PLUGINS) { runnable ->
        Thread(runnable, "plugin-worker").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()

internal fun QuickJs.configurePluginRuntime() {
    evaluationTimeoutMillis = PLUGIN_TIMEOUT_MS
}
