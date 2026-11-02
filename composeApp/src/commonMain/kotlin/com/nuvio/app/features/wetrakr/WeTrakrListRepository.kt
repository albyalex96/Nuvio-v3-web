package com.nuvio.app.features.wetrakr

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingExternalIds
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** Kind of a WeTrakr title, as the `target` of the tracking endpoints. */
enum class WeTrakrMediaType(val wireType: String, val target: String) {
    MOVIE("movie", "movies"),
    SHOW("show", "shows"),
}

/** A title of the WeTrakr watchlist (the `planning` tracking list). */
@Serializable
data class WeTrakrListEntry(
    val wetrakrId: Long,
    val type: WeTrakrMediaType,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
    val tvdb: String? = null,
    val trackedAt: String? = null,
)

@Serializable
internal data class WeTrakrListSnapshot(
    val entries: List<WeTrakrListEntry> = emptyList(),
    val fetchedAtEpochMs: Long = 0L,
)

data class WeTrakrListUiState(
    val entries: List<WeTrakrListEntry> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fetchedAtEpochMs: Long = 0L,
)

/** Parses one entry of `GET /sync/tracking/planning/{target}`; null when it carries no WeTrakr id. */
internal fun parseWeTrakrListEntry(element: JsonObject, fallbackType: WeTrakrMediaType): WeTrakrListEntry? {
    fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    // Some list shapes nest the title under "movie"/"show"; the flat compact media object is the default.
    val media = (element["movie"] as? JsonObject) ?: (element["show"] as? JsonObject) ?: element
    val id = (media["id"] as? JsonPrimitive)?.longOrNull ?: return null
    val type = when (media.string("type") ?: element.string("type")) {
        "movie" -> WeTrakrMediaType.MOVIE
        "show" -> WeTrakrMediaType.SHOW
        else -> fallbackType
    }
    val ids = media["ids"] as? JsonObject
    val date = media.string("release_date") ?: media.string("first_air_date")
    return WeTrakrListEntry(
        wetrakrId = id,
        type = type,
        title = media.string("title") ?: media.string("name") ?: "",
        year = date?.take(4)?.toIntOrNull(),
        posterPath = media.string("poster_path") ?: media.string("current_season_poster_path"),
        imdb = ids?.string("imdb"),
        tmdb = (ids?.get("tmdb") as? JsonPrimitive)?.longOrNull,
        tvdb = ids?.string("tvdb"),
        trackedAt = element.string("tracked_at") ?: element.string("added_at"),
    )
}

/** Body of `POST /sync/tracking` and `/sync/tracking/remove` for one title of the watchlist. */
internal fun weTrakrTrackingBody(type: WeTrakrMediaType, ids: TrackingExternalIds, wetrakrId: Long?): String =
    buildJsonObject {
        put(
            type.target,
            buildJsonArray {
                add(
                    buildJsonObject {
                        if (wetrakrId != null) {
                            put("id", wetrakrId)
                        } else {
                            put(
                                "ids",
                                buildJsonObject {
                                    ids.tmdb?.let { put("tmdb", it) }
                                    ids.imdb?.takeIf(String::isNotBlank)?.let { put("imdb", it) }
                                    ids.tvdb?.takeIf(String::isNotBlank)?.let { put("tvdb", it) }
                                    ids.trakt?.let { put("trakt", it) }
                                    ids.simkl?.let { put("simkl", it) }
                                    ids.mal?.let { put("mal", it) }
                                    ids.anilist?.let { put("anilist", it) }
                                    ids.kitsu?.let { put("kitsu", it) }
                                },
                            )
                        }
                        put("status", WETRAKR_WATCHLIST_STATUS)
                    },
                )
            },
        )
    }.toString()

internal const val WETRAKR_WATCHLIST_STATUS = "planning"

/**
 * The user's WeTrakr watchlist: cached per profile, refreshed at most every
 * [AUTOMATIC_REFRESH_INTERVAL_MS] unless the user asks, and updated after every write.
 */
object WeTrakrListRepository {
    private val log = Logger.withTag("WeTrakrList")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _uiState = MutableStateFlow(WeTrakrListUiState())
    val uiState: StateFlow<WeTrakrListUiState> = _uiState.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        val cached = runCatching { AppFileCache.read(cachePath(profileId)) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<WeTrakrListSnapshot>(payload) }
                    .onFailure { error -> log.w(error) { "WeTrakr list cache is unreadable" } }
                    .getOrNull()
            }
        _uiState.value = WeTrakrListUiState(
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
        _uiState.value = WeTrakrListUiState()
    }

    fun refreshAsync(intent: TrackingRefreshIntent) {
        scope.launch { refresh(intent) }
    }

    suspend fun refresh(intent: TrackingRefreshIntent) {
        ensureLoaded()
        if (!WeTrakrAuthRepository.isAuthenticated.value) return
        refreshMutex.withLock {
            val state = _uiState.value
            val age = nowEpochMs() - state.fetchedAtEpochMs
            if (intent == TrackingRefreshIntent.AUTOMATIC && state.hasLoaded && age < AUTOMATIC_REFRESH_INTERVAL_MS) {
                log.d { "WeTrakr list refresh skipped (age=${age / 1_000}s)" }
                return@withLock
            }
            val profileId = ProfileRepository.activeProfileId
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val entries = (fetchAll(WeTrakrMediaType.MOVIE) + fetchAll(WeTrakrMediaType.SHOW))
                    .sortedByDescending { it.trackedAt.orEmpty() }
                if (profileId != ProfileRepository.activeProfileId) return@withLock
                publishEntries(entries, fetchedAt = nowEpochMs())
                log.i { "WeTrakr watchlist refreshed (${entries.size} titles, intent=$intent)" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "WeTrakr watchlist refresh failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    /** Adds a title to the watchlist, then reloads the list so it shows the server title and poster. */
    suspend fun add(type: WeTrakrMediaType, ids: TrackingExternalIds) {
        writeMutex.withLock {
            val response = WeTrakrApi.client.execute(
                method = WeTrakrHttpMethod.POST,
                path = "/sync/tracking",
                jsonBody = weTrakrTrackingBody(type, ids, wetrakrId = null),
            )
            if (isNotFound(response.body, type)) {
                log.w { "WeTrakr could not match the ${type.wireType} (tmdb=${ids.tmdb}, imdb=${ids.imdb})" }
                error("Title not found on WeTrakr")
            }
            log.i { "WeTrakr ${type.wireType} added to the watchlist (tmdb=${ids.tmdb}, imdb=${ids.imdb})" }
        }
        refresh(TrackingRefreshIntent.INVALIDATED)
    }

    suspend fun remove(entry: WeTrakrListEntry) = writeMutex.withLock {
        WeTrakrApi.client.execute(
            method = WeTrakrHttpMethod.POST,
            path = "/sync/tracking/remove",
            jsonBody = weTrakrTrackingBody(entry.type, TrackingExternalIds(), wetrakrId = entry.wetrakrId),
        )
        log.i { "WeTrakr ${entry.type.wireType} ${entry.wetrakrId} removed from the watchlist" }
        publishEntries(_uiState.value.entries.filterNot { it.wetrakrId == entry.wetrakrId && it.type == entry.type })
    }

    private fun isNotFound(body: String, type: WeTrakrMediaType): Boolean = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return false
        val notFound = root["notFound"] as? JsonObject ?: return false
        (notFound[type.target] as? JsonArray)?.isNotEmpty() == true
    }.getOrDefault(false)

    private suspend fun fetchAll(type: WeTrakrMediaType): List<WeTrakrListEntry> {
        val entries = mutableListOf<WeTrakrListEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = WeTrakrApi.client.execute(
                method = WeTrakrHttpMethod.GET,
                path = "/sync/tracking/$WETRAKR_WATCHLIST_STATUS/${type.target}",
                query = mapOf("page" to page.toString(), "limit" to PAGE_SIZE.toString()),
            )
            val items = (json.parseToJsonElement(response.body) as? JsonArray).orEmpty()
            items.mapNotNullTo(entries) { element -> (element as? JsonObject)?.let { parseWeTrakrListEntry(it, type) } }
            val pageCount = response.header("X-Pagination-Page-Count")?.trim()?.toIntOrNull()
            val done = if (pageCount != null) page >= pageCount else items.size < PAGE_SIZE
            if (done || items.isEmpty()) return entries
            page++
        }
        log.w { "WeTrakr ${type.target} watchlist truncated after $MAX_PAGES pages" }
        return entries
    }

    private fun publishEntries(entries: List<WeTrakrListEntry>, fetchedAt: Long = _uiState.value.fetchedAtEpochMs) {
        _uiState.value = WeTrakrListUiState(
            entries = entries,
            hasLoaded = true,
            isLoading = false,
            fetchedAtEpochMs = fetchedAt,
        )
        val profileId = loadedProfileId ?: return
        runCatching {
            AppFileCache.write(cachePath(profileId), json.encodeToString(WeTrakrListSnapshot(entries, fetchedAt)))
        }.onFailure { error -> log.w(error) { "Failed to cache the WeTrakr watchlist" } }
    }

    private fun cachePath(profileId: Int): String = "wetrakr/watchlist_$profileId.json"

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** WeTrakr caps regular list pages at 100 items. */
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 100
    private const val AUTOMATIC_REFRESH_INTERVAL_MS = 15L * 60L * 1_000L
}
