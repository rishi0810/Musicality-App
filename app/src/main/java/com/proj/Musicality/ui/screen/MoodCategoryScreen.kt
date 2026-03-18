package com.proj.Musicality.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.parser.MoodCategoryParser
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.ErrorMessage
import com.proj.Musicality.ui.components.SectionHeader
import com.proj.Musicality.ui.components.ShimmerSection
import com.proj.Musicality.viewmodel.MoodCategoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoodCategoryScreen(
    mood: MoodCategoryParser.Mood,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val viewModel: MoodCategoryViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(mood) { viewModel.initialize(mood) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                viewModel.refresh(mood)
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
            item(key = "mood-title") {
                Text(
                    text = mood.label(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 58.dp, bottom = 10.dp)
                )
            }

            when (val s = state) {
                is MoodCategoryViewModel.UiState.Loading -> {
                    items(5, key = { "mood-shimmer-$it" }) { ShimmerSection() }
                }

                is MoodCategoryViewModel.UiState.Error -> {
                    item(key = "mood-error") { ErrorMessage(message = s.message) }
                }

                is MoodCategoryViewModel.UiState.Loaded -> {
                    if (s.sections.isEmpty()) {
                        item(key = "mood-empty") {
                            ErrorMessage(message = "No sections found for ${s.mood.label()}.")
                        }
                    } else {
                        s.sections.forEachIndexed { index, section ->
                            item(key = "mood-header-$index") {
                                SectionHeader(section.title)
                            }
                            item(key = "mood-row-$index") {
                                MoodCategoryCardRow(
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
}

@Composable
private fun MoodCategoryCardRow(
    section: HomeSection,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onArtistTap: (String, String, String?) -> Unit,
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
                onClick = { handleMoodCardTap(card, onArtistTap, onAlbumTap, onPlaylistTap) }
            )
        }
    }
}

private fun handleMoodCardTap(
    card: HomeItem.Card,
    onArtistTap: (String, String, String?) -> Unit,
    onAlbumTap: (String, String, String?, String?) -> Unit,
    onPlaylistTap: (String, String, String?, String?) -> Unit
) {
    val id = card.id ?: return
    when (card.pageType) {
        "MUSIC_PAGE_TYPE_PLAYLIST" ->
            onPlaylistTap(
                card.title,
                id,
                card.subtitle?.substringAfterLast(" • ", "")?.takeIf { it.isNotBlank() },
                card.thumbnailUrl
            )

        "MUSIC_PAGE_TYPE_ALBUM" ->
            onAlbumTap(
                card.title,
                id,
                card.subtitle?.substringAfterLast(" • ", "")?.takeIf { it.isNotBlank() },
                card.thumbnailUrl
            )

        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" ->
            onArtistTap(card.title, id, card.thumbnailUrl)
    }
}

private fun MoodCategoryParser.Mood.label(): String =
    name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

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
