package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

internal enum class IntegrationLogo {
    Tmdb,
    Trakt,
    Simkl,
    MdbList,
    IntroDb,
    Discord,
    AnimeSkip,
    MyAnimeList,
    Kitsu,
    AniList,
    SubDl,
    Telegram,
    SkipDb,
    AniSkip,
    OpenSubtitles,
    Cloudstream,
    WeTrakr,
}

@Composable
internal expect fun integrationLogoPainter(logo: IntegrationLogo): Painter
