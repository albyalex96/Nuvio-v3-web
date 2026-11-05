package com.nuvio.app.core.time

import com.nuvio.app.web.compat.jsLocalIsoDate
import com.nuvio.app.web.compat.jsNowMs
import com.nuvio.app.web.compat.webParseLocalDateTime

internal actual object EpisodeReleaseDatePlatform {
    actual fun nowEpochMs(): Long = jsNowMs().toLong()

    actual fun localIsoDateAtEpochMs(epochMs: Long): String? = jsLocalIsoDate(epochMs.toDouble())

    actual fun localDateTimeToEpochMs(normalizedIsoDateTime: String): Long? =
        webParseLocalDateTime(normalizedIsoDateTime)
}
