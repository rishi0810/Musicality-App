package com.example.musicality.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for downloaded songs.
 * 
 * Provides CRUD operations for downloaded song metadata
 * and queries for checking download status.
 */
@Dao
interface DownloadedSongDao {
    
    /**
     * Insert or replace a downloaded song
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedSong(song: DownloadedSongEntity)
    
    /**
     * Delete a downloaded song
     */
    @Delete
    suspend fun deleteDownloadedSong(song: DownloadedSongEntity)
    
    /**
     * Delete a downloaded song by videoId
     */
    @Query("DELETE FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
    
    /**
     * Get all downloaded songs as a Flow (reactive updates)
     */
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloadedSongsFlow(): Flow<List<DownloadedSongEntity>>
    
    /**
     * Get all downloaded songs (one-time query)
     */
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadedSongs(): List<DownloadedSongEntity>
    
    /**
     * Check if a song is downloaded
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE videoId = :videoId)")
    suspend fun isDownloaded(videoId: String): Boolean
    
    /**
     * Check if a song is downloaded (Flow for reactive UI updates)
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE videoId = :videoId)")
    fun isDownloadedFlow(videoId: String): Flow<Boolean>
    
    /**
     * Get downloaded song by videoId
     */
    @Query("SELECT * FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun getDownloadedSong(videoId: String): DownloadedSongEntity?
    
    /**
     * Get file path for a downloaded song (audio file)
     */
    @Query("SELECT filePath FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun getFilePath(videoId: String): String?
    
    /**
     * Get thumbnail file path for a downloaded song
     */
    @Query("SELECT thumbnailFilePath FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun getThumbnailFilePath(videoId: String): String?
    
    /**
     * Get total downloaded songs count
     */
    @Query("SELECT COUNT(*) FROM downloaded_songs")
    fun getDownloadedSongsCount(): Flow<Int>
    
    /**
     * Get total storage used by downloaded songs (audio + thumbnails)
     */
    @Query("SELECT COALESCE(SUM(fileSize + thumbnailFileSize), 0) FROM downloaded_songs")
    fun getTotalStorageUsed(): Flow<Long>
}
