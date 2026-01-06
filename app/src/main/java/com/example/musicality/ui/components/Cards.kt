package com.example.musicality.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Reusable UI components for card-based layouts.
 * Used across Library, Search Results, and other screens.
 */

// Default colors (can be overridden)
val DefaultSurfaceColor = Color(0xFF121212)
val DefaultTextPrimary = Color.White
val DefaultTextMuted = Color.White.copy(alpha = 0.5f)


@Composable
fun SectionHeader(
    title: String,
    textColor: Color = DefaultTextPrimary,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

/**
 * Card with icon for "You" section items (Downloads, Liked Songs, etc.)
 */
@Composable
fun IconCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    cardSize: Dp = 140.dp,
    iconSize: Dp = 48.dp,
    surfaceColor: Color = DefaultSurfaceColor,
    textColor: Color = DefaultTextPrimary,
    subtitleColor: Color = DefaultTextMuted
) {
    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(SquircleShape12)
                .background(surfaceColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = textColor,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Card with thumbnail image for Albums, Artists, Playlists.
 */
@Composable
fun ThumbnailCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
    cardSize: Dp = 140.dp,
    surfaceColor: Color = DefaultSurfaceColor,
    textColor: Color = DefaultTextPrimary,
    subtitleColor: Color = DefaultTextMuted
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(SquircleShape12)
                .background(surfaceColor)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .build(),
                contentDescription = title,
                imageLoader = ImageLoaderConfig.getImageLoader(context),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
