package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.NextResponse
import com.proj.Musicality.data.json.PlaylistPanelContentItem
import com.proj.Musicality.data.json.PlaylistPanelVideoRenderer
import com.proj.Musicality.data.json.PlaylistPanelVideoWrapperRenderer
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.util.albumArtUrlOrNull
import com.proj.Musicality.util.upscaleThumbnail

object NextParser {

    private const val TAB_UP_NEXT = "Up next"
    private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
    private const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"

    fun extractUpNextQueue(jsonString: String): PlaybackQueue? {
        val response = runCatching {
            JsonParser.instance.decodeFromString<NextResponse>(jsonString)
        }.getOrNull() ?: return null

        val tabs = response.contents
            ?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs
            ?: emptyList()

        val upNextTab = tabs.firstOrNull { tab ->
            tab.tabRenderer?.title?.equals(TAB_UP_NEXT, ignoreCase = true) == true
        } ?: tabs.firstOrNull()

        val contents = upNextTab
            ?.tabRenderer
            ?.content
            ?.musicQueueRenderer
            ?.content
            ?.playlistPanelRenderer
            ?.contents
            ?: emptyList()

        val currentVideoId = response.currentVideoEndpoint?.watchEndpoint?.videoId

        val items = ArrayList<MediaItem>(contents.size)
        var selectedIndex: Int? = null

        for (content in contents) {
            val renderer = resolvePreferredRenderer(content)
                ?: continue

            val item = renderer.toMediaItem() ?: continue
            val isSelected = renderer.selected == true ||
                content.playlistPanelVideoWrapperRenderer?.primaryRenderer?.playlistPanelVideoRenderer?.selected == true
            if (selectedIndex == null && isSelected) selectedIndex = items.size
            items.add(item)
        }

        if (items.isEmpty()) return null

        val currentIndex = selectedIndex
            ?: currentVideoId
                ?.let { id -> items.indexOfFirst { it.videoId == id }.takeIf { it >= 0 } }
            ?: 0

        return PlaybackQueue(
            items = items,
            currentIndex = currentIndex,
            source = QueueSource.UP_NEXT
        )
    }

    private fun resolvePreferredRenderer(content: PlaylistPanelContentItem): PlaylistPanelVideoRenderer? {
        content.playlistPanelVideoRenderer?.let { return it }
        val wrapper = content.playlistPanelVideoWrapperRenderer ?: return null
        return resolvePreferredRenderer(wrapper)
    }

    private fun resolvePreferredRenderer(wrapper: PlaylistPanelVideoWrapperRenderer): PlaylistPanelVideoRenderer? {
        val primary = wrapper.primaryRenderer?.playlistPanelVideoRenderer
        val counterpart = wrapper.counterpart
            ?.firstOrNull()
            ?.counterpartRenderer
            ?.playlistPanelVideoRenderer

        val primaryType = primary?.extractMusicVideoType()
        val counterpartType = counterpart?.extractMusicVideoType()

        if (primaryType == MUSIC_VIDEO_TYPE_ATV) return primary
        if (counterpartType == MUSIC_VIDEO_TYPE_ATV) return counterpart
        if (primaryType == MUSIC_VIDEO_TYPE_OMV && counterpart != null) return counterpart

        return primary ?: counterpart
    }

    private fun PlaylistPanelVideoRenderer.extractMusicVideoType(): String? =
        navigationEndpoint
            ?.watchEndpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType

    private fun PlaylistPanelVideoRenderer.toMediaItem(): MediaItem? {
        val id = videoId?.takeIf { it.isNotBlank() }
            ?: navigationEndpoint?.watchEndpoint?.videoId?.takeIf { it.isNotBlank() }
            ?: return null

        val titleText = title?.runs?.firstOrNull()?.text?.trim().orEmpty()
        if (titleText.isBlank()) return null

        val rawThumb = thumbnail?.thumbnails?.maxByOrNull { it.width }?.url
        val duration = lengthText?.runs?.firstOrNull()?.text

        val bylineRuns = longBylineText?.runs
        val shortBylineRuns = shortBylineText?.runs

        val artistRuns = bylineRuns?.filter { run ->
            run.navigationEndpoint?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType == PAGE_TYPE_ARTIST
        }.orEmpty()

        val artistName = when {
            artistRuns.isNotEmpty() -> {
                if (artistRuns.size == 1) artistRuns[0].text
                else artistRuns.joinToString(", ") { it.text }
            }
            else -> shortBylineRuns?.firstOrNull()?.text.orEmpty()
        }

        val artistId = artistRuns.firstOrNull()
            ?.navigationEndpoint
            ?.browseEndpoint
            ?.browseId

        val albumRun = bylineRuns?.firstOrNull { run ->
            run.navigationEndpoint?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType == PAGE_TYPE_ALBUM
        }
        val albumName = albumRun?.text
        val albumId = albumRun
            ?.navigationEndpoint
            ?.browseEndpoint
            ?.browseId

        val musicVideoType = extractMusicVideoType()
        val thumb = albumArtUrlOrNull(rawThumb) ?: upscaleThumbnail(rawThumb, size = 544)

        return MediaItem(
            videoId = id,
            title = titleText,
            artistName = artistName,
            artistId = artistId,
            albumName = albumName,
            albumId = albumId,
            thumbnailUrl = thumb,
            durationText = duration,
            musicVideoType = musicVideoType
        )
    }
}
