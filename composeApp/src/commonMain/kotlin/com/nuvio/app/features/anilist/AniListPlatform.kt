package com.nuvio.app.features.anilist

/** Profile-scoped AniList storage: plain metadata plus encrypted credentials (tokens). */
internal expect object AniListAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadCredentialsPayload(): String?
    fun saveCredentialsPayload(value: String?)
    fun removeProfile(profileId: Int)
}
