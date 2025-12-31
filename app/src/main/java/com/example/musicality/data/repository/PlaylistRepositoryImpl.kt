package com.example.musicality.data.repository

import android.util.Log
import com.example.musicality.data.mapper.parsePlaylistDetails
import com.example.musicality.data.model.AlbumBrowseRequestDto
import com.example.musicality.data.model.AlbumClientDto
import com.example.musicality.data.model.AlbumContextDto
import com.example.musicality.data.remote.PlaylistApiService
import com.example.musicality.domain.model.PlaylistDetail
import com.example.musicality.domain.repository.PlaylistRepository

/**
 * Implementation of PlaylistRepository
 * Handles fetching playlist details from YouTube Music browse API
 */
class PlaylistRepositoryImpl(
    private val apiService: PlaylistApiService
) : PlaylistRepository {
    
    companion object {
        private const val TAG = "PlaylistRepository"
    }
    
    override suspend fun getPlaylistDetails(playlistId: String): Result<PlaylistDetail> {
        return try {
            Log.d(TAG, "Fetching playlist details for: $playlistId")
            
            val request = AlbumBrowseRequestDto(
                browseId = playlistId,
                context = AlbumContextDto(
                    client = AlbumClientDto()
                )
            )
            
            val response = apiService.getPlaylistDetails(request)
            
            val playlistDetail = response.parsePlaylistDetails()
            
            if (playlistDetail.playlistName.isBlank()) {
                return Result.failure(Exception("Playlist not found"))
            }
            
            Log.d(TAG, "Successfully fetched playlist: ${playlistDetail.playlistName} with ${playlistDetail.songs.size} songs")
            
            Result.success(playlistDetail)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist details", e)
            Result.failure(e)
        }
    }
}
