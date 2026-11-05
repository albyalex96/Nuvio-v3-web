package com.nuvio.app.web.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun lsGet(key: String): String? = js("window.localStorage.getItem(key)")
private fun lsSet(key: String, value: String) { js("window.localStorage.setItem(key, value)") }
private fun lsRemove(key: String) { js("window.localStorage.removeItem(key)") }
private fun lsLength(): Int = js("window.localStorage.length")
private fun lsKey(index: Int): String? = js("window.localStorage.key(index)")

/**
 * Minimal key-value store with the same API as iOS `NSUserDefaults`, backed by the
 * browser's `localStorage`. It lets the web target reuse the iOS storage actuals
 * unchanged. Values are stored with a type prefix (s:, b:, i:, f:, a:).
 */
class NSUserDefaults(suiteName: String? = null) {
    companion object {
        private const val ROOT_PREFIX = "nuvio:"
        val standardUserDefaults: NSUserDefaults = NSUserDefaults()
    }

    private val PREFIX: String = if (suiteName == null) ROOT_PREFIX else "$ROOT_PREFIX$suiteName/"

    private fun raw(key: String): String? = runCatching { lsGet(PREFIX + key) }.getOrNull()
    private fun put(key: String, value: String) { runCatching { lsSet(PREFIX + key, value) } }

    fun objectForKey(defaultName: String): Any? {
        val stored = raw(defaultName) ?: return null
        return when (stored.take(2)) {
            "s:" -> stored.substring(2)
            "b:" -> stored.substring(2) == "1"
            "i:" -> stored.substring(2).toLongOrNull()
            "f:" -> stored.substring(2).toDoubleOrNull()
            "a:" -> decodeArray(stored.substring(2))
            else -> stored
        }
    }

    fun stringForKey(defaultName: String): String? = when (val value = objectForKey(defaultName)) {
        null -> null
        is String -> value
        is List<*> -> null
        is Boolean -> if (value) "1" else "0"
        else -> value.toString()
    }

    fun boolForKey(defaultName: String): Boolean = when (val value = objectForKey(defaultName)) {
        is Boolean -> value
        is Long -> value != 0L
        is Double -> value != 0.0
        is String -> value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
        else -> false
    }

    fun integerForKey(defaultName: String): Long = when (val value = objectForKey(defaultName)) {
        is Long -> value
        is Double -> value.toLong()
        is Boolean -> if (value) 1L else 0L
        is String -> value.toDoubleOrNull()?.toLong() ?: 0L
        else -> 0L
    }

    fun floatForKey(defaultName: String): Float = doubleForKey(defaultName).toFloat()

    fun doubleForKey(defaultName: String): Double = when (val value = objectForKey(defaultName)) {
        is Long -> value.toDouble()
        is Double -> value
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    fun arrayForKey(defaultName: String): List<*>? = objectForKey(defaultName) as? List<*>

    fun stringArrayForKey(defaultName: String): List<*>? = arrayForKey(defaultName)

    fun setObject(value: Any?, forKey: String) {
        when (value) {
            null -> removeObjectForKey(forKey)
            is String -> put(forKey, "s:$value")
            is Boolean -> setBool(value, forKey)
            is Int -> setInteger(value.toLong(), forKey)
            is Long -> setInteger(value, forKey)
            is Float -> setDouble(value.toDouble(), forKey)
            is Double -> setDouble(value, forKey)
            is Collection<*> -> put(forKey, "a:" + encodeArray(value))
            is Array<*> -> put(forKey, "a:" + encodeArray(value.toList()))
            else -> put(forKey, "s:$value")
        }
    }

    fun setBool(value: Boolean, forKey: String) = put(forKey, if (value) "b:1" else "b:0")
    fun setInteger(value: Long, forKey: String) = put(forKey, "i:$value")
    fun setFloat(value: Float, forKey: String) = setDouble(value.toDouble(), forKey)
    fun setDouble(value: Double, forKey: String) = put(forKey, "f:$value")

    fun removeObjectForKey(defaultName: String) {
        runCatching { lsRemove(PREFIX + defaultName) }
    }

    fun synchronize(): Boolean = true

    /** Removes every key of this store (the iOS API takes the suite name; it must match). */
    @Suppress("UNUSED_PARAMETER")
    fun removePersistentDomainForName(domainName: String) {
        val count = runCatching { lsLength() }.getOrDefault(0)
        val keys = (0 until count).mapNotNull { runCatching { lsKey(it) }.getOrNull() }.filter { it.startsWith(PREFIX) }
        keys.forEach { runCatching { lsRemove(it) } }
    }

    fun dictionaryRepresentation(): Map<Any?, *> {
        val result = linkedMapOf<Any?, Any?>()
        val count = runCatching { lsLength() }.getOrDefault(0)
        for (index in 0 until count) {
            val key = runCatching { lsKey(index) }.getOrNull() ?: continue
            if (!key.startsWith(PREFIX)) continue
            val name = key.removePrefix(PREFIX)
            result[name] = objectForKey(name)
        }
        return result
    }

    private fun encodeArray(values: Collection<*>): String =
        JsonArray(values.map { JsonPrimitive(it?.toString()) }).toString()

    private fun decodeArray(encoded: String): List<String?> =
        runCatching {
            Json.parseToJsonElement(encoded).let { it as JsonArray }.map { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList())
}
