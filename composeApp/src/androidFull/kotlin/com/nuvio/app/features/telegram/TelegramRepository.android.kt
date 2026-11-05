package com.nuvio.app.features.telegram

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File

data class TelegramVideoMessage(
    val messageId: Long,
    val chatId: Long,
    val fileName: String,
    val fileId: Int,
    val fileSize: Long,
    val duration: Int,
    val mimeType: String,
    val caption: String,
)

/** Telegram session, cache and message search, used by the settings page and the stream resolver. */
object TelegramRepository {
    private val log = Logger.withTag("Telegram")
    @Volatile private var appContext: Context? = null

    val authState: StateFlow<TelegramAuthState> get() = TelegramClient.authState

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        // Called from MainActivity.onCreate: the file checks and the cleanup run off the main
        // thread so they never delay the first frame (TDLib itself already starts in background).
        startupScope.launch {
            if (sessionMarker(ctx).exists()) {
                log.i { "Restoring the saved Telegram session" }
                TelegramClient.initialize(ctx)
            } else {
                // A login that was never completed leaves a half-written database behind.
                wipeTdlibFiles(ctx)
            }
        }
    }

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun sessionMarker(context: Context) = File(context.filesDir, TelegramClient.TDLIB_SESSION_MARKER)

    fun isAuthenticated(): Boolean = TelegramClient.authState.value is TelegramAuthState.Ready

    fun isClientReady(): Boolean = TelegramClient.isClientReady()

    suspend fun awaitClient() = TelegramClient.awaitClient()

    fun startAuth() {
        appContext?.let(TelegramClient::initialize)
    }

    fun retry() {
        appContext?.let(TelegramClient::retry)
    }

    fun submitPhone(phone: String) = TelegramClient.submitPhone(phone)
    fun submitCode(code: String) = TelegramClient.submitCode(code)
    fun submitPassword(password: String) = TelegramClient.submitPassword(password)

    fun requestQrLogin() = TelegramClient.requestQrLogin()

    /**
     * Approves, with the account logged in here, the QR login [link] of another device
     * (the TV): returns null on success, the Telegram error message otherwise.
     */
    suspend fun confirmQrLogin(link: String): String? {
        if (!isAuthenticated()) return "Telegram is not connected on this device"
        if (!link.startsWith("tg://login?token=")) return "Invalid login link"
        return try {
            TelegramClient.sendRequest(TdApi.ConfirmQrCodeAuthentication(link))
                ?: return "Telegram did not answer"
            log.i { "QR login of another device approved" }
            null
        } catch (error: TelegramApiException) {
            log.w(error) { "QR login approval failed" }
            error.message ?: "Telegram error"
        }
    }

    fun disconnect() {
        log.i { "Disconnecting the Telegram account" }
        TelegramStreamingProxy.stop()
        TelegramClient.reset(logOut = true)
        appContext?.let { sessionMarker(it).delete() }
    }

    private fun wipeTdlibFiles(context: Context) {
        // A login started in the meantime owns the files now.
        if (TelegramClient.authState.value !is TelegramAuthState.Idle) return
        sessionMarker(context).delete()
        File(context.filesDir, TelegramClient.TDLIB_DB_DIR).deleteRecursively()
        File(context.filesDir, TelegramClient.TDLIB_FILES_DIR).deleteRecursively()
    }

    fun getCacheSize(): Long {
        val ctx = appContext ?: return 0L
        val dir = File(ctx.filesDir, TelegramClient.TDLIB_FILES_DIR)
        return if (dir.exists()) dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    fun clearCache() {
        val ctx = appContext ?: return
        File(ctx.filesDir, TelegramClient.TDLIB_FILES_DIR).listFiles()?.forEach { it.deleteRecursively() }
        log.i { "Telegram media cache cleared" }
    }

    suspend fun searchVideoMessages(query: String, limit: Int = 50): List<TelegramVideoMessage> {
        if (!isAuthenticated()) return emptyList()

        val filters = listOf(TdApi.SearchMessagesFilterDocument(), TdApi.SearchMessagesFilterVideo())
        val seen = mutableSetOf<Pair<String, Long>>()
        val results = mutableListOf<TelegramVideoMessage>()

        for (filter in filters) {
            val found = TelegramClient.sendRequest(TdApi.SearchMessages().also { req ->
                req.chatList = null
                req.query = query
                req.offset = ""
                req.limit = limit
                req.filter = filter
            }) as? TdApi.FoundMessages ?: continue

            for (msg in found.messages) {
                val video = when (val content = msg.content) {
                    is TdApi.MessageDocument -> {
                        val mime = content.document.mimeType
                        if (!mime.startsWith("video/") && mime != "application/x-matroska") continue
                        TelegramVideoMessage(
                            messageId = msg.id,
                            chatId = msg.chatId,
                            fileName = content.document.fileName,
                            fileId = content.document.document.id,
                            fileSize = content.document.document.size,
                            duration = 0,
                            mimeType = mime,
                            caption = content.caption.text,
                        )
                    }
                    is TdApi.MessageVideo -> TelegramVideoMessage(
                        messageId = msg.id,
                        chatId = msg.chatId,
                        fileName = content.video.fileName,
                        fileId = content.video.video.id,
                        fileSize = content.video.video.size,
                        duration = content.video.duration,
                        mimeType = content.video.mimeType,
                        caption = content.caption.text,
                    )
                    else -> continue
                }
                if (!isPlayableVideo(video)) {
                    log.d { "Skipping Telegram media '${video.fileName}' mime=${video.mimeType} size=${video.fileSize} duration=${video.duration}" }
                    continue
                }
                if (seen.add(video.fileName to video.fileSize)) results.add(video)
            }
        }
        log.d { "Telegram search '$query' videos=${results.size}" }
        return results
    }

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "svg")
    private val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg")

    /** Drops images, GIFs and short clips (GIFs are often re-encoded by Telegram as short mp4 videos). */
    private fun isPlayableVideo(video: TelegramVideoMessage): Boolean {
        val mime = video.mimeType.lowercase()
        val ext = video.fileName.substringAfterLast('.', "").lowercase()
        if (mime.startsWith("image/") || ext in imageExtensions) return false
        val looksLikeVideo = mime.startsWith("video/") || mime == "application/x-matroska" || ext in videoExtensions
        if (!looksLikeVideo) return false
        if (video.duration in 1 until MIN_DURATION_SECONDS) return false
        if (video.fileSize in 1 until MIN_FILE_SIZE_BYTES) return false
        return true
    }

    private const val MIN_DURATION_SECONDS = 120
    private const val MIN_FILE_SIZE_BYTES = 20L * 1024 * 1024

    fun getStreamUrl(fileId: Int): String = TelegramStreamingProxy.getUrl(fileId)
}
