package com.example.musicality.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.ui.components.MarqueeText
import com.example.musicality.util.UiState
import kotlinx.coroutines.launch

/**
 * Queue sheet that slides up from the bottom of the expanded player.
 * Shows related songs that can be played.
 * 
 * Behavior:
 * - Swipe up from the arrow at bottom of player to open
 * - Swipe down to close (drag-to-dismiss like the player)
 * - Tap a song to play it
 */
@Composable
fun QueueSheet(
    queueState: UiState<List<QueueSong>>,
    currentSongThumbnail: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSongClick: (QueueSong) -> Unit,
    onOffsetChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    // Get screen height for proper animation - calculate first before using
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Initialize off-screen to prevent flash
    val offsetY = remember { Animatable(screenHeightPx) }
    val scope = rememberCoroutineScope()
    
    // Slide up animation when opening
    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    
    // Report offset changes to parent
    LaunchedEffect(offsetY.value) {
        onOffsetChanged(offsetY.value)
    }
    
    // Glassmorphic styling
    val squircleShape = SquircleShape(cornerRadius = 24.dp)
    
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Content container with gesture handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                .background(Color.Black) // Background moves with the sheet
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val pointerId = awaitFirstDown().id
                            
                            drag(pointerId) { change ->
                                val dragAmount = change.positionChange().y
                                val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                
                                scope.launch {
                                    offsetY.snapTo(newOffset)
                                }
                                
                                if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) change.consume()
                            }
                            
                            val threshold = size.height * 0.3f
                            if (offsetY.value > threshold) {
                                // Animate to bottom before dismissing
                                scope.launch {
                                    offsetY.animateTo(
                                        targetValue = size.height.toFloat(),
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    onDismiss()
                                    offsetY.snapTo(0f) // Reset for next open
                                }
                            } else {
                                scope.launch {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    )
                                }
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button and title
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = {
                            scope.launch {
                                // Animate to full screen height for smooth exit
                                offsetY.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                onDismiss()
                                offsetY.snapTo(0f) // Reset for next open
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .border(0.7.dp, color = Color.DarkGray, shape = CircleShape)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                id = com.example.musicality.R.drawable.keyboard_arrow_up_24px
                            ),
                            contentDescription = "Close Queue",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 180f } // Rotate to point down
                        )
                    }
                    
                    // Title
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.size(44.dp))
                }
                
                // Queue content - no outer background, items have their own glassmorphic background
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .weight(1f)
                ) {
                    when (queueState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        is UiState.Success -> {
                            val songs = queueState.data
                            if (songs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No songs in queue",
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(
                                        items = songs,
                                        key = { it.videoId }
                                    ) { song ->
                                        QueueSongItem(
                                            song = song,
                                            onClick = { onSongClick(song) }
                                        )
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Failed to load queue",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = queueState.message,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        else -> {
                            // Idle state
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Queue not loaded",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Individual song item in the queue list
 * Glassmorphic background with smaller rounded corners
 * Uses MarqueeText for auto-panning long song names and artists
 */
@Composable
fun QueueSongItem(
    song: QueueSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp), // Smaller corner radius
        color = Color.White.copy(alpha = 0.15f) // Glassmorphic background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Song info with marquee for long text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                MarqueeText(
                    text = song.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                
                if (song.singer.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    MarqueeText(
                        text = song.singer,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(fraction = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Duration
            if (song.duration.isNotEmpty()) {
                Text(
                    text = song.duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
