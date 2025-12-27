package com.example.musicality.data.repository

import android.util.Log
import com.example.musicality.data.mapper.parseAlbumDetails
import com.example.musicality.data.model.AlbumBrowseRequestDto
import com.example.musicality.data.model.AlbumClientDto
import com.example.musicality.data.model.AlbumContextDto
import com.example.musicality.data.remote.AlbumApiService
import com.example.musicality.domain.model.AlbumDetail
import com.example.musicality.domain.repository.AlbumRepository

/**
 * Implementation of AlbumRepository
 * Handles fetching album details from YouTube Music browse API
 */
class AlbumRepositoryImpl(
    private val apiService: AlbumApiService
) : AlbumRepository {
    
    companion object {
        private const val TAG = "AlbumRepository"
    }
    
    override suspend fun getAlbumDetails(albumId: String): Result<AlbumDetail> {
        return try {
            Log.d(TAG, "Fetching album details for: $albumId")
            
            val request = AlbumBrowseRequestDto(
                browseId = albumId,
                context = AlbumContextDto(
                    client = AlbumClientDto()
                )
            )
            
            val response = apiService.getAlbum(request)
            
            val albumDetail = response.parseAlbumDetails()
            
            if (albumDetail.albumName.isBlank()) {
                return Result.failure(Exception("Album not found"))
            }
            
            Log.d(TAG, "Successfully fetched album: ${albumDetail.albumName} with ${albumDetail.songs.size} songs")
            
            Result.success(albumDetail)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album details", e)
            Result.failure(e)
        }
    }
}
