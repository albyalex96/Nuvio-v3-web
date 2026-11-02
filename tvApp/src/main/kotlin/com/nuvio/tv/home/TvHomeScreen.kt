package com.nuvio.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.localizedContinueWatchingSubtitle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.components.Top10RankNumber
import com.nuvio.app.features.home.components.top10RankSpace
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.tv.TvHeroMeta
import com.nuvio.app.tv.TvHeroMetaRows
import com.nuvio.app.tv.TvHomeModel
import com.nuvio.app.tv.toTvHeroMeta
import com.nuvio.app.tv.TvHomeRow
import com.nuvio.app.tv.TvHomeStatus
import com.nuvio.tv.R
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.tv.settings.TvLayoutSettings
import com.nuvio.tv.launcher.TvWatchNextSync
import com.nuvio.tv.settings.rememberTvLayoutSettings
import com.nuvio.tv.ui.TvActionButton
import com.nuvio.tv.ui.TvCardMetrics
import com.nuvio.tv.ui.TvLandscapeArtwork
import com.nuvio.tv.ui.TvRowCardFocusBorder
import com.nuvio.tv.ui.TvRowLandscapeCard
import com.nuvio.tv.ui.TvRowPosterCard
import com.nuvio.tv.ui.cardMetrics
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvMessage
import com.nuvio.tv.ui.landscapeArtwork
import com.nuvio.tv.ui.showsLandscape
import com.nuvio.tv.ui.tvFocusable
import com.nuvio.tv.ui.rememberTvRowsBringIntoViewSpec
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.lerp
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.tv.ui.TV_FOCUS_ANIMATION_MS
import com.nuvio.tv.ui.TvActionIconButton
import com.nuvio.tv.ui.TvPlayButton
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLayoutDirection
import com.nuvio.app.features.collection.Collection
import kotlin.math.roundToInt

private val log = Logger.withTag("TvHome")

/** Delay before the hero follows the focused card, so fast scrolling does not flicker. */
private const val HERO_FOLLOW_DELAY_MS = 220L

/** NuvioTV `HeroCarousel`: slides advance by themselves every 10 s. */
private const val HERO_AUTO_ADVANCE_MS = 10_000L
private const val HERO_FOCUS_KEY = "hero"
/** The carousel dots sit low, partly in the gap above the first row, to leave room to the plot. */
private val HERO_DOTS_OFFSET = 8.dp
/** Space kept free under the hero text for the carousel dots. */
private val HERO_DOTS_BAND = 12.dp
private val HERO_ROWS_GAP = 14.dp

/** Collapse/expand of the hero when the first row leaves or re-enters the screen. */
private const val HERO_COLLAPSE_ANIMATION_MS = 350

/**
 * OK on the hero opens it: every detail, play and library buttons. It grows only by the height of
 * the buttons, so the rows of the home stay on screen.
 */
private const val HERO_EXPAND_ANIMATION_MS = 350
private const val HERO_EXPANDED_TEXT_WIDTH_FRACTION = 0.55f
/** Play button (about 46 dp with its focus scale) plus the spacing around the buttons row. */
private val HERO_EXPANDED_EXTRA_HEIGHT = 72.dp
/** The plot of the expanded hero scrolls inside this height (about 5 lines). */
private val HERO_EXPANDED_PLOT_MAX_HEIGHT = 104.dp
/** Share of the visible plot scrolled by one D-pad press in the expanded hero. */
private const val HERO_PLOT_SCROLL_FRACTION = 0.6f
/** Focus outline around the hero text: drawn this far outside the text, so the text does not move. */
private val HERO_OUTLINE_INSET = 12.dp
private val HERO_OUTLINE_WIDTH = 2.dp
private val HERO_OUTLINE_RADIUS = 16.dp

/** NuvioTV never expands a focused poster sooner than this, even with a 0 s delay. */
private const val MIN_EXPAND_DELAY_MS = 150L

// Hero text block of the NuvioTV modern home (`HeroTitleContent`).
private const val HERO_TEXT_WIDTH_FRACTION = 0.42f
private val HERO_TEXT_END_PADDING = 48.dp
private val HERO_LOGO_HEIGHT = 100.dp
private val HERO_LOGO_MIN_WIDTH = 100.dp
private val HERO_LOGO_MAX_WIDTH = 220.dp
private const val HERO_DESCRIPTION_MAX_LINES = 4
/** NuvioTV shrinks the title and the plot a little with portrait posters (taller rows). */
private const val HERO_PORTRAIT_TITLE_SCALE = 0.92f
private const val HERO_PORTRAIT_DESCRIPTION_SCALE = 0.9f

// Hero backdrop of the NuvioTV modern home (`ModernHeroMediaLayer`).
private const val HERO_MEDIA_WIDTH_FRACTION = 0.72f
private val HERO_MEDIA_END_OVERFLOW = 56.dp
/** The backdrop ends just under the title of the first row (NuvioTV: row title + 14 dp). */
private val HERO_MEDIA_BELOW_ROWS_TOP = 24.dp + 14.dp
private const val HERO_BACKDROP_CROSSFADE_MS = 450

/**
 * Data class: the hero [Crossfade]s compare it, a new but equal value must not restart them.
 * [source] is the title whose details complete [meta] once the focus settles on it.
 */
private data class HeroInfo(
    val key: String,
    val title: String,
    val logo: String?,
    val backdrop: String?,
    val meta: TvHeroMeta,
    val description: String?,
    val source: MetaPreview? = null,
    val continueWatching: Boolean = false,
    val detailsLoaded: Boolean = false,
    /** Continue watching card of the hero: "play" resumes it like the card. */
    val continueWatchingItem: ContinueWatchingItem? = null,
)

@Composable
fun TvHomeScreen(model: TvHomeModel) {
    TvWatchNextSync(items = model.continueWatching, ready = model.status == TvHomeStatus.Content)
    when (model.status) {
        TvHomeStatus.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.nuvio.colors.accent)
                Text(
                    text = stringResource(R.string.tv_home_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
        TvHomeStatus.NoSources -> CenteredMessage(
            title = stringResource(R.string.tv_home_no_sources_title),
            message = stringResource(R.string.tv_home_no_sources_message),
            onRetry = null,
        )
        TvHomeStatus.Empty -> CenteredMessage(
            title = stringResource(R.string.tv_home_empty_title),
            message = stringResource(R.string.tv_home_empty_message),
            onRetry = null,
        )
        TvHomeStatus.Error -> CenteredMessage(
            title = stringResource(R.string.tv_home_error_title),
            message = model.errorMessage,
            onRetry = model.onRetry,
        )
        TvHomeStatus.Content -> TvHomeContent(model)
    }
}

@Composable
private fun CenteredMessage(title: String, message: String?, onRetry: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvMessage(
            title = title,
            message = message,
            action = onRetry?.let { retry ->
                {
                    TvActionButton(
                        label = stringResource(R.string.tv_action_retry),
                        icon = Icons.Filled.Refresh,
                        primary = true,
                        onClick = retry,
                    )
                }
            },
        )
    }
}

/**
 * Focus state of the TV home, kept out of [TvHomeContent]: the D-pad moves change it on every key
 * press, and only the hero and the one card that expands read it (not the whole list of rows).
 */
@Stable
private class TvHomeFocusState(
    defaultHero: HeroInfo?,
    private val lastFocused: MutableState<String?>,
) {
    /** Preview of the focused card (null while the hero carousel has the focus). */
    var focusedHero by mutableStateOf<HeroInfo?>(null)
    /** What the hero shows: follows [focusedHero] after [HERO_FOLLOW_DELAY_MS]. */
    var displayedHero by mutableStateOf(defaultHero)
    /** Card focused in a catalog row, and the one widened to its backdrop after the delay. */
    var focusedPosterKey by mutableStateOf<String?>(null)
    var expandedPosterKey by mutableStateOf<String?>(null)
    var heroFocused by mutableStateOf(false)
    /** The hero is open on the whole screen (OK on the hero): rows pushed out, buttons focused. */
    var heroExpanded by mutableStateOf(false)

    val lastFocusedKey: String? get() = lastFocused.value

    fun onCardFocused(key: String, hero: HeroInfo) {
        // Leaving the carousel: the card preview replaces it at once, without the follow delay.
        if (focusedHero == null) displayedHero = hero
        lastFocused.value = key
        focusedHero = hero
        focusedPosterKey = null
    }

    fun onPosterFocused(key: String, hero: HeroInfo) {
        onCardFocused(key, hero)
        focusedPosterKey = key
    }

    /** [showCarousel]: the carousel replaces the card preview; without carousel the hero keeps the card. */
    fun onHeroFocused(showCarousel: Boolean) {
        lastFocused.value = HERO_FOCUS_KEY
        if (showCarousel) focusedHero = null
        focusedPosterKey = null
    }
}

/** Top padding of the rows read during layout, so the hero collapse animation does not recompose. */
private class TvRowsContentPadding(private val top: () -> Dp, private val bottom: Dp) : PaddingValues {
    override fun calculateTopPadding(): Dp = top()
    override fun calculateBottomPadding(): Dp = bottom
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvHomeContent(model: TvHomeModel) {
    val settings = rememberTvLayoutSettings()
    val metrics = remember(settings) { settings.cardMetrics() }
    val continueWatchingPrefs by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
    // The root tabs keep a visited tab composed and RESUMED only while it is on screen: the hero
    // carousel timer and the hero details stop while the home is hidden (other tab, details, player).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val screenActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(screenActive) { log.d { if (screenActive) "Home on screen: hero resumed" else "Home hidden: hero paused" } }
    LaunchedEffect(Unit) { ContinueWatchingPreferencesRepository.ensureLoaded() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val requesterFor = remember { { key: String -> requesters.getOrPut(key) { FocusRequester() } } }
    val lastFocused = rememberSaveable { mutableStateOf<String?>(null) }
    val defaultHero = remember(model.heroItems, model.continueWatching) {
        model.continueWatching.firstOrNull()?.let { heroOf(it, subtitle = null, state = null) }
            ?: model.heroItems.firstOrNull()?.let(::heroOf)
    }
    val focus = remember { TvHomeFocusState(defaultHero, lastFocused) }
    LaunchedEffect(defaultHero) {
        if (focus.displayedHero == null && defaultHero != null) focus.displayedHero = defaultHero
    }
    val onCardFocused = remember(focus) { { key: String, hero: HeroInfo -> focus.onCardFocused(key, hero) } }
    val onPosterFocused = remember(focus) { { key: String, hero: HeroInfo -> focus.onPosterFocused(key, hero) } }
    val rowsState = rememberLazyListState()

    LaunchedEffect(focus, settings.expandFocusedPoster, settings.expandDelaySeconds) {
        snapshotFlow { focus.focusedPosterKey to rowsState.isScrollInProgress }.collectLatest { (key, scrolling) ->
            // Always collapse first: moving the focus or scrolling shrinks the expanded card.
            focus.expandedPosterKey = null
            if (key == null || !settings.expandFocusedPoster || scrolling) return@collectLatest
            delay((settings.expandDelaySeconds * 1_000L).coerceAtLeast(MIN_EXPAND_DELAY_MS))
            focus.expandedPosterKey = key
            log.d { "Focused poster expanded to its backdrop" }
        }
    }
    LaunchedEffect(focus) {
        snapshotFlow { focus.focusedHero }.collectLatest { next ->
            next ?: return@collectLatest
            delay(HERO_FOLLOW_DELAY_MS)
            focus.displayedHero = next
        }
    }
    // NuvioTV enriches the settled hero (genres, runtime, badges, ratings): only once the focus
    // stops, a new focus cancels the request.
    LaunchedEffect(focus, model.loadHeroDetails, screenActive) {
        if (!screenActive) return@LaunchedEffect
        snapshotFlow { focus.displayedHero }.collectLatest { hero ->
            if (hero == null || hero.detailsLoaded) return@collectLatest
            val source = hero.source ?: return@collectLatest
            val details = try {
                model.loadHeroDetails(source)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Hero details could not be loaded" }
                return@collectLatest
            }
            if (focus.displayedHero?.key == hero.key) {
                focus.displayedHero = hero.withDetails(details)
                log.d { "Hero details loaded" }
            }
        }
    }

    val carouselItems = model.heroItems
    val heroRequester = requesterFor(HERO_FOCUS_KEY)
    val heroIndex = rememberSaveable { mutableIntStateOf(0) }

    val firstKey = remember(model) { firstFocusableKey(model) }
    LaunchedEffect(firstKey) {
        // NuvioTV starts on the hero carousel when there is one.
        val initialKey = focus.lastFocusedKey ?: if (carouselItems.isNotEmpty()) HERO_FOCUS_KEY else firstKey
        val target = initialKey?.let(requesters::get) ?: firstKey?.let(requesters::get) ?: return@LaunchedEffect
        // The lazy rows compose their first items a few frames after the list.
        repeat(10) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not move the initial focus into the home rows" }
    }

    // The hero belongs to the top of the page: once the first row scrolls off the screen it
    // collapses (backdrop included) and the rows take the whole height.
    val heroHidden by remember { derivedStateOf { rowsState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(heroHidden) { log.d { if (heroHidden) "Hero collapsed" else "Hero expanded" } }
    // Read only in the layout and draw phases: the collapse animation never recomposes the home.
    val heroProgress = animateFloatAsState(
        targetValue = if (heroHidden) 0f else 1f,
        animationSpec = tween(HERO_COLLAPSE_ANIMATION_MS),
        label = "tvHomeHeroCollapse",
    )
    val heroBackdropVisible by remember { derivedStateOf { heroProgress.value > 0f } }
    val heroExpandProgress = animateFloatAsState(
        targetValue = if (focus.heroExpanded) 1f else 0f,
        animationSpec = tween(HERO_EXPAND_ANIMATION_MS),
        label = "tvHomeHeroExpand",
    )
    // Back closes the expanded hero (before the sidebar) and the focus returns to the hero text.
    // Play closes it too, but then the next page owns the focus: only Back refocuses the hero.
    var heroRefocusRequests by remember { mutableIntStateOf(0) }
    PlatformBackHandler(enabled = focus.heroExpanded) {
        log.i { "Expanded hero closed with Back" }
        focus.heroExpanded = false
        heroRefocusRequests++
    }
    LaunchedEffect(focus.heroExpanded) {
        if (focus.heroExpanded) log.i { "Hero expanded with its buttons" }
    }
    LaunchedEffect(heroRefocusRequests) {
        if (heroRefocusRequests == 0) return@LaunchedEffect
        // The hero target is not focusable while expanded: wait for the frame that enables it.
        repeat(10) {
            withFrameNanos { }
            if (runCatching { heroRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
        log.w { "Could not move the focus back to the hero" }
    }
    val hasCarousel = carouselItems.isNotEmpty()
    val rowsPadding = remember(hasCarousel) {
        TvRowsContentPadding(
            // Room between the carousel dots and the first row.
            // Collapsed hero: the rows start below the floating pill / top bar.
            top = {
                val progress = heroProgress.value
                (if (hasCarousel) HERO_ROWS_GAP else 0.dp) * progress + TvDimens.chromeTopInset * (1f - progress)
            },
            bottom = 48.dp,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // NuvioTV keeps the rows in the bottom 52% of the screen (49% with landscape posters).
        val collapsedHeroHeight = maxHeight * (1f - if (settings.landscapePosters) 0.49f else 0.52f)
        val screenHeight = maxHeight
        // The expanded hero grows by the buttons row only; read in the layout phase, so the
        // animation does not recompose the home.
        val expandedHeroHeight = minOf(collapsedHeroHeight + HERO_EXPANDED_EXTRA_HEIGHT, screenHeight)
        val heroHeight = remember(collapsedHeroHeight, expandedHeroHeight) {
            { lerp(collapsedHeroHeight, expandedHeroHeight, heroExpandProgress.value) }
        }
        if (heroBackdropVisible) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = heroProgress.value }) {
                TvHomeHeroBackdrop(
                    model = model,
                    focus = focus,
                    heroIndex = heroIndex,
                    fullScreen = settings.heroFullScreenBackdrop,
                    mediaHeight = { heroHeight() + (if (hasCarousel) HERO_ROWS_GAP else 0.dp) + HERO_MEDIA_BELOW_ROWS_TOP },
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .collapsingHeight(heroHeight) { heroProgress.value }
                    // Clipped only while collapsing: the dots hang a little below the hero.
                    .graphicsLayer {
                        val progress = heroProgress.value
                        alpha = progress
                        clip = progress < 1f
                    },
            ) {
                TvHomeHero(
                    model = model,
                    focus = focus,
                    heroIndex = heroIndex,
                    heroRequester = heroRequester,
                    heroHidden = { heroHidden },
                    active = screenActive,
                    portraitMode = !settings.landscapePosters,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val continueWatchingSubtitle: @Composable (ContinueWatchingItem) -> String = { item ->
                localizedContinueWatchingSubtitle(item, compact = true)
            }
            // NuvioTV row scrolling: the focused row always lands right below the top padding.
            val rowsTopPadding = remember(rowsPadding) { { rowsPadding.calculateTopPadding() } }
            val rowsBringIntoViewSpec = rememberTvRowsBringIntoViewSpec(rowsState, rowsTopPadding)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowsBringIntoViewSpec) {
                LazyColumn(
                    state = rowsState,
                    contentPadding = rowsPadding,
                    verticalArrangement = Arrangement.spacedBy(TvDimens.rowGap - TvDimens.rowVerticalPadding * 2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Focus leaving the rows (sidebar, top bar) collapses the expanded poster.
                        .onFocusChanged { state -> if (!state.hasFocus) focus.focusedPosterKey = null }
                        .focusRestorer(),
                ) {
                    if (model.continueWatching.isNotEmpty()) {
                        item(key = "continue_watching", contentType = "landscape_row") {
                            ContinueWatchingRow(
                                title = stringResource(R.string.tv_home_continue_watching),
                                items = model.continueWatching,
                                keyPrefix = "cw",
                                upcoming = false,
                                model = model,
                                settings = settings,
                                metrics = metrics,
                                useEpisodeThumbnails = continueWatchingPrefs.useEpisodeThumbnails,
                                blurNextUp = continueWatchingPrefs.blurNextUp,
                                subtitle = continueWatchingSubtitle,
                                requesterFor = requesterFor,
                                onFocused = onCardFocused,
                            )
                        }
                    }
                    if (model.upcoming.isNotEmpty()) {
                        item(key = "upcoming", contentType = "landscape_row") {
                            ContinueWatchingRow(
                                title = stringResource(R.string.tv_home_upcoming),
                                items = model.upcoming,
                                keyPrefix = "up",
                                upcoming = true,
                                model = model,
                                settings = settings,
                                metrics = metrics,
                                useEpisodeThumbnails = continueWatchingPrefs.useEpisodeThumbnails,
                                blurNextUp = continueWatchingPrefs.blurNextUp,
                                subtitle = continueWatchingSubtitle,
                                requesterFor = requesterFor,
                                onFocused = onCardFocused,
                            )
                        }
                    }
                    items(model.rows, key = TvHomeRow::key, contentType = { row -> row::class.simpleName }) { row ->
                        when (row) {
                            is TvHomeRow.Catalog -> CatalogRow(
                                row = row,
                                model = model,
                                settings = settings,
                                metrics = metrics,
                                focus = focus,
                                requesterFor = requesterFor,
                                onFocused = onCardFocused,
                                onPosterFocused = onPosterFocused,
                            )
                            is TvHomeRow.Folders -> FoldersRow(
                                row = row,
                                model = model,
                                settings = settings,
                                metrics = metrics,
                                requesterFor = requesterFor,
                                onFocused = onCardFocused,
                            )
                            is TvHomeRow.Top10 -> Top10Row(
                                row = row,
                                model = model,
                                settings = settings,
                                metrics = metrics,
                                requesterFor = requesterFor,
                                onFocused = onCardFocused,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Height animated during layout: the box takes [fullHeight] times [progress] and its content keeps
 * the full height, aligned to the bottom, so the hero slides up instead of being squashed.
 */
private fun Modifier.collapsingHeight(fullHeight: () -> Dp, progress: () -> Float): Modifier =
    layout { measurable, constraints ->
        val fullPx = fullHeight().roundToPx()
        val placeable = measurable.measure(constraints.copy(minHeight = fullPx, maxHeight = fullPx))
        val height = (fullPx * progress().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, height - fullPx) }
    }

/** Height read during layout (animated), capped to the space the parent gives. */
private fun Modifier.layoutHeight(height: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val px = height().roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
        layout(placeable.width, px) { placeable.place(0, 0) }
    }

/** Index of the carousel slide on screen, clamped to the current items. */
private fun activeHeroIndex(heroIndex: Int, count: Int): Int = heroIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

/** The hero shown by the backdrop and the info panel: the carousel slide, or the focused card. */
@Composable
private fun rememberShownHero(model: TvHomeModel, focus: TvHomeFocusState, heroIndex: MutableIntState): State<HeroInfo?> {
    val carouselItems = model.heroItems
    return remember(carouselItems, focus, heroIndex) {
        derivedStateOf {
            val carouselHero = carouselItems
                .getOrNull(activeHeroIndex(heroIndex.intValue, carouselItems.size))
                ?.takeIf { focus.focusedHero == null }
            carouselHero?.let(::heroOf) ?: focus.displayedHero
        }
    }
}

@Composable
private fun TvHomeHeroBackdrop(
    model: TvHomeModel,
    focus: TvHomeFocusState,
    heroIndex: MutableIntState,
    fullScreen: Boolean,
    mediaHeight: () -> Dp,
) {
    // Keyed on the state: it is rebuilt when the carousel items arrive or change (enriched metas),
    // and a derived state reading the first one would keep showing the last focused card.
    val shownHero = rememberShownHero(model, focus, heroIndex)
    val backdrop by remember(shownHero) { derivedStateOf { shownHero.value?.backdrop } }
    HeroBackdrop(url = backdrop, fullScreen = fullScreen, mediaHeight = mediaHeight)
}

/**
 * Hero carousel of NuvioTV (hero sources of Settings -> Home screen): shown while the hero has
 * the focus or no card was focused yet; a focused card replaces it with its own preview.
 */
@Composable
private fun TvHomeHero(
    model: TvHomeModel,
    focus: TvHomeFocusState,
    heroIndex: MutableIntState,
    heroRequester: FocusRequester,
    heroHidden: () -> Boolean,
    active: Boolean,
    portraitMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val carouselItems = model.heroItems
    val activeIndex = activeHeroIndex(heroIndex.intValue, carouselItems.size)
    val carouselActive = carouselItems.isNotEmpty() && focus.focusedHero == null
    val expanded = focus.heroExpanded
    LaunchedEffect(carouselActive, activeIndex, carouselItems.size, active, expanded) {
        // Hidden home: no slide change (and no backdrop crossfade or image load) off screen.
        // The expanded hero stays on its title while it is being read.
        if (!active || expanded || !carouselActive || carouselItems.size <= 1) return@LaunchedEffect
        // A manual change restarts the wait (the effect is keyed on the index).
        delay(HERO_AUTO_ADVANCE_MS)
        heroIndex.intValue = (activeIndex + 1) % carouselItems.size
    }
    val shownHero by rememberShownHero(model, focus, heroIndex)
    val hasCarousel = carouselItems.isNotEmpty()
    Box(modifier = modifier) {
        // The carousel dots get their own band below the hero text.
        HeroInfoPanel(
            hero = shownHero,
            portraitMode = portraitMode,
            showImdbRating = model.showImdbRating,
            showMdbListRatings = model.showMdbListRatings,
            outlined = focus.heroFocused && !expanded,
            expanded = expanded,
            actions = { hero -> HeroExpandedActions(hero = hero, model = model, onPlayStarted = { focus.heroExpanded = false }) },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = when {
                        hasCarousel -> HERO_DOTS_BAND
                        else -> HERO_ROWS_GAP
                    },
                ),
        )
        // The hero text is reachable with the remote (outlined when focused) and OK opens it on
        // the whole screen. Left/right change the carousel slide; without a carousel the hero
        // keeps the preview of the card focused last.
        if (hasCarousel || shownHero?.source != null) {
            HeroCarouselFocusTarget(
                count = carouselItems.size,
                activeIndex = activeIndex,
                visible = carouselActive && !expanded,
                focused = focus.heroFocused,
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !heroHidden() && !focus.heroExpanded }
                    .focusRequester(heroRequester)
                    .onFocusChanged { state ->
                        focus.heroFocused = state.isFocused
                        if (state.isFocused) focus.onHeroFocused(showCarousel = hasCarousel)
                    },
                onPrevious = { hasCarousel && (activeIndex > 0).also { moved -> if (moved) heroIndex.intValue = activeIndex - 1 } },
                onNext = { hasCarousel && (activeIndex < carouselItems.lastIndex).also { moved -> if (moved) heroIndex.intValue = activeIndex + 1 } },
                onClick = {
                    if (shownHero?.source != null) focus.heroExpanded = true
                },
            )
        }
    }
}

/**
 * Buttons of the expanded hero, in the style of the detail page: play (or resume, like the
 * continue watching card) and the card menu's library action. Play takes the focus; up/down
 * scroll the plot (see [HeroInfoPanel]) and the focus never leaves the hero.
 */
@Composable
private fun HeroExpandedActions(hero: HeroInfo, model: TvHomeModel, onPlayStarted: () -> Unit) {
    val source = hero.source ?: return
    val resumable = hero.continueWatchingItem
        ?: model.continueWatching.firstOrNull { item ->
            item.parentMetaId == source.id && !item.isNextUp && item.progressFraction > 0f
        }
    val progress = resumable?.takeIf { !it.isNextUp }?.progressFraction?.takeIf { it > 0f }
    val onPlay = model.onPlay
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(hero.key) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { playRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
        log.w { "Could not focus the play button of the expanded hero" }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPlayButton(
            text = stringResource(if (progress != null) R.string.tv_home_hero_resume else R.string.tv_home_hero_play),
            enabled = resumable != null || onPlay != null,
            progress = progress,
            onClick = {
                if (resumable != null) {
                    log.i { "Expanded hero play: continue watching item" }
                    model.onContinueWatchingClick(resumable)
                    onPlayStarted()
                } else if (onPlay != null) {
                    log.i { "Expanded hero play: primary action of the details" }
                    onPlay(source)
                    onPlayStarted()
                }
            },
            onLongClick = when {
                resumable != null -> model.onContinueWatchingLongClick?.let { longClick ->
                    {
                        log.i { "Expanded hero play long press: continue watching menu" }
                        longClick(resumable)
                    }
                }
                else -> model.onPlayLongClick?.let { longClick ->
                    {
                        log.i { "Expanded hero play long press: play options menu" }
                        longClick(source)
                    }
                }
            },
            modifier = Modifier
                .focusRequester(playRequester)
                .focusProperties { start = FocusRequester.Cancel },
        )
        model.onToggleSaved?.let { toggleSaved ->
            val saved = model.isSaved(source)
            TvActionIconButton(
                icon = if (saved) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = stringResource(
                    if (saved) R.string.tv_details_in_library else R.string.tv_details_add_to_library,
                ),
                onClick = {
                    log.i { "Expanded hero library action (saved: $saved)" }
                    toggleSaved(source)
                },
                modifier = Modifier.focusProperties { end = FocusRequester.Cancel },
            )
        }
    }
}

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Focus target of the hero carousel (NuvioTV `HeroCarousel`): left/right change the slide, OK
 * expands the hero, the indicator dots sit at the bottom. Left on the first slide is not consumed,
 * so it still opens the sidebar.
 */
@Composable
private fun HeroCarouselFocusTarget(
    count: Int,
    activeIndex: Int,
    visible: Boolean,
    focused: Boolean,
    onPrevious: () -> Boolean,
    onNext: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> if (rtl) onNext() else onPrevious()
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> if (rtl) onPrevious() else onNext()
                    event.key in ConfirmKeys && event.type == KeyEventType.KeyDown -> true
                    event.key in ConfirmKeys && event.type == KeyEventType.KeyUp -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        if (visible && count > 1) {
            val accent = MaterialTheme.nuvio.colors.accent
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = HERO_DOTS_OFFSET),
            ) {
                repeat(count) { index ->
                    val active = index == activeIndex
                    Box(
                        modifier = Modifier
                            .size(width = if (active) 32.dp else 12.dp, height = if (active) 6.dp else 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    active && focused -> accent
                                    active -> Color.White.copy(alpha = 0.85f)
                                    else -> Color.White.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * NuvioTV `ModernHeroMediaLayer` + `ModernHeroGradientLayer`: the backdrop fills the top right of
 * the screen (or the whole screen with "Fullscreen hero backdrop") and fades into the background
 * on the start side and along the bottom edge. The gradients are drawn in a single draw pass.
 */
@Composable
private fun HeroBackdrop(url: String?, fullScreen: Boolean, mediaHeight: () -> Dp) {
    val background = MaterialTheme.nuvio.colors.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mediaModifier = if (fullScreen) {
        Modifier.fillMaxSize()
    } else {
        // Slightly past the end edge, like NuvioTV, so the artwork does not stop at the overscan.
        Modifier
            .fillMaxWidth(HERO_MEDIA_WIDTH_FRACTION)
            .layoutHeight(mediaHeight)
            .offset(x = if (rtl) -HERO_MEDIA_END_OVERFLOW else HERO_MEDIA_END_OVERFLOW)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopEnd).then(mediaModifier)) {
            Crossfade(
                targetState = url,
                animationSpec = tween(HERO_BACKDROP_CROSSFADE_MS),
                label = "tvHomeBackdrop",
                modifier = Modifier.fillMaxSize(),
            ) { backdrop ->
                if (!backdrop.isNullOrBlank()) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopEnd,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().heroGradient(background, fullScreen, rtl))
        }
    }
}

private fun Modifier.heroGradient(background: Color, fullScreen: Boolean, rtl: Boolean): Modifier =
    drawWithCache {
        val fadeWidth = size.width * if (fullScreen) 0.65f else 0.45f
        val horizontalStops = if (fullScreen) {
            arrayOf(
                0f to background,
                0.22f to background.copy(alpha = 0.90f),
                0.46f to background.copy(alpha = 0.80f),
                0.76f to background.copy(alpha = 0.42f),
                1f to Color.Transparent,
            )
        } else {
            arrayOf(
                0f to background,
                0.22f to background.copy(alpha = 0.86f),
                0.46f to background.copy(alpha = 0.56f),
                0.76f to background.copy(alpha = 0.16f),
                1f to Color.Transparent,
            )
        }
        val horizontal = if (rtl) {
            Brush.horizontalGradient(*horizontalStops, startX = size.width, endX = size.width - fadeWidth)
        } else {
            Brush.horizontalGradient(*horizontalStops, startX = 0f, endX = fadeWidth)
        }
        val bottomStart = size.height * if (fullScreen) 0.64f else 0.82f
        val vertical = Brush.verticalGradient(
            *if (fullScreen) {
                arrayOf(
                    0f to Color.Transparent,
                    0.30f to background.copy(alpha = 0.35f),
                    0.60f to background.copy(alpha = 0.75f),
                    1f to background,
                )
            } else {
                arrayOf(
                    0f to Color.Transparent,
                    0.40f to background.copy(alpha = 0.25f),
                    0.75f to background.copy(alpha = 0.65f),
                    1f to background,
                )
            },
            startY = bottomStart,
            endY = size.height,
        )
        onDrawBehind {
            drawRect(
                brush = horizontal,
                topLeft = Offset(if (rtl) size.width - fadeWidth else 0f, 0f),
                size = Size(fadeWidth, size.height),
            )
            drawRect(
                brush = vertical,
                topLeft = Offset(0f, bottomStart),
                size = Size(size.width, size.height - bottomStart),
            )
        }
    }

/**
 * NuvioTV `HeroTitleContent`: logo (or title), the metadata lines and the plot, anchored to the
 * bottom of the hero right above the rows. The panel fades only when the title changes: details
 * arriving for the same title update it in place.
 */
@Composable
private fun HeroInfoPanel(
    hero: HeroInfo?,
    portraitMode: Boolean,
    showImdbRating: Boolean,
    showMdbListRatings: Boolean,
    outlined: Boolean,
    expanded: Boolean,
    actions: @Composable (HeroInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headline = MaterialTheme.typography.headlineLarge
    val body = MaterialTheme.typography.bodyMedium
    val titleStyle = remember(headline, portraitMode) {
        val scale = if (portraitMode) HERO_PORTRAIT_TITLE_SCALE else 1f
        headline.copy(fontWeight = FontWeight.Bold, fontSize = headline.fontSize * scale, lineHeight = headline.lineHeight * scale)
    }
    val descriptionStyle = remember(body, portraitMode) {
        val scale = if (portraitMode) HERO_PORTRAIT_DESCRIPTION_SCALE else 1f
        body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale)
    }
    val outlineColor = MaterialTheme.nuvio.colors.focusRing
    val outlineAlpha = animateFloatAsState(
        targetValue = if (outlined) 1f else 0f,
        animationSpec = tween(TV_FOCUS_ANIMATION_MS),
        label = "tvHomeHeroOutline",
    )
    val scope = rememberCoroutineScope()
    AnimatedContent(
        targetState = hero,
        // No clipping: the focus outline is drawn a little outside the text.
        transitionSpec = { (fadeIn(tween(300)) togetherWith fadeOut(tween(300))).using(SizeTransform(clip = false)) },
        contentKey = { info -> info?.key },
        label = "tvHomeHeroInfo",
        modifier = modifier,
    ) { info ->
        if (info == null) return@AnimatedContent
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = TvDimens.rowPadding, end = HERO_TEXT_END_PADDING, top = TvDimens.chromeTopInset),
        ) {
            val plotScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (expanded) HERO_EXPANDED_TEXT_WIDTH_FRACTION else HERO_TEXT_WIDTH_FRACTION)
                    // NuvioTV `HeroTitleBlock`: anchored above the rows and free to grow upwards, so the
                    // logo, details and the 4 plot lines are never squeezed by the hero height.
                    // Expanded, the buttons are added and the plot scrolls inside a capped height.
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
                    .heroOutline(color = outlineColor, alpha = { outlineAlpha.value })
                    // Expanded: up/down scroll the plot and never move the focus out of the hero.
                    .onPreviewKeyEvent { event ->
                        if (!expanded || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val down = when (event.key) {
                            Key.DirectionDown -> true
                            Key.DirectionUp -> false
                            else -> return@onPreviewKeyEvent false
                        }
                        val canScroll = if (down) plotScroll.canScrollForward else plotScroll.canScrollBackward
                        if (canScroll) {
                            val step = plotScroll.viewportSize * HERO_PLOT_SCROLL_FRACTION
                            scope.launch { plotScroll.animateScrollBy(if (down) step else -step) }
                        }
                        true
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var logoFailed by remember(info.logo) { mutableStateOf(false) }
                if (!info.logo.isNullOrBlank() && !logoFailed) {
                    AsyncImage(
                        model = info.logo,
                        contentDescription = info.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { logoFailed = true },
                        modifier = Modifier
                            .height(HERO_LOGO_HEIGHT)
                            .widthIn(min = HERO_LOGO_MIN_WIDTH, max = HERO_LOGO_MAX_WIDTH)
                            .fillMaxWidth(),
                    )
                } else if (info.title.isNotBlank()) {
                    Text(
                        text = info.title,
                        style = titleStyle,
                        color = MaterialTheme.nuvio.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TvHeroMetaRows(
                    meta = info.meta,
                    showImdbRating = showImdbRating,
                    showMdbListRatings = showMdbListRatings,
                    expanded = expanded,
                )
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    actions(info)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!info.description.isNullOrBlank()) {
                    if (expanded) {
                        // The whole plot; a long one scrolls inside a few lines (up/down).
                        Text(
                            text = info.description,
                            style = descriptionStyle,
                            color = MaterialTheme.nuvio.colors.textPrimary,
                            modifier = Modifier.heightIn(max = HERO_EXPANDED_PLOT_MAX_HEIGHT).verticalScroll(plotScroll),
                        )
                    } else {
                        Text(
                            text = info.description,
                            style = descriptionStyle,
                            color = MaterialTheme.nuvio.colors.textPrimary,
                            maxLines = HERO_DESCRIPTION_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Rounded focus outline drawn [HERO_OUTLINE_INSET] outside the content; [alpha] is read in the draw phase. */
private fun Modifier.heroOutline(color: Color, alpha: () -> Float): Modifier =
    drawWithContent {
        drawContent()
        val visibility = alpha()
        if (visibility <= 0f) return@drawWithContent
        val inset = HERO_OUTLINE_INSET.toPx()
        val radius = HERO_OUTLINE_RADIUS.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = HERO_OUTLINE_WIDTH.toPx()),
            alpha = visibility,
        )
    }

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<ContinueWatchingItem>,
    keyPrefix: String,
    upcoming: Boolean,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    useEpisodeThumbnails: Boolean,
    blurNextUp: Boolean,
    subtitle: @Composable (ContinueWatchingItem) -> String,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    TvContentRow(title = title) {
        items(items, key = { item -> "$keyPrefix:${continueWatchingKey(item)}" }) { item ->
            val key = "$keyPrefix:${continueWatchingKey(item)}"
            val itemSubtitle = subtitle(item)
            val state = continueWatchingText(item, short = false, upcoming = upcoming).badge
            TvContinueWatchingCard(
                item = item,
                style = settings.continueWatchingStyle,
                metrics = metrics,
                useEpisodeThumbnails = useEpisodeThumbnails,
                blurNextUp = blurNextUp,
                upcoming = upcoming,
                onClick = { model.onContinueWatchingClick(item) },
                onLongClick = model.onContinueWatchingLongClick?.let { longClick -> { longClick(item) } },
                onFocused = { onFocused(key, heroOf(item, itemSubtitle, state)) },
                modifier = Modifier.focusRequester(requesterFor(key)),
            )
        }
    }
}

@Composable
private fun CatalogRow(
    row: TvHomeRow.Catalog,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    focus: TvHomeFocusState,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
    onPosterFocused: (String, HeroInfo) -> Unit,
) {
    val landscape = settings.showsLandscape(row.items)
    TvContentRow(title = row.section.title) {
        items(row.items, key = { item -> "${row.key}:${item.stableKey()}" }) { item ->
            val key = "${row.key}:${item.stableKey()}"
            val focusModifier = Modifier.focusRequester(requesterFor(key))
            if (landscape) {
                TvRowLandscapeCard(
                    artwork = settings.landscapeArtwork(item),
                    title = item.name,
                    width = metrics.landscapeWidth,
                    height = metrics.landscapeHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = model.isPosterWatched(item),
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            } else {
                val square = item.posterShape == PosterShape.Square
                // Only portrait posters with a real backdrop expand (NuvioTV: catalog items only).
                val expandedArtwork = if (square || !settings.expandFocusedPoster) {
                    null
                } else {
                    settings.landscapeArtwork(item).takeUnless { it.fitImage }
                }
                // Derived per card: only the card that expands or collapses recomposes.
                val expanded by remember(key) { derivedStateOf { focus.expandedPosterKey == key } }
                TvRowPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    width = if (square) metrics.squareSize else metrics.posterWidth,
                    height = if (square) metrics.squareSize else metrics.posterHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = model.isPosterWatched(item),
                    expandedWidth = metrics.expandedPosterWidth,
                    expanded = expanded,
                    expandedArtwork = expandedArtwork,
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onPosterFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            }
        }
        val onCatalogClick = model.onCatalogClick
        if (row.canOpenCatalog && onCatalogClick != null) {
            item(key = "${row.key}:see_all") {
                SeeAllTile(
                    width = if (landscape) metrics.landscapeWidth else metrics.posterWidth,
                    height = if (landscape) metrics.landscapeHeight else metrics.posterHeight,
                    shape = metrics.shape,
                    onClick = { onCatalogClick(row.section) },
                )
            }
        }
    }
}

/**
 * "Top 10" row: portrait posters with the big rank number behind their left edge. The posters
 * never expand to the backdrop here, so the numbers keep their place while moving the focus.
 */
@Composable
private fun Top10Row(
    row: TvHomeRow.Top10,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    // The focused poster stops after the room of its rank number, which stays visible.
    TvContentRow(title = row.title, focusStartInset = top10RankSpace(1, metrics.posterWidth)) {
        itemsIndexed(row.items, key = { _, item -> "${row.key}:${item.stableKey()}" }) { index, item ->
            val key = "${row.key}:${item.stableKey()}"
            val rank = index + 1
            val rankSpace = top10RankSpace(rank, metrics.posterWidth)
            Box {
                Top10RankNumber(
                    rank = rank,
                    height = metrics.posterHeight,
                    width = rankSpace + metrics.posterWidth * TOP10_RANK_OVERLAP,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                TvRowPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    width = metrics.posterWidth,
                    height = metrics.posterHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = model.isPosterWatched(item),
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onFocused(key, heroOf(item)) },
                    modifier = Modifier
                        .padding(start = rankSpace)
                        .focusRequester(requesterFor(key)),
                )
            }
        }
    }
}

/** Part of the rank number that slides under the poster (same as the phone row). */
private const val TOP10_RANK_OVERLAP = 0.22f

@Composable
private fun FoldersRow(
    row: TvHomeRow.Folders,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    val onFolderClick = model.onFolderClick ?: return
    // The folder's own tile shape (collection editor) wins over the global landscape posters setting.
    TvContentRow(title = row.collection.title) {
        items(row.collection.folders, key = { folder -> "${row.key}:${folder.id}" }) { folder ->
            val key = "${row.key}:${folder.id}"
            val modifier = Modifier.focusRequester(requesterFor(key))
            val square = folder.tileShape.equals("square", ignoreCase = true)
            if (folder.isPosterTile() || square) {
                TvRowPosterCard(
                    imageUrl = folder.coverImageUrl,
                    title = folder.title,
                    width = if (square) metrics.squareSize else metrics.posterWidth,
                    height = if (square) metrics.squareSize else metrics.posterHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    onFocused = { onFocused(key, heroOf(row.collection, folder)) },
                    modifier = modifier,
                )
            } else {
                TvRowLandscapeCard(
                    artwork = TvLandscapeArtwork(imageUrl = folder.coverImageUrl, logoUrl = null, customLandscape = true),
                    title = folder.title,
                    width = metrics.landscapeWidth,
                    height = metrics.landscapeHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    onFocused = { onFocused(key, heroOf(row.collection, folder)) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SeeAllTile(width: Dp, height: Dp, shape: Shape, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .tvFocusable(
                shape = shape,
                onClick = onClick,
                focusedScale = 1f,
                borderWidth = TvRowCardFocusBorder,
                onFocusChange = { focused = it },
            )
            .background(if (focused) Color.White.copy(alpha = 0.18f) else MaterialTheme.nuvio.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.nuvio.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.tv_home_see_all),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
        }
    }
    Spacer(modifier = Modifier.width(TvDimens.screenPadding))
}

private fun CollectionFolder.isPosterTile(): Boolean =
    tileShape.isBlank() || tileShape.equals("poster", ignoreCase = true)

private fun continueWatchingKey(item: ContinueWatchingItem): String =
    "${item.parentMetaId}:${item.seasonNumber ?: -1}:${item.episodeNumber ?: -1}"

private fun firstFocusableKey(model: TvHomeModel): String? {
    model.continueWatching.firstOrNull()?.let { return "cw:${continueWatchingKey(it)}" }
    model.upcoming.firstOrNull()?.let { return "up:${continueWatchingKey(it)}" }
    return when (val row = model.rows.firstOrNull()) {
        is TvHomeRow.Catalog -> row.items.firstOrNull()?.let { "${row.key}:${it.stableKey()}" }
        is TvHomeRow.Folders -> row.collection.folders.firstOrNull()?.let { "${row.key}:${it.id}" }
        is TvHomeRow.Top10 -> row.items.firstOrNull()?.let { "${row.key}:${it.stableKey()}" }
        null -> null
    }
}

private fun heroOf(item: MetaPreview): HeroInfo = HeroInfo(
    key = item.stableKey(),
    title = item.name,
    logo = item.logo,
    backdrop = item.banner ?: item.landscapePoster ?: item.poster,
    meta = item.toTvHeroMeta(),
    description = item.description,
    source = item,
)

/** NuvioTV continue watching preview: the episode instead of the type, the watch state on line two. */
private fun heroOf(item: ContinueWatchingItem, subtitle: String?, state: String?): HeroInfo = HeroInfo(
    key = "cw:${continueWatchingKey(item)}",
    title = item.title,
    logo = item.logo,
    backdrop = item.background ?: item.imageUrl ?: item.poster,
    meta = TvHeroMeta(type = item.parentMetaType, episodeLabel = subtitle, highlight = state),
    description = item.pauseDescription,
    source = MetaPreview(id = item.parentMetaId, type = item.parentMetaType, name = item.title, poster = item.poster),
    continueWatching = true,
    continueWatchingItem = item,
)

/** Folder of a collection: its hero backdrop and title logo (NuvioTV `buildCollectionFolderItem`). */
private fun heroOf(collection: Collection, folder: CollectionFolder): HeroInfo = HeroInfo(
    key = "folder:${collection.id}:${folder.id}",
    title = when {
        folder.hideTitle -> ""
        !folder.coverEmoji.isNullOrBlank() -> "${folder.coverEmoji}  ${folder.title}"
        else -> folder.title
    },
    logo = folder.titleLogoUrl,
    backdrop = listOf(folder.heroBackdropUrl, folder.coverImageUrl, collection.backdropImageUrl)
        .firstOrNull { !it.isNullOrBlank() },
    meta = TvHeroMeta(),
    description = null,
)

/**
 * Hero completed with the details of its title. A continue watching item keeps its own lines
 * (episode, watch state) and only gains the genre and the year, like NuvioTV.
 */
private fun HeroInfo.withDetails(details: MetaPreview): HeroInfo {
    val detailsMeta = details.toTvHeroMeta()
    return copy(
        meta = if (continueWatching) {
            meta.copy(genre = meta.genre ?: detailsMeta.genre, releaseInfo = meta.releaseInfo ?: detailsMeta.releaseInfo)
        } else {
            detailsMeta
        },
        description = description?.takeIf { it.isNotBlank() } ?: details.description,
        detailsLoaded = true,
    )
}
