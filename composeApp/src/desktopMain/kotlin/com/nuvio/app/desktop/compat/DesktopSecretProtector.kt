package com.nuvio.app.desktop.compat

import com.nuvio.app.core.storage.DesktopStorage
import com.sun.jna.platform.win32.Crypt32Util
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts credentials at rest. Windows uses DPAPI (bound to the Windows user account, like
 * the Android KeyStore is bound to the app); other systems use AES-GCM with a local key file.
 */
internal object DesktopSecretProtector {
    private const val DPAPI_PREFIX = "dpapi:"
    private const val AES_PREFIX = "aes:"
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun protect(value: String): String {
        val plain = value.encodeToByteArray()
        return if (isWindows) {
            DPAPI_PREFIX + encoder.encodeToString(Crypt32Util.cryptProtectData(plain))
        } else {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, fileKey(), GCMParameterSpec(128, iv))
            AES_PREFIX + encoder.encodeToString(iv) + "." + encoder.encodeToString(cipher.doFinal(plain))
        }
    }

    fun unprotect(value: String): String = when {
        value.startsWith(DPAPI_PREFIX) ->
            Crypt32Util.cryptUnprotectData(decoder.decode(value.removePrefix(DPAPI_PREFIX))).decodeToString()
        value.startsWith(AES_PREFIX) -> {
            val (iv, data) = value.removePrefix(AES_PREFIX).split('.', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fileKey(), GCMParameterSpec(128, decoder.decode(iv)))
            cipher.doFinal(decoder.decode(data)).decodeToString()
        }
        else -> error("Unknown credential format")
    }

    private fun fileKey(): SecretKeySpec {
        val file = DesktopStorage.rootDir.resolve("secret.key").toFile()
        val bytes = if (file.isFile && file.length() == 32L) {
            file.readBytes()
        } else {
            ByteArray(32).also(SecureRandom()::nextBytes).also { file.writeBytes(it) }
        }
        return SecretKeySpec(bytes, "AES")
    }
}
