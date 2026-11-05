package com.nuvio.app.features.servers

import com.nuvio.app.web.compat.WebSecureStore

internal actual object ServerStorage {
    private val store = WebSecureStore("com.nuvio.media.servers")

    actual fun read(key: String): String? = store.read(key)

    actual fun write(key: String, value: String?) = store.write(key, value)

    actual fun clear() = store.clear()
}
