package com.example.musicality.ui.player

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.ui.components.MarqueeText
import com.example.musicality.util.UiState
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * A squircle shape - a mathematical hybrid between a square and a circle.
 * Uses Bezier curves to create smooth, organic corners like Apple's app icons.
 */
class SquircleShape(private val cornerRadius: Dp = 24.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val width = size.width
        val height = size.height
        
        // Clamp radius to half of the smallest dimension
        val radius = min(radiusPx, min(width, height) / 2)
        
        // Smoothing factor for Bezier control points (0.55 is close to a circle, higher = more squircle)
        val smoothing = 0.6f
        val controlOffset = radius * (1 - smoothing)
        
        return Outline.Generic(
            Path().apply {
                // Start from top-left corner, after the curve
                moveTo(0f, radius)
                
                // Top-left corner (squircle curve using cubic Bezier)
                cubicTo(
                    0f, controlOffset,
                    controlOffset, 0f,
                    radius, 0f
                )
                
                // Top edge
                lineTo(width - radius, 0f)
                
                // Top-right corner
                cubicTo(
                    width - controlOffset, 0f,
                    width, controlOffset,
                    width, radius
                )
                
                // Right edge
                lineTo(width, height - radius)
                
                // Bottom-right corner
                cubicTo(
                    width, height - controlOffset,
                    width - controlOffset, height,
                    width - radius, height
                )
                
                // Bottom edge
                lineTo(radius, height)
                
                // Bottom-left corner
                cubicTo(
                    controlOffset, height,
                    0f, height - controlOffset,
                    0f, height - radius
                )
                
                // Left edge (implicit close)
                close()
            }
        )
    }
}

@Composable
fun CollapsiblePlayer(
    playerState: UiState<SongPlaybackInfo>,
    isPlaying: Boolean,
    isExpanded: Boolean,
    isBuffering: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onToggleExpanded: () -> Unit,
    onDragDown: () -> Unit,
    onOffsetChanged: (Float) -> Unit = {},
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onSeek: (Float) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (playerState) {
        is UiState.Success -> {
            val songInfo = playerState.data
            
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                if (isExpanded) {
                    ExpandedPlayer(
                        songInfo = songInfo,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onTogglePlayPause = onTogglePlayPause,
                        onClose = onClose,
                        onDragDown = onDragDown,
                        onOffsetChanged = onOffsetChanged,
                        currentPosition = currentPosition,
                        duration = duration,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        modifier = modifier
                    )
                } else {
                    CollapsedPlayer(
                        songInfo = songInfo,
                        isPlaying = isPlaying,
                        onTogglePlayPause = onTogglePlayPause,
                        onClick = onToggleExpanded,
                        modifier = modifier
                    )
                }
            }
        }
        is UiState.Loading -> {
            if (isExpanded) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        is UiState.Error -> {
            if (isExpanded) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Error loading song", style = MaterialTheme.typography.titleLarge)
                        Text(text = playerState.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onClose) {
                            Text("Close")
                        }
                    }
                }
            }
        }
        else -> { /* Idle */ }
    }
}

@Composable
fun ExpandedPlayer(
    songInfo: SongPlaybackInfo,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onDragDown: () -> Unit,
    onOffsetChanged: (Float) -> Unit = {},
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onSeek: (Float) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Get screen height for smooth animation - calculate first before using
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Initialize off-screen to prevent flash
    val offsetY = remember { androidx.compose.animation.core.Animatable(screenHeightPx) }
    
    // Slide up animation when opening
    LaunchedEffect(Unit) {
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }
    
    // Report offset changes
    LaunchedEffect(offsetY.value) {
        onOffsetChanged(offsetY.value)
    }
    
    // Common styling
    val squircleShape = SquircleShape(cornerRadius = 24.dp)
    val contentWidth = 0.9f // Increased to 90% width
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {

        // Dark overlay for better readability - also follows drag
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) } // Follow drag
                .background(Color.Black.copy(alpha = 0.4f))
        )
        
        // Content container with gesture handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
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
                                // Animate to bottom before calling onDragDown
                                scope.launch {
                                    offsetY.animateTo(
                                        targetValue = size.height.toFloat(),
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    onDragDown()
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
                // Back button - top left (minimizes player)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(contentWidth)
                        .padding(bottom = 50.dp), // Increased from 16dp
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = {
                            // Animate down smoothly before minimizing
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                onDragDown()
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
                            painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.arrow_back_ios_new_24px),
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 2.dp) // Center the arrow icon
                        )
                    }
                }
                
                // Album art - 85% screen width
                AsyncImage(
                    model = songInfo.thumbnailUrl,
                    contentDescription = songInfo.title,
                    modifier = Modifier
                        .fillMaxWidth(contentWidth)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(size = 15.dp))
                        .border(0.7.dp, color = Color.DarkGray, shape = RoundedCornerShape(size = 15.dp)),
                    contentScale = ContentScale.Crop

                )
                
                Spacer(modifier = Modifier.height(24.dp)) // Fixed spacing instead of weight
                
                
                // Song info card with glassmorphic effect
                val context = LocalContext.current
                
                Surface(
                    modifier = Modifier.fillMaxWidth(contentWidth),
                    shape = squircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Row for song title, artist, and share button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Song info column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Song title with marquee for long names
                                MarqueeText(
                                    text = songInfo.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Artist
                                Text(
                                    text = songInfo.author,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Share button
                            IconButton(
                                onClick = {
                                    val shareUrl = "https://music.youtube.com/watch?v=${songInfo.videoId}"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "${songInfo.title} - ${songInfo.author}")
                                        putExtra(Intent.EXTRA_TEXT, "${songInfo.title} by ${songInfo.author}\n$shareUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share song"))
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.forward_24px),
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Wavy/squiggly progress bar with seek support
                        val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                        WavyProgressBar(
                            progress = progress,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onSeek = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Time info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMs(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatDurationMs(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(15.dp)) // Fixed spacing instead of weight

                // Controls card with glassmorphic effect and squircle shape
                Surface(
                    modifier = Modifier.fillMaxWidth(contentWidth),
                    shape = squircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = Color.White,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.skip_previous_24px),
                                contentDescription = "Previous",
                                modifier = Modifier.size(28.dp),
                                tint = Color.Black
                            )
                        }
                        
                        // Play/Pause button - larger and highlighted
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    color = Color.White, // Yellow accent
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = if (isPlaying) com.example.musicality.R.drawable.pause_circle_24px 
                                         else com.example.musicality.R.drawable.play_circle_24px
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                                tint = Color.Black
                            )
                        }
                        
                        // Next button
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = Color.White, // Yellow accent
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.skip_next_24px),
                                contentDescription = "Next",
                                modifier = Modifier.size(28.dp),
                                tint = Color.Black
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Queue CTA button at bottom - tap to open queue
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.musicality.R.drawable.keyboard_arrow_up_24px),
                        contentDescription = "Open Queue",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Wavy/Squiggly progress bar for visualizing playback with seek support
 * Uses Canvas to draw an animated sine wave that transitions to a straight line
 * When isBuffering is true, shows an oscillating loader instead of progress
 */
@Composable
fun WavyProgressBar(
    progress: Float,
    isPlaying: Boolean = true,
    isBuffering: Boolean = false,
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    waveFrequency: Float = 0.08f // Number of squiggles
) {
    val activeColor = Color.White // Yellow for played portion
    val inactiveColor = Color.White.copy(alpha = 0.4f)
    
    // Animate the wave "phase" so it looks like it's moving only when playing
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedPhase by if (isPlaying && !isBuffering) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2 * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    
    // Oscillating loader animation - moves from 0 to 1 and back
    val loaderPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loader"
    )
    
    // Animate amplitude based on playing state
    val targetAmplitude = if (isPlaying && !isBuffering) 8f else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "amplitude"
    )
    
    val phase = if (isPlaying && !isBuffering) animatedPhase else 0f
    
    // Reuse Path objects to avoid allocation in onDraw
    val wavyPath = remember { Path() }
    val straightPath = remember { Path() }
    
    Canvas(
        modifier = modifier
            .pointerInput(isBuffering) {
                // Disable seek when buffering
                if (!isBuffering) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            val componentWidth = size.width
                            if (componentWidth > 0) {
                                val seekFraction = down.position.x / componentWidth
                                onSeek(seekFraction.coerceIn(0f, 1f))
                                
                                drag(down.id) { change ->
                                    val newFraction = change.position.x / componentWidth
                                    onSeek(newFraction.coerceIn(0f, 1f))
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val centerY = size.height / 2
        val strokeWidth = 4.dp.toPx()
        
        if (isBuffering) {
            // Draw oscillating loader
            // Background line
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Animated filled segment (20% of width)
            val segmentWidth = width * 0.2f
            val segmentStart = loaderPosition * (width - segmentWidth)
            val segmentEnd = segmentStart + segmentWidth
            
            drawLine(
                color = activeColor,
                start = Offset(segmentStart, centerY),
                end = Offset(segmentEnd, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        } else {
            // Normal progress bar with wave
            val progressX = width * progress
            
            // Reset and rebuild paths
            wavyPath.reset()
            wavyPath.moveTo(0f, centerY)
            
            // Optimize drawing loop
            val step = 2
            val endX = progressX.toInt()
            
            if (animatedAmplitude > 0.1f) {
                for (x in 0..endX step step) {
                    val relativeX = x.toFloat()
                    val angle = relativeX * waveFrequency + phase
                    val y = centerY + sin(angle) * animatedAmplitude
                    wavyPath.lineTo(relativeX, y)
                }
            } else {
                wavyPath.lineTo(progressX, centerY)
            }
            
            // Draw the played path
            drawPath(
                path = wavyPath,
                color = activeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Straight path for unplayed
            straightPath.reset()
            straightPath.moveTo(progressX, centerY)
            straightPath.lineTo(width, centerY)
            
            drawPath(
                path = straightPath,
                color = inactiveColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Draw thumb
            val thumbWidth = 6.dp.toPx()
            val thumbHeight = 20.dp.toPx()
            
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(progressX - thumbWidth / 2, centerY - thumbHeight / 2),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2, thumbWidth / 2)
            )
        }
    }
}

/**
 * Format milliseconds to mm:ss
 */
private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

@Composable
fun CollapsedPlayer(
    songInfo: SongPlaybackInfo,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = songInfo.thumbnailUrl,
                contentDescription = songInfo.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = songInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = songInfo.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Pause button
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        id = if (isPlaying) com.example.musicality.R.drawable.pause_circle_24px 
                             else com.example.musicality.R.drawable.play_circle_24px
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }
    }
}
