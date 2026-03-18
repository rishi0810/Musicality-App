package com.proj.Musicality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.data.model.AlbumPage
import com.proj.Musicality.data.parser.AlbumParser
import com.proj.Musicality.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumViewModel(private val browseId: String) : ViewModel() {

    sealed interface UiState {
        data class Seed(val title: String, val artistName: String?, val thumbnailUrl: String?, val year: String?) : UiState
        data class Loaded(val album: AlbumPage) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Seed("", null, null, null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun initialize(seed: Route.Album) {
        if (_state.value is UiState.Loaded) return
        _state.value = UiState.Seed(seed.title, seed.artistName, seed.thumbnailUrl, seed.year)
        viewModelScope.launch(Dispatchers.IO) {
            val cached = AppCache.browse.get(browseId) as? AlbumPage
            if (cached != null) {
                _state.value = UiState.Loaded(cached)
                return@launch
            }
            fetchFromApi()
        }
    }

    private suspend fun fetchFromApi() {
        runCatching {
            val json = VisitorManager.executeBrowseRequestWithRecovery(browseId)
            val album = AlbumParser.extractAlbumPage(json)
            if (album != null) {
                AppCache.browse.put(browseId, album)
                _state.value = UiState.Loaded(album)
            } else {
                _state.value = UiState.Error("Failed to load album")
            }
        }.onFailure {
            _state.value = UiState.Error(it.message ?: "Unknown error")
        }
    }
}
