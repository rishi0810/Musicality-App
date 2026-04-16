package com.proj.Musicality.ui.player

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.media3.common.Player
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.components.hapticClickable
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
private const val DEFAULT_MINI_SWIPE_SENSITIVITY = 0.73f
private val QueueSheetContainerColor = Color(0xFF18181B)
private val PlayerHorizontalPadding = 20.dp

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
    miniPlayerHeight: Dp = 70.dp,
    onLyricsOpenChange: (Boolean) -> Unit = {},
    onProgressBarInteractingChange: (Boolean) -> Unit = {}
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
        fallbackSurface = surface,
        animateTransitions = false
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

    val canMorphAlbumArt =
        showMorphingOverlay &&
            miniArtBounds != null &&
            fullArtBounds != null

    // ── Lyrics panel state: 0 = closed, 1 = full-screen ──
    val lyricsAnim = remember { Animatable(0f) }
    val isLyricsOpen by remember { derivedStateOf { lyricsAnim.value > 0.01f } }

    val showPlayerContent by remember { derivedStateOf { lyricsAnim.value < 0.999f } }

    val hideBaseArtDuringExpandMorph = canMorphAlbumArt
    val expandedArtAlpha = if (hideBaseArtDuringExpandMorph) 0f else 1f

    fun openLyrics() = coroutineScope.launch { lyricsAnim.animateTo(1f, lyricsSpring) }
    fun closeLyrics() = coroutineScope.launch { lyricsAnim.animateTo(0f, lyricsSpring) }

    LaunchedEffect(isLyricsOpen) { onLyricsOpenChange(isLyricsOpen) }

    // Reset lyrics panel when the sheet collapses
    LaunchedEffect(isExpanded) {
        if (!isExpanded) lyricsAnim.animateTo(0f, lyricsSpring)
    }

    val isLooping = state.repeatMode == Player.REPEAT_MODE_ONE
    var showQueueSheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = isExpanded) { progress: Flow<androidx.activity.BackEventCompat> ->
        try {
            progress.collect { }
            when {
                lyricsAnim.value > 0.01f -> closeLyrics()
                else -> onCollapse()
            }
        } catch (_: CancellationException) {
        }
    }

    BackHandler(enabled = isExpanded) {
        when {
            lyricsAnim.value > 0.01f -> closeLyrics()
            else -> onCollapse()
        }
    }

    // Keep collapsed state fully off, but start tinting immediately once expansion begins.
    // This avoids a visible "nothing, then sudden color" threshold while dragging.
    val playerBackgroundAlpha = run {
        val t = clampedExpandProgress.coerceIn(0f, 1f)
        val smoothstep = t * t * (3f - 2f * t)
        smoothstep * 0.96f
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

        // ── Invisible bounds tracker for full art (always composed during expand morph) ──
        // Mirrors the real art geometry: edge-to-edge, square, starts right below the status bar.
        if (fullContentAlpha <= 0.001f && clampedExpandProgress > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { /* invisible – only tracks layout bounds */ }
                    .zIndex(-1f)
                    .systemBarsPadding()
            ) {
                // Mirrors the real column: back row (~48dp) + 12dp gap + centered art + content
                Spacer(Modifier.height(48.dp))
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(28.dp))
                Spacer(Modifier.height(250.dp))
            }
        }

        val effectiveFullContentAlpha = fullContentAlpha
        if (effectiveFullContentAlpha > 0.001f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveFullContentAlpha }
                    .zIndex(1f)
                    .systemBarsPadding()
            ) {
                if (showPlayerContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val f = lyricsAnim.value
                                alpha = 1f - f.coerceIn(0f, 1f)
                                translationY = -f.coerceIn(0f, 1f) * size.height * 0.12f
                            }
                    ) {
                        // ── Back button anchored top-left ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp)
                        ) {
                            HapticIconButton(
                                onClick = {
                                    when {
                                        lyricsAnim.value > 0.01f -> closeLyrics()
                                        else -> onCollapse()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse player",
                                    tint = onSurface,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Small gap between arrow and cover art
                        Spacer(Modifier.height(30.dp))

                        // ── Centered cover art (capped 340dp, rounded 24dp) ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
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

                        Spacer(Modifier.height(32.dp))

                        // ── Lyrics + Queue, conjoined pill CTAs ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Conjoined pill container
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(onSurface.copy(alpha = 0.08f))
                                    .height(52.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Lyrics button
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .hapticClickable { if (isLyricsOpen) closeLyrics() else openLyrics() }
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.lyrics_24px),
                                        contentDescription = null,
                                        tint = onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Lyrics",
                                        color = onSurface,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Vertical divider
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight(0.55f)
                                        .background(onSurface.copy(alpha = 0.20f))
                                )

                                // Queue button
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .hapticClickable { showQueueSheet = true }
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.queue_music_24px),
                                        contentDescription = null,
                                        tint = onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Queue",
                                        color = onSurface,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(34.dp))

                        // ── Row 1: Title/Artist (left, weight 1) | Like | Options (right) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedContent(
                                targetState = item,
                                transitionSpec = {
                                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                                        togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                                },
                                contentKey = { it.videoId },
                                label = "song-title",
                                modifier = Modifier.weight(1f)
                            ) { animItem ->
                                Text(
                                    text = animItem.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = onSurface,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HapticIconButton(
                                    onClick = {
                                        coroutineScope.launch { libraryRepository.toggleLike(item) }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (mediaLibraryState.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (mediaLibraryState.isLiked) "Unlike" else "Like",
                                        tint = if (mediaLibraryState.isLiked) playbackAccent else onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                HapticIconButton(
                                    onClick = { showOptionsSheet = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        contentDescription = "More options",
                                        tint = onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        // ── Artist name on its own line beneath the title ──
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                                    togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                            },
                            contentKey = { it.videoId },
                            label = "song-artist",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) { animItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                val artistClickModifier = if (animItem.artistId != null) {
                                    Modifier.clickable {
                                        animItem.artistId?.let { onArtistTap(it, animItem.artistName, null) }
                                    }
                                } else {
                                    Modifier
                                }

                                Text(
                                    text = animItem.artistName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurface.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = artistClickModifier
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // ── Row 2: Progress bar ──
                        ExpandedPlaybackProgressSection(
                            positionMsFlow = positionMsFlow,
                            durationMs = state.durationMs,
                            durationText = item.durationText,
                            crossfadeEnabled = crossfadeEnabled,
                            hasNextTrack = queue.currentIndex + 1 < queue.items.size,
                            onSeek = onSeek,
                            onProgressBarInteractingChange = onProgressBarInteractingChange,
                            playbackAccent = playbackAccent,
                            onSurface = onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )

                        Spacer(Modifier.height(18.dp))

                        // ── Row 3: Prev + Play/Pause + Next, centered ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HapticIconButton(onClick = onSkipPrev, modifier = Modifier.size(64.dp)) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = onSurface,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }

                                val playInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .pressScale(playInteraction)
                                        .hapticClickable(
                                            interactionSource = playInteraction,
                                            indication = null
                                        ) { onPlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }

                                HapticIconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) {
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = onSurface,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
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

        // ── Options bottom sheet ──
        if (showOptionsSheet) {
            val switchColors = SwitchDefaults.colors(
                checkedThumbColor = selectedControlOnAccent,
                checkedTrackColor = selectedControlAccent.copy(alpha = 0.56f),
                checkedBorderColor = selectedControlAccent,
                checkedIconColor = selectedControlOnAccent
            )
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
                                colors = switchColors
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onToggleCrossfade() }
                    )
                    ListItem(
                        headlineContent = { Text("Loop song") },
                        supportingContent = { Text("Repeat the current track") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isLooping,
                                onCheckedChange = { onToggleRepeat() },
                                colors = switchColors
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onToggleRepeat() }
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
                    ListItem(
                        headlineContent = {
                            Text(if (mediaLibraryState.isDownloaded) "Downloaded" else "Download song")
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.download_24px),
                                contentDescription = null,
                                tint = if (mediaLibraryState.isDownloaded) primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showOptionsSheet = false
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
                                        if (result.isSuccess) "Downloaded" else "Download failed",
                                        android.widget.Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Share song") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.share_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showOptionsSheet = false
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
            canSkipNext = queue.currentIndex < queue.items.lastIndex,
            canSkipPrevious = queue.currentIndex > 0,
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

        // ── Lyrics full-screen sliding panel ──
        if (isLyricsOpen) {
            LyricsPanel(
                positionMsFlow = positionMsFlow,
                lyricsStateFlow = lyricsStateFlow,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                onClose = { closeLyrics() },
                onSeek = onSeek,
                onSkipPrev = onSkipPrev,
                onSkipNext = onSkipNext,
                onPlayPause = onPlayPause,
                backgroundColor = mediaPalette.bottom,
                playbackAccent = playbackAccent,
                playbackButton = playbackAccent,
                onProgressBarInteractingChange = onProgressBarInteractingChange,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .graphicsLayer {
                        val f = lyricsAnim.value
                        translationY = (1f - f) * size.height
                    }
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
    onProgressBarInteractingChange: (Boolean) -> Unit,
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
            onInteractingChange = onProgressBarInteractingChange,
            modifier = Modifier.fillMaxWidth(),
            color = playbackAccent,
            trackColor = Color(0xFF4A4A4A),
            trackHeight = 6.dp,
            expandedTrackHeight = 12.dp
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
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    onMiniArtPositioned: (LayoutCoordinates) -> Unit
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val progress = playbackProgress(positionMs, durationMs)
    val miniSwipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val miniInteractionsEnabled = !isExpanded
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val miniSwipeAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
    val autoSwipeThresholdPx = remember {
        (
            600f /
                (1f + kotlin.math.exp(-(-11.44748f * DEFAULT_MINI_SWIPE_SENSITIVITY + 9.04945f)))
            )
    }

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
            .pointerInput(miniInteractionsEnabled, canSkipNext, canSkipPrevious, layoutDirection) {
                if (!miniInteractionsEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartTime = System.currentTimeMillis()
                        totalDragDistance = 0f
                    },
                    onDragEnd = {
                        val dragDurationMs = System.currentTimeMillis() - dragStartTime
                        val velocityPxPerMs =
                            if (dragDurationMs > 0L) totalDragDistance / dragDurationMs else 0f
                        val currentOffset = miniSwipeOffset.value
                        val velocityThreshold =
                            (DEFAULT_MINI_SWIPE_SENSITIVITY * -8.25f) + 8.5f
                        val shouldChangeSong =
                            (
                                currentOffset.absoluteValue > 50f &&
                                    velocityPxPerMs > velocityThreshold
                                ) ||
                                (currentOffset.absoluteValue > autoSwipeThresholdPx)

                        if (shouldChangeSong) {
                            if (currentOffset > 0f && canSkipPrevious) {
                                onSkipPrev()
                            } else if (currentOffset <= 0f && canSkipNext) {
                                onSkipNext()
                            }
                        }
                        coroutineScope.launch {
                            miniSwipeOffset.animateTo(0f, animationSpec = miniSwipeAnimationSpec)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            miniSwipeOffset.animateTo(0f, animationSpec = miniSwipeAnimationSpec)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val adjustedDragAmount =
                            if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                        val tryingToSwipeRight = adjustedDragAmount > 0f
                        val tryingToSwipeLeft = adjustedDragAmount < 0f
                        val allowLeft = tryingToSwipeLeft && canSkipNext
                        val allowRight = tryingToSwipeRight && canSkipPrevious
                        val canReturnToCenter =
                            (tryingToSwipeRight && !canSkipPrevious && miniSwipeOffset.value < 0f) ||
                                (tryingToSwipeLeft && !canSkipNext && miniSwipeOffset.value > 0f)

                        if (allowLeft || allowRight || canReturnToCenter) {
                            totalDragDistance += adjustedDragAmount.absoluteValue
                            coroutineScope.launch {
                                miniSwipeOffset.snapTo(miniSwipeOffset.value + adjustedDragAmount)
                            }
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
                        .hapticClickable(
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
                                .clip(CircleShape)
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
                HapticIconButton(onClick = onPlayPause, enabled = miniInteractionsEnabled) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = miniTitleColor
                    )
                }
                HapticIconButton(onClick = onSkipNext, enabled = miniInteractionsEnabled) {
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                    HapticIconButton(
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
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .absoluteValue
        val interpolation = (1f - pageOffset.coerceIn(0f, 1f))
        val pageScale = lerpFloat(0.92f, 1f, interpolation)
        val pageAlpha = lerpFloat(0.7f, 1f, interpolation)

        AsyncImage(
            model = upscaleThumbnail(pageItem.thumbnailUrl),
            contentDescription = pageItem.title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                    alpha = pageAlpha
                }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LyricsPanel(
    positionMsFlow: StateFlow<Long>,
    lyricsStateFlow: StateFlow<LyricsState>,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayPause: () -> Unit,
    onProgressBarInteractingChange: (Boolean) -> Unit,
    backgroundColor: Color,
    playbackAccent: Color,
    playbackButton: Color,
    modifier: Modifier = Modifier
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val lyricsState by lyricsStateFlow.collectAsStateWithLifecycle()
    val playbackProgress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val syncedLines = (lyricsState as? LyricsState.Loaded)?.takeIf { it.isSynced }?.lines
    val currentLineIndex = remember(positionMs, syncedLines) {
        syncedLines?.let { lines ->
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

    var userScrolled by remember { mutableStateOf(false) }
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
            val target = (currentLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(index = target)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
    ) {
        // ── Header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lyrics",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // ── Lyrics body ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val ls = lyricsState) {
                is LyricsState.Loading, LyricsState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White
                        )
                    }
                }
                is LyricsState.NotFound -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No lyrics available",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is LyricsState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Couldn't load lyrics",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is LyricsState.Loaded -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            ls.lines,
                            key = { index, _ -> index }
                        ) { index, line ->
                            val distance = if (ls.isSynced) (index - currentLineIndex).absoluteValue else 0
                            val alpha = when {
                                !ls.isSynced -> 1f
                                distance == 0 -> 1f
                                distance == 1 -> 0.55f
                                distance == 2 -> 0.4f
                                else -> 0.25f
                            }
                            val lineModifier = if (ls.isSynced) {
                                Modifier
                                    .fillMaxWidth()
                                    .hapticClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        userScrolled = true
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(5000)
                                            userScrolled = false
                                        }
                                        onSeek(line.timeMs)
                                    }
                                    .padding(vertical = 4.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            }
                            Text(
                                text = line.text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = alpha),
                                modifier = lineModifier
                            )
                        }
                    }
                }
            }
        }

        // ── Controls ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 56.dp, top = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                HapticIconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = R.drawable.collapse_all_24px),
                        contentDescription = "Close lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            SeekablePlaybackBar(
                progress = playbackProgress,
                durationMs = durationMs,
                onSeek = onSeek,
                onInteractingChange = onProgressBarInteractingChange,
                modifier = Modifier.fillMaxWidth(),
                color = playbackAccent,
                trackColor = Color.White.copy(alpha = 0.18f),
                trackHeight = 3.dp,
                expandedTrackHeight = 10.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatMs(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    if (durationMs > 0) formatMs(durationMs) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HapticIconButton(onClick = onSkipPrev, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                val playInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .pressScale(playInteraction)
                        .shadow(
                            12.dp,
                            CircleShape,
                            ambientColor = playbackButton.copy(alpha = 0.25f),
                            spotColor = playbackButton.copy(alpha = 0.3f)
                        )
                        .background(playbackButton, CircleShape)
                        .hapticClickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                HapticIconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekablePlaybackBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onInteractingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    trackHeight: Dp,
    expandedTrackHeight: Dp
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var lastDragHapticBucket by remember { mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val displayProgress = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f)

    LaunchedEffect(progress, isSeeking) {
        if (!isSeeking) {
            seekPosition = progress.coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(isSeeking) {
        onInteractingChange(isSeeking)
    }

    val expandedHeight = expandedTrackHeight
    val animatedHeight by animateDpAsState(
        targetValue = if (isSeeking) expandedHeight else trackHeight,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "progressBarHeight"
    )

    Box(
        modifier = modifier
            .height(expandedHeight)
            .pointerInput(durationMs) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                detectTapGestures(
                    onPress = { offset ->
                        isSeeking = true
                        seekPosition = (offset.x / width).coerceIn(0f, 1f)
                        if (tryAwaitRelease()) {
                            if (durationMs > 0L) {
                                onSeek((seekPosition * durationMs).toLong())
                                // Stronger confirmation haptic for discrete tap-to-seek.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            isSeeking = false
                        }
                    }
                )
            }
            .pointerInput(durationMs) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isSeeking = true
                        seekPosition = (offset.x / width).coerceIn(0f, 1f)
                        lastDragHapticBucket = (seekPosition * 50f).toInt()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = {
                        if (durationMs > 0L) {
                            onSeek((seekPosition * durationMs).toLong())
                        }
                        isSeeking = false
                        lastDragHapticBucket = -1
                    },
                    onDragCancel = {
                        isSeeking = false
                        lastDragHapticBucket = -1
                    },
                    onHorizontalDrag = { change, _ ->
                        seekPosition = (change.position.x / width).coerceIn(0f, 1f)
                        val bucket = (seekPosition * 50f).toInt()
                        if (bucket != lastDragHapticBucket) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastDragHapticBucket = bucket
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(displayProgress)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
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
