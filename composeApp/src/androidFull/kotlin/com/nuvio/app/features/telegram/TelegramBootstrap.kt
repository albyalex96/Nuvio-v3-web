package com.nuvio.app.features.telegram

import android.content.Context

/** Called from MainActivity: restores the Telegram session when the user logged in before. */
object TelegramBootstrap {
    fun initialize(context: Context) {
        TelegramRepository.initialize(context)
    }
}
