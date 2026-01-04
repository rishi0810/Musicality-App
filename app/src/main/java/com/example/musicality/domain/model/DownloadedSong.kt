package com.example.musicality.domain.model

/**
 * Domain model for a downloaded song
 */
data class DownloadedSong(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val duration: String,
    val channelId: String = "",
    val filePath: String,
    val fileSize: Long = 0L,
    val mimeType: String = "audio/webm",
    val downloadedAt: Long = System.currentTimeMillis()
)
