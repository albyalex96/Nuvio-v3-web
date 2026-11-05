package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.logging.AppLogEntry
import com.nuvio.app.core.logging.AppLogLevel
import com.nuvio.app.core.logging.AppLogPlatform
import com.nuvio.app.core.logging.AppLogSource
import com.nuvio.app.core.logging.AppLogStore
import com.nuvio.app.core.logging.AppLogText
import com.nuvio.app.core.logging.AppLoggerSettings
import com.nuvio.app.core.logging.rememberAppLogFileSharer
import com.nuvio.app.core.logging.writeAppLogExportFile
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.app_logs_categories_selected
import nuvio.composeapp.generated.resources.app_logs_category_all
import nuvio.composeapp.generated.resources.app_logs_category_option
import nuvio.composeapp.generated.resources.app_logs_category_sheet_hint
import nuvio.composeapp.generated.resources.app_logs_category_sheet_title
import nuvio.composeapp.generated.resources.app_logs_category_title
import nuvio.composeapp.generated.resources.app_logs_clear_confirm
import nuvio.composeapp.generated.resources.app_logs_clear_description
import nuvio.composeapp.generated.resources.app_logs_clear_dialog_message
import nuvio.composeapp.generated.resources.app_logs_clear_dialog_title
import nuvio.composeapp.generated.resources.app_logs_clear_title
import nuvio.composeapp.generated.resources.app_logs_copied
import nuvio.composeapp.generated.resources.app_logs_copied_truncated
import nuvio.composeapp.generated.resources.app_logs_copy_description
import nuvio.composeapp.generated.resources.app_logs_copy_title
import nuvio.composeapp.generated.resources.app_logs_empty
import nuvio.composeapp.generated.resources.app_logs_empty_disabled
import nuvio.composeapp.generated.resources.app_logs_enabled_description
import nuvio.composeapp.generated.resources.app_logs_enabled_title
import nuvio.composeapp.generated.resources.app_logs_import_description
import nuvio.composeapp.generated.resources.app_logs_import_failed
import nuvio.composeapp.generated.resources.app_logs_import_title
import nuvio.composeapp.generated.resources.app_logs_imported
import nuvio.composeapp.generated.resources.app_logs_level_debug
import nuvio.composeapp.generated.resources.app_logs_level_error
import nuvio.composeapp.generated.resources.app_logs_level_info
import nuvio.composeapp.generated.resources.app_logs_level_warn
import nuvio.composeapp.generated.resources.app_logs_levels_description
import nuvio.composeapp.generated.resources.app_logs_levels_title
import nuvio.composeapp.generated.resources.app_logs_live_description
import nuvio.composeapp.generated.resources.app_logs_live_title
import nuvio.composeapp.generated.resources.app_logs_nothing_to_export
import nuvio.composeapp.generated.resources.app_logs_search_clear
import nuvio.composeapp.generated.resources.app_logs_search_placeholder
import nuvio.composeapp.generated.resources.app_logs_section_actions
import nuvio.composeapp.generated.resources.app_logs_section_entries
import nuvio.composeapp.generated.resources.app_logs_section_filters
import nuvio.composeapp.generated.resources.app_logs_section_recording
import nuvio.composeapp.generated.resources.app_logs_share_description
import nuvio.composeapp.generated.resources.app_logs_share_failed
import nuvio.composeapp.generated.resources.app_logs_share_title
import nuvio.composeapp.generated.resources.app_logs_system_badge
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The clipboard can't carry megabytes: "copy" keeps the newest entries within this size. */
private const val CLIPBOARD_MAX_CHARS = 300_000
private const val COLLAPSED_MESSAGE_MAX_LINES = 4
private const val EXPORT_FILE_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"
private const val ENTRY_TIME_PATTERN = "HH:mm:ss.SSS"
private const val ENTRY_DAY_TIME_PATTERN = "dd/MM HH:mm:ss.SSS"
private const val DAY_KEY_PATTERN = "yyyyMMdd"

/** The log console has a fixed height (a share of the window) and scrolls on its own. */
private const val CONSOLE_WINDOW_HEIGHT_FRACTION = 0.7f
private val ConsoleMinHeight = 280.dp
private val ConsoleMaxHeight = 900.dp

/** The category picker never grows past this share of the window (phone, tablet, TV, desktop); its list scrolls. */
private const val CATEGORY_SHEET_MAX_HEIGHT_FRACTION = 0.6f

private val AppLogInfoColor = Color(0xFF4FA3FF)
private val AppLogWarnColor = Color(0xFFFFB020)
private val AppLogErrorColor = Color(0xFFFF5A5F)

/** "App logs" page (Settings → Advanced → Diagnostics). */
internal fun LazyListScope.appLogsSettingsContent(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    item(key = "app_logs_recording") {
        AppLogsRecordingSection(isTablet = isTablet)
    }
    item(key = "app_logs_filters") {
        AppLogsFiltersSection(isTablet = isTablet, viewer = viewer)
    }
    item(key = "app_logs_actions") {
        AppLogsActionsSection(isTablet = isTablet, viewer = viewer)
    }

    item(key = "app_logs_console") {
        AppLogsConsole(isTablet = isTablet, viewer = viewer)
    }
}

/**
 * Fixed-height card holding the entries in its own lazy list: the settings page keeps its size and
 * only the console scrolls. With live updates on, it stays pinned to the newest entry while at the top.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppLogsConsole(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    val tokens = MaterialTheme.nuvio
    val result = viewer.result
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val consoleHeight = (windowHeight * CONSOLE_WINDOW_HEIGHT_FRACTION).coerceIn(ConsoleMinHeight, ConsoleMaxHeight)
    val listState = rememberLazyListState()
    var stickToTop by remember { mutableStateOf(true) }

    // Only the user's scrolling decides whether the console follows the newest entries.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                stickToTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
    }
    val newestId = result.entries.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (stickToTop && newestId != null) listState.scrollToItem(0)
    }

    Column {
        NuvioSectionLabel(
            text = stringResource(Res.string.app_logs_section_entries, result.entries.size, result.totalCount),
        )
        Spacer(modifier = Modifier.height(if (isTablet) tokens.spacing.listGap else NuvioTokens.Space.s10))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(consoleHeight),
            color = tokens.colors.surface,
            shape = if (isTablet) RoundedCornerShape(NuvioTokens.Radius.xl) else tokens.shapes.compactCard,
            border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
        ) {
            if (result.entries.isEmpty()) {
                AppLogsEmptyState(isTablet = isTablet)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = result.entries,
                        key = { _, entry -> entry.id },
                        contentType = { _, _ -> "app_log_entry" },
                    ) { index, entry ->
                        AppLogEntryRow(
                            entry = entry,
                            expanded = entry.id in viewer.expandedIds,
                            showDivider = index < result.entries.lastIndex,
                            onClick = { viewer.toggleExpanded(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLogsRecordingSection(isTablet: Boolean) {
    val enabled by remember {
        AppLoggerSettings.ensureLoaded()
        AppLoggerSettings.enabled
    }.collectAsStateWithLifecycle()

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_recording),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.app_logs_enabled_title),
                description = stringResource(Res.string.app_logs_enabled_description),
                checked = enabled,
                isTablet = isTablet,
                onCheckedChange = AppLoggerSettings::setEnabled,
            )
        }
    }
}

@Composable
private fun AppLogsFiltersSection(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    val tokens = MaterialTheme.nuvio
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    val filter = viewer.filter
    val result = viewer.result
    val allCategoriesLabel = stringResource(Res.string.app_logs_category_all)
    val selectedCategoriesLabel = when (filter.categories.size) {
        0 -> allCategoriesLabel
        1, 2 -> filter.categories.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString()
        else -> stringResource(Res.string.app_logs_categories_selected, filter.categories.size)
    }

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_filters),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_category_title),
                description = selectedCategoriesLabel,
                isTablet = isTablet,
                onClick = { showCategorySheet = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsChipRow(
                title = stringResource(Res.string.app_logs_levels_title),
                description = stringResource(Res.string.app_logs_levels_description),
                isTablet = isTablet,
            ) {
                AppLogLevel.entries.forEach { level ->
                    AppLogLevelCheckbox(
                        label = stringResource(level.labelRes),
                        color = level.color(),
                        checked = level in filter.levels,
                        onToggle = { viewer.toggleLevel(level) },
                    )
                }
            }
            SettingsGroupDivider(isTablet = isTablet)
            NuvioInputField(
                value = filter.query,
                onValueChange = { viewer.filter = viewer.filter.copy(query = it) },
                placeholder = stringResource(Res.string.app_logs_search_placeholder),
                modifier = Modifier.padding(
                    horizontal = if (isTablet) 20.dp else 16.dp,
                    vertical = 12.dp,
                ),
                trailingContent = if (filter.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewer.filter = viewer.filter.copy(query = "") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(Res.string.app_logs_search_clear),
                                tint = tokens.colors.textMuted,
                            )
                        }
                    }
                } else {
                    null
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.app_logs_live_title),
                description = stringResource(Res.string.app_logs_live_description),
                checked = viewer.live,
                isTablet = isTablet,
                onCheckedChange = { viewer.live = it },
            )
        }
    }

    if (showCategorySheet) {
        AppLogsCategorySheet(
            categories = result.categories,
            totalCount = result.totalCount,
            selected = filter.categories,
            allCategoriesLabel = allCategoriesLabel,
            onToggle = viewer::toggleCategory,
            onSelectAll = viewer::clearCategories,
            onDismiss = { showCategorySheet = false },
        )
    }
}

/** Multi-select category picker: "All categories" clears the selection, every other row toggles a tag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLogsCategorySheet(
    categories: List<Pair<String, Int>>,
    totalCount: Int,
    selected: Set<String>,
    allCategoriesLabel: String,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }
    // Tags selected earlier but no longer in the list (e.g. after clearing) stay visible to be unticked.
    val rows = remember(categories, selected) {
        val known = categories.map { it.first }.toSet()
        categories + selected.filter { it !in known }.map { it to 0 }
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val sheetMaxHeight = windowHeight * CATEGORY_SHEET_MAX_HEIGHT_FRACTION

    NuvioModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .padding(bottom = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = stringResource(Res.string.app_logs_category_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.app_logs_category_sheet_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            item {
                AppLogsCategoryRow(
                    label = stringResource(Res.string.app_logs_category_option, allCategoriesLabel, totalCount),
                    checked = selected.isEmpty(),
                    onClick = onSelectAll,
                )
            }
            items(rows, key = { it.first }) { (category, count) ->
                AppLogsCategoryRow(
                    label = stringResource(Res.string.app_logs_category_option, category, count),
                    checked = category in selected,
                    onClick = { onToggle(category) },
                )
            }
            item {
                DialogButtons(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DialogButton(
                        text = stringResource(Res.string.action_done),
                        onClick = dismiss,
                        style = DialogButtonStyle.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLogsCategoryRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor = tokens.colors.accent,
                uncheckedColor = tokens.colors.textMuted,
                checkmarkColor = tokens.colors.onAccent,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppLogLevelCheckbox(
    label: String,
    color: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = color.copy(alpha = 0.7f),
                checkmarkColor = Color.Black,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) color else color.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppLogsActionsSection(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    val clipboardManager = LocalClipboardManager.current
    val shareFile = rememberAppLogFileSharer()
    val scope = rememberCoroutineScope()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val nothingToExport = stringResource(Res.string.app_logs_nothing_to_export)
    val shareFailed = stringResource(Res.string.app_logs_share_failed)
    val importFailed = stringResource(Res.string.app_logs_import_failed)

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_actions),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_copy_title),
                description = stringResource(Res.string.app_logs_copy_description),
                icon = Icons.Rounded.ContentCopy,
                isTablet = isTablet,
                onClick = {
                    val entries = viewer.result.filteredInOrder
                    if (entries.isEmpty()) {
                        NuvioToastController.show(nothingToExport)
                        return@SettingsNavigationRow
                    }
                    scope.launch {
                        val (text, count) = withContext(Dispatchers.Default) {
                            AppLogText.formatNewestWithin(entries, CLIPBOARD_MAX_CHARS)
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        NuvioToastController.show(
                            if (count < entries.size) {
                                getString(Res.string.app_logs_copied_truncated, count)
                            } else {
                                getString(Res.string.app_logs_copied, count)
                            },
                        )
                    }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_share_title),
                description = stringResource(Res.string.app_logs_share_description),
                icon = Icons.Rounded.Share,
                isTablet = isTablet,
                onClick = {
                    val entries = viewer.result.filteredInOrder
                    if (entries.isEmpty()) {
                        NuvioToastController.show(nothingToExport)
                        return@SettingsNavigationRow
                    }
                    scope.launch {
                        val path = withContext(Dispatchers.Default) {
                            val now = AppLogStore.nowEpochMs()
                            val fileName = "nuvio-log-${AppLogPlatform.formatTimestamp(now, EXPORT_FILE_TIMESTAMP_PATTERN)}.txt"
                            writeAppLogExportFile(fileName, AppLogText.format(entries))
                        }
                        if (path == null) {
                            NuvioToastController.show(shareFailed)
                        } else {
                            runCatching { shareFile(path) }.onFailure { NuvioToastController.show(shareFailed) }
                        }
                    }
                },
            )
            if (AppLogPlatform.supportsSystemLogImport) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.app_logs_import_title),
                    description = stringResource(Res.string.app_logs_import_description),
                    icon = Icons.Rounded.Terminal,
                    isTablet = isTablet,
                    onClick = {
                        scope.launch {
                            val lines = withContext(Dispatchers.Default) {
                                runCatching { AppLogPlatform.readSystemLog() }.getOrNull()
                            }
                            if (lines == null) {
                                NuvioToastController.show(importFailed)
                            } else {
                                val added = AppLogStore.import(lines)
                                NuvioToastController.show(getString(Res.string.app_logs_imported, added))
                            }
                        }
                    },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_clear_title),
                description = stringResource(Res.string.app_logs_clear_description),
                icon = Icons.Rounded.DeleteOutline,
                isTablet = isTablet,
                onClick = { showClearDialog = true },
            )
        }
    }

    if (showClearDialog) {
        AppLogsClearDialog(
            onConfirm = viewer::clear,
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun AppLogsClearDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.app_logs_clear_dialog_title),
    ) {
        Text(
            text = stringResource(Res.string.app_logs_clear_dialog_message),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textMuted,
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.app_logs_clear_confirm),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun AppLogsEmptyState(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    val recording by AppLoggerSettings.enabled.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (recording) Res.string.app_logs_empty else Res.string.app_logs_empty_disabled,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = 18.dp,
            ),
        )
    }
}

/** One line of the log console; tapping it shows the full message and stack trace. */
@Composable
private fun AppLogEntryRow(
    entry: AppLogEntry,
    expanded: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val levelColor = entry.level.color()
    val monoSmall = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick)
                .padding(end = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(levelColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = rememberEntryTimeLabel(entry.epochMs),
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textMuted,
                    )
                    Text(
                        text = stringResource(entry.level.labelRes).uppercase(),
                        style = monoSmall.copy(fontSize = 10.sp),
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(levelColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Text(
                        text = entry.tag,
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.source == AppLogSource.SYSTEM) {
                        Text(
                            text = stringResource(Res.string.app_logs_system_badge),
                            style = monoSmall.copy(fontSize = 10.sp),
                            color = tokens.colors.textMuted,
                            modifier = Modifier
                                .background(tokens.colors.borderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                Text(
                    text = entry.message,
                    style = monoSmall,
                    color = if (entry.level == AppLogLevel.ERROR) levelColor else tokens.colors.textPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MESSAGE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                val throwable = entry.throwable
                if (throwable != null) {
                    Text(
                        text = throwable,
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textMuted,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = tokens.borders.hairline,
                color = tokens.colors.borderSubtle,
            )
        }
    }
}

/** `HH:mm:ss.SSS` for today's entries, with the day in front for older ones (retention spans 24 h). */
@Composable
private fun rememberEntryTimeLabel(epochMs: Long): String =
    remember(epochMs) {
        val today = AppLogPlatform.formatTimestamp(AppLogStore.nowEpochMs(), DAY_KEY_PATTERN)
        val day = AppLogPlatform.formatTimestamp(epochMs, DAY_KEY_PATTERN)
        AppLogPlatform.formatTimestamp(epochMs, if (day == today) ENTRY_TIME_PATTERN else ENTRY_DAY_TIME_PATTERN)
    }

private val AppLogLevel.labelRes: StringResource
    get() = when (this) {
        AppLogLevel.DEBUG -> Res.string.app_logs_level_debug
        AppLogLevel.INFO -> Res.string.app_logs_level_info
        AppLogLevel.WARN -> Res.string.app_logs_level_warn
        AppLogLevel.ERROR -> Res.string.app_logs_level_error
    }

@Composable
private fun AppLogLevel.color(): Color =
    when (this) {
        AppLogLevel.DEBUG -> MaterialTheme.nuvio.colors.textMuted
        AppLogLevel.INFO -> AppLogInfoColor
        AppLogLevel.WARN -> AppLogWarnColor
        AppLogLevel.ERROR -> AppLogErrorColor
    }
