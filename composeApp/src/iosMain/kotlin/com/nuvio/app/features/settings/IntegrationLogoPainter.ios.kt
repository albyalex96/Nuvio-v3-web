package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.simkl.SimklBrandAsset
import com.nuvio.app.features.simkl.simklBrandPainter
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.discord_logo
import nuvio.composeapp.generated.resources.animeskip_logo
import nuvio.composeapp.generated.resources.myanimelist_logo
import nuvio.composeapp.generated.resources.kitsu_logo
import nuvio.composeapp.generated.resources.wetrakr_logo
import nuvio.composeapp.generated.resources.anilist_logo
import nuvio.composeapp.generated.resources.subdl_logo
import nuvio.composeapp.generated.resources.telegram_logo
import nuvio.composeapp.generated.resources.skipdb_logo
import nuvio.composeapp.generated.resources.aniskip_logo
import nuvio.composeapp.generated.resources.opensubtitles_logo
import nuvio.composeapp.generated.resources.cloudstream_logo
import nuvio.composeapp.generated.resources.introdb_favicon
import nuvio.composeapp.generated.resources.mdblist_logo
import nuvio.composeapp.generated.resources.rating_tmdb
import nuvio.composeapp.generated.resources.trakt_tv_favicon
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> painterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.Trakt -> painterResource(Res.drawable.trakt_tv_favicon)
        IntegrationLogo.Simkl -> simklBrandPainter(SimklBrandAsset.Glyph)
        IntegrationLogo.MdbList -> painterResource(Res.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> painterResource(Res.drawable.introdb_favicon)
        IntegrationLogo.Discord -> painterResource(Res.drawable.discord_logo)
        IntegrationLogo.AnimeSkip -> painterResource(Res.drawable.animeskip_logo)
        IntegrationLogo.MyAnimeList -> painterResource(Res.drawable.myanimelist_logo)
        IntegrationLogo.Kitsu -> painterResource(Res.drawable.kitsu_logo)
        IntegrationLogo.AniList -> painterResource(Res.drawable.anilist_logo)
        IntegrationLogo.SubDl -> painterResource(Res.drawable.subdl_logo)
        IntegrationLogo.Telegram -> painterResource(Res.drawable.telegram_logo)
        IntegrationLogo.SkipDb -> painterResource(Res.drawable.skipdb_logo)
        IntegrationLogo.AniSkip -> painterResource(Res.drawable.aniskip_logo)
        IntegrationLogo.OpenSubtitles -> painterResource(Res.drawable.opensubtitles_logo)
        IntegrationLogo.Cloudstream -> painterResource(Res.drawable.cloudstream_logo)
        IntegrationLogo.WeTrakr -> painterResource(Res.drawable.wetrakr_logo)
    }
