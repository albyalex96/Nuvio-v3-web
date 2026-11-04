package com.nuvio.app.desktop.mpv

import com.nuvio.app.desktop.compat.DesktopContext

/** The mpv instance currently on screen, plus the volume kept between videos and app restarts. */
internal object DesktopActivePlayer {
    @Volatile
    var current: MpvPlayer? = null

    private val prefs by lazy { DesktopContext.getSharedPreferences("nuvio_desktop_player", 0) }

    /** 0..1, default full volume. */
    val savedVolume: Float get() = prefs.getFloat("volume", 1f).coerceIn(0f, 1f)

    fun rememberVolume(level: Float) {
        prefs.edit().putFloat("volume", level.coerceIn(0f, 1f)).apply()
    }
}
