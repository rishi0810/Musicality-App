package com.proj.Musicality.api

object ApiConstants {
    const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    const val SUGGESTION_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    const val VISITOR_BROWSE_URL = "https://music.youtube.com/sw.js_data"
    const val PLAYER_URL = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"

    const val WEB_REMIX_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val ANDROID_VR_USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"
    const val KTOR_USER_AGENT = "ktor-client"

    const val WEB_REMIX_CLIENT_NAME = "WEB_REMIX"
    const val WEB_REMIX_CLIENT_VERSION = "1.20260209.03.00"
    const val ANDROID_VR_CLIENT_NAME = "ANDROID_VR"
    const val ANDROID_VR_CLIENT_VERSION = "1.43.32"
}

enum class SearchType(val params: String) {
    ALL(""),
    SONGS("EgWKAQIIAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    VIDEOS("EgWKAQIQAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    ARTISTS("EgWKAQIgAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    ALBUMS("EgWKAQIYAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    PLAYLISTS("EgeKAQQoAEABahAQAxAEEAUQEBAKEAkQFRAR"),
    FEATURED_PLAYLISTS("EgeKAQQoADgBahIQCRAKEAUQAxAEEBUQDhAQEBE=")
}
