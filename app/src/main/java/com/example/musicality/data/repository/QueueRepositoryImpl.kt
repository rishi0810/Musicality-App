package com.example.musicality.data.repository

import com.example.musicality.data.mapper.extractPlaylistId
import com.example.musicality.data.mapper.parseQueueSongs
import com.example.musicality.data.model.QueueClientDto
import com.example.musicality.data.model.QueueContextDto
import com.example.musicality.data.model.QueueRequestDto
import com.example.musicality.data.remote.QueueApiService
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.repository.QueueRepository

/**
 * Implementation of QueueRepository
 * Handles the sequential API flow to fetch related songs queue
 */
class QueueRepositoryImpl(
    private val apiService: QueueApiService
) : QueueRepository {
    
    override suspend fun getQueue(videoId: String): Result<List<QueueSong>> {
        return try {
            // Step 1: First API call with videoId to get playlistId
            val firstRequest = QueueRequestDto(
                videoId = videoId,
                context = QueueContextDto(
                    client = QueueClientDto()
                )
            )
            
            val firstResponse = apiService.getNext(firstRequest)
            
            // Extract playlistId from the first response
            val playlistId = firstResponse.extractPlaylistId(videoId)
            
            if (playlistId == null) {
                return Result.failure(Exception("Playlist ID not found for the given video ID"))
            }
            
            android.util.Log.d("QueueRepository", "Extracted playlistId: $playlistId for videoId: $videoId")
            
            // Step 2: Second API call with playlistId to get the full queue
            val secondRequest = QueueRequestDto(
                playlistId = playlistId,
                context = QueueContextDto(
                    client = QueueClientDto()
                )
            )
            
            val secondResponse = apiService.getNext(secondRequest)
            
            // Parse the queue songs from the second response
            val queueSongs = secondResponse.parseQueueSongs()
            
            if (queueSongs.isEmpty()) {
                return Result.failure(Exception("No songs found in queue"))
            }
            
            android.util.Log.d("QueueRepository", "Fetched ${queueSongs.size} songs in queue")
            
            Result.success(queueSongs)
        } catch (e: Exception) {
            android.util.Log.e("QueueRepository", "Error fetching queue", e)
            Result.failure(e)
        }
    }
}
