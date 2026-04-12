package com.proj.Musicality.ui.player

import android.content.Intent
import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.media3.common.Player
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.proj.Musicality.R
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.ui.components.pressScale
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.formatMs
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val TAG = "PlayerSheet"
private const val CROSSFADE_UI_LEAD_MS = 10_000L
private val QueueSheetContainerColor = Color(0xFF18181B)

// Spring spec matching the nav transitions – no bounce, medium-low stiffness
private val lyricsSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlayerSheet(
    state: PlaybackState,
    positionMsFlow: StateFlow<Long>,
    lyricsStateFlow: StateFlow<LyricsState>,
    isExpanded: Boolean,
    expandProgressState: State<Float>,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    crossfadeEnabled: Boolean = false,
    onToggleCrossfade: () -> Unit = {},
    miniPlayerHeight: Dp = 70.dp
) {
    val item = state.currentItem ?: return
    val queue = state.queue
    // Read the expand progress state here — PlayerSheet subscribes, not MusicApp
    val expandProgress = expandProgressState.value
    val clampedExpandProgress = expandProgress.coerceIn(0f, 1f)
    val miniContentAlpha = (1f - clampedExpandProgress * 4f).coerceIn(0f, 1f)
    val fullContentAlpha = ((clampedExpandProgress - 0.4f) / 0.2f).coerceIn(0f, 1f)
    val showMorphingOverlay = clampedExpandProgress in 0.001f..0.999f
    val surface = MaterialTheme.colorScheme.surface
    val artworkUrl = upscaleThumbnail(item.thumbnailUrl)
    LaunchedEffect(item.videoId, artworkUrl) {
        Log.d(TAG, "PlayerSheet hero artwork url=$artworkUrl for videoId=${item.videoId}")
    }
    val sharedBackdropPalette = LocalPlaybackBackdropPalette.current
    // Reuse the root palette when available so first playback doesn't decode
    // and extract the same artwork colors twice in parallel.
    val mediaPalette = sharedBackdropPalette ?: rememberMediaBackdropPalette(
        imageUrl = artworkUrl,
        fallbackSurface = surface
    )
    val primary = mediaPalette.accent
    val onSurface = mediaPalette.title
    val onSurfaceVariant = mediaPalette.body
    val playbackAccent = mediaPalette.accent
    val onPlaybackAccent = mediaPalette.onAccent
    val sharedPlaybackUiPalette = LocalPlaybackUiPalette.current
    val selectedControlAccent = sharedPlaybackUiPalette?.accent ?: playbackAccent
    val selectedControlOnAccent = sharedPlaybackUiPalette?.onAccent ?: onPlaybackAccent
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val libraryRepository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val mediaLibraryState by libraryRepository
        .observeMediaState(item.videoId)
        .collectAsStateWithLifecycle(initialValue = MediaLibraryState())

    var shellCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var miniArtBounds by remember { mutableStateOf<Rect?>(null) }
    var fullArtBounds by remember { mutableStateOf<Rect?>(null) }
    // Tracks the compact album art position inside the lyrics layout
    var lyricsArtBounds by remember { mutableStateOf<Rect?>(null) }

    val canMorphAlbumArt =
        showMorphingOverlay &&
            miniArtBounds != null &&
            fullArtBounds != null

    // ── Lyrics mode animated progress (0 = normal player, 1 = lyrics view) ──
    val lyricsAnim = remember { Animatable(0f) }
    val lyricsProgress = lyricsAnim.value
    val isLyricsTransitioning = lyricsProgress in 0.001f..0.999f

    // Alpha curves for smooth cross-fade:
    //  - Normal content: fades out in 0→20% on enter; fades in 20%→0 on exit (no overlap)
    //  - Lyrics controls: tight window near 1.0 so they vanish the instant a drag starts
    //    and only materialise once the art has fully morphed into place
    //  - Lyrics list: appears well after the art settles; gone early on exit so the
    //    growing art has a clean canvas
    val normalContentAlpha = (1f - lyricsProgress / 0.2f).coerceIn(0f, 1f)
    val lyricsControlsAlpha = ((lyricsProgress - 0.93f) / 0.07f).coerceIn(0f, 1f)
    val lyricsListAlpha = ((lyricsProgress - 0.6f) / 0.2f).coerceIn(0f, 1f)

    // Lyrics art morph overlay fires when both bounds are known and we're mid-transition
    val canMorphLyricsArt = isLyricsTransitioning && fullArtBounds != null && lyricsArtBounds != null
    // Hide the static full-size art for the entire morph window so it never "lags"
    // behind the sheet drag before the morph starts.
    val hideBaseArtDuringExpandMorph = canMorphAlbumArt

    // The static full-size art is hidden whenever either morph is active
    val expandedArtAlpha = when {
        hideBaseArtDuringExpandMorph -> 0f
        canMorphLyricsArt -> 0f
        lyricsProgress > 0.3f -> 0f   // fully replaced by lyrics compact art
        else -> 1f
    }
    // Compact lyrics art is hidden while the morph overlay is animating it
    val lyricsCompactArtAlpha = if (canMorphLyricsArt) 0f else 1f

    fun exitLyrics() = coroutineScope.launch { lyricsAnim.animateTo(0f, lyricsSpring) }
    fun enterLyrics() = coroutineScope.launch { lyricsAnim.animateTo(1f, lyricsSpring) }

    // Reset lyrics mode when the sheet collapses
    LaunchedEffect(isExpanded) {
        if (!isExpanded) lyricsAnim.animateTo(0f, lyricsSpring)
    }

    val isLooping = state.repeatMode == Player.REPEAT_MODE_ONE
    var showQueueSheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = isExpanded) { progress: Flow<androidx.activity.BackEventCompat> ->
        try {
            progress.collect { }
            if (lyricsProgress > 0.5f) exitLyrics() else onCollapse()
        } catch (_: CancellationException) {
        }
    }

    // Fade the mesh/gradient out faster on drag-to-close, and keep it fully off in the collapsed mini state
    // so the navbar + mini-player pills feel detached (no shared background strip).
    val playerBackgroundAlpha = run {
        val t = ((clampedExpandProgress - 0.06f) / 0.94f).coerceIn(0f, 1f)
        t * t
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { shellCoordinates = it }
    ) {
        // Memoize gradient colors to avoid allocations during animation frames
        val meshGradientColors = remember(mediaPalette.top, mediaPalette.middle, mediaPalette.bottom) {
            listOf(
                mediaPalette.top.copy(alpha = 0.96f),
                mediaPalette.middle.copy(alpha = 0.84f),
                mediaPalette.bottom.copy(alpha = 0.98f)
            )
        }
        val meshGlowColors = remember(mediaPalette.accent, mediaPalette.middle) {
            listOf(
                mediaPalette.accent.copy(alpha = 0.34f),
                mediaPalette.middle.copy(alpha = 0.16f),
                Color.Transparent
            )
        }
        val meshTintColor = remember(mediaPalette.bottom) { mediaPalette.bottom.copy(alpha = 0.12f) }

        if (playerBackgroundAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Opaque surface base — gradient colors have alpha < 1, so this prevents bleed-through
                        drawRect(surface)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = meshGradientColors,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = meshGlowColors,
                                center = Offset(size.width, size.height),
                                radius = size.maxDimension * 0.7f
                            ),
                            center = Offset(size.width, size.height),
                            radius = size.maxDimension * 0.7f
                        )
                        drawRect(meshTintColor)
                    }
                    .graphicsLayer { alpha = playerBackgroundAlpha }
            )
        }

        // ── Invisible bounds tracker for full art (always composed during expand morph) ──
        if (fullContentAlpha <= 0.001f && clampedExpandProgress > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { /* invisible – only tracks layout bounds */ }
                    .zIndex(-1f)
                    .systemBarsPadding()
            ) {
                Spacer(Modifier.height(56.dp))
                Spacer(Modifier.height(15.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 340.dp)
                            .aspectRatio(1f)
                            .onGloballyPositioned { coordinates ->
                                fullArtBounds = boundsInContainer(shellCoordinates, coordinates)
                            }
                    )
                }
            }
        }

        val effectiveFullContentAlpha = if (isExpanded) 1f else fullContentAlpha
        if (effectiveFullContentAlpha > 0.001f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveFullContentAlpha }
                    .zIndex(1f)
                    .systemBarsPadding()
            ) {
                // ── Top bar ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { if (lyricsProgress > 0.5f) exitLyrics() else onCollapse() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = onSurface,
                            modifier = Modifier.graphicsLayer { rotationZ = -90f }
                        )
                    }
                    Text(
                        text = "NOW PLAYING",
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = { showOptionsSheet = true },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = onSurface
                        )
                    }
                }

                // ── Switchable content area: both layers always composed ──
                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Layer 1: Normal player content ──
                    if (normalContentAlpha > 0.001f) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = normalContentAlpha }
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(15.dp))

                            // ── Album art pager ──
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (queue.items.size > 1 && clampedExpandProgress > 0.95f) {
                                    AlbumArtPager(
                                        queue = queue,
                                        onSkipToIndex = onSkipToIndex,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 340.dp)
                                            .aspectRatio(1f)
                                            .graphicsLayer { alpha = expandedArtAlpha }
                                            .onGloballyPositioned { coordinates ->
                                                fullArtBounds = boundsInContainer(shellCoordinates, coordinates)
                                            }
                                    )
                                } else {
                                    AsyncImage(
                                        model = artworkUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 340.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(24.dp))
                                            .graphicsLayer { alpha = expandedArtAlpha }
                                            .onGloballyPositioned { coordinates ->
                                                fullArtBounds = boundsInContainer(shellCoordinates, coordinates)
                                            }
                                    )
                                }
                            }

                            Spacer(Modifier.height(28.dp))
                            AnimatedContent(
                                targetState = item,
                                transitionSpec = {
                                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                                        togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                                },
                                contentKey = { it.videoId },
                                label = "song-info"
                            ) { animItem ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = animItem.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = animItem.artistName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.clickable {
                                            animItem.artistId?.let { onArtistTap(it, animItem.artistName, null) }
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))

                            ExpandedPlaybackProgressSection(
                                positionMsFlow = positionMsFlow,
                                durationMs = state.durationMs,
                                durationText = item.durationText,
                                crossfadeEnabled = crossfadeEnabled,
                                hasNextTrack = queue.currentIndex + 1 < queue.items.size,
                                onSeek = onSeek,
                                playbackAccent = playbackAccent,
                                onSurface = onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(22.dp))

                            // ── Transport controls ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onToggleRepeat() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Repeat,
                                        contentDescription = if (isLooping) "Disable loop" else "Enable loop",
                                        tint = if (isLooping) primary else onSurfaceVariant,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    IconButton(onClick = onSkipPrev, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            Icons.Rounded.SkipPrevious,
                                            contentDescription = "Previous",
                                            tint = onSurface,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    val playInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .pressScale(playInteraction)
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = CircleShape,
                                                ambientColor = playbackAccent.copy(alpha = 0.25f),
                                                spotColor = playbackAccent.copy(alpha = 0.3f)
                                            )
                                            .background(playbackAccent, CircleShape)
                                            .clickable(
                                                interactionSource = playInteraction,
                                                indication = null
                                            ) { onPlayPause() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = onPlaybackAccent,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            Icons.Rounded.SkipNext,
                                            contentDescription = "Next",
                                            tint = onSurface,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            libraryRepository.toggleLike(item)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (mediaLibraryState.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (mediaLibraryState.isLiked) "Unlike" else "Like",
                                        tint = if (mediaLibraryState.isLiked) playbackAccent else onSurfaceVariant,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(50.dp))

                            val actionIconTint = onSurface
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { enterLyrics() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.lyrics_24px),
                                        contentDescription = "Lyrics",
                                        tint = actionIconTint,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }
                                IconButton(onClick = { showQueueSheet = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.queue_music_24px),
                                        contentDescription = "Queue",
                                        tint = actionIconTint,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (mediaLibraryState.isDownloaded) {
                                                android.widget.Toast
                                                    .makeText(context, "Already downloaded", android.widget.Toast.LENGTH_SHORT)
                                                    .show()
                                                return@launch
                                            }
                                            val result = libraryRepository.download(item)
                                            android.widget.Toast
                                                .makeText(
                                                    context,
                                                    if (result.isSuccess) {
                                                        "Downloaded"
                                                    } else {
                                                        "Download failed"
                                                    },
                                                    android.widget.Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.download_24px),
                                        contentDescription = "Download",
                                        tint = if (mediaLibraryState.isDownloaded) primary else actionIconTint,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val shareUrl = "https://music.youtube.com/watch?v=${item.videoId}"
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                                    },
                                                    "Share"
                                                )
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.share_24px),
                                        contentDescription = "Share",
                                        tint = actionIconTint,
                                        modifier = Modifier.size(35.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    // ── Layer 2: Lyrics content (always composed so bounds are tracked) ──
                    if (lyricsProgress > 0.001f) {
                        LyricsContentHost(
                            positionMsFlow = positionMsFlow,
                            lyricsStateFlow = lyricsStateFlow,
                            durationMs = state.durationMs,
                            isPlaying = state.isPlaying,
                            artworkUrl = artworkUrl,
                            compactArtAlpha = lyricsCompactArtAlpha,
                            controlsAlpha = lyricsControlsAlpha,
                            listAlpha = lyricsListAlpha,
                            onSeek = onSeek,
                            onSkipPrev = onSkipPrev,
                            onSkipNext = onSkipNext,
                            onPlayPause = onPlayPause,
                            onExitLyrics = { exitLyrics() },
                            onCompactArtPositioned = { coords ->
                                lyricsArtBounds = boundsInContainer(shellCoordinates, coords)
                            },
                            primary = primary,
                            playbackAccent = playbackAccent,
                            onPlaybackAccent = onPlaybackAccent,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant
                        )
                    } else {
                        // Always render an invisible compact art placeholder to track its bounds
                        // even when lyrics mode hasn't been activated yet
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent { /* invisible – only tracks layout bounds */ }
                                .zIndex(-1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .onGloballyPositioned { coords ->
                                            lyricsArtBounds = boundsInContainer(shellCoordinates, coords)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showQueueSheet) {
            QueueActionSheetHost(
                queue = queue,
                positionMsFlow = positionMsFlow,
                durationMs = state.durationMs,
                onDismiss = { showQueueSheet = false },
                onSkipToIndex = onSkipToIndex,
                onRemoveFromQueue = onRemoveFromQueue,
                onMoveInQueue = onMoveInQueue,
                onArtistTap = onArtistTap,
                onAlbumTap = onAlbumTap,
                crossfadeEnabled = crossfadeEnabled,
            )
        }

        // ── Options bottom sheet (Crossfade / View Artist / View Album) ──
        if (showOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOptionsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Crossfade") },
                        supportingContent = { Text("Blend songs seamlessly") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = { onToggleCrossfade() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedControlOnAccent,
                                    checkedTrackColor = selectedControlAccent.copy(alpha = 0.56f),
                                    checkedBorderColor = selectedControlAccent,
                                    checkedIconColor = selectedControlOnAccent
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onToggleCrossfade() }
                    )
                    ListItem(
                        headlineContent = { Text("View Artist") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showOptionsSheet = false
                            item.artistId?.let { onArtistTap(it, item.artistName, null) }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("View Album") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showOptionsSheet = false
                            item.albumId?.let { onAlbumTap(it, item.albumName ?: "", null) }
                        }
                    )
                }
            }
        }

        // ── Mini player with horizontal swipe ──
        val miniContainerColor = remember(mediaPalette.middle, mediaPalette.bottom) {
            opaqueColorForWhiteForeground(
                lerp(mediaPalette.middle, mediaPalette.bottom, 0.48f).copy(alpha = 1f)
            )
        }
        val miniTitleColor = readableContentColor(miniContainerColor)
        val miniSubtitleColor = miniTitleColor.copy(alpha = 0.78f)
        MiniPlayerBar(
            positionMsFlow = positionMsFlow,
            durationMs = state.durationMs,
            item = item,
            artworkUrl = artworkUrl,
            isPlaying = state.isPlaying,
            isExpanded = isExpanded,
            miniPlayerHeight = miniPlayerHeight,
            miniContentAlpha = miniContentAlpha,
            clampedExpandProgress = clampedExpandProgress,
            miniContainerColor = miniContainerColor,
            miniTitleColor = miniTitleColor,
            miniSubtitleColor = miniSubtitleColor,
            miniArtAlpha = if (hideBaseArtDuringExpandMorph) 0f else 1f,
            onExpand = onExpand,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrev = onSkipPrev,
            onMiniArtPositioned = { coordinates ->
                miniArtBounds = boundsInContainer(shellCoordinates, coordinates)
            }
        )

        // ── Expand/collapse morphing album art overlay ──
        val startBounds = miniArtBounds
        val endBounds = fullArtBounds
        if (startBounds != null && endBounds != null && canMorphAlbumArt) {
            MorphingAlbumArt(
                thumbnailUrl = artworkUrl,
                fromBounds = startBounds,
                toBounds = endBounds,
                expansion = clampedExpandProgress
            )
        }

        // ── Lyrics mode morphing album art overlay ──
        val lyricsFromBounds = fullArtBounds
        val lyricsToBounds = lyricsArtBounds
        if (lyricsFromBounds != null && lyricsToBounds != null && canMorphLyricsArt) {
            MorphingLyricsArt(
                thumbnailUrl = artworkUrl,
                fromBounds = lyricsFromBounds,
                toBounds = lyricsToBounds,
                expansion = lyricsProgress
            )
        }
    }
}

@Composable
private fun ExpandedPlaybackProgressSection(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    durationText: String?,
    crossfadeEnabled: Boolean,
    hasNextTrack: Boolean,
    onSeek: (Long) -> Unit,
    playbackAccent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val playbackProgress = playbackProgress(positionMs, durationMs)

    Column(modifier = modifier) {
        SeekablePlaybackBar(
            progress = playbackProgress,
            durationMs = durationMs,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
            color = playbackAccent,
            trackColor = Color(0xFF4A4A4A),
            trackHeight = 6.dp,
            thumbSize = 14.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(alpha = 0.85f)
            )
            Text(
                text = if (durationMs > 0) formatMs(durationMs) else (durationText ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(alpha = 0.85f)
            )
        }

        CrossfadeCountdownCue(
            visible = showCrossfadeCue(
                positionMs = positionMs,
                durationMs = durationMs,
                crossfadeEnabled = crossfadeEnabled,
                hasNextTrack = hasNextTrack
            ),
            accentColor = playbackAccent,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun LyricsContentHost(
    positionMsFlow: StateFlow<Long>,
    lyricsStateFlow: StateFlow<LyricsState>,
    durationMs: Long,
    isPlaying: Boolean,
    artworkUrl: String?,
    compactArtAlpha: Float,
    controlsAlpha: Float,
    listAlpha: Float,
    onSeek: (Long) -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayPause: () -> Unit,
    onExitLyrics: () -> Unit,
    onCompactArtPositioned: (LayoutCoordinates) -> Unit,
    primary: Color,
    playbackAccent: Color,
    onPlaybackAccent: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val lyricsState by lyricsStateFlow.collectAsStateWithLifecycle()

    LyricsContent(
        lyricsState = lyricsState,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        artworkUrl = artworkUrl,
        compactArtAlpha = compactArtAlpha,
        controlsAlpha = controlsAlpha,
        listAlpha = listAlpha,
        onSeek = onSeek,
        onSkipPrev = onSkipPrev,
        onSkipNext = onSkipNext,
        onPlayPause = onPlayPause,
        onExitLyrics = onExitLyrics,
        onCompactArtPositioned = onCompactArtPositioned,
        primary = primary,
        playbackAccent = playbackAccent,
        onPlaybackAccent = onPlaybackAccent,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant
    )
}

@Composable
private fun QueueActionSheetHost(
    queue: PlaybackQueue,
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    onDismiss: () -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    crossfadeEnabled: Boolean = false
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()

    QueueActionSheet(
        queue = queue,
        onDismiss = onDismiss,
        onSkipToIndex = onSkipToIndex,
        onRemoveFromQueue = onRemoveFromQueue,
        onMoveInQueue = onMoveInQueue,
        onArtistTap = onArtistTap,
        onAlbumTap = onAlbumTap,
        crossfadeEnabled = crossfadeEnabled,
        crossfadeLockActive = showCrossfadeCue(
            positionMs = positionMs,
            durationMs = durationMs,
            crossfadeEnabled = crossfadeEnabled,
            hasNextTrack = queue.currentIndex + 1 < queue.items.size
        )
    )
}

@Composable
private fun BoxScope.MiniPlayerBar(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    item: MediaItem,
    artworkUrl: String?,
    isPlaying: Boolean,
    isExpanded: Boolean,
    miniPlayerHeight: Dp,
    miniContentAlpha: Float,
    clampedExpandProgress: Float,
    miniContainerColor: Color,
    miniTitleColor: Color,
    miniSubtitleColor: Color,
    miniArtAlpha: Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onMiniArtPositioned: (LayoutCoordinates) -> Unit
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val progress = playbackProgress(positionMs, durationMs)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val miniSwipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val miniInteractionsEnabled = !isExpanded

    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(if (clampedExpandProgress < 0.35f) 2f else 0f)
            .fillMaxWidth()
            .height(miniPlayerHeight)
            .graphicsLayer {
                alpha = miniContentAlpha
                translationX = miniSwipeOffset.value
            }
            .pointerInput(miniInteractionsEnabled) {
                if (!miniInteractionsEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (miniSwipeOffset.value.absoluteValue > swipeThresholdPx) {
                                val goingLeft = miniSwipeOffset.value < 0
                                miniSwipeOffset.animateTo(
                                    targetValue = if (goingLeft) -size.width.toFloat() else size.width.toFloat(),
                                    animationSpec = tween(150)
                                )
                                if (goingLeft) onSkipNext() else onSkipPrev()
                                miniSwipeOffset.snapTo(if (goingLeft) size.width.toFloat() else -size.width.toFloat())
                                miniSwipeOffset.animateTo(0f, animationSpec = tween(200))
                            } else {
                                miniSwipeOffset.animateTo(0f, animationSpec = spring())
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { miniSwipeOffset.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            miniSwipeOffset.snapTo(miniSwipeOffset.value + dragAmount)
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            color = miniContainerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = miniInteractionsEnabled,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onExpand
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = miniTitleColor.copy(alpha = 0.92f),
                            trackColor = miniTitleColor.copy(alpha = 0.18f),
                            strokeWidth = 3.dp
                        )
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .graphicsLayer { alpha = miniArtAlpha }
                                .onGloballyPositioned(onMiniArtPositioned),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.width(14.dp))
                    AnimatedContent(
                        targetState = item,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        contentKey = { it.videoId },
                        label = "mini-info"
                    ) { animItem ->
                        Column {
                            Text(
                                text = animItem.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = miniTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = animItem.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = miniSubtitleColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                IconButton(onClick = onPlayPause, enabled = miniInteractionsEnabled) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = miniTitleColor
                    )
                }
                IconButton(onClick = onSkipNext, enabled = miniInteractionsEnabled) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = miniTitleColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueActionSheet(
    queue: PlaybackQueue,
    onDismiss: () -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    crossfadeEnabled: Boolean = false,
    crossfadeLockActive: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = QueueSheetContainerColor,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        QueueContent(
            queue = queue,
            onSkipToIndex = onSkipToIndex,
            onRemoveFromQueue = onRemoveFromQueue,
            onMoveInQueue = onMoveInQueue,
            onArtistTap = onArtistTap,
            onAlbumTap = onAlbumTap,
            onQueueActionConsumed = onDismiss,
            crossfadeEnabled = crossfadeEnabled,
            crossfadeLockActive = crossfadeLockActive,
            headerContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AlbumArtPager(
    queue: PlaybackQueue,
    onSkipToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = queue.currentIndex,
        pageCount = { queue.items.size }
    )

    LaunchedEffect(queue.currentIndex) {
        if (pagerState.currentPage != queue.currentIndex) {
            pagerState.animateScrollToPage(queue.currentIndex)
        }
    }

    val currentIndex by rememberUpdatedState(queue.currentIndex)
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userGestureActive by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userGestureActive = true
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collectLatest { settledPage ->
                if (userGestureActive && settledPage != currentIndex) {
                    onSkipToIndex(settledPage)
                }
                userGestureActive = false
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1)
        ),
        pageSpacing = 16.dp,
        beyondViewportPageCount = 1
    ) { page ->
        val pageItem = queue.items[page]
        AsyncImage(
            model = upscaleThumbnail(pageItem.thumbnailUrl),
            contentDescription = pageItem.title,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

// ── Expand/collapse morph overlay (mini circular → full rounded square) ──
@Composable
private fun MorphingAlbumArt(
    thumbnailUrl: String?,
    fromBounds: Rect,
    toBounds: Rect,
    expansion: Float
) {
    val density = LocalDensity.current
    val left = lerpFloat(fromBounds.left, toBounds.left, expansion)
    val top = lerpFloat(fromBounds.top, toBounds.top, expansion)
    val width = lerpFloat(fromBounds.width, toBounds.width, expansion)
    val miniCorner = fromBounds.width / 2f
    val fullCornerPx = with(density) { 24.dp.toPx() }
    val cornerPx = lerpFloat(miniCorner, fullCornerPx, expansion)
    val artSizeDp = with(density) { width.toDp() }
    val cornerDp = with(density) { cornerPx.toDp() }

    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(artSizeDp)
            .clip(RoundedCornerShape(cornerDp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun CrossfadeCountdownCue(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val cueAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "crossfadeCueAlpha"
    )
    val labelColor = accentColor.copy(alpha = (0.78f * cueAlpha).coerceIn(0f, 1f))

    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.graphicsLayer { alpha = cueAlpha },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CROSSFADE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                letterSpacing = 1.8.sp
            )
        }
    }
}

// ── Lyrics mode morph overlay (full rounded square → small compact square) ──
@Composable
private fun MorphingLyricsArt(
    thumbnailUrl: String?,
    fromBounds: Rect,
    toBounds: Rect,
    expansion: Float
) {
    val density = LocalDensity.current
    val left = lerpFloat(fromBounds.left, toBounds.left, expansion)
    val top = lerpFloat(fromBounds.top, toBounds.top, expansion)
    val width = lerpFloat(fromBounds.width, toBounds.width, expansion)
    val fullCornerPx = with(density) { 24.dp.toPx() }
    val compactCornerPx = with(density) { 12.dp.toPx() }
    val cornerPx = lerpFloat(fullCornerPx, compactCornerPx, expansion)
    val artSizeDp = with(density) { width.toDp() }
    val cornerDp = with(density) { cornerPx.toDp() }

    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(artSizeDp)
            .clip(RoundedCornerShape(cornerDp)),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LyricsContent(
    lyricsState: LyricsState,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    artworkUrl: String?,
    compactArtAlpha: Float,
    controlsAlpha: Float,
    listAlpha: Float,
    onSeek: (Long) -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayPause: () -> Unit,
    onExitLyrics: () -> Unit,
    onCompactArtPositioned: (LayoutCoordinates) -> Unit,
    primary: Color,
    playbackAccent: Color,
    onPlaybackAccent: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    val listState = rememberLazyListState()
    val playbackProgress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    val syncedLines = (lyricsState as? LyricsState.Loaded)?.takeIf { it.isSynced }?.lines
    var previousLineIndex by remember { mutableIntStateOf(-1) }
    val currentLineIndex = remember(positionMs, syncedLines) {
        syncedLines?.let { lines ->
            // Binary search for last line with timeMs <= positionMs (O(log n) vs O(n))
            var lo = 0
            var hi = lines.size - 1
            var result = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    result = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            result.coerceAtLeast(0)
        } ?: 0
    }
    LaunchedEffect(currentLineIndex) {
        // Track the previous line so we can animate it out
        previousLineIndex = currentLineIndex
    }

    // Auto-scroll: disabled for 5 s after manual scroll or lyric tap
    var userScrolled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            coroutineScope.launch {
                kotlinx.coroutines.delay(5000)
                userScrolled = false
            }
        }
    }
    LaunchedEffect(currentLineIndex) {
        if (!userScrolled && syncedLines != null && syncedLines.isNotEmpty()) {
            listState.animateScrollToItem(index = (currentLineIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Compact art + controls row ──
        // The row always occupies its layout slot (so bounds are always tracked), but its
        // content is faded via controlsAlpha.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = controlsAlpha }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CTA 1: tap compact art → exit lyrics
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer { alpha = compactArtAlpha }
                    .onGloballyPositioned { onCompactArtPositioned(it) }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onExitLyrics
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SeekablePlaybackBar(
                    progress = playbackProgress,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                    color = playbackAccent,
                    trackColor = Color(0xFF4A4A4A),
                    trackHeight = 5.dp,
                    thumbSize = 12.dp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatMs(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.75f)
                    )
                    Text(
                        if (durationMs > 0) formatMs(durationMs) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.75f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSkipPrev, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    val playInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .pressScale(playInteraction)
                            .shadow(
                                12.dp,
                                CircleShape,
                                ambientColor = playbackAccent.copy(alpha = 0.25f),
                                spotColor = playbackAccent.copy(alpha = 0.3f)
                            )
                            .background(playbackAccent, CircleShape)
                            .clickable(
                                interactionSource = playInteraction,
                                indication = null
                            ) { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = onPlaybackAccent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // ── Lyrics body ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = listAlpha }
        ) {
            when (val ls = lyricsState) {
                is LyricsState.Loading, LyricsState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(
                            modifier = Modifier.size(56.dp),
                            color = primary
                        )
                    }
                }
                is LyricsState.NotFound -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No lyrics available",
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is LyricsState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Couldn't load lyrics",
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is LyricsState.Loaded -> {
                    val dimColor = remember(onSurface) { onSurface.copy(alpha = 0.35f) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(ls.lines) { index, line ->
                            val isCurrentLine = ls.isSynced && index == currentLineIndex
                            val needsAnimation = ls.isSynced && (index == currentLineIndex || index == previousLineIndex)
                            val lineColor = if (needsAnimation) {
                                animateColorAsState(
                                    targetValue = if (isCurrentLine) onSurface else dimColor,
                                    animationSpec = tween(300),
                                    label = "lyricColor"
                                ).value
                            } else if (isCurrentLine) {
                                onSurface
                            } else {
                                dimColor
                            }
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                color = lineColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (ls.isSynced) Modifier.clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            userScrolled = true
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(5000)
                                                userScrolled = false
                                            }
                                            onSeek(line.timeMs)
                                        } else Modifier
                                    )
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekablePlaybackBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    trackHeight: Dp,
    thumbSize: Dp
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val displayProgress = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f)

    LaunchedEffect(progress, isSeeking) {
        if (!isSeeking) {
            seekPosition = progress.coerceIn(0f, 1f)
        }
    }

    Slider(
        value = displayProgress,
        onValueChange = {
            isSeeking = true
            seekPosition = it.coerceIn(0f, 1f)
        },
        onValueChangeFinished = {
            val targetPosition = (seekPosition.coerceIn(0f, 1f) * durationMs).toLong()
            if (durationMs > 0L) {
                onSeek(targetPosition)
            }
            isSeeking = false
        },
        valueRange = 0f..1f,
        modifier = modifier,
        thumb = {
            Box(
                Modifier
                    .size(thumbSize)
                    .shadow(4.dp, CircleShape, ambientColor = color.copy(alpha = 0.35f), spotColor = color.copy(alpha = 0.4f))
                    .background(color, CircleShape)
            )
        },
        track = {
            LinearProgressIndicator(
                progress = { displayProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight),
                color = color,
                trackColor = trackColor
            )
        }
    )
}

private fun boundsInContainer(
    container: LayoutCoordinates?,
    target: LayoutCoordinates
): Rect? {
    val shell = container ?: return null
    val topLeft: Offset = shell.localPositionOf(target, Offset.Zero)
    val width = target.size.width.toFloat()
    val height = target.size.height.toFloat()
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + width,
        bottom = topLeft.y + height
    )
}

private fun playbackProgress(positionMs: Long, durationMs: Long): Float {
    return if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
}

private fun showCrossfadeCue(
    positionMs: Long,
    durationMs: Long,
    crossfadeEnabled: Boolean,
    hasNextTrack: Boolean
): Boolean {
    if (!crossfadeEnabled || !hasNextTrack || durationMs <= 0L) return false
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    return remainingMs in 1L..CROSSFADE_UI_LEAD_MS
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun readableContentColor(background: Color): Color {
    val blackContrast = ColorUtils.calculateContrast(Color.Black.toArgb(), background.toArgb())
    val whiteContrast = ColorUtils.calculateContrast(Color.White.toArgb(), background.toArgb())
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun opaqueColorForWhiteForeground(
    color: Color,
    minContrast: Double = 4.5
): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.copy(alpha = 1f).toArgb(), hsl)
    var candidate = Color(ColorUtils.HSLToColor(hsl)).copy(alpha = 1f)
    repeat(10) {
        val contrast = ColorUtils.calculateContrast(Color.White.toArgb(), candidate.toArgb())
        if (contrast >= minContrast) return candidate
        hsl[2] = (hsl[2] - 0.06f).coerceAtLeast(0.12f)
        candidate = Color(ColorUtils.HSLToColor(hsl)).copy(alpha = 1f)
    }
    return candidate
}
