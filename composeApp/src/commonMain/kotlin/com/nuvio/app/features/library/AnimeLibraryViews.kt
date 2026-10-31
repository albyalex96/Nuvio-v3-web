package com.nuvio.app.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.anime.AnimeLibrarySources
import com.nuvio.app.features.settings.IntegrationLogo
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.providerId
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_anime_section_title
import nuvio.composeapp.generated.resources.library_source_kitsu
import nuvio.composeapp.generated.resources.library_source_mal
import nuvio.composeapp.generated.resources.library_status_completed
import nuvio.composeapp.generated.resources.library_status_dropped
import nuvio.composeapp.generated.resources.library_status_on_hold
import nuvio.composeapp.generated.resources.library_status_plan_to_watch
import nuvio.composeapp.generated.resources.library_status_watching
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Chip label and logo of an anime tracker in the library. AniList goes here when it is added. */
internal data class AnimeLibraryChipStyle(
    val label: StringResource,
    val logo: IntegrationLogo,
)

internal fun animeLibraryChipStyle(providerId: TrackingProviderId): AnimeLibraryChipStyle? = when (providerId) {
    TrackingProviderId.MAL -> AnimeLibraryChipStyle(Res.string.library_source_mal, IntegrationLogo.MyAnimeList)
    TrackingProviderId.KITSU -> AnimeLibraryChipStyle(Res.string.library_source_kitsu, IntegrationLogo.Kitsu)
    else -> null
}

/** The lists of one connected anime tracker, for its library chip and for "All". */
internal data class AnimeLibraryView(
    val providerId: TrackingProviderId,
    val state: LibraryUiState,
)

/** The connected anime trackers with their lists; a tracker without login has no chip. */
@Composable
internal fun rememberConnectedAnimeLibraries(): List<AnimeLibraryView> {
    val views = ArrayList<AnimeLibraryView>(AnimeLibrarySources.all.size)
    AnimeLibrarySources.all.forEach { source ->
        val connected by source.isConnected.collectAsStateWithLifecycle()
        val state by remember(source) { source.libraryState }.collectAsStateWithLifecycle(LibraryUiState())
        if (connected) views += AnimeLibraryView(source.providerId, state)
    }
    return views
}

private val animeLibrarySectionTitleResources: Map<String, StringResource> = mapOf(
    "mal:status:watching" to Res.string.library_status_watching,
    "mal:status:plan_to_watch" to Res.string.library_status_plan_to_watch,
    "mal:status:on_hold" to Res.string.library_status_on_hold,
    "mal:status:completed" to Res.string.library_status_completed,
    "mal:status:dropped" to Res.string.library_status_dropped,
    "kitsu:status:current" to Res.string.library_status_watching,
    "kitsu:status:planned" to Res.string.library_status_plan_to_watch,
    "kitsu:status:on_hold" to Res.string.library_status_on_hold,
    "kitsu:status:completed" to Res.string.library_status_completed,
    "kitsu:status:dropped" to Res.string.library_status_dropped,
)

/** Localized title of a library list (a library section or a list picker destination). */
@Composable
internal fun localizedLibraryListTitle(key: String, fallback: String): String =
    animeLibrarySectionTitleResources[key]?.let { stringResource(it) } ?: localizedLibrarySectionTitle(key, fallback)

/** Localized titles of the anime tracker sections. */
internal data class AnimeLibrarySectionTitles(
    val statusTitles: Map<String, String>,
    val providerLabels: Map<TrackingProviderId, String>,
    val titleFormat: String,
) {
    /**
     * Localizes the status sections of the anime trackers. In the "All" view the title also names the
     * tracker, so its lists stay distinct from the ones of the library source.
     */
    fun localize(sections: List<LibrarySection>, withProviderName: Boolean): List<LibrarySection> =
        sections.map { section ->
            val source = AnimeLibrarySources.forSection(section.type) ?: return@map section
            val title = statusTitles[section.type] ?: section.displayTitle
            val providerLabel = providerLabels[source.providerId]
            section.copy(
                displayTitle = if (withProviderName && providerLabel != null) {
                    titleFormat.replace("%1\$s", providerLabel).replace("%2\$s", title)
                } else {
                    title
                },
            )
        }
}

@Composable
internal fun rememberAnimeLibrarySectionTitles(): AnimeLibrarySectionTitles {
    val statusTitles = animeLibrarySectionTitleResources.mapValues { (_, resource) -> stringResource(resource) }
    val providerLabels = AnimeLibrarySources.all.mapNotNull { source ->
        animeLibraryChipStyle(source.providerId)?.let { style -> source.providerId to stringResource(style.label) }
    }.toMap()
    val titleFormat = stringResource(Res.string.library_anime_section_title)
    return remember(statusTitles, providerLabels, titleFormat) {
        AnimeLibrarySectionTitles(statusTitles, providerLabels, titleFormat)
    }
}

/** The library source backed by an anime tracker, so its chip and "All" do not list it twice. */
internal fun LibrarySourceMode.animeProviderId(): TrackingProviderId? =
    providerId?.takeIf { id -> AnimeLibrarySources.byProvider(id) != null }

/**
 * The sections of the "All" view: the library source plus every connected anime tracker that is not
 * already the library source.
 */
internal fun mergeAllLibrarySections(
    saved: LibraryUiState,
    animeLibraries: List<AnimeLibraryView>,
): LibraryUiState {
    val sourceProvider = saved.sourceMode.animeProviderId()
    val extra = animeLibraries.filter { it.providerId != sourceProvider }.map { it.state }
    if (extra.isEmpty()) return saved
    return saved.copy(
        items = saved.items + extra.flatMap { it.items },
        sections = saved.sections + extra.flatMap { it.sections },
    )
}
