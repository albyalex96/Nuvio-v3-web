package com.nuvio.app.tv

import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Info
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_action_random_episode
import nuvio.composeapp.generated.resources.tv_action_view_details
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.DeviceLinkAuthRepository
import com.nuvio.app.features.auth.ServerConnectionController
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.AppScreenTab
import com.nuvio.app.AppTabActions
import com.nuvio.app.AppTabHost
import com.nuvio.app.AppTabRequests
import com.nuvio.app.AppTabState
import com.nuvio.app.MainTabsDestination
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.groupedEpisodesForDisplay
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeHeroMetaRepository
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.canOpenCatalog
import com.nuvio.app.features.player.PlayerScreenRuntime
import com.nuvio.app.features.player.PlayerSeekDirection
import com.nuvio.app.features.player.handleDoubleTapSeek
import com.nuvio.app.features.player.playNextEpisode
import com.nuvio.app.features.player.scheduleProgressSyncAfterSeek
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.player.togglePlayback
import com.nuvio.app.features.player.isPartyControlLocked
import com.nuvio.app.features.player.ownSeekTarget
import com.nuvio.app.features.player.partyBlocksControl
import com.nuvio.app.features.player.phoneRemotePlayback
import com.nuvio.app.features.player.handlePhoneRemoteCommand
import com.nuvio.app.features.player.publishPartyPlayback
import com.nuvio.app.features.player.cycleResizeMode
import com.nuvio.app.features.player.openEpisodesPanel
import com.nuvio.app.features.player.refreshTracks
import com.nuvio.app.features.player.switchToLiveChannel
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem

private val log = Logger.withTag("TvShell")

/** Number of catalog items shown in a TV home row before "see all". */
private const val TV_HOME_ROW_LIMIT = 24

/** Share of the visible height scrolled by one D-pad press in the stream "Info" panel. */
private const val STREAM_INFO_SCROLL_FRACTION = 0.6f

@Composable
internal fun TvShellThemeOverlay(content: @Composable () -> Unit) {
    val shell = LocalTvShell.current
    if (shell == null) {
        content()
    } else {
        shell.themeOverlay(content)
    }
}

/**
 * Same contract as [MainTabsDestination]. With a [TvShell] the mobile navigation bars are
 * replaced by the TV chrome (sidebar) while the tab content stays the shared [AppTabHost].
 */
@Composable
internal fun ShellTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    showLiveTvInNavigation: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    val shell = LocalTvShell.current
    if (shell == null) {
        MainTabsDestination(
            selectedTab = selectedTab,
            initialHomeReady = initialHomeReady,
            rootRouteActive = rootRouteActive,
            useTabletFloatingTabBar = useTabletFloatingTabBar,
            useNativeNavigation = useNativeNavigation,
            useNativeTabBar = useNativeTabBar,
            liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
            liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
            showLiveTvInNavigation = showLiveTvInNavigation,
            requests = requests,
            state = state,
            actions = actions,
            onBack = onBack,
            onTabSelected = onTabSelected,
            onProfileSelected = onProfileSelected,
            onAddProfileRequested = onAddProfileRequested,
        )
        return
    }

    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)
    val tabActions = remember(actions) { actions(true) }
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val tabs = remember(showLiveTvInNavigation) {
        buildList {
            add(AppScreenTab.Home)
            add(AppScreenTab.Search)
            add(AppScreenTab.Library)
            if (showLiveTvInNavigation) add(AppScreenTab.LiveTv)
            add(AppScreenTab.Settings)
        }
    }
    val chrome = TvTabsChrome(
        selectedTab = selectedTab,
        tabs = tabs,
        activeProfile = profileState.activeProfile,
        contentVisible = initialHomeReady,
        onTabSelected = onTabSelected,
        onSwitchProfile = onAddProfileRequested,
    )
    shell.tabsChrome(chrome) {
        CompositionLocalProvider(
            LocalNuvioBottomNavigationOverlayPadding provides 0.dp,
            LocalNuvioNavBarScrollState provides rememberNuvioNavBarScrollState(),
        ) {
            Box(modifier = Modifier.fillMaxSize().alpha(if (initialHomeReady) 1f else 0f)) {
                AppTabHost(
                    selectedTab = selectedTab,
                    requests = requests,
                    state = state,
                    actions = tabActions,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// Shared no-op callbacks: a new `{}` per build would make every rebuilt model unequal.
private val NoOpPosterClick: (MetaPreview) -> Unit = {}
private val NoOpContinueWatchingClick: (ContinueWatchingItem) -> Unit = {}

internal fun buildTvHomeModel(
    homeUiState: HomeUiState,
    enabledHomeItems: List<HomeCatalogSettingsItem>,
    sectionsMap: Map<String, HomeCatalogSection>,
    collectionsMap: Map<String, Collection>,
    continueWatchingItems: List<ContinueWatchingItem>,
    upcomingItems: List<ContinueWatchingItem>,
    top10Titles: Map<com.nuvio.app.features.home.Top10Kind, String>,
    showContinueWatching: Boolean,
    isInitialLoading: Boolean,
    hasContentSources: Boolean,
    addonManifestErrorMessage: String?,
    isPosterWatched: (MetaPreview) -> Boolean,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)?,
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    onRetry: () -> Unit,
    heroItems: List<MetaPreview> = homeUiState.heroItems,
    showImdbRating: Boolean = true,
    showMdbListRatings: Boolean = false,
    isSaved: (MetaPreview) -> Boolean = { false },
    onToggleSaved: ((MetaPreview) -> Unit)? = null,
    onPlay: ((MetaPreview) -> Unit)? = null,
    onPlayLongClick: ((MetaPreview) -> Unit)? = null,
): TvHomeModel {
    val top10Rows = homeUiState.top10Rows.map { row ->
        TvHomeRow.Top10(key = row.key, title = top10Titles[row.kind].orEmpty(), items = row.items)
    }
    val rows = top10Rows + enabledHomeItems.mapNotNull { item ->
        if (item.isCollection) {
            collectionsMap[item.key]?.let { collection -> TvHomeRow.Folders(key = item.key, collection = collection) }
        } else {
            sectionsMap[item.key]
                ?.takeIf { it.items.isNotEmpty() }
                ?.let { section ->
                    TvHomeRow.Catalog(
                        key = item.key,
                        section = section,
                        items = section.items.take(TV_HOME_ROW_LIMIT),
                        canOpenCatalog = section.canOpenCatalog(TV_HOME_ROW_LIMIT),
                    )
                }
        }
    }.distinctBy(TvHomeRow::key)
    val visibleContinueWatching = if (showContinueWatching) continueWatchingItems else emptyList()
    val visibleUpcoming = if (showContinueWatching) upcomingItems else emptyList()
    val status = when {
        isInitialLoading -> TvHomeStatus.Loading
        !hasContentSources && rows.isEmpty() -> TvHomeStatus.NoSources
        rows.isEmpty() && homeUiState.heroItems.isEmpty() &&
            visibleContinueWatching.isEmpty() && visibleUpcoming.isEmpty() -> {
            if (homeUiState.errorMessage.isNullOrBlank() && addonManifestErrorMessage == null) {
                TvHomeStatus.Empty
            } else {
                TvHomeStatus.Error
            }
        }
        else -> TvHomeStatus.Content
    }
    return TvHomeModel(
        status = status,
        errorMessage = homeUiState.errorMessage ?: addonManifestErrorMessage,
        heroItems = heroItems,
        continueWatching = visibleContinueWatching,
        upcoming = visibleUpcoming,
        rows = rows,
        isPosterWatched = isPosterWatched,
        onPosterClick = onPosterClick ?: NoOpPosterClick,
        onPosterLongClick = onPosterLongClick,
        onContinueWatchingClick = onContinueWatchingClick ?: NoOpContinueWatchingClick,
        onContinueWatchingLongClick = onContinueWatchingLongPress,
        onCatalogClick = onCatalogClick,
        onFolderClick = onFolderClick,
        onRetry = onRetry,
        showImdbRating = showImdbRating,
        showMdbListRatings = showMdbListRatings,
        loadHeroDetails = LoadTvHeroDetails,
        isSaved = isSaved,
        onToggleSaved = onToggleSaved,
        onPlay = onPlay,
        onPlayLongClick = onPlayLongClick,
    )
}

// Shared instance, like the no-op callbacks: keeps rebuilt models equal.
private val LoadTvHeroDetails: suspend (MetaPreview) -> MetaPreview = { item -> HomeHeroMetaRepository.enrich(item) }

internal fun buildTvDetailsModel(
    meta: MetaDetails,
    hasEpisodes: Boolean,
    primaryActionLabel: String,
    primaryActionEnabled: Boolean,
    primaryActionSeason: Int?,
    primaryActionEpisode: Int?,
    primaryActionProgress: Float?,
    onPrimaryAction: () -> Unit,
    onPrimaryActionManual: (() -> Unit)?,
    episodeProgress: (MetaVideo) -> Float?,
    isEpisodeWatched: (MetaVideo) -> Boolean,
    onEpisodeClick: (MetaVideo) -> Unit,
    onEpisodeManualClick: ((MetaVideo) -> Unit)?,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    shuffleActive: Boolean,
    onShuffleClick: (() -> Unit)?,
    onOpenMeta: ((MetaPreview) -> Unit)?,
    onBack: () -> Unit,
    showExternalRatings: Boolean,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    onCastClick: ((com.nuvio.app.features.details.MetaPerson) -> Unit)?,
    onSeasonLongClick: ((Int) -> Unit)?,
): TvDetailsModel {
    val isSeries = meta.type == "series" || hasEpisodes
    val seasons = if (hasEpisodes) {
        meta.groupedEpisodesForDisplay()
            .entries
            .sortedBy { (season, _) -> if (season <= 0) Int.MAX_VALUE else season }
            .map { (season, episodes) -> TvSeason(number = season, episodes = episodes) }
    } else {
        emptyList()
    }
    return TvDetailsModel(
        meta = meta,
        isSeries = isSeries,
        primaryActionLabel = primaryActionLabel,
        primaryActionEnabled = primaryActionEnabled,
        primaryActionSeason = primaryActionSeason,
        primaryActionEpisode = primaryActionEpisode,
        primaryActionProgress = primaryActionProgress,
        onPrimaryAction = onPrimaryAction,
        onPrimaryActionManual = onPrimaryActionManual,
        seasons = seasons,
        episodeProgress = episodeProgress,
        isEpisodeWatched = isEpisodeWatched,
        onEpisodeClick = onEpisodeClick,
        onEpisodeManualClick = onEpisodeManualClick,
        isSaved = isSaved,
        onToggleSaved = onToggleSaved,
        isWatched = isWatched,
        onToggleWatched = onToggleWatched,
        shuffleActive = shuffleActive,
        onShuffleClick = onShuffleClick,
        onOpenMeta = onOpenMeta ?: {},
        onBack = onBack,
        showExternalRatings = showExternalRatings,
        episodeRatings = episodeRatings,
        onCastClick = onCastClick,
        onSeasonLongClick = onSeasonLongClick,
    )
}

/**
 * Season selector of the detail page: posters or text pills. Same per-device choice as the
 * phone's toggle (`SeasonViewModeStorage`), posters by default.
 */
object TvSeasonViewMode {
    fun postersEnabled(): Boolean =
        (com.nuvio.app.features.details.SeasonViewModeStorage.load()
            ?: com.nuvio.app.features.details.SeasonViewMode.Posters) == com.nuvio.app.features.details.SeasonViewMode.Posters

    fun setPostersEnabled(enabled: Boolean) {
        com.nuvio.app.features.details.SeasonViewModeStorage.save(
            if (enabled) com.nuvio.app.features.details.SeasonViewMode.Posters else com.nuvio.app.features.details.SeasonViewMode.Text,
        )
    }
}

/** MDBList ratings row of the detail page (same logos and provider order as the phone app). */
@Composable
fun TvExternalRatingsRow(
    ratings: List<com.nuvio.app.features.details.MetaExternalRating>,
    modifier: Modifier = Modifier,
) {
    com.nuvio.app.features.details.components.DetailRatingsRow(ratings = ratings, modifier = modifier)
}

/**
 * TV sign-in: renders the QR / device-link screen instead of the shared email form.
 * Returns false when the shared [com.nuvio.app.features.auth.AuthScreen] must be drawn
 * (no TV shell, or the user chose email sign-in; Back returns to the QR screen).
 */
@Composable
internal fun TvAuthHook(): Boolean {
    val auth = LocalTvShell.current?.auth ?: return false
    var useEmail by rememberSaveable { mutableStateOf(false) }
    if (useEmail) {
        PlatformBackHandler(enabled = true) {
            AuthRepository.clearError()
            useEmail = false
        }
        return false
    }
    val deviceLinkState by DeviceLinkAuthRepository.state.collectAsStateWithLifecycle()
    val serverState by ServerConnectionController.state.collectAsStateWithLifecycle()
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val activeServer = serverState.activeServer
    DisposableEffect(Unit) {
        onDispose(DeviceLinkAuthRepository::cancel)
    }
    auth(
        TvAuthModel(
            deviceLinkState = deviceLinkState,
            deviceLinkEnabled = activeServer.capabilities.tvLogin,
            serverUrl = activeServer.backendUrl,
            isCustomServer = activeServer.isCustom,
            authError = authError,
            onStartDeviceLink = {
                AuthRepository.clearError()
                DeviceLinkAuthRepository.start()
            },
            onCancelDeviceLink = DeviceLinkAuthRepository::cancel,
            onUseEmail = {
                log.i { "Switching to email sign-in" }
                DeviceLinkAuthRepository.cancel()
                useEmail = true
            },
            onContinueWithoutAccount = {
                DeviceLinkAuthRepository.cancel()
                AuthRepository.signInAnonymously()
            },
        ),
    )
    return true
}

/** Exposes the player runtime to the TV remote while the player is on screen (TV shell only). */
@Composable
internal fun PlayerScreenRuntime.BindTvPlayerRemote() {
    if (LocalTvShell.current == null) return
    val runtime by rememberUpdatedState(this)
    DisposableEffect(Unit) {
        val remote = object : TvPlayerRemote {
            override val controlsVisible: Boolean
                get() = runtime.controlsVisible

            override val isPlaying: Boolean
                get() = runtime.playbackSnapshot.isPlaying

            override val overlayOpen: Boolean
                get() = runtime.tvOverlayOpen()

            override val streamInfoOpen: Boolean
                get() = runtime.showStreamInfo

            override fun scrollStreamInfo(down: Boolean): Boolean {
                val scrollState = runtime.streamInfoScrollState
                val canScroll = if (down) scrollState.canScrollForward else scrollState.canScrollBackward
                if (!canScroll) return false
                val step = scrollState.viewportSize * STREAM_INFO_SCROLL_FRACTION
                runtime.scope.launch { scrollState.animateScrollBy(if (down) step else -step) }
                return true
            }

            override fun closeStreamInfo() {
                log.d { "Stream info closed from remote" }
                runtime.showStreamInfo = false
            }

            override fun togglePlayback() {
                runtime.togglePlayback()
            }

            override fun activatePrompt(): Boolean {
                val interval = runtime.activeSkipInterval
                if (interval != null && !runtime.skipIntervalDismissed && runtime.skipPromptOnScreen) {
                    val durationMs = runtime.playbackSnapshot.durationMs
                    val action = interval.internalSkipAction(runtime.skipIntervals, durationMs)
                    if (action != null) {
                        val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                        // Same watch party rules as the on-screen skip button.
                        val seekMs = if (runtime.isPartyControlLocked) runtime.ownSeekTarget(requestedMs) else requestedMs
                        if (seekMs != null) {
                            runtime.playerController?.seekTo(seekMs)
                            runtime.scheduleProgressSyncAfterSeek()
                            runtime.publishPartyPlayback(positionMs = seekMs)
                            runtime.skipIntervalDismissed = true
                            log.d { "Remote skipped interval to ${seekMs}ms" }
                        }
                        return true
                    }
                }
                if (runtime.showNextEpisodeCard && runtime.nextEpisodeInfo != null) {
                    if (!runtime.partyBlocksControl()) {
                        log.d { "Remote started next episode from prompt" }
                        runtime.playNextEpisode()
                    }
                    return true
                }
                return false
            }

            override fun seekStep(forward: Boolean) {
                if (runtime.tvSeekPreviewAvailable()) {
                    if (!runtime.controlsVisible) log.d { "Seek with preview: showing the timeline" }
                    TvTimelineSeek.request(forward)
                    runtime.controlsVisible = true
                    runtime.controlsActivityTick += 1
                    return
                }
                runtime.handleDoubleTapSeek(
                    if (forward) PlayerSeekDirection.Forward else PlayerSeekDirection.Backward,
                )
            }

            override val seekPreviewPending: Boolean
                get() = TvTimelineSeek.pending

            override fun showControls() {
                runtime.controlsVisible = true
                runtime.controlsActivityTick += 1
            }

            override fun hideControls() {
                runtime.controlsVisible = false
                TvTimelineSeek.pendingSteps = 0
            }

            override fun registerActivity() {
                runtime.controlsActivityTick += 1
            }

            override fun toggleSubtitleMenu(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                val open = !runtime.showSubtitleModal
                runtime.closeTvMenus()
                if (open) {
                    runtime.refreshTracks()
                    runtime.showSubtitleModal = true
                }
                log.d { "Remote subtitles key: menu ${if (open) "opened" else "closed"}" }
                return true
            }

            override fun toggleAudioMenu(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                val open = !runtime.showAudioModal
                runtime.closeTvMenus()
                if (open) {
                    runtime.refreshTracks()
                    runtime.showAudioModal = true
                }
                log.d { "Remote audio key: menu ${if (open) "opened" else "closed"}" }
                return true
            }

            override fun toggleGuidePanel(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                when {
                    runtime.isLiveTvPlayback -> {
                        val open = !runtime.showLiveChannelsPanel
                        runtime.closeTvMenus()
                        if (open) {
                            runtime.showLiveChannelsPanel = true
                            runtime.controlsVisible = false
                        }
                        log.d { "Remote guide key: channels panel ${if (open) "opened" else "closed"}" }
                    }
                    runtime.isSeries -> {
                        val open = !runtime.showEpisodesPanel
                        runtime.closeTvMenus()
                        if (open) runtime.openEpisodesPanel()
                        log.d { "Remote guide key: episodes panel ${if (open) "opened" else "closed"}" }
                    }
                    else -> return false
                }
                return true
            }

            override fun playNext(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                if (runtime.isLiveTvPlayback) return zapChannel(1)
                val next = runtime.nextEpisodeInfo
                if (
                    next?.hasAired != true ||
                    runtime.nextEpisodeAutoPlaySearching ||
                    runtime.nextEpisodeAutoPlayCountdown != null
                ) {
                    log.d { "Remote next key: no next episode available" }
                    return false
                }
                if (runtime.partyBlocksControl()) return true
                runtime.closeTvMenus()
                log.i { "Remote next key: playing the next episode" }
                runtime.playNextEpisode()
                return true
            }

            override fun playPrevious(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                return runtime.isLiveTvPlayback && zapChannel(-1)
            }

            override fun zapChannel(step: Int): Boolean {
                if (!runtime.isLiveTvPlayback || runtime.tvShortcutsBlocked()) return false
                val channels = LiveTvRepository.uiState.value.displayedChannels
                if (channels.isEmpty()) {
                    log.w { "Remote zapping: no channels in the list" }
                    return true
                }
                val current = channels.indexOfFirst { it.id == runtime.activeVideoId }
                    .takeIf { it >= 0 }
                    ?: channels.indexOfFirst { it.streamUrl == runtime.activeSourceUrl }
                val target = if (current < 0) {
                    0
                } else {
                    (current + step).mod(channels.size)
                }
                val channel = channels[target]
                runtime.closeTvMenus()
                log.i { "Remote zapping ${if (step > 0) "up" else "down"} to ${channel.name}" }
                runtime.switchToLiveChannel(channel)
                return true
            }

            override fun cycleResizeMode(): Boolean {
                if (runtime.tvShortcutsBlocked()) return false
                runtime.cycleResizeMode()
                log.d { "Remote zoom key: resize mode ${runtime.resizeMode}" }
                return true
            }

            override fun phoneRemotePlayback() = runtime.phoneRemotePlayback()

            override fun handlePhoneRemoteCommand(
                request: com.nuvio.app.features.tvremote.TvRemoteCommandRequest,
            ) = runtime.handlePhoneRemoteCommand(request)
        }
        TvPlayerRemoteRegistry.active = remote
        log.i { "Player remote bound" }
        onDispose {
            if (TvPlayerRemoteRegistry.active === remote) {
                TvPlayerRemoteRegistry.active = null
            }
            TvTimelineSeek.pendingSteps = 0
            log.i { "Player remote released" }
        }
    }
    // Focus rules while the controls are on screen: they always open on the timeline (a skip /
    // next-episode prompt on screen stays reachable with the D-pad and takes the focus back when
    // the controls hide); when a panel or a prompt closes, the focus goes back to the last used
    // control (e.g. the button that opened it).
    LaunchedEffect(Unit) {
        var wasVisible = false
        var hadOverlay = false
        var hadPrompt = false
        snapshotFlow {
            Triple(runtime.controlsVisible, runtime.tvOverlayOpen(), runtime.tvPromptVisible())
        }.collect { (visible, overlay, prompt) ->
            if (visible && !overlay) {
                when {
                    !wasVisible -> TvPlayerFocus.focus(TvPlayerFocus.timeline, "controls shown")
                    hadOverlay -> TvPlayerFocus.focus(TvPlayerFocus.controls, "panel closed")
                    hadPrompt && !prompt -> TvPlayerFocus.focus(TvPlayerFocus.controls, "prompt closed")
                }
            }
            wasVisible = visible
            hadOverlay = overlay
            hadPrompt = prompt
        }
    }
}

/** A panel, modal or blocking prompt of the player is open: it owns the remote keys. */
private fun PlayerScreenRuntime.tvOverlayOpen(): Boolean =
    showSourcesPanel || showEpisodesPanel || showLiveChannelsPanel || showChaptersPanel ||
        showSubmitIntroModal || showAudioModal || showSubtitleModal ||
        showVideoSettingsModal || showStillWatchingPrompt || showPartyPanel ||
        pendingP2pSwitch != null || errorMessage != null

/** A prompt or panel that must be answered first is open: the dedicated remote buttons do nothing. */
private fun PlayerScreenRuntime.tvShortcutsBlocked(): Boolean =
    showSubmitIntroModal || showStillWatchingPrompt || showPartyPanel ||
        pendingP2pSwitch != null || errorMessage != null

/** Closes the menus and panels a remote button can replace with its own. */
private fun PlayerScreenRuntime.closeTvMenus() {
    showSubtitleModal = false
    showAudioModal = false
    showVideoSettingsModal = false
    showSourcesPanel = false
    showEpisodesPanel = false
    showLiveChannelsPanel = false
    showChaptersPanel = false
}

/**
 * Seek steps go through the timeline preview: the stream has seek thumbnails and nothing else
 * (a prompt that takes the focus, locked controls) would keep the timeline from receiving the keys.
 */
private fun PlayerScreenRuntime.tvSeekPreviewAvailable(): Boolean =
    seekThumbnailProvider?.available == true && playbackSnapshot.durationMs > 0L &&
        !playerControlsLocked && !tvOverlayOpen() && !tvPromptVisible()

/**
 * "Skip intro/recap/outro" or "next episode" is on screen (it takes the focus itself while the
 * controls are hidden). The skip button counts only while it is drawn: it auto-hides after a while.
 */
private fun PlayerScreenRuntime.tvPromptVisible(): Boolean =
    (activeSkipInterval != null && !skipIntervalDismissed && skipPromptOnScreen) || showNextEpisodeCard


/** Search field row: on TV the shell's voice search button sits before the field. */
@Composable
internal fun TvSearchFieldRow(
    onVoiceQuery: (String) -> Unit,
    field: @Composable () -> Unit,
) {
    val voiceSearch = LocalTvShell.current?.voiceSearch
    if (voiceSearch == null) {
        field()
        return
    }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        voiceSearch(onVoiceQuery)
        Box(modifier = Modifier.weight(1f)) { field() }
    }
}


/**
 * Card options on TV: renders the shell's menu instead of the phone overlay.
 * Returns false (nothing drawn) without a TV shell.
 */
@Composable
internal fun TvPosterMenuHook(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    actions: List<com.nuvio.app.core.ui.PosterZoomOverlayAction>,
    onDismiss: () -> Unit,
): Boolean {
    val render = LocalTvShell.current?.posterMenu ?: return false
    render(TvPosterMenu(title = title, subtitle = subtitle, imageUrl = imageUrl, actions = actions, onDismiss = onDismiss))
    return true
}

/** TV-only first options of a card menu: open the details and, for series, a random episode. */
@Composable
internal fun tvMetaShortcutActions(
    type: String,
    id: String,
    includeDetails: Boolean,
    onOpenDetails: () -> Unit,
): List<com.nuvio.app.core.ui.PosterZoomOverlayAction> {
    if (LocalTvShell.current == null) return emptyList()
    val detailsLabel = org.jetbrains.compose.resources.stringResource(Res.string.tv_action_view_details)
    val randomLabel = org.jetbrains.compose.resources.stringResource(Res.string.tv_action_random_episode)
    return buildList {
        if (includeDetails) {
            add(
                com.nuvio.app.core.ui.PosterZoomOverlayAction(
                    icon = androidx.compose.material.icons.Icons.Default.Info,
                    label = detailsLabel,
                    onSelected = onOpenDetails,
                ),
            )
        }
        if (type == "series" || type == "tv") {
            add(
                com.nuvio.app.core.ui.PosterZoomOverlayAction(
                    icon = androidx.compose.material.icons.Icons.Default.Shuffle,
                    label = randomLabel,
                    onSelected = {
                        log.i { "Random episode requested from a card menu" }
                        TvPendingDetailAction.requestRandomEpisode(id)
                        onOpenDetails()
                    },
                ),
            )
        }
    }
}
