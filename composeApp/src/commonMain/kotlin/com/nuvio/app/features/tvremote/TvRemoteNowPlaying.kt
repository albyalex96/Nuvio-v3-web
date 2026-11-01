package com.nuvio.app.features.tvremote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.SeekIntervalIcon
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_skip
import nuvio.composeapp.generated.resources.player_skip_intro
import nuvio.composeapp.generated.resources.player_skip_movie_credits
import nuvio.composeapp.generated.resources.player_skip_outro
import nuvio.composeapp.generated.resources.player_skip_recap
import nuvio.composeapp.generated.resources.player_skip_to_post_credits
import nuvio.composeapp.generated.resources.tv_remote_buffering
import nuvio.composeapp.generated.resources.tv_remote_command_unavailable
import nuvio.composeapp.generated.resources.tv_remote_connection_lost
import nuvio.composeapp.generated.resources.tv_remote_episode_label
import nuvio.composeapp.generated.resources.tv_remote_live
import nuvio.composeapp.generated.resources.tv_remote_mute
import nuvio.composeapp.generated.resources.tv_remote_next_episode
import nuvio.composeapp.generated.resources.tv_remote_next_episode_label
import nuvio.composeapp.generated.resources.tv_remote_next_episode_searching
import nuvio.composeapp.generated.resources.tv_remote_nothing_playing
import nuvio.composeapp.generated.resources.tv_remote_now_playing_on
import nuvio.composeapp.generated.resources.tv_remote_pause
import nuvio.composeapp.generated.resources.tv_remote_play
import nuvio.composeapp.generated.resources.tv_remote_seek_back
import nuvio.composeapp.generated.resources.tv_remote_seek_forward
import nuvio.composeapp.generated.resources.tv_remote_subtitles
import nuvio.composeapp.generated.resources.tv_remote_subtitles_off
import nuvio.composeapp.generated.resources.tv_remote_unmute
import nuvio.composeapp.generated.resources.tv_remote_volume_down
import nuvio.composeapp.generated.resources.tv_remote_volume_fixed
import nuvio.composeapp.generated.resources.tv_remote_volume_up
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Phone UI of the TV remote: a "now playing" card above the navigation bar while the paired TV
 * plays something, and the control sheet it opens.
 */

/**
 * "Now playing on the TV" card. Draws nothing outside the Android full phone app, without a paired
 * TV or while the TV player is closed. Keeps the TV observed while the screen is resumed.
 */
@Composable
fun TvRemoteNowPlayingCard(modifier: Modifier = Modifier) {
    val client = PhoneTvRemote.activeClient ?: return
    val state by client.state.collectAsStateWithLifecycle()
    if (state.pairedTv == null) return
    LifecycleResumeEffect(client) {
        val release = client.observe()
        onPauseOrDispose { release() }
    }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val playback = state.playback
    AnimatedVisibility(
        visible = playback != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (playback != null) {
            NowPlayingCardContent(
                playback = playback,
                tvName = state.pairedTv?.name.orEmpty(),
                enabled = state.canControl,
                onOpen = { showSheet = true },
                onTogglePlayback = { client.send(TvRemoteCommandType.TogglePlayback) },
            )
        }
    }
    if (showSheet) {
        TvRemoteControlSheet(client = client, onDismiss = { showSheet = false })
    }
}

@Composable
private fun NowPlayingCardContent(
    playback: TvRemotePlayback,
    tvName: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(18.dp)
    val positionMs = rememberInterpolatedPosition(playback)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, shape)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(playback = playback, modifier = Modifier.width(72.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playback.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tv, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = listOfNotNull(episodeLabel(playback), stringResource(Res.string.tv_remote_now_playing_on, tvName))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PlayPauseButton(
                playback = playback,
                enabled = enabled,
                size = 44,
                onClick = onTogglePlayback,
            )
        }
        if (playback.canSeek && playback.durationMs > 0L) {
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.accent,
                trackColor = colors.borderSubtle,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

/** Full controls of the TV playback. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TvRemoteControlSheet(client: PhoneTvRemoteClient, onDismiss: () -> Unit) {
    val state by client.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(client) {
        val release = client.observe()
        onPauseOrDispose { release() }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.nuvio.colors
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val playback = state.playback
            val enabled = state.canControl
            if (state.connection == PhoneTvRemoteConnection.Unreachable) {
                Banner(icon = Icons.Rounded.WifiOff, text = stringResource(Res.string.tv_remote_connection_lost))
            }
            if (playback == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Tv, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Text(
                        text = stringResource(Res.string.tv_remote_nothing_playing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                    state.pairedTv?.let { tv ->
                        Text(
                            text = stringResource(Res.string.tv_remote_now_playing_on, tv.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                VolumeSection(state = state, client = client)
                return@Column
            }

            Header(playback = playback, tvName = state.pairedTv?.name.orEmpty())
            Timeline(playback = playback, enabled = enabled, client = client)
            TransportRow(playback = playback, enabled = enabled, client = client)

            val showSkip = playback.skipSegmentType != null
            val nextEpisode = playback.nextEpisode
            if (showSkip || nextEpisode != null || playback.subtitlesAvailable) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showSkip) {
                        ActionChip(
                            icon = Icons.Rounded.FastForward,
                            text = skipLabel(playback.skipSegmentType, playback.skipToPostCredits),
                            highlighted = true,
                            enabled = enabled,
                            onClick = { client.send(TvRemoteCommandType.SkipSegment) },
                        )
                    }
                    if (nextEpisode != null) {
                        ActionChip(
                            icon = Icons.Rounded.SkipNext,
                            text = if (nextEpisode.available) {
                                stringResource(
                                    Res.string.tv_remote_next_episode_label,
                                    nextEpisode.seasonNumber,
                                    nextEpisode.episodeNumber,
                                )
                            } else {
                                stringResource(Res.string.tv_remote_next_episode_searching)
                            },
                            contentDescription = stringResource(Res.string.tv_remote_next_episode),
                            enabled = enabled && nextEpisode.available,
                            onClick = { client.send(TvRemoteCommandType.NextEpisode) },
                        )
                    }
                    if (playback.subtitlesAvailable) {
                        ActionChip(
                            icon = if (playback.subtitlesEnabled) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                            text = if (playback.subtitlesEnabled) {
                                playback.subtitleLabel ?: stringResource(Res.string.tv_remote_subtitles)
                            } else {
                                "${stringResource(Res.string.tv_remote_subtitles)}: ${stringResource(Res.string.tv_remote_subtitles_off)}"
                            },
                            highlighted = playback.subtitlesEnabled,
                            enabled = enabled,
                            onClick = { client.send(TvRemoteCommandType.ToggleSubtitles) },
                        )
                    }
                }
            }

            VolumeSection(state = state, client = client)

            if (state.lastCommandError == TvRemoteCommandError.Unavailable) {
                Text(
                    text = stringResource(Res.string.tv_remote_command_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Header(playback: TvRemotePlayback, tvName: String) {
    val colors = MaterialTheme.nuvio.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Artwork(playback = playback, modifier = Modifier.fillMaxSize())
            if (playback.isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                }
            }
            if (playback.isLive) {
                Text(
                    text = stringResource(Res.string.tv_remote_live),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD32F2F))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = playback.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val episode = listOfNotNull(episodeLabel(playback), playback.episodeTitle).joinToString(" · ")
            if (episode.isNotBlank()) {
                Text(
                    text = episode,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (playback.isBuffering) {
                    stringResource(Res.string.tv_remote_buffering)
                } else {
                    stringResource(Res.string.tv_remote_now_playing_on, tvName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun Timeline(playback: TvRemotePlayback, enabled: Boolean, client: PhoneTvRemoteClient) {
    if (!playback.canSeek || playback.durationMs <= 0L) return
    val colors = MaterialTheme.nuvio.colors
    val positionMs = rememberInterpolatedPosition(playback)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: (positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    Column {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { target ->
                    client.send(TvRemoteCommandType.SeekTo, positionMs = (target * playback.durationMs).toLong())
                }
                dragFraction = null
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.borderDefault,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime((fraction * playback.durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "-" + formatTime(playback.durationMs - (fraction * playback.durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TransportRow(playback: TvRemotePlayback, enabled: Boolean, client: PhoneTvRemoteClient) {
    val colors = MaterialTheme.nuvio.colors
    val seconds = playback.seekIntervalSeconds
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playback.canSeek) {
            IconButton(
                onClick = { client.send(TvRemoteCommandType.SeekStep, forward = false) },
                enabled = enabled,
                modifier = Modifier.size(56.dp),
            ) {
                SeekIntervalIcon(
                    seconds = seconds,
                    forward = false,
                    contentDescription = stringResource(Res.string.tv_remote_seek_back, seconds),
                    tint = if (enabled) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        PlayPauseButton(
            playback = playback,
            enabled = enabled,
            size = 72,
            onClick = { client.send(TvRemoteCommandType.TogglePlayback) },
        )
        if (playback.canSeek) {
            IconButton(
                onClick = { client.send(TvRemoteCommandType.SeekStep, forward = true) },
                enabled = enabled,
                modifier = Modifier.size(56.dp),
            ) {
                SeekIntervalIcon(
                    seconds = seconds,
                    forward = true,
                    contentDescription = stringResource(Res.string.tv_remote_seek_forward, seconds),
                    tint = if (enabled) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun VolumeSection(state: PhoneTvRemoteUiState, client: PhoneTvRemoteClient) {
    if (state.connection != PhoneTvRemoteConnection.Connected && state.volume == null) return
    val colors = MaterialTheme.nuvio.colors
    val volume = state.volume
    if (volume == null) {
        Text(
            text = stringResource(Res.string.tv_remote_volume_fixed),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val enabled = state.connection == PhoneTvRemoteConnection.Connected
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceElevated)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { client.send(TvRemoteCommandType.ToggleMute) }, enabled = enabled) {
            Icon(
                imageVector = if (volume.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(if (volume.muted) Res.string.tv_remote_unmute else Res.string.tv_remote_mute),
                tint = if (volume.muted) colors.warning else colors.textPrimary,
            )
        }
        IconButton(onClick = { client.send(TvRemoteCommandType.VolumeDown) }, enabled = enabled) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(Res.string.tv_remote_volume_down), tint = colors.textPrimary)
        }
        var dragLevel by remember { mutableStateOf<Float?>(null) }
        val max = volume.max.coerceAtLeast(1)
        Slider(
            value = dragLevel ?: volume.level.toFloat(),
            onValueChange = { dragLevel = it },
            onValueChangeFinished = {
                dragLevel?.let { level -> client.send(TvRemoteCommandType.SetVolume, level = level.toInt()) }
                dragLevel = null
            },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.borderDefault,
            ),
        )
        IconButton(onClick = { client.send(TvRemoteCommandType.VolumeUp) }, enabled = enabled) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(Res.string.tv_remote_volume_up), tint = colors.textPrimary)
        }
        Text(
            text = "${((dragLevel ?: volume.level.toFloat()) * 100 / max).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(end = 8.dp).widthIn(min = 36.dp),
        )
    }
}

@Composable
private fun PlayPauseButton(playback: TvRemotePlayback, enabled: Boolean, size: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.accent else colors.borderDefault)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (playback.isPlaying) Res.string.tv_remote_pause else Res.string.tv_remote_play),
            tint = colors.onAccent,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(50)
    val content = when {
        !enabled -> colors.textDisabled
        highlighted -> colors.onAccent
        else -> colors.textPrimary
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (highlighted && enabled) colors.accent else colors.surfaceElevated)
            .border(1.dp, if (highlighted && enabled) colors.accent else colors.borderSubtle, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.warning.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
    }
}

@Composable
private fun Artwork(playback: TvRemotePlayback, modifier: Modifier) {
    val colors = MaterialTheme.nuvio.colors
    Box(modifier = modifier.background(colors.surfaceCard), contentAlignment = Alignment.Center) {
        if (playback.artworkUrl != null) {
            AsyncImage(
                model = playback.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun episodeLabel(playback: TvRemotePlayback): String? {
    val season = playback.seasonNumber ?: return null
    val episode = playback.episodeNumber ?: return null
    return stringResource(Res.string.tv_remote_episode_label, season, episode)
}

@Composable
private fun skipLabel(type: String?, skipsToPostCredits: Boolean): String =
    stringResource(tvRemoteSkipLabelRes(type, skipsToPostCredits))

/** Label of the skip button for a segment type: the same one the TV player shows. */
internal fun tvRemoteSkipLabelRes(type: String?, skipsToPostCredits: Boolean): StringResource =
    when (type?.trim()?.lowercase()) {
        "intro", "op", "mixed-op" -> Res.string.player_skip_intro
        "outro", "ed", "mixed-ed", "credits" ->
            if (skipsToPostCredits) Res.string.player_skip_to_post_credits else Res.string.player_skip_outro
        "recap" -> Res.string.player_skip_recap
        "movie-credits" ->
            if (skipsToPostCredits) Res.string.player_skip_to_post_credits else Res.string.player_skip_movie_credits
        else -> Res.string.player_skip
    }

/** Position moved forward locally while the TV plays, between two state updates. */
@Composable
private fun rememberInterpolatedPosition(playback: TvRemotePlayback): Long {
    val receivedAt = remember(playback) { TimeSource.Monotonic.markNow() }
    var now by remember(playback) { mutableLongStateOf(playback.positionMs) }
    LaunchedEffect(playback) {
        if (!playback.isPlaying || playback.isBuffering || !playback.canSeek) return@LaunchedEffect
        while (true) {
            val elapsed = receivedAt.elapsedNow().inWholeMilliseconds
            val position = playback.positionMs + (elapsed * playback.playbackSpeed).toLong()
            now = if (playback.durationMs > 0L) position.coerceAtMost(playback.durationMs) else position
            delay(500L)
        }
    }
    return now
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun two(value: Long) = value.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}
