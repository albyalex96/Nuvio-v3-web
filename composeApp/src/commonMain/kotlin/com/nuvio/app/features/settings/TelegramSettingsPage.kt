package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope

/** The Telegram integration page (login and cache). Real content only in the Android full distribution. */
internal expect fun LazyListScope.telegramSettingsContent(isTablet: Boolean)
