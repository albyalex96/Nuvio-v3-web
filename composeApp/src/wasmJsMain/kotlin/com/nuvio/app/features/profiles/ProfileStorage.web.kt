package com.nuvio.app.features.profiles

import com.nuvio.app.web.compat.NSUserDefaults

actual object ProfileStorage {
    private const val payloadKey = "profile_payload"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey)

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey)
    }
}