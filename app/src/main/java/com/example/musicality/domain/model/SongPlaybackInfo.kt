package com.example.musicality.domain.model

/**
 * Domain model for song playback information
 */
data class SongPlaybackInfo(
    val mainUrl: String,
    val videoId: String,
    val title: String,
    val lengthSeconds: String,
    val thumbnailUrl: String,
    val author: String,
    val viewCount: String,
    val channelId: String = ""
)
