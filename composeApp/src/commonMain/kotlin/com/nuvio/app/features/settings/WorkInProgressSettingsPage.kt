package com.nuvio.app.features.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_work_in_progress_badge
import nuvio.composeapp.generated.resources.settings_work_in_progress_message
import nuvio.composeapp.generated.resources.settings_work_in_progress_title
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("WorkInProgressIntegration")

/** Placeholder page for integrations that are announced but not implemented yet. */
internal fun LazyListScope.workInProgressIntegrationContent(
    isTablet: Boolean,
    page: SettingsPage,
) {
    item(key = "work_in_progress_${page.name}") {
        LaunchedEffect(page) {
            log.d { "Opened placeholder page for ${page.name}" }
        }
        val logo = when (page) {
            SettingsPage.Telegram -> IntegrationLogo.Telegram
            else -> null
        }
        WorkInProgressPlaceholder(
            isTablet = isTablet,
            name = stringResource(page.titleRes),
            iconPainter = logo?.let { integrationLogoPainter(it) },
            icon = null,
            modifier = Modifier.fillParentMaxHeight(0.8f),
        )
    }
}

@Composable
private fun WorkInProgressPlaceholder(
    isTablet: Boolean,
    name: String,
    iconPainter: Painter?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val logoSize = if (isTablet) 96.dp else 84.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(logoSize),
                color = tokens.colors.accent.copy(alpha = tokens.opacity.pressed),
                shape = RoundedCornerShape(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (iconPainter != null) {
                        Image(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .size(logoSize * 0.6f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(logoSize * 0.5f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            WorkInProgressBadge()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.settings_work_in_progress_title, name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.settings_work_in_progress_message),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WorkInProgressBadge() {
    val tokens = MaterialTheme.nuvio
    val badgeColor = tokens.colors.warning
    val transition = rememberInfiniteTransition(label = "work_in_progress_badge")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "work_in_progress_pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "work_in_progress_scale",
    )

    Surface(
        modifier = Modifier.scale(glow),
        color = badgeColor.copy(alpha = 0.14f),
        contentColor = badgeColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f + 0.5f * pulse)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
            Icon(
                imageVector = Icons.Rounded.Construction,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.settings_work_in_progress_badge),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
