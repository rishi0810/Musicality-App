package com.example.musicality.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.SearchResponse
import com.example.musicality.domain.repository.SearchRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository = NetworkModule.provideSearchRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<UiState<SearchResponse>>(UiState.Idle)
    val searchState: StateFlow<UiState<SearchResponse>> = _searchState.asStateFlow()

    init {
        // Setup auto-search with debounce
        viewModelScope.launch {
            searchQuery
                .debounce(500) // Wait 500ms after user stops typing
                .filter { it.isNotBlank() } // Only search if query is not blank
                .distinctUntilChanged() // Only search if query changed
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchState.value = UiState.Idle
        }
    }

    /**
     * Perform search
     */
    private fun performSearch(query: String) {
        viewModelScope.launch {
            _searchState.value = UiState.Loading

            repository.search(query).fold(
                onSuccess = { response ->
                    _searchState.value = UiState.Success(response)
                },
                onFailure = { exception ->
                    _searchState.value = UiState.Error(
                        exception.message ?: "Failed to search"
                    )
                }
            )
        }
    }

    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchState.value = UiState.Idle
    }
}
