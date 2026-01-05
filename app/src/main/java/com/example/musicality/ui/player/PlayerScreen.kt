package com.example.musicality.ui.player

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicality.R
import com.example.musicality.domain.model.PlaybackSource
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.ui.components.SwipeUpTutorialOverlay
import com.example.musicality.util.UiState
import kotlinx.coroutines.launch
import kotlin.math.min

// ============================================================================
// COLOR CONSTANTS
// ============================================================================

private val PrimaryAccentColor = Color(0xFF607AFB) // Primary purple accent
private val DefaultBackgroundDark = Color(0xFF0F1323) // Deep dark blue
private val GlowColor = Color(0xFF7F13EC) // Purple glow
private val TextSecondaryColor = Color.White.copy(alpha = 0.6f)
private val TextMutedColor = Color.White.copy(alpha = 0.5f)
private val ProgressTrackColor = Color.White.copy(alpha = 0.2f)
private val ProgressBufferColor = Color.White.copy(alpha = 0.3f)
private val QueueSheetBackground = Color(0xFF251A30)

// Cached SquircleShape instance
private val DefaultSquircleShape = SquircleShape(cornerRadius = 24.dp)

// ============================================================================
// SQUIRCLE SHAPE (Apple-style rounded corners)
// ============================================================================

class SquircleShape(private val cornerRadius: Dp = 24.dp) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val width = size.width
        val height = size.height
        
        val radius = min(radiusPx, min(width, height) / 2)
        val smoothing = 0.6f
        val controlOffset = radius * (1 - smoothing)
        
        return Outline.Generic(
            Path().apply {
                moveTo(0f, radius)
                cubicTo(0f, controlOffset, controlOffset, 0f, radius, 0f)
                lineTo(width - radius, 0f)
                cubicTo(width - controlOffset, 0f, width, controlOffset, width, radius)
                lineTo(width, height - radius)
                cubicTo(width, height - controlOffset, width - controlOffset, height, width - radius, height)
                lineTo(radius, height)
                cubicTo(controlOffset, height, 0f, height - controlOffset, 0f, height - radius)
                close()
            }
        )
    }
}

// ============================================================================
// COLOR EXTRACTION UTILITIES
// ============================================================================

/**
 * Data class holding extracted palette colors for the player background
 */
data class PlayerColors(
    val primaryColor: Color = DefaultBackgroundDark,   // Main color from image (for gradient top)
    val secondaryColor: Color = DefaultBackgroundDark, // Secondary color (for gradient middle)
    val accentColor: Color = PrimaryAccentColor        // Vibrant accent (for buttons, progress)
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
 * Calculates the saturation of a color (0.0 = gray, 1.0 = fully saturated)
 */
private fun calculateSaturation(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return if (max == 0f) 0f else (max - min) / max
}

/**
 * Extracts colors from a bitmap using Android's Palette API
 * Prioritizes visually prominent colors (dominant and vibrant)
 */
private fun extractColors(bitmap: Bitmap?): PlayerColors {
    if (bitmap == null) return PlayerColors()
    
    // Generate palette with more color samples for accuracy
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()
    
    // Get all available swatches, sorted by population (most common first)
    val dominantSwatch = palette.dominantSwatch
    val vibrantSwatch = palette.vibrantSwatch
    val darkVibrantSwatch = palette.darkVibrantSwatch
    val lightVibrantSwatch = palette.lightVibrantSwatch
    val mutedSwatch = palette.mutedSwatch
    val darkMutedSwatch = palette.darkMutedSwatch
    
    // Primary color: Use DOMINANT color (most common in image)
    // This should pick up the main visual color
    val primaryColor = dominantSwatch?.rgb?.let { Color(it) } 
        ?: darkMutedSwatch?.rgb?.let { Color(it) }
        ?: DefaultBackgroundDark
    
    // Secondary color: Use dark vibrant or dark muted for a complementary darker tone
    // Falls back to darkened dominant if not available
    val secondaryColor = darkVibrantSwatch?.rgb?.let { Color(it) }
        ?: darkMutedSwatch?.rgb?.let { Color(it) }
        ?: darkenColor(primaryColor, 0.5f)
    
    // Accent color: Use VIBRANT for eye-catching UI elements (buttons, progress)
    // Prioritize vibrant > light vibrant > dark vibrant > dominant
    val accentColor = vibrantSwatch?.rgb?.let { Color(it) }
        ?: lightVibrantSwatch?.rgb?.let { Color(it) }
        ?: darkVibrantSwatch?.rgb?.let { Color(it) }
        ?: dominantSwatch?.rgb?.let { Color(it) }
        ?: PrimaryAccentColor
    
    // Check if primary color has enough saturation to be visually interesting
    // If too gray/desaturated, try to use a more vibrant alternative
    val finalPrimary = if (calculateSaturation(primaryColor) < 0.2f) {
        // Primary is too gray, try vibrant colors instead
        vibrantSwatch?.rgb?.let { Color(it) }
            ?: darkVibrantSwatch?.rgb?.let { Color(it) }
            ?: primaryColor
    } else {
        primaryColor
    }
    
    android.util.Log.d("PlayerColors", "Extracted - Primary: $finalPrimary, Secondary: $secondaryColor, Accent: $accentColor")
    android.util.Log.d("PlayerColors", "Swatches - Dominant: ${dominantSwatch?.rgb}, Vibrant: ${vibrantSwatch?.rgb}, DarkVibrant: ${darkVibrantSwatch?.rgb}")
    
    return PlayerColors(
        primaryColor = finalPrimary,
        secondaryColor = secondaryColor,
        accentColor = accentColor
    )
}

// ============================================================================
// MAIN COLLAPSIBLE PLAYER
// ============================================================================

@Composable
fun CollapsiblePlayer(
    playerState: UiState<SongPlaybackInfo>,
    isPlaying: Boolean,
    isExpanded: Boolean,
    isBuffering: Boolean = false,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
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
    onViewArtist: (channelId: String) -> Unit = {},
    onToggleLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    showSwipeUpHint: Boolean = false,
    onSwipeUpHintDismissed: () -> Unit = {},
    onSwipeUpProgress: (Float) -> Unit = {},
    onSwipeUpComplete: () -> Unit = {},
    onSwipeUpCancel: () -> Unit = {},
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
                        isLiked = isLiked,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
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
                        onViewArtist = onViewArtist,
                        onToggleLike = onToggleLike,
                        onDownload = onDownload,
                        showSwipeUpHint = showSwipeUpHint,
                        onSwipeUpHintDismissed = onSwipeUpHintDismissed,
                        onSwipeUpProgress = onSwipeUpProgress,
                        onSwipeUpComplete = onSwipeUpComplete,
                        onSwipeUpCancel = onSwipeUpCancel,
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
                        .background(DefaultBackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryAccentColor)
                }
            } else {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(DefaultBackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryAccentColor
                    )
                }
            }
        }
        is UiState.Error -> {
            if (isExpanded) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(DefaultBackgroundDark)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Error loading song", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(text = playerState.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentColor)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
        else -> { /* Idle */ }
    }
}

// ============================================================================
// EXPANDED PLAYER (NEW DESIGN)
// ============================================================================

@Composable
fun ExpandedPlayer(
    songInfo: SongPlaybackInfo,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
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
    onViewArtist: (channelId: String) -> Unit = {},
    onToggleLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    showSwipeUpHint: Boolean = false,
    onSwipeUpHintDismissed: () -> Unit = {},
    onSwipeUpProgress: (Float) -> Unit = {},
    onSwipeUpComplete: () -> Unit = {},
    onSwipeUpCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Initialize off-screen to prevent flash
    val offsetY = remember { Animatable(screenHeightPx) }
    
    // Extract colors from album art
    var playerColors by remember { mutableStateOf(PlayerColors()) }
    
    // Load bitmap for color extraction
    val imageRequest = remember(songInfo.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(songInfo.thumbnailUrl)
            .allowHardware(false) // Required for Palette
            .build()
    }
    
    val painter = rememberAsyncImagePainter(imageRequest)
    val painterState by painter.state.collectAsState()
    
    LaunchedEffect(painterState) {
        when (val state = painterState) {
            is AsyncImagePainter.State.Success -> {
                val bitmap = state.result.image.toBitmap()
                playerColors = extractColors(bitmap)
            }
            else -> { /* Loading, Error, or Empty */ }
        }
    }
    
    // Animate background color changes
    val animatedPrimaryColor by animateColorAsState(
        targetValue = playerColors.primaryColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "primaryColor"
    )
    
    val animatedSecondaryColor by animateColorAsState(
        targetValue = playerColors.secondaryColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "secondaryColor"
    )
    
    val animatedAccentColor by animateColorAsState(
        targetValue = playerColors.accentColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "accentColor"
    )
    
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
    
    // Calculate background alpha based on drag offset
    val backgroundAlpha by remember {
        derivedStateOf {
            (1f - (offsetY.value / screenHeightPx).coerceIn(0f, 1f))
        }
    }
    
    // Create gradient colors - use PRIMARY (dominant) at top, SECONDARY in middle
    // This creates a gradient from the album's main color to a darker complementary color
    val topGradientColor = darkenColor(animatedPrimaryColor, 0.6f)
    val middleGradientColor = darkenColor(animatedSecondaryColor, 0.4f)
    val bottomGradientColor = Color(0xFF0A0A12)
    
    // Gradient background based on extracted colors - FULLY OPAQUE
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            topGradientColor,
            middleGradientColor,
            bottomGradientColor
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // SOLID BLACK BASE - ensures no transparency
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        
        // Background with gradient (now on top of solid black)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                .background(brush = gradientBackground)
        )
        
        // Glow effect at top
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                .fillMaxWidth()
                .height(300.dp)
                .offset(y = (-50).dp)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccentColor.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(0.5f, 0f),
                        radius = 600f
                    )
                )
        )
        
        // Content container with gesture handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            val pointerId = down.id
                            val initialTouchY = down.position.y
                            var totalDragY = 0f
                            val maxSwipeDistance = size.height * 0.5f
                            val isInSwipeUpZone = initialTouchY > size.height * 0.6f
                            
                            drag(pointerId) { change ->
                                val dragAmount = change.positionChange().y
                                totalDragY += dragAmount
                                
                                if (totalDragY < 0 && isInSwipeUpZone) {
                                    val progress = (-totalDragY / maxSwipeDistance).coerceIn(0f, 1f)
                                    onSwipeUpProgress(progress)
                                }
                                
                                if (dragAmount > 0 && totalDragY >= 0) {
                                    val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                    scope.launch {
                                        offsetY.snapTo(newOffset)
                                    }
                                }
                                
                                if (change.positionChange() != Offset.Zero) change.consume()
                            }
                            
                            val threshold = size.height * 0.3f
                            val swipeUpThreshold = -150f
                            
                            when {
                                totalDragY < swipeUpThreshold && isInSwipeUpZone -> {
                                    onSwipeUpHintDismissed()
                                    onSwipeUpComplete()
                                }
                                totalDragY < 0 && isInSwipeUpZone -> {
                                    onSwipeUpCancel()
                                }
                                offsetY.value > threshold -> {
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
                                }
                                else -> {
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
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                // ==================== HEADER ====================
                var showMenu by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = {
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                                )
                                onDragDown()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.arrow_back_ios_new_24px),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    
                    // Header label - changes based on playback source
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Determine header text based on playback context
                        val (headerLabel, headerSubtitle) = when (songInfo.playbackContext.source) {
                            PlaybackSource.ALBUM, PlaybackSource.PLAYLIST -> {
                                "PLAYING FROM" to songInfo.playbackContext.sourceName.take(25) + 
                                    if (songInfo.playbackContext.sourceName.length > 25) "..." else ""
                            }
                            PlaybackSource.SEARCH, PlaybackSource.QUEUE -> {
                                "NOW PLAYING" to songInfo.title.take(25) + 
                                    if (songInfo.title.length > 25) "..." else ""
                            }
                        }
                        
                        Text(
                            text = headerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = animatedAccentColor,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = headerSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.list_24px),
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        // Dropdown menu
                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                        ) {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 8.dp),
                                modifier = Modifier
                                    .width(200.dp)
                                    .background(
                                        color = Color(0xFF2C2C2E),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                // View Artist option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "View Artist",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (songInfo.channelId.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.artist_24px),
                                            contentDescription = null,
                                            tint = if (songInfo.channelId.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        if (songInfo.channelId.isNotBlank()) {
                                            scope.launch {
                                                offsetY.animateTo(
                                                    targetValue = screenHeightPx,
                                                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                                                )
                                                onDragDown()
                                                onViewArtist(songInfo.channelId)
                                            }
                                        }
                                    },
                                    enabled = songInfo.channelId.isNotBlank(),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                
                                // View Queue option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "View Queue",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.queue_music_24px),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onOpenQueue()
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // ==================== ALBUM ART ====================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow behind album art
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .blur(40.dp)
                            .background(
                                color = animatedAccentColor.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(32.dp)
                            )
                    )
                    
                    // Album art image
                    AsyncImage(
                        model = songInfo.thumbnailUrl,
                        contentDescription = songInfo.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // ==================== SONG INFO ====================
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = songInfo.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = songInfo.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ==================== PROGRESS BAR ====================
                val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                
                SimpleProgressBar(
                    progress = progress,
                    accentColor = animatedAccentColor,
                    isBuffering = isBuffering,
                    onSeek = onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDurationMs(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(25.dp))
                
                // ==================== PLAYBACK CONTROLS ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.skip_previous_24px),
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // Play/Pause button - large and prominent with white background
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = Color.White,
                                spotColor = Color.White
                            )
                            .background(
                                color = Color.White,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                id = if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // Next button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.skip_next_24px),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // ==================== ACTION BUTTONS ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like button
                    IconButton(onClick = onToggleLike) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                id = if (isLiked) R.drawable.favorite_filled_24px else R.drawable.favorite_24px
                            ),
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) Color.White else TextSecondaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
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
                        }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.forward_24px),
                            contentDescription = "Share",
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Download button
                    Box {
                        IconButton(
                            onClick = { if (!isDownloading) onDownload() },
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                // Show loading indicator while downloading
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        id = if (isDownloaded) R.drawable.download_for_filled_24px else R.drawable.download_for_24px
                                    ),
                                    contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                    tint = if (isDownloaded) Color.White else TextSecondaryColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        // Downloaded indicator dot
                        if (isDownloaded && !isDownloading) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                    
                    // Queue button
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.queue_music_24px),
                            contentDescription = "Queue",
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // ==================== QUEUE CTA (KEEP EXISTING UP ARROW) ====================
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.keyboard_arrow_up_24px),
                        contentDescription = "Open Queue",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Swipe-up tutorial overlay for first-time users
        if (showSwipeUpHint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            
            SwipeUpTutorialOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                animationSize = 160, // Bigger animation
                displayDurationMs = 5000L, // 5 seconds
                onAutoDismiss = {
                    // Auto-dismiss after 5 seconds and mark as shown
                    onSwipeUpHintDismissed()
                }
            )
        }
    }
}

// ============================================================================
// SIMPLE PROGRESS BAR (Replaces wavy progress bar)
// ============================================================================

@Composable
fun SimpleProgressBar(
    progress: Float,
    accentColor: Color,
    isBuffering: Boolean = false,
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buffer")
    
    // Loader animation for buffering state
    val loaderPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loader"
    )
    
    // Larger touch area (24dp) for easier seeking, while visual bar is only 3dp
    // IMPORTANT: Seeking is ALWAYS enabled, even during buffering
    // This prevents the infinite loader issue where failed seeks would permanently disable seeking
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp) // Touch area height
            .pointerInput(Unit) {
                // Tap to seek - ALWAYS enabled
                detectTapGestures { offset ->
                    val fraction = offset.x / size.width
                    onSeek(fraction.coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                // Drag to seek - ALWAYS enabled
                detectHorizontalDragGestures { change, _ ->
                    val fraction = change.position.x / size.width
                    onSeek(fraction.coerceIn(0f, 1f))
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val widthDp = maxWidth
        
        // Background track - thin bar centered in the touch area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(ProgressTrackColor)
        )
        
        if (isBuffering) {
            // Buffering indicator - animated segment
            // Shows visual feedback but doesn't block interaction
            val segmentWidthFraction = 0.2f
            val offsetFraction = loaderPosition * (1f - segmentWidthFraction)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(segmentWidthFraction)
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = widthDp * offsetFraction)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = 0.7f)) // Slightly transparent during buffering
            )
        } else {
            // Buffer progress (slightly ahead of current position)
            Box(
                modifier = Modifier
                    .fillMaxWidth((progress * 1.3f).coerceIn(0f, 1f))
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(ProgressBufferColor)
            )
            
            // Current progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format milliseconds to mm:ss
 */
private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

// ============================================================================
// COLLAPSED PLAYER (MINI PLAYER)
// ============================================================================

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
        color = Color.White.copy(alpha = 0.1f),
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
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = songInfo.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryColor,
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
                        id = if (isPlaying) R.drawable.pause_circle_24px else R.drawable.play_circle_24px
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }
    }
}
