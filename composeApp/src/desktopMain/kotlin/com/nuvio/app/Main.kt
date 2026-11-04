package com.nuvio.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
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
            App()
        }
    }
}
