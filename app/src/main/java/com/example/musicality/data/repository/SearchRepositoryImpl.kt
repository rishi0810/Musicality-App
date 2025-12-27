package com.example.musicality.data.repository

import com.example.musicality.data.mapper.parseYouTubeMusicResponse
import com.example.musicality.data.model.SearchClientDto
import com.example.musicality.data.model.SearchContextDto
import com.example.musicality.data.model.SearchRequestDto
import com.example.musicality.data.remote.SearchApiService
import com.example.musicality.domain.model.SearchResponse
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
}
