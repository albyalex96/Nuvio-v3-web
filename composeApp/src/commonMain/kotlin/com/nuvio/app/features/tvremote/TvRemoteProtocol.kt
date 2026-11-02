package com.nuvio.app.features.tvremote

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/*
 * Phone -> TV remote protocol (v1), shared by the TV server (`tvApp`) and the phone client
 * (Android full distribution).
 *
 * Transport: plain HTTP on the local network, every body sealed with AES-256-GCM.
 * - Pairing: the TV QR holds a one-use secret; the phone seals its pair request with it and
 *   receives its own random client key (the secret never travels on the network).
 * - Every later request is sealed with the client key and carries the server boot id, a
 *   timestamp and a request id: the TV rejects replays and requests from a previous run.
 * - The playback state only carries display metadata (no stream URLs, headers or credentials).
 */

const val TV_REMOTE_PROTOCOL_VERSION: Int = 1
/** Port the TV server prefers; used when the user types an address without a port. */
const val TV_REMOTE_DEFAULT_PORT: Int = 47_380
const val TV_REMOTE_QR_SCHEME: String = "nuvio-remote"
const val TV_REMOTE_NSD_SERVICE_TYPE: String = "_nuvio-remote._tcp."
const val TV_REMOTE_NSD_ID_ATTRIBUTE: String = "id"
const val TV_REMOTE_NSD_NAME_ATTRIBUTE: String = "n"
const val TV_REMOTE_PAIR_CLIENT_ID: String = "pair"
const val TV_REMOTE_PATH_PAIR: String = "/v1/pair"
const val TV_REMOTE_PATH_STATE: String = "/v1/state"
const val TV_REMOTE_PATH_COMMAND: String = "/v1/command"
const val TV_REMOTE_PATH_FORGET: String = "/v1/forget"

/** Longest a state request waits on the TV for a change. */
const val TV_REMOTE_MAX_WAIT_MS: Long = 20_000L

/** Requests older/newer than this (TV clock) are rejected. */
const val TV_REMOTE_MAX_CLOCK_SKEW_MS: Long = 10 * 60_000L

/** Pairing code shown by the TV under the QR (typed on the phone with the TV address). */
const val TV_REMOTE_PAIRING_CODE_LENGTH: Int = 8

/** No 0/O, 1/I/L: the code is read from the TV screen and typed by hand. */
const val TV_REMOTE_PAIRING_CODE_ALPHABET: String = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** Uppercases and drops spaces/dashes; null when the typed code can not be a pairing code. */
fun normalizeTvRemotePairingCode(raw: String): String? {
    val code = raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()
    if (code.length != TV_REMOTE_PAIRING_CODE_LENGTH) return null
    if (code.any { it !in TV_REMOTE_PAIRING_CODE_ALPHABET }) return null
    return code
}

/** Pairing code as shown on the TV: "ABCD-EFGH". */
fun formatTvRemotePairingCode(code: String): String =
    if (code.length == TV_REMOTE_PAIRING_CODE_LENGTH) "${code.take(4)}-${code.drop(4)}" else code

/** Content of the pairing QR code shown by the TV. */
@Immutable
data class TvRemotePairingInfo(
    val tvId: String,
    val tvName: String,
    val host: String,
    val port: Int,
    /** One-use pairing secret (base64url, 32 bytes). */
    val secret: String,
    val version: Int = TV_REMOTE_PROTOCOL_VERSION,
) {
    fun toUri(): String = buildString {
        append(TV_REMOTE_QR_SCHEME).append("://pair?v=").append(version)
        append("&id=").append(encodeQueryValue(tvId))
        append("&n=").append(encodeQueryValue(tvName))
        append("&h=").append(encodeQueryValue(host))
        append("&p=").append(port)
        append("&s=").append(encodeQueryValue(secret))
    }

    companion object {
        /** Parses a scanned QR code; null when it is not a valid Nuvio TV pairing code. */
        fun parse(raw: String): TvRemotePairingInfo? {
            val text = raw.trim()
            val prefix = "$TV_REMOTE_QR_SCHEME://pair?"
            if (!text.startsWith(prefix, ignoreCase = true)) return null
            val params = text.substring(prefix.length).split('&').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to decodeQueryValue(part.substring(index + 1))
            }.toMap()
            val version = params["v"]?.toIntOrNull() ?: return null
            val tvId = params["id"]?.takeIf { it.isNotBlank() } ?: return null
            val host = params["h"]?.takeIf { it.isNotBlank() } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
            val secret = params["s"]?.takeIf { it.length >= 32 } ?: return null
            return TvRemotePairingInfo(
                tvId = tvId,
                tvName = params["n"].orEmpty().ifBlank { "Android TV" },
                host = host,
                port = port,
                secret = secret,
                version = version,
            )
        }
    }
}

/** HTTP body: sealed JSON payload. [clientId] is [TV_REMOTE_PAIR_CLIENT_ID] while pairing. */
@Serializable
data class TvRemoteEnvelope(
    val clientId: String,
    /** GCM nonce, base64. */
    val iv: String,
    /** Ciphertext + tag, base64. */
    val data: String,
)

@Serializable
data class TvRemotePairRequest(
    val phoneName: String,
    val timestampMs: Long,
    val requestId: String,
)

@Serializable
data class TvRemotePairResponse(
    val clientId: String,
    /** Client key, base64 (32 bytes). */
    val clientKey: String,
    val tvId: String,
    val tvName: String,
)

@Serializable
data class TvRemoteStateRequest(
    val timestampMs: Long,
    val requestId: String,
    /** Revision the phone already has; the TV answers once the state moves past it (or on timeout). */
    val sinceRevision: Long,
    val waitMs: Long,
)

@Serializable
data class TvRemoteStateResponse(
    /** Changes on every TV app start: commands must carry the current one. */
    val bootId: String,
    val revision: Long,
    val tvName: String,
    /** Null while the TV player is closed. */
    val playback: TvRemotePlayback? = null,
    val volume: TvRemoteVolume? = null,
)

@Serializable
enum class TvRemoteCommandType {
    Play,
    Pause,
    TogglePlayback,
    /** Seeks by the TV seek interval ([TvRemoteCommandRequest.forward]). */
    SeekStep,
    /** Seeks to [TvRemoteCommandRequest.positionMs]. */
    SeekTo,
    /** Skips the current intro / recap / outro segment. */
    SkipSegment,
    /** Plays the next episode, searching its stream with the user's stream settings. */
    NextEpisode,
    ToggleSubtitles,
    VolumeUp,
    VolumeDown,
    /** Sets the volume to [TvRemoteCommandRequest.level]. */
    SetVolume,
    ToggleMute,
}

@Serializable
data class TvRemoteCommandRequest(
    val timestampMs: Long,
    val requestId: String,
    val bootId: String,
    val command: TvRemoteCommandType,
    /** Playback the command was meant for: a command for a previous episode is rejected. */
    val sessionKey: String? = null,
    val positionMs: Long? = null,
    val forward: Boolean? = null,
    val level: Int? = null,
)

@Serializable
data class TvRemoteCommandResponse(
    val accepted: Boolean,
    val error: TvRemoteCommandError? = null,
)

@Serializable
enum class TvRemoteCommandError {
    /** The TV app restarted: refresh the state first. */
    StaleBoot,
    /** The playback changed since the command was sent. */
    StaleSession,
    NoPlayback,
    /** The action is not available right now (no next episode, live channel, watch party lock…). */
    Unavailable,
    Duplicate,
}

@Serializable
data class TvRemoteForgetRequest(
    val timestampMs: Long,
    val requestId: String,
)

/** What the TV player is showing: display metadata only. */
@Immutable
@Serializable
data class TvRemotePlayback(
    /** Identifies the playing item: commands carry it back. */
    val sessionKey: String,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    /** HTTPS artwork (episode still, backdrop or poster). */
    val artworkUrl: String? = null,
    val logoUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val isLive: Boolean = false,
    val canSeek: Boolean = true,
    /** Seek step configured on the TV (seconds). */
    val seekIntervalSeconds: Int = 10,
    val subtitlesAvailable: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val subtitleLabel: String? = null,
    /** Type of the skippable segment on screen (`intro`, `recap`, `outro`…); null when none. */
    val skipSegmentType: String? = null,
    val skipToPostCredits: Boolean = false,
    val nextEpisode: TvRemoteNextEpisode? = null,
)

@Immutable
@Serializable
data class TvRemoteNextEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    /** False while the stream search for it is running (or its auto-play countdown). */
    val available: Boolean = true,
)

@Immutable
@Serializable
data class TvRemoteVolume(
    val level: Int,
    val max: Int,
    val muted: Boolean,
)

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

private fun encodeQueryValue(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val char = (byte.toInt() and 0xFF).toChar()
        if (char in UNRESERVED) {
            append(char)
        } else {
            append('%')
            append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0x0F])
            append("0123456789ABCDEF"[byte.toInt() and 0x0F])
        }
    }
}

private fun decodeQueryValue(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (hex != null) {
                bytes += hex.toByte()
                index += 3
                continue
            }
        }
        if (char == '+') {
            bytes += ' '.code.toByte()
        } else {
            char.toString().encodeToByteArray().forEach { bytes += it }
        }
        index += 1
    }
    return bytes.toByteArray().decodeToString()
}
