package com.proj.Musicality.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proj.Musicality.ui.theme.LocalSharedTransitionScope
import com.proj.Musicality.ui.theme.MediaBoundsSpring

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ContentCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    thumbnailSize: Dp = 150.dp,
    onOverflowClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sharedTransitionScope = if (sharedElementKey != null && animatedVisibilityScope != null) {
        LocalSharedTransitionScope.current
    } else null

    Column(
        modifier = modifier
            .width(cardWidth)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        val thumbnailModifier =
            if (sharedTransitionScope != null && sharedElementKey != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = sharedElementKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = MediaBoundsSpring
                )
            }
        } else Modifier

        Box {
            Thumbnail(
                url = thumbnailUrl,
                size = thumbnailSize,
                cornerRadius = 8.dp,
                modifier = thumbnailModifier
            )
            if (onOverflowClick != null) {
                IconButton(
                    onClick = onOverflowClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions for $title",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.height(68.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
