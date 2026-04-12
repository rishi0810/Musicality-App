package com.proj.Musicality.api

object ApiConstants {
    const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    const val SUGGESTION_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    const val VISITOR_BROWSE_URL = "https://music.youtube.com/sw.js_data"
    const val REEL_URL = "https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch"
    const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    const val WEB_REMIX_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val ANDROID_USER_AGENT = "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"
    const val KTOR_USER_AGENT = "ktor-client"

    const val WEB_REMIX_CLIENT_NAME = "WEB_REMIX"
    const val WEB_REMIX_CLIENT_VERSION = "1.20260209.03.00"
    const val ANDROID_CLIENT_NAME = "ANDROID"
    const val ANDROID_CLIENT_VERSION = "21.03.36"
    const val ANDROID_VR_CLIENT_NAME = "ANDROID_VR"
    const val ANDROID_VR_CLIENT_VERSION = "1.71.26"
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
