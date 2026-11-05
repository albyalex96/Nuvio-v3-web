package com.nuvio.app.features.watchprogress


actual object WatchProgressClock {
    actual fun nowEpochMs(): Long = com.nuvio.app.web.compat.jsNowMs().toLong()
}
