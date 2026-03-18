package com.proj.Musicality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.SearchType
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.data.model.SearchSuggestionResult
import com.proj.Musicality.data.parser.SearchParser
import com.proj.Musicality.data.parser.SuggestionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val suggestions: StateFlow<List<SearchSuggestionResult>> = _query
        .debounce(300)
        .filter { it.length >= 2 }
        .mapLatest { q ->
            runCatching {
                val json = VisitorManager.executeSuggestionRequestWithRecovery(q)
                SuggestionParser.extractSuggestions(json)
            }.getOrDefault(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeTab = MutableStateFlow(SearchType.SONGS)
    val activeTab: StateFlow<SearchType> = _activeTab.asStateFlow()

    private val _results = MutableStateFlow<Map<SearchType, Any>>(emptyMap())
    val results: StateFlow<Map<SearchType, Any>> = _results.asStateFlow()

    private val _isSearchMode = MutableStateFlow(false)
    val isSearchMode: StateFlow<Boolean> = _isSearchMode.asStateFlow()

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isEmpty()) {
            _isSearchMode.value = false
            _results.value = emptyMap()
        }
    }

    fun onSubmit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _isSearchMode.value = true
        fetchTab(q, _activeTab.value)
    }

    fun onTabChange(tab: SearchType) {
        _activeTab.value = tab
        val q = _query.value.trim()
        if (q.isNotEmpty() && !_results.value.containsKey(tab)) {
            fetchTab(q, tab)
        }
    }

    private fun fetchTab(query: String, type: SearchType) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = VisitorManager.executeSearchRequestWithRecovery(query, type.params)
                val parsed: Any = withContext(Dispatchers.Default) {
                    when (type) {
                        SearchType.SONGS -> SearchParser.extractSongs(json)
                        SearchType.VIDEOS -> SearchParser.extractVideos(json)
                        SearchType.ARTISTS -> SearchParser.extractArtists(json)
                        SearchType.ALBUMS -> SearchParser.extractAlbums(json)
                        SearchType.PLAYLISTS -> SearchParser.extractPlaylists(json)
                        SearchType.FEATURED_PLAYLISTS -> SearchParser.extractPlaylists(json)
                    }
                }
                _results.update { it + (type to parsed) }
            }
        }
    }
}
