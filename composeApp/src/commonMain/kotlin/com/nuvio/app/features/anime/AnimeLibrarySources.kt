package com.nuvio.app.features.anime

import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListTrackingLibraryProvider
import com.nuvio.app.features.anilist.isAniListLibrarySection
import com.nuvio.app.features.kitsu.KitsuAuthRepository
import com.nuvio.app.features.kitsu.KitsuTrackingLibraryProvider
import com.nuvio.app.features.kitsu.isKitsuLibrarySection
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.mal.MalAuthRepository
import com.nuvio.app.features.mal.MalTrackingLibraryProvider
import com.nuvio.app.features.mal.isMalLibrarySection
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.wetrakr.WeTrakrAuthRepository
import com.nuvio.app.features.wetrakr.WeTrakrTrackingLibraryProvider
import com.nuvio.app.features.wetrakr.isWeTrakrLibrarySection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * An anime tracker whose lists the library shows in its own chip and in the "All" view, whatever the
 * selected library source. The chip is offered only while [isConnected].
 */
interface AnimeLibrarySource {
    val providerId: TrackingProviderId
    val isConnected: StateFlow<Boolean>

    /** The tracker lists as library sections; empty while the tracker is not connected. */
    val libraryState: Flow<LibraryUiState>

    fun ownsSection(sectionType: String?): Boolean

    /** Loads the lists and refreshes them when stale. */
    fun prepare()

    suspend fun refresh(intent: TrackingRefreshIntent)
}

/** Every anime tracker with library lists. */
object AnimeLibrarySources {
    val all: List<AnimeLibrarySource> = listOf(
        MalAnimeLibrarySource,
        KitsuAnimeLibrarySource,
        AniListAnimeLibrarySource,
        WeTrakrLibrarySource,
    )

    fun byProvider(providerId: TrackingProviderId): AnimeLibrarySource? =
        all.firstOrNull { it.providerId == providerId }

    fun forSection(sectionType: String?): AnimeLibrarySource? =
        all.firstOrNull { it.ownsSection(sectionType) }
}

private object MalAnimeLibrarySource : AnimeLibrarySource {
    override val providerId: TrackingProviderId = TrackingProviderId.MAL
    override val isConnected: StateFlow<Boolean>
        get() {
            MalAuthRepository.ensureLoaded()
            return MalAuthRepository.isAuthenticated
        }
    override val libraryState: Flow<LibraryUiState> get() = MalTrackingLibraryProvider.libraryState

    override fun ownsSection(sectionType: String?): Boolean = isMalLibrarySection(sectionType)

    override fun prepare() = MalTrackingLibraryProvider.prepare()

    override suspend fun refresh(intent: TrackingRefreshIntent) = MalTrackingLibraryProvider.refresh(intent)
}

private object KitsuAnimeLibrarySource : AnimeLibrarySource {
    override val providerId: TrackingProviderId = TrackingProviderId.KITSU
    override val isConnected: StateFlow<Boolean>
        get() {
            KitsuAuthRepository.ensureLoaded()
            return KitsuAuthRepository.isAuthenticated
        }
    override val libraryState: Flow<LibraryUiState> get() = KitsuTrackingLibraryProvider.libraryState

    override fun ownsSection(sectionType: String?): Boolean = isKitsuLibrarySection(sectionType)

    override fun prepare() = KitsuTrackingLibraryProvider.prepare()

    override suspend fun refresh(intent: TrackingRefreshIntent) = KitsuTrackingLibraryProvider.refresh(intent)
}

private object AniListAnimeLibrarySource : AnimeLibrarySource {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST
    override val isConnected: StateFlow<Boolean>
        get() {
            AniListAuthRepository.ensureLoaded()
            return AniListAuthRepository.isAuthenticated
        }
    override val libraryState: Flow<LibraryUiState> get() = AniListTrackingLibraryProvider.libraryState

    override fun ownsSection(sectionType: String?): Boolean = isAniListLibrarySection(sectionType)

    override fun prepare() = AniListTrackingLibraryProvider.prepare()

    override suspend fun refresh(intent: TrackingRefreshIntent) = AniListTrackingLibraryProvider.refresh(intent)
}

/** WeTrakr is not anime-only, but its watchlist uses the same per-tracker chip and "All" merge. */
private object WeTrakrLibrarySource : AnimeLibrarySource {
    override val providerId: TrackingProviderId = TrackingProviderId.WETRAKR
    override val isConnected: StateFlow<Boolean>
        get() {
            WeTrakrAuthRepository.ensureLoaded()
            return WeTrakrAuthRepository.isAuthenticated
        }
    override val libraryState: Flow<LibraryUiState> get() = WeTrakrTrackingLibraryProvider.libraryState

    override fun ownsSection(sectionType: String?): Boolean = isWeTrakrLibrarySection(sectionType)

    override fun prepare() = WeTrakrTrackingLibraryProvider.prepare()

    override suspend fun refresh(intent: TrackingRefreshIntent) = WeTrakrTrackingLibraryProvider.refresh(intent)
}
