package com.example.musicality.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.local.LikedSongEntity
import com.example.musicality.data.local.SavedAlbumDao
import com.example.musicality.data.local.SavedAlbumEntity
import com.example.musicality.data.local.SavedArtistDao
import com.example.musicality.data.local.SavedArtistEntity
import com.example.musicality.data.local.SavedPlaylistDao
import com.example.musicality.data.local.SavedPlaylistEntity
import com.example.musicality.domain.model.DownloadedSong
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.repository.DownloadRepository
import com.example.musicality.domain.repository.LikedSongsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 * 
 * Manages collections:
 * - Liked Songs
 * - Downloaded Songs
 * - Saved Albums, Artists, Playlists
 */
class LibraryViewModel(
    private val likedSongsRepository: LikedSongsRepository,
    private val downloadRepository: DownloadRepository,
    private val savedAlbumDao: SavedAlbumDao,
    private val savedArtistDao: SavedArtistDao,
    private val savedPlaylistDao: SavedPlaylistDao
) : ViewModel() {
    
    // ==================== LIKED SONGS ====================
    val likedSongs: StateFlow<List<LikedSongEntity>> = likedSongsRepository.getAllLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val likedSongsCount: StateFlow<Int> = likedSongsRepository.getLikedSongsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // ==================== DOWNLOADED SONGS ====================
    val downloadedSongs: StateFlow<List<DownloadedSong>> = downloadRepository.getAllDownloadedSongsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val downloadedSongsCount: StateFlow<Int> = downloadRepository.getDownloadedSongsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val totalStorageUsed: StateFlow<Long> = downloadRepository.getTotalStorageUsed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    
    // ==================== SAVED ALBUMS ====================
    val savedAlbums: StateFlow<List<SavedAlbumEntity>> = savedAlbumDao.getAllSavedAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // ==================== SAVED ARTISTS ====================
    val savedArtists: StateFlow<List<SavedArtistEntity>> = savedArtistDao.getAllSavedArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // ==================== SAVED PLAYLISTS ====================
    val savedPlaylists: StateFlow<List<SavedPlaylistEntity>> = savedPlaylistDao.getAllSavedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // ==================== QUEUE CONVERSION ====================
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
    
    fun getDownloadedSongsAsQueue(): List<QueueSong> {
        return downloadedSongs.value.map { downloadedSong ->
            QueueSong(
                videoId = downloadedSong.videoId,
                name = downloadedSong.title,
                singer = downloadedSong.author,
                thumbnailUrl = downloadedSong.thumbnailSource,
                duration = downloadedSong.duration
            )
        }
    }
    
    // ==================== ACTIONS ====================
    fun deleteDownloadedSong(videoId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownloadedSong(videoId)
        }
    }
    
    fun formatStorageSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * Factory for creating LibraryViewModel with dependencies
     */
    class Factory(
        private val likedSongsRepository: LikedSongsRepository,
        private val downloadRepository: DownloadRepository,
        private val savedAlbumDao: SavedAlbumDao,
        private val savedArtistDao: SavedArtistDao,
        private val savedPlaylistDao: SavedPlaylistDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(
                    likedSongsRepository,
                    downloadRepository,
                    savedAlbumDao,
                    savedArtistDao,
                    savedPlaylistDao
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
