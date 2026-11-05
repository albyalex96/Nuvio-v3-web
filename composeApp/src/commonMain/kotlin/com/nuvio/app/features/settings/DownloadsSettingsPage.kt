package com.nuvio.app.features.settings

import com.nuvio.app.core.compat.appIo
import com.nuvio.app.core.ui.NuvioActionMenuDivider
import com.nuvio.app.core.ui.NuvioActionMenuGroup
import com.nuvio.app.core.ui.NuvioActionMenuRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.cast.CastDevicePickerDialog
import com.nuvio.app.features.cast.CastPlatform
import com.nuvio.app.features.cast.CastSessionState
import com.nuvio.app.features.cast.isAvailable
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadPlaybackMode
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.DownloadTitleGroup
import com.nuvio.app.features.downloads.DownloadTitleAction
import com.nuvio.app.features.downloads.DownloadTitleStatus
import com.nuvio.app.features.downloads.DownloadsClock
import com.nuvio.app.features.downloads.downloadTitleStatus
import com.nuvio.app.features.downloads.season.SeasonDownloadRepository
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.nuvio.app.features.downloads.groupDownloadsByTitle
import com.nuvio.app.features.downloads.DownloadsAction
import com.nuvio.app.features.downloads.DownloadsActionBridge
import com.nuvio.app.features.downloads.DownloadsFolderPlatform
import com.nuvio.app.features.downloads.DownloadsPlatformDownloader
import com.nuvio.app.features.downloads.DownloadsPreferences
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.DownloadsStorageStats
import com.nuvio.app.features.downloads.ExternalVideoFile
import com.nuvio.app.features.downloads.ExternalVideoOrigin
import com.nuvio.app.features.downloads.displayTitle
import com.nuvio.app.features.downloads.downloadDisplaySubtitle
import com.nuvio.app.features.downloads.findExternalVideos
import com.nuvio.app.features.downloads.formatDownloadBytes
import com.nuvio.app.features.downloads.rememberDownloadsFolderPicker
import com.nuvio.app.features.downloads.rememberDownloadsPowerState
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.tv.tvLongPressKeys
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val downloadsSettingsLog = Logger.withTag("Downloads")

/** The folder section exists where the user can pick a folder (Android with a document picker). */
internal val downloadsFolderOptionsVisible: Boolean
    get() = DownloadsFolderPlatform.isFolderPickerSupported

internal fun LazyListScope.downloadsSettingsContent(isTablet: Boolean) {
    item(key = "downloads-settings") {
        DownloadsSettingsBody(isTablet = isTablet)
    }
}

/** A row of the downloaded files list: a download of this profile or a file found in the folder. */
private sealed interface DownloadEntry {
    val key: String

    data class App(val item: DownloadItem) : DownloadEntry {
        override val key: String get() = "app:${item.id}"
    }

    data class External(val file: ExternalVideoFile) : DownloadEntry {
        override val key: String get() = "file:${file.uri}"
    }
}

private val DownloadItem.sizeOnDisk: Long
    get() = totalBytes?.takeIf { it > 0L } ?: downloadedBytes

@Composable
private fun DownloadsSettingsBody(isTablet: Boolean) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val folder by DownloadsFolderPlatform.selectedFolder.collectAsStateWithLifecycle()
    var refreshKey by remember { mutableIntStateOf(0) }
    var folderAccessible by remember { mutableStateOf(true) }
    var externalFiles by remember { mutableStateOf<List<ExternalVideoFile>>(emptyList()) }
    var storageStats by remember { mutableStateOf<DownloadsStorageStats?>(null) }
    val currentItems by rememberUpdatedState(uiState.items)
    val completedCount = uiState.completedItems.size

    // Folder access, files in the folder and disk space: provider queries, off the main thread.
    LaunchedEffect(folder, refreshKey, completedCount) {
        val selected = folder
        val knownItems = currentItems
        val (accessible, found, stats) = withContext(Dispatchers.appIo) {
            val accessible = selected?.let { DownloadsFolderPlatform.isFolderAccessible(it.treeUri) } ?: true
            val folderEntries = if (selected != null && accessible) {
                DownloadsFolderPlatform.listFolderFiles(selected.treeUri)
            } else {
                emptyList()
            }
            Triple(
                accessible,
                findExternalVideos(folderEntries, knownItems),
                DownloadsFolderPlatform.storageStats(selected?.treeUri?.takeIf { accessible }),
            )
        }
        folderAccessible = accessible
        externalFiles = found
        storageStats = stats
        downloadsSettingsLog.d { "Downloads page refreshed: ${found.size} file(s) not downloaded by this profile" }
    }

    val folderFailedText = stringResource(Res.string.downloads_settings_folder_failed)
    val picker = rememberDownloadsFolderPicker { treeUri ->
        if (!DownloadsFolderPlatform.selectFolder(treeUri)) NuvioToastController.show(folderFailedText)
    }

    // Stable order: the progress of a download must not move its card (see groupDownloadsByTitle).
    val titleGroups = remember(uiState.items) { groupDownloadsByTitle(uiState.items) }
    val sortedExternalFiles = remember(externalFiles) { externalFiles.sortedBy { it.displayName.lowercase() } }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val seasonBatches by remember {
        SeasonDownloadRepository.ensureLoaded()
        SeasonDownloadRepository.batches
    }.collectAsStateWithLifecycle()
    val seasonCooldowns by SeasonDownloadRepository.cooldowns.collectAsStateWithLifecycle()
    val anyRunning = uiState.items.any { it.status == DownloadStatus.Downloading } ||
        seasonBatches.any { !it.paused && it.pendingCount > 0 }
    val anyPaused = uiState.items.any { it.status == DownloadStatus.Paused } || seasonBatches.any { it.paused && !it.isFinished }
    val anyUnfinished = uiState.items.any { it.status != DownloadStatus.Completed } || seasonBatches.any { !it.isFinished }
    // Cancel waiting for confirmation: one title (key and name), or everything when the key is null.
    var cancelTarget by remember { mutableStateOf<Pair<String?, String?>?>(null) }

    var menuEntry by remember { mutableStateOf<DownloadEntry?>(null) }
    var infoEntry by remember { mutableStateOf<DownloadEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<DownloadEntry?>(null) }
    var castEntry by remember { mutableStateOf<DownloadEntry?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp)) {
        if (downloadsFolderOptionsVisible || folder != null) {
            DownloadsFolderSection(
                isTablet = isTablet,
                folderName = folder?.displayName,
                folderAccessible = folderAccessible,
                onPickFolder = picker,
                onUseAppStorage = DownloadsFolderPlatform::clearFolder,
            )
        }

        DownloadsBackgroundSection(isTablet = isTablet)

        DownloadsOrganizationSection(isTablet = isTablet)

        DownloadsSeasonSection(isTablet = isTablet)

        SettingsSection(
            title = stringResource(Res.string.downloads_settings_section_summary),
            isTablet = isTablet,
        ) {
            DownloadsStorageSummary(
                isTablet = isTablet,
                videoCount = uiState.completedItems.size + externalFiles.size,
                videoBytes = uiState.completedItems.sumOf { it.sizeOnDisk } + externalFiles.sumOf { it.sizeBytes },
                stats = storageStats,
            )
        }

        SettingsSection(
            title = stringResource(Res.string.downloads_settings_section_files),
            isTablet = isTablet,
            actions = {
                if (anyRunning || anyPaused) {
                    DownloadsActionButton(
                        icon = if (anyRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (anyRunning) Res.string.downloads_pause_all else Res.string.downloads_resume_all,
                        ),
                        onClick = { if (anyRunning) pauseDownloads(null) else resumeDownloads(null) },
                    )
                }
                if (anyUnfinished) {
                    DownloadsActionButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = stringResource(Res.string.downloads_cancel_all),
                        onClick = { cancelTarget = null to null },
                        tint = MaterialTheme.nuvio.colors.danger,
                        borderColor = MaterialTheme.nuvio.colors.danger.copy(alpha = 0.5f),
                    )
                }
                DownloadsActionButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.downloads_settings_refresh),
                    onClick = { refreshKey++ },
                )
            },
        ) {
            if (titleGroups.isEmpty() && sortedExternalFiles.isEmpty()) {
                Text(
                    text = stringResource(Res.string.downloads_settings_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textMuted,
                )
            } else {
                val entryCard: @Composable (DownloadEntry) -> Unit = { entry ->
                    DownloadFileCard(
                        entry = entry,
                        isTablet = isTablet,
                        onClick = {
                            if (entry.isPlayable) {
                                entry.play(DownloadPlaybackMode.Default)
                            } else {
                                menuEntry = entry
                            }
                        },
                        onLongClick = { menuEntry = entry },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    titleGroups.forEach { group ->
                        androidx.compose.runtime.key("group:${group.key}") {
                            if (!group.isSeries && group.items.size == 1) {
                                entryCard(DownloadEntry.App(group.items.single()))
                            } else {
                                DownloadTitleGroupCard(
                                    group = group,
                                    status = downloadTitleStatus(group, seasonBatches, seasonCooldowns, DownloadsClock.nowEpochMs()),
                                    isTablet = isTablet,
                                    expanded = expandedGroups[group.key] == true,
                                    onToggle = {
                                        expandedGroups[group.key] = expandedGroups[group.key] != true
                                    },
                                    onPause = { pauseDownloads(group.key) },
                                    onResume = { resumeDownloads(group.key) },
                                    onCancel = { cancelTarget = group.key to group.title },
                                ) { item -> entryCard(DownloadEntry.App(item)) }
                            }
                        }
                    }
                    sortedExternalFiles.forEach { file ->
                        val entry = DownloadEntry.External(file)
                        androidx.compose.runtime.key(entry.key) { entryCard(entry) }
                    }
                }
            }
        }
    }

    cancelTarget?.let { (parentMetaId, title) ->
        NuvioStatusModal(
            title = if (title != null) {
                stringResource(Res.string.downloads_cancel_title_confirm, title)
            } else {
                stringResource(Res.string.downloads_cancel_all_confirm)
            },
            message = stringResource(Res.string.downloads_cancel_confirm_message),
            isVisible = true,
            destructive = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                cancelTarget = null
                cancelDownloads(parentMetaId)
            },
            onDismiss = { cancelTarget = null },
        )
    }

    menuEntry?.let { entry ->
        DownloadActionsDialog(
            entry = entry,
            onDismiss = { menuEntry = null },
            onCast = {
                if (CastPlatform.state.value is CastSessionState.Connected) {
                    entry.play(DownloadPlaybackMode.Internal)
                } else {
                    castEntry = entry
                }
            },
            onInfo = { infoEntry = entry },
            onDelete = { deleteEntry = entry },
            onChanged = { refreshKey++ },
        )
    }
    infoEntry?.let { entry ->
        DownloadInfoDialog(entry = entry, onDismiss = { infoEntry = null })
    }
    deleteEntry?.let { entry ->
        DownloadDeleteDialog(
            entry = entry,
            onDismiss = { deleteEntry = null },
            onDeleted = {
                deleteEntry = null
                refreshKey++
            },
        )
    }
    castEntry?.let { entry ->
        CastDevicePickerDialog(
            onDismiss = { castEntry = null },
            onConnected = {
                castEntry = null
                entry.play(DownloadPlaybackMode.Internal)
            },
        )
    }
}

private val DownloadEntry.isPlayable: Boolean
    get() = when (this) {
        is DownloadEntry.App -> item.isPlayable
        is DownloadEntry.External -> true
    }

private fun DownloadEntry.play(mode: DownloadPlaybackMode, fromStart: Boolean = false) {
    val action = when (this) {
        is DownloadEntry.App -> DownloadsAction.PlayDownload(item, fromStart = fromStart, mode = mode)
        is DownloadEntry.External -> DownloadsAction.PlayExternalFile(file, mode = mode)
    }
    downloadsSettingsLog.i { "Play ${if (this is DownloadEntry.App) "download" else "folder file"} (mode=$mode, fromStart=$fromStart)" }
    DownloadsActionBridge.send(action)
}

@Composable
private fun DownloadsFolderSection(
    isTablet: Boolean,
    folderName: String?,
    folderAccessible: Boolean,
    onPickFolder: (() -> Unit)?,
    onUseAppStorage: () -> Unit,
) {
    SettingsSection(
        title = stringResource(Res.string.downloads_settings_section_folder),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.downloads_settings_folder_title),
                description = when {
                    folderName == null -> stringResource(Res.string.downloads_settings_folder_internal)
                    folderAccessible -> folderName
                    else -> stringResource(Res.string.downloads_settings_folder_unavailable, folderName)
                },
                icon = Icons.Rounded.Folder,
                enabled = onPickFolder != null,
                isTablet = isTablet,
                onClick = { onPickFolder?.invoke() },
            )
            if (folderName != null) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.downloads_settings_use_internal_title),
                    description = stringResource(Res.string.downloads_settings_use_internal_description),
                    icon = Icons.Rounded.PhoneAndroid,
                    isTablet = isTablet,
                    onClick = onUseAppStorage,
                )
            }
        }
        Text(
            text = stringResource(Res.string.downloads_settings_folder_hint),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.nuvio.colors.textMuted,
        )
    }
}

/**
 * How the downloaded files are named: the "Series/Season N/<code> - <title>" folders apply to every
 * episode download, single or from a whole season.
 */
@Composable
private fun DownloadsOrganizationSection(isTablet: Boolean) {
    if (!DownloadsPlatformDownloader.supportsEpisodeFolders) return
    val organizeEpisodeFolders by remember {
        DownloadsPreferences.ensureLoaded()
        DownloadsPreferences.organizeEpisodeFolders
    }.collectAsStateWithLifecycle()
    SettingsSection(
        title = stringResource(Res.string.downloads_settings_section_organization),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.downloads_settings_episode_folders_title),
                description = stringResource(Res.string.downloads_settings_episode_folders_description),
                checked = organizeEpisodeFolders,
                isTablet = isTablet,
                onCheckedChange = DownloadsPreferences::setOrganizeEpisodeFolders,
            )
        }
    }
}

/** Season downloads: how many episodes download at the same time. */
@Composable
private fun DownloadsSeasonSection(isTablet: Boolean) {
    val concurrency by remember {
        DownloadsPreferences.ensureLoaded()
        DownloadsPreferences.seasonConcurrency
    }.collectAsStateWithLifecycle()
    SettingsSection(
        title = stringResource(Res.string.downloads_settings_section_seasons),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSliderRow(
                title = stringResource(Res.string.downloads_settings_season_concurrency_title),
                value = concurrency,
                valueText = { it.toString() },
                valueRange = DownloadsPreferences.SEASON_CONCURRENCY_RANGE,
                step = 1,
                isTablet = isTablet,
                onValueChange = DownloadsPreferences::setSeasonConcurrency,
            )
        }
        Text(
            text = stringResource(Res.string.downloads_settings_season_concurrency_description),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.nuvio.colors.textMuted,
        )
    }
}

/**
 * Battery optimization status and the system pages that keep background downloads running:
 * some manufacturers stop background work much more aggressively than stock Android.
 */
@Composable
private fun DownloadsBackgroundSection(isTablet: Boolean) {
    val power = rememberDownloadsPowerState() ?: return
    val tokens = MaterialTheme.nuvio
    SettingsSection(
        title = stringResource(Res.string.downloads_settings_section_background),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.downloads_settings_battery_title),
                description = if (power.restricted) {
                    stringResource(Res.string.downloads_settings_battery_restricted)
                } else {
                    stringResource(Res.string.downloads_settings_battery_unrestricted)
                },
                icon = if (power.restricted) Icons.Rounded.BatteryAlert else Icons.Rounded.BatteryChargingFull,
                isTablet = isTablet,
                trailingContent = {
                    if (!power.restricted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                        )
                    }
                },
                onClick = power.openBatteryOptimization,
            )
        }
        Text(
            text = stringResource(Res.string.downloads_settings_battery_hint),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
        )
        OutlinedButton(
            onClick = power.openAppSettings,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(Res.string.downloads_settings_battery_open_app_settings))
        }
    }
}

@Composable
private fun DownloadsStorageSummary(
    isTablet: Boolean,
    videoCount: Int,
    videoBytes: Long,
    stats: DownloadsStorageStats?,
) {
    val tokens = MaterialTheme.nuvio
    SettingsGroup(isTablet = isTablet) {
        Column(
            modifier = Modifier.padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.downloads_settings_summary_videos, videoCount, formatDownloadBytes(videoBytes)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
            )
            if (stats != null && stats.totalBytes > 0L) {
                val total = stats.totalBytes.toFloat()
                StorageBar(
                    label = stringResource(Res.string.downloads_settings_summary_used),
                    value = stringResource(
                        Res.string.downloads_settings_summary_of_total,
                        formatDownloadBytes(videoBytes),
                        formatDownloadBytes(stats.totalBytes),
                    ),
                    fraction = videoBytes / total,
                    color = tokens.colors.accent,
                )
                StorageBar(
                    label = stringResource(Res.string.downloads_settings_summary_free),
                    value = stringResource(
                        Res.string.downloads_settings_summary_of_total,
                        formatDownloadBytes(stats.freeBytes),
                        formatDownloadBytes(stats.totalBytes),
                    ),
                    fraction = stats.freeBytes / total,
                    color = tokens.colors.success,
                )
            }
        }
    }
}

@Composable
private fun StorageBar(label: String, value: String, fraction: Float, color: Color) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textSecondary)
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadFileCard(
    entry: DownloadEntry,
    isTablet: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val thumbnailWidth = if (isTablet) 144.dp else 112.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .tvLongPressKeys(onClick = onClick, onLongClick = onLongClick)
            .combinedClickable(
                interactionSource = null,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DownloadThumbnail(entry = entry, width = thumbnailWidth)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (entry) {
                    is DownloadEntry.App -> AppDownloadCardText(entry.item)
                    is DownloadEntry.External -> ExternalFileCardText(entry.file)
                }
            }
            if (entry is DownloadEntry.App && entry.item.status != DownloadStatus.Completed) {
                DownloadTransferButton(entry.item)
            }
        }
    }
}

/**
 * The downloads of a series (or of a title with several files) as one card: tap to show or hide
 * its episodes. The header sums them up, with the overall progress while some are downloading.
 */
@Composable
private fun DownloadTitleGroupCard(
    group: DownloadTitleGroup,
    status: DownloadTitleStatus,
    isTablet: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    itemContent: @Composable (DownloadItem) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val thumbnailWidth = if (isTablet) 144.dp else 112.dp
    val first = group.items.first()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.compactCard)
                .clickable(onClick = onToggle),
            color = tokens.colors.surface,
            shape = tokens.shapes.compactCard,
            border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DownloadThumbnailImage(imageUrl = first.background ?: first.poster ?: first.episodeThumbnail, width = thumbnailWidth)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(
                            stringResource(Res.string.downloads_settings_group_episodes, status.total),
                            formatDownloadBytes(group.sizeBytes),
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (status.action != DownloadTitleAction.Idle || status.completed < status.total) {
                        DownloadTitleIndicators(status = status)
                    }
                }
                if (status.canPause || status.canResume) {
                    DownloadsActionButton(
                        icon = if (status.canPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (status.canPause) Res.string.downloads_title_pause else Res.string.downloads_title_resume,
                        ),
                        onClick = if (status.canPause) onPause else onResume,
                        tint = tokens.colors.textPrimary,
                    )
                }
                if (status.completed < status.total) {
                    DownloadsActionButton(
                        icon = Icons.Rounded.Cancel,
                        contentDescription = stringResource(Res.string.downloads_title_cancel),
                        onClick = onCancel,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) Res.string.downloads_settings_group_collapse else Res.string.downloads_settings_group_expand,
                    ),
                    tint = tokens.colors.textMuted,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = if (isTablet) 20.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                group.items.forEach { item ->
                    androidx.compose.runtime.key(item.id) { itemContent(item) }
                }
            }
        }
    }
}

/**
 * Indicators of a title card: what it is doing now, the overall progress (episodes of its season
 * downloads not started yet included) and one chip per state with episodes in it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadTitleIndicators(status: DownloadTitleStatus) {
    val tokens = MaterialTheme.nuvio
    val action = status.action
    // The rate limit pause shows the minutes left: refreshed while it lasts.
    var nowEpochMs by remember { mutableLongStateOf(DownloadsClock.nowEpochMs()) }
    if (action is DownloadTitleAction.RateLimited) {
        LaunchedEffect(action.untilEpochMs) {
            while (nowEpochMs < action.untilEpochMs) {
                delay(10_000L)
                nowEpochMs = DownloadsClock.nowEpochMs()
            }
        }
    }
    val (actionIcon, actionText) = when (action) {
        is DownloadTitleAction.Converting -> Icons.Rounded.MergeType to stringResource(
            Res.string.downloads_title_action_converting,
            action.item.episodeLabel(),
            percentText(action.item.progressFraction),
        )
        is DownloadTitleAction.Downloading -> Icons.Rounded.Download to if (action.others > 0) {
            stringResource(Res.string.downloads_title_action_downloading_more, action.item.episodeLabel(), percentText(action.item.progressFraction), action.others)
        } else {
            stringResource(Res.string.downloads_title_action_downloading, action.item.episodeLabel(), percentText(action.item.progressFraction))
        }
        is DownloadTitleAction.Searching -> Icons.Rounded.Search to stringResource(
            Res.string.downloads_title_action_searching,
            localizedSeasonEpisodeCode(action.seasonNumber, action.episodeNumber).orEmpty(),
        )
        is DownloadTitleAction.RateLimited -> Icons.Rounded.HourglassTop to stringResource(
            Res.string.downloads_title_action_rate_limited,
            ((action.untilEpochMs - nowEpochMs + 59_999L) / 60_000L).coerceAtLeast(1L).toInt(),
        )
        DownloadTitleAction.Queued -> Icons.Rounded.Schedule to stringResource(Res.string.downloads_title_action_queued)
        DownloadTitleAction.Paused -> Icons.Rounded.Pause to stringResource(Res.string.downloads_title_action_paused)
        DownloadTitleAction.Failed -> Icons.Rounded.ErrorOutline to stringResource(Res.string.downloads_title_action_failed)
        DownloadTitleAction.Idle -> null to null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (actionIcon != null && actionText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (action == DownloadTitleAction.Failed) tokens.colors.danger else tokens.colors.accent,
                )
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (action == DownloadTitleAction.Failed) tokens.colors.danger else tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringResource(Res.string.downloads_title_progress, status.completed, status.total, percentText(status.progress)),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = tokens.colors.accent,
            trackColor = tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (status.downloading > 0) TitleStatusChip(Icons.Rounded.Download, stringResource(Res.string.downloads_title_chip_downloading, status.downloading), tokens.colors.accent)
            if (status.converting > 0) TitleStatusChip(Icons.Rounded.MergeType, stringResource(Res.string.downloads_title_chip_converting, status.converting), tokens.colors.accent)
            if (status.searching > 0) TitleStatusChip(Icons.Rounded.Search, stringResource(Res.string.downloads_title_chip_searching, status.searching), tokens.colors.info)
            if (status.queued > 0) TitleStatusChip(Icons.Rounded.Schedule, stringResource(Res.string.downloads_title_chip_queued, status.queued), tokens.colors.textSecondary)
            if (status.paused > 0) TitleStatusChip(Icons.Rounded.Pause, stringResource(Res.string.downloads_title_chip_paused, status.paused), tokens.colors.warning)
            if (status.withoutStream > 0) TitleStatusChip(Icons.Rounded.SearchOff, stringResource(Res.string.downloads_title_chip_no_stream, status.withoutStream), tokens.colors.warning)
            if (status.failed > 0) TitleStatusChip(Icons.Rounded.ErrorOutline, stringResource(Res.string.downloads_title_chip_failed, status.failed), tokens.colors.danger)
        }
    }
}

@Composable
private fun TitleStatusChip(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
        Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

private fun DownloadItem.episodeLabel(): String =
    localizedSeasonEpisodeCode(seasonNumber, episodeNumber) ?: displayTitle()

private fun percentText(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%"

/** Pauses the downloads of [parentMetaId] (all of them when null), season downloads first so no new episode starts. */
private fun pauseDownloads(parentMetaId: String?) {
    downloadsSettingsLog.i { "Pause ${parentMetaId?.let { "the downloads of $it" } ?: "every download"}" }
    SeasonDownloadRepository.pauseAll(parentMetaId)
    if (parentMetaId == null) DownloadsRepository.pauseActiveDownloads() else DownloadsRepository.pauseTitleDownloads(parentMetaId)
}

/**
 * Cancels the downloads of [parentMetaId] (all of them when null): season downloads first so no
 * new episode starts, then every unfinished download with its partial files. Completed ones stay.
 */
private fun cancelDownloads(parentMetaId: String?) {
    downloadsSettingsLog.i { "Cancel ${parentMetaId?.let { "the downloads of $it" } ?: "every unfinished download"}" }
    SeasonDownloadRepository.cancelAll(parentMetaId)
    DownloadsRepository.cancelUnfinishedDownloads(parentMetaId)
}

private fun resumeDownloads(parentMetaId: String?) {
    downloadsSettingsLog.i { "Resume ${parentMetaId?.let { "the downloads of $it" } ?: "every download"}" }
    SeasonDownloadRepository.resumeAll(parentMetaId)
    DownloadsRepository.resumePausedDownloads(parentMetaId)
}

@Composable
private fun DownloadThumbnail(entry: DownloadEntry, width: androidx.compose.ui.unit.Dp) {
    val imageUrl = (entry as? DownloadEntry.App)?.item?.let { it.episodeThumbnail ?: it.background ?: it.poster }
    DownloadThumbnailImage(imageUrl = imageUrl, width = width)
}

@Composable
private fun DownloadThumbnailImage(imageUrl: String?, width: androidx.compose.ui.unit.Dp) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .width(width)
            .height(width * 9 / 16)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun AppDownloadCardText(item: DownloadItem) {
    val tokens = MaterialTheme.nuvio
    val title = if (item.isEpisode) item.title else item.displayTitle()
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = tokens.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (item.isEpisode) {
        Text(
            text = downloadDisplaySubtitle(item = item, displayTitle = title),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        text = downloadStatusLine(item),
        style = MaterialTheme.typography.labelSmall,
        color = if (item.status == DownloadStatus.Failed) tokens.colors.danger else tokens.colors.textMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Paused) {
        Spacer(modifier = Modifier.height(4.dp))
        if (item.totalBytes != null && item.totalBytes > 0L) {
            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = tokens.colors.accent,
                trackColor = tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else if (item.status == DownloadStatus.Downloading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = tokens.colors.accent,
                trackColor = tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover),
            )
        }
    }
}

@Composable
private fun ExternalFileCardText(file: ExternalVideoFile) {
    val tokens = MaterialTheme.nuvio
    Text(
        text = file.displayName,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = tokens.colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OriginBadge(file.origin)
        Text(
            text = listOfNotNull(
                formatDownloadBytes(file.sizeBytes),
                file.modifiedAtEpochMs?.let { formatLocalDateTime(it) },
            ).joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OriginBadge(origin: ExternalVideoOrigin) {
    val tokens = MaterialTheme.nuvio
    val color = when (origin) {
        ExternalVideoOrigin.External -> tokens.colors.warning
        ExternalVideoOrigin.OtherProfile -> tokens.colors.info
    }
    Text(
        text = stringResource(
            when (origin) {
                ExternalVideoOrigin.External -> Res.string.downloads_settings_badge_external
                ExternalVideoOrigin.OtherProfile -> Res.string.downloads_settings_badge_other_profile
            },
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun downloadStatusLine(item: DownloadItem): String {
    val progress = if (item.totalBytes != null && item.totalBytes > 0L) {
        "${formatDownloadBytes(item.downloadedBytes)} / ${formatDownloadBytes(item.totalBytes)}"
    } else {
        formatDownloadBytes(item.downloadedBytes)
    }
    if (item.isConverting) {
        return stringResource(Res.string.downloads_status_converting, (item.progressFraction * 100f).toInt())
    }
    return when (item.status) {
        DownloadStatus.Downloading -> stringResource(Res.string.downloads_status_downloading, progress)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_status_paused, progress)
        DownloadStatus.Failed -> item.errorMessage ?: stringResource(Res.string.downloads_status_failed)
        DownloadStatus.Completed -> listOf(
            formatDownloadBytes(item.sizeOnDisk),
            formatLocalDateTime(item.updatedAtEpochMs),
        ).joinToString(" • ")
    }
}

@Composable
private fun DownloadTransferButton(item: DownloadItem) {
    val icon = when (item.status) {
        DownloadStatus.Downloading -> Icons.Rounded.Pause
        DownloadStatus.Paused -> Icons.Rounded.PlayArrow
        else -> Icons.Rounded.Refresh
    }
    val label = when (item.status) {
        DownloadStatus.Downloading -> Res.string.compose_action_pause
        DownloadStatus.Paused -> Res.string.action_resume
        else -> Res.string.action_retry
    }
    DownloadsActionButton(
        icon = icon,
        contentDescription = stringResource(label),
        onClick = {
            when (item.status) {
                DownloadStatus.Downloading -> DownloadsRepository.pauseDownload(item.id)
                DownloadStatus.Paused -> DownloadsRepository.resumeDownload(item.id)
                else -> DownloadsRepository.retryDownload(item.id)
            }
        },
    )
}

/** Icon button with a thin border, used by every action of the downloads list. */
@Composable
private fun DownloadsActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.nuvio.colors.textSecondary,
    borderColor: Color = MaterialTheme.nuvio.colors.borderSubtle,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp).size(36.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

private data class DownloadMenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun DownloadActionsDialog(
    entry: DownloadEntry,
    onDismiss: () -> Unit,
    onCast: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onChanged: () -> Unit,
) {
    val castState by CastPlatform.state.collectAsStateWithLifecycle()
    val castAvailable = castState.isAvailable && !TvAppRuntime.isTvApp
    val shareFailedText = stringResource(Res.string.downloads_share_failed)
    val openFolderFailedText = stringResource(Res.string.downloads_open_directory_failed)
    val item = (entry as? DownloadEntry.App)?.item
    val resumable = remember(item?.videoId) {
        item?.videoId?.takeIf { it.isNotBlank() }
            ?.let(WatchProgressRepository::progressForVideo)
            ?.isResumable == true
    }

    val playLabel = stringResource(if (resumable) Res.string.action_resume else Res.string.action_play)
    val playFromStartLabel = stringResource(Res.string.downloads_action_play_from_start)
    val externalPlayerLabel = stringResource(Res.string.downloads_action_external_player)
    val castLabel = stringResource(Res.string.cast_stream_action)
    val openFolderLabel = stringResource(Res.string.downloads_action_open_folder)
    val shareLabel = stringResource(Res.string.downloads_action_share)
    val detailsLabel = stringResource(Res.string.downloads_action_details)
    val infoLabel = stringResource(Res.string.downloads_action_info)
    val deleteLabel = stringResource(Res.string.action_delete)
    val pauseLabel = stringResource(Res.string.compose_action_pause)
    val resumeLabel = stringResource(Res.string.action_resume)
    val retryLabel = stringResource(Res.string.action_retry)

    val fileUri = when (entry) {
        is DownloadEntry.App -> entry.item.localFileUri
        is DownloadEntry.External -> entry.file.uri
    }
    val fileName = when (entry) {
        is DownloadEntry.App -> entry.item.fileName
        is DownloadEntry.External -> entry.file.fileName
    }
    val folderUri = when (entry) {
        is DownloadEntry.App -> entry.item.storageTreeUri
        is DownloadEntry.External -> DownloadsFolderPlatform.selectedFolder.value?.treeUri
    }

    val actions = buildList {
        if (entry.isPlayable) {
            add(DownloadMenuAction(Icons.Rounded.PlayArrow, playLabel) { entry.play(DownloadPlaybackMode.Default) })
            if (resumable) {
                add(DownloadMenuAction(Icons.Rounded.Replay, playFromStartLabel) {
                    entry.play(DownloadPlaybackMode.Default, fromStart = true)
                })
            }
            add(DownloadMenuAction(Icons.AutoMirrored.Rounded.OpenInNew, externalPlayerLabel) {
                entry.play(DownloadPlaybackMode.ExternalPlayer)
            })
            if (castAvailable) add(DownloadMenuAction(Icons.Rounded.Cast, castLabel, onClick = onCast))
        }
        if (item != null && item.status != DownloadStatus.Completed) {
            when (item.status) {
                DownloadStatus.Downloading -> add(DownloadMenuAction(Icons.Rounded.Pause, pauseLabel) {
                    DownloadsRepository.pauseDownload(item.id)
                })
                DownloadStatus.Paused -> add(DownloadMenuAction(Icons.Rounded.PlayArrow, resumeLabel) {
                    DownloadsRepository.resumeDownload(item.id)
                })
                else -> add(DownloadMenuAction(Icons.Rounded.Refresh, retryLabel) {
                    DownloadsRepository.retryDownload(item.id)
                })
            }
        }
        if (entry.isPlayable) {
            add(DownloadMenuAction(Icons.Rounded.FolderOpen, openFolderLabel) {
                if (!DownloadsFolderPlatform.openFolder(folderUri)) NuvioToastController.show(openFolderFailedText)
            })
            if (fileUri != null && !TvAppRuntime.isTvApp) {
                add(DownloadMenuAction(Icons.Rounded.Share, shareLabel) {
                    if (!DownloadsFolderPlatform.shareFile(fileUri, fileName)) NuvioToastController.show(shareFailedText)
                })
            }
        }
        if (item != null && item.parentMetaId.isNotBlank()) {
            add(DownloadMenuAction(Icons.Rounded.Info, detailsLabel) {
                DownloadsActionBridge.send(DownloadsAction.OpenDetails(item))
            })
        }
        add(DownloadMenuAction(Icons.Rounded.Description, infoLabel, onClick = onInfo))
        add(DownloadMenuAction(Icons.Rounded.Delete, deleteLabel, destructive = true, onClick = onDelete))
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = when (entry) {
            is DownloadEntry.App -> entry.item.displayTitle()
            is DownloadEntry.External -> entry.file.displayName
        },
    ) {
        NuvioActionMenuGroup(modifier = Modifier.ignoreHeldConfirmKey()) {
            actions.forEachIndexed { index, action ->
                if (index > 0) NuvioActionMenuDivider()
                NuvioActionMenuRow(
                    title = action.label,
                    icon = action.icon,
                    destructive = action.destructive,
                    onClick = {
                        onDismiss()
                        action.onClick()
                        onChanged()
                    },
                )
            }
        }
    }
}

/**
 * TV: the menu opens while OK is still held on the card (long press). Its repeats and its
 * release must not activate the first row: OK works again after it has been released, or
 * after a pause without repeats.
 */
@Composable
private fun Modifier.ignoreHeldConfirmKey(): Modifier {
    if (!TvAppRuntime.isTvApp) return this
    val guard = remember { HeldConfirmKeyGuard() }
    return onPreviewKeyEvent { event ->
        val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        guard.shouldConsume(isConfirm, event.type)
    }
}

private class HeldConfirmKeyGuard {
    private val openedAt = TimeSource.Monotonic.markNow()
    private var lastConfirmDown = openedAt
    private var armed = false

    fun shouldConsume(isConfirm: Boolean, type: KeyEventType): Boolean {
        if (armed) return false
        if (!isConfirm) {
            armed = true
            return false
        }
        return when (type) {
            KeyEventType.KeyUp -> {
                armed = true
                true
            }
            KeyEventType.KeyDown -> {
                val now = TimeSource.Monotonic.markNow()
                val isRepeat = (now - lastConfirmDown).inWholeMilliseconds < HELD_KEY_REPEAT_GAP_MS
                lastConfirmDown = now
                if (!isRepeat && openedAt.elapsedNow().inWholeMilliseconds > HELD_KEY_GRACE_MS) {
                    armed = true
                    false
                } else {
                    true
                }
            }
            else -> false
        }
    }
}

private const val HELD_KEY_REPEAT_GAP_MS = 250L
private const val HELD_KEY_GRACE_MS = 600L

@Composable
private fun DownloadInfoDialog(entry: DownloadEntry, onDismiss: () -> Unit) {
    val lines = buildList {
        when (entry) {
            is DownloadEntry.App -> {
                val item = entry.item
                add(stringResource(Res.string.downloads_info_name) to item.fileName)
                add(
                    stringResource(Res.string.downloads_info_location) to
                        DownloadsFolderPlatform.describeLocation(item.storageTreeUri, item.fileName),
                )
                add(stringResource(Res.string.downloads_info_size) to formatDownloadBytes(item.sizeOnDisk))
                add(stringResource(Res.string.downloads_info_date) to formatLocalDateTime(item.updatedAtEpochMs))
                add(stringResource(Res.string.downloads_info_status) to downloadStatusLine(item))
                add(
                    stringResource(Res.string.downloads_info_source) to
                        listOf(item.providerName, item.streamTitle).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                )
                add(stringResource(Res.string.downloads_info_origin) to stringResource(Res.string.downloads_info_origin_app))
            }
            is DownloadEntry.External -> {
                val file = entry.file
                add(stringResource(Res.string.downloads_info_name) to file.fileName)
                add(
                    stringResource(Res.string.downloads_info_location) to DownloadsFolderPlatform.describeLocation(
                        DownloadsFolderPlatform.selectedFolder.value?.treeUri,
                        file.fileName,
                    ),
                )
                add(stringResource(Res.string.downloads_info_size) to formatDownloadBytes(file.sizeBytes))
                file.modifiedAtEpochMs?.let {
                    add(stringResource(Res.string.downloads_info_date) to formatLocalDateTime(it))
                }
                add(
                    stringResource(Res.string.downloads_info_origin) to stringResource(
                        when (file.origin) {
                            ExternalVideoOrigin.External -> Res.string.downloads_settings_badge_external
                            ExternalVideoOrigin.OtherProfile -> Res.string.downloads_settings_badge_other_profile
                        },
                    ),
                )
            }
        }
    }
    val tokens = MaterialTheme.nuvio
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.downloads_action_info),
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                lines.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary)
                    }
                }
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_close),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun DownloadDeleteDialog(entry: DownloadEntry, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val deleteFailedText = stringResource(Res.string.downloads_delete_failed)
    NuvioStatusModal(
        title = stringResource(Res.string.action_delete_confirm_title),
        message = when (entry) {
            is DownloadEntry.App -> stringResource(Res.string.downloads_delete_message)
            is DownloadEntry.External -> stringResource(Res.string.downloads_delete_external_message, entry.file.fileName)
        },
        isVisible = true,
        destructive = true,
        confirmText = stringResource(Res.string.action_delete),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            when (entry) {
                is DownloadEntry.App -> DownloadsRepository.cancelDownload(entry.item.id)
                is DownloadEntry.External -> {
                    if (!DownloadsFolderPlatform.deleteFolderFile(entry.file.uri)) {
                        NuvioToastController.show(deleteFailedText)
                    }
                }
            }
            onDeleted()
        },
        onDismiss = onDismiss,
    )
}
