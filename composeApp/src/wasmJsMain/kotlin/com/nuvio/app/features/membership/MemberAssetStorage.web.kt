package com.nuvio.app.features.membership

import com.nuvio.app.web.compat.NSUserDefaults
import com.nuvio.app.web.compat.WebFileStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Web: images are cached as Base64 in localStorage; avatars are served as data: URIs. */
@OptIn(ExperimentalEncodingApi::class)
internal actual object MemberAssetStorage {
    private const val accessPayloadKey = "member_access_payload"
    private const val backgroundCatalogPayloadKey = "member_background_catalog_payload"
    private const val backgroundDirectory = "membership/backgrounds"
    private const val avatarDirectory = "membership/avatars"

    actual fun loadAccessPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(accessPayloadKey)

    actual fun saveAccessPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = accessPayloadKey)
    }

    actual fun loadProfileBackgroundCatalogPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(backgroundCatalogPayloadKey)

    actual fun saveProfileBackgroundCatalogPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = backgroundCatalogPayloadKey)
    }

    actual fun loadProfileBackground(cacheKey: String): ByteArray? =
        WebFileStore.read("$backgroundDirectory/${safeKey(cacheKey)}")
            ?.let { runCatching { Base64.decode(it) }.getOrNull() }

    actual fun saveProfileBackground(cacheKey: String, bytes: ByteArray) {
        runCatching { WebFileStore.write("$backgroundDirectory/${safeKey(cacheKey)}", Base64.encode(bytes)) }
    }

    actual fun loadProfileAvatar(cacheKey: String): String? =
        WebFileStore.read("$avatarDirectory/${safeKey(cacheKey)}")?.let { "data:image/png;base64,$it" }

    actual fun saveProfileAvatar(cacheKey: String, bytes: ByteArray): String? = runCatching {
        val encoded = Base64.encode(bytes)
        WebFileStore.write("$avatarDirectory/${safeKey(cacheKey)}", encoded)
        "data:image/png;base64,$encoded"
    }.getOrNull()

    actual fun clearAccess() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(accessPayloadKey)
    }

    private fun safeKey(value: String): String = value.map { character ->
        if (character.isLetterOrDigit() || character == '.' || character == '_' || character == '-') character else '_'
    }.joinToString("")
}
