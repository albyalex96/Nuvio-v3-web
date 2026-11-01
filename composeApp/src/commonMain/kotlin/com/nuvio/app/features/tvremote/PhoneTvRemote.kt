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
) {
    val canControl: Boolean
        get() = connection == PhoneTvRemoteConnection.Connected && playback != null
}

interface PhoneTvRemoteClient {
    val state: StateFlow<PhoneTvRemoteUiState>

    /** Pairs with the TV of a scanned QR code (replacing the paired TV, if any). */
    fun pair(scannedCode: String)

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

    /** QR scanner launcher; the callback receives the scanned text (null when cancelled). */
    @Composable
    fun rememberQrScanner(onResult: (String?) -> Unit): () -> Unit
}

object PhoneTvRemote {
    /** Set at startup by the Android full distribution. */
    var client: PhoneTvRemoteClient? = null

    /** The phone remote is offered: Android full distribution, phone/tablet app (not the TV app). */
    val activeClient: PhoneTvRemoteClient?
        get() = client?.takeIf { !TvAppRuntime.isTvApp }
}
