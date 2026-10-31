package com.nuvio.app.features.kitsu

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
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

data class KitsuListUiState(
    val entries: List<KitsuListEntry> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fetchedAtEpochMs: Long = 0L,
)

/**
 * The user's Kitsu anime library: cached per profile, refreshed at most every
 * [AUTOMATIC_REFRESH_INTERVAL_MS] unless the user asks, and updated in place after every write.
 */
object KitsuListRepository {
    private val log = Logger.withTag("KitsuList")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _uiState = MutableStateFlow(KitsuListUiState())
    val uiState: StateFlow<KitsuListUiState> = _uiState.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        val cached = runCatching { AppFileCache.read(cachePath(profileId)) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<KitsuListSnapshot>(payload) }
                    .onFailure { error -> log.w(error) { "Kitsu list cache is unreadable" } }
                    .getOrNull()
            }
        _uiState.value = KitsuListUiState(
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
        _uiState.value = KitsuListUiState()
    }

    fun entry(kitsuId: Long): KitsuListEntry? {
        ensureLoaded()
        return _uiState.value.entries.firstOrNull { it.kitsuId == kitsuId }
    }

    fun refreshAsync(intent: TrackingRefreshIntent) {
        scope.launch { refresh(intent) }
    }

    suspend fun refresh(intent: TrackingRefreshIntent) {
        ensureLoaded()
        if (!KitsuAuthRepository.isAuthenticated.value) return
        val userId = KitsuAuthRepository.userId() ?: return
        refreshMutex.withLock {
            val state = _uiState.value
            val age = nowEpochMs() - state.fetchedAtEpochMs
            if (intent == TrackingRefreshIntent.AUTOMATIC && state.hasLoaded && age < AUTOMATIC_REFRESH_INTERVAL_MS) {
                log.d { "Kitsu list refresh skipped (age=${age / 1_000}s)" }
                return@withLock
            }
            val profileId = ProfileRepository.activeProfileId
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val entries = fetchAll(userId)
                if (profileId != ProfileRepository.activeProfileId) return@withLock
                publishEntries(entries, fetchedAt = nowEpochMs())
                log.i { "Kitsu list refreshed (${entries.size} anime, intent=$intent)" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Kitsu list refresh failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    /** Adds [kitsuId] to the library with [status], or moves it there when already listed. */
    suspend fun setStatus(kitsuId: Long, status: KitsuListStatus): Boolean = writeMutex.withLock {
        val userId = KitsuAuthRepository.userId() ?: error("Kitsu account not connected")
        val current = fetchEntry(userId, kitsuId)
        if (current?.status == status) {
            log.d { "Kitsu anime $kitsuId already in ${status.wireValue}" }
            upsert(current)
            return@withLock false
        }
        if (current == null) {
            KitsuApi.client.execute(
                method = KitsuHttpMethod.POST,
                path = "/library-entries",
                jsonBody = kitsuCreateEntryBody(userId, kitsuId, status),
            )
        } else {
            KitsuApi.client.execute(
                method = KitsuHttpMethod.PATCH,
                path = "/library-entries/${current.entryId}",
                jsonBody = kitsuUpdateStatusBody(current.entryId, status),
            )
        }
        val saved = fetchEntry(userId, kitsuId)
        if (saved != null) upsert(saved)
        log.i { "Kitsu anime $kitsuId: ${current?.statusWire ?: "not listed"} -> ${status.wireValue}" }
        true
    }

    suspend fun remove(kitsuId: Long): Boolean = writeMutex.withLock {
        val userId = KitsuAuthRepository.userId() ?: error("Kitsu account not connected")
        val entryId = fetchEntry(userId, kitsuId)?.entryId
        if (entryId != null) {
            KitsuApi.client.execute(
                method = KitsuHttpMethod.DELETE,
                path = "/library-entries/$entryId",
                allowNotFound = true,
            )
        }
        log.i { "Kitsu anime $kitsuId removed from the library (listed=${entryId != null})" }
        publishEntries(_uiState.value.entries.filterNot { it.kitsuId == kitsuId })
        true
    }

    /** The current library entry of [kitsuId] on Kitsu, so writes never rely on a stale cache. */
    private suspend fun fetchEntry(userId: String, kitsuId: Long): KitsuListEntry? {
        val response = KitsuApi.client.execute(
            method = KitsuHttpMethod.GET,
            path = "/library-entries",
            query = mapOf(
                "filter[user_id]" to userId,
                "filter[anime_id]" to kitsuId.toString(),
                "page[limit]" to "1",
            ) + kitsuLibraryFields,
        )
        return parseKitsuLibraryPage(json.parseToJsonElement(response.body).jsonObject).entries.firstOrNull()
    }

    private suspend fun fetchAll(userId: String): List<KitsuListEntry> {
        val entries = mutableListOf<KitsuListEntry>()
        var page = parsePage(
            KitsuApi.client.execute(
                method = KitsuHttpMethod.GET,
                path = "/library-entries",
                query = mapOf(
                    "filter[user_id]" to userId,
                    "filter[kind]" to "anime",
                    "sort" to "-updated_at",
                    "page[limit]" to PAGE_SIZE.toString(),
                    "page[offset]" to "0",
                ) + kitsuLibraryFields,
            ).body,
        )
        repeat(MAX_PAGES) {
            entries += page.entries
            val next = page.nextUrl ?: return entries
            if (page.entries.isEmpty()) return entries
            page = parsePage(KitsuApi.client.execute(method = KitsuHttpMethod.GET, path = next).body)
        }
        log.w { "Kitsu list truncated after $MAX_PAGES pages" }
        return entries
    }

    private fun parsePage(body: String): KitsuLibraryPage =
        parseKitsuLibraryPage(json.parseToJsonElement(body).jsonObject)

    private fun upsert(entry: KitsuListEntry) {
        val current = _uiState.value.entries
        val updated = if (entry.statusWire == null) {
            current.filterNot { it.kitsuId == entry.kitsuId }
        } else if (current.any { it.kitsuId == entry.kitsuId }) {
            current.map { if (it.kitsuId == entry.kitsuId) entry else it }
        } else {
            listOf(entry) + current
        }
        publishEntries(updated)
    }

    private fun publishEntries(entries: List<KitsuListEntry>, fetchedAt: Long = _uiState.value.fetchedAtEpochMs) {
        _uiState.value = KitsuListUiState(
            entries = entries,
            hasLoaded = true,
            isLoading = false,
            fetchedAtEpochMs = fetchedAt,
        )
        val profileId = loadedProfileId ?: return
        runCatching {
            AppFileCache.write(cachePath(profileId), json.encodeToString(KitsuListSnapshot(entries, fetchedAt)))
        }.onFailure { error -> log.w(error) { "Failed to cache the Kitsu list" } }
    }

    private fun cachePath(profileId: Int): String = "kitsu/anime_list_$profileId.json"

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Kitsu caps `library-entries` pages at 500 items. */
    private const val PAGE_SIZE = 500
    private const val MAX_PAGES = 40
    private const val AUTOMATIC_REFRESH_INTERVAL_MS = 15L * 60L * 1_000L
}
