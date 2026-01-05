package com.example.musicality.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved albums operations
 */
@Dao
interface SavedAlbumDao {
    
    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun getAllSavedAlbums(): Flow<List<SavedAlbumEntity>>
    
    @Query("SELECT COUNT(*) FROM saved_albums")
    fun getSavedAlbumsCount(): Flow<Int>
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumId = :albumId)")
    suspend fun isAlbumSaved(albumId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumId = :albumId)")
    fun isAlbumSavedFlow(albumId: String): Flow<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlbum(album: SavedAlbumEntity)
    
    @Query("DELETE FROM saved_albums WHERE albumId = :albumId")
    suspend fun unsaveAlbum(albumId: String)
    
    @Query("DELETE FROM saved_albums")
    suspend fun clearAll()
}
