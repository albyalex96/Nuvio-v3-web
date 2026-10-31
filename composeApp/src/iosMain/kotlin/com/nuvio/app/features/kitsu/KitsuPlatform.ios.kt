package com.nuvio.app.features.kitsu

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal actual object KitsuAuthStorage {
    private const val METADATA_KEY = "kitsu_auth_metadata"
    private const val CREDENTIALS_KEY = "kitsu_credentials"
    private const val KEYCHAIN_SERVICE = "com.nuvio.media.kitsu"

    actual fun loadMetadataPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(METADATA_KEY))

    actual fun saveMetadataPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(METADATA_KEY))
    }

    actual fun loadCredentialsPayload(): String? = loadKeychainValue(CREDENTIALS_KEY)

    actual fun saveCredentialsPayload(value: String?) = saveKeychainValue(CREDENTIALS_KEY, value)

    actual fun removeProfile(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(METADATA_KEY, profileId))
        deleteKeychainValue(CREDENTIALS_KEY, profileId)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun loadKeychainValue(key: String): String? = withKeychainQuery(key) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            if (status != errSecSuccess) return@memScoped null
            val data: CFDataRef = result.value?.reinterpret() ?: return@memScoped null
            try {
                val length = CFDataGetLength(data).toInt()
                val bytes = CFDataGetBytePtr(data) ?: return@memScoped null
                ByteArray(length) { index -> bytes[index].toByte() }.decodeToString()
            } finally {
                CFRelease(data)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveKeychainValue(key: String, value: String?) {
        deleteKeychainValue(key)
        if (value.isNullOrBlank()) return
        withKeychainQuery(key) { query ->
            val bytes = value.encodeToByteArray().toUByteArray()
            val data = CFDataCreate(null, bytes.refTo(0), bytes.size.toLong())
                ?: error("Unable to encode Kitsu credential")
            try {
                CFDictionarySetValue(query, kSecValueData, data)
                check(SecItemAdd(query, null) == errSecSuccess) { "Unable to store Kitsu credential" }
            } finally {
                CFRelease(data)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteKeychainValue(key: String, profileId: Int? = null) {
        withKeychainQuery(key, profileId) { query -> SecItemDelete(query) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private inline fun <T> withKeychainQuery(
        key: String,
        profileId: Int? = null,
        block: (CFMutableDictionaryRef) -> T,
    ): T {
        val service = CFStringCreateWithCString(null, KEYCHAIN_SERVICE, kCFStringEncodingUTF8)
            ?: error("Unable to encode Keychain service")
        val account = CFStringCreateWithCString(
            null,
            profileId?.let { id -> ProfileScopedKey.of(key, id) } ?: ProfileScopedKey.of(key),
            kCFStringEncodingUTF8,
        ) ?: error("Unable to encode Keychain account")
        val query = CFDictionaryCreateMutable(
            allocator = null,
            capacity = 0L,
            keyCallBacks = null,
            valueCallBacks = null,
        ) ?: error("Unable to create Keychain query")
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(account)
            CFRelease(service)
        }
    }
}
