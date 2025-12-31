package com.example.musicality.data.mapper

import android.util.Log
import com.example.musicality.domain.model.*

private const val TAG = "ArtistMapper"

/**
 * Extension function to parse artist details from raw YouTube Music browse API response
 */
fun Map<String, Any>.parseArtistDetails(): ArtistDetail {
    var artistName = ""
    var monthlyAudience = ""
    var artistThumbnail = ""
    var about = ArtistAbout("", "")
    val topSongs = mutableListOf<ArtistTopSong>()
    val albums = mutableListOf<ArtistAlbum>()
    val featuredPlaylists = mutableListOf<ArtistPlaylist>()
    val artistPlaylists = mutableListOf<ArtistPlaylist>()
    val similarArtists = mutableListOf<SimilarArtist>()
    
    try {
        // --- 1. Header Info ---
        try {
            val header = this["header"] as? Map<*, *>
            val headerRenderer = header?.get("musicImmersiveHeaderRenderer") as? Map<*, *>
            
            if (headerRenderer != null) {
                // Artist name
                val title = headerRenderer["title"] as? Map<*, *>
                val titleRuns = title?.get("runs") as? List<*>
                val firstTitleRun = titleRuns?.getOrNull(0) as? Map<*, *>
                artistName = (firstTitleRun?.get("text") as? String) ?: ""
                
                // Monthly audience
                val monthlyListenerCount = headerRenderer["monthlyListenerCount"] as? Map<*, *>
                val listenerRuns = monthlyListenerCount?.get("runs") as? List<*>
                val firstListenerRun = listenerRuns?.getOrNull(0) as? Map<*, *>
                monthlyAudience = (firstListenerRun?.get("text") as? String) ?: ""
                
                // Artist thumbnail
                val thumbnail = headerRenderer["thumbnail"] as? Map<*, *>
                val musicThumbnailRenderer = thumbnail?.get("musicThumbnailRenderer") as? Map<*, *>
                val thumbObj = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
                val thumbnails = thumbObj?.get("thumbnails") as? List<*>
                if (thumbnails != null && thumbnails.isNotEmpty()) {
                    val thumbAtIndex1 = thumbnails.getOrNull(1) as? Map<*, *>
                    val lastThumb = thumbnails.last() as? Map<*, *>
                    artistThumbnail = (thumbAtIndex1?.get("url") as? String)
                        ?: (lastThumb?.get("url") as? String) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract header info: ${e.message}")
        }
        
        // Locate main content sections
        val contents = this["contents"] as? Map<*, *>
        val singleColumnRenderer = contents?.get("singleColumnBrowseResultsRenderer") as? Map<*, *>
        val tabs = singleColumnRenderer?.get("tabs") as? List<*>
        val firstTab = tabs?.getOrNull(0) as? Map<*, *>
        val tabRenderer = firstTab?.get("tabRenderer") as? Map<*, *>
        val tabContent = tabRenderer?.get("content") as? Map<*, *>
        val sectionListRenderer = tabContent?.get("sectionListRenderer") as? Map<*, *>
        val sections = sectionListRenderer?.get("contents") as? List<*>
        
        if (sections != null) {
            for (section in sections) {
                val sectionMap = section as? Map<*, *> ?: continue
                
                // --- 2. Top Songs (musicShelfRenderer) ---
                val musicShelfRenderer = sectionMap["musicShelfRenderer"] as? Map<*, *>
                if (musicShelfRenderer != null) {
                    try {
                        val shelfTitle = musicShelfRenderer["title"] as? Map<*, *>
                        val shelfTitleRuns = shelfTitle?.get("runs") as? List<*>
                        val shelfTitleText = (shelfTitleRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String
                        
                        if (shelfTitleText == "Top songs") {
                            val songsList = musicShelfRenderer["contents"] as? List<*>
                            // Get max 5 songs
                            songsList?.take(5)?.forEach { item ->
                                try {
                                    val itemMap = item as? Map<*, *>
                                    val songData = itemMap?.get("musicResponsiveListItemRenderer") as? Map<*, *>
                                    if (songData != null) {
                                        // Check for explicit badge
                                        var isExplicit = false
                                        val badges = songData["badges"] as? List<*>
                                        badges?.forEach { badge ->
                                            val badgeMap = badge as? Map<*, *>
                                            val inlineBadge = badgeMap?.get("musicInlineBadgeRenderer") as? Map<*, *>
                                            val icon = inlineBadge?.get("icon") as? Map<*, *>
                                            if ((icon?.get("iconType") as? String) == "MUSIC_EXPLICIT_BADGE") {
                                                isExplicit = true
                                            }
                                        }
                                        
                                        // Video ID
                                        val playlistItemData = songData["playlistItemData"] as? Map<*, *>
                                        val videoId = (playlistItemData?.get("videoId") as? String) ?: ""
                                        
                                        // Flex columns
                                        val flexColumns = songData["flexColumns"] as? List<*>
                                        
                                        fun getFlexColumnText(index: Int): String {
                                            val col = flexColumns?.getOrNull(index) as? Map<*, *>
                                            val renderer = col?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                                            val text = renderer?.get("text") as? Map<*, *>
                                            val runs = text?.get("runs") as? List<*>
                                            val firstRun = runs?.getOrNull(0) as? Map<*, *>
                                            return (firstRun?.get("text") as? String) ?: ""
                                        }
                                        
                                        val songName = getFlexColumnText(0)
                                        val songArtistName = getFlexColumnText(1)
                                        val plays = getFlexColumnText(2)
                                        val albumName = getFlexColumnText(3)
                                        
                                        // Thumbnail
                                        val thumbObj = songData["thumbnail"] as? Map<*, *>
                                        val musicThumbRenderer = thumbObj?.get("musicThumbnailRenderer") as? Map<*, *>
                                        val thumbInner = musicThumbRenderer?.get("thumbnail") as? Map<*, *>
                                        val thumbList = thumbInner?.get("thumbnails") as? List<*>
                                        val lastThumb = thumbList?.lastOrNull() as? Map<*, *>
                                        val thumbnail = (lastThumb?.get("url") as? String) ?: ""
                                        
                                        if (videoId.isNotBlank() && songName.isNotBlank()) {
                                            topSongs.add(
                                                ArtistTopSong(
                                                    videoId = videoId,
                                                    songName = songName,
                                                    artistName = songArtistName,
                                                    plays = plays,
                                                    albumName = albumName,
                                                    thumbnail = thumbnail,
                                                    isExplicit = isExplicit
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to extract song: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract top songs section: ${e.message}")
                    }
                }
                
                // --- 3. Carousel sections (Albums, Playlists, Similar Artists) ---
                val carouselRenderer = sectionMap["musicCarouselShelfRenderer"] as? Map<*, *>
                if (carouselRenderer != null) {
                    try {
                        val header = carouselRenderer["header"] as? Map<*, *>
                        val basicHeader = header?.get("musicCarouselShelfBasicHeaderRenderer") as? Map<*, *>
                        val headerTitle = basicHeader?.get("title") as? Map<*, *>
                        val headerRuns = headerTitle?.get("runs") as? List<*>
                        val title = (headerRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                        
                        val carouselContents = carouselRenderer["contents"] as? List<*>
                        
                        when {
                            title == "Albums" -> {
                                carouselContents?.forEach { item ->
                                    try {
                                        val itemMap = item as? Map<*, *>
                                        val albumData = itemMap?.get("musicTwoRowItemRenderer") as? Map<*, *>
                                        if (albumData != null) {
                                            // Check for explicit badge
                                            var isExplicit = false
                                            val subtitleBadges = albumData["subtitleBadges"] as? List<*>
                                            subtitleBadges?.forEach { badge ->
                                                val badgeMap = badge as? Map<*, *>
                                                val inlineBadge = badgeMap?.get("musicInlineBadgeRenderer") as? Map<*, *>
                                                val icon = inlineBadge?.get("icon") as? Map<*, *>
                                                if ((icon?.get("iconType") as? String) == "MUSIC_EXPLICIT_BADGE") {
                                                    isExplicit = true
                                                }
                                            }
                                            
                                            // Album name
                                            val albumTitle = albumData["title"] as? Map<*, *>
                                            val albumTitleRuns = albumTitle?.get("runs") as? List<*>
                                            val albumName = (albumTitleRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                                            
                                            // Year (last item in subtitle runs)
                                            val subtitle = albumData["subtitle"] as? Map<*, *>
                                            val subtitleRuns = subtitle?.get("runs") as? List<*>
                                            val year = (subtitleRuns?.lastOrNull() as? Map<*, *>)?.get("text") as? String ?: ""
                                            
                                            // Browse ID
                                            val navEndpoint = albumData["navigationEndpoint"] as? Map<*, *>
                                            val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
                                            val browseId = (browseEndpoint?.get("browseId") as? String) ?: ""
                                            
                                            // Thumbnail
                                            val thumbnailRenderer = albumData["thumbnailRenderer"] as? Map<*, *>
                                            val musicThumbRenderer = thumbnailRenderer?.get("musicThumbnailRenderer") as? Map<*, *>
                                            val thumbObj = musicThumbRenderer?.get("thumbnail") as? Map<*, *>
                                            val thumbList = thumbObj?.get("thumbnails") as? List<*>
                                            val lastThumb = thumbList?.lastOrNull() as? Map<*, *>
                                            val thumbnail = (lastThumb?.get("url") as? String) ?: ""
                                            
                                            if (browseId.isNotBlank() && albumName.isNotBlank()) {
                                                albums.add(
                                                    ArtistAlbum(
                                                        albumName = albumName,
                                                        year = year,
                                                        browseId = browseId,
                                                        thumbnail = thumbnail,
                                                        isExplicit = isExplicit
                                                    )
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to extract album: ${e.message}")
                                    }
                                }
                            }
                            
                            title == "Featured on" -> {
                                carouselContents?.forEach { item ->
                                    try {
                                        val itemMap = item as? Map<*, *>
                                        val playlistData = itemMap?.get("musicTwoRowItemRenderer") as? Map<*, *>
                                        if (playlistData != null) {
                                            val playlist = extractPlaylistFromRenderer(playlistData)
                                            if (playlist.playlistId.isNotBlank()) {
                                                featuredPlaylists.add(playlist)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to extract featured playlist: ${e.message}")
                                    }
                                }
                            }
                            
                            title.startsWith("Playlists by") -> {
                                carouselContents?.forEach { item ->
                                    try {
                                        val itemMap = item as? Map<*, *>
                                        val playlistData = itemMap?.get("musicTwoRowItemRenderer") as? Map<*, *>
                                        if (playlistData != null) {
                                            val playlist = extractPlaylistFromRenderer(playlistData, extractViewsAndArtist = true)
                                            if (playlist.playlistId.isNotBlank()) {
                                                artistPlaylists.add(playlist)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to extract artist playlist: ${e.message}")
                                    }
                                }
                            }
                            
                            title == "Fans might also like" -> {
                                carouselContents?.forEach { item ->
                                    try {
                                        val itemMap = item as? Map<*, *>
                                        val artistData = itemMap?.get("musicTwoRowItemRenderer") as? Map<*, *>
                                        if (artistData != null) {
                                            // Artist name
                                            val artistTitle = artistData["title"] as? Map<*, *>
                                            val artistTitleRuns = artistTitle?.get("runs") as? List<*>
                                            val name = (artistTitleRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                                            
                                            // Monthly audience
                                            val subtitle = artistData["subtitle"] as? Map<*, *>
                                            val subtitleRuns = subtitle?.get("runs") as? List<*>
                                            val audience = (subtitleRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                                            
                                            // Artist ID
                                            val navEndpoint = artistData["navigationEndpoint"] as? Map<*, *>
                                            val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
                                            val artistId = (browseEndpoint?.get("browseId") as? String) ?: ""
                                            
                                            // Thumbnail
                                            val thumbnailRenderer = artistData["thumbnailRenderer"] as? Map<*, *>
                                            val musicThumbRenderer = thumbnailRenderer?.get("musicThumbnailRenderer") as? Map<*, *>
                                            val thumbObj = musicThumbRenderer?.get("thumbnail") as? Map<*, *>
                                            val thumbList = thumbObj?.get("thumbnails") as? List<*>
                                            val lastThumb = thumbList?.lastOrNull() as? Map<*, *>
                                            val thumbnail = (lastThumb?.get("url") as? String) ?: ""
                                            
                                            if (artistId.isNotBlank() && name.isNotBlank()) {
                                                similarArtists.add(
                                                    SimilarArtist(
                                                        artistName = name,
                                                        thumbnail = thumbnail,
                                                        monthlyAudience = audience,
                                                        artistId = artistId
                                                    )
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to extract similar artist: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract carousel section: ${e.message}")
                    }
                }
                
                // --- 4. About Section ---
                val descRenderer = sectionMap["musicDescriptionShelfRenderer"] as? Map<*, *>
                if (descRenderer != null) {
                    try {
                        val subheader = descRenderer["subheader"] as? Map<*, *>
                        val subheaderRuns = subheader?.get("runs") as? List<*>
                        val views = (subheaderRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                        
                        val description = descRenderer["description"] as? Map<*, *>
                        val descRuns = description?.get("runs") as? List<*>
                        val descText = (descRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
                        
                        about = ArtistAbout(views = views, description = descText)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract about section: ${e.message}")
                    }
                }
            }
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing artist details: ${e.message}")
    }
    
    return ArtistDetail(
        artistName = artistName,
        monthlyAudience = monthlyAudience,
        artistThumbnail = artistThumbnail,
        about = about,
        topSongs = topSongs,
        albums = albums,
        featuredPlaylists = featuredPlaylists,
        artistPlaylists = artistPlaylists,
        similarArtists = similarArtists
    )
}

/**
 * Helper function to extract playlist info from musicTwoRowItemRenderer
 */
private fun extractPlaylistFromRenderer(
    playlistData: Map<*, *>,
    extractViewsAndArtist: Boolean = false
): ArtistPlaylist {
    // Playlist name
    val playlistTitle = playlistData["title"] as? Map<*, *>
    val titleRuns = playlistTitle?.get("runs") as? List<*>
    val name = (titleRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
    
    // Playlist ID
    val navEndpoint = playlistData["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val playlistId = (browseEndpoint?.get("browseId") as? String) ?: ""
    
    // Thumbnail
    val thumbnailRenderer = playlistData["thumbnailRenderer"] as? Map<*, *>
    val musicThumbRenderer = thumbnailRenderer?.get("musicThumbnailRenderer") as? Map<*, *>
    val thumbObj = musicThumbRenderer?.get("thumbnail") as? Map<*, *>
    val thumbList = thumbObj?.get("thumbnails") as? List<*>
    val lastThumb = thumbList?.lastOrNull() as? Map<*, *>
    val thumbnail = (lastThumb?.get("url") as? String) ?: ""
    
    var views = ""
    var artistName = ""
    
    if (extractViewsAndArtist) {
        val subtitle = playlistData["subtitle"] as? Map<*, *>
        val subtitleRuns = subtitle?.get("runs") as? List<*>
        
        subtitleRuns?.forEach { run ->
            val runMap = run as? Map<*, *>
            val text = (runMap?.get("text") as? String) ?: ""
            
            // Check for views
            if (text.contains("views")) {
                views = text
            }
            
            // Check for artist (has navigation to artist page)
            val navEndpoint = runMap?.get("navigationEndpoint") as? Map<*, *>
            val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
            val contextConfig = browseEndpoint?.get("browseEndpointContextSupportedConfigs") as? Map<*, *>
            val musicConfig = contextConfig?.get("browseEndpointContextMusicConfig") as? Map<*, *>
            val pageType = musicConfig?.get("pageType") as? String
            
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                artistName = text
            }
        }
    }
    
    return ArtistPlaylist(
        name = name,
        thumbnailImg = thumbnail,
        views = views,
        artistName = artistName,
        playlistId = playlistId
    )
}
