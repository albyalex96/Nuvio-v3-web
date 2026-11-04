package com.nuvio.app.features.subdl

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

internal actual fun inflateRawDeflate(bytes: ByteArray, maxOutputBytes: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(bytes)
        val output = ByteArrayOutputStream(minOf(maxOf(bytes.size * 4, 16 * 1024), maxOutputBytes))
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished() && output.size() < maxOutputBytes) {
            val read = inflater.inflate(buffer, 0, minOf(buffer.size, maxOutputBytes - output.size()))
            if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    } finally {
        inflater.end()
    }
}
