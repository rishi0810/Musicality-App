package com.proj.Musicality.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongListItem(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onOverflowClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sharedTransitionScope = if (sharedElementKey != null && animatedVisibilityScope != null) {
        LocalSharedTransitionScope.current
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnailModifier = if (sharedTransitionScope != null && sharedElementKey != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = sharedElementKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = MediaBoundsSpring
                )
            }
        } else Modifier

        Thumbnail(
            url = thumbnailUrl,
            size = 48.dp,
            modifier = thumbnailModifier
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onOverflowClick != null) {
            IconButton(onClick = onOverflowClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More actions for $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
