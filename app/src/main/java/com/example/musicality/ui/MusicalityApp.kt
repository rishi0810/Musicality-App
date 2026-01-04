package com.example.musicality.ui

import android.content.ComponentName
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicality.R
import com.example.musicality.service.MusicService
import com.example.musicality.ui.home.HomeScreen
import com.example.musicality.ui.library.LibraryScreen
import com.example.musicality.ui.player.CollapsiblePlayer
import com.example.musicality.ui.player.LikedSongsManager
import com.example.musicality.ui.player.PlayerViewModel
import com.example.musicality.ui.player.QueueSheet
import com.example.musicality.ui.search.SearchScreen
import com.example.musicality.ui.search.SearchResultsScreen
import com.example.musicality.ui.album.AlbumScreen
import com.example.musicality.ui.playlist.PlaylistScreen
import com.example.musicality.ui.artist.ArtistScreen
import com.example.musicality.util.OnboardingPreferences
import com.google.common.util.concurrent.MoreExecutors

/**
 * Screen definition with drawable icons for filled and unfilled states
 */
sealed class Screen(
    val route: String,
    val labelRes: Int,
    @DrawableRes val iconUnfilled: Int,
    @DrawableRes val iconFilled: Int
) {
    object Home : Screen(
        "home",
        R.string.title_home,
        R.drawable.home_24px,
        R.drawable.home_filled_24px
    )
    object Search : Screen(
        "search",
        R.string.title_search,
        R.drawable.search_24px,
        R.drawable.search_24px
    )
    object Library : Screen(
        "library",
        R.string.title_library,
        R.drawable.library_music_24px,
        R.drawable.library_music_filled_24px
    )
}

val items = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MusicalityApp(openPlayerOnStart: Boolean = false) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val context = LocalContext.current
    
    // Initialize download repository
    LaunchedEffect(Unit) {
        playerViewModel.initializeDownloadRepository(context)
    }
    
    // Connect to MusicService using MediaController
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    
    DisposableEffect(Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                // Initialize the ViewModel with the MediaController's player
                playerViewModel.initializePlayer(controller)
            } catch (e: Exception) {
                android.util.Log.e("MusicalityApp", "Failed to connect to MusicService", e)
            }
        }, MoreExecutors.directExecutor())
        
        onDispose {
            mediaController?.release()
            mediaController = null
        }
    }
    
    // Register broadcast receiver for next/previous actions from notification
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    MusicService.ACTION_PLAY_NEXT -> playerViewModel.playNext()
                    MusicService.ACTION_PLAY_PREVIOUS -> playerViewModel.playPrevious()
                }
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction(MusicService.ACTION_PLAY_NEXT)
            addAction(MusicService.ACTION_PLAY_PREVIOUS)
        }
        
        // Use ContextCompat for proper flag handling across all Android versions
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    // Expand player if opened from notification
    LaunchedEffect(openPlayerOnStart) {
        if (openPlayerOnStart) {
            playerViewModel.setExpanded(true)
        }
    }
    
    val playerState by playerViewModel.playerState.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isExpanded by playerViewModel.isExpanded.collectAsState()
    val isBuffering by playerViewModel.isBuffering.collectAsState()
    val isDownloaded by playerViewModel.isDownloaded.collectAsState()
    val isDownloading by playerViewModel.isDownloading.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    
    // Queue state
    val queueState by playerViewModel.queueState.collectAsState()
    val isQueueSheetVisible by playerViewModel.isQueueSheetVisible.collectAsState()
    
    
    // Track the drag offset from the expanded player
    // 0f = Fully expanded (top of screen)
    // Positive values = Dragging down
    var playerDragOffset by remember { mutableFloatStateOf(0f) }
    
    // Swipe-up tutorial hint state - shown only for first-time users
    var showSwipeUpHint by remember {
        mutableStateOf(!OnboardingPreferences.hasSwipeUpHintBeenShown(context))
    }
    
    // Interactive swipe-up state for queue sheet
    var isSwipingUpQueue by remember { mutableStateOf(false) }
    var swipeUpProgress by remember { mutableFloatStateOf(0f) }
    
    // Liked songs state
    val likedSongsManager = remember { LikedSongsManager.getInstance(context) }
    val currentVideoId = (playerState as? com.example.musicality.util.UiState.Success)?.data?.videoId
    val isCurrentSongLiked by remember(currentVideoId) {
        if (currentVideoId != null) {
            likedSongsManager.isSongLiked(currentVideoId)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }.collectAsState(initial = false)
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Calculate bottom padding for content
        // Navigation Bar is always there (approx 80dp)
        // Mini player is additional 80dp if active
        val bottomContentPadding = if (playerState !is com.example.musicality.util.UiState.Idle) 160.dp else 80.dp
        
        Scaffold(
            // We remove bottomBar from Scaffold to handle it manually as an overlay
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) { 
                        HomeScreen(bottomPadding = bottomContentPadding) 
                    }
                    composable(Screen.Search.route) { 
                        SearchScreen(
                            onSongClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onAlbumClick = { albumId ->
                                navController.navigate("album/$albumId")
                            },
                            onPlaylistClick = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onArtistClick = { artistId ->
                                navController.navigate("artist/$artistId")
                            },
                            onSearchResultsClick = { query ->
                                navController.navigate("searchResults/${java.net.URLEncoder.encode(query, "UTF-8")}")
                            },
                            bottomPadding = bottomContentPadding
                        )
                    }
                    composable(Screen.Library.route) { 
                        LibraryScreen(
                            bottomPadding = bottomContentPadding,
                            onSongClick = { videoId, allSongs, albumName, thumbnail ->
                                // Play song from liked songs
                                playerViewModel.playSongFromPlaylist(videoId, allSongs, albumName, thumbnail)
                            },
                            onPlayLikedSongs = { songs, shuffle ->
                                playerViewModel.playPlaylist(songs, "Liked Songs", "", shuffle)
                            }
                        )
                    }
                    // Album detail screen
                    composable("album/{albumId}") { backStackEntry ->
                        val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                        AlbumScreen(
                            albumId = albumId,
                            onBackClick = { navController.popBackStack() },
                            onSongClick = { videoId, albumSongs, albumName, albumThumbnail ->
                                // Play song from album - sets album as queue
                                playerViewModel.playSongFromAlbum(videoId, albumSongs, albumName, albumThumbnail)
                            },
                            onPlayAlbum = { albumSongs, albumName, albumThumbnail, shuffle ->
                                playerViewModel.playAlbum(albumSongs, albumName, albumThumbnail, shuffle)
                            },
                            onAlbumClick = { newAlbumId ->
                                navController.navigate("album/$newAlbumId")
                            }
                        )
                    }
                    // Playlist detail screen
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                        PlaylistScreen(
                            playlistId = playlistId,
                            onBackClick = { navController.popBackStack() },
                            onSongClick = { videoId, playlistSongs, playlistName, thumbnail ->
                                // Play song from playlist - sets playlist as queue
                                playerViewModel.playSongFromPlaylist(videoId, playlistSongs, playlistName, thumbnail)
                            },
                            onPlayPlaylist = { playlistSongs, playlistName, playlistThumbnail, shuffle ->
                                playerViewModel.playPlaylist(playlistSongs, playlistName, playlistThumbnail, shuffle)
                            }
                        )
                    }
                    // Artist detail screen
                    composable("artist/{artistId}") { backStackEntry ->
                        val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                        ArtistScreen(
                            artistId = artistId,
                            onBackClick = { navController.popBackStack() },
                            onSongClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onAlbumClick = { albumId ->
                                navController.navigate("album/$albumId")
                            },
                            onPlaylistClick = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onArtistClick = { newArtistId ->
                                navController.navigate("artist/$newArtistId")
                            }
                        )
                    }
                    // Search Results screen
                    composable("searchResults/{query}") { backStackEntry ->
                        val query = backStackEntry.arguments?.getString("query") ?: ""
                        val decodedQuery = java.net.URLDecoder.decode(query, "UTF-8")
                        SearchResultsScreen(
                            query = decodedQuery,
                            onSongClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onVideoClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onAlbumClick = { albumId ->
                                navController.navigate("album/$albumId")
                            },
                            onArtistClick = { artistId ->
                                navController.navigate("artist/$artistId")
                            },
                            onPlaylistClick = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onBackClick = { navController.popBackStack() },
                            bottomPadding = bottomContentPadding
                        )
                    }
                }
            }
        }
        
        // Bottom Panel Overlay (Collapsed Player + Navigation Bar)
        // We calculate visibility/offset similarly
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenHeightPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
        
        val bottomPanelOffset = if (isExpanded) {
            with(density) {
                val maxTranslation = 200.dp.toPx() // Enough to hide bottom panel
                val dragFraction = (playerDragOffset / screenHeightPx).coerceIn(0f, 1f)
                val offsetPx = maxTranslation * (1f - dragFraction)
                offsetPx.toInt()
            }
        } else {
            0
        }

        // Only show if not fully expanded or if dragging
        if (!isExpanded || playerDragOffset > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, bottomPanelOffset) }
                    .zIndex(1f) // Ensure it stays on top
            ) {
                Column {
                    // Collapsed player
                    if (playerState !is com.example.musicality.util.UiState.Idle) {
                        CollapsiblePlayer(
                            playerState = playerState,
                            isPlaying = isPlaying,
                            isExpanded = false,
                            isBuffering = isBuffering,
                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                            onClose = { playerViewModel.closePlayer() },
                            onToggleExpanded = { playerViewModel.toggleExpanded() },
                            onDragDown = { playerViewModel.setExpanded(false) }
                        )
                    }
                    
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        painter = painterResource(
                                            id = if (isSelected) screen.iconFilled else screen.iconUnfilled
                                        ), 
                                        contentDescription = null
                                    ) 
                                },
                                label = { Text(stringResource(screen.labelRes)) },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Expanded player (full screen overlay spanning entire height)
        if (playerState !is com.example.musicality.util.UiState.Idle && isExpanded) {
            CollapsiblePlayer(
                playerState = playerState,
                isPlaying = isPlaying,
                isExpanded = true,
                isBuffering = isBuffering,
                isLiked = isCurrentSongLiked,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                onClose = { playerViewModel.closePlayer() },
                onToggleExpanded = { playerViewModel.toggleExpanded() },
                onDragDown = { 
                    playerViewModel.setExpanded(false) 
                    playerDragOffset = 0f // Reset local drag state
                },
                onOffsetChanged = { offset ->
                    playerDragOffset = offset
                },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { fraction -> playerViewModel.seekToFraction(fraction) },
                onOpenQueue = {
                    // Dismiss hint when queue is opened (whether by swipe or button)
                    if (showSwipeUpHint) {
                        showSwipeUpHint = false
                        OnboardingPreferences.markSwipeUpHintAsShown(context)
                    }
                    playerViewModel.toggleQueueSheet()
                },
                onNext = { playerViewModel.playNext() },
                onPrevious = { playerViewModel.playPrevious() },
                onViewArtist = { channelId ->
                    navController.navigate("artist/$channelId")
                },
                onToggleLike = {
                    (playerState as? com.example.musicality.util.UiState.Success)?.data?.let { songInfo ->
                        likedSongsManager.toggleLike(songInfo)
                    }
                },
                onDownload = {
                    playerViewModel.downloadCurrentSong()
                },
                showSwipeUpHint = showSwipeUpHint,
                onSwipeUpHintDismissed = {
                    showSwipeUpHint = false
                    OnboardingPreferences.markSwipeUpHintAsShown(context)
                },
                onSwipeUpProgress = { progress ->
                    isSwipingUpQueue = true
                    swipeUpProgress = progress
                },
                onSwipeUpComplete = {
                    isSwipingUpQueue = false
                    swipeUpProgress = 0f
                    // Dismiss hint when queue is opened
                    if (showSwipeUpHint) {
                        showSwipeUpHint = false
                        OnboardingPreferences.markSwipeUpHintAsShown(context)
                    }
                    playerViewModel.setQueueSheetVisible(true)
                },
                onSwipeUpCancel = {
                    isSwipingUpQueue = false
                    swipeUpProgress = 0f
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            )
            
            // Queue sheet overlay - slides up on top of the player like a bottom sheet
            val currentThumbnail = (playerState as? com.example.musicality.util.UiState.Success)?.data?.thumbnailUrl ?: ""
            QueueSheet(
                queueState = queueState,
                currentSongThumbnail = currentThumbnail,
                isVisible = isQueueSheetVisible,
                onDismiss = { playerViewModel.setQueueSheetVisible(false) },
                onSongClick = { song -> playerViewModel.playFromQueue(song) },
                dragProgress = swipeUpProgress,
                isDragging = isSwipingUpQueue,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f) // On top of player
            )
            
            // BackHandler for expanded player - placed AFTER content so it takes precedence
            // over navigation back handling when the player is expanded
            BackHandler(enabled = isQueueSheetVisible || isExpanded) {
                when {
                    isQueueSheetVisible -> playerViewModel.setQueueSheetVisible(false)
                    isExpanded -> playerViewModel.setExpanded(false)
                }
            }
        }
    }
}
