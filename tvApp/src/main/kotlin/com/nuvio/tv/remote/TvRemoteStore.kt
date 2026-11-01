package com.nuvio.tv.remote

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.nuvio.app.features.tvremote.TvRemoteCrypto
import com.nuvio.app.features.tvremote.TvRemoteSecretStore
import org.json.JSONArray
import org.json.JSONObject

/** A phone allowed to control this TV. Its key lives in [TvRemoteStore] (Keystore encrypted). */
data class TvPairedPhone(
    val clientId: String,
    val name: String,
    val pairedAtMs: Long,
)

/** Identity of this TV and the paired phones (device-wide, not per profile). */
internal class TvRemoteStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val secrets = TvRemoteSecretStore(preferences, KEY_ALIAS)

    val tvId: String = preferences.getString(TV_ID, null) ?: TvRemoteCrypto.randomId().also { id ->
        preferences.edit().putString(TV_ID, id).apply()
    }

    val tvName: String = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android TV"

    fun phones(): List<TvPairedPhone> {
        val raw = preferences.getString(PHONES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                TvPairedPhone(
                    clientId = item.getString("id"),
                    name = item.optString("name"),
                    pairedAtMs = item.optLong("pairedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clientKey(clientId: String): ByteArray? =
        secrets.load(clientKeyName(clientId))?.let { runCatching { TvRemoteCrypto.decode(it) }.getOrNull() }

    fun addPhone(phone: TvPairedPhone, key: ByteArray) {
        secrets.save(clientKeyName(phone.clientId), TvRemoteCrypto.encode(key))
        savePhones(phones().filterNot { it.clientId == phone.clientId } + phone)
    }

    fun removePhone(clientId: String) {
        secrets.save(clientKeyName(clientId), null)
        savePhones(phones().filterNot { it.clientId == clientId })
    }

    private fun savePhones(phones: List<TvPairedPhone>) {
        val array = JSONArray()
        phones.forEach { phone ->
            array.put(
                JSONObject()
                    .put("id", phone.clientId)
                    .put("name", phone.name)
                    .put("pairedAt", phone.pairedAtMs),
            )
        }
        preferences.edit().putString(PHONES, array.toString()).commit()
    }

    private fun clientKeyName(clientId: String) = "client_key_$clientId"

    private companion object {
        const val PREFERENCES = "nuvio_tv_phone_remote"
        const val KEY_ALIAS = "com.nuvio.tv.phone_remote.v1"
        const val TV_ID = "tv_id"
        const val PHONES = "paired_phones"
    }
}
