package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// iOS keeps downloads in the app sandbox: no folder picker.
internal actual object DownloadsFolderPlatform {
    actual val isFolderPickerSupported: Boolean = false

    private val folder = MutableStateFlow<DownloadsFolder?>(null)
    actual val selectedFolder: StateFlow<DownloadsFolder?> = folder.asStateFlow()

    actual fun selectFolder(treeUri: String): Boolean = false

    actual fun clearFolder() = Unit

    actual fun isFolderAccessible(treeUri: String): Boolean = false

    actual fun treeUriForNewDownload(): String? = null

    actual fun storageStats(treeUri: String?): DownloadsStorageStats? = null

    actual fun listFolderFiles(treeUri: String): List<DownloadsFolderEntry> = emptyList()

    actual fun openFolder(treeUri: String?): Boolean = DownloadsPlatformDownloader.openDownloadsDirectory()

    actual fun shareFile(fileUri: String, fileName: String): Boolean = false

    actual fun deleteFolderFile(fileUri: String): Boolean = false

    actual fun describeLocation(treeUri: String?, fileName: String): String = fileName
}

@Composable
internal actual fun rememberDownloadsFolderPicker(onFolderPicked: (treeUri: String) -> Unit): (() -> Unit)? = null
