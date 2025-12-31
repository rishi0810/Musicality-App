package com.example.musicality.ui

import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.example.musicality.ui.notifications.NotificationsScreen
import com.example.musicality.ui.player.CollapsiblePlayer
import com.example.musicality.ui.player.PlayerViewModel
import com.example.musicality.ui.player.QueueSheet
import com.example.musicality.ui.search.SearchScreen
import com.example.musicality.ui.album.AlbumScreen
import com.example.musicality.ui.playlist.PlaylistScreen
import com.example.musicality.ui.artist.ArtistScreen
import com.google.common.util.concurrent.MoreExecutors

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.title_home, Icons.Default.Home)
    object Search : Screen("search", R.string.title_search, Icons.Default.Search)
    object Notifications : Screen("notifications", R.string.title_notifications, Icons.Default.Notifications)
}

val items = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Notifications,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MusicalityApp(openPlayerOnStart: Boolean = false) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val context = LocalContext.current
    
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
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    
    // Queue state
    val queueState by playerViewModel.queueState.collectAsState()
    val isQueueSheetVisible by playerViewModel.isQueueSheetVisible.collectAsState()
    
    // Handle back press: close queue sheet first, then expanded player, then navigate back
    BackHandler(enabled = isQueueSheetVisible || isExpanded) {
        when {
            isQueueSheetVisible -> playerViewModel.setQueueSheetVisible(false)
            isExpanded -> playerViewModel.setExpanded(false)
        }
    }
    
    // Track the drag offset from the expanded player
    // 0f = Fully expanded (top of screen)
    // Positive values = Dragging down
    var playerDragOffset by remember { mutableFloatStateOf(0f) }
    
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
                            bottomPadding = bottomContentPadding
                        )
                    }
                    composable(Screen.Notifications.route) { 
                        NotificationsScreen(bottomPadding = bottomContentPadding) 
                    }
                    // Album detail screen
                    composable("album/{albumId}") { backStackEntry ->
                        val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                        AlbumScreen(
                            albumId = albumId,
                            onBackClick = { navController.popBackStack() },
                            onSongClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onPlayAlbum = { albumSongs, albumThumbnail, shuffle ->
                                playerViewModel.playAlbum(albumSongs, albumThumbnail, shuffle)
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
                            onSongClick = { videoId, thumbnailUrl ->
                                playerViewModel.playSong(videoId, thumbnailUrl)
                            },
                            onPlayPlaylist = { playlistSongs, playlistThumbnail, shuffle ->
                                playerViewModel.playAlbum(playlistSongs, playlistThumbnail, shuffle)
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
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(stringResource(screen.labelRes)) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
                onOpenQueue = { playerViewModel.toggleQueueSheet() },
                onNext = { playerViewModel.playNext() },
                onPrevious = { playerViewModel.playPrevious() },
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
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f) // On top of player
            )
        }
    }
}
