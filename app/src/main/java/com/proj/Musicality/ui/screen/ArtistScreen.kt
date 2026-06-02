package com.proj.Musicality.ui.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
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
import com.proj.Musicality.data.local.MediaDownloadState
import com.proj.Musicality.data.local.MediaLibraryState
import com.proj.Musicality.data.local.SavedEntry
import com.proj.Musicality.data.local.SavedEntryType
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
import com.proj.Musicality.ui.components.HapticFilledTonalButton
import com.proj.Musicality.ui.components.HapticFilledTonalIconButton
import com.proj.Musicality.ui.components.HapticOutlinedButton
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.theme.AppColors
import com.proj.Musicality.ui.theme.AppShapes
import com.proj.Musicality.ui.theme.AppTypography
import com.proj.Musicality.ui.theme.GradientTheme
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.ui.theme.defaultMediaBackdropPalette
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.ArtistViewModel
import com.proj.Musicality.viewmodel.ArtistViewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "ArtistScreen"
private val ArtistHeaderBaseHeight = 350.dp

private data class ArtistBackdropGeometry(
    val scrollY: Float,
    val contentHeight: Float
)

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
    onMoreTap: (String, String, String, String?, String) -> Unit,
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
    val density = LocalDensity.current

    val reusableSeedThumbUrl = seed.thumbnailUrl
    val hasReusableSeedThumb = !reusableSeedThumbUrl.isNullOrBlank()
    val fetchedThumbUrl = when (val s = state) {
        is ArtistViewModel.UiState.Seed -> s.thumbnailUrl
        is ArtistViewModel.UiState.Loaded -> s.details.thumbnails.maxByOrNull { it.width }?.url
        else -> null
    }
    val thumbUrl = reusableSeedThumbUrl ?: fetchedThumbUrl
    val mediaPalette = rememberMediaBackdropPalette(
        imageUrl = thumbUrl,
        fallbackSurface = surfaceColor,
        // Always allow network for the (tiny 256px) palette image so the color reliably
        // resolves even when the seed thumb wasn't cached yet (e.g. a fast tap on a
        // search suggestion). It still hits memory/disk first, so a cached image costs
        // no network — the hero image keeps its own cache-first policy below.
        allowNetworkFetch = true,
        animateTransitions = false
    )
    // The backdrop gradient + image masking render IMMEDIATELY against a black
    // placeholder, so the page never waits on data. These detail screens render white
    // foreground text over the gradient, so a black base keeps the fade legible. The
    // most-present artwork color — available only once the image loads, or once the
    // artist API returns a thumbnail on no-thumbnail entry paths — then crossfades in
    // on top via the tween. Until the palette genuinely resolves we hold the placeholder
    // rather than its near-black default, so the pop-in is a deliberate transition, not
    // an incidental one.
    val themeBaseColor = Color(0xFF000000)
    val pageEndColor = themeBaseColor
    val unresolvedDominant = remember(surfaceColor) { defaultMediaBackdropPalette(surfaceColor).top }
    val dominantColor by animateColorAsState(
        targetValue = if (mediaPalette.top == unresolvedDominant) themeBaseColor else mediaPalette.top,
        animationSpec = tween(500),
        label = "artistDominantColor"
    )
    val imageHeightPx = with(density) { (ArtistHeaderBaseHeight + statusBarTop).toPx() }

    // Anchor the backdrop gradient in content space (not the viewport) so it spans
    // the whole page: dominant at the image seam, fully black at the bottom of the
    // last section. Below-fold item heights are estimated from the running average
    // and refined to exact values as they scroll into view.
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { entries -> entries.forEach { (index, size) -> itemHeights[index] = size } }
    }
    val backdropGeometry by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val totalItems = info.totalItemsCount
            if (visible.isEmpty() || totalItems == 0) {
                ArtistBackdropGeometry(scrollY = 0f, contentHeight = imageHeightPx + 1f)
            } else {
                val avg = if (itemHeights.isNotEmpty()) {
                    itemHeights.values.sum().toFloat() / itemHeights.size
                } else 0f
                fun heightOf(index: Int): Float = itemHeights[index]?.toFloat() ?: avg
                val first = visible.first()
                val before = (0 until first.index).fold(0f) { acc, i -> acc + heightOf(i) }
                ArtistBackdropGeometry(
                    scrollY = (before - first.offset).coerceAtLeast(0f),
                    contentHeight = (0 until totalItems)
                        .fold(0f) { acc, i -> acc + heightOf(i) }
                        .coerceAtLeast(imageHeightPx + 1f)
                )
            }
        }
    }
    val animatedContentHeight by animateFloatAsState(
        targetValue = backdropGeometry.contentHeight,
        animationSpec = tween(450),
        label = "artistBackdropContentHeight"
    )
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
    val subscribers = seedStateAudience ?: seed.audienceText
    val librarySnapshot by repository.snapshot.collectAsStateWithLifecycle()
    val downloadStates by repository.downloadStates.collectAsStateWithLifecycle()

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
            .drawBehind {
                // Virtual gradient in content space: dominant at the image seam
                // (contentY = imageHeightPx) → black at the content bottom. Sampled with
                // a smoothstep ramp so it barely moves off dominant right under the seam
                // (no bright line where the masked image meets the page) yet still settles
                // fully into black by the last section. Multiple stops since smoothstep
                // isn't linear across the viewport.
                val gradientTop = imageHeightPx
                // Floor the span at the on-screen area below the image so the gradient
                // never collapses into a thin strip while content is still loading (when
                // contentHeight is just the hero + spinner). It fills the viewport and
                // fades to black immediately, then expands to the true content length once
                // the sections arrive — so the colored gradient shows during the load, not
                // only after it.
                val minSpan = (size.height - gradientTop).coerceAtLeast(1f)
                val span = (animatedContentHeight - gradientTop).coerceAtLeast(minSpan)
                val scrollY = backdropGeometry.scrollY
                val stopCount = 8
                val colorStops = Array(stopCount + 1) { i ->
                    val frac = i.toFloat() / stopCount
                    val t = (((scrollY + frac * size.height) - gradientTop) / span).coerceIn(0f, 1f)
                    val eased = t * t * (3f - 2f * t)
                    frac to lerp(dominantColor, pageEndColor, eased)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = colorStops,
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
    ) {
    GradientTheme {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight)
    ) {
        item(key = "hero-header") {
            val heroModifier = Modifier

            val savedArtistEntry = librarySnapshot.artists.firstOrNull { it.id == seed.browseId }
            val isArtistSaved = savedArtistEntry != null
            ArtistHeroHeader(
                name = artistName,
                subtitle = subscribers,
                thumbnailUrl = thumbUrl,
                allowNetworkFetch = !hasReusableSeedThumb,
                surfaceColor = surfaceColor,
                blendPrimaryColor = dominantColor,
                statusBarTop = statusBarTop,
                isSaved = isArtistSaved,
                onAdd = {
                    scope.launch {
                        if (savedArtistEntry != null) {
                            repository.removeSavedEntry(savedArtistEntry)
                        } else {
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
                            downloadState = downloadStates[song.videoId],
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
                    item(key = "singles-header") {
                        SectionHeaderWithArrow(
                            title = "Singles",
                            showArrow = details.singlesMoreEndpoint != null,
                            onArrowClick = {
                                details.singlesMoreEndpoint?.let { ep ->
                                    onMoreTap(details.name, "Singles", ep.browseId, ep.params, "singles")
                                }
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
                    item(key = "videos-header") {
                        SectionHeaderWithArrow(
                            title = "Videos",
                            showArrow = details.videosMoreEndpoint != null,
                            onArrowClick = {
                                details.videosMoreEndpoint?.let { ep ->
                                    onMoreTap(details.name, "Videos", ep.browseId, ep.params, "videos")
                                }
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(videosPreview, key = { it.videoId }) { video ->
                        val videoItem = video.toMediaItem(details.name, seed.browseId)
                        SongListItem(
                            title = video.title,
                            subtitle = video.views,
                            thumbnailUrl = video.image,
                            downloadState = downloadStates[video.videoId],
                            sharedElementKey = "thumb-${video.videoId}",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOverflowClick = {
                                selectedTopSongMenu = ArtistTopSongMenuModel(
                                    title = video.title,
                                    subtitle = video.views,
                                    artistName = details.name,
                                    artistId = seed.browseId,
                                    albumName = null,
                                    thumb = video.image,
                                    videoId = video.videoId,
                                    metadata = listOfNotNull(
                                        "Artist: ${details.name}",
                                        video.views?.takeIf { it.isNotBlank() }?.let { "Views: $it" },
                                        "Video ID: ${video.videoId}"
                                    )
                                )
                            },
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
    } // GradientTheme
    } // Box

    selectedTopSongMenu?.let { menu ->
        val mediaState by remember(menu.videoId) {
            repository.observeMediaState(menu.videoId)
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        val menuMediaItem = MediaItem(
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
        ArtistTopSongActionsSheet(
            model = menu,
            isLiked = mediaState.isLiked,
            downloadState = downloadStates[menu.videoId],
            onDismiss = { selectedTopSongMenu = null },
            onToggleLike = {
                scope.launch { repository.toggleLike(menuMediaItem) }
                selectedTopSongMenu = null
            },
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
                    selectedTopSongMenu = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistTopSongActionsSheet(
    model: ArtistTopSongMenuModel,
    isLiked: Boolean,
    downloadState: MediaDownloadState?,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreInfo by remember(model.videoId, model.title) { mutableStateOf(false) }
    val infoLines = remember(model) {
        buildList {
            add("Title: ${model.title}")
            model.subtitle?.takeIf { it.isNotBlank() }?.let { add("Info: $it") }
            addAll(model.metadata)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.bottomSheet(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ArtistTopSongSheetHeader(model = model)
            HorizontalDivider()
            ArtistTopSongActionItem(
                label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                supportingText = if (isLiked) "Take it out of your Liked songs" else "Save this track in Liked",
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                enabled = true,
                onClick = onToggleLike
            )
            ArtistTopSongActionItem(
                label = "View Artist",
                supportingText = "More from ${model.artistName}",
                icon = Icons.Rounded.Person,
                leadingThumbnailUrl = model.thumb,
                leadingThumbnailShape = CircleShape,
                enabled = true,
                onClick = onViewArtist
            )
            ArtistTopSongActionItem(
                label = "View Album",
                supportingText = model.albumName ?: "Open album",
                icon = Icons.Rounded.Album,
                leadingThumbnailUrl = model.thumb,
                leadingThumbnailShape = RoundedCornerShape(8.dp),
                enabled = model.albumId != null,
                onClick = onViewAlbum
            )
            ArtistTopSongActionItem(
                label = "Share",
                supportingText = "Send a link to this song",
                icon = Icons.Rounded.Share,
                enabled = true,
                onClick = onShare
            )
            ArtistTopSongActionItem(
                label = when {
                    downloadState?.isDownloading == true -> "Downloading ${(downloadState.progress * 100).toInt()}%"
                    downloadState?.isDownloaded == true -> "Downloaded"
                    else -> "Download"
                },
                supportingText = when {
                    downloadState?.isDownloading == true -> "${(downloadState.progress * 100).toInt()}% complete"
                    downloadState?.isDownloaded == true -> "Saved for offline"
                    else -> "Listen without a connection"
                },
                icon = Icons.Rounded.Download,
                enabled = downloadState?.isDownloading != true,
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
private fun ArtistTopSongActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    leadingThumbnailUrl: String? = null,
    leadingThumbnailShape: androidx.compose.ui.graphics.Shape? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    val useThumbnail = !leadingThumbnailUrl.isNullOrBlank() && leadingThumbnailShape != null
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
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
        leadingContent = {
            if (useThumbnail) {
                AsyncImage(
                    model = upscaleThumbnail(leadingThumbnailUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(leadingThumbnailShape!!)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.hapticClickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun ArtistTopSongSheetHeader(model: ArtistTopSongMenuModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!model.thumb.isNullOrBlank()) {
            AsyncImage(
                model = upscaleThumbnail(model.thumb),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(AppShapes.thumbnailLarge())
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val secondary = listOfNotNull(
                model.artistName.takeIf { it.isNotBlank() },
                model.albumName?.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistHeroHeader(
    name: String,
    subtitle: String?,
    thumbnailUrl: String?,
    allowNetworkFetch: Boolean,
    surfaceColor: Color,
    blendPrimaryColor: Color,
    statusBarTop: androidx.compose.ui.unit.Dp,
    isSaved: Boolean,
    onAdd: () -> Unit,
    heroModifier: Modifier = Modifier
) {
    val ctaContainerColor = AppColors.ArtistCtaContainer
    val ctaContentColor = AppColors.ArtistCtaContent
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
    val imageHeight = ArtistHeaderBaseHeight + statusBarTop

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

        // Same masking logic as the player (song) screen: a long opacity ramp that
        // dissolves the artwork into the dominant color. Reaches fully solid by 0.9 so
        // the bottom strip is clean dominant (no darker image bleed lingering into the
        // seam), which is what made the hero/page boundary read as a hard line.
        val artistGradientStops = remember(blendPrimaryColor) {
            arrayOf(
                0f to Color.Transparent,
                0.3f to Color.Transparent,
                0.5f to blendPrimaryColor.copy(alpha = 0.25f),
                0.66f to blendPrimaryColor.copy(alpha = 0.6f),
                0.8f to blendPrimaryColor.copy(alpha = 0.9f),
                0.9f to blendPrimaryColor,
                1f to blendPrimaryColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colorStops = artistGradientStops))
        )
        // Solid seam guard: masks sub-pixel gap flashes at the hero/content boundary.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.BottomCenter)
                .background(blendPrimaryColor)
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
                    style = AppTypography.PageTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                HapticFilledTonalIconButton(
                    onClick = onAdd,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = ctaContainerColor.copy(alpha = 0.92f),
                        contentColor = ctaContentColor
                    )
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = if (isSaved) "Remove from library" else "Add to library"
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
    val primaryContainerColor = AppColors.ArtistCtaContainer
    val secondaryContainerColor = AppColors.ArtistCtaContainerVariant
    val ctaContentColor = AppColors.ArtistCtaContent
    val ctaBorderColor = AppColors.ArtistCtaBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HapticFilledTonalButton(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = primaryContainerColor,
                contentColor = ctaContentColor
            ),
            border = BorderStroke(1.dp, ctaBorderColor)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play", fontWeight = FontWeight.SemiBold)
        }
        HapticOutlinedButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = secondaryContainerColor,
                contentColor = ctaContentColor
            ),
            border = BorderStroke(1.dp, ctaBorderColor)
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
            modifier = Modifier.height(420.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun SectionHeaderWithArrow(
    title: String,
    showArrow: Boolean,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTypography.SectionTitle
        )
        if (showArrow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.hapticClickable(onClick = onArrowClick)
            ) {
                Text(
                    text = "More",
                    style = AppTypography.SectionTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "See all $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
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
