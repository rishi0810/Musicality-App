package com.proj.Musicality.ui.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.proj.Musicality.data.local.MediaDownloadState
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.*
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.*
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.AlbumViewModel
import com.proj.Musicality.viewmodel.AlbumViewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "AlbumScreen"

private data class AlbumTrackMenuModel(
    val title: String,
    val mediaItem: MediaItem,
    val shareUrl: String?,
    val artistName: String,
    val artistId: String?
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    seed: Route.Album,
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
    val viewModel: AlbumViewModel = viewModel(
        key = seed.browseId,
        factory = AlbumViewModelFactory(seed.browseId)
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
        is AlbumViewModel.UiState.Seed -> upscaleThumbnail(s.thumbnailUrl)
        is AlbumViewModel.UiState.Loaded -> upscaleThumbnail(s.album.thumbnail)
        else -> null
    }
    val thumbUrl = reusableSeedThumbUrl ?: fetchedThumbUrl
    LaunchedEffect(thumbUrl) {
        Log.d(TAG, "Album hero artwork url=$thumbUrl")
    }
    val mediaPalette = rememberMediaBackdropPalette(
        imageUrl = thumbUrl,
        fallbackSurface = surfaceColor,
        allowNetworkFetch = !hasReusableSeedThumb
    )
    val scope = rememberCoroutineScope()
    var selectedTrackMenu by remember { mutableStateOf<AlbumTrackMenuModel?>(null) }

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
                        sharedContentState = rememberSharedContentState(key = "thumb-album-${seed.browseId}"),
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
                    is AlbumViewModel.UiState.Seed -> s.title
                    is AlbumViewModel.UiState.Loaded -> s.album.title
                    else -> seed.title
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = mediaPalette.title
                )

                val artistName = when (val s = state) {
                    is AlbumViewModel.UiState.Seed -> s.artistName
                    is AlbumViewModel.UiState.Loaded -> s.album.artist.name
                    else -> seed.artistName
                }
                if (!artistName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = mediaPalette.accent,
                        modifier = Modifier.clickable {
                            val artistId = (state as? AlbumViewModel.UiState.Loaded)?.album?.artist?.id
                            if (artistId != null) {
                                onArtistTap(artistName, artistId, null)
                            }
                        }
                    )
                }

                val year = when (val s = state) {
                    is AlbumViewModel.UiState.Seed -> s.year
                    is AlbumViewModel.UiState.Loaded -> s.album.year
                    else -> seed.year
                }
                if (!year.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mediaPalette.body
                    )
                }
            }
        }

        when (val s = state) {
            is AlbumViewModel.UiState.Seed -> {
                items(4, key = { "shimmer-$it" }) { ShimmerSection() }
            }
            is AlbumViewModel.UiState.Loaded -> {
                val album = s.album
                val saveTitle = album.title.ifBlank { seed.title }
                val saveArtist = album.artist.name.ifBlank { seed.artistName.orEmpty() }.ifBlank { null }
                val saveYear = album.year ?: seed.year
                val saveThumb = album.thumbnail ?: seed.thumbnailUrl
                val savedAlbumEntry = librarySnapshot.albums.firstOrNull { it.id == seed.browseId }
                val isAlbumSaved = savedAlbumEntry != null

                // Meta info
                if (album.trackCount != null || album.duration != null) {
                    item(key = "meta") {
                        Text(
                            text = listOfNotNull(
                                album.trackCount?.let { "$it songs" },
                                album.duration
                            ).joinToString(" \u2022 "),
                            style = MaterialTheme.typography.bodySmall,
                            color = mediaPalette.body,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Play / Shuffle buttons
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
                                        if (savedAlbumEntry != null) {
                                            repository.removeSavedEntry(savedAlbumEntry)
                                        } else {
                                            repository.rememberAlbum(
                                                albumId = seed.browseId,
                                                title = saveTitle,
                                                artistName = saveArtist,
                                                year = saveYear,
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
                                if (isAlbumSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = if (isAlbumSaved) "Remove album" else "Add album",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HapticFilledTonalButton(
                            onClick = {
                                val queue = PlaybackQueue(
                                    items = album.tracks.map { it.toMediaItem(album) },
                                    currentIndex = 0,
                                    source = QueueSource.ALBUM
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
                                contentDescription = "Play album",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        HapticOutlinedButton(
                            onClick = {
                                val shuffledTracks = album.tracks.shuffled()
                                val queue = PlaybackQueue(
                                    items = shuffledTracks.map { it.toMediaItem(album) },
                                    currentIndex = 0,
                                    source = QueueSource.ALBUM
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
                                contentDescription = "Shuffle album",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item(key = "action-buttons-spacing") {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Track list
                itemsIndexed(album.tracks, key = { _, track -> track.videoId ?: track.index }, contentType = { _, _ -> "track" }) { index, track ->
                    val videoId = track.videoId.orEmpty()
                    val downloadState = downloadStates[videoId]
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable {
                                val queue = PlaybackQueue(
                                    items = album.tracks.map { it.toMediaItem(album) },
                                    currentIndex = index,
                                    source = QueueSource.ALBUM
                                )
                                onTrackTap(queue)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${track.index}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!track.plays.isNullOrBlank()) {
                                Text(
                                    text = track.plays,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                val mediaItem = track.toMediaItem(album)
                                selectedTrackMenu = AlbumTrackMenuModel(
                                    title = track.title,
                                    mediaItem = mediaItem,
                                    shareUrl = track.videoId?.takeIf { it.isNotBlank() }?.let {
                                        "https://music.youtube.com/watch?v=$it"
                                    },
                                    artistName = album.artist.name,
                                    artistId = album.artist.id
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
            is AlbumViewModel.UiState.Error -> {
                item(key = "error") { ErrorMessage(s.message) }
            }
        }
        }
    } // Box

    selectedTrackMenu?.let { menu ->
        val mediaState by remember(menu.mediaItem.videoId) {
            repository.observeMediaState(menu.mediaItem.videoId)
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        AlbumTrackActionsSheet(
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
                val artistId = menu.artistId ?: return@AlbumTrackActionsSheet
                onArtistTap(menu.artistName, artistId, null)
                selectedTrackMenu = null
            },
            onShare = {
                val shareUrl = menu.shareUrl ?: return@AlbumTrackActionsSheet
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
private fun AlbumTrackActionsSheet(
    model: AlbumTrackMenuModel,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            if (hasVideoId) {
                AlbumActionItem(
                    label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = onToggleLike
                )
                AlbumActionItem(
                    label = "Play next",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPlayNext
                )
                AlbumActionItem(
                    label = "Add to Queue",
                    icon = Icons.Rounded.Add,
                    onClick = onAddToQueue
                )
            }
            if (model.artistId != null) {
                AlbumActionItem(
                    label = "View Artist",
                    icon = Icons.Rounded.Person,
                    onClick = onViewArtist
                )
            }
            if (model.shareUrl != null) {
                AlbumActionItem(
                    label = "Share",
                    icon = Icons.Rounded.Share,
                    onClick = onShare
                )
            }
            if (hasVideoId) {
                AlbumActionItem(
                    label = when {
                        downloadState?.isDownloading == true -> "Downloading ${(downloadState.progress * 100).toInt()}%"
                        downloadState?.isDownloaded == true -> "Downloaded"
                        else -> "Download"
                    },
                    icon = Icons.Rounded.Download,
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
private fun AlbumActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    ListItem(
        headlineContent = {
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        },
        leadingContent = {
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
