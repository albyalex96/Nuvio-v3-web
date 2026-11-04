package com.nuvio.app.features.subdl

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val SUBDL_LOG_TAG = "SubDL"
internal const val SUBDL_PROVIDER_NAME = "SubDL"

/**
 * SubDL preferences, all synced with the profile (API key included) through [SubDlProfileSync].
 * [languages] holds app language codes (`AvailableLanguageOptions`); empty means "use the
 * player's preferred subtitle languages".
 */
@Serializable
internal data class SubDlSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val languages: List<String> = emptyList(),
    val includeHearingImpaired: Boolean = true,
    val includeAiTranslated: Boolean = false,
    val includeSeasonPacks: Boolean = true,
    /** AutoSync may check the file the user picks (one download, like attaching it). */
    val autoSyncEnabled: Boolean = false,
) {
    val isUsable: Boolean
        get() = enabled && apiKey.isNotBlank()
}

internal object SubDlRepository {
    private val log = Logger.withTag(SUBDL_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(SubDlSettings())
    val uiState: StateFlow<SubDlSettings> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = SubDlStorage.loadSettingsPayload()
            ?.let { payload ->
                runCatching { json.decodeFromString<SubDlSettings>(payload) }
                    .onFailure { error -> log.w(error) { "Invalid stored SubDL settings, using defaults" } }
                    .getOrNull()
            }
            ?: SubDlSettings()
    }

    fun snapshot(): SubDlSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun onProfileChanged() {
        hasLoaded = false
        ensureLoaded()
        SubDlLinkResolver.clear()
        log.d { "Profile changed, enabled=${_uiState.value.enabled} key=${_uiState.value.apiKey.isNotBlank()}" }
    }

    /** Account sign-out: the platform cleaner wipes the storage, here only the in-memory state goes. */
    fun clearLocalState() {
        hasLoaded = false
        SubDlLinkResolver.clear()
        _uiState.value = SubDlSettings()
    }

    fun applyRemoteSettings(settings: SubDlSettings) {
        ensureLoaded()
        if (settings == _uiState.value) return
        log.d { "Applying SubDL settings synced with the profile" }
        update { settings }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setApiKey(apiKey: String) = update { it.copy(apiKey = apiKey.trim()) }

    fun setLanguages(languages: List<String>) = update { it.copy(languages = languages.distinct()) }

    fun setIncludeHearingImpaired(include: Boolean) = update { it.copy(includeHearingImpaired = include) }

    fun setIncludeAiTranslated(include: Boolean) = update { it.copy(includeAiTranslated = include) }

    fun setIncludeSeasonPacks(include: Boolean) = update { it.copy(includeSeasonPacks = include) }

    fun setAutoSyncEnabled(enabled: Boolean) = update { it.copy(autoSyncEnabled = enabled) }

    private fun update(transform: (SubDlSettings) -> SubDlSettings) {
        ensureLoaded()
        val updated = transform(_uiState.value)
        if (updated == _uiState.value) return
        _uiState.value = updated
        SubDlStorage.saveSettingsPayload(json.encodeToString(updated))
    }

    internal fun encode(settings: SubDlSettings): String = json.encodeToString(settings)

    internal fun decode(payload: String): SubDlSettings? =
        runCatching { json.decodeFromString<SubDlSettings>(payload) }
            .onFailure { error -> log.w(error) { "Invalid SubDL settings in the profile blob" } }
            .getOrNull()
}
