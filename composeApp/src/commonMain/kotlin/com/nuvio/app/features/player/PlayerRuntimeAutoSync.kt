package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.nuvio.app.features.autosync.AutoSyncPlayerController
import com.nuvio.app.features.autosync.AutoSyncPreferencesRepository
import com.nuvio.app.features.autosync.AutoSyncSubtitleCandidate
import co.touchlab.kermit.Logger
import com.nuvio.app.features.opensubtitles.OpenSubtitlesRepository
import com.nuvio.app.features.opensubtitles.isOpenSubtitlesPlaceholderUrl
import com.nuvio.app.features.subdl.SubDlRepository
import com.nuvio.app.features.subdl.isSubDlPlaceholderUrl
import kotlinx.coroutines.flow.collect

private val autoSyncIntegrationLog = Logger.withTag("AutoSyncIntegrations")

/**
 * Whether AutoSync may check the integration subtitle at [url] (OpenSubtitles, SubDL): off by
 * default, each integration page has a switch. Add-on subtitles are always allowed.
 */
internal fun isIntegrationAutoSyncAllowed(url: String): Boolean = when {
    isOpenSubtitlesPlaceholderUrl(url) -> OpenSubtitlesRepository.snapshot().autoSyncEnabled
    isSubDlPlaceholderUrl(url) -> SubDlRepository.snapshot().autoSyncEnabled
    else -> true
}

/**
 * Downloads the integration file (one download of its quota, then cached) and checks it with
 * AutoSync as the user's pick: it is never swapped for another subtitle.
 */
private fun PlayerScreenRuntime.attachIntegrationSubtitleWithAutoSync(
    url: String,
    controller: PlayerEngineController,
) {
    autoSyncIntegrationLog.i { "AutoSync on integration subtitle $url" }
    attachExternalSubtitleUrl(url) { localUri ->
        controller.setSubtitleUriWithSelectedAutoSync(localUri)
    }
}

// Integration files (OpenSubtitles, SubDL) are never alternatives: each check would spend a quota-limited
// download. With their AutoSync switch on, only the file the user picked is checked.
private fun List<AddonSubtitle>.toAutoSyncCandidates(): List<AutoSyncSubtitleCandidate> =
    filter { it.source == AddonSubtitleSource.Addon }.map { subtitle ->
        AutoSyncSubtitleCandidate(
            url = subtitle.url,
            language = subtitle.language,
            name = subtitle.display,
        )
    }

private fun PlayerScreenRuntime.currentAutoSyncCandidates(): List<AutoSyncSubtitleCandidate> =
    addonSubtitles.toAutoSyncCandidates()

internal fun PlayerScreenRuntime.configureAutoSyncController(
    controller: PlayerEngineController,
) {
    controller.setAutoSyncSubtitleCandidates(currentAutoSyncCandidates())
    controller.setAutoSyncAppliedListener { subtitleUrl, delayMs ->
        val listedUrl = listedExternalSubtitleUrl(subtitleUrl)
        val appliedSubtitle = addonSubtitles.firstOrNull { it.url == listedUrl }
        selectedAddonSubtitleId = appliedSubtitle?.selectionKey ?: subtitleUrl
        selectedSubtitleIndex = -1
        useCustomSubtitles = true
        preferredSubtitleSelectionApplied = true
        if (appliedSubtitle != null && isUserExplicitSubtitleSelection) {
            persistAddonSubtitlePreference(appliedSubtitle)
        }

        val appliedDelayMs = delayMs.coerceIn(
            SUBTITLE_DELAY_MIN_MS,
            SUBTITLE_DELAY_MAX_MS,
        )
        subtitleDelayMs = appliedDelayMs
        PlayerTrackPreferenceStorage.saveSubtitleDelayMs(
            playbackSession.videoId,
            appliedDelayMs,
        )
    }
}

@Composable
internal fun PlayerScreenRuntime.BindAutoSyncRuntimeEffects() {
    val activeController = playerController
    DisposableEffect(activeController) {
        // Runs once playerController is set, which Nuvio only does for the live playback key.
        activeController?.let { configureAutoSyncController(it) }
        onDispose { activeController?.setAutoSyncAppliedListener(null) }
    }
    LaunchedEffect(playerController, playbackSnapshot.isLoading, isLoadingAddonSubtitles, addonSubtitles) {
        // Add-ons can finish after Nuvio has completed its first track scan with no subtitle.
        // Retry that automatic pick only for AutoSync, while the user still has no selection.
        if (playerController !is AutoSyncPlayerController || playbackSnapshot.isLoading) return@LaunchedEffect
        if (!AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value) return@LaunchedEffect
        if (isLoadingAddonSubtitles || addonSubtitles.isEmpty()) return@LaunchedEffect
        if (!preferredSubtitleSelectionApplied || isUserExplicitSubtitleSelection) return@LaunchedEffect
        if (selectedSubtitleIndex >= 0 || selectedAddonSubtitleId != null) return@LaunchedEffect
        preferredSubtitleSelectionApplied = false
        refreshTracks()
    }
    LaunchedEffect(playerController, externalSubtitles) {
        val controller = playerController ?: return@LaunchedEffect
        SubtitleRepository.addonSubtitles.collect { repositorySubtitles ->
            controller.setAutoSyncSubtitleCandidates(
                mergeStreamAndAddonSubtitles(
                    repositorySubtitles,
                    externalSubtitles,
                ).toAutoSyncCandidates(),
            )
        }
    }
}

/** The user picked an addon subtitle: let AutoSync check it without replacing their choice. */
internal fun PlayerScreenRuntime.attachSelectedAddonSubtitleWithAutoSync(url: String) {
    subtitleAutoSyncState = SubtitleAutoSyncUiState()
    if (isIntegrationSubtitlePlaceholderUrl(url)) {
        val controller = playerController
        if (controller != null && isIntegrationAutoSyncAllowed(url)) {
            attachIntegrationSubtitleWithAutoSync(url, controller)
        } else {
            // AutoSync is off for this integration: attach the file as it is.
            attachExternalSubtitleUrl(url) { playerController?.setSubtitleUri(it) }
        }
        return
    }
    playerController?.setSubtitleUriWithSelectedAutoSync(url)
}

/** The user's own pick for this content: sync it at start, never swap in another subtitle. */
internal fun PlayerScreenRuntime.maybeAutoSyncRestoredSubtitleAtStart(url: String): Boolean {
    val isIntegration = isIntegrationSubtitlePlaceholderUrl(url)
    if (isIntegration && !isIntegrationAutoSyncAllowed(url)) return false
    val controller = playerController ?: return false
    if (controller !is AutoSyncPlayerController) return false
    if (!AutoSyncPreferencesRepository.claimStartupRun(hashCode(), activePlaybackIdentity)) {
        return false
    }
    if (isIntegration) {
        attachIntegrationSubtitleWithAutoSync(url, controller)
    } else {
        controller.setSubtitleUriWithSelectedAutoSync(url)
    }
    return true
}

internal fun PlayerScreenRuntime.maybeAutoSyncPreferredSubtitleAtStart(
    subtitle: AddonSubtitle,
): Boolean {
    if (isUserExplicitSubtitleSelection) return false
    val isIntegration = isIntegrationSubtitlePlaceholderUrl(subtitle.url)
    if (isIntegration && !isIntegrationAutoSyncAllowed(subtitle.url)) return false
    val preferredLanguage =
        normalizeLanguageCode(playerSettingsUiState.preferredSubtitleLanguage) ?: return false
    if (
        preferredLanguage.isBlank() ||
        preferredLanguage == SubtitleLanguageOption.NONE ||
        preferredLanguage == SubtitleLanguageOption.FORCED
    ) {
        return false
    }

    val controller = playerController ?: return false
    if (controller !is AutoSyncPlayerController) return false
    if (!AutoSyncPreferencesRepository.claimStartupRun(hashCode(), activePlaybackIdentity)) {
        return false
    }
    if (isIntegration) {
        // Other integration files are never tried: each one would spend a download.
        attachIntegrationSubtitleWithAutoSync(subtitle.url, controller)
    } else {
        controller.setSubtitleUriWithAutoSync(subtitle.url)
    }
    return true
}
