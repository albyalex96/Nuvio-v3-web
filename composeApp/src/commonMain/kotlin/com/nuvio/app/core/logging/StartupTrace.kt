package com.nuvio.app.core.logging

import co.touchlab.kermit.Logger
import kotlin.time.TimeSource

/**
 * Startup timing: measures the blocks run while the app opens, so slow steps show up in the
 * in-app logger under the "Startup" category. Times are relative to the first call of [mark].
 */
object StartupTrace {
    private val log = Logger.withTag("Startup")
    private val origin by lazy { TimeSource.Monotonic.markNow() }

    /** Blocks slower than this are logged as warnings. */
    private const val SLOW_BLOCK_MS = 50L

    fun mark(label: String) {
        log.i { "$label at +${origin.elapsedNow().inWholeMilliseconds} ms" }
    }

    inline fun <T> measure(label: String, block: () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        val result = block()
        report(label, start.elapsedNow().inWholeMilliseconds)
        return result
    }

    @PublishedApi
    internal fun report(label: String, elapsedMs: Long) {
        origin // starts the clock if needed
        if (elapsedMs >= SLOW_BLOCK_MS) {
            log.w { "$label took $elapsedMs ms (slow)" }
        } else {
            log.d { "$label took $elapsedMs ms" }
        }
    }
}
