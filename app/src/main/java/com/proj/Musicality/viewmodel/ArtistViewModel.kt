package com.proj.Musicality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.data.model.ArtistDetails
import com.proj.Musicality.data.parser.ArtistParser
import com.proj.Musicality.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistViewModel(private val browseId: String) : ViewModel() {

    sealed interface UiState {
        data class Seed(
            val name: String,
            val thumbnailUrl: String?,
            val audienceText: String?
        ) : UiState
        data class Loaded(val details: ArtistDetails) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Seed("", null, null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun initialize(seed: Route.Artist) {
        if (_state.value is UiState.Loaded) return
        _state.value = UiState.Seed(seed.name, seed.thumbnailUrl, seed.audienceText)
        viewModelScope.launch(Dispatchers.IO) {
            val cached = AppCache.browse.get(browseId) as? ArtistDetails
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
            val details = ArtistParser.extractArtistDetails(json)
            if (details != null) {
                AppCache.browse.put(browseId, details)
                _state.value = UiState.Loaded(details)
            } else {
                _state.value = UiState.Error("Failed to load artist")
            }
        }.onFailure {
            _state.value = UiState.Error(it.message ?: "Unknown error")
        }
    }
}
