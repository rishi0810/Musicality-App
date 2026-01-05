package com.example.musicality.domain.repository

import com.example.musicality.domain.model.UrlFetchResult

/**
 * Repository interface for fetching URLs with custom headers
 * and saving responses locally.
 */
interface UrlFetchRepository {
    /**
     * Fetches a URL with YouTube Android client headers
     * and saves the response body locally.
     * 
     * @param url The URL to fetch
     * @return Result containing the fetch result with headers and file info
     */
    suspend fun fetchAndSave(url: String): Result<UrlFetchResult>
}
