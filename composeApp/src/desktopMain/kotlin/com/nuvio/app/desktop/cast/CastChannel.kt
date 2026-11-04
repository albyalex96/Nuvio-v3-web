package com.nuvio.app.desktop.cast

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

internal const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
internal const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
internal const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
internal const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
internal const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"

/** One message received on the channel. */
internal data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: JsonObject,
) {
    val type: String? get() = payload["type"]?.jsonPrimitive?.contentOrNull
    val requestId: Int? get() = payload["requestId"]?.jsonPrimitive?.intOrNull
}

/**
 * Open Cast v2 protocol (CASTV2) to a Chromecast: a TLS socket on port 8009 carrying
 * length-prefixed protobuf `CastMessage`s whose payload is JSON. The receiver uses a self-signed
 * certificate, so it is accepted as is (the connection only targets devices found on the LAN).
 */
internal class CastChannel private constructor(private val socket: SSLSocket) {
    private val log = Logger.withTag("CastChannel")
    private val output = DataOutputStream(socket.getOutputStream())
    private val input = DataInputStream(socket.getInputStream())
    private val closed = AtomicBoolean(false)
    private val nextRequestId = AtomicInteger(1)
    private val listeners = CopyOnWriteArrayList<(CastMessage) -> Unit>()
    private val connectedDestinations = ConcurrentHashMap.newKeySet<String>()

    /** Called once when the socket closes for any reason. */
    @Volatile
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = !closed.get()

    fun addListener(listener: (CastMessage) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (CastMessage) -> Unit) {
        listeners -= listener
    }

    fun newRequestId(): Int = nextRequestId.getAndIncrement()

    /** Opens the virtual connection to [destinationId] once (needed before talking to it). */
    fun connectTo(destinationId: String) {
        if (!connectedDestinations.add(destinationId)) return
        send(NS_CONNECTION, destinationId, buildJsonObject { put("type", "CONNECT") })
    }

    fun send(namespace: String, destinationId: String, payload: JsonObject) {
        if (closed.get()) return
        val bytes = encode(SENDER_ID, destinationId, namespace, Json.encodeToString(JsonObject.serializer(), payload))
        try {
            synchronized(output) {
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }
        } catch (error: Exception) {
            log.w(error) { "Send failed on $namespace" }
            close()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        onClosed?.invoke()
    }

    private fun start() {
        thread(name = "nuvio-cast-reader", isDaemon = true) { readLoop() }
        thread(name = "nuvio-cast-heartbeat", isDaemon = true) {
            while (!closed.get()) {
                send(NS_HEARTBEAT, RECEIVER_ID, buildJsonObject { put("type", "PING") })
                runCatching { Thread.sleep(HEARTBEAT_MS) }
            }
        }
    }

    private fun readLoop() {
        try {
            while (!closed.get()) {
                val length = input.readInt()
                if (length <= 0 || length > MAX_MESSAGE_BYTES) error("Invalid message length $length")
                val bytes = ByteArray(length)
                input.readFully(bytes)
                val message = decode(bytes) ?: continue
                if (message.namespace == NS_HEARTBEAT && message.type == "PING") {
                    send(NS_HEARTBEAT, message.sourceId, buildJsonObject { put("type", "PONG") })
                    continue
                }
                if (message.namespace == NS_CONNECTION && message.type == "CLOSE") {
                    connectedDestinations -= message.sourceId
                }
                listeners.forEach { listener -> runCatching { listener(message) }.onFailure { log.e(it) { "Listener failed" } } }
            }
        } catch (error: Exception) {
            if (!closed.get()) log.w { "Connection closed: ${error.message}" }
        } finally {
            close()
        }
    }

    companion object {
        const val SENDER_ID = "sender-nuvio"
        const val RECEIVER_ID = "receiver-0"
        private const val PORT_TIMEOUT_MS = 8_000
        private const val HEARTBEAT_MS = 5_000L
        private const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024

        fun open(host: String, port: Int): CastChannel {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val context = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
            val socket = context.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), PORT_TIMEOUT_MS)
            socket.startHandshake()
            return CastChannel(socket).also { channel ->
                channel.start()
                channel.connectTo(RECEIVER_ID)
            }
        }

        // ---- protobuf CastMessage (extensions/api/cast_channel/cast_channel.proto) ----------

        private fun encode(source: String, destination: String, namespace: String, payload: String): ByteArray {
            val out = ByteArrayOutputStream()
            fun varint(value: Long) {
                var v = value
                while (v and 0x7F.inv().toLong() != 0L) {
                    out.write(((v and 0x7F) or 0x80).toInt())
                    v = v ushr 7
                }
                out.write(v.toInt())
            }
            fun string(field: Int, value: String) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                varint(((field shl 3) or 2).toLong())
                varint(bytes.size.toLong())
                out.write(bytes)
            }
            varint((1 shl 3).toLong()); varint(0) // protocol_version = CASTV2_1_0
            string(2, source)
            string(3, destination)
            string(4, namespace)
            varint((5 shl 3).toLong()); varint(0) // payload_type = STRING
            string(6, payload)
            return out.toByteArray()
        }

        private fun decode(bytes: ByteArray): CastMessage? {
            var index = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val b = bytes[index++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            val strings = HashMap<Int, String>()
            while (index < bytes.size) {
                val key = varint().toInt()
                val field = key ushr 3
                when (key and 7) {
                    0 -> varint()
                    2 -> {
                        val length = varint().toInt()
                        strings[field] = String(bytes, index, length, Charsets.UTF_8)
                        index += length
                    }
                    else -> return null
                }
            }
            val payload = strings[6] ?: return null // binary payloads are not used by these namespaces
            val json = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            return CastMessage(
                sourceId = strings[2].orEmpty(),
                destinationId = strings[3].orEmpty(),
                namespace = strings[4].orEmpty(),
                payload = json,
            )
        }
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
