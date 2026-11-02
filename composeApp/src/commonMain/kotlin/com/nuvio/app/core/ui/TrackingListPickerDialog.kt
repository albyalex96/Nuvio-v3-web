package com.nuvio.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.library.localizedLibraryListTitle
import com.nuvio.app.features.settings.IntegrationLogo
import com.nuvio.app.features.settings.integrationLogoPainter
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingMembershipGroup
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.trackingMembershipGroups
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.compose_tracking_list_picker_loading
import nuvio.composeapp.generated.resources.compose_tracking_list_picker_subtitle
import nuvio.composeapp.generated.resources.library_picker_nuvio_group
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingListPickerDialog(
    visible: Boolean,
    title: String,
    tabs: List<TrackingLibraryTab>,
    membership: Map<String, Boolean>,
    isPending: Boolean,
    errorMessage: String?,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tokens = MaterialTheme.nuvio
    val groups = trackingMembershipGroups(tabs)

    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Text(
            text = stringResource(Res.string.compose_tracking_list_picker_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }

        if (isPending && groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NuvioTokens.Space.s80 + NuvioTokens.Space.s80 + NuvioTokens.Space.s80 + NuvioTokens.Space.s40),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
                ) {
                    NuvioLoadingIndicator(
                        modifier = Modifier.size(tokens.icons.lg),
                    )
                    Text(
                        text = stringResource(Res.string.compose_tracking_list_picker_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NuvioTokens.Space.s80 + NuvioTokens.Space.s80 + NuvioTokens.Space.s80 + NuvioTokens.Space.s40),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            ) {
                groups.forEachIndexed { index, group ->
                    item(key = "group:${group.providerId?.storageId ?: "nuvio"}") {
                        TrackingListPickerGroupHeader(
                            group = group,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                        )
                    }
                    items(items = group.tabs, key = { it.key }) { tab ->
                        DialogOption(
                            text = localizedLibraryListTitle(tab.key, tab.title),
                            selected = membership[tab.key] == true,
                            enabled = !isPending,
                            // Lists of a selection group (e.g. the MAL statuses) are alternatives.
                            role = if (tab.selectionGroup != null) Role.RadioButton else Role.Checkbox,
                            onClick = { onToggle(tab.key) },
                        )
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                enabled = !isPending,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = onSave,
                style = DialogButtonStyle.Primary,
                loading = isPending,
            )
        }
    }
}

@Composable
private fun TrackingListPickerGroupHeader(
    group: TrackingMembershipGroup,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        group.providerId?.pickerLogo()?.let { logo ->
            Image(
                painter = integrationLogoPainter(logo),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = group.providerId?.displayName ?: stringResource(Res.string.library_picker_nuvio_group),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
        )
    }
}

private fun TrackingProviderId.pickerLogo(): IntegrationLogo? = when (this) {
    TrackingProviderId.TRAKT -> IntegrationLogo.Trakt
    TrackingProviderId.SIMKL -> IntegrationLogo.Simkl
    TrackingProviderId.MDBLIST -> IntegrationLogo.MdbList
    TrackingProviderId.MAL -> IntegrationLogo.MyAnimeList
    TrackingProviderId.KITSU -> IntegrationLogo.Kitsu
    TrackingProviderId.ANILIST -> IntegrationLogo.AniList
    TrackingProviderId.WETRAKR -> IntegrationLogo.WeTrakr
}
