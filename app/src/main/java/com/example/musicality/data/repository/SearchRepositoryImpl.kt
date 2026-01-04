package com.example.musicality.data.repository

import com.example.musicality.data.mapper.parseContinuationResponse
import com.example.musicality.data.mapper.parseSearchResultsResponse
import com.example.musicality.data.mapper.parseYouTubeMusicResponse
import com.example.musicality.data.mapper.toTypeParam
import com.example.musicality.data.model.FullSearchRequestDto
import com.example.musicality.data.model.SearchClientDto
import com.example.musicality.data.model.SearchContextDto
import com.example.musicality.data.model.SearchPaginationRequestDto
import com.example.musicality.data.model.SearchRequestDto
import com.example.musicality.data.remote.SearchApiService
import com.example.musicality.domain.model.SearchResponse
import com.example.musicality.domain.model.SearchResultType
import com.example.musicality.domain.model.SearchResultsResponse
import com.example.musicality.domain.repository.SearchRepository

/**
 * Implementation of SearchRepository
 */
class SearchRepositoryImpl(
    private val apiService: SearchApiService
) : SearchRepository {
    
    override suspend fun search(query: String): Result<SearchResponse> {
        return try {
            val requestBody = SearchRequestDto(
                input = query,
                context = SearchContextDto(
                    client = SearchClientDto()
                )
            )
            val response = apiService.search(requestBody)
            val parsedResponse = response.parseYouTubeMusicResponse()
            Result.success(parsedResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchResults(query: String, type: SearchResultType): Result<SearchResultsResponse> {
        return try {
            val requestBody = FullSearchRequestDto(
                query = query,
                params = type.toTypeParam(),
                context = SearchContextDto(
                    client = SearchClientDto()
                )
            )
            val response = apiService.searchResults(requestBody)
            val parsedResponse = response.parseSearchResultsResponse(type)
            Result.success(parsedResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchResultsPagination(ctoken: String, type: SearchResultType): Result<SearchResultsResponse> {
        return try {
            android.util.Log.d("SearchRepository", "Pagination ctoken: $ctoken")
            val requestBody = SearchPaginationRequestDto(
                context = SearchContextDto(
                    client = SearchClientDto()
                )
            )
            val response = apiService.searchResultsPagination(ctoken = ctoken, request = requestBody)
            val parsedResponse = response.parseContinuationResponse(type)
            Result.success(parsedResponse)
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Pagination failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}

