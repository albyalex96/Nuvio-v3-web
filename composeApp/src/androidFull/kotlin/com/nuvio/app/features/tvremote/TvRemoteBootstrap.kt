package com.nuvio.app.features.tvremote

import android.content.Context
import com.nuvio.app.tv.TvAppRuntime

/** Called from MainActivity: registers the phone remote client (phone/tablet app only). */
object TvRemoteBootstrap {
    fun initialize(context: Context) {
        if (TvAppRuntime.isTvApp || PhoneTvRemote.client != null) return
        PhoneTvRemote.client = PhoneTvRemoteClientImpl(context)
    }
}
