package com.nuvio.app.features.simkl

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object SimklPlatformClock {
    actual fun nowEpochMs(): Long = com.nuvio.app.web.compat.jsNowMs().toLong()
}

internal actual object SimklPkceCrypto {
    actual fun secureRandomBytes(size: Int): ByteArray {
        require(size > 0)
        return com.nuvio.app.web.compat.secureRandomBytes(size)
    }

    actual fun sha256(value: ByteArray): ByteArray {
        require(value.isNotEmpty())
        return com.nuvio.app.features.cloudstream.sha256Hex(value).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

internal actual object SimklAuthStorage {
    private const val METADATA_KEY = "simkl_auth_metadata"
    private const val ACCESS_TOKEN_KEY = "simkl_access_token"
    private const val CODE_VERIFIER_KEY = "simkl_code_verifier"
    private const val KEYCHAIN_SERVICE = "com.nuvio.media.simkl"

    actual fun loadMetadataPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(METADATA_KEY))

    actual fun saveMetadataPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(METADATA_KEY))
    }

    actual fun loadAccessToken(): String? = loadKeychainValue(ACCESS_TOKEN_KEY)

    actual fun saveAccessToken(value: String?) = saveKeychainValue(ACCESS_TOKEN_KEY, value)

    actual fun loadCodeVerifier(): String? = loadKeychainValue(CODE_VERIFIER_KEY)

    actual fun saveCodeVerifier(value: String?) = saveKeychainValue(CODE_VERIFIER_KEY, value)

    actual fun removeProfile(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(METADATA_KEY, profileId))
        deleteKeychainValue(ACCESS_TOKEN_KEY, profileId)
        deleteKeychainValue(CODE_VERIFIER_KEY, profileId)
    }

    private fun keychainKey(key: String, profileId: Int? = null): String =
        "secure." + KEYCHAIN_SERVICE + "." + (profileId?.let { id -> ProfileScopedKey.of(key, id) } ?: ProfileScopedKey.of(key))

    private fun loadKeychainValue(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(keychainKey(key))

    private fun saveKeychainValue(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            deleteKeychainValue(key)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(value, forKey = keychainKey(key))
        }
    }

    private fun deleteKeychainValue(key: String, profileId: Int? = null) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(keychainKey(key, profileId))
    }
}
