package com.nuvio.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import co.touchlab.kermit.Logger
import com.nuvio.app.MainActivity
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.tv.player.TvPlayerKeyRouter
import com.nuvio.tv.remote.TvPhoneRemote
import com.nuvio.tv.search.TvVoiceSearchIntents
import com.nuvio.tv.settings.TvLayoutSettingsRepository

/**
 * Android TV entry point. [MainActivity] performs the whole platform initialization
 * (storages, Sentry, notifications…); this activity only swaps the content for [TvApp]
 * before the first composition and routes remote keys to the player.
 */
class TvActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        TvAppRuntime.isTvApp = true
        super.onCreate(savedInstanceState)
        TvLayoutSettingsRepository.initialize(this)
        TvPhoneRemote.initialize(this)
        Logger.withTag("TvApp").i { "Starting Android TV shell" }
        // Replaces the phone content set by MainActivity: the view is not attached yet,
        // so the phone UI is never composed.
        setContent { TvApp() }
        // A recreated activity already handled its launch intent.
        if (savedInstanceState == null) TvVoiceSearchIntents.handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TvVoiceSearchIntents.handle(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (TvPlayerKeyRouter.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
