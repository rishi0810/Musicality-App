package com.proj.Musicality.ui.player

import android.content.Intent
import android.content.ClipData
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.util.fastForEach
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.media3.common.Player
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.proj.Musicality.R
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.ProviderLoadState
import com.proj.Musicality.data.model.RelatedState
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.ui.components.pressScale
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.theme.AppTypography
import com.proj.Musicality.ui.theme.AppShapes
import com.proj.Musicality.ui.theme.AppColors
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.ui.theme.defaultMediaBackdropPalette
import com.proj.Musicality.config.AppConfig
import com.proj.Musicality.util.formatMs
import com.proj.Musicality.util.toCleanSongTitle
import com.proj.Musicality.util.toSearchAwareTitle
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

private const val TAG = "PlayerSheet"
private const val CROSSFADE_UI_LEAD_MS = 10_000L
private const val DEFAULT_MINI_SWIPE_SENSITIVITY = 0.73f
private const val MAX_LYRICS_SHARE_LINES = 6
// Active-line transition fires this many ms before the line's timestamp, matching
// the anticipatory feel of Apple-Music-style synced lyrics.
private const val LINE_LOOKAHEAD_MS = 100L
private const val PlayerHeroHeightFraction = 0.47f
private val PlayerHorizontalPadding = 20.dp
private val PlayerHeroMinHeight = 360.dp
private val PlayerHeroMaxHeight = 440.dp
// Direct distance from the absolute top of the screen to the Lyrics/Queue stack.
// Increase this to move Lyrics/Queue, song details, and controls further down.
// Decrease this to move the stack up.
private val PlayerControlsTopDistanceFromScreenTop = 465.dp
private const val PlayerOverlayDismissFraction = 0.2f
private const val PlayerOverlayDismissVelocityPx = 1200f
@Immutable
private data class LyricsShareColorCombo(
    val name: String,
    val background: Color,
    val textColor: Color
)

// Curated aesthetic background/text pairs. The first slot is reserved at runtime
// for a combo derived from the current song's extracted palette.
private val LyricsShareStaticCombos = listOf(
    LyricsShareColorCombo("Ink", Color(0xFF0D1117), Color(0xFFEDEDED)),
    LyricsShareColorCombo("Cream", Color(0xFFF2EBD7), Color(0xFF1B1A17)),
    LyricsShareColorCombo("Navy", Color(0xFF0F2A4A), Color(0xFFF7E7CE)),
    LyricsShareColorCombo("Forest", Color(0xFF1F4037), Color(0xFFE9F5DB)),
    LyricsShareColorCombo("Plum", Color(0xFF2B1944), Color(0xFFE9D5FF)),
    LyricsShareColorCombo("Crimson", Color(0xFF9F1239), Color(0xFFFFE4E6)),
    LyricsShareColorCombo("Amber", Color(0xFFF59E0B), Color(0xFF1F1209)),
    LyricsShareColorCombo("Charcoal", Color(0xFF1F2937), Color(0xFF86EFAC)),
    LyricsShareColorCombo("Blush", Color(0xFFFFE4E6), Color(0xFF7C2D12)),
    LyricsShareColorCombo("Electric", Color(0xFF2563EB), Color(0xFFE0F2FE)),
    LyricsShareColorCombo("Jet", Color(0xFF111111), Color(0xFFFCD34D)),
    LyricsShareColorCombo("Paper", Color(0xFFFFFFFF), Color(0xFF0A0A0A))
)

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
    loadingTrackIdFlow: StateFlow<String?>,
    lyricsStateFlow: StateFlow<LyricsState>,
    lyricsProviderStatesFlow: StateFlow<Map<String, ProviderLoadState>>,
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
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    relatedStateFlow: StateFlow<RelatedState>,
    onRelatedLoad: (String) -> Unit,
    onRelatedSongTap: (MediaItem) -> Unit,
    onLyricsLoadAllProviders: (MediaItem) -> Unit,
    onLyricsSwitchProvider: (String) -> Unit,
    crossfadeEnabled: Boolean = false,
    onToggleCrossfade: () -> Unit = {},
    miniPlayerHeight: Dp = 70.dp,
    onLyricsOpenChange: (Boolean) -> Unit = {},
    onProgressBarInteractingChange: (Boolean) -> Unit = {}
) {
    val item = state.currentItem ?: return
    val queue = state.queue
    val loadingTrackId by loadingTrackIdFlow.collectAsStateWithLifecycle()
    val isCurrentLoading = loadingTrackId != null && loadingTrackId == item.videoId
    var displayIndex by remember { mutableIntStateOf(queue.currentIndex) }
    LaunchedEffect(queue.currentIndex) { displayIndex = queue.currentIndex }
    val displayItem = queue.items.getOrElse(displayIndex) { item }
    // Read the expand progress state here — PlayerSheet subscribes, not MusicApp
    val expandProgress = expandProgressState.value
    val clampedExpandProgress = expandProgress.coerceIn(0f, 1f)
    val miniContentAlpha = (1f - clampedExpandProgress * 4f).coerceIn(0f, 1f)
    val fullContentAlpha = clampedExpandProgress
    val surface = MaterialTheme.colorScheme.surface

    val artworkUrl = upscaleThumbnail(item.thumbnailUrl)
    LaunchedEffect(item.videoId, artworkUrl) {
        Log.d(TAG, "PlayerSheet hero artwork url=$artworkUrl for videoId=${item.videoId}")
    }
    // Always read the root-provided palette. Running a parallel extraction here
    // would diverge in timing (root animates, this one didn't) and produce
    // visibly mismatched colors between the mini/full player and consumers like
    // the bottom nav bar. When the root palette is briefly null (no artwork
    // available yet) fall back to the synchronous default so every component
    // still sees a consistent value.
    val mediaPalette = LocalPlaybackBackdropPalette.current
        ?: remember(surface) { defaultMediaBackdropPalette(surface) }
    val playerScrollState = rememberScrollState()
    val density = LocalDensity.current
    val miniPositionMs by positionMsFlow.collectAsStateWithLifecycle()
    val miniPlayerRevealOffsetPx = with(density) { 520.dp.toPx() }
    val miniPlayerAlpha by animateFloatAsState(
        targetValue = if (playerScrollState.value > miniPlayerRevealOffsetPx) 1f else 0f,
        animationSpec = tween(180),
        label = "scroll-mini-player-alpha"
    )
    val gradientEnabled by AppConfig.playerGradientEnabled.collectAsStateWithLifecycle()
    val plainOnSurface = MaterialTheme.colorScheme.onSurface
    val primary = mediaPalette.accent
    val onSurface = if (gradientEnabled) mediaPalette.title else plainOnSurface
    val onSurfaceVariant = if (gradientEnabled) mediaPalette.body else plainOnSurface.copy(alpha = 0.66f)
    // Controls sit over the artwork gradient (white reads on any art) in gradient
    // mode; over a plain themed surface they must track the theme instead.
    val playerControlColor = if (gradientEnabled) Color.White else plainOnSurface
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
    val lyricsState by lyricsStateFlow.collectAsStateWithLifecycle()
    val relatedState by relatedStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(item.videoId) { onRelatedLoad(item.videoId) }

    // ── Lyrics panel state: 0 = closed, 1 = full-screen ──
    val lyricsAnim = remember { Animatable(0f) }
    val canHandleBack = clampedExpandProgress > 0.02f || lyricsAnim.value > 0.01f
    val isLyricsOpen by remember { derivedStateOf { lyricsAnim.value > 0.01f } }
    val showPlayerContent = true

    val isLooping = state.repeatMode == Player.REPEAT_MODE_ONE
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember(item.videoId) { mutableStateOf<Int?>(null) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var lyricsShareMode by remember(item.videoId) { mutableStateOf(false) }
    var dismissOptionsAfterDownload by remember(item.videoId) { mutableStateOf(false) }
    val lyricCandidates = remember(lyricsState, item.videoId) {
        (lyricsState as? LyricsState.Loaded)
            ?.lines
            ?.map { it.text.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    fun openLyrics() = coroutineScope.launch { lyricsAnim.animateTo(1f, lyricsSpring) }
    fun closeLyrics() = coroutineScope.launch { lyricsAnim.animateTo(0f, lyricsSpring) }
    fun closeLyricsScreen() {
        lyricsShareMode = false
        closeLyrics()
    }
    fun handleBackCta() {
        if (lyricsAnim.value > 0.01f) {
            closeLyricsScreen()
        } else {
            onCollapse()
        }
    }

    LaunchedEffect(isLyricsOpen) { onLyricsOpenChange(isLyricsOpen) }

    // Reset lyrics panel when the sheet collapses
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            lyricsAnim.animateTo(0f, lyricsSpring)
            lyricsShareMode = false
        }
    }

    LaunchedEffect(showOptionsSheet, dismissOptionsAfterDownload, mediaLibraryState.isDownloaded) {
        if (showOptionsSheet && dismissOptionsAfterDownload && mediaLibraryState.isDownloaded) {
            kotlinx.coroutines.delay(220)
            showOptionsSheet = false
            dismissOptionsAfterDownload = false
        }
    }

    LaunchedEffect(item.videoId, sleepTimerMinutes) {
        val minutes = sleepTimerMinutes ?: return@LaunchedEffect
        kotlinx.coroutines.delay(minutes * 60_000L)
        if (state.isPlaying) onPlayPause()
        sleepTimerMinutes = null
    }

    PredictiveBackHandler(enabled = canHandleBack) { progress: Flow<androidx.activity.BackEventCompat> ->
        try {
            progress.collect { }
            handleBackCta()
        } catch (_: CancellationException) {
        }
    }

    BackHandler(enabled = canHandleBack) {
        handleBackCta()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollFade = if (gradientEnabled) {
            (playerScrollState.value / with(density) { 520.dp.toPx() }).coerceIn(0f, 1f)
        } else {
            0f
        }
        val sheetBackground = if (gradientEnabled) {
            lerp(mediaPalette.bottom, Color.Black, scrollFade)
        } else {
            surface
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(sheetBackground)
        )

        val effectiveFullContentAlpha = fullContentAlpha
        if (effectiveFullContentAlpha > 0.001f) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveFullContentAlpha }
                    .zIndex(1f)
            ) {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val heroHeight = (maxHeight * PlayerHeroHeightFraction)
                    .coerceIn(PlayerHeroMinHeight, PlayerHeroMaxHeight)
                val plainArtSide = (maxWidth * 0.78f).coerceAtMost(heroHeight)
                val gradientSwatchesByLightness = remember(mediaPalette.top, mediaPalette.middle, mediaPalette.bottom) {
                    listOf(mediaPalette.top, mediaPalette.middle, mediaPalette.bottom)
                        .sortedByDescending { it.luminance() }
                }
                val heroBlendColor = gradientSwatchesByLightness[0]
                val heroGradientStops = remember(heroBlendColor) {
                    arrayOf(
                        0f to Color.Transparent,
                        0.3f to Color.Transparent,
                        0.5f to heroBlendColor.copy(alpha = 0.25f),
                        0.65f to heroBlendColor.copy(alpha = 0.55f),
                        0.8f to heroBlendColor.copy(alpha = 0.85f),
                        0.9f to heroBlendColor.copy(alpha = 0.95f),
                        1f to heroBlendColor
                    )
                }

                if (showPlayerContent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(if (gradientEnabled) heroBlendColor else surface)
                            }
                    ) {
                    Box(Modifier.fillMaxSize()) {
                        if (gradientEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight)
                                    .graphicsLayer {
                                        translationY = -playerScrollState.value * 0.72f
                                    }
                                    .zIndex(2f)
                            ) {
                                if (queue.items.size > 1 && clampedExpandProgress > 0.95f) {
                                    AlbumArtPager(
                                        queue = queue,
                                        onSkipToIndex = onSkipToIndex,
                                        onDisplayIndexChanged = { displayIndex = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(heroHeight),
                                        cornerRadius = 0.dp
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artworkUrl)
                                            .size(coil3.size.Size.ORIGINAL)
                                            .crossfade(false)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(heroHeight)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.BottomCenter)
                                        .background(Brush.verticalGradient(colorStops = heroGradientStops))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(heroBlendColor)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(PlayerControlsTopDistanceFromScreenTop)
                                    .graphicsLayer {
                                        translationY = -playerScrollState.value * 0.72f
                                    }
                                    .zIndex(2f),
                                contentAlignment = Alignment.Center
                            ) {
                                val plainArtShape = RoundedCornerShape(18.dp)
                                if (queue.items.size > 1 && clampedExpandProgress > 0.95f) {
                                    AlbumArtPager(
                                        queue = queue,
                                        onSkipToIndex = onSkipToIndex,
                                        onDisplayIndexChanged = { displayIndex = it },
                                        modifier = Modifier.size(plainArtSide),
                                        cornerRadius = 18.dp
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artworkUrl)
                                            .size(coil3.size.Size.ORIGINAL)
                                            .crossfade(false)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(plainArtSide)
                                            .clip(plainArtShape)
                                    )
                                }
                            }
                        }

                        HapticIconButton(
                            onClick = ::handleBackCta,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .zIndex(3f)
                                    .padding(start = 12.dp, top = statusBarTop + 10.dp)
                                .size(46.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.36f),
                                    spotColor = Color.Black.copy(alpha = 0.46f)
                                )
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.54f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse player",
                                tint = Color.White.copy(alpha = 0.96f),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(playerScrollState)
                                .padding(top = PlayerControlsTopDistanceFromScreenTop)
                        ) {
                            // ── Row 1: Song title (full width, no trailing CTAs) ──
                            AnimatedContent(
                                targetState = displayItem,
                                transitionSpec = {
                                    val oldIdx = queue.items.indexOfFirst { it.videoId == initialState.videoId }
                                    val newIdx = queue.items.indexOfFirst { it.videoId == targetState.videoId }
                                    val dir = if (newIdx >= oldIdx) 1 else -1
                                    (slideInHorizontally { fullWidth -> dir * fullWidth / 3 } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                                        togetherWith slideOutHorizontally { fullWidth -> -dir * fullWidth / 3 } + fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                                },
                                contentKey = { it.videoId },
                                label = "song-title",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) { animItem ->
                                Text(
                                    text = animItem.title.toSearchAwareTitle(queue.searchQuery),
                                    style = AppTypography.DetailTitle,
                                    maxLines = 1,
                                    color = onSurface,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                            // ── Row 1b: Artist name ──
                            AnimatedContent(
                                targetState = displayItem,
                                transitionSpec = {
                                    val oldIdx = queue.items.indexOfFirst { it.videoId == initialState.videoId }
                                    val newIdx = queue.items.indexOfFirst { it.videoId == targetState.videoId }
                                    val dir = if (newIdx >= oldIdx) 1 else -1
                                    (slideInHorizontally { fullWidth -> dir * fullWidth / 3 } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                                        togetherWith slideOutHorizontally { fullWidth -> -dir * fullWidth / 3 } + fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                                },
                                contentKey = { it.videoId },
                                label = "song-artist",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) { animItem ->
                                Text(
                                    text = animItem.artistName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = onSurface.copy(alpha = 0.72f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (animItem.artistId != null) {
                                        Modifier.clickable {
                                            onArtistTap(animItem.artistId, animItem.artistName, null)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                            }

                            Spacer(Modifier.height(28.dp))

                            // ── Row 1c: Horizontal scrollable option pills ──
                            val isCurrentLongFormPill = remember(item.durationText) {
                                val parts = item.durationText?.split(":")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
                                val seconds = when (parts.size) {
                                    2 -> parts[0] * 60 + parts[1]
                                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                                    else -> 0L
                                }
                                seconds > 900L
                            }
                            val crossfadeDisabledByLongFormPill = isCurrentLongFormPill
                            val effectiveCrossfadePill = crossfadeEnabled && !crossfadeDisabledByLongFormPill
                            val downloadStatesMapPill by libraryRepository.downloadStates.collectAsStateWithLifecycle()
                            val downloadStatePill = downloadStatesMapPill[item.videoId]
                            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Like pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(
                                                        if (mediaLibraryState.isLiked)
                                                            R.drawable.thumb_up_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                                                        else
                                                            R.drawable.thumb_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = "Like",
                                            isActive = mediaLibraryState.isLiked,
                                            isSuccess = false,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = playerControlColor,
                                            onClick = {
                                                coroutineScope.launch { libraryRepository.toggleLike(item) }
                                            }
                                        )
                                    }

                                    // Queue pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.QueueMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = "Queue",
                                            isActive = false,
                                            isSuccess = false,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = playerControlColor,
                                            onClick = {
                                                showQueueSheet = true
                                            }
                                        )
                                    }
                                    // Lyrics pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.lyrics_24px),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = "Lyrics",
                                            isActive = isLyricsOpen,
                                            isSuccess = false,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = playerControlColor,
                                            onClick = {
                                                if (isLyricsOpen) closeLyrics() else openLyrics()
                                            }
                                        )
                                    }
                                    // Share pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.share_24px),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = "Share",
                                            isActive = false,
                                            isSuccess = false,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = playerControlColor,
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
                                        )
                                    }
                                    // Download pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                if (mediaLibraryState.isDownloaded) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(R.drawable.download_24px),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            label = if (downloadStatePill?.isDownloading == true)
                                                "${(downloadStatePill.progress * 100).toInt()}%"
                                            else if (mediaLibraryState.isDownloaded) "Downloaded"
                                            else "Download",
                                            isActive = false,
                                            isSuccess = mediaLibraryState.isDownloaded,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = playerControlColor,
                                            onClick = {
                                                if (!mediaLibraryState.isDownloaded) {
                                                    coroutineScope.launch {
                                                        val result = libraryRepository.download(item)
                                                        if (result.isFailure) {
                                                            android.widget.Toast
                                                                .makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT)
                                                                .show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    // Crossfade pill
                                    item {
                                        PlayerOptionPill(
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = "Crossfade",
                                            isActive = effectiveCrossfadePill,
                                            isSuccess = false,
                                            activeColor = playbackAccent,
                                            onActiveColor = mediaPalette.onAccent,
                                            controlColor = if (crossfadeDisabledByLongFormPill) playerControlColor.copy(alpha = 0.38f) else playerControlColor,
                                            onClick = {
                                                if (!crossfadeDisabledByLongFormPill) onToggleCrossfade()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(32.dp))

                            // ── Row 2: Progress bar ──
                            ExpandedPlaybackProgressSection(
                                positionMsFlow = positionMsFlow,
                                durationMs = state.durationMs,
                                durationText = item.durationText,
                                crossfadeEnabled = crossfadeEnabled,
                                hasNextTrack = queue.currentIndex + 1 < queue.items.size,
                                isLoading = isCurrentLoading,
                                onSeek = onSeek,
                                onProgressBarInteractingChange = onProgressBarInteractingChange,
                                playbackAccent = playbackAccent,
                                onSurface = onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            // ── Row 3: Sleep Timer + Prev + Play/Pause + Next + Loop ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sleep timer with dot indicator
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp)
                                ) {
                                    HapticIconButton(
                                        onClick = { showSleepTimerSheet = true },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                            contentDescription = "Sleep timer",
                                            tint = if (sleepTimerMinutes != null) playbackAccent else playerControlColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    // Active dot
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (sleepTimerMinutes != null) playbackAccent
                                                else Color.Transparent
                                            )
                                    )
                                }

                                HapticIconButton(
                                    onClick = onSkipPrev,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = playerControlColor,
                                        modifier = Modifier.size(52.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticIconButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier.size(88.dp)
                                ) {
                                    Icon(
                                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = playerControlColor,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticIconButton(
                                    onClick = onSkipNext,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = playerControlColor,
                                        modifier = Modifier.size(52.dp)
                                    )
                                }

                                // Loop with dot indicator
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp)
                                ) {
                                    HapticIconButton(
                                        onClick = onToggleRepeat,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Repeat,
                                            contentDescription = "Loop",
                                            tint = if (isLooping) playbackAccent else playerControlColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    // Active dot
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isLooping) playbackAccent
                                                else Color.Transparent
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(32.dp))

                            // Related remains part of the same page as the controls.
                            RelatedContent(
                                state = relatedState,
                                onRetry = { onRelatedLoad(item.videoId) },
                                onSongTap = onRelatedSongTap,
                                onArtistTap = onArtistTap,
                                onAlbumTap = onAlbumTap,
                                onPlaylistTap = onPlaylistTap,
                                onDismiss = {},
                                nestedScrollConnection = null,
                                modifier = Modifier
                            )
                            Spacer(Modifier.height(24.dp))
                            Spacer(Modifier.height(48.dp))
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
                containerColor = mediaPalette.bottom
            )
        }

        if (showSleepTimerSheet) {
            val sleepTimerOptions = listOf(
                null to "Off",
                15 to "15 minutes",
                30 to "30 minutes",
                45 to "45 minutes",
                60 to "60 minutes"
            )
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = AppShapes.bottomSheet(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "Sleep timer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    )
                    Text(
                        text = "Pause playback automatically after a set time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    sleepTimerOptions.forEach { (minutes, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = null,
                                    tint = if (sleepTimerMinutes == minutes) playbackAccent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = if (sleepTimerMinutes == minutes) {
                                {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = playbackAccent
                                    )
                                }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                sleepTimerMinutes = minutes
                                showSleepTimerSheet = false
                            }
                        )
                    }
                }
            }
        }

        if (miniPlayerAlpha > 0.001f) {
            val miniProgress = playbackProgress(miniPositionMs, state.durationMs)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = miniPlayerAlpha }
                    .zIndex(4f)
                    .background(lerp(mediaPalette.bottom, Color.Black, 0.18f))
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HapticIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(miniProgress)
                            .fillMaxHeight()
                            .background(playbackAccent)
                    )
                }
            }
        }

        // ── Options bottom sheet ──
        if (showOptionsSheet) {
            val switchColors = SwitchDefaults.colors(
                checkedThumbColor = selectedControlOnAccent,
                checkedTrackColor = selectedControlAccent.copy(alpha = 0.56f),
                checkedBorderColor = selectedControlAccent,
                checkedIconColor = selectedControlOnAccent
            )
            val isCurrentLongForm = remember(item.durationText) {
                val parts = item.durationText?.split(":")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
                val seconds = when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> 0L
                }
                seconds > 900L
            }
            val downloadStatesMap by libraryRepository.downloadStates.collectAsStateWithLifecycle()
            val downloadState = downloadStatesMap[item.videoId]
            val optionsArtworkUrl = remember(item.thumbnailUrl) { upscaleThumbnail(item.thumbnailUrl) }
            val hasArtwork = !item.thumbnailUrl.isNullOrBlank()
            ModalBottomSheet(
                onDismissRequest = {
                    dismissOptionsAfterDownload = false
                    showOptionsSheet = false
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = AppShapes.bottomSheet(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasArtwork) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(optionsArtworkUrl)
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(AppShapes.thumbnailLarge())
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(AppShapes.thumbnailLarge())
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            val subtitle = buildString {
                                append(item.artistName)
                                item.albumName?.takeIf { it.isNotBlank() }?.let {
                                    append("  ·  ")
                                    append(it)
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    val crossfadeDisabledByLongForm = isCurrentLongForm
                    val effectiveCrossfade = crossfadeEnabled && !crossfadeDisabledByLongForm
                    val disabledAlpha = if (crossfadeDisabledByLongForm) 0.38f else 1f
                    ListItem(
                        headlineContent = {
                            Text(
                                "Crossfade",
                                color = LocalContentColor.current.copy(alpha = disabledAlpha)
                            )
                        },
                        supportingContent = {
                            Text(
                                if (crossfadeDisabledByLongForm) "Not available for long-form audio"
                                else "Blend songs seamlessly",
                                color = LocalContentColor.current.copy(alpha = disabledAlpha)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = effectiveCrossfade,
                                onCheckedChange = { if (!crossfadeDisabledByLongForm) onToggleCrossfade() },
                                enabled = !crossfadeDisabledByLongForm,
                                colors = switchColors
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = if (crossfadeDisabledByLongForm) Modifier else Modifier.clickable { onToggleCrossfade() }
                    )
                    val repeatSupportingText = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Off"
                    }
                    ListItem(
                        headlineContent = { Text("Loop song") },
                        supportingContent = { Text(repeatSupportingText) },
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
                    item.artistId?.let { artistId ->
                        ListItem(
                            headlineContent = { Text("View Artist") },
                            supportingContent = { Text("More from ${item.artistName}") },
                            leadingContent = {
                                if (hasArtwork) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(optionsArtworkUrl)
                                            .crossfade(true)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                showOptionsSheet = false
                                // Song page has track art, not canonical artist art.
                                // Pass null so ArtistScreen fetches proper artist image.
                                onArtistTap(artistId, item.artistName, null)
                            }
                        )
                    }
                    item.albumId?.let { albumId ->
                        ListItem(
                            headlineContent = { Text("View Album") },
                            supportingContent = { Text(item.albumName ?: "Open album") },
                            leadingContent = {
                                if (hasArtwork) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(optionsArtworkUrl)
                                            .crossfade(true)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                showOptionsSheet = false
                                onAlbumTap(albumId, item.albumName ?: "", null)
                            }
                        )
                    }
                    val downloadSupportingText = when {
                        downloadState?.isDownloading == true ->
                            "${(downloadState.progress * 100).toInt()}% complete"
                        mediaLibraryState.isDownloaded -> "Saved for offline"
                        else -> "Listen without a connection"
                    }
                    ListItem(
                        headlineContent = {
                            Text(if (mediaLibraryState.isDownloaded) "Downloaded" else "Download song")
                        },
                        supportingContent = { Text(downloadSupportingText) },
                        leadingContent = {
                            if (mediaLibraryState.isDownloaded) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = primary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.download_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            if (mediaLibraryState.isDownloaded) {
                                dismissOptionsAfterDownload = true
                                return@clickable
                            }
                            coroutineScope.launch {
                                val result = libraryRepository.download(item)
                                if (result.isSuccess) {
                                    dismissOptionsAfterDownload = true
                                } else {
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            "Download failed",
                                            android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Share song") },
                        supportingContent = { Text("Send a link to this song") },
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
        // Use `bottom` so the mini bar matches the other solid surfaces in the
        // playback UI (lyrics screen, queue sheet, floating controls) rather than
        // matching the top of the expanded gradient. The mini bar fades out by
        // ~25% expand progress (see miniContentAlpha), so any momentary mismatch
        // with the gradient's top edge during the transition is masked by the
        // crossfade.
        val miniContainerColor = mediaPalette.bottom
        val miniTitleColor = mediaPalette.title
        val miniSubtitleColor = mediaPalette.body
        MiniPlayerBar(
            positionMsFlow = positionMsFlow,
            durationMs = state.durationMs,
            item = item,
            artworkUrl = artworkUrl,
            isPlaying = state.isPlaying,
            isLoading = isCurrentLoading,
            isExpanded = isExpanded,
            miniPlayerHeight = miniPlayerHeight,
            miniContentAlpha = miniContentAlpha,
            clampedExpandProgress = clampedExpandProgress,
            miniContainerColor = miniContainerColor,
            miniTitleColor = miniTitleColor,
            miniSubtitleColor = miniSubtitleColor,
            onExpand = onExpand,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrev = onSkipPrev,
            // Local-only queues wrap at both ends — mini-player swipe must mirror that.
            canSkipNext = (queue.source == QueueSource.PLAYED || queue.source == QueueSource.DOWNLOADED) && queue.items.size > 1 ||
                queue.currentIndex < queue.items.lastIndex,
            canSkipPrevious = (queue.source == QueueSource.PLAYED || queue.source == QueueSource.DOWNLOADED) && queue.items.size > 1 ||
                queue.currentIndex > 0,
            queue = queue
        )

        PlayerOverlayBottomSheet(
            visible = isLyricsOpen,
            onDismiss = ::closeLyricsScreen,
            containerColor = mediaPalette.bottom,
            swipeEnabled = !lyricsShareMode
        ) { nestedScrollConnection ->
                LyricsPanel(
                    positionMsFlow = positionMsFlow,
                    lyricsStateFlow = lyricsStateFlow,
                    lyricsProviderStatesFlow = lyricsProviderStatesFlow,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    onClose = ::closeLyricsScreen,
                    shareItem = item,
                    shareArtworkUrl = artworkUrl,
                    shareUrl = "https://music.youtube.com/watch?v=${item.videoId}",
                    shareMode = lyricsShareMode,
                    onShareModeChange = { active ->
                        if (active && lyricCandidates.isEmpty()) {
                            Toast
                                .makeText(context, "Lyrics are still loading", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            lyricsShareMode = active
                        }
                    },
                    onSeek = onSeek,
                    onSkipPrev = onSkipPrev,
                    onSkipNext = onSkipNext,
                    onPlayPause = onPlayPause,
                    onLoadAllProviders = { onLyricsLoadAllProviders(item) },
                    onSwitchProvider = onLyricsSwitchProvider,
                    backgroundColor = mediaPalette.bottom,
                    playbackAccent = playbackAccent,
                    playbackButton = playbackAccent,
                    songPaletteBackground = mediaPalette.bottom,
                    songPaletteTextColor = mediaPalette.accent,
                    onProgressBarInteractingChange = onProgressBarInteractingChange,
                    nestedScrollConnection = nestedScrollConnection,
                    modifier = Modifier
                        .fillMaxSize()
                )
        }
    }
}

@Composable
private fun PlayerOverlayBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    containerColor: Color,
    swipeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (NestedScrollConnection) -> Unit
) {
    if (!visible) return

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(4f)
    ) {
        val density = LocalDensity.current
        val sheetHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        var sheetOffsetY by remember(sheetHeightPx) { mutableFloatStateOf(sheetHeightPx) }
        var isSettling by remember { mutableStateOf(false) }

        fun dragSheetBy(deltaY: Float): Float {
            val previous = sheetOffsetY
            sheetOffsetY = (sheetOffsetY + deltaY).coerceIn(0f, sheetHeightPx)
            return sheetOffsetY - previous
        }

        suspend fun animateSheetTo(targetOffsetY: Float) {
            isSettling = true
            val target = targetOffsetY.coerceIn(0f, sheetHeightPx)
            val animation = Animatable(sheetOffsetY)
            animation.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) {
                sheetOffsetY = value
            }
            sheetOffsetY = target
            isSettling = false
        }

        suspend fun settleSheet(velocityY: Float = 0f) {
            val shouldDismiss =
                velocityY > PlayerOverlayDismissVelocityPx ||
                    sheetOffsetY > sheetHeightPx * PlayerOverlayDismissFraction
            if (shouldDismiss) {
                animateSheetTo(sheetHeightPx)
                currentOnDismiss()
            } else {
                animateSheetTo(0f)
            }
        }

        LaunchedEffect(sheetHeightPx) {
            sheetOffsetY = sheetHeightPx
            animateSheetTo(0f)
        }

        BackHandler(enabled = !isSettling) {
            coroutineScope.launch {
                animateSheetTo(sheetHeightPx)
                currentOnDismiss()
            }
        }

        val swipeEnabledState = rememberUpdatedState(swipeEnabled)
        val nestedScrollConnection = remember(sheetHeightPx) {
            object : NestedScrollConnection {
                private var topReached = false

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (!swipeEnabledState.value) return Offset.Zero
                    if (source != NestedScrollSource.UserInput || isSettling) return Offset.Zero

                    if (available.y < 0f) {
                        topReached = false
                    }

                    if (available.y < 0f && sheetOffsetY > 0f) {
                        return Offset(x = 0f, y = dragSheetBy(available.y))
                    }

                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (!swipeEnabledState.value) return Offset.Zero
                    if (source != NestedScrollSource.UserInput || isSettling) return Offset.Zero

                    if (!topReached) {
                        topReached = consumed.y == 0f && available.y > 0f
                    }

                    return if (topReached && available.y > 0f) {
                        Offset(x = 0f, y = dragSheetBy(available.y))
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!swipeEnabledState.value) return Velocity.Zero
                    return if (topReached && available.y > 0f) {
                        settleSheet(available.y)
                        available
                    } else {
                        Velocity.Zero
                    }
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    if (!swipeEnabledState.value) return Velocity.Zero
                    if (topReached && sheetOffsetY > 0f) {
                        settleSheet(available.y)
                    }
                    topReached = false
                    return Velocity.Zero
                }
            }
        }

        val sheetCorner = if (sheetOffsetY <= 1f) 0.dp else 28.dp
        val sheetShape = RoundedCornerShape(topStart = sheetCorner, topEnd = sheetCorner)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = sheetOffsetY }
                .clip(sheetShape)
                .background(containerColor)
                .pointerInput(sheetHeightPx, isSettling, swipeEnabled) {
                    if (isSettling || !swipeEnabled) return@pointerInput

                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                            coroutineScope.launch { settleSheet() }
                        },
                        onDragEnd = {
                            val velocityY = velocityTracker.calculateVelocity().y
                            velocityTracker.resetTracking()
                            coroutineScope.launch { settleSheet(velocityY) }
                        },
                        onDrag = { change, dragAmount ->
                            if (change.isConsumed) return@detectDragGestures
                            velocityTracker.addPointerInputChange(change)
                            val consumedY = dragSheetBy(dragAmount.y)
                            if (consumedY != 0f) {
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            content(nestedScrollConnection)
        }
    }
}

// ── Option pill with three visual states: active, success, normal ──
@Composable
private fun PlayerOptionPill(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    isSuccess: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    controlColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isSuccess -> activeColor.copy(alpha = 0.18f)
            else -> controlColor.copy(alpha = 0.10f)
        },
        animationSpec = tween(220),
        label = "pill-bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isActive -> onActiveColor
            isSuccess -> activeColor
            else -> controlColor
        },
        animationSpec = tween(220),
        label = "pill-content"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> Color.Transparent
            isSuccess -> activeColor.copy(alpha = 0.45f)
            else -> controlColor.copy(alpha = 0.28f)
        },
        animationSpec = tween(220),
        label = "pill-border"
    )

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(50))
            .pressScale(remember { MutableInteractionSource() })
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}


// ── Peeking queue overlay: starts at peek height, slides up to reveal full queue ──
// This lives in the outer player Box (zIndex 3) so it is a true overlay,
// not constrained inside the content Column.
@Composable
private fun BoxScope.PeekingQueueOverlay(
    queue: PlaybackQueue,
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    relatedStateFlow: StateFlow<RelatedState>,
    onRelatedLoad: (String) -> Unit,
    onRelatedSongTap: (MediaItem) -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    crossfadeEnabled: Boolean,
    containerColor: Color,
    controlColor: Color,
    globalAlpha: Float
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // How tall just the handle strip is (handle bar + "Up Next" label)
    val peekHeightDp = 64.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.BottomCenter)
            .zIndex(3f)
            .graphicsLayer { alpha = globalAlpha }
    ) {
        val fullHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val peekHeightPx = with(density) { peekHeightDp.toPx() }
        // sheetOffsetY == 0 → fully open (sheet fills whole parent)
        // sheetOffsetY == fullHeightPx - peekHeightPx → only peek strip visible
        val peekOffsetY = remember(fullHeightPx, peekHeightPx) {
            Animatable(fullHeightPx - peekHeightPx)
        }
        var isSettling by remember { mutableStateOf(false) }
        // Whether the sheet is fully open (past ¼ of full height)
        val isOpen by remember { derivedStateOf { peekOffsetY.value < fullHeightPx * 0.75f } }

        fun dragSheetBy(deltaY: Float): Float {
            val previous = peekOffsetY.value
            val next = (peekOffsetY.value + deltaY).coerceIn(0f, fullHeightPx - peekHeightPx)
            coroutineScope.launch { peekOffsetY.snapTo(next) }
            return next - previous
        }

        suspend fun settleSheet(velocityY: Float = 0f) {
            isSettling = true
            val expandThreshold = fullHeightPx * 0.55f
            // Positive velocityY = swipe down → close; negative = swipe up → open
            val shouldOpen = velocityY < -600f || (velocityY > -600f && velocityY < 600f && peekOffsetY.value < expandThreshold)
            val target = if (shouldOpen) 0f else fullHeightPx - peekHeightPx
            peekOffsetY.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            isSettling = false
        }

        BackHandler(enabled = isOpen && !isSettling) {
            coroutineScope.launch { settleSheet(800f) }
        }

        // Nested scroll so the queue LazyColumn can hand off overscroll back to the sheet
        val nestedScrollConnection = remember(fullHeightPx, peekHeightPx) {
            object : NestedScrollConnection {
                private var topReached = false

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || isSettling) return Offset.Zero
                    if (available.y < 0f) topReached = false
                    // Scrolling up while sheet is not fully open → move sheet up
                    if (available.y < 0f && peekOffsetY.value > 0f) {
                        val consumed = dragSheetBy(available.y)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || isSettling) return Offset.Zero
                    if (!topReached) topReached = consumed.y == 0f && available.y > 0f
                    return if (topReached && available.y > 0f) {
                        val c = dragSheetBy(available.y)
                        Offset(0f, c)
                    } else Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return if (topReached && available.y > 0f) {
                        settleSheet(available.y)
                        available
                    } else Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (topReached && peekOffsetY.value > 0f) settleSheet(available.y)
                    topReached = false
                    return Velocity.Zero
                }
            }
        }

        val sheetCorner by animateDpAsState(
            targetValue = if (peekOffsetY.value <= 1f) 0.dp else 22.dp,
            label = "sheet-corner"
        )
        val sheetShape = RoundedCornerShape(topStart = sheetCorner, topEnd = sheetCorner)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = peekOffsetY.value }
                .clip(sheetShape)
                .background(containerColor)
                .pointerInput(fullHeightPx, peekHeightPx, isSettling) {
                    if (isSettling) return@pointerInput
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                            coroutineScope.launch { settleSheet() }
                        },
                        onDragEnd = {
                            val vy = velocityTracker.calculateVelocity().y
                            velocityTracker.resetTracking()
                            coroutineScope.launch { settleSheet(vy) }
                        },
                        onDrag = { change, dragAmount ->
                            if (change.isConsumed) return@detectDragGestures
                            velocityTracker.addPointerInputChange(change)
                            val next = (peekOffsetY.value + dragAmount.y)
                                .coerceIn(0f, fullHeightPx - peekHeightPx)
                            coroutineScope.launch { peekOffsetY.snapTo(next) }
                            change.consume()
                        }
                    )
                }
        ) {
            // Content area: queue sheet (only meaningful when open)
            val backdropPalette = LocalPlaybackBackdropPalette.current
            val headerColor = backdropPalette?.title ?: Color.White
            val relatedState by relatedStateFlow.collectAsStateWithLifecycle()
            val currentVideoId = queue.items.getOrNull(queue.currentIndex)?.videoId
            var selectedTab by remember { mutableIntStateOf(0) }
            LaunchedEffect(currentVideoId) { currentVideoId?.let(onRelatedLoad) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedTab) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onHorizontalDrag = { change, dx ->
                                totalDragX += dx
                                change.consume()
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDragX) > 72f) {
                                    selectedTab = if (totalDragX < 0f) 1 else 0
                                }
                            }
                        )
                    }
            ) {
                // Handle strip — always visible, tapping it toggles the sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekHeightDp)
                        .hapticClickable {
                            coroutineScope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                settleSheet(if (isOpen) 800f else -800f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Drag handle bar
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(controlColor.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = controlColor.copy(alpha = 0.6f)
                        )
                    }
                }

                // Full queue content below the handle strip
                val crossfadeLock = showCrossfadeCue(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    crossfadeEnabled = crossfadeEnabled,
                    hasNextTrack = queue.currentIndex + 1 < queue.items.size
                )
                RelatedTabsHeader(
                    selectedTab = selectedTab,
                    onSelectedTab = { selectedTab = it },
                    onDismiss = { coroutineScope.launch { settleSheet(800f) } },
                    headerColor = headerColor,
                    headerVariantColor = headerColor.copy(alpha = 0.74f),
                    selectedColor = LocalPlaybackBackdropPalette.current?.accent ?: headerColor
                )
                when (selectedTab) {
                    0 -> QueueContent(
                        queue = queue,
                        onSkipToIndex = onSkipToIndex,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onMoveInQueue = onMoveInQueue,
                        onArtistTap = onArtistTap,
                        onAlbumTap = onAlbumTap,
                        crossfadeEnabled = crossfadeEnabled,
                        crossfadeLockActive = crossfadeLock,
                        nestedScrollConnection = nestedScrollConnection,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> RelatedContent(
                        state = relatedState,
                        onRetry = { currentVideoId?.let(onRelatedLoad) },
                        onSongTap = onRelatedSongTap,
                        onArtistTap = onArtistTap,
                        onAlbumTap = onAlbumTap,
                        onPlaylistTap = onPlaylistTap,
                        onDismiss = { coroutineScope.launch { settleSheet(800f) } },
                        nestedScrollConnection = nestedScrollConnection,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
    isLoading: Boolean,
    onSeek: (Long) -> Unit,
    onProgressBarInteractingChange: (Boolean) -> Unit,
    playbackAccent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val playbackProgress = playbackProgress(positionMs, durationMs)

    Column(modifier = modifier) {
        if (isLoading) {
            LoadingPlaybackBar(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.PlayerProgress.copy(alpha = 0.55f),
                trackColor = AppColors.PlayerTrack.copy(alpha = 0.55f),
                height = 12.dp
            )
        } else {
            SeekablePlaybackBar(
                progress = playbackProgress,
                durationMs = durationMs,
                onSeek = onSeek,
                onInteractingChange = onProgressBarInteractingChange,
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.PlayerProgress,
                trackColor = AppColors.PlayerTrack,
                trackHeight = 6.dp,
                expandedTrackHeight = 12.dp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isLoading) "--:--" else formatMs(positionMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = onSurface.copy(alpha = if (isLoading) 0.5f else 0.85f)
                )
                Text(
                    text = when {
                        isLoading -> durationText ?: "--:--"
                        durationMs > 0 -> formatMs(durationMs)
                        else -> durationText ?: ""
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = onSurface.copy(alpha = if (isLoading) 0.5f else 0.85f)
                )
            }
            CrossfadeCountdownCue(
                visible = !isLoading && showCrossfadeCue(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    crossfadeEnabled = crossfadeEnabled,
                    hasNextTrack = hasNextTrack
                ),
                accentColor = playbackAccent,
                modifier = Modifier.align(Alignment.Center)
            )
        }
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
    crossfadeEnabled: Boolean = false,
    containerColor: Color
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
        containerColor = containerColor,
        crossfadeLockActive = showCrossfadeCue(
            positionMs = positionMs,
            durationMs = durationMs,
            crossfadeEnabled = crossfadeEnabled,
            hasNextTrack = queue.currentIndex + 1 < queue.items.size
        )
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.MiniPlayerBar(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    item: MediaItem,
    artworkUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isExpanded: Boolean,
    miniPlayerHeight: Dp,
    miniContentAlpha: Float,
    clampedExpandProgress: Float,
    miniContainerColor: Color,
    miniTitleColor: Color,
    miniSubtitleColor: Color,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    queue: PlaybackQueue
) {
    val positionMs = rememberMiniPlayerPositionMs(
        positionMsFlow = positionMsFlow,
        isExpanded = isExpanded
    )
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
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = miniTitleColor.copy(alpha = 0.55f),
                                trackColor = miniTitleColor.copy(alpha = 0.14f)
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = miniTitleColor.copy(alpha = 0.92f),
                                trackColor = miniTitleColor.copy(alpha = 0.18f),
                                strokeWidth = 3.dp
                            )
                        }
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.width(14.dp))
                    AnimatedContent(
                        targetState = item,
                        transitionSpec = {
                            val oldIdx = queue.items.indexOfFirst { it.videoId == initialState.videoId }
                            val newIdx = queue.items.indexOfFirst { it.videoId == targetState.videoId }
                            val dir = if (newIdx >= oldIdx) 1 else -1
                            (slideInHorizontally { fullWidth -> dir * fullWidth / 3 } + fadeIn(tween(200))
                                togetherWith slideOutHorizontally { fullWidth -> -dir * fullWidth / 3 } + fadeOut(tween(150)))
                        },
                        contentKey = { it.videoId },
                        label = "mini-info"
                    ) { animItem ->
                        Column {
                            Text(
                                text = animItem.title.toCleanSongTitle(),
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

@Composable
private fun rememberMiniPlayerPositionMs(
    positionMsFlow: StateFlow<Long>,
    isExpanded: Boolean
): Long {
    var positionMs by remember(positionMsFlow) { mutableLongStateOf(positionMsFlow.value) }

    LaunchedEffect(positionMsFlow, isExpanded) {
        // Keep mini progress synchronized immediately on state transitions.
        positionMs = positionMsFlow.value
        if (!isExpanded) {
            positionMsFlow.collectLatest { latest ->
                positionMs = latest
            }
        }
    }

    return positionMs
}

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
    crossfadeLockActive: Boolean = false,
    containerColor: Color
) {
    val backdropPalette = LocalPlaybackBackdropPalette.current
    val headerColor = backdropPalette?.title ?: Color.White
    val headerVariantColor = backdropPalette?.body ?: Color.White.copy(alpha = 0.74f)

    PlayerOverlayBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        containerColor = containerColor
    ) { nestedScrollConnection ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            QueueSheetHeader(
                onDismiss = onDismiss,
                headerColor = headerColor,
                headerVariantColor = headerVariantColor
            )
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
                nestedScrollConnection = nestedScrollConnection,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun QueueSheetHeader(
    onDismiss: () -> Unit,
    headerColor: Color,
    headerVariantColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Upcoming",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = headerColor
        )
        Spacer(Modifier.weight(1f))
        HapticIconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close Upcoming",
                tint = headerVariantColor
            )
        }
    }
}

@Composable
private fun RelatedTabsHeader(
    selectedTab: Int,
    onSelectedTab: (Int) -> Unit,
    onDismiss: () -> Unit,
    headerColor: Color,
    headerVariantColor: Color,
    selectedColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            HapticIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Up Next",
                    tint = headerVariantColor
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = headerColor.copy(alpha = 0.10f),
            tonalElevation = 1.dp
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                listOf("Up Next", "Related").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val textColor = if (isSelected) {
                        if (selectedColor.luminance() > 0.5f) Color.Black else Color.White
                    } else {
                        headerVariantColor
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) selectedColor.copy(alpha = 0.88f) else Color.Transparent
                            )
                            .hapticClickable(onClick = { onSelectedTab(index) })
                            .padding(vertical = 13.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// Slot↔real index mapping for the circular AlbumArtPager. Circular queues (PLAYED, size > 1)
// render two ghost slots that mirror the wrap targets:
//   slot 0          → items[size - 1]  (leading ghost; shown when user drags past first)
//   slot 1..size    → items[0..size-1] (real items)
//   slot size + 1   → items[0]         (trailing ghost; shown when user drags past last)
// When a drag lands on a ghost, the gesture handler emits onSkipToIndex(realIndex) and a
// follow-up scroll silently retargets the twin real slot. The ghost and twin show the same
// image, so the jump is imperceptible.
private fun realFromSlot(slot: Int, size: Int, circular: Boolean): Int = when {
    size == 0 -> 0
    !circular -> slot.coerceIn(0, size - 1)
    slot <= 0 -> size - 1
    slot >= size + 1 -> 0
    else -> (slot - 1).coerceIn(0, size - 1)
}

private fun slotFromReal(real: Int, circular: Boolean): Int = if (circular) real + 1 else real

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumArtPager(
    queue: PlaybackQueue,
    onSkipToIndex: (Int) -> Unit,
    onDisplayIndexChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp
) {
    val currentQueueIndex = queue.currentIndex
    val queueItems = queue.items
    val context = LocalContext.current
    val updatedCurrentIndex by rememberUpdatedState(currentQueueIndex)
    val updatedQueueItems by rememberUpdatedState(queueItems)
    val isCircular = (queue.source == QueueSource.PLAYED || queue.source == QueueSource.DOWNLOADED) && queueItems.size > 1
    val updatedIsCircular by rememberUpdatedState(isCircular)
    val pagerItemCount = if (isCircular) queueItems.size + 2 else queueItems.size

    // Pre-fetch and decode images for currentIndex ± 2 into Coil memory cache.
    // Both the prefetch and the display AsyncImage use Size.ORIGINAL so their
    // cache keys match — the AsyncImage gets an instant memory-cache hit with
    // the bitmap already decoded, skipping the Loading→Success state transition
    // that was causing ContentScale.Crop to recalculate (the stretch).
    LaunchedEffect(currentQueueIndex) {
        val loader = coil3.SingletonImageLoader.get(context)
        val windowStart = (currentQueueIndex - 2).coerceAtLeast(0)
        val windowEnd = (currentQueueIndex + 2).coerceAtMost(queueItems.size - 1)
        for (i in windowStart..windowEnd) {
            launch {
                val url = upscaleThumbnail(queueItems[i].thumbnailUrl) ?: return@launch
                runCatching {
                    loader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(coil3.size.Size.ORIGINAL)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = slotFromReal(currentQueueIndex, isCircular)
            .coerceIn(0, (pagerItemCount - 1).coerceAtLeast(0))
    )
    val isDragged by gridState.interactionSource.collectIsDraggedAsState()
    var userGestureActive by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) userGestureActive = true
    }

    val snapProvider = remember(gridState) {
        carouselSnapLayoutInfoProvider(
            lazyGridState = gridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize / 2f - itemSize / 2f)
            }
        )
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (slot, offset) ->
                val currentItems = updatedQueueItems
                val size = currentItems.size
                if (size == 0) return@collect
                val circular = updatedIsCircular
                val viewportWidth = gridState.layoutInfo.viewportSize.width
                val dominantSlot = if (viewportWidth > 0 && offset > viewportWidth / 2) slot + 1 else slot
                val dominantReal = realFromSlot(dominantSlot, size, circular)
                if (dominantReal in currentItems.indices) {
                    onDisplayIndexChanged(dominantReal)
                }
                if (offset == 0 && userGestureActive) {
                    val realIndex = realFromSlot(slot, size, circular)
                    if (realIndex != updatedCurrentIndex && realIndex in currentItems.indices) {
                        onSkipToIndex(realIndex)
                    }
                }
                if (offset == 0) userGestureActive = false
            }
    }

    LaunchedEffect(currentQueueIndex, isCircular, queueItems.size) {
        if (currentQueueIndex !in queueItems.indices) return@LaunchedEffect
        val targetSlot = slotFromReal(currentQueueIndex, isCircular)
        if (gridState.firstVisibleItemIndex != targetSlot) {
            gridState.scrollToItem(targetSlot)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val itemWidth = maxWidth

        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            LazyHorizontalGrid(
                state = gridState,
                rows = GridCells.Fixed(1),
                flingBehavior = rememberSnapFlingBehavior(snapProvider),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = pagerItemCount,
                    key = { slot ->
                        val real = realFromSlot(slot, queueItems.size, isCircular)
                        val base = queueItems.getOrNull(real)?.videoId
                        when {
                            base == null -> "empty-$slot"
                            isCircular && slot == 0 -> "wrap-leading-$base"
                            isCircular && slot == queueItems.size + 1 -> "wrap-trailing-$base"
                            else -> base
                        }
                    }
                ) { slot ->
                    val real = realFromSlot(slot, queueItems.size, isCircular)
                    val mediaItem = queueItems[real]

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(upscaleThumbnail(mediaItem.thumbnailUrl))
                                .size(coil3.size.Size.ORIGINAL)
                                .crossfade(false)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = mediaItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(cornerRadius))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun carouselSnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 500f,
): SnapLayoutInfoProvider = object : SnapLayoutInfoProvider {
    private val layoutInfo: LazyGridLayoutInfo
        get() = lazyGridState.layoutInfo

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        val bounds = calculateSnappingOffsetBounds()
        if (abs(velocity) < velocityThreshold) {
            return if (abs(bounds.start) < abs(bounds.endInclusive)) {
                bounds.start
            } else {
                bounds.endInclusive
            }
        }
        return when {
            velocity < 0 -> bounds.start
            velocity > 0 -> bounds.endInclusive
            else -> 0f
        }
    }

    private fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val containerSize = layoutInfo.let {
                (if (it.orientation == androidx.compose.foundation.gestures.Orientation.Vertical)
                    it.viewportSize.height else it.viewportSize.width) -
                        it.beforeContentPadding - it.afterContentPadding
            }
            val desiredPosition = positionInLayout(containerSize.toFloat(), item.size.width.toFloat())
            val offset = item.offset.x.toFloat() - desiredPosition

            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }
            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }
        return lowerBoundOffset.rangeTo(upperBoundOffset)
    }
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
    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cueAlpha > 0f) {
            val labelColor = AppColors.PlayerLabelDim.copy(alpha = (0.72f * cueAlpha).coerceIn(0f, 1f))
            val shimmerTransition = rememberInfiniteTransition(label = "crossfadeShimmer")
            val shimmerProgress by shimmerTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "crossfadeShimmerProgress"
            )
            val shimmerTextBrush = remember(shimmerProgress, labelColor) {
                val startX = -96f + (260f * shimmerProgress)
                val endX = startX + 120f
                Brush.linearGradient(
                    colors = listOf(
                        labelColor.copy(alpha = 0.48f),
                        Color.White.copy(alpha = (0.92f * cueAlpha).coerceIn(0f, 1f)),
                        labelColor.copy(alpha = 0.48f)
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, 18f)
                )
            }
            val iconShimmerT = (1f - abs(shimmerProgress * 2f - 1f)).coerceIn(0f, 1f)
            val iconTint = lerp(
                labelColor,
                Color.White.copy(alpha = cueAlpha),
                iconShimmerT * 0.35f
            )

            Row(
                modifier = Modifier.graphicsLayer { alpha = cueAlpha },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Crossfade",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge.copy(brush = shimmerTextBrush)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LyricsPanel(
    positionMsFlow: StateFlow<Long>,
    lyricsStateFlow: StateFlow<LyricsState>,
    lyricsProviderStatesFlow: StateFlow<Map<String, ProviderLoadState>>,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    shareItem: MediaItem,
    shareArtworkUrl: String?,
    shareUrl: String,
    shareMode: Boolean,
    onShareModeChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayPause: () -> Unit,
    onLoadAllProviders: () -> Unit,
    onSwitchProvider: (String) -> Unit,
    onProgressBarInteractingChange: (Boolean) -> Unit,
    backgroundColor: Color,
    playbackAccent: Color,
    playbackButton: Color,
    songPaletteBackground: Color,
    songPaletteTextColor: Color,
    nestedScrollConnection: NestedScrollConnection? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val wordSyncEnabled by AppConfig.wordSyncLyrics.collectAsStateWithLifecycle()
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    // Project position forward from the last poll using the monotonic clock, so word
    // emphasis tracks real audio at frame rate instead of trailing behind the 300 ms
    // poll cadence. Held as a State<Long> (not a delegated Long) because the
    // derivedStateOf below needs to observe the underlying State directly — a local
    // `val` captured by the remember'd lambda would freeze at its first value.
    val syncPositionState = produceState(initialValue = positionMs, positionMs, isPlaying) {
        if (!isPlaying) {
            value = positionMs
            return@produceState
        }
        val anchorPos = positionMs
        val anchorClock = SystemClock.elapsedRealtime()
        while (true) {
            withFrameNanos { }
            value = anchorPos + (SystemClock.elapsedRealtime() - anchorClock)
        }
    }
    val syncPositionMs = syncPositionState.value
    val lyricsState by lyricsStateFlow.collectAsStateWithLifecycle()
    val playbackProgress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val listState = rememberLazyListState()

    val loadedLines = (lyricsState as? LyricsState.Loaded)?.lines
    val canEnterShareMode = loadedLines?.any { it.text.isNotBlank() } == true

    // Share-mode state (resets per song)
    var selectedIndices by remember(shareItem.videoId) { mutableStateOf<Set<Int>>(emptySet()) }
    // Combo index 0 is reserved for the song-derived palette, so it tracks the
    // current artwork even when the user keeps the default selected across tracks.
    var selectedComboIndex by remember(shareItem.videoId) { mutableIntStateOf(0) }
    val shareCombos = remember(songPaletteBackground, songPaletteTextColor) {
        buildList {
            add(LyricsShareColorCombo("Song", songPaletteBackground, songPaletteTextColor))
            addAll(LyricsShareStaticCombos)
        }
    }
    val selectedCombo = shareCombos.getOrElse(selectedComboIndex) { shareCombos.first() }
    val selectedBackground = selectedCombo.background
    val selectedTextColor = selectedCombo.textColor
    var isGenerating by remember(shareItem.videoId) { mutableStateOf(false) }
    var showCustomizeSheet by remember(shareItem.videoId) { mutableStateOf(false) }
    var dragAnchorIndex by remember(shareItem.videoId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(shareMode) {
        if (!shareMode) {
            selectedIndices = emptySet()
            showCustomizeSheet = false
            dragAnchorIndex = null
        }
    }

    BackHandler(enabled = showCustomizeSheet) {
        showCustomizeSheet = false
    }
    BackHandler(enabled = shareMode && !showCustomizeSheet) {
        if (selectedIndices.isNotEmpty()) {
            selectedIndices = emptySet()
            dragAnchorIndex = null
        } else {
            onShareModeChange(false)
        }
    }

    val selectedLyrics = remember(selectedIndices, loadedLines) {
        val lines = loadedLines ?: return@remember emptyList()
        selectedIndices
            .sorted()
            .mapNotNull { idx ->
            lines.getOrNull(idx)?.text?.trim()?.takeIf { it.isNotBlank() }
            }
            .take(MAX_LYRICS_SHARE_LINES)
    }

    val syncedLines = (lyricsState as? LyricsState.Loaded)?.takeIf { it.isSynced }?.lines
    // Reads syncPositionState.value INSIDE the derivedStateOf block — that's what
    // registers it with the snapshot system so the lambda re-evaluates each frame the
    // position changes. Reading the parent's captured `syncPositionMs` Long would
    // freeze the active line at index 0 (a remember'd lambda only ever sees the
    // locals captured the first time it ran).
    //
    // The 100 ms lookahead biases the transition slightly ahead of the line's
    // timestamp, matching the Apple-Music-style "anticipate the next line" feel.
    val currentLineIndex by remember(syncedLines) {
        derivedStateOf {
            syncedLines?.let { lines ->
                val pos = syncPositionState.value + LINE_LOOKAHEAD_MS
                var lo = 0
                var hi = lines.size - 1
                var result = -1
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (lines[mid].timeMs <= pos) {
                        result = mid
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                result.coerceAtLeast(0)
            } ?: 0
        }
    }

    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress, shareMode) {
        if (shareMode) return@LaunchedEffect
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            coroutineScope.launch {
                kotlinx.coroutines.delay(5000)
                userScrolled = false
            }
        }
    }
    LaunchedEffect(currentLineIndex, shareMode) {
        if (!shareMode && !userScrolled && syncedLines != null && syncedLines.isNotEmpty()) {
            val target = (currentLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(index = target)
        }
    }

    val providerStates by lyricsProviderStatesFlow.collectAsStateWithLifecycle()
    val activeProvider = (lyricsState as? LyricsState.Loaded)?.provider

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ── Header ──
        // Share mode keeps the original centered "Drag to select" layout. The default
        // mode now uses a Row with "Lyrics" anchored left and the provider switcher
        // anchored right, with a centered drag handle bar above for visual hint.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(Color.White.copy(alpha = 0.42f))
            )
            Spacer(Modifier.height(10.dp))
            if (shareMode) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HapticIconButton(
                        onClick = { onShareModeChange(false) },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel selection",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Drag to select · ${selectedLyrics.size}/$MAX_LYRICS_SHARE_LINES",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lyrics",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (providerStates.isNotEmpty()) {
                        LyricsProviderDropdown(
                            providers = providerStates,
                            activeProvider = activeProvider,
                            accentColor = playbackAccent,
                            onMenuOpen = onLoadAllProviders,
                            onSelect = onSwitchProvider
                        )
                    }
                }
            }
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
                    val selectableLineIndices = remember(ls.lines) {
                        ls.lines.mapIndexedNotNull { index, lyricLine ->
                            index.takeIf { lyricLine.text.isNotBlank() }
                        }
                    }
                    val syncGlowTransition = rememberInfiniteTransition(label = "lyricsSyncGlow")
                    val syncGlowPulse by syncGlowTransition.animateFloat(
                        initialValue = 0.78f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "lyricsSyncGlowPulse"
                    )
                    val syncedMutedAlpha = 0.42f
                    val shareSelectionModifier = if (shareMode && selectableLineIndices.isNotEmpty()) {
                        Modifier.pointerInput(ls.lines, selectableLineIndices, listState) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val touchedIndex =
                                        findLyricLineIndexAtOffsetY(listState, offset.y) ?: return@detectDragGesturesAfterLongPress
                                    val anchor =
                                        nearestSelectableLyricIndex(touchedIndex, selectableLineIndices)
                                            ?: return@detectDragGesturesAfterLongPress
                                    dragAnchorIndex = anchor
                                    selectedIndices = setOf(anchor)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragEnd = {
                                    dragAnchorIndex = null
                                },
                                onDragCancel = {
                                    dragAnchorIndex = null
                                }
                            ) { change, _ ->
                                val anchor = dragAnchorIndex ?: return@detectDragGesturesAfterLongPress
                                val touchedIndex = findLyricLineIndexAtOffsetY(
                                    listState = listState,
                                    offsetY = change.position.y
                                )
                                if (touchedIndex != null) {
                                    val currentIndex =
                                        nearestSelectableLyricIndex(touchedIndex, selectableLineIndices)
                                            ?: return@detectDragGesturesAfterLongPress
                                    val nextSelection = buildConsecutiveLyricSelection(
                                        anchorLineIndex = anchor,
                                        currentLineIndex = currentIndex,
                                        selectableLineIndices = selectableLineIndices,
                                        maxLines = MAX_LYRICS_SHARE_LINES
                                    )
                                    if (nextSelection != selectedIndices) {
                                        selectedIndices = nextSelection
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                val edgeThresholdPx = 56.dp.toPx()
                                val dragY = change.position.y
                                val viewportHeight = size.height.toFloat()
                                when {
                                    dragY < edgeThresholdPx -> {
                                        coroutineScope.launch {
                                            listState.scrollBy(
                                                ((dragY - edgeThresholdPx) / 3f).coerceAtLeast(-42f)
                                            )
                                        }
                                    }

                                    dragY > viewportHeight - edgeThresholdPx -> {
                                        coroutineScope.launch {
                                            listState.scrollBy(
                                                ((dragY - (viewportHeight - edgeThresholdPx)) / 3f)
                                                    .coerceAtMost(42f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (nestedScrollConnection != null) {
                                    Modifier.nestedScroll(nestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .then(shareSelectionModifier),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = if (shareMode) 18.dp else 24.dp,
                            end = if (shareMode) 18.dp else 24.dp,
                            top = 12.dp,
                            bottom = if (shareMode) 12.dp else 72.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (shareMode) 2.dp else 10.dp)
                    ) {
                        itemsIndexed(
                            ls.lines,
                            key = { index, _ -> index }
                        ) { index, line ->
                            val isSelected = shareMode && index in selectedIndices
                            val distance = if (ls.isSynced) (index - currentLineIndex).absoluteValue else 0
                            val isCurrentSyncedLine = !shareMode && ls.isSynced && distance == 0
                            val targetLineAlpha = when {
                                shareMode -> if (line.text.isBlank()) 0.3f else 1f
                                !ls.isSynced -> 1f
                                isCurrentSyncedLine -> 1f
                                else -> syncedMutedAlpha
                            }
                            val lineAlpha by animateFloatAsState(
                                targetValue = targetLineAlpha,
                                animationSpec = tween(
                                    durationMillis = 280,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "lyricsLineAlpha"
                            )
                            val seekModifier = if (!shareMode && ls.isSynced) {
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
                            val lineModifier = if (shareMode && line.text.isNotBlank()) {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) {
                                            playbackAccent.copy(alpha = 0.32f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = 8.dp)
                                    .then(seekModifier)
                            } else {
                                seekModifier
                            }
                            val words = line.words
                            val useWordSync = wordSyncEnabled && !shareMode && ls.isSynced &&
                                distance == 0 && !words.isNullOrEmpty()
                            if (useWordSync) {
                                // Keep original line text intact and style ranges in-place,
                                // so we don't alter word spacing/kerning while syncing.
                                val rawLine = line.text
                                val annotated = buildAnnotatedString {
                                    append(rawLine)
                                    addStyle(
                                        style = SpanStyle(color = Color.White.copy(alpha = syncedMutedAlpha)),
                                        start = 0,
                                        end = rawLine.length
                                    )

                                    val tokenRanges = Array<IntRange?>(words.size) { null }
                                    var searchStart = 0
                                    words.forEachIndexed { i, w ->
                                        val token = w.text.trim()
                                        if (token.isEmpty()) return@forEachIndexed
                                        val foundAt = rawLine.indexOf(token, startIndex = searchStart)
                                        if (foundAt >= 0) {
                                            tokenRanges[i] = foundAt until (foundAt + token.length)
                                            searchStart = foundAt + token.length
                                        }
                                    }

                                    val highlightAlpha = FloatArray(words.size) { syncedMutedAlpha }
                                    val pulseTop = syncGlowPulse.coerceIn(syncedMutedAlpha, 1f)
                                    val sungAlpha = 0.88f
                                    val pos = syncPositionMs
                                    val lastIdx = words.lastIndex

                                    if (lastIdx >= 0) {
                                        val primaryIdx = words.indexOfLast { it.startMs <= pos }
                                        when {
                                            primaryIdx < 0 -> {
                                                val firstStart = words[0].startMs
                                                val leadInMs = 200L
                                                val fadeStart = firstStart - leadInMs
                                                if (pos >= fadeStart) {
                                                    val tRaw = ((pos - fadeStart).toFloat() / leadInMs)
                                                        .coerceIn(0f, 1f)
                                                    val t = tRaw * tRaw * (3f - 2f * tRaw)
                                                    highlightAlpha[0] =
                                                        syncedMutedAlpha + (pulseTop - syncedMutedAlpha) * t
                                                }
                                            }

                                            primaryIdx >= lastIdx -> {
                                                for (i in 0 until lastIdx) highlightAlpha[i] = sungAlpha
                                                highlightAlpha[lastIdx] = pulseTop
                                            }

                                            else -> {
                                                // Past words stay lit at sungAlpha so the line reads as
                                                // "already sung" rather than snapping back to muted.
                                                for (i in 0 until primaryIdx) highlightAlpha[i] = sungAlpha

                                                val currentStart = words[primaryIdx].startMs
                                                val nextStart = max(words[primaryIdx + 1].startMs, currentStart + 1L)
                                                val tRaw =
                                                    ((pos - currentStart).toFloat() / (nextStart - currentStart).toFloat())
                                                        .coerceIn(0f, 1f)
                                                // Smoothstep for a gentle handoff between consecutive words.
                                                val t = tRaw * tRaw * (3f - 2f * tRaw)
                                                highlightAlpha[primaryIdx] =
                                                    sungAlpha + (pulseTop - sungAlpha) * (1f - t)
                                                highlightAlpha[primaryIdx + 1] =
                                                    syncedMutedAlpha + (pulseTop - syncedMutedAlpha) * t
                                            }
                                        }
                                    }

                                    tokenRanges.forEachIndexed { i, range ->
                                        if (range != null) {
                                            addStyle(
                                                style = SpanStyle(
                                                    color = Color.White.copy(alpha = highlightAlpha[i].coerceIn(0f, 1f))
                                                ),
                                                start = range.first,
                                                end = range.last + 1
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = annotated,
                                    fontSize = 28.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = lineModifier
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    fontSize = 28.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = lineAlpha),
                                    modifier = lineModifier
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Footer: share controls or playback controls ──
        AnimatedContent(
            targetState = shareMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) togetherWith
                    fadeOut(animationSpec = tween(120)))
            },
            label = "lyricsFooter"
        ) { inShareMode ->
            if (inShareMode) {
                LyricsShareContinueBar(
                    selectedCount = selectedLyrics.size,
                    maxCount = MAX_LYRICS_SHARE_LINES,
                    accentColor = playbackAccent,
                    onClear = {
                        selectedIndices = emptySet()
                        dragAnchorIndex = null
                    },
                    onContinue = { showCustomizeSheet = true }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 44.dp, top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .padding(top = 12.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(42.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            tonalElevation = 0.dp
                        ) {
                            HapticIconButton(
                                onClick = { onShareModeChange(true) },
                                enabled = canEnterShareMode,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.share_24px),
                                    contentDescription = "Share lyrics card",
                                    tint = if (canEnterShareMode) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                userScrolled = false
                                coroutineScope.launch {
                                    val target = (currentLineIndex - 2).coerceAtLeast(0)
                                    listState.animateScrollToItem(index = target)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.1f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .size(42.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            tonalElevation = 0.dp
                        ) {
                            HapticIconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Close lyrics",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {
                        SeekablePlaybackBar(
                            progress = playbackProgress,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            onInteractingChange = onProgressBarInteractingChange,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.18f),
                            trackHeight = 3.dp,
                            expandedTrackHeight = 10.dp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
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
                                .padding(top = 4.dp),
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
                                        tint = Color.White,
                                        modifier = Modifier.size(68.dp)
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
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                                HapticIconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) {
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCustomizeSheet) {
            LyricsShareCustomizeSheet(
                title = shareItem.title,
                artist = shareItem.artistName,
                artworkUrl = shareArtworkUrl,
                selectedLyrics = selectedLyrics,
                combos = shareCombos,
                selectedComboIndex = selectedComboIndex,
                onSelectCombo = { selectedComboIndex = it },
                isGenerating = isGenerating,
                onEditLyrics = { showCustomizeSheet = false },
                onDismiss = { if (!isGenerating) showCustomizeSheet = false },
                onShare = {
                    if (selectedLyrics.isEmpty()) return@LyricsShareCustomizeSheet
                    coroutineScope.launch {
                        isGenerating = true
                        val file = LyricsShareCardGenerator.generate(
                            context = context,
                            spec = LyricsShareCardSpec(
                                title = shareItem.title,
                                artist = shareItem.artistName,
                                artworkUrl = shareArtworkUrl,
                                lyricLines = selectedLyrics,
                                backgroundColor = selectedBackground.toArgb(),
                                textColor = selectedTextColor.toArgb()
                            )
                        )
                        isGenerating = false
                        if (file == null) {
                            Toast
                                .makeText(context, "Couldn't create share card", Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_SUBJECT, shareItem.title)
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        clipData = ClipData.newRawUri(
                                            "lyrics_share_card",
                                            contentUri
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share"
                                )
                            )
                            showCustomizeSheet = false
                            onShareModeChange(false)
                        }.onFailure {
                            Toast
                                .makeText(context, "No share apps available", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun LyricsProviderDropdown(
    providers: Map<String, ProviderLoadState>,
    activeProvider: String?,
    accentColor: Color,
    onMenuOpen: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = activeProvider ?: "Provider"

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(Color.White.copy(alpha = 0.14f))
                .hapticClickable {
                    expanded = true
                    onMenuOpen()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = displayLabel,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1A1A1A),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            // Preserve the canonical provider order from LyricsHelper, not the map's
            // iteration order (which depends on insertion sequence at runtime).
            LyricsHelper.providerNames.forEach { name ->
                val state = providers[name] ?: ProviderLoadState.Idle
                val isActive = name == activeProvider
                val enabled = state is ProviderLoadState.Loaded && !isActive
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = name,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isActive) accentColor
                                else if (state is ProviderLoadState.Unavailable) Color.White.copy(alpha = 0.38f)
                                else Color.White
                            )
                            val sub = when (state) {
                                is ProviderLoadState.Loaded -> if (state.snapshot.isSynced) "Synced" else "Plain lyrics"
                                ProviderLoadState.Loading -> "Loading…"
                                ProviderLoadState.Idle -> "Tap to load"
                                ProviderLoadState.Unavailable -> "Unavailable"
                            }
                            Text(
                                text = sub,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    },
                    leadingIcon = {
                        when {
                            isActive -> Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Active",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            state is ProviderLoadState.Loading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                strokeWidth = 2.dp
                            )
                            else -> Spacer(Modifier.size(20.dp))
                        }
                    },
                    enabled = enabled,
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White,
                        disabledTextColor = Color.White.copy(alpha = 0.5f)
                    ),
                    onClick = {
                        expanded = false
                        onSelect(name)
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricsShareContinueBar(
    selectedCount: Int,
    maxCount: Int,
    accentColor: Color,
    onClear: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 44.dp)
    ) {
        Text(
            text = if (selectedCount == 0) {
                "Press and drag to pick up to $maxCount consecutive lines"
            } else {
                "$selectedCount of $maxCount lines selected"
            },
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onContinue,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.14f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                modifier = Modifier.weight(1.6f)
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun findLyricLineIndexAtOffsetY(
    listState: androidx.compose.foundation.lazy.LazyListState,
    offsetY: Float
): Int? {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null
    val hit = visibleItems.firstOrNull { item ->
        offsetY >= item.offset && offsetY <= item.offset + item.size
    }
    if (hit != null) return hit.index
    return visibleItems.minByOrNull { item ->
        when {
            offsetY < item.offset -> item.offset - offsetY
            offsetY > item.offset + item.size -> offsetY - (item.offset + item.size)
            else -> 0f
        }
    }?.index
}

private fun nearestSelectableLyricIndex(rawIndex: Int, selectableLineIndices: List<Int>): Int? {
    if (selectableLineIndices.isEmpty()) return null
    return selectableLineIndices.minByOrNull { (it - rawIndex).absoluteValue }
}

private fun buildConsecutiveLyricSelection(
    anchorLineIndex: Int,
    currentLineIndex: Int,
    selectableLineIndices: List<Int>,
    maxLines: Int
): Set<Int> {
    val anchorPosition = selectableLineIndices.indexOf(anchorLineIndex)
    val currentPosition = selectableLineIndices.indexOf(currentLineIndex)
    if (anchorPosition < 0 || currentPosition < 0) return emptySet()

    val maxSpan = (maxLines - 1).coerceAtLeast(0)
    val (startPosition, endPosition) = if (currentPosition >= anchorPosition) {
        anchorPosition to min(currentPosition, anchorPosition + maxSpan)
    } else {
        max(currentPosition, anchorPosition - maxSpan) to anchorPosition
    }
    return selectableLineIndices
        .subList(startPosition, endPosition + 1)
        .toSet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsShareCustomizeSheet(
    title: String,
    artist: String,
    artworkUrl: String?,
    selectedLyrics: List<String>,
    combos: List<LyricsShareColorCombo>,
    selectedComboIndex: Int,
    onSelectCombo: (Int) -> Unit,
    isGenerating: Boolean,
    onEditLyrics: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedCombo = combos.getOrElse(selectedComboIndex) { combos.first() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            Text(
                text = "Style your card",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${selectedLyrics.size} line${if (selectedLyrics.size == 1) "" else "s"} ready to share",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LyricsShareCardPreview(
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    lyricLines = selectedLyrics,
                    backgroundColor = selectedCombo.background,
                    textColor = selectedCombo.textColor,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .wrapContentHeight()
                )
            }

            Spacer(Modifier.height(18.dp))
            LyricsShareCombosRow(
                combos = combos,
                selectedIndex = selectedComboIndex,
                onSelect = onSelectCombo
            )

            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onEditLyrics,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Edit Lyrics", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onShare,
                    enabled = !isGenerating && selectedLyrics.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.share_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Share", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsShareCombosRow(
    combos: List<LyricsShareColorCombo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = "Theme",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(combos) { index, combo ->
                val isSelected = index == selectedIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(combo.background)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .hapticClickable { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa",
                            color = combo.textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = combo.name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareCardPreview(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricLines: List<String>,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val lineCount = lyricLines.size.coerceAtLeast(1)
    val lyricFontSize = when (lineCount) {
        1 -> 28.sp
        2 -> 25.sp
        3 -> 22.sp
        4 -> 20.sp
        5 -> 18.sp
        else -> 17.sp
    }
    val lyricLineHeight = (lyricFontSize.value * 1.24f).sp

    Surface(color = backgroundColor, shape = RectangleShape, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = textColor.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = lyricLines.joinToString("\n").ifBlank { "Select lyrics to preview" },
                color = textColor,
                fontSize = lyricFontSize,
                lineHeight = lyricLineHeight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
            )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPlaybackBar(
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    height: Dp
) {
    // M3 Expressive indeterminate wavy bar — shown while the track audio is being
    // resolved/downloaded so the bar visually distinguishes loading from playback.
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor
        )
    }
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
