package com.nuvio.app.features.livetv

import com.nuvio.app.web.compat.WebInflate

internal actual fun liveTvGunzip(bytes: ByteArray, maxOutputBytes: Int): ByteArray {
    if (bytes.isEmpty()) return bytes
    return WebInflate.gunzip(bytes, maxOutputBytes)
}
