package com.example.musicality.ui.artist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.local.SavedArtistDao
import com.example.musicality.data.local.SavedArtistEntity
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
    private val repository: ArtistRepository = NetworkModule.provideArtistRepository(),
    private val savedArtistDao: SavedArtistDao? = null
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<ArtistDetail>>(UiState.Idle)
    val uiState: StateFlow<UiState<ArtistDetail>> = _uiState.asStateFlow()
    
    private val _currentArtistId = MutableStateFlow<String?>(null)
    val currentArtistId: StateFlow<String?> = _currentArtistId.asStateFlow()
    
    private val _isArtistSaved = MutableStateFlow(false)
    val isArtistSaved: StateFlow<Boolean> = _isArtistSaved.asStateFlow()
    
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
            
            // Check if artist is saved
            savedArtistDao?.let { dao ->
                _isArtistSaved.value = dao.isArtistSaved(artistId)
            }
            
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
     * Toggle save/unsave artist to library
     */
    fun toggleSaveArtist() {
        val artistDetail = (_uiState.value as? UiState.Success)?.data ?: return
        val dao = savedArtistDao ?: return
        val artistId = _currentArtistId.value ?: return
        
        viewModelScope.launch {
            if (_isArtistSaved.value) {
                dao.unsaveArtist(artistId)
                _isArtistSaved.value = false
            } else {
                val entity = SavedArtistEntity(
                    artistId = artistId,
                    name = artistDetail.artistName,
                    thumbnailUrl = artistDetail.artistThumbnail,
                    monthlyListeners = artistDetail.monthlyAudience
                )
                dao.saveArtist(entity)
                _isArtistSaved.value = true
            }
        }
    }
    
    /**
     * Clear artist state (for when navigating away)
     */
    fun clearArtist() {
        _uiState.value = UiState.Idle
        _currentArtistId.value = null
        _isArtistSaved.value = false
    }
    
    /**
     * Factory for creating ArtistViewModel with dependencies
     */
    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtistViewModel::class.java)) {
                val database = MusicalityDatabase.getDatabase(context)
                return ArtistViewModel(
                    repository = NetworkModule.provideArtistRepository(),
                    savedArtistDao = database.savedArtistDao()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

