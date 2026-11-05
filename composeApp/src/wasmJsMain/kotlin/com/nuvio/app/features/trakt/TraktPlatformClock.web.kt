package com.nuvio.app.features.trakt

import com.nuvio.app.web.compat.jsHardwareConcurrency
import com.nuvio.app.web.compat.jsNowMs

internal actual object TraktPlatformClock {
    actual fun nowEpochMs(): Long = jsNowMs().toLong()

    actual fun parseIsoDateTimeToEpochMs(value: String): Long? =
        parseTraktIsoDateTimeToEpochMs(value)

    actual fun availableProcessors(): Int = jsHardwareConcurrency()
}
