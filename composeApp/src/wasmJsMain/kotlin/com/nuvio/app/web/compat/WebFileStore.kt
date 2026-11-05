package com.nuvio.app.web.compat

/**
 * Tiny "file system" for the web target: text files are kept in `localStorage`
 * under a `file:` prefix. Paths are virtual and only meaningful inside the app.
 */
internal object WebFileStore {
    private const val PREFIX = "file:"
    private val defaults get() = NSUserDefaults.standardUserDefaults

    fun read(path: String): String? = defaults.stringForKey(PREFIX + path)

    fun write(path: String, text: String) = defaults.setObject(text, forKey = PREFIX + path)

    fun exists(path: String): Boolean = read(path) != null

    fun delete(path: String) = defaults.removeObjectForKey(PREFIX + path)

    fun deleteTree(directory: String) {
        val prefix = PREFIX + directory.trimEnd('/') + "/"
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(prefix) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    fun uri(path: String): String = "webfile://$path"
}
