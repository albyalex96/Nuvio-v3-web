package com.nuvio.app.desktop.mpv

import co.touchlab.kermit.Logger
import com.nuvio.app.desktop.compat.toNativeMediaLocation
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One libmpv instance rendered with the software render API: every frame is drawn by mpv
 * (video, scaling, letterboxing and libass subtitles) into a memory buffer, then copied into a
 * Skia bitmap that Compose draws. This keeps the Compose player controls on top of the video.
 */
internal class MpvPlayer private constructor(
    private val mpv: LibMpv,
    private val handle: Pointer,
) {
    private val log = Logger.withTag(TAG)
    private val released = AtomicBoolean(false)
    private val frameSignal = Semaphore(0)
    private val targetSize = AtomicReference(Size(0, 0))
    private val errorMessage = AtomicReference<String?>(null)
    private val pendingAfterLoad = AtomicReference<(() -> Unit)?>(null)

    /** Two bitmaps used alternately: mpv writes one while Compose draws the other. */
    private val bitmaps = arrayOf(Bitmap(), Bitmap())
    private val frontIndex = AtomicInteger(-1)
    private val frameCounter = AtomicInteger(0)

    @Volatile
    var onFrame: (() -> Unit)? = null

    // Kept as a field: JNA callbacks must stay strongly reachable while mpv can call them.
    private val updateCallback = LibMpv.RenderUpdateCallback { frameSignal.release() }

    private val renderThread = Thread(::renderLoop, "nuvio-mpv-render").apply { isDaemon = true }
    private val eventThread = Thread(::eventLoop, "nuvio-mpv-events").apply { isDaemon = true }

    data class Size(val width: Int, val height: Int)

    /** Latest complete frame (null before the first one). Read on the UI thread. */
    fun currentFrame(): Bitmap? = frontIndex.get().takeIf { it >= 0 }?.let { bitmaps[it] }

    fun frameNumber(): Int = frameCounter.get()

    fun lastError(): String? = errorMessage.get()

    fun setTargetSize(width: Int, height: Int) {
        val capped = capToRenderBudget(width, height)
        if (targetSize.getAndSet(capped) != capped) frameSignal.release()
    }

    private fun start() {
        renderThread.start()
        eventThread.start()
    }

    // ---- Commands -----------------------------------------------------------------------

    fun load(
        url: String,
        audioUrl: String?,
        headers: Map<String, String>,
        startSeconds: Double?,
        afterLoad: () -> Unit,
    ) {
        if (released.get()) return
        errorMessage.set(null)
        setProperty("http-header-fields", headers.entries.joinToString(",") { (k, v) -> escapeListItem("$k: $v") })
        setProperty("start", startSeconds?.takeIf { it > 0.0 }?.let { "+$it" } ?: "none")
        setProperty("audio-files", audioUrl?.toNativeMediaLocation().orEmpty())
        pendingAfterLoad.set(afterLoad)
        log.i { "Loading media (audio track: ${audioUrl != null}, start: ${startSeconds ?: 0.0}s)" }
        command("loadfile", url.toNativeMediaLocation(), "replace")
    }

    fun command(vararg args: String): Int {
        if (released.get()) return -1
        val result = mpv.mpv_command(handle, arrayOf(*args, null))
        if (result < 0) log.w { "Command ${args.firstOrNull()} failed: ${mpv.mpv_error_string(result)}" }
        return result
    }

    fun setProperty(name: String, value: String): Int {
        if (released.get()) return -1
        val result = mpv.mpv_set_property_string(handle, name, value)
        if (result < 0) log.d { "Property $name not set: ${mpv.mpv_error_string(result)}" }
        return result
    }

    fun getProperty(name: String): String? {
        if (released.get()) return null
        val pointer = mpv.mpv_get_property_string(handle, name) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            mpv.mpv_free(pointer)
        }
    }

    fun doubleProperty(name: String): Double? = getProperty(name)?.toDoubleOrNull()

    fun flagProperty(name: String): Boolean = getProperty(name) == "yes"

    fun release() {
        if (!released.compareAndSet(false, true)) return
        log.i { "Releasing player" }
        frameSignal.release()
        mpv.mpv_wakeup(handle)
        runCatching { renderThread.join(2_000) }
        runCatching { eventThread.join(2_000) }
        mpv.mpv_terminate_destroy(handle)
        bitmaps.forEach { runCatching { it.close() } }
    }

    // ---- Threads ------------------------------------------------------------------------

    private fun renderLoop() {
        val contextRef = PointerByReference()
        val apiType = Memory(3).apply { setString(0, "sw", "US-ASCII") }
        val createParams = renderParams(LibMpv.RENDER_PARAM_API_TYPE to apiType)
        val created = mpv.mpv_render_context_create(contextRef, handle, createParams)
        if (created < 0) {
            val message = "mpv render context failed: ${mpv.mpv_error_string(created)}"
            log.e { message }
            errorMessage.set(message)
            return
        }
        val context = contextRef.value
        mpv.mpv_render_context_set_update_callback(context, updateCallback, null)
        log.i { "Software render context ready" }

        val format = Memory(5).apply { setString(0, "bgr0", "US-ASCII") }
        val sizeParam = Memory(8)
        val strideParam = Memory(Native.SIZE_T_SIZE.toLong())
        var buffer: Memory? = null
        var pixels = ByteArray(0)
        var renderedSize = Size(0, 0)
        var backIndex = 0

        try {
            while (!released.get()) {
                frameSignal.tryAcquire(250, TimeUnit.MILLISECONDS)
                if (released.get()) break
                val flags = mpv.mpv_render_context_update(context)
                val size = targetSize.get()
                val sizeChanged = size != renderedSize
                if ((flags and LibMpv.RENDER_UPDATE_FRAME) == 0L && !sizeChanged) continue
                if (size.width <= 0 || size.height <= 0) continue

                val stride = ((size.width * 4) + 63) / 64 * 64
                val bytes = stride.toLong() * size.height
                if (buffer == null || buffer.size() < bytes) {
                    buffer = Memory(bytes)
                    pixels = ByteArray(bytes.toInt())
                }
                sizeParam.setInt(0, size.width)
                sizeParam.setInt(4, size.height)
                if (Native.SIZE_T_SIZE == 8) strideParam.setLong(0, stride.toLong()) else strideParam.setInt(0, stride)

                val params = renderParams(
                    LibMpv.RENDER_PARAM_SW_SIZE to sizeParam,
                    LibMpv.RENDER_PARAM_SW_FORMAT to format,
                    LibMpv.RENDER_PARAM_SW_STRIDE to strideParam,
                    LibMpv.RENDER_PARAM_SW_POINTER to buffer,
                )
                val result = mpv.mpv_render_context_render(context, params)
                if (result < 0) {
                    log.w { "Render failed: ${mpv.mpv_error_string(result)}" }
                    continue
                }
                renderedSize = size
                buffer.read(0, pixels, 0, bytes.toInt())

                val target = bitmaps[backIndex]
                val info = ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                target.installPixels(info, pixels, stride)
                target.notifyPixelsChanged()
                frontIndex.set(backIndex)
                backIndex = 1 - backIndex
                frameCounter.incrementAndGet()
                onFrame?.invoke()
            }
        } catch (error: Throwable) {
            log.e(error) { "Render loop stopped" }
        } finally {
            mpv.mpv_render_context_set_update_callback(context, null, null)
            mpv.mpv_render_context_free(context)
        }
    }

    private fun eventLoop() {
        while (!released.get()) {
            val event = mpv.mpv_wait_event(handle, 0.5)
            when (event.getInt(0)) {
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_FILE_LOADED -> {
                    log.i { "File loaded" }
                    pendingAfterLoad.getAndSet(null)?.invoke()
                }
                LibMpv.EVENT_END_FILE -> {
                    // mpv_event.data -> mpv_event_end_file { int reason; int error; ... }
                    val data = event.getPointer(16)
                    val reason = data?.getInt(0) ?: -1
                    if (reason == LibMpv.END_FILE_REASON_ERROR) {
                        val code = data?.getInt(4) ?: 0
                        val message = mpv.mpv_error_string(code)
                        log.e { "Playback error: $message" }
                        errorMessage.set(message)
                    }
                }
            }
        }
    }

    private fun renderParams(vararg params: Pair<Int, Pointer>): Memory {
        val entrySize = 8L + Native.POINTER_SIZE
        val memory = Memory(entrySize * (params.size + 1))
        memory.clear()
        params.forEachIndexed { index, (type, data) ->
            memory.setInt(index * entrySize, type)
            memory.setPointer(index * entrySize + 8, data)
        }
        memory.setInt(params.size * entrySize, LibMpv.RENDER_PARAM_INVALID)
        return memory
    }

    companion object {
        private const val TAG = "DesktopMpvPlayer"

        /** Software rendering cost grows with pixels: render at most ~1440p and let Compose scale. */
        private const val MAX_RENDER_PIXELS = 2560 * 1440

        fun create(configure: (MpvPlayer) -> Unit = {}): MpvPlayer? {
            val mpv = LibMpv.instance ?: return null
            val handle = mpv.mpv_create() ?: return null
            val player = MpvPlayer(mpv, handle)
            mapOf(
                "vo" to "libmpv",
                "hwdec" to "auto-copy",
                "keep-open" to "yes",
                "idle" to "yes",
                "ytdl" to "no",
                "terminal" to "no",
                "input-default-bindings" to "no",
                "osc" to "no",
                "sub-auto" to "no",
                "cache" to "yes",
                "demuxer-max-bytes" to "150MiB",
                "demuxer-readahead-secs" to "60",
                "network-timeout" to "30",
                "user-agent" to "Nuvio",
            ).forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            configure(player)
            val initialized = mpv.mpv_initialize(handle)
            if (initialized < 0) {
                Logger.e(TAG) { "mpv_initialize failed: ${mpv.mpv_error_string(initialized)}" }
                mpv.mpv_terminate_destroy(handle)
                return null
            }
            player.start()
            return player
        }

        private fun capToRenderBudget(width: Int, height: Int): Size {
            if (width <= 0 || height <= 0) return Size(0, 0)
            val pixels = width.toLong() * height
            if (pixels <= MAX_RENDER_PIXELS) return Size(width, height)
            val scale = kotlin.math.sqrt(MAX_RENDER_PIXELS.toDouble() / pixels)
            return Size((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
        }

        /** mpv list options use ',' as separator: escape it inside one item. */
        private fun escapeListItem(value: String): String = value.replace("\\", "\\\\").replace(",", "\\,")
    }
}
