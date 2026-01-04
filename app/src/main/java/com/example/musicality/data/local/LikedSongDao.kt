package com.example.musicality.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for liked songs
 */
@Dao
interface LikedSongDao {
    
    /**
     * Get all liked songs ordered by most recent first
     */
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>
    
    /**
     * Check if a song is liked
     */
    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE videoId = :videoId)")
    fun isSongLiked(videoId: String): Flow<Boolean>
    
    /**
     * Check if a song is liked (non-flow version for one-time checks)
     */
    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE videoId = :videoId)")
    suspend fun isSongLikedSync(videoId: String): Boolean
    
    /**
     * Get the count of liked songs
     */
    @Query("SELECT COUNT(*) FROM liked_songs")
    fun getLikedSongsCount(): Flow<Int>
    
    /**
     * Insert or update a liked song
     */
    @Upsert
    suspend fun upsertLikedSong(song: LikedSongEntity)
    
    /**
     * Remove a song from liked songs
     */
    @Query("DELETE FROM liked_songs WHERE videoId = :videoId")
    suspend fun deleteLikedSong(videoId: String)
    
    /**
     * Toggle like status - if exists, delete; if not, insert
     */
    @Transaction
    suspend fun toggleLike(song: LikedSongEntity) {
        if (isSongLikedSync(song.videoId)) {
            deleteLikedSong(song.videoId)
        } else {
            upsertLikedSong(song)
        }
    }
}
