package com.nuvio.app.desktop

import com.dokar.quickjs.quickJs
import com.nuvio.engine.NuvioEngineRuntime
import java.io.File
import com.nuvio.app.features.downloads.DownloadsPlatformDownloader
import com.nuvio.app.features.plugins.configureDesktopQuickJsLibrary
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The native libraries bundled in the desktop jars load on this machine. */
class DesktopNativeLibrariesTest {
    @Test
    fun ffmpegLoadsSoHlsDownloadsAreAvailable() {
        assertTrue(DownloadsPlatformDownloader.supportsHls)
    }

    @Test
    fun quickJsRunsPluginCode() = runBlocking {
        configureDesktopQuickJsLibrary()
        val result = quickJs { evaluate<Int>("1 + 2") }
        assertEquals(3, result)
    }

    @Test
    fun torrentEngineLibraryLoads() {
        val runtime = NuvioEngineRuntime.load(File("desktopNative/windows/nuvio_engine.dll").absoluteFile)
        assertTrue(runtime.version.isNotBlank())
    }
}
