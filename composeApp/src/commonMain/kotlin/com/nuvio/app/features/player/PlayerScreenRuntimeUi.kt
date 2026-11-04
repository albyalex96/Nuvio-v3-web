package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.TimestampProviderSettingsRepository
import com.nuvio.app.features.player.skip.TimestampProviders
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.cast.CastDevicePickerDialog
import com.nuvio.app.features.cast.CastPlatform
import com.nuvio.app.features.cast.CastPlayerSurface
import com.nuvio.app.features.cast.CastingBackdrop
import com.nuvio.app.features.cast.isAvailable
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.p2p.formatP2pMegabytes
import com.nuvio.app.features.p2p.formatP2pSpeed
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.servers.ServerStreams
import com.nuvio.app.features.streams.streamAddonInstanceId
import com.nuvio.app.isIos
import kotlin.math.ceil
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.currentProgram
import com.nuvio.app.features.livetv.rememberLiveTvEpgNowMillis
import com.nuvio.app.features.streams.parser.StreamParserSettingsRepository
import nuvio.composeapp.generated.resources.*

@Composable
internal fun PlayerScreenRuntime.RenderPlayerRuntimeUi() {
    val runtime = this
    val isInPip = rememberIsInPictureInPicture()
    val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
    val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null
    val currentGestureFeedback = liveGestureFeedback ?: gestureFeedback
    val isP2pPlaybackActive = activeTorrentInfoHash != null
    val p2pConnecting = p2pStreamingState as? P2pStreamingState.Connecting
    val p2pStats = p2pStreamingState as? P2pStreamingState.Streaming
    val p2pPeerInfo = p2pStats?.let { stats ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            stats.seeds,
            stats.peers,
        )
    }
    val p2pDownloadSpeed = p2pStats?.let { formatP2pSpeed(it.downloadSpeed) }
    val p2pLoadingBytes = p2pStats?.let { maxOf(it.downloadedBytes, it.deliveredBytes) } ?: 0L
    val connectingPeerInfo = p2pConnecting?.let { state ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            state.seeds,
            state.peers,
        )
    }
    val p2pInitialLoadingMessage = when {
        !isP2pPlaybackActive || initialLoadCompleted -> null
        p2pConnecting != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                p2pConnectingPhaseLabel(p2pConnecting.phase)
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_connecting_status,
                    p2pConnectingPhaseLabel(p2pConnecting.phase),
                    connectingPeerInfo.orEmpty(),
                    formatP2pSpeed(p2pConnecting.downloadSpeed),
                )
            }
        }
        p2pStats != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                null
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_loading_status,
                    formatP2pMegabytes(p2pLoadingBytes),
                    p2pPeerInfo.orEmpty(),
                    p2pDownloadSpeed.orEmpty(),
                )
            }
        }
        else -> org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
        )
    }
    val bufferedAheadMs = (playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs)
        .coerceAtLeast(0L)
    val p2pInitialLoadingProgress = when {
        !isP2pPlaybackActive || initialLoadCompleted || p2pStats == null -> null
        else -> p2pInitialLoadingProgress(
            bufferedAheadMs = bufferedAheadMs,
            downloadedBytes = p2pStats.downloadedBytes,
            deliveredBytes = p2pStats.deliveredBytes,
        )
    }
    val showP2pRebufferStats = isP2pPlaybackActive &&
        initialLoadCompleted &&
        playbackSnapshot.isLoading &&
        p2pStats != null &&
        !p2pSettingsUiState.hideTorrentStats
    val p2pRebufferMessage = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000L)
                .coerceAtLeast(0L)
            "${bufferedSeconds}s buffered · ${p2pPeerInfo.orEmpty()} · ${p2pDownloadSpeed.orEmpty()}"
        }
    }
    val p2pRebufferProgress = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000f)
                .coerceAtLeast(0f)
            (bufferedSeconds / 10f).coerceIn(0f, 1f)
        }
    }
    val gestureCallbacks = rememberSurfaceGestureCallbacks()
    val playbackGesturesEnabled = initialLoadCompleted && errorMessage == null

    BindAutoSyncRuntimeEffects() // AutoSync hook
    BindLiveTvStillWatchingTimer()
    BindAudioDelay()
    BindPrivateViewingNotice()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
            .onPlayerMouseMove { revealControlsForMouse() }
            .then(
                hiddenPlayerPointerIcon
                    ?.takeIf { !controlsVisible && playbackSnapshot.isPlaying }
                    ?.let { Modifier.pointerHoverIcon(it) }
                    ?: Modifier,
            )
            .playerSurfaceTapGestures(
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                onSurfaceTap = gestureCallbacks.onSurfaceTap,
                onSurfaceDoubleTap = gestureCallbacks.onSurfaceDoubleTap,
                activateHoldToSpeedState = gestureCallbacks.activateHoldToSpeed,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
            )
            .playerSurfaceDragGestures(
                gestureController = gestureController,
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                sideGestureSystemEdgeExclusionPx = sideGestureSystemEdgeExclusionPx,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                touchGesturesEnabledState = gestureCallbacks.touchGesturesEnabled,
                isHoldToSpeedGestureActiveState = gestureCallbacks.isHoldToSpeedGestureActive,
                currentPositionMsState = gestureCallbacks.currentPositionMs,
                currentDurationMsState = gestureCallbacks.currentDurationMs,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                showHorizontalSeekPreviewState = gestureCallbacks.showHorizontalSeekPreview,
                showBrightnessFeedbackState = gestureCallbacks.showBrightnessFeedback,
                showVolumeFeedbackState = gestureCallbacks.showVolumeFeedback,
                clearLiveGestureFeedbackState = gestureCallbacks.clearLiveGestureFeedback,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
                commitHorizontalSeekState = gestureCallbacks.commitHorizontalSeek,
            ),
    ) {
        val playerSurfaceSourceUrl = runtime.playerSurfaceSourceUrl
        val playbackKey = activePlaybackKey
        val initialPositionRequestKey = currentInitialPositionRequestKey()
        if (playerSurfaceSourceUrl != null) {
            key(playbackKey) {
                val active = remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    onDispose { active.value = false }
                }
                val onInitialPositionHandled: (String, Boolean) -> Unit = { key, handled ->
                    if (active.value && playbackKey == activePlaybackKey && key == currentInitialPositionRequestKey()) {
                        initialSeekApplied = handled
                    }
                }
                val onControllerReady: (PlayerEngineController) -> Unit = { controller ->
                    if (active.value && playbackKey == activePlaybackKey) {
                        playerController = controller
                        playerControllerSourceUrl = activeSourceUrl
                    }
                }
                val onSnapshot: (PlayerPlaybackSnapshot) -> Unit = onSnapshot@{ snapshot ->
                    if (!active.value || !updatePlaybackSnapshot(snapshot, playbackKey)) return@onSnapshot
                    refreshAudioTracksIfChanged()
                    if (!snapshot.isLoading) initialLoadCompleted = true
                    if (snapshot.isEnded) {
                        shouldPlay = false
                        controlsVisible = !playerControlsLocked
                    }
                }
                val onError: (String?) -> Unit = onError@{ message ->
                    if (!active.value || playbackKey != activePlaybackKey) return@onError
                    // A receiver failure says nothing about the link (usually format/codec), so the
                    // credential refresh and the reuse cache only react to local playback errors.
                    val castError = castSessionId != null
                    if (message != null && !castError && tryRefreshCredentialedSourceAfterError(message)) {
                        return@onError
                    }
                    errorMessage = message
                    if (message != null) {
                        scrubbingPositionMs = null
                        controlsVisible = !playerControlsLocked
                        if (!castError) removeFailedStreamFromCache()
                    }
                }
                if (castSessionId != null) {
                    CastingBackdrop(
                        artworkUrl = castBackdropUrl,
                        deviceName = castDeviceName,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CastPlayerSurface(
                        request = castMediaRequest(playerSurfaceSourceUrl),
                        playWhenReady = shouldPlay,
                        initialPositionMs = activeInitialPositionMs.takeIf { it > 0L },
                        initialPositionRequestKey = initialPositionRequestKey,
                        onInitialPositionHandled = onInitialPositionHandled,
                        onControllerReady = onControllerReady,
                        onSnapshot = onSnapshot,
                        onError = onError,
                    )
                } else {
                    PlatformPlayerSurface(
                        sourceUrl = playerSurfaceSourceUrl,
                        sourceAudioUrl = activeSourceAudioUrl,
                        sourceHeaders = activeSourceHeaders,
                        sourceResponseHeaders = activeSourceResponseHeaders,
                        externalSubtitles = externalSubtitles,
                        streamType = activeStreamType,
                        modifier = Modifier.fillMaxSize(),
                        playWhenReady = shouldPlay,
                        initialPositionMs = activeInitialPositionMs.takeIf { it > 0L },
                        initialPositionRequestKey = initialPositionRequestKey,
                        resizeMode = resizeMode,
                        playbackEngine = playbackEngineOverride,
                        onInitialPositionHandled = onInitialPositionHandled,
                        onControllerReady = onControllerReady,
                        onSnapshot = onSnapshot,
                        onError = onError,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerSettingsUiState.pauseOverlayEnabled && pausedOverlayVisible && !controlsVisible && !playerControlsLocked,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        ) {
            PauseMetadataOverlay(
                title = if (isLiveTvPlayback) activeStreamTitle else title,
                logo = if (isLiveTvPlayback) activeLogo else logo,
                isEpisode = isEpisode,
                seasonNumber = activeSeasonNumber,
                episodeNumber = activeEpisodeNumber,
                episodeTitle = activeEpisodeTitle,
                pauseDescription = activePauseDescription ?: activeStreamSubtitle,
                providerName = activeProviderName,
                metrics = metrics,
                horizontalSafePadding = horizontalSafePadding,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CompositionLocalProvider(LocalSeekThumbnails provides rememberSeekThumbnailUi()) {
            RenderPlayerControls(displayedPositionMs = displayedPositionMs, isEpisode = isEpisode)
        }
        RenderPlaybackOverlays(
            runtime = runtime,
            displayedPositionMs = displayedPositionMs,
            currentGestureFeedback = currentGestureFeedback,
            p2pInitialLoadingMessage = p2pInitialLoadingMessage,
            p2pInitialLoadingProgress = p2pInitialLoadingProgress,
            showP2pRebufferStats = showP2pRebufferStats,
            p2pRebufferMessage = p2pRebufferMessage,
            p2pRebufferProgress = p2pRebufferProgress,
        )
        RenderPlayerModals(displayedPositionMs = displayedPositionMs)
        RenderParty(runtime = runtime)
        RenderStillWatchingPrompt()
        if (showCastPicker) {
            CastDevicePickerDialog(onDismiss = { showCastPicker = false })
        }
    }
}

@Composable
private fun PlayerScreenRuntime.RenderStillWatchingPrompt() {
    if (!showStillWatchingPrompt) return

    // Drains from 1 to 0; the player closes when it runs out without a choice.
    val countdown = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        countdown.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = STILL_WATCHING_AUTO_EXIT_SECONDS * 1_000,
                easing = LinearEasing,
            ),
        )
        exitAfterStillWatchingPrompt()
    }
    val remainingSeconds = ceil(countdown.value * STILL_WATCHING_AUTO_EXIT_SECONDS).toInt()

    StillWatchingDialog(
        countdownProgress = countdown.value,
        remainingSeconds = remainingSeconds,
        isLiveTv = isLiveTvPlayback,
        consecutiveEpisodes = consecutiveEpisodesPlayed,
        liveTvMinutes = playerSettingsUiState.stillWatchingLiveTvMinutes,
        nextEpisode = nextEpisodeInfo,
        blurNextEpisodeThumbnail = metaScreenSettingsUiState.blurUnwatchedEpisodes && nextEpisodeInfo?.isWatched == false,
        onContinue = { continueAfterStillWatchingPrompt() },
        onExit = { exitAfterStillWatchingPrompt() },
    )
}

@Composable
private fun p2pConnectingPhaseLabel(phase: String): String = when (phase) {
    "add_magnet" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_fetching_metadata,
    )
    "prepare_stream", "attach_route" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_preparing_stream,
    )
    else -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
    )
}

private val PlayerScreenRuntime.activeAddonLogo: String?
    get() = addonsUiState.addons.firstNotNullOfOrNull { addon ->
        addon.manifest?.takeIf { addon.streamAddonInstanceId(it.id) == activeProviderAddonId }?.logoUrl
    }

private fun PlayerScreenRuntime.currentInitialPositionRequestKey(): String? {
    val positionMs = activeInitialPositionMs.takeIf { it > 0L } ?: return null
    return "$activePlaybackIdentity:${activeVideoId.orEmpty()}:$positionMs"
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerControls(displayedPositionMs: Long, isEpisode: Boolean) {
    val isInPip = rememberIsInPictureInPicture()
    // For live TV the provider line also shows the programme currently on air (from the EPG).
    val liveTvNowProgramTitle = if (isLiveTvPlayback) {
        LaunchedEffect(Unit) { LiveTvRepository.ensureChannelsLoaded() }
        val liveTvEpgNowMillis = rememberLiveTvEpgNowMillis()
        val liveTvState by LiveTvRepository.uiState.collectAsStateWithLifecycle()
        activeVideoId?.let { channelId -> liveTvState.epgPrograms[channelId] }
            ?.currentProgram(liveTvEpgNowMillis)
            ?.title
    } else {
        null
    }
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val partyActive = partyState.party != null
    val partyAvailable by PartyRepository.available.collectAsStateWithLifecycle()
    val castState by CastPlatform.state.collectAsStateWithLifecycle()
    remember { TimestampProviderSettingsRepository.ensureLoaded() }
    val timestampProviderSettings by TimestampProviderSettingsRepository.uiState.collectAsStateWithLifecycle()
    val canSubmitTimestamps = remember(playerSettingsUiState, timestampProviderSettings) {
        TimestampProviders.activeSubmitTargets(timestampProviderSettings).isNotEmpty()
    }
    val streamParserSettings by remember {
        StreamParserSettingsRepository.ensureLoaded()
        StreamParserSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    // Stream Parser badges of the playing stream under the title (not for live TV channels).
    val showStreamBadges = streamParserSettings.enabled && streamParserSettings.playerBadgesEnabled && !isLiveTvPlayback
    // Parsed also without the badges: the "Via" line shows the stream source (as in the stream card).
    val parsedStreamInfo = if (!isLiveTvPlayback) {
        remember(activeStream, activeStreamTitle, activeStreamSubtitle, activeSourceFilename, title) {
            parsePlayerStream(
                stream = activeStream,
                streamTitle = activeStreamTitle,
                streamSubtitle = activeStreamSubtitle,
                filename = activeSourceFilename,
                providerName = activeProviderName,
                providerAddonId = activeProviderAddonId,
                knownTitle = title,
            )
        }
    } else {
        null
    }
    val streamBadgeInfo = parsedStreamInfo?.takeIf { showStreamBadges }
    AnimatedVisibility(
        visible = (controlsVisible || showParentalGuide) && !playerControlsLocked && !isInPip,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        PlayerControlsShell(
            title = title,
            // Live TV keeps the channel name: its logo is the channel's, not a title logo.
            logo = if (isLiveTvPlayback) null else logo,
            clock = playerSettingsUiState.playerClock.takeIf { it.enabled },
            streamBadges = streamBadgeInfo?.let { info ->
                PlayerStreamBadges(info = info, animationsEnabled = streamParserSettings.animationsEnabled)
            },
            streamTitle = activeStreamTitle,
            streamSourceName = parsedStreamInfo?.sourceLabel,
            providerName = liveTvNowProgramTitle?.let { programTitle -> "$activeProviderName · $programTitle" }
                ?: activeProviderName,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            resizeMode = resizeMode,
            isLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            showRemainingTime = showRemainingTime,
            onRuntimeClick = { showRemainingTime = !showRemainingTime },
            releaseInfo = metaUiState.meta?.takeIf { it.id == parentMetaId }?.releaseInfo,
            hideDetails = activeSkipInterval != null && !skipIntervalDismissed,
            onNextEpisodeClick = if (nextEpisodeInfo?.hasAired == true && !nextEpisodeAutoPlaySearching && nextEpisodeAutoPlayCountdown == null) {
                {
                    if (!partyBlocksControl()) playNextEpisode()
                }
            } else null,
            onInteraction = { controlsActivityTick += 1 },
            showPlaybackControls = controlsVisible,
            onLockToggle = {
                if (playerControlsLocked) unlockPlayerControls() else lockPlayerControls()
            },
            onBack = {
                flushWatchProgress()
                args.onBack()
            },
            // Live TV channels have no shared timeline, so they cannot host a watch party.
            onPartyClick = if (partyAvailable && !isLiveTvPlayback) {
                { openPartyPanel() }
            } else {
                null
            },
            partyActive = partyActive,
            onCastClick = if (castState.isAvailable && !TvAppRuntime.isTvApp) {
                { showCastPicker = true }
            } else {
                null
            },
            castActive = castSessionId != null,
            onTogglePlayback = { togglePlayback() },
            seekIntervalSeconds = playerSettingsUiState.seekIntervalSeconds,
            onSeekBack = { seekBy(-playerSettingsUiState.seekIntervalSeconds * 1_000L) },
            onSeekForward = { seekBy(playerSettingsUiState.seekIntervalSeconds * 1_000L) },
            onResizeModeClick = { cycleResizeMode() },
            onSpeedClick = { cyclePlaybackSpeed() },
            onSubtitleClick = {
                refreshTracks()
                showSubtitleModal = true
            },
            onAudioClick = {
                refreshTracks()
                showAudioModal = true
            },
            onVideoSettingsClick = if (isIos) {
                {
                    showVideoSettingsModal = true
                    controlsVisible = true
                }
            } else {
                null
            },
            onSourcesClick = if (!isLiveTvPlayback && activeVideoId != null) { { openSourcesPanel() } } else null,
            onEpisodesClick = if (isSeries) { { openEpisodesPanel() } } else null,
            onChaptersClick = if (playbackSnapshot.chapters.isNotEmpty()) { { openChaptersPanel() } } else null,
            onLiveChannelsClick = if (isLiveTvPlayback) {
                {
                    showLiveChannelsPanel = true
                    controlsVisible = false
                }
            } else null,
            onOpenInExternalPlayer = args.onOpenInExternalPlayer
                ?.takeUnless { ServerStreams.isServerSourceId(activeProviderAddonId) }
                ?.let { openExternal ->
                {
                    val loadedSubtitles = addonSubtitles
                        .takeIf { it.isNotEmpty() }
                        ?.map { sub ->
                            SubtitleInput(
                                url = sub.url,
                                name = buildString {
                                    if (!sub.addonName.isNullOrBlank()) append("[${sub.addonName}] ")
                                    append(sub.display)
                                },
                                lang = sub.language,
                            )
                        }
                    PlayerStreamsRepository.pauseSearchForPlayback()
                    openExternal(
                        ExternalPlayerPlaybackRequest(
                            sourceUrl = activeSourceUrl,
                            title = title,
                            streamTitle = activeStreamTitle,
                            sourceHeaders = activeSourceHeaders,
                            resumePositionMs = playbackSnapshot.positionMs,
                            durationMs = playbackSnapshot.durationMs.takeIf { it > 0L },
                            playbackSession = playbackSession,
                            subtitles = loadedSubtitles,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                            episodeTitle = activeEpisodeTitle,
                        ),
                    )
                }
            },
            onSubmitIntroClick = if (
                isSeries && canSubmitTimestamps
            ) {
                { showSubmitIntroModal = true }
            } else {
                null
            },
            onSwitchEngineClick = if (playerController?.playbackEngine != null) {
                { switchPlaybackEngine() }
            } else {
                null
            },
            onStreamInfoClick = { openStreamInfo() },
            parentalWarnings = parentalWarnings,
            showParentalGuide = showParentalGuide,
            onParentalGuideAnimationComplete = { showParentalGuide = false },
            onScrubChange = { positionMs ->
                isScrubbingTimeline = true
                scrubbingPositionMs = positionMs
                seekThumbnailProvider?.request(positionMs)
            },
            onScrubFinished = { positionMs ->
                val targetMs = if (isPartyControlLocked) ownSeekTarget(positionMs) else positionMs
                if (targetMs == null) {
                    isScrubbingTimeline = false
                    scrubbingPositionMs = null
                } else {
                    finishTimelineScrub(targetMs)
                    playerController?.seekTo(targetMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = targetMs)
                }
            },
            horizontalSafePadding = horizontalSafePadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.RenderPlaybackOverlays(
    runtime: PlayerScreenRuntime,
    displayedPositionMs: Long,
    currentGestureFeedback: GestureFeedbackState?,
    p2pInitialLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
) {
    runtime.run {
        PlayerPlaybackOverlays(
            playerControlsLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            lockedOverlayVisible = lockedOverlayVisible,
            showRemainingTime = showRemainingTime,
            playbackSnapshot = playbackSnapshot,
        displayedPositionMs = displayedPositionMs,
        metrics = metrics,
        horizontalSafePadding = horizontalSafePadding,
        onUnlock = { unlockPlayerControls() },
        showOpeningOverlay = playerSettingsUiState.showLoadingOverlay && !initialLoadCompleted && errorMessage == null,
        backdropArtwork = if (isLiveTvPlayback) activeLogo?.takeIf(String::isNotBlank) ?: background ?: poster else background ?: poster,
        logo = if (isLiveTvPlayback) activeLogo else logo,
        title = if (isLiveTvPlayback) activeStreamTitle else title,
        onBackWithProgress = {
            flushWatchProgress()
            args.onBack()
        },
        openingLoadingMessage = if (playerSettingsUiState.showPlayerLoadingStatus) {
            p2pInitialLoadingMessage ?: playerLoadingStatusMessage(
                showStatus = true,
                controllerReady = playerController != null,
                buffering = playbackSnapshot.isLoading,
            )
        } else null,
        p2pInitialLoadingProgress = p2pInitialLoadingProgress,
        showP2pRebufferStats = showP2pRebufferStats,
        p2pRebufferMessage = p2pRebufferMessage,
        p2pRebufferProgress = p2pRebufferProgress,
        currentGestureFeedback = currentGestureFeedback,
        renderedGestureFeedback = renderedGestureFeedback,
        initialLoadCompleted = initialLoadCompleted,
        pausedOverlayVisible = pausedOverlayVisible,
        activeSkipInterval = activeSkipInterval,
        skipsToPostCredits = activeSkipInterval?.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.skipsToPostCredits == true,
        skipIntervalDismissed = skipIntervalDismissed,
        controlsVisible = controlsVisible,
        onSkipInterval = { interval ->
            interval.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.let { action ->
                val durationMs = playbackSnapshot.durationMs
                val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                val seekMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) else requestedMs
                if (seekMs != null) {
                    playerController?.seekTo(seekMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = seekMs)
                    skipIntervalDismissed = true
                }
            }
        },
        onDismissSkipInterval = { skipIntervalDismissed = true },
        onSkipPromptVisibleChange = { skipPromptOnScreen = it },
        sliderEdgePadding = sliderEdgePadding,
        overlayBottomPadding = overlayBottomPadding,
        isSeries = isSeries,
        nextEpisodeInfo = nextEpisodeInfo,
        showNextEpisodeCard = showNextEpisodeCard,
        nextEpisodeAutoPlaySearching = nextEpisodeAutoPlaySearching,
        nextEpisodeAutoPlaySourceName = nextEpisodeAutoPlaySourceName,
        nextEpisodeAutoPlayCountdown = nextEpisodeAutoPlayCountdown,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        onPlayNextEpisode = {
            if (!partyBlocksControl()) playNextEpisode()
        },
        onDismissNextEpisode = {
            cancelNextEpisodeAutoPlay()
            nextEpisodeCardDismissed = true
            showNextEpisodeCard = false
        },
        errorMessage = errorMessage,
            onDismissError = {
                flushWatchProgress()
                args.onBack()
            },
        )
    }
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerModals(displayedPositionMs: Long) {
    PlayerScreenModalHosts(
        pendingP2pSwitch = pendingP2pSwitch,
        onPendingP2pSwitchChanged = { pendingP2pSwitch = it },
        onP2pEpisodeStreamSelected = { stream, episode, isAutoPlay ->
            switchToP2pEpisodeStream(stream, episode, isAutoPlay)
        },
        onP2pSourceStreamSelected = { stream -> switchToP2pSourceStream(stream) },
        onNextEpisodeAutoPlaySearchingChanged = { nextEpisodeAutoPlaySearching = it },
        onNextEpisodeAutoPlayCountdownChanged = { nextEpisodeAutoPlayCountdown = it },
        onNextEpisodeAutoPlaySourceNameChanged = { nextEpisodeAutoPlaySourceName = it },
        showAudioModal = showAudioModal,
        audioTracks = serverAudioTracks.ifEmpty { audioTracks },
        selectedAudioIndex = serverAudioTracks.firstOrNull { it.isSelected }?.index ?: selectedAudioIndex,
        onAudioTrackSelected = { index ->
            if (serverAudioTracks.isNotEmpty()) {
                selectServerAudioTrack(index)
            } else {
                selectedAudioIndex = index
                persistAudioPreference(audioTracks.firstOrNull { it.index == index })
                playerController?.selectAudioTrack(index)
            }
            scope.launch {
                kotlinx.coroutines.delay(200)
                showAudioModal = false
            }
        },
        onAudioModalDismissed = { showAudioModal = false },
        audioDelayMs = audioDelayParts.totalMs,
        streamAudioDelayMs = audioDelayParts.streamMs,
        audioDelaySavedForDeviceLabel = audioOutputRoute?.label?.takeIf {
            playerSettingsUiState.rememberAudioDelayPerDevice
        },
        onAudioDelayChanged = { delayMs -> setAudioDelay(delayMs) },
        showSubtitleModal = showSubtitleModal,
        subtitleTracks = serverSubtitleTracks.ifEmpty { subtitleTracks },
        selectedSubtitleIndex = if (serverSubtitleTracks.isEmpty()) {
            selectedSubtitleIndex
        } else {
            serverSubtitleTracks.firstOrNull { it.isSelected }?.index ?: -1
        },
        addonSubtitles = visibleAddonSubtitles,
        selectedAddonSubtitleId = selectedAddonSubtitleId,
        isLoadingAddonSubtitles = isLoadingAddonSubtitles,
        subtitleStyle = subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        selectedAddonSubtitle = selectedAddonSubtitle,
        subtitleAutoSyncState = subtitleAutoSyncState,
        onBuiltInSubtitleTrackSelected = { index -> selectBuiltInSubtitleTrack(index) },
        onAddonSubtitleSelected = { addon -> selectAddonSubtitle(addon) },
        onFetchAddonSubtitles = { fetchAddonSubtitlesForActiveItem() },
        onSubtitleStyleChanged = PlayerSettingsRepository::setSubtitleStyle,
        onSubtitleDelayChanged = { delayMs -> setSubtitleDelay(delayMs) },
        onSubtitleDelayReset = { setSubtitleDelay(0) },
        onAutoSyncCapture = { captureSubtitleAutoSyncTime() },
        onAutoSyncCueSelected = { cue -> applySubtitleAutoSyncCue(cue) },
        onAutoSyncReload = { loadSubtitleAutoSyncCues(force = true) },
        onSubtitleModalDismissed = { showSubtitleModal = false },
        showVideoSettingsModal = showVideoSettingsModal,
        playerSettings = playerSettingsUiState,
        onVideoSettingsChanged = {
            playerController?.configureIosVideoOutput(PlayerSettingsRepository.uiState.value)
        },
        onVideoSettingsModalDismissed = { showVideoSettingsModal = false },
        showSourcesPanel = showSourcesPanel,
        sourceStreamsState = sourceStreamsState,
        contentTitle = title,
        activeEpisodeTitle = activeEpisodeTitle,
        activeSourceUrl = activeSourceUrl,
        activeStreamTitle = activeStreamTitle,
        onSourceFilterSelected = PlayerStreamsRepository::selectSourceFilter,
        onSourceStreamSelected = { stream -> switchToSource(stream) },
        onReloadSources = {
            val vid = activeVideoId
            if (vid != null) {
                PlayerStreamsRepository.loadSources(
                    type = contentType ?: parentMetaType,
                    videoId = vid,
                    season = activeSeasonNumber,
                    episode = activeEpisodeNumber,
                    forceRefresh = true,
                )
            }
        },
        onSourcesPanelDismissed = {
            showSourcesPanel = false
            PlayerStreamsRepository.stopSourcesLoading()
            controlsVisible = true
        },
        isSeries = isSeries,
        showEpisodesPanel = showEpisodesPanel,
        allEpisodes = playerMetaVideos,
        parentMetaType = parentMetaType,
        parentMetaId = parentMetaId,
        activeSeasonNumber = activeSeasonNumber,
        activeEpisodeNumber = activeEpisodeNumber,
        watchProgressByVideoId = watchProgressUiState.byVideoIdForContent(parentMetaId),
        watchedKeys = watchedUiState.watchedKeys,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        episodeStreamsPanelState = episodeStreamsPanelState,
        episodeStreamsRepoState = episodeStreamsRepoState,
        onEpisodeSelectedForDownload = { episode ->
            selectDownloadedEpisodeForPlayback(
                parentMetaId = parentMetaId,
                episode = episode,
                onDownloadedEpisodeSelected = { item, video -> switchToDownloadedEpisode(item, video) },
            )
        },
        onEpisodeStreamsRequested = { episode ->
            PlayerStreamsRepository.loadEpisodeStreams(
                type = contentType ?: parentMetaType,
                videoId = episode.id,
                season = episode.season,
                episode = episode.episode,
            )
            episodeStreamsPanelState = EpisodeStreamsPanelState(showStreams = true, selectedEpisode = episode)
        },
        onEpisodeStreamFilterSelected = PlayerStreamsRepository::selectEpisodeStreamsFilter,
        onEpisodeStreamSelected = { stream, episode -> switchToEpisodeStream(stream, episode) },
        onBackToEpisodes = {
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
        },
        onReloadEpisodeStreams = {
            val episode = episodeStreamsPanelState.selectedEpisode
            if (episode != null) {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            }
        },
        onEpisodesPanelDismissed = {
            showEpisodesPanel = false
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
            controlsVisible = true
        },
        showSubmitIntroModal = showSubmitIntroModal,
        activeVideoId = activeVideoId,
        metaUiState = metaUiState,
        displayedPositionMs = displayedPositionMs,
        streamDurationMs = if (playbackSnapshotKey == activePlaybackKey) playbackSnapshot.durationMs else 0L,
        submitIntroSegmentType = submitIntroSegmentType,
        onSubmitIntroSegmentTypeChanged = { submitIntroSegmentType = it },
        submitIntroStartTimeStr = submitIntroStartTimeStr,
        onSubmitIntroStartTimeChanged = { submitIntroStartTimeStr = it },
        submitIntroEndTimeStr = submitIntroEndTimeStr,
        onSubmitIntroEndTimeChanged = { submitIntroEndTimeStr = it },
        onSubmitIntroDismissed = { showSubmitIntroModal = false },
        onSubmitIntroSuccess = {
            submitIntroStartTimeStr = "00:00"
            submitIntroEndTimeStr = "00:00"
            submitIntroSegmentType = "intro"
            showSubmitIntroModal = false
        },
    )
    StreamInfoOverlay(
        visible = showStreamInfo,
        addonName = activeProviderName,
        addonLogo = activeAddonLogo,
        streamName = activeStreamTitle,
        streamDescription = activeStreamSubtitle,
        playbackEngine = playerController?.playbackEngine,
        serverPlayback = serverPlaybackSummary(),
        mediaInfo = streamMediaInfo,
        audioTrack = audioTracks.firstOrNull { it.index == selectedAudioIndex },
        subtitleTrack = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }.takeIf { !useCustomSubtitles },
        addonSubtitle = selectedAddonSubtitle.takeIf { useCustomSubtitles },
        onDismiss = { showStreamInfo = false },
        scrollState = streamInfoScrollState,
    )

    PlayerChaptersPanel(
        visible = showChaptersPanel,
        chapters = playbackSnapshot.chapters,
        positionMs = displayedPositionMs,
        onChapterSelected = { chapter -> seekToChapter(chapter) },
        onDismiss = {
            showChaptersPanel = false
            controlsVisible = true
        },
    )

    if (isLiveTvPlayback) {
        val liveTvState by LiveTvRepository.uiState.collectAsStateWithLifecycle()
        PlayerLiveChannelsPanel(
            visible = showLiveChannelsPanel,
            channels = liveTvState.displayedChannels,
            currentStreamUrl = activeSourceUrl,
            onChannelSelected = { channel -> switchToLiveChannel(channel) },
            onDismiss = {
                showLiveChannelsPanel = false
                controlsVisible = true
            },
        )
    }
}
