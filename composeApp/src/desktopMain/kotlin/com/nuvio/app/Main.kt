package com.nuvio.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import com.nuvio.app.features.player.PlayerWindowFullscreen
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.logging.AppLogger
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import com.nuvio.app.desktop.DesktopStorageBootstrap
import com.nuvio.app.desktop.compat.DesktopPreferencesRegistry
import com.nuvio.app.features.plugins.configureDesktopQuickJsLibrary
import org.jetbrains.compose.resources.painterResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark

private val log = Logger.withTag("DesktopApp")

fun main(args: Array<String>) {
    // Before any socket: on Windows the IPv6 stack makes JmDNS (Chromecast discovery) fail with
    // "setInterface6: Invalid argument". Also set in the packaged app's JVM options.
    System.setProperty("java.net.preferIPv4Stack", "true")
    // Same order as NuvioApplication/MainActivity on Android: storage first, then logger and DNS.
    DesktopStorageBootstrap.initialize()
    AppLogger.install(debuggable = System.getProperty("nuvio.debug") == "true")
    DnsOverHttpsRepository.ensureLoaded()
    runCatching { configureDesktopQuickJsLibrary() }
        .onFailure { log.e(it) { "QuickJS native library not available: plugins disabled" } }
    log.i { "Starting Nuvio desktop (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})" }

    args.firstOrNull { it.startsWith("nuvio://") || it.startsWith("stremio://") }?.let { url ->
        log.i { "Opening launch link" }
        handleAppUrl(url)
    }

    Runtime.getRuntime().addShutdownHook(
        Thread { runCatching { DesktopPreferencesRegistry.flush() } },
    )

    com.nuvio.app.desktop.RightClickAsLongPress.install()

    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        )
        Window(
            onCloseRequest = {
                DesktopPreferencesRegistry.flush()
                exitApplication()
            },
            state = windowState,
            title = "Nuvio",
            icon = painterResource(Res.drawable.app_logo_wordmark),
        ) {
            // Fullscreen for the player (F, double click): the window owns it.
            LaunchedEffect(windowState) {
                PlayerWindowFullscreen.handler = { fullscreen ->
                    windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                }
                snapshotFlow { windowState.placement }.collect { placement ->
                    PlayerWindowFullscreen.reportState(placement == WindowPlacement.Fullscreen)
                }
            }
            // Right click is a long press (RightClickAsLongPress): a short timeout opens menus quickly.
            val base = LocalViewConfiguration.current
            val viewConfiguration = remember(base) { DesktopViewConfiguration(base) }
            CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
                App()
            }
        }
    }
}

/** Desktop timings: [longPressTimeoutMillis] matches [com.nuvio.app.desktop.RightClickAsLongPress]. */
private class DesktopViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = com.nuvio.app.desktop.RightClickAsLongPress.LONG_PRESS_TIMEOUT_MS
}
