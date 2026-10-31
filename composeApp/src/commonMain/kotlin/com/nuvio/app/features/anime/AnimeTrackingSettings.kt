package com.nuvio.app.features.anime

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncStringSet
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.parseTrackingExternalIds
import com.nuvio.app.features.tracking.trackingMediaKind
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal expect object AnimeTrackingStorage {
    fun loadExcludedProviders(): String?
    fun saveExcludedProviders(payload: String)
    fun loadLibraryDestinations(): String?
    fun saveLibraryDestinations(payload: String)
}

data class AnimeTrackingSettingsUiState(
    /** Providers that must NOT receive automatic anime updates; new providers are included by default. */
    val excludedProviders: Set<TrackingProviderId> = emptySet(),
    /** Library list keys chosen the last time an anime was saved, offered pre-selected the next time. */
    val lastLibraryDestinations: Set<String> = emptySet(),
)

/**
 * The "services to update for anime" selector: which connected trackers receive anime episode
 * completions and watched marks. Non-anime content always reaches every connected tracker.
 */
object AnimeTrackingSettingsRepository {
    private val log = Logger.withTag("AnimeTracking")
    private val _uiState = MutableStateFlow(AnimeTrackingSettingsUiState())
    val uiState: StateFlow<AnimeTrackingSettingsUiState> = _uiState.asStateFlow()
    private var loadedProfileId: Int? = null

    /** Loads the active profile's choice; switching profile reloads it on the next access. */
    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        _uiState.value = AnimeTrackingSettingsUiState(
            excludedProviders = decodeExcludedProviders(AnimeTrackingStorage.loadExcludedProviders()),
            lastLibraryDestinations = decodeLibraryDestinations(AnimeTrackingStorage.loadLibraryDestinations()),
        )
    }

    fun isEnabledForAnime(providerId: TrackingProviderId): Boolean {
        ensureLoaded()
        return providerId !in _uiState.value.excludedProviders
    }

    fun setEnabledForAnime(providerId: TrackingProviderId, enabled: Boolean) {
        ensureLoaded()
        val current = _uiState.value.excludedProviders
        val updated = if (enabled) current - providerId else current + providerId
        if (updated == current) return
        log.i { "Anime updates for ${providerId.storageId} set to $enabled" }
        save(updated)
    }

    fun lastLibraryDestinations(): Set<String> {
        ensureLoaded()
        return _uiState.value.lastLibraryDestinations
    }

    /** Remembers where an anime was saved; an empty choice (a removal) keeps the previous one. */
    fun rememberLibraryDestinations(listKeys: Set<String>) {
        ensureLoaded()
        if (listKeys.isEmpty() || listKeys == _uiState.value.lastLibraryDestinations) return
        log.i { "Anime library destinations remembered: ${listKeys.sorted()}" }
        saveLibraryDestinations(listKeys)
    }

    /** Content IDs (lowercase) the user removed from every anime destination during this session. */
    private val removedFromLibrary = mutableSetOf<String>()

    /**
     * Records a picker save: a removal stops the remembered destinations from being preselected for
     * that item, so the picker shows it as not saved; a new save clears the mark.
     */
    fun onLibraryDestinationsSaved(contentId: String, listKeys: Set<String>) {
        val key = contentId.lowercase()
        if (listKeys.isEmpty()) {
            log.d { "Anime $contentId removed from the library: no preselection for it" }
            removedFromLibrary += key
        } else {
            removedFromLibrary -= key
            rememberLibraryDestinations(listKeys)
        }
    }

    fun wasRemovedFromLibrary(contentId: String): Boolean = contentId.lowercase() in removedFromLibrary

    internal fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        put(SYNC_KEY, encodeSyncStringSet(_uiState.value.excludedProviders.mapTo(linkedSetOf()) { it.storageId }))
        put(DESTINATIONS_SYNC_KEY, encodeSyncStringSet(_uiState.value.lastLibraryDestinations))
    }

    internal fun applyFromSyncPayload(payload: JsonObject) {
        ensureLoaded()
        payload.decodeSyncStringSet(SYNC_KEY)?.let { remote ->
            val excluded = remote.mapNotNullTo(linkedSetOf(), TrackingProviderId::fromStorage)
            if (excluded != _uiState.value.excludedProviders) save(excluded)
        }
        payload.decodeSyncStringSet(DESTINATIONS_SYNC_KEY)?.let { remote ->
            if (remote != _uiState.value.lastLibraryDestinations) saveLibraryDestinations(remote)
        }
    }

    private fun save(excluded: Set<TrackingProviderId>) {
        _uiState.value = _uiState.value.copy(excludedProviders = excluded)
        AnimeTrackingStorage.saveExcludedProviders(excluded.joinToString(",") { it.storageId })
    }

    private fun saveLibraryDestinations(listKeys: Set<String>) {
        _uiState.value = _uiState.value.copy(lastLibraryDestinations = listKeys)
        AnimeTrackingStorage.saveLibraryDestinations(listKeys.joinToString("\n"))
    }

    private const val SYNC_KEY = "anime_tracking_excluded_providers"
    private const val DESTINATIONS_SYNC_KEY = "anime_library_destinations"
}

internal fun decodeLibraryDestinations(payload: String?): Set<String> =
    payload.orEmpty()
        .split('\n')
        .mapNotNullTo(linkedSetOf()) { value -> value.trim().takeIf(String::isNotEmpty) }

internal fun decodeExcludedProviders(payload: String?): Set<TrackingProviderId> =
    payload.orEmpty()
        .split(',')
        .mapNotNullTo(linkedSetOf()) { value -> TrackingProviderId.fromStorage(value.trim().takeIf(String::isNotEmpty)) }

/**
 * Routing rule of the selector: anime content goes only to the providers left enabled; anything
 * else goes everywhere. [isAnime] is evaluated lazily because it may need the ID index.
 */
internal suspend fun <T> filterProvidersForMedia(
    providers: List<T>,
    providerId: (T) -> TrackingProviderId,
    isAnime: suspend () -> Boolean,
    isEnabledForAnime: (TrackingProviderId) -> Boolean = AnimeTrackingSettingsRepository::isEnabledForAnime,
): List<T> {
    if (providers.all { isEnabledForAnime(providerId(it)) }) return providers
    if (!isAnime()) return providers
    return providers.filter { isEnabledForAnime(providerId(it)) }
}

internal suspend fun isAnimeMedia(kind: TrackingMediaKind, ids: TrackingExternalIds): Boolean =
    kind == TrackingMediaKind.ANIME || AnimeIdMapper.isKnownAnime(ids)

/** Watched items a provider should receive: anime items are dropped when it is excluded from anime updates. */
internal suspend fun watchedItemsForAnimeSelection(
    providerId: TrackingProviderId,
    items: Collection<WatchedItem>,
): Collection<WatchedItem> {
    if (AnimeTrackingSettingsRepository.isEnabledForAnime(providerId)) return items
    return items.filterNot { item ->
        val ids = parseTrackingExternalIds(item.id).mergeMissing(parseTrackingExternalIds(item.videoId))
        isAnimeMedia(trackingMediaKind(item.type, ids), ids)
    }
}
