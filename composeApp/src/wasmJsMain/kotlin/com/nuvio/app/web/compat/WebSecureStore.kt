package com.nuvio.app.web.compat

/**
 * Credential store for the web target (replaces the iOS Keychain). The browser has no
 * secure enclave: values live in `localStorage`, isolated by the page origin.
 */
internal class WebSecureStore(private val service: String) {
    private val prefix = "secure.$service."
    private val defaults get() = NSUserDefaults.standardUserDefaults

    fun read(key: String): String? = defaults.stringForKey(prefix + key)

    fun write(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(prefix + key) else defaults.setObject(value, forKey = prefix + key)
    }

    fun clear() {
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(prefix) }
            .forEach { defaults.removeObjectForKey(it) }
    }
}
