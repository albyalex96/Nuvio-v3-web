package com.nuvio.app.desktop.compat

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.Executors

/**
 * Desktop counterpart of Android's SharedPreferences: same API surface used by the
 * storage classes, so their Android implementations can be ported unchanged.
 * Each preferences file is a JSON document under the app data directory.
 */
interface SharedPreferences {
    val all: Map<String, *>
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    operator fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

/** Mirrors androidx.core.content.edit. */
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) editor.commit() else editor.apply()
}

internal object DesktopPreferencesRegistry {
    private val log = Logger.withTag("DesktopPreferences")
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nuvio-prefs-writer").apply { isDaemon = false }
    }
    private val instances = mutableMapOf<String, FilePreferences>()

    val directory: File by lazy {
        DesktopStorage.rootDir.resolve("prefs").toFile().also { it.mkdirs() }
    }

    fun get(name: String): SharedPreferences = synchronized(instances) {
        instances.getOrPut(name) { FilePreferences(File(directory, "${sanitize(name)}.json")) }
    }

    fun clearAll() {
        synchronized(instances) {
            instances.values.forEach { it.edit().clear().commit() }
        }
        directory.listFiles()?.forEach { it.delete() }
    }

    fun flush() {
        writer.submit {}.get()
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private class FilePreferences(private val file: File) : SharedPreferences {
        private val lock = Any()
        private val values: MutableMap<String, Any> = load()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override val all: Map<String, *> get() = synchronized(lock) { HashMap(values) }

        override fun getString(key: String, defValue: String?): String? =
            synchronized(lock) { values[key] as? String } ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            synchronized(lock) { (values[key] as? Set<String>)?.toSet() } ?: defValues

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            synchronized(lock) { values[key] as? Boolean } ?: defValue

        override fun getInt(key: String, defValue: Int): Int =
            synchronized(lock) { (values[key] as? Number)?.toInt() } ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            synchronized(lock) { (values[key] as? Number)?.toLong() } ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            synchronized(lock) { (values[key] as? Number)?.toFloat() } ?: defValue

        override fun contains(key: String): Boolean = synchronized(lock) { key in values }

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            synchronized(lock) { listeners += listener }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            synchronized(lock) { listeners -= listener }
        }

        private inner class EditorImpl : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values?.toSet() }
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
            override fun remove(key: String) = apply { changes[key] = null }
            override fun clear() = apply { clear = true }

            override fun apply() {
                val snapshot = commitToMemory()
                writer.execute { write(snapshot) }
            }

            override fun commit(): Boolean {
                val snapshot = commitToMemory()
                return writer.submit<Boolean> { write(snapshot) }.get()
            }

            private fun commitToMemory(): Map<String, Any> {
                val changedKeys: Set<String>
                val snapshot: Map<String, Any>
                val currentListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>
                synchronized(lock) {
                    if (clear) values.clear()
                    changes.forEach { (key, value) ->
                        if (value == null) values.remove(key) else values[key] = value
                    }
                    changedKeys = changes.keys.toSet()
                    snapshot = HashMap(values)
                    currentListeners = listeners.toList()
                }
                changedKeys.forEach { key ->
                    currentListeners.forEach { it.onSharedPreferenceChanged(this@FilePreferences, key) }
                }
                return snapshot
            }
        }

        private fun write(snapshot: Map<String, Any>): Boolean = runCatching {
            val obj = JsonObject(snapshot.mapValues { (_, value) -> encode(value) })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(Json.encodeToString(JsonObject.serializer(), obj))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            true
        }.getOrElse { error ->
            log.e(error) { "Failed to write ${file.name}" }
            false
        }

        private fun load(): MutableMap<String, Any> {
            if (!file.exists()) return mutableMapOf()
            return runCatching {
                val obj = Json.parseToJsonElement(file.readText()).jsonObject
                obj.mapNotNull { (key, element) -> decode(element)?.let { key to it } }
                    .toMap(mutableMapOf())
            }.getOrElse { error ->
                log.e(error) { "Failed to read ${file.name}, starting empty" }
                mutableMapOf()
            }
        }

        private fun encode(value: Any): JsonElement {
            val (type, payload) = when (value) {
                is String -> "s" to JsonPrimitive(value)
                is Boolean -> "b" to JsonPrimitive(value)
                is Int -> "i" to JsonPrimitive(value)
                is Long -> "l" to JsonPrimitive(value)
                is Float -> "f" to JsonPrimitive(value)
                is Set<*> -> "S" to JsonArray(value.map { JsonPrimitive(it.toString()) })
                else -> "s" to JsonPrimitive(value.toString())
            }
            return JsonObject(mapOf("t" to JsonPrimitive(type), "v" to payload))
        }

        private fun decode(element: JsonElement): Any? {
            val obj = element as? JsonObject ?: return null
            val type = obj["t"]?.jsonPrimitive?.content ?: return null
            val value = obj["v"] ?: return null
            return when (type) {
                "s" -> value.jsonPrimitive.content
                "b" -> value.jsonPrimitive.booleanOrNull
                "i" -> value.jsonPrimitive.content.toIntOrNull()
                "l" -> value.jsonPrimitive.content.toLongOrNull()
                "f" -> value.jsonPrimitive.content.toFloatOrNull()
                "S" -> value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                else -> null
            }
        }
    }
}

/**
 * Minimal stand-in for android.content.Context, enough for the ported storage classes.
 */
open class Context {
    open val applicationContext: Context get() = this
    open val filesDir: File get() = DesktopStorage.rootDir.resolve("files").toFile().also { it.mkdirs() }
    open val cacheDir: File get() = DesktopStorage.cacheDir.toFile().also { it.mkdirs() }

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences =
        DesktopPreferencesRegistry.get(name)

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}

object DesktopContext : Context()
