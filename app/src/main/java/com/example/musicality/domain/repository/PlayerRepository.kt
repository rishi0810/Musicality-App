package com.example.musicality.domain.repository

import com.example.musicality.domain.model.SongPlaybackInfo

/**
 * Repository interface for player operations
 */
interface PlayerRepository {
    /**
     * Get song playback information
     * @param videoId The video ID of the song
     * @return Result containing SongPlaybackInfo or error
     */
    suspend fun getSongInfo(videoId: String): Result<SongPlaybackInfo>
}
