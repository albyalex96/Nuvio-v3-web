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
import com.nuvio.app.features.opensubtitles.OPENSUBTITLES_LOG_TAG
import com.nuvio.app.features.opensubtitles.OpenSubtitlesApi
import com.nuvio.app.features.opensubtitles.OpenSubtitlesKeyCheck
import com.nuvio.app.features.opensubtitles.OpenSubtitlesRepository
import com.nuvio.app.features.player.AvailableLanguageOptions
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.compose_settings_page_opensubtitles
import nuvio.composeapp.generated.resources.opensubtitles_api_key
import nuvio.composeapp.generated.resources.opensubtitles_api_key_description
import nuvio.composeapp.generated.resources.opensubtitles_api_key_invalid
import nuvio.composeapp.generated.resources.opensubtitles_enabled
import nuvio.composeapp.generated.resources.opensubtitles_enabled_description
import nuvio.composeapp.generated.resources.opensubtitles_hearing_impaired
import nuvio.composeapp.generated.resources.opensubtitles_hearing_impaired_description
import nuvio.composeapp.generated.resources.opensubtitles_languages
import nuvio.composeapp.generated.resources.opensubtitles_languages_clear
import nuvio.composeapp.generated.resources.opensubtitles_languages_player_default
import nuvio.composeapp.generated.resources.opensubtitles_machine_translated
import nuvio.composeapp.generated.resources.opensubtitles_machine_translated_description
import nuvio.composeapp.generated.resources.opensubtitles_autosync
import nuvio.composeapp.generated.resources.opensubtitles_autosync_description
import nuvio.composeapp.generated.resources.opensubtitles_section_search
import nuvio.composeapp.generated.resources.settings_playback_not_set
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)

internal fun LazyListScope.openSubtitlesSettingsContent(
    isTablet: Boolean,
) {
    item(key = "opensubtitles_settings") {
        OpenSubtitlesSettingsSection(isTablet = isTablet)
    }
}

/** Shows only the end of a secret, e.g. `••••a1b2`. */
internal fun maskedSecret(value: String): String =
    if (value.length <= 4) "••••" else "••••${value.takeLast(4)}"

@Composable
private fun OpenSubtitlesSettingsSection(
    isTablet: Boolean,
) {
    val settings by OpenSubtitlesRepository.uiState.collectAsStateWithLifecycle()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLanguagesDialog by remember { mutableStateOf(false) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)
    val languageLabels = AvailableLanguageOptions.associate { it.code to stringResource(it.labelRes) }
    val languagesSummary = settings.languages
        .mapNotNull { languageLabels[it] }
        .joinToString(", ")
        .ifBlank { stringResource(Res.string.opensubtitles_languages_player_default) }

    SettingsSection(
        title = stringResource(Res.string.compose_settings_page_opensubtitles),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.opensubtitles_enabled),
                description = stringResource(Res.string.opensubtitles_enabled_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "OpenSubtitles ${if (enabled) "enabled" else "disabled"}" }
                    OpenSubtitlesRepository.setEnabled(enabled)
                },
            )
            if (settings.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.opensubtitles_api_key),
                    description = settings.apiKey.takeIf { it.isNotBlank() }?.let(::maskedSecret) ?: notSetLabel,
                    isTablet = isTablet,
                    onClick = { showApiKeyDialog = true },
                )
            }
        }
    }

    if (settings.enabled) {
        SettingsSection(
            title = stringResource(Res.string.opensubtitles_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.opensubtitles_languages),
                    description = languagesSummary,
                    isTablet = isTablet,
                    onClick = { showLanguagesDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.opensubtitles_hearing_impaired),
                    description = stringResource(Res.string.opensubtitles_hearing_impaired_description),
                    checked = settings.includeHearingImpaired,
                    isTablet = isTablet,
                    onCheckedChange = OpenSubtitlesRepository::setIncludeHearingImpaired,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.opensubtitles_machine_translated),
                    description = stringResource(Res.string.opensubtitles_machine_translated_description),
                    checked = settings.includeMachineTranslated,
                    isTablet = isTablet,
                    onCheckedChange = OpenSubtitlesRepository::setIncludeMachineTranslated,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.opensubtitles_autosync),
                    description = stringResource(Res.string.opensubtitles_autosync_description),
                    checked = settings.autoSyncEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        log.i { "OpenSubtitles AutoSync ${if (enabled) "enabled" else "disabled"}" }
                        OpenSubtitlesRepository.setAutoSyncEnabled(enabled)
                    },
                )
            }
        }
    }

    if (showApiKeyDialog) {
        OpenSubtitlesApiKeyDialog(
            initialValue = settings.apiKey,
            onSave = {
                log.i { "OpenSubtitles API key ${if (it.isBlank()) "cleared" else "updated"}" }
                OpenSubtitlesRepository.setApiKey(it)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showLanguagesDialog) {
        OpenSubtitlesLanguagesDialog(
            selected = settings.languages,
            labels = languageLabels,
            onChange = { languages ->
                log.d { "OpenSubtitles languages: ${languages.ifEmpty { listOf("player default") }}" }
                OpenSubtitlesRepository.setLanguages(languages)
            },
            onDismiss = { showLanguagesDialog = false },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OpenSubtitlesApiKeyDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initialValue) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidKeyMessage = stringResource(Res.string.opensubtitles_api_key_invalid)

    DialogSurface(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = stringResource(Res.string.opensubtitles_api_key),
    ) {
        Text(
            text = stringResource(Res.string.opensubtitles_api_key_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = value,
            onValueChange = {
                value = it
                errorMessage = null
            },
            label = stringResource(Res.string.opensubtitles_api_key),
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
                        val check = OpenSubtitlesApi.verifyApiKey(trimmed)
                        isVerifying = false
                        // A network problem must not block saving: only an explicit rejection does.
                        if (check == OpenSubtitlesKeyCheck.Invalid) {
                            log.w { "OpenSubtitles rejected the API key" }
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
private fun OpenSubtitlesLanguagesDialog(
    selected: List<String>,
    labels: Map<String, String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sortedCodes = remember(labels) { labels.entries.sortedBy { it.value.lowercase() }.map { it.key } }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.opensubtitles_languages),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "player_default") {
                DialogOption(
                    text = stringResource(Res.string.opensubtitles_languages_clear),
                    description = stringResource(Res.string.opensubtitles_languages_player_default),
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
