package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.kitsu.KitsuAuthError
import com.nuvio.app.features.kitsu.KitsuAuthRepository
import com.nuvio.app.features.kitsu.KitsuListRepository
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_kitsu_authorization_revoked
import nuvio.composeapp.generated.resources.settings_kitsu_connect
import nuvio.composeapp.generated.resources.settings_kitsu_connected_as
import nuvio.composeapp.generated.resources.settings_kitsu_connected_description
import nuvio.composeapp.generated.resources.settings_kitsu_default_user
import nuvio.composeapp.generated.resources.settings_kitsu_email_label
import nuvio.composeapp.generated.resources.settings_kitsu_invalid_credentials
import nuvio.composeapp.generated.resources.settings_kitsu_password_label
import nuvio.composeapp.generated.resources.settings_kitsu_password_note
import nuvio.composeapp.generated.resources.settings_kitsu_section
import nuvio.composeapp.generated.resources.settings_kitsu_sign_in_description
import nuvio.composeapp.generated.resources.settings_kitsu_sign_in_failed
import nuvio.composeapp.generated.resources.settings_kitsu_sync_login_description
import nuvio.composeapp.generated.resources.settings_kitsu_sync_login_title
import nuvio.composeapp.generated.resources.settings_kitsu_sync_login_warning
import nuvio.composeapp.generated.resources.settings_kitsu_sync_now
import nuvio.composeapp.generated.resources.settings_kitsu_visit
import nuvio.composeapp.generated.resources.settings_mal_disconnect
import nuvio.composeapp.generated.resources.settings_tracking_services
import org.jetbrains.compose.resources.stringResource

/** The Kitsu integration page: account card with the login form, and login sync across devices. */
internal fun LazyListScope.kitsuSettingsContent(isTablet: Boolean) {
    item(key = "kitsu_account") {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_services),
            isTablet = isTablet,
        ) {
            KitsuProviderCard(Modifier.fillMaxWidth())
        }
    }

    item(key = "kitsu_login_sync") {
        SettingsSection(
            title = stringResource(Res.string.settings_kitsu_section),
            isTablet = isTablet,
        ) {
            KitsuLoginSyncSection(isTablet = isTablet)
        }
    }
}

@Composable
private fun KitsuLoginSyncSection(isTablet: Boolean) {
    val uiState by remember {
        KitsuAuthRepository.ensureLoaded()
        KitsuAuthRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsGroup(isTablet = isTablet) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_kitsu_sync_login_title),
            description = stringResource(Res.string.settings_kitsu_sync_login_description),
            checked = uiState.syncLoginAcrossDevices,
            isTablet = isTablet,
            onCheckedChange = KitsuAuthRepository::setSyncLoginAcrossDevices,
        )
    }
}

@Composable
internal fun KitsuProviderCard(modifier: Modifier = Modifier) {
    val uiState by remember {
        KitsuAuthRepository.ensureLoaded()
        KitsuAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState by remember {
        KitsuListRepository.ensureLoaded()
        KitsuListRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val connectLabel = stringResource(Res.string.settings_kitsu_connect)
    TrackingProviderCard(
        brand = TrackingBrand.KITSU,
        mode = if (uiState.isConnected) TrackingConnectionCardMode.CONNECTED else TrackingConnectionCardMode.DISCONNECTED,
        credentialsConfigured = true,
        isLoading = uiState.isLoading,
        connectedLabel = stringResource(
            Res.string.settings_kitsu_connected_as,
            uiState.username ?: stringResource(Res.string.settings_kitsu_default_user),
        ),
        connectedDescription = stringResource(Res.string.settings_kitsu_connected_description),
        signInDescription = stringResource(Res.string.settings_kitsu_sign_in_description),
        finishSignInLabel = connectLabel,
        approvalDescription = "",
        connectLabel = connectLabel,
        openLoginLabel = connectLabel,
        disconnectLabel = stringResource(Res.string.settings_mal_disconnect),
        missingCredentialsMessage = "",
        errorMessage = kitsuErrorMessage(uiState.error) ?: listState.errorMessage,
        // The warning is part of the login section, so it is read before the credentials are typed.
        statusMessage = stringResource(Res.string.settings_kitsu_sync_login_warning)
            .takeIf { uiState.syncLoginAcrossDevices },
        syncLabel = stringResource(Res.string.settings_kitsu_sync_now),
        isSyncing = listState.isLoading,
        onSyncRequested = {
            scope.launch { KitsuListRepository.refresh(TrackingRefreshIntent.USER_INITIATED) }
        },
        websiteLabel = stringResource(Res.string.settings_kitsu_visit),
        websiteUrl = KITSU_WEBSITE_URL,
        onConnectRequested = { null },
        onResumeAuthorization = { null },
        onCancelAuthorization = {},
        disconnectedContent = {
            KitsuLoginForm(
                isLoading = uiState.isLoading,
                connectLabel = connectLabel,
                onSubmit = { username, password ->
                    scope.launch { KitsuAuthRepository.signIn(username, password) }
                },
            )
        },
        onDisconnect = KitsuAuthRepository::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun KitsuLoginForm(
    isLoading: Boolean,
    connectLabel: String,
    onSubmit: (username: String, password: String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // The password is never saved, not even across configuration changes.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val canSubmit = !isLoading && username.isNotBlank() && password.isNotEmpty()
    val submit = {
        if (canSubmit) {
            onSubmit(username, password)
            password = ""
        }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
        cursorColor = Color.White,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.72f),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(Res.string.settings_kitsu_email_label)) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.settings_kitsu_password_label)) },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                    )
                }
            },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.settings_kitsu_password_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
        )
        TrackingBrandPrimaryButton(
            label = connectLabel,
            loading = isLoading,
            enabled = canSubmit,
            onClick = submit,
        )
    }
}

@Composable
private fun kitsuErrorMessage(error: KitsuAuthError?): String? = when (error) {
    null -> null
    KitsuAuthError.INVALID_CREDENTIALS -> stringResource(Res.string.settings_kitsu_invalid_credentials)
    KitsuAuthError.SIGN_IN_FAILED -> stringResource(Res.string.settings_kitsu_sign_in_failed)
    KitsuAuthError.AUTHORIZATION_REVOKED -> stringResource(Res.string.settings_kitsu_authorization_revoked)
}

private const val KITSU_WEBSITE_URL = "https://kitsu.app"
