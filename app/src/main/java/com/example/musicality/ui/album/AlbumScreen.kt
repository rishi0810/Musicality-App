package com.example.musicality.ui.album

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import com.example.musicality.domain.model.AlbumDetail
import com.example.musicality.domain.model.AlbumSong
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.model.RelatedAlbum
import com.example.musicality.ui.components.SkeletonAlbumScreen
import com.example.musicality.util.ImageUtils
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
 * Album detail screen with full-width album art, back button overlay,
 * song list similar to queue, and "Explore More" album carousel
 */
@Composable
fun AlbumScreen(
    albumId: String,
    onBackClick: () -> Unit = {},
    onSongClick: (videoId: String, albumSongs: List<QueueSong>, albumName: String, albumThumbnail: String) -> Unit = { _, _, _, _ -> },
    onPlayAlbum: (albumSongs: List<QueueSong>, albumName: String, albumThumbnail: String, shuffle: Boolean) -> Unit = { _, _, _, _ -> },
    onAlbumClick: (albumId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AlbumViewModel = viewModel(
        factory = AlbumViewModel.Factory(context)
    )
    
    val albumState by viewModel.albumState.collectAsState()
    val isAlbumSaved by viewModel.isAlbumSaved.collectAsState()
    
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
                // Shimmer skeleton loading - mimics actual album layout
                SkeletonAlbumScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
            
            is UiState.Success -> {
                AlbumContent(
                    albumDetail = state.data,
                    isAlbumSaved = isAlbumSaved,
                    onBackClick = onBackClick,
                    onSongClick = onSongClick,
                    onPlayAlbum = onPlayAlbum,
                    onAlbumClick = onAlbumClick,
                    onToggleSave = { viewModel.toggleSaveAlbum() }
                )
            }
            
            is UiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onBackClick = onBackClick
                )
            }
            
            else -> {
                // Idle state - show shimmer skeleton
                SkeletonAlbumScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun AlbumContent(
    albumDetail: AlbumDetail,
    isAlbumSaved: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (videoId: String, albumSongs: List<QueueSong>, albumName: String, albumThumbnail: String) -> Unit,
    onPlayAlbum: (albumSongs: List<QueueSong>, albumName: String, albumThumbnail: String, shuffle: Boolean) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onToggleSave: () -> Unit
) {
    val context = LocalContext.current
    
    // Track if album is currently playing
    var isAlbumPlaying by remember { mutableStateOf(false) }
    // Track shuffle state
    var isShuffleEnabled by remember { mutableStateOf(false) }
    
    // Extract colors from album art
    var screenColors by remember { mutableStateOf(ScreenColors()) }
    
    // Load bitmap for color extraction
    val imageRequest = remember(albumDetail.albumThumbnail) {
        ImageRequest.Builder(context)
            .data(albumDetail.albumThumbnail)
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
                    isSaved = isAlbumSaved,
                    onShuffleClick = {
                        isShuffleEnabled = !isShuffleEnabled
                    },
                    onPlayClick = {
                        // Play album and mark as playing
                        if (albumSongsAsQueue.isNotEmpty()) {
                            isAlbumPlaying = true
                            onPlayAlbum(albumSongsAsQueue, albumDetail.albumName, albumDetail.albumThumbnail, isShuffleEnabled)
                        }
                    },
                    onAddClick = onToggleSave
                )
            }
            
            // Songs list - clicking a song uses album as queue
            items(
                items = albumDetail.songs,
                key = { it.videoId }
            ) { song ->
                AlbumSongItem(
                    song = song,
                    onClick = { 
                        // Pass album context so queue becomes the album
                        onSongClick(song.videoId, albumSongsAsQueue, albumDetail.albumName, albumDetail.albumThumbnail) 
                    }
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
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
}

/**
 * Album header with back button above, centered art, centered name/artist/metadata, and centered CTAs
 * - Matches PlaylistScreen layout with centralized elements
 * - Three CTAs: Shuffle (44dp), Play (52dp bigger), Add (44dp)
 */
@Composable
private fun AlbumHeader(
    albumDetail: AlbumDetail,
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
        // Back button - top left, above album art
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
        
        // Album cover - 70% width, centered, rounded
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = albumDetail.albumThumbnail,
                contentDescription = albumDetail.albumName,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Album name - centered, bigger and bolder
        Text(
            text = albumDetail.albumName,
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Artist image + name row - centered
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small circular artist image
            AsyncImage(
                model = albumDetail.artistThumbnail,
                contentDescription = albumDetail.artist,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Artist name
            Text(
                text = albumDetail.artist,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Metadata - centered (track count + duration)
        val hasCount = albumDetail.count.isNotBlank()
        val hasDuration = albumDetail.duration.isNotBlank()
        
        if (hasCount || hasDuration) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (hasCount) {
                    Text(
                        text = albumDetail.count,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (hasCount && hasDuration) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (hasDuration) {
                    Text(
                        text = albumDetail.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Control buttons - centered row: Shuffle (44dp) | Play (52dp) | Add (44dp)
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
 * Individual song item in the album (no thumbnail - album art shown at top)
 */
@Composable
private fun AlbumSongItem(
    song: AlbumSong,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
        // Rounded album art - resized to 360x360 for optimal loading
        AsyncImage(
            model = ImageUtils.resizeThumbnail(album.albumImg, 360, 360),
            contentDescription = album.albumName,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Album name with ellipsis
        Text(
            text = album.albumName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Artist name with ellipsis
        Text(
            text = album.albumArtist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
