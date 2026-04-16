package com.proj.Musicality.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.data.local.DateSortOrder
import com.proj.Musicality.data.local.LibraryCollectionType
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.LibrarySnapshot
import com.proj.Musicality.data.local.SavedEntry
import com.proj.Musicality.data.local.SavedFilter
import com.proj.Musicality.R
import com.proj.Musicality.ui.components.HapticFilledTonalButton
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onOpenCollection: (LibraryCollectionType) -> Unit,
    onOpenArtist: (String, String, String?) -> Unit,
    onOpenPlaylist: (String, String, String?, String?) -> Unit,
    onOpenAlbum: (String, String, String?, String?, String?) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val playbackUiPalette = LocalPlaybackUiPalette.current
    val selectedTabColor = playbackUiPalette?.accent ?: MaterialTheme.colorScheme.primary
    val selectedTabContentColor = playbackUiPalette?.accent ?: MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()

    var selectedPrimaryTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSavedFilter by rememberSaveable { mutableStateOf(SavedFilter.ARTIST) }
    var sortOrder by rememberSaveable { mutableStateOf(DateSortOrder.NEWEST) }
    var isSavedEditMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.refresh()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                HapticFilledTonalButton(
                    onClick = {
                        if (selectedPrimaryTab != 1) {
                            selectedPrimaryTab = 1
                            isSavedEditMode = true
                        } else {
                            isSavedEditMode = !isSavedEditMode
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isSavedEditMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                        contentDescription = if (isSavedEditMode) "Done editing saved items" else "Edit saved items"
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSavedEditMode) "Done" else "Edit")
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedPrimaryTab,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedPrimaryTab, matchContentSize = true),
                        color = selectedTabColor
                    )
                }
            ) {
                Tab(
                    selected = selectedPrimaryTab == 0,
                    onClick = { selectedPrimaryTab = 0 },
                    selectedContentColor = selectedTabContentColor,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("You", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedPrimaryTab == 1,
                    onClick = { selectedPrimaryTab = 1 },
                    selectedContentColor = selectedTabContentColor,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("Saved", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedPrimaryTab == 0) {
                YouLibrarySection(
                    snapshot = snapshot,
                    onOpenCollection = onOpenCollection,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SavedLibrarySection(
                    snapshot = snapshot,
                    selectedSavedFilter = selectedSavedFilter,
                    sortOrder = sortOrder,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onFilterSelect = { selectedSavedFilter = it },
                    onSortOrderSelect = { sortOrder = it },
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenAlbum = onOpenAlbum,
                    isEditMode = isSavedEditMode,
                    onRemoveEntry = { entry ->
                        scope.launch {
                            repository.removeSavedEntry(entry)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun YouLibrarySection(
    snapshot: LibrarySnapshot,
    onOpenCollection: (LibraryCollectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val collections = remember(snapshot) {
        listOf(
            Triple("Songs", "${snapshot.likedSongs.size} items", LibraryCollectionType.LIKED),
            Triple("Top Songs", "${snapshot.topSongs.size} items", LibraryCollectionType.TOP_SONGS),
            Triple("Downloads", "${snapshot.downloadedMedia.size} items", LibraryCollectionType.DOWNLOADED)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(collections, key = { it.third.name }) { (title, subtitle, type) ->
            CollectionSummaryCard(
                title = title,
                subtitle = subtitle,
                icon = {
                    when (type) {
                        LibraryCollectionType.LIKED -> Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(42.dp)
                        )
                        LibraryCollectionType.TOP_SONGS -> Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(42.dp)
                        )
                        LibraryCollectionType.DOWNLOADED -> Icon(
                            painter = painterResource(id = R.drawable.download_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                },
                onClick = { onOpenCollection(type) }
            )
        }
    }
}

@Composable
private fun SavedLibrarySection(
    snapshot: LibrarySnapshot,
    selectedSavedFilter: SavedFilter,
    sortOrder: DateSortOrder,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onFilterSelect: (SavedFilter) -> Unit,
    onSortOrderSelect: (DateSortOrder) -> Unit,
    onOpenArtist: (String, String, String?) -> Unit,
    onOpenPlaylist: (String, String, String?, String?) -> Unit,
    onOpenAlbum: (String, String, String?, String?, String?) -> Unit,
    isEditMode: Boolean,
    onRemoveEntry: (SavedEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val backdropPalette = LocalPlaybackBackdropPalette.current
    val segmentedActiveContainerColor = backdropPalette?.middle
        ?: MaterialTheme.colorScheme.secondaryContainer
    val segmentedActiveContentColor = Color.White

    val filteredEntries = remember(snapshot, selectedSavedFilter, sortOrder) {
        val base = snapshot.entriesFor(selectedSavedFilter)
        if (sortOrder == DateSortOrder.NEWEST) {
            base.sortedByDescending { it.dateAdded }
        } else {
            base.sortedBy { it.dateAdded }
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SavedFilter.entries.forEachIndexed { index, filter ->
                        SegmentedButton(
                            selected = selectedSavedFilter == filter,
                            onClick = { onFilterSelect(filter) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SavedFilter.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = segmentedActiveContainerColor,
                                activeContentColor = segmentedActiveContentColor
                            ),
                            label = {
                                Text(
                                    text = filter.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Box {
                    HapticIconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Sort options"
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest first") },
                            onClick = {
                                onSortOrderSelect(DateSortOrder.NEWEST)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest first") },
                            onClick = {
                                onSortOrderSelect(DateSortOrder.OLDEST)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved ${selectedSavedFilter.name.lowercase()} items yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            item {
                MasonrySavedGrid(
                    entries = filteredEntries,
                    selectedSavedFilter = selectedSavedFilter,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onEntryClick = { entry ->
                        when (selectedSavedFilter) {
                            SavedFilter.ARTIST -> onOpenArtist(entry.title, entry.id, entry.thumbnailUrl)
                            SavedFilter.PLAYLIST -> onOpenPlaylist(entry.title, entry.id, entry.subtitle, entry.thumbnailUrl)
                            SavedFilter.ALBUM -> onOpenAlbum(entry.title, entry.id, entry.subtitle, entry.thumbnailUrl, entry.year)
                        }
                    },
                    isEditMode = isEditMode,
                    onRemoveEntry = onRemoveEntry
                )
            }
        }
    }
}

// Spring spec for the masonry sort animation — low bounce, medium-low stiffness matches
// the rest of the app's spatial motion style (same as MediaBoundsSpring).
private val MasonrySortBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

@Composable
private fun MasonrySavedGrid(
    entries: List<SavedEntry>,
    selectedSavedFilter: SavedFilter,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isEditMode: Boolean,
    onEntryClick: (SavedEntry) -> Unit,
    onRemoveEntry: (SavedEntry) -> Unit
) {
    if (entries.isEmpty()) return

    // LookaheadScope provides the "target layout" pass that animateBounds needs to
    // compute where each card is heading before the actual layout renders.
    LookaheadScope {
        MasonryLayout(spacing = 12.dp) {
            entries.forEach { entry ->
                // key(entry.id) keeps the same composable instance alive as items
                // reorder — animateBounds then detects the bounds change and fires
                // a spring animation from the old slot position/size to the new one.
                key(entry.id) {
                    MasonryCard(
                        entry = entry,
                        selectedSavedFilter = selectedSavedFilter,
                        animatedVisibilityScope = animatedVisibilityScope,
                        isEditMode = isEditMode,
                        modifier = Modifier.animateBounds(
                            lookaheadScope = this@LookaheadScope,
                            boundsTransform = MasonrySortBoundsTransform
                        ),
                        onClick = onEntryClick,
                        onRequestRemove = onRemoveEntry
                    )
                }
            }
        }
    }
}

// Custom layout that positions every card as a direct child so animateBounds can
// track cross-slot movements in a single coordinate space.
//
// Slot layout (0-indexed):
//   0  → left half,  258 dp tall  (tall hero card)
//   1  → right half, 123 dp tall  (upper-right)
//   2  → right half, 123 dp tall  (lower-right, below 1)
//   3  → left half,  160 dp tall  (row 2 left)
//   4  → right half, 160 dp tall  (row 2 right)
//   5  → left half,  160 dp tall  (row 3 left)
//   6  → right half, 160 dp tall  (row 3 right)
//   7+ → alternating columns, 160 dp each
@Composable
private fun MasonryLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.roundToPx() }
    val h258 = with(density) { 258.dp.roundToPx() }
    val h123 = with(density) { 123.dp.roundToPx() }
    val h160 = with(density) { 160.dp.roundToPx() }

    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val totalWidth = constraints.maxWidth
        val halfWidth = (totalWidth - spacingPx) / 2
        val rightX = halfWidth + spacingPx

        // Returns [x, y, width, height] in pixels for each slot index.
        fun slotOf(index: Int): IntArray = when (index) {
            0 -> intArrayOf(0,      0,                                   halfWidth, h258)
            1 -> intArrayOf(rightX, 0,                                   halfWidth, h123)
            2 -> intArrayOf(rightX, h123 + spacingPx,                    halfWidth, h123)
            3 -> intArrayOf(0,      h258 + spacingPx,                    halfWidth, h160)
            4 -> intArrayOf(rightX, h258 + spacingPx,                    halfWidth, h160)
            5 -> intArrayOf(0,      h258 + spacingPx + h160 + spacingPx, halfWidth, h160)
            6 -> intArrayOf(rightX, h258 + spacingPx + h160 + spacingPx, halfWidth, h160)
            else -> {
                val rem   = index - 7
                val col   = rem % 2
                val row   = rem / 2
                // baseY is directly below the three fixed rows + spacing
                val baseY = h258 + spacingPx + h160 + spacingPx + h160 + spacingPx
                intArrayOf(
                    if (col == 0) 0 else rightX,
                    baseY + row * (h160 + spacingPx),
                    halfWidth,
                    h160
                )
            }
        }

        val slots     = measurables.indices.map { slotOf(it) }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(slots[i][2], slots[i][3]))
        }
        val totalHeight = if (slots.isEmpty()) 0 else slots.maxOf { it[1] + it[3] }

        layout(totalWidth, totalHeight) {
            placeables.forEachIndexed { i, p ->
                p.placeRelative(slots[i][0], slots[i][1])
            }
        }
    }
}

@Composable
private fun MasonryCard(
    entry: SavedEntry,
    selectedSavedFilter: SavedFilter,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isEditMode: Boolean,
    modifier: Modifier,
    onClick: (SavedEntry) -> Unit,
    onRequestRemove: (SavedEntry) -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val sharedElementKey = when (selectedSavedFilter) {
        SavedFilter.ALBUM -> "thumb-album-${entry.id}"
        SavedFilter.PLAYLIST -> "thumb-playlist-${entry.id}"
        SavedFilter.ARTIST -> null
    }

    Card(
        modifier = modifier
            .clickable { onClick(entry) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!entry.thumbnailUrl.isNullOrBlank()) {
                val thumbnailModifier = if (sharedElementKey != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = sharedElementKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = MasonrySortBoundsTransform
                        )
                    }
                } else {
                    Modifier
                }
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier = thumbnailModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.84f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }

            if (isEditMode) {
                HapticIconButton(
                    onClick = { onRequestRemove(entry) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = "Delete saved item",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.subtitle.isNullOrBlank()) {
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSummaryCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
