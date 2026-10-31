package com.nuvio.app.features.kitsu

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter

private const val KITSU_MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024
private const val KITSU_JSON_API = "application/vnd.api+json"

internal enum class KitsuHttpMethod {
    GET,
    POST,
    PATCH,
    DELETE,
}

internal class KitsuApiException(
    val status: Int?,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun interface KitsuHttpEngine {
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): RawHttpResponse
}

/**
 * Authenticated calls to the Kitsu JSON:API. A 401 triggers one token refresh and a single retry;
 * a second 401 means the authorization is gone and [onUnauthorized] disconnects the account.
 */
internal class KitsuApiClient(
    private val engine: KitsuHttpEngine,
    private val accessToken: suspend () -> String?,
    private val refreshAccessToken: suspend (rejectedToken: String) -> String?,
    private val onUnauthorized: () -> Unit,
) {
    private val log = Logger.withTag("KitsuApi")

    suspend fun execute(
        method: KitsuHttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        jsonBody: String = "",
        allowNotFound: Boolean = false,
    ): RawHttpResponse {
        val token = accessToken() ?: throw KitsuApiException(status = null, message = "Kitsu account not connected")
        val url = if (path.startsWith("https://")) path else kitsuApiUrl(path, query)
        val first = send(method, url, token, jsonBody)
        val response = if (first.status == 401) {
            log.i { "Kitsu returned 401 for ${method.name} ${path.substringBefore('?')}, refreshing the access token" }
            val refreshed = refreshAccessToken(token)
            if (refreshed == null) {
                onUnauthorized()
                throw KitsuApiException(status = 401, message = "Kitsu authorization rejected")
            }
            send(method, url, refreshed, jsonBody).also { retried ->
                if (retried.status == 401) {
                    onUnauthorized()
                    throw KitsuApiException(status = 401, message = "Kitsu authorization rejected")
                }
            }
        } else {
            first
        }
        log.d { "Kitsu HTTP ${method.name} ${path.substringBefore('?')} status=${response.status} bodyChars=${response.body.length}" }
        if (response.status !in 200..299 && !(allowNotFound && response.status == 404)) {
            throw KitsuApiException(status = response.status, message = "Kitsu HTTP ${response.status} for ${method.name} ${path.substringBefore('?')}")
        }
        return response
    }

    private suspend fun send(
        method: KitsuHttpMethod,
        url: String,
        token: String,
        jsonBody: String,
    ): RawHttpResponse = engine.execute(
        method = method.name,
        url = url,
        headers = buildMap {
            put("Authorization", "Bearer $token")
            put("Accept", KITSU_JSON_API)
            if (jsonBody.isNotEmpty()) put("Content-Type", KITSU_JSON_API)
        },
        body = jsonBody,
    )
}

internal fun kitsuApiUrl(path: String, query: Map<String, String>): String = buildString {
    append(KITSU_API_BASE_URL)
    append(if (path.startsWith('/')) path else "/$path")
    if (query.isNotEmpty()) {
        append('?')
        append(
            query.entries.joinToString("&") { (key, value) ->
                "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
            },
        )
    }
}

internal val kitsuHttpEngine = KitsuHttpEngine { method, url, headers, body ->
    httpRequestRaw(
        method = method,
        url = url,
        headers = headers,
        body = body,
        maxResponseBodyBytes = KITSU_MAX_RESPONSE_BODY_BYTES,
    )
}

internal object KitsuApi {
    val client: KitsuApiClient by lazy {
        KitsuApiClient(
            engine = kitsuHttpEngine,
            accessToken = KitsuAuthRepository::validAccessToken,
            refreshAccessToken = KitsuAuthRepository::refreshAfterRejection,
            onUnauthorized = KitsuAuthRepository::onUnauthorizedResponse,
        )
    }
}
