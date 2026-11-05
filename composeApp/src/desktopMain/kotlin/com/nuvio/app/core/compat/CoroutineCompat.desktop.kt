package com.nuvio.app.core.compat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = kotlinx.coroutines.runBlocking(context, block)

actual val Dispatchers.appIo: CoroutineDispatcher
    get() = Dispatchers.IO
