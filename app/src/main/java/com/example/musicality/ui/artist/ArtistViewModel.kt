package com.example.musicality.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.ArtistDetail
import com.example.musicality.domain.repository.ArtistRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for ArtistScreen
 * Handles fetching and managing artist details state
 */
class ArtistViewModel(
    private val repository: ArtistRepository = NetworkModule.provideArtistRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<ArtistDetail>>(UiState.Idle)
    val uiState: StateFlow<UiState<ArtistDetail>> = _uiState.asStateFlow()
    
    private val _currentArtistId = MutableStateFlow<String?>(null)
    val currentArtistId: StateFlow<String?> = _currentArtistId.asStateFlow()
    
    /**
     * Load artist details by artist ID
     */
    fun loadArtist(artistId: String) {
        // Don't reload if same artist
        if (_currentArtistId.value == artistId && _uiState.value is UiState.Success) {
            return
        }
        
        _currentArtistId.value = artistId
        
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            repository.getArtistDetails(artistId).fold(
                onSuccess = { artistDetail ->
                    _uiState.value = UiState.Success(artistDetail)
                },
                onFailure = { exception ->
                    _uiState.value = UiState.Error(
                        exception.message ?: "Failed to load artist"
                    )
                }
            )
        }
    }
    
    /**
     * Clear artist state (for when navigating away)
     */
    fun clearArtist() {
        _uiState.value = UiState.Idle
        _currentArtistId.value = null
    }
}
