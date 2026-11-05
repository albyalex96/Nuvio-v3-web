package com.nuvio.app.features.subdl

import com.nuvio.app.web.compat.WebInflate

internal actual fun inflateRawDeflate(bytes: ByteArray, maxOutputBytes: Int): ByteArray {
    if (bytes.isEmpty()) return bytes
    return WebInflate.inflateRaw(bytes, maxOutputBytes).first
}
