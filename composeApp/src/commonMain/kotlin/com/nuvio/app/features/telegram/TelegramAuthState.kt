package com.nuvio.app.features.telegram

/** Login state of the Telegram (TDLib) client, shown by the Telegram settings page. */
sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()
    data object Initializing : TelegramAuthState()
    /** TELEGRAM_API_ID / TELEGRAM_API_HASH were not provided at build time. */
    data object NotConfigured : TelegramAuthState()
    /** The TDLib native library is missing for this CPU (e.g. x86 emulators). */
    data object Unavailable : TelegramAuthState()
    data object WaitPhone : TelegramAuthState()
    data class WaitQr(val link: String) : TelegramAuthState()
    data class WaitCode(val codeLength: Int = 5) : TelegramAuthState()
    data object WaitPassword : TelegramAuthState()
    data class Ready(val firstName: String, val userId: Long) : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}
