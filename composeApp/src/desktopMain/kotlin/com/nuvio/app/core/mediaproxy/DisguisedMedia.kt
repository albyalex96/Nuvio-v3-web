package com.nuvio.app.core.mediaproxy

/** Bytes read from a response to look for the real start of a disguised media segment. */
internal const val DISGUISED_MEDIA_SNIFF_BYTES = 256 * 1024

private const val TS_PACKET_BYTES = 188
private const val TS_SYNC_BYTE = 0x47.toByte()
private const val TS_SYNC_PACKETS = 4
private val MP4_SEGMENT_BOXES = listOf("ftyp", "styp", "moof", "sidx")

/** Where the media really starts in a response, and its actual type. */
internal data class MediaStart(val offset: Int, val mimeType: String)

/**
 * Many sites serve HLS segments disguised as pages or images (".html", ".png", text/html…),
 * often with a fake header in front of the MPEG-TS/fMP4 data. Native players skip it (they look
 * for the TS sync byte), the web player of Cast receivers does not: this finds the real start.
 * Null when [head] holds no recognizable media (e.g. an AES-128 encrypted segment).
 */
internal fun findDisguisedMediaStart(head: ByteArray, length: Int = head.size): MediaStart? {
    val lastStart = length - TS_PACKET_BYTES * (TS_SYNC_PACKETS - 1) - 1
    for (offset in 0..lastStart) {
        if ((0 until TS_SYNC_PACKETS).all { head[offset + it * TS_PACKET_BYTES] == TS_SYNC_BYTE }) {
            return MediaStart(offset, "video/mp2t")
        }
    }
    for (offset in 0..length - 8) {
        val type = String(head, offset + 4, 4, Charsets.ISO_8859_1)
        if (type !in MP4_SEGMENT_BOXES) continue
        val size = ((head[offset].toInt() and 0xFF) shl 24) or ((head[offset + 1].toInt() and 0xFF) shl 16) or
            ((head[offset + 2].toInt() and 0xFF) shl 8) or (head[offset + 3].toInt() and 0xFF)
        if (size >= 8) return MediaStart(offset, "video/mp4")
    }
    return null
}

/** Content types that cannot be the real type of a media segment. */
internal fun isDisguisedMediaType(contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return type.isEmpty() || type.startsWith("text/") || type.startsWith("image/") ||
        type == "application/octet-stream" || type == "binary/octet-stream" || type == "application/xhtml+xml"
}
