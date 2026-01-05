package com.example.musicality.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved playlists operations
 */
@Dao
interface SavedPlaylistDao {
    
    @Query("SELECT * FROM saved_playlists ORDER BY savedAt DESC")
    fun getAllSavedPlaylists(): Flow<List<SavedPlaylistEntity>>
    
    @Query("SELECT COUNT(*) FROM saved_playlists")
    fun getSavedPlaylistsCount(): Flow<Int>
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_playlists WHERE playlistId = :playlistId)")
    suspend fun isPlaylistSaved(playlistId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_playlists WHERE playlistId = :playlistId)")
    fun isPlaylistSavedFlow(playlistId: String): Flow<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylist(playlist: SavedPlaylistEntity)
    
    @Query("DELETE FROM saved_playlists WHERE playlistId = :playlistId")
    suspend fun unsavePlaylist(playlistId: String)
    
    @Query("DELETE FROM saved_playlists")
    suspend fun clearAll()
}
