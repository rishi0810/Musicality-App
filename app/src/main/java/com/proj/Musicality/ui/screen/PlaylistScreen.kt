package com.proj.Musicality.ui.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaDownloadState
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.*
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.*
import com.proj.Musicality.ui.theme.ForceGradientStatusBar
import com.proj.Musicality.ui.theme.GradientTheme
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.toCompactSongTitle
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
    val librarySnapshot by repository.snapshot.collectAsStateWithLifecycle()
    val downloadStates by repository.downloadStates.collectAsStateWithLifecycle()
    val viewModel: PlaylistViewModel = viewModel(
        key = seed.browseId,
        factory = PlaylistViewModelFactory(seed.browseId)
    )

    ForceGradientStatusBar()

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

    GradientTheme {
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

        CompositionLocalProvider(LocalOverscrollFactory provides null) {
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
                item(key = "loader") { PageLoader() }
            }
            is PlaylistViewModel.UiState.Loaded -> {
                val playlist = s.playlist
                val saveTitle = playlist.title.ifBlank { seed.title }
                val saveAuthor = playlist.author ?: seed.author
                val saveThumb = playlist.thumbnails.maxByOrNull { it.width }?.url ?: seed.thumbnailUrl
                val savedPlaylistEntry = librarySnapshot.playlists.firstOrNull { it.id == seed.browseId }
                val isPlaylistSaved = savedPlaylistEntry != null

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HapticOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (savedPlaylistEntry != null) {
                                            repository.removeSavedEntry(savedPlaylistEntry)
                                        } else {
                                            repository.rememberPlaylist(
                                                playlistId = seed.browseId,
                                                title = saveTitle,
                                                author = saveAuthor,
                                                thumbnailUrl = saveThumb
                                            )
                                        }
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
                                    if (isPlaylistSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (isPlaylistSaved) "Remove playlist" else "Add playlist",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HapticFilledTonalButton(
                                onClick = {
                                    val queue = PlaybackQueue(
                                        items = playlist.tracks.map { it.toMediaItem() },
                                        currentIndex = 0,
                                        source = QueueSource.PLAYLIST
                                    )
                                    onTrackTap(queue)
                                },
                                modifier = Modifier.size(76.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = mediaPalette.accent,
                                    contentColor = mediaPalette.onAccent
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play playlist",
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            HapticOutlinedButton(
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
                    item(key = "action-buttons-spacing") {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Track list
                itemsIndexed(
                    playlist.tracks,
                    // Some playlists can contain duplicate video IDs; include index to guarantee uniqueness.
                    key = { index, track -> "${track.videoId}-$index" },
                    contentType = { _, _ -> "track" }
                ) { index, track ->
                    val downloadState = downloadStates[track.videoId]
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
                                text = track.title.toCompactSongTitle(),
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
                            if (downloadState?.isDownloading == true) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier.fillMaxWidth()
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
                        if (downloadState?.isDownloaded == true) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        HapticIconButton(
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
    } // GradientTheme

    selectedTrackMenu?.let { menu ->
        val mediaState by remember(menu.mediaItem.videoId) {
            repository.observeMediaState(menu.mediaItem.videoId)
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        PlaylistTrackActionsSheet(
            model = menu,
            isLiked = mediaState.isLiked,
            downloadState = downloadStates[menu.mediaItem.videoId],
            onDismiss = { selectedTrackMenu = null },
            onToggleLike = {
                scope.launch { repository.toggleLike(menu.mediaItem) }
                selectedTrackMenu = null
            },
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
                    selectedTrackMenu = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistTrackActionsSheet(
    model: PlaylistTrackMenuModel,
    isLiked: Boolean,
    downloadState: MediaDownloadState?,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onViewArtist: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasVideoId = model.mediaItem.videoId.isNotBlank()
    var showMoreInfo by remember(model.mediaItem.videoId, model.title) { mutableStateOf(false) }
    val infoLines = remember(model) {
        listOfNotNull(
            "Title: ${model.title}",
            model.artistName.takeIf { it.isNotBlank() }?.let { "Artist: $it" },
            model.mediaItem.albumName?.takeIf { it.isNotBlank() }?.let { "Album: $it" },
            model.mediaItem.durationText?.takeIf { it.isNotBlank() }?.let { "Duration: $it" },
            model.mediaItem.videoId.takeIf { it.isNotBlank() }?.let { "Video ID: $it" }
        )
    }

    val headerThumbUrl = remember(model.mediaItem.thumbnailUrl) {
        com.proj.Musicality.util.upscaleThumbnail(model.mediaItem.thumbnailUrl)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumbnail(
                    url = headerThumbUrl,
                    size = 60.dp,
                    cornerRadius = 12.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOfNotNull(
                        model.artistName.takeIf { it.isNotBlank() },
                        model.mediaItem.albumName?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider()

            if (hasVideoId) {
                PlaylistActionItem(
                    label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    supportingText = if (isLiked) "Take it out of your Liked songs" else "Save this track in Liked",
                    onClick = onToggleLike
                )
                PlaylistActionItem(
                    label = "Play next",
                    icon = Icons.Rounded.PlayArrow,
                    supportingText = "Play right after the current song",
                    onClick = onPlayNext
                )
                PlaylistActionItem(
                    label = "Add to Queue",
                    icon = Icons.Rounded.Add,
                    supportingText = "Append to the end of your queue",
                    onClick = onAddToQueue
                )
            }
            if (model.artistId != null) {
                PlaylistActionItem(
                    label = "View Artist",
                    icon = Icons.Rounded.Person,
                    supportingText = "More from ${model.artistName}",
                    onClick = onViewArtist,
                    leadingContent = {
                        val artistAvatarUrl = model.mediaItem.thumbnailUrl
                        if (!artistAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artistAvatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            if (model.shareUrl != null) {
                PlaylistActionItem(
                    label = "Share",
                    icon = Icons.Rounded.Share,
                    supportingText = "Send a link to this song",
                    onClick = onShare
                )
            }
            if (hasVideoId) {
                val downloadSupporting = when {
                    downloadState?.isDownloading == true -> "${(downloadState.progress * 100).toInt()}%"
                    downloadState?.isDownloaded == true -> "Saved for offline"
                    else -> "Listen without a connection"
                }
                PlaylistActionItem(
                    label = when {
                        downloadState?.isDownloading == true -> "Downloading ${(downloadState.progress * 100).toInt()}%"
                        downloadState?.isDownloaded == true -> "Downloaded"
                        else -> "Download"
                    },
                    icon = Icons.Rounded.Download,
                    supportingText = downloadSupporting,
                    onClick = onDownload,
                    enabled = downloadState?.isDownloading != true
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(text = "More Info") },
                trailingContent = {
                    Icon(
                        imageVector = if (showMoreInfo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showMoreInfo) "Collapse more info" else "Expand more info"
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showMoreInfo = !showMoreInfo }
            )

            if (showMoreInfo && infoLines.isNotEmpty()) {
                infoLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = leadingContent ?: {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.hapticClickable(enabled = enabled, onClick = onClick)
    )
}
