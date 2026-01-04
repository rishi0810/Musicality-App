package com.example.musicality.ui.player

import android.content.Context
import com.example.musicality.data.local.LikedSongEntity
import com.example.musicality.di.DatabaseModule
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.domain.repository.LikedSongsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Manager class for handling liked songs operations
 * Provides a singleton instance that can be used across the app
 */
class LikedSongsManager private constructor(
    private val likedSongsRepository: LikedSongsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Check if a song is liked
     */
    fun isSongLiked(videoId: String): Flow<Boolean> {
        return likedSongsRepository.isSongLiked(videoId)
    }
    
    /**
     * Toggle the like status of the current song
     */
    fun toggleLike(songInfo: SongPlaybackInfo) {
        scope.launch {
            val entity = LikedSongEntity(
                videoId = songInfo.videoId,
                title = songInfo.title,
                author = songInfo.author,
                thumbnailUrl = songInfo.thumbnailUrl,
                duration = formatDuration(songInfo.lengthSeconds),
                channelId = songInfo.channelId
            )
            likedSongsRepository.toggleLike(entity)
        }
    }
    
    /**
     * Format duration from seconds string to mm:ss format
     */
    private fun formatDuration(lengthSeconds: String): String {
        return try {
            val totalSeconds = lengthSeconds.toLongOrNull() ?: 0L
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%d:%02d", minutes, seconds)
        } catch (e: Exception) {
            lengthSeconds
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: LikedSongsManager? = null
        
        fun getInstance(context: Context): LikedSongsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LikedSongsManager(
                    DatabaseModule.provideLikedSongsRepository(context)
                ).also { INSTANCE = it }
            }
        }
    }
}
