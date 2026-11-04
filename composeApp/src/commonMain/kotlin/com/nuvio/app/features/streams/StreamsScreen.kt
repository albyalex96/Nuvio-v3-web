package com.nuvio.app.features.streams

import com.nuvio.app.features.cloudstream.CloudStreamBadge
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioActionMenuDivider
import com.nuvio.app.core.ui.NuvioActionMenuGroup
import com.nuvio.app.core.ui.NuvioActionMenuRow
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.player.PrivatePlaybackRequests
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.hls.HlsDownloadDialog
import com.nuvio.app.features.downloads.hls.HlsDownloadSpec
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.features.cast.CastDevicePickerDialog
import com.nuvio.app.features.cast.CastPlatform
import com.nuvio.app.features.cast.CastSessionState
import com.nuvio.app.features.cast.castCompatibilityIssues
import com.nuvio.app.features.cast.isAvailable
import com.nuvio.app.tv.LocalTvShell
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.tv.TvPosterMenuHook
import com.nuvio.app.core.ui.PosterZoomOverlayAction
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.shimmer
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.downloads.hls.HlsDownloadPreferences
import com.nuvio.app.features.downloads.season.SeasonDownloadPickBanner
import com.nuvio.app.features.downloads.season.SeasonDownloadRepository
import com.nuvio.app.features.downloads.season.SeasonDownloadRequest
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.debrid.toastMessage
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.streams.parser.StreamCardHost
import com.nuvio.app.features.streams.parser.StreamOriginalDescriptionDialog
import com.nuvio.app.features.streams.parser.StreamParserContextEffect
import com.nuvio.app.features.streams.parser.StreamParserSettingsRepository
import com.nuvio.app.features.streams.parser.isQualitySortedGroup
import com.nuvio.app.features.streams.parser.rememberQualitySortedGroups
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// Streams Screen
// ---------------------------------------------------------------------------

@Composable
fun StreamsScreen(
    type: String,
    videoId: String,
    parentMetaId: String,
    parentMetaType: String,
    title: String,
    logo: String? = null,
    poster: String? = null,
    background: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    episodeThumbnail: String? = null,
    overview: String? = null,
    resumePositionMs: Long? = null,
    resumeProgressFraction: Float? = null,
    manualSelection: Boolean = false,
    startFromBeginning: Boolean = false,
    showLoadingScreen: Boolean = false,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit = { _, _, _ -> },
    onStreamActionOpen: (
        stream: StreamItem,
        openExternally: Boolean,
        resumePositionMs: Long?,
        resumeProgressFraction: Float?,
    ) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useNativeNavigation = LocalUseNativeNavigation.current
    val uiState by StreamsRepository.uiState.collectAsStateWithLifecycle()
    val streamDisplaySettings by remember {
        StreamBadgeSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgressUiState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val metaScreenSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    remember {
        DownloadsRepository.ensureLoaded()
    }
    val isEpisode = seasonNumber != null && episodeNumber != null
    StreamParserContextEffect(title, seasonNumber, episodeNumber, episodeTitle)
    val clipboardManager = LocalClipboardManager.current
    val streamLinkCopiedText = stringResource(Res.string.streams_link_copied)
    val noDirectStreamLinkText = stringResource(Res.string.streams_no_direct_link)
    var streamActionsTarget by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    var originalDescriptionTarget by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    val streamParserSettings by remember {
        StreamParserSettingsRepository.ensureLoaded()
        StreamParserSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    // With the Stream Parser the menu also offers the original description, so it opens for
    // every stream; the link actions stay limited to streams that have a link.
    val onStreamLongPress: (StreamItem) -> Unit = { stream ->
        if (streamParserSettings.enabled || stream.hasLinkActions) {
            streamActionsTarget = stream
        }
    }
    val downloadScope = rememberCoroutineScope()
    // HLS stream waiting for the user to pick quality, audio and subtitles.
    var pendingHlsDownload by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    val enqueueDownload: (StreamItem, HlsDownloadSpec?) -> Unit = { stream, hls ->
        val result = DownloadsRepository.enqueueFromStream(
            contentType = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            parentMetaType = parentMetaType,
            title = title,
            logo = logo,
            poster = poster,
            background = background,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            episodeThumbnail = episodeThumbnail,
            stream = stream,
            hls = hls,
        )
        NuvioToastController.show(result.toastMessage())
    }
    val startDownload: suspend (StreamItem) -> Unit = { stream ->
        if (DownloadsRepository.isHlsStreamDownload(stream)) {
            pendingHlsDownload = stream
        } else {
            enqueueDownload(stream, null)
        }
    }
    // Season download opened from the details screen: the stream picked here is the model for
    // every episode, and picking it starts the download instead of the playback.
    val pendingSeasonRequest by SeasonDownloadRepository.pendingRequest.collectAsStateWithLifecycle()
    val seasonRequest = pendingSeasonRequest?.takeIf { it.matches(parentMetaId, seasonNumber, episodeNumber) }
    // Stream picked as the model of the season, waiting for its HLS choices.
    var pendingSeasonTemplate by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    val seasonDownloadStartedText = stringResource(Res.string.season_download_started)
    DisposableEffect(parentMetaId, seasonNumber, episodeNumber) {
        onDispose {
            SeasonDownloadRepository.pendingRequest.value
                ?.takeIf { it.matches(parentMetaId, seasonNumber, episodeNumber) }
                ?.let(SeasonDownloadRepository::clearRequest)
        }
    }
    val finishSeasonDownload: (SeasonDownloadRequest, StreamItem, StreamItem, HlsDownloadSpec?, HlsDownloadPreferences?) -> Unit =
        { request, template, playable, hlsSpec, hlsPreferences ->
            SeasonDownloadRepository.start(request, template, playable, hlsSpec, hlsPreferences)
            NuvioToastController.show(seasonDownloadStartedText)
            onBack()
        }
    val startSeasonDownload: (StreamItem) -> Unit = { template ->
        downloadScope.launch {
            val request = seasonRequest ?: return@launch
            val playable = if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(template)) {
                when (val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(template, seasonNumber, episodeNumber)) {
                    is DirectDebridPlayableResult.Success -> resolved.stream
                    else -> {
                        resolved.toastMessage()?.let { NuvioToastController.show(it) }
                        return@launch
                    }
                }
            } else {
                template
            }
            if (playable.playableDirectUrl == null) {
                NuvioToastController.show(noDirectStreamLinkText)
                return@launch
            }
            if (DownloadsRepository.isHlsStreamDownload(playable)) {
                pendingSeasonTemplate = template
                pendingHlsDownload = playable
            } else {
                finishSeasonDownload(request, template, playable, null, null)
            }
        }
    }
    val selectStream: (StreamItem, Long?, Float?) -> Unit = { stream, positionMs, progressFraction ->
        if (seasonRequest != null) {
            startSeasonDownload(stream)
        } else {
            PrivatePlaybackRequests.clear()
            onStreamSelected(stream, positionMs, progressFraction)
        }
    }
    val castState by CastPlatform.state.collectAsStateWithLifecycle()
    val castAvailable = castState.isAvailable && !TvAppRuntime.isTvApp
    // Stream waiting for a cast session: it opens in the player (on the receiver) once connected.
    var pendingCastStream by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    var preferredFilterApplied by remember(videoId) { mutableStateOf(false) }
    val episodeProgress = watchProgressUiState.progressForVideo(
        videoId = videoId,
        parentMetaId = parentMetaId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )
    val storedProgress = if (startFromBeginning) {
        null
    } else {
        episodeProgress
    }
    val resumeState = resolveStreamResumeState(
        progress = episodeProgress,
        initialPositionMs = resumePositionMs,
        initialProgressFraction = resumeProgressFraction,
        startFromBeginning = startFromBeginning,
    )
    val effectiveResumePositionMs = resumeState.positionMs
    val effectiveResumeProgressFraction = resumeState.progressFraction

    LaunchedEffect(type, videoId, seasonNumber, episodeNumber, manualSelection) {
        StreamsRepository.load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = seasonNumber,
            episode = episodeNumber,
            manualSelection = manualSelection,
        )
    }

    LaunchedEffect(uiState.groups, storedProgress?.providerAddonId, preferredFilterApplied) {
        if (preferredFilterApplied) return@LaunchedEffect
        val preferredAddonId = storedProgress?.providerAddonId ?: return@LaunchedEffect
        if (uiState.groups.any { it.addonId == preferredAddonId }) {
            StreamsRepository.selectFilter(preferredAddonId)
            preferredFilterApplied = true
        }
    }

    val heroArtwork = if (isEpisode) {
        episodeThumbnail ?: background ?: poster
    } else {
        background ?: poster
    }
    val isEpisodeWatched = episodeProgress?.isEffectivelyCompleted == true || watchedItemKeys(
        type = parentMetaType,
        id = parentMetaId,
        season = seasonNumber,
        episode = episodeNumber,
    ).any(watchedUiState.watchedKeys::contains)
    val blurEpisodeThumbnail = metaScreenSettings.blurUnwatchedEpisodes &&
        isEpisode &&
        !isEpisodeWatched &&
        !episodeThumbnail.isNullOrBlank()
    val reloadStreams: () -> Unit = {
        StreamsRepository.reload(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = seasonNumber,
            episode = episodeNumber,
            manualSelection = manualSelection,
        )
    }

    if (showLoadingScreen) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTabletLayout = maxWidth >= 768.dp

        if (isTabletLayout) {
            TabletStreamsLayout(
                isEpisode = isEpisode,
                title = title,
                logo = logo,
                poster = poster,
                background = background,
                episodeThumbnail = episodeThumbnail,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                overview = overview,
                uiState = uiState,
                debridEnabled = debridSettings.canResolvePlayableLinks,
                appendInstantServiceToDefaultName = debridSettings.canResolvePlayableLinks && !debridSettings.hasCustomStreamFormatting,
                resumePositionMs = effectiveResumePositionMs,
                resumeProgressFraction = effectiveResumeProgressFraction,
                onStreamSelected = selectStream,
                onStreamLongPress = onStreamLongPress,
                onRefresh = reloadStreams,
            )
        } else {
            MobileStreamsLayout(
                isEpisode = isEpisode,
                backgroundMode = streamDisplaySettings.backgroundMode,
                title = title,
                logo = logo,
                heroArtwork = heroArtwork,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                blurEpisodeThumbnail = blurEpisodeThumbnail,
                uiState = uiState,
                debridEnabled = debridSettings.canResolvePlayableLinks,
                appendInstantServiceToDefaultName = debridSettings.canResolvePlayableLinks && !debridSettings.hasCustomStreamFormatting,
                resumePositionMs = effectiveResumePositionMs,
                resumeProgressFraction = effectiveResumeProgressFraction,
                onStreamSelected = selectStream,
                onStreamLongPress = onStreamLongPress,
                onRefresh = reloadStreams,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(start = 12.dp, top = if (useNativeNavigation) 52.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NuvioBackButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp),
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
        }


        seasonRequest?.let { request ->
            SeasonDownloadPickBanner(
                request = request,
                onCancel = { SeasonDownloadRepository.clearRequest(request) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 16.dp),
            )
        }

        DisposableEffect(Unit) {
            onDispose { PrivatePlaybackRequests.clear() }
        }

        StreamActionsSheet(
            stream = streamActionsTarget,
            externalPlayerEnabled = playerSettings.externalPlayerEnabled,
            showOriginalDescription = streamParserSettings.enabled,
            castAvailable = castAvailable,
            // Pointless while private viewing is on for everything.
            privatePlaybackAvailable = !playerSettings.privateViewingEnabled,
            isEpisode = isEpisode,
            imageUrl = poster ?: background,
            onDismiss = { streamActionsTarget = null },
            onPlay = { stream ->
                PrivatePlaybackRequests.clear()
                onStreamSelected(stream, effectiveResumePositionMs, effectiveResumeProgressFraction)
            },
            onPlayPrivately = { stream ->
                PrivatePlaybackRequests.request(parentMetaId = parentMetaId, videoId = videoId)
                onStreamSelected(stream, effectiveResumePositionMs, effectiveResumeProgressFraction)
            },
            onCast = { stream ->
                if (castState is CastSessionState.Connected) {
                    streamCastLog.i { "Casting the selected stream" }
                    onStreamActionOpen(stream, false, effectiveResumePositionMs, effectiveResumeProgressFraction)
                } else {
                    streamCastLog.i { "No cast session: opening the device picker" }
                    pendingCastStream = stream
                }
            },
            onShowOriginalDescription = { stream -> originalDescriptionTarget = stream },
            onCopyLink = { stream ->
                val directUrl = stream.playableDirectUrl ?: stream.externalOpenUrl
                if (!directUrl.isNullOrBlank()) {
                    clipboardManager.setText(AnnotatedString(directUrl))
                    NuvioToastController.show(streamLinkCopiedText)
                } else if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
                    downloadScope.launch {
                        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
                            stream = stream,
                            season = seasonNumber,
                            episode = episodeNumber,
                        )
                        when (resolved) {
                            is DirectDebridPlayableResult.Success -> {
                                val resolvedUrl = resolved.stream.playableDirectUrl
                                if (!resolvedUrl.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(resolvedUrl))
                                    NuvioToastController.show(streamLinkCopiedText)
                                } else {
                                    NuvioToastController.show(noDirectStreamLinkText)
                                }
                            }
                            else -> {
                                val message = resolved.toastMessage()
                                if (message != null) {
                                    NuvioToastController.show(message)
                                }
                            }
                        }
                    }
                } else {
                    NuvioToastController.show(noDirectStreamLinkText)
                }
            },
            onDownload = { stream ->
                if (seasonRequest != null) {
                    startSeasonDownload(stream)
                } else downloadScope.launch {
                    if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
                        when (
                            val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
                                stream = stream,
                                season = seasonNumber,
                                episode = episodeNumber,
                            )
                        ) {
                            is DirectDebridPlayableResult.Success -> startDownload(resolved.stream)
                            else -> resolved.toastMessage()?.let { NuvioToastController.show(it) }
                        }
                    } else {
                        startDownload(stream)
                    }
                }
            },
            onOpen = { stream, openExternally ->
                onStreamActionOpen(
                    stream,
                    openExternally,
                    effectiveResumePositionMs,
                    effectiveResumeProgressFraction,
                )
            },
        )

        originalDescriptionTarget?.let { stream ->
            StreamOriginalDescriptionDialog(
                stream = stream,
                onDismiss = { originalDescriptionTarget = null },
            )
        }

        pendingHlsDownload?.let { stream ->
            val url = stream.playableDirectUrl
            if (url != null) {
                HlsDownloadDialog(
                    url = url,
                    headers = DownloadsRepository.downloadRequestHeaders(stream),
                    onDismiss = {
                        pendingHlsDownload = null
                        pendingSeasonTemplate = null
                    },
                    onConfirm = { spec, preferences ->
                        val template = pendingSeasonTemplate
                        pendingHlsDownload = null
                        pendingSeasonTemplate = null
                        val request = seasonRequest
                        if (template != null && request != null) {
                            finishSeasonDownload(request, template, stream, spec, preferences)
                        } else {
                            enqueueDownload(stream, spec)
                        }
                    },
                )
            }
        }

        pendingCastStream?.let { stream ->
            CastDevicePickerDialog(
                onDismiss = { pendingCastStream = null },
                onConnected = {
                    pendingCastStream = null
                    onStreamActionOpen(stream, false, effectiveResumePositionMs, effectiveResumeProgressFraction)
                },
            )
        }
    }
}

private val StreamItem.hasLinkActions: Boolean
    get() = playableDirectUrl != null || shouldOpenExternally || isAddonDebridCandidate

/**
 * Streams the player can hand to the receiver: HTTP links (direct or through debrid) and
 * torrents, which the P2P engine serves through the LAN proxy.
 */
private val StreamItem.canCast: Boolean
    get() = playableDirectUrl != null || isAddonDebridCandidate || p2pInfoHash != null

private val streamCastLog = Logger.withTag("Cast")

@Composable
private fun MobileStreamsLayout(
    isEpisode: Boolean,
    backgroundMode: StreamBackgroundMode,
    title: String,
    logo: String?,
    heroArtwork: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    blurEpisodeThumbnail: Boolean,
    uiState: StreamsUiState,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundMode == StreamBackgroundMode.Cinematic && heroArtwork != null) {
            AsyncImage(
                model = heroArtwork,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(22.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isEpisode) 0.9f else 0.82f)),
            )
        }

        val streamBlendColor = MaterialTheme.colorScheme.background

        Column(modifier = Modifier.fillMaxSize()) {
            if (isEpisode && seasonNumber != null && episodeNumber != null) {
                EpisodeHeroBlock(
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle ?: title,
                    thumbnail = heroArtwork,
                    blurred = blurEpisodeThumbnail,
                    showTitle = title,
                )
            } else {
                MovieHeroBlock(
                    title = title,
                    logo = logo,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (isEpisode && backgroundMode == StreamBackgroundMode.Cinematic) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        streamBlendColor.copy(alpha = 0.98f),
                                        streamBlendColor.copy(alpha = 0.84f),
                                        streamBlendColor.copy(alpha = 0.52f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if ((resumePositionMs != null && resumePositionMs > 0L) || (resumeProgressFraction != null && resumeProgressFraction > 0f)) {
                        ResumeBanner(
                            positionMs = resumePositionMs,
                            progressFraction = resumeProgressFraction,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    ProviderFilterRow(
                        groups = uiState.groups,
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { addonId -> StreamsRepository.selectFilter(addonId) },
                        onRefresh = onRefresh,
                    )

                    StreamList(
                        uiState = uiState,
                        debridEnabled = debridEnabled,
                        appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                        onStreamSelected = onStreamSelected,
                        onStreamLongPress = onStreamLongPress,
                        resumePositionMs = resumePositionMs,
                        resumeProgressFraction = resumeProgressFraction,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal data class StreamResumeState(
    val positionMs: Long? = null,
    val progressFraction: Float? = null,
)

internal fun resolveStreamResumeState(
    progress: WatchProgressEntry?,
    initialPositionMs: Long?,
    initialProgressFraction: Float?,
    startFromBeginning: Boolean,
): StreamResumeState {
    if (startFromBeginning || progress?.isResumable == false) return StreamResumeState()
    val fraction = (if (progress != null) progress.progressPercent?.div(100f) else initialProgressFraction)
        ?.takeIf { it > 0f }?.coerceIn(0f, 1f)
    val position = if (fraction != null) null
        else (progress?.lastPositionMs ?: initialPositionMs)?.takeIf { it > 0L }
    return StreamResumeState(positionMs = position, progressFraction = fraction)
}

@Composable
internal fun ResumeBanner(
    positionMs: Long?,
    progressFraction: Float? = null,
    modifier: Modifier = Modifier,
) {
    val resumeText = when {
        progressFraction != null && progressFraction > 0f -> stringResource(
            Res.string.streams_resume_from_percent,
            (progressFraction * 100f).roundToInt(),
        )
        positionMs != null && positionMs > 0L -> stringResource(
            Res.string.streams_resume_from_time,
            positionMs.toPlaybackClock(),
        )
        else -> null
    } ?: return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = resumeText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Movie Hero
// ---------------------------------------------------------------------------

@Composable
private fun MovieHeroBlock(
    title: String,
    logo: String?,
    modifier: Modifier = Modifier,
) {
    var logoLoadError by remember(logo) { mutableStateOf(false) }
    val logoUrl = logo?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null && !logoLoadError) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth(0.85f),
                contentScale = ContentScale.Fit,
                onError = { logoLoadError = true },
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Episode Hero
// ---------------------------------------------------------------------------

@Composable
private fun EpisodeHeroBlock(
    seasonNumber: Int,
    episodeNumber: Int,
    episodeTitle: String,
    thumbnail: String?,
    blurred: Boolean,
    showTitle: String,
    modifier: Modifier = Modifier,
) {
    val heroBlendColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        // Thumbnail image
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurred) Modifier.blur(18.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f)),
        )

        // Gradient overlay bottom-up
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.58f to Color.Transparent,
                            0.8f to Color.Black.copy(alpha = 0.42f),
                            0.93f to heroBlendColor.copy(alpha = 0.84f),
                            1.0f to heroBlendColor,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )

        // Safe-area push-down for status bar, then content pinned to bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Episode label
            Text(
                text = formatEpisodeCodeWithSelectedFormat(seasonNumber, episodeNumber)
                    ?: stringResource(Res.string.streams_episode_badge, seasonNumber, episodeNumber),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Episode title
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Show title
            Text(
                text = showTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stream List
// ---------------------------------------------------------------------------

@Composable
internal fun StreamList(
    uiState: StreamsUiState,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
    modifier: Modifier = Modifier,
) {
    val filteredGroups = rememberQualitySortedGroups(uiState.filteredGroups)
    val hasGroups = filteredGroups.isNotEmpty()
    val hasAnyStreams = filteredGroups.any { it.streams.isNotEmpty() }
    val anyLoading = filteredGroups.any { it.isLoading }
    val torrentNotSupportedText = stringResource(Res.string.streams_torrent_not_supported)
    val fetchingText = stringResource(Res.string.streams_fetching)
    val findingStreamsText = stringResource(Res.string.streams_finding_streams)
    val checkingMoreAddonsText = stringResource(Res.string.streams_checking_more_addons)
    val formatStreamSize = rememberStreamSizeLabelFormat()
    val sharedStream = LocalSharedStream.current
    val streamBadgeSettings by remember {
        StreamBadgeSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalStreamSizeLabelFormat provides formatStreamSize) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        sharedStream?.let { shared ->
            item(key = "shared_stream") {
                Column {
                    StreamSectionHeader(
                        addonName = shared.label,
                        isLoading = false,
                        fetchingText = fetchingText,
                    )
                    StreamCardHost(
                        stream = shared.stream,
                        enabled = true,
                        appendInstantServiceToDefaultName = false,
                        showFileSizeBadges = streamBadgeSettings.showFileSizeBadges,
                        showAddonLogo = streamBadgeSettings.showAddonLogo,
                        badgePlacement = streamBadgeSettings.badgePlacement,
                        onClick = { onStreamSelected(shared.stream, resumePositionMs, resumeProgressFraction) },
                        outlined = true,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
        when {
            hasGroups && anyLoading && !hasAnyStreams -> {
                item {
                    LoadingStateBlock(findingStreamsText = findingStreamsText)
                }
            }

            !hasAnyStreams && !uiState.isAnyLoading -> {
                item {
                    EmptyStateBlock(reason = uiState.emptyStateReason)
                }
            }

            else -> {
                filteredGroups.forEachIndexed { groupIndex, group ->
                    streamSection(
                        sectionKey = streamSectionRenderKey(groupIndex = groupIndex, group = group),
                        group = group,
                        showHeader = uiState.selectedFilter == null && !group.isQualitySortedGroup,
                        debridEnabled = debridEnabled,
                        appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                        showFileSizeBadges = streamBadgeSettings.showFileSizeBadges,
                        showAddonLogo = streamBadgeSettings.showAddonLogo,
                        badgePlacement = streamBadgeSettings.badgePlacement,
                        torrentNotSupportedText = torrentNotSupportedText,
                        fetchingText = fetchingText,
                        onStreamSelected = onStreamSelected,
                        onStreamLongPress = onStreamLongPress,
                        resumePositionMs = resumePositionMs,
                        resumeProgressFraction = resumeProgressFraction,
                    )
                }
                if (anyLoading) {
                    item {
                        FooterLoadingBlock(checkingMoreAddonsText = checkingMoreAddonsText)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(nuvioSafeBottomPadding(80.dp)))
                }
            }
        }
    }
    }
}

private fun LazyListScope.streamSection(
    sectionKey: String,
    group: AddonStreamGroup,
    showHeader: Boolean,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    showFileSizeBadges: Boolean,
    showAddonLogo: Boolean,
    badgePlacement: StreamBadgePlacement,
    torrentNotSupportedText: String,
    fetchingText: String,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
) {
    if (group.streams.isEmpty() && !group.isLoading) return

    if (showHeader) {
        item(key = "header_$sectionKey") {
            StreamSectionHeader(
                addonName = group.addonName,
                isLoading = group.isLoading,
                fetchingText = fetchingText,
                isCloudStream = CloudStreamStreams.isCloudStreamSourceId(group.addonId),
            )
        }
    }

    val streamsBySource = if (group.isQualitySortedGroup) {
        // Quality-sorted list: keep the sort order, no per-source sub-headers.
        mapOf("" to group.streams)
    } else {
        group.streams.groupBy { stream ->
            stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonName
        }
    }
    val sortedSources = streamsBySource.keys.sortedBy { it.lowercase() }
    val showSourceHeaders = sortedSources.size > 1

    sortedSources.forEachIndexed { sourceIndex, sourceName ->
        val sourceStreams = streamsBySource[sourceName].orEmpty()
        if (showSourceHeaders) {
            item(key = "source_${sectionKey}_$sourceIndex") {
                StreamSourceHeader(sourceName = sourceName)
            }
        }

        itemsIndexed(
            items = sourceStreams,
            key = { index, stream ->
                streamCardRenderKey(
                    sectionKey = sectionKey,
                    sourceIndex = sourceIndex,
                    itemIndex = index,
                    stream = stream,
                )
            },
        ) { _, stream ->
            val isSelectable = stream.isSelectableForPlayback(debridEnabled)
            val isUnsupportedTorrentStream =
                stream.needsLocalDebridResolve &&
                    !AppFeaturePolicy.p2pEnabled &&
                    !(debridEnabled && stream.isAddonDebridCandidate)
            StreamCardHost(
                stream = stream,
                enabled = isSelectable || isUnsupportedTorrentStream,
                appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                showFileSizeBadges = showFileSizeBadges,
                showAddonLogo = showAddonLogo,
                badgePlacement = badgePlacement,
                onClick = {
                    if (isSelectable) {
                        onStreamSelected(stream, resumePositionMs, resumeProgressFraction)
                    } else if (isUnsupportedTorrentStream) {
                        NuvioToastController.show(torrentNotSupportedText)
                    }
                },
                onLongClick = { onStreamLongPress(stream) },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

internal fun streamSectionRenderKey(
    groupIndex: Int,
    group: AddonStreamGroup,
): String = "$groupIndex:${group.addonId}"

internal fun streamCardRenderKey(
    sectionKey: String,
    sourceIndex: Int,
    itemIndex: Int,
    stream: StreamItem,
): String = buildString {
    append(sectionKey)
    append(':')
    append(sourceIndex)
    append(':')
    append(itemIndex)
    append(':')
    append(stream.url ?: stream.infoHash ?: stream.clientResolve?.infoHash ?: stream.streamLabel)
    stream.externalUrl?.let {
        append(':')
        append(it)
    }
}

// ---------------------------------------------------------------------------
// Stream Section Header
// ---------------------------------------------------------------------------

@Composable
private fun StreamSectionHeader(
    addonName: String,
    isLoading: Boolean,
    fetchingText: String,
    modifier: Modifier = Modifier,
    isCloudStream: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = addonName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCloudStream) CloudStreamBadge()
        }
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = fetchingText,
                modifier = Modifier.shimmer(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StreamSourceHeader(
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = sourceName,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamActionsSheet(
    stream: StreamItem?,
    externalPlayerEnabled: Boolean,
    showOriginalDescription: Boolean,
    castAvailable: Boolean,
    privatePlaybackAvailable: Boolean,
    isEpisode: Boolean,
    imageUrl: String?,
    onDismiss: () -> Unit,
    onPlay: (StreamItem) -> Unit,
    onPlayPrivately: (StreamItem) -> Unit,
    onCast: (StreamItem) -> Unit,
    onShowOriginalDescription: (StreamItem) -> Unit,
    onCopyLink: (StreamItem) -> Unit,
    onDownload: (StreamItem) -> Unit,
    onOpen: (StreamItem, openExternally: Boolean) -> Unit,
) {
    if (stream == null) return
    val privatePlaybackTitle = stringResource(Res.string.private_viewing_play_action)

    // TV: the card menu dialog of the TV shell (icons, D-pad). It also ignores the OK still held
    // from the long press, which the bottom sheet turned into a click on its first row.
    if (LocalTvShell.current?.posterMenu != null) {
        val openLabel = stringResource(
            if (externalPlayerEnabled) Res.string.streams_open_internal_player else Res.string.streams_open_external_player,
        )
        TvPosterMenuHook(
            title = stream.streamLabel,
            subtitle = stream.streamSubtitle?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            actions = buildList {
                add(PosterZoomOverlayAction(Icons.Rounded.PlayArrow, stringResource(Res.string.action_play), onSelected = { onPlay(stream) }))
                if (stream.hasLinkActions) {
                    add(PosterZoomOverlayAction(Icons.AutoMirrored.Rounded.OpenInNew, openLabel, onSelected = { onOpen(stream, !externalPlayerEnabled) }))
                }
                if (showOriginalDescription) {
                    add(
                        PosterZoomOverlayAction(
                            Icons.Rounded.Description,
                            stringResource(Res.string.stream_parser_original_description),
                            onSelected = { onShowOriginalDescription(stream) },
                        ),
                    )
                }
                if (stream.hasLinkActions) {
                    add(PosterZoomOverlayAction(Icons.Rounded.Download, stringResource(Res.string.streams_download_file), onSelected = { onDownload(stream) }))
                    add(PosterZoomOverlayAction(Icons.Rounded.ContentCopy, stringResource(Res.string.streams_copy_link), onSelected = { onCopyLink(stream) }))
                }
                if (castAvailable && stream.canCast) {
                    add(PosterZoomOverlayAction(Icons.Rounded.Cast, stringResource(Res.string.cast_stream_action), onSelected = { onCast(stream) }))
                }
                if (privatePlaybackAvailable) {
                    add(PosterZoomOverlayAction(Icons.Rounded.VisibilityOff, privatePlaybackTitle, onSelected = { onPlayPrivately(stream) }))
                }
            },
            onDismiss = onDismiss,
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stream.streamLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                stream.streamSubtitle
                    ?.takeIf { it.isNotBlank() }
                    ?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }

            val dismissAfter: (() -> Unit) -> Unit = { action ->
                // Dialogs and the device picker live outside the sheet, so they can open right away.
                action()
                coroutineScope.launch {
                    dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                }
            }
            val castIssues = remember(stream) { stream.castCompatibilityIssues() }
            val castSubtitle = castIssues.takeIf { it.isNotEmpty() }?.let {
                stringResource(Res.string.cast_compatibility_warning, it.joinToString(", "))
            }
            val actions = buildList {
                if (showOriginalDescription) {
                    add(StreamMenuAction(Icons.Rounded.Description, stringResource(Res.string.stream_parser_original_description)) {
                        onShowOriginalDescription(stream)
                    })
                }
                if (stream.hasLinkActions) {
                    add(StreamMenuAction(Icons.Rounded.ContentCopy, stringResource(Res.string.streams_copy_link)) {
                        onCopyLink(stream)
                    })
                    add(
                        StreamMenuAction(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            stringResource(
                                if (externalPlayerEnabled) {
                                    Res.string.streams_open_internal_player
                                } else {
                                    Res.string.streams_open_external_player
                                },
                            ),
                        ) { onOpen(stream, !externalPlayerEnabled) },
                    )
                    add(StreamMenuAction(Icons.Rounded.Download, stringResource(Res.string.streams_download_file)) {
                        onDownload(stream)
                    })
                }
                if (castAvailable && stream.canCast) {
                    add(StreamMenuAction(Icons.Rounded.Cast, stringResource(Res.string.cast_stream_action), castSubtitle) {
                        onCast(stream)
                    })
                }
                if (privatePlaybackAvailable) {
                    add(
                        StreamMenuAction(
                            Icons.Rounded.VisibilityOff,
                            privatePlaybackTitle,
                            stringResource(
                                if (isEpisode) {
                                    Res.string.private_viewing_play_action_episode_description
                                } else {
                                    Res.string.private_viewing_play_action_movie_description
                                },
                            ),
                        ) { onPlayPrivately(stream) },
                    )
                }
            }
            NuvioActionMenuGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) NuvioActionMenuDivider()
                    NuvioActionMenuRow(
                        title = action.title,
                        icon = action.icon,
                        subtitle = action.subtitle,
                        onClick = { dismissAfter(action.onClick) },
                    )
                }
            }
        }
    }
}

private class StreamMenuAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

private fun Long.toPlaybackClock(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        buildString {
            append(hours)
            append(':')
            append(minutes.toString().padStart(2, '0'))
            append(':')
            append(seconds.toString().padStart(2, '0'))
        }
    } else {
        buildString {
            append(minutes)
            append(':')
            append(seconds.toString().padStart(2, '0'))
        }
    }
}

// ---------------------------------------------------------------------------
// State blocks
// ---------------------------------------------------------------------------

@Composable
private fun LoadingStateBlock(
    findingStreamsText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = findingStreamsText,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shimmer(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        repeat(4) {
            StreamCardSkeleton()
        }
    }
}

@Composable
private fun EmptyStateBlock(
    reason: StreamsEmptyStateReason?,
    modifier: Modifier = Modifier,
) {
    val title: String
    val message: String

    when (reason) {
        StreamsEmptyStateReason.NoAddonsInstalled,
        StreamsEmptyStateReason.NoCompatibleAddons -> {
            title = stringResource(Res.string.playback_unavailable)
            message = stringResource(Res.string.playback_unavailable_message)
        }

        StreamsEmptyStateReason.StreamFetchFailed -> {
            title = stringResource(Res.string.streams_empty_load_failed_title)
            message = stringResource(Res.string.streams_empty_load_failed_message)
        }

        StreamsEmptyStateReason.NoStreamsFound, null -> {
            title = stringResource(Res.string.compose_player_no_streams_found)
            message = stringResource(Res.string.streams_empty_no_streams_message)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FooterLoadingBlock(
    checkingMoreAddonsText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = checkingMoreAddonsText,
            modifier = Modifier.shimmer(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
