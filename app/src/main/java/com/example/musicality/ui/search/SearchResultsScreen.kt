package com.example.musicality.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.musicality.R
import com.example.musicality.domain.model.SearchResultType
import com.example.musicality.domain.model.TypedSearchResult
import com.example.musicality.ui.components.SkeletonSearchResultsList
import com.example.musicality.util.UiState

// Pre-defined color constants to avoid reallocation on each recomposition
private val ListItemBackgroundColor = Color.White.copy(alpha = 0.08f)
private val SearchBarBackgroundFocused = Color.White.copy(alpha = 0.1f)
private val SearchBarBackgroundUnfocused = Color.White.copy(alpha = 0.08f)
private val CategoryPillUnselected = Color.White.copy(alpha = 0.12f)
private val SubtleTextColor = Color.White.copy(alpha = 0.6f)
private val DimTextColor = Color.White.copy(alpha = 0.5f)
private val FaintTextColor = Color.White.copy(alpha = 0.4f)

/**
 * Search Results Screen with category pills and paginated results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    viewModel: SearchResultsViewModel = viewModel(),
    onSongClick: (videoId: String, thumbnailUrl: String) -> Unit = { _, _ -> },
    onVideoClick: (videoId: String, thumbnailUrl: String, channelId: String) -> Unit = { _, _, _ -> },
    onAlbumClick: (albumId: String) -> Unit = {},
    onArtistClick: (artistId: String) -> Unit = {},
    onPlaylistClick: (playlistId: String) -> Unit = {},
    onBackClick: () -> Unit = {},
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val resultsState by viewModel.resultsState.collectAsState()
    val accumulatedResults by viewModel.accumulatedResults.collectAsState()
    val continuationToken by viewModel.continuationToken.collectAsState()
    val isPaginating by viewModel.isPaginating.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Set initial query from navigation - only runs once per ViewModel instance
    LaunchedEffect(Unit) {
        viewModel.setInitialQuery(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black)
            .padding(bottom = bottomPadding)
    ) {
        // Search Bar with back button
        SearchResultsBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onClear = { viewModel.updateSearchQuery("") },
            onBackClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )

        // Category Pills
        CategoryPills(
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) },
            modifier = Modifier.fillMaxWidth()
        )

        // Results content
        when (val state = resultsState) {
            is UiState.Idle -> {
                SearchEmptyResultsState()
            }

            is UiState.Loading -> {
                // Shimmer skeleton loading - shows ghost items that mimic actual content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(8) {
                        SkeletonSearchResultsList(
                            modifier = Modifier.fillMaxWidth(),
                            itemCount = 1
                        )
                    }
                }
            }

            is UiState.Success -> {
                SearchResultsList(
                    results = accumulatedResults,
                    selectedCategory = selectedCategory,
                    hasMoreResults = continuationToken != null,
                    isPaginating = isPaginating,
                    onLoadMore = { viewModel.loadMore() },
                    onSongClick = { song ->
                        keyboardController?.hide()
                        onSongClick(song.id, song.thumbnailUrl)
                    },
                    onVideoClick = { video ->
                        keyboardController?.hide()
                        onVideoClick(video.id, video.thumbnailUrl, video.channelId)
                    },
                    onAlbumClick = { album ->
                        keyboardController?.hide()
                        onAlbumClick(album.id)
                    },
                    onArtistClick = { artist ->
                        keyboardController?.hide()
                        onArtistClick(artist.id)
                    },
                    onPlaylistClick = { playlist ->
                        keyboardController?.hide()
                        onPlaylistClick(playlist.id)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is UiState.Error -> {
                SearchResultsErrorState(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Search bar with back button for results screen
 */
@Composable
private fun SearchResultsBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_back_ios_new_24px),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Search field
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp)),
            placeholder = {
                Text(
                    text = "Search...",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SearchBarBackgroundFocused,
                unfocusedContainerColor = SearchBarBackgroundUnfocused,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )
    }
}

/**
 * Horizontal scrollable category pills
 */
@Composable
private fun CategoryPills(
    selectedCategory: SearchResultType,
    onCategorySelected: (SearchResultType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchResultType.entries.forEach { category ->
            CategoryPill(
                category = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

/**
 * Individual category pill
 */
@Composable
private fun CategoryPill(
    category: SearchResultType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) Color.White else CategoryPillUnselected,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * List of search results with pagination
 */
@Composable
private fun SearchResultsList(
    results: List<TypedSearchResult>,
    selectedCategory: SearchResultType,
    hasMoreResults: Boolean,
    isPaginating: Boolean,
    onLoadMore: () -> Unit,
    onSongClick: (TypedSearchResult.Song) -> Unit,
    onVideoClick: (TypedSearchResult.Video) -> Unit,
    onAlbumClick: (TypedSearchResult.Album) -> Unit,
    onArtistClick: (TypedSearchResult.Artist) -> Unit,
    onPlaylistClick: (TypedSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        NoResultsState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = results,
            key = { it.id }
        ) { result ->
            when (result) {
                is TypedSearchResult.Song -> SongResultItem(
                    song = result,
                    onClick = { onSongClick(result) }
                )
                is TypedSearchResult.Video -> VideoResultItem(
                    video = result,
                    onClick = { onVideoClick(result) }
                )
                is TypedSearchResult.Album -> AlbumResultItem(
                    album = result,
                    onClick = { onAlbumClick(result) }
                )
                is TypedSearchResult.Artist -> ArtistResultItem(
                    artist = result,
                    onClick = { onArtistClick(result) }
                )
                is TypedSearchResult.CommunityPlaylist -> PlaylistResultItem(
                    playlist = result,
                    onClick = { onPlaylistClick(result) }
                )
                is TypedSearchResult.FeaturedPlaylist -> FeaturedPlaylistResultItem(
                    playlist = result,
                    onClick = { onPlaylistClick(result) }
                )
            }
        }

        // Show More button or loading indicator
        if (hasMoreResults) {
            item {
                ShowMoreButton(
                    isLoading = isPaginating,
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }

        // Bottom spacing for player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Show More pagination button
 */
@Composable
private fun ShowMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp
            )
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Show More",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Song result item
 */
@Composable
private fun SongResultItem(
    song: TypedSearchResult.Song,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildString {
                        if (song.artistName.isNotBlank()) append(song.artistName)
                        if (song.albumName.isNotBlank()) {
                            if (isNotBlank()) append(" • ")
                            append(song.albumName)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration and views
            Column(horizontalAlignment = Alignment.End) {
                if (song.duration.isNotBlank()) {
                    Text(
                        text = song.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = DimTextColor
                    )
                }
                if (song.views.isNotBlank()) {
                    Text(
                        text = song.views,
                        style = MaterialTheme.typography.labelSmall,
                        color = FaintTextColor
                    )
                }
            }
        }
    }
}

/**
 * Video result item
 */
@Composable
private fun VideoResultItem(
    video: TypedSearchResult.Video,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail (wider for video)
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.name,
                modifier = Modifier
                    .width(100.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row {
                    if (video.views.isNotBlank()) {
                        Text(
                            text = video.views,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    if (video.duration.isNotBlank()) {
                        Text(
                            text = " • ${video.duration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Album result item
 */
@Composable
private fun AlbumResultItem(
    album: TypedSearchResult.Album,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = album.thumbnailUrl,
                contentDescription = album.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildString {
                        append("Album")
                        if (album.artistName.isNotBlank()) append(" • ${album.artistName}")
                        if (album.year.isNotBlank()) append(" • ${album.year}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Artist result item
 */
@Composable
private fun ArtistResultItem(
    artist: TypedSearchResult.Artist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail (circular for artist)
            AsyncImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildString {
                        append("Artist")
                        if (artist.monthlyAudience.isNotBlank()) append(" • ${artist.monthlyAudience}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Community Playlist result item
 */
@Composable
private fun PlaylistResultItem(
    playlist: TypedSearchResult.CommunityPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildString {
                        append("Playlist")
                        if (playlist.artistName.isNotBlank()) append(" • ${playlist.artistName}")
                        if (playlist.views.isNotBlank()) append(" • ${playlist.views}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Featured Playlist result item
 */
@Composable
private fun FeaturedPlaylistResultItem(
    playlist: TypedSearchResult.FeaturedPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ListItemBackgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildString {
                        if (playlist.artistName.isNotBlank()) append(playlist.artistName)
                        if (playlist.songCount.isNotBlank()) {
                            if (isNotBlank()) append(" • ")
                            append(playlist.songCount)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Empty state when search has no results yet
 */
@Composable
private fun SearchEmptyResultsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = "Enter a search term",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * No results state
 */
@Composable
private fun NoResultsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "😕",
                fontSize = 48.sp
            )
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "Try a different search term or category",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Error state with retry
 */
@Composable
private fun SearchResultsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 48.sp
            )
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text = "Retry",
                    color = Color.White
                )
            }
        }
    }
}
