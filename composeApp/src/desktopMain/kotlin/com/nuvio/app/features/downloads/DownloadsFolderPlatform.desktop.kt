package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.revealInFileManager
import com.nuvio.app.desktop.compat.DesktopContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Desktop downloads folder: the "tree URI" of the shared API is the `file:` URI of a normal
 * directory. Without a choice, downloads go to Videos\Nuvio.
 */
internal actual object DownloadsFolderPlatform {
    private val log = Logger.withTag("DownloadsFolder")
    private const val PREFS = "nuvio_downloads_folder"
    private const val KEY_FOLDER = "folder_uri"
    private val prefs by lazy { DesktopContext.getSharedPreferences(PREFS, 0) }

    actual val isFolderPickerSupported: Boolean = true

    private val folder = MutableStateFlow(loadSaved())
    actual val selectedFolder: StateFlow<DownloadsFolder?> = folder.asStateFlow()

    /** Default folder when the user has not picked one. */
    val defaultDirectory: File
        get() {
            val home = File(System.getProperty("user.home").orEmpty())
            val videos = File(home, "Videos").takeIf { it.isDirectory } ?: home
            return File(videos, "Nuvio").apply { mkdirs() }
        }

    private fun loadSaved(): DownloadsFolder? =
        prefs.getString(KEY_FOLDER, null)?.let(::toDirectory)?.let(::toFolder)

    actual fun selectFolder(treeUri: String): Boolean {
        val directory = toDirectory(treeUri) ?: return false
        if (!directory.isDirectory || !directory.canWrite()) {
            log.w { "Folder not writable, selection ignored" }
            return false
        }
        prefs.edit().putString(KEY_FOLDER, directory.toURI().toString()).apply()
        folder.value = toFolder(directory)
        log.i { "Downloads folder selected" }
        return true
    }

    actual fun clearFolder() {
        prefs.edit().remove(KEY_FOLDER).apply()
        folder.value = null
        log.i { "Downloads folder reset to default" }
    }

    actual fun isFolderAccessible(treeUri: String): Boolean =
        toDirectory(treeUri)?.let { it.isDirectory && it.canWrite() } == true

    actual fun treeUriForNewDownload(): String? =
        folder.value?.treeUri?.takeIf(::isFolderAccessible)

    actual fun storageStats(treeUri: String?): DownloadsStorageStats? {
        val directory = treeUri?.let(::toDirectory) ?: defaultDirectory
        return runCatching { DownloadsStorageStats(freeBytes = directory.usableSpace, totalBytes = directory.totalSpace) }
            .getOrNull()
            ?.takeIf { it.totalBytes > 0L }
    }

    actual fun listFolderFiles(treeUri: String): List<DownloadsFolderEntry> =
        toDirectory(treeUri)?.listFiles()?.filter { it.isFile }?.map { file ->
            DownloadsFolderEntry(
                uri = file.toURI().toString(),
                name = file.name,
                sizeBytes = file.length(),
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L },
            )
        }.orEmpty()

    actual fun openFolder(treeUri: String?): Boolean {
        val directory = treeUri?.let(::toDirectory) ?: defaultDirectory
        return runCatching {
            java.awt.Desktop.getDesktop().open(directory)
            true
        }.onFailure { log.e(it) { "Cannot open the downloads folder" } }.getOrDefault(false)
    }

    /** No share sheet on desktop: shows the file in Explorer. */
    actual fun shareFile(fileUri: String, fileName: String): Boolean {
        val file = runCatching { File(URI(fileUri)) }.getOrNull() ?: return false
        revealInFileManager(file)
        return true
    }

    actual fun deleteFolderFile(fileUri: String): Boolean =
        runCatching { File(URI(fileUri)).delete() }.getOrDefault(false)

    actual fun describeLocation(treeUri: String?, fileName: String): String {
        val directory = treeUri?.let(::toDirectory) ?: defaultDirectory
        return File(directory, fileName).absolutePath
    }

    internal fun toDirectory(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) File(URI(uri)) else File(uri)
    }.getOrNull()

    private fun toFolder(directory: File) = DownloadsFolder(
        treeUri = directory.toURI().toString(),
        displayName = directory.absolutePath,
    )
}

@Composable
internal actual fun rememberDownloadsFolderPicker(onFolderPicked: (treeUri: String) -> Unit): (() -> Unit)? {
    val latest = rememberUpdatedState(onFolderPicked)
    return remember {
        {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser(DownloadsFolderPlatform.selectedFolder.value?.treeUri
                    ?.let(DownloadsFolderPlatform::toDirectory) ?: DownloadsFolderPlatform.defaultDirectory).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile?.let { latest.value(it.toURI().toString()) }
                }
            }
        }
    }
}
