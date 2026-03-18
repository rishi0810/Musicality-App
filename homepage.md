# Homepage & Explore — API Parsers Documentation

Covers three Innertube browse endpoints used for the home and explore screens:
the initial home feed, home feed continuation pages, and the explore feed.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Domain Models](#2-domain-models)
3. [API 1 — Home Feed (FEmusic_home)](#3-api-1--home-feed-femusic_home)
4. [API 2 — Home Feed Continuation](#4-api-2--home-feed-continuation)
5. [API 3 — Explore Feed (FEmusic_explore)](#5-api-3--explore-feed-femusic_explore)
6. [HomeItem Type Reference](#6-homeitem-type-reference)
7. [UI Usage Guide](#7-ui-usage-guide)
8. [File Map](#8-file-map)

---

## 1. Architecture Overview

```
RequestExecutor          →  raw JSON string
        │
        ▼
HomeParser / ExploreParser  →  HomeFeed (domain model)
        │
        ▼
ViewModel / UI              ←  HomeFeed.sections : List<HomeSection>
                                          │
                                          └─ HomeSection.items : List<HomeItem>
                                                    │
                                                    ├─ HomeItem.Song
                                                    ├─ HomeItem.Card
                                                    ├─ HomeItem.NavButton
                                                    └─ HomeItem.PodcastEpisode
```

All three endpoints share the same domain model (`HomeFeed`) and the same
`HomeItem` sealed class. The parsers are the only layer that differs.

---

## 2. Domain Models

### `HomeFeed`
```kotlin
data class HomeFeed(
    val sections: List<HomeSection>,
    val continuation: String?          // null when no more pages
)
```

### `HomeSection`
```kotlin
data class HomeSection(
    val title: String,                 // shelf label, e.g. "Quick picks"
    val items: List<HomeItem>,
    val moreEndpoint: HomeMoreEndpoint?
)
```

`title` is an empty string `""` for the explore top-nav grid section (it has
no label in the API response).

### `HomeMoreEndpoint`
Exactly one of the two fields will be non-null.

```kotlin
data class HomeMoreEndpoint(
    val watchEndpoint: HomeWatchEndpoint?,   // "Play all" button
    val browseEndpoint: HomeBrowseEndpoint?  // "More" / "See all" button
)

data class HomeWatchEndpoint(
    val videoId: String,
    val playlistId: String?,
    val params: String?
)

data class HomeBrowseEndpoint(
    val browseId: String,
    val params: String?
)
```

### `HomeItem` (sealed class)
See [Section 6](#6-homeitem-type-reference) for the full field reference.

---

## 3. API 1 — Home Feed (`FEmusic_home`)

### Request

```
POST https://music.youtube.com/youtubei/v1/browse?prettyPrint=false
```

**Headers**
| Key | Value |
|---|---|
| `x-goog-api-format-version` | `1` |
| `x-youtube-client-name` | `67` |
| `x-youtube-client-version` | `1.20260124.01.00` |
| `x-origin` | `https://music.youtube.com` |
| `referer` | `https://music.youtube.com/` |
| `x-goog-visitor-id` | `<browse visitor ID>` |
| `user-agent` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0` |
| `content-type` | `application/json` |
| `accept` | `application/json` |
| `accept-language` | `en-US,en;q=0.9` |
| `cache-control` | `no-cache` |
| `accept-charset` | `UTF-8` |

**Body**
```json
{
  "context": {
    "client": {
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20260124.01.00",
      "gl": "US",
      "hl": "en",
      "visitorData": "<browse visitor ID>"
    },
    "request": { "internalExperimentFlags": [], "useSsl": true },
    "user": { "lockedSafetyMode": false }
  },
  "browseId": "FEmusic_home"
}
```

### Network Call

```kotlin
val json = RequestExecutor.executeBrowseRequest(
    browseId  = "FEmusic_home",
    visitorId = visitorManager.getBrowseVisitorId()
)
```

### Parsing

```kotlin
val feed: HomeFeed = HomeParser.extractFeed(json)
```

### Response Structure (abbreviated)

```
contents
└─ singleColumnBrowseResultsRenderer
   └─ tabs[0].tabRenderer.content
      └─ sectionListRenderer
         ├─ contents[]                 ← List<HomeSectionItem>
         │   └─ musicCarouselShelfRenderer
         │       ├─ header
         │       │   └─ musicCarouselShelfBasicHeaderRenderer
         │       │       ├─ title.runs[0].text          ← section title
         │       │       └─ moreContentButton            ← "Play all" endpoint
         │       └─ contents[]         ← List<HomeCarouselItem>
         │           └─ musicResponsiveListItemRenderer  ← HomeItem.Song
         └─ continuations[0].nextContinuationData.continuation  ← token
```

### Sections returned (typical)

| Section title | Item type | Notes |
|---|---|---|
| `"Quick picks"` | `HomeItem.Song` | 16 songs, ATV music type |
| `"Trending community playlists"` | `HomeItem.Card` | MUSIC_PAGE_TYPE_PLAYLIST |
| *(continuation required for more)* | | |

### Output

```kotlin
feed.sections        // 2–3 sections on first load
feed.continuation    // non-null → more sections available
```

---

## 4. API 2 — Home Feed Continuation

Used to load additional carousel shelves beyond the first page.
The `continuation` token comes from `HomeFeed.continuation` returned by
API 1 or from a previous continuation call.

### Request

Same URL and headers as API 1. The body replaces `browseId` with `continuation`.

**Body**
```json
{
  "context": {
    "client": {
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20260124.01.00",
      "gl": "US",
      "hl": "en",
      "visitorData": "<browse visitor ID>"
    },
    "request": { "internalExperimentFlags": [], "useSsl": true },
    "user": { "lockedSafetyMode": false }
  },
  "continuation": "<token from previous response>"
}
```

### Network Call

```kotlin
val json = RequestExecutor.executeBrowseContinuationRequest(
    continuation = feed.continuation!!,
    visitorId    = visitorManager.getBrowseVisitorId()
)
```

### Parsing

```kotlin
val nextPage: HomeFeed = HomeParser.extractContinuation(json)
```

### Response Structure (abbreviated)

```
continuationContents
└─ sectionListContinuation
   ├─ contents[]                         ← same HomeSectionItem list as API 1
   └─ continuations[0].nextContinuationData.continuation  ← next token or absent
```

Note: the response also contains a top-level `contents` field with tab
metadata only — it carries no page data and is intentionally ignored by
the parser.

### Pagination Loop

```kotlin
// In ViewModel or Repository:
var feed = HomeParser.extractFeed(
    RequestExecutor.executeBrowseRequest("FEmusic_home", visitorId)
)

while (feed.continuation != null) {
    val more = HomeParser.extractContinuation(
        RequestExecutor.executeBrowseContinuationRequest(feed.continuation!!, visitorId)
    )
    feed = feed.copy(
        sections     = feed.sections + more.sections,
        continuation = more.continuation
    )
}
```

Each continuation call typically returns 3 more sections. The chain ends
when `HomeFeed.continuation` is `null`.

---

## 5. API 3 — Explore Feed (`FEmusic_explore`)

### Request

Same URL and headers as API 1.

**Body**
```json
{
  "context": {
    "client": {
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20260124.01.00",
      "gl": "US",
      "hl": "en",
      "visitorData": "<browse visitor ID>"
    },
    "request": { "internalExperimentFlags": [], "useSsl": true },
    "user": { "lockedSafetyMode": false }
  },
  "browseId": "FEmusic_explore"
}
```

### Network Call

```kotlin
val json = RequestExecutor.executeBrowseRequest(
    browseId  = "FEmusic_explore",
    visitorId = visitorManager.getBrowseVisitorId()
)
```

### Parsing

```kotlin
val feed: HomeFeed = ExploreParser.extractFeed(json)
// feed.continuation is always null — explore is a single page
```

### Response Structure (abbreviated)

```
contents
└─ singleColumnBrowseResultsRenderer
   └─ tabs[0].tabRenderer.content
      └─ sectionListRenderer
         └─ contents[]
             ├─ gridRenderer                       ← Section 0: top-nav buttons
             │   └─ items[].musicNavigationButtonRenderer
             └─ musicCarouselShelfRenderer[]        ← Sections 1–5
                 ├─ header
                 │   └─ musicCarouselShelfBasicHeaderRenderer
                 │       ├─ title
                 │       └─ moreContentButton
                 └─ contents[]
                     ├─ musicTwoRowItemRenderer          ← HomeItem.Card
                     ├─ musicNavigationButtonRenderer    ← HomeItem.NavButton
                     └─ musicMultiRowListItemRenderer    ← HomeItem.PodcastEpisode
```

### Sections returned

| # | Section title | Item type | `pageType` / notes |
|---|---|---|---|
| 0 | `""` (grid) | `HomeItem.NavButton` | 4 top-nav links |
| 1 | `"New albums & singles"` | `HomeItem.Card` | `MUSIC_PAGE_TYPE_ALBUM` |
| 2 | `"Moods & genres"` | `HomeItem.NavButton` | 49 genre chips with accent colors |
| 3 | `"Popular episodes"` | `HomeItem.PodcastEpisode` | `MUSIC_VIDEO_TYPE_PODCAST_EPISODE` |
| 4 | `"Trending"` | `HomeItem.Song` | `MUSIC_VIDEO_TYPE_OMV`, view counts |
| 5 | `"New music videos"` | `HomeItem.Card` | mix of watch + browse endpoints |

### Top-nav grid browseIds

| Label | browseId | icon |
|---|---|---|
| New releases | `FEmusic_new_releases` | `MUSIC_NEW_RELEASE` |
| Charts | `FEmusic_charts` | `TRENDING_UP` |
| Moods & genres | `FEmusic_moods_and_genres` | `STICKER_EMOTICON` |
| Podcasts | `FEmusic_non_music_audio` | `BROADCAST` |

---

## 6. `HomeItem` Type Reference

### `HomeItem.Song`

Emitted by: `musicResponsiveListItemRenderer`
Appears in: Quick picks (home), Trending (explore)

```kotlin
data class Song(
    val videoId: String,        // e.g. "oop_w1P9EL8"
    val playlistId: String?,    // auto-radio seed, e.g. "RDAMVMoop_w1P9EL8"
    val title: String,
    val artistName: String,
    val artistId: String?,      // browseId for artist page
    val albumName: String?,
    val albumId: String?,       // browseId for album page (MPREb_...)
    val plays: String?,         // e.g. "3.9M plays" or "8.2M views"
    val thumbnailUrl: String?,  // upscaled to 544px
    val musicVideoType: String? // "MUSIC_VIDEO_TYPE_ATV" or "MUSIC_VIDEO_TYPE_OMV"
)
```

**To play:** use `videoId` + `playlistId`.
**To navigate to artist:** browse with `artistId`.
**To navigate to album:** browse with `albumId`.

---

### `HomeItem.Card`

Emitted by: `musicTwoRowItemRenderer`
Appears in: all playlist/album/video carousel shelves

```kotlin
data class Card(
    val id: String?,            // browseId — non-null for browse targets
    val videoId: String?,       // non-null for music video cards (watchEndpoint)
    val title: String,
    val subtitle: String?,      // e.g. "Single • Artist" or "Artist • 10M views"
    val thumbnailUrl: String?,
    val pageType: String?,      // discriminates browse target (see below)
    val musicVideoType: String? // non-null only when videoId is set
)
```

**`pageType` values and their meaning:**

| `pageType` | Action |
|---|---|
| `MUSIC_PAGE_TYPE_PLAYLIST` | Browse playlist with `id` |
| `MUSIC_PAGE_TYPE_ALBUM` | Browse album with `id` |
| `MUSIC_PAGE_TYPE_ARTIST` | Browse artist with `id` |
| `MUSIC_PAGE_TYPE_USER_CHANNEL` | Browse user channel with `id` |
| `MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE` | Browse podcast episode page |
| `null` (when `videoId != null`) | Play music video with `videoId` |

**Navigation logic:**
```kotlin
when {
    card.videoId != null -> playVideo(card.videoId)
    card.pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> openPlaylist(card.id!!)
    card.pageType == "MUSIC_PAGE_TYPE_ALBUM"    -> openAlbum(card.id!!)
    card.pageType == "MUSIC_PAGE_TYPE_ARTIST"   -> openArtist(card.id!!)
    else -> browsePage(card.id!!)
}
```

---

### `HomeItem.NavButton`

Emitted by: `musicNavigationButtonRenderer`
Appears in: explore top-nav grid (section 0), Moods & genres carousel

```kotlin
data class NavButton(
    val label: String,      // e.g. "Chill", "New releases"
    val browseId: String,   // destination page
    val params: String?,    // required for moods/genres category pages
    val icon: String?,      // e.g. "MUSIC_NEW_RELEASE" — only on grid buttons
    val color: Long?        // ARGB long — only on mood/genre chips
)
```

**Grid buttons** have `icon` set and `color` null.
**Mood/genre chips** have `color` set (ARGB) and `icon` null.

**To navigate:** call `executeBrowseRequest(browseId, visitorId)` and pass
`params` in the body if non-null.

**Rendering the accent color:**
```kotlin
// color is a 32-bit ARGB long from the API
val accentColor = Color(navButton.color?.toInt() ?: 0xFF888888.toInt())
```

---

### `HomeItem.PodcastEpisode`

Emitted by: `musicMultiRowListItemRenderer`
Appears in: Popular episodes (explore)

```kotlin
data class PodcastEpisode(
    val videoId: String,               // play target
    val title: String,
    val showName: String?,             // podcast / show name
    val showId: String?,               // browseId for the show page (MPSPPLxxx)
    val timeAgo: String?,              // e.g. "2d ago"
    val description: String?,          // full episode description text
    val duration: String?,             // e.g. "3 hr 48 min"
    val thumbnailUrl: String?,
    val playbackProgressPercent: Float? // 0–100, or null if not started
)
```

**To play:** use `videoId` with params `"8gEDmAEI"` (podcast episode params).
**To open show page:** browse with `showId`.

---

## 7. UI Usage Guide

### 7.1 ViewModel

```kotlin
class HomeViewModel(
    private val executor: RequestExecutor,
    private val visitorManager: VisitorManager
) : ViewModel() {

    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    fun loadHome() = viewModelScope.launch {
        val json = executor.executeBrowseRequest(
            "FEmusic_home",
            visitorManager.getBrowseVisitorId()
        )
        _feed.value = HomeParser.extractFeed(json)
    }

    fun loadMoreSections() = viewModelScope.launch {
        val token = _feed.value?.continuation ?: return@launch
        _isLoadingMore.value = true
        val json = executor.executeBrowseContinuationRequest(
            token,
            visitorManager.getBrowseVisitorId()
        )
        val next = HomeParser.extractContinuation(json)
        _feed.update { current ->
            current?.copy(
                sections     = current.sections + next.sections,
                continuation = next.continuation
            )
        }
        _isLoadingMore.value = false
    }

    fun loadExplore() = viewModelScope.launch {
        val json = executor.executeBrowseRequest(
            "FEmusic_explore",
            visitorManager.getBrowseVisitorId()
        )
        _feed.value = ExploreParser.extractFeed(json)
    }
}
```

### 7.2 Composable — Full Feed

```kotlin
@Composable
fun HomeFeedScreen(viewModel: HomeViewModel) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadHome() }

    LazyColumn {
        feed?.sections?.forEach { section ->
            item {
                HomeSectionRow(
                    section = section,
                    onMoreClick = { endpoint ->
                        // navigate using endpoint.watchEndpoint or endpoint.browseEndpoint
                    }
                )
            }
        }

        // Trigger next page when last section is visible
        if (feed?.continuation != null) {
            item {
                LaunchedEffect(Unit) { viewModel.loadMoreSections() }
                if (isLoadingMore) CircularProgressIndicator()
            }
        }
    }
}
```

### 7.3 Composable — Section Row

```kotlin
@Composable
fun HomeSectionRow(
    section: HomeSection,
    onMoreClick: (HomeMoreEndpoint) -> Unit
) {
    Column {
        // Header — skip for the explore top-nav grid (title is empty)
        if (section.title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                section.moreEndpoint?.let { endpoint ->
                    TextButton(onClick = { onMoreClick(endpoint) }) {
                        Text(if (endpoint.watchEndpoint != null) "Play all" else "More")
                    }
                }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(section.items) { item ->
                HomeItemView(item)
            }
        }
    }
}
```

### 7.4 Composable — Item Dispatch

```kotlin
@Composable
fun HomeItemView(item: HomeItem) {
    when (item) {
        is HomeItem.Song          -> SongRow(item)
        is HomeItem.Card          -> MediaCard(item)
        is HomeItem.NavButton     -> NavChip(item)
        is HomeItem.PodcastEpisode -> PodcastEpisodeRow(item)
    }
}
```

### 7.5 Composable — `HomeItem.Song`

```kotlin
@Composable
fun SongRow(song: HomeItem.Song, onClick: (HomeItem.Song) -> Unit = {}) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .clickable { onClick(song) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(song.artistName)
                    song.plays?.let { append(" • $it") }
                },
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// To play:
// player.play(mediaItem = song.toMediaItem())
// where toMediaItem() maps Song → MediaItem using videoId + musicVideoType
```

### 7.6 Composable — `HomeItem.Card`

```kotlin
@Composable
fun MediaCard(card: HomeItem.Card, onClick: (HomeItem.Card) -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick(card) }
            .padding(8.dp)
    ) {
        AsyncImage(
            model = card.thumbnailUrl,
            contentDescription = card.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        card.subtitle?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Navigation:
// card.videoId != null  → play video
// card.pageType == "MUSIC_PAGE_TYPE_ALBUM"    → openAlbum(card.id!!)
// card.pageType == "MUSIC_PAGE_TYPE_PLAYLIST" → openPlaylist(card.id!!)
// card.pageType == "MUSIC_PAGE_TYPE_ARTIST"   → openArtist(card.id!!)
```

### 7.7 Composable — `HomeItem.NavButton`

```kotlin
@Composable
fun NavChip(button: HomeItem.NavButton, onClick: (HomeItem.NavButton) -> Unit = {}) {
    val accentColor = button.color
        ?.let { Color(it.toInt()) }
        ?: MaterialTheme.colorScheme.primaryContainer

    Surface(
        modifier = Modifier
            .height(48.dp)
            .clickable { onClick(button) },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left accent stripe (mood/genre chips only)
            if (button.color != null) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
            Text(
                button.label,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// Navigation: executeBrowseRequest(button.browseId, visitorId)
// Include button.params in the body if non-null (required for genre categories)
```

### 7.8 Composable — `HomeItem.PodcastEpisode`

```kotlin
@Composable
fun PodcastEpisodeRow(
    episode: HomeItem.PodcastEpisode,
    onClick: (HomeItem.PodcastEpisode) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .width(320.dp)
            .clickable { onClick(episode) }
            .padding(8.dp)
    ) {
        Box {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
            )
            // Playback progress bar
            episode.playbackProgressPercent?.let { pct ->
                if (pct > 0f) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            episode.showName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1)
            }
            Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Row {
                episode.timeAgo?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                episode.duration?.let {
                    Text(" • $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// To play: use episode.videoId with podcast params "8gEDmAEI"
// To open show: executeBrowseRequest(episode.showId!!, visitorId)
```

---

## 8. File Map

### New files created

| File | Purpose |
|---|---|
| `data/json/HomeJsonModels.kt` | Serializable JSON classes for home + explore responses |
| `data/model/HomeModels.kt` | Domain models (`HomeFeed`, `HomeSection`, `HomeItem`) |
| `data/parser/HomeParser.kt` | Parses home feed — `extractFeed()` + `extractContinuation()` |
| `data/parser/ExploreParser.kt` | Parses explore feed — `extractFeed()` |

### Modified files

| File | Change |
|---|---|
| `data/json/CommonJsonModels.kt` | Added `params: String?` to `WatchEndpoint` and `BrowseEndpoint` |
| `data/json/ArtistJsonModels.kt` | Extended `BasicHeader` with `moreContentButton: MoreContentButton?` |
| `api/RequestExecutor.kt` | Added `executeBrowseContinuationRequest(continuation, visitorId)` |

### JSON model classes (HomeJsonModels.kt)

| Class | Represents |
|---|---|
| `HomeFeedResponse` | Top-level initial response |
| `HomeContinuationResponse` | Top-level continuation response |
| `HomeSectionItem` | Union of `musicCarouselShelfRenderer` / `gridRenderer` |
| `HomeCarouselShelf` | A single carousel shelf with header + items |
| `HomeCarouselItem` | Union of all four item renderer types |
| `MusicNavigationButtonRenderer` | Navigation chip / top-nav button |
| `MusicMultiRowListItemRenderer` | Podcast episode row |
| `PlaybackProgress` | Episode playback progress + duration |
| `GridRenderer` / `GridItem` | Explore top-nav 2×2 grid |
| `HomeContinuation` / `NextContinuationData` | Pagination token wrapper |
