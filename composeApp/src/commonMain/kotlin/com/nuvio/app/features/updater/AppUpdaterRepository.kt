package com.nuvio.app.features.updater

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_github_api_error
import org.jetbrains.compose.resources.getString

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
)

internal class NoChannelReleaseException : IllegalStateException()

internal object AppUpdaterRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getLatestChannelUpdate(channel: UpdateChannel): Result<AppUpdate> = runCatching {
        val response = httpRequestRaw(
            method = "GET",
            url = "https://api.github.com/repos/albyalex96/NuvioMobile-V2/${releasePath(channel)}",
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "NuvioMobile",
            ),
            body = "",
        )
        currentCoroutineContext().ensureActive()
        if (response.status == 404) throw NoChannelReleaseException()
        if (response.status !in 200..299) {
            error(getString(Res.string.updates_github_api_error, response.status))
        }
        selectUpdate(response.body, channel, AppUpdaterPlatform.getSupportedAbis())
            ?: throw NoChannelReleaseException()
    }

    /** Release notes of [version] (the `v<version>` tag), e.g. the version installed on the device. */
    suspend fun getReleaseNotes(version: String): Result<ReleaseNotesSource> = runCatching {
        val tag = "v${VersionUtils.normalize(version)}"
        val response = httpRequestRaw(
            method = "GET",
            url = "https://api.github.com/repos/albyalex96/NuvioMobile-V2/releases/tags/$tag",
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "NuvioMobile",
            ),
            body = "",
        )
        currentCoroutineContext().ensureActive()
        if (response.status == 404) throw NoChannelReleaseException()
        if (response.status !in 200..299) {
            error(getString(Res.string.updates_github_api_error, response.status))
        }
        parseReleaseNotes(response.body, tag)
    }

    internal fun parseReleaseNotes(responseBody: String, fallbackTag: String): ReleaseNotesSource {
        val release = json.decodeFromString<GitHubReleaseDto>(responseBody)
        val tag = release.tagName?.takeIf(String::isNotBlank) ?: fallbackTag
        return ReleaseNotesSource(
            tag = tag,
            title = release.name?.takeIf(String::isNotBlank) ?: tag,
            notes = release.body.orEmpty(),
            releaseUrl = release.htmlUrl,
        )
    }

    internal fun releasePath(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "releases/latest"
        UpdateChannel.BETA -> "releases?per_page=100"
    }

    internal fun selectUpdate(
        responseBody: String,
        channel: UpdateChannel,
        supportedAbis: List<String>,
        tvApp: Boolean = TvAppRuntime.isTvApp,
    ): AppUpdate? {
        val releases = when (channel) {
            UpdateChannel.STABLE -> listOf(json.decodeFromString<GitHubReleaseDto>(responseBody))
            UpdateChannel.BETA -> json.decodeFromString<List<GitHubReleaseDto>>(responseBody)
        }
        return ReleaseSelector.eligibleReleases(releases, channel).firstNotNullOfOrNull { release ->
            val asset = chooseBestApkAsset(release.assets, supportedAbis, tvApp) ?: return@firstNotNullOfOrNull null
            val tag = release.tagName?.takeIf { VersionUtils.parse(it) != null }
                ?: release.name.orEmpty()
            AppUpdate(
                tag = tag,
                title = release.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size,
            )
        }
    }

    private fun chooseBestApkAsset(
        assets: List<GitHubAssetDto>,
        supportedAbis: List<String>,
        tvApp: Boolean,
    ): GitHubAssetDto? {
        // Desktop: the Windows installer attached to the same release.
        if (com.nuvio.app.isDesktop) {
            return assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
        }
        val apkAssets = assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType == "application/vnd.android.package-archive"
        }.filter { asset -> isTvApkAssetName(asset.name) == tvApp }
        for (abi in supportedAbis) {
            apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apkAssets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("universal") || name.contains("all")
        } ?: apkAssets.firstOrNull()
    }
}

private val tvApkNameToken = Regex("(?:^|[\\s._-])tv(?:[\\s._-]|$)", RegexOption.IGNORE_CASE)

/**
 * Android TV APKs carry a standalone `TV` token in the file name
 * (e.g. `Nuvio-Enhanced-Alby-TV-0.5.16-arm64-v8a.apk`): the TV app only installs those,
 * the phone app ignores them.
 */
internal fun isTvApkAssetName(name: String): Boolean = tvApkNameToken.containsMatchIn(name)
