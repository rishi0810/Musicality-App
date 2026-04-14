package com.proj.Musicality.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proj.Musicality.R
import com.proj.Musicality.data.local.LibraryCollectionType
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.theme.rememberAlbumColors
import kotlinx.coroutines.launch

private data class LibraryTrackMenuModel(
    val title: String,
    val mediaItem: com.proj.Musicality.data.model.MediaItem,
    val shareUrl: String?,
    val artistName: String,
    val artistId: String?
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryCollectionScreen(
    collectionType: LibraryCollectionType,
    onTrackTap: (PlaybackQueue) -> Unit,
    onPlayNext: (com.proj.Musicality.data.model.MediaItem) -> Unit,
    onAddToQueue: (com.proj.Musicality.data.model.MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val items = when (collectionType) {
        LibraryCollectionType.LIKED -> snapshot.likedSongs
        LibraryCollectionType.TOP_SONGS -> snapshot.topSongs
        LibraryCollectionType.DOWNLOADED -> snapshot.downloadedMedia
    }

    val title = when (collectionType) {
        LibraryCollectionType.LIKED -> "Songs"
        LibraryCollectionType.TOP_SONGS -> "Top Songs"
        LibraryCollectionType.DOWNLOADED -> "Downloads"
    }
    val artworkUrl = items.firstOrNull()?.thumbnailUrl
    val albumColors = rememberAlbumColors(
        imageUrl = artworkUrl,
        fallbackPrimary = MaterialTheme.colorScheme.primaryContainer,
        fallbackSecondary = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val hasArtwork = !artworkUrl.isNullOrBlank()
    val countLabel = "${items.size} ${if (items.size == 1) "song" else "songs"}"
    val surfaceColor = MaterialTheme.colorScheme.surface
    val scope = rememberCoroutineScope()
    var selectedTrackMenu by remember { mutableStateOf<LibraryTrackMenuModel?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        val collGradientStops = remember(albumColors) {
            arrayOf(
                0f to albumColors.secondary.copy(alpha = 0.55f),
                0.15f to albumColors.primary.copy(alpha = 0.45f),
                0.55f to albumColors.secondary.copy(alpha = 0.18f),
                1f to Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(Brush.verticalGradient(colorStops = collGradientStops))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
        ) {
            item(key = "collection-header") {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp + statusBarTop, start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hasArtwork) {
                        Thumbnail(
                            url = artworkUrl,
                            size = 220.dp,
                            cornerRadius = 16.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            when (collectionType) {
                                LibraryCollectionType.LIKED -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                                LibraryCollectionType.TOP_SONGS -> {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                                LibraryCollectionType.DOWNLOADED -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.download_24px),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (items.isNotEmpty()) {
                item(key = "action-buttons") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onTrackTap(
                                    PlaybackQueue(
                                        items = items,
                                        currentIndex = 0,
                                        source = QueueSource.SEARCH
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = {
                                onTrackTap(
                                    PlaybackQueue(
                                        items = items.shuffled(),
                                        currentIndex = 0,
                                        source = QueueSource.SEARCH
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item(key = "empty-state") {
                    Text(
                        text = "No items yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(items, key = { _, item -> item.videoId }, contentType = { _, _ -> "song" }) { index, item ->
                    SongListItem(
                        title = item.title,
                        subtitle = item.artistName,
                        thumbnailUrl = item.thumbnailUrl,
                        trailingText = item.durationText,
                        onClick = {
                            onTrackTap(
                                PlaybackQueue(
                                    items = items,
                                    currentIndex = index,
                                    source = QueueSource.SEARCH
                                )
                            )
                        },
                        onOverflowClick = {
                            selectedTrackMenu = LibraryTrackMenuModel(
                                title = item.title,
                                mediaItem = item,
                                shareUrl = item.videoId.takeIf { it.isNotBlank() }?.let {
                                    "https://music.youtube.com/watch?v=$it"
                                },
                                artistName = item.artistName,
                                artistId = item.artistId
                            )
                        }
                    )
                }
            }
        }
    }

    selectedTrackMenu?.let { menu ->
        val mediaState by remember(menu.mediaItem.videoId) {
            repository.observeMediaState(menu.mediaItem.videoId)
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        LibraryTrackActionsSheet(
            model = menu,
            isLiked = mediaState.isLiked,
            onDismiss = { selectedTrackMenu = null },
            onToggleLike = {
                scope.launch { repository.toggleLike(menu.mediaItem) }
                selectedTrackMenu = null
            },
            onRemoveFromCollection = {
                scope.launch {
                    repository.removeFromCollection(collectionType, menu.mediaItem)
                    Toast.makeText(context, "Removed: ${menu.title}", Toast.LENGTH_SHORT).show()
                }
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
                val artistId = menu.artistId ?: return@LibraryTrackActionsSheet
                onArtistTap(menu.artistName, artistId, null)
                selectedTrackMenu = null
            },
            onShare = {
                val shareUrl = menu.shareUrl ?: return@LibraryTrackActionsSheet
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
private fun LibraryTrackActionsSheet(
    model: LibraryTrackMenuModel,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onRemoveFromCollection: () -> Unit,
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
                LibraryActionItem(
                    label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = onToggleLike
                )
            }
            LibraryActionItem(
                label = "Remove song",
                icon = Icons.Rounded.DeleteForever,
                onClick = onRemoveFromCollection
            )
            if (hasVideoId) {
                LibraryActionItem(
                    label = "Play next",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPlayNext
                )
                LibraryActionItem(
                    label = "Add to Queue",
                    icon = Icons.Rounded.Add,
                    onClick = onAddToQueue
                )
            }
            if (model.artistId != null) {
                LibraryActionItem(
                    label = "View Artist",
                    icon = Icons.Rounded.Person,
                    onClick = onViewArtist
                )
            }
            if (model.shareUrl != null) {
                LibraryActionItem(
                    label = "Share",
                    icon = Icons.Rounded.Share,
                    onClick = onShare
                )
            }
            if (hasVideoId) {
                LibraryActionItem(
                    label = "Download",
                    icon = Icons.Rounded.Download,
                    onClick = onDownload
                )
            }
        }
    }
}

@Composable
private fun LibraryActionItem(
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
