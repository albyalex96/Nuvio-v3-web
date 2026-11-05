package com.nuvio.app.features.streams

import com.nuvio.app.web.compat.jsNowMs

internal actual fun epochMs(): Long = jsNowMs().toLong()
