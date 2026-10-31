package com.nuvio.app.features.telegram

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.StateFlow
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
        if (sessionMarker(ctx).exists()) {
            log.i { "Restoring the saved Telegram session" }
            TelegramClient.initialize(ctx)
        } else {
            // A login that was never completed leaves a half-written database behind.
            wipeTdlibFiles(ctx)
        }
    }

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

    fun disconnect() {
        log.i { "Disconnecting the Telegram account" }
        TelegramStreamingProxy.stop()
        TelegramClient.reset(logOut = true)
        appContext?.let { sessionMarker(it).delete() }
    }

    private fun wipeTdlibFiles(context: Context) {
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
                if (seen.add(video.fileName to video.fileSize)) results.add(video)
            }
        }
        log.d { "Telegram search '$query' videos=${results.size}" }
        return results
    }

    fun getStreamUrl(fileId: Int): String = TelegramStreamingProxy.getUrl(fileId)
}
