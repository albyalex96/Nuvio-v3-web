package com.nuvio.app.features.tvremote

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Sealing of the phone remote messages (AES-256-GCM), shared by the TV server and the phone client. */
object TvRemoteCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    const val KEY_BYTES = 32

    private val random = SecureRandom()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun randomId(): String = UUID.randomUUID().toString()

    fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Short fingerprint for logs (never the key itself). */
    fun fingerprint(key: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(key).take(4).joinToString("") { "%02x".format(it) }

    /**
     * Seals [payload] for [path]: the path and the client id are authenticated, so a body can not be
     * replayed on another endpoint or under another client.
     */
    fun <T> seal(
        key: ByteArray,
        clientId: String,
        path: String,
        serializer: KSerializer<T>,
        payload: T,
    ): TvRemoteEnvelope {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(clientId, path))
        val plain = json.encodeToString(serializer, payload).encodeToByteArray()
        return TvRemoteEnvelope(
            clientId = clientId,
            iv = encode(iv),
            data = encode(cipher.doFinal(plain)),
        )
    }

    /** Opens an envelope; throws when the key, the path or the content does not match. */
    fun <T> open(
        key: ByteArray,
        path: String,
        serializer: KSerializer<T>,
        envelope: TvRemoteEnvelope,
    ): T {
        val iv = decode(envelope.iv)
        require(iv.size == IV_BYTES) { "Invalid nonce" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(envelope.clientId, path))
        val plain = cipher.doFinal(decode(envelope.data))
        return json.decodeFromString(serializer, plain.decodeToString())
    }

    private fun aad(clientId: String, path: String): ByteArray =
        "nuvio-remote/v$TV_REMOTE_PROTOCOL_VERSION|$clientId|$path".encodeToByteArray()
}

/**
 * Strings stored in [SharedPreferences] encrypted with an Android Keystore key (keys of paired
 * devices). Values that can no longer be decrypted (key lost after a restore) read as null.
 */
class TvRemoteSecretStore(
    private val preferences: SharedPreferences,
    private val keyAlias: String,
) {
    private val lock = Any()

    fun load(key: String): String? = synchronized(lock) {
        val value = preferences.getString(key, null) ?: return@synchronized null
        runCatching {
            val parts = value.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, TvRemoteCrypto.decode(parts[0])))
            cipher.updateAAD(key.encodeToByteArray())
            cipher.doFinal(TvRemoteCrypto.decode(parts[1])).decodeToString()
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    fun save(key: String, value: String?) {
        synchronized(lock) {
            val editor = preferences.edit()
            if (value == null) {
                editor.remove(key)
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                cipher.updateAAD(key.encodeToByteArray())
                val encrypted = cipher.doFinal(value.encodeToByteArray())
                editor.putString(key, "${TvRemoteCrypto.encode(cipher.iv)}.${TvRemoteCrypto.encode(encrypted)}")
            }
            editor.commit()
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
