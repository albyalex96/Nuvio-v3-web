package com.nuvio.tv.details

import com.nuvio.app.core.ui.primaryActionContentColor
import com.nuvio.app.core.ui.primaryActionSurface
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.features.details.MetaEpisodeCardStyle
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsUiState
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import com.nuvio.app.tv.TvSeasonViewMode
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.ui.EpisodeCodeChip
import com.nuvio.app.core.format.formatCountriesForDisplay
import com.nuvio.app.core.format.formatLanguageForDisplay
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.tv.TvDetailsModel
import com.nuvio.app.tv.TvExternalRatingsRow
import com.nuvio.app.tv.TvPendingDetailAction
import com.nuvio.app.tv.TvSeason
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.showsLandscape
import com.nuvio.tv.settings.rememberTvLayoutSettings
import com.nuvio.tv.ui.TvRowLandscapeCard
import com.nuvio.tv.ui.TvRowPosterCard
import com.nuvio.tv.ui.cardMetrics
import com.nuvio.tv.ui.landscapeArtwork
import com.nuvio.tv.ui.TvSectionTitle
import com.nuvio.tv.ui.TvBlurredImage
import com.nuvio.tv.ui.tvFocusable
import com.nuvio.tv.ui.TvActionIconButton
import com.nuvio.tv.ui.TvPlayButton
import kotlinx.coroutines.delay

private val log = Logger.withTag("TvDetails")

// Layout of NuvioTV's detail page (HeroSection, EpisodesSection, CastSection) at 960dp width.
private val HeroHorizontalPadding = 48.dp
private val EpisodeCardShape = RoundedCornerShape(12.dp)
private val SeasonTabShape = RoundedCornerShape(20.dp)
// NuvioTV `CastSection`: 100dp photo in a 150dp column.
private val CastPhotoSize = 100.dp
private val CastItemWidth = 150.dp
private const val SEASON_FOCUS_SELECT_DELAY_MS = 150L
// Expandable plot of the phone episode cards (EPISODE_OVERVIEW_ANIMATION_MS in DetailSeriesContent).
private const val EPISODE_OVERVIEW_ANIMATION_MS = 280
private const val EPISODE_OVERVIEW_COLLAPSED_LINES = 3
private val OverviewToggleSize = 36.dp
private val EpisodeListArtworkWidth = 240.dp
// Season posters of the phone selector, and NuvioTV rating chips.
private val SeasonPosterWidth = 110.dp
private val SeasonPosterShape = RoundedCornerShape(12.dp)
private val RatingChipShape = RoundedCornerShape(14.dp)
private val EpisodeTextShadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 1f)

/** TV detail page, recreated from NuvioTV: full-screen backdrop, bottom-aligned hero, seasons and episodes below. */
@Composable
fun TvDetailsScreen(model: TvDetailsModel) {
    val meta = model.meta
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrolledPastHero by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Episode layout, blur and ratings follow the shared detail page settings (also on the phone).
    LaunchedEffect(Unit) { MetaScreenSettingsRepository.ensureLoaded() }
    val metaSettings by MetaScreenSettingsRepository.uiState.collectAsState()
    val seasons = remember(model.seasons) { orderedSeasons(model.seasons) }
    var selectedSeason by rememberSaveable(meta.id) { mutableStateOf<Int?>(null) }
    val season = seasons.firstOrNull { it.number == selectedSeason }
        ?: seasons.firstOrNull { it.number == model.primaryActionSeason }
        ?: seasons.firstOrNull()

    // "Watch a random episode" from a card menu: play one as soon as the episodes are loaded.
    LaunchedEffect(meta.id, model.seasons) {
        if (model.seasons.isEmpty()) return@LaunchedEffect
        if (!TvPendingDetailAction.consumeRandomEpisode(meta.id)) return@LaunchedEffect
        val episodes = model.seasons
            .filter { it.number > 0 }
            .flatMap { it.episodes }
            .filter { it.available }
        val pick = episodes.filterNot(model.isEpisodeWatched).randomOrNull() ?: episodes.randomOrNull()
        if (pick == null) {
            log.w { "No episode available for a random pick" }
        } else {
            log.i { "Playing a random episode: S${pick.season}E${pick.episode}" }
            model.onEpisodeClick(pick)
        }
    }
    // "Play" from the expanded home hero: the primary action once a series knows its episodes.
    val currentModel by rememberUpdatedState(model)
    LaunchedEffect(meta.id, model.isSeries, model.seasons) {
        if (model.isSeries && model.seasons.isEmpty()) return@LaunchedEffect
        if (!TvPendingDetailAction.consumePrimaryAction(meta.id)) return@LaunchedEffect
        val current = currentModel
        if (current.primaryActionEnabled) {
            log.i { "Primary action requested from the home hero: ${current.primaryActionLabel}" }
            current.onPrimaryAction()
        } else {
            log.w { "Primary action requested from the home hero is not available" }
        }
    }
    // A request this page could not run (e.g. a series without episodes) must not fire on a later visit.
    DisposableEffect(meta.id) {
        onDispose { TvPendingDetailAction.consumePrimaryAction(meta.id) }
    }
    LaunchedEffect(meta.id) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { playRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not focus the play button" }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.nuvio.colors.background),
    ) {
        val heroHeight = maxHeight
        DetailsBackdrop(url = meta.background ?: meta.poster, dimmed = scrolledPastHero)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            item(key = "hero") {
                HeroSection(
                    model = model,
                    playRequester = playRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                )
            }
            if (season != null) {
                episodesSection(
                    model = model,
                    seasons = seasons,
                    season = season,
                    onSeasonSelected = { selectedSeason = it },
                    settings = metaSettings,
                )
            }
            // NuvioTV "Ratings": series only, when the detail settings show episode ratings.
            if (model.isSeries && metaSettings.episodeRatingsVisibility.showRatings && model.episodeRatings.isNotEmpty()) {
                item(key = "episode_ratings") {
                    EpisodeRatingsSection(model = model, seasons = seasons, settings = metaSettings)
                }
            }
            if (meta.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(cast = meta.cast, onClick = model.onCastClick)
                }
            }
            if (meta.collectionItems.isNotEmpty()) {
                item(key = "collection") {
                    PosterRow(
                        title = meta.collectionName ?: stringResource(R.string.tv_details_collection),
                        items = meta.collectionItems,
                        onClick = model.onOpenMeta,
                    )
                }
            }
            if (meta.moreLikeThis.isNotEmpty()) {
                item(key = "more_like_this") {
                    PosterRow(
                        title = stringResource(R.string.tv_details_more_like_this),
                        items = meta.moreLikeThis,
                        onClick = model.onOpenMeta,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsBackdrop(url: String?, dimmed: Boolean) {
    val background = MaterialTheme.nuvio.colors.background
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) 0.15f else 1f,
        animationSpec = tween(if (dimmed) 300 else 800),
        label = "tvDetailsBackdrop",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                alpha = alpha,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Left and bottom fades so the hero text stays readable (NuvioTV backdrop gradients).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.95f),
                        0.35f to background.copy(alpha = 0.75f),
                        0.65f to background.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to background.copy(alpha = 0.7f),
                        1f to background,
                    ),
                ),
        )
    }
}

@Composable
private fun HeroSection(
    model: TvDetailsModel,
    playRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val meta = model.meta
    var logoFailed by remember(meta.logo) { mutableStateOf(false) }
    val creditLine = when {
        meta.director.isNotEmpty() -> if (model.isSeries) {
            stringResource(R.string.tv_details_creator, meta.director.joinToString(", "))
        } else {
            stringResource(R.string.tv_details_director, meta.director.joinToString(", "))
        }
        meta.writer.isNotEmpty() -> stringResource(R.string.tv_details_writer, meta.writer.joinToString(", "))
        else -> null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.Bottom) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HeroHorizontalPadding, end = HeroHorizontalPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (!meta.logo.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = meta.logo,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(0.4f)
                        .padding(bottom = 16.dp),
                )
            } else {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(bottom = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusRestorer(playRequester),
            ) {
                TvPlayButton(
                    text = model.primaryActionLabel,
                    enabled = model.primaryActionEnabled,
                    progress = model.primaryActionProgress,
                    onClick = model.onPrimaryAction,
                    onLongClick = model.onPrimaryActionManual,
                    modifier = Modifier.focusRequester(playRequester),
                )
                TvActionIconButton(
                    icon = if (model.isSaved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (model.isSaved) R.string.tv_details_in_library else R.string.tv_details_add_to_library,
                    ),
                    onClick = model.onToggleSaved,
                )
                TvActionIconButton(
                    icon = if (model.isWatched) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(
                        if (model.isWatched) R.string.tv_details_watched else R.string.tv_details_mark_watched,
                    ),
                    selected = model.isWatched,
                    onClick = model.onToggleWatched,
                )
                model.onPrimaryActionManual?.let { manual ->
                    TvActionIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.tv_details_choose_source),
                        onClick = manual,
                    )
                }
                model.onShuffleClick?.let { shuffle ->
                    TvActionIconButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = stringResource(
                            if (model.shuffleActive) R.string.tv_details_shuffle_on else R.string.tv_details_shuffle,
                        ),
                        selected = model.shuffleActive,
                        onClick = shuffle,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!creditLine.isNullOrBlank()) {
                Text(
                    text = creditLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // MDBList ratings (NuvioTV `MDBListRatingsRow`), between the credits and the plot.
            if (model.showExternalRatings && meta.externalRatings.isNotEmpty()) {
                TvExternalRatingsRow(
                    ratings = meta.externalRatings,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // NuvioTV plot: up to 8 lines, focusable when cut, OK opens the whole plot.
            val description = meta.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                var showFullDescription by rememberSaveable(meta.id) { mutableStateOf(false) }
                TvSynopsisDescription(
                    description = description,
                    onShowFullDescription = {
                        log.d { "Full plot opened" }
                        showFullDescription = true
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(bottom = 12.dp),
                )
                if (showFullDescription) {
                    TvSynopsisOverlay(
                        title = meta.name,
                        description = description,
                        onDismiss = { showFullDescription = false },
                    )
                }
            }

            MetaInfoRows(model)
        }
    }
}

@Composable
private fun MetaInfoRows(model: TvDetailsModel) {
    val meta = model.meta
    val secondary = MaterialTheme.nuvio.colors.textSecondary
    // Like NuvioTV, the IMDb score is not repeated when the MDBList row already shows it.
    val imdbInRatingsRow = model.showExternalRatings && meta.externalRatings.any { it.source == "imdb" }
    val primaryItems = buildList {
        meta.genres.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" • ")) }
        meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(formatReleaseDateForDisplay(it)) }
        if (!imdbInRatingsRow) meta.imdbRating?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
    }
    val status = when (meta.status?.trim()?.lowercase()) {
        null, "" -> null
        "ended" -> stringResource(R.string.tv_details_status_ended)
        "continuing", "returning series" -> stringResource(R.string.tv_details_status_continuing)
        "canceled", "cancelled" -> stringResource(R.string.tv_details_status_cancelled)
        else -> meta.status?.trim()
    }?.uppercase()
    val secondaryItems = listOfNotNull(
        meta.runtime?.takeIf { it.isNotBlank() },
        meta.country?.takeIf { it.isNotBlank() }?.let { formatCountriesForDisplay(it) },
        meta.language?.takeIf { it.isNotBlank() }?.let { formatLanguageForDisplay(it) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (primaryItems.isNotEmpty()) {
            DividedRow(items = primaryItems, color = secondary, large = true)
        }
        val ageRating = meta.ageRating?.takeIf { it.isNotBlank() }
        if (ageRating != null || status != null || secondaryItems.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ageRating?.let { MetaBadge(it, MaterialTheme.nuvio.colors.textSecondary) }
                status?.let { MetaBadge(it, MaterialTheme.nuvio.colors.textPrimary) }
                if (secondaryItems.isNotEmpty()) {
                    DividedRow(items = secondaryItems, color = MaterialTheme.nuvio.colors.textPrimary, large = false)
                }
            }
        }
    }
}

@Composable
private fun DividedRow(items: List<String>, color: Color, large: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Text(
                text = item,
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.nuvio.colors.textMuted),
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Seasons in NuvioTV order: specials (season 0) go last. */
private fun orderedSeasons(seasons: List<TvSeason>): List<TvSeason> =
    seasons.filter { it.number > 0 }.sortedBy { it.number } + seasons.filter { it.number <= 0 }

/**
 * Season tabs and episodes, following the shared detail page settings (Settings -> Detail page):
 * horizontal cards or a vertical list, blurred unwatched artwork and the episode ratings rule.
 */
private fun LazyListScope.episodesSection(
    model: TvDetailsModel,
    seasons: List<TvSeason>,
    season: TvSeason,
    onSeasonSelected: (Int) -> Unit,
    settings: MetaScreenSettingsUiState,
) {
    item(key = "season_tabs") {
        if (seasons.size > 1) {
            SeasonSelector(model = model, seasons = seasons, selectedSeason = season.number, onSeasonSelected = onSeasonSelected)
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    if (settings.episodeCardStyle == MetaEpisodeCardStyle.List) {
        items(season.episodes, key = { episode -> "episode:${episode.season}:${episode.episode}:${episode.id}" }) { episode ->
            EpisodeListRow(model = model, episode = episode, settings = settings)
        }
    } else {
        item(key = "episodes_row") {
            EpisodesRow(model = model, season = season, settings = settings)
        }
    }
}

/** Season artwork like the phone (`resolveSeasonPoster`): the episodes' season poster, then the addon map. */
private fun seasonPoster(model: TvDetailsModel, season: TvSeason): String? =
    season.episodes.firstNotNullOfOrNull { episode -> episode.seasonPoster?.takeIf(String::isNotBlank) }
        ?: model.meta.seasonPosters[season.number]?.takeIf(String::isNotBlank)

/**
 * Season selector of the phone: posters (default) or text pills, with the same per-device toggle.
 * Without any season artwork only the pills are shown.
 */
@Composable
private fun SeasonSelector(
    model: TvDetailsModel,
    seasons: List<TvSeason>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
) {
    val hasPosters = remember(seasons, model.meta.seasonPosters) { seasons.any { seasonPoster(model, it) != null } }
    var postersEnabled by remember { mutableStateOf(TvSeasonViewMode.postersEnabled()) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HeroHorizontalPadding),
        ) {
            Text(
                text = stringResource(R.string.tv_details_seasons),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.nuvio.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (hasPosters) {
                SeasonViewModeToggle(
                    posters = postersEnabled,
                    onToggle = {
                        postersEnabled = !postersEnabled
                        TvSeasonViewMode.setPostersEnabled(postersEnabled)
                        log.d { "Season selector: ${if (postersEnabled) "posters" else "text"}" }
                    },
                )
            }
        }
        if (hasPosters && postersEnabled) {
            SeasonPosters(model = model, seasons = seasons, selectedSeason = selectedSeason, onSeasonSelected = onSeasonSelected)
        } else {
            SeasonTabs(
                seasons = seasons,
                selectedSeason = selectedSeason,
                onSeasonSelected = onSeasonSelected,
                onSeasonLongClick = model.onSeasonLongClick,
            )
        }
    }
}

@Composable
private fun SeasonViewModeToggle(posters: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.nuvio.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .tvFocusable(shape = SeasonTabShape, onClick = onToggle, focusedScale = 1f, borderWidth = 2.dp, onFocusChange = { focused = it })
            .background(if (focused) Color.White else colors.surfaceCard, SeasonTabShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (posters) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
            contentDescription = null,
            tint = if (focused) Color.Black else colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            // Shows the mode the toggle switches to, like the phone button.
            text = stringResource(if (posters) R.string.tv_details_season_view_text else R.string.tv_details_season_view_posters),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Color.Black else colors.textPrimary,
        )
    }
}

/** Season posters (2:3) with the label below; focusing one selects it after a short pause. */
@Composable
private fun SeasonPosters(
    model: TvDetailsModel,
    seasons: List<TvSeason>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
) {
    var pendingSeason by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(SEASON_FOCUS_SELECT_DELAY_MS)
        onSeasonSelected(target)
        pendingSeason = null
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = seasons.indexOfFirst { it.number == selectedSeason }
        if (index > 0) listState.scrollToItem(index)
    }
    val colors = MaterialTheme.nuvio.colors
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(seasons, key = TvSeason::number) { season ->
            val selected = season.number == selectedSeason
            val label = seasonLabel(season.number)
            Column(modifier = Modifier.width(SeasonPosterWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .onFocusChanged { state ->
                            if (state.isFocused && !selected) pendingSeason = season.number
                        }
                        .tvFocusable(
                            shape = SeasonPosterShape,
                            onClick = { onSeasonSelected(season.number) },
                            onLongClick = model.onSeasonLongClick?.let { open ->
                                {
                                    log.d { "Season actions: season ${season.number}" }
                                    open(season.number)
                                }
                            },
                            focusedScale = 1f,
                            borderWidth = 3.dp,
                        )
                        .background(colors.surfaceCard)
                        .then(if (selected) Modifier.border(2.dp, colors.accent, SeasonPosterShape) else Modifier),
                ) {
                    val poster = seasonPoster(model, season) ?: model.meta.poster ?: model.meta.background
                    if (poster != null) {
                        AsyncImage(
                            model = poster,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * IMDb rating of every episode (NuvioTV `EpisodeRatingsSection`): season pills, a summary line
 * and colored rating chips. Hidden ratings ("only watched episodes") show a dash.
 */
@Composable
private fun EpisodeRatingsSection(model: TvDetailsModel, seasons: List<TvSeason>, settings: MetaScreenSettingsUiState) {
    // Specials are never rated (NuvioTV).
    val ratedSeasons = remember(seasons) { seasons.filter { it.number > 0 } }
    if (ratedSeasons.isEmpty()) return
    var selected by rememberSaveable(model.meta.id) { mutableStateOf(ratedSeasons.first().number) }
    val season = ratedSeasons.firstOrNull { it.number == selected } ?: ratedSeasons.first()
    val episodes = remember(season) {
        season.episodes.filter { it.episode != null }.distinctBy { it.episode }.sortedBy { it.episode }
    }
    val colors = MaterialTheme.nuvio.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        TvSectionTitle(stringResource(R.string.tv_details_ratings))
        LazyRow(
            contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(ratedSeasons, key = TvSeason::number) { item ->
                val isSelected = item.number == season.number
                Text(
                    text = stringResource(R.string.tv_details_ratings_season, item.number),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .onFocusChanged { state -> if (state.isFocused) selected = item.number }
                        .tvFocusable(
                            shape = RatingChipShape,
                            onClick = { selected = item.number },
                            focusedScale = 1f,
                            borderWidth = 2.dp,
                        )
                        .background(if (isSelected) Color.White.copy(alpha = 0.18f) else colors.surfaceCard)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.tv_details_ratings_summary, season.number, episodes.size),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = HeroHorizontalPadding, vertical = 2.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(episodes, key = { episode -> "${episode.season}:${episode.episode}" }) { episode ->
                val number = episode.episode ?: 0
                val watched = model.isEpisodeWatched(episode)
                val rating = (model.episodeRatings[season.number to number] ?: episode.rating)
                    ?.takeIf { it > 0.0 && settings.episodeRatingsVisibility.showRating(watched) }
                val chipColor = rating?.let(::ratingColor) ?: colors.surfaceCard
                val textColor = rating?.let(::ratingTextColor) ?: colors.textSecondary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(width = 72.dp, height = 46.dp)
                        .tvFocusable(
                            shape = RatingChipShape,
                            onClick = { model.onEpisodeClick(episode) },
                            focusedScale = 1.03f,
                            borderWidth = 2.dp,
                        )
                        .background(chipColor),
                ) {
                    Text(
                        text = stringResource(R.string.tv_details_ratings_episode, number),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                    )
                    Text(
                        text = rating?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                    )
                }
            }
        }
    }
}

// Rating scale of NuvioTV's episode ratings.
private fun ratingColor(value: Double): Color = when {
    value >= 9.0 -> Color(0xFF186A3B)
    value >= 8.0 -> Color(0xFF28B463)
    value >= 7.5 -> Color(0xFFF4D03F)
    value >= 7.0 -> Color(0xFFF39C12)
    value >= 6.0 -> Color(0xFFE74C3C)
    else -> Color(0xFF633974)
}

private fun ratingTextColor(value: Double): Color =
    if (value >= 7.0 && value < 8.0) Color(0xFF1D1D1F) else Color.White

/** Season pills: moving the focus on a season selects it after a short pause (NuvioTV behavior). */
@Composable
private fun SeasonTabs(
    seasons: List<TvSeason>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongClick: ((Int) -> Unit)?,
) {
    var pendingSeason by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(SEASON_FOCUS_SELECT_DELAY_MS)
        onSeasonSelected(target)
        pendingSeason = null
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(seasons, key = TvSeason::number) { item ->
            var focused by remember { mutableStateOf(false) }
            val selected = item.number == selectedSeason
            val accent = MaterialTheme.nuvio.colors.accent
            Box(
                modifier = Modifier
                    .onFocusChanged { state ->
                        if (state.isFocused && !selected) pendingSeason = item.number
                    }
                    .tvFocusable(
                        shape = SeasonTabShape,
                        onClick = { onSeasonSelected(item.number) },
                        onLongClick = onSeasonLongClick?.let { open ->
                            {
                                log.d { "Season actions: season ${item.number}" }
                                open(item.number)
                            }
                        },
                        focusedScale = 1f,
                        onFocusChange = { focused = it },
                    )
                    .background(
                        when {
                            focused -> accent
                            selected -> Color.White.copy(alpha = 0.16f)
                            else -> MaterialTheme.nuvio.colors.surfaceCard
                        },
                        SeasonTabShape,
                    ),
            ) {
                Text(
                    text = seasonLabel(item.number),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        focused -> MaterialTheme.nuvio.colors.onAccent
                        selected -> MaterialTheme.nuvio.colors.textPrimary
                        else -> MaterialTheme.nuvio.colors.textSecondary
                    },
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodesRow(model: TvDetailsModel, season: TvSeason, settings: MetaScreenSettingsUiState) {
    val metrics = rememberEpisodeCardMetrics()
    val listState = rememberLazyListState()
    val targetIndex = season.episodes.indexOfFirst { episode ->
        season.number == model.primaryActionSeason && episode.episode == model.primaryActionEpisode
    }
    LaunchedEffect(season.number) {
        listState.scrollToItem(targetIndex.coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = metrics.rowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(season.episodes, key = { episode -> "${episode.season}:${episode.episode}:${episode.id}" }) { episode ->
            EpisodeCard(model = model, episode = episode, metrics = metrics, settings = settings)
        }
    }
}

/** Episode card size of NuvioTV (`rememberEpisodeCardMetrics`), which grows on wider TV canvases. */
private data class EpisodeCardMetrics(val width: Dp, val height: Dp, val spacing: Dp, val rowPadding: Dp)

@Composable
private fun rememberEpisodeCardMetrics(): EpisodeCardMetrics {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp) {
        when {
            screenWidthDp >= 1300 -> EpisodeCardMetrics(width = 400.dp, height = 263.dp, spacing = 20.dp, rowPadding = 56.dp)
            screenWidthDp >= 1000 -> EpisodeCardMetrics(width = 360.dp, height = 235.dp, spacing = 18.dp, rowPadding = 52.dp)
            else -> EpisodeCardMetrics(width = 320.dp, height = 207.dp, spacing = 16.dp, rowPadding = 48.dp)
        }
    }
}

/** Texts and state of an episode shared by the card and the list row. */
private class EpisodeInfo(
    val code: String?,
    val date: String?,
    val runtime: String?,
    val rating: String?,
    val progress: Float?,
    val watched: Boolean,
    val blurArtwork: Boolean,
)

@Composable
private fun rememberEpisodeInfo(model: TvDetailsModel, episode: MetaVideo, settings: MetaScreenSettingsUiState): EpisodeInfo {
    val watched = model.isEpisodeWatched(episode)
    val code = formatEpisodeCodeWithSelectedFormat(episode.season, episode.episode)
        ?: if (episode.season != null && episode.episode != null) {
            stringResource(R.string.tv_details_episode_code, episode.season ?: 0, episode.episode ?: 0)
        } else {
            null
        }
    return EpisodeInfo(
        code = code,
        date = episode.released?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay),
        runtime = episode.runtime?.takeIf { it > 0 }?.let { stringResource(R.string.tv_details_runtime_minutes, it) },
        // "Episode ratings" of the detail page settings (all / hidden / only watched episodes).
        rating = (episode.season?.let { season -> episode.episode?.let { model.episodeRatings[season to it] } } ?: episode.rating)
            ?.takeIf { it > 0.0 && settings.episodeRatingsVisibility.showRating(watched) }
            ?.let { "%.1f".format(it) },
        progress = model.episodeProgress(episode),
        watched = watched,
        blurArtwork = settings.blurUnwatchedEpisodes && !watched,
    )
}

/** Episode card of NuvioTV: artwork with a bottom scrim, episode badge, title, plot and info row. */
@Composable
private fun EpisodeCard(
    model: TvDetailsModel,
    episode: MetaVideo,
    metrics: EpisodeCardMetrics,
    settings: MetaScreenSettingsUiState,
) {
    var focused by remember { mutableStateOf(false) }
    val expandState = rememberOverviewExpandState(episode)
    val info = rememberEpisodeInfo(model, episode, settings)

    Column(modifier = Modifier.width(metrics.width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(EPISODE_OVERVIEW_ANIMATION_MS))
                // Expanded: the card grows below the artwork, which keeps its size (like the phone).
                .then(if (expandState.expanded) Modifier.heightIn(min = metrics.height) else Modifier.height(metrics.height))
                .then(expandState.downToToggle())
                .tvFocusable(
                    shape = EpisodeCardShape,
                    onClick = { model.onEpisodeClick(episode) },
                    onLongClick = model.onEpisodeManualClick?.let { manual -> { manual(episode) } },
                    focusedScale = 1f,
                    onFocusChange = {
                        focused = it
                        expandState.ownerFocused = it
                    },
                )
                .background(MaterialTheme.nuvio.colors.surfaceCard, EpisodeCardShape)
                .nuvioCardDepth(shape = EpisodeCardShape, surface = NuvioCardDepthSurface.EpisodeCards),
        ) {
            // Fills the area below the artwork when the plot is expanded, continuing the scrim.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = metrics.height)
                    .background(Color.Black.copy(alpha = 0.92f)),
            )
            EpisodeArtwork(
                model = model,
                episode = episode,
                blurred = info.blurArtwork,
                modifier = Modifier.fillMaxWidth().height(metrics.height),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.height)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.2f to Color.Black.copy(alpha = 0.04f),
                            0.38f to Color.Black.copy(alpha = 0.26f),
                            0.54f to Color.Black.copy(alpha = 0.56f),
                            0.7f to Color.Black.copy(alpha = 0.76f),
                            0.86f to Color.Black.copy(alpha = 0.88f),
                            1f to Color.Black.copy(alpha = if (focused || expandState.expanded) 0.95f else 0.9f),
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
            ) {
                EpisodeCodeBadge(info.code)
                EpisodeTitle(episode.title)
                EpisodeOverview(episode = episode, state = expandState)
                EpisodeInfoRow(info)
            }
            EpisodeProgressBar(info, modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            EpisodeStatusBadge(episode = episode, info = info, modifier = Modifier.padding(14.dp))
        }
        if (expandState.canExpand) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                OverviewToggleButton(state = expandState)
            }
        }
    }
}

/**
 * Vertical "List" layout of the phone (Settings -> Detail page -> episode card style): artwork on
 * the left, details and the plot on the right, the expand arrow at the end of the row.
 */
@Composable
private fun EpisodeListRow(model: TvDetailsModel, episode: MetaVideo, settings: MetaScreenSettingsUiState) {
    val expandState = rememberOverviewExpandState(episode)
    val info = rememberEpisodeInfo(model, episode, settings)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HeroHorizontalPadding, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .animateContentSize(animationSpec = tween(EPISODE_OVERVIEW_ANIMATION_MS))
                .then(expandState.rightToToggle())
                .tvFocusable(
                    shape = EpisodeCardShape,
                    onClick = { model.onEpisodeClick(episode) },
                    onLongClick = model.onEpisodeManualClick?.let { manual -> { manual(episode) } },
                    focusedScale = 1f,
                    onFocusChange = { expandState.ownerFocused = it },
                )
                .background(MaterialTheme.nuvio.colors.surfaceCard, EpisodeCardShape)
                .nuvioCardDepth(shape = EpisodeCardShape, surface = NuvioCardDepthSurface.EpisodeCards)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(EpisodeListArtworkWidth)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                EpisodeArtwork(model = model, episode = episode, blurred = info.blurArtwork, modifier = Modifier.fillMaxSize())
                EpisodeProgressBar(info, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp))
                EpisodeStatusBadge(episode = episode, info = info, modifier = Modifier.padding(8.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f).padding(top = 2.dp, end = 6.dp),
            ) {
                EpisodeCodeBadge(info.code)
                EpisodeTitle(episode.title)
                EpisodeOverview(episode = episode, state = expandState)
                EpisodeInfoRow(info)
            }
        }
        // Always laid out so the rows keep the same width; focusable only when the plot overflows.
        Box(modifier = Modifier.size(OverviewToggleSize)) {
            if (expandState.canExpand) OverviewToggleButton(state = expandState)
        }
    }
}

/**
 * Expand state of an episode plot. The arrow button is a separate D-pad target (down from a card,
 * right from a list row) that can take the focus only from its own episode, so moving down from
 * a card without a long plot still reaches the next section.
 */
@Stable
private class OverviewExpandState(val toggleRequester: FocusRequester) {
    var expanded by mutableStateOf(false)
    var canExpand by mutableStateOf(false)
    var ownerFocused by mutableStateOf(false)
    var toggleFocused by mutableStateOf(false)

    fun toggle() {
        expanded = !expanded
        log.d { "Episode plot ${if (expanded) "expanded" else "collapsed"}" }
    }

    fun downToToggle(): Modifier = Modifier.focusProperties {
        if (canExpand) down = toggleRequester
    }

    fun rightToToggle(): Modifier = Modifier.focusProperties {
        if (canExpand) right = toggleRequester
    }
}

@Composable
private fun rememberOverviewExpandState(episode: MetaVideo): OverviewExpandState {
    val requester = remember { FocusRequester() }
    return remember(episode.id) { OverviewExpandState(requester) }
}

@Composable
private fun OverviewToggleButton(state: OverviewExpandState) {
    val rotation by animateFloatAsState(
        targetValue = if (state.expanded) 180f else 0f,
        animationSpec = tween(EPISODE_OVERVIEW_ANIMATION_MS),
        label = "episodeOverviewArrow",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(OverviewToggleSize)
            .focusRequester(state.toggleRequester)
            .focusProperties { canFocus = state.ownerFocused || state.toggleFocused }
            .tvFocusable(
                shape = CircleShape,
                onClick = state::toggle,
                focusedScale = 1f,
                borderWidth = 2.dp,
                onFocusChange = { state.toggleFocused = it },
            )
            .background(if (state.toggleFocused) Color.White else Color.White.copy(alpha = 0.14f), CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(
                if (state.expanded) R.string.tv_details_collapse_overview else R.string.tv_details_expand_overview,
            ),
            tint = if (state.toggleFocused) Color.Black else MaterialTheme.nuvio.colors.textPrimary,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun EpisodeArtwork(model: TvDetailsModel, episode: MetaVideo, blurred: Boolean, modifier: Modifier) {
    val artwork = episode.thumbnail ?: model.meta.background
    val alpha = if (episode.available) 1f else 0.5f
    if (blurred) {
        // "Blur unwatched episodes" of the detail page settings (spoiler protection).
        TvBlurredImage(url = artwork, alpha = alpha, contentDescription = episode.title, modifier = modifier)
        return
    }
    AsyncImage(
        model = artwork,
        contentDescription = episode.title,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier,
    )
}

@Composable
private fun EpisodeCodeBadge(code: String?) {
    if (code == null) return
    // Same chip as the phone episode list and Continue Watching.
    EpisodeCodeChip(
        text = code,
        textSize = MaterialTheme.typography.labelSmall.fontSize,
        radius = 7.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 3.dp,
    )
}

@Composable
private fun EpisodeTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, shadow = EpisodeTextShadow),
        color = MaterialTheme.nuvio.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Plot: three lines, the whole text once expanded; reports whether it is cut. */
@Composable
private fun EpisodeOverview(episode: MetaVideo, state: OverviewExpandState) {
    val overview = episode.overview?.trim().orEmpty()
    if (overview.isBlank()) return
    Text(
        text = overview,
        style = MaterialTheme.typography.bodySmall.copy(shadow = EpisodeTextShadow, lineHeight = 17.sp),
        color = Color.White.copy(alpha = 0.9f),
        maxLines = if (state.expanded) Int.MAX_VALUE else EPISODE_OVERVIEW_COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> if (!state.expanded) state.canExpand = layout.hasVisualOverflow },
    )
}

@Composable
private fun EpisodeInfoRow(info: EpisodeInfo) {
    if (info.runtime == null && info.rating == null && info.date == null) return
    val muted = MaterialTheme.nuvio.colors.textSecondary
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        info.runtime?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
            }
        }
        info.rating?.let {
            Text(
                text = "IMDb $it",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF5C518),
                maxLines = 1,
            )
        }
        info.date?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EpisodeProgressBar(info: EpisodeInfo, modifier: Modifier) {
    val progress = info.progress ?: return
    if (progress <= 0f || info.watched) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.nuvio.colors.accent),
        )
    }
}

/** Unavailable label (top end), watched check or the dashed "not started" circle (top start). */
@Composable
private fun BoxScope.EpisodeStatusBadge(episode: MetaVideo, info: EpisodeInfo, modifier: Modifier) {
    val muted = MaterialTheme.nuvio.colors.textSecondary
    when {
        !episode.available -> Text(
            text = stringResource(R.string.tv_details_unavailable).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(modifier)
                .background(Color(0xCC5D4037), RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        info.watched -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(modifier)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.nuvio.colors.accent),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.tv_watched),
                tint = MaterialTheme.nuvio.colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        (info.progress ?: 0f) < 0.02f -> {
            val dashColor = muted.copy(alpha = 0.9f)
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(modifier)
                    .size(28.dp),
            ) {
                drawCircle(
                    color = dashColor,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)),
                )
            }
        }
    }
}

@Composable
private fun CastSection(cast: List<MetaPerson>, onClick: ((MetaPerson) -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        TvSectionTitle(stringResource(R.string.tv_details_cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(cast.take(30), key = { person -> "${person.tmdbId}:${person.name}:${person.role}" }) { person ->
                CastMemberItem(person = person, onClick = onClick)
            }
        }
    }
}

/** Round cast photo with name and role (NuvioTV `CastMemberItem`); OK opens the person page. */
@Composable
private fun CastMemberItem(person: MetaPerson, onClick: ((MetaPerson) -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(CastItemWidth),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CastPhotoSize)
                .tvFocusable(
                    shape = CircleShape,
                    onClick = {
                        log.d { "Cast member opened: ${person.tmdbId}" }
                        onClick?.invoke(person)
                    },
                    focusedScale = 1.06f,
                )
                .background(MaterialTheme.nuvio.colors.surfaceCard, CircleShape),
        ) {
            if (!person.photo.isNullOrBlank()) {
                AsyncImage(
                    model = person.photo,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.nuvio.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.nuvio.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    onClick: (MetaPreview) -> Unit,
) {
    val settings = rememberTvLayoutSettings()
    val metrics = remember(settings) { settings.cardMetrics() }
    val landscape = settings.showsLandscape(items)
    TvContentRow(title = title, modifier = Modifier.padding(top = 18.dp)) {
        items(items, key = MetaPreview::stableKey) { item ->
            if (landscape) {
                TvRowLandscapeCard(
                    artwork = settings.landscapeArtwork(item),
                    title = item.name,
                    width = metrics.landscapeWidth,
                    height = metrics.landscapeHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onClick(item) },
                )
            } else {
                // Outside the home rows NuvioTV uses the base poster size.
                TvRowPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    width = metrics.basePosterWidth,
                    height = metrics.basePosterWidth * 1.5f,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

@Composable
private fun seasonLabel(number: Int): String =
    if (number <= 0) {
        stringResource(R.string.tv_details_specials)
    } else {
        stringResource(R.string.tv_details_season, number)
    }
