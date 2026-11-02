package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.wetrakr.WETRAKR_WEBSITE_URL
import com.nuvio.app.features.wetrakr.WeTrakrAuthError
import com.nuvio.app.features.wetrakr.WeTrakrAuthRepository
import com.nuvio.app.features.wetrakr.WeTrakrListRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_mal_disconnect
import nuvio.composeapp.generated.resources.settings_tracking_services
import nuvio.composeapp.generated.resources.settings_wetrakr_access_denied
import nuvio.composeapp.generated.resources.settings_wetrakr_approval_description
import nuvio.composeapp.generated.resources.settings_wetrakr_authorization_revoked
import nuvio.composeapp.generated.resources.settings_wetrakr_code_expired
import nuvio.composeapp.generated.resources.settings_wetrakr_connect
import nuvio.composeapp.generated.resources.settings_wetrakr_connected_as
import nuvio.composeapp.generated.resources.settings_wetrakr_connected_description
import nuvio.composeapp.generated.resources.settings_wetrakr_default_user
import nuvio.composeapp.generated.resources.settings_wetrakr_finish_sign_in
import nuvio.composeapp.generated.resources.settings_wetrakr_missing_client_id
import nuvio.composeapp.generated.resources.settings_wetrakr_open_activation
import nuvio.composeapp.generated.resources.settings_wetrakr_section
import nuvio.composeapp.generated.resources.settings_wetrakr_sign_in_description
import nuvio.composeapp.generated.resources.settings_wetrakr_sign_in_failed
import nuvio.composeapp.generated.resources.settings_wetrakr_sync_login_description
import nuvio.composeapp.generated.resources.settings_wetrakr_sync_login_title
import nuvio.composeapp.generated.resources.settings_wetrakr_sync_login_warning
import nuvio.composeapp.generated.resources.settings_wetrakr_sync_now
import nuvio.composeapp.generated.resources.settings_wetrakr_too_many_requests
import nuvio.composeapp.generated.resources.settings_wetrakr_visit
import org.jetbrains.compose.resources.stringResource

/** The WeTrakr integration page: account card with the device code login, and login sync across devices. */
internal fun LazyListScope.weTrakrSettingsContent(isTablet: Boolean) {
    item(key = "wetrakr_account") {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_services),
            isTablet = isTablet,
        ) {
            WeTrakrProviderCard(Modifier.fillMaxWidth())
        }
    }

    item(key = "wetrakr_login_sync") {
        SettingsSection(
            title = stringResource(Res.string.settings_wetrakr_section),
            isTablet = isTablet,
        ) {
            WeTrakrLoginSyncSection(isTablet = isTablet)
        }
    }
}

@Composable
private fun WeTrakrLoginSyncSection(isTablet: Boolean) {
    val uiState by remember {
        WeTrakrAuthRepository.ensureLoaded()
        WeTrakrAuthRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsGroup(isTablet = isTablet) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_wetrakr_sync_login_title),
            description = stringResource(Res.string.settings_wetrakr_sync_login_description),
            checked = uiState.syncLoginAcrossDevices,
            isTablet = isTablet,
            onCheckedChange = WeTrakrAuthRepository::setSyncLoginAcrossDevices,
        )
    }
}

@Composable
internal fun WeTrakrProviderCard(modifier: Modifier = Modifier) {
    val uiState by remember {
        WeTrakrAuthRepository.ensureLoaded()
        WeTrakrAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState by remember {
        WeTrakrListRepository.ensureLoaded()
        WeTrakrListRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pending = uiState.pending
    TrackingProviderCard(
        brand = TrackingBrand.WETRAKR,
        mode = when {
            uiState.isConnected -> TrackingConnectionCardMode.CONNECTED
            pending != null -> TrackingConnectionCardMode.AWAITING_APPROVAL
            else -> TrackingConnectionCardMode.DISCONNECTED
        },
        credentialsConfigured = WeTrakrAuthRepository.isClientConfigured,
        isLoading = uiState.isLoading,
        connectedLabel = stringResource(
            Res.string.settings_wetrakr_connected_as,
            uiState.username ?: stringResource(Res.string.settings_wetrakr_default_user),
        ),
        connectedDescription = stringResource(Res.string.settings_wetrakr_connected_description),
        signInDescription = stringResource(Res.string.settings_wetrakr_sign_in_description),
        finishSignInLabel = stringResource(Res.string.settings_wetrakr_finish_sign_in),
        approvalDescription = stringResource(
            Res.string.settings_wetrakr_approval_description,
            pending?.verificationUrl.orEmpty(),
        ),
        authorizationCode = pending?.userCode,
        connectLabel = stringResource(Res.string.settings_wetrakr_connect),
        openLoginLabel = stringResource(Res.string.settings_wetrakr_open_activation),
        disconnectLabel = stringResource(Res.string.settings_mal_disconnect),
        missingCredentialsMessage = stringResource(Res.string.settings_wetrakr_missing_client_id),
        errorMessage = weTrakrErrorMessage(uiState.error) ?: listState.errorMessage,
        // The warning is part of the login section, so it is read before signing in.
        statusMessage = stringResource(Res.string.settings_wetrakr_sync_login_warning)
            .takeIf { uiState.syncLoginAcrossDevices },
        syncLabel = stringResource(Res.string.settings_wetrakr_sync_now),
        isSyncing = listState.isLoading,
        onSyncRequested = {
            scope.launch { WeTrakrListRepository.refresh(TrackingRefreshIntent.USER_INITIATED) }
        },
        websiteLabel = stringResource(Res.string.settings_wetrakr_visit),
        websiteUrl = WETRAKR_WEBSITE_URL,
        onConnectRequested = { WeTrakrAuthRepository.requestAuthorizationUrl() },
        onResumeAuthorization = { WeTrakrAuthRepository.pendingAuthorizationUrl() },
        onCancelAuthorization = WeTrakrAuthRepository::cancelAuthorization,
        onDisconnect = WeTrakrAuthRepository::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun weTrakrErrorMessage(error: WeTrakrAuthError?): String? = when (error) {
    null -> null
    WeTrakrAuthError.MISSING_CLIENT_ID -> stringResource(Res.string.settings_wetrakr_missing_client_id)
    WeTrakrAuthError.SIGN_IN_FAILED -> stringResource(Res.string.settings_wetrakr_sign_in_failed)
    WeTrakrAuthError.CODE_EXPIRED -> stringResource(Res.string.settings_wetrakr_code_expired)
    WeTrakrAuthError.ACCESS_DENIED -> stringResource(Res.string.settings_wetrakr_access_denied)
    WeTrakrAuthError.TOO_MANY_REQUESTS -> stringResource(Res.string.settings_wetrakr_too_many_requests)
    WeTrakrAuthError.AUTHORIZATION_REVOKED -> stringResource(Res.string.settings_wetrakr_authorization_revoked)
}
