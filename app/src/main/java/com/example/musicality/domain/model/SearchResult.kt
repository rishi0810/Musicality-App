package com.example.musicality.domain.model

/**
 * Domain model for search response
 */
data class SearchResponse(
    val suggestions: List<String>,
    val results: List<SearchResult>
)

/**
 * Sealed class representing different types of search results
 */
sealed class SearchResult {
    abstract val id: String
    abstract val name: String
    abstract val thumbnailUrl: String
    abstract val isExplicit: Boolean
    abstract val year: String
    
    data class Song(
        override val id: String,
        override val name: String,
        override val thumbnailUrl: String,
        override val isExplicit: Boolean,
        override val year: String,
        val singer: String
    ) : SearchResult()
    
    data class Artist(
        override val id: String,
        override val name: String,
        override val thumbnailUrl: String,
        override val isExplicit: Boolean,
        override val year: String,
        val monthlyAudience: String
    ) : SearchResult()
    
    data class Playlist(
        override val id: String,
        override val name: String,
        override val thumbnailUrl: String,
        override val isExplicit: Boolean,
        override val year: String,
        val views: String
    ) : SearchResult()
    
    data class Album(
        override val id: String,
        override val name: String,
        override val thumbnailUrl: String,
        override val isExplicit: Boolean,
        override val year: String,
        val singer: String
    ) : SearchResult()
}
