package com.example.musicality.domain.repository

import com.example.musicality.data.local.LikedSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for liked songs
 */
interface LikedSongsRepository {
    
    /**
     * Get all liked songs
     */
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>
    
    /**
     * Check if a song is liked
     */
    fun isSongLiked(videoId: String): Flow<Boolean>
    
    /**
     * Get the count of liked songs
     */
    fun getLikedSongsCount(): Flow<Int>
    
    /**
     * Toggle the like status of a song
     */
    suspend fun toggleLike(song: LikedSongEntity)
    
    /**
     * Like a song (add to liked songs)
     */
    suspend fun likeSong(song: LikedSongEntity)
    
    /**
     * Unlike a song (remove from liked songs)
     */
    suspend fun unlikeSong(videoId: String)
}
