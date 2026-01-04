package com.example.musicality.domain.repository

import com.example.musicality.domain.model.SearchResponse
import com.example.musicality.domain.model.SearchResultType
import com.example.musicality.domain.model.SearchResultsResponse

/**
 * Repository interface for search operations
 */
interface SearchRepository {
    /**
     * Search for music content (suggestions)
     * @param query The search query string
     * @return Result containing SearchResponse or error
     */
    suspend fun search(query: String): Result<SearchResponse>
    
    /**
     * Full search for music content with type filtering
     * @param query The search query string
     * @param type The type of results to search for
     * @return Result containing SearchResultsResponse or error
     */
    suspend fun searchResults(query: String, type: SearchResultType): Result<SearchResultsResponse>
    
    /**
     * Paginated search results using continuation token
     * @param ctoken The continuation token from previous response
     * @param type The type of results being searched
     * @return Result containing SearchResultsResponse or error
     */
    suspend fun searchResultsPagination(ctoken: String, type: SearchResultType): Result<SearchResultsResponse>
}

