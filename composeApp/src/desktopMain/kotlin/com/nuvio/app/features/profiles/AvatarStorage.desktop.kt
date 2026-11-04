package com.nuvio.app.features.profiles

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences

actual object AvatarStorage {
    private const val preferencesName = "nuvio_avatar_cache"
    private const val payloadKey = "avatar_catalog_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(payloadKey, null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(payloadKey, payload)
            ?.apply()
    }
}