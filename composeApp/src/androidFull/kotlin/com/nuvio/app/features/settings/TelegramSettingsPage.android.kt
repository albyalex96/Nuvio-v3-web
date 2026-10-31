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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.telegram.TelegramAuthState
import com.nuvio.app.features.telegram.TelegramRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_telegram_2fa_card_title
import nuvio.composeapp.generated.resources.settings_telegram_2fa_instruction
import nuvio.composeapp.generated.resources.settings_telegram_2fa_label
import nuvio.composeapp.generated.resources.settings_telegram_clear_cache
import nuvio.composeapp.generated.resources.settings_telegram_code_card_title
import nuvio.composeapp.generated.resources.settings_telegram_code_instruction
import nuvio.composeapp.generated.resources.settings_telegram_code_label
import nuvio.composeapp.generated.resources.settings_telegram_connected_title
import nuvio.composeapp.generated.resources.settings_telegram_description
import nuvio.composeapp.generated.resources.settings_telegram_disconnect
import nuvio.composeapp.generated.resources.settings_telegram_error_title
import nuvio.composeapp.generated.resources.settings_telegram_initializing
import nuvio.composeapp.generated.resources.settings_telegram_logged_in_as
import nuvio.composeapp.generated.resources.settings_telegram_media_cache
import nuvio.composeapp.generated.resources.settings_telegram_not_configured
import nuvio.composeapp.generated.resources.settings_telegram_phone_card_title
import nuvio.composeapp.generated.resources.settings_telegram_phone_instruction
import nuvio.composeapp.generated.resources.settings_telegram_phone_label
import nuvio.composeapp.generated.resources.settings_telegram_phone_placeholder
import nuvio.composeapp.generated.resources.settings_telegram_qr_scan
import nuvio.composeapp.generated.resources.settings_telegram_retry
import nuvio.composeapp.generated.resources.settings_telegram_section_account
import nuvio.composeapp.generated.resources.settings_telegram_send_code
import nuvio.composeapp.generated.resources.settings_telegram_submit_password
import nuvio.composeapp.generated.resources.settings_telegram_unavailable
import nuvio.composeapp.generated.resources.settings_telegram_verify_code
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/** Telegram page (Android full): phone login with code and 2FA, then account info and media cache. */
internal actual fun LazyListScope.telegramSettingsContent(isTablet: Boolean) {
    item(key = "telegram_account") {
        SettingsSection(
            title = stringResource(Res.string.settings_telegram_section_account),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                TelegramAccountContent(isTablet = isTablet)
            }
        }
    }
}

@Composable
private fun TelegramAccountContent(isTablet: Boolean) {
    val authState by TelegramRepository.authState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { TelegramRepository.startAuth() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BodyText(stringResource(Res.string.settings_telegram_description))
        when (val state = authState) {
            TelegramAuthState.Idle, TelegramAuthState.WaitPhone -> PhoneStep()
            TelegramAuthState.Initializing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.settings_telegram_initializing),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is TelegramAuthState.WaitCode -> SecretStep(
                title = stringResource(Res.string.settings_telegram_code_card_title),
                instruction = stringResource(Res.string.settings_telegram_code_instruction, state.codeLength),
                label = stringResource(Res.string.settings_telegram_code_label),
                action = stringResource(Res.string.settings_telegram_verify_code),
                keyboardType = KeyboardType.Number,
                isPassword = false,
                onSubmit = TelegramRepository::submitCode,
            )
            TelegramAuthState.WaitPassword -> SecretStep(
                title = stringResource(Res.string.settings_telegram_2fa_card_title),
                instruction = stringResource(Res.string.settings_telegram_2fa_instruction),
                label = stringResource(Res.string.settings_telegram_2fa_label),
                action = stringResource(Res.string.settings_telegram_submit_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                onSubmit = TelegramRepository::submitPassword,
            )
            is TelegramAuthState.WaitQr -> BodyText(stringResource(Res.string.settings_telegram_qr_scan, state.link))
            is TelegramAuthState.Ready -> ConnectedStep(state)
            TelegramAuthState.NotConfigured -> BodyText(stringResource(Res.string.settings_telegram_not_configured))
            TelegramAuthState.Unavailable -> BodyText(stringResource(Res.string.settings_telegram_unavailable))
            is TelegramAuthState.Error -> {
                StepTitle(stringResource(Res.string.settings_telegram_error_title), MaterialTheme.colorScheme.error)
                BodyText(state.message)
                Button(onClick = TelegramRepository::retry) {
                    Text(stringResource(Res.string.settings_telegram_retry))
                }
            }
        }
    }
}

@Composable
private fun PhoneStep() {
    var phone by rememberSaveable { mutableStateOf("+") }
    var waitingForClient by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val submit = {
        if (phone.length > 1 && !waitingForClient) {
            if (TelegramRepository.isClientReady()) {
                TelegramRepository.submitPhone(phone)
            } else {
                waitingForClient = true
                TelegramRepository.startAuth()
                scope.launch {
                    TelegramRepository.awaitClient()
                    TelegramRepository.submitPhone(phone)
                    waitingForClient = false
                }
            }
        }
    }

    StepTitle(stringResource(Res.string.settings_telegram_phone_card_title))
    BodyText(stringResource(Res.string.settings_telegram_phone_instruction))
    OutlinedTextField(
        value = phone,
        onValueChange = { value ->
            val digits = value.filter(Char::isDigit)
            phone = "+$digits"
        },
        label = { Text(stringResource(Res.string.settings_telegram_phone_label)) },
        placeholder = { Text(stringResource(Res.string.settings_telegram_phone_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { submit() }, enabled = !waitingForClient) {
        if (waitingForClient) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.settings_telegram_send_code))
        }
    }
}

@Composable
private fun SecretStep(
    title: String,
    instruction: String,
    label: String,
    action: String,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    onSubmit: (String) -> Unit,
) {
    var value by remember(title) { mutableStateOf("") }
    val submit = { if (value.isNotBlank()) onSubmit(value.trim()) }
    StepTitle(title)
    BodyText(instruction)
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { submit() }) { Text(action) }
}

@Composable
private fun ConnectedStep(state: TelegramAuthState.Ready) {
    var cacheSize by remember { mutableLongStateOf(TelegramRepository.getCacheSize()) }
    StepTitle(stringResource(Res.string.settings_telegram_connected_title))
    BodyText(stringResource(Res.string.settings_telegram_logged_in_as, state.firstName.ifBlank { state.userId.toString() }))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyText(stringResource(Res.string.settings_telegram_media_cache, formatBytes(cacheSize)))
        OutlinedButton(onClick = {
            TelegramRepository.clearCache()
            cacheSize = TelegramRepository.getCacheSize()
        }) {
            Text(stringResource(Res.string.settings_telegram_clear_cache))
        }
    }
    Button(
        onClick = {
            TelegramRepository.disconnect()
            cacheSize = 0L
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(stringResource(Res.string.settings_telegram_disconnect))
    }
}

@Composable
private fun StepTitle(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun BodyText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
}
