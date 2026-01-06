package com.example.musicality.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.musicality.R
import com.example.musicality.domain.model.*
import com.example.musicality.ui.components.SkeletonArtistScreen
import com.example.musicality.util.ImageUtils
import com.example.musicality.util.UiState

/**
 * Artist detail screen with:
 * - Full-screen artist thumbnail with gradient overlay
 * - Artist name and monthly views at bottom left
 * - Top songs list
 * - Albums carousel
 * - Playlists carousel
 * - Similar artists carousel
 * - About section
 */
@Composable
fun ArtistScreen(
    artistId: String,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, thumbnail: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArtistViewModel = viewModel(
        factory = ArtistViewModel.Factory(context)
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val isArtistSaved by viewModel.isArtistSaved.collectAsState()
    
    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is UiState.Idle -> {
                // Initial state - show shimmer skeleton for immediate feedback
                SkeletonArtistScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UiState.Loading -> {
                // Shimmer skeleton loading - mimics actual artist layout
                SkeletonArtistScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UiState.Success -> {
                ArtistContent(
                    artistDetail = state.data,
                    isArtistSaved = isArtistSaved,
                    onBackClick = onBackClick,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onToggleSave = { viewModel.toggleSaveArtist() }
                )
            }
            is UiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onBackClick = onBackClick
                )
            }
        }
    }
}

@Composable
private fun ArtistContent(
    artistDetail: ArtistDetail,
    isArtistSaved: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, thumbnail: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    onToggleSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Hero header with full-screen image
        item {
            ArtistHeader(
                artistDetail = artistDetail,
                isSaved = isArtistSaved,
                onBackClick = onBackClick,
                onAddClick = onToggleSave
            )
        }
        
        // Top Songs section
        if (artistDetail.topSongs.isNotEmpty()) {
            item {
                SectionTitle(title = "Top Songs")
            }
            items(
                items = artistDetail.topSongs,
                key = { it.videoId }
            ) { song ->
                TopSongItem(
                    song = song,
                    onClick = { onSongClick(song.videoId, song.thumbnail) }
                )
            }
        }
        
        // Albums section
        if (artistDetail.albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(title = "Albums")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = artistDetail.albums,
                        key = { it.browseId }
                    ) { album ->
                        AlbumItem(
                            album = album,
                            onClick = { onAlbumClick(album.browseId) }
                        )
                    }
                }
            }
        }
        
        // Artist Playlists section
        if (artistDetail.artistPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(title = "Playlists")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = artistDetail.artistPlaylists,
                        key = { it.playlistId }
                    ) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.playlistId) }
                        )
                    }
                }
            }
        }
        
        // Featured On section
        if (artistDetail.featuredPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(title = "Featured On")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = artistDetail.featuredPlaylists,
                        key = { it.playlistId }
                    ) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.playlistId) }
                        )
                    }
                }
            }
        }
        
        // Similar Artists section
        if (artistDetail.similarArtists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(title = "Fans Also Like")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = artistDetail.similarArtists,
                        key = { it.artistId }
                    ) { artist ->
                        SimilarArtistItem(
                            artist = artist,
                            onClick = { onArtistClick(artist.artistId) }
                        )
                    }
                }
            }
        }
        
        // About section
        if (artistDetail.about.description.isNotBlank()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AboutSection(about = artistDetail.about)
            }
        }
        
        // Bottom padding for player
        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

/**
 * Hero header with full-screen artist image and gradient overlay
 */
@Composable
private fun ArtistHeader(
    artistDetail: ArtistDetail,
    isSaved: Boolean = false,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f) // Slightly taller than square for immersive feel
    ) {
        // Artist thumbnail - full screen
        AsyncImage(
            model = artistDetail.artistThumbnail,
            contentDescription = artistDetail.artistName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Gradient overlay - darker at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black
                        )
                    )
                )
        )
        
        // Back button - top left
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(44.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
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
        
        // Artist name, monthly views, and Add button - bottom row
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left side - Artist name and monthly views
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = artistDetail.artistName,
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (artistDetail.monthlyAudience.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistDetail.monthlyAudience,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Right side - Add button - toggles save state
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = Color.White,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isSaved) R.drawable.check_24px else R.drawable.add_24px
                    ),
                    contentDescription = if (isSaved) "Saved to Library" else "Add to Library",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

/**
 * Top song item with thumbnail, song info, and plays count
 */
@Composable
private fun TopSongItem(
    song: ArtistTopSong,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song thumbnail
            AsyncImage(
                model = ImageUtils.upscaleThumbnail(song.thumbnail, 226),
                contentDescription = song.songName,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.songName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (song.isExplicit) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = Color.White.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "E",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = buildString {
                        if (song.albumName.isNotBlank()) {
                            append(song.albumName)
                        }
                        if (song.plays.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(song.plays)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Album item for carousel
 */
@Composable
private fun AlbumItem(
    album: ArtistAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Album thumbnail
        Box {
            AsyncImage(
                model = ImageUtils.resizeThumbnail(album.thumbnail, 360, 360),
                contentDescription = album.albumName,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (album.isExplicit) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Album name
        Text(
            text = album.albumName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Year
        if (album.year.isNotBlank()) {
            Text(
                text = album.year,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Playlist item for carousel
 */
@Composable
private fun PlaylistItem(
    playlist: ArtistPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Playlist thumbnail
        AsyncImage(
            model = ImageUtils.resizeThumbnail(playlist.thumbnailImg, 360, 360),
            contentDescription = playlist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Playlist name
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Views (if available)
        if (playlist.views.isNotBlank()) {
            Text(
                text = playlist.views,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Similar artist item with circular thumbnail
 */
@Composable
private fun SimilarArtistItem(
    artist: SimilarArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artist thumbnail - circular
        AsyncImage(
            model = ImageUtils.resizeThumbnail(artist.thumbnail, 360, 360),
            contentDescription = artist.artistName,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Artist name
        Text(
            text = artist.artistName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        // Monthly audience
        if (artist.monthlyAudience.isNotBlank()) {
            Text(
                text = artist.monthlyAudience,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * About section with description
 */
@Composable
private fun AboutSection(about: ArtistAbout) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (about.views.isNotBlank()) {
                    Text(
                        text = about.views,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Text(
                    text = about.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button at top
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.Start)
                .size(44.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_back_ios_new_24px),
                contentDescription = "Back",
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Failed to load artist",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
