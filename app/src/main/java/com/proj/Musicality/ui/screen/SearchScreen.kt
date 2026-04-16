package com.proj.Musicality.ui.screen

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proj.Musicality.api.SearchType
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.AlbumResult
import com.proj.Musicality.data.model.AllResult
import com.proj.Musicality.data.model.ArtistResult
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.PlaylistResult
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.model.SongResult
import com.proj.Musicality.data.model.SuggestionType
import com.proj.Musicality.data.model.VideoResult
import com.proj.Musicality.data.model.toMediaItem
import com.proj.Musicality.data.parser.MoodCategoryParser
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

private data class SearchResultMenuModel(
    val typeLabel: String,
    val title: String,
    val subtitle: String?,
    val thumb: String?,
    val artistName: String? = null,
    val artistId: String? = null,
    val albumName: String? = null,
    val albumId: String? = null,
    val shareUrl: String? = null,
    val metadata: List<String> = emptyList(),
    val mediaItem: MediaItem? = null
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onArtistMenuTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onAlbumMenuTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onMoodTap: (MoodCategoryParser.Mood) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val isSearchMode by viewModel.isSearchMode.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    val motionScheme = MaterialTheme.motionScheme
    val backdropPalette = LocalPlaybackBackdropPalette.current
    val selectedChipContainerColor = backdropPalette?.middle
        ?: MaterialTheme.colorScheme.secondaryContainer
    val selectedChipLabelColor = Color.White
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var selectedResultMenu by remember { mutableStateOf<SearchResultMenuModel?>(null) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    val dismissKeyboard = remember(focusManager, keyboardController, context, view) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    val querySuggestions = remember(suggestions) {
        suggestions.filter { it.type == SuggestionType.SUGGESTION }
    }
    val richSuggestions = remember(suggestions) {
        suggestions.filter { it.type != SuggestionType.SUGGESTION && it.type != SuggestionType.UNKNOWN }
    }
    val isInExploreDefaultState = query.isBlank() && !isSearchMode && !isSearchFieldFocused
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val searchBarOverlayModifier = if (!isInExploreDefaultState) {
        with(sharedTransitionScope) {
            Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
        }
    } else Modifier

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(searchBarOverlayModifier)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !isInExploreDefaultState,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                viewModel.onQueryChange("")
                                dismissKeyboard()
                                isSearchFieldFocused = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            TextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchFieldFocused = it.isFocused },
                placeholder = {
                    Text(
                        "Search songs, artists, albums...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        HapticIconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    dismissKeyboard()
                    viewModel.onSubmit()
                })
            )
        }

        val showExploreDefault = isInExploreDefaultState
        if (showExploreDefault) {
            ExploreScreen(
                modifier = Modifier.weight(1f),
                animatedVisibilityScope = animatedVisibilityScope,
                collapsedMiniPlayerHeight = collapsedMiniPlayerHeight,
                onArtistTap = { name, id, thumb -> onArtistTap(name, id, thumb, null) },
                onAlbumTap = onAlbumTap,
                onPlaylistTap = onPlaylistTap,
                onMoodTap = onMoodTap
            )
        } else {
            AnimatedContent(
                targetState = isSearchMode,
                transitionSpec = {
                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                        slideInVertically(motionScheme.fastSpatialSpec()) { it / 8 })
                        .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                },
                label = "search-mode"
            ) { searchMode ->
                if (searchMode) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val tabs = listOf(
                        SearchType.ALL to "All",
                        SearchType.SONGS to "Songs",
                        SearchType.VIDEOS to "Videos",
                        SearchType.ARTISTS to "Artists",
                        SearchType.ALBUMS to "Albums",
                        SearchType.PLAYLISTS to "Playlists"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        items(tabs.size) { index ->
                            val (type, label) = tabs[index]
                            FilterChip(
                                selected = activeTab == type,
                                onClick = { viewModel.onTabChange(type) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = selectedChipContainerColor,
                                    selectedLabelColor = selectedChipLabelColor
                                )
                            )
                        }
                    }

                    Crossfade(
                        targetState = activeTab,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "tab-crossfade"
                    ) { tab ->
                        val currentResults = results[tab]
                        val songList = remember(currentResults) {
                            (currentResults as? List<*>)
                                ?.filterIsInstance<SongResult>()
                                .orEmpty()
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
                        ) {
                            when (tab) {
                                SearchType.ALL -> {
                                    val allList = (currentResults as? List<*>)
                                        ?.filterIsInstance<AllResult>()
                                        .orEmpty()
                                    // Group results by type, preserving order, with top result first
                                    val topResults = allList.filter { it.isTopResult }
                                    val grouped = allList.filter { !it.isTopResult }
                                        .groupBy { it.type }

                                    if (topResults.isNotEmpty()) {
                                        item(key = "header-top-result") {
                                            Text(
                                                text = "Top result",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                            )
                                        }
                                        items(topResults, key = { "top-${it.id}" }, contentType = { "all" }) { result ->
                                            SongListItem(
                                                title = result.title,
                                                subtitle = result.subtitle,
                                                thumbnailUrl = result.thumb,
                                                onOverflowClick = {
                                                    selectedResultMenu = buildAllResultMenuModel(result)
                                                },
                                                onClick = {
                                                    handleAllResultTap(
                                                        result, onSongTap, onVideoTap, onArtistTap, onAlbumTap, onPlaylistTap
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    grouped.forEach { (typeLabel, items) ->
                                        item(key = "header-$typeLabel") {
                                            Text(
                                                text = typeLabel.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                            )
                                        }
                                        items(items, key = { "${it.type}-${it.id}" }, contentType = { "all" }) { result ->
                                            SongListItem(
                                                title = result.title,
                                                subtitle = result.subtitle,
                                                thumbnailUrl = result.thumb,
                                                onOverflowClick = {
                                                    selectedResultMenu = buildAllResultMenuModel(result)
                                                },
                                                onClick = {
                                                    handleAllResultTap(
                                                        result, onSongTap, onVideoTap, onArtistTap, onAlbumTap, onPlaylistTap
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                SearchType.SONGS -> {
                                    itemsIndexed(songList, key = { _, song -> song.videoId }, contentType = { _, _ -> "song" }) { index, song ->
                                        SongListItem(
                                            title = song.title,
                                            subtitle = "${song.artist}${song.album?.let { " • $it" } ?: ""}",
                                            thumbnailUrl = song.thumb,
                                            trailingText = song.duration,
                                            sharedElementKey = "thumb-${song.videoId}",
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onOverflowClick = {
                                                selectedResultMenu = SearchResultMenuModel(
                                                    typeLabel = "Song",
                                                    title = song.title,
                                                    subtitle = "${song.artist}${song.album?.let { " • $it" } ?: ""}",
                                                    thumb = song.thumb,
                                                    artistName = song.artist,
                                                    artistId = song.artistId,
                                                    albumName = song.album,
                                                    albumId = song.albumId,
                                                    shareUrl = "https://music.youtube.com/watch?v=${song.videoId}",
                                                    mediaItem = song.toMediaItem(),
                                                    metadata = listOfNotNull(
                                                        "Artist: ${song.artist}",
                                                        song.album?.let { "Album: $it" },
                                                        song.duration?.let { "Duration: $it" },
                                                        song.plays?.let { "Plays: $it" }
                                                    )
                                                )
                                            },
                                            onClick = {
                                                val queue = PlaybackQueue(
                                                    items = songList.map { it.toMediaItem() },
                                                    currentIndex = index,
                                                    source = QueueSource.SEARCH
                                                )
                                                onSongTap(song.toMediaItem(), queue)
                                            }
                                        )
                                    }
                                }

                                SearchType.VIDEOS -> {
                                    val videoList = (currentResults as? List<*>)
                                        ?.filterIsInstance<VideoResult>()
                                        .orEmpty()
                                    items(videoList, key = { it.videoId }, contentType = { "video" }) { video ->
                                        SongListItem(
                                            title = video.title,
                                            subtitle = "${video.artist}${video.views?.let { " • $it" } ?: ""}",
                                            thumbnailUrl = video.thumb,
                                            trailingText = video.duration,
                                            sharedElementKey = "thumb-${video.videoId}",
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onOverflowClick = {
                                                selectedResultMenu = SearchResultMenuModel(
                                                    typeLabel = "Video",
                                                    title = video.title,
                                                    subtitle = "${video.artist}${video.views?.let { " • $it" } ?: ""}",
                                                    thumb = video.thumb,
                                                    artistName = video.artist,
                                                    artistId = video.artistId,
                                                    shareUrl = "https://music.youtube.com/watch?v=${video.videoId}",
                                                    mediaItem = video.toMediaItem(),
                                                    metadata = listOfNotNull(
                                                        "Artist: ${video.artist}",
                                                        video.views?.let { "Views: $it" },
                                                        video.duration?.let { "Duration: $it" }
                                                    )
                                                )
                                            },
                                            onClick = { onVideoTap(video.toMediaItem()) }
                                        )
                                    }
                                }

                                SearchType.ARTISTS -> {
                                    val artistList = (currentResults as? List<*>)
                                        ?.filterIsInstance<ArtistResult>()
                                        .orEmpty()
                                    items(artistList, key = { it.artistId }, contentType = { "artist" }) { artist ->
                                        SongListItem(
                                            title = artist.name,
                                            subtitle = artist.subscribers,
                                            thumbnailUrl = artist.thumb,
                                            sharedElementKey = "thumb-artist-${artist.artistId}",
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onOverflowClick = {
                                                selectedResultMenu = SearchResultMenuModel(
                                                    typeLabel = "Artist",
                                                    title = artist.name,
                                                    subtitle = artist.subscribers,
                                                    thumb = artist.thumb,
                                                    artistName = artist.name,
                                                    artistId = artist.artistId,
                                                    shareUrl = "https://music.youtube.com/browse/${artist.artistId}",
                                                    metadata = listOfNotNull(
                                                        artist.subscribers?.let { "Subscribers: $it" },
                                                        "Browse ID: ${artist.artistId}"
                                                    )
                                                )
                                            },
                                            onClick = {
                                                onArtistTap(
                                                    artist.name,
                                                    artist.artistId,
                                                    artist.thumb,
                                                    artist.subscribers
                                                )
                                            }
                                        )
                                    }
                                }

                                SearchType.ALBUMS -> {
                                    val albumList = (currentResults as? List<*>)
                                        ?.filterIsInstance<AlbumResult>()
                                        .orEmpty()
                                    items(albumList, key = { it.albumId }, contentType = { "album" }) { album ->
                                        SongListItem(
                                            title = album.title,
                                            subtitle = "${album.artist}${album.year?.let { " • $it" } ?: ""}",
                                            thumbnailUrl = album.thumb,
                                            sharedElementKey = "thumb-album-${album.albumId}",
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onOverflowClick = {
                                                selectedResultMenu = SearchResultMenuModel(
                                                    typeLabel = "Album",
                                                    title = album.title,
                                                    subtitle = "${album.artist}${album.year?.let { " • $it" } ?: ""}",
                                                    thumb = album.thumb,
                                                    artistName = album.artist,
                                                    albumName = album.title,
                                                    albumId = album.albumId,
                                                    shareUrl = "https://music.youtube.com/browse/${album.albumId}",
                                                    metadata = listOfNotNull(
                                                        "Artist: ${album.artist}",
                                                        album.year?.let { "Year: $it" },
                                                        "Browse ID: ${album.albumId}"
                                                    )
                                                )
                                            },
                                            onClick = { onAlbumTap(album.title, album.albumId, album.artist, album.thumb) }
                                        )
                                    }
                                }

                                SearchType.PLAYLISTS,
                                SearchType.FEATURED_PLAYLISTS -> {
                                    val playlistList = (currentResults as? List<*>)
                                        ?.filterIsInstance<PlaylistResult>()
                                        .orEmpty()
                                    items(playlistList, key = { it.playlistId }, contentType = { "playlist" }) { playlist ->
                                        SongListItem(
                                            title = playlist.title,
                                            subtitle = "${playlist.author}${playlist.countOrViews?.let { " • $it" } ?: ""}",
                                            thumbnailUrl = playlist.thumb,
                                            sharedElementKey = "thumb-playlist-${playlist.playlistId}",
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onOverflowClick = {
                                                selectedResultMenu = SearchResultMenuModel(
                                                    typeLabel = "Playlist",
                                                    title = playlist.title,
                                                    subtitle = "${playlist.author}${playlist.countOrViews?.let { " • $it" } ?: ""}",
                                                    thumb = playlist.thumb,
                                                    artistName = playlist.author,
                                                    shareUrl = "https://music.youtube.com/playlist?list=${playlist.playlistId}",
                                                    metadata = listOfNotNull(
                                                        "Author: ${playlist.author}",
                                                        playlist.countOrViews?.let { "Count/Views: $it" },
                                                        "Playlist ID: ${playlist.playlistId}"
                                                    )
                                                )
                                            },
                                            onClick = {
                                                onPlaylistTap(
                                                    playlist.title,
                                                    playlist.playlistId,
                                                    playlist.author,
                                                    playlist.thumb
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
                ) {
                    items(querySuggestions, key = { "query-${it.title}" }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onQueryChange(suggestion.title)
                                    viewModel.onSubmit()
                                    dismissKeyboard()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = suggestion.title,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    if (querySuggestions.isNotEmpty() && richSuggestions.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    items(richSuggestions, key = { "${it.type}-${it.id ?: it.title}" }) { suggestion ->
                        val suggestionThumb = upscaleThumbnail(
                            suggestion.thumbnails?.maxByOrNull { it.width }?.url,
                            size = 544
                        )
                        when (suggestion.type) {
                            SuggestionType.SONG -> {
                                SongListItem(
                                    title = suggestion.title,
                                    subtitle = suggestion.subtitle,
                                    thumbnailUrl = suggestionThumb,
                                    onOverflowClick = {
                                        val item = MediaItem(
                                            videoId = suggestion.id ?: "",
                                            title = suggestion.title,
                                            artistName = suggestion.artists?.firstOrNull()?.name ?: "",
                                            artistId = suggestion.artists?.firstOrNull()?.id,
                                            albumName = suggestion.album?.name,
                                            albumId = suggestion.album?.id,
                                            thumbnailUrl = suggestionThumb,
                                            durationText = null,
                                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                        )
                                        selectedResultMenu = SearchResultMenuModel(
                                            typeLabel = "Song",
                                            title = suggestion.title,
                                            subtitle = suggestion.subtitle,
                                            thumb = suggestionThumb,
                                            artistName = item.artistName,
                                            artistId = item.artistId,
                                            albumName = item.albumName,
                                            albumId = item.albumId,
                                            shareUrl = suggestion.id?.takeIf { it.isNotBlank() }
                                                ?.let { "https://music.youtube.com/watch?v=$it" },
                                            metadata = listOfNotNull(
                                                suggestion.artists?.firstOrNull()?.name?.let { "Artist: $it" },
                                                suggestion.album?.name?.let { "Album: $it" },
                                                suggestion.subtitle
                                            ),
                                            mediaItem = item
                                        )
                                    },
                                    onClick = {
                                        dismissKeyboard()
                                        val item = MediaItem(
                                            videoId = suggestion.id ?: "",
                                            title = suggestion.title,
                                            artistName = suggestion.artists?.firstOrNull()?.name ?: "",
                                            artistId = suggestion.artists?.firstOrNull()?.id,
                                            albumName = suggestion.album?.name,
                                            albumId = suggestion.album?.id,
                                            thumbnailUrl = suggestionThumb,
                                            durationText = null,
                                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                        )
                                        Log.d(
                                            "TapTrace",
                                            "suggestion-tap: videoId='${item.videoId}' title='${item.title}' " +
                                                "artistName='${item.artistName}' artistsRaw=${suggestion.artists?.map { it.name }} " +
                                                "durationText=${item.durationText}"
                                        )
                                        val queue = PlaybackQueue(listOf(item), 0, QueueSource.SINGLE)
                                        onSongTap(item, queue)
                                    }
                                )
                            }

                            SuggestionType.VIDEO -> {
                                SongListItem(
                                    title = suggestion.title,
                                    subtitle = suggestion.subtitle,
                                    thumbnailUrl = suggestionThumb,
                                    onClick = {
                                        dismissKeyboard()
                                        val item = MediaItem(
                                            videoId = suggestion.id ?: "",
                                            title = suggestion.title,
                                            artistName = suggestion.artists?.firstOrNull()?.name ?: "",
                                            artistId = suggestion.artists?.firstOrNull()?.id,
                                            albumName = null,
                                            albumId = null,
                                            thumbnailUrl = suggestionThumb,
                                            durationText = null,
                                            musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
                                        )
                                        onVideoTap(item)
                                    }
                                )
                            }

                            SuggestionType.ARTIST -> {
                                SongListItem(
                                    title = suggestion.title,
                                    subtitle = suggestion.subtitle,
                                    thumbnailUrl = suggestionThumb,
                                    sharedElementKey = suggestion.id?.let { "thumb-artist-$it" },
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = {
                                        dismissKeyboard()
                                        onArtistTap(
                                            suggestion.title,
                                            suggestion.id ?: "",
                                            suggestionThumb,
                                            suggestion.subtitle
                                        )
                                    }
                                )
                            }

                            SuggestionType.ALBUM -> {
                                SongListItem(
                                    title = suggestion.title,
                                    subtitle = suggestion.subtitle,
                                    thumbnailUrl = suggestionThumb,
                                    sharedElementKey = suggestion.id?.let { "thumb-album-$it" },
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = {
                                        dismissKeyboard()
                                        onAlbumTap(
                                            suggestion.title,
                                            suggestion.id ?: "",
                                            null,
                                            suggestionThumb
                                        )
                                    }
                                )
                            }

                            SuggestionType.PLAYLIST -> {
                                SongListItem(
                                    title = suggestion.title,
                                    subtitle = suggestion.subtitle,
                                    thumbnailUrl = suggestionThumb,
                                    sharedElementKey = suggestion.id?.let { "thumb-playlist-$it" },
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = {
                                        dismissKeyboard()
                                        onPlaylistTap(
                                            suggestion.title,
                                            suggestion.id ?: "",
                                            null,
                                            suggestionThumb
                                        )
                                    }
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    selectedResultMenu?.let { menu ->
        val likedVideoId = menu.mediaItem?.videoId
        val mediaState by remember(likedVideoId) {
            if (likedVideoId != null) repository.observeMediaState(likedVideoId)
            else kotlinx.coroutines.flow.flowOf(MediaLibraryState())
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        SearchResultActionsSheet(
            model = menu,
            isLiked = mediaState.isLiked,
            onDismiss = { selectedResultMenu = null },
            onToggleLike = {
                val media = menu.mediaItem ?: return@SearchResultActionsSheet
                scope.launch { repository.toggleLike(media) }
                selectedResultMenu = null
            },
            onPlayNext = {
                val media = menu.mediaItem ?: return@SearchResultActionsSheet
                onPlayNext(media)
                selectedResultMenu = null
            },
            onAddToQueue = {
                val media = menu.mediaItem ?: return@SearchResultActionsSheet
                onAddToQueue(media)
                selectedResultMenu = null
            },
            onViewArtist = {
                val artistId = menu.artistId ?: return@SearchResultActionsSheet
                onArtistMenuTap(menu.artistName ?: menu.title, artistId, null)
                selectedResultMenu = null
            },
            onViewAlbum = {
                val albumId = menu.albumId ?: return@SearchResultActionsSheet
                onAlbumMenuTap(menu.albumName ?: menu.title, albumId, menu.artistName, null)
                selectedResultMenu = null
            },
            onShare = {
                val shareText = buildString {
                    append(menu.title)
                    if (!menu.subtitle.isNullOrBlank()) {
                        append("\n")
                        append(menu.subtitle)
                    }
                    menu.shareUrl?.let {
                        append("\n")
                        append(it)
                    }
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, menu.title)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            "Share"
                        )
                    )
                }
                selectedResultMenu = null
            },
            onDownload = {
                val media = menu.mediaItem
                if (media == null) {
                    Toast.makeText(context, "Download unavailable for this item", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val result = repository.download(media)
                        Toast.makeText(
                            context,
                            if (result.isSuccess) "Downloaded: ${menu.title}" else "Download failed: ${menu.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                selectedResultMenu = null
            }
        )
    }
}

}


private fun buildAllResultMenuModel(result: AllResult): SearchResultMenuModel {
    val typeLower = result.type.lowercase()
    val isVideoType = typeLower.contains("video")
    val isArtistType = typeLower.contains("artist")
    val isAlbumType = typeLower.contains("album") || typeLower.contains("single") || typeLower.contains("ep")
    val isPlaylistType = typeLower.contains("playlist")
    val isPlayable = isVideoType || (!isArtistType && !isAlbumType && !isPlaylistType)

    val shareUrl = when {
        isArtistType || isAlbumType -> "https://music.youtube.com/browse/${result.id}"
        isPlaylistType -> "https://music.youtube.com/playlist?list=${result.id}"
        else -> "https://music.youtube.com/watch?v=${result.id}"
    }

    val mediaItem = if (isPlayable) {
        MediaItem(
            videoId = result.id,
            title = result.title,
            artistName = "",
            artistId = null,
            albumName = null,
            albumId = null,
            thumbnailUrl = result.thumb,
            durationText = null,
            musicVideoType = if (isVideoType) "MUSIC_VIDEO_TYPE_OMV" else "MUSIC_VIDEO_TYPE_ATV"
        )
    } else null

    return SearchResultMenuModel(
        typeLabel = result.type.replaceFirstChar { it.uppercase() },
        title = result.title,
        subtitle = result.subtitle,
        thumb = result.thumb,
        shareUrl = shareUrl,
        mediaItem = mediaItem,
        metadata = buildList {
            if (result.type.isNotBlank()) add("Type: ${result.type}")
            if (result.subtitle.isNotBlank()) add("Info: ${result.subtitle}")
        }
    )
}

private fun handleAllResultTap(
    result: AllResult,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val typeLower = result.type.lowercase()
    when {
        typeLower.contains("artist") -> onArtistTap(result.title, result.id, result.thumb, result.subtitle)
        typeLower.contains("album") || typeLower.contains("single") || typeLower.contains("ep") -> onAlbumTap(result.title, result.id, null, result.thumb)
        typeLower.contains("playlist") -> onPlaylistTap(result.title, result.id, null, result.thumb)
        typeLower.contains("video") -> {
            val item = MediaItem(
                videoId = result.id, title = result.title,
                artistName = "", artistId = null, albumName = null, albumId = null,
                thumbnailUrl = result.thumb, durationText = null, musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
            )
            onVideoTap(item)
        }
        else -> {
            // Default: treat as song
            val item = MediaItem(
                videoId = result.id, title = result.title,
                artistName = "", artistId = null, albumName = null, albumId = null,
                thumbnailUrl = result.thumb, durationText = null, musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
            )
            val queue = PlaybackQueue(listOf(item), 0, QueueSource.SEARCH)
            onSongTap(item, queue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultActionsSheet(
    model: SearchResultMenuModel,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreInfo by remember(model.shareUrl, model.title) { mutableStateOf(false) }
    val hasPlayableMedia = model.mediaItem?.videoId?.isNotBlank() == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            if (hasPlayableMedia) {
                SearchActionItem(
                    label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    enabled = true,
                    onClick = onToggleLike
                )
                SearchActionItem(
                    label = "Play next",
                    icon = Icons.Rounded.PlayArrow,
                    enabled = true,
                    onClick = onPlayNext
                )
                SearchActionItem(
                    label = "Add to Queue",
                    icon = Icons.Rounded.Add,
                    enabled = true,
                    onClick = onAddToQueue
                )
            }
            SearchActionItem(
                label = "View Artist",
                icon = Icons.Rounded.Person,
                enabled = model.artistId != null,
                onClick = onViewArtist
            )
            SearchActionItem(
                label = "View Album",
                icon = Icons.Rounded.Album,
                enabled = model.albumId != null,
                onClick = onViewAlbum
            )
            SearchActionItem(
                label = "Share",
                icon = Icons.Rounded.Share,
                enabled = true,
                onClick = onShare
            )
            SearchActionItem(
                label = "Download",
                icon = Icons.Rounded.Download,
                enabled = hasPlayableMedia,
                onClick = onDownload
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(text = "More Info") },
                trailingContent = {
                    Icon(
                        imageVector = if (showMoreInfo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showMoreInfo) "Collapse more info" else "Expand more info"
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showMoreInfo = !showMoreInfo }
            )

            if (showMoreInfo && model.metadata.isNotEmpty()) {
                model.metadata.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    ListItem(
        headlineContent = {
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.hapticClickable(enabled = enabled, onClick = onClick)
    )
}
