package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing downloaded songs in the local database
 * Songs are stored as Opus files for offline playback
 */
@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val duration: String,
    val channelId: String = "",
    /** Path to the downloaded audio file */
    val filePath: String,
    /** File size in bytes */
    val fileSize: Long = 0L,
    /** MIME type of the downloaded file */
    val mimeType: String = "audio/webm",
    val downloadedAt: Long = System.currentTimeMillis()
)
