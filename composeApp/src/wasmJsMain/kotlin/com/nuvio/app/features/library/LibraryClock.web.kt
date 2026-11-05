package com.nuvio.app.features.library


actual object LibraryClock {
    actual fun nowEpochMs(): Long = com.nuvio.app.web.compat.jsNowMs().toLong()
}
