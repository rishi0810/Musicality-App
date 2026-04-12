package com.proj.Musicality.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import kotlinx.coroutines.launch

private data class QueueItemMenuModel(
    val title: String,
    val subtitle: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String?,
    val shareUrl: String,
    val metadata: List<String>,
    val videoId: String,
    val musicVideoType: String?
)

private fun queueItemStableKey(index: Int, item: MediaItem): String = item.videoId
private val QueueCurrentItemColor = Color(0xFF3A2A30)
private val QueueCurrentAccentColor = Color(0xFFF0B6BF)
private val QueueCurrentSecondaryColor = Color(0xFFD7A8AF)

/**
 * Reusable queue list content – embedded inside the player's queue sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueContent(
    queue: PlaybackQueue,
    onSkipToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    onArtistTap: ((String, String, String?) -> Unit)? = null,
    onAlbumTap: ((String, String, String?) -> Unit)? = null,
    onQueueActionConsumed: (() -> Unit)? = null,
    crossfadeEnabled: Boolean = false,
    crossfadeLockActive: Boolean = false,
    headerContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackUiPalette = LocalPlaybackUiPalette.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val latestItems by rememberUpdatedState(queue.items)
    val lockedNextIndex = remember(queue.currentIndex, queue.items.size, crossfadeEnabled, crossfadeLockActive) {
        if (crossfadeEnabled && crossfadeLockActive && queue.currentIndex + 1 in queue.items.indices) {
            queue.currentIndex + 1
        } else {
            -1
        }
    }
    var selectedMenu by remember { mutableStateOf<QueueItemMenuModel?>(null) }
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val currentItemContainerColor = playbackUiPalette?.currentItemContainer ?: QueueCurrentItemColor
    val currentItemAccentColor = playbackUiPalette?.accent ?: QueueCurrentAccentColor
    val currentItemSecondaryColor = playbackUiPalette?.currentItemSecondary ?: QueueCurrentSecondaryColor

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (headerContent != null) {
                item(key = "queue-header", contentType = "header") {
                    headerContent()
                }
            }
            itemsIndexed(
                items = queue.items,
                key = { index, item -> queueItemStableKey(index, item) },
                contentType = { _, _ -> "queue_item" }
            ) { index, item ->
                val itemKey = item.videoId
                val isCurrent = index == queue.currentIndex
                val isLockedNext = index == lockedNextIndex
                val isDragging = draggedItemKey == itemKey
                val dismissState = rememberSwipeToDismissBoxState()
                // Guard against double-removal: once dismissed, ignore subsequent LaunchedEffect runs
                var dismissed by remember { mutableStateOf(false) }

                LaunchedEffect(dismissState.currentValue) {
                    if (!dismissed && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        dismissed = true
                        val removeIndex = latestItems.indexOfFirst { it.videoId == itemKey }
                        if (removeIndex in latestItems.indices && latestItems.size > 1 && removeIndex != lockedNextIndex) {
                            onRemoveFromQueue(removeIndex)
                        }
                    }
                }

                Column {
                    SwipeToDismissBox(
                        modifier = Modifier.animateItem(),
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    else -> MaterialTheme.colorScheme.errorContainer
                                },
                                label = "swipe-bg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(color)
                            )
                        },
                        enableDismissFromStartToEnd = queue.items.size > 1 && draggedItemKey == null && !isLockedNext,
                        enableDismissFromEndToStart = queue.items.size > 1 && draggedItemKey == null && !isLockedNext
                    ) {
                        val bgColor = if (isCurrent) {
                            currentItemContainerColor
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .graphicsLayer {
                                    translationY = if (isDragging) draggedOffsetY else 0f
                                    shadowElevation = if (isDragging) 12f else 0f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                                .onSizeChanged {
                                    if (it.height > 0) itemHeightPx = it.height.toFloat()
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        enabled = draggedItemKey == null,
                                        onClick = { onSkipToIndex(index) },
                                        onLongClick = { selectedMenu = queueMenuModelFor(item, queue.source) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) {
                                            currentItemAccentColor
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = item.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) currentItemSecondaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item.durationText?.let { dur ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) currentItemSecondaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 32.dp, height = 40.dp)
                                    .pointerInput(itemKey, latestItems.size, itemHeightPx) {
                                        detectDragGestures(
                                            onDragStart = {
                                                if (latestItems.size <= 1) return@detectDragGestures
                                                val startIndex = latestItems.indexOfFirst { it.videoId == itemKey }
                                                if (startIndex == -1 || startIndex == lockedNextIndex) return@detectDragGestures
                                                draggedItemKey = itemKey
                                                draggedItemIndex = startIndex
                                                draggedOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggedItemKey = null
                                                draggedItemIndex = -1
                                                draggedOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedItemKey = null
                                                draggedItemIndex = -1
                                                draggedOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggedItemKey != itemKey || draggedItemIndex == -1) {
                                                    return@detectDragGestures
                                                }
                                                change.consume()
                                                val rowHeight = itemHeightPx
                                                if (rowHeight <= 0f) return@detectDragGestures

                                                draggedOffsetY += dragAmount.y
                                                val lastIndex = latestItems.lastIndex
                                                if (lastIndex < 0) return@detectDragGestures

                                                if (draggedItemIndex == 0 && draggedOffsetY < 0f) draggedOffsetY = 0f
                                                if (draggedItemIndex == lastIndex && draggedOffsetY > 0f) draggedOffsetY = 0f

                                                val threshold = rowHeight * 0.5f
                                                if (draggedOffsetY > threshold && draggedItemIndex < lastIndex) {
                                                    val targetIndex = draggedItemIndex + 1
                                                    if (targetIndex == lockedNextIndex) {
                                                        draggedOffsetY = threshold
                                                        return@detectDragGestures
                                                    }
                                                    onMoveInQueue(draggedItemIndex, targetIndex)
                                                    draggedItemIndex += 1
                                                    draggedOffsetY -= rowHeight
                                                } else if (draggedOffsetY < -threshold && draggedItemIndex > 0) {
                                                    val targetIndex = draggedItemIndex - 1
                                                    if (targetIndex == lockedNextIndex) {
                                                        draggedOffsetY = -threshold
                                                        return@detectDragGestures
                                                    }
                                                    onMoveInQueue(draggedItemIndex, targetIndex)
                                                    draggedItemIndex -= 1
                                                    draggedOffsetY += rowHeight
                                                }
                                            }
                                        )
                                    }
                                    .padding(start = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLockedNext) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Next song locked for crossfade",
                                        tint = QueueCurrentSecondaryColor
                                    )
                                } else {
                                    ReorderHandle()
                                }
                            }
                        }
                    }
                    // ── Crossfade connector between current and next song ──
                    if (crossfadeEnabled && isCurrent && index + 1 < queue.items.size) {
                        CrossfadeConnector()
                    }
                }
            }
        }
    }

    selectedMenu?.let { menu ->
        QueueItemActionsSheet(
            model = menu,
            onDismiss = { selectedMenu = null },
            onViewArtist = {
                val artistId = menu.artistId ?: return@QueueItemActionsSheet
                onArtistTap?.invoke(artistId, menu.artistName, null)
                onQueueActionConsumed?.invoke()
                selectedMenu = null
            },
            onViewAlbum = {
                val albumId = menu.albumId ?: return@QueueItemActionsSheet
                onAlbumTap?.invoke(albumId, menu.albumName ?: menu.title, null)
                onQueueActionConsumed?.invoke()
                selectedMenu = null
            },
            onShare = {
                val shareText = buildString {
                    append(menu.title)
                    if (menu.subtitle.isNotBlank()) {
                        append("\n")
                        append(menu.subtitle)
                    }
                    append("\n")
                    append(menu.shareUrl)
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
                selectedMenu = null
            },
            onDownload = {
                scope.launch {
                    val result = repository.download(
                        MediaItem(
                            videoId = menu.videoId,
                            title = menu.title,
                            artistName = menu.artistName,
                            artistId = menu.artistId,
                            albumName = menu.albumName,
                            albumId = menu.albumId,
                            thumbnailUrl = menu.thumbnailUrl,
                            durationText = null,
                            musicVideoType = menu.musicVideoType
                        )
                    )
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Downloaded: ${menu.title}" else "Download failed: ${menu.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                selectedMenu = null
            }
        )
    }
}

/**
 * Standalone queue sheet – kept for backward compatibility but no longer
 * used from MusicApp. The queue now lives inside the player's tabbed sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: PlaybackQueue,
    onDismiss: () -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        tonalElevation = 4.dp,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss)
                        .padding(6.dp)
                )
            }
            QueueContent(
                queue = queue,
                onSkipToIndex = onSkipToIndex,
                onRemoveFromQueue = onRemoveFromQueue,
                onMoveInQueue = onMoveInQueue,
                modifier = Modifier.fillMaxHeight(0.96f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemActionsSheet(
    model: QueueItemMenuModel,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreInfo by remember(model.shareUrl) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            QueueActionItem(
                label = "View Artist",
                icon = Icons.Rounded.Person,
                enabled = model.artistId != null,
                onClick = onViewArtist
            )
            QueueActionItem(
                label = "View Album",
                icon = Icons.Rounded.Album,
                enabled = model.albumId != null,
                onClick = onViewAlbum
            )
            QueueActionItem(
                label = "Share",
                icon = Icons.Rounded.Share,
                enabled = true,
                onClick = onShare
            )
            QueueActionItem(
                label = "Download",
                icon = Icons.Rounded.Download,
                enabled = true,
                onClick = onDownload
            )

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

            if (showMoreInfo && model.metadata.isNotEmpty()) {
                model.metadata.forEach { line ->
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
private fun QueueActionItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
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
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun ReorderHandle() {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(lineColor)
            )
            if (it < 2) Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun CrossfadeConnector() {
    val accentColor = LocalPlaybackUiPalette.current?.accent ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = accentColor.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        Text(
            text = "crossfade",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = accentColor.copy(alpha = 0.4f),
            thickness = 1.dp
        )
    }
}

private fun queueMenuModelFor(item: MediaItem, source: QueueSource): QueueItemMenuModel {
    val subtitle = listOfNotNull(
        item.artistName.takeIf { it.isNotBlank() },
        item.albumName?.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

    val sourceLabel = source.name
        .lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }

    return QueueItemMenuModel(
        title = item.title,
        subtitle = subtitle,
        artistName = item.artistName,
        artistId = item.artistId,
        albumName = item.albumName,
        albumId = item.albumId,
        thumbnailUrl = item.thumbnailUrl,
        shareUrl = "https://music.youtube.com/watch?v=${item.videoId}",
        metadata = listOfNotNull(
            item.artistName.takeIf { it.isNotBlank() }?.let { "Artist: $it" },
            item.albumName?.takeIf { it.isNotBlank() }?.let { "Album: $it" },
            item.durationText?.let { "Duration: $it" },
            "Video ID: ${item.videoId}",
            "Queue source: $sourceLabel"
        ),
        videoId = item.videoId,
        musicVideoType = item.musicVideoType
    )
}
