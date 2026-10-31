package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.anilist.AniListAuthError
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.anilist.AniListListRepository
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_anilist_authorization_denied
import nuvio.composeapp.generated.resources.settings_anilist_authorization_expired
import nuvio.composeapp.generated.resources.settings_anilist_authorization_revoked
import nuvio.composeapp.generated.resources.settings_anilist_connect
import nuvio.composeapp.generated.resources.settings_anilist_connected_as
import nuvio.composeapp.generated.resources.settings_anilist_connected_description
import nuvio.composeapp.generated.resources.settings_anilist_default_user
import nuvio.composeapp.generated.resources.settings_anilist_finish_sign_in
import nuvio.composeapp.generated.resources.settings_anilist_invalid_callback
import nuvio.composeapp.generated.resources.settings_anilist_missing_credentials
import nuvio.composeapp.generated.resources.settings_anilist_open_login
import nuvio.composeapp.generated.resources.settings_anilist_section
import nuvio.composeapp.generated.resources.settings_anilist_sign_in_description
import nuvio.composeapp.generated.resources.settings_anilist_sign_in_failed
import nuvio.composeapp.generated.resources.settings_anilist_sync_login_description
import nuvio.composeapp.generated.resources.settings_anilist_sync_login_title
import nuvio.composeapp.generated.resources.settings_anilist_sync_login_warning
import nuvio.composeapp.generated.resources.settings_anilist_sync_now
import nuvio.composeapp.generated.resources.settings_anilist_visit
import nuvio.composeapp.generated.resources.settings_mal_disconnect
import nuvio.composeapp.generated.resources.settings_tracking_approval_redirect
import nuvio.composeapp.generated.resources.settings_tracking_services
import org.jetbrains.compose.resources.stringResource

/** The AniList integration page: account card with the browser sign-in, and login sync across devices. */
internal fun LazyListScope.aniListSettingsContent(isTablet: Boolean) {
    item(key = "anilist_account") {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_services),
            isTablet = isTablet,
        ) {
            AniListProviderCard(Modifier.fillMaxWidth())
        }
    }

    item(key = "anilist_login_sync") {
        SettingsSection(
            title = stringResource(Res.string.settings_anilist_section),
            isTablet = isTablet,
        ) {
            AniListLoginSyncSection(isTablet = isTablet)
        }
    }
}

@Composable
private fun AniListLoginSyncSection(isTablet: Boolean) {
    val uiState by remember {
        AniListAuthRepository.ensureLoaded()
        AniListAuthRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsGroup(isTablet = isTablet) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_anilist_sync_login_title),
            description = stringResource(Res.string.settings_anilist_sync_login_description),
            checked = uiState.syncLoginAcrossDevices,
            isTablet = isTablet,
            onCheckedChange = AniListAuthRepository::setSyncLoginAcrossDevices,
        )
    }
}

@Composable
internal fun AniListProviderCard(modifier: Modifier = Modifier) {
    val uiState by remember {
        AniListAuthRepository.ensureLoaded()
        AniListAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState by remember {
        AniListListRepository.ensureLoaded()
        AniListListRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TrackingProviderCard(
        brand = TrackingBrand.ANILIST,
        mode = when (uiState.mode) {
            AniListConnectionMode.DISCONNECTED -> TrackingConnectionCardMode.DISCONNECTED
            AniListConnectionMode.AWAITING_APPROVAL -> TrackingConnectionCardMode.AWAITING_APPROVAL
            AniListConnectionMode.CONNECTED -> TrackingConnectionCardMode.CONNECTED
        },
        credentialsConfigured = uiState.credentialsConfigured,
        isLoading = uiState.isLoading,
        connectedLabel = stringResource(
            Res.string.settings_anilist_connected_as,
            uiState.username ?: stringResource(Res.string.settings_anilist_default_user),
        ),
        connectedDescription = stringResource(Res.string.settings_anilist_connected_description),
        signInDescription = stringResource(Res.string.settings_anilist_sign_in_description),
        finishSignInLabel = stringResource(Res.string.settings_anilist_finish_sign_in),
        approvalDescription = stringResource(Res.string.settings_tracking_approval_redirect),
        connectLabel = stringResource(Res.string.settings_anilist_connect),
        openLoginLabel = stringResource(Res.string.settings_anilist_open_login),
        disconnectLabel = stringResource(Res.string.settings_mal_disconnect),
        missingCredentialsMessage = stringResource(Res.string.settings_anilist_missing_credentials),
        errorMessage = aniListErrorMessage(uiState.error) ?: listState.errorMessage,
        // The warning is part of the login section, so it is read before signing in.
        statusMessage = stringResource(Res.string.settings_anilist_sync_login_warning)
            .takeIf { uiState.syncLoginAcrossDevices },
        syncLabel = stringResource(Res.string.settings_anilist_sync_now),
        isSyncing = listState.isLoading,
        onSyncRequested = {
            scope.launch { AniListListRepository.refresh(TrackingRefreshIntent.USER_INITIATED) }
        },
        websiteLabel = stringResource(Res.string.settings_anilist_visit),
        websiteUrl = ANILIST_WEBSITE_URL,
        onConnectRequested = AniListAuthRepository::onConnectRequested,
        onResumeAuthorization = {
            AniListAuthRepository.pendingAuthorizationUrl()
                ?: AniListAuthRepository.onConnectRequested()
        },
        onCancelAuthorization = AniListAuthRepository::onCancelAuthorization,
        onDisconnect = AniListAuthRepository::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun aniListErrorMessage(error: AniListAuthError?): String? = when (error) {
    null, AniListAuthError.MISSING_CLIENT_ID -> null
    AniListAuthError.INVALID_CALLBACK -> stringResource(Res.string.settings_anilist_invalid_callback)
    AniListAuthError.AUTHORIZATION_DENIED -> stringResource(Res.string.settings_anilist_authorization_denied)
    AniListAuthError.AUTHORIZATION_EXPIRED -> stringResource(Res.string.settings_anilist_authorization_expired)
    AniListAuthError.SIGN_IN_FAILED -> stringResource(Res.string.settings_anilist_sign_in_failed)
    AniListAuthError.AUTHORIZATION_REVOKED -> stringResource(Res.string.settings_anilist_authorization_revoked)
}

private const val ANILIST_WEBSITE_URL = "https://anilist.co"
