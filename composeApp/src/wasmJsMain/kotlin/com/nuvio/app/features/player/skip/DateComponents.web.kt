package com.nuvio.app.features.player.skip

import com.nuvio.app.web.compat.jsLocalDay
import com.nuvio.app.web.compat.jsLocalMonth
import com.nuvio.app.web.compat.jsLocalYear

internal actual fun currentDateComponents(): DateComponents =
    DateComponents(year = jsLocalYear(), month = jsLocalMonth(), day = jsLocalDay())
