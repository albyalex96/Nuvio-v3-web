package com.nuvio.app.features.wetrakr

import co.touchlab.kermit.Logger
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.mal.externalIds
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Library section (and list picker destination) of the WeTrakr watchlist. */
internal const val WETRAKR_WATCHLIST_KEY = "wetrakr:status:planning"

/** True for the library section keys of the WeTrakr lists. */
fun isWeTrakrLibrarySection(sectionType: String?): Boolean =
    sectionType?.startsWith("wetrakr:") == true

private const val TMDB_POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"

internal data class WeTrakrLibraryProjection(
    val items: List<LibraryItem>,
    val sections: List<LibrarySection>,
    /** Catalog IDs (lowercase) of every listed title, pointing to its entry. */
    val entriesByContentId: Map<String, WeTrakrListEntry>,
)

/** Every catalog ID a WeTrakr title can be reached with. */
internal fun WeTrakrListEntry.contentIds(): Set<String> = buildSet {
    imdb?.let { add(it.lowercase()) }
    tmdb?.let { add("tmdb:$it") }
    tvdb?.let { add("tvdb:${it.lowercase()}") }
    add("wetrakr:$wetrakrId")
}

/** Primary content ID of a WeTrakr title: the IMDb id when known, the catalogs' common choice. */
internal fun WeTrakrListEntry.primaryContentId(): String =
    imdb ?: tmdb?.let { "tmdb:$it" } ?: tvdb?.let { "tvdb:$it" } ?: "wetrakr:$wetrakrId"

internal fun projectWeTrakrLibrary(entries: List<WeTrakrListEntry>, sectionTitle: String): WeTrakrLibraryProjection {
    val byContentId = linkedMapOf<String, WeTrakrListEntry>()
    val items = entries
        .map { entry ->
            entry.contentIds().forEach { id -> byContentId.putIfAbsent(id, entry) }
            entry.toLibraryItem()
        }
        .distinctBy { item -> "${item.type}:${item.id}" }
        .sortedByDescending(LibraryItem::savedAtEpochMs)
    val sections = items.takeIf(List<LibraryItem>::isNotEmpty)
        ?.let { listOf(LibrarySection(type = WETRAKR_WATCHLIST_KEY, displayTitle = sectionTitle, items = it)) }
        .orEmpty()
    return WeTrakrLibraryProjection(items = items, sections = sections, entriesByContentId = byContentId)
}

private fun WeTrakrListEntry.toLibraryItem(): LibraryItem {
    val contentId = primaryContentId()
    return LibraryItem(
        id = contentId,
        type = if (type == WeTrakrMediaType.MOVIE) "movie" else "series",
        name = title,
        poster = posterPath?.let { path -> if (path.startsWith("http")) path else TMDB_POSTER_BASE_URL + path },
        releaseInfo = year?.toString(),
        posterShape = PosterShape.Poster,
        listKeys = setOf(WETRAKR_WATCHLIST_KEY),
        imdbId = imdb,
        tmdbId = tmdb?.toInt(),
        trackingProviderId = TrackingProviderId.WETRAKR.storageId,
        trackingProviderItemId = "wetrakr:${type.wireType}:$wetrakrId",
        trackingSourceUrl = "$WETRAKR_WEBSITE_URL/${if (type == WeTrakrMediaType.MOVIE) "movies" else "shows"}/$wetrakrId",
        savedAtEpochMs = trackedAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
object WeTrakrTrackingLibraryProvider : TrackingLibraryProvider {
    private val log = Logger.withTag("WeTrakrLibrary")
    private const val WATCHLIST_TITLE = "Watchlist"

    override val providerId: TrackingProviderId = TrackingProviderId.WETRAKR
    override val changes: Flow<Unit> = WeTrakrListRepository.uiState.map { }
    override val connectionRefreshIntent: TrackingRefreshIntent = TrackingRefreshIntent.INVALIDATED

    /** The WeTrakr watchlist as a library section for the "WeTrakr" chip and "All"; empty while disconnected. */
    val libraryState: Flow<LibraryUiState> = WeTrakrAuthRepository.isAuthenticated
        .flatMapLatest { connected ->
            if (!connected) {
                flowOf(LibraryUiState(sourceMode = LibrarySourceMode.LOCAL, isLoaded = true))
            } else {
                WeTrakrListRepository.uiState.map {
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

    private val projectionLock = SynchronizedObject()
    private var cachedEntries: List<WeTrakrListEntry>? = null
    private var cachedProjection: WeTrakrLibraryProjection? = null

    override fun ensureLoaded() {
        WeTrakrAuthRepository.ensureLoaded()
        WeTrakrListRepository.ensureLoaded()
    }

    override fun prepare() {
        ensureLoaded()
        WeTrakrListRepository.refreshAsync(TrackingRefreshIntent.AUTOMATIC)
    }

    override fun onProfileChanged() = WeTrakrListRepository.onProfileChanged()

    override fun clearLocalState() = WeTrakrListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) = WeTrakrListRepository.refresh(intent)

    override fun snapshot(): TrackingLibrarySnapshot {
        ensureLoaded()
        val state = WeTrakrListRepository.uiState.value
        val projection = projection()
        return TrackingLibrarySnapshot(
            items = projection.items,
            sections = projection.sections,
            tabs = listOf(
                TrackingLibraryTab(
                    key = WETRAKR_WATCHLIST_KEY,
                    title = WATCHLIST_TITLE,
                    providerId = TrackingProviderId.WETRAKR,
                    kind = TrackingLibraryTabKind.WATCHLIST,
                    supportedContentTypes = setOf("movie", "series"),
                ),
            ),
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    /** WeTrakr tracks movies and shows. */
    override fun supportsItem(item: LibraryItem): Boolean =
        mediaType(item) != null

    override fun contains(contentId: String, contentType: String?): Boolean =
        projection().entriesByContentId.containsKey(contentId.lowercase())

    override fun find(contentId: String): LibraryItem? {
        val projection = projection()
        projection.items.firstOrNull { it.id.equals(contentId, ignoreCase = true) }?.let { return it }
        val entry = projection.entriesByContentId[contentId.lowercase()] ?: return null
        return projection.items.firstOrNull { it.trackingProviderItemId == "wetrakr:${entry.type.wireType}:${entry.wetrakrId}" }
    }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> =
        mapOf(WETRAKR_WATCHLIST_KEY to (listedEntry(item) != null))

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        mapOf(WETRAKR_WATCHLIST_KEY to (currentMembership[WETRAKR_WATCHLIST_KEY] != true))

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        if (profileId != ProfileRepository.activeProfileId) return null
        val wanted = desiredMembership[WETRAKR_WATCHLIST_KEY] == true
        val listed = listedEntry(item)
        if (wanted == (listed != null)) return null
        if (listed != null) {
            log.i { "Removing ${item.id} from the WeTrakr watchlist" }
            WeTrakrListRepository.remove(listed)
        } else {
            val type = mediaType(item) ?: error("${item.name} cannot be added to WeTrakr")
            val ids = item.externalIds()
            if (!ids.hasAny) error("${item.name} has no id WeTrakr can match")
            log.i { "Adding ${item.id} to the WeTrakr watchlist" }
            WeTrakrListRepository.add(type, ids)
        }
        return null
    }

    private fun mediaType(item: LibraryItem): WeTrakrMediaType? = when (item.type.lowercase()) {
        "movie" -> WeTrakrMediaType.MOVIE
        "series", "show", "tv" -> WeTrakrMediaType.SHOW
        else -> null
    }

    private fun listedEntry(item: LibraryItem): WeTrakrListEntry? {
        val projection = projection()
        item.trackingProviderItemId?.takeIf { it.startsWith("wetrakr:") }?.let { key ->
            projection.items.firstOrNull { it.trackingProviderItemId == key }
                ?.let { listed -> projection.entriesByContentId[listed.id.lowercase()] }
                ?.let { return it }
        }
        // TMDB reuses ids between movies and shows: a match must also have the item's type.
        val type = mediaType(item)
        val ids = item.externalIds()
        return listOfNotNull(
            item.id.lowercase(),
            item.imdbId?.lowercase(),
            item.tmdbId?.let { "tmdb:$it" },
            ids.imdb?.lowercase(),
            ids.tmdb?.let { "tmdb:$it" },
        ).firstNotNullOfOrNull { key ->
            projection.entriesByContentId[key]?.takeIf { entry -> type == null || entry.type == type }
        }
    }

    private fun projection(): WeTrakrLibraryProjection = synchronized(projectionLock) {
        val entries = WeTrakrListRepository.uiState.value.entries
        cachedProjection?.takeIf { cachedEntries === entries }?.let { return@synchronized it }
        projectWeTrakrLibrary(entries, WATCHLIST_TITLE).also { projection ->
            cachedEntries = entries
            cachedProjection = projection
        }
    }
}
