package com.example.musicality.domain.repository

import com.example.musicality.domain.model.QueueSong

/**
 * Repository interface for queue operations
 */
interface QueueRepository {
    /**
     * Get related songs queue for a video
     * This makes two sequential API calls:
     * 1. First call with videoId to get playlistId
     * 2. Second call with playlistId to get the full queue
     * 
     * @param videoId The video ID of the currently playing song
     * @return Result containing list of QueueSong or error
     */
    suspend fun getQueue(videoId: String): Result<List<QueueSong>>
}
