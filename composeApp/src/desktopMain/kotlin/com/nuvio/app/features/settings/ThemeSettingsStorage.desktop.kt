package com.nuvio.app.features.settings

import com.nuvio.app.desktop.compat.Context
import com.nuvio.app.desktop.compat.SharedPreferences
import java.util.Locale
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

actual object ThemeSettingsStorage {
    private const val preferencesName = "nuvio_theme_settings"
    private const val selectedThemeKey = "selected_theme"
    private const val customThemeColorsKey = "custom_theme_colors"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val amoledSurfacesEnabledKey = "amoled_surfaces_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val NAV_BAR_STYLE_KEY = "nav_bar_style"
    private const val navBarGlowEnabledKey = "nav_bar_glow_enabled"
    private const val topNavBarEnabledKey = "top_nav_bar_enabled"
    private const val topNavBarStyleKey = "top_nav_bar_style"
    private const val dateFormatKey = "date_format"
    private const val episodeCodeFormatKey = "episode_code_format"
    private val profileScopedSyncKeys = listOf(
        selectedThemeKey,
        customThemeColorsKey,
        amoledEnabledKey,
        amoledSurfacesEnabledKey,
        navBarGlowEnabledKey,
        topNavBarEnabledKey,
        topNavBarStyleKey,
        liquidGlassNativeTabBarEnabledKey,
        NAV_BAR_STYLE_KEY,
        dateFormatKey,
        episodeCodeFormatKey,
    )

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }

    actual fun loadSelectedTheme(): String? =
        preferences?.getString(ProfileScopedKey.of(selectedThemeKey), null)

    actual fun saveSelectedTheme(themeName: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(selectedThemeKey), themeName)
            ?.apply()
    }

    actual fun loadCustomThemeColors(): String? =
        preferences?.getString(ProfileScopedKey.of(customThemeColorsKey), null)

    actual fun saveCustomThemeColors(colors: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(customThemeColorsKey), colors)
            ?.apply()
    }

    actual fun loadAmoledEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(amoledEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(amoledEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadAmoledSurfacesEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(amoledSurfacesEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveAmoledSurfacesEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(amoledSurfacesEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadNavBarGlowEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(navBarGlowEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveNavBarGlowEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(navBarGlowEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadTopNavBarEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(topNavBarEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveTopNavBarEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(topNavBarEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadTopNavBarStyle(): String? =
        preferences?.getString(ProfileScopedKey.of(topNavBarStyleKey), null)

    actual fun saveTopNavBarStyle(styleKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(topNavBarStyleKey), styleKey)
            ?.apply()
    }

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadSelectedAppLanguage(): String? {
        val value = preferences?.getString(selectedAppLanguageKey, null)
        if (value != null) return value
        val legacy = preferences?.getString(ProfileScopedKey.of(selectedAppLanguageKey), null)
        if (legacy != null) saveSelectedAppLanguage(legacy)
        return legacy
    }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        preferences
            ?.edit()
            ?.putString(selectedAppLanguageKey, languageCode)
            ?.apply()
    }

    private val systemLocale: Locale = Locale.getDefault()

    actual fun applySelectedAppLanguage(languageCode: String) {
        // Compose resources follow the JVM default locale on desktop.
        val locale = if (languageCode.equals("device", ignoreCase = true)) {
            systemLocale
        } else {
            Locale.forLanguageTag(languageCode)
        }
        Locale.setDefault(locale)
    }

    actual fun loadNavBarStyle(): String? =
        preferences?.getString(ProfileScopedKey.of(NAV_BAR_STYLE_KEY), null)

    actual fun saveNavBarStyle(styleKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(NAV_BAR_STYLE_KEY), styleKey)
            ?.apply()
    }

    actual fun loadDateFormat(): String? =
        preferences?.getString(ProfileScopedKey.of(dateFormatKey), null)

    actual fun saveDateFormat(formatKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(dateFormatKey), formatKey)
            ?.apply()
    }

    actual fun loadEpisodeCodeFormat(): String? =
        preferences?.getString(ProfileScopedKey.of(episodeCodeFormatKey), null)

    actual fun saveEpisodeCodeFormat(formatKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(episodeCodeFormatKey), formatKey)
            ?.apply()
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadSelectedTheme()?.let { put(selectedThemeKey, encodeSyncString(it)) }
        loadCustomThemeColors()?.let { put(customThemeColorsKey, encodeSyncString(it)) }
        loadNavBarGlowEnabled()?.let { put(navBarGlowEnabledKey, encodeSyncBoolean(it)) }
        loadTopNavBarEnabled()?.let { put(topNavBarEnabledKey, encodeSyncBoolean(it)) }
        loadTopNavBarStyle()?.let { put(topNavBarStyleKey, encodeSyncString(it)) }
        loadAmoledEnabled()?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
        loadAmoledSurfacesEnabled()?.let { put(amoledSurfacesEnabledKey, encodeSyncBoolean(it)) }
        loadLiquidGlassNativeTabBarEnabled()?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
        loadNavBarStyle()?.let { put(NAV_BAR_STYLE_KEY, encodeSyncString(it)) }
        loadDateFormat()?.let { put(dateFormatKey, encodeSyncString(it)) }
        loadEpisodeCodeFormat()?.let { put(episodeCodeFormatKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            profileScopedSyncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncString(selectedThemeKey)?.let(::saveSelectedTheme)
        payload.decodeSyncString(customThemeColorsKey)?.let(::saveCustomThemeColors)
        payload.decodeSyncBoolean(navBarGlowEnabledKey)?.let(::saveNavBarGlowEnabled)
        payload.decodeSyncBoolean(topNavBarEnabledKey)?.let(::saveTopNavBarEnabled)
        payload.decodeSyncString(topNavBarStyleKey)?.let(::saveTopNavBarStyle)
        payload.decodeSyncBoolean(amoledEnabledKey)?.let(::saveAmoledEnabled)
        payload.decodeSyncBoolean(amoledSurfacesEnabledKey)?.let(::saveAmoledSurfacesEnabled)
        payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let(::saveLiquidGlassNativeTabBarEnabled)
        payload.decodeSyncString(NAV_BAR_STYLE_KEY)?.let(::saveNavBarStyle)
        payload.decodeSyncString(dateFormatKey)?.let(::saveDateFormat)
        payload.decodeSyncString(episodeCodeFormatKey)?.let(::saveEpisodeCodeFormat)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }
}
