package com.example.musicality.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved artists operations
 */
@Dao
interface SavedArtistDao {
    
    @Query("SELECT * FROM saved_artists ORDER BY savedAt DESC")
    fun getAllSavedArtists(): Flow<List<SavedArtistEntity>>
    
    @Query("SELECT COUNT(*) FROM saved_artists")
    fun getSavedArtistsCount(): Flow<Int>
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_artists WHERE artistId = :artistId)")
    suspend fun isArtistSaved(artistId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_artists WHERE artistId = :artistId)")
    fun isArtistSavedFlow(artistId: String): Flow<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveArtist(artist: SavedArtistEntity)
    
    @Query("DELETE FROM saved_artists WHERE artistId = :artistId")
    suspend fun unsaveArtist(artistId: String)
    
    @Query("DELETE FROM saved_artists")
    suspend fun clearAll()
}
