package com.nuvio.app.features.kitsu

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
import kotlin.time.Instant

internal const val KITSU_STATUS_SELECTION_GROUP = "kitsu:status"

internal data class KitsuLibraryStatusDefinition(
    val status: KitsuListStatus,
    val key: String,
    val title: String,
)

internal val kitsuLibraryStatusDefinitions = listOf(
    KitsuLibraryStatusDefinition(KitsuListStatus.CURRENT, "kitsu:status:current", "Watching"),
    KitsuLibraryStatusDefinition(KitsuListStatus.PLANNED, "kitsu:status:planned", "Plan to Watch"),
    KitsuLibraryStatusDefinition(KitsuListStatus.ON_HOLD, "kitsu:status:on_hold", "On Hold"),
    KitsuLibraryStatusDefinition(KitsuListStatus.COMPLETED, "kitsu:status:completed", "Completed"),
    KitsuLibraryStatusDefinition(KitsuListStatus.DROPPED, "kitsu:status:dropped", "Dropped"),
)

/** True for the library section keys of the Kitsu lists (one per status). */
fun isKitsuLibrarySection(sectionType: String?): Boolean =
    sectionType?.startsWith("kitsu:status:") == true

/** Translates between Kitsu anime IDs and the content IDs used by Nuvio catalogs and the library. */
internal object KitsuLibraryResolver {
    suspend fun kitsuId(ids: TrackingExternalIds): Long? =
        ids.kitsu ?: AnimeIdMapper.index()?.primaryEntry(ids)?.kitsu

    /** Same as [kitsuId] without loading the index; null until the index is in memory. */
    fun kitsuIdIfReady(ids: TrackingExternalIds): Long? =
        ids.kitsu ?: AnimeIdMapper.currentIndex()?.primaryEntry(ids)?.kitsu

    /** Content ID of a Kitsu entry in the library, following the anime ID preference shared with Simkl. */
    fun contentId(kitsuId: Long, index: AnimeIdIndex?, preference: SimklAnimeIdPreference): String {
        val mapping = index?.byKitsu(kitsuId)
        val fallback = "kitsu:$kitsuId"
        return when (preference) {
            SimklAnimeIdPreference.KITSU -> fallback
            SimklAnimeIdPreference.MAL -> mapping?.mal?.let { "mal:$it" } ?: fallback
            SimklAnimeIdPreference.TVDB -> mapping?.tvdb?.let { "tvdb:$it" } ?: fallback
            SimklAnimeIdPreference.IMDB -> mapping?.imdb?.firstOrNull()
                ?: mapping?.tvdb?.let { "tvdb:$it" }
                ?: fallback
        }
    }

    /** Every content ID a Kitsu entry can be reached with, for library lookups by catalog ID. */
    fun alternateContentIds(kitsuId: Long, index: AnimeIdIndex?): Set<String> = buildSet {
        add("kitsu:$kitsuId")
        val mapping = index?.byKitsu(kitsuId) ?: return@buildSet
        mapping.mal?.let { add("mal:$it") }
        mapping.anilist?.let { add("anilist:$it") }
        mapping.tvdb?.let { add("tvdb:$it") }
        mapping.tmdbTv?.let { add("tmdb:$it") }
        mapping.tmdbMovie?.let { add("tmdb:$it") }
        mapping.imdb.forEach { add(it.lowercase()) }
    }
}

internal data class KitsuLibraryProjection(
    val items: List<LibraryItem>,
    val sections: List<LibrarySection>,
    /** Catalog IDs (lowercase) of every listed anime, pointing to its Kitsu id. */
    val kitsuIdsByContentId: Map<String, Long>,
)

/** Projects the Kitsu library into library sections, one per status, newest update first. */
internal fun projectKitsuLibrary(
    entries: List<KitsuListEntry>,
    index: AnimeIdIndex?,
    preference: SimklAnimeIdPreference,
): KitsuLibraryProjection {
    val kitsuIdsByContentId = linkedMapOf<String, Long>()
    val sections = kitsuLibraryStatusDefinitions.mapNotNull { definition ->
        val items = entries
            .asSequence()
            .filter { entry -> entry.status == definition.status }
            .map { entry ->
                val contentId = KitsuLibraryResolver.contentId(entry.kitsuId, index, preference)
                KitsuLibraryResolver.alternateContentIds(entry.kitsuId, index)
                    .plus(contentId.lowercase())
                    .forEach { id -> kitsuIdsByContentId.putIfAbsent(id, entry.kitsuId) }
                entry.toLibraryItem(contentId, definition.key)
            }
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs)
            .toList()
        items.takeIf(List<LibraryItem>::isNotEmpty)?.let {
            LibrarySection(type = definition.key, displayTitle = definition.title, items = items)
        }
    }
    return KitsuLibraryProjection(
        items = sections
            .flatMap(LibrarySection::items)
            .distinctBy { item -> "${item.type}:${item.id}" }
            .sortedByDescending(LibraryItem::savedAtEpochMs),
        sections = sections,
        kitsuIdsByContentId = kitsuIdsByContentId,
    )
}

private fun KitsuListEntry.toLibraryItem(contentId: String, listKey: String): LibraryItem =
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
        trackingProviderId = TrackingProviderId.KITSU.storageId,
        trackingProviderItemId = "kitsu:$kitsuId",
        trackingSourceUrl = "https://kitsu.app/anime/$kitsuId",
        savedAtEpochMs = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L,
    )

@OptIn(ExperimentalCoroutinesApi::class)
object KitsuTrackingLibraryProvider : TrackingLibraryProvider {
    private val log = Logger.withTag("KitsuLibrary")
    override val providerId: TrackingProviderId = TrackingProviderId.KITSU
    // The anime index re-projects the content IDs once the lists are loaded.
    override val changes: Flow<Unit> = combine(KitsuListRepository.uiState, AnimeIdMapper.indexState) { _, _ -> }

    /** The Kitsu lists as library sections for the library "Kitsu" and "All" views; empty while disconnected. */
    val libraryState: Flow<LibraryUiState> = KitsuAuthRepository.isAuthenticated
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
    private var cachedKey: Triple<List<KitsuListEntry>, AnimeIdIndex?, SimklAnimeIdPreference>? = null
    private var cachedProjection: KitsuLibraryProjection? = null

    override fun ensureLoaded() {
        KitsuAuthRepository.ensureLoaded()
        KitsuListRepository.ensureLoaded()
        AnimeIdMapper.ensureLoadedAsync()
    }

    override fun prepare() {
        ensureLoaded()
        KitsuListRepository.refreshAsync(TrackingRefreshIntent.AUTOMATIC)
    }

    override fun onProfileChanged() = KitsuListRepository.onProfileChanged()

    override fun clearLocalState() = KitsuListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) {
        AnimeIdMapper.index()
        KitsuListRepository.refresh(intent)
    }

    override fun snapshot(): TrackingLibrarySnapshot {
        ensureLoaded()
        val state = KitsuListRepository.uiState.value
        val projection = projection()
        return TrackingLibrarySnapshot(
            items = projection.items,
            sections = projection.sections,
            tabs = kitsuLibraryStatusDefinitions.map { definition ->
                TrackingLibraryTab(
                    key = definition.key,
                    title = definition.title,
                    providerId = TrackingProviderId.KITSU,
                    kind = if (definition.status == KitsuListStatus.PLANNED) {
                        TrackingLibraryTabKind.WATCHLIST
                    } else {
                        TrackingLibraryTabKind.STATUS
                    },
                    selectionGroup = KITSU_STATUS_SELECTION_GROUP,
                )
            },
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    /** Kitsu lists only anime here: its tabs are offered for anime items only. */
    override fun supportsItem(item: LibraryItem): Boolean {
        if (item.mediaCategory.equals("anime", ignoreCase = true)) return true
        val ids = item.externalIds()
        if (trackingMediaKind(item.type, ids) == TrackingMediaKind.ANIME) return true
        if (projection().kitsuIdsByContentId.containsKey(item.id.lowercase())) return true
        AnimeIdMapper.ensureLoadedAsync()
        return AnimeIdMapper.currentIndex()?.isKnownAnime(ids) == true
    }

    override fun contains(contentId: String, contentType: String?): Boolean =
        projection().kitsuIdsByContentId.containsKey(contentId.lowercase())

    override fun find(contentId: String): LibraryItem? {
        val projection = projection()
        projection.items.firstOrNull { it.id.equals(contentId, ignoreCase = true) }?.let { return it }
        val kitsuId = projection.kitsuIdsByContentId[contentId.lowercase()] ?: return null
        return projection.items.firstOrNull { it.trackingProviderItemId == "kitsu:$kitsuId" }
    }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val status = listedKitsuId(item)?.let(KitsuListRepository::entry)?.status
        return kitsuLibraryStatusDefinitions.associate { definition -> definition.key to (definition.status == status) }
    }

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        currentMembership.mapValues { false }.toMutableMap().apply {
            if (currentMembership.values.none { it }) {
                this[kitsuLibraryStatusDefinitions.first { it.status == KitsuListStatus.PLANNED }.key] = true
            }
        }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        if (profileId != ProfileRepository.activeProfileId) return null
        val desired = kitsuLibraryStatusDefinitions.filter { desiredMembership[it.key] == true }
        require(desired.size <= 1) { "A Kitsu anime can have only one library status" }
        val kitsuId = listedKitsuId(item) ?: KitsuLibraryResolver.kitsuId(item.externalIds())
            ?: error("${item.name} was not found on Kitsu")
        val current = KitsuListRepository.entry(kitsuId)?.status
        val target = desired.singleOrNull()?.status
        if (target == current) return null
        log.i { "Kitsu library membership of $kitsuId: $current -> $target" }
        if (target == null) KitsuListRepository.remove(kitsuId) else KitsuListRepository.setStatus(kitsuId, target)
        return null
    }

    private fun listedKitsuId(item: LibraryItem): Long? =
        item.trackingProviderItemId?.takeIf { it.startsWith("kitsu:") }?.removePrefix("kitsu:")?.toLongOrNull()
            ?: projection().kitsuIdsByContentId[item.id.lowercase()]
            ?: KitsuLibraryResolver.kitsuIdIfReady(item.externalIds())

    private fun projection(): KitsuLibraryProjection = synchronized(projectionLock) {
        val key = Triple(
            KitsuListRepository.uiState.value.entries,
            AnimeIdMapper.currentIndex(),
            TrackingSettingsRepository.uiState.value.simklAnimeIdPreference,
        )
        cachedProjection?.takeIf { cachedKey?.let { it.first === key.first && it.second === key.second && it.third == key.third } == true }
            ?.let { return@synchronized it }
        projectKitsuLibrary(key.first, key.second, key.third).also { projection ->
            cachedKey = key
            cachedProjection = projection
        }
    }
}
