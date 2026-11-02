package com.nuvio.app.features.wetrakr

/** Profile-scoped WeTrakr storage: plain metadata plus encrypted credentials (tokens). */
internal expect object WeTrakrAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadCredentialsPayload(): String?
    fun saveCredentialsPayload(value: String?)
    fun removeProfile(profileId: Int)
}
