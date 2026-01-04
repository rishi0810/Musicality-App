package com.example.musicality.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.musicality.R
import com.example.musicality.data.local.LikedSongEntity
import com.example.musicality.di.DatabaseModule
import com.example.musicality.domain.model.QueueSong

// Color constants matching the app theme
private val BackgroundColor = Color(0xFF0F1323)
private val SurfaceColor = Color(0xFF1A1F35)
private val PrimaryAccent = Color(0xFF607AFB)
private val LikedSongsGradientStart = Color(0xFF7B2CBF)
private val LikedSongsGradientEnd = Color(0xFF9D4EDD)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.7f)
private val TextMuted = Color.White.copy(alpha = 0.5f)

@Composable
fun LibraryScreen(
    bottomPadding: Dp = 0.dp,
    onSongClick: (videoId: String, allSongs: List<QueueSong>, albumName: String, thumbnail: String) -> Unit = { _, _, _, _ -> },
    onPlayLikedSongs: (songs: List<QueueSong>, shuffle: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(
            DatabaseModule.provideLikedSongsRepository(context)
        )
    )
    
    val likedSongs by viewModel.likedSongs.collectAsState()
    val songCount by viewModel.likedSongsCount.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(bottom = bottomPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Liked Songs Album Card
                item {
                    LikedSongsAlbumCard(
                        songCount = songCount,
                        firstSongThumbnail = likedSongs.firstOrNull()?.thumbnailUrl ?: "",
                        onClick = {
                            // Play liked songs
                            if (likedSongs.isNotEmpty()) {
                                val queueSongs = viewModel.getLikedSongsAsQueue()
                                onPlayLikedSongs(queueSongs, false)
                            }
                        },
                        onShuffleClick = {
                            if (likedSongs.isNotEmpty()) {
                                val queueSongs = viewModel.getLikedSongsAsQueue()
                                onPlayLikedSongs(queueSongs, true)
                            }
                        }
                    )
                }
                
                // Section Header
                if (likedSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Liked Songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Liked Songs List
                    items(likedSongs, key = { it.videoId }) { song ->
                        LikedSongItem(
                            song = song,
                            onClick = {
                                val queueSongs = viewModel.getLikedSongsAsQueue()
                                onSongClick(
                                    song.videoId,
                                    queueSongs,
                                    "Liked Songs",
                                    song.thumbnailUrl
                                )
                            }
                        )
                    }
                }
                
                // Empty state
                if (likedSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.favorite_24px),
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No liked songs yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Like songs to add them to your library",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedSongsAlbumCard(
    songCount: Int,
    firstSongThumbnail: String,
    onClick: () -> Unit,
    onShuffleClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(LikedSongsGradientStart, LikedSongsGradientEnd)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art Grid or Placeholder
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (firstSongThumbnail.isNotEmpty()) {
                        AsyncImage(
                            model = firstSongThumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.favorite_filled_24px),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                // Album Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Liked Songs",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Shuffle button
                    if (songCount > 0) {
                        Button(
                            onClick = onShuffleClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.shuffle_24px),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shuffle",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedSongItem(
    song: LikedSongEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.author,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Duration
        Text(
            text = song.duration,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Liked indicator
        Icon(
            painter = painterResource(id = R.drawable.favorite_filled_24px),
            contentDescription = "Liked",
            tint = LikedSongsGradientEnd,
            modifier = Modifier.size(24.dp)
        )
    }
}
