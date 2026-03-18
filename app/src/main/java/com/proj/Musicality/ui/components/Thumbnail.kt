package com.proj.Musicality.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size

@Composable
fun Thumbnail(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    allowNetworkFetch: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val context = LocalContext.current
    val targetPx = with(density) { size.roundToPx() }
    val imageRequest = remember(context, url, targetPx, allowNetworkFetch) {
        ImageRequest.Builder(context).apply {
            if (!allowNetworkFetch) {
                networkCachePolicy(CachePolicy.DISABLED)
            }
        }
            .data(url)
            .size(Size(targetPx, targetPx))
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop
    )
}
