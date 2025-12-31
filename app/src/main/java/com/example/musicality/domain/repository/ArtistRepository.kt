package com.example.musicality.domain.repository

import com.example.musicality.domain.model.ArtistDetail

/**
 * Repository interface for artist operations
 */
interface ArtistRepository {
    suspend fun getArtistDetails(artistId: String): Result<ArtistDetail>
}
