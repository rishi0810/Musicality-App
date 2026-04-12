package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.Run

private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
private const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
private const val PAGE_TYPE_USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"

private val COUNT_SUFFIX = Regex("""\s(plays|views)$""", RegexOption.IGNORE_CASE)

internal fun List<Run>.primaryArtistRun(): Run? {
    // Prefer a run explicitly typed as an artist page.
    firstOrNull { it.pageType() == PAGE_TYPE_ARTIST }?.let { return it }
    // Then a user-channel page (uploader for VIDEO search results).
    firstOrNull { it.pageType() == PAGE_TYPE_USER_CHANNEL }?.let { return it }

    // Fallback for concatenated-collab strings like "Artist A & Artist B" that
    // YT Music returns as a single plain-text run with no navigationEndpoint.
    // Skip separators, empty runs, count suffixes ("X plays"/"X views"),
    // and runs that link to an album/playlist.
    return firstOrNull { run ->
        val text = run.text
        if (text.isBlank()) return@firstOrNull false
        if (text.isSeparator()) return@firstOrNull false
        if (COUNT_SUFFIX.containsMatchIn(text)) return@firstOrNull false
        val pt = run.pageType()
        pt == null || pt == PAGE_TYPE_ARTIST || pt == PAGE_TYPE_USER_CHANNEL
    }
}

private fun Run.pageType(): String? =
    navigationEndpoint?.browseEndpoint
        ?.browseEndpointContextSupportedConfigs
        ?.browseEndpointContextMusicConfig
        ?.pageType

private fun String.isSeparator(): Boolean {
    val t = trim()
    return t == "•" || t == "&" || t == ","
}

internal fun Run.artistBrowseIdOrNull(): String? =
    navigationEndpoint?.browseEndpoint?.browseId?.takeIf { it.isNotBlank() }

internal fun String.primaryArtistName(): String =
    substringBefore(',').trim()

