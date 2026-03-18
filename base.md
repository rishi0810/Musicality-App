# Music App — Product Requirements Document

## Material 3 Expressive Edition

**Version:** 2.0  
**Date:** February 2026  
**Target:** Android 14+ (API 34+), Kotlin 2.1, Compose BOM 2025.12.00, Material 3 1.4.x (Expressive alpha for advanced components)

---

## 1. Architecture Overview

### 1.1 Design Philosophy

Every interaction in this app follows three principles from Material 3 Expressive:

1. **Spring-first motion** — All animations use physics-based springs via `MotionScheme.expressive()`. No `tween()` unless a strict deadline is required (e.g., color fades). Springs are interruptible, feel natural, and carry velocity across interrupted gestures.
2. **Zero-fetch UI** — Seed data renders on the same frame as navigation. The user never sees a blank screen. Shimmer placeholders fill in only for content that requires a network fetch.
3. **Shape as language** — Rounded polygons, morph transitions, and M3 Expressive `MaterialShapes` communicate state changes (playing/paused, selected/unselected) without relying on opacity alone.

### 1.2 Dependency Stack

| Dependency | Version | Purpose | Size |
|---|---|---|---|
| `kotlinx.serialization` | 1.7.3 | JSON parsing + Nav route serialization | ~200 KB |
| `OkHttp` | 4.12.0 | HTTP client (pooling, compression) | ~400 KB |
| `Coil` | 3.1.0 | Image loading + disk/memory cache | ~300 KB |
| `Kotlin Coroutines` | 1.9.0 | Async operations | ~150 KB |
| `Compose BOM` | 2025.12.00 | UI toolkit (runtime, M3, animation, navigation) | ~3 MB |
| `navigation-compose` | 2.9.7 | Type-safe routes | included in BOM |
| `graphics-shapes` | 1.0.1 | `RoundedPolygon` / `Morph` for shape morphing | ~80 KB |
| `androidx.media3` | 1.6.0 | ExoPlayer for audio playback | ~2 MB |
| **Total** | | | **~6 MB** |

Removed from prior spec: No Retrofit (OkHttp direct), no Room/SQLite (in-memory cache), no Gson/Moshi (kotlinx.serialization covers all). Added: `graphics-shapes` for morph animations, `media3` for proper audio playback lifecycle.

### 1.3 Shared JSON Parser

```kotlin
object JsonParser {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
```

---

## 2. API Layer — Innertube Integration

All curl commands and parsing logic below are the foundation for the network layer. The API layer is intentionally thin: each function returns a parsed domain model, with no UI concerns.

### 2.1 VisitorID

Two separate visitor IDs are required for two different client identities.

#### Method 1 — Browsing (WEB_REMIX)

Used for: search, suggestions, browse (artist/album/playlist)

```bash
curl -H 'accept: application/json' \
     -H 'accept-language: en-US,en;q=0.9' \
     -H 'cache-control: no-cache' \
     --compressed \
     -H 'accept-charset: UTF-8' \
     -H 'user-agent: ktor-client' \
     https://music.youtube.com/sw.js_data
```

Parse:

```kotlin
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun extractVisitorData(rawResponse: String): String? {
    val regex = Regex("""Cg[a-zA-Z0-9%_-]{20,}""")
    val match = regex.find(rawResponse)?.value
    return match?.let {
        URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
    }
}
```

#### Method 2 — Streaming (ANDROID)

Used for: `reel_item_watch` (song/video stream URLs)

```bash
curl -H 'user-agent: com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip' \
     -H 'x-goog-api-format-version: 2' \
     -H 'content-type: application/json' \
     -H 'accept-language: en-GB, en;q=0.9' \
     --compressed -X POST \
     'https://youtubei.googleapis.com/youtubei/v1/visitor_id?prettyPrint=false' \
     -d '{"context":{"client":{"clientName":"ANDROID","clientVersion":"21.03.36","clientScreen":"WATCH","platform":"MOBILE","osName":"Android","osVersion":"16","androidSdkVersion":36,"hl":"en-GB","gl":"GB","utcOffsetMinutes":0},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}}}'
```

Parse:

```kotlin
@Serializable
data class YouTubeResponse(val responseContext: ResponseContext)

@Serializable
data class ResponseContext(val visitorData: String, val rolloutToken: String? = null)

fun extract(jsonString: String): String {
    val format = Json { ignoreUnknownKeys = true }
    return format.decodeFromString<YouTubeResponse>(jsonString).responseContext.visitorData
}
```

#### VisitorID Manager

```kotlin
object VisitorManager {
    var browseVisitorId: String = ""
    var streamVisitorId: String = ""

    suspend fun initialize() = coroutineScope {
        val browsing = async(Dispatchers.IO) { fetchBrowseVisitorId() }
        val streaming = async(Dispatchers.IO) { fetchStreamVisitorId() }
        browseVisitorId = browsing.await()
        streamVisitorId = streaming.await()
    }
}
```

### 2.2 Search Suggestions

```bash
curl -H 'x-goog-api-format-version: 1' \
     -H 'x-youtube-client-name: 67' \
     -H 'x-youtube-client-version: 1.20260124.01.00' \
     -H 'x-origin: https://music.youtube.com' \
     -H 'referer: https://music.youtube.com/' \
     -H 'x-goog-visitor-id: id' \
     -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0' \
     -H 'accept: application/json' \
     -H 'accept-language: en-US,en;q=0.9' \
     -H 'cache-control: no-cache' \
     --compressed \
     -H 'accept-charset: UTF-8' \
     -H 'content-type: application/json' \
     -X POST 'https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false' \
     -d '{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20260124.01.00","gl":"US","hl":"en","visitorData":"id"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"input":"m"}'
```

#### Data Models

```kotlin
@Serializable
data class SuggestionResponse(val contents: List<SuggestionSection>? = null)

@Serializable
data class SuggestionSection(val searchSuggestionsSectionRenderer: SuggestionSectionRenderer? = null)

@Serializable
data class SuggestionSectionRenderer(val contents: List<SuggestionItem>? = null)

@Serializable
data class SuggestionItem(
    val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class SearchSuggestionRenderer(
    val suggestion: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val menu: MenuRenderer? = null
)

@Serializable
data class FlexColumn(val musicResponsiveListItemFlexColumnRenderer: TextRenderer? = null)

@Serializable
data class TextRenderer(val text: Runs? = null)

@Serializable
data class Runs(val runs: List<Run>? = null)

@Serializable
data class Run(val text: String, val navigationEndpoint: NavigationEndpoint? = null)

@Serializable
data class ThumbnailRenderer(val musicThumbnailRenderer: ThumbnailData? = null)

@Serializable
data class ThumbnailData(val thumbnail: Thumbnails? = null)

@Serializable
data class Thumbnails(val thumbnails: List<Thumbnail>? = null)

@Serializable
data class Thumbnail(val url: String, val width: Int, val height: Int)

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val searchEndpoint: SearchEndpoint? = null
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val watchEndpointMusicSupportedConfigs: WatchConfig? = null
)

@Serializable
data class WatchConfig(val watchEndpointMusicConfig: WatchMusicConfig? = null)

@Serializable
data class WatchMusicConfig(val musicVideoType: String? = null)

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseConfig? = null
)

@Serializable
data class BrowseConfig(val browseEndpointContextMusicConfig: MusicConfig? = null)

@Serializable
data class MusicConfig(val pageType: String? = null)

@Serializable
data class SearchEndpoint(val query: String? = null)

@Serializable
data class MenuRenderer(val menuRenderer: MenuItems? = null)

@Serializable
data class MenuItems(val items: List<MenuItem>? = null)

@Serializable
data class MenuItem(val menuNavigationItemRenderer: MenuNavigationItem? = null)

@Serializable
data class MenuNavigationItem(val navigationEndpoint: NavigationEndpoint? = null)
```

#### Domain Models

```kotlin
enum class SuggestionType { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, SUGGESTION, UNKNOWN }

data class SearchSuggestionResult(
    val type: SuggestionType,
    val title: String,
    val id: String? = null,
    val subtitle: String? = null,
    val thumbnails: List<Thumbnail>? = null,
    val artists: List<ArtistInfo>? = null,
    val album: AlbumInfo? = null
)

data class ArtistInfo(val name: String, val id: String)
data class AlbumInfo(val name: String?, val id: String)
```

#### Extraction Logic

```kotlin
fun extractSuggestions(jsonString: String): List<SearchSuggestionResult> {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val response = runCatching {
        json.decodeFromString<SuggestionResponse>(jsonString)
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
        type = if (videoType == "MUSIC_VIDEO_TYPE_ATV") SuggestionType.SONG
               else SuggestionType.VIDEO
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
        subtitleRuns.forEach { run ->
            val runNav = run.navigationEndpoint?.browseEndpoint
            if (runNav != null) {
                val pt = runNav.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig?.pageType
                if (pt == "MUSIC_PAGE_TYPE_ARTIST") {
                    artists.add(ArtistInfo(run.text, runNav.browseId ?: ""))
                } else if (pt == "MUSIC_PAGE_TYPE_ALBUM") {
                    album = AlbumInfo(run.text, runNav.browseId ?: "")
                }
            }
        }
    }

    if (album == null && type == SuggestionType.SONG) {
        renderer.menu?.menuRenderer?.items?.forEach { menuItem ->
            val menuNav = menuItem.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint
            if (menuNav?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig?.pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                album = AlbumInfo(null, menuNav.browseId ?: "")
            }
        }
    }

    return SearchSuggestionResult(type, title, id, subtitle, thumbnails,
        artists.ifEmpty { null }, album)
}
```

### 2.3 Search Per Field

#### Params Mapping

```kotlin
enum class SearchType(val params: String) {
    SONGS("EgWKAQIIAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    VIDEOS("EgWKAQIQAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    ARTISTS("EgWKAQIgAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    ALBUMS("EgWKAQIYAWoQEAMQBBAFEBAQChAJEBUQEQ=="),
    PLAYLISTS("EgeKAQQoAEABahAQAxAEEAUQEBAKEAkQFRAR"),
    FEATURED_PLAYLISTS("EgeKAQQoADgBahIQCRAKEAUQAxAEEBUQDhAQEBE=")
}
```

#### Curl (Songs example — same pattern for all types)

```bash
curl 'https://music.youtube.com/youtubei/v1/search?prettyPrint=false' \
  -H 'content-type: application/json' \
  -H 'user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36' \
  -H 'x-goog-visitor-id: <VISITOR_ID>' \
  --data-raw '{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20250127.01.00"}},"query":"Coldplay","params":"EgWKAQIIAWoQEAMQBBAFEBAQChAJEBUQEQ%3D%3D"}'
```

#### Search Result Models

```kotlin
@Serializable
data class SearchResultResponse(val contents: SearchResultContents? = null)

@Serializable
data class SearchResultContents(val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null)

@Serializable
data class TabbedSearchResultsRenderer(val tabs: List<TabRenderer> = emptyList())

@Serializable
data class TabRenderer(val content: TabContent? = null)

@Serializable
data class TabContent(val sectionListRenderer: SectionListRenderer? = null)

@Serializable
data class SectionListRenderer(val contents: List<SectionContent> = emptyList())

@Serializable
data class SectionContent(val musicShelfRenderer: MusicShelfRenderer? = null)

@Serializable
data class MusicShelfRenderer(val contents: List<MusicResponsiveListItemWrapper> = emptyList())

@Serializable
data class MusicResponsiveListItemWrapper(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer
)
```

#### Domain Result Types

```kotlin
data class SongResult(
    val title: String, val videoId: String, val artist: String,
    val album: String?, val duration: String?, val thumb: String?
)
data class VideoResult(
    val title: String, val videoId: String, val artist: String,
    val views: String?, val duration: String?, val thumb: String?
)
data class ArtistResult(
    val name: String, val artistId: String,
    val subscribers: String?, val thumb: String?
)
data class AlbumResult(
    val title: String, val albumId: String, val artist: String,
    val year: String?, val thumb: String?
)
data class PlaylistResult(
    val title: String, val playlistId: String, val author: String,
    val countOrViews: String?, val thumb: String?
)
```

#### Extraction Functions

```kotlin
private val json = Json { ignoreUnknownKeys = true }

private fun getShelfItems(jsonResponse: String) =
    json.decodeFromString<SearchResultResponse>(jsonResponse)
        .contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
        ?.content?.sectionListRenderer?.contents?.firstOrNull()
        ?.musicShelfRenderer?.contents ?: emptyList()

fun extractSongs(jsonResponse: String): List<SongResult> =
    getShelfItems(jsonResponse).map { wrapper ->
        val item = wrapper.musicResponsiveListItemRenderer
        val cols = item.flexColumns
        val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.firstOrNull()?.text ?: ""
        val videoId = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: item.overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                ?.watchEndpoint?.videoId ?: ""
        val meta = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs ?: emptyList()
        val artist = meta.getOrNull(0)?.text ?: ""
        val album = if (meta.size >= 3) meta[2].text else null
        val duration = if (meta.size >= 5) meta[4].text
                       else if (meta.size >= 3 && album == null) meta[2].text else null
        val thumb = item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
            ?.thumbnails?.lastOrNull()?.url
        SongResult(title, videoId, artist, album, duration, thumb)
    }

fun extractVideos(jsonResponse: String): List<VideoResult> =
    getShelfItems(jsonResponse).map { wrapper ->
        val item = wrapper.musicResponsiveListItemRenderer
        val cols = item.flexColumns
        val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.firstOrNull()?.text ?: ""
        val videoId = item.overlay?.musicItemThumbnailOverlayRenderer?.content
            ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
            ?: cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId ?: ""
        val meta = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs ?: emptyList()
        VideoResult(title, videoId, meta.getOrNull(0)?.text ?: "",
            if (meta.size >= 3) meta[2].text else null,
            if (meta.size >= 5) meta[4].text else null,
            item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?.thumbnails?.lastOrNull()?.url)
    }

fun extractArtists(jsonResponse: String): List<ArtistResult> =
    getShelfItems(jsonResponse).map { wrapper ->
        val item = wrapper.musicResponsiveListItemRenderer
        val cols = item.flexColumns
        ArtistResult(
            cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.text ?: "",
            item.navigationEndpoint?.browseEndpoint?.browseId ?: "",
            cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.lastOrNull()?.text,
            item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?.thumbnails?.lastOrNull()?.url
        )
    }

fun extractAlbums(jsonResponse: String): List<AlbumResult> =
    getShelfItems(jsonResponse).map { wrapper ->
        val item = wrapper.musicResponsiveListItemRenderer
        val cols = item.flexColumns
        val meta = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs ?: emptyList()
        AlbumResult(
            cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.text ?: "",
            cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
                ?: item.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint
                    ?.watchPlaylistEndpoint?.playlistId ?: "",
            if (meta.size >= 3) meta[2].text else "",
            if (meta.size >= 5) meta[4].text else null,
            item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?.thumbnails?.lastOrNull()?.url
        )
    }

fun extractPlaylists(jsonResponse: String): List<PlaylistResult> =
    getShelfItems(jsonResponse).map { wrapper ->
        val item = wrapper.musicResponsiveListItemRenderer
        val cols = item.flexColumns
        val meta = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs ?: emptyList()
        PlaylistResult(
            cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.text ?: "",
            item.overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                ?.watchPlaylistEndpoint?.playlistId
                ?: cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
                    ?.text?.runs?.firstOrNull()?.navigationEndpoint
                    ?.browseEndpoint?.browseId ?: "",
            meta.getOrNull(0)?.text ?: "",
            if (meta.size >= 3) meta[2].text else null,
            item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?.thumbnails?.lastOrNull()?.url
        )
    }
```

### 2.4 Song Fetch (Stream URL)

```bash
curl --location \
  'https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&id=P8gkyyuLitM&%24fields=playerResponse' \
  --header 'user-agent: com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip' \
  --header 'x-goog-api-format-version: 2' \
  --header 'content-type: application/json' \
  --header 'accept-language: en-GB, en;q=0.9' \
  --data '{
    "context":{"client":{"clientName":"ANDROID","clientVersion":"21.03.36","clientScreen":"WATCH","platform":"MOBILE","visitorData":"<METHOD_2_ID>","osName":"Android","osVersion":"16","androidSdkVersion":36,"hl":"en-GB","gl":"GB","utcOffsetMinutes":0},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},
    "playerRequest":{"videoId":"P8gkyyuLitM","cpn":"ipimdu7K_Rq5KeeN","contentCheckOk":true,"racyCheckOk":true},
    "disablePlayerResponse":false
  }'
```

```kotlin
@Serializable
data class SongPlaybackDetails(
    val streamUrl: String?,
    val expiry: Long?,
    val viewCount: String,
    val lengthSeconds: Long,
    val channelId: String,
    val description: String
)

@Serializable
data class StreamResponse(val playerResponse: PlayerResponse? = null)
@Serializable
data class PlayerResponse(
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null
)
@Serializable
data class VideoDetails(
    val viewCount: String = "0", val lengthSeconds: String = "0",
    val channelId: String = "", val shortDescription: String = ""
)
@Serializable
data class StreamingData(val adaptiveFormats: List<AdaptiveFormat> = emptyList())
@Serializable
data class AdaptiveFormat(val itag: Int, val url: String)

fun extractSongDetails(jsonResponse: String): SongPlaybackDetails {
    val json = Json { ignoreUnknownKeys = true }
    val response = json.decodeFromString<StreamResponse>(jsonResponse)
    val formats = response.playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
    val details = response.playerResponse?.videoDetails
    val bestFormat = formats.find { it.itag == 251 } ?: formats.find { it.itag == 140 }
    return SongPlaybackDetails(
        streamUrl = bestFormat?.url, expiry = null,
        viewCount = details?.viewCount ?: "0",
        lengthSeconds = details?.lengthSeconds?.toLongOrNull() ?: 0L,
        channelId = details?.channelId ?: "",
        description = details?.shortDescription ?: ""
    )
}
```

### 2.5 Artist Fetch

```bash
curl -H 'x-goog-api-format-version: 1' \
     -H 'x-youtube-client-name: 67' \
     -H 'x-youtube-client-version: 1.20260124.01.00' \
     -H 'x-origin: https://music.youtube.com' \
     -H 'referer: https://music.youtube.com/' \
     -H 'x-goog-visitor-id: <VISITOR_ID>' \
     -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0' \
     -H 'accept: application/json' \
     -H 'accept-language: en-US,en;q=0.9' \
     -H 'cache-control: no-cache' \
     --compressed -H 'accept-charset: UTF-8' \
     -H 'content-type: application/json' \
     -X POST 'https://music.youtube.com/youtubei/v1/browse?prettyPrint=false' \
     -d '{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20260124.01.00","gl":"US","hl":"en","visitorData":"<VISITOR_ID>"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"browseId":"UCyI0V6RmULLsRxegMTCmkWQ"}'
```

Domain models and extraction follow the same pattern as the original spec — `ArtistDetails`, `ArtistSong`, `ArtistContent`, `ArtistVideo`, `ArtistRelated` remain unchanged. See Section 2.5 of the innertube reference for the full parsing logic.

### 2.6 Album Fetch

```bash
curl -H 'x-goog-api-format-version: 1' \
     -H 'x-youtube-client-name: 67' \
     -H 'x-youtube-client-version: 1.20260124.01.00' \
     -H 'x-origin: https://music.youtube.com' \
     -H 'referer: https://music.youtube.com/' \
     -H 'x-goog-visitor-id: <VISITOR_ID>' \
     -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0' \
     -H 'accept: application/json' \
     -H 'accept-language: en-US,en;q=0.9' \
     -H 'cache-control: no-cache' \
     --compressed -H 'accept-charset: UTF-8' \
     -H 'content-type: application/json' \
     -X POST 'https://music.youtube.com/youtubei/v1/browse?prettyPrint=false' \
     -d '{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20260124.01.00","gl":"US","hl":"en","visitorData":"<VISITOR_ID>"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"browseId":"MPREb_m7saomf2NFN"}'
```

Domain models: `AlbumPage`, `ArtistTiny`, `Track` — parsing logic unchanged from innertube reference.

### 2.7 Playlist Fetch

Supports both **Editorial** (`VLRDCLAK5uy_...`) and **Community** (`VLPL...`) playlists using the same browse endpoint. Domain models: `PlaylistPage`, `PlaylistTrack`, `PlaylistArtist` — parsing logic unchanged from innertube reference.

### 2.8 Video Fetch

Same `reel_item_watch` endpoint as Song Fetch but used for video content. Domain model: `VideoFetchData` — parsing logic unchanged from innertube reference.

---

## 3. Unified Data Model

### 3.1 MediaItem

The single object the player ever sees. Constructed from any source without additional fetches.

```kotlin
data class MediaItem(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String?,
    val durationText: String?,
    val musicVideoType: String?
)
```

#### Conversion Extensions

```kotlin
fun SongResult.toMediaItem() = MediaItem(
    videoId, title, artist, null, album, null, thumb, duration, "MUSIC_VIDEO_TYPE_ATV"
)

fun Track.toMediaItem(album: AlbumPage) = MediaItem(
    videoId ?: "", title, album.artist.name, album.artist.id,
    album.title, null, album.thumbnail, duration, "MUSIC_VIDEO_TYPE_ATV"
)

fun PlaylistTrack.toMediaItem() = MediaItem(
    videoId, title, artists.firstOrNull()?.name ?: "", artists.firstOrNull()?.id,
    albumOrDate, null, thumbnailUrl, duration, musicVideoType
)

fun ArtistSong.toMediaItem(artistName: String, artistId: String?) = MediaItem(
    videoId, title, artistName, artistId, album, null, image, null, "MUSIC_VIDEO_TYPE_ATV"
)
```

### 3.2 Queue

```kotlin
data class PlaybackQueue(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val source: QueueSource
)

enum class QueueSource { SEARCH, ALBUM, PLAYLIST, ARTIST_TOP_SONGS, SINGLE }
```

### 3.3 Seed Data

```kotlin
data class ArtistSeed(val name: String, val browseId: String, val thumbnailUrl: String? = null)
data class AlbumSeed(val title: String, val browseId: String, val artistName: String? = null, val thumbnailUrl: String? = null, val year: String? = null)
data class PlaylistSeed(val title: String, val browseId: String, val author: String? = null, val thumbnailUrl: String? = null)
```

---

## 4. Caching Architecture

```
┌──────────────┬──────────────┬───────────────────────────────────────┐
│ Layer        │ Storage      │ Content                               │
├──────────────┼──────────────┼───────────────────────────────────────┤
│ Image Cache  │ Disk + Mem   │ Thumbnail URLs → Bitmaps (Coil)      │
│              │ 100MB disk   │ Auto-managed                         │
├──────────────┼──────────────┼───────────────────────────────────────┤
│ Browse Cache │ In-Memory    │ browseId → parsed response            │
│              │ ~30 entries  │ ArtistDetails, AlbumPage, PlaylistPage│
├──────────────┼──────────────┼───────────────────────────────────────┤
│ Stream Cache │ In-Memory    │ videoId → stream URL + expiry         │
│              │ ~20 entries  │ TTL: min(6h, URL expire param)        │
├──────────────┼──────────────┼───────────────────────────────────────┤
│ Search Cache │ In-Memory    │ (query + params) → parsed results     │
│              │ ~15 entries  │ TTL: 5 minutes                        │
├──────────────┼──────────────┼───────────────────────────────────────┤
│ Visitor ID   │ SharedPrefs  │ visitorData string                    │
│              │              │ Refresh: once per cold start          │
└──────────────┴──────────────┴───────────────────────────────────────┘
```

```kotlin
class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    @Synchronized fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) {
        map[key] = value
        if (map.size > maxSize) map.remove(map.entries.first().key)
    }
}

data class CachedStream(val url: String, val expiresAtMillis: Long)

object AppCache {
    val browse = LruCache<String, Any>(30)
    val stream = LruCache<String, CachedStream>(20)

    fun getStreamUrl(videoId: String): String? {
        val cached = stream.get(videoId) ?: return null
        if (System.currentTimeMillis() > cached.expiresAtMillis) return null
        return cached.url
    }

    fun putStreamUrl(videoId: String, url: String) {
        val expire = url.substringAfter("expire=", "")
            .substringBefore("&").toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 6 * 3600 * 1000)
        stream.put(videoId, CachedStream(url, expire))
    }
}
```

### Request Deduplication

```kotlin
class RequestDeduplicator {
    private val inFlight = ConcurrentHashMap<String, Deferred<*>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T): T {
        val existing = inFlight[key] as? Deferred<T>
        if (existing != null && existing.isActive) return existing.await()
        val deferred = coroutineScope {
            async(Dispatchers.IO) {
                try { block() } finally { inFlight.remove(key) }
            }
        }
        inFlight[key] = deferred
        return deferred.await()
    }
}
```

---

## 5. Screen Architecture & Navigation

### 5.1 Screens

```
┌──────────────────────────────────────────────────────────────┐
│  1. SearchScreen        (Home — suggestions + results)       │
│  2. ArtistScreen        (Artist browse page)                 │
│  3. AlbumScreen         (Album browse page)                  │
│  4. PlaylistScreen      (Editorial/Community playlist page)  │
│  5. PlayerSheet         (Bottom sheet: mini ↔ full player)   │
│  6. VideoPlayerScreen   (Full-screen video playback)         │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 Type-Safe Navigation Routes

```kotlin
@Serializable
sealed interface Route {
    @Serializable data object Search : Route
    @Serializable data class Artist(
        val name: String, val browseId: String, val thumbnailUrl: String? = null
    ) : Route
    @Serializable data class Album(
        val title: String, val browseId: String,
        val artistName: String? = null, val thumbnailUrl: String? = null, val year: String? = null
    ) : Route
    @Serializable data class Playlist(
        val title: String, val browseId: String,
        val author: String? = null, val thumbnailUrl: String? = null
    ) : Route
}
```

The Player is NOT a navigation route — it lives in a `BottomSheetScaffold` that persists across all screens without backstack destruction.

### 5.3 Navigation Graph

```
SearchScreen
  ├─ tap suggestion (text) → re-triggers search with query
  ├─ tap suggestion (song) → PlayerSheet expands
  ├─ tap suggestion (artist) → ArtistScreen
  ├─ tap suggestion (album) → AlbumScreen
  ├─ search tab: Songs → tap → PlayerSheet
  ├─ search tab: Videos → tap → VideoPlayerScreen
  ├─ search tab: Artists → tap → ArtistScreen
  ├─ search tab: Albums → tap → AlbumScreen
  └─ search tab: Playlists → tap → PlaylistScreen

ArtistScreen
  ├─ tap top song → PlayerSheet
  ├─ tap album card → AlbumScreen
  ├─ tap video card → VideoPlayerScreen
  ├─ tap similar artist → ArtistScreen (new instance)
  └─ tap playlist card → PlaylistScreen

AlbumScreen
  ├─ tap track → PlayerSheet (queue = album tracks)
  └─ tap artist name → ArtistScreen

PlaylistScreen
  ├─ tap track → PlayerSheet (queue = playlist tracks)
  └─ tap artist name → ArtistScreen

PlayerSheet (persistent)
  ├─ tap mini-bar → expand to full player
  ├─ tap artist name → ArtistScreen (collapse sheet first)
  └─ tap album name → AlbumScreen (collapse sheet first)
```

---

## 6. Material 3 Expressive — Theme & Motion System

This is the core of the app's visual identity. Every animation is governed by the M3 Expressive `MotionScheme`, which provides themed spring specs for spatial (bounds/shape) and effects (color/alpha) animations.

### 6.1 Theme Setup

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFBB86FC),
            surface = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1E1E1E)
        )
        else -> lightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = {
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this
                ) {
                    content()
                }
            }
        }
    )
}
```

**Key decisions:**

- `MotionScheme.expressive()` — All M3 components (buttons, FABs, sheets) automatically use spring-based physics. No per-component animation spec needed.
- `SharedTransitionLayout` at root — Enables shared element transitions across all navigation destinations without per-screen wrapping.
- `LocalSharedTransitionScope` — Provides the scope to any composable that needs shared transitions, avoiding deep parameter drilling.

### 6.2 MotionScheme Token Usage

The `MotionScheme` provides four animation spec factories. Use the right one for the right job:

| Token | Use For | Example |
|---|---|---|
| `defaultSpatialSpec<T>()` | Bounds, position, size, shape changes | Thumbnail morph, card expansion |
| `fastSpatialSpec<T>()` | Quick spatial responses to direct interaction | Button press scale, drag release |
| `defaultEffectsSpec<T>()` | Color, alpha, non-spatial properties | Background tint transitions |
| `fastEffectsSpec<T>()` | Quick non-spatial feedback | Ripple alpha, icon color swap |

```kotlin
// Use throughout the app:
val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
```

### 6.3 Shapes & Morph

M3 Expressive introduces `MaterialShapes` — preset polygon shapes that morph smoothly between each other. We use these for the play/pause button and loading indicators.

```kotlin
// Play button: circle at rest, squircle when pressed
val playShape = remember { RoundedPolygon.circle() }
val pauseShape = remember { RoundedPolygon(4, rounding = CornerRounding(0.3f)) }
val morph = remember { Morph(playShape, pauseShape) }

val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val morphProgress by animateFloatAsState(
    targetValue = if (isPressed) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "play-morph"
)

Box(
    modifier = Modifier
        .size(64.dp)
        .clip(MorphPolygonShape(morph, morphProgress))
        .background(MaterialTheme.colorScheme.primary)
        .clickable(interactionSource = interactionSource, indication = null) { onPlayPause() },
    contentAlignment = Alignment.Center
) {
    Icon(
        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = "Play/Pause",
        tint = MaterialTheme.colorScheme.onPrimary
    )
}
```

---

## 7. Animation Specifications

Every animation in the app is cataloged here with its exact spec. No animation uses a hardcoded `tween()` unless noted.

### 7.1 Shared Element Transitions

Thumbnails morph between list items and detail headers. The key must match between source and destination.

**Key convention:**
- Songs/Videos: `"thumb-${videoId}"`
- Albums: `"thumb-album-${browseId}"`
- Artists: `"thumb-artist-${browseId}"`
- Playlists: `"thumb-playlist-${browseId}"`

```kotlin
// In list item
@Composable
fun SongListItem(
    item: SongResult,
    onTap: () -> Unit,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    with(sharedTransitionScope) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AsyncImage(
                model = item.thumb,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .sharedElement(
                        state = rememberSharedContentState(key = "thumb-${item.videoId}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            )
            // ... title, artist text
        }
    }
}
```

**Shared bounds with shape morph** — For transitions where the thumbnail shape changes (e.g., small rounded rect in list → large rounded rect on album page):

```kotlin
@Composable
fun Modifier.sharedBoundsRevealWithShapeMorph(
    sharedContentState: SharedTransitionScope.SharedContentState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    restingShape: RoundedPolygon = RoundedPolygon(4, rounding = CornerRounding(0.05f)),
    targetShape: RoundedPolygon = RoundedPolygon(4, rounding = CornerRounding(0.12f)),
): Modifier = with(sharedTransitionScope) {
    val morph = remember { Morph(restingShape, targetShape) }
    this@sharedBoundsRevealWithShapeMorph
        .sharedBounds(
            sharedContentState = sharedContentState,
            animatedVisibilityScope = animatedVisibilityScope,
            clipInOverlayDuringTransition = OverlayClip(
                MorphPolygonShape(morph, progress = 0.5f)
            )
        )
}
```

### 7.2 animateBounds — Layout Animations

Compose 1.10's `Modifier.animateBounds()` automatically animates position and size changes within a `LookaheadScope`. We use this for the player sheet expansion and search tab switching.

```kotlin
LookaheadScope {
    // Search tab indicator automatically animates between tab positions
    Box(
        modifier = Modifier
            .animateBounds(this@LookaheadScope)
            .width(tabWidth)
            .height(3.dp)
            .offset(x = selectedTabOffset)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
            )
    )
}
```

### 7.3 Predictive Back

The app opts into predictive back for all navigation transitions. On Android 15+, the system automatically provides back-to-home and cross-activity animations.

**Manifest:**

```xml
<application android:enableOnBackInvokedCallback="true">
```

**NavHost with predictive back transitions:**

```kotlin
NavHost(
    navController = navController,
    startDestination = Route.Search,
    popExitTransition = {
        scaleOut(
            targetScale = 0.9f,
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        ) + fadeOut(
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
        )
    },
    popEnterTransition = {
        scaleIn(
            initialScale = 0.95f,
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        ) + fadeIn(
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
        )
    },
    enterTransition = {
        slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
    },
    exitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())
    }
) {
    // composable destinations...
}
```

**Player sheet predictive back** — Collapsing the expanded player via back gesture:

```kotlin
PredictiveBackHandler(enabled = isPlayerExpanded) { progress: Flow<BackEventCompat> ->
    try {
        progress.collect { backEvent ->
            // Smoothly collapse sheet proportional to gesture progress
            sheetOffset = backEvent.progress
        }
        // Gesture completed — collapse fully
        scope.launch { sheetState.bottomSheetState.partialExpand() }
    } catch (e: CancellationException) {
        // Gesture cancelled — snap back to expanded
        scope.launch { sheetState.bottomSheetState.expand() }
    }
}
```

### 7.4 List Item Animations

#### Staggered Appear

When search results or album tracks load, items enter with a staggered spring animation. Uses `AnimatedVisibility` per item with increasing delay.

```kotlin
@Composable
fun StaggeredItem(
    index: Int,
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val delay = (index * 30L).coerceAtMost(300L)
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(delay)
            isVisible = true
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    ) {
        content()
    }
}
```

#### Press Scale Feedback

Every tappable item uses a scale-down spring on press interaction, following the Androidify pattern:

```kotlin
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press-scale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
```

### 7.5 Player Animations

#### Album Art Crossfade on Skip

When skipping to the next track, the album art crossfades with a subtle scale:

```kotlin
AnimatedContent(
    targetState = currentItem,
    transitionSpec = {
        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
            .togetherWith(
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 1.05f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
            )
    },
    contentKey = { it?.videoId },
    label = "album-art"
) { item ->
    AsyncImage(
        model = item?.thumbnailUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}
```

#### Play/Pause Morph Icon

The play and pause icons morph using `AnimatedVectorDrawable`:

```kotlin
val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.avd_play_to_pause)
val isPlaying by playbackState.collectAsStateWithLifecycle()

Icon(
    painter = rememberAnimatedVectorPainter(playToPause, atEnd = isPlaying),
    contentDescription = "Play/Pause",
    modifier = Modifier.size(36.dp)
)
```

#### Seekbar — Smooth Tracking

The seekbar uses a spring for the thumb position when seeking, and linear tracking during playback:

```kotlin
val animatedPosition by animateFloatAsState(
    targetValue = if (isSeeking) seekPosition else playbackProgress,
    animationSpec = if (isSeeking)
        MaterialTheme.motionScheme.fastSpatialSpec()
    else
        snap(), // Linear tracking during playback — no spring bounce
    label = "seekbar"
)
```

### 7.6 Shimmer Placeholder

Zero-dependency shimmer using `rememberInfiniteTransition`:

```kotlin
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing),
            RepeatMode.Reverse
        ), label = "shimmer-alpha"
    )
    Box(modifier.background(shimmerColor.copy(alpha = alpha)))
}
```

### 7.7 Carousel Cards (Artist Page)

Album and single cards in horizontal rows use M3's `HorizontalCenteredHeroCarousel` pattern with spring-based scroll snap:

```kotlin
LazyRow(
    flingBehavior = rememberSnapFlingBehavior(lazyListState),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(horizontal = 16.dp)
) {
    items(items = albums, key = { it.browseId }) { album ->
        ContentCard(
            title = album.title,
            subtitle = album.year,
            thumbnailUrl = album.image,
            onClick = { onAlbumTap(album) },
            modifier = Modifier.animateItem(
                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                fadeOutSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        )
    }
}
```

### 7.8 Mini Player ↔ Full Player Transition

The player sheet transition uses the `BottomSheetScaffold`'s built-in spring animation (governed by `MotionScheme.expressive()`). The content adapts based on the sheet expansion fraction:

```kotlin
val sheetProgress = sheetState.bottomSheetState.requireOffset()
    .let { offset ->
        val collapsed = sheetState.bottomSheetState.requireOffset()
        // Normalize to 0 (collapsed) → 1 (expanded)
        // Implementation depends on scaffold internals
    }

// Album art size interpolates
val artSize by animateDpAsState(
    targetValue = if (isExpanded) 300.dp else 40.dp,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "art-size"
)

// Corner radius morphs
val artRadius by animateDpAsState(
    targetValue = if (isExpanded) 12.dp else 4.dp,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "art-radius"
)
```

---

## 8. ViewModel Architecture

### 8.1 ViewModel Map

| ViewModel | Scope | Holds |
|---|---|---|
| `PlaybackViewModel` | Activity (shared) | Queue, current MediaItem, stream URL, playback position, isPlaying |
| `SearchViewModel` | NavGraph | query, suggestions, search results, active tab |
| `ArtistViewModel` | Route (per browseId) | ArtistDetails |
| `AlbumViewModel` | Route (per browseId) | AlbumPage |
| `PlaylistViewModel` | Route (per browseId) | PlaylistPage |

### 8.2 PlaybackViewModel

```kotlin
class PlaybackViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun playQueue(queue: PlaybackQueue) {
        val item = queue.items[queue.currentIndex]
        _state.update {
            it.copy(currentItem = item, queue = queue, isPlaying = true,
                streamUrl = null, positionMs = 0L)
        }
        fetchStreamUrl(item.videoId)
        prefetchNext(queue)
    }

    fun skipNext() {
        val current = _state.value
        val next = current.queue.currentIndex + 1
        if (next < current.queue.items.size) {
            val nextItem = current.queue.items[next]
            _state.update {
                it.copy(currentItem = nextItem,
                    queue = it.queue.copy(currentIndex = next),
                    streamUrl = AppCache.getStreamUrl(nextItem.videoId),
                    positionMs = 0L)
            }
            if (_state.value.streamUrl == null) fetchStreamUrl(nextItem.videoId)
            prefetchNext(_state.value.queue)
        }
    }

    private fun fetchStreamUrl(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            AppCache.getStreamUrl(videoId)?.let { url ->
                _state.update { it.copy(streamUrl = url) }
                return@launch
            }
            val details = extractSongDetails(executeReelRequest(videoId))
            details.streamUrl?.let { url ->
                AppCache.putStreamUrl(videoId, url)
                _state.update { it.copy(streamUrl = url) }
            }
        }
    }

    private fun prefetchNext(queue: PlaybackQueue) {
        val nextIdx = queue.currentIndex + 1
        if (nextIdx < queue.items.size) {
            val nextId = queue.items[nextIdx].videoId
            if (AppCache.getStreamUrl(nextId) == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val details = extractSongDetails(executeReelRequest(nextId))
                    details.streamUrl?.let { AppCache.putStreamUrl(nextId, it) }
                }
            }
        }
    }
}

data class PlaybackState(
    val currentItem: MediaItem? = null,
    val queue: PlaybackQueue = PlaybackQueue(emptyList(), 0, QueueSource.SINGLE),
    val streamUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L
) {
    val hasMedia: Boolean get() = currentItem != null
}
```

### 8.3 Detail Screen ViewModel Pattern

All detail screens follow the same sealed state pattern:

```kotlin
class ArtistViewModel(private val browseId: String) : ViewModel() {
    sealed interface UiState {
        data class Seed(val name: String, val thumbnailUrl: String?) : UiState
        data class Loaded(val details: ArtistDetails) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Seed("", null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun initialize(seed: Route.Artist) {
        _state.value = UiState.Seed(seed.name, seed.thumbnailUrl)
        viewModelScope.launch(Dispatchers.IO) {
            val cached = AppCache.browse.get(browseId) as? ArtistDetails
            if (cached != null) { _state.value = UiState.Loaded(cached); return@launch }
            val json = executeBrowseRequest(browseId)
            val details = extractArtistDetails(json)
            if (details != null) {
                AppCache.browse.put(browseId, details)
                _state.value = UiState.Loaded(details)
            } else {
                _state.value = UiState.Error("Failed to load artist")
            }
        }
    }
}
```

### 8.4 SearchViewModel

```kotlin
class SearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val suggestions: StateFlow<List<SearchSuggestionResult>> = _query
        .debounce(300)
        .filter { it.length >= 2 }
        .mapLatest { q -> extractSuggestions(executeSuggestionRequest(q)) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeTab = MutableStateFlow(SearchType.SONGS)
    val activeTab: StateFlow<SearchType> = _activeTab.asStateFlow()

    private val _results = MutableStateFlow<Map<SearchType, Any>>(emptyMap())
    val results: StateFlow<Map<SearchType, Any>> = _results.asStateFlow()

    fun onQueryChange(q: String) { _query.value = q }

    fun onSubmit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        fetchTab(q, _activeTab.value)
    }

    fun onTabChange(tab: SearchType) {
        _activeTab.value = tab
        val q = _query.value.trim()
        if (q.isNotEmpty() && !_results.value.containsKey(tab)) fetchTab(q, tab)
    }

    private fun fetchTab(query: String, type: SearchType) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = executeSearchRequest(query, type.params)
            val parsed = when (type) {
                SearchType.SONGS -> extractSongs(json)
                SearchType.VIDEOS -> extractVideos(json)
                SearchType.ARTISTS -> extractArtists(json)
                SearchType.ALBUMS -> extractAlbums(json)
                SearchType.PLAYLISTS, SearchType.FEATURED_PLAYLISTS -> extractPlaylists(json)
            }
            _results.update { it + (type to parsed) }
        }
    }
}
```

---

## 9. App Shell

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicApp() {
    val navController = rememberNavController()
    val playbackViewModel: PlaybackViewModel = viewModel()
    val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()

    val sheetState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = if (playbackState.hasMedia) 64.dp else 0.dp,
        sheetContent = {
            PlayerSheet(
                state = playbackState,
                isExpanded = sheetState.bottomSheetState.currentValue == SheetValue.Expanded,
                onCollapse = { scope.launch { sheetState.bottomSheetState.partialExpand() } },
                onExpand = { scope.launch { sheetState.bottomSheetState.expand() } },
                onArtistTap = { id, name, thumb ->
                    scope.launch { sheetState.bottomSheetState.partialExpand() }
                    navController.navigate(Route.Artist(name, id, thumb))
                },
                onAlbumTap = { id, title, thumb ->
                    scope.launch { sheetState.bottomSheetState.partialExpand() }
                    navController.navigate(Route.Album(title, id, thumbnailUrl = thumb))
                },
                onSkipNext = playbackViewModel::skipNext,
                onSkipPrev = playbackViewModel::skipPrev,
                onPlayPause = playbackViewModel::togglePlayPause,
                onSeek = playbackViewModel::seekTo
            )
        },
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Search,
            modifier = Modifier.padding(innerPadding),
            // Transition specs from Section 7.3
        ) {
            composable<Route.Search> {
                SearchScreen(
                    onSongTap = { _, queue -> playbackViewModel.playQueue(queue) },
                    onArtistTap = { name, id, thumb -> navController.navigate(Route.Artist(name, id, thumb)) },
                    onAlbumTap = { title, id, artist, thumb -> navController.navigate(Route.Album(title, id, artist, thumb)) },
                    onPlaylistTap = { title, id, author, thumb -> navController.navigate(Route.Playlist(title, id, author, thumb)) },
                    animatedVisibilityScope = this@composable
                )
            }
            composable<Route.Artist> { entry ->
                val route = entry.toRoute<Route.Artist>()
                ArtistScreen(seed = route, /* callbacks... */, animatedVisibilityScope = this@composable)
            }
            composable<Route.Album> { entry ->
                val route = entry.toRoute<Route.Album>()
                AlbumScreen(seed = route, /* callbacks... */, animatedVisibilityScope = this@composable)
            }
            composable<Route.Playlist> { entry ->
                val route = entry.toRoute<Route.Playlist>()
                PlaylistScreen(seed = route, /* callbacks... */, animatedVisibilityScope = this@composable)
            }
        }
    }
}
```

---

## 10. Performance Specifications

### 10.1 Performance Budget

| Operation | Target | Mechanism |
|---|---|---|
| Search suggestion render | < 16ms (1 frame) | Pre-parsed flat list, keyed LazyColumn |
| Screen transition (tap → first paint) | 0ms | Seed data renders in navigation callback |
| Full screen load (seed → complete) | < 500ms | API + JSON parse + render |
| Song skip (tap next → UI update) | 0ms | MediaItem from queue, no fetch |
| Song skip (tap next → audio start) | < 400ms | Prefetched stream URL when possible |
| JSON parse (any response) | < 10ms | kotlinx.serialization compile-time codegen |
| Back navigation | 0ms | Backstack preservation, no re-fetch |
| Shared element transition | 16ms frames | `MotionScheme.expressive()` springs |

### 10.2 Compose Performance Rules

```kotlin
// 1. STABLE TYPES — All data classes are already stable
//    → Compose skips recomposition when unchanged

// 2. KEY LAZY ITEMS
LazyColumn {
    items(items = songs, key = { it.videoId }) { song ->
        SongItem(song)
    }
}

// 3. DERIVED STATE
val currentResults by remember { derivedStateOf { results[activeTab] } }

// 4. MINIMIZE ALLOCATIONS
//    Never allocate inside @Composable. Pre-compute in ViewModel.

// 5. COIL CACHE KEYS
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(200)
        .memoryCacheKey(url)
        .diskCacheKey(url)
        .size(with(LocalDensity.current) { size.roundToPx() })
        .build(),
    contentDescription = null
)

// 6. USE animateItem() ON LAZY ITEMS
items(items = tracks, key = { it.videoId }) { track ->
    TrackRow(
        track = track,
        modifier = Modifier.animateItem(
            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
            fadeOutSpec = spring(stiffness = Spring.StiffnessMedium)
        )
    )
}
```

### 10.3 retain API — Surviving Config Changes Without Serialization

Compose 1.10 introduces `retain {}` for persisting non-serializable objects across config changes (rotation, dark mode toggle) without process death serialization:

```kotlin
// Retain the ExoPlayer instance across config changes
val player = retain { ExoPlayer.Builder(context).build() }

// Retain the OkHttp client (connection pool survives rotation)
val httpClient = retain { OkHttpClient.Builder().build() }
```

This replaces the prior pattern of holding ExoPlayer in a ViewModel or Service just to survive rotation.

### 10.4 Prefetching Strategy

```kotlin
fun onSongStarted(queue: PlaybackQueue) {
    val nextIndex = queue.currentIndex + 1
    if (nextIndex < queue.items.size) {
        val nextItem = queue.items[nextIndex]
        if (AppCache.getStreamUrl(nextItem.videoId) == null) {
            coroutineScope.launch(Dispatchers.IO) {
                val details = fetchSongDetails(nextItem.videoId)
                details.streamUrl?.let { AppCache.putStreamUrl(nextItem.videoId, it) }
            }
        }
    }
}
```

---

## 11. Complete Data Flow

```
User Action          Compose Layer           ViewModel           Network

Type query ──────► TextField.onValueChange
                     └──► SearchVM.onQueryChange("cold")
                             └──► _query.debounce(300ms)
                                     └──────────────────► suggestions API
                             suggestions StateFlow updates
                     LazyColumn recomposes (staggered appear animation)

Tap artist ──────► onClick
                     └──► navController.navigate(Route.Artist(...))
                   ArtistScreen composes with seed (Frame 0)
                   SharedTransition: thumbnail morphs list → hero
                     └──► ArtistVM.initialize(seed)
                             ├── Cache HIT → UiState.Loaded (0ms)
                             └── Cache MISS → browse API (~300ms)
                   shimmer → sections (staggered appear)

Tap song ────────► onClick
                     └──► PlaybackVM.playQueue(queue)
                             ├── _state.update (instant UI)
                             ├── fetchStreamUrl → reel API
                             └── prefetchNext (background)
                   PlayerSheet renders: title, art, artist (0ms)
                   Audio starts when stream URL arrives (~200ms)

Skip next ───────► onClick
                     ├── Frame 0: UI updates from queue[n+1] (0ms)
                     ├── Stream URL from prefetch cache (0ms if hit)
                     └── Audio switches (~200ms total)
```

---

## 12. Dependencies (build.gradle.kts)

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // M3 Expressive (alpha for MaterialExpressiveTheme, MaterialShapes)
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")

    // Graphics shapes for morph animations
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    // Navigation — type-safe routes
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Coil for Compose
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-session:1.6.0")

    // Material Icons Extended (opt-in since M3 1.4.0)
    implementation("androidx.compose.material:material-icons-extended")
}
```

---

## Appendix A: Animation Decision Matrix

| Context | Animation Type | Spec | Duration | Notes |
|---|---|---|---|---|
| Thumbnail list → detail hero | Shared element | `defaultSpatialSpec` | ~350ms | Spring, interruptible |
| Button press | Scale 0.96f | `fastSpatialSpec` | ~150ms | Immediate response |
| Play → Pause icon | AVD morph | `fastSpatialSpec` | ~250ms | AnimatedVectorDrawable |
| Search results appear | Staggered fade+slide | `spring(LowBouncy, MediumLow)` | ~400ms | 30ms inter-item delay |
| Album art on skip | Crossfade + scale | `spring(MediumLow)` | ~300ms | Old scales up, new scales in |
| Tab indicator slide | `animateBounds` | `defaultSpatialSpec` | ~300ms | Within LookaheadScope |
| Sheet expand/collapse | BottomSheet spring | `MotionScheme.expressive()` | ~400ms | System-managed |
| Predictive back | Scale + fade | Custom per-screen | Gesture-driven | Follows finger |
| Color transitions | Tint/alpha | `defaultEffectsSpec` | ~200ms | No overshoot |
| Shimmer pulse | Infinite float | `tween(800, Linear)` | Infinite | Only exception to spring-first |
| Error shake | Offset spring | `spring(stiffness=100000)` | ~300ms | 10 iterations |

## Appendix B: What Was Removed from Prior Spec

| Item | Reason |
|---|---|
| `MaterialTheme` (standard) | Replaced by `MaterialExpressiveTheme` for spring-first motion |
| Manual `tween()` on transitions | Replaced by `MotionScheme` tokens (`defaultSpatialSpec`, etc.) |
| `RoundedCornerShape` on play button | Replaced by `RoundedPolygon` + `Morph` for shape morphing |
| Static icon swap (play/pause) | Replaced by `AnimatedVectorDrawable` morph |
| Hardcoded transition durations | Replaced by spring specs (self-timing, interruptible) |
| `SharedTransitionLayout` per-screen | Hoisted to theme root via `LocalSharedTransitionScope` |
| Manual backstack management for player | `BottomSheetScaffold` with `PredictiveBackHandler` |
| Coil 2.x | Upgraded to Coil 3.x (Compose-native, better cache API) |