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
import nuvio.composeapp.generated.resources.settings_telegram_phone_login_title
import nuvio.composeapp.generated.resources.settings_telegram_phone_login_body
import nuvio.composeapp.generated.resources.settings_telegram_phone_login_button
import nuvio.composeapp.generated.resources.settings_telegram_phone_login_waiting
import nuvio.composeapp.generated.resources.settings_telegram_phone_login_needs_pairing
import nuvio.composeapp.generated.resources.settings_telegram_manual_login_title
import nuvio.composeapp.generated.resources.settings_telegram_tv_section
import nuvio.composeapp.generated.resources.settings_telegram_tv_description
import nuvio.composeapp.generated.resources.settings_telegram_tv_unreachable
import nuvio.composeapp.generated.resources.settings_telegram_tv_unavailable
import nuvio.composeapp.generated.resources.settings_telegram_tv_starting
import nuvio.composeapp.generated.resources.settings_telegram_tv_connected
import nuvio.composeapp.generated.resources.settings_telegram_tv_approve_title
import nuvio.composeapp.generated.resources.settings_telegram_tv_approve_body
import nuvio.composeapp.generated.resources.settings_telegram_tv_approve_button
import nuvio.composeapp.generated.resources.settings_telegram_tv_manual_title
import nuvio.composeapp.generated.resources.settings_telegram_tv_qr_waiting
import nuvio.composeapp.generated.resources.settings_telegram_tv_start
import com.nuvio.app.features.tvremote.PhoneTvRemote
import com.nuvio.app.features.tvremote.PhoneTvRemoteClient
import com.nuvio.app.features.tvremote.TvRemotePairedPhones
import com.nuvio.app.features.tvremote.TvRemoteTelegramActionType
import com.nuvio.app.features.tvremote.TvRemoteTelegramState
import com.nuvio.app.features.tvremote.TvRemoteTelegramStep
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // Phone with a paired Nuvio TV: logs in to Telegram on the TV from here.
    if (!TvAppRuntime.isTvApp) {
        item(key = "telegram_tv") {
            val client = PhoneTvRemote.activeClient ?: return@item
            val remote by client.state.collectAsStateWithLifecycle()
            val tv = remote.pairedTv ?: return@item
            SettingsSection(
                title = stringResource(Res.string.settings_telegram_tv_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 18.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvTelegramLogin(client = client, tvName = tv.name)
                    }
                }
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
        val loginInProgress = authState !is TelegramAuthState.Ready &&
            authState != TelegramAuthState.NotConfigured &&
            authState != TelegramAuthState.Unavailable
        if (TvAppRuntime.isTvApp && loginInProgress) {
            // TV: the login from the paired phone comes first, the manual one stays below.
            PhoneLoginStep()
            StepTitle(stringResource(Res.string.settings_telegram_manual_login_title))
        }
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

/** TV: login driven by the paired phone (disabled until a phone is paired). */
@Composable
private fun PhoneLoginStep() {
    val pairedPhones by TvRemotePairedPhones.count.collectAsStateWithLifecycle()
    var started by rememberSaveable { mutableStateOf(false) }
    StepTitle(stringResource(Res.string.settings_telegram_phone_login_title))
    if (pairedPhones == 0) {
        BodyText(stringResource(Res.string.settings_telegram_phone_login_needs_pairing))
        Button(onClick = {}, enabled = false) {
            Text(stringResource(Res.string.settings_telegram_phone_login_button))
        }
        return
    }
    BodyText(stringResource(Res.string.settings_telegram_phone_login_body))
    if (started) {
        StepTitle(stringResource(Res.string.settings_telegram_phone_login_waiting), MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = {
            started = true
            TelegramRepository.startAuth()
        }) {
            Text(stringResource(Res.string.settings_telegram_phone_login_button))
        }
    }
}

/** Phone: Telegram login of the paired TV, through the TV remote connection. */
@Composable
private fun TvTelegramLogin(client: PhoneTvRemoteClient, tvName: String) {
    val phoneAuth by TelegramRepository.authState.collectAsStateWithLifecycle()
    var tvState by remember { mutableStateOf<TvRemoteTelegramState?>(null) }
    var reachable by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    // The user asked to approve: the QR link is confirmed as soon as the TV shows it.
    var approving by remember { mutableStateOf(false) }
    var approveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(client) {
        while (isActive) {
            val state = client.telegramState()
            reachable = state != null
            if (state != null) tvState = state
            delay(if (state == null) 4_000L else 2_000L)
        }
    }
    val qrLink = tvState?.qrLink
    LaunchedEffect(approving, qrLink) {
        if (approving && qrLink != null) {
            approveError = TelegramRepository.confirmQrLogin(qrLink)
            approving = false
            client.telegramState()?.let { tvState = it }
        }
    }
    val send: (TvRemoteTelegramActionType, String?) -> Unit = { action, value ->
        scope.launch {
            busy = true
            val state = client.telegramAction(action, value)
            if (state != null) tvState = state
            reachable = state != null
            busy = false
        }
    }

    BodyText(stringResource(Res.string.settings_telegram_tv_description, tvName))
    if (!reachable) {
        StepTitle(stringResource(Res.string.settings_telegram_tv_unreachable), MaterialTheme.colorScheme.error)
    }
    val state = tvState ?: return
    when (state.step) {
        TvRemoteTelegramStep.Unavailable -> BodyText(stringResource(Res.string.settings_telegram_tv_unavailable))
        TvRemoteTelegramStep.Starting -> {
            BodyText(stringResource(Res.string.settings_telegram_tv_starting))
            Button(onClick = { send(TvRemoteTelegramActionType.Start, null) }, enabled = !busy) {
                Text(stringResource(Res.string.settings_telegram_tv_start))
            }
        }
        TvRemoteTelegramStep.Ready -> StepTitle(
            stringResource(Res.string.settings_telegram_tv_connected, state.accountName.orEmpty()),
            MaterialTheme.colorScheme.primary,
        )
        TvRemoteTelegramStep.WaitPhone, TvRemoteTelegramStep.WaitQr -> {
            val account = phoneAuth as? TelegramAuthState.Ready
            if (account != null) {
                StepTitle(stringResource(Res.string.settings_telegram_tv_approve_title))
                BodyText(
                    stringResource(
                        Res.string.settings_telegram_tv_approve_body,
                        account.firstName.ifBlank { account.userId.toString() },
                    ),
                )
                Button(
                    onClick = {
                        approving = true
                        approveError = null
                        if (state.step != TvRemoteTelegramStep.WaitQr) send(TvRemoteTelegramActionType.RequestQr, null)
                    },
                    enabled = !approving && !busy,
                ) {
                    if (approving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.settings_telegram_tv_approve_button))
                    }
                }
                approveError?.let { BodyText(it) }
            } else if (state.step == TvRemoteTelegramStep.WaitQr && qrLink != null) {
                BodyText(stringResource(Res.string.settings_telegram_tv_qr_waiting, qrLink))
            }
            StepTitle(stringResource(Res.string.settings_telegram_tv_manual_title))
            PhoneNumberField(busy = busy) { phone -> send(TvRemoteTelegramActionType.SubmitPhone, phone) }
        }
        TvRemoteTelegramStep.WaitCode -> SecretStep(
            title = stringResource(Res.string.settings_telegram_code_card_title),
            instruction = stringResource(Res.string.settings_telegram_code_instruction, state.codeLength ?: 5),
            label = stringResource(Res.string.settings_telegram_code_label),
            action = stringResource(Res.string.settings_telegram_verify_code),
            keyboardType = KeyboardType.Number,
            isPassword = false,
            onSubmit = { code -> send(TvRemoteTelegramActionType.SubmitCode, code) },
        )
        TvRemoteTelegramStep.WaitPassword -> SecretStep(
            title = stringResource(Res.string.settings_telegram_2fa_card_title),
            instruction = stringResource(Res.string.settings_telegram_2fa_instruction),
            label = stringResource(Res.string.settings_telegram_2fa_label),
            action = stringResource(Res.string.settings_telegram_submit_password),
            keyboardType = KeyboardType.Password,
            isPassword = true,
            onSubmit = { password -> send(TvRemoteTelegramActionType.SubmitPassword, password) },
        )
        TvRemoteTelegramStep.Error -> {
            StepTitle(stringResource(Res.string.settings_telegram_error_title), MaterialTheme.colorScheme.error)
            BodyText(state.error.orEmpty())
            Button(onClick = { send(TvRemoteTelegramActionType.Retry, null) }, enabled = !busy) {
                Text(stringResource(Res.string.settings_telegram_retry))
            }
        }
    }
}

@Composable
private fun PhoneNumberField(busy: Boolean, onSubmit: (String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("+") }
    val submit = { if (phone.length > 1 && !busy) onSubmit(phone) }
    BodyText(stringResource(Res.string.settings_telegram_phone_instruction))
    OutlinedTextField(
        value = phone,
        onValueChange = { value -> phone = "+" + value.filter(Char::isDigit) },
        label = { Text(stringResource(Res.string.settings_telegram_phone_label)) },
        placeholder = { Text(stringResource(Res.string.settings_telegram_phone_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { submit() }, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.settings_telegram_send_code))
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
