package com.nuvio.app.core.build

actual object AppFeaturePolicy {
    // JS scraper plugins run on QuickJS (native library shipped in the quickjs-kt jar).
    // CloudStream providers are Android DEX: listed, but not loadable on desktop.
    actual val pluginsEnabled: Boolean = true
    actual val supportersContributorsPageEnabled: Boolean = true
    actual val donationActionsEnabled: Boolean = false
    actual val donationProgressEnabled: Boolean = true
    actual val accountDeletionEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    actual val p2pEnabled: Boolean = true
    actual val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    actual val heroTrailerPlaybackSupported: Boolean = false
    actual val inAppUpdaterEnabled: Boolean = false
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = false
    actual val downloadForegroundServiceEnabled: Boolean = false
    actual val customServerConnectionsEnabled: Boolean = false
}
