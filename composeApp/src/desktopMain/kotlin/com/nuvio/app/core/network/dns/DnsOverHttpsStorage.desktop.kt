package com.nuvio.app.core.network.dns

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences

internal actual object DnsOverHttpsStorage {
    private const val PREFERENCES_NAME = "nuvio_dns_over_https"
    private const val SETTINGS_KEY = "settings"
    private const val SYNC_KEY = "sync_across_devices"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (preferences == null) {
            preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }
    }

    actual fun loadSettings(): String? = preferences?.getString(SETTINGS_KEY, null)

    actual fun saveSettings(json: String) {
        preferences?.edit()?.putString(SETTINGS_KEY, json)?.apply()
    }

    actual fun loadSyncEnabled(): Boolean? =
        preferences?.let { prefs -> if (prefs.contains(SYNC_KEY)) prefs.getBoolean(SYNC_KEY, false) else null }

    actual fun saveSyncEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(SYNC_KEY, enabled)?.apply()
    }
}
