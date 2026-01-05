package com.example.musicality.domain.repository

import com.example.musicality.domain.model.DownloadedSong
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for download operations.
 * 
 * Uses ExoPlayer's DownloadManager for robust downloading of large files
 * from YouTube-style CDN URLs with expiring links.
 */
interface DownloadRepository {
    
    /**
     * Download a song to local storage.
     * 
     * This method:
     * - Uses ExoPlayer's DownloadManager for the audio file
     * - Downloads and compresses the thumbnail locally
     * - Saves metadata to Room database
     * 
     * @param videoId Video ID (used as unique identifier)
     * @param url Audio URL to download from
     * @param title Song title
     * @param author Song author/artist
     * @param thumbnailUrl Thumbnail URL to download
     * @param duration Duration string
     * @param channelId Channel ID
     * @param mimeType MIME type of the audio
     * @return Result with the downloaded song info
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
     * Start a download using ExoPlayer's DownloadService.
     * Returns immediately - download happens in background.
     */
    fun startDownload(
        videoId: String,
        url: String,
        title: String,
        mimeType: String
    )
    
    /**
     * Resume a paused or failed download with a fresh URL.
     * Useful when the original CDN URL has expired.
     */
    fun resumeDownloadWithFreshUrl(
        videoId: String,
        freshUrl: String
    )
    
    /**
     * Delete a downloaded song and its files.
     */
    suspend fun deleteDownloadedSong(videoId: String): Result<Unit>
    
    /**
     * Check if a song is downloaded.
     */
    suspend fun isDownloaded(videoId: String): Boolean
    
    /**
     * Check if a song is downloaded (reactive Flow).
     */
    fun isDownloadedFlow(videoId: String): Flow<Boolean>
    
    /**
     * Get all downloaded songs.
     */
    fun getAllDownloadedSongsFlow(): Flow<List<DownloadedSong>>
    
    /**
     * Get downloaded song by videoId.
     */
    suspend fun getDownloadedSong(videoId: String): DownloadedSong?
    
    /**
     * Get file path for a downloaded song.
     */
    suspend fun getFilePath(videoId: String): String?
    
    /**
     * Get download progress (0.0 to 1.0) for a song.
     * Returns -1 if no download exists.
     */
    fun getDownloadProgress(videoId: String): Float
    
    /**
     * Get total storage used by all downloads.
     */
    fun getTotalStorageUsed(): Flow<Long>
    
    /**
     * Get downloaded songs count.
     */
    fun getDownloadedSongsCount(): Flow<Int>
}
