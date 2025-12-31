package com.example.musicality.domain.repository

import com.example.musicality.domain.model.PlaylistDetail

/**
 * Repository interface for playlist operations
 */
interface PlaylistRepository {
    suspend fun getPlaylistDetails(playlistId: String): Result<PlaylistDetail>
}
