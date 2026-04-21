package com.proj.Musicality

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.LibraryMusic
import com.proj.Musicality.data.parser.MoodCategoryParser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.proj.Musicality.NotificationOpenEventBus
import com.proj.Musicality.data.local.LibraryCollectionType
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.ExpressiveBottomNavBar
import com.proj.Musicality.ui.components.ExpressiveBottomNavItem
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.player.PlayerSheet
import com.proj.Musicality.ui.screen.*
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.rememberPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.rememberPlaybackUiPalette
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.PlaybackViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MusicApp() {
    val navController = rememberNavController()
    val playbackViewModel: PlaybackViewModel = viewModel()
    // Only subscribe to the hasMedia boolean — avoids full MusicApp recomposition on every state update
    val hasMediaFlow = remember(playbackViewModel) {
        playbackViewModel.state.map { it.hasMedia }.distinctUntilChanged()
    }
    val hasMedia by hasMediaFlow.collectAsStateWithLifecycle(
        initialValue = playbackViewModel.state.value.hasMedia
    )
    val currentArtworkFlow = remember(playbackViewModel) {
        playbackViewModel.state
            .map { it.currentItem?.thumbnailUrl?.let(::upscaleThumbnail) }
            .distinctUntilChanged()
    }
    val currentArtworkUrl by currentArtworkFlow.collectAsStateWithLifecycle(
        initialValue = playbackViewModel.state.value.currentItem?.thumbnailUrl?.let(::upscaleThumbnail)
    )
    val playbackBackdropPalette = rememberPlaybackBackdropPalette(currentArtworkUrl)
    val playbackUiPalette = rememberPlaybackUiPalette(playbackBackdropPalette)
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val navBarMaxHeight = 84.dp
    // Use tappableElement so we only reserve space where system navigation
    // actually intercepts taps (3-button nav), while avoiding extra gap
    // on fully gestural devices.
    val navBarBottomInset = with(density) { WindowInsets.tappableElement.getBottom(density).toDp() }
    val navBarContainerHeight = navBarMaxHeight + navBarBottomInset
    val navBarContainerHeightPx = with(density) { navBarContainerHeight.toPx() }
    val miniPlayerHeight = 74.dp
    val screenWidth = configuration.screenWidthDp.dp
    // 3 nav items at 68 dp each + 32 dp padding = 236 dp, capped near screen width
    val navBarWidth = min(252.dp.value, (screenWidth - 24.dp).value).dp
    var hadMediaPreviously by rememberSaveable { mutableStateOf(playbackViewModel.state.value.hasMedia) }
    // Keep the collapsed mini player above the app navigation bar.
    // This also makes morph end-bounds line up with the visible mini art target.
    val sheetPeekHeight = if (hasMedia) miniPlayerHeight + navBarContainerHeight else 0.dp
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberHalfHeightBottomSheetState(
            peekHeight = sheetPeekHeight,
            initialValue = SheetValue.PartiallyExpanded
        )
    )
    val scope = rememberCoroutineScope()
    val bottomSheetState = sheetState.bottomSheetState
    // Continuous expand progress (0 = collapsed, 1 = expanded)
    val sheetTravelPx = with(density) {
        (configuration.screenHeightDp.dp - sheetPeekHeight).coerceAtLeast(1.dp).toPx()
    }
    // Store as raw State so it can be read in deferred layout/draw scopes without recomposition
    val expandProgressState = remember(bottomSheetState, sheetTravelPx) {
        derivedStateOf {
            val offset = try {
                bottomSheetState.requireOffset()
            } catch (_: IllegalStateException) {
                if (bottomSheetState.currentValue == SheetValue.Expanded) 0f else sheetTravelPx
            }
            (1f - (offset / sheetTravelPx)).coerceIn(0f, 1f)
        }
    }
    // Only recomposes MusicApp when the bool flips (not on every drag frame)
    val isPlayerExpanded by remember { derivedStateOf { expandProgressState.value > 0.5f } }
    // Let content render under the floating pills; only scroll containers should use this as bottom padding.
    val floatingControlsHeight = remember(hasMedia) {
        if (hasMedia) miniPlayerHeight + navBarContainerHeight else navBarContainerHeight
    }

    // ── Remembered callbacks (stable across recompositions) ──
    val onPlayQueue = remember<(PlaybackQueue) -> Unit> {
        { queue ->
            val upscaled = queue.copy(
                items = queue.items.map { it.copy(thumbnailUrl = upscaleThumbnail(it.thumbnailUrl)) }
            )
            playbackViewModel.playQueue(upscaled)
        }
    }
    val onSongTap = remember<(MediaItem, PlaybackQueue) -> Unit> {
        { _, queue ->
            val upscaled = queue.copy(
                items = queue.items.map { it.copy(thumbnailUrl = upscaleThumbnail(it.thumbnailUrl)) }
            )
            playbackViewModel.playQueue(upscaled)
        }
    }
    val onVideoTap = remember<(MediaItem) -> Unit> {
        { mediaItem ->
            playbackViewModel.playSingle(
                mediaItem.copy(thumbnailUrl = upscaleThumbnail(mediaItem.thumbnailUrl))
            )
        }
    }
    val onPlayNext = remember<(MediaItem) -> Unit> {
        { item -> playbackViewModel.playNext(item.copy(thumbnailUrl = upscaleThumbnail(item.thumbnailUrl))) }
    }
    val onAddToQueue = remember<(MediaItem) -> Unit> {
        { item -> playbackViewModel.addToQueue(item.copy(thumbnailUrl = upscaleThumbnail(item.thumbnailUrl))) }
    }
    val navToArtist = remember<(String, String, String?) -> Unit> {
        { name, id, thumb -> navController.navigate(Route.Artist(name, id, thumb)) }
    }
    val navToArtistNoThumb = remember<(String, String, String?) -> Unit> {
        { name, id, _ -> navController.navigate(Route.Artist(name, id, null)) }
    }
    val navToAlbum = remember<(String, String, String?, String?) -> Unit> {
        { title, id, artist, thumb -> navController.navigate(Route.Album(title, id, artist, upscaleThumbnail(thumb))) }
    }
    val navToAlbumNoThumb = remember<(String, String, String?, String?) -> Unit> {
        { title, id, artist, _ -> navController.navigate(Route.Album(title, id, artist, null)) }
    }
    val navToAlbum5 = remember<(String, String, String?, String?, String?) -> Unit> {
        { title, id, artist, thumb, year -> navController.navigate(Route.Album(title, id, artist, upscaleThumbnail(thumb), year)) }
    }
    val navToPlaylist = remember<(String, String, String?, String?) -> Unit> {
        { title, id, author, thumb -> navController.navigate(Route.Playlist(title, id, author, upscaleThumbnail(thumb))) }
    }
    // Player-specific callbacks — remembered so PlayerSheet never sees new lambda instances
    val onArtistTapPlayer = remember(scope, bottomSheetState, navController) {
        { artistId: String, name: String, thumb: String? ->
            scope.launch { bottomSheetState.partialExpand() }
            navController.navigate(Route.Artist(name, artistId, thumb))
        }
    }
    val onAlbumTapPlayer = remember(scope, bottomSheetState, navController) {
        { albumId: String, title: String, _: String? ->
            scope.launch { bottomSheetState.partialExpand() }
            navController.navigate(Route.Album(title, albumId, thumbnailUrl = null))
        }
    }
    // Track selected bottom nav tab
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Sync selected tab when navigating back to a tab route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val showRouteBackButton =
        currentRoute.contains("Artist") ||
            currentRoute.contains("Album") ||
            currentRoute.contains("Playlist") ||
            currentRoute.contains("LibraryCollection") ||
            currentRoute.contains("MoodCategory")
    val showBackButton = showRouteBackButton
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
        when {
            route.endsWith("Home") -> selectedTab = 0
            route.endsWith("Search") || route.endsWith("Explore") -> selectedTab = 1
            route.endsWith("Library") || route.contains("LibraryCollection") -> selectedTab = 2
        }
    }
    LaunchedEffect(hasMedia, bottomSheetState) {
        // Show the mini player on first playback, but don't force a full-screen
        // expand while audio/service/artwork initialization is still happening.
        if (!hadMediaPreviously && hasMedia) {
            runCatching { bottomSheetState.partialExpand() }
        }
        hadMediaPreviously = hasMedia
    }
    LaunchedEffect(playbackViewModel, bottomSheetState) {
        NotificationOpenEventBus.openPlayerEvents.collect {
            playbackViewModel.syncPlaybackStateFromSessionWithRetry(timeoutMs = 4_000L)
            val syncedMediaReady = withTimeoutOrNull(4_000L) {
                playbackViewModel.state
                    .map { it.hasMedia }
                    .first { it }
            } ?: playbackViewModel.state.value.hasMedia
            if (syncedMediaReady) {
                val expanded = runCatching { bottomSheetState.expand() }.isSuccess
                if (!expanded) {
                    runCatching { bottomSheetState.partialExpand() }
                }
            }
        }
    }
    DisposableEffect(lifecycleOwner, playbackViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                playbackViewModel.syncPlaybackStateFromSessionWithRetry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val bottomNavItems = remember(navController, scope, bottomSheetState) {
        listOf(
            ExpressiveBottomNavItem(
                label = "Home",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home
            ) {
                selectedTab = 0
                scope.launch { runCatching { bottomSheetState.partialExpand() } }
                navController.navigate(Route.Home) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = false
                }
            },
            ExpressiveBottomNavItem(
                label = "Search",
                selectedIcon = Icons.Filled.Search,
                unselectedIcon = Icons.Outlined.Search
            ) {
                selectedTab = 1
                scope.launch { runCatching { bottomSheetState.partialExpand() } }
                navController.navigate(Route.Search) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = false
                }
            },
            ExpressiveBottomNavItem(
                label = "Library",
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Rounded.LibraryMusic
            ) {
                selectedTab = 2
                scope.launch { runCatching { bottomSheetState.partialExpand() } }
                navController.navigate(Route.Library) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = false
                }
            }
        )
    }

    CompositionLocalProvider(
        LocalPlaybackBackdropPalette provides playbackBackdropPalette,
        LocalPlaybackUiPalette provides playbackUiPalette
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            // Read expandProgress in layout phase — avoids recomposition during drag
                            val fraction = (1f - expandProgressState.value).coerceIn(0f, 1f)
                            val h = (navBarContainerHeightPx * fraction).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = h)
                            )
                            layout(placeable.width, h) {
                                placeable.place(0, 0)
                            }
                        }
                        .clipToBounds(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Keep nav actions in their normal visual band and reserve
                    // tappable/system inset space below as a buffer area.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(navBarContainerHeight)
                    ) {
                        if (navBarBottomInset > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(navBarBottomInset)
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                        ExpressiveBottomNavBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navBarMaxHeight)
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    val fraction = (1f - expandProgressState.value).coerceIn(0f, 1f)
                                    alpha = fraction
                                    translationY = navBarContainerHeightPx * (1f - fraction)
                                },
                            barHeight = navBarMaxHeight,
                            barWidth = navBarWidth,
                            selectedIndex = selectedTab,
                            items = bottomNavItems,
                        )
                    }
                }
            }
        ) {
            var lyricsOpen by remember { mutableStateOf(false) }
            var progressBarInteracting by remember { mutableStateOf(false) }
            BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = sheetPeekHeight,
            sheetSwipeEnabled = !lyricsOpen && !progressBarInteracting,
            sheetContent = {
                // Collect full playbackState here so only PlayerSheet recomposes on state changes,
                // not the entire MusicApp tree
                val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()
                val crossfadeEnabled by playbackViewModel.crossfadeEnabled.collectAsStateWithLifecycle()
                PlayerSheet(
                    state = playbackState,
                    positionMsFlow = playbackViewModel.positionMs,
                    lyricsStateFlow = playbackViewModel.lyricsState,
                    isExpanded = isPlayerExpanded,
                    expandProgressState = expandProgressState,
                    onCollapse = { scope.launch { bottomSheetState.partialExpand() } },
                    onExpand = { scope.launch { bottomSheetState.expand() } },
                    onArtistTap = onArtistTapPlayer,
                    onAlbumTap = onAlbumTapPlayer,
                    onSkipNext = playbackViewModel::skipNext,
                    onSkipPrev = playbackViewModel::skipPrev,
                    onPlayPause = playbackViewModel::togglePlayPause,
                    onToggleRepeat = playbackViewModel::toggleRepeatMode,
                    onSeek = playbackViewModel::seekTo,
                    onSkipToIndex = playbackViewModel::skipToIndex,
                    onRemoveFromQueue = playbackViewModel::removeFromQueue,
                    onMoveInQueue = playbackViewModel::moveInQueue,
                    crossfadeEnabled = crossfadeEnabled,
                    onToggleCrossfade = playbackViewModel::toggleCrossfade,
                    miniPlayerHeight = miniPlayerHeight,
                    onLyricsOpenChange = { lyricsOpen = it },
                    onProgressBarInteractingChange = { progressBarInteracting = it }
                )
            },
            sheetDragHandle = null,
            sheetShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            sheetContainerColor = Color.Transparent,
            // Keep sheet geometry independent from Scaffold bottom-bar padding.
            // Tying both created a progress/layout feedback loop during drag.
            modifier = Modifier
        ) { _ ->
            val motionScheme = MaterialTheme.motionScheme
            // Non-bouncy spring for horizontal slide nav (M3 Expressive spatial motion feel)
            val navSpring = spring<IntOffset>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
            SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Route.Home,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(motionScheme.defaultEffectsSpec()) },
                    exitTransition = { fadeOut(motionScheme.fastEffectsSpec()) },
                    popEnterTransition = { fadeIn(motionScheme.defaultEffectsSpec()) },
                    popExitTransition = { fadeOut(motionScheme.fastEffectsSpec()) }
                ) {
                    composable<Route.Home> {
                        HomeScreen(
                        modifier = Modifier.statusBarsPadding(),
                        animatedVisibilityScope = this@composable,
                        collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp,
                        onSongTap = onSongTap,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onVideoTap = onVideoTap,
                        onArtistTap = navToArtist,
                        onAlbumTap = navToAlbum,
                        onPlaylistTap = navToPlaylist
                    )
                    }

                    composable<Route.Explore> {
                        ExploreScreen(
                            modifier = Modifier.statusBarsPadding(),
                            animatedVisibilityScope = this@composable,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp,
                            onArtistTap = { name, id, thumb, audience ->
                                navController.navigate(
                                    Route.Artist(
                                        name = name,
                                        browseId = id,
                                        thumbnailUrl = thumb,
                                        audienceText = audience
                                    )
                                )
                            },
                            onAlbumTap = navToAlbum,
                            onPlaylistTap = navToPlaylist,
                            onMoodTap = { mood ->
                                navController.navigate(Route.MoodCategory(mood.name))
                            }
                        )
                    }

                    composable<Route.MoodCategory>(
                        enterTransition = { slideInHorizontally(navSpring) { it } },
                        exitTransition = { slideOutHorizontally(navSpring) { -it / 4 } + fadeOut(motionScheme.fastEffectsSpec()) },
                        popEnterTransition = { slideInHorizontally(navSpring) { -it / 4 } + fadeIn(motionScheme.defaultEffectsSpec()) },
                        popExitTransition = { slideOutHorizontally(navSpring) { it } + fadeOut(motionScheme.fastEffectsSpec()) }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.MoodCategory>()
                        val mood = runCatching { MoodCategoryParser.Mood.valueOf(route.moodName) }
                            .getOrElse { MoodCategoryParser.Mood.FEEL_GOOD }
                        MoodCategoryScreen(
                            mood = mood,
                            modifier = Modifier.statusBarsPadding(),
                            animatedVisibilityScope = this@composable,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp,
                            onArtistTap = navToArtist,
                            onAlbumTap = navToAlbum,
                            onPlaylistTap = navToPlaylist
                        )
                    }

                    composable<Route.Search> {
                        SearchScreen(
                        modifier = Modifier.statusBarsPadding(),
                        animatedVisibilityScope = this@composable,
                        collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp,
                        onSongTap = onSongTap,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onVideoTap = onVideoTap,
                        onArtistTap = { name, id, thumb, audience ->
                            navController.navigate(
                                Route.Artist(
                                    name = name,
                                    browseId = id,
                                    thumbnailUrl = thumb,
                                    audienceText = audience
                                )
                            )
                        },
                        onArtistMenuTap = navToArtistNoThumb,
                        onAlbumTap = navToAlbum,
                        onAlbumMenuTap = navToAlbumNoThumb,
                        onPlaylistTap = navToPlaylist,
                        onMoodTap = { mood ->
                            navController.navigate(Route.MoodCategory(mood.name))
                        }
                    )
                    }

                    composable<Route.Library> {
                        LibraryScreen(
                        modifier = Modifier.statusBarsPadding(),
                        animatedVisibilityScope = this@composable,
                        onOpenCollection = { collectionType ->
                            navController.navigate(Route.LibraryCollection(collectionType.name))
                        },
                        onOpenArtist = navToArtist,
                        onOpenPlaylist = navToPlaylist,
                        onOpenAlbum = navToAlbum5
                    )
                    }

                    composable<Route.LibraryCollection>(
                        enterTransition = { slideInHorizontally(navSpring) { it } },
                        exitTransition = { slideOutHorizontally(navSpring) { -it / 4 } + fadeOut(motionScheme.fastEffectsSpec()) },
                        popEnterTransition = { slideInHorizontally(navSpring) { -it / 4 } + fadeIn(motionScheme.defaultEffectsSpec()) },
                        popExitTransition = { slideOutHorizontally(navSpring) { it } + fadeOut(motionScheme.fastEffectsSpec()) }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.LibraryCollection>()
                        val type = runCatching { LibraryCollectionType.valueOf(route.type) }
                            .getOrElse { LibraryCollectionType.LIKED }
                        LibraryCollectionScreen(
                            collectionType = type,
                            onTrackTap = onPlayQueue,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onArtistTap = navToArtistNoThumb,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable<Route.Artist>(
                        enterTransition = { slideInHorizontally(navSpring) { it } },
                        exitTransition = { slideOutHorizontally(navSpring) { -it / 4 } + fadeOut(motionScheme.fastEffectsSpec()) },
                        popEnterTransition = { slideInHorizontally(navSpring) { -it / 4 } + fadeIn(motionScheme.defaultEffectsSpec()) },
                        popExitTransition = { slideOutHorizontally(navSpring) { it } + fadeOut(motionScheme.fastEffectsSpec()) }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.Artist>()
                        ArtistScreen(
                            seed = route,
                            animatedVisibilityScope = this@composable,
                            onSongTap = onSongTap,
                            onAlbumTap = navToAlbum5,
                            onVideoTap = onVideoTap,
                            onPlaylistTap = navToPlaylist,
                            onSimilarArtistTap = navToArtist,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp
                        )
                    }

                    composable<Route.Album>(
                        enterTransition = { slideInHorizontally(navSpring) { it } },
                        exitTransition = { slideOutHorizontally(navSpring) { -it / 4 } + fadeOut(motionScheme.fastEffectsSpec()) },
                        popEnterTransition = { slideInHorizontally(navSpring) { -it / 4 } + fadeIn(motionScheme.defaultEffectsSpec()) },
                        popExitTransition = { slideOutHorizontally(navSpring) { it } + fadeOut(motionScheme.fastEffectsSpec()) }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.Album>()
                        AlbumScreen(
                            seed = route,
                            animatedVisibilityScope = this@composable,
                            onTrackTap = onPlayQueue,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onArtistTap = navToArtistNoThumb,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp
                        )
                    }

                    composable<Route.Playlist>(
                        enterTransition = { slideInHorizontally(navSpring) { it } },
                        exitTransition = { slideOutHorizontally(navSpring) { -it / 4 } + fadeOut(motionScheme.fastEffectsSpec()) },
                        popEnterTransition = { slideInHorizontally(navSpring) { -it / 4 } + fadeIn(motionScheme.defaultEffectsSpec()) },
                        popExitTransition = { slideOutHorizontally(navSpring) { it } + fadeOut(motionScheme.fastEffectsSpec()) }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.Playlist>()
                        PlaylistScreen(
                            seed = route,
                            animatedVisibilityScope = this@composable,
                            onTrackTap = onPlayQueue,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onArtistTap = navToArtistNoThumb,
                            collapsedMiniPlayerHeight = floatingControlsHeight + 3.dp
                        )
                    }
                }

                if (showBackButton) {
                    HapticIconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 6.dp, top = 6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.44f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.36f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
            }
            }
            }
        }
    }
}
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHalfHeightBottomSheetState(
    peekHeight: Dp,
    initialValue: SheetValue = SheetValue.PartiallyExpanded
): SheetState {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val confirmValueChange: (SheetValue) -> Boolean = { true }
    val positionalThreshold = {
        with(density) { 56.dp.toPx() }
    }
    // Reduce accidental high-speed settle when releasing near the collapse boundary.
    val velocityThreshold = { with(density) { 320.dp.toPx() } }

    return rememberSaveable(
        configuration.screenHeightDp,
        peekHeight,
        saver = SheetState.Saver(
            skipPartiallyExpanded = false,
            positionalThreshold = positionalThreshold,
            velocityThreshold = velocityThreshold,
            confirmValueChange = confirmValueChange,
            skipHiddenState = true,
        )
    ) {
        SheetState(
            skipPartiallyExpanded = false,
            positionalThreshold = positionalThreshold,
            velocityThreshold = velocityThreshold,
            initialValue = initialValue,
            confirmValueChange = confirmValueChange,
            skipHiddenState = true,
        )
    }
}
