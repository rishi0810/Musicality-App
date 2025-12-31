package com.example.musicality.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val repository: PlaylistRepository = NetworkModule.providePlaylistRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Idle)
    val uiState: StateFlow<UiState<PlaylistDetail>> = _uiState.asStateFlow()
    
    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()
    
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
     * Clear playlist state (for when navigating away)
     */
    fun clearPlaylist() {
        _uiState.value = UiState.Idle
        _currentPlaylistId.value = null
    }
}
