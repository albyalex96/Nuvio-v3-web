package com.nuvio.app.features.notifications

import com.nuvio.app.web.compat.jsLocalIsoDate

internal actual object EpisodeReleaseNotificationsClock {
    actual fun isoDateFromEpochMs(epochMs: Long): String = jsLocalIsoDate(epochMs.toDouble())
}
