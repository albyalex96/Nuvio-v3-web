package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope

/** Telegram is available only in the Android full distribution: keep the "work in progress" page here. */
internal actual fun LazyListScope.telegramSettingsContent(isTablet: Boolean) {
    workInProgressIntegrationContent(isTablet = isTablet, page = SettingsPage.Telegram)
}
