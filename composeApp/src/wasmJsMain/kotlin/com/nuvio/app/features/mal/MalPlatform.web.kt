package com.nuvio.app.features.mal

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.web.compat.NSUserDefaults

internal actual object MalAuthStorage {
    private const val METADATA_KEY = "mal_auth_metadata"
    private const val CREDENTIALS_KEY = "mal_credentials"
    private const val CODE_VERIFIER_KEY = "mal_code_verifier"
    private const val KEYCHAIN_SERVICE = "com.nuvio.media.mal"

    actual fun loadMetadataPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(METADATA_KEY))

    actual fun saveMetadataPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(METADATA_KEY))
    }

    actual fun loadCredentialsPayload(): String? = loadKeychainValue(CREDENTIALS_KEY)

    actual fun saveCredentialsPayload(value: String?) = saveKeychainValue(CREDENTIALS_KEY, value)

    actual fun loadCodeVerifier(): String? = loadKeychainValue(CODE_VERIFIER_KEY)

    actual fun saveCodeVerifier(value: String?) = saveKeychainValue(CODE_VERIFIER_KEY, value)

    actual fun removeProfile(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(METADATA_KEY, profileId))
        deleteKeychainValue(CREDENTIALS_KEY, profileId)
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
