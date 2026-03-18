package com.proj.Musicality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.data.model.PlaylistPage
import com.proj.Musicality.data.parser.PlaylistParser
import com.proj.Musicality.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(private val browseId: String) : ViewModel() {

    sealed interface UiState {
        data class Seed(val title: String, val author: String?, val thumbnailUrl: String?) : UiState
        data class Loaded(val playlist: PlaylistPage) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Seed("", null, null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun initialize(seed: Route.Playlist) {
        if (_state.value is UiState.Loaded) return
        _state.value = UiState.Seed(seed.title, seed.author, seed.thumbnailUrl)
        viewModelScope.launch(Dispatchers.IO) {
            val cached = AppCache.browse.get(browseId) as? PlaylistPage
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
            val playlist = PlaylistParser.extractPlaylistPage(json)
            if (playlist != null) {
                AppCache.browse.put(browseId, playlist)
                _state.value = UiState.Loaded(playlist)
            } else {
                _state.value = UiState.Error("Failed to load playlist")
            }
        }.onFailure {
            _state.value = UiState.Error(it.message ?: "Unknown error")
        }
    }
}
