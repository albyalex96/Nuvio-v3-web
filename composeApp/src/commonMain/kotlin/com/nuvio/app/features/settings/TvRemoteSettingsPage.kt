package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.nuvio.app.features.tvremote.parseManualTvAddress
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.PhoneTvRemote
import com.nuvio.app.features.tvremote.PhoneTvRemoteClient
import com.nuvio.app.features.tvremote.PhoneTvRemoteConnection
import com.nuvio.app.features.tvremote.PhoneTvRemotePairing
import com.nuvio.app.features.tvremote.PhoneTvRemotePairingError
import com.nuvio.app.features.tvremote.TvRemoteControlSheet
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_about_body
import nuvio.composeapp.generated.resources.tv_remote_background_description
import nuvio.composeapp.generated.resources.tv_remote_background_title
import nuvio.composeapp.generated.resources.tv_remote_error_expired
import nuvio.composeapp.generated.resources.tv_remote_error_invalid_code
import nuvio.composeapp.generated.resources.tv_remote_error_rejected
import nuvio.composeapp.generated.resources.tv_remote_error_unknown
import nuvio.composeapp.generated.resources.tv_remote_error_unreachable
import nuvio.composeapp.generated.resources.tv_remote_forget_description
import nuvio.composeapp.generated.resources.tv_remote_manual_address_hint
import nuvio.composeapp.generated.resources.tv_remote_manual_address_invalid
import nuvio.composeapp.generated.resources.tv_remote_manual_address_label
import nuvio.composeapp.generated.resources.tv_remote_manual_address_save
import nuvio.composeapp.generated.resources.tv_remote_forget_title
import nuvio.composeapp.generated.resources.tv_remote_open_controls
import nuvio.composeapp.generated.resources.tv_remote_open_controls_description
import nuvio.composeapp.generated.resources.tv_remote_pairing_in_progress
import nuvio.composeapp.generated.resources.tv_remote_reconnect
import nuvio.composeapp.generated.resources.tv_remote_scan_description
import nuvio.composeapp.generated.resources.tv_remote_scan_other_description
import nuvio.composeapp.generated.resources.tv_remote_scan_other_title
import nuvio.composeapp.generated.resources.tv_remote_scan_title
import nuvio.composeapp.generated.resources.tv_remote_section_about
import nuvio.composeapp.generated.resources.tv_remote_section_tv
import nuvio.composeapp.generated.resources.tv_remote_status_connected
import nuvio.composeapp.generated.resources.tv_remote_status_connecting
import nuvio.composeapp.generated.resources.tv_remote_status_idle
import nuvio.composeapp.generated.resources.tv_remote_status_revoked
import nuvio.composeapp.generated.resources.tv_remote_status_unreachable
import org.jetbrains.compose.resources.stringResource

/** Settings -> TV remote on the phone (Android full): pairing with a Nuvio TV and its status. */
internal fun LazyListScope.tvRemoteSettingsContent(isTablet: Boolean) {
    val client = PhoneTvRemote.activeClient ?: return
    item(key = "tv_remote_tv") {
        SettingsSection(
            title = stringResource(Res.string.tv_remote_section_tv),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                TvRemoteSettingsRows(client = client, isTablet = isTablet)
            }
        }
    }
    item(key = "tv_remote_about") {
        SettingsSection(
            title = stringResource(Res.string.tv_remote_section_about),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                Text(
                    text = stringResource(Res.string.tv_remote_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = if (isTablet) 20.dp else 16.dp,
                        vertical = if (isTablet) 16.dp else 14.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TvRemoteSettingsRows(client: PhoneTvRemoteClient, isTablet: Boolean) {
    val state by client.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(client) {
        val release = client.observe()
        onPauseOrDispose { release() }
    }
    val scan = client.rememberQrScanner { scanned -> scanned?.let(client::pair) }
    var showControls by remember { mutableStateOf(false) }
    val paired = state.pairedTv

    if (paired != null) {
        SettingsNavigationRow(
            title = paired.name,
            description = stringResource(
                when (state.connection) {
                    PhoneTvRemoteConnection.Connected -> Res.string.tv_remote_status_connected
                    PhoneTvRemoteConnection.Connecting -> Res.string.tv_remote_status_connecting
                    PhoneTvRemoteConnection.Unreachable -> Res.string.tv_remote_status_unreachable
                    PhoneTvRemoteConnection.Revoked -> Res.string.tv_remote_status_revoked
                    PhoneTvRemoteConnection.Idle -> Res.string.tv_remote_status_idle
                },
            ),
            icon = Icons.Rounded.Tv,
            isTablet = isTablet,
            onClick = {
                when (state.connection) {
                    PhoneTvRemoteConnection.Revoked -> scan()
                    PhoneTvRemoteConnection.Connected -> showControls = true
                    else -> client.reconnect()
                }
            },
        )
        if (state.connection == PhoneTvRemoteConnection.Connected) {
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.tv_remote_open_controls),
                description = stringResource(Res.string.tv_remote_open_controls_description),
                icon = Icons.Rounded.Cast,
                isTablet = isTablet,
                onClick = { showControls = true },
            )
        } else if (state.connection == PhoneTvRemoteConnection.Unreachable) {
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.tv_remote_reconnect),
                description = null,
                icon = Icons.Rounded.Refresh,
                isTablet = isTablet,
                onClick = client::reconnect,
            )
        }
        SettingsGroupDivider(isTablet = isTablet)
    }

    when (val pairing = state.pairing) {
        PhoneTvRemotePairing.InProgress -> PairingProgressRow(isTablet = isTablet)
        else -> {
            SettingsNavigationRow(
                title = stringResource(
                    if (paired == null) Res.string.tv_remote_scan_title else Res.string.tv_remote_scan_other_title,
                ),
                description = if (pairing is PhoneTvRemotePairing.Failed) {
                    stringResource(pairing.error.messageRes())
                } else {
                    stringResource(
                        if (paired == null) Res.string.tv_remote_scan_description else Res.string.tv_remote_scan_other_description,
                    )
                },
                icon = Icons.Rounded.QrCodeScanner,
                isTablet = isTablet,
                onClick = {
                    client.clearPairingError()
                    scan()
                },
            )
        }
    }

    SettingsGroupDivider(isTablet = isTablet)
    ManualAddressRow(
        saved = state.manualTvAddress,
        isTablet = isTablet,
        onSave = client::setManualTvAddress,
    )

    if (paired != null) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsSwitchRow(
            title = stringResource(Res.string.tv_remote_background_title),
            description = stringResource(Res.string.tv_remote_background_description),
            checked = state.backgroundControls,
            isTablet = isTablet,
            onCheckedChange = client::setBackgroundControls,
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsNavigationRow(
            title = stringResource(Res.string.tv_remote_forget_title),
            description = stringResource(Res.string.tv_remote_forget_description),
            icon = Icons.Rounded.LinkOff,
            isTablet = isTablet,
            onClick = client::forget,
        )
    }

    if (showControls) {
        TvRemoteControlSheet(client = client, onDismiss = { showControls = false })
    }
}

/** TV address typed by hand (saved on Done, on the check button or when the field loses focus). */
@Composable
private fun ManualAddressRow(saved: String, isTablet: Boolean, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val focusManager = LocalFocusManager.current
    val invalid = text.isNotBlank() && parseManualTvAddress(text, 1) == null
    val tokens = MaterialTheme.nuvio
    fun save() {
        if (!invalid && text.trim() != saved) onSave(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp)
            .onFocusChanged { if (!it.isFocused) save() },
        singleLine = true,
        label = { Text(stringResource(Res.string.tv_remote_manual_address_label)) },
        placeholder = { Text("192.168.1.50") },
        isError = invalid,
        supportingText = {
            Text(
                stringResource(
                    if (invalid) Res.string.tv_remote_manual_address_invalid else Res.string.tv_remote_manual_address_hint,
                ),
            )
        },
        trailingIcon = if (!invalid && text.trim() != saved) {
            {
                IconButton(onClick = { save(); focusManager.clearFocus() }) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(Res.string.tv_remote_manual_address_save))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save(); focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
            unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
            focusedContainerColor = tokens.colors.surface,
            unfocusedContainerColor = tokens.colors.surface,
        ),
    )
}

@Composable
private fun PairingProgressRow(isTablet: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 18.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Column {
            Text(
                text = stringResource(Res.string.tv_remote_pairing_in_progress),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
        }
    }
}

private fun PhoneTvRemotePairingError.messageRes() = when (this) {
    PhoneTvRemotePairingError.InvalidCode -> Res.string.tv_remote_error_invalid_code
    PhoneTvRemotePairingError.Expired -> Res.string.tv_remote_error_expired
    PhoneTvRemotePairingError.Unreachable -> Res.string.tv_remote_error_unreachable
    PhoneTvRemotePairingError.Rejected -> Res.string.tv_remote_error_rejected
    PhoneTvRemotePairingError.Unknown -> Res.string.tv_remote_error_unknown
}
