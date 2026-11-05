package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anime.AnimeIdIndex
import com.nuvio.app.features.anime.AnimeIdMapper
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.simkl.SimklAnimeIdPreference
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.trackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaKind
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal const val MAL_STATUS_SELECTION_GROUP = "mal:status"

internal data class MalLibraryStatusDefinition(
    val status: MalListStatus,
    val key: String,
    val title: String,
)

internal val malLibraryStatusDefinitions = listOf(
    MalLibraryStatusDefinition(MalListStatus.WATCHING, "mal:status:watching", "Watching"),
    MalLibraryStatusDefinition(MalListStatus.PLAN_TO_WATCH, "mal:status:plan_to_watch", "Plan to Watch"),
    MalLibraryStatusDefinition(MalListStatus.ON_HOLD, "mal:status:on_hold", "On Hold"),
    MalLibraryStatusDefinition(MalListStatus.COMPLETED, "mal:status:completed", "Completed"),
    MalLibraryStatusDefinition(MalListStatus.DROPPED, "mal:status:dropped", "Dropped"),
)

/** True for the library section keys of the MAL lists (one per status). */
fun isMalLibrarySection(sectionType: String?): Boolean =
    sectionType?.startsWith("mal:status:") == true

internal fun malLibraryStatusDefinition(status: MalListStatus?): MalLibraryStatusDefinition? =
    malLibraryStatusDefinitions.firstOrNull { it.status == status }

internal data class MalLibraryProjection(
    val items: List<LibraryItem>,
    val sections: List<LibrarySection>,
    /** Catalog IDs (lowercase) of every listed anime, pointing to its MAL id. */
    val malIdsByContentId: Map<String, Long>,
)

/** Projects the MAL list into library sections, one per status, newest update first. */
internal fun projectMalLibrary(
    entries: List<MalListEntry>,
    index: AnimeIdIndex?,
    preference: SimklAnimeIdPreference,
): MalLibraryProjection {
    val malIdsByContentId = linkedMapOf<String, Long>()
    val sections = malLibraryStatusDefinitions.mapNotNull { definition ->
        val items = entries
            .asSequence()
            .filter { entry -> entry.status == definition.status }
            .map { entry ->
                val contentId = MalLibraryResolver.contentId(entry.malId, index, preference)
                MalLibraryResolver.alternateContentIds(entry.malId, index?.byMal(entry.malId))
                    .plus(contentId.lowercase())
                    .forEach { id -> malIdsByContentId.getOrPut(id) { entry.malId } }
                entry.toLibraryItem(contentId, definition.key)
            }
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs)
            .toList()
        items.takeIf(List<LibraryItem>::isNotEmpty)?.let {
            LibrarySection(type = definition.key, displayTitle = definition.title, items = items)
        }
    }
    return MalLibraryProjection(
        items = sections
            .flatMap(LibrarySection::items)
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs),
        sections = sections,
        malIdsByContentId = malIdsByContentId,
    )
}

private fun MalListEntry.toLibraryItem(contentId: String, listKey: String): LibraryItem =
    LibraryItem(
        id = contentId,
        type = if (isMovie) "movie" else "series",
        name = title,
        poster = posterUrl,
        releaseInfo = startYear?.toString(),
        imdbRating = mean?.let { score -> ((score * 10).toInt() / 10.0).toString() },
        posterShape = PosterShape.Poster,
        listKeys = setOf(listKey),
        imdbId = contentId.takeIf { it.startsWith("tt") },
        mediaCategory = "anime",
        trackingProviderId = TrackingProviderId.MAL.storageId,
        trackingProviderItemId = "mal:$malId",
        trackingSourceUrl = "https://myanimelist.net/anime/$malId",
        savedAtEpochMs = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L,
    )

@OptIn(ExperimentalCoroutinesApi::class)
object MalTrackingLibraryProvider : TrackingLibraryProvider {
    private val log = Logger.withTag("MalLibrary")
    override val providerId: TrackingProviderId = TrackingProviderId.MAL
    // The anime index re-projects the content IDs once the lists are loaded.
    override val changes: Flow<Unit> = combine(MalListRepository.uiState, AnimeIdMapper.indexState) { _, _ -> }

    /**
     * The MAL lists as library sections for the library "MAL" and "All" views, whatever the selected
     * library source; empty while MAL is not connected (nothing is loaded or downloaded then).
     */
    val libraryState: Flow<LibraryUiState> = MalAuthRepository.isAuthenticated
        .flatMapLatest { connected ->
            if (!connected) {
                flowOf(LibraryUiState(sourceMode = LibrarySourceMode.MAL, isLoaded = true))
            } else {
                changes.map {
                    val snapshot = snapshot()
                    LibraryUiState(
                        sourceMode = LibrarySourceMode.MAL,
                        items = snapshot.items,
                        sections = snapshot.sections,
                        isLoaded = snapshot.hasLoaded,
                        isLoading = snapshot.isLoading,
                        errorMessage = snapshot.errorMessage,
                    )
                }
            }
        }
        .distinctUntilChanged()
    override val connectionRefreshIntent: TrackingRefreshIntent = TrackingRefreshIntent.INVALIDATED

    private val projectionLock = SynchronizedObject()
    private var cachedKey: Triple<List<MalListEntry>, AnimeIdIndex?, SimklAnimeIdPreference>? = null
    private var cachedProjection: MalLibraryProjection? = null

    override fun ensureLoaded() {
        MalAuthRepository.ensureLoaded()
        MalListRepository.ensureLoaded()
        AnimeIdMapper.ensureLoadedAsync()
    }

    override fun prepare() {
        ensureLoaded()
        MalListRepository.refreshAsync(TrackingRefreshIntent.AUTOMATIC)
    }

    override fun onProfileChanged() = MalListRepository.onProfileChanged()

    override fun clearLocalState() = MalListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) {
        AnimeIdMapper.index()
        MalListRepository.refresh(intent)
    }

    override fun snapshot(): TrackingLibrarySnapshot {
        ensureLoaded()
        val state = MalListRepository.uiState.value
        val projection = projection()
        return TrackingLibrarySnapshot(
            items = projection.items,
            sections = projection.sections,
            tabs = malLibraryStatusDefinitions.map { definition ->
                TrackingLibraryTab(
                    key = definition.key,
                    title = definition.title,
                    providerId = TrackingProviderId.MAL,
                    kind = if (definition.status == MalListStatus.PLAN_TO_WATCH) {
                        TrackingLibraryTabKind.WATCHLIST
                    } else {
                        TrackingLibraryTabKind.STATUS
                    },
                    selectionGroup = MAL_STATUS_SELECTION_GROUP,
                )
            },
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    /** MAL lists only anime: its tabs are offered for anime items only. */
    override fun supportsItem(item: LibraryItem): Boolean {
        if (item.mediaCategory.equals("anime", ignoreCase = true)) return true
        val ids = item.externalIds()
        if (trackingMediaKind(item.type, ids) == TrackingMediaKind.ANIME) return true
        if (projection().malIdsByContentId.containsKey(item.id.lowercase())) return true
        AnimeIdMapper.ensureLoadedAsync()
        return AnimeIdMapper.currentIndex()?.isKnownAnime(ids) == true
    }

    override fun contains(contentId: String, contentType: String?): Boolean =
        projection().malIdsByContentId.containsKey(contentId.lowercase())

    override fun find(contentId: String): LibraryItem? {
        val projection = projection()
        projection.items.firstOrNull { it.id.equals(contentId, ignoreCase = true) }?.let { return it }
        val malId = projection.malIdsByContentId[contentId.lowercase()] ?: return null
        return projection.items.firstOrNull { it.trackingProviderItemId == "mal:$malId" }
    }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val status = listedMalId(item)?.let(MalListRepository::entry)?.status
        return malLibraryStatusDefinitions.associate { definition -> definition.key to (definition.status == status) }
    }

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        currentMembership.mapValues { false }.toMutableMap().apply {
            if (currentMembership.values.none { it }) {
                this[malLibraryStatusDefinition(MalListStatus.PLAN_TO_WATCH)!!.key] = true
            }
        }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        if (profileId != ProfileRepository.activeProfileId) return null
        val desired = malLibraryStatusDefinitions.filter { desiredMembership[it.key] == true }
        require(desired.size <= 1) { "A MyAnimeList anime can have only one list status" }
        val malId = listedMalId(item) ?: MalLibraryResolver.malId(item)
            ?: error("${item.name} was not found on MyAnimeList")
        val current = MalListRepository.entry(malId)?.status
        val target = desired.singleOrNull()?.status
        if (target == current) return null
        log.i { "MAL library membership of $malId: $current -> $target" }
        if (target == null) MalListRepository.remove(malId) else MalListRepository.setStatus(malId, target)
        return null
    }

    private fun listedMalId(item: LibraryItem): Long? =
        item.trackingProviderItemId?.takeIf { it.startsWith("mal:") }?.removePrefix("mal:")?.toLongOrNull()
            ?: projection().malIdsByContentId[item.id.lowercase()]
            ?: MalLibraryResolver.malIdIfReady(item.externalIds())

    private fun projection(): MalLibraryProjection = synchronized(projectionLock) {
        val key = Triple(
            MalListRepository.uiState.value.entries,
            AnimeIdMapper.currentIndex(),
            TrackingSettingsRepository.uiState.value.simklAnimeIdPreference,
        )
        cachedProjection?.takeIf { cachedKey?.let { it.first === key.first && it.second === key.second && it.third == key.third } == true }
            ?.let { return@synchronized it }
        projectMalLibrary(key.first, key.second, key.third).also { projection ->
            cachedKey = key
            cachedProjection = projection
        }
    }
}
