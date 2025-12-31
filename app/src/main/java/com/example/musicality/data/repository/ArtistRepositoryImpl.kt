package com.example.musicality.data.repository

import android.util.Log
import com.example.musicality.data.mapper.parseArtistDetails
import com.example.musicality.data.model.AlbumBrowseRequestDto
import com.example.musicality.data.model.AlbumClientDto
import com.example.musicality.data.model.AlbumContextDto
import com.example.musicality.data.remote.ArtistApiService
import com.example.musicality.domain.model.ArtistDetail
import com.example.musicality.domain.repository.ArtistRepository

/**
 * Implementation of ArtistRepository
 * Handles fetching artist details from YouTube Music browse API
 */
class ArtistRepositoryImpl(
    private val apiService: ArtistApiService
) : ArtistRepository {
    
    companion object {
        private const val TAG = "ArtistRepository"
    }
    
    override suspend fun getArtistDetails(artistId: String): Result<ArtistDetail> {
        return try {
            Log.d(TAG, "Fetching artist details for: $artistId")
            
            val request = AlbumBrowseRequestDto(
                browseId = artistId,
                context = AlbumContextDto(
                    client = AlbumClientDto()
                )
            )
            
            val response = apiService.getArtistDetails(request)
            
            val artistDetail = response.parseArtistDetails()
            
            if (artistDetail.artistName.isBlank()) {
                return Result.failure(Exception("Artist not found"))
            }
            
            Log.d(TAG, "Successfully fetched artist: ${artistDetail.artistName} with ${artistDetail.topSongs.size} top songs")
            
            Result.success(artistDetail)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist details", e)
            Result.failure(e)
        }
    }
}
