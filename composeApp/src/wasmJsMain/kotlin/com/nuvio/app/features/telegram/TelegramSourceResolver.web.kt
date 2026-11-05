package com.nuvio.app.features.telegram

import com.nuvio.app.features.streams.StreamItem

/** Telegram is available only in the Android full distribution. */
internal actual object TelegramSourceResolver {
    actual val isSupported: Boolean = false

    actual fun isEnabled(): Boolean = false

    actual suspend fun resolve(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): List<StreamItem> = emptyList()
}
