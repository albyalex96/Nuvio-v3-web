package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.PhoneTvRemote
import com.nuvio.app.features.tvremote.PhoneTvRemoteClient
import com.nuvio.app.features.tvremote.PhoneTvRemoteConnection
import com.nuvio.app.features.tvremote.PhoneTvRemoteDiscoveredTv
import com.nuvio.app.features.tvremote.PhoneTvRemotePairing
import com.nuvio.app.features.tvremote.PhoneTvRemotePairingError
import com.nuvio.app.features.tvremote.TV_REMOTE_DEFAULT_PORT
import com.nuvio.app.features.tvremote.TV_REMOTE_PAIRING_CODE_ALPHABET
import com.nuvio.app.features.tvremote.TV_REMOTE_PAIRING_CODE_LENGTH
import com.nuvio.app.features.tvremote.TvRemoteControlSheet
import com.nuvio.app.features.tvremote.normalizeTvRemotePairingCode
import com.nuvio.app.features.tvremote.parseManualTvAddress
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_about_body
import nuvio.composeapp.generated.resources.tv_remote_background_description
import nuvio.composeapp.generated.resources.tv_remote_background_title
import nuvio.composeapp.generated.resources.tv_remote_cancel
import nuvio.composeapp.generated.resources.tv_remote_code_dialog_body
import nuvio.composeapp.generated.resources.tv_remote_code_dialog_title
import nuvio.composeapp.generated.resources.tv_remote_code_invalid
import nuvio.composeapp.generated.resources.tv_remote_code_label
import nuvio.composeapp.generated.resources.tv_remote_discovered_current
import nuvio.composeapp.generated.resources.tv_remote_discovered_description
import nuvio.composeapp.generated.resources.tv_remote_discovering_description
import nuvio.composeapp.generated.resources.tv_remote_discovering_title
import nuvio.composeapp.generated.resources.tv_remote_discovery_refresh
import nuvio.composeapp.generated.resources.tv_remote_error_expired
import nuvio.composeapp.generated.resources.tv_remote_error_invalid_code
import nuvio.composeapp.generated.resources.tv_remote_error_rejected
import nuvio.composeapp.generated.resources.tv_remote_error_unknown
import nuvio.composeapp.generated.resources.tv_remote_error_unreachable
import nuvio.composeapp.generated.resources.tv_remote_error_wrong_code
import nuvio.composeapp.generated.resources.tv_remote_forget_description
import nuvio.composeapp.generated.resources.tv_remote_forget_title
import nuvio.composeapp.generated.resources.tv_remote_manual_address_hint
import nuvio.composeapp.generated.resources.tv_remote_manual_address_invalid
import nuvio.composeapp.generated.resources.tv_remote_manual_address_label
import nuvio.composeapp.generated.resources.tv_remote_manual_description
import nuvio.composeapp.generated.resources.tv_remote_manual_title
import nuvio.composeapp.generated.resources.tv_remote_open_controls
import nuvio.composeapp.generated.resources.tv_remote_open_controls_description
import nuvio.composeapp.generated.resources.tv_remote_pair_action
import nuvio.composeapp.generated.resources.tv_remote_pairing_in_progress
import nuvio.composeapp.generated.resources.tv_remote_reconnect
import nuvio.composeapp.generated.resources.tv_remote_scan_description
import nuvio.composeapp.generated.resources.tv_remote_scan_other_description
import nuvio.composeapp.generated.resources.tv_remote_scan_title
import nuvio.composeapp.generated.resources.tv_remote_section_about
import nuvio.composeapp.generated.resources.tv_remote_section_pair
import nuvio.composeapp.generated.resources.tv_remote_section_pair_other
import nuvio.composeapp.generated.resources.tv_remote_section_tv
import nuvio.composeapp.generated.resources.tv_remote_status_connected
import nuvio.composeapp.generated.resources.tv_remote_status_connecting
import nuvio.composeapp.generated.resources.tv_remote_status_idle
import nuvio.composeapp.generated.resources.tv_remote_status_revoked
import nuvio.composeapp.generated.resources.tv_remote_status_unreachable
import org.jetbrains.compose.resources.stringResource

/**
 * Settings -> TV remote on the phone (Android full): paired TV and its status, TVs found on the
 * network, QR scanner and pairing with address + code.
 */
internal fun LazyListScope.tvRemoteSettingsContent(isTablet: Boolean) {
    val client = PhoneTvRemote.activeClient ?: return
    item(key = "tv_remote_content") {
        TvRemoteSettingsContent(client = client, isTablet = isTablet)
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

/** Address prefilled in the code dialog: from a discovered TV or the last typed one. */
private data class CodeDialogRequest(val address: String, val tvName: String?)

@Composable
private fun TvRemoteSettingsContent(client: PhoneTvRemoteClient, isTablet: Boolean) {
    val state by client.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(client) {
        val releaseObserve = client.observe()
        val releaseDiscovery = client.discover()
        onPauseOrDispose {
            releaseObserve()
            releaseDiscovery()
        }
    }
    var showScanner by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var codeDialog by remember { mutableStateOf<CodeDialogRequest?>(null) }
    // After a successful pairing the control panel opens by itself once the TV answers.
    var openControlsWhenConnected by remember { mutableStateOf(false) }
    val paired = state.pairedTv
    val pairing = state.pairing

    LaunchedEffect(pairing, state.connection, paired?.tvId) {
        if (pairing is PhoneTvRemotePairing.InProgress) {
            openControlsWhenConnected = true
            codeDialog = null
        }
        if (openControlsWhenConnected && paired != null && state.connection == PhoneTvRemoteConnection.Connected) {
            openControlsWhenConnected = false
            showControls = true
        }
        if (pairing is PhoneTvRemotePairing.Failed) openControlsWhenConnected = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 24.dp else 20.dp)) {
        if (paired != null) {
            SettingsSection(title = stringResource(Res.string.tv_remote_section_tv), isTablet = isTablet) {
                SettingsGroup(isTablet = isTablet) {
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
                                PhoneTvRemoteConnection.Revoked -> showScanner = true
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
            }
        }

        SettingsSection(
            title = stringResource(if (paired == null) Res.string.tv_remote_section_pair else Res.string.tv_remote_section_pair_other),
            isTablet = isTablet,
            actions = {
                IconButton(onClick = client::refreshDiscovery) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(Res.string.tv_remote_discovery_refresh),
                        tint = MaterialTheme.nuvio.colors.textSecondary,
                    )
                }
            },
        ) {
            SettingsGroup(isTablet = isTablet) {
                when (pairing) {
                    PhoneTvRemotePairing.InProgress -> {
                        ProgressRow(title = stringResource(Res.string.tv_remote_pairing_in_progress), description = null, isTablet = isTablet)
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    is PhoneTvRemotePairing.Failed -> {
                        ErrorRow(text = stringResource(pairing.error.messageRes()), isTablet = isTablet)
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    PhoneTvRemotePairing.Idle -> Unit
                }

                // TVs found on the network: a tap asks for the code shown on that TV.
                state.discoveredTvs.forEach { tv ->
                    val isPaired = tv.tvId == paired?.tvId
                    SettingsNavigationRow(
                        title = tv.name,
                        description = stringResource(
                            if (isPaired) Res.string.tv_remote_discovered_current else Res.string.tv_remote_discovered_description,
                            tv.address,
                        ),
                        icon = Icons.Rounded.Tv,
                        enabled = pairing !is PhoneTvRemotePairing.InProgress,
                        isTablet = isTablet,
                        onClick = {
                            if (isPaired && state.connection == PhoneTvRemoteConnection.Connected) {
                                showControls = true
                            } else {
                                client.clearPairingError()
                                codeDialog = CodeDialogRequest(address = tv.dialogAddress(), tvName = tv.name)
                            }
                        },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                }
                if (state.discoveredTvs.isEmpty() && state.discovering) {
                    ProgressRow(
                        title = stringResource(Res.string.tv_remote_discovering_title),
                        description = stringResource(Res.string.tv_remote_discovering_description),
                        isTablet = isTablet,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                }

                SettingsNavigationRow(
                    title = stringResource(Res.string.tv_remote_scan_title),
                    description = stringResource(
                        if (paired == null) Res.string.tv_remote_scan_description else Res.string.tv_remote_scan_other_description,
                    ),
                    icon = Icons.Rounded.QrCodeScanner,
                    enabled = pairing !is PhoneTvRemotePairing.InProgress,
                    isTablet = isTablet,
                    onClick = {
                        client.clearPairingError()
                        showScanner = true
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.tv_remote_manual_title),
                    description = stringResource(Res.string.tv_remote_manual_description),
                    icon = Icons.Rounded.Keyboard,
                    enabled = pairing !is PhoneTvRemotePairing.InProgress,
                    isTablet = isTablet,
                    onClick = {
                        client.clearPairingError()
                        codeDialog = CodeDialogRequest(address = state.manualTvAddress, tvName = null)
                    },
                )
            }
        }
    }

    if (showScanner) {
        client.QrScanner { scanned ->
            showScanner = false
            scanned?.let(client::pair)
        }
    }
    codeDialog?.let { request ->
        PairWithCodeDialog(
            request = request,
            onDismiss = { codeDialog = null },
            onPair = { address, code ->
                codeDialog = null
                client.pairWithCode(address, code)
            },
        )
    }
    if (showControls) {
        TvRemoteControlSheet(client = client, onDismiss = { showControls = false })
    }
}

private fun PhoneTvRemoteDiscoveredTv.dialogAddress(): String =
    if (port == TV_REMOTE_DEFAULT_PORT && ':' !in host) host else address

@Composable
private fun PairWithCodeDialog(
    request: CodeDialogRequest,
    onDismiss: () -> Unit,
    onPair: (address: String, code: String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var address by remember(request) { mutableStateOf(request.address) }
    var code by remember(request) { mutableStateOf("") }
    val addressInvalid = address.isNotBlank() && parseManualTvAddress(address, TV_REMOTE_DEFAULT_PORT) == null
    val codeComplete = normalizeTvRemotePairingCode(code) != null
    val canPair = address.isNotBlank() && !addressInvalid && codeComplete
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
        unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
    )
    DialogSurface(
        onDismissRequest = onDismiss,
        title = request.tvName ?: stringResource(Res.string.tv_remote_code_dialog_title),
        message = stringResource(Res.string.tv_remote_code_dialog_body),
    ) {
        if (request.tvName == null) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(Res.string.tv_remote_manual_address_label)) },
                placeholder = { Text("192.168.1.50") },
                isError = addressInvalid,
                supportingText = {
                    Text(
                        stringResource(
                            if (addressInvalid) Res.string.tv_remote_manual_address_invalid else Res.string.tv_remote_manual_address_hint,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = fieldColors,
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { typed ->
                // Keeps only the characters the TV can show, formatted as on the TV ("ABCD-EFGH").
                val clean = typed.uppercase()
                    .filter { it in TV_REMOTE_PAIRING_CODE_ALPHABET }
                    .take(TV_REMOTE_PAIRING_CODE_LENGTH)
                code = if (clean.length > 4) "${clean.take(4)}-${clean.drop(4)}" else clean
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(Res.string.tv_remote_code_label)) },
            placeholder = { Text("ABCD-EFGH") },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
            isError = code.length == TV_REMOTE_PAIRING_CODE_LENGTH + 1 && !codeComplete,
            supportingText = { Text(stringResource(Res.string.tv_remote_code_invalid)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canPair) onPair(address, code) }),
            colors = fieldColors,
        )
        DialogButtons {
            DialogButton(text = stringResource(Res.string.tv_remote_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.tv_remote_pair_action),
                onClick = { onPair(address, code) },
                style = DialogButtonStyle.Primary,
                enabled = canPair,
            )
        }
    }
}

@Composable
private fun ProgressRow(title: String, description: String?, isTablet: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 18.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ErrorRow(text: String, isTablet: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.nuvio.colors.danger,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.nuvio.colors.textPrimary,
        )
    }
}

private fun PhoneTvRemotePairingError.messageRes() = when (this) {
    PhoneTvRemotePairingError.InvalidCode -> Res.string.tv_remote_error_invalid_code
    PhoneTvRemotePairingError.Expired -> Res.string.tv_remote_error_expired
    PhoneTvRemotePairingError.Unreachable -> Res.string.tv_remote_error_unreachable
    PhoneTvRemotePairingError.Rejected -> Res.string.tv_remote_error_rejected
    PhoneTvRemotePairingError.WrongCode -> Res.string.tv_remote_error_wrong_code
    PhoneTvRemotePairingError.Unknown -> Res.string.tv_remote_error_unknown
}
