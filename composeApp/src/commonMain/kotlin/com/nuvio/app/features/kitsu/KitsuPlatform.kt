package com.nuvio.app.features.kitsu

/** Profile-scoped Kitsu storage: plain metadata plus encrypted credentials (tokens). */
internal expect object KitsuAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadCredentialsPayload(): String?
    fun saveCredentialsPayload(value: String?)
    fun removeProfile(profileId: Int)
}
