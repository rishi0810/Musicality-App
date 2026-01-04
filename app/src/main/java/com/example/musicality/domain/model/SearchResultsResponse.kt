package com.example.musicality.domain.model

/**
 * Response container for typed search results with pagination support
 */
data class SearchResultsResponse(
    val results: List<TypedSearchResult>,
    val continuationToken: String?
)

/**
 * Enum representing search result categories/types
 */
enum class SearchResultType(val code: String, val displayName: String) {
    SONGS("s", "Songs"),
    VIDEOS("v", "Videos"),
    ALBUMS("al", "Albums"),
    ARTISTS("ar", "Artists"),
    COMMUNITY_PLAYLISTS("cp", "Community Playlists"),
    FEATURED_PLAYLISTS("fp", "Featured Playlists")
}

/**
 * Sealed class representing different types of search results with full details
 */
sealed class TypedSearchResult {
    abstract val id: String
    abstract val name: String
    abstract val thumbnailUrl: String

    /**
     * Song result from search
     */
    data class Song(
        override val id: String,  // videoId
        override val name: String,
        override val thumbnailUrl: String,
        val artistName: String,
        val albumName: String,
        val duration: String,
        val views: String
    ) : TypedSearchResult()

    /**
     * Video result from search
     */
    data class Video(
        override val id: String,  // videoId
        override val name: String,
        override val thumbnailUrl: String,
        val channelName: String,
        val views: String,
        val duration: String
    ) : TypedSearchResult()

    /**
     * Album result from search
     */
    data class Album(
        override val id: String,  // albumId
        override val name: String,
        override val thumbnailUrl: String,
        val artistName: String,
        val year: String
    ) : TypedSearchResult()

    /**
     * Artist result from search
     */
    data class Artist(
        override val id: String,  // artistId
        override val name: String,
        override val thumbnailUrl: String,
        val monthlyAudience: String
    ) : TypedSearchResult()

    /**
     * Community Playlist result from search
     */
    data class CommunityPlaylist(
        override val id: String,  // playlist browseId
        override val name: String,
        override val thumbnailUrl: String,
        val artistName: String,
        val views: String
    ) : TypedSearchResult()

    /**
     * Featured Playlist result from search
     */
    data class FeaturedPlaylist(
        override val id: String,  // playlist browseId
        override val name: String,
        override val thumbnailUrl: String,
        val artistName: String,
        val songCount: String
    ) : TypedSearchResult()
}
