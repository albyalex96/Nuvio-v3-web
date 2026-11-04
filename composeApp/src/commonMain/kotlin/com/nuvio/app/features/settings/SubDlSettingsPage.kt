package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.subdl.SUBDL_LOG_TAG
import com.nuvio.app.features.subdl.SubDlApi
import com.nuvio.app.features.subdl.SubDlKeyCheck
import com.nuvio.app.features.subdl.SubDlRepository
import com.nuvio.app.features.subdl.toSubDlLanguage
import com.nuvio.app.features.player.AvailableLanguageOptions
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag(SUBDL_LOG_TAG)

internal fun LazyListScope.subDlSettingsContent(
    isTablet: Boolean,
) {
    item(key = "subdl_settings") {
        SubDlSettingsSection(isTablet = isTablet)
    }
}

@Composable
private fun SubDlSettingsSection(
    isTablet: Boolean,
) {
    val settings by SubDlRepository.uiState.collectAsStateWithLifecycle()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLanguagesDialog by remember { mutableStateOf(false) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)
    // Only the languages SubDL can search.
    val languageLabels = AvailableLanguageOptions
        .filter { toSubDlLanguage(it.code) != null }
        .associate { it.code to stringResource(it.labelRes) }
    val languagesSummary = settings.languages
        .mapNotNull { languageLabels[it] }
        .joinToString(", ")
        .ifBlank { stringResource(Res.string.subdl_languages_player_default) }

    SettingsSection(
        title = stringResource(Res.string.compose_settings_page_subdl),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.subdl_enabled),
                description = stringResource(Res.string.subdl_enabled_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "SubDL ${if (enabled) "enabled" else "disabled"}" }
                    SubDlRepository.setEnabled(enabled)
                },
            )
            if (settings.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.subdl_api_key),
                    description = settings.apiKey.takeIf { it.isNotBlank() }?.let(::maskedSecret) ?: notSetLabel,
                    isTablet = isTablet,
                    onClick = { showApiKeyDialog = true },
                )
            }
        }
    }

    if (settings.enabled) {
        SettingsSection(
            title = stringResource(Res.string.subdl_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.subdl_languages),
                    description = languagesSummary,
                    isTablet = isTablet,
                    onClick = { showLanguagesDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.subdl_hearing_impaired),
                    description = stringResource(Res.string.subdl_hearing_impaired_description),
                    checked = settings.includeHearingImpaired,
                    isTablet = isTablet,
                    onCheckedChange = SubDlRepository::setIncludeHearingImpaired,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.subdl_ai_translated),
                    description = stringResource(Res.string.subdl_ai_translated_description),
                    checked = settings.includeAiTranslated,
                    isTablet = isTablet,
                    onCheckedChange = SubDlRepository::setIncludeAiTranslated,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.subdl_season_packs),
                    description = stringResource(Res.string.subdl_season_packs_description),
                    checked = settings.includeSeasonPacks,
                    isTablet = isTablet,
                    onCheckedChange = SubDlRepository::setIncludeSeasonPacks,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.subdl_autosync),
                    description = stringResource(Res.string.subdl_autosync_description),
                    checked = settings.autoSyncEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        log.i { "SubDL AutoSync ${if (enabled) "enabled" else "disabled"}" }
                        SubDlRepository.setAutoSyncEnabled(enabled)
                    },
                )
            }
        }
    }

    if (showApiKeyDialog) {
        SubDlApiKeyDialog(
            initialValue = settings.apiKey,
            onSave = {
                log.i { "SubDL API key ${if (it.isBlank()) "cleared" else "updated"}" }
                SubDlRepository.setApiKey(it)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showLanguagesDialog) {
        SubDlLanguagesDialog(
            selected = settings.languages,
            labels = languageLabels,
            onChange = { languages ->
                log.d { "SubDL languages: ${languages.ifEmpty { listOf("player default") }}" }
                SubDlRepository.setLanguages(languages)
            },
            onDismiss = { showLanguagesDialog = false },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubDlApiKeyDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initialValue) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidKeyMessage = stringResource(Res.string.subdl_api_key_invalid)

    DialogSurface(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = stringResource(Res.string.subdl_api_key),
    ) {
        Text(
            text = stringResource(Res.string.subdl_api_key_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = value,
            onValueChange = {
                value = it
                errorMessage = null
            },
            label = stringResource(Res.string.subdl_api_key),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
        )
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                enabled = !isVerifying,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty() || trimmed == initialValue) {
                        onSave(trimmed)
                        return@DialogButton
                    }
                    isVerifying = true
                    errorMessage = null
                    scope.launch {
                        val check = SubDlApi.verifyApiKey(trimmed)
                        isVerifying = false
                        // A network problem must not block saving: only an explicit rejection does.
                        if (check == SubDlKeyCheck.Invalid) {
                            log.w { "SubDL rejected the API key" }
                            errorMessage = invalidKeyMessage
                        } else {
                            onSave(trimmed)
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
                loading = isVerifying,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubDlLanguagesDialog(
    selected: List<String>,
    labels: Map<String, String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sortedCodes = remember(labels) { labels.entries.sortedBy { it.value.lowercase() }.map { it.key } }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.subdl_languages),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "player_default") {
                DialogOption(
                    text = stringResource(Res.string.subdl_languages_clear),
                    description = stringResource(Res.string.subdl_languages_player_default),
                    selected = selected.isEmpty(),
                    onClick = { onChange(emptyList()) },
                )
            }
            items(sortedCodes, key = { it }) { code ->
                val isSelected = code in selected
                DialogOption(
                    text = labels[code].orEmpty(),
                    selected = isSelected,
                    role = Role.Checkbox,
                    onClick = { onChange(if (isSelected) selected - code else selected + code) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}
