package com.example.musicality.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.local.SavedPlaylistDao
import com.example.musicality.data.local.SavedPlaylistEntity
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.PlaylistDetail
import com.example.musicality.domain.repository.PlaylistRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for PlaylistScreen
 * Handles fetching and managing playlist details state
 */
class PlaylistViewModel(
    private val repository: PlaylistRepository = NetworkModule.providePlaylistRepository(),
    private val savedPlaylistDao: SavedPlaylistDao? = null
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Idle)
    val uiState: StateFlow<UiState<PlaylistDetail>> = _uiState.asStateFlow()
    
    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()
    
    private val _isPlaylistSaved = MutableStateFlow(false)
    val isPlaylistSaved: StateFlow<Boolean> = _isPlaylistSaved.asStateFlow()
    
    /**
     * Load playlist details by playlist ID
     */
    fun loadPlaylist(playlistId: String) {
        // Don't reload if same playlist
        if (_currentPlaylistId.value == playlistId && _uiState.value is UiState.Success) {
            return
        }
        
        _currentPlaylistId.value = playlistId
        
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            // Check if playlist is saved
            savedPlaylistDao?.let { dao ->
                _isPlaylistSaved.value = dao.isPlaylistSaved(playlistId)
            }
            
            repository.getPlaylistDetails(playlistId).fold(
                onSuccess = { playlistDetail ->
                    _uiState.value = UiState.Success(playlistDetail)
                },
                onFailure = { exception ->
                    _uiState.value = UiState.Error(
                        exception.message ?: "Failed to load playlist"
                    )
                }
            )
        }
    }
    
    /**
     * Toggle save/unsave playlist to library
     */
    fun toggleSavePlaylist() {
        val playlistDetail = (_uiState.value as? UiState.Success)?.data ?: return
        val dao = savedPlaylistDao ?: return
        val playlistId = _currentPlaylistId.value ?: return
        
        viewModelScope.launch {
            if (_isPlaylistSaved.value) {
                dao.unsavePlaylist(playlistId)
                _isPlaylistSaved.value = false
            } else {
                val entity = SavedPlaylistEntity(
                    playlistId = playlistId,
                    name = playlistDetail.playlistName,
                    thumbnailUrl = playlistDetail.thumbnailImg,
                    trackCount = playlistDetail.totalTracks
                )
                dao.savePlaylist(entity)
                _isPlaylistSaved.value = true
            }
        }
    }
    
    /**
     * Clear playlist state (for when navigating away)
     */
    fun clearPlaylist() {
        _uiState.value = UiState.Idle
        _currentPlaylistId.value = null
        _isPlaylistSaved.value = false
    }
    
    /**
     * Factory for creating PlaylistViewModel with dependencies
     */
    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaylistViewModel::class.java)) {
                val database = MusicalityDatabase.getDatabase(context)
                return PlaylistViewModel(
                    repository = NetworkModule.providePlaylistRepository(),
                    savedPlaylistDao = database.savedPlaylistDao()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

