package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anime.AnimeIdIndex
import com.nuvio.app.features.anime.AnimeIdMapper
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.mal.externalIds
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.simkl.SimklAnimeIdPreference
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.trackingMediaKind
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal const val ANILIST_STATUS_SELECTION_GROUP = "anilist:status"

internal data class AniListLibraryStatusDefinition(
    val status: AniListListStatus,
    val key: String,
    val title: String,
)

internal val aniListLibraryStatusDefinitions = listOf(
    AniListLibraryStatusDefinition(AniListListStatus.CURRENT, "anilist:status:current", "Watching"),
    AniListLibraryStatusDefinition(AniListListStatus.PLANNING, "anilist:status:planned", "Plan to Watch"),
    AniListLibraryStatusDefinition(AniListListStatus.PAUSED, "anilist:status:on_hold", "On Hold"),
    AniListLibraryStatusDefinition(AniListListStatus.COMPLETED, "anilist:status:completed", "Completed"),
    AniListLibraryStatusDefinition(AniListListStatus.DROPPED, "anilist:status:dropped", "Dropped"),
)

/** True for the library section keys of the AniList lists (one per status). */
fun isAniListLibrarySection(sectionType: String?): Boolean =
    sectionType?.startsWith("anilist:status:") == true

/** Translates between AniList anime IDs and the content IDs used by Nuvio catalogs and the library. */
internal object AniListLibraryResolver {
    suspend fun aniListId(ids: TrackingExternalIds): Long? =
        ids.anilist ?: AnimeIdMapper.index()?.primaryEntry(ids)?.anilist

    /** Same as [aniListId] without loading the index; null until the index is in memory. */
    fun aniListIdIfReady(ids: TrackingExternalIds): Long? =
        ids.anilist ?: AnimeIdMapper.currentIndex()?.primaryEntry(ids)?.anilist

    /**
     * Content ID of an AniList entry in the library, following the anime ID preference shared with
     * Simkl. Catalogs rarely know `anilist:` IDs, so the MAL ID that AniList returns is the fallback.
     */
    fun contentId(aniListId: Long, malId: Long?, index: AnimeIdIndex?, preference: SimklAnimeIdPreference): String {
        val mapping = index?.byAnilist(aniListId)
        val fallback = (mapping?.mal ?: malId)?.let { "mal:$it" } ?: "anilist:$aniListId"
        return when (preference) {
            SimklAnimeIdPreference.KITSU -> mapping?.kitsu?.let { "kitsu:$it" } ?: fallback
            SimklAnimeIdPreference.MAL -> fallback
            SimklAnimeIdPreference.TVDB -> mapping?.tvdb?.let { "tvdb:$it" } ?: fallback
            SimklAnimeIdPreference.IMDB -> mapping?.imdb?.firstOrNull()
                ?: mapping?.tvdb?.let { "tvdb:$it" }
                ?: fallback
        }
    }

    /** Every content ID an AniList entry can be reached with, for library lookups by catalog ID. */
    fun alternateContentIds(aniListId: Long, malId: Long?, index: AnimeIdIndex?): Set<String> = buildSet {
        malId?.let { add("mal:$it") }
        add("anilist:$aniListId")
        val mapping = index?.byAnilist(aniListId) ?: return@buildSet
        mapping.mal?.let { add("mal:$it") }
        mapping.kitsu?.let { add("kitsu:$it") }
        mapping.tvdb?.let { add("tvdb:$it") }
        mapping.tmdbTv?.let { add("tmdb:$it") }
        mapping.tmdbMovie?.let { add("tmdb:$it") }
        mapping.imdb.forEach { add(it.lowercase()) }
    }
}

internal data class AniListLibraryProjection(
    val items: List<LibraryItem>,
    val sections: List<LibrarySection>,
    /** Catalog IDs (lowercase) of every listed anime, pointing to its AniList id. */
    val aniListIdsByContentId: Map<String, Long>,
)

/** Projects the AniList library into library sections, one per status, newest update first. */
internal fun projectAniListLibrary(
    entries: List<AniListListEntry>,
    index: AnimeIdIndex?,
    preference: SimklAnimeIdPreference,
): AniListLibraryProjection {
    val aniListIdsByContentId = linkedMapOf<String, Long>()
    val sections = aniListLibraryStatusDefinitions.mapNotNull { definition ->
        val items = entries
            .asSequence()
            .filter { entry -> entry.displayStatus == definition.status }
            .map { entry ->
                val contentId = AniListLibraryResolver.contentId(entry.aniListId, entry.malId, index, preference)
                AniListLibraryResolver.alternateContentIds(entry.aniListId, entry.malId, index)
                    .plus(contentId.lowercase())
                    .forEach { id -> aniListIdsByContentId.getOrPut(id) { entry.aniListId } }
                entry.toLibraryItem(contentId, definition.key)
            }
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs)
            .toList()
        items.takeIf(List<LibraryItem>::isNotEmpty)?.let {
            LibrarySection(type = definition.key, displayTitle = definition.title, items = items)
        }
    }
    return AniListLibraryProjection(
        items = sections
            .flatMap(LibrarySection::items)
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs),
        sections = sections,
        aniListIdsByContentId = aniListIdsByContentId,
    )
}

private fun AniListListEntry.toLibraryItem(contentId: String, listKey: String): LibraryItem =
    LibraryItem(
        id = contentId,
        type = if (isMovie) "movie" else "series",
        name = title,
        poster = posterUrl,
        releaseInfo = startYear?.toString(),
        imdbRating = rating?.toString(),
        posterShape = PosterShape.Poster,
        listKeys = setOf(listKey),
        imdbId = contentId.takeIf { it.startsWith("tt") },
        mediaCategory = "anime",
        trackingProviderId = TrackingProviderId.ANILIST.storageId,
        trackingProviderItemId = "anilist:$aniListId",
        trackingSourceUrl = "https://anilist.co/anime/$aniListId",
        savedAtEpochMs = (updatedAtEpochSeconds ?: 0L) * 1_000L,
    )

@OptIn(ExperimentalCoroutinesApi::class)
object AniListTrackingLibraryProvider : TrackingLibraryProvider {
    private val log = Logger.withTag("AniListLibrary")
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST
    // The anime index re-projects the content IDs once the lists are loaded.
    override val changes: Flow<Unit> = combine(AniListListRepository.uiState, AnimeIdMapper.indexState) { _, _ -> }

    /** The AniList lists as library sections for the library "AniList" and "All" views; empty while disconnected. */
    val libraryState: Flow<LibraryUiState> = AniListAuthRepository.isAuthenticated
        .flatMapLatest { connected ->
            if (!connected) {
                flowOf(LibraryUiState(sourceMode = LibrarySourceMode.LOCAL, isLoaded = true))
            } else {
                changes.map {
                    val snapshot = snapshot()
                    LibraryUiState(
                        sourceMode = LibrarySourceMode.LOCAL,
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
    private var cachedKey: Triple<List<AniListListEntry>, AnimeIdIndex?, SimklAnimeIdPreference>? = null
    private var cachedProjection: AniListLibraryProjection? = null

    override fun ensureLoaded() {
        AniListAuthRepository.ensureLoaded()
        AniListListRepository.ensureLoaded()
        AnimeIdMapper.ensureLoadedAsync()
    }

    override fun prepare() {
        ensureLoaded()
        AniListListRepository.refreshAsync(TrackingRefreshIntent.AUTOMATIC)
    }

    override fun onProfileChanged() = AniListListRepository.onProfileChanged()

    override fun clearLocalState() = AniListListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) {
        AnimeIdMapper.index()
        AniListListRepository.refresh(intent)
    }

    override fun snapshot(): TrackingLibrarySnapshot {
        ensureLoaded()
        val state = AniListListRepository.uiState.value
        val projection = projection()
        return TrackingLibrarySnapshot(
            items = projection.items,
            sections = projection.sections,
            tabs = aniListLibraryStatusDefinitions.map { definition ->
                TrackingLibraryTab(
                    key = definition.key,
                    title = definition.title,
                    providerId = TrackingProviderId.ANILIST,
                    kind = if (definition.status == AniListListStatus.PLANNING) {
                        TrackingLibraryTabKind.WATCHLIST
                    } else {
                        TrackingLibraryTabKind.STATUS
                    },
                    selectionGroup = ANILIST_STATUS_SELECTION_GROUP,
                )
            },
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    /** AniList lists only anime here: its tabs are offered for anime items only. */
    override fun supportsItem(item: LibraryItem): Boolean {
        if (item.mediaCategory.equals("anime", ignoreCase = true)) return true
        val ids = item.externalIds()
        if (trackingMediaKind(item.type, ids) == TrackingMediaKind.ANIME) return true
        if (projection().aniListIdsByContentId.containsKey(item.id.lowercase())) return true
        AnimeIdMapper.ensureLoadedAsync()
        return AnimeIdMapper.currentIndex()?.isKnownAnime(ids) == true
    }

    override fun contains(contentId: String, contentType: String?): Boolean =
        projection().aniListIdsByContentId.containsKey(contentId.lowercase())

    override fun find(contentId: String): LibraryItem? {
        val projection = projection()
        projection.items.firstOrNull { it.id.equals(contentId, ignoreCase = true) }?.let { return it }
        val aniListId = projection.aniListIdsByContentId[contentId.lowercase()] ?: return null
        return projection.items.firstOrNull { it.trackingProviderItemId == "anilist:$aniListId" }
    }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val status = listedAniListId(item)?.let(AniListListRepository::entry)?.displayStatus
        return aniListLibraryStatusDefinitions.associate { definition -> definition.key to (definition.status == status) }
    }

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        currentMembership.mapValues { false }.toMutableMap().apply {
            if (currentMembership.values.none { it }) {
                this[aniListLibraryStatusDefinitions.first { it.status == AniListListStatus.PLANNING }.key] = true
            }
        }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        if (profileId != ProfileRepository.activeProfileId) return null
        val desired = aniListLibraryStatusDefinitions.filter { desiredMembership[it.key] == true }
        require(desired.size <= 1) { "An AniList anime can have only one library status" }
        val aniListId = listedAniListId(item) ?: AniListLibraryResolver.aniListId(item.externalIds())
            ?: error("${item.name} was not found on AniList")
        val current = AniListListRepository.entry(aniListId)?.displayStatus
        val target = desired.singleOrNull()?.status
        if (target == current) return null
        log.i { "AniList library membership of $aniListId: $current -> $target" }
        if (target == null) AniListListRepository.remove(aniListId) else AniListListRepository.setStatus(aniListId, target)
        return null
    }

    private fun listedAniListId(item: LibraryItem): Long? =
        item.trackingProviderItemId?.takeIf { it.startsWith("anilist:") }?.removePrefix("anilist:")?.toLongOrNull()
            ?: projection().aniListIdsByContentId[item.id.lowercase()]
            ?: AniListLibraryResolver.aniListIdIfReady(item.externalIds())

    private fun projection(): AniListLibraryProjection = synchronized(projectionLock) {
        val key = Triple(
            AniListListRepository.uiState.value.entries,
            AnimeIdMapper.currentIndex(),
            TrackingSettingsRepository.uiState.value.simklAnimeIdPreference,
        )
        cachedProjection?.takeIf { cachedKey?.let { it.first === key.first && it.second === key.second && it.third == key.third } == true }
            ?.let { return@synchronized it }
        projectAniListLibrary(key.first, key.second, key.third).also { projection ->
            cachedKey = key
            cachedProjection = projection
        }
    }
}
