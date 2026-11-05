package com.nuvio.app.core.sync

import com.nuvio.app.web.compat.NSUserDefaults

actual object SyncClientIdentityStorage {
    private const val clientIdKey = "client_instance_id"

    actual fun loadClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(clientIdKey)

    actual fun saveClientId(clientId: String) {
        NSUserDefaults.standardUserDefaults.setObject(clientId, forKey = clientIdKey)
    }
}
