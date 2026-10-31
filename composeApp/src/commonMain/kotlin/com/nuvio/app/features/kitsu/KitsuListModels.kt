package com.nuvio.app.features.kitsu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class KitsuListStatus(val wireValue: String) {
    CURRENT("current"),
    PLANNED("planned"),
    ON_HOLD("on_hold"),
    COMPLETED("completed"),
    DROPPED("dropped");

    companion object {
        fun fromWire(value: String?): KitsuListStatus? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/** One anime of the user's Kitsu library, as cached and projected into the Nuvio library. */
@Serializable
data class KitsuListEntry(
    /** ID of the library entry itself, needed to update or delete it. */
    @SerialName("entry_id") val entryId: String,
    @SerialName("id") val kitsuId: Long,
    @SerialName("title") val title: String,
    @SerialName("poster") val posterUrl: String? = null,
    @SerialName("subtype") val subtype: String? = null,
    @SerialName("episodes") val episodeCount: Int = 0,
    @SerialName("year") val startYear: Int? = null,
    /** Kitsu average rating on a 0-10 scale. */
    @SerialName("rating") val rating: Double? = null,
    @SerialName("status") val statusWire: String? = null,
    @SerialName("progress") val progress: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val status: KitsuListStatus?
        get() = KitsuListStatus.fromWire(statusWire)

    val isMovie: Boolean
        get() = subtype.equals("movie", ignoreCase = true)
}

@Serializable
internal data class KitsuListSnapshot(
    @SerialName("entries") val entries: List<KitsuListEntry> = emptyList(),
    @SerialName("fetched_at") val fetchedAtEpochMs: Long = 0L,
)

/** A page of `library-entries` with the included anime resolved. */
internal data class KitsuLibraryPage(
    val entries: List<KitsuListEntry>,
    val nextUrl: String?,
)

/** Parses a JSON:API `library-entries` document that includes its anime. */
internal fun parseKitsuLibraryPage(document: JsonObject): KitsuLibraryPage {
    val anime = (document["included"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .filter { it.string("type") == "anime" }
        .associateBy { it.string("id") }
    val entries = (document["data"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .mapNotNull { entry ->
            val entryId = entry.string("id") ?: return@mapNotNull null
            val animeId = entry.obj("relationships")?.obj("anime")?.obj("data")?.string("id") ?: return@mapNotNull null
            val kitsuId = animeId.toLongOrNull() ?: return@mapNotNull null
            val attributes = entry.obj("attributes")
            val animeAttributes = anime[animeId]?.obj("attributes")
            KitsuListEntry(
                entryId = entryId,
                kitsuId = kitsuId,
                title = animeAttributes?.let(::kitsuTitle) ?: "Kitsu $kitsuId",
                posterUrl = animeAttributes?.obj("posterImage")?.let { image ->
                    image.string("large") ?: image.string("medium") ?: image.string("original")
                },
                subtype = animeAttributes?.string("subtype"),
                episodeCount = animeAttributes?.string("episodeCount")?.toIntOrNull() ?: 0,
                startYear = animeAttributes?.string("startDate")?.take(4)?.toIntOrNull(),
                rating = animeAttributes?.string("averageRating")?.toDoubleOrNull()
                    ?.takeIf { it in 0.0..100.0 }
                    ?.let { percent -> (percent / 10.0 * 10).toInt() / 10.0 },
                statusWire = attributes?.string("status"),
                progress = attributes?.string("progress")?.toIntOrNull() ?: 0,
                updatedAt = attributes?.string("updatedAt"),
            )
        }
    val next = document.obj("links")?.string("next")?.takeIf(String::isNotBlank)
    return KitsuLibraryPage(entries = entries, nextUrl = next)
}

/** English title first, then the canonical one. */
private fun kitsuTitle(attributes: JsonObject): String? =
    attributes.obj("titles")?.let { titles -> titles.string("en") ?: titles.string("en_us") }?.takeIf(String::isNotBlank)
        ?: attributes.string("canonicalTitle")?.takeIf(String::isNotBlank)
        ?: attributes.obj("titles")?.string("en_jp")?.takeIf(String::isNotBlank)

internal fun kitsuCreateEntryBody(userId: String, kitsuId: Long, status: KitsuListStatus): String =
    buildJsonObject {
        putJsonObject("data") {
            put("type", "libraryEntries")
            putJsonObject("attributes") { put("status", status.wireValue) }
            putJsonObject("relationships") {
                putJsonObject("user") {
                    putJsonObject("data") {
                        put("type", "users")
                        put("id", userId)
                    }
                }
                putJsonObject("anime") {
                    putJsonObject("data") {
                        put("type", "anime")
                        put("id", kitsuId.toString())
                    }
                }
            }
        }
    }.toString()

internal fun kitsuUpdateStatusBody(entryId: String, status: KitsuListStatus): String =
    buildJsonObject {
        putJsonObject("data") {
            put("type", "libraryEntries")
            put("id", entryId)
            putJsonObject("attributes") { put("status", status.wireValue) }
        }
    }.toString()

/** Fields requested for library entries and their anime, so every call fills the same entry. */
internal val kitsuLibraryFields: Map<String, String> = mapOf(
    "include" to "anime",
    "fields[libraryEntries]" to "status,progress,updatedAt,anime",
    "fields[anime]" to "canonicalTitle,titles,posterImage,episodeCount,subtype,startDate,averageRating",
)

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
