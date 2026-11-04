package com.nuvio.app.desktop.mpv

import co.touchlab.kermit.Logger
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.File

/** Minimal JNA binding of the libmpv client + render (software) API. */
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_command_async(handle: Pointer, replyUserdata: Long, args: Array<String?>): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(handle: Pointer)
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String

    fun mpv_render_context_create(result: PointerByReference, handle: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(context: Pointer, callback: RenderUpdateCallback?, userdata: Pointer?)
    fun mpv_render_context_update(context: Pointer): Long
    fun mpv_render_context_render(context: Pointer, params: Pointer): Int
    fun mpv_render_context_free(context: Pointer)

    fun interface RenderUpdateCallback : Callback {
        fun invoke(userdata: Pointer?)
    }

    companion object {
        const val RENDER_PARAM_INVALID = 0
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20
        const val RENDER_UPDATE_FRAME = 1L

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val END_FILE_REASON_ERROR = 4

        private val log = Logger.withTag("LibMpv")

        /** Loaded once; null when libmpv-2.dll cannot be found or loaded. */
        val instance: LibMpv? by lazy {
            runCatching {
                nativeDirectory()?.let { dir ->
                    val current = System.getProperty("jna.library.path").orEmpty()
                    if (!current.split(File.pathSeparator).contains(dir.absolutePath)) {
                        System.setProperty(
                            "jna.library.path",
                            listOf(current, dir.absolutePath).filter(String::isNotBlank).joinToString(File.pathSeparator),
                        )
                    }
                }
                Native.load("libmpv-2", LibMpv::class.java)
            }.onSuccess {
                log.i { "libmpv loaded" }
            }.onFailure { error ->
                log.e(error) { "libmpv could not be loaded" }
            }.getOrNull()
        }

        /** Packaged app: Compose resources dir; development run: composeApp/desktopNative/windows. */
        private fun nativeDirectory(): File? {
            val packaged = System.getProperty("compose.application.resources.dir")
                ?.let(::File)
                ?.takeIf { File(it, "libmpv-2.dll").isFile }
            if (packaged != null) return packaged
            return sequenceOf(
                File("desktopNative/windows"),
                File("composeApp/desktopNative/windows"),
            ).firstOrNull { File(it, "libmpv-2.dll").isFile }?.absoluteFile
        }
    }
}
