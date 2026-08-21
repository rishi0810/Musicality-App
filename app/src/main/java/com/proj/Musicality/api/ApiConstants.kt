package com.proj.Musicality.api

object ApiConstants {
    const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    const val SUGGESTION_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    const val VISITOR_BROWSE_URL = "https://music.youtube.com/sw.js_data"
    const val PLAYER_URL = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"

    const val WEB_REMIX_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val RELATED_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    const val RELATED_CLIENT_VERSION = "1.20260707.12.00"
    const val VISIONOS_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    const val KTOR_USER_AGENT = "ktor-client"

    const val WEB_REMIX_CLIENT_NAME = "WEB_REMIX"
    const val WEB_REMIX_CLIENT_VERSION = "1.20260209.03.00"
    const val ALL_SEARCH_CLIENT_VERSION = "1.20260728.15.00"
    const val ALL_SEARCH_USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
    const val VISIONOS_CLIENT_NAME = "VISIONOS"
    const val VISIONOS_CLIENT_VERSION = "0.1"
    const val VISIONOS_OS_VERSION = "1.3.21O771"
    const val VISIONOS_DEVICE_MODEL = "RealityDevice14,1"
    const val VISIONOS_HEADER_CLIENT_NAME = "101"
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
