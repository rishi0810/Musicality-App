package com.example.musicality.ui.library

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicality.R
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.local.SavedArtistEntity
import com.example.musicality.ui.components.SquircleShape12

/**
 * Screen showing all saved artists.
 * Header with icon on black background, followed by list of artists.
 */
@Composable
fun SavedArtistsScreen(
    onBackClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit
) {
    val context = LocalContext.current
    val database = MusicalityDatabase.getDatabase(context)
    val savedArtists by database.savedArtistDao().getAllSavedArtists().collectAsState(initial = emptyList())
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        item {
            SavedArtistsHeader(
                onBackClick = onBackClick
            )
        }
        
        // Artists list
        items(savedArtists, key = { it.artistId }) { artist ->
            SavedArtistItem(
                artist = artist,
                onClick = { onArtistClick(artist.artistId) }
            )
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SavedArtistsHeader(
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp)
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Icon on black background (centered)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.artist_24px),
                contentDescription = "Saved Artists",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
        }
        
        // Title
        Text(
            text = "Saved Artists",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SavedArtistItem(
    artist: SavedArtistEntity,
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
            // Thumbnail (circular for artists)
            AsyncImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Artist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (artist.monthlyListeners.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = artist.monthlyListeners,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
