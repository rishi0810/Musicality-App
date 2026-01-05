package com.example.musicality.domain.model

/**
 * Domain model for a downloaded song.
 * 
 * Contains all metadata and file paths for offline playback and display.
 */
data class DownloadedSong(
    val videoId: String,
    val title: String,
    val author: String,
    val duration: String,
    val channelId: String = "",
    
    // Original thumbnail URL (for fallback)
    val thumbnailUrl: String,
    
    // Path to locally saved thumbnail (null if not saved)
    val thumbnailFilePath: String? = null,
    
    // Path to the downloaded audio file
    val filePath: String,
    
    // Audio file size in bytes
    val fileSize: Long = 0L,
    
    // Thumbnail file size in bytes
    val thumbnailFileSize: Long = 0L,
    
    // MIME type of the audio
    val mimeType: String = "audio/webm",
    
    // When the song was downloaded
    val downloadedAt: Long = System.currentTimeMillis()
) {
    /**
     * Get the best available thumbnail source.
     * Prefers local file if available, falls back to URL.
     */
    val thumbnailSource: String
        get() = thumbnailFilePath ?: thumbnailUrl
    
    /**
     * Total storage used by this download (audio + thumbnail)
     */
    val totalStorageUsed: Long
        get() = fileSize + thumbnailFileSize
}
