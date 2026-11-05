package com.nuvio.app.features.watchprogress

import com.nuvio.app.web.compat.jsLocalIsoDate
import com.nuvio.app.web.compat.jsLocalStartOfDayMs
import com.nuvio.app.web.compat.jsNowMs

actual object CurrentDateProvider {
    actual fun todayIsoDate(): String = jsLocalIsoDate(jsNowMs())

    actual fun localStartOfDayEpochMs(isoDate: String): Long? =
        jsLocalStartOfDayMs(isoDate).takeIf { it >= 0 }?.toLong()
}
