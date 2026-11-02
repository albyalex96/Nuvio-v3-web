package com.nuvio.app.features.wetrakr

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter

private const val WETRAKR_MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024

internal enum class WeTrakrHttpMethod {
    GET,
    POST,
}

internal class WeTrakrApiException(
    val status: Int?,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun interface WeTrakrHttpEngine {
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): RawHttpResponse
}

/** Headers every WeTrakr request carries: the app key and the API version. */
internal fun weTrakrBaseHeaders(withJsonBody: Boolean): Map<String, String> = buildMap {
    put("Accept", "application/json")
    put("wetrakr-api-key", WeTrakrConfig.CLIENT_ID)
    put("wetrakr-api-version", WETRAKR_API_VERSION)
    if (withJsonBody) put("Content-Type", "application/json")
}

/**
 * Authenticated calls to the WeTrakr REST API. A 401 triggers one token refresh and a single retry;
 * a second 401 means the authorization is gone and [onUnauthorized] disconnects the account.
 */
internal class WeTrakrApiClient(
    private val engine: WeTrakrHttpEngine,
    private val accessToken: suspend () -> String?,
    private val refreshAccessToken: suspend (rejectedToken: String) -> String?,
    private val onUnauthorized: () -> Unit,
) {
    private val log = Logger.withTag("WeTrakrApi")

    suspend fun execute(
        method: WeTrakrHttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        jsonBody: String = "",
    ): RawHttpResponse {
        val token = accessToken() ?: throw WeTrakrApiException(status = null, message = "WeTrakr account not connected")
        val url = weTrakrApiUrl(path, query)
        val first = send(method, url, token, jsonBody)
        val response = if (first.status == 401) {
            log.i { "WeTrakr returned 401 for ${method.name} $path, refreshing the access token" }
            val refreshed = refreshAccessToken(token)
            if (refreshed == null) {
                onUnauthorized()
                throw WeTrakrApiException(status = 401, message = "WeTrakr authorization rejected")
            }
            send(method, url, refreshed, jsonBody).also { retried ->
                if (retried.status == 401) {
                    onUnauthorized()
                    throw WeTrakrApiException(status = 401, message = "WeTrakr authorization rejected")
                }
            }
        } else {
            first
        }
        log.d { "WeTrakr HTTP ${method.name} $path status=${response.status} bodyChars=${response.body.length}" }
        if (response.status !in 200..299) {
            throw WeTrakrApiException(status = response.status, message = "WeTrakr HTTP ${response.status} for ${method.name} $path")
        }
        return response
    }

    private suspend fun send(
        method: WeTrakrHttpMethod,
        url: String,
        token: String,
        jsonBody: String,
    ): RawHttpResponse = engine.execute(
        method = method.name,
        url = url,
        headers = weTrakrBaseHeaders(withJsonBody = jsonBody.isNotEmpty()) + ("Authorization" to "Bearer $token"),
        body = jsonBody,
    )
}

internal fun weTrakrApiUrl(path: String, query: Map<String, String> = emptyMap()): String = buildString {
    append(WETRAKR_API_BASE_URL)
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

internal fun RawHttpResponse.header(name: String): String? =
    headers.entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

internal val weTrakrHttpEngine = WeTrakrHttpEngine { method, url, headers, body ->
    httpRequestRaw(
        method = method,
        url = url,
        headers = headers,
        body = body,
        maxResponseBodyBytes = WETRAKR_MAX_RESPONSE_BODY_BYTES,
    )
}

internal object WeTrakrApi {
    val client: WeTrakrApiClient by lazy {
        WeTrakrApiClient(
            engine = weTrakrHttpEngine,
            accessToken = WeTrakrAuthRepository::validAccessToken,
            refreshAccessToken = WeTrakrAuthRepository::refreshAfterRejection,
            onUnauthorized = WeTrakrAuthRepository::onUnauthorizedResponse,
        )
    }
}
