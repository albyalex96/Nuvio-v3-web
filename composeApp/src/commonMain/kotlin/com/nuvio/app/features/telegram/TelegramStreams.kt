package com.nuvio.app.features.telegram

import co.touchlab.kermit.Logger
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.streams.AddonStreamGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val TELEGRAM_ADDON_ID = "telegram_native"
internal const val TELEGRAM_ADDON_NAME = "Telegram"

private val log = Logger.withTag("TelegramStreams")
private val yearRegex = Regex("""\b(19|20)\d{2}\b""")

/** A Telegram stream group, loaded alongside addons, plugins and CloudStream on the stream screen. */
internal class TelegramStreamSource(
    private val title: String,
    private val year: Int?,
    private val season: Int?,
    private val episode: Int?,
) {
    val addonId: String = TELEGRAM_ADDON_ID

    fun loadingGroup(): AddonStreamGroup =
        AddonStreamGroup(
            addonName = TELEGRAM_ADDON_NAME,
            addonId = TELEGRAM_ADDON_ID,
            streams = emptyList(),
            isLoading = true,
        )

    suspend fun load(): AddonStreamGroup = try {
        val streams = TelegramSourceResolver.resolve(title = title, year = year, season = season, episode = episode)
        log.i { "Telegram streams title='$title' S=$season E=$episode found=${streams.size}" }
        AddonStreamGroup(
            addonName = TELEGRAM_ADDON_NAME,
            addonId = TELEGRAM_ADDON_ID,
            streams = streams,
            isLoading = false,
            error = if (streams.isEmpty()) "No files found" else null,
        )
    } catch (error: Throwable) {
        if (error is CancellationException) currentCoroutineContext().ensureActive()
        log.w(error) { "Telegram source failed title='$title'" }
        AddonStreamGroup(
            addonName = TELEGRAM_ADDON_NAME,
            addonId = TELEGRAM_ADDON_ID,
            streams = emptyList(),
            isLoading = false,
            error = error.message,
        )
    }
}

internal object TelegramStreams {
    fun sources(
        type: String,
        videoId: String,
        parentMetaId: String?,
        season: Int?,
        episode: Int?,
    ): List<TelegramStreamSource> {
        if (!TelegramSourceResolver.isEnabled()) return emptyList()
        val metaLookupId = parentMetaId?.takeIf { it.isNotBlank() } ?: videoId
        val meta = MetaDetailsRepository.peek(type, metaLookupId)
            ?: MetaDetailsRepository.uiState.value.meta?.takeIf { meta ->
                meta.id == metaLookupId || meta.videos.any { it.id == videoId }
            }
        val title = meta?.name?.trim()?.takeIf { it.isNotBlank() }
        if (title == null) {
            log.d { "Skipping Telegram: no title for type=$type id=$videoId" }
            return emptyList()
        }
        val year = meta.releaseInfo?.let { yearRegex.find(it)?.value?.toIntOrNull() }
        val isSeries = season != null && episode != null
        return listOf(
            TelegramStreamSource(
                title = title,
                year = if (isSeries) null else year,
                season = season,
                episode = episode,
            ),
        )
    }
}
