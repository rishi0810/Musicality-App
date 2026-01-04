package com.example.musicality.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.musicality.domain.model.SearchResult
import com.example.musicality.ui.components.SkeletonSongItem
import com.example.musicality.ui.components.SkeletonSuggestionsSection
import com.example.musicality.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    onSongClick: (videoId: String, thumbnailUrl: String) -> Unit = { _, _ -> },
    onAlbumClick: (albumId: String) -> Unit = { },
    onPlaylistClick: (playlistId: String) -> Unit = { },
    onArtistClick: (artistId: String) -> Unit = { },
    onSearchResultsClick: (query: String) -> Unit = { },
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black)
            .padding(bottom = bottomPadding)
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onClear = { viewModel.clearSearch() },
            onSearch = { 
                // Navigate to search results page when user presses Enter/Go
                if (searchQuery.isNotBlank()) {
                    onSearchResultsClick(searchQuery)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )

        // Content
        when (val state = searchState) {
            is UiState.Idle -> {
                SearchEmptyState()
            }

            is UiState.Loading -> {
                // Shimmer skeleton loading - shows ghost items that mimic actual content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ghost Suggestions Section
                    item {
                        SkeletonSuggestionsSection(suggestionCount = 4)
                    }
                    
                    // Ghost Results Section
                    items(6) {
                        SkeletonSongItem(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            is UiState.Success -> {
                SearchResults(
                    searchResponse = state.data,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSuggestionClick = onSearchResultsClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is UiState.Error -> {
                ErrorState(
                    message = state.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit = {}, // Called when user presses Enter/Go
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        placeholder = {
            Text(
                text = "Search songs, artists, albums...",
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
            focusedContainerColor = Color.Black,
            unfocusedContainerColor = Color.Black,
            disabledContainerColor =  MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = {
                if (query.isNotBlank()) {
                    onSearch()
                }
            }
        )
    )
}

@Composable
fun SearchResults(
    searchResponse: com.example.musicality.domain.model.SearchResponse,
    onSongClick: (videoId: String, thumbnailUrl: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit = { },
    onPlaylistClick: (playlistId: String) -> Unit = { },
    onArtistClick: (artistId: String) -> Unit = { },
    onSuggestionClick: (query: String) -> Unit = { },
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Suggestions Section
        if (searchResponse.suggestions.isNotEmpty()) {
            item {
                SuggestionsSection(
                    suggestions = searchResponse.suggestions,
                    onSuggestionClick = onSuggestionClick
                )
            }
        }

        // Results Section
        if (searchResponse.results.isNotEmpty()) {
            item {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(
                items = searchResponse.results,
                key = { it.id }
            ) { result ->
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                SearchResultItem(
                    result = result,
                    onClick = {
                        keyboardController?.hide()
                        when (result) {
                            is SearchResult.Song -> {
                                android.util.Log.d("SearchScreen", "Song clicked - ID: ${result.id}, Thumbnail: ${result.thumbnailUrl}")
                                onSongClick(result.id, result.thumbnailUrl)
                            }
                            is SearchResult.Album -> {
                                android.util.Log.d("SearchScreen", "Album clicked - ID: ${result.id}")
                                onAlbumClick(result.id)
                            }
                            is SearchResult.Playlist -> {
                                android.util.Log.d("SearchScreen", "Playlist clicked - ID: ${result.id}")
                                onPlaylistClick(result.id)
                            }
                            is SearchResult.Artist -> {
                                android.util.Log.d("SearchScreen", "Artist clicked - ID: ${result.id}")
                                onArtistClick(result.id)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SuggestionsSection(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit = { }
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        suggestions.take(5).forEach { suggestion ->
            SuggestionChip(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun SuggestionChip(
    suggestion: String,
    onClick: () -> Unit = { }
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
            color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.08f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = result.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title with explicit badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (result.isExplicit) {
                        ExplicitBadge()
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle based on type
                when (result) {
                    is SearchResult.Song -> {
                        Text(
                            text = "Song • ${result.singer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is SearchResult.Artist -> {
                        Text(
                            text = "Artist${if (result.monthlyAudience.isNotEmpty()) " • ${result.monthlyAudience}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is SearchResult.Playlist -> {
                        Text(
                            text = "Playlist${if (result.views.isNotEmpty()) " • ${result.views}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is SearchResult.Album -> {
                        Text(
                            text = "Album • ${result.singer}${if (result.year.isNotEmpty()) " • ${result.year}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExplicitBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SearchEmptyState() {
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "Search for songs, artists, and more",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
