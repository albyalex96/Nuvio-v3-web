package com.nuvio.app.desktop.cast

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** A cast receiver announced in an mDNS answer. */
internal data class MdnsCastRecord(
    val id: String,
    val name: String,
    val model: String?,
    val capabilities: Int?,
    val host: String,
    val port: Int,
)

/**
 * Active "legacy unicast" mDNS query (RFC 6762 §6.7) for `_googlecast._tcp.local`: sent from an
 * ephemeral port, so receivers answer straight to that port. Unlike listening on UDP 5353 (what
 * JmDNS does) the answers pass the Windows firewall, which allows replies to an outgoing packet.
 */
internal object CastMdnsQuery {
    private const val SERVICE = "_googlecast._tcp.local"
    private const val TYPE_A = 1
    private const val TYPE_PTR = 12
    private const val TYPE_TXT = 16
    private const val TYPE_SRV = 33
    private val MDNS_GROUP: InetAddress = InetAddress.getByName("224.0.0.251")

    /** Sends one query from [localAddress] and collects the receivers that answer within [listenMs]. */
    fun query(localAddress: InetAddress, listenMs: Int): List<MdnsCastRecord> {
        val answers = Answers()
        DatagramSocket(InetSocketAddress(localAddress, 0)).use { socket ->
            val packet = buildQuery()
            socket.send(DatagramPacket(packet, packet.size, MDNS_GROUP, 5353))
            socket.soTimeout = 250
            val buffer = ByteArray(9000)
            val deadline = System.currentTimeMillis() + listenMs
            while (System.currentTimeMillis() < deadline) {
                val incoming = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(incoming)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                runCatching { parse(incoming.data.copyOf(incoming.length), answers) }
                answers.sourceFallback = incoming.address.hostAddress
                answers.collect()
            }
        }
        return answers.records.values.toList()
    }

    private fun buildQuery(): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: id 0, flags 0, one question.
        out.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        SERVICE.split('.').forEach { label ->
            val bytes = label.toByteArray()
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, TYPE_PTR.toByte(), 0, 1))
        return out.toByteArray()
    }

    private class Answers {
        val instances = linkedSetOf<String>()
        val srv = mutableMapOf<String, Pair<String, Int>>()
        val txt = mutableMapOf<String, Map<String, String>>()
        val hosts = mutableMapOf<String, String>()
        val records = linkedMapOf<String, MdnsCastRecord>()
        var sourceFallback: String? = null

        fun collect() {
            instances.forEach { instance ->
                val (target, port) = srv[instance] ?: return@forEach
                val props = txt[instance].orEmpty()
                val host = hosts[target.lowercase()] ?: sourceFallback ?: return@forEach
                val id = props["id"] ?: instance
                records[id] = MdnsCastRecord(
                    id = id,
                    name = props["fn"] ?: instance.substringBefore("._googlecast"),
                    model = props["md"],
                    capabilities = props["ca"]?.toIntOrNull(),
                    host = host,
                    port = port,
                )
            }
        }
    }

    private fun parse(data: ByteArray, answers: Answers) {
        fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

        fun readName(start: Int): Pair<String, Int> {
            val labels = mutableListOf<String>()
            var pos = start
            var end = -1
            var jumps = 0
            while (true) {
                val len = data[pos].toInt() and 0xFF
                when {
                    len == 0 -> { pos += 1; break }
                    len and 0xC0 == 0xC0 -> {
                        if (end < 0) end = pos + 2
                        pos = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                        if (++jumps > 32) error("Name pointer loop")
                    }
                    else -> {
                        labels += String(data, pos + 1, len, Charsets.UTF_8)
                        pos += 1 + len
                    }
                }
            }
            return labels.joinToString(".") to (if (end >= 0) end else pos)
        }

        val questions = u16(4)
        val records = u16(6) + u16(8) + u16(10)
        var pos = 12
        repeat(questions) { pos = readName(pos).second + 4 }
        repeat(records) {
            val (name, afterName) = readName(pos)
            val type = u16(afterName)
            val length = u16(afterName + 8)
            val rdata = afterName + 10
            when (type) {
                TYPE_PTR -> if (name.equals(SERVICE, ignoreCase = true)) {
                    answers.instances += readName(rdata).first
                }
                TYPE_SRV -> answers.srv[name] = readName(rdata + 6).first to u16(rdata + 4)
                TYPE_TXT -> {
                    val props = mutableMapOf<String, String>()
                    var at = rdata
                    while (at < rdata + length) {
                        val len = data[at].toInt() and 0xFF
                        val entry = String(data, at + 1, len, Charsets.UTF_8)
                        props[entry.substringBefore('=')] = entry.substringAfter('=', "")
                        at += 1 + len
                    }
                    answers.txt[name] = props
                }
                TYPE_A -> if (length == 4) {
                    answers.hosts[name.lowercase()] =
                        InetAddress.getByAddress(data.copyOfRange(rdata, rdata + 4)).hostAddress
                }
            }
            pos = rdata + length
        }
    }
}
