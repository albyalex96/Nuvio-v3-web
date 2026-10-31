package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val ANILIST_MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024

internal class AniListApiException(
    val status: Int?,
    override val message: String,
    /** True when AniList answered "Not Found" (for example a media that is not in the list). */
    val isNotFound: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun interface AniListHttpEngine {
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): RawHttpResponse
}

/**
 * Authenticated calls to the AniList GraphQL API. AniList tokens cannot be refreshed: a 401 means
 * the authorization is gone and [onUnauthorized] disconnects the account.
 */
internal class AniListApiClient(
    private val engine: AniListHttpEngine,
    private val accessToken: () -> String?,
    private val onUnauthorized: () -> Unit,
) {
    private val log = Logger.withTag("AniListApi")
    private val json = Json { ignoreUnknownKeys = true }

    /** Runs [query] and returns its `data` object. */
    suspend fun query(
        operation: String,
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        allowNotFound: Boolean = false,
    ): JsonObject? {
        val token = accessToken() ?: throw AniListApiException(status = null, message = "AniList account not connected")
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()
        val response = engine.execute(
            method = "POST",
            url = ANILIST_GRAPHQL_URL,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
            body = body,
        )
        log.d { "AniList $operation status=${response.status} bodyChars=${response.body.length}" }
        if (response.status == 401) {
            log.w { "AniList returned 401 for $operation" }
            onUnauthorized()
            throw AniListApiException(status = 401, message = "AniList authorization rejected")
        }
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
        val errors = root?.get("errors") as? JsonArray
        val notFound = errors?.any { error ->
            (error as? JsonObject)?.get("status")?.jsonPrimitive?.intOrNull == 404
        } == true || response.status == 404
        if (notFound && allowNotFound) return null
        if (response.status !in 200..299 || !errors.isNullOrEmpty()) {
            val message = (errors?.firstOrNull() as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            throw AniListApiException(
                status = response.status,
                message = "AniList HTTP ${response.status} for $operation${message?.let { ": $it" }.orEmpty()}",
                isNotFound = notFound,
            )
        }
        return root?.get("data")?.takeUnless { it is JsonNull } as? JsonObject
    }
}

internal val aniListHttpEngine = AniListHttpEngine { method, url, headers, body ->
    httpRequestRaw(
        method = method,
        url = url,
        headers = headers,
        body = body,
        maxResponseBodyBytes = ANILIST_MAX_RESPONSE_BODY_BYTES,
    )
}

internal object AniListApi {
    val client: AniListApiClient by lazy {
        AniListApiClient(
            engine = aniListHttpEngine,
            accessToken = AniListAuthRepository::validAccessToken,
            onUnauthorized = AniListAuthRepository::onUnauthorizedResponse,
        )
    }
}

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject
