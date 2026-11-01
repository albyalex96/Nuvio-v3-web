package com.nuvio.tv.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.TvSettingsPage
import com.nuvio.app.tv.TvSettingsSearchItem
import com.nuvio.tv.R
import com.nuvio.tv.settings.TvSettingsActionRow
import com.nuvio.tv.settings.TvSettingsNote
import com.nuvio.tv.ui.TvQrCode
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/** Settings -> Phone remote: pairing QR and paired phones. */
val TvPhoneRemoteSettingsPage = TvSettingsPage(
    content = { _ -> phoneRemoteSettingsContent() },
    searchEntries = { phoneRemoteSearchEntries() },
)

private val QrShape = RoundedCornerShape(20.dp)

private fun LazyListScope.phoneRemoteSettingsContent() {
    item(key = "tv-phone-remote-pairing") { PairingSection() }
    item(key = "tv-phone-remote-phones-title") { PhonesHeader() }
    item(key = "tv-phone-remote-phones") { PhonesSection() }
    item(key = "tv-phone-remote-note") {
        TvSettingsNote(stringResource(R.string.tv_phone_remote_security_note))
    }
}

@Composable
private fun PairingSection() {
    val state by TvPhoneRemote.state.collectAsState()
    // The QR opens with the page when no phone is paired yet; it closes when the page is left.
    DisposableEffect(Unit) {
        if (TvPhoneRemote.state.value.phones.isEmpty()) TvPhoneRemote.startPairing()
        onDispose { TvPhoneRemote.stopPairing() }
    }
    val pairing = state.pairing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.justPairedPhone?.let { name ->
            TvSettingsNote(stringResource(R.string.tv_phone_remote_paired_message, name))
        }
        if (pairing != null) {
            var remainingSeconds by remember(state.pairingExpiresAtMs) { mutableLongStateOf(0L) }
            LaunchedEffect(state.pairingExpiresAtMs) {
                while (true) {
                    remainingSeconds = ((state.pairingExpiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                    delay(1_000L)
                }
            }
            // Focusable so the D-pad can reach it: the list then scrolls the whole QR into view
            // (otherwise only the rows below it take focus and its top stays cut off).
            var qrFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(QrShape)
                    .border(
                        width = 2.dp,
                        color = if (qrFocused) MaterialTheme.nuvio.colors.textPrimary else Color.Transparent,
                        shape = QrShape,
                    )
                    .onFocusChanged { qrFocused = it.isFocused }
                    .focusable()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(220.dp).clip(QrShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    TvQrCode(
                        content = pairing.toUri(),
                        contentDescription = stringResource(R.string.tv_phone_remote_qr_description),
                        modifier = Modifier.size(204.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tv_phone_remote_scan_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.nuvio.colors.textPrimary,
                    )
                    listOf(
                        R.string.tv_phone_remote_step_1,
                        R.string.tv_phone_remote_step_2,
                        R.string.tv_phone_remote_step_3,
                    ).forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. ${stringResource(step)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textSecondary,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.tv_phone_remote_code_expires,
                            "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                    Text(
                        text = "${pairing.host}:${pairing.port}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                }
            }
            TvSettingsActionRow(
                title = stringResource(R.string.tv_phone_remote_new_code),
                subtitle = null,
                leadingIcon = Icons.Filled.Refresh,
                onClick = TvPhoneRemote::startPairing,
            )
        } else {
            if (state.pairingError) {
                TvSettingsNote(stringResource(R.string.tv_phone_remote_pairing_error))
            }
            TvSettingsActionRow(
                title = stringResource(
                    if (state.phones.isEmpty()) R.string.tv_phone_remote_show_code else R.string.tv_phone_remote_pair_another,
                ),
                subtitle = stringResource(R.string.tv_phone_remote_show_code_sub),
                leadingIcon = Icons.Filled.QrCode2,
                onClick = TvPhoneRemote::startPairing,
            )
        }
    }
}

@Composable
private fun PhonesHeader() {
    Text(
        text = stringResource(R.string.tv_phone_remote_phones_title),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.nuvio.colors.textSecondary,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PhonesSection() {
    val state by TvPhoneRemote.state.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            TvPhoneRemote.refreshConnections()
            delay(5_000L)
        }
    }
    if (state.phones.isEmpty()) {
        TvSettingsNote(stringResource(R.string.tv_phone_remote_no_phones))
        return
    }
    var confirmingId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.phones.sortedByDescending { it.pairedAtMs }.forEach { phone ->
            val connected = phone.clientId in state.connectedPhoneIds
            val pairedOn = remember(phone.pairedAtMs) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(phone.pairedAtMs))
            }
            val confirming = confirmingId == phone.clientId
            TvSettingsActionRow(
                title = phone.name,
                subtitle = if (connected) {
                    stringResource(R.string.tv_phone_remote_phone_connected)
                } else {
                    stringResource(R.string.tv_phone_remote_phone_paired_on, pairedOn)
                },
                value = if (confirming) stringResource(R.string.tv_phone_remote_forget_confirm) else null,
                leadingIcon = Icons.Filled.PhoneAndroid,
                trailingIcon = Icons.Filled.Delete,
                onClick = {
                    if (confirming) {
                        confirmingId = null
                        TvPhoneRemote.forgetPhone(phone.clientId)
                    } else {
                        confirmingId = phone.clientId
                    }
                },
            )
        }
    }
}

@Composable
private fun phoneRemoteSearchEntries(): List<TvSettingsSearchItem> {
    val section = stringResource(R.string.tv_phone_remote_title)
    return listOf(
        TvSettingsSearchItem(
            key = "tv_phone_remote_show_code",
            title = stringResource(R.string.tv_phone_remote_show_code),
            description = stringResource(R.string.tv_phone_remote_show_code_sub),
            section = section,
        ),
        TvSettingsSearchItem(
            key = "tv_phone_remote_phones",
            title = stringResource(R.string.tv_phone_remote_phones_title),
            description = stringResource(R.string.tv_phone_remote_security_note),
            section = section,
        ),
    )
}
