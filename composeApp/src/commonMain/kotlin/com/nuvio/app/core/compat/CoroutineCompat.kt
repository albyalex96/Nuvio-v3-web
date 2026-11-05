package com.nuvio.app.core.compat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * `kotlinx.coroutines.runBlocking` on Android, desktop and iOS. The web (Wasm) has a single
 * thread and cannot block: there the block runs undispatched and must complete without
 * really suspending (e.g. string resources already loaded), otherwise it throws.
 */
expect fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T

/** `Dispatchers.IO` where it exists; `Dispatchers.Default` on the web. */
expect val Dispatchers.appIo: CoroutineDispatcher
