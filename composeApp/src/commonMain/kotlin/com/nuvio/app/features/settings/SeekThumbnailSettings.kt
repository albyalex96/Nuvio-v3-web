package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.seekthumb.SeekThumbnailSize
import com.nuvio.app.features.player.seekthumb.seekThumbnailsSupported
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.settings_playback_section_seek_thumbnails
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_description
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_size
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_size_large
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_size_medium
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_size_small
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_wifi_only
import nuvio.composeapp.generated.resources.settings_playback_seek_thumbnails_wifi_only_description
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val SeekThumbnailSize.labelRes: StringResource
    get() = when (this) {
        SeekThumbnailSize.Small -> Res.string.settings_playback_seek_thumbnails_size_small
        SeekThumbnailSize.Medium -> Res.string.settings_playback_seek_thumbnails_size_medium
        SeekThumbnailSize.Large -> Res.string.settings_playback_seek_thumbnails_size_large
    }

/** "Seek thumbnails" section of the playback page; absent where the platform has no preview decoder. */
@Composable
internal fun SeekThumbnailSettingsSection(
    settings: PlayerSettingsUiState,
    isTablet: Boolean,
) {
    if (!seekThumbnailsSupported) return
    var showSizeDialog by remember { mutableStateOf(false) }
    val usable = settings.seekThumbnailsEnabled && !settings.externalPlayerEnabled

    SettingsSection(
        title = stringResource(Res.string.settings_playback_section_seek_thumbnails),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_seek_thumbnails),
                description = stringResource(Res.string.settings_playback_seek_thumbnails_description),
                checked = settings.seekThumbnailsEnabled,
                enabled = !settings.externalPlayerEnabled,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setSeekThumbnailsEnabled,
            )
            // A PC is on Ethernet or home Wi-Fi: no mobile data to save there.
            if (!com.nuvio.app.isDesktop) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_seek_thumbnails_wifi_only),
                    description = stringResource(Res.string.settings_playback_seek_thumbnails_wifi_only_description),
                    checked = settings.seekThumbnailsWifiOnly,
                    enabled = usable,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setSeekThumbnailsWifiOnly,
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_playback_seek_thumbnails_size),
                description = stringResource(settings.seekThumbnailSize.labelRes),
                enabled = usable,
                isTablet = isTablet,
                onClick = { showSizeDialog = true },
            )
        }
    }

    if (showSizeDialog) {
        DialogSurface(
            onDismissRequest = { showSizeDialog = false },
            title = stringResource(Res.string.settings_playback_seek_thumbnails_size),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeekThumbnailSize.entries.forEach { size ->
                    DialogOption(
                        text = stringResource(size.labelRes),
                        selected = size == settings.seekThumbnailSize,
                        onClick = {
                            PlayerSettingsRepository.setSeekThumbnailSize(size)
                            showSizeDialog = false
                        },
                    )
                }
            }
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_done),
                    onClick = { showSizeDialog = false },
                )
            }
        }
    }
}
