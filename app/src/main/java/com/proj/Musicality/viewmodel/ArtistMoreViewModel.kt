package com.proj.Musicality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.data.model.ArtistContent
import com.proj.Musicality.data.model.ArtistVideo
import com.proj.Musicality.data.parser.ArtistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistMoreViewModel(
    private val browseId: String,
    private val params: String?,
    private val type: String
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class LoadedSingles(val items: List<ArtistContent>) : UiState
        data class LoadedVideos(val items: List<ArtistVideo>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = VisitorManager.executeBrowseRequestWithRecovery(browseId, params)
                if (json.isBlank()) {
                    _state.value = UiState.Error("Failed to load")
                    return@launch
                }
                when (type) {
                    "singles" -> {
                        val items = ArtistParser.extractMoreSingles(json)
                        _state.value = if (items.isNotEmpty()) UiState.LoadedSingles(items)
                        else UiState.Error("No singles found")
                    }
                    "videos" -> {
                        val items = ArtistParser.extractMoreVideos(json)
                        _state.value = if (items.isNotEmpty()) UiState.LoadedVideos(items)
                        else UiState.Error("No videos found")
                    }
                    else -> _state.value = UiState.Error("Unknown type")
                }
            }.onFailure {
                _state.value = UiState.Error(it.message ?: "Unknown error")
            }
        }
    }
}
