package com.proj.Musicality.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.proj.Musicality.R
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.parser.MoodCategoryParser
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.ErrorMessage
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.components.pressScale
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.ShimmerSection
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.viewmodel.ExploreViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    onMoodTap: (MoodCategoryParser.Mood) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val viewModel: ExploreViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.initialize() }

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
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight + 8.dp)
        ) {
            item(key = "explore-top-space") {
                Spacer(Modifier.height(12.dp))
            }

            when (val s = state) {
                is ExploreViewModel.UiState.Loading -> {
                    items(5, key = { "shimmer-$it" }) { ShimmerSection() }
                }

                is ExploreViewModel.UiState.Error -> {
                    item(key = "explore-error") { ErrorMessage(message = s.message) }
                }

                is ExploreViewModel.UiState.Loaded -> {
                    val data = s.data

                    // ── 1a. Genres (image cards) ──────────────────────────────
                    item(key = "genres-header") {
                        SectionHeader("Genres")
                    }
                    item(key = "genres-row") {
                        GenresRow(onMoodTap = onMoodTap)
                        Spacer(Modifier.height(14.dp))
                    }

                    // ── 1b. Moods (chips, remaining) ──────────────────────────
                    item(key = "moods-header") {
                        SectionHeader("Moods")
                    }
                    item(key = "moods-row") {
                        MoodsRow(onMoodTap = onMoodTap)
                        Spacer(Modifier.height(14.dp))
                    }

                    // ── 2. Global Top Charts (1 per column) ──────────────────
                    val globalVideoSection = data.chartsGlobalSections.firstOrNull()
                    if (globalVideoSection != null) {
                        item(key = "global-charts-header") {
                            SectionHeader("Global Top Charts")
                        }
                        item(key = "global-charts-row") {
                            CardRow(
                                section = globalVideoSection,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onArtistTap = onArtistTap,
                                onAlbumTap = onAlbumTap,
                                onPlaylistTap = onPlaylistTap
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    // ── 3. Global Top Artists (2 long cards per column) ─────
                    val globalArtistSection = data.chartsGlobalSections
                        .firstOrNull { "artist" in it.title.lowercase() }
                        ?: data.chartsGlobalSections.lastOrNull()
                            ?.takeIf { it != globalVideoSection }

                    if (globalArtistSection != null) {
                        item(key = "global-artists-header") {
                            SectionHeader("Global Top Artists")
                        }
                        item(key = "global-artists-row") {
                            ArtistStackRow(
                                section = globalArtistSection,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onArtistTap = onArtistTap,
                                onPlaylistTap = onPlaylistTap
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    // ── 4. Feel Good (first section) ──────────────────────────
                    val feelGood = data.feelGoodFirstSection
                    if (feelGood != null) {
                        item(key = "feelgood-header") {
                            SectionHeader(feelGood.title)
                        }
                        item(key = "feelgood-row") {
                            CardRow(
                                section = feelGood,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onArtistTap = onArtistTap,
                                onAlbumTap = onAlbumTap,
                                onPlaylistTap = onPlaylistTap
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    // ── 5. Local Top Charts + 6. Local Top Artists ────────────
                    if (data.countryCode != "ZZ" && data.chartsLocalSections.isNotEmpty()) {
                        val localVideoSection = data.chartsLocalSections.firstOrNull()
                        val localArtistSection = data.chartsLocalSections
                            .firstOrNull { "artist" in it.title.lowercase() }
                            ?: data.chartsLocalSections.lastOrNull()
                                ?.takeIf { it != localVideoSection }

                        if (localVideoSection != null) {
                            item(key = "local-charts-header") {
                                SectionHeader("Top Charts · ${data.countryName}")
                            }
                            item(key = "local-charts-row") {
                                CardRow(
                                    section = localVideoSection,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onArtistTap = onArtistTap,
                                    onAlbumTap = onAlbumTap,
                                    onPlaylistTap = onPlaylistTap
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        if (localArtistSection != null) {
                            item(key = "local-artists-header") {
                                SectionHeader("Top Artists · ${data.countryName}")
                            }
                            item(key = "local-artists-row") {
                                ArtistStackRow(
                                    section = localArtistSection,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onArtistTap = onArtistTap,
                                    onPlaylistTap = onPlaylistTap
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }

                    // ── 7. Party — first 2 sections ──────────────────────────
                    data.partySections.forEachIndexed { idx, section ->
                        item(key = "party-header-$idx") {
                            SectionHeader(section.title)
                        }
                        item(key = "party-row-$idx") {
                            CardRow(
                                section = section,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onArtistTap = onArtistTap,
                                onAlbumTap = onAlbumTap,
                                onPlaylistTap = onPlaylistTap
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Genre cards ───────────────────────────────────────────────────────────────

private val genreMoods = setOf(
    MoodCategoryParser.Mood.PARTY,
    MoodCategoryParser.Mood.ROMANCE,
    MoodCategoryParser.Mood.WORKOUT,
    MoodCategoryParser.Mood.ENERGIZE,
    MoodCategoryParser.Mood.CHILL,
    MoodCategoryParser.Mood.SAD,
    MoodCategoryParser.Mood.FOCUS
)

private val genreMoodList = listOf(
    MoodCategoryParser.Mood.PARTY,
    MoodCategoryParser.Mood.ROMANCE,
    MoodCategoryParser.Mood.WORKOUT,
    MoodCategoryParser.Mood.ENERGIZE,
    MoodCategoryParser.Mood.CHILL,
    MoodCategoryParser.Mood.SAD,
    MoodCategoryParser.Mood.FOCUS
)

private fun MoodCategoryParser.Mood.genreDrawable(): Int = when (this) {
    MoodCategoryParser.Mood.PARTY    -> R.drawable.party
    MoodCategoryParser.Mood.ROMANCE  -> R.drawable.romantic
    MoodCategoryParser.Mood.WORKOUT  -> R.drawable.workout
    MoodCategoryParser.Mood.ENERGIZE -> R.drawable.energize
    MoodCategoryParser.Mood.CHILL    -> R.drawable.chill
    MoodCategoryParser.Mood.SAD      -> R.drawable.sad
    MoodCategoryParser.Mood.FOCUS    -> R.drawable.focus
    else -> error("No genre drawable for $this")
}

@Composable
private fun GenresRow(onMoodTap: (MoodCategoryParser.Mood) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(genreMoodList, key = { it.name }) { mood ->
            GenreCard(mood = mood, onClick = { onMoodTap(mood) })
        }
    }
}

@Composable
private fun GenreCard(mood: MoodCategoryParser.Mood, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    // Strong scrim covering bottom ~50% of card
    val scrim = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.35f to Color(0x99000000),
                1.0f to Color(0xF2000000)
            )
        )
    }

    Box(
        modifier = Modifier
            .width(195.dp)
            .height(260.dp)
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .pressScale(interactionSource)
            .hapticClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        // Image
        AsyncImage(
            model = mood.genreDrawable(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Subtle full-card dim to reduce image noise
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x28000000))
        )
        // Strong gradient scrim — bottom 50%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomStart)
                .background(scrim)
        )
        // Genre name + Explore affordance
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = mood.label().uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(14.dp)
                )
            }
        }
    }
}

// ── Moods row (3 chips per column) ────────────────────────────────────────────

@Composable
private fun MoodsRow(onMoodTap: (MoodCategoryParser.Mood) -> Unit) {
    val groups = remember { MoodCategoryParser.Mood.entries.filter { it !in genreMoods }.chunked(2) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(groups, key = { index, _ -> index }) { _, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { mood ->
                    MoodChip(mood = mood, onClick = { onMoodTap(mood) })
                }
            }
        }
    }
}

@Composable
private fun MoodChip(mood: MoodCategoryParser.Mood, onClick: () -> Unit) {
    val accentColor = moodAccentColor(mood)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(172.dp)
            .hapticClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
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
            Text(
                text = mood.label(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun moodAccentColor(mood: MoodCategoryParser.Mood) = when (mood.ordinal % 4) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    2 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun MoodCategoryParser.Mood.label(): String =
    name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

// ── Card row (1 per column) ────────────────────────────────────────────────────

@Composable
private fun CardRow(
    section: HomeSection,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val cards = remember(section.items) { section.items.filterIsInstance<HomeItem.Card>() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(cards, key = { it.id ?: it.title }) { card ->
            ContentCard(
                title = card.title,
                subtitle = card.subtitle,
                thumbnailUrl = card.thumbnailUrl,
                sharedElementKey = card.sharedElementKey(),
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { handleCardTap(card, onArtistTap, onAlbumTap, onPlaylistTap) }
            )
        }
    }
}

// ── Artist stack row (2 long cards per column) ────────────────────────────────

@Composable
private fun ArtistStackRow(
    section: HomeSection,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val cards = remember(section.items) { section.items.filterIsInstance<HomeItem.Card>() }
    val groups = remember(cards) { cards.chunked(2) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(groups, key = { index, _ -> index }) { _, group ->
            Column(
                modifier = Modifier.width(142.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                group.forEach { card ->
                    ArtistTallCard(
                        title = card.title,
                        audience = card.artistAudienceText(),
                        thumbnailUrl = card.thumbnailUrl,
                        sharedElementKey = card.sharedElementKey(),
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { handleCardTap(card, onArtistTap, { _, _, _, _ -> }, onPlaylistTap) }
                    )
                }
            }
        }
    }
}

// ── Navigation helper ──────────────────────────────────────────────────────────

private fun handleCardTap(
    card: HomeItem.Card,
    onArtistTap: (String, String, String?, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val id = card.id ?: return
    when (card.pageType) {
        "MUSIC_PAGE_TYPE_PLAYLIST" ->
            onPlaylistTap(
                card.title, id,
                card.subtitle?.substringAfterLast(" • ", "")?.takeIf { it.isNotBlank() },
                card.thumbnailUrl
            )
        "MUSIC_PAGE_TYPE_ALBUM" ->
            onAlbumTap(
                card.title, id,
                card.subtitle?.substringAfterLast(" • ", "")?.takeIf { it.isNotBlank() },
                card.thumbnailUrl
            )
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" ->
            onArtistTap(card.title, id, card.thumbnailUrl, card.artistAudienceText())
    }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArtistTallCard(
    title: String,
    audience: String?,
    thumbnailUrl: String?,
    sharedElementKey: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val imageRequest = remember(context, thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .crossfade(false)
            .build()
    }
    val sharedTransitionScope = if (sharedElementKey != null && animatedVisibilityScope != null) {
        LocalSharedTransitionScope.current
    } else null
    val imageModifier =
        if (sharedTransitionScope != null && sharedElementKey != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = sharedElementKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = MediaBoundsSpring
                )
            }
        } else {
            Modifier
        }

    Column(
        modifier = Modifier
            .width(142.dp)
            .pressScale(interactionSource)
            .hapticClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imageModifier
                .width(142.dp)
                .height(142.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        if (!audience.isNullOrBlank()) {
            Text(
                text = audience,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun HomeItem.Card.artistAudienceText(): String? {
    val s = subtitle?.trim().orEmpty()
    if (s.isBlank()) return null
    val candidate = s.substringAfterLast(" • ", s).trim()
    return if (candidate.isBlank()) null else candidate
}
