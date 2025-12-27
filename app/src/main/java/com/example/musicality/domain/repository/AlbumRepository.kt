package com.example.musicality.domain.repository

import com.example.musicality.domain.model.AlbumDetail

/**
 * Repository interface for album operations
 */
interface AlbumRepository {
    /**
     * Get album details by album ID (browseId)
     * 
     * @param albumId The browse ID of the album
     * @return Result containing AlbumDetail on success or error on failure
     */
    suspend fun getAlbumDetails(albumId: String): Result<AlbumDetail>
}
