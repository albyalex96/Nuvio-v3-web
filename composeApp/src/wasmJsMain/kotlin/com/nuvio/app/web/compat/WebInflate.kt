package com.nuvio.app.web.compat

/**
 * Pure Kotlin DEFLATE decoder (RFC 1951) with gzip (RFC 1952) and zlib (RFC 1950)
 * wrappers, used by the web target where zlib / java.util.zip are not available.
 */
internal object WebInflate {
    private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private class Huffman(lengths: IntArray, count: Int) {
        val counts = IntArray(16)
        val symbols = IntArray(count)

        init {
            for (i in 0 until count) counts[lengths[i]]++
            counts[0] = 0
            val offsets = IntArray(16)
            for (i in 1 until 16) offsets[i] = offsets[i - 1] + counts[i - 1]
            for (i in 0 until count) if (lengths[i] != 0) symbols[offsets[lengths[i]]++] = i
        }
    }

    private class Reader(val data: ByteArray, var pos: Int) {
        private var bitBuffer = 0
        private var bitCount = 0

        fun bit(): Int {
            if (bitCount == 0) {
                if (pos >= data.size) throw IllegalStateException("Unexpected end of deflate stream")
                bitBuffer = data[pos++].toInt() and 0xFF
                bitCount = 8
            }
            val b = bitBuffer and 1
            bitBuffer = bitBuffer ushr 1
            bitCount--
            return b
        }

        fun bits(n: Int): Int {
            var value = 0
            for (i in 0 until n) value = value or (bit() shl i)
            return value
        }

        fun alignToByte() {
            bitCount = 0
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1 until 16) {
                code = code or bit()
                val count = h.counts[len]
                if (code - count < first) return h.symbols[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            throw IllegalStateException("Invalid Huffman code")
        }
    }

    private class Output(private val max: Int) {
        var buffer = ByteArray(64 * 1024)
        var size = 0

        fun put(b: Byte) {
            if (size >= max) throw IllegalStateException("Decompressed data exceeds $max bytes")
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = b
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }

    private val fixedLiteral: Huffman by lazy {
        val lengths = IntArray(288) { i -> when { i < 144 -> 8; i < 256 -> 9; i < 280 -> 7; else -> 8 } }
        Huffman(lengths, 288)
    }
    private val fixedDistance: Huffman by lazy { Huffman(IntArray(30) { 5 }, 30) }

    /** Raw DEFLATE data starting at [offset]; returns the output and the end offset of the stream. */
    fun inflateRaw(data: ByteArray, maxOutputBytes: Int, offset: Int = 0): Pair<ByteArray, Int> {
        val reader = Reader(data, offset)
        val out = Output(maxOutputBytes)
        do {
            val last = reader.bit()
            when (reader.bits(2)) {
                0 -> {
                    reader.alignToByte()
                    if (reader.pos + 4 > data.size) throw IllegalStateException("Truncated stored block")
                    val len = (data[reader.pos].toInt() and 0xFF) or ((data[reader.pos + 1].toInt() and 0xFF) shl 8)
                    reader.pos += 4
                    if (reader.pos + len > data.size) throw IllegalStateException("Truncated stored block")
                    for (i in 0 until len) out.put(data[reader.pos + i])
                    reader.pos += len
                }
                1 -> inflateBlock(reader, out, fixedLiteral, fixedDistance)
                2 -> {
                    val hlit = reader.bits(5) + 257
                    val hdist = reader.bits(5) + 1
                    val hclen = reader.bits(4) + 4
                    val codeLengths = IntArray(19)
                    for (i in 0 until hclen) codeLengths[CODE_LENGTH_ORDER[i]] = reader.bits(3)
                    val codeHuffman = Huffman(codeLengths, 19)
                    val lengths = IntArray(hlit + hdist)
                    var i = 0
                    while (i < hlit + hdist) {
                        val symbol = reader.decode(codeHuffman)
                        when {
                            symbol < 16 -> lengths[i++] = symbol
                            symbol == 16 -> {
                                if (i == 0) throw IllegalStateException("Invalid repeat")
                                val prev = lengths[i - 1]
                                repeat(3 + reader.bits(2)) { lengths[i++] = prev }
                            }
                            symbol == 17 -> repeat(3 + reader.bits(3)) { lengths[i++] = 0 }
                            else -> repeat(11 + reader.bits(7)) { lengths[i++] = 0 }
                        }
                    }
                    val literal = Huffman(lengths.copyOfRange(0, hlit), hlit)
                    val distance = Huffman(lengths.copyOfRange(hlit, hlit + hdist), hdist)
                    inflateBlock(reader, out, literal, distance)
                }
                else -> throw IllegalStateException("Invalid deflate block type")
            }
        } while (last == 0)
        return out.toByteArray() to reader.pos
    }

    private fun inflateBlock(reader: Reader, out: Output, literal: Huffman, distance: Huffman) {
        while (true) {
            val symbol = reader.decode(literal)
            when {
                symbol < 256 -> out.put(symbol.toByte())
                symbol == 256 -> return
                else -> {
                    val lengthIndex = symbol - 257
                    if (lengthIndex >= LENGTH_BASE.size) throw IllegalStateException("Invalid length symbol")
                    val length = LENGTH_BASE[lengthIndex] + reader.bits(LENGTH_EXTRA[lengthIndex])
                    val distIndex = reader.decode(distance)
                    if (distIndex >= DIST_BASE.size) throw IllegalStateException("Invalid distance symbol")
                    val dist = DIST_BASE[distIndex] + reader.bits(DIST_EXTRA[distIndex])
                    if (dist > out.size) throw IllegalStateException("Invalid distance")
                    for (k in 0 until length) out.put(out.buffer[out.size - dist])
                }
            }
        }
    }

    /** gzip (possibly multi-member), zlib or raw deflate, detected from the header. */
    fun gunzip(data: ByteArray, maxOutputBytes: Int): ByteArray {
        if (data.size >= 2 && (data[0].toInt() and 0xFF) == 0x1F && (data[1].toInt() and 0xFF) == 0x8B) {
            var pos = 0
            var result = ByteArray(0)
            while (pos + 10 <= data.size && (data[pos].toInt() and 0xFF) == 0x1F && (data[pos + 1].toInt() and 0xFF) == 0x8B) {
                val flags = data[pos + 3].toInt() and 0xFF
                pos += 10
                if (flags and 0x04 != 0) {
                    val xlen = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
                    pos += 2 + xlen
                }
                if (flags and 0x08 != 0) { while (data[pos].toInt() != 0) pos++; pos++ }
                if (flags and 0x10 != 0) { while (data[pos].toInt() != 0) pos++; pos++ }
                if (flags and 0x02 != 0) pos += 2
                val (chunk, end) = inflateRaw(data, maxOutputBytes - result.size, pos)
                result += chunk
                pos = end + 8 // CRC32 + ISIZE
            }
            return result
        }
        val first = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return data
        val second = data.getOrNull(1)?.toInt()?.and(0xFF) ?: return data
        if (first and 0x0F == 8 && ((first shl 8) or second) % 31 == 0) {
            return inflateRaw(data, maxOutputBytes, 2).first
        }
        return inflateRaw(data, maxOutputBytes).first
    }
}
