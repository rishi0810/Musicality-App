package com.proj.Musicality.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import coil3.compose.AsyncImage
import com.proj.Musicality.ui.theme.LocalPlaybackBackdropPalette
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.model.RelatedFeed
import com.proj.Musicality.data.model.RelatedArtist
import com.proj.Musicality.data.model.RelatedState
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.SongListItem
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.theme.AppTypography
import kotlinx.coroutines.launch

@Composable
fun RelatedContent(
    state: RelatedState,
    onRetry: () -> Unit,
    onSongTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
    nestedScrollConnection: NestedScrollConnection?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context.applicationContext) {
        LibraryRepository.getInstance(context.applicationContext)
    }
    var selectedMenu by remember { mutableStateOf<QueueItemMenuModel?>(null) }

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        when (state) {
            RelatedState.Idle -> RelatedEmptyState(
                message = "Related items will appear here",
                modifier = modifier
            )
            is RelatedState.Loading -> RelatedLoadingState(modifier)
            is RelatedState.Error -> RelatedEmptyState(
                message = state.message,
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = modifier
            )
            is RelatedState.Loaded -> RelatedFeedContent(
                feed = state.feed,
                onSongTap = onSongTap,
                onArtistTap = onArtistTap,
                onAlbumTap = onAlbumTap,
                onPlaylistTap = onPlaylistTap,
                onSongOverflow = { song ->
                    selectedMenu = queueMenuModelFor(song.toMediaItem(), QueueSource.SINGLE)
                },
                nestedScrollConnection = nestedScrollConnection,
                modifier = modifier
            )
        }
    }

    selectedMenu?.let { menu ->
        QueueItemActionsSheet(
            model = menu,
            onDismiss = { selectedMenu = null },
            onViewArtist = {
                val artistId = menu.artistId ?: return@QueueItemActionsSheet
                onDismiss()
                onArtistTap(artistId, menu.artistName, menu.thumbnailUrl)
                selectedMenu = null
            },
            onViewAlbum = {
                val albumId = menu.albumId ?: return@QueueItemActionsSheet
                onDismiss()
                onAlbumTap(albumId, menu.albumName ?: menu.title, null)
                selectedMenu = null
            },
            onShare = {
                onDismiss()
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, menu.title)
                                putExtra(Intent.EXTRA_TEXT, "${menu.title}\n${menu.shareUrl}")
                            },
                            "Share"
                        )
                    )
                }
                selectedMenu = null
            },
            onDownload = {
                onDismiss()
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

@Composable
private fun RelatedFeedContent(
    feed: RelatedFeed,
    onSongTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onSongOverflow: (HomeItem.Song) -> Unit,
    nestedScrollConnection: NestedScrollConnection?,
    modifier: Modifier
) {
    val backdropColor = LocalPlaybackBackdropPalette.current?.bottom
        ?: MaterialTheme.colorScheme.surface
    val sectionSurfaceColor = lerp(backdropColor, Color.Black, 0.12f)
    val displaySections = remember(feed.sections, feed.artist?.name) {
        val artistName = feed.artist?.name?.trim().orEmpty()
        val artistSection = feed.sections.firstOrNull {
            artistName.isNotBlank() && it.title.trim().equals(artistName, ignoreCase = true)
        }
        if (artistSection == null || artistName.isBlank()) {
            feed.sections
        } else {
            val renamed = artistSection.copy(title = "More from $artistName")
            val remaining = feed.sections.filterNot { it === artistSection }
            val recommendedIndex = remaining.indexOfFirst {
                it.title.equals("Recommended playlists", ignoreCase = true)
            }
            val otherPerformancesIndex = remaining.indexOfFirst {
                it.title.equals("Other performances", ignoreCase = true)
            }
            val insertionIndex = when {
                recommendedIndex >= 0 && otherPerformancesIndex > recommendedIndex ->
                    otherPerformancesIndex
                otherPerformancesIndex >= 0 -> otherPerformancesIndex
                else -> remaining.size
            }
            remaining.toMutableList().apply { add(insertionIndex, renamed) }
        }
    }

    if (nestedScrollConnection == null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (feed.aboutArtist != null || feed.artist != null) {
                ArtistInfoSection(
                    artist = feed.artist,
                    description = feed.aboutArtist,
                    onArtistTap = onArtistTap,
                    surfaceColor = sectionSurfaceColor
                )
            }
            displaySections.forEach { section ->
                RelatedSection(
                    section = section,
                    onSongTap = onSongTap,
                    onArtistTap = onArtistTap,
                    onAlbumTap = onAlbumTap,
                    onPlaylistTap = onPlaylistTap,
                    onSongOverflow = onSongOverflow
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(nestedScrollConnection)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "related-about-artist") {
            if (feed.aboutArtist != null || feed.artist != null) {
                ArtistInfoSection(
                    artist = feed.artist,
                    description = feed.aboutArtist,
                    onArtistTap = onArtistTap,
                    surfaceColor = sectionSurfaceColor
                )
            }
        }
        itemsIndexed(
            items = displaySections,
            key = { index, section -> "related-section-${section.title}-$index" }
        ) { index, section ->
            RelatedSection(
                section = section,
                onSongTap = onSongTap,
                onArtistTap = onArtistTap,
                onAlbumTap = onAlbumTap,
                onPlaylistTap = onPlaylistTap,
                onSongOverflow = onSongOverflow
            )
        }
    }
}

@Composable
private fun ArtistInfoSection(
    artist: RelatedArtist?,
    description: String?,
    onArtistTap: (String, String, String?) -> Unit,
    surfaceColor: Color
) {
    val artistName = artist?.name ?: "About the artist"
    val artistId = artist?.artistId
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (artistId != null) {
                    Modifier.hapticClickable {
                        onArtistTap(artistId, artistName, artist?.thumbnailUrl)
                    }
                } else {
                    Modifier
                }
        ),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = artist?.thumbnailUrl,
                    contentDescription = artistName,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.78f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
                Text(
                    text = "ABOUT THE ARTIST",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (artistId != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "View artist",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedSection(
    section: HomeSection,
    onSongTap: (MediaItem) -> Unit,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onSongOverflow: (HomeItem.Song) -> Unit
) {
    val songs = remember(section.items) { section.items.filterIsInstance<HomeItem.Song>() }
    val cards = remember(section.items) { section.items.filterIsInstance<HomeItem.Card>() }
    val isFourItemSongSection = section.title.equals("You might also like", ignoreCase = true) ||
        section.title.equals("Other performances", ignoreCase = true)
    val isSimilarArtists = section.title.equals("Similar artists", ignoreCase = true)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (section.title.startsWith("More from ", ignoreCase = true)) {
            Text(
                text = section.title,
                style = AppTypography.SectionTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            SectionHeader(title = section.title)
        }
        when {
            isFourItemSongSection && songs.size == section.items.size -> {
                FourItemSongColumns(
                    songs = songs,
                    onSongTap = onSongTap,
                    onSongOverflow = onSongOverflow
                )
            }
            isSimilarArtists && cards.size == section.items.size -> {
                SimilarArtistsSection(cards = cards, onArtistTap = onArtistTap)
            }
            songs.size == section.items.size -> {
                section.items.forEach { item ->
                    val song = item as HomeItem.Song
                    SongListItem(
                        title = song.title,
                        subtitle = listOfNotNull(
                            song.artistName.takeIf { it.isNotBlank() },
                            song.plays
                        ).joinToString(" • "),
                        thumbnailUrl = song.thumbnailUrl,
                        onClick = { onSongTap(song.toMediaItem()) }
                    )
                }
            }
            cards.size == section.items.size -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(
                        items = cards,
                        key = { index, card -> "related-card-${card.title}-$index" }
                    ) { _, card ->
                        ContentCard(
                            title = card.title,
                            subtitle = card.subtitle,
                            thumbnailUrl = card.thumbnailUrl,
                            cardWidth = 150.dp,
                            thumbnailSize = 150.dp,
                            onClick = {
                                when {
                                    card.videoId != null -> onSongTap(card.toMediaItem())
                                    card.id == null -> Unit
                                    card.pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                                        card.pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" -> {
                                        onArtistTap(card.id, card.title, card.thumbnailUrl)
                                    }
                                    card.pageType == "MUSIC_PAGE_TYPE_ALBUM" -> {
                                        onAlbumTap(card.id, card.title, card.thumbnailUrl)
                                    }
                                    card.pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                                        onPlaylistTap(
                                            card.title,
                                            card.id,
                                            card.subtitle
                                                ?.substringAfter(" • ", "")
                                                ?.substringBefore(" • ")
                                                ?.takeIf { it.isNotBlank() },
                                            card.thumbnailUrl
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistsSection(
    cards: List<HomeItem.Card>,
    onArtistTap: (String, String, String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(
            items = cards,
            key = { index, card -> "related-artist-${card.id ?: card.title}-$index" }
        ) { _, card ->
            val artistId = card.id ?: return@itemsIndexed
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .hapticClickable {
                        onArtistTap(artistId, card.title, card.thumbnailUrl)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Thumbnail(
                    url = card.thumbnailUrl,
                    size = 104.dp,
                    cornerRadius = 52.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(40.dp)
                )
            }
        }
    }
}

@Composable
private fun FourItemSongColumns(
    songs: List<HomeItem.Song>,
    onSongTap: (MediaItem) -> Unit,
    onSongOverflow: (HomeItem.Song) -> Unit
) {
    val groups = remember(songs) { songs.chunked(4) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        itemsIndexed(
            items = groups,
            key = { index, _ -> "related-song-column-$index" }
        ) { _, group ->
            Column(modifier = Modifier.width(340.dp)) {
                group.forEach { song ->
                    SongListItem(
                        title = song.title,
                        subtitle = listOfNotNull(
                            song.artistName.takeIf { it.isNotBlank() },
                            song.plays
                        ).joinToString(" • "),
                        thumbnailUrl = song.thumbnailUrl,
                        onOverflowClick = { onSongOverflow(song) },
                        onClick = { onSongTap(song.toMediaItem()) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RelatedLoadingState(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(56.dp),
            color = LocalPlaybackBackdropPalette.current?.accent
                ?: MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RelatedEmptyState(
    message: String,
    modifier: Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.hapticClickable(onClick = onAction)
            )
        }
    }
}

private fun HomeItem.Song.toMediaItem(): MediaItem = MediaItem(
    videoId = videoId,
    title = title,
    artistName = artistName,
    artistId = artistId,
    albumName = albumName,
    albumId = albumId,
    thumbnailUrl = thumbnailUrl,
    durationText = null,
    musicVideoType = musicVideoType
)

private fun HomeItem.Card.toMediaItem(): MediaItem = MediaItem(
    videoId = videoId.orEmpty(),
    title = title,
    artistName = subtitle?.substringBefore(" • ").orEmpty(),
    artistId = null,
    albumName = null,
    albumId = null,
    thumbnailUrl = thumbnailUrl,
    durationText = null,
    musicVideoType = musicVideoType
)
