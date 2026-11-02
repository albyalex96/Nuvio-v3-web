package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.nuvio.app.R
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
import nuvio.composeapp.generated.resources.rating_tmdb
import org.jetbrains.compose.resources.painterResource as composePainterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> composePainterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.Trakt -> painterResource(id = R.drawable.trakt_tv_favicon)
        IntegrationLogo.Simkl -> simklBrandPainter(SimklBrandAsset.Glyph)
        IntegrationLogo.MdbList -> painterResource(id = R.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> composePainterResource(Res.drawable.introdb_favicon)
        IntegrationLogo.Discord -> composePainterResource(Res.drawable.discord_logo)
        IntegrationLogo.AnimeSkip -> composePainterResource(Res.drawable.animeskip_logo)
        IntegrationLogo.MyAnimeList -> composePainterResource(Res.drawable.myanimelist_logo)
        IntegrationLogo.Kitsu -> composePainterResource(Res.drawable.kitsu_logo)
        IntegrationLogo.AniList -> composePainterResource(Res.drawable.anilist_logo)
        IntegrationLogo.SubDl -> composePainterResource(Res.drawable.subdl_logo)
        IntegrationLogo.Telegram -> composePainterResource(Res.drawable.telegram_logo)
        IntegrationLogo.SkipDb -> composePainterResource(Res.drawable.skipdb_logo)
        IntegrationLogo.AniSkip -> composePainterResource(Res.drawable.aniskip_logo)
        IntegrationLogo.OpenSubtitles -> composePainterResource(Res.drawable.opensubtitles_logo)
        IntegrationLogo.Cloudstream -> composePainterResource(Res.drawable.cloudstream_logo)
        IntegrationLogo.WeTrakr -> composePainterResource(Res.drawable.wetrakr_logo)
    }
