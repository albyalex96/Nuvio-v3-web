package com.nuvio.app.features.player

import com.nuvio.app.web.compat.jsIsTouchTablet

internal actual fun isTabletSubtitleFormFactor(): Boolean = jsIsTouchTablet()
