package com.nuvio.app.core.format

import com.nuvio.app.web.compat.jsDisplayName

internal actual fun platformCountryDisplayName(code: String, localeTag: String): String? =
    jsDisplayName("region", code, localeTag)

internal actual fun platformLanguageDisplayName(code: String, localeTag: String): String? =
    jsDisplayName("language", code, localeTag)
