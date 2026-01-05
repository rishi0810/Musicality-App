package com.example.musicality.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicality.R
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.di.DatabaseModule
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.ui.components.IconCard
import com.example.musicality.ui.components.SectionHeader
import com.example.musicality.ui.components.SquircleShape12
import com.example.musicality.ui.components.ThumbnailCard

// Color constants - minimal black/white theme
private val BackgroundColor = Color.Black
private val SurfaceColor = Color(0xFF121212)
private val TextPrimary = Color.White
private val TextMuted = Color.White.copy(alpha = 0.1f)

@Composable
fun LibraryScreen(
    bottomPadding: Dp = 0.dp,
    onSongClick: (videoId: String, allSongs: List<QueueSong>, albumName: String, thumbnail: String) -> Unit = { _, _, _, _ -> },
    onPlayLikedSongs: (songs: List<QueueSong>, shuffle: Boolean) -> Unit = { _, _ -> },
    onPlayDownloadedSongs: (songs: List<QueueSong>, shuffle: Boolean) -> Unit = { _, _ -> },
    onAlbumClick: (albumId: String) -> Unit = {},
    onArtistClick: (artistId: String) -> Unit = {},
    onPlaylistClick: (playlistId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val database = MusicalityDatabase.getDatabase(context)
    
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(
            likedSongsRepository = DatabaseModule.provideLikedSongsRepository(context),
            downloadRepository = DatabaseModule.provideDownloadRepository(context),
            savedAlbumDao = database.savedAlbumDao(),
            savedArtistDao = database.savedArtistDao(),
            savedPlaylistDao = database.savedPlaylistDao()
        )
    )
    
    val likedSongsCount by viewModel.likedSongsCount.collectAsState()
    val downloadedSongsCount by viewModel.downloadedSongsCount.collectAsState()
    val savedAlbums by viewModel.savedAlbums.collectAsState()
    val savedArtists by viewModel.savedArtists.collectAsState()
    val savedPlaylists by viewModel.savedPlaylists.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(bottom = bottomPadding),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Library",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
        
        // ==================== YOU SECTION ====================
        item {
            SectionHeader(title = "You", textColor = TextPrimary)
        }
        
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // Downloads Card
                item {
                    IconCard(
                        title = "Downloads",
                        subtitle = "$downloadedSongsCount Songs",
                        iconRes = R.drawable.download_for_filled_24px,
                        onClick = {
                            if (downloadedSongsCount > 0) {
                                val queue = viewModel.getDownloadedSongsAsQueue()
                                onPlayDownloadedSongs(queue, false)
                            }
                        },
                        surfaceColor = SurfaceColor,
                        textColor = TextPrimary,
                        subtitleColor = TextMuted
                    )
                }
                
                // Liked Songs Card
                item {
                    IconCard(
                        title = "Liked Songs",
                        subtitle = "$likedSongsCount Songs",
                        iconRes = R.drawable.favorite_filled_24px,
                        onClick = {
                            if (likedSongsCount > 0) {
                                val queue = viewModel.getLikedSongsAsQueue()
                                onPlayLikedSongs(queue, false)
                            }
                        },
                        surfaceColor = SurfaceColor,
                        textColor = TextPrimary,
                        subtitleColor = TextMuted
                    )
                }
                
                // Add Playlist Placeholder
                item {
                    AddPlaylistCard()
                }
            }
        }
        
        // ==================== ALBUMS SECTION ====================
        if (savedAlbums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums", textColor = TextPrimary)
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(savedAlbums, key = { it.albumId }) { album ->
                        ThumbnailCard(
                            title = album.name,
                            subtitle = album.songCount,
                            thumbnailUrl = album.thumbnailUrl,
                            onClick = { onAlbumClick(album.albumId) },
                            surfaceColor = SurfaceColor,
                            textColor = TextPrimary,
                            subtitleColor = TextMuted
                        )
                    }
                }
            }
        }
        
        // ==================== ARTISTS SECTION ====================
        if (savedArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists", textColor = TextPrimary)
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(savedArtists, key = { it.artistId }) { artist ->
                        ThumbnailCard(
                            title = artist.name,
                            subtitle = artist.monthlyListeners,
                            thumbnailUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist.artistId) },
                            surfaceColor = SurfaceColor,
                            textColor = TextPrimary,
                            subtitleColor = TextMuted
                        )
                    }
                }
            }
        }
        
        // ==================== PLAYLISTS SECTION ====================
        if (savedPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "Playlists", textColor = TextPrimary)
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(savedPlaylists, key = { it.playlistId }) { playlist ->
                        ThumbnailCard(
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} Tracks",
                            thumbnailUrl = playlist.thumbnailUrl,
                            onClick = { onPlaylistClick(playlist.playlistId) },
                            surfaceColor = SurfaceColor,
                            textColor = TextPrimary,
                            subtitleColor = TextMuted
                        )
                    }
                }
            }
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun AddPlaylistCard() {
    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(SquircleShape12)
                .background(SurfaceColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.download_for_24px),
                contentDescription = "Add Playlist",
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add Playlist",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            maxLines = 1
        )
    }
}
