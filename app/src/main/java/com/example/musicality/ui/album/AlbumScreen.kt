package com.example.musicality.ui.album

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.musicality.R
import com.example.musicality.domain.model.AlbumDetail
import com.example.musicality.domain.model.AlbumSong
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.model.RelatedAlbum
import com.example.musicality.ui.components.MarqueeText
import com.example.musicality.util.UiState

/**
 * Album detail screen with full-width album art, back button overlay,
 * song list similar to queue, and "Explore More" album carousel
 */
@Composable
fun AlbumScreen(
    albumId: String,
    viewModel: AlbumViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onSongClick: (videoId: String, thumbnailUrl: String) -> Unit = { _, _ -> },
    onPlayAlbum: (albumSongs: List<QueueSong>, albumThumbnail: String, shuffle: Boolean) -> Unit = { _, _, _ -> },
    onAlbumClick: (albumId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val albumState by viewModel.albumState.collectAsState()
    
    // Load album when screen opens
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = albumState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            
            is UiState.Success -> {
                AlbumContent(
                    albumDetail = state.data,
                    onBackClick = onBackClick,
                    onSongClick = onSongClick,
                    onPlayAlbum = onPlayAlbum,
                    onAlbumClick = onAlbumClick
                )
            }
            
            is UiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onBackClick = onBackClick
                )
            }
            
            else -> {
                // Idle state - show loading indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AlbumContent(
    albumDetail: AlbumDetail,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, thumbnailUrl: String) -> Unit,
    onPlayAlbum: (albumSongs: List<QueueSong>, albumThumbnail: String, shuffle: Boolean) -> Unit,
    onAlbumClick: (albumId: String) -> Unit
) {
    // Track if album is currently playing
    var isAlbumPlaying by remember { mutableStateOf(false) }
    // Track shuffle state
    var isShuffleEnabled by remember { mutableStateOf(false) }
    
    // Convert AlbumSong to QueueSong for player compatibility
    val albumSongsAsQueue = remember(albumDetail.songs) {
        albumDetail.songs.map { song ->
            QueueSong(
                videoId = song.videoId,
                name = song.title,
                singer = albumDetail.artist,
                thumbnailUrl = albumDetail.albumThumbnail,
                duration = song.duration
            )
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Album Header with art and info
        item {
            AlbumHeader(
                albumDetail = albumDetail,
                onBackClick = onBackClick,
                isPlaying = isAlbumPlaying,
                isShuffleEnabled = isShuffleEnabled,
                onShuffleClick = {
                    isShuffleEnabled = !isShuffleEnabled
                },
                onPlayClick = {
                    // Play album and mark as playing
                    if (albumSongsAsQueue.isNotEmpty()) {
                        isAlbumPlaying = true
                        onPlayAlbum(albumSongsAsQueue, albumDetail.albumThumbnail, isShuffleEnabled)
                    }
                }
            )
        }
        
        // Songs list
        items(
            items = albumDetail.songs,
            key = { it.videoId }
        ) { song ->
            AlbumSongItem(
                song = song,
                albumThumbnail = albumDetail.albumThumbnail,
                onClick = { onSongClick(song.videoId, albumDetail.albumThumbnail) }
            )
        }
        
        // Explore More section
        if (albumDetail.moreAlbums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Explore More",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = albumDetail.moreAlbums,
                        key = { it.albumId }
                    ) { album ->
                        RelatedAlbumItem(
                            album = album,
                            onClick = { onAlbumClick(album.albumId) }
                        )
                    }
                }
            }
        }
        
        // Bottom padding for player
        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

/**
 * Album header with back button above, padded art, and info row with circular buttons
 */
@Composable
private fun AlbumHeader(
    albumDetail: AlbumDetail,
    onBackClick: () -> Unit,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Back button - top left, above album art
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(start = 12.dp, top = 8.dp)
                .size(44.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .border(0.7.dp, color = Color.DarkGray, shape = CircleShape)
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.arrow_back_ios_new_24px),
                contentDescription = "Minimize",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 2.dp) // Center the arrow icon
            )
        }
        
        Spacer(modifier = Modifier.height(25.dp))
        
        // Album cover - with 12dp horizontal padding, rounded
        AsyncImage(
            model = albumDetail.albumThumbnail,
            contentDescription = albumDetail.albumName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(1f) // Square album art
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info row: Artist/Album on left, circular buttons on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side - Artist name and Album name only (buttons match this height)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = albumDetail.artist,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = albumDetail.albumName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Right side - Circular buttons (Shuffle + Play/Pause)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button - white when enabled, neutral when disabled
                IconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = if (isShuffleEnabled) Color.White else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.shuffle_24px),
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Play/Pause button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = Color.White,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Song count and duration - separate row below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (albumDetail.count.isNotBlank()) {
                Text(
                    text = albumDetail.count,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            if (albumDetail.duration.isNotBlank()) {
                Text(
                    text = "• ${albumDetail.duration}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Individual song item in the album (similar to queue item style)
 */
@Composable
private fun AlbumSongItem(
    song: AlbumSong,
    albumThumbnail: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
            // Thumbnail using album art
            AsyncImage(
                model = albumThumbnail,
                contentDescription = song.title,
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
                MarqueeText(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                
                if (song.viewCount.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.viewCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Duration
            if (song.duration.isNotBlank()) {
                Text(
                    text = song.duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Related album item in the "Explore More" carousel
 * Rounded album art with name and artist below (left aligned with marquee)
 */
@Composable
private fun RelatedAlbumItem(
    album: RelatedAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Rounded album art
        AsyncImage(
            model = album.albumImg,
            contentDescription = album.albumName,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Album name with marquee effect
        MarqueeText(
            text = album.albumName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        
        // Artist name with marquee effect
        MarqueeText(
            text = album.albumArtist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
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
                painter = painterResource(id = R.drawable.keyboard_arrow_up_24px),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = -90f }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Failed to load album",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
