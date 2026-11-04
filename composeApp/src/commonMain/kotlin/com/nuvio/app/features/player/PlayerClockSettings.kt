package com.nuvio.app.features.player

import com.nuvio.app.tv.TvAppRuntime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the clock sits in the player controls. */
@Serializable
enum class PlayerClockPosition {
    TopStart,
    TopEnd,
    CenterStart,
    CenterEnd,
    BottomStart,
    BottomEnd,
}

/** Device class with its own clock options: phones and tablets share one copy, TVs another. */
enum class PlayerClockDevice(internal val storageSuffix: String) {
    Mobile("mobile"),
    Tv("tv"),
    Desktop("desktop"),
}

/** Class of this device; the TV app always counts as TV. */
internal fun currentPlayerClockDevice(): PlayerClockDevice =
    when {
        TvAppRuntime.isTvApp -> PlayerClockDevice.Tv
        com.nuvio.app.isDesktop -> PlayerClockDevice.Desktop
        else -> PlayerClockDevice.Mobile
    }

/** Clock options of one device class, synced with the profile so both can be edited anywhere. */
@Serializable
data class PlayerClockSettings(
    val enabled: Boolean = false,
    val use24Hour: Boolean = true,
    val position: PlayerClockPosition = PlayerClockPosition.TopStart,
) {
    fun encode(): String = playerClockJson.encodeToString(serializer(), this)

    companion object {
        private val playerClockJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): PlayerClockSettings? {
            if (raw.isNullOrBlank()) return null
            return runCatching { playerClockJson.decodeFromString(serializer(), raw) }.getOrNull()
        }
    }
}

internal fun defaultPlayerClockSettings(): Map<PlayerClockDevice, PlayerClockSettings> =
    PlayerClockDevice.entries.associateWith { PlayerClockSettings() }

/** "14:05" with [use24Hour], "2:05 PM" otherwise. */
internal fun formatPlayerClockTime(hour: Int, minute: Int, use24Hour: Boolean): String {
    val minutes = minute.toString().padStart(2, '0')
    if (use24Hour) return "${hour.toString().padStart(2, '0')}:$minutes"
    val hour12 = (hour % 12).let { if (it == 0) 12 else it }
    return "$hour12:$minutes ${if (hour < 12) "AM" else "PM"}"
}
