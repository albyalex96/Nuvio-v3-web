package com.nuvio.app.core.mediaproxy

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.nuvioSharedOkHttpClient
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private val log = Logger.withTag("LanProxy")

private const val SOCKET_TIMEOUT_MS = 30_000
private const val MAX_REQUEST_HEAD_BYTES = 16 * 1024
private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
private const val MANIFEST_SNIFF_BYTES = 512
private const val MAX_REDIRECTS = 5
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val MIN_SEGMENT_BYTES = 4 * 1024L
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Interfaces that face the local network: Wi-Fi, hotspot, Ethernet, USB tethering. */
private val VIRTUAL_INTERFACE_WORDS = setOf("vmware", "virtualbox", "hyper-v", "vethernet", "docker", "wsl", "tap", "tun", "vpn")

/** Upstream response headers forwarded to the client. */
private val FORWARDED_RESPONSE_HEADERS = listOf(
    "Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag",
)

/** Headers the client may not override through [MediaProxySource.Remote.responseHeaders]. */
private val PROTECTED_RESPONSE_HEADERS = setOf(
    "content-length", "content-range", "transfer-encoding", "connection", "content-encoding",
)

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

actual object LanMediaProxy {
    actual val isSupported: Boolean = true

    private val _status = MutableStateFlow(MediaProxyStatus())
    actual val status: StateFlow<MediaProxyStatus> = _status.asStateFlow()

    private val settings: MediaProxySettings
        get() = MediaProxySettingsRepository.settings.value

    /** Redirects are followed by hand in openUpstream; timeouts come from the settings. */
    private val upstreamHttpClient: OkHttpClient by lazy {
        nuvioSharedOkHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private class Route(
        val token: String,
        val source: MediaProxySource,
        val options: MediaProxyOptions,
        /** "http://host:port" the client reaches the proxy at; used to build rewritten URLs. */
        val baseUrl: String,
        val requestHeaders: Map<String, String>,
        /** Loopback sources may only reach their own host:port (no open proxy to this device). */
        val restrictedAuthority: String?,
    ) {
        /** Diagnostics: the first media response (segment, key, file) is logged once. */
        @Volatile var firstMediaLogged = false
        @Volatile var disguisedMediaLogged = false
    }

    private val routes = ConcurrentHashMap<String, Route>()
    private val lock = Any()
    private val random = SecureRandom()
    private var serverSocket: ServerSocket? = null
    private var workers: ExecutorService? = null

    init {
        // Desktop has no Application.onCreate: the settings are loaded on first use.
        MediaProxySettingsRepository.ensureLoaded()
    }

    actual fun lanAddress(): String? = settings.lanAddressOverride.takeIf { it.isNotEmpty() } ?: detectedLanAddress()

    actual fun detectedLanAddress(): String? = interfaceLanAddress()

    // Under the server lock, so a concurrent unregister cannot stop the server being reused here.
    actual fun register(source: MediaProxySource, options: MediaProxyOptions): MediaProxyHandle? =
        synchronized(lock) { registerLocked(source, options) }

    private fun registerLocked(source: MediaProxySource, requested: MediaProxyOptions): MediaProxyHandle? {
        MediaProxySettingsRepository.ensureLoaded()
        // The global switches of the settings page can only turn the requested features off.
        val options = requested.copy(
            cors = requested.cors && settings.corsEnabled,
            rewriteManifests = requested.rewriteManifests && settings.rewriteManifests,
        )
        val host = when (options.scope) {
            MediaProxyScope.Loopback -> "127.0.0.1"
            MediaProxyScope.Lan -> lanAddress() ?: run {
                log.w { "${options.label}: no local network address, cannot serve on the LAN" }
                return null
            }
        }
        val port = ensureServer() ?: return null
        val token = newToken()
        val baseUrl = "http://$host:$port"
        val route = when (source) {
            is MediaProxySource.Remote -> {
                val uri = parseUri(source.url) ?: run {
                    log.w { "${options.label}: invalid source link" }
                    stopIfIdle()
                    return null
                }
                Route(
                    token = token,
                    source = source,
                    options = options,
                    baseUrl = baseUrl,
                    requestHeaders = source.requestHeaders.withBasicAuth(uri.rawUserInfo),
                    restrictedAuthority = uri.authorityWithoutUserInfo()
                        .takeIf { uri.host?.lowercase() in LOOPBACK_HOSTS || uri.host?.startsWith("127.") == true },
                )
            }
            is MediaProxySource.LocalFile -> Route(token, source, options, baseUrl, emptyMap(), null)
        }
        val url = when (source) {
            is MediaProxySource.Remote -> proxiedRemoteUrl(route, source.url)
            is MediaProxySource.LocalFile -> "$baseUrl/f/$token/${java.net.URLEncoder.encode(source.fileName(), Charsets.UTF_8).replace("+", "%20")}"
        }
        if (url == null) {
            stopIfIdle()
            return null
        }
        routes[token] = route
        updateLocks()
        publishStatus()
        log.i {
            val kind = when (source) {
                is MediaProxySource.Remote -> "link on ${parseUri(source.url)?.host} (${source.requestHeaders.size} headers)"
                is MediaProxySource.LocalFile -> "local file"
            }
            "${options.label}: serving $kind on the ${options.scope} (port $port, ${routes.size} active)"
        }
        return MediaProxyHandle(token, url)
    }

    actual fun unregister(handle: MediaProxyHandle) {
        val route = routes.remove(handle.token) ?: return
        log.i { "${route.options.label}: stopped serving (${routes.size} active)" }
        updateLocks()
        stopIfIdle()
        publishStatus()
    }

    // region Server lifecycle

    private fun ensureServer(): Int? = synchronized(lock) {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        return runCatching {
            // Bound to every interface: each route checks who may reach it (see Route.options.scope).
            val socket = bindServerSocket(settings.port)
            val pool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "LanMediaProxy").apply { isDaemon = true }
            }
            serverSocket = socket
            workers = pool
            pool.execute { acceptLoop(socket, pool) }
            log.i { "Proxy server started on port ${socket.localPort}" }
            socket.localPort
        }.onFailure { error ->
            log.e(error) { "Could not start the proxy server" }
        }.getOrNull()
    }

    /** Fixed port from the settings, falling back to a free one when it is taken. */
    private fun bindServerSocket(port: Int): ServerSocket {
        val anyAddress = InetAddress.getByName("0.0.0.0")
        if (port != MEDIA_PROXY_AUTO_PORT) {
            runCatching { return ServerSocket(port, 64, anyAddress).apply { reuseAddress = true } }
                .onFailure { error -> log.w(error) { "Port $port unavailable: using a free port" } }
        }
        return ServerSocket(0, 64, anyAddress)
    }

    private fun publishStatus() {
        val socket = serverSocket?.takeIf { !it.isClosed }
        _status.value = MediaProxyStatus(
            running = socket != null,
            port = socket?.localPort,
            activeSources = routes.size,
            activeLabels = routes.values.map { it.options.label }.distinct().sorted(),
        )
    }

    private fun stopIfIdle(): Unit = synchronized(lock) {
        if (routes.isNotEmpty()) return
        val socket = serverSocket ?: return
        serverSocket = null
        runCatching { socket.close() }
        workers?.shutdownNow()
        workers = null
        log.i { "Proxy server stopped" }
    }

    private fun acceptLoop(socket: ServerSocket, pool: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (error: IOException) {
                log.w(error) { "Accept failed" }
                continue
            }
            runCatching { pool.execute { handleClient(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    /** Desktop: no Wi-Fi/wake locks, the PC does not sleep the network while the app is open. */
    private fun updateLocks() = Unit

    // endregion

    // region Request handling

    private class ClientRequest(val method: String, val target: String, val headers: Map<String, String>)

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream(), COPY_BUFFER_BYTES)
                val request = readRequest(input) ?: return
                serve(client, request, output)
                output.flush()
            } catch (_: SocketException) {
                // The client closed the connection (seek, stop): normal for media players.
            } catch (error: IOException) {
                log.d { "Connection ended: ${error.message}" }
            } catch (error: Exception) {
                log.e(error) { "Unexpected proxy error" }
            }
        }
    }

    private fun readRequest(input: InputStream): ClientRequest? {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        var total = 0
        while (true) {
            val byte = input.read()
            if (byte == -1) return null
            if (++total > MAX_REQUEST_HEAD_BYTES) return null
            when (byte) {
                '\r'.code -> Unit
                '\n'.code -> {
                    if (line.isEmpty()) break
                    lines += line.toString()
                    line.clear()
                }
                else -> line.append(byte.toChar())
            }
        }
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 2) return null
        val headers = lines.drop(1).mapNotNull { header ->
            val separator = header.indexOf(':')
            if (separator <= 0) null else header.substring(0, separator).trim().lowercase() to header.substring(separator + 1).trim()
        }.toMap()
        return ClientRequest(parts[0].uppercase(), parts[1], headers)
    }

    private fun serve(client: Socket, request: ClientRequest, output: OutputStream) {
        if (request.method == "OPTIONS") {
            writeHead(output, 204, "No Content", corsHeaders())
            return
        }
        if (request.method != "GET" && request.method != "HEAD") {
            writeEmpty(output, 405, "Method Not Allowed")
            return
        }
        val path = request.target
        val kind = path.removePrefix("/").substringBefore('/')
        val afterKind = path.removePrefix("/$kind/")
        val token = afterKind.substringBefore('/').substringBefore('?')
        val route = routes[token]
        if (route == null || (kind != "p" && kind != "f")) {
            writeEmpty(output, 404, "Not Found")
            return
        }
        if (route.options.scope == MediaProxyScope.Loopback && !client.inetAddress.isLoopbackAddress) {
            writeEmpty(output, 403, "Forbidden")
            return
        }
        when (val source = route.source) {
            is MediaProxySource.Remote -> {
                if (kind != "p") return writeEmpty(output, 404, "Not Found")
                serveRemote(route, source, afterKind.removePrefix(token), request, output)
            }
            is MediaProxySource.LocalFile -> {
                if (kind != "f") return writeEmpty(output, 404, "Not Found")
                serveLocalFile(route, source, request, output)
            }
        }
    }

    /** [encodedTarget] is "/<scheme>/<authority>/<path>?<query>" as built by [proxiedRemoteUrl]. */
    private fun serveRemote(
        route: Route,
        source: MediaProxySource.Remote,
        encodedTarget: String,
        request: ClientRequest,
        output: OutputStream,
    ) {
        val withoutSlash = encodedTarget.removePrefix("/")
        val scheme = withoutSlash.substringBefore('/').lowercase()
        val afterScheme = withoutSlash.substringAfter('/', missingDelimiterValue = "")
        val authority = afterScheme.takeWhile { it != '/' && it != '?' }
        val encodedRest = afterScheme.removePrefix(authority).let { if (it.startsWith("/")) it else "/$it" }
        val (rest, hintedType) = splitSegmentExtensionHint(encodedRest)
        if ((scheme != "http" && scheme != "https") || authority.isEmpty()) {
            return writeEmpty(output, 400, "Bad Request")
        }
        if (route.restrictedAuthority != null && !authority.equals(route.restrictedAuthority, ignoreCase = true)) {
            return writeEmpty(output, 403, "Forbidden")
        }
        val response = openUpstream("$scheme://$authority$rest", route, request) ?: run {
            return writeEmpty(output, 502, "Bad Gateway")
        }
        response.use {
            val code = response.code
            val finalUrl = response.request.url.toString()
            val upstreamType = response.header("Content-Type")
            // The stream's response headers describe the link itself, not the segments and keys of a playlist.
            val isEntry = proxiedRemoteUrl(route, source.url) == proxiedRemoteUrl(route, "$scheme://$authority$rest")
            val responseHeaders = if (isEntry) source.responseHeaders else emptyMap()
            val contentType = responseHeaders.entries
                .firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value ?: upstreamType
            if (code >= 400) {
                log.w {
                    "${route.options.label}: upstream answered $code for ${describeResource(authority, rest)} " +
                        "(type=$upstreamType)"
                }
            }
            val isRange = request.headers.containsKey("range")
            val buffered = BufferedInputStream(response.body.byteStream(), COPY_BUFFER_BYTES)

            val manifest = if (
                route.options.rewriteManifests && request.method == "GET" && code == 200 && !isRange
            ) {
                detectManifest(contentType, finalUrl, buffered)
            } else {
                null
            }
            if (manifest != null) {
                serveManifest(route, manifest, "$scheme://$authority$rest", finalUrl, buffered, output)
                return
            }

            val disguised = if (
                route.options.rewriteManifests && request.method == "GET" && code == 200 && !isRange &&
                isDisguisedMediaType(contentType) &&
                // Keys and other tiny files are never segments.
                (response.header("Content-Length")?.toLongOrNull() ?: Long.MAX_VALUE) >= MIN_SEGMENT_BYTES
            ) {
                sniffDisguisedMedia(route, buffered, authority, rest, contentType)
            } else {
                null
            }

            val headers = linkedMapOf<String, String>()
            FORWARDED_RESPONSE_HEADERS.forEach { name ->
                response.header(name)?.let { headers[name] = it }
            }
            contentType?.let { headers["Content-Type"] = it }
            if (disguised != null) {
                headers["Content-Type"] = disguised.mimeType
                if (disguised.offset > 0) {
                    // Offsets no longer match the upstream file: no ranges on this response.
                    headers.remove("Accept-Ranges")
                    headers.remove("ETag")
                    response.header("Content-Length")?.toLongOrNull()
                        ?.let { headers["Content-Length"] = (it - disguised.offset).coerceAtLeast(0L).toString() }
                        ?: headers.remove("Content-Length")
                    buffered.skipFully(disguised.offset.toLong())
                }
            }
            // Segments renamed by the playlist rewrite get the type of their real container.
            if (code < 400) hintedType?.let { headers["Content-Type"] = it }
            if (code < 400 && !route.firstMediaLogged) {
                route.firstMediaLogged = true
                log.d {
                    "${route.options.label}: first media response $code for ${describeResource(authority, rest)} " +
                        "(type=$contentType, length=${response.header("Content-Length")})"
                }
            }
            responseHeaders.forEach { (name, value) ->
                if (name.lowercase() !in PROTECTED_RESPONSE_HEADERS) headers[name] = value
            }
            if (route.options.cors) headers += corsHeaders()
            writeHead(output, code, response.message.ifEmpty { "OK" }, headers)
            if (request.method == "GET") buffered.copyTo(output, COPY_BUFFER_BYTES)
        }
    }

    /**
     * Follows redirects by hand, keeping the method and the client headers on every hop. The shared
     * OkHttp client resolves through NuvioDns, so the upstream follows the DNS over HTTPS option.
     */
    private fun openUpstream(url: String, route: Route, request: ClientRequest): Response? {
        val client = upstreamHttpClient.newBuilder()
            .connectTimeout(settings.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val upstreamRequest = runCatching {
                Request.Builder()
                    .url(current)
                    .method(request.method, null)
                    // Identity encoding keeps Content-Length valid for the client (no transparent gzip).
                    .header("Accept-Encoding", "identity")
                    .apply {
                        val hasUserAgent = route.requestHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }
                        if (!hasUserAgent) {
                            header("User-Agent", settings.userAgent.ifEmpty { request.headers["user-agent"] ?: DEFAULT_USER_AGENT })
                        }
                        route.requestHeaders.forEach { (name, value) -> header(name, value) }
                        request.headers["range"]?.let { header("Range", it) }
                        request.headers["if-range"]?.let { header("If-Range", it) }
                    }
                    .build()
            }.getOrElse { error ->
                log.w(error) { "${route.options.label}: invalid upstream link" }
                return null
            }
            val response = try {
                client.newCall(upstreamRequest).execute()
            } catch (error: IOException) {
                log.w { "${route.options.label}: upstream unreachable (${upstreamRequest.url.host}): ${error.message}" }
                return null
            }
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            response.close()
            current = location?.let { resolveUrl(current, it) } ?: return null
        }
        log.w { "${route.options.label}: too many redirects" }
        return null
    }

    private enum class ManifestKind { Hls, Dash }

    private fun detectManifest(contentType: String?, url: String, body: BufferedInputStream): ManifestKind? {
        val type = contentType?.lowercase().orEmpty()
        val path = url.substringBefore('?').lowercase()
        when {
            "mpegurl" in type || path.endsWith(".m3u8") || path.endsWith(".m3u") -> return ManifestKind.Hls
            "dash+xml" in type || path.endsWith(".mpd") -> return ManifestKind.Dash
            type.startsWith("video/") || type.startsWith("audio/") -> return null
        }
        // Many IPTV and addon links serve playlists as text/plain or octet-stream: peek at the body.
        body.mark(MANIFEST_SNIFF_BYTES)
        val head = ByteArray(MANIFEST_SNIFF_BYTES)
        val read = body.read(head).coerceAtLeast(0)
        body.reset()
        val text = String(head, 0, read, Charsets.UTF_8).trimStart('﻿', ' ', '\r', '\n', '\t')
        return when {
            text.startsWith("#EXTM3U") -> ManifestKind.Hls
            "<MPD" in text -> ManifestKind.Dash
            else -> null
        }
    }

    /**
     * Finds the real media in a segment served as a page or image (see [findDisguisedMediaStart]).
     * The stream is left at its start; null when nothing recognizable is found.
     */
    private fun sniffDisguisedMedia(
        route: Route,
        body: BufferedInputStream,
        authority: String,
        rest: String,
        contentType: String?,
    ): MediaStart? {
        body.mark(DISGUISED_MEDIA_SNIFF_BYTES)
        val head = ByteArray(DISGUISED_MEDIA_SNIFF_BYTES)
        var read = 0
        while (read < head.size) {
            val count = body.read(head, read, head.size - read)
            if (count <= 0) break
            read += count
        }
        body.reset()
        val start = findDisguisedMediaStart(head, read)
        if (!route.disguisedMediaLogged) {
            route.disguisedMediaLogged = true
            val prefix = head.take(minOf(read, 8)).joinToString("") { "%02x".format(it) }
            if (start != null) {
                log.i {
                    "${route.options.label}: segment disguised as $contentType on ${describeResource(authority, rest)}: " +
                        "serving it as ${start.mimeType}, skipping ${start.offset} bytes of fake header (starts with $prefix)"
                }
            } else {
                log.w {
                    "${route.options.label}: no media found in a $contentType response on " +
                        "${describeResource(authority, rest)} (starts with $prefix, maybe encrypted)"
                }
            }
        }
        return start
    }

    private fun serveManifest(
        route: Route,
        kind: ManifestKind,
        requestedUrl: String,
        finalUrl: String,
        body: InputStream,
        output: OutputStream,
    ) {
        val bytes = body.readLimited(MAX_MANIFEST_BYTES) ?: run {
            log.w { "${route.options.label}: manifest too large to rewrite" }
            return writeEmpty(output, 502, "Bad Gateway")
        }
        val text = bytes.toString(Charsets.UTF_8)
        val proxify: (String) -> String = { absolute -> proxiedRemoteUrl(route, absolute) ?: absolute }
        val proxifyWithExtension: (String, String) -> String = { absolute, extension ->
            proxiedRemoteUrl(route, absolute, extension) ?: absolute
        }
        val rewritten = when (kind) {
            ManifestKind.Hls -> rewriteHlsPlaylist(text, finalUrl, proxify, proxifyWithExtension)
            ManifestKind.Dash -> rewriteDashManifest(text, requestedUrl, finalUrl, proxify)
        }.toByteArray(Charsets.UTF_8)
        val headers = linkedMapOf(
            "Content-Type" to if (kind == ManifestKind.Hls) "application/vnd.apple.mpegurl" else "application/dash+xml",
            "Content-Length" to rewritten.size.toString(),
            "Cache-Control" to "no-cache",
        )
        if (route.options.cors) headers += corsHeaders()
        log.d { "${route.options.label}: rewrote $kind manifest (${bytes.size} -> ${rewritten.size} bytes)" }
        writeHead(output, 200, "OK", headers)
        output.write(rewritten)
    }

    private fun serveLocalFile(route: Route, source: MediaProxySource.LocalFile, request: ClientRequest, output: OutputStream) {
        val media = openLocalMedia(source.uri) ?: run {
            log.w { "${route.options.label}: local file not readable" }
            return writeEmpty(output, 404, "Not Found")
        }
        media.use {
            val length = media.length
            val range = request.headers["range"]?.let { parseRange(it, length) }
            if (request.headers.containsKey("range") && range == null && length > 0) {
                val headers = linkedMapOf("Content-Range" to "bytes */$length")
                if (route.options.cors) headers += corsHeaders()
                return writeHead(output, 416, "Range Not Satisfiable", headers)
            }
            val start = range?.first ?: 0L
            val end = range?.last ?: (length - 1)
            val headers = linkedMapOf(
                "Content-Type" to mimeTypeFor(source.fileName()),
                "Accept-Ranges" to "bytes",
                "Content-Length" to (end - start + 1).coerceAtLeast(0L).toString(),
            )
            if (range != null) headers["Content-Range"] = "bytes $start-$end/$length"
            if (route.options.cors) headers += corsHeaders()
            if (range != null) writeHead(output, 206, "Partial Content", headers) else writeHead(output, 200, "OK", headers)
            if (request.method == "GET") media.copyRange(start, end - start + 1, output)
        }
    }

    // endregion

    // region Local files

    private class LocalMedia(
        val length: Long,
        private val randomAccess: RandomAccessFile?,
    ) : AutoCloseable {
        fun copyRange(start: Long, count: Long, output: OutputStream) {
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var remaining = count
            if (randomAccess != null) {
                randomAccess.seek(start)
                while (remaining > 0) {
                    val read = randomAccess.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }

        override fun close() {
            runCatching { randomAccess?.close() }
        }
    }

    private fun openLocalMedia(uri: String): LocalMedia? = runCatching {
        when {
            else -> {
                val file = if (uri.startsWith("file:", ignoreCase = true)) File(URI(uri)) else File(uri)
                if (!file.isFile || !file.canRead()) return null
                LocalMedia(file.length(), RandomAccessFile(file, "r"))
            }
        }
    }.onFailure { error -> log.w(error) { "Could not open the local file" } }.getOrNull()

    // endregion

    // region URLs and helpers

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * "<base>/p/<token>/<scheme>/<authority><path>?<query>": relative URLs keep resolving through
     * the proxy. With [extensionHint] the path ends with an extra "/[SEGMENT_HINT_MARKER]<ext>"
     * (see [splitSegmentExtensionHint]), removed again before the upstream request.
     */
    private fun proxiedRemoteUrl(route: Route, absoluteUrl: String, extensionHint: String? = null): String? {
        val uri = parseUri(absoluteUrl) ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val authority = uri.authorityWithoutUserInfo() ?: return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val hint = extensionHint?.let { "/$SEGMENT_HINT_MARKER$it" }.orEmpty()
        return "${route.baseUrl}/p/${route.token}/$scheme/$authority$path$hint$query"
    }

    private fun corsHeaders(): Map<String, String> = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
        "Access-Control-Allow-Headers" to "Range, Content-Type, Accept, Origin, If-Range",
        "Access-Control-Expose-Headers" to "Content-Length, Content-Range, Accept-Ranges, Content-Type",
    )

    private fun writeHead(output: OutputStream, code: Int, reason: String, headers: Map<String, String>) {
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeEmpty(output: OutputStream, code: Int, reason: String) {
        writeHead(output, code, reason, corsHeaders() + ("Content-Length" to "0"))
    }

    // endregion

    // region Network address

    /** First active physical LAN adapter (Windows names them ethernet_/wireless_, not wlan/eth). */
    private fun interfaceLanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { iface ->
                iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                    "${iface.name} ${iface.displayName}".lowercase()
                        .split(Regex("[^a-z0-9-]+")).toSet()
                        .none { it in VIRTUAL_INTERFACE_WORDS }
            }
            .firstNotNullOfOrNull { iface ->
                iface.inetAddresses.toList()
                    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                    ?.hostAddress
            }
    }.getOrNull()

    // endregion
}

private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

/** Parses links leniently: addon links sometimes contain spaces or pipes. */
/** Last path segment added to renamed HLS segments, followed by their real extension. */
private const val SEGMENT_HINT_MARKER = "nuvio-segment."

private val SEGMENT_HINT_TYPES = mapOf(
    "ts" to "video/mp2t",
    "m4s" to "video/mp4",
    "mp4" to "video/mp4",
)

/** Splits "<path>/nuvio-segment.<ext>?<query>" into the upstream "<path>?<query>" and the type of <ext>. */
internal fun splitSegmentExtensionHint(pathAndQuery: String): Pair<String, String?> {
    val path = pathAndQuery.substringBefore('?')
    val query = pathAndQuery.substring(path.length)
    val marker = path.lastIndexOf("/$SEGMENT_HINT_MARKER")
    if (marker < 0) return pathAndQuery to null
    val type = SEGMENT_HINT_TYPES[path.substring(marker + SEGMENT_HINT_MARKER.length + 1)] ?: return pathAndQuery to null
    return (path.substring(0, marker).ifEmpty { "/" } + query) to type
}

/** Host and file extension of a proxied resource, for the logs (never the full link: it may hold tokens). */
private fun describeResource(authority: String, pathAndQuery: String): String {
    val name = pathAndQuery.substringBefore('?').substringAfterLast('/')
    val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 } ?: "no-ext"
    return "${authority.substringBefore(':')} [.$extension]"
}

private fun InputStream.skipFully(count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining--
        } else {
            return
        }
    }
}

private fun parseUri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()
    ?: runCatching { URI(url.trim().replace(" ", "%20").replace("|", "%7C")) }.getOrNull()

private fun URI.authorityWithoutUserInfo(): String? {
    val host = host ?: return null
    return if (port != -1) "$host:$port" else host
}

private fun Map<String, String>.withBasicAuth(rawUserInfo: String?): Map<String, String> {
    if (rawUserInfo.isNullOrEmpty() || keys.any { it.equals("authorization", ignoreCase = true) }) return this
    val credentials = java.net.URLDecoder.decode(rawUserInfo.replace("+", "%2B"), Charsets.UTF_8)
    return this + ("Authorization" to "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.toByteArray()))
}

private fun MediaProxySource.LocalFile.fileName(): String =
    java.net.URLDecoder.decode(uri.substringBefore('?').substringAfterLast('/').replace("+", "%2B"), Charsets.UTF_8).ifBlank { "media" }

private fun mimeTypeFor(fileName: String): String {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        "mkv" -> "video/x-matroska"
        "ts" -> "video/mp2t"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> java.net.URLConnection.guessContentTypeFromName("x.$extension") ?: "application/octet-stream"
    }
}

/** Single "bytes=a-b", "bytes=a-" or "bytes=-n" range clamped to [length]; null when unsatisfiable. */
internal fun parseRange(header: String, length: Long): LongRange? {
    if (length <= 0) return null
    val spec = header.trim().removePrefix("bytes=").substringBefore(',').trim()
    val startText = spec.substringBefore('-').trim()
    val endText = spec.substringAfter('-', missingDelimiterValue = "").trim()
    val range = when {
        startText.isEmpty() -> {
            val suffix = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            (length - suffix).coerceAtLeast(0L)..(length - 1)
        }
        else -> {
            val start = startText.toLongOrNull() ?: return null
            val end = endText.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
            start..end
        }
    }
    return range.takeIf { it.first in 0 until length && it.first <= it.last }
}

private fun InputStream.readLimited(limit: Int): ByteArray? {
    val bytes = readNBytesCompat(limit + 1)
    return bytes.takeIf { it.size <= limit }
}

private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    while (out.size() < max) {
        val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (read <= 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

internal actual object MediaProxySettingsStorage {
    private const val PREFERENCES_NAME = "nuvio_media_proxy"
    private const val SETTINGS_KEY = "media_proxy_settings"

    actual fun load(): String? =
        com.nuvio.app.desktop.compat.DesktopContext.getSharedPreferences(PREFERENCES_NAME, 0).getString(SETTINGS_KEY, null)

    actual fun save(json: String) {
        com.nuvio.app.desktop.compat.DesktopContext.getSharedPreferences(PREFERENCES_NAME, 0).edit().putString(SETTINGS_KEY, json).apply()
    }
}
