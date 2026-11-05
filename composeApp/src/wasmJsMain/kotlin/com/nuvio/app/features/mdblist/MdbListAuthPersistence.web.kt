package com.nuvio.app.features.mdblist

import com.nuvio.app.web.compat.WebSecureStore

internal actual object PlatformMdbListAuthPersistence : MdbListAuthPersistence {
    private val store = WebSecureStore("com.nuvio.media.mdblist")

    actual override fun read(profileId: Int): String? = store.read("profile.$profileId")

    actual override fun write(profileId: Int, value: String?) = store.write("profile.$profileId", value)

    actual override fun clear() = store.clear()
}
