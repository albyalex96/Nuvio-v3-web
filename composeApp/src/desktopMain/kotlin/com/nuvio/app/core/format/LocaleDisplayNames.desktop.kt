package com.nuvio.app.core.format

import java.util.Locale

internal actual fun platformCountryDisplayName(code: String, localeTag: String): String? =
    runCatching { Locale("", code).getDisplayCountry(Locale.forLanguageTag(localeTag)) }.getOrNull()

internal actual fun platformLanguageDisplayName(code: String, localeTag: String): String? =
    runCatching { Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag(localeTag)) }.getOrNull()
