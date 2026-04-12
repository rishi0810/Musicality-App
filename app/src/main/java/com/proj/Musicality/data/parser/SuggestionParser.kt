package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*

object SuggestionParser {

    fun extractSuggestions(jsonString: String): List<SearchSuggestionResult> {
        val response = runCatching {
            JsonParser.instance.decodeFromString<SuggestionResponse>(jsonString)
        }.getOrNull() ?: return emptyList()

        val results = mutableListOf<SearchSuggestionResult>()

        response.contents?.forEach { section ->
            section.searchSuggestionsSectionRenderer?.contents?.forEach { item ->
                item.searchSuggestionRenderer?.let { renderer ->
                    val text = renderer.suggestion?.runs?.joinToString("") { it.text } ?: ""
                    val query = renderer.navigationEndpoint?.searchEndpoint?.query
                    if (text.isNotEmpty()) {
                        results.add(SearchSuggestionResult(SuggestionType.SUGGESTION, text, query))
                    }
                }

                item.musicResponsiveListItemRenderer?.let { renderer ->
                    parseMusicItem(renderer)?.let { results.add(it) }
                }
            }
        }
        return results
    }

    private fun parseMusicItem(renderer: MusicResponsiveListItemRenderer): SearchSuggestionResult? {
        val nav = renderer.navigationEndpoint ?: return null
        var type = SuggestionType.UNKNOWN
        var id: String? = null

        if (nav.watchEndpoint != null) {
            id = nav.watchEndpoint.videoId
            val videoType = nav.watchEndpoint.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig?.musicVideoType
            type = if (videoType == "MUSIC_VIDEO_TYPE_ATV") SuggestionType.SONG else SuggestionType.VIDEO
        } else if (nav.browseEndpoint != null) {
            id = nav.browseEndpoint.browseId
            val pageType = nav.browseEndpoint.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig?.pageType
            type = when (pageType) {
                "MUSIC_PAGE_TYPE_ARTIST" -> SuggestionType.ARTIST
                "MUSIC_PAGE_TYPE_ALBUM" -> SuggestionType.ALBUM
                "MUSIC_PAGE_TYPE_PLAYLIST" -> SuggestionType.PLAYLIST
                else -> SuggestionType.UNKNOWN
            }
        }

        if (type == SuggestionType.UNKNOWN || id == null) return null

        val title = renderer.flexColumns?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.joinToString("") { it.text } ?: ""

        val thumbnails = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails

        var subtitle: String? = null
        val artists = mutableListOf<ArtistInfo>()
        var album: AlbumInfo? = null

        val subtitleRuns = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
        if (subtitleRuns != null) {
            subtitle = subtitleRuns.joinToString("") { it.text }

            // First pass: individual artists / album with proper browseIds (Shape B).
            subtitleRuns.forEach { run ->
                val runNav = run.navigationEndpoint?.browseEndpoint
                if (runNav != null) {
                    val pageType = runNav.browseEndpointContextSupportedConfigs
                        ?.browseEndpointContextMusicConfig?.pageType
                    if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                        artists.add(ArtistInfo(run.text, runNav.browseId ?: ""))
                    } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                        album = AlbumInfo(run.text, runNav.browseId ?: "")
                    }
                }
            }

            // Shape A fallback: concatenated artist text with no browseId. The artist
            // segment lives between the first and second " • " separators, e.g.
            //   "Song" • "Artist A & Artist B" • "8.3M plays"
            // Pair the display name with the overflow menu's "Go to artist" browseId
            // (the primary artist — the only id recoverable when flex[1] runs carry
            // no navigation) so downstream consumers (e.g. recordPlayback) have a
            // usable artist_id for personalization even for collab tracks.
            if (artists.isEmpty()) {
                val bulletIdxs = subtitleRuns.mapIndexedNotNull { i, run ->
                    if (run.text.trim() == "•") i else null
                }
                if (bulletIdxs.size >= 2) {
                    val segment = subtitleRuns
                        .subList(bulletIdxs[0] + 1, bulletIdxs[1])
                        .joinToString("") { it.text }
                        .trim()
                    if (segment.isNotEmpty()) {
                        val menuArtistId = renderer.menu.artistIdFromMenu().orEmpty()
                        artists.add(ArtistInfo(segment, menuArtistId))
                    }
                }
            }
        }

        // Album for SONG suggestions lives in flex[2] (not flex[1]).
        if (album == null && type == SuggestionType.SONG) {
            val albumRun = renderer.flexColumns?.getOrNull(2)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.firstOrNull { run ->
                    run.navigationEndpoint?.browseEndpoint
                        ?.browseEndpointContextSupportedConfigs
                        ?.browseEndpointContextMusicConfig
                        ?.pageType == "MUSIC_PAGE_TYPE_ALBUM"
                }
            if (albumRun != null) {
                album = AlbumInfo(albumRun.text, albumRun.navigationEndpoint?.browseEndpoint?.browseId ?: "")
            }
        }

        // Last-ditch album fallback via overflow menu (id only, no name).
        if (album == null && type == SuggestionType.SONG) {
            renderer.menu?.menuRenderer?.items?.forEach { menuItem ->
                val menuNav = menuItem.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint
                if (menuNav?.browseEndpointContextSupportedConfigs
                        ?.browseEndpointContextMusicConfig?.pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                    album = AlbumInfo(null, menuNav.browseId ?: "")
                }
            }
        }

        return SearchSuggestionResult(type, title, id, subtitle, thumbnails, artists.ifEmpty { null }, album)
    }

    private fun MenuRenderer?.artistIdFromMenu(): String? {
        val items = this?.menuRenderer?.items ?: return null
        for (item in items) {
            val ep = item.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint ?: continue
            val pt = ep.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
            if (pt == "MUSIC_PAGE_TYPE_ARTIST") {
                return ep.browseId?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
