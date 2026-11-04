package com.nuvio.app.features.cast

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.desktop.cast.CastChannel
import com.nuvio.app.desktop.cast.CastMessage
import com.nuvio.app.desktop.cast.DEFAULT_MEDIA_RECEIVER
import com.nuvio.app.desktop.cast.NS_RECEIVER
import com.nuvio.app.desktop.cast.string
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.concurrent.thread

/** Connected receiver running the Default Media Receiver, used by the cast player surface. */
internal class DesktopCastSession(
    val channel: CastChannel,
    val transportId: String,
    val sessionId: String,
    val deviceName: String,
)

/**
 * Google Cast on desktop through the open Cast v2 protocol: receivers are found with mDNS
 * (`_googlecast._tcp`) and driven over their TLS channel with the Default Media Receiver.
 */
internal actual object CastPlatform {
    private val log = Logger.withTag("CastSession")
    private const val SERVICE_TYPE = "_googlecast._tcp.local."

    private val _state = MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    actual val state: StateFlow<CastSessionState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    actual val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private data class Receiver(val device: CastDevice, val host: String, val port: Int)

    private val receivers = ConcurrentHashMap<String, Receiver>()
    private var discoveryClients = 0
    private var jmdns: JmDNS? = null

    @Volatile
    private var session: DesktopCastSession? = null

    internal fun currentSession(): DesktopCastSession? = session?.takeIf { it.channel.isOpen }

    @Synchronized
    actual fun startDiscovery() {
        discoveryClients += 1
        if (discoveryClients > 1) return
        thread(name = "nuvio-cast-discovery", isDaemon = true) {
            runCatching {
                val address = lanInterfaceAddress()
                log.i { "Searching for cast receivers on ${address?.hostAddress ?: "default interface"}" }
                val dns = if (address != null) JmDNS.create(address) else JmDNS.create()
                synchronized(this) {
                    if (discoveryClients == 0) {
                        dns.close()
                        return@thread
                    }
                    jmdns = dns
                }
                dns.addServiceListener(SERVICE_TYPE, serviceListener)
            }.onFailure { error -> log.w(error) { "Cast discovery could not start" } }
        }
    }

    @Synchronized
    actual fun stopDiscovery() {
        discoveryClients = (discoveryClients - 1).coerceAtLeast(0)
        if (discoveryClients > 0) return
        val dns = jmdns
        jmdns = null
        receivers.clear()
        publishDevices()
        if (dns != null) thread(name = "nuvio-cast-discovery-stop", isDaemon = true) { runCatching { dns.close() } }
    }

    actual fun selectDevice(deviceId: String) {
        val receiver = receivers[deviceId]
        if (receiver == null) {
            log.w { "Selected device is no longer available" }
            return
        }
        session?.let { endSession(it, stopApp = true) }
        _state.value = CastSessionState.Connecting(receiver.device.name)
        markConnecting(deviceId, true)
        thread(name = "nuvio-cast-connect", isDaemon = true) {
            try {
                log.i { "Connecting to ${receiver.device.name}" }
                val channel = CastChannel.open(receiver.host, receiver.port)
                launchReceiver(channel, receiver.device.name)
            } catch (error: Exception) {
                log.w(error) { "Could not connect to ${receiver.device.name}" }
                _state.value = CastSessionState.Idle
            } finally {
                markConnecting(deviceId, false)
            }
        }
    }

    actual fun disconnect() {
        val current = session ?: return
        log.i { "Ending cast session" }
        endSession(current, stopApp = true)
    }

    actual fun canServeLocalMedia(): Boolean = LanMediaProxy.lanAddress() != null

    private fun launchReceiver(channel: CastChannel, deviceName: String) {
        var launched = false
        val requestId = channel.newRequestId()
        val listener: (CastMessage) -> Unit = listener@{ message ->
            if (message.namespace != NS_RECEIVER || message.type != "RECEIVER_STATUS") return@listener
            val app = message.payload.receiverApp()
            val current = session
            when {
                app != null && current == null && !launched -> {
                    launched = true
                    val transportId = app.string("transportId") ?: return@listener
                    val sessionId = app.string("sessionId") ?: return@listener
                    channel.connectTo(transportId)
                    session = DesktopCastSession(channel, transportId, sessionId, deviceName)
                    log.i { "Session started on $deviceName" }
                    _state.value = CastSessionState.Connected(deviceName = deviceName, sessionId = sessionId)
                }
                app == null && current != null && current.channel === channel -> {
                    // The receiver app was closed (from the TV, another sender or its own timeout).
                    log.i { "Receiver app stopped on $deviceName" }
                    endSession(current, stopApp = false)
                }
            }
        }
        channel.addListener(listener)
        channel.onClosed = {
            val current = session
            if (current == null || current.channel === channel) {
                session = null
                if (_state.value !is CastSessionState.Idle) log.i { "Cast connection closed" }
                _state.value = CastSessionState.Idle
            }
        }
        channel.send(
            NS_RECEIVER,
            CastChannel.RECEIVER_ID,
            buildJsonObject {
                put("type", "LAUNCH")
                put("requestId", requestId)
                put("appId", DEFAULT_MEDIA_RECEIVER)
            },
        )
    }

    /** Receiver mute, for the player's mute button. */
    internal fun setMuted(muted: Boolean) {
        val current = currentSession() ?: return
        current.channel.send(
            NS_RECEIVER,
            CastChannel.RECEIVER_ID,
            buildJsonObject {
                put("type", "SET_VOLUME")
                put("requestId", current.channel.newRequestId())
                putJsonObject("volume") { put("muted", muted) }
            },
        )
    }

    private fun endSession(current: DesktopCastSession, stopApp: Boolean) {
        session = null
        if (stopApp) {
            current.channel.send(
                NS_RECEIVER,
                CastChannel.RECEIVER_ID,
                buildJsonObject {
                    put("type", "STOP")
                    put("requestId", current.channel.newRequestId())
                    put("sessionId", current.sessionId)
                },
            )
        }
        current.channel.close()
        _state.value = CastSessionState.Idle
    }

    private fun JsonObject.receiverApp(): JsonObject? =
        ((this["status"] as? JsonObject)?.get("applications") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("appId") == DEFAULT_MEDIA_RECEIVER }

    private fun markConnecting(deviceId: String, connecting: Boolean) {
        receivers.computeIfPresent(deviceId) { _, receiver ->
            receiver.copy(device = receiver.device.copy(isConnecting = connecting))
        }
        publishDevices()
    }

    private fun publishDevices() {
        _devices.update { receivers.values.map { it.device }.sortedBy { it.name.lowercase() } }
    }

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            event.dns.requestServiceInfo(event.type, event.name, 3_000)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val id = event.info?.getPropertyString("id") ?: event.name
            if (receivers.remove(id) != null) publishDevices()
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: info.hostAddresses.firstOrNull() ?: return
            val id = info.getPropertyString("id") ?: info.name
            // Cast groups and speakers without video ("ca" capability bit 0 = video out) are skipped.
            val capabilities = info.getPropertyString("ca")?.toIntOrNull()
            if (capabilities != null && capabilities and 1 == 0) return
            val name = info.getPropertyString("fn") ?: info.name
            val model = info.getPropertyString("md")
            if (receivers.containsKey(id)) return
            log.i { "Found cast receiver $name ($model)" }
            receivers[id] = Receiver(CastDevice(id = id, name = name, description = model), host, info.port)
            publishDevices()
        }
    }

    /** IPv4 address of the first active LAN interface (mDNS on the wrong adapter finds nothing). */
    private fun lanInterfaceAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && it.supportsMulticast() }
            .filterNot { iface ->
                val name = "${iface.name} ${iface.displayName}".lowercase()
                listOf("vmware", "virtualbox", "hyper-v", "vethernet", "docker", "wsl", "tap", "tun", "vpn").any { it in name }
            }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrNull()
}
