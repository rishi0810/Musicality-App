package com.example.musicality.util

import com.example.musicality.BuildConfig

/**
 * Network configuration constants
 */
object NetworkConstants {
    const val BASE_URL = "https://jsonplaceholder.typicode.com/"
    val SEARCH_BASE_URL = BuildConfig.SEARCH_BASE_URL
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
