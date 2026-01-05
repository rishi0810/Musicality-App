package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing downloaded songs in the local database.
 * 
 * This entity stores:
 * - Audio file locally (as Opus/WebM for efficient storage)
 * - Thumbnail image locally (as compressed JPEG for minimal storage)
 * - All metadata for offline display
 * 
 * Storage optimization:
 * - Audio: Uses original codec (Opus/WebM) which is highly compressed
 * - Thumbnail: Compressed JPEG at 80% quality, small dimension (226x226)
 * - All text fields stored as-is
 */
@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey
    val videoId: String,
    
    // Metadata fields
    val title: String,
    val author: String,
    val duration: String,
    val channelId: String = "",
    
    // Original thumbnail URL (for fallback if local file not found)
    val thumbnailUrl: String,
    
    // Path to locally saved thumbnail image (compressed JPEG)
    val thumbnailFilePath: String? = null,
    
    // Path to the downloaded audio file
    val filePath: String,
    
    // File size in bytes (audio file)
    val fileSize: Long = 0L,
    
    // Thumbnail file size in bytes
    val thumbnailFileSize: Long = 0L,
    
    // MIME type of the downloaded audio file
    val mimeType: String = "audio/webm",
    
    // Timestamp when downloaded
    val downloadedAt: Long = System.currentTimeMillis()
)
