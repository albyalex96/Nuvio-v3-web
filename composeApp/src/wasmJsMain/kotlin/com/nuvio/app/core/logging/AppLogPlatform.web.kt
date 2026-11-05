package com.nuvio.app.core.logging

import com.nuvio.app.web.compat.NSUserDefaults
import com.nuvio.app.web.compat.WebFileStore
import com.nuvio.app.web.compat.webFormatDate

internal actual object AppLogFileStorage {
    private const val PATH = "app_logs/app_log.jsonl"

    // localStorage is small (about 5 MB per origin): keep only the most recent part of the log.
    private const val MAX_CHARS = 1_000_000

    actual fun readText(): String? = WebFileStore.read(PATH)

    actual fun append(text: String) {
        if (text.isEmpty()) return
        overwrite(readText().orEmpty() + text)
    }

    actual fun overwrite(text: String) {
        val trimmed = if (text.length > MAX_CHARS) {
            val tail = text.substring(text.length - MAX_CHARS)
            tail.substringAfter('\n', tail)
        } else {
            text
        }
        WebFileStore.write(PATH, trimmed)
    }

    actual fun clear() = WebFileStore.delete(PATH)
}

internal actual object AppLoggerSettingsStorage {
    private const val ENABLED_KEY = "app_logger_enabled"

    actual fun loadEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(ENABLED_KEY) != null) defaults.boolForKey(ENABLED_KEY) else null
    }

    actual fun saveEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ENABLED_KEY)
    }
}

internal actual object AppLogPlatform {
    actual val supportsSystemLogImport: Boolean = false

    actual fun readSystemLog(): List<PendingAppLogEntry> = emptyList()

    actual fun formatTimestamp(epochMs: Long, pattern: String): String = webFormatDate(epochMs, pattern)
}
