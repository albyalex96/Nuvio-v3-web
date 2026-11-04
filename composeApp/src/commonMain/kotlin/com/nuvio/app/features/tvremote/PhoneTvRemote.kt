package com.nuvio.app.features.tvremote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.flow.StateFlow

/*
 * Phone side of the TV remote: state and actions used by the shared UI (settings page, now
 * playing card, control sheet). The network client exists only in the Android full
 * distribution (`PhoneTvRemoteClientImpl`), registered at startup in [PhoneTvRemote.client].
 */

enum class PhoneTvRemoteConnection {
    /** No TV paired, or observation paused (app in background). */
    Idle,
    Connecting,
    Connected,
    /** The TV does not answer (off, other network, Nuvio closed): retried with backoff. */
    Unreachable,
    /** The TV removed this phone: it must be paired again. */
    Revoked,
}

enum class PhoneTvRemotePairingError {
    /** The scanned code is not a Nuvio TV code. */
    InvalidCode,
    /** The code expired or was already used: show a new one on the TV. */
    Expired,
    /** The TV could not be reached (different network?). */
    Unreachable,
    /** The TV refused the request (wrong code or clock difference). */
    Rejected,
    /** The typed pairing code does not match the one shown by the TV. */
    WrongCode,
    Unknown,
}

@Immutable
sealed interface PhoneTvRemotePairing {
    data object Idle : PhoneTvRemotePairing
    data object InProgress : PhoneTvRemotePairing
    data class Failed(val error: PhoneTvRemotePairingError) : PhoneTvRemotePairing
}

@Immutable
data class PhoneTvRemotePairedTv(
    val tvId: String,
    val name: String,
    val host: String,
    val port: Int,
)

/** Nuvio TV found on the local network (its pairing page or a paired phone keeps it visible). */
@Immutable
data class PhoneTvRemoteDiscoveredTv(
    val tvId: String,
    val name: String,
    val host: String,
    val port: Int,
) {
    val address: String get() = if (':' in host) "[$host]:$port" else "$host:$port"
}

@Immutable
data class PhoneTvRemoteUiState(
    val pairedTv: PhoneTvRemotePairedTv? = null,
    val connection: PhoneTvRemoteConnection = PhoneTvRemoteConnection.Idle,
    val pairing: PhoneTvRemotePairing = PhoneTvRemotePairing.Idle,
    /** What the TV is playing; null when its player is closed or the state is too old. */
    val playback: TvRemotePlayback? = null,
    /** TV volume; null when the TV does not let apps change it (HDMI-CEC / fixed volume). */
    val volume: TvRemoteVolume? = null,
    /** Last command refused by the TV (cleared by the next command). */
    val lastCommandError: TvRemoteCommandError? = null,
    /** Media notification, lock screen controls and volume keys while the app is in background. */
    val backgroundControls: Boolean = true,
    /** Last TV address typed by the user ("ip" or "ip:port"), offered again in the address dialog. */
    val manualTvAddress: String = "",
    /** Nuvio TVs found on the network while the settings page is open. */
    val discoveredTvs: List<PhoneTvRemoteDiscoveredTv> = emptyList(),
    val discovering: Boolean = false,
) {
    val canControl: Boolean
        get() = connection == PhoneTvRemoteConnection.Connected && playback != null
}

interface PhoneTvRemoteClient {
    val state: StateFlow<PhoneTvRemoteUiState>

    /** Pairs with the TV of a scanned QR code (replacing the paired TV, if any). */
    fun pair(scannedCode: String)

    /**
     * Pairs with the TV at [address] ("ip" or "ip:port") using the pairing code shown on its
     * screen: the alternative to the QR code (replacing the paired TV, if any).
     */
    fun pairWithCode(address: String, code: String)

    /** Looks for Nuvio TVs on the network while the returned handle is open; call it to stop. */
    fun discover(): () -> Unit

    /** Starts the network search again (list cleared); also retries the paired TV at once. */
    fun refreshDiscovery()

    fun clearPairingError()

    /** Removes the paired TV (and asks the TV to forget this phone when it is reachable). */
    fun forget()

    /** Retries at once after a connection error. */
    fun reconnect()

    /**
     * Keeps the TV state updated while the returned handle is open (screens showing it, app in
     * foreground). Call the returned function to release it.
     */
    fun observe(): () -> Unit

    fun send(
        command: TvRemoteCommandType,
        positionMs: Long? = null,
        forward: Boolean? = null,
        level: Int? = null,
    )

    /** Turns the notification / lock screen controls on or off. */
    fun setBackgroundControls(enabled: Boolean)

    /** Telegram login step of the paired TV; null when the TV can not be reached. */
    suspend fun telegramState(): TvRemoteTelegramState?

    /** Sends a Telegram login step to the paired TV; returns the new TV state (null on failure). */
    suspend fun telegramAction(action: TvRemoteTelegramActionType, value: String? = null): TvRemoteTelegramState?

    /** Full screen QR scanner; [onResult] receives the scanned text (null when closed). */
    @Composable
    fun QrScanner(onResult: (String?) -> Unit)
}

object PhoneTvRemote {
    /** Set at startup by the Android full distribution. */
    var client: PhoneTvRemoteClient? = null

    /** The phone remote is offered: Android full distribution, phone/tablet app (not the TV app). */
    val activeClient: PhoneTvRemoteClient?
        get() = client?.takeIf { !TvAppRuntime.isTvApp }
}

/** TV side: phones paired with the TV remote (set by the TV app), used by the Telegram page. */
object TvRemotePairedPhones {
    val count = kotlinx.coroutines.flow.MutableStateFlow(0)
}

/** Parses "host" or "host:port" (IPv6 as "[::1]:port"); null when it is not valid. */
fun parseManualTvAddress(raw: String, defaultPort: Int): Pair<String, Int>? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val host: String
    val port: Int
    if (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end <= 1) return null
        host = text.substring(1, end)
        val rest = text.substring(end + 1)
        port = when {
            rest.isEmpty() -> defaultPort
            rest.startsWith(":") -> rest.substring(1).toIntOrNull() ?: return null
            else -> return null
        }
    } else if (text.count { it == ':' } == 1) {
        host = text.substringBefore(':')
        port = text.substringAfter(':').toIntOrNull() ?: return null
    } else {
        host = text
        port = defaultPort
    }
    if (host.isBlank() || host.any { it.isWhitespace() || it == '/' }) return null
    if (port !in 1..65_535) return null
    return host to port
}
