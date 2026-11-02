package com.nuvio.app.features.wetrakr

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.app.core.storage.ProfileScopedKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal actual object WeTrakrAuthStorage {
    private const val PREFERENCES_NAME = "nuvio_wetrakr_auth"
    private const val METADATA_KEY = "wetrakr_auth_metadata"
    private const val CREDENTIALS_KEY = "wetrakr_credentials"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "nuvio.wetrakr.credentials.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadMetadataPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(METADATA_KEY), null)

    actual fun saveMetadataPayload(payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(METADATA_KEY), payload)?.apply()
    }

    actual fun loadCredentialsPayload(): String? = loadEncrypted(CREDENTIALS_KEY)

    actual fun saveCredentialsPayload(value: String?) = saveEncrypted(CREDENTIALS_KEY, value)

    actual fun removeProfile(profileId: Int) {
        preferences?.edit()
            ?.remove(ProfileScopedKey.of(METADATA_KEY, profileId))
            ?.remove(ProfileScopedKey.of(CREDENTIALS_KEY, profileId))
            ?.apply()
    }

    private fun loadEncrypted(key: String): String? {
        val scopedKey = ProfileScopedKey.of(key)
        val stored = preferences?.getString(scopedKey, null) ?: return null
        return runCatching { decrypt(stored) }
            .onFailure { preferences?.edit()?.remove(scopedKey)?.apply() }
            .getOrNull()
    }

    private fun saveEncrypted(key: String, value: String?) {
        val scopedKey = ProfileScopedKey.of(key)
        val editor = preferences?.edit() ?: return
        if (value.isNullOrBlank()) {
            editor.remove(scopedKey).apply()
        } else {
            editor.putString(scopedKey, encrypt(value)).apply()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        return "${cipher.iv.toBase64()}.${ciphertext.toBase64()}"
    }

    private fun decrypt(value: String): String {
        val separator = value.indexOf('.')
        require(separator > 0 && separator < value.lastIndex) { "Invalid encrypted credential" }
        val iv = value.substring(0, separator).fromBase64()
        val ciphertext = value.substring(separator + 1).fromBase64()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)
}
