package com.nuvio.app.core.compat

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlin.coroutines.CoroutineContext

/**
 * The browser has a single thread: blocking would freeze the page. The block runs
 * undispatched and its result is returned only if it completed without suspending.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T {
    val deferred = GlobalScope.async(context + Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED, block = block)
    if (deferred.isCompleted) return deferred.getCompleted()
    deferred.cancel()
    Logger.withTag("WebRunBlocking").w { "runBlocking block suspended: not supported on the web" }
    throw IllegalStateException("Blocking call not supported on the web")
}

actual val Dispatchers.appIo: CoroutineDispatcher
    get() = Dispatchers.Default
