package com.nuvio.app.desktop.compat

import java.io.File
import java.net.URI

/**
 * Java writes file URIs as `file:/C:/...`, which FFmpeg and mpv do not read on Windows:
 * native libraries get the plain path instead. Other addresses are returned unchanged.
 */
internal fun String.toNativeMediaLocation(): String =
    if (startsWith("file:", ignoreCase = true)) {
        runCatching { File(URI(this)).absolutePath }.getOrDefault(this)
    } else {
        this
    }
