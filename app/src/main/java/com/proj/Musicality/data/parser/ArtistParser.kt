package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object ArtistParser {

    fun extractArtistDetails(jsonString: String): ArtistDetails? {
        val response = runCatching {
            JsonParser.instance.decodeFromString<ArtistBrowseResponse>(jsonString)
        }.getOrNull() ?: return null

        val microformat = response.microformat?.microformatDataRenderer
        val name = microformat?.title ?: "Unknown Artist"
        val description = microformat?.description

        // Primary: microformat.thumbnail.thumbnails (square artist image, e.g. 544×544)
        val microformatThumbs = microformat?.thumbnail?.thumbnails
            ?.map { Thumbnail(it.url, it.width, it.height) } ?: emptyList()

        // Fallback: header banner thumbnail, force-cropped to 544×544 square via URL params
        val thumbnails = if (microformatThumbs.isNotEmpty()) {
            microformatThumbs
        } else {
            val bestHeader = response.header?.musicImmersiveHeaderRenderer?.thumbnail
                ?.musicThumbnailRenderer?.thumbnail?.thumbnails
                ?.maxByOrNull { it.width }
            if (bestHeader != null) {
                val squareUrl = upscaleThumbnail(bestHeader.url, 544) ?: bestHeader.url
                listOf(Thumbnail(squareUrl, 544, 544))
            } else emptyList()
        }

        val topSongs = mutableListOf<ArtistSong>()
        val albums = mutableListOf<ArtistContent>()
        val singles = mutableListOf<ArtistContent>()
        val videos = mutableListOf<ArtistVideo>()
        val livePerformances = mutableListOf<ArtistVideo>()
        val featuredOn = mutableListOf<ArtistContent>()
        val playlists = mutableListOf<ArtistContent>()
        val similarArtists = mutableListOf<ArtistRelated>()

        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents ?: emptyList()

        sections.forEach { section ->
            section.musicShelfRenderer?.let { shelf ->
                val title = shelf.title?.runs?.firstOrNull()?.text
                if (title == "Top songs") {
                    shelf.contents?.forEach { item ->
                        item.musicResponsiveListItemRenderer?.let { parseSongItem(it) }?.let { topSongs.add(it) }
                    }
                }
            }

            section.musicCarouselShelfRenderer?.let { carousel ->
                val title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: ""
                val items = carousel.contents ?: emptyList()

                when {
                    title == "Albums" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistContent(it)?.let { albums.add(it) } }
                    }
                    title == "Singles" || title == "Singles & EPs" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistContent(it)?.let { singles.add(it) } }
                    }
                    title == "Videos" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistVideo(it)?.let { videos.add(it) } }
                    }
                    title == "Live performances" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistVideo(it)?.let { livePerformances.add(it) } }
                    }
                    title == "Featured on" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistContent(it)?.let { featuredOn.add(it) } }
                    }
                    title.startsWith("Playlists") -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseArtistContent(it)?.let { playlists.add(it) } }
                    }
                    title == "Fans might also like" || title == "Related" -> items.forEach { item ->
                        item.musicTwoRowItemRenderer?.let { parseSimilarArtist(it)?.let { similarArtists.add(it) } }
                    }
                }
            }
        }

        return ArtistDetails(
            name = name,
            description = description,
            viewCount = extractAudienceText(jsonString),
            thumbnails = thumbnails,
            topSongs = topSongs,
            albums = albums,
            singles = singles,
            videos = videos,
            livePerformances = livePerformances,
            featuredOn = featuredOn,
            playlists = playlists,
            similarArtists = similarArtists
        )
    }

    private fun parseSongItem(item: MusicResponsiveListItemRenderer): ArtistSong? {
        val columns = item.flexColumns
        val titleText = columns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""
        val videoId = item.playlistItemData?.videoId
            ?: item.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.videoId
        val thumb = bestThumbUrl(item.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails)

        val playCount = columns?.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
        val albumName = columns?.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text

        return if (videoId != null && titleText.isNotEmpty()) {
            ArtistSong(titleText, videoId, thumb, null, playCount, albumName)
        } else null
    }

    private fun parseArtistContent(item: MusicTwoRowItemRenderer): ArtistContent? {
        val title = item.title?.runs?.firstOrNull()?.text ?: return null
        val browseId = item.navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val year = item.subtitle?.runs?.lastOrNull()?.text
        val thumb = bestThumbUrl(item.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails)
        val type = if (browseId.startsWith("MPREb_")) "ALBUM" else "PLAYLIST"
        return ArtistContent(title, browseId, year, thumb, type)
    }

    private fun parseArtistVideo(item: MusicTwoRowItemRenderer): ArtistVideo? {
        val title = item.title?.runs?.firstOrNull()?.text ?: return null
        val videoId = item.navigationEndpoint?.watchEndpoint?.videoId ?: return null
        val thumb = bestThumbUrl(item.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails)
        val views = item.subtitle?.runs?.firstOrNull()?.text
        return ArtistVideo(title, videoId, thumb, views)
    }

    private fun parseSimilarArtist(item: MusicTwoRowItemRenderer): ArtistRelated? {
        val name = item.title?.runs?.firstOrNull()?.text ?: return null
        val browseId = item.navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val subs = item.subtitle?.runs?.firstOrNull()?.text
        val thumb = bestThumbUrl(item.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails)
        return ArtistRelated(name, browseId, subs, thumb)
    }

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }

    private fun extractAudienceText(jsonString: String): String? {
        val root = runCatching { JsonParser.instance.parseToJsonElement(jsonString) }.getOrNull()
            ?: return null
        val audiencePattern = Regex(
            pattern = """\b\d[\d.,]*\s*[KMB]?\s*(monthly listeners|listeners|subscribers|followers)\b""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return findAudienceText(root, audiencePattern)
    }

    private fun findAudienceText(node: JsonElement, pattern: Regex): String? {
        return when (node) {
            is JsonObject -> node.values.asSequence().mapNotNull { findAudienceText(it, pattern) }.firstOrNull()
            is JsonArray -> node.asSequence().mapNotNull { findAudienceText(it, pattern) }.firstOrNull()
            is JsonPrimitive -> {
                val text = node.contentOrNull ?: return null
                pattern.find(text)?.value?.trim()
            }
            else -> null
        }
    }
}
