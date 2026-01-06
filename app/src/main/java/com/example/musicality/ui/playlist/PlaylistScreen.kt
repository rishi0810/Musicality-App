package com.example.musicality.ui.playlist

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicality.R
import com.example.musicality.domain.model.PlaylistDetail
import com.example.musicality.domain.model.PlaylistSong
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.ui.components.SkeletonPlaylistScreen
import com.example.musicality.util.UiState

// ============================================================================
// COLOR CONSTANTS & UTILITIES (Same as PlayerScreen)
// ============================================================================

private val DefaultBackgroundDark = Color(0xFF0F1323)

/**
 * Data class holding extracted palette colors for the background gradient
 */
private data class ScreenColors(
    val primaryColor: Color = DefaultBackgroundDark,
    val secondaryColor: Color = DefaultBackgroundDark
)

/**
 * Darkens a color by a factor (0.0 = black, 1.0 = original)
 */
private fun darkenColor(color: Color, factor: Float = 0.6f): Color {
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

/**
 * Calculates the saturation of a color
 */
private fun calculateSaturation(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return if (max == 0f) 0f else (max - min) / max
}

/**
 * Extracts colors from a bitmap using Android's Palette API
 */
private fun extractColors(bitmap: Bitmap?): ScreenColors {
    if (bitmap == null) return ScreenColors()
    
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()
    
    val dominantSwatch = palette.dominantSwatch
    val vibrantSwatch = palette.vibrantSwatch
    val darkVibrantSwatch = palette.darkVibrantSwatch
    val darkMutedSwatch = palette.darkMutedSwatch
    
    val primaryColor = dominantSwatch?.rgb?.let { Color(it) } 
        ?: darkMutedSwatch?.rgb?.let { Color(it) }
        ?: DefaultBackgroundDark
    
    val secondaryColor = darkVibrantSwatch?.rgb?.let { Color(it) }
        ?: darkMutedSwatch?.rgb?.let { Color(it) }
        ?: darkenColor(primaryColor, 0.5f)
    
    val finalPrimary = if (calculateSaturation(primaryColor) < 0.2f) {
        vibrantSwatch?.rgb?.let { Color(it) }
            ?: darkVibrantSwatch?.rgb?.let { Color(it) }
            ?: primaryColor
    } else {
        primaryColor
    }
    
    return ScreenColors(
        primaryColor = finalPrimary,
        secondaryColor = secondaryColor
    )
}

/**
 * Playlist detail screen with similar UI to Album page
 * - 70% centered playlist art
 * - Centered playlist name
 * - Centered metadata (tracks count + duration)
 * - No artist section
 * - No Explore More section
 */
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, playlistSongs: List<QueueSong>, playlistName: String, thumbnail: String) -> Unit,
    onPlayPlaylist: (songs: List<QueueSong>, playlistName: String, thumbnail: String, shuffle: Boolean) -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlaylistViewModel = viewModel(
        factory = PlaylistViewModel.Factory(context)
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val isPlaylistSaved by viewModel.isPlaylistSaved.collectAsState()
    
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        when (val state = uiState) {
            is UiState.Idle -> {
                // Initial state - show shimmer skeleton
                SkeletonPlaylistScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UiState.Loading -> {
                // Shimmer skeleton loading - mimics actual playlist layout
                SkeletonPlaylistScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UiState.Success -> {
                PlaylistContent(
                    playlistDetail = state.data,
                    isPlaylistSaved = isPlaylistSaved,
                    onBackClick = onBackClick,
                    onSongClick = onSongClick,
                    onPlayPlaylist = onPlayPlaylist,
                    onToggleSave = { viewModel.toggleSavePlaylist() }
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
private fun PlaylistContent(
    playlistDetail: PlaylistDetail,
    isPlaylistSaved: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, playlistSongs: List<QueueSong>, playlistName: String, thumbnail: String) -> Unit,
    onPlayPlaylist: (songs: List<QueueSong>, playlistName: String, thumbnail: String, shuffle: Boolean) -> Unit,
    onToggleSave: () -> Unit
) {
    val context = LocalContext.current
    
    var isPlaying by remember { mutableStateOf(false) }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    
    // Extract colors from playlist art
    var screenColors by remember { mutableStateOf(ScreenColors()) }
    
    // Load bitmap for color extraction
    val imageRequest = remember(playlistDetail.thumbnailImg) {
        ImageRequest.Builder(context)
            .data(playlistDetail.thumbnailImg)
            .allowHardware(false) // Required for Palette
            .build()
    }
    
    val painter = rememberAsyncImagePainter(imageRequest)
    val painterState by painter.state.collectAsState()
    
    LaunchedEffect(painterState) {
        when (val state = painterState) {
            is AsyncImagePainter.State.Success -> {
                val bitmap = state.result.image.toBitmap()
                screenColors = extractColors(bitmap)
            }
            else -> { /* Loading, Error, or Empty */ }
        }
    }
    
    // Animate background color changes
    val animatedPrimaryColor by animateColorAsState(
        targetValue = screenColors.primaryColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "primaryColor"
    )
    
    val animatedSecondaryColor by animateColorAsState(
        targetValue = screenColors.secondaryColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "secondaryColor"
    )
    
    // Create gradient colors - same as PlayerScreen
    val topGradientColor = darkenColor(animatedPrimaryColor, 0.6f)
    val middleGradientColor = darkenColor(animatedSecondaryColor, 0.4f)
    val bottomGradientColor = Color(0xFF0A0A12)
    
    // Gradient background
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            topGradientColor,
            middleGradientColor,
            bottomGradientColor
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
    
    // Convert playlist songs to queue songs
    val queueSongs = remember(playlistDetail.songs) {
        playlistDetail.songs.map { song ->
            QueueSong(
                videoId = song.videoId,
                name = song.songName,
                singer = song.artists,
                thumbnailUrl = song.thumbnail,
                duration = song.duration
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Solid black base
    ) {
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradientBackground)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header section
            item {
                PlaylistHeader(
                    playlistDetail = playlistDetail,
                    onBackClick = onBackClick,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    isSaved = isPlaylistSaved,
                    onShuffleClick = { isShuffleEnabled = !isShuffleEnabled },
                    onPlayClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            onPlayPlaylist(queueSongs, playlistDetail.playlistName, playlistDetail.thumbnailImg, isShuffleEnabled)
                        }
                    },
                    onAddClick = onToggleSave
                )
            }
            
            // Songs list - clicking a song uses playlist as queue
            items(
                items = playlistDetail.songs,
                key = { "${it.videoId}_${playlistDetail.songs.indexOf(it)}" }
            ) { song ->
                PlaylistSongItem(
                    song = song,
                    onClick = { 
                        // Pass playlist context so queue becomes the playlist
                        onSongClick(song.videoId, queueSongs, playlistDetail.playlistName, song.thumbnail) 
                    }
                )
            }
            
            // Bottom padding for player
            item {
                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }
}

/**
 * Playlist header with back button, centered art, name, and metadata
 */
@Composable
private fun PlaylistHeader(
    playlistDetail: PlaylistDetail,
    onBackClick: () -> Unit,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isSaved: Boolean = false,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Back button - top left
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(start = 20.dp, top = 8.dp)
                .size(44.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .border(0.7.dp, color = Color.DarkGray, shape = CircleShape)
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
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Playlist cover - 70% width, centered, rounded
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = playlistDetail.thumbnailImg,
                contentDescription = playlistDetail.playlistName,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Playlist name - centered
        Text(
            text = playlistDetail.playlistName,
            style = MaterialTheme.typography.headlineMedium,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Metadata - centered (tracks count + duration)
        val hasTrackCount = playlistDetail.totalTracks > 0
        val hasDuration = playlistDetail.totalTime.isNotBlank()
        
        if (hasTrackCount || hasDuration) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (hasTrackCount) {
                    Text(
                        text = "${playlistDetail.totalTracks} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                // Only show separator if both values exist
                if (hasTrackCount && hasDuration) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (hasDuration) {
                    Text(
                        text = playlistDetail.totalTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Control buttons - centered
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle button
            IconButton(
                onClick = onShuffleClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isShuffleEnabled) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.shuffle_24px),
                    contentDescription = "Shuffle",
                    tint = if (isShuffleEnabled) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Play/Pause button - bigger (52dp)
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
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Add button - toggles save state
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isSaved) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isSaved) R.drawable.check_24px else R.drawable.add_24px
                    ),
                    contentDescription = if (isSaved) "Saved to Library" else "Add to Library",
                    tint = if (isSaved) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Individual song item in the playlist (with thumbnail since songs have different covers)
 */
@Composable
private fun PlaylistSongItem(
    song: PlaylistSong,
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
                model = song.thumbnail,
                contentDescription = song.songName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.songName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (song.artists.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artists,
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

@Composable
private fun ErrorContent(
    message: String,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            text = "Failed to load playlist",
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
