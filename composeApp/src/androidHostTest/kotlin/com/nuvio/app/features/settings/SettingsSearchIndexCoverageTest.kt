package com.nuvio.app.features.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the settings search index: every settings row (`SettingsSwitchRow`, `SettingsNavigationRow`,
 * `SettingsSliderRow`, ... any `*Row(` call) whose title or description is a literal string resource
 * must also be referenced by `SettingsSearch.kt`, otherwise the search cannot find it.
 *
 * When this fails, add the reported row to `settingsSearchEntries` (title and description), or, if the
 * row must not be searchable, add its string key to [ignoredKeys] with a reason.
 */
class SettingsSearchIndexCoverageTest {

    @Test
    fun everySettingsRowIsIndexedForSearch() {
        val featuresDir = locateFeaturesDir()
        val indexSource = File(featuresDir, "settings/SettingsSearch.kt").readText()
        val indexedKeys = stringKeyRegex.findAll(indexSource).map { it.groupValues[1] }.toSet()

        val missing = settingsSourceFiles(featuresDir)
            .flatMap { file -> rowStringKeys(file).map { (key, line) -> Triple(file, line, key) } }
            .filter { (_, _, key) -> key !in indexedKeys && key !in ignoredKeys }
            .map { (file, line, key) -> "${file.relativeTo(featuresDir).invariantSeparatorsPath}:$line -> $key" }
            .distinct()

        if (missing.isNotEmpty()) {
            fail(
                "Settings rows missing from the search index (SettingsSearch.kt):\n" +
                    missing.joinToString(separator = "\n"),
            )
        }
    }

    @Test
    fun searchNormalizationIgnoresCaseAndAccents() {
        assertEquals("modalita velocita", normalizeSettingsSearchText("Modalità VELOCITÀ"))
        assertEquals("perche cosi", normalizeSettingsSearchText("perché così"))
        assertTrue(normalizeSettingsSearchText("Qualità video").contains(normalizeSettingsSearchText("qualita")))
    }

    private fun settingsSourceFiles(featuresDir: File): List<File> =
        featuresDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val relative = file.relativeTo(featuresDir).invariantSeparatorsPath
                relative.startsWith("settings/") || file.name.contains("Settings")
            }
            .filter { it.name !in ignoredFiles }
            .filter { !it.relativeTo(featuresDir).invariantSeparatorsPath.startsWith("player/") }
            .toList()

    private fun rowStringKeys(file: File): List<Pair<String, Int>> {
        val source = file.readText()
        val result = mutableListOf<Pair<String, Int>>()
        rowCallRegex.findAll(source).forEach { match ->
            val prefix = source.substring((match.range.first - 40).coerceAtLeast(0), match.range.first)
            if (functionDeclarationRegex.containsMatchIn(prefix)) return@forEach
            val body = callBody(source, match.range.last + 1)
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            listOf("title", "description", "subtitle").forEach { argument ->
                Regex("""\b$argument\s*=\s*stringResource\(\s*Res\.string\.(\w+)""")
                    .find(body)
                    ?.let { result += it.groupValues[1] to line }
            }
        }
        return result
    }

    /** Returns the argument list of a call whose opening parenthesis ends right before [start]. */
    private fun callBody(source: String, start: Int): String {
        var depth = 1
        var index = start
        while (depth > 0 && index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            index++
        }
        return source.substring(start, (index - 1).coerceAtLeast(start))
    }

    private fun locateFeaturesDir(): File {
        val relative = "src/commonMain/kotlin/com/nuvio/app/features"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "composeApp/$relative"))
                .firstOrNull { it.isDirectory }
                ?.let { return it }
            dir = dir.parentFile
        }
        error("Cannot locate $relative from ${File("").absolutePath}")
    }

    private companion object {
        val stringKeyRegex = Regex("""Res\.string\.(\w+)""")
        val rowCallRegex = Regex("""\b[A-Z]\w*Row\(""")
        val functionDeclarationRegex = Regex("""fun\s+(<[^>]*>\s*)?$""")

        /** Files whose rows depend on runtime data (one page per configured server, etc.). */
        val ignoredFiles = setOf(
            "SettingsSearch.kt",
            "MediaServerSettingsPage.kt",
            "MediaServersSettingsPage.kt",
        )

        /** String keys of rows that intentionally have no search entry. */
        val ignoredKeys = setOf(
            // TV remote: action shown only while the paired TV is unreachable, not a setting.
            "tv_remote_reconnect",
            // Debug-only row.
            "updates_debug_test_title",
            "updates_debug_test_description",
            // Opens an external web page, there is no settings page to navigate to.
            "compose_settings_page_privacy_policy",
            "compose_settings_root_privacy_policy_description",
            // Lives on the root page next to "Check for updates", which is already indexed.
            "updates_channel_title",
            "updates_channel_description",
            // Live "N of M providers enabled" counter; the row is indexed with its title and hint.
            "settings_playback_provider_priority_order_description",
        )
    }
}
