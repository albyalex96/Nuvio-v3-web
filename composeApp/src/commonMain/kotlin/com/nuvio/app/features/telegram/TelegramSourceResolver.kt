package com.nuvio.app.features.telegram

import com.nuvio.app.features.streams.StreamItem

/** Searches the user's Telegram chats for video files. Implemented only in the Android full distribution. */
internal expect object TelegramSourceResolver {
    /** True when this build ships the Telegram integration (Android full distribution). */
    val isSupported: Boolean

    /** True when the platform supports Telegram and the user is logged in. */
    fun isEnabled(): Boolean

    suspend fun resolve(
        title: String,
        year: Int?,
        season: Int? = null,
        episode: Int? = null,
    ): List<StreamItem>
}
