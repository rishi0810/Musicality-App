package com.example.musicality.ui.album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.local.SavedAlbumDao
import com.example.musicality.data.local.SavedAlbumEntity
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
    private val repository: AlbumRepository = NetworkModule.provideAlbumRepository(),
    private val savedAlbumDao: SavedAlbumDao? = null
) : ViewModel() {
    
    private val _albumState = MutableStateFlow<UiState<AlbumDetail>>(UiState.Idle)
    val albumState: StateFlow<UiState<AlbumDetail>> = _albumState.asStateFlow()
    
    private val _currentAlbumId = MutableStateFlow<String?>(null)
    val currentAlbumId: StateFlow<String?> = _currentAlbumId.asStateFlow()
    
    private val _isAlbumSaved = MutableStateFlow(false)
    val isAlbumSaved: StateFlow<Boolean> = _isAlbumSaved.asStateFlow()
    
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
            
            // Check if album is saved
            savedAlbumDao?.let { dao ->
                _isAlbumSaved.value = dao.isAlbumSaved(albumId)
            }
            
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
     * Toggle save/unsave album to library
     */
    fun toggleSaveAlbum() {
        val albumDetail = (_albumState.value as? UiState.Success)?.data ?: return
        val dao = savedAlbumDao ?: return
        val albumId = _currentAlbumId.value ?: return
        
        viewModelScope.launch {
            if (_isAlbumSaved.value) {
                dao.unsaveAlbum(albumId)
                _isAlbumSaved.value = false
            } else {
                val entity = SavedAlbumEntity(
                    albumId = albumId,
                    name = albumDetail.albumName,
                    artist = albumDetail.artist,
                    thumbnailUrl = albumDetail.albumThumbnail,
                    songCount = albumDetail.count
                )
                dao.saveAlbum(entity)
                _isAlbumSaved.value = true
            }
        }
    }
    
    /**
     * Clear album state (for when navigating away)
     */
    fun clearAlbum() {
        _albumState.value = UiState.Idle
        _currentAlbumId.value = null
        _isAlbumSaved.value = false
    }
    
    /**
     * Factory for creating AlbumViewModel with dependencies
     */
    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlbumViewModel::class.java)) {
                val database = MusicalityDatabase.getDatabase(context)
                return AlbumViewModel(
                    repository = NetworkModule.provideAlbumRepository(),
                    savedAlbumDao = database.savedAlbumDao()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

