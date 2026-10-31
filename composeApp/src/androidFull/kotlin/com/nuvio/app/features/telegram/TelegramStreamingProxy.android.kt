package com.nuvio.app.features.telegram

import co.touchlab.kermit.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.net.ServerSocket

/**
 * Local HTTP server (127.0.0.1 only) that serves Telegram files to the player with range
 * requests, downloading the requested parts through TDLib.
 */
object TelegramStreamingProxy {
    private val log = Logger.withTag("Telegram")
    private const val HOST = "127.0.0.1"
    private const val CHUNK_SIZE = 2 * 1024 * 1024
    private const val PREFETCH_SIZE = 20 * 1024 * 1024L
    private const val DOWNLOAD_TIMEOUT_MS = 30_000L
    private const val DOWNLOAD_PRIORITY = 32
    private const val POLL_INTERVAL_MS = 100L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var port: Int = 0
    @Volatile private var server: EmbeddedServer<*, *>? = null
    @Volatile private var lastStreamedFileId: Int? = null

    private fun start() {
        synchronized(lock) {
            if (server != null) return
            port = findFreePort()
            server = embeddedServer(CIO, port = port, host = HOST) {
                routing {
                    get("/file/{fileId}") {
                        val fileId = call.parameters["fileId"]?.toIntOrNull()
                        val rangeHeader = call.request.headers[HttpHeaders.Range]
                        log.d { "Stream request fileId=$fileId range=$rangeHeader" }
                        if (fileId == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }

                        // Only one file is played at a time: drop the previous one from the cache.
                        val previous = lastStreamedFileId
                        if (previous != null && previous != fileId) scope.launch { deleteFile(previous) }
                        lastStreamedFileId = fileId

                        val totalSize = fileSize(fileId)
                        if (totalSize <= 0L) {
                            log.w { "Telegram file $fileId has no known size" }
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }

                        val (rangeStart, rangeEnd) = parseRange(rangeHeader)
                        val start = (rangeStart ?: 0L).coerceIn(0L, totalSize - 1L)
                        val end = (rangeEnd ?: (totalSize - 1L)).coerceIn(start, totalSize - 1L)

                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalSize")
                        val status = if (rangeHeader != null) HttpStatusCode.PartialContent else HttpStatusCode.OK

                        call.respondBytesWriter(
                            contentType = ContentType.Video.Any,
                            status = status,
                            contentLength = end - start + 1,
                        ) {
                            var offset = start
                            while (offset <= end) {
                                val chunkSize = minOf(CHUNK_SIZE.toLong(), end - offset + 1).toInt()
                                val bytes = downloadChunk(fileId, offset, chunkSize)
                                if (bytes == null || bytes.isEmpty()) {
                                    log.w { "Telegram download stalled fileId=$fileId offset=$offset" }
                                    break
                                }
                                writeFully(bytes)
                                offset += bytes.size
                            }
                        }
                    }
                }
            }
            server!!.start(wait = false)
            log.i { "Telegram streaming proxy started on $HOST:$port" }
        }
    }

    fun stop() {
        synchronized(lock) {
            lastStreamedFileId?.let { id -> scope.launch { deleteFile(id) } }
            lastStreamedFileId = null
            server?.stop(0, 0)
            server = null
        }
        log.i { "Telegram streaming proxy stopped" }
    }

    fun getUrl(fileId: Int): String {
        start()
        return "http://$HOST:$port/file/$fileId"
    }

    private suspend fun deleteFile(fileId: Int) {
        runCatching {
            TelegramClient.sendRequest(TdApi.CancelDownloadFile().also { req ->
                req.fileId = fileId
                req.onlyIfPending = false
            })
        }
        runCatching { TelegramClient.sendRequest(TdApi.DeleteFile().also { it.fileId = fileId }) }
            .onSuccess { log.d { "Deleted cached Telegram file $fileId" } }
    }

    private suspend fun downloadChunk(fileId: Int, offset: Long, limit: Int): ByteArray? {
        try {
            TelegramClient.sendRequest(TdApi.DownloadFile().also { req ->
                req.fileId = fileId
                req.priority = DOWNLOAD_PRIORITY
                req.offset = offset
                req.limit = PREFETCH_SIZE
                req.synchronous = false
            }, DOWNLOAD_TIMEOUT_MS)

            val ready = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                var available = false
                while (!available) {
                    val local = (TelegramClient.sendRequest(TdApi.GetFile(fileId)) as? TdApi.File)?.local
                    available = local != null &&
                        (local.isDownloadingCompleted || local.downloadedPrefixSize >= limit)
                    if (!available) delay(POLL_INTERVAL_MS)
                }
                true
            }
            if (ready != true) return null

            val data = TelegramClient.sendRequest(TdApi.ReadFilePart(fileId, offset, limit.toLong())) as? TdApi.Data
            return data?.data?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.e(e) { "Telegram chunk download failed fileId=$fileId offset=$offset" }
            return null
        }
    }

    private suspend fun fileSize(fileId: Int): Long {
        val file = runCatching { TelegramClient.sendRequest(TdApi.GetFile(fileId)) as? TdApi.File }
            .getOrNull() ?: return 0L
        return file.size.takeIf { it > 0 } ?: file.expectedSize
    }

    private fun parseRange(header: String?): Pair<Long?, Long?> {
        if (header == null || !header.startsWith("bytes=")) return null to null
        val parts = header.removePrefix("bytes=").substringBefore(',').split("-")
        return parts.getOrNull(0)?.trim()?.toLongOrNull() to parts.getOrNull(1)?.trim()?.toLongOrNull()
    }

    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
}
