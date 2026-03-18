package com.proj.Musicality.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.ShimmerSection
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette
import com.proj.Musicality.util.upscaleThumbnail
import com.proj.Musicality.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
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
                        animatedVisibilityScope = animatedVisibilityScope,
                        onSongTap = onSongTap,
                        onVideoTap = onVideoTap,
                        onArtistTap = onArtistTap,
                        onAlbumTap = onAlbumTap,
                        onPlaylistTap = onPlaylistTap
                    )
                }
            } else if (state.reservedPersonalizedSlots > 0) {
                items(state.reservedPersonalizedSlots, key = { "personalized-shimmer-$it" }) {
                    ShimmerSection()
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
                            animatedVisibilityScope = animatedVisibilityScope,
                            onSongTap = onSongTap,
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

                state.isInitialLoading -> {
                    items(4, key = { "api-shimmer-$it" }) {
                        ShimmerSection()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeSectionShelf(
    section: HomeSection,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongTap: (MediaItem, PlaybackQueue) -> Unit,
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
                    onSongTap = onSongTap
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
                                        sharedElementKey = "thumb-${item.videoId}",
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onOverflowClick = {},
                                        onClick = {
                                            val index = songIndexById[item.videoId] ?: 0
                                            onSongTap(
                                                item.toMediaItem(),
                                                PlaybackQueue(
                                                    items = queueItems.ifEmpty { listOf(item.toMediaItem()) },
                                                    currentIndex = index,
                                                    source = QueueSource.HOME
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
                                        onOverflowClick = {},
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
                                        onOverflowClick = {},
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
                                onOverflowClick = {},
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
                                sharedElementKey = "thumb-${song.videoId}",
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOverflowClick = {},
                                onClick = {
                                    val index = songIndexById[song.videoId] ?: 0
                                    onSongTap(
                                        song.toMediaItem(),
                                        PlaybackQueue(
                                            items = queueItems,
                                            currentIndex = index,
                                            source = QueueSource.HOME
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
                            onOverflowClick = {},
                            onClick = {
                                val index = songIndexById[item.videoId] ?: 0
                                onSongTap(
                                    item.toMediaItem(),
                                    PlaybackQueue(
                                        items = queueItems.ifEmpty { listOf(item.toMediaItem()) },
                                        currentIndex = index,
                                        source = QueueSource.HOME
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
private fun HomeMoreButton(
    endpoint: HomeMoreEndpoint?,
    onVideoTap: (MediaItem) -> Unit
) {
    val watchEndpoint = endpoint?.watchEndpoint ?: return
    TextButton(onClick = { onVideoTap(watchEndpoint.toMediaItem("Home")) }) {
        Text("Play all")
    }
}

@Composable
private fun HomeSongCard(
    song: HomeItem.Song,
    onOverflowClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            Thumbnail(
                url = song.thumbnailUrl,
                size = 160.dp,
                cornerRadius = 8.dp
            )
            if (onOverflowClick != null) {
                IconButton(
                    onClick = onOverflowClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions for ${song.title}"
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.height(52.dp)) {
            Text(
                text = song.title,
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
            .clickable(onClick = onClick)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                onSongTap(
                    mediaItem,
                    PlaybackQueue(
                        items = listOf(mediaItem),
                        currentIndex = 0,
                        source = QueueSource.HOME
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

        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
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
                text = song.title,
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
    onSongTap: (MediaItem, PlaybackQueue) -> Unit
) {
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
                            onSongTap(
                                song.toMediaItem(),
                                PlaybackQueue(
                                    items = listOf(song.toMediaItem()),
                                    currentIndex = 0,
                                    source = QueueSource.HOME
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSongCard(
    song: HomeItem.Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
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
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = song.title,
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
