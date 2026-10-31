package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

data class AniListListUiState(
    val entries: List<AniListListEntry> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fetchedAtEpochMs: Long = 0L,
)

/**
 * The user's AniList anime list: cached per profile, refreshed at most every
 * [AUTOMATIC_REFRESH_INTERVAL_MS] unless the user asks, and updated in place after every write.
 */
object AniListListRepository {
    private val log = Logger.withTag("AniListList")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _uiState = MutableStateFlow(AniListListUiState())
    val uiState: StateFlow<AniListListUiState> = _uiState.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        val cached = runCatching { AppFileCache.read(cachePath(profileId)) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<AniListListSnapshot>(payload) }
                    .onFailure { error -> log.w(error) { "AniList list cache is unreadable" } }
                    .getOrNull()
            }
        _uiState.value = AniListListUiState(
            entries = cached?.entries.orEmpty(),
            hasLoaded = cached != null,
            fetchedAtEpochMs = cached?.fetchedAtEpochMs ?: 0L,
        )
    }

    fun onProfileChanged() {
        loadedProfileId = null
        ensureLoaded()
    }

    fun clearLocalState() {
        loadedProfileId?.let { profileId -> runCatching { AppFileCache.write(cachePath(profileId), "") } }
        loadedProfileId = null
        _uiState.value = AniListListUiState()
    }

    fun entry(aniListId: Long): AniListListEntry? {
        ensureLoaded()
        return _uiState.value.entries.firstOrNull { it.aniListId == aniListId }
    }

    fun refreshAsync(intent: TrackingRefreshIntent) {
        scope.launch { refresh(intent) }
    }

    suspend fun refresh(intent: TrackingRefreshIntent) {
        ensureLoaded()
        if (!AniListAuthRepository.isAuthenticated.value) return
        val userId = AniListAuthRepository.userId() ?: return
        refreshMutex.withLock {
            val state = _uiState.value
            val age = nowEpochMs() - state.fetchedAtEpochMs
            if (intent == TrackingRefreshIntent.AUTOMATIC && state.hasLoaded && age < AUTOMATIC_REFRESH_INTERVAL_MS) {
                log.d { "AniList list refresh skipped (age=${age / 1_000}s)" }
                return@withLock
            }
            val profileId = ProfileRepository.activeProfileId
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val data = AniListApi.client.query(
                    operation = "MediaListCollection",
                    query = ANILIST_LIST_COLLECTION_QUERY,
                    variables = buildJsonObject { put("userId", userId) },
                )
                val entries = parseAniListCollection(data?.get("MediaListCollection").objectOrNull())
                if (profileId != ProfileRepository.activeProfileId) return@withLock
                publishEntries(entries, fetchedAt = nowEpochMs())
                log.i { "AniList list refreshed (${entries.size} anime, intent=$intent)" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "AniList list refresh failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    /** Adds [aniListId] to the list with [status], or moves it there when already listed. */
    suspend fun setStatus(aniListId: Long, status: AniListListStatus): Boolean = writeMutex.withLock {
        AniListAuthRepository.userId() ?: error("AniList account not connected")
        val data = AniListApi.client.query(
            operation = "SaveMediaListEntry",
            query = ANILIST_SAVE_ENTRY_MUTATION,
            variables = buildJsonObject {
                put("mediaId", aniListId)
                put("status", status.wireValue)
            },
        )
        val saved = data?.get("SaveMediaListEntry").objectOrNull()?.let(::parseAniListEntry)
        if (saved != null) upsert(saved)
        log.i { "AniList anime $aniListId saved as ${status.wireValue}" }
        true
    }

    suspend fun remove(aniListId: Long): Boolean = writeMutex.withLock {
        val userId = AniListAuthRepository.userId() ?: error("AniList account not connected")
        val entryId = fetchEntry(userId, aniListId)?.entryId
        if (entryId != null) {
            AniListApi.client.query(
                operation = "DeleteMediaListEntry",
                query = ANILIST_DELETE_ENTRY_MUTATION,
                variables = buildJsonObject { put("id", entryId) },
            )
        }
        log.i { "AniList anime $aniListId removed from the list (listed=${entryId != null})" }
        publishEntries(_uiState.value.entries.filterNot { it.aniListId == aniListId })
        true
    }

    /** The current list entry of [aniListId] on AniList, so deletes never rely on a stale cache. */
    private suspend fun fetchEntry(userId: Long, aniListId: Long): AniListListEntry? =
        AniListApi.client.query(
            operation = "MediaList",
            query = ANILIST_LIST_ENTRY_QUERY,
            variables = buildJsonObject {
                put("userId", userId)
                put("mediaId", aniListId)
            },
            allowNotFound = true,
        )?.get("MediaList").objectOrNull()?.let(::parseAniListEntry)

    private fun upsert(entry: AniListListEntry) {
        val current = _uiState.value.entries
        val updated = if (current.any { it.aniListId == entry.aniListId }) {
            listOf(entry) + current.filterNot { it.aniListId == entry.aniListId }
        } else {
            listOf(entry) + current
        }
        publishEntries(updated)
    }

    private fun publishEntries(entries: List<AniListListEntry>, fetchedAt: Long = _uiState.value.fetchedAtEpochMs) {
        _uiState.value = AniListListUiState(
            entries = entries,
            hasLoaded = true,
            isLoading = false,
            fetchedAtEpochMs = fetchedAt,
        )
        val profileId = loadedProfileId ?: return
        runCatching {
            AppFileCache.write(cachePath(profileId), json.encodeToString(AniListListSnapshot(entries, fetchedAt)))
        }.onFailure { error -> log.w(error) { "Failed to cache the AniList list" } }
    }

    private fun cachePath(profileId: Int): String = "anilist/anime_list_$profileId.json"

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private const val AUTOMATIC_REFRESH_INTERVAL_MS = 15L * 60L * 1_000L
}
