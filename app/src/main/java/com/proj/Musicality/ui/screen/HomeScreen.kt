package com.proj.Musicality.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.MediaDownloadState
import com.proj.Musicality.data.local.MediaLibraryState
import coil3.compose.AsyncImage
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeMoreEndpoint
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.model.HomeWatchEndpoint
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.model.SectionLayout
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.ErrorMessage
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.ui.components.HapticTextButton
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.components.hapticCombinedClickable
import com.proj.Musicality.ui.components.pressScale
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.ui.theme.rememberMediaBackdropPalette
import com.proj.Musicality.util.toCompactSongTitle
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

private data class HomeSongMenuModel(
    val title: String,
    val subtitle: String?,
    val mediaItem: MediaItem,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumb: String?,
    val shareUrl: String
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val downloadStates by repository.downloadStates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedSongMenu by remember { mutableStateOf<HomeSongMenuModel?>(null) }
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val playbackUiPalette = LocalPlaybackUiPalette.current
    val refreshContainerColor = playbackUiPalette?.accent ?: MaterialTheme.colorScheme.primary
    val refreshIndicatorColor = playbackUiPalette?.onAccent ?: MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                viewModel.refresh()
                isRefreshing = false
            }
        },
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                containerColor = refreshContainerColor,
                color = refreshIndicatorColor,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight + 8.dp)
        ) {
            item(key = "home-title") {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            val personalizedSections = state.personalizedSections
            if (personalizedSections.isNotEmpty()) {
                itemsIndexed(
                    personalizedSections,
                    key = { index, section -> "personalized-$index-${section.title}" }
                ) { _, section ->
                    HomeSectionShelf(
                        section = section,
                        downloadStates = downloadStates,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onSongTap = onSongTap,
                        onSongOverflowClick = { selectedSongMenu = it.toMenuModel() },
                        onVideoTap = onVideoTap,
                        onArtistTap = onArtistTap,
                        onAlbumTap = onAlbumTap,
                        onPlaylistTap = onPlaylistTap
                    )
                }
            }

            val showUnifiedLoader = state.isApiLoading || state.isPersonalizationLoading
            if (showUnifiedLoader) {
                item(key = "home-unified-loader") {
                    HomeLoadingPlaceholder(
                        accentColor = refreshContainerColor
                    )
                }
            }

            val apiFeed = state.apiFeed
            val errorMessage = state.errorMessage
            when {
                apiFeed != null -> {
                    itemsIndexed(
                        apiFeed.sections,
                        key = { index, section -> "section-$index-${section.title}" }
                    ) { _, section ->
                        HomeSectionShelf(
                            section = section,
                            downloadStates = downloadStates,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onSongTap = onSongTap,
                            onSongOverflowClick = { selectedSongMenu = it.toMenuModel() },
                            onVideoTap = onVideoTap,
                            onArtistTap = onArtistTap,
                            onAlbumTap = onAlbumTap,
                            onPlaylistTap = onPlaylistTap
                        )
                    }

                    if (apiFeed.continuation != null) {
                        item(key = "home-load-more-${apiFeed.continuation}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }

                errorMessage != null -> {
                    item(key = "home-error") {
                        ErrorMessage(message = errorMessage)
                    }
                }
            }
        }
    }

    selectedSongMenu?.let { menu ->
        val mediaState by remember(menu.mediaItem.videoId) {
            repository.observeMediaState(menu.mediaItem.videoId)
        }.collectAsStateWithLifecycle(initialValue = MediaLibraryState())
        HomeSongActionsSheet(
            model = menu,
            isLiked = mediaState.isLiked,
            downloadState = downloadStates[menu.mediaItem.videoId],
            onDismiss = { selectedSongMenu = null },
            onToggleLike = {
                scope.launch { repository.toggleLike(menu.mediaItem) }
                selectedSongMenu = null
            },
            onPlayNext = {
                onPlayNext(menu.mediaItem)
                selectedSongMenu = null
            },
            onAddToQueue = {
                onAddToQueue(menu.mediaItem)
                selectedSongMenu = null
            },
            onViewArtist = {
                val artistId = menu.artistId ?: return@HomeSongActionsSheet
                onArtistTap(menu.artistName, artistId, menu.thumb)
                selectedSongMenu = null
            },
            onViewAlbum = {
                val albumId = menu.albumId ?: return@HomeSongActionsSheet
                onAlbumTap(menu.albumName ?: menu.title, albumId, menu.artistName, menu.thumb)
                selectedSongMenu = null
            },
            onShare = {
                val shareText = buildString {
                    append(menu.title)
                    if (!menu.subtitle.isNullOrBlank()) {
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
                selectedSongMenu = null
            },
            onDownload = {
                scope.launch {
                    val result = repository.download(menu.mediaItem)
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Downloaded: ${menu.title}" else "Download failed: ${menu.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedSongMenu = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeLoadingPlaceholder(
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(56.dp),
            color = accentColor
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeSectionShelf(
    section: HomeSection,
    downloadStates: Map<String, MediaDownloadState>,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onSongOverflowClick: (HomeItem.Song) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val songs = remember(section.items) { section.items.filterIsInstance<HomeItem.Song>() }
    val queueItems = remember(songs) { songs.map { it.toMediaItem() } }
    val songIndexById = remember(songs) {
        songs.mapIndexed { index, song -> song.videoId to index }.toMap()
    }
    val isSongShelf = section.items.isNotEmpty() && songs.size == section.items.size
    val isFourStackShelf = remember(section.title, isSongShelf, section.layoutHint) {
        section.layoutHint == SectionLayout.STACKED_SONGS ||
            section.title.equals("Quick picks", ignoreCase = true) ||
            section.title.equals("Top music videos", ignoreCase = true) ||
            (isSongShelf && section.title.contains("trending", ignoreCase = true))
    }
    val isTrendingShortsShelf = remember(section.title) {
        section.title.equals("Trending on Shorts", ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Dispatch on layout hints for personalized sections
        when (section.layoutHint) {
            SectionLayout.HERO_CARD -> {
                HeroContinuePlaying(
                    section = section,
                    onSongTap = onSongTap
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            SectionLayout.HERO_WITH_TOP_PICKS -> {
                HeroContinuePlayingWithTopPicks(
                    section = section,
                    onSongTap = onSongTap,
                    onSongOverflowClick = onSongOverflowClick
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            SectionLayout.ALBUM_CAROUSEL -> {
                if (section.title.isNotBlank()) {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AlbumCarousel(
                    section = section,
                    onAlbumTap = onAlbumTap
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            SectionLayout.SONG_CAROUSEL -> {
                if (section.title.isNotBlank()) {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SongCarousel(
                    section = section,
                    onSongTap = onSongTap,
                    onSongOverflowClick = onSongOverflowClick
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            SectionLayout.SONG_FEATURED_MIX -> {
                if (section.title.isNotBlank()) {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SongFeaturedMix(
                    section = section,
                    onSongTap = onSongTap,
                    onSongOverflowClick = onSongOverflowClick
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            else -> { /* fall through to existing logic */ }
        }

        if (section.title.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = section.title,
                    modifier = Modifier.weight(1f)
                )
                HomeMoreButton(
                    endpoint = section.moreEndpoint,
                    onVideoTap = onVideoTap
                )
            }
        }

        if (isFourStackShelf) {
            val groupedItems = remember(section.items) { section.items.chunked(4) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                itemsIndexed(groupedItems, key = { index, _ -> "group-$index-${section.title}" }) { _, group ->
                    Column(
                        modifier = Modifier.width(340.dp)
                    ) {
                        group.forEach { item ->
                            when (item) {
                                is HomeItem.Song -> {
                                    SongListItem(
                                        title = item.title,
                                        subtitle = listOfNotNull(
                                            item.artistName.takeIf { it.isNotBlank() },
                                            item.plays
                                        ).joinToString(" • "),
                                        thumbnailUrl = item.thumbnailUrl,
                                        downloadState = downloadStates[item.videoId],
                                        sharedElementKey = "thumb-${item.videoId}",
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onOverflowClick = { onSongOverflowClick(item) },
                                        onClick = {
                                            val index = songIndexById[item.videoId] ?: 0
                                            val mediaItem = item.toMediaItem()
                                            onSongTap(
                                                mediaItem,
                                                buildHomeSectionQueue(
                                                    section = section,
                                                    tappedItem = mediaItem,
                                                    queueItems = queueItems,
                                                    currentIndex = index
                                                )
                                            )
                                        }
                                    )
                                }

                                is HomeItem.Card -> {
                                    SongListItem(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        thumbnailUrl = item.thumbnailUrl,
                                        onOverflowClick = null,
                                        onClick = {
                                            when {
                                                item.videoId != null -> onVideoTap(item.toVideoMediaItem())
                                                item.pageType == "MUSIC_PAGE_TYPE_PLAYLIST" && item.id != null -> {
                                                    onPlaylistTap(
                                                        item.title,
                                                        item.id,
                                                        item.subtitle?.substringAfterLast(
                                                            " • ",
                                                            missingDelimiterValue = ""
                                                        )?.takeIf { it.isNotBlank() },
                                                        item.thumbnailUrl
                                                    )
                                                }

                                                item.pageType == "MUSIC_PAGE_TYPE_ALBUM" && item.id != null -> {
                                                    onAlbumTap(
                                                        item.title,
                                                        item.id,
                                                        item.subtitle?.substringAfterLast(
                                                            " • ",
                                                            missingDelimiterValue = ""
                                                        )?.takeIf { it.isNotBlank() },
                                                        item.thumbnailUrl
                                                    )
                                                }

                                                (item.pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                                                    item.pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL") &&
                                                    item.id != null -> {
                                                    onArtistTap(item.title, item.id, item.thumbnailUrl)
                                                }
                                            }
                                        }
                                    )
                                }

                                is HomeItem.PodcastEpisode -> {
                                    SongListItem(
                                        title = item.title,
                                        subtitle = listOfNotNull(
                                            item.showName,
                                            item.timeAgo,
                                            item.duration
                                        ).joinToString(" • "),
                                        thumbnailUrl = item.thumbnailUrl,
                                        onOverflowClick = null,
                                        onClick = { onVideoTap(item.toMediaItem()) }
                                    )
                                }

                                is HomeItem.NavButton -> {
                                    HomeNavButton(item = item)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            return@Column
        }

        if (isTrendingShortsShelf) {
            val shortsCards = remember(section.items) {
                section.items.filterIsInstance<HomeItem.Card>()
            }
            val groupedCards = remember(shortsCards) { shortsCards.chunked(2) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(groupedCards, key = { index, _ -> "shorts-group-$index-${section.title}" }) { _, group ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        group.forEach { card ->
                            ContentCard(
                                title = card.title,
                                subtitle = card.subtitle,
                                thumbnailUrl = card.thumbnailUrl,
                                cardWidth = 220.dp,
                                thumbnailSize = 220.dp,
                                onOverflowClick = null,
                                onClick = { if (card.videoId != null) onVideoTap(card.toVideoMediaItem()) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            return@Column
        }

        if (isSongShelf) {
            val groupedSongs = remember(songs) { songs.chunked(4) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                itemsIndexed(groupedSongs, key = { index, _ -> "song-group-$index-${section.title}" }) { _, group ->
                    Column(modifier = Modifier.width(340.dp)) {
                        group.forEach { song ->
                            SongListItem(
                                title = song.title,
                                subtitle = listOfNotNull(
                                    song.artistName.takeIf { it.isNotBlank() },
                                    song.plays
                                ).joinToString(" • "),
                                thumbnailUrl = song.thumbnailUrl,
                                downloadState = downloadStates[song.videoId],
                                sharedElementKey = "thumb-${song.videoId}",
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOverflowClick = { onSongOverflowClick(song) },
                                onClick = {
                                    val index = songIndexById[song.videoId] ?: 0
                                    val mediaItem = song.toMediaItem()
                                    onSongTap(
                                        mediaItem,
                                        buildHomeSectionQueue(
                                            section = section,
                                            tappedItem = mediaItem,
                                            queueItems = queueItems,
                                            currentIndex = index
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            return@Column
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(
                section.items,
                key = { index, item -> item.key(index) }
            ) { _, item ->
                when (item) {
                    is HomeItem.Card -> {
                        ContentCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            thumbnailUrl = item.thumbnailUrl,
                            sharedElementKey = item.sharedElementKey(),
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                when {
                                    item.videoId != null -> onVideoTap(item.toVideoMediaItem())
                                    item.pageType == "MUSIC_PAGE_TYPE_PLAYLIST" && item.id != null -> {
                                        onPlaylistTap(
                                            item.title,
                                            item.id,
                                            item.subtitle?.substringAfterLast(" • ", missingDelimiterValue = "")
                                                ?.takeIf { it.isNotBlank() },
                                            item.thumbnailUrl
                                        )
                                    }

                                    item.pageType == "MUSIC_PAGE_TYPE_ALBUM" && item.id != null -> {
                                        onAlbumTap(
                                            item.title,
                                            item.id,
                                            item.subtitle?.substringAfterLast(" • ", missingDelimiterValue = "")
                                                ?.takeIf { it.isNotBlank() },
                                            item.thumbnailUrl
                                        )
                                    }

                                    (item.pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                                        item.pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL") &&
                                        item.id != null -> {
                                        onArtistTap(item.title, item.id, item.thumbnailUrl)
                                    }
                                }
                            }
                        )
                    }

                    is HomeItem.NavButton -> {
                        HomeNavButton(item = item)
                    }

                    is HomeItem.PodcastEpisode -> {
                        PodcastEpisodeCard(
                            episode = item,
                            onClick = {
                                onVideoTap(item.toMediaItem())
                            }
                        )
                    }

                    is HomeItem.Song -> {
                        HomeSongCard(
                            song = item,
                            onLongPress = { onSongOverflowClick(item) },
                            onClick = {
                                val index = songIndexById[item.videoId] ?: 0
                                val mediaItem = item.toMediaItem()
                                onSongTap(
                                    mediaItem,
                                    buildHomeSectionQueue(
                                        section = section,
                                        tappedItem = mediaItem,
                                        queueItems = queueItems,
                                        currentIndex = index
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SongCarousel(
    section: HomeSection,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onSongOverflowClick: (HomeItem.Song) -> Unit
) {
    val songs = remember(section.items) { section.items.filterIsInstance<HomeItem.Song>() }
    if (songs.isEmpty()) return
    val queueItems = remember(songs) { songs.map { it.toMediaItem() } }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(
            songs,
            key = { _, song -> "carousel-song-${song.videoId}" }
        ) { index, song ->
            HomeSongCard(
                song = song,
                onLongPress = { onSongOverflowClick(song) },
                onClick = {
                    val mediaItem = song.toMediaItem()
                    onSongTap(
                        mediaItem,
                        buildHomeSectionQueue(
                            section = section,
                            tappedItem = mediaItem,
                            queueItems = queueItems,
                            currentIndex = index
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun SongFeaturedMix(
    section: HomeSection,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onSongOverflowClick: (HomeItem.Song) -> Unit
) {
    val songs = remember(section.items) { section.items.filterIsInstance<HomeItem.Song>() }
    if (songs.isEmpty()) return
    val queueItems = remember(songs) { songs.map { it.toMediaItem() } }
    val featured = songs.first()
    val followUps = songs.drop(1).take(7)
    val featuredPalette = rememberMediaBackdropPalette(
        imageUrl = upscaleThumbnail(featured.thumbnailUrl, 720),
        fallbackSurface = MaterialTheme.colorScheme.surfaceContainerHigh,
        animateTransitions = false
    )

    val darkTheme = isSystemInDarkTheme()
    val gradientTop = if (darkTheme) {
        featuredPalette.top
    } else {
        lerp(featuredPalette.top, Color.White, 0.62f)
    }
    val gradientBottom = if (darkTheme) {
        lerp(featuredPalette.bottom, Color.Black, 0.12f)
    } else {
        lerp(featuredPalette.bottom, Color.White, 0.78f)
    }
    val accent = featuredPalette.accent
    val titleColor = if (darkTheme) featuredPalette.title else Color(0xFF111315)
    val bodyColor = if (darkTheme) featuredPalette.body else Color(0xFF111315).copy(alpha = 0.62f)
    val borderColor = if (darkTheme) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color(0xFF111315).copy(alpha = 0.06f)
    }
    val overflowBg = if (darkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF111315).copy(alpha = 0.05f)
    }
    val cardGradient = Brush.verticalGradient(listOf(gradientTop, gradientBottom))
    val haptics = LocalHapticFeedback.current
    val cardInteraction = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        val featuredItem = featured.toMediaItem()
        val playTapped = {
            onSongTap(
                featuredItem,
                buildHomeSectionQueue(
                    section = section,
                    tappedItem = featuredItem,
                    queueItems = queueItems,
                    currentIndex = 0
                )
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .pressScale(cardInteraction)
                .clip(RoundedCornerShape(8.dp))
                .background(cardGradient)
                .border(
                    BorderStroke(1.dp, borderColor),
                    RoundedCornerShape(8.dp)
                )
                .hapticCombinedClickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = { playTapped() },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSongOverflowClick(featured)
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (darkTheme) 0.18f else 0.14f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(140f, 140f),
                            radius = 520f
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumbnail(
                    url = featured.thumbnailUrl,
                    size = 84.dp,
                    cornerRadius = 8.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = featured.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 21.sp
                    )
                    val meta = listOfNotNull(
                        featured.artistName.takeIf { it.isNotBlank() },
                        featured.plays
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = meta,
                            fontSize = 13.sp,
                            color = bodyColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(overflowBg)
                        .clickable { onSongOverflowClick(featured) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions for ${featured.title}",
                        tint = bodyColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable { playTapped() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${featured.title}",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        if (followUps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp)
            ) {
                itemsIndexed(
                    followUps,
                    key = { _, song -> "featured-followup-${song.videoId}" }
                ) { _, song ->
                    val songIndex = songs.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                    CompactSongCard(
                        song = song,
                        onClick = {
                            val mediaItem = song.toMediaItem()
                            onSongTap(
                                mediaItem,
                                buildHomeSectionQueue(
                                    section = section,
                                    tappedItem = mediaItem,
                                    queueItems = queueItems,
                                    currentIndex = songIndex
                                )
                            )
                        },
                        onLongPress = { onSongOverflowClick(song) },
                        modifier = Modifier.width(122.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSongActionsSheet(
    model: HomeSongMenuModel,
    isLiked: Boolean,
    downloadState: MediaDownloadState?,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreInfo by remember(model.shareUrl, model.title) { mutableStateOf(false) }
    val infoLines = remember(model) {
        listOfNotNull(
            "Title: ${model.title}",
            model.subtitle?.takeIf { it.isNotBlank() }?.let { "Info: $it" },
            model.artistName.takeIf { it.isNotBlank() }?.let { "Artist: $it" },
            model.albumName?.takeIf { it.isNotBlank() }?.let { "Album: $it" },
            model.mediaItem.durationText?.takeIf { it.isNotBlank() }?.let { "Duration: $it" },
            "Video ID: ${model.mediaItem.videoId}"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            HomeSongActionItem(
                label = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = onToggleLike
            )
            HomeSongActionItem(
                label = "Play next",
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlayNext
            )
            HomeSongActionItem(
                label = "Add to Queue",
                icon = Icons.Rounded.Add,
                onClick = onAddToQueue
            )
            if (model.artistId != null) {
                HomeSongActionItem(
                    label = "View Artist",
                    icon = Icons.Rounded.Person,
                    onClick = onViewArtist
                )
            }
            if (model.albumId != null) {
                HomeSongActionItem(
                    label = "View Album",
                    icon = Icons.Rounded.Album,
                    onClick = onViewAlbum
                )
            }
            HomeSongActionItem(
                label = "Share",
                icon = Icons.Rounded.Share,
                onClick = onShare
            )
            HomeSongActionItem(
                label = when {
                    downloadState?.isDownloading == true -> "Downloading ${(downloadState.progress * 100).toInt()}%"
                    downloadState?.isDownloaded == true -> "Downloaded"
                    else -> "Download"
                },
                icon = Icons.Rounded.Download,
                onClick = onDownload,
                enabled = downloadState?.isDownloading != true
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
private fun HomeSongActionItem(
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
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun HomeMoreButton(
    endpoint: HomeMoreEndpoint?,
    onVideoTap: (MediaItem) -> Unit
) {
    val watchEndpoint = endpoint?.watchEndpoint ?: return
    HapticTextButton(onClick = { onVideoTap(watchEndpoint.toMediaItem("Home")) }) {
        Text("Play all")
    }
}

@Composable
private fun HomeSongCard(
    song: HomeItem.Song,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onLongPress != null) {
        Modifier.hapticCombinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongPress,
            onClick = onClick
        )
    } else {
        Modifier.hapticClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .pressScale(interaction)
            .then(clickModifier)
    ) {
        Box {
            Thumbnail(
                url = song.thumbnailUrl,
                size = 160.dp,
                cornerRadius = 8.dp
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.height(52.dp)) {
            Text(
                text = song.title.toCompactSongTitle(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    song.artistName.takeIf { it.isNotBlank() },
                    song.plays
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeNavButton(item: HomeItem.NavButton) {
    val accentColor = item.color
        ?.let { Color(it.toInt()) }
        ?: MaterialTheme.colorScheme.primary
    val icon = item.icon.toImageVector()
    Surface(
        modifier = Modifier.width(176.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            if (icon != null) {
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun PodcastEpisodeCard(
    episode: HomeItem.PodcastEpisode,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .hapticClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(140.dp)
        ) {
            Thumbnail(
                url = episode.thumbnailUrl,
                size = 140.dp,
                cornerRadius = 8.dp
            )
            episode.playbackProgressPercent
                ?.takeIf { it > 0f }
                ?.let { pct ->
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(episode.showName, episode.timeAgo, episode.duration)
                .joinToString(" • ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroContinuePlaying(
    section: HomeSection,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit
) {
    val song = section.items.firstOrNull() as? HomeItem.Song ?: return
    val mediaItem = remember(song) { song.toMediaItem() }
    val bottomScrim = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.35f to Color(0x99000000),
                1.0f to Color(0xF2000000)
            )
        )
    }
    val queueItems = remember(section.items) {
        section.items.filterIsInstance<HomeItem.Song>().map { it.toMediaItem() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                onSongTap(
                    mediaItem,
                    buildHomeSectionQueue(
                        section = section,
                        tappedItem = mediaItem,
                        queueItems = queueItems,
                        currentIndex = queueItems.indexOfFirst { it.videoId == mediaItem.videoId }
                    )
                )
            }
    ) {
        AsyncImage(
            model = upscaleThumbnail(song.thumbnailUrl, 1080),
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Subtle full-card dim to reduce image noise
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x28000000))
        )
        // Strong gradient scrim — bottom area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(bottomScrim)
        )

        // Bottom-left text
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.8f)
                .padding(16.dp)
        ) {
            Text(
                text = "Continue Playing",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = song.title.toCompactSongTitle(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artistName.isNotBlank()) {
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom-right play icon
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(36.dp)
        )
    }
}

@Composable
private fun HeroContinuePlayingWithTopPicks(
    section: HomeSection,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onSongOverflowClick: (HomeItem.Song) -> Unit
) {
    val queueItems = remember(section.items) {
        section.items.filterIsInstance<HomeItem.Song>().map { it.toMediaItem() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HeroContinuePlaying(section = section, onSongTap = onSongTap)

        val topPicks = remember(section.items) {
            section.items
                .drop(1)
                .filterIsInstance<HomeItem.Song>()
                .take(3)
        }

        if (topPicks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topPicks.forEach { song ->
                    CompactSongCard(
                        song = song,
                        onClick = {
                            val mediaItem = song.toMediaItem()
                            val index = queueItems.indexOfFirst { it.videoId == mediaItem.videoId }
                            onSongTap(
                                mediaItem,
                                buildHomeSectionQueue(
                                    section = section,
                                    tappedItem = mediaItem,
                                    queueItems = queueItems,
                                    currentIndex = index
                                )
                            )
                        },
                        onLongPress = { onSongOverflowClick(song) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun HomeSection.usesUpNextSeedQueue(): Boolean {
    return title.startsWith("Continue Playing", ignoreCase = true) ||
        title.startsWith("Similar to", ignoreCase = true)
}

private fun buildHomeSectionQueue(
    section: HomeSection,
    tappedItem: MediaItem,
    queueItems: List<MediaItem>,
    currentIndex: Int
): PlaybackQueue {
    if (section.usesUpNextSeedQueue()) {
        // Seed with only the tapped song so PlaybackViewModel upgrades it via the Next endpoint queue.
        return PlaybackQueue(
            items = listOf(tappedItem),
            currentIndex = 0,
            source = QueueSource.SINGLE
        )
    }

    val safeQueueItems = queueItems.ifEmpty { listOf(tappedItem) }
    val safeIndex = currentIndex.coerceIn(0, safeQueueItems.lastIndex)
    return PlaybackQueue(
        items = safeQueueItems,
        currentIndex = safeIndex,
        source = QueueSource.HOME
    )
}

@Composable
private fun CompactSongCard(
    song: HomeItem.Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val clickModifier = if (onLongPress != null) {
        Modifier.hapticCombinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            }
        )
    } else {
        Modifier.hapticClickable(onClick = onClick)
    }
    Surface(
        modifier = modifier
            .pressScale(interaction)
            .then(clickModifier),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            AsyncImage(
                model = upscaleThumbnail(song.thumbnailUrl, 720),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = song.title.toCompactSongTitle(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.artistName.isNotBlank()) {
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.labelSmall,
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
private fun AlbumCarousel(
    section: HomeSection,
    onAlbumTap: (String, String, String?, String?) -> Unit
) {
    val albumCards = remember(section.items) {
        section.items.filterIsInstance<HomeItem.Card>()
    }
    if (albumCards.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(
            albumCards,
            key = { index, card -> "album-${card.id ?: index}" }
        ) { _, card ->
            ContentCard(
                title = card.title,
                subtitle = card.subtitle,
                thumbnailUrl = card.thumbnailUrl,
                cardWidth = 170.dp,
                thumbnailSize = 170.dp,
                onClick = {
                    if (card.id != null) {
                        onAlbumTap(
                            card.title,
                            card.id,
                            card.subtitle?.substringAfterLast(" • ", missingDelimiterValue = "")
                                ?.takeIf { it.isNotBlank() },
                            card.thumbnailUrl
                        )
                    }
                }
            )
        }
    }
}

private fun HomeItem.key(index: Int): String = when (this) {
    is HomeItem.Song -> "song-$videoId"
    is HomeItem.Card -> "card-${id ?: videoId ?: index}"
    is HomeItem.NavButton -> "nav-$browseId-$index"
    is HomeItem.PodcastEpisode -> "podcast-$videoId"
}

private fun HomeItem.Song.toMediaItem(): MediaItem {
    return MediaItem(
        videoId = videoId,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        thumbnailUrl = thumbnailUrl,
        durationText = null,
        musicVideoType = musicVideoType ?: "MUSIC_VIDEO_TYPE_ATV"
    )
}

private fun HomeItem.Song.toMenuModel(): HomeSongMenuModel {
    val subtitle = listOfNotNull(
        artistName.takeIf { it.isNotBlank() },
        albumName?.takeIf { it.isNotBlank() },
        plays?.takeIf { it.isNotBlank() }
    ).joinToString(" • ").ifBlank { null }

    return HomeSongMenuModel(
        title = title,
        subtitle = subtitle,
        mediaItem = toMediaItem(),
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        thumb = thumbnailUrl,
        shareUrl = "https://music.youtube.com/watch?v=$videoId"
    )
}

private fun HomeItem.Card.toVideoMediaItem(): MediaItem {
    return MediaItem(
        videoId = videoId ?: "",
        title = title,
        artistName = subtitle?.substringBefore(" • ").orEmpty(),
        artistId = null,
        albumName = null,
        albumId = null,
        thumbnailUrl = thumbnailUrl,
        durationText = null,
        musicVideoType = musicVideoType ?: "MUSIC_VIDEO_TYPE_OMV"
    )
}

private fun HomeItem.PodcastEpisode.toMediaItem(): MediaItem {
    return MediaItem(
        videoId = videoId,
        title = title,
        artistName = showName ?: "",
        artistId = showId,
        albumName = showName,
        albumId = showId,
        thumbnailUrl = thumbnailUrl,
        durationText = duration,
        musicVideoType = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
    )
}

private fun HomeWatchEndpoint.toMediaItem(fallbackTitle: String): MediaItem {
    return MediaItem(
        videoId = videoId,
        title = fallbackTitle,
        artistName = "",
        artistId = null,
        albumName = null,
        albumId = null,
        thumbnailUrl = null,
        durationText = null,
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
    )
}

private fun HomeItem.Card.sharedElementKey(): String? {
    if (videoId != null) return "thumb-$videoId"
    if (id.isNullOrBlank()) return null
    return when (pageType) {
        "MUSIC_PAGE_TYPE_ALBUM" -> "thumb-album-$id"
        "MUSIC_PAGE_TYPE_PLAYLIST" -> "thumb-playlist-$id"
        "MUSIC_PAGE_TYPE_ARTIST",
        "MUSIC_PAGE_TYPE_USER_CHANNEL" -> "thumb-artist-$id"
        else -> null
    }
}

private fun String?.toImageVector(): ImageVector? = when (this) {
    "MUSIC_NEW_RELEASE" -> Icons.Rounded.Widgets
    "TRENDING_UP" -> Icons.AutoMirrored.Rounded.TrendingUp
    "BROADCAST" -> Icons.Rounded.Podcasts
    else -> null
}
