package com.example.musicality.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// SHIMMER BRUSH AND MODIFIER
// ============================================================================

/**
 * Pre-defined shimmer colors optimized for dark theme
 */
private val ShimmerColorLight = Color.White.copy(alpha = 0.1f)
private val ShimmerColorMedium = Color.White.copy(alpha = 0.2f)
private val ShimmerColorHighlight = Color.White.copy(alpha = 0.35f)

/**
 * Creates an animated shimmer brush for skeleton loading effects.
 * Uses a linear gradient that sweeps across the component.
 *
 * @param duration Animation duration in milliseconds
 * @param angle Angle of the shimmer gradient
 */
@Composable
fun shimmerBrush(
    duration: Int = 1200,
    angle: Float = 20f
): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    
    // Calculate gradient offset based on progress
    val translateX = shimmerProgress * 1000f
    
    return remember(shimmerProgress) {
        Brush.linearGradient(
            colors = listOf(
                ShimmerColorLight,
                ShimmerColorMedium,
                ShimmerColorHighlight,
                ShimmerColorMedium,
                ShimmerColorLight
            ),
            start = Offset(translateX - 300f, 0f),
            end = Offset(translateX + 300f, 0f)
        )
    }
}

/**
 * Modifier extension that applies shimmer effect to any composable.
 * Best applied to Box elements that represent skeleton placeholders.
 */
fun Modifier.shimmerEffect(
    duration: Int = 1200,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
): Modifier = composed {
    val brush = shimmerBrush(duration = duration)
    this
        .clip(shape)
        .background(brush)
}

// ============================================================================
// SKELETON ITEM COMPONENTS - Song/Video List Items
// ============================================================================

/**
 * Skeleton for song result items in search results and lists.
 * Matches the layout of SongResultItem, VideoResultItem in SearchResultsScreen
 */
@Composable
fun SkeletonSongItem(
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = 56.dp
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ghost Thumbnail
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Ghost Content
        Column(modifier = Modifier.weight(1f)) {
            // Ghost Title
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Ghost Subtitle
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        
        // Ghost Duration
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Skeleton for video result items with wider thumbnail
 */
@Composable
fun SkeletonVideoItem(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ghost Video Thumbnail (wider)
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Ghost Content
        Column(modifier = Modifier.weight(1f)) {
            // Ghost Title (2 lines possible)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Ghost Channel name
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Ghost Views + Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

/**
 * Skeleton for artist items with circular thumbnail
 */
@Composable
fun SkeletonArtistItem(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ghost Circular Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(brush)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Ghost Content
        Column(modifier = Modifier.weight(1f)) {
            // Ghost Name
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Ghost Type + Monthly listeners
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

// ============================================================================
// SKELETON CAROUSEL ITEMS - For LazyRow components
// ============================================================================

/**
 * Skeleton for album/playlist carousel items
 * Matches AlbumItem, RelatedAlbumItem, PlaylistItem
 */
@Composable
fun SkeletonCarouselItem(
    modifier: Modifier = Modifier,
    itemWidth: Dp = 140.dp
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier.width(itemWidth),
        horizontalAlignment = Alignment.Start
    ) {
        // Ghost Cover Image
        Box(
            modifier = Modifier
                .size(itemWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Ghost Title
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Ghost Subtitle
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Skeleton for circular artist carousel items
 * Matches SimilarArtistItem
 */
@Composable
fun SkeletonCircleCarouselItem(
    modifier: Modifier = Modifier,
    itemWidth: Dp = 120.dp,
    imageSize: Dp = 100.dp
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ghost Circular Image
        Box(
            modifier = Modifier
                .size(imageSize)
                .clip(CircleShape)
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Ghost Name
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Ghost Subtitle
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

// ============================================================================
// SKELETON SECTIONS - Complete Loading Sections
// ============================================================================

/**
 * Skeleton for horizontal carousel sections (Albums, Playlists, Similar Artists)
 */
@Composable
fun SkeletonCarouselSection(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
    isCircular: Boolean = false
) {
    val brush = shimmerBrush()
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Ghost Section Title
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .width(120.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        // Ghost Carousel Items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(itemCount) {
                if (isCircular) {
                    SkeletonCircleCarouselItem()
                } else {
                    SkeletonCarouselItem()
                }
            }
        }
    }
}

/**
 * Skeleton for song list sections (Top Songs in Artist)
 */
@Composable
fun SkeletonSongListSection(
    modifier: Modifier = Modifier,
    itemCount: Int = 5
) {
    val brush = shimmerBrush()
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Ghost Section Title
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .width(100.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        // Ghost Song Items
        repeat(itemCount) {
            SkeletonAlbumSongItem(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Skeleton for album song items (no thumbnail version)
 * Matches AlbumSongItem
 */
@Composable
fun SkeletonAlbumSongItem(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ghost Song Info
        Column(modifier = Modifier.weight(1f)) {
            // Ghost Title
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Ghost View count
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        
        // Ghost Duration
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Skeleton for playlist/album song items (with thumbnail)
 * Matches PlaylistSongItem
 */
@Composable
fun SkeletonPlaylistSongItem(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ghost Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Ghost Song Info
        Column(modifier = Modifier.weight(1f)) {
            // Ghost Title
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Ghost Artist
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        
        // Ghost Duration
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

// ============================================================================
// SCREEN-SPECIFIC SKELETON LOADERS
// ============================================================================

/**
 * Complete skeleton for Album Screen loading state
 * Matches the layout of AlbumContent
 */
@Composable
fun SkeletonAlbumScreen(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Ghost Back Button
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 48.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Ghost Album Cover (centered 70%)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Ghost Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left - Album info
            Column(modifier = Modifier.weight(1f)) {
                // Ghost Album Name
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Ghost Artist Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Ghost Song count + duration
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            
            // Right - Control buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Ghost Song Items
        repeat(6) {
            SkeletonAlbumSongItem(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Complete skeleton for Artist Screen loading state
 * Matches the layout of ArtistContent
 */
@Composable
fun SkeletonArtistScreen(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Ghost Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
        ) {
            // Ghost Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush)
            )
            
            // Ghost Back Button
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 48.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            
            // Ghost Artist Name + Monthly Listeners
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }
        }
        
        // Ghost Top Songs Section
        SkeletonSongListSection(itemCount = 4)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ghost Albums Section
        SkeletonCarouselSection(itemCount = 3)
    }
}

/**
 * Complete skeleton for Playlist Screen loading state
 * Matches the layout of PlaylistContent
 */
@Composable
fun SkeletonPlaylistScreen(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Ghost Back Button
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 48.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Ghost Playlist Cover (centered 70%)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Ghost Playlist Name (centered)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(180.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Ghost Track count + duration (centered)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(120.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ghost Control buttons (centered)
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Ghost Song Items
        repeat(6) {
            SkeletonPlaylistSongItem(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Complete skeleton for Search Results loading state
 * Shows category pills skeleton + list items
 */
@Composable
fun SkeletonSearchResultsList(
    modifier: Modifier = Modifier,
    itemCount: Int = 8
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(itemCount) { index ->
            when (index % 4) {
                0, 1 -> SkeletonSongItem(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                2 -> SkeletonArtistItem(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                else -> SkeletonVideoItem(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Skeleton for category selection pills
 */
@Composable
fun SkeletonCategoryPills(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == 0) 60.dp else (70 + index * 10).dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(brush)
            )
        }
    }
}

/**
 * Skeleton for search suggestions section
 */
@Composable
fun SkeletonSuggestionsSection(
    modifier: Modifier = Modifier,
    suggestionCount: Int = 4
) {
    val brush = shimmerBrush()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ghost Section Title
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        
        repeat(suggestionCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ghost Icon
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Ghost Text (varied widths)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f + (index * 0.1f).coerceAtMost(0.4f))
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

// ============================================================================
// SHIMMER ASYNC IMAGE - Image loading with shimmer placeholder
// ============================================================================

/**
 * A generic shimmer placeholder box that can be used for any shape/size
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}
