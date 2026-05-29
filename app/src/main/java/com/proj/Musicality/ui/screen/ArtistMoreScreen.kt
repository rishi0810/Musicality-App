package com.proj.Musicality.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proj.Musicality.data.model.ArtistContent
import com.proj.Musicality.data.model.ArtistVideo
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.navigation.Route
import com.proj.Musicality.ui.components.ContentCard
import com.proj.Musicality.ui.components.ErrorMessage
import com.proj.Musicality.ui.components.Thumbnail
import com.proj.Musicality.ui.components.hapticClickable
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring
import com.proj.Musicality.viewmodel.ArtistMoreViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.proj.Musicality.ui.components.pressScale
import com.proj.Musicality.viewmodel.ArtistMoreViewModelFactory
import com.proj.Musicality.ui.theme.AppSpacing
import com.proj.Musicality.ui.theme.AppTypography

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistMoreScreen(
    seed: Route.ArtistMore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAlbumTap: (String, String, String?, String?, String?) -> Unit,
    onVideoTap: (MediaItem) -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val viewModel: ArtistMoreViewModel = viewModel(
        key = seed.browseId,
        factory = ArtistMoreViewModelFactory(seed.browseId, seed.params, seed.type)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = statusBarTop + 48.dp)
    ) {
        Text(
            text = seed.sectionTitle,
            style = AppTypography.DetailTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = seed.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
        )

        when (val s = state) {
            is ArtistMoreViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is ArtistMoreViewModel.UiState.LoadedSingles -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = collapsedMiniPlayerHeight + AppSpacing.MiniPlayerBottomExtra
                    )
                ) {
                    items(s.items, key = { it.browseId }) { item ->
                        ContentCard(
                            title = item.title,
                            subtitle = item.year,
                            thumbnailUrl = item.image,
                            sharedElementKey = "thumb-more-${item.browseId}",
                            animatedVisibilityScope = animatedVisibilityScope,
                            cardWidth = 180.dp,
                            thumbnailSize = 180.dp,
                            onClick = {
                                onAlbumTap(item.title, item.browseId, seed.artistName, item.image, item.year)
                            }
                        )
                    }
                }
            }

            is ArtistMoreViewModel.UiState.LoadedVideos -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = collapsedMiniPlayerHeight + AppSpacing.MiniPlayerBottomExtra
                    )
                ) {
                    items(s.items, key = { it.videoId }) { video ->
                        VideoGridCard(
                            video = video,
                            artistName = seed.artistName,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onTap = {
                                onVideoTap(
                                    MediaItem(
                                        videoId = video.videoId,
                                        title = video.title,
                                        artistName = seed.artistName,
                                        artistId = null,
                                        albumName = null,
                                        albumId = null,
                                        thumbnailUrl = video.image,
                                        durationText = null,
                                        musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
                                    )
                                )
                            }
                        )
                    }
                }
            }

            is ArtistMoreViewModel.UiState.Error -> {
                ErrorMessage(s.message)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VideoGridCard(
    video: ArtistVideo,
    artistName: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onTap: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .hapticClickable(interactionSource = interactionSource, indication = null, onClick = onTap)
    ) {
        val thumbModifier = with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "thumb-more-${video.videoId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = MediaBoundsSpring
            )
        }
        Thumbnail(
            url = video.image,
            size = 180.dp,
            cornerRadius = 8.dp,
            modifier = thumbModifier
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!video.views.isNullOrBlank()) {
            Text(
                text = video.views,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
