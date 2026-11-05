package com.nuvio.app.features.profiles

import com.nuvio.app.web.compat.NSUserDefaults

actual object AvatarStorage {
    private const val payloadKey = "avatar_catalog_payload"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey)

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey)
    }
}