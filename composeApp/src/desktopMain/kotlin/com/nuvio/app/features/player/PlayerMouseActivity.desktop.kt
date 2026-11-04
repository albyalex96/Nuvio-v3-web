package com.nuvio.app.features.player

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/** A fully transparent 1x1 cursor: hides the pointer over the video, like mpv/VLC. */
internal actual val hiddenPlayerPointerIcon: PointerIcon? by lazy {
    runCatching {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-hidden-cursor"))
    }.getOrNull()
}
