package com.example.musicality.domain.repository

import com.example.musicality.domain.model.DownloadedSong
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for download operations
 */
interface DownloadRepository {
    
    /**
     * Download a song to local storage
     * @param videoId Video ID of the song
     * @param url URL to download from
     * @param title Song title
     * @param author Song author
     * @param thumbnailUrl Thumbnail URL
     * @param duration Duration string
     * @param channelId Channel ID
     * @param mimeType MIME type of the audio
     * @return Result indicating success or failure
     */
    suspend fun downloadSong(
        videoId: String,
        url: String,
        title: String,
        author: String,
        thumbnailUrl: String,
        duration: String,
        channelId: String,
        mimeType: String
    ): Result<DownloadedSong>
    
    /**
     * Delete a downloaded song
     */
    suspend fun deleteDownloadedSong(videoId: String): Result<Unit>
    
    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(videoId: String): Boolean
    
    /**
     * Check if a song is downloaded (reactive Flow)
     */
    fun isDownloadedFlow(videoId: String): Flow<Boolean>
    
    /**
     * Get all downloaded songs
     */
    fun getAllDownloadedSongsFlow(): Flow<List<DownloadedSong>>
    
    /**
     * Get downloaded song by videoId
     */
    suspend fun getDownloadedSong(videoId: String): DownloadedSong?
    
    /**
     * Get file path for a downloaded song
     */
    suspend fun getFilePath(videoId: String): String?
}
