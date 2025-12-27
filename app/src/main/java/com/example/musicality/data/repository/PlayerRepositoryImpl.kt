package com.example.musicality.data.repository

import com.example.musicality.data.mapper.parsePlayerResponse
import com.example.musicality.data.model.PlayerClientDto
import com.example.musicality.data.model.PlayerContextDto
import com.example.musicality.data.model.PlayerRequestDto
import com.example.musicality.data.remote.PlayerApiService
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.domain.repository.PlayerRepository

/**
 * Implementation of PlayerRepository
 */
class PlayerRepositoryImpl(
    private val apiService: PlayerApiService
) : PlayerRepository {
    
    override suspend fun getSongInfo(videoId: String): Result<SongPlaybackInfo> {
        return try {
            val requestBody = PlayerRequestDto(
                videoId = videoId,
                context = PlayerContextDto(
                    client = PlayerClientDto()
                )
            )
            
            val response = apiService.getPlayerInfo(requestBody)
            val parsedResponse = response.parsePlayerResponse()
            
            if (parsedResponse != null) {
                Result.success(parsedResponse)
            } else {
                Result.failure(Exception("Failed to parse player response or video not playable"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
