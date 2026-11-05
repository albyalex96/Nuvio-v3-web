package com.nuvio.app.core.network.dns

import com.nuvio.app.web.compat.NSUserDefaults

/** The browser resolves DNS itself: DoH is not offered on the web. */
internal actual object DnsOverHttpsPlatform {
    actual val isSupported: Boolean = false

    actual suspend fun test(settings: DnsOverHttpsSettings): DnsOverHttpsTestResult =
        DnsOverHttpsTestResult.Failure("DNS over HTTPS is not supported on this platform")
}

internal actual object DnsOverHttpsStorage {
    private const val SETTINGS_KEY = "dns_over_https_settings"
    private const val SYNC_KEY = "dns_over_https_sync_across_devices"

    actual fun loadSettings(): String? = NSUserDefaults.standardUserDefaults.stringForKey(SETTINGS_KEY)

    actual fun saveSettings(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, forKey = SETTINGS_KEY)
    }

    actual fun loadSyncEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(SYNC_KEY) != null) defaults.boolForKey(SYNC_KEY) else null
    }

    actual fun saveSyncEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = SYNC_KEY)
    }
}
