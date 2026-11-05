package com.nuvio.app.core.storage

import com.nuvio.app.web.compat.WebFileStore

internal actual object AppFileCache {
    private const val ROOT = "cache"

    actual fun read(path: String): String? = WebFileStore.read("$ROOT/$path")

    actual fun fileUri(path: String): String? = WebFileStore.uri("$ROOT/$path")

    actual fun write(path: String, payload: String) = WebFileStore.write("$ROOT/$path", payload)
}
