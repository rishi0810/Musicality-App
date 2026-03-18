package com.proj.Musicality.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Home : Route

    @Serializable
    data object Explore : Route

    @Serializable
    data class MoodCategory(
        val moodName: String
    ) : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Library : Route

    @Serializable
    data class LibraryCollection(
        val type: String
    ) : Route

    @Serializable
    data class Artist(
        val name: String,
        val browseId: String,
        val thumbnailUrl: String? = null,
        val audienceText: String? = null
    ) : Route

    @Serializable
    data class Album(
        val title: String,
        val browseId: String,
        val artistName: String? = null,
        val thumbnailUrl: String? = null,
        val year: String? = null
    ) : Route

    @Serializable
    data class Playlist(
        val title: String,
        val browseId: String,
        val author: String? = null,
        val thumbnailUrl: String? = null
    ) : Route
}
