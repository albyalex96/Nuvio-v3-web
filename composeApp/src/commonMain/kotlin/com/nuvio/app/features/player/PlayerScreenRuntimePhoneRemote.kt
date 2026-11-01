package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.tvremote.TvRemoteCommandError
import com.nuvio.app.features.tvremote.TvRemoteCommandRequest
import com.nuvio.app.features.tvremote.TvRemoteCommandType
import com.nuvio.app.features.tvremote.TvRemoteNextEpisode
import com.nuvio.app.features.tvremote.TvRemotePlayback

/*
 * Player side of the phone remote (Android TV app): the playback state published to the paired
 * phone and the commands it sends. Same rules as the on-screen controls (watch party locks,
 * skip targets, next-episode stream search with the user's settings).
 */

private val log = Logger.withTag("TvRemote")

/** Subtitle the phone remote turned off, restored when it turns subtitles back on. */
internal sealed interface PhoneRemoteSubtitleChoice {
    data class BuiltIn(val index: Int) : PhoneRemoteSubtitleChoice
    data class Server(val index: Int) : PhoneRemoteSubtitleChoice
    data class Addon(val selectionKey: String) : PhoneRemoteSubtitleChoice
}

/** Built-in (embedded) subtitle chosen by the user, or -1 to turn subtitles off. */
internal fun PlayerScreenRuntime.selectBuiltInSubtitleTrack(index: Int) {
    if (serverSubtitleTracks.isNotEmpty() && index >= 0) {
        selectServerSubtitleTrack(index)
        return
    }
    val wasCustom = useCustomSubtitles
    isUserExplicitSubtitleSelection = true
    preferredSubtitleSelectionApplied = true
    selectedSubtitleIndex = index
    selectedAddonSubtitleId = null
    useCustomSubtitles = false
    persistInternalSubtitlePreference(subtitleTracks.firstOrNull { it.index == index })
    if (wasCustom) {
        playerController?.clearExternalSubtitleAndSelect(index)
    } else {
        playerController?.selectSubtitleTrack(index)
    }
    if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
}

/** External (addon / OpenSubtitles / SubDL) subtitle chosen by the user. */
internal fun PlayerScreenRuntime.selectAddonSubtitle(addon: AddonSubtitle) {
    isUserExplicitSubtitleSelection = true
    selectedAddonSubtitleId = addon.selectionKey
    selectedSubtitleIndex = -1
    useCustomSubtitles = true
    preferredSubtitleSelectionApplied = true
    persistAddonSubtitlePreference(addon)
    attachSelectedAddonSubtitleWithAutoSync(addon.url) // AutoSync hook
    if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
}

private fun PlayerScreenRuntime.currentSubtitleChoice(): PhoneRemoteSubtitleChoice? {
    serverSubtitleTracks.firstOrNull { it.isSelected }?.let { return PhoneRemoteSubtitleChoice.Server(it.index) }
    if (useCustomSubtitles) {
        selectedAddonSubtitleId?.let { return PhoneRemoteSubtitleChoice.Addon(it) }
    }
    if (selectedSubtitleIndex >= 0) return PhoneRemoteSubtitleChoice.BuiltIn(selectedSubtitleIndex)
    return null
}

private fun PlayerScreenRuntime.currentSubtitleLabel(): String? =
    when (val choice = currentSubtitleChoice()) {
        is PhoneRemoteSubtitleChoice.Server -> serverSubtitleTracks.firstOrNull { it.index == choice.index }?.label
        is PhoneRemoteSubtitleChoice.BuiltIn -> subtitleTracks.firstOrNull { it.index == choice.index }?.label
        is PhoneRemoteSubtitleChoice.Addon -> addonSubtitles.firstOrNull { it.selectionKey == choice.selectionKey }
            ?.let { subtitle -> subtitle.display.ifBlank { subtitle.language } }
        null -> null
    }?.takeIf { it.isNotBlank() }

private fun PlayerScreenRuntime.hasAnySubtitle(): Boolean =
    serverSubtitleTracks.isNotEmpty() || subtitleTracks.isNotEmpty() || visibleAddonSubtitles.isNotEmpty()

private fun PlayerScreenRuntime.applySubtitleChoice(choice: PhoneRemoteSubtitleChoice): Boolean =
    when (choice) {
        is PhoneRemoteSubtitleChoice.Server -> {
            if (serverSubtitleTracks.none { it.index == choice.index }) {
                false
            } else {
                selectServerSubtitleTrack(choice.index)
                true
            }
        }
        is PhoneRemoteSubtitleChoice.BuiltIn -> {
            if (subtitleTracks.none { it.index == choice.index }) {
                false
            } else {
                selectBuiltInSubtitleTrack(choice.index)
                true
            }
        }
        is PhoneRemoteSubtitleChoice.Addon -> {
            val subtitle = addonSubtitles.firstOrNull { it.selectionKey == choice.selectionKey }
            if (subtitle == null) {
                false
            } else {
                selectAddonSubtitle(subtitle)
                true
            }
        }
    }

private val NON_LANGUAGE_SUBTITLE_OPTIONS = setOf(
    SubtitleLanguageOption.NONE,
    SubtitleLanguageOption.DEVICE,
    SubtitleLanguageOption.FORCED,
)

/** Subtitle to turn on when nothing was turned off before: preferred languages first. */
private fun PlayerScreenRuntime.defaultSubtitleChoice(): PhoneRemoteSubtitleChoice? {
    val languages = listOf(
        playerSettingsUiState.preferredSubtitleLanguage,
        playerSettingsUiState.secondaryPreferredSubtitleLanguage,
    ).filterNotNull().filter { it.isNotBlank() && it !in NON_LANGUAGE_SUBTITLE_OPTIONS }

    serverSubtitleTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
        val track = languages.firstNotNullOfOrNull { language ->
            tracks.firstOrNull { languageMatchesPreference(it.language, language) }
        } ?: tracks.first()
        return PhoneRemoteSubtitleChoice.Server(track.index)
    }
    val builtIn = subtitleTracks.filterNot { it.isForced }.ifEmpty { subtitleTracks }
    languages.firstNotNullOfOrNull { language ->
        builtIn.firstOrNull { languageMatchesPreference(it.language, language) }
    }?.let { return PhoneRemoteSubtitleChoice.BuiltIn(it.index) }
    val addons = visibleAddonSubtitles
    languages.firstNotNullOfOrNull { language ->
        addons.firstOrNull { languageMatchesPreference(it.language, language) }
    }?.let { return PhoneRemoteSubtitleChoice.Addon(it.selectionKey) }
    builtIn.firstOrNull()?.let { return PhoneRemoteSubtitleChoice.BuiltIn(it.index) }
    return addons.firstOrNull()?.let { PhoneRemoteSubtitleChoice.Addon(it.selectionKey) }
}

/** Turns subtitles off (remembering the track) or back on; false when there is nothing to show. */
internal fun PlayerScreenRuntime.togglePhoneRemoteSubtitles(): Boolean {
    val current = currentSubtitleChoice()
    if (current != null) {
        phoneRemoteSubtitleRestore = current
        if (current is PhoneRemoteSubtitleChoice.Server) {
            clearServerSubtitleTrack()
        } else {
            selectBuiltInSubtitleTrack(-1)
        }
        log.i { "Phone remote: subtitles off" }
        return true
    }
    val restored = phoneRemoteSubtitleRestore?.let { applySubtitleChoice(it) } == true
    if (restored) {
        log.i { "Phone remote: subtitles restored" }
        return true
    }
    val fallback = defaultSubtitleChoice() ?: run {
        log.d { "Phone remote: no subtitle to turn on" }
        return false
    }
    log.i { "Phone remote: subtitles on (${fallback::class.simpleName})" }
    return applySubtitleChoice(fallback)
}

/** Skips the intro / recap / outro on screen; false when there is none. */
internal fun PlayerScreenRuntime.skipActiveSegment(): Boolean {
    val interval = activeSkipInterval ?: return false
    val durationMs = playbackSnapshot.durationMs
    val action = interval.internalSkipAction(skipIntervals, durationMs) ?: return false
    val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
    // Same watch party rules as the on-screen skip button.
    val seekMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) else requestedMs
    if (seekMs != null) {
        playerController?.seekTo(seekMs)
        scheduleProgressSyncAfterSeek()
        publishPartyPlayback(positionMs = seekMs)
        skipIntervalDismissed = true
        log.d { "Skipped ${interval.type} to ${seekMs}ms" }
    }
    return true
}

/** Next episode is ready to be started (aired, no search or countdown already running). */
internal fun PlayerScreenRuntime.canStartNextEpisode(): Boolean {
    val next = nextEpisodeInfo ?: return false
    return next.hasAired && !nextEpisodeAutoPlaySearching && nextEpisodeAutoPlayCountdown == null
}

internal val PlayerScreenRuntime.phoneRemoteSessionKey: String
    get() = listOf(parentMetaId, activeVideoId.orEmpty(), activeSeasonNumber ?: "", activeEpisodeNumber ?: "")
        .joinToString("|")

/** State published to the phone remote. */
internal fun PlayerScreenRuntime.phoneRemotePlayback(): TvRemotePlayback {
    val snapshot = playbackSnapshot
    val skipInterval = activeSkipInterval
    val skipAction = skipInterval?.internalSkipAction(skipIntervals, snapshot.durationMs)
    val next = nextEpisodeInfo?.takeIf { !isLiveTvPlayback && it.hasAired }
    val artwork = listOf(activeEpisodeThumbnail, background, poster)
        .firstOrNull { it?.startsWith("https://", ignoreCase = true) == true }
    return TvRemotePlayback(
        sessionKey = phoneRemoteSessionKey,
        title = title,
        seasonNumber = activeSeasonNumber,
        episodeNumber = activeEpisodeNumber,
        episodeTitle = activeEpisodeTitle?.takeIf { it.isNotBlank() },
        artworkUrl = artwork,
        logoUrl = activeLogo?.takeIf { it.startsWith("https://", ignoreCase = true) },
        isPlaying = snapshot.isPlaying,
        isBuffering = snapshot.isLoading,
        isEnded = snapshot.isEnded,
        positionMs = snapshot.positionMs.coerceAtLeast(0L),
        durationMs = snapshot.durationMs.coerceAtLeast(0L),
        playbackSpeed = snapshot.playbackSpeed,
        isLive = isLiveTvPlayback,
        canSeek = !isLiveTvPlayback && snapshot.durationMs > 0L,
        seekIntervalSeconds = playerSettingsUiState.seekIntervalSeconds,
        subtitlesAvailable = hasAnySubtitle(),
        subtitlesEnabled = currentSubtitleChoice() != null,
        subtitleLabel = currentSubtitleLabel(),
        skipSegmentType = skipInterval?.type?.takeIf { skipAction != null },
        skipToPostCredits = skipAction?.skipsToPostCredits == true,
        nextEpisode = next?.let {
            TvRemoteNextEpisode(
                seasonNumber = it.season,
                episodeNumber = it.episode,
                title = it.title.takeIf { title -> title.isNotBlank() },
                available = canStartNextEpisode(),
            )
        },
    )
}

/** Runs a phone command on the player; null when it was applied. Volume commands are handled by the TV app. */
internal fun PlayerScreenRuntime.handlePhoneRemoteCommand(request: TvRemoteCommandRequest): TvRemoteCommandError? {
    val sessionKey = request.sessionKey
    if (sessionKey != null && sessionKey != phoneRemoteSessionKey) return TvRemoteCommandError.StaleSession
    log.d { "Phone remote command ${request.command}" }
    return when (request.command) {
        TvRemoteCommandType.Play -> {
            setPlaybackPlaying(true)
            null
        }
        TvRemoteCommandType.Pause -> {
            setPlaybackPlaying(false)
            null
        }
        TvRemoteCommandType.TogglePlayback -> {
            togglePlayback()
            null
        }
        TvRemoteCommandType.SeekStep -> {
            if (isLiveTvPlayback || playbackSnapshot.durationMs <= 0L) return TvRemoteCommandError.Unavailable
            val stepMs = playerSettingsUiState.seekIntervalSeconds * 1_000L
            seekBy(if (request.forward == false) -stepMs else stepMs)
            null
        }
        TvRemoteCommandType.SeekTo -> {
            val durationMs = playbackSnapshot.durationMs
            if (isLiveTvPlayback || durationMs <= 0L) return TvRemoteCommandError.Unavailable
            val target = (request.positionMs ?: return TvRemoteCommandError.Unavailable)
                .coerceIn(0L, (durationMs - 1).coerceAtLeast(0L))
            seekBy(target - currentPlaybackPositionMs())
            null
        }
        TvRemoteCommandType.SkipSegment ->
            if (skipActiveSegment()) null else TvRemoteCommandError.Unavailable
        TvRemoteCommandType.NextEpisode -> {
            if (isLiveTvPlayback || !canStartNextEpisode()) return TvRemoteCommandError.Unavailable
            if (partyBlocksControl()) return TvRemoteCommandError.Unavailable
            log.i { "Phone remote: playing the next episode" }
            playNextEpisode()
            null
        }
        TvRemoteCommandType.ToggleSubtitles ->
            if (togglePhoneRemoteSubtitles()) null else TvRemoteCommandError.Unavailable
        TvRemoteCommandType.VolumeUp,
        TvRemoteCommandType.VolumeDown,
        TvRemoteCommandType.SetVolume,
        TvRemoteCommandType.ToggleMute,
        -> TvRemoteCommandError.Unavailable
    }
}
