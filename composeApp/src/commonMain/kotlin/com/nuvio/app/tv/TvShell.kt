package com.nuvio.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.auth.DeviceLinkAuthState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.concurrent.Volatile

/**
 * Alternative presentation layer used by the Android TV app (`tvApp` module).
 *
 * The shared app keeps owning navigation, data loading, sync and playback orchestration;
 * when a [TvShell] is provided through [LocalTvShell], a few screens hand their already
 * computed state to these renderers instead of drawing the phone/tablet layout.
 * On phone, tablet and iOS the local is `null` and every hook falls back to the mobile UI.
 */
class TvShell(
    /** Wraps the whole app inside the Nuvio theme (focus indication, ripple tuning…). */
    val themeOverlay: @Composable (content: @Composable () -> Unit) -> Unit,
    /** Replaces the bottom/floating navigation bar of the root tabs. */
    val tabsChrome: @Composable (chrome: TvTabsChrome, content: @Composable () -> Unit) -> Unit,
    val home: @Composable (model: TvHomeModel) -> Unit,
    val details: @Composable (model: TvDetailsModel) -> Unit,
    /** Sign-in screen (QR / device link). Null keeps the shared email sign-in screen. */
    val auth: (@Composable (model: TvAuthModel) -> Unit)? = null,
    /** Voice search button shown next to the search field; it reports the recognized text. */
    val voiceSearch: (@Composable (onQuery: (String) -> Unit) -> Unit)? = null,
    /** Options menu of a poster / continue-watching card (long OK press), replacing the phone overlay. */
    val posterMenu: (@Composable (menu: TvPosterMenu) -> Unit)? = null,
    /** TV-only layout settings page (Settings -> Appearance -> TV layout). */
    val layoutSettings: TvSettingsPage? = null,
    /** "Phone remote" page (Settings -> Phone remote): pairing QR and paired phones. */
    val phoneRemoteSettings: TvSettingsPage? = null,
)

/** Options of a long-pressed card; [actions] are the shared ones (library, watched, resume…). */
@Immutable
class TvPosterMenu(
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val actions: List<com.nuvio.app.core.ui.PosterZoomOverlayAction>,
    val onDismiss: () -> Unit,
)

/**
 * One-shot action for the next detail page: "watch a random episode" from a card menu opens the
 * series and plays a random episode as soon as its episodes are loaded; "play" from the expanded
 * home hero runs the primary action of the page (play, resume or next episode).
 */
object TvPendingDetailAction {
    private var randomEpisodeFor: String? = null
    private var primaryActionFor: String? = null

    fun requestRandomEpisode(metaId: String) {
        randomEpisodeFor = metaId
        primaryActionFor = null
    }

    /** True once for [metaId] after [requestRandomEpisode]. */
    fun consumeRandomEpisode(metaId: String): Boolean {
        if (randomEpisodeFor != metaId) return false
        randomEpisodeFor = null
        return true
    }

    fun requestPrimaryAction(metaId: String) {
        primaryActionFor = metaId
        randomEpisodeFor = null
    }

    /** True once for [metaId] after [requestPrimaryAction]. */
    fun consumePrimaryAction(metaId: String): Boolean {
        if (primaryActionFor != metaId) return false
        primaryActionFor = null
        return true
    }
}

val LocalTvShell = staticCompositionLocalOf<TvShell?> { null }

@Immutable
class TvTabsChrome(
    val selectedTab: AppScreenTab,
    val tabs: List<AppScreenTab>,
    val activeProfile: NuvioProfile?,
    val contentVisible: Boolean,
    val onTabSelected: (AppScreenTab) -> Unit,
    val onSwitchProfile: () -> Unit,
)

enum class TvHomeStatus {
    Loading,
    Content,
    NoSources,
    Error,
    Empty,
}

/**
 * Rows and model of the TV home are data classes: [HomeScreen] rebuilds them on every recomposition,
 * and equal values let the TV home skip instead of recomposing every row.
 */
@Immutable
sealed interface TvHomeRow {
    val key: String

    @Immutable
    data class Catalog(
        override val key: String,
        val section: HomeCatalogSection,
        val items: List<MetaPreview>,
        val canOpenCatalog: Boolean,
    ) : TvHomeRow

    @Immutable
    data class Folders(
        override val key: String,
        val collection: Collection,
    ) : TvHomeRow

    /** "Top 10" row: [items] are already ranked (rank = index + 1). */
    @Immutable
    data class Top10(
        override val key: String,
        val title: String,
        val items: List<MetaPreview>,
    ) : TvHomeRow
}

@Immutable
data class TvHomeModel(
    val status: TvHomeStatus,
    val errorMessage: String?,
    val heroItems: List<MetaPreview>,
    val continueWatching: List<ContinueWatchingItem>,
    val upcoming: List<ContinueWatchingItem>,
    val rows: List<TvHomeRow>,
    val isPosterWatched: (MetaPreview) -> Boolean,
    val onPosterClick: (MetaPreview) -> Unit,
    val onPosterLongClick: ((MetaPreview) -> Unit)?,
    val onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    val onContinueWatchingLongClick: ((ContinueWatchingItem) -> Unit)?,
    val onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    val onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    val onRetry: () -> Unit,
    /** "Show overall ratings" setting: IMDb rating on the hero. */
    val showImdbRating: Boolean = true,
    /** MDBList active with "Show on hero": external ratings on the hero. */
    val showMdbListRatings: Boolean = false,
    /** Details of the title on the hero (genres, runtime, badges, ratings), cached; the item itself when unavailable. */
    val loadHeroDetails: suspend (MetaPreview) -> MetaPreview = NoHeroDetails,
    /** Library state of a title (same check as the card menu); a new value when the library changes. */
    val isSaved: (MetaPreview) -> Boolean = NotSaved,
    /** Adds or removes a title with the card menu's library action (lists picker, trackers…). */
    val onToggleSaved: ((MetaPreview) -> Unit)? = null,
    /** Opens the details of a title and runs its primary action (play, resume, next episode). */
    val onPlay: ((MetaPreview) -> Unit)? = null,
)

private val NoHeroDetails: suspend (MetaPreview) -> MetaPreview = { it }
private val NotSaved: (MetaPreview) -> Boolean = { false }

@Immutable
class TvDetailsModel(
    val meta: MetaDetails,
    val isSeries: Boolean,
    val primaryActionLabel: String,
    val primaryActionEnabled: Boolean,
    /** Season of the episode the primary action plays (used to preselect the season tab). */
    val primaryActionSeason: Int?,
    val primaryActionEpisode: Int?,
    /** Resume progress of the primary action, when it resumes a partially watched item. */
    val primaryActionProgress: Float?,
    val onPrimaryAction: () -> Unit,
    val onPrimaryActionManual: (() -> Unit)?,
    val seasons: List<TvSeason>,
    val episodeProgress: (MetaVideo) -> Float?,
    val isEpisodeWatched: (MetaVideo) -> Boolean,
    val onEpisodeClick: (MetaVideo) -> Unit,
    val onEpisodeManualClick: ((MetaVideo) -> Unit)?,
    val isSaved: Boolean,
    val onToggleSaved: () -> Unit,
    val isWatched: Boolean,
    val onToggleWatched: () -> Unit,
    val shuffleActive: Boolean,
    val onShuffleClick: (() -> Unit)?,
    val onOpenMeta: (MetaPreview) -> Unit,
    val onBack: () -> Unit,
    /** MDBList is active: show `meta.externalRatings` (draw them with [TvExternalRatingsRow]). */
    val showExternalRatings: Boolean = false,
    /** IMDb rating of each episode, keyed by (season, episode); empty while loading or when hidden. */
    val episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    /** Opens the person page (filmography) of a cast member; null when not available. */
    val onCastClick: ((com.nuvio.app.features.details.MetaPerson) -> Unit)? = null,
    /** Opens the season actions menu (mark season / previous seasons watched), like the phone long press. */
    val onSeasonLongClick: ((Int) -> Unit)? = null,
)

/** State of the TV sign-in screen, backed by `DeviceLinkAuthRepository` (QR login). */
@Immutable
class TvAuthModel(
    val deviceLinkState: DeviceLinkAuthState,
    /** False when the active server does not support device/QR login. */
    val deviceLinkEnabled: Boolean,
    val serverUrl: String,
    val isCustomServer: Boolean,
    val authError: String?,
    val onStartDeviceLink: () -> Unit,
    val onCancelDeviceLink: () -> Unit,
    /** Shows the shared email/password screen; the Back key returns to the QR screen. */
    val onUseEmail: () -> Unit,
    val onContinueWithoutAccount: () -> Unit,
)

@Immutable
class TvSeason(
    val number: Int,
    val episodes: List<MetaVideo>,
)

/**
 * Remote-control access to the active in-app player. Registered while a player screen is on
 * screen inside a [TvShell]; the TV activity routes D-pad/media keys through it.
 */
interface TvPlayerRemote {
    val controlsVisible: Boolean
    val isPlaying: Boolean

    /** A panel, modal or prompt is open: keys must reach it untouched. */
    val overlayOpen: Boolean

    /** The stream "Info" panel is open: it has no buttons, so the D-pad scrolls or closes it. */
    val streamInfoOpen: Boolean

    /** Scrolls the "Info" panel one step; false when there is nothing more to scroll that way. */
    fun scrollStreamInfo(down: Boolean): Boolean

    fun closeStreamInfo()

    fun togglePlayback()

    /** Triggers the visible "skip intro/recap/outro" or "next episode" prompt; false if none is shown. */
    fun activatePrompt(): Boolean

    /**
     * Seeks by the configured interval. With seek thumbnails available it shows the controls and
     * moves the timeline preview instead: the player seeks once the presses stop.
     */
    fun seekStep(forward: Boolean)

    /** A previewed seek step is waiting for the timeline: left/right must keep calling [seekStep]. */
    val seekPreviewPending: Boolean
    fun showControls()
    fun hideControls()

    /** Keeps the controls on screen while the user is navigating them. */
    fun registerActivity()

    /*
     * Dedicated remote buttons. Each returns false when the action is not available right now
     * (wrong content, blocking prompt, watch party lock), so the key falls through to the system.
     */

    /** Subtitles button: opens the subtitle menu, or closes it when already open. */
    fun toggleSubtitleMenu(): Boolean

    /** Audio track button: opens the audio menu, or closes it when already open. */
    fun toggleAudioMenu(): Boolean

    /** Guide button: episodes panel for series, channel list for Live TV (toggles). */
    fun toggleGuidePanel(): Boolean

    /** Next button: next episode, or next channel on Live TV. */
    fun playNext(): Boolean

    /** Previous button: previous channel on Live TV. */
    fun playPrevious(): Boolean

    /** Channel up/down: Live TV zapping ([step] = +1 next, -1 previous). */
    fun zapChannel(step: Int): Boolean

    /** Zoom / aspect button: cycles fit, fill and zoom. */
    fun cycleResizeMode(): Boolean

    /*
     * Paired phone remote (see `com.nuvio.app.features.tvremote`). Both run on the main thread.
     */

    /** Playback state shown by the paired phone. */
    fun phoneRemotePlayback(): com.nuvio.app.features.tvremote.TvRemotePlayback

    /** Applies a phone command (volume commands excluded); null when it was applied. */
    fun handlePhoneRemoteCommand(
        request: com.nuvio.app.features.tvremote.TvRemoteCommandRequest,
    ): com.nuvio.app.features.tvremote.TvRemoteCommandError?
}

/** Process-wide flags for the Android TV app, set by its activity before any UI is shown. */
object TvAppRuntime {
    /**
     * True inside the `tvApp` APK. The in-app updater then only picks release assets whose
     * file name contains the `TV` token, and the phone app skips them (see `isTvApkAssetName`).
     */
    var isTvApp: Boolean = false
}

object TvPlayerRemoteRegistry {
    @Volatile
    var active: TvPlayerRemote? = null
        internal set
}
