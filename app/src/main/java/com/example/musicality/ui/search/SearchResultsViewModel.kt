package com.example.musicality.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.SearchResultType
import com.example.musicality.domain.model.SearchResultsResponse
import com.example.musicality.domain.model.TypedSearchResult
import com.example.musicality.domain.repository.SearchRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SearchResultsScreen
 * Manages search results with category filtering and pagination
 */
class SearchResultsViewModel(
    private val repository: SearchRepository = NetworkModule.provideSearchRepository()
) : ViewModel() {

    // Query - set by the screen via setInitialQuery
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Track if initial query has been set
    private var initialQuerySet = false

    // Selected category
    private val _selectedCategory = MutableStateFlow(SearchResultType.SONGS)
    val selectedCategory: StateFlow<SearchResultType> = _selectedCategory.asStateFlow()

    // Results state
    private val _resultsState = MutableStateFlow<UiState<SearchResultsResponse>>(UiState.Idle)
    val resultsState: StateFlow<UiState<SearchResultsResponse>> = _resultsState.asStateFlow()

    // Accumulated results per category (for pagination)
    private val _accumulatedResults = MutableStateFlow<List<TypedSearchResult>>(emptyList())
    val accumulatedResults: StateFlow<List<TypedSearchResult>> = _accumulatedResults.asStateFlow()

    // Continuation token for pagination
    private val _continuationToken = MutableStateFlow<String?>(null)
    val continuationToken: StateFlow<String?> = _continuationToken.asStateFlow()

    // Loading state for pagination
    private val _isPaginating = MutableStateFlow(false)
    val isPaginating: StateFlow<Boolean> = _isPaginating.asStateFlow()

    // Cache results per category to avoid re-fetching when switching
    private val resultsCache = mutableMapOf<SearchResultType, CachedResults>()

    /**
     * Set initial query from navigation - only runs once
     */
    fun setInitialQuery(query: String) {
        if (initialQuerySet) return
        initialQuerySet = true
        if (query.isNotBlank()) {
            _searchQuery.value = query
            performSearch(query, _selectedCategory.value)
        }
    }

    /**
     * Update search query and perform search
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        // Clear cache when query changes
        resultsCache.clear()
        if (query.isNotBlank()) {
            performSearch(query, _selectedCategory.value)
        } else {
            _resultsState.value = UiState.Idle
            _accumulatedResults.value = emptyList()
            _continuationToken.value = null
        }
    }

    /**
     * Change selected category and load results
     */
    fun selectCategory(category: SearchResultType) {
        if (_selectedCategory.value == category) return
        
        _selectedCategory.value = category
        
        // Check cache first
        val cached = resultsCache[category]
        if (cached != null) {
            _accumulatedResults.value = cached.results
            _continuationToken.value = cached.continuationToken
            _resultsState.value = UiState.Success(
                SearchResultsResponse(
                    results = cached.results,
                    continuationToken = cached.continuationToken
                )
            )
        } else {
            // Load new results for this category
            performSearch(_searchQuery.value, category)
        }
    }

    /**
     * Perform initial search
     */
    private fun performSearch(query: String, type: SearchResultType) {
        viewModelScope.launch {
            _resultsState.value = UiState.Loading
            _accumulatedResults.value = emptyList()
            _continuationToken.value = null

            repository.searchResults(query, type).fold(
                onSuccess = { response ->
                    _accumulatedResults.value = response.results
                    _continuationToken.value = response.continuationToken
                    _resultsState.value = UiState.Success(response)
                    
                    // Cache results
                    resultsCache[type] = CachedResults(
                        results = response.results,
                        continuationToken = response.continuationToken
                    )
                },
                onFailure = { exception ->
                    _resultsState.value = UiState.Error(
                        exception.message ?: "Failed to search"
                    )
                }
            )
        }
    }

    /**
     * Load more results using continuation token
     */
    fun loadMore() {
        val token = _continuationToken.value ?: return
        if (_isPaginating.value) return

        viewModelScope.launch {
            _isPaginating.value = true

            repository.searchResultsPagination(token, _selectedCategory.value).fold(
                onSuccess = { response ->
                    // Filter out duplicates based on id
                    val existingIds = _accumulatedResults.value.map { it.id }.toSet()
                    val uniqueNewResults = response.results.filter { it.id !in existingIds }
                    val newResults = _accumulatedResults.value + uniqueNewResults
                    _accumulatedResults.value = newResults
                    _continuationToken.value = response.continuationToken
                    
                    // Update cache
                    resultsCache[_selectedCategory.value] = CachedResults(
                        results = newResults,
                        continuationToken = response.continuationToken
                    )
                },
                onFailure = { exception ->
                    android.util.Log.e("SearchResultsVM", "Failed to load more: ${exception.message}")
                }
            )

            _isPaginating.value = false
        }
    }

    /**
     * Retry search after error
     */
    fun retry() {
        if (_searchQuery.value.isNotBlank()) {
            performSearch(_searchQuery.value, _selectedCategory.value)
        }
    }

    /**
     * Cache holder for results per category
     */
    private data class CachedResults(
        val results: List<TypedSearchResult>,
        val continuationToken: String?
    )
}
