package com.nuvio.app.features.watchprogress

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences

actual object WatchProgressStorage {
    private const val preferencesName = "nuvio_watch_progress"
    private const val payloadKey = "watch_progress_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString("${payloadKey}_$profileId", null)

    actual fun savePayload(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString("${payloadKey}_$profileId", payload)
            ?.apply()
    }
}
