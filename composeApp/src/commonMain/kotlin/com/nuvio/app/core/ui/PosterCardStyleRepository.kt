package com.nuvio.app.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.SubtitleSizeFormFactor
import com.nuvio.app.features.player.currentSubtitleSizeFormFactor
import com.nuvio.app.isDesktop
import kotlin.math.roundToInt

internal const val DefaultPosterCardWidthDp = 126
internal const val DefaultPosterCardHeightDp = 189
internal const val DefaultPosterCardCornerRadiusDp = 12
internal const val DefaultHoverPreviewOpenDelayMillis = 2_000
internal const val MinHoverPreviewOpenDelayMillis = 500
internal const val MaxHoverPreviewOpenDelayMillis = 5_000
internal const val HoverPreviewOpenDelayStepMillis = 500

internal fun normalizeHoverPreviewOpenDelayMillis(delayMillis: Int): Int =
    (delayMillis.coerceIn(MinHoverPreviewOpenDelayMillis, MaxHoverPreviewOpenDelayMillis) /
        HoverPreviewOpenDelayStepMillis.toFloat()).roundToInt() * HoverPreviewOpenDelayStepMillis

/** Desktop windows are much wider in dp than phones and TVs: presets are drawn this much bigger. */
internal const val DesktopPosterCardScale = 1.4f

private val posterSizeLog = Logger.withTag("PosterCardSize")

/** Size actually drawn for a chosen preset on this device. */
internal fun effectivePosterCardWidthDp(presetWidthDp: Int, desktop: Boolean = isDesktop): Int =
    if (desktop) (presetWidthDp * DesktopPosterCardScale).roundToInt() else presetWidthDp

@Serializable
private data class StoredPosterCardStylePreferences(
    val widthDp: Int = DefaultPosterCardWidthDp,
    val heightDp: Int = DefaultPosterCardHeightDp,
    val cornerRadiusDp: Int = DefaultPosterCardCornerRadiusDp,
    val catalogLandscapeModeEnabled: Boolean = false,
    val hideLabelsEnabled: Boolean = false,
    val alwaysShowLandscapeClearlogo: Boolean = false,
    val hoverScaleEnabled: Boolean = true,
    val hoverPreviewEnabled: Boolean = true,
    val hoverPreviewOpenDelayMillis: Int = DefaultHoverPreviewOpenDelayMillis,
    val hoverPreviewTrailerEnabled: Boolean = false,
    val hoverPreviewTrailerSoundEnabled: Boolean = false,
    /** Preset width per device class (phone, tablet, TV, desktop), synced with the profile. */
    val widthsByDevice: Map<String, Int> = emptyMap(),
)

data class PosterCardStyleUiState(
    /** Width drawn on this device (the preset, scaled up on desktop). */
    val widthDp: Int = effectivePosterCardWidthDp(DefaultPosterCardWidthDp),
    val heightDp: Int = effectivePosterCardWidthDp(DefaultPosterCardWidthDp) * 3 / 2,
    val cornerRadiusDp: Int = DefaultPosterCardCornerRadiusDp,
    val catalogLandscapeModeEnabled: Boolean = false,
    val hideLabelsEnabled: Boolean = false,
    val alwaysShowLandscapeClearlogo: Boolean = false,
    val hoverScaleEnabled: Boolean = true,
    val hoverPreviewEnabled: Boolean = true,
    val hoverPreviewOpenDelayMillis: Int = DefaultHoverPreviewOpenDelayMillis,
    val hoverPreviewTrailerEnabled: Boolean = false,
    val hoverPreviewTrailerSoundEnabled: Boolean = false,
    /** Preset chosen for this device class (what the settings show). */
    val presetWidthDp: Int = DefaultPosterCardWidthDp,
    val device: SubtitleSizeFormFactor = SubtitleSizeFormFactor.Mobile,
    val widthsByDevice: Map<SubtitleSizeFormFactor, Int> = emptyMap(),
)

object PosterCardStyleRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(defaultState())
    val uiState: StateFlow<PosterCardStyleUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    /** Legacy single width: still written so older app versions keep a sensible size. */
    private var legacyWidthDp = DefaultPosterCardWidthDp

    private fun defaultState(): PosterCardStyleUiState = stateFor(currentSubtitleSizeFormFactor(), emptyMap(), DefaultPosterCardWidthDp)

    private fun stateFor(
        device: SubtitleSizeFormFactor,
        widths: Map<SubtitleSizeFormFactor, Int>,
        legacyWidth: Int,
        base: PosterCardStyleUiState = PosterCardStyleUiState(),
    ): PosterCardStyleUiState {
        val preset = widths[device] ?: legacyWidth
        val width = effectivePosterCardWidthDp(preset)
        return base.copy(
            widthDp = width,
            heightDp = (width * 3) / 2,
            presetWidthDp = preset,
            device = device,
            widthsByDevice = widths,
        )
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        legacyWidthDp = DefaultPosterCardWidthDp
        _uiState.value = defaultState()
    }

    fun setWidthDp(widthDp: Int) {
        ensureLoaded()
        val current = _uiState.value
        if (current.presetWidthDp == widthDp && current.widthsByDevice[current.device] == widthDp) return
        val widths = current.widthsByDevice + (current.device to widthDp)
        if (current.device == SubtitleSizeFormFactor.Mobile) legacyWidthDp = widthDp
        _uiState.value = stateFor(current.device, widths, legacyWidthDp, current)
        posterSizeLog.i { "Poster width of ${current.device.storageSuffix} set to $widthDp dp (drawn ${_uiState.value.widthDp} dp)" }
        persist()
    }

    fun setCornerRadiusDp(cornerRadiusDp: Int) {
        ensureLoaded()
        if (_uiState.value.cornerRadiusDp == cornerRadiusDp) return
        _uiState.value = _uiState.value.copy(cornerRadiusDp = cornerRadiusDp)
        persist()
    }

    fun setCatalogLandscapeModeEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.catalogLandscapeModeEnabled == enabled) return
        _uiState.value = _uiState.value.copy(catalogLandscapeModeEnabled = enabled)
        persist()
    }

    fun setHideLabelsEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.hideLabelsEnabled == enabled) return
        _uiState.value = _uiState.value.copy(hideLabelsEnabled = enabled)
        persist()
    }

    fun setAlwaysShowLandscapeClearlogo(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.alwaysShowLandscapeClearlogo == enabled) return
        _uiState.value = _uiState.value.copy(alwaysShowLandscapeClearlogo = enabled)
        persist()
    }

    fun setHoverScaleEnabled(enabled: Boolean) = update { it.copy(hoverScaleEnabled = enabled) }

    fun setHoverPreviewEnabled(enabled: Boolean) = update { it.copy(hoverPreviewEnabled = enabled) }

    fun setHoverPreviewOpenDelayMillis(delayMillis: Int) =
        update { it.copy(hoverPreviewOpenDelayMillis = normalizeHoverPreviewOpenDelayMillis(delayMillis)) }

    fun setHoverPreviewTrailerEnabled(enabled: Boolean) = update { it.copy(hoverPreviewTrailerEnabled = enabled) }

    fun setHoverPreviewTrailerSoundEnabled(enabled: Boolean) = update { it.copy(hoverPreviewTrailerSoundEnabled = enabled) }

    private fun update(change: (PosterCardStyleUiState) -> PosterCardStyleUiState) {
        ensureLoaded()
        val next = change(_uiState.value)
        if (next == _uiState.value) return
        _uiState.value = next
        persist()
    }

    fun resetToDefaults() {
        ensureLoaded()
        val defaults = defaultState()
        if (_uiState.value == defaults) return
        legacyWidthDp = DefaultPosterCardWidthDp
        _uiState.value = defaults
        persist()
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = PosterCardStyleStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) {
            legacyWidthDp = DefaultPosterCardWidthDp
            _uiState.value = defaultState()
            return
        }

        val stored = runCatching {
            json.decodeFromString<StoredPosterCardStylePreferences>(payload)
        }.getOrNull()

        _uiState.value = if (stored != null) {
            legacyWidthDp = stored.widthDp.takeIf { it > 0 } ?: DefaultPosterCardWidthDp
            val widths = stored.widthsByDevice.mapNotNull { (suffix, width) ->
                SubtitleSizeFormFactor.entries.firstOrNull { it.storageSuffix == suffix }
                    ?.takeIf { width > 0 }
                    ?.let { it to width }
            }.toMap()
            val cornerRadiusDp = stored.cornerRadiusDp.coerceAtLeast(0)
            stateFor(currentSubtitleSizeFormFactor(), widths, legacyWidthDp).copy(
                cornerRadiusDp = cornerRadiusDp,
                catalogLandscapeModeEnabled = stored.catalogLandscapeModeEnabled,
                hideLabelsEnabled = stored.hideLabelsEnabled,
                alwaysShowLandscapeClearlogo = stored.alwaysShowLandscapeClearlogo,
                hoverScaleEnabled = stored.hoverScaleEnabled,
                hoverPreviewEnabled = stored.hoverPreviewEnabled,
                hoverPreviewOpenDelayMillis = normalizeHoverPreviewOpenDelayMillis(stored.hoverPreviewOpenDelayMillis),
                hoverPreviewTrailerEnabled = stored.hoverPreviewTrailerEnabled,
                hoverPreviewTrailerSoundEnabled = stored.hoverPreviewTrailerSoundEnabled,
            )
        } else {
            legacyWidthDp = DefaultPosterCardWidthDp
            defaultState()
        }
    }

    private fun persist() {
        PosterCardStyleStorage.savePayload(
            json.encodeToString(
                StoredPosterCardStylePreferences(
                    widthDp = legacyWidthDp,
                    heightDp = (legacyWidthDp * 3) / 2,
                    cornerRadiusDp = _uiState.value.cornerRadiusDp,
                    catalogLandscapeModeEnabled = _uiState.value.catalogLandscapeModeEnabled,
                    hideLabelsEnabled = _uiState.value.hideLabelsEnabled,
                    alwaysShowLandscapeClearlogo = _uiState.value.alwaysShowLandscapeClearlogo,
                    hoverScaleEnabled = _uiState.value.hoverScaleEnabled,
                    hoverPreviewEnabled = _uiState.value.hoverPreviewEnabled,
                    hoverPreviewOpenDelayMillis = _uiState.value.hoverPreviewOpenDelayMillis,
                    hoverPreviewTrailerEnabled = _uiState.value.hoverPreviewTrailerEnabled,
                    hoverPreviewTrailerSoundEnabled = _uiState.value.hoverPreviewTrailerSoundEnabled,
                    widthsByDevice = _uiState.value.widthsByDevice.mapKeys { it.key.storageSuffix },
                ),
            ),
        )
    }
}