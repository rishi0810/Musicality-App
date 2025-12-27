package com.example.musicality.domain.repository

import com.example.musicality.domain.model.SearchResponse

/**
 * Repository interface for search operations
 */
interface SearchRepository {
    /**
     * Search for music content
     * @param query The search query string
     * @return Result containing SearchResponse or error
     */
    suspend fun search(query: String): Result<SearchResponse>
}
