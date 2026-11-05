package com.nuvio.app.core.sync

import com.nuvio.app.web.compat.addVisibilityListener
import com.nuvio.app.web.compat.jsIsDocumentVisible
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal actual object AppForegroundMonitor {
    actual fun events(): Flow<AppVisibility> = callbackFlow {
        fun state(visible: Boolean) = if (visible) AppVisibility.Foreground else AppVisibility.Background
        trySend(state(jsIsDocumentVisible()))
        val remove = addVisibilityListener { visible -> trySend(state(visible)) }
        awaitClose { remove() }
    }.distinctUntilChanged()
}
