package com.nuvio.app.core.logging

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual object AppLogFileStorage {
    private var file: File? = null

    fun initialize(context: Context) {
        file = File(File(context.filesDir, "app_logs"), "app_log.jsonl")
    }

    actual fun readText(): String? = file?.takeIf { it.isFile }?.readText()

    actual fun append(text: String) {
        val target = file ?: return
        target.parentFile?.mkdirs()
        target.appendText(text)
    }

    actual fun overwrite(text: String) {
        val target = file ?: return
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    actual fun clear() {
        file?.delete()
    }
}

internal actual object AppLoggerSettingsStorage {
    private const val PREFERENCES_NAME = "nuvio_app_logger"
    private const val ENABLED_KEY = "app_logger_enabled"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(ENABLED_KEY)) prefs.getBoolean(ENABLED_KEY, false) else null
        }

    actual fun saveEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(ENABLED_KEY, enabled)?.apply()
    }
}

internal actual object AppLogPlatform {
    // No system log comparable to logcat on desktop: the in-app logger is the only source.
    actual val supportsSystemLogImport: Boolean = false

    fun initialize(context: Context) {
        AppLogFileStorage.initialize(context)
        AppLoggerSettingsStorage.initialize(context)
    }

    actual fun readSystemLog(): List<PendingAppLogEntry> = emptyList()

    actual fun formatTimestamp(epochMs: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(epochMs))
}
