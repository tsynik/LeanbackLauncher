package com.amazon.tv.firetv.leanbacklauncher.util

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import com.amazon.tv.firetv.leanbacklauncher.apps.AppCategory

object AppCategorizer {

    private val VIDEO_FILTER = arrayOf(
        "abema",
        "air.com.vudu",
        "android.rbtv",
        "anilabx3",
        "anixartd",
        "appletv",
        "bbc.iplayer",
        "beeline.ott",
        "bloomberg",
        "brouken.player",
        "catchplay",
        "cbs",
        "cn.tv",
        "com.canal.android",
        "com.daserste.daserste",
        "com.google.android.tv",
        "com.lgi.orionandroid.tv",
        "com.nhl",
        "com.plexapp",
        "com.senchick.viewbox",
        "com.tvp.vodtv",
        "com.vgtrk",
        "courville.nova",
        "ctcmediagroup.ctc",
        "dailymotion",
        "de.swr.ard",
        "disney",
        "divantv",
        "domatv.app",
        "dramafever",
        "dtv.tvx",
        "dutafilm",
        "exoplayer",
        "facebook",
        "filmsclub",
        "filmbluray",
        "firebat",
        "foxnews",
        "foxnow",
        "fujitv",
        "fxnow",
        "gyao",
        "haystack",
        "hbo",
        "hedwig",
        "hopwatch",
        "hotstar",
        "hulu",
        "iflix",
        "iptvsmarterstvbox",
        "iptvclient.android.idmnc",
        "imdb.tv",
        "iptvremote",
        "iqiyi",
        "khd.app",
        "kinopub",
        "kinotrend",
        "kodi",
        "lazycatsoft",
        "lionsgateplay",
        "loklok",
        "maxstream",
        "megafon.tv",
        "megogo",
        "molatvbox",
        "movies",
        "mrmc",
        "mts.mtstv",
        "mtv",
        "mubi",
        "nathnetwork",
        "netflix",
        "niklabs.pp",
        "ontv",
        "ott_play",
        "ottclub",
        "ottnavigator",
        "ottplay",
        "pl.redefine.ipla",
        "pl.tvn.player.tv",
        "pl.tvp.tvp_sport",
        "pl.polsatgo",
        "com.canal.android.canal",
        "com.viaplay.android",
        "player.online",
        "player.clouddy.online",
        "planeta.tv",
        "raintv",
        "redbull.rbtv",
        "ru.beeline.tv",
        "ru.cn.tv",
        "ru.ctv",
        "ru.ivi",
        "ru.kinopoisk",
        "ru.ntv",
        "ru.rutube",
        "ru.start",
        "ru.tv1",
        "ru.twicker.lampa",
        "ru.yourok",
        "schabi",
        "serials",
        "showtime",
        "simon.kaelae",
        "skyking",
        "smarttubetv",
        "smartup.tv",
        "smotreshka",
        "spb.tv",
        "spbtv",
        "starz",
        "ted",
        "tencent.qqlivei18n",
        "tiktok",
        "tnt_premier",
        "toggo",
        "tricoloronline",
        "tubitv",
        "tv2x2",
        "tv.a24h",
        "tv.kartina",
        "tv.mola",
        "tv.mts",
        "tv.ntvplus",
        "tv.okko",
        "tv.pluto",
        "tv.vintera",
        "tver",
        "tviztv.tviz",
        "tvplayer",
        "twitch",
        "twittertv",
        "txcom.vplayer",
        "ua.youtv",
        "ufc.android",
        "unext",
        "useetv",
        "video",
        "vidio.android",
        "videomanager.kids",
        "viki",
        "visionplus",
        "viu.tv",
        "viutv",
        "vplay",
        "wifire",
        "youtube",
        "yuriev.ndr",
    )
    private val MUSIC_FILTER = arrayOf(
        "audials",
        "audioplayer",
        "deezer",
        "fmplay",
        "ituner",
        "music",
        "pandora",
        "radio",
        "somafm",
        "spotify",
        "tidal",
        "tunein",
        "zaycev",
    )
    private val GAMES_FILTER = arrayOf(
        "android.play.games",
        "signaltalk",
        "tetris",
        "com.google.stadia",
    )

    fun getAppCategory(pkgName: String?, actInfo: ActivityInfo?): AppCategory {
        pkgName?.let { pn ->
            for (s in VIDEO_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.VIDEO
                }
            }
            for (s in MUSIC_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.MUSIC
                }
            }
            for (s in GAMES_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.GAME
                }
            }
        }
        actInfo?.let { ai ->
            if (ai.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0 ||
                ai.applicationInfo.metaData != null && ai.applicationInfo.metaData.getBoolean(
                    "isGame",
                    false
                )
            ) {
                return AppCategory.GAME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (ai.applicationInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> {
                        return AppCategory.GAME
                    }
                    ApplicationInfo.CATEGORY_VIDEO -> {
                        return AppCategory.VIDEO
                    }
                    ApplicationInfo.CATEGORY_AUDIO -> {
                        return AppCategory.MUSIC
                    }
                }
            }
        }
        return AppCategory.OTHER
    }
}
