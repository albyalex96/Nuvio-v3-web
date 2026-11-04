package com.nuvio.app.features.opensubtitles

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val OPENSUBTITLES_LOG_TAG = "OpenSubtitles"
internal const val OPENSUBTITLES_PROVIDER_NAME = "OpenSubtitles"

/**
 * OpenSubtitles preferences, all synced with the profile (API key included) through
 * [OpenSubtitlesProfileSync]. [languages] holds app language codes (`AvailableLanguageOptions`);
 * empty means "use the player's preferred subtitle languages".
 */
@Serializable
internal data class OpenSubtitlesSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val languages: List<String> = emptyList(),
    val includeHearingImpaired: Boolean = true,
    val includeMachineTranslated: Boolean = false,
    /** AutoSync may check the file the user picks (one download, like attaching it). */
    val autoSyncEnabled: Boolean = false,
) {
    val isUsable: Boolean
        get() = enabled && apiKey.isNotBlank()
}

internal object OpenSubtitlesRepository {
    private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(OpenSubtitlesSettings())
    val uiState: StateFlow<OpenSubtitlesSettings> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = OpenSubtitlesStorage.loadSettingsPayload()
            ?.let { payload ->
                runCatching { json.decodeFromString<OpenSubtitlesSettings>(payload) }
                    .onFailure { error -> log.w(error) { "Invalid stored OpenSubtitles settings, using defaults" } }
                    .getOrNull()
            }
            ?: OpenSubtitlesSettings()
    }

    fun snapshot(): OpenSubtitlesSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun onProfileChanged() {
        hasLoaded = false
        ensureLoaded()
        OpenSubtitlesLinkResolver.clear()
        log.d { "Profile changed, enabled=${_uiState.value.enabled} key=${_uiState.value.apiKey.isNotBlank()}" }
    }

    /** Account sign-out: the platform cleaner wipes the storage, here only the in-memory state goes. */
    fun clearLocalState() {
        hasLoaded = false
        OpenSubtitlesLinkResolver.clear()
        _uiState.value = OpenSubtitlesSettings()
    }

    fun applyRemoteSettings(settings: OpenSubtitlesSettings) {
        ensureLoaded()
        if (settings == _uiState.value) return
        log.d { "Applying OpenSubtitles settings synced with the profile" }
        update { settings }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setApiKey(apiKey: String) {
        update { it.copy(apiKey = apiKey.trim()) }
        OpenSubtitlesLinkResolver.clear()
    }

    fun setLanguages(languages: List<String>) = update { it.copy(languages = languages.distinct()) }

    fun setIncludeHearingImpaired(include: Boolean) = update { it.copy(includeHearingImpaired = include) }

    fun setIncludeMachineTranslated(include: Boolean) = update { it.copy(includeMachineTranslated = include) }

    fun setAutoSyncEnabled(enabled: Boolean) = update { it.copy(autoSyncEnabled = enabled) }

    private fun update(transform: (OpenSubtitlesSettings) -> OpenSubtitlesSettings) {
        ensureLoaded()
        val updated = transform(_uiState.value)
        if (updated == _uiState.value) return
        _uiState.value = updated
        OpenSubtitlesStorage.saveSettingsPayload(json.encodeToString(updated))
    }

    internal fun encode(settings: OpenSubtitlesSettings): String = json.encodeToString(settings)

    internal fun decode(payload: String): OpenSubtitlesSettings? =
        runCatching { json.decodeFromString<OpenSubtitlesSettings>(payload) }
            .onFailure { error -> log.w(error) { "Invalid OpenSubtitles settings in the profile blob" } }
            .getOrNull()
}
