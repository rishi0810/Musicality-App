package com.example.musicality.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.local.LikedSongEntity
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.repository.LikedSongsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Library screen
 */
class LibraryViewModel(
    private val likedSongsRepository: LikedSongsRepository
) : ViewModel() {
    
    /**
     * All liked songs from the database
     */
    val likedSongs: StateFlow<List<LikedSongEntity>> = likedSongsRepository.getAllLikedSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Count of liked songs
     */
    val likedSongsCount: StateFlow<Int> = likedSongsRepository.getLikedSongsCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * Convert liked songs to QueueSong format for playback
     */
    fun getLikedSongsAsQueue(): List<QueueSong> {
        return likedSongs.value.map { likedSong ->
            QueueSong(
                videoId = likedSong.videoId,
                name = likedSong.title,
                singer = likedSong.author,
                thumbnailUrl = likedSong.thumbnailUrl,
                duration = likedSong.duration
            )
        }
    }
    
    /**
     * Factory for creating LibraryViewModel with dependencies
     */
    class Factory(
        private val likedSongsRepository: LikedSongsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(likedSongsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
