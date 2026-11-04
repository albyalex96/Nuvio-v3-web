package com.nuvio.app.features.player.seekthumb

import androidx.compose.ui.graphics.ImageBitmap
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val SEEK_THUMBNAILS_LOG_TAG = "SeekThumbnails"

internal val seekThumbLog = Logger.withTag(SEEK_THUMBNAILS_LOG_TAG)

/** Width of the preview above the seek bar; the picture is decoded at about this size, never larger. */
@Serializable
enum class SeekThumbnailSize(val widthDp: Int) {
    Small(144),
    Medium(192),
    Large(256),
}

/** Device class with its own preview options: phones and tablets share one copy, TVs another. */
enum class SeekThumbnailDevice(internal val storageSuffix: String) {
    Mobile("mobile"),
    Tv("tv"),
    Desktop("desktop"),
}

/** Class of this device; the TV app always counts as TV. */
internal fun currentSeekThumbnailDevice(): SeekThumbnailDevice =
    when {
        TvAppRuntime.isTvApp -> SeekThumbnailDevice.Tv
        com.nuvio.app.isDesktop -> SeekThumbnailDevice.Desktop
        else -> SeekThumbnailDevice.Mobile
    }

/** Preview options of one device class, synced with the profile. */
@Serializable
data class SeekThumbnailOptions(
    val enabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val size: SeekThumbnailSize = SeekThumbnailSize.Medium,
) {
    fun encode(): String = optionsJson.encodeToString(serializer(), this)

    companion object {
        private val optionsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): SeekThumbnailOptions? {
            if (raw.isNullOrBlank()) return null
            return runCatching { optionsJson.decodeFromString(serializer(), raw) }.getOrNull()
        }
    }
}

internal fun defaultSeekThumbnailOptions(): Map<SeekThumbnailDevice, SeekThumbnailOptions> =
    SeekThumbnailDevice.entries.associateWith { SeekThumbnailOptions() }

/** A decoded preview. [positionMs] is where its keyframe really is, which may differ from the asked position. */
class SeekThumbnailFrame(
    val image: ImageBitmap,
    val positionMs: Long,
)

/** The stream a provider makes previews of: the same address and headers the player uses. */
data class SeekThumbnailRequest(
    val url: String,
    val headers: Map<String, String>,
    val streamType: String?,
    val durationMs: Long,
    /** The stream's known response headers (Content-Type tells HLS from a single file). */
    val responseHeaders: Map<String, String> = emptyMap(),
)

data class SeekThumbnailConfig(
    val size: SeekThumbnailSize,
    /** Remote streams get previews only on Wi-Fi or Ethernet; local files always do. */
    val wifiOnly: Boolean,
)

/**
 * Previews of one stream. [request] is cheap and may be called on every scrub frame: only the last asked position is
 * worked on, one at a time, on a background thread. [frame] holds the picture closest to the last asked position.
 */
interface SeekThumbnailProvider {
    val frame: StateFlow<SeekThumbnailFrame?>

    /** False while no picture can come: previews given up for this stream, or waiting for Wi-Fi. */
    val available: Boolean

    fun request(positionMs: Long)

    /** The scrub ended: the shown picture is let go, the decoded ones stay cached for the next scrub. */
    fun clearFrame()

    fun close()
}

/** False where the platform has no preview decoder (the setting is hidden there). */
internal expect val seekThumbnailsSupported: Boolean

/** Null when this stream gets no previews (live, unsupported address, platform without a decoder). */
internal expect fun createSeekThumbnailProvider(
    request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
): SeekThumbnailProvider?
