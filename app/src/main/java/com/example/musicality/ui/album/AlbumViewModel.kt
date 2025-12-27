package com.example.musicality.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.AlbumDetail
import com.example.musicality.domain.repository.AlbumRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for AlbumScreen
 * Handles fetching and managing album details state
 */
class AlbumViewModel(
    private val repository: AlbumRepository = NetworkModule.provideAlbumRepository()
) : ViewModel() {
    
    private val _albumState = MutableStateFlow<UiState<AlbumDetail>>(UiState.Idle)
    val albumState: StateFlow<UiState<AlbumDetail>> = _albumState.asStateFlow()
    
    private val _currentAlbumId = MutableStateFlow<String?>(null)
    val currentAlbumId: StateFlow<String?> = _currentAlbumId.asStateFlow()
    
    /**
     * Load album details by album ID
     */
    fun loadAlbum(albumId: String) {
        // Don't reload if same album
        if (_currentAlbumId.value == albumId && _albumState.value is UiState.Success) {
            return
        }
        
        _currentAlbumId.value = albumId
        
        viewModelScope.launch {
            _albumState.value = UiState.Loading
            
            repository.getAlbumDetails(albumId).fold(
                onSuccess = { albumDetail ->
                    _albumState.value = UiState.Success(albumDetail)
                },
                onFailure = { exception ->
                    _albumState.value = UiState.Error(
                        exception.message ?: "Failed to load album"
                    )
                }
            )
        }
    }
    
    /**
     * Clear album state (for when navigating away)
     */
    fun clearAlbum() {
        _albumState.value = UiState.Idle
        _currentAlbumId.value = null
    }
}
