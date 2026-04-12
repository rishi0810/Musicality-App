package com.proj.Musicality.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.SearchType
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.data.model.SearchSuggestionResult
import com.proj.Musicality.data.model.SuggestionType
import com.proj.Musicality.data.parser.SearchParser
import com.proj.Musicality.data.parser.SuggestionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "search_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_HISTORY_ENTRIES = 12
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _searchHistory = MutableStateFlow(loadSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    val suggestions: StateFlow<List<SearchSuggestionResult>> = combine(
        _query.debounce(300).map { it.trim() },
        _searchHistory
    ) { q, history -> q to history }
        .mapLatest { (q, history) ->
            if (q.isEmpty()) {
                history.map { term ->
                    SearchSuggestionResult(
                        type = SuggestionType.SUGGESTION,
                        title = term
                    )
                }
            } else {
                runCatching {
                    val json = VisitorManager.executeSuggestionRequestWithRecovery(q)
                    SuggestionParser.extractSuggestions(json)
                }.getOrDefault(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeTab = MutableStateFlow(SearchType.ALL)
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
        addSearchToHistory(q)
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
                val json = if (type == SearchType.ALL) {
                    VisitorManager.executeSearchAllRequestWithRecovery(query)
                } else {
                    VisitorManager.executeSearchRequestWithRecovery(query, type.params)
                }
                val parsed: Any = withContext(Dispatchers.Default) {
                    when (type) {
                        SearchType.ALL -> SearchParser.extractAll(json)
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

    private fun addSearchToHistory(query: String) {
        val updated = buildList {
            add(query)
            addAll(_searchHistory.value.filterNot { it.equals(query, ignoreCase = true) })
        }.take(MAX_HISTORY_ENTRIES)
        _searchHistory.value = updated
        saveSearchHistory(updated)
    }

    private fun loadSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun saveSearchHistory(history: List<String>) {
        val serialized = JSONArray(history).toString()
        prefs.edit().putString(KEY_SEARCH_HISTORY, serialized).apply()
    }
}
