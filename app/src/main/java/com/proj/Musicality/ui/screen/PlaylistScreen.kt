package com.proj.Musicality.ui.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.model.*
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.*
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.PlaylistViewModel
import com.proj.Musicality.viewmodel.PlaylistViewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "PlaylistScreen"

private data class PlaylistTrackMenuModel(
    val title: String,
    val mediaItem: MediaItem,
    val shareUrl: String?,
    val artistName: String,
    val artistId: String?
)

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun PlaylistScreen(
    seed: Route.Playlist,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onTrackTap: (PlaybackQueue) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val viewModel: PlaylistViewModel = viewModel(
        key = seed.browseId,
        factory = PlaylistViewModelFactory(seed.browseId)
    )

    LaunchedEffect(seed.browseId) {
        viewModel.initialize(seed)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val surfaceColor = MaterialTheme.colorScheme.surface

    val reusableSeedThumbUrl = upscaleThumbnail(seed.thumbnailUrl)
    val hasReusableSeedThumb = !reusableSeedThumbUrl.isNullOrBlank()
    val fetchedThumbUrl = when (val s = state) {
        is PlaylistViewModel.UiState.Seed -> upscaleThumbnail(s.thumbnailUrl)
        is PlaylistViewModel.UiState.Loaded -> upscaleThumbnail(
            s.playlist.thumbnails.maxByOrNull { it.width }?.url
        )
        else -> null
    }
    val thumbUrl = reusableSeedThumbUrl ?: fetchedThumbUrl
    LaunchedEffect(thumbUrl) {
        Log.d(TAG, "Playlist hero artwork url=$thumbUrl")
    }
    val mediaPalette = rememberMediaBackdropPalette(
        imageUrl = thumbUrl,
        fallbackSurface = surfaceColor,
        allowNetworkFetch = !hasReusableSeedThumb
    )
    val scope = rememberCoroutineScope()
    var selectedTrackMenu by remember { mutableStateOf<PlaylistTrackMenuModel?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            mediaPalette.top.copy(alpha = 0.96f),
                            mediaPalette.middle.copy(alpha = 0.82f),
                            mediaPalette.bottom.copy(alpha = 0.12f),
                            surfaceColor
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            mediaPalette.accent.copy(alpha = 0.24f),
                            Color.Transparent
                        )
                    )
                )
        )

        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
        ) {
        // Header
        item(key = "header") {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp + statusBarTop, start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val heroModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "thumb-playlist-${seed.browseId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = MediaBoundsSpring
                    )
                }

                Thumbnail(
                    url = thumbUrl,
                    size = 220.dp,
                    cornerRadius = 16.dp,
                    allowNetworkFetch = !hasReusableSeedThumb,
                    modifier = heroModifier
                )
                Spacer(Modifier.height(20.dp))

                val title = when (val s = state) {
                    is PlaylistViewModel.UiState.Seed -> s.title
                    is PlaylistViewModel.UiState.Loaded -> s.playlist.title
                    else -> seed.title
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = mediaPalette.title
                )

                val author = when (val s = state) {
                    is PlaylistViewModel.UiState.Seed -> s.author
                    is PlaylistViewModel.UiState.Loaded -> s.playlist.author
                    else -> seed.author
                }
                if (!author.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.titleMedium,
                        color = mediaPalette.body
                    )
                }
            }
        }

        when (val s = state) {
            is PlaylistViewModel.UiState.Seed -> {
                items(4, key = { "shimmer-$it" }) { ShimmerSection() }
            }
            is PlaylistViewModel.UiState.Loaded -> {
                val playlist = s.playlist
                val saveTitle = playlist.title.ifBlank { seed.title }
                val saveAuthor = playlist.author ?: seed.author
                val saveThumb = playlist.thumbnails.maxByOrNull { it.width }?.url ?: seed.thumbnailUrl

                // Meta info
                item(key = "meta") {
                    Text(
                        text = listOfNotNull(
                            playlist.trackCount?.let { "$it tracks" },
                            playlist.duration,
                            playlist.viewCount
                        ).joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = mediaPalette.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Description
                if (!playlist.description.isNullOrBlank()) {
                    item(key = "description") {
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = mediaPalette.body,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Play / Shuffle buttons
                if (playlist.tracks.isNotEmpty()) {
                    item(key = "action-buttons") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.rememberPlaylist(
                                            playlistId = seed.browseId,
                                            title = saveTitle,
                                            author = saveAuthor,
                                            thumbnailUrl = saveThumb
                                        )
                                    }
                                },
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = mediaPalette.title
                                ),
                                border = BorderStroke(1.dp, mediaPalette.outline.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Add playlist",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val queue = PlaybackQueue(
                                        items = playlist.tracks.map { it.toMediaItem() },
                                        currentIndex = 0,
                                        source = QueueSource.PLAYLIST
                                    )
                                    onTrackTap(queue)
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = mediaPalette.accent,
                                    contentColor = mediaPalette.onAccent
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play")
                            }
                            OutlinedButton(
                                onClick = {
                                    val shuffledTracks = playlist.tracks.shuffled()
                                    val queue = PlaybackQueue(
                                        items = shuffledTracks.map { it.toMediaItem() },
                                        currentIndex = 0,
                                        source = QueueSource.PLAYLIST
                                    )
                                    onTrackTap(queue)
                                },
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = mediaPalette.title
                                ),
                                border = BorderStroke(1.dp, mediaPalette.outline.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle playlist",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Divider
                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Track list
                itemsIndexed(
                    playlist.tracks,
                    // Some playlists can contain duplicate video IDs; include index to guarantee uniqueness.
                    key = { index, track -> "${track.videoId}-$index" },
                    contentType = { _, _ -> "track" }
                ) { index, track ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable {
                                val queue = PlaybackQueue(
                                    items = playlist.tracks.map { it.toMediaItem() },
                                    currentIndex = index,
                                    source = QueueSource.PLAYLIST
                                )
                                onTrackTap(queue)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumbnail(
                            url = track.thumbnailUrl,
                            size = 48.dp,
                            cornerRadius = 8.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val artistText = track.artists.joinToString(", ") { it.name }
                            if (artistText.isNotBlank()) {
                                Text(
                                    text = artistText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        val artist = track.artists.firstOrNull()
                                        if (artist?.id != null) {
                                            onArtistTap(artist.name, artist.id, null)
                                        }
                                    }
                                )
                            }
                        }
                        if (!track.duration.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = track.duration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val mediaItem = track.toMediaItem()
                                val artist = track.artists.firstOrNull()
                                selectedTrackMenu = PlaylistTrackMenuModel(
                                    title = track.title,
                                    mediaItem = mediaItem,
                                    shareUrl = track.videoId.takeIf { it.isNotBlank() }?.let {
                                        "https://music.youtube.com/watch?v=$it"
                                    },
                                    artistName = artist?.name ?: mediaItem.artistName,
                                    artistId = artist?.id
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Track options"
                            )
                        }
                    }
                }
            }
            is PlaylistViewModel.UiState.Error -> {
                item(key = "error") { ErrorMessage(s.message) }
            }
        }
        }
        }
    } // Box

    selectedTrackMenu?.let { menu ->
        PlaylistTrackActionsSheet(
            model = menu,
            onDismiss = { selectedTrackMenu = null },
            onPlayNext = {
                onPlayNext(menu.mediaItem)
                selectedTrackMenu = null
            },
            onAddToQueue = {
                onAddToQueue(menu.mediaItem)
                selectedTrackMenu = null
            },
            onViewArtist = {
                val artistId = menu.artistId ?: return@PlaylistTrackActionsSheet
                onArtistTap(menu.artistName, artistId, null)
                selectedTrackMenu = null
            },
            onShare = {
                val shareUrl = menu.shareUrl ?: return@PlaylistTrackActionsSheet
                val shareText = buildString {
                    append(menu.title)
                    append("\n")
                    append(shareUrl)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, menu.title)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            "Share"
                        )
                    )
                }
                selectedTrackMenu = null
            },
            onDownload = {
                scope.launch {
                    val result = repository.download(menu.mediaItem)
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Downloaded: ${menu.title}" else "Download failed: ${menu.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                selectedTrackMenu = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistTrackActionsSheet(
    model: PlaylistTrackMenuModel,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onViewArtist: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasVideoId = model.mediaItem.videoId.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            if (hasVideoId) {
                PlaylistActionItem(
                    label = "Play next",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPlayNext
                )
                PlaylistActionItem(
                    label = "Add to Queue",
                    icon = Icons.Rounded.Add,
                    onClick = onAddToQueue
                )
            }
            if (model.artistId != null) {
                PlaylistActionItem(
                    label = "View Artist",
                    icon = Icons.Rounded.Person,
                    onClick = onViewArtist
                )
            }
            if (model.shareUrl != null) {
                PlaylistActionItem(
                    label = "Share",
                    icon = Icons.Rounded.Share,
                    onClick = onShare
                )
            }
            if (hasVideoId) {
                PlaylistActionItem(
                    label = "Download",
                    icon = Icons.Rounded.Download,
                    onClick = onDownload
                )
            }
        }
    }
}

@Composable
private fun PlaylistActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
