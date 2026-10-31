package com.nuvio.app.features.telegram

import co.touchlab.kermit.Logger
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

internal actual object TelegramSourceResolver {
    private val log = Logger.withTag("Telegram")
    private const val SCORE_THRESHOLD = 55
    private const val SEARCH_TIMEOUT_MS = 20_000L
    private const val MAX_RESULTS = 50

    actual val isSupported: Boolean = true

    actual fun isEnabled(): Boolean = TelegramRepository.isAuthenticated()

    actual suspend fun resolve(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): List<StreamItem> {
        if (!isEnabled()) return emptyList()
        return try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) { resolveInternal(title, year, season, episode) }
                ?: emptyList<StreamItem>().also { log.w { "Telegram search timed out for '$title'" } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.e(e) { "Telegram search failed for '$title'" }
            emptyList()
        }
    }

    private suspend fun resolveInternal(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): List<StreamItem> {
        val queries = if (season != null && episode != null) {
            TelegramSearchMatcher.buildSeriesQueries(title, season, episode)
        } else {
            TelegramSearchMatcher.buildMovieQueries(title, year)
        }

        val seen = mutableSetOf<Pair<String, Long>>()
        val messages = coroutineScope {
            queries.map { query ->
                async {
                    try {
                        TelegramRepository.searchVideoMessages(query, MAX_RESULTS)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        log.w(e) { "Telegram query failed '$query'" }
                        emptyList()
                    }
                }
            }.awaitAll().flatten().filter { seen.add(it.fileName to it.fileSize) }
        }

        return messages
            .filter { msg ->
                TelegramSearchMatcher.score(
                    fileName = msg.fileName,
                    caption = msg.caption,
                    title = title,
                    year = year,
                    season = season,
                    episode = episode,
                ) >= SCORE_THRESHOLD
            }
            .sortedByDescending { it.fileSize }
            .map { msg ->
                val displayName = if (msg.fileName.startsWith("Default_Name.")) {
                    msg.caption.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: msg.fileName
                } else {
                    msg.fileName
                }
                StreamItem(
                    name = "Telegram\n${parseQuality("${msg.fileName} ${msg.caption}")}",
                    title = displayName,
                    description = listOf(displayName, formatBytes(msg.fileSize))
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                    url = TelegramRepository.getStreamUrl(msg.fileId),
                    addonName = TELEGRAM_ADDON_NAME,
                    addonId = TELEGRAM_ADDON_ID,
                    behaviorHints = StreamBehaviorHints(
                        videoSize = msg.fileSize,
                        filename = msg.fileName,
                    ),
                )
            }
    }

    private fun parseQuality(raw: String): String {
        val t = raw.lowercase().replace(' ', '.')
        fun has(vararg xs: String) = xs.any { it in t }
        return when {
            has("2160", ".4k.", ".uhd.", "ultrahd") -> "4K"
            has("1080", ".fhd.") -> "1080p"
            has("720") -> "720p"
            has("480") -> "480p"
            has("360") -> "360p"
            has("dvdscr", "screener", ".scr.") -> "SCR"
            has(".cam.", "camrip", "hdcam", "hdts", "telesync") -> "CAM"
            else -> "HD"
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    }
}
