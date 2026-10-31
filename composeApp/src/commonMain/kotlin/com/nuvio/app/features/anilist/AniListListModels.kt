package com.nuvio.app.features.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** AniList `MediaListStatus`; REPEATING (rewatching) is shown together with CURRENT. */
enum class AniListListStatus(val wireValue: String) {
    CURRENT("CURRENT"),
    PLANNING("PLANNING"),
    PAUSED("PAUSED"),
    COMPLETED("COMPLETED"),
    DROPPED("DROPPED"),
    REPEATING("REPEATING");

    companion object {
        fun fromWire(value: String?): AniListListStatus? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/** One anime of the user's AniList list, as cached and projected into the Nuvio library. */
@Serializable
data class AniListListEntry(
    /** ID of the list entry itself, needed to delete it. */
    @SerialName("entry_id") val entryId: Long,
    @SerialName("id") val aniListId: Long,
    @SerialName("mal_id") val malId: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("poster") val posterUrl: String? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("year") val startYear: Int? = null,
    /** AniList average score on a 0-10 scale. */
    @SerialName("rating") val rating: Double? = null,
    @SerialName("status") val statusWire: String? = null,
    /** Last update of the entry, epoch seconds. */
    @SerialName("updated_at") val updatedAtEpochSeconds: Long? = null,
) {
    val status: AniListListStatus?
        get() = AniListListStatus.fromWire(statusWire)

    /** Status of the library section: a rewatch is shown with the anime being watched. */
    val displayStatus: AniListListStatus?
        get() = status?.let { if (it == AniListListStatus.REPEATING) AniListListStatus.CURRENT else it }

    val isMovie: Boolean
        get() = format.equals("MOVIE", ignoreCase = true)
}

@Serializable
internal data class AniListListSnapshot(
    val entries: List<AniListListEntry> = emptyList(),
    val fetchedAtEpochMs: Long = 0L,
)

internal const val ANILIST_ENTRY_FIELDS = """
    id
    status
    updatedAt
    media {
      id
      idMal
      format
      seasonYear
      startDate { year }
      averageScore
      title { userPreferred romaji english }
      coverImage { large medium }
    }
"""

internal const val ANILIST_LIST_COLLECTION_QUERY = """
query (${'$'}userId: Int) {
  MediaListCollection(userId: ${'$'}userId, type: ANIME) {
    lists {
      entries {
        $ANILIST_ENTRY_FIELDS
      }
    }
  }
}
"""

internal const val ANILIST_LIST_ENTRY_QUERY = """
query (${'$'}userId: Int, ${'$'}mediaId: Int) {
  MediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId) {
    $ANILIST_ENTRY_FIELDS
  }
}
"""

internal const val ANILIST_SAVE_ENTRY_MUTATION = """
mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus) {
  SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status) {
    $ANILIST_ENTRY_FIELDS
  }
}
"""

internal const val ANILIST_DELETE_ENTRY_MUTATION = """
mutation (${'$'}id: Int) {
  DeleteMediaListEntry(id: ${'$'}id) { deleted }
}
"""

/** Parses one `MediaList` object; null when it has no media. */
internal fun parseAniListEntry(entry: JsonObject): AniListListEntry? {
    val media = entry["media"] as? JsonObject ?: return null
    val aniListId = media["id"]?.jsonPrimitive?.longOrNull ?: return null
    val entryId = entry["id"]?.jsonPrimitive?.longOrNull ?: return null
    val title = (media["title"] as? JsonObject)?.let { titles ->
        listOf("userPreferred", "english", "romaji")
            .firstNotNullOfOrNull { key -> titles[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
    } ?: "AniList $aniListId"
    val cover = media["coverImage"] as? JsonObject
    return AniListListEntry(
        entryId = entryId,
        aniListId = aniListId,
        malId = media["idMal"]?.jsonPrimitive?.longOrNull,
        title = title,
        posterUrl = cover?.get("large")?.jsonPrimitive?.contentOrNull
            ?: cover?.get("medium")?.jsonPrimitive?.contentOrNull,
        format = media["format"]?.jsonPrimitive?.contentOrNull,
        startYear = media["seasonYear"]?.jsonPrimitive?.intOrNull
            ?: (media["startDate"] as? JsonObject)?.get("year")?.jsonPrimitive?.intOrNull,
        rating = media["averageScore"]?.jsonPrimitive?.intOrNull?.let { it / 10.0 },
        statusWire = entry["status"]?.jsonPrimitive?.contentOrNull,
        updatedAtEpochSeconds = entry["updatedAt"]?.jsonPrimitive?.longOrNull,
    )
}

/** Every entry of a `MediaListCollection` (custom lists can repeat an anime: the first copy wins). */
internal fun parseAniListCollection(collection: JsonObject?): List<AniListListEntry> =
    (collection?.get("lists") as? JsonArray).orEmpty()
        .asSequence()
        .mapNotNull { list -> (list as? JsonObject)?.get("entries") as? JsonArray }
        .flatten()
        .mapNotNull { entry -> (entry as? JsonObject)?.let(::parseAniListEntry) }
        .distinctBy(AniListListEntry::aniListId)
        .sortedByDescending { it.updatedAtEpochSeconds ?: 0L }
        .toList()
