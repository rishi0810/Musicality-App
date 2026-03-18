package com.proj.Musicality.ui.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.model.ArtistContent
import com.proj.Musicality.data.model.ArtistDetails
import com.proj.Musicality.data.model.ArtistRelated
import com.proj.Musicality.data.model.ArtistSong
import com.proj.Musicality.data.model.ArtistVideo
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.model.toMediaItem
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.ErrorMessage
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.ShimmerSection
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.ArtistViewModel
import com.proj.Musicality.viewmodel.ArtistViewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "ArtistScreen"

private data class ArtistDerivedCollections(
    val playableItems: List<MediaItem>,
    val topSongsPreview: List<ArtistSong>,
    val topSongQueueItems: List<MediaItem>,
    val topSongIndexById: Map<String, Int>,
    val videosPreview: List<ArtistVideo>
)

private data class ArtistTopSongMenuModel(
    val title: String,
    val subtitle: String?,
    val artistName: String,
    val artistId: String,
    val albumName: String?,
    val albumId: String? = null,
    val thumb: String?,
    val videoId: String,
    val metadata: List<String> = emptyList()
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    seed: Route.Artist,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onAlbumTap: (String, String, String?, String?, String?) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onSimilarArtistTap: (String, String, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val viewModel: ArtistViewModel = viewModel(
        key = seed.browseId,
        factory = ArtistViewModelFactory(seed.browseId)
    )

    LaunchedEffect(seed.browseId) {
        viewModel.initialize(seed)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val reusableSeedThumbUrl = upscaleThumbnail(seed.thumbnailUrl)
    val hasReusableSeedThumb = !reusableSeedThumbUrl.isNullOrBlank()
    val fetchedThumbUrl = when (val s = state) {
        is ArtistViewModel.UiState.Seed -> upscaleThumbnail(s.thumbnailUrl, size = 1200)
        is ArtistViewModel.UiState.Loaded -> upscaleThumbnail(
            s.details.thumbnails.maxByOrNull { it.width }?.url,
            size = 1200
        )
        else -> null
    }
    val thumbUrl = reusableSeedThumbUrl ?: fetchedThumbUrl
    LaunchedEffect(thumbUrl) {
        Log.d(TAG, "Artist hero artwork url=$thumbUrl")
    }
    val seedStateName = (state as? ArtistViewModel.UiState.Seed)?.name
    val seedStateAudience = (state as? ArtistViewModel.UiState.Seed)?.audienceText
    val loadedDetails = (state as? ArtistViewModel.UiState.Loaded)?.details
    val loadedName = loadedDetails?.name
    val artistName = listOf(seed.name, seedStateName, loadedName)
        .firstOrNull { !it.isNullOrBlank() }
        ?: "Artist"
    val subscribers = loadedDetails?.viewCount ?: seedStateAudience ?: seed.audienceText

    val derivedCollections = remember(
        loadedDetails?.topSongs,
        loadedDetails?.videos,
        loadedDetails?.name,
        seed.browseId
    ) {
        loadedDetails?.let { details ->
            ArtistDerivedCollections(
                playableItems = details.playableItems(seed.browseId),
                topSongsPreview = details.topSongs.take(5),
                topSongQueueItems = details.topSongs.map { it.toMediaItem(details.name, seed.browseId) },
                topSongIndexById = details.topSongs
                    .mapIndexed { index, song -> song.videoId to index }
                    .toMap(),
                videosPreview = details.videos.take(5)
            )
        }
    }

    val scope = rememberCoroutineScope()
    var selectedTopSongMenu by remember { mutableStateOf<ArtistTopSongMenuModel?>(null) }
    val sectionSpacing = 20.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
    ) {
        item(key = "hero-header") {
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val heroModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = "thumb-artist-${seed.browseId}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = MediaBoundsSpring
                )
            }

            ArtistHeroHeader(
                name = artistName,
                subtitle = subscribers,
                thumbnailUrl = thumbUrl,
                allowNetworkFetch = !hasReusableSeedThumb,
                surfaceColor = surfaceColor,
                statusBarTop = statusBarTop,
                onAdd = {
                    scope.launch {
                        repository.rememberArtist(
                            artistId = seed.browseId,
                            name = loadedDetails?.name ?: seed.name,
                            thumbnailUrl = loadedDetails
                                ?.thumbnails
                                ?.maxByOrNull { it.width }
                                ?.url
                                ?: seed.thumbnailUrl
                        )
                    }
                },
                heroModifier = heroModifier
            )
        }

        when (val s = state) {
            is ArtistViewModel.UiState.Seed -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(3, key = { "shimmer-$it" }) { ShimmerSection() }
            }

            is ArtistViewModel.UiState.Loaded -> {
                val details = s.details
                val playableItems = derivedCollections?.playableItems ?: details.playableItems(seed.browseId)
                val topSongsPreview = derivedCollections?.topSongsPreview ?: details.topSongs.take(5)
                val topSongQueueItems = derivedCollections?.topSongQueueItems
                    ?: details.topSongs.map { it.toMediaItem(details.name, seed.browseId) }
                val topSongIndexById = derivedCollections?.topSongIndexById
                    ?: details.topSongs.mapIndexed { index, song -> song.videoId to index }.toMap()
                val videosPreview = derivedCollections?.videosPreview ?: details.videos.take(5)

                if (playableItems.isNotEmpty()) {
                    item(key = "action-buttons") {
                        ArtistActionButtons(
                            onPlay = {
                                onSongTap(
                                    playableItems.first(),
                                    PlaybackQueue(
                                        items = playableItems,
                                        currentIndex = 0,
                                        source = QueueSource.ARTIST_TOP_SONGS
                                    )
                                )
                            },
                            onShuffle = {
                                val shuffled = playableItems.shuffled()
                                onSongTap(
                                    shuffled.first(),
                                    PlaybackQueue(
                                        items = shuffled,
                                        currentIndex = 0,
                                        source = QueueSource.ARTIST_TOP_SONGS
                                    )
                                )
                            }
                        )
                    }
                }

                if (topSongsPreview.isNotEmpty()) {
                    item(key = "top-songs-header") { SectionHeader("Top Songs", modifier = Modifier.padding(top = 8.dp)) }
                    val artistApiThumb = details.thumbnails.maxByOrNull { it.width }?.url
                    items(topSongsPreview, key = { it.videoId }) { song ->
                        val songItem = song.toMediaItem(details.name, seed.browseId)
                        SongListItem(
                            title = song.title,
                            subtitle = song.plays ?: song.album,
                            thumbnailUrl = song.image,
                            sharedElementKey = "thumb-${song.videoId}",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOverflowClick = {
                                selectedTopSongMenu = ArtistTopSongMenuModel(
                                    title = song.title,
                                    subtitle = song.plays ?: song.album,
                                    artistName = details.name,
                                    artistId = seed.browseId,
                                    albumName = song.album,
                                    thumb = artistApiThumb,
                                    videoId = song.videoId,
                                    metadata = listOfNotNull(
                                        "Artist: ${details.name}",
                                        song.album?.takeIf { it.isNotBlank() }?.let { "Album: $it" },
                                        song.plays?.takeIf { it.isNotBlank() }?.let { "Plays: $it" },
                                        "Video ID: ${song.videoId}"
                                    )
                                )
                            },
                            onClick = {
                                val queue = PlaybackQueue(
                                    items = topSongQueueItems,
                                    currentIndex = topSongIndexById[song.videoId] ?: 0,
                                    source = QueueSource.ARTIST_TOP_SONGS
                                )
                                onSongTap(songItem, queue)
                            }
                        )
                    }
                    item(key = "top-songs-spacer") { Spacer(Modifier.height(sectionSpacing)) }
                }

                if (details.albums.isNotEmpty()) {
                    item(key = "albums-header") { SectionHeader("Albums", modifier = Modifier.padding(top = 4.dp)) }
                    item(key = "albums-grid") {
                        ArtistContentGrid(
                            items = details.albums,
                            keyPrefix = "thumb-album-",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onItemTap = { album ->
                                onAlbumTap(album.title, album.browseId, details.name, album.image, album.year)
                            }
                        )
                    }
                    item(key = "albums-spacer") { Spacer(Modifier.height(sectionSpacing)) }
                }

                if (details.singles.isNotEmpty()) {
                    item(key = "singles-header") { SectionHeader("Singles", modifier = Modifier.padding(top = 4.dp)) }
                    item(key = "singles-grid") {
                        ArtistContentGrid(
                            items = details.singles,
                            keyPrefix = "thumb-album-",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onItemTap = { single ->
                                onAlbumTap(single.title, single.browseId, details.name, single.image, single.year)
                            }
                        )
                    }
                    item(key = "singles-spacer") { Spacer(Modifier.height(sectionSpacing)) }
                }

                if (videosPreview.isNotEmpty()) {
                    item(key = "videos-header") { SectionHeader("Videos", modifier = Modifier.padding(top = 4.dp)) }
                    items(videosPreview, key = { it.videoId }) { video ->
                        val videoItem = video.toMediaItem(details.name, seed.browseId)
                        SongListItem(
                            title = video.title,
                            subtitle = video.views,
                            thumbnailUrl = video.image,
                            sharedElementKey = "thumb-${video.videoId}",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onVideoTap(videoItem) }
                        )
                    }
                    item(key = "videos-spacer") { Spacer(Modifier.height(sectionSpacing)) }
                }

                if (details.playlists.isNotEmpty()) {
                    item(key = "playlists-header") { SectionHeader("Playlists", modifier = Modifier.padding(top = 4.dp)) }
                    item(key = "playlists-grid") {
                        ArtistContentGrid(
                            items = details.playlists,
                            keyPrefix = "thumb-playlist-",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onItemTap = { playlist ->
                                onPlaylistTap(playlist.title, playlist.browseId, null, playlist.image)
                            }
                        )
                    }
                    item(key = "playlists-spacer") { Spacer(Modifier.height(sectionSpacing)) }
                }

                if (details.similarArtists.isNotEmpty()) {
                    item(key = "similar-header") { SectionHeader("Fans Might Also Like", modifier = Modifier.padding(top = 4.dp)) }
                    item(key = "similar-row") {
                        SimilarArtistsRow(
                            items = details.similarArtists,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onArtistTap = onSimilarArtistTap
                        )
                    }
                }
            }

            is ArtistViewModel.UiState.Error -> {
                item(key = "error") { ErrorMessage(s.message) }
            }
        }
    }
    }

    selectedTopSongMenu?.let { menu ->
        ArtistTopSongActionsSheet(
            model = menu,
            onDismiss = { selectedTopSongMenu = null },
            onViewArtist = {
                onSimilarArtistTap(menu.artistName, menu.artistId, null)
                selectedTopSongMenu = null
            },
            onViewAlbum = {
                val albumId = menu.albumId ?: return@ArtistTopSongActionsSheet
                onAlbumTap(menu.albumName ?: menu.title, albumId, menu.artistName, null, null)
                selectedTopSongMenu = null
            },
            onShare = {
                val shareText = buildString {
                    append(menu.title)
                    if (!menu.subtitle.isNullOrBlank()) {
                        append("\n")
                        append(menu.subtitle)
                    }
                    append("\n")
                    append("https://music.youtube.com/watch?v=${menu.videoId}")
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
                selectedTopSongMenu = null
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
                            thumbnailUrl = menu.thumb,
                            durationText = null,
                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                        )
                    )
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Downloaded: ${menu.title}" else "Download failed: ${menu.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                selectedTopSongMenu = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistTopSongActionsSheet(
    model: ArtistTopSongMenuModel,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                text = "Song",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            ArtistTopSongActionItem(
                label = "View Artist",
                icon = Icons.Rounded.Person,
                enabled = true,
                onClick = onViewArtist
            )
            ArtistTopSongActionItem(
                label = "View Album",
                icon = Icons.Rounded.Album,
                enabled = model.albumId != null,
                onClick = onViewAlbum
            )
            ArtistTopSongActionItem(
                label = "Share",
                icon = Icons.Rounded.Share,
                enabled = true,
                onClick = onShare
            )
            ArtistTopSongActionItem(
                label = "Download",
                icon = Icons.Rounded.Download,
                enabled = true,
                onClick = onDownload
            )

            if (model.metadata.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
private fun ArtistTopSongActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun ArtistHeroHeader(
    name: String,
    subtitle: String?,
    thumbnailUrl: String?,
    allowNetworkFetch: Boolean,
    surfaceColor: Color,
    statusBarTop: androidx.compose.ui.unit.Dp,
    onAdd: () -> Unit,
    heroModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroRequest = remember(context, thumbnailUrl, allowNetworkFetch) {
        ImageRequest.Builder(context).apply {
            if (!allowNetworkFetch) {
                networkCachePolicy(CachePolicy.DISABLED)
            }
        }
            .data(thumbnailUrl)
            .size(Size(1200, 1200))
            .crossfade(false)
            .build()
    }
    val imageHeight = 350.dp + statusBarTop

    Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(surfaceColor)
    ) {
        AsyncImage(
            model = heroRequest,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = heroModifier
                .fillMaxWidth()
                .height(imageHeight)
        )

        val artistGradientStops = remember(surfaceColor) {
            arrayOf(
                0f to Color.Transparent,
                0.55f to surfaceColor.copy(alpha = 0.75f),
                1f to surfaceColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colorStops = artistGradientStops))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = onAdd
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add"
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArtistActionButtons(
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shuffle", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArtistContentGrid(
    items: List<ArtistContent>,
    keyPrefix: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onItemTap: (ArtistContent) -> Unit
) {
    if (items.size <= 2) {
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(rowState),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(items, key = { it.browseId }) { item ->
                ContentCard(
                    title = item.title,
                    subtitle = item.year,
                    thumbnailUrl = item.image,
                    sharedElementKey = "$keyPrefix${item.browseId}",
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { onItemTap(item) }
                )
            }
        }
    } else {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.height(480.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(items, key = { it.browseId }) { item ->
                ContentCard(
                    title = item.title,
                    subtitle = item.year,
                    thumbnailUrl = item.image,
                    sharedElementKey = "$keyPrefix${item.browseId}",
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { onItemTap(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SimilarArtistsRow(
    items: List<ArtistRelated>,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onArtistTap: (String, String, String?) -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items, key = { it.browseId }) { artist ->
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .clickable { onArtistTap(artist.name, artist.browseId, artist.image) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val artistModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "thumb-artist-${artist.browseId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = MediaBoundsSpring
                    )
                }
                Thumbnail(
                    url = artist.image,
                    size = 104.dp,
                    cornerRadius = 52.dp,
                    modifier = artistModifier
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun ArtistDetails.playableItems(artistId: String): List<MediaItem> {
    if (topSongs.isNotEmpty()) {
        return topSongs.map { it.toMediaItem(name, artistId) }
    }
    if (videos.isNotEmpty()) {
        return videos.map { it.toMediaItem(name, artistId) }
    }
    return emptyList()
}

private fun ArtistVideo.toMediaItem(
    artistName: String,
    artistId: String
): MediaItem {
    return MediaItem(
        videoId = videoId,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = null,
        albumId = null,
        thumbnailUrl = image,
        durationText = null,
        musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
    )
}
