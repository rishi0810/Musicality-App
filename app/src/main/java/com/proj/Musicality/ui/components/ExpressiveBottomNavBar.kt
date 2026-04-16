package com.proj.Musicality.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.proj.Musicality.ui.theme.LocalPlaybackUiPalette

@Immutable
data class ExpressiveBottomNavItem(
    val label: String,
    val contentDescription: String = label,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBottomNavBar(
    items: List<ExpressiveBottomNavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 64.dp,
    barWidth: Dp? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (items.isEmpty()) return

    val motionScheme = MaterialTheme.motionScheme
    val safeSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val playbackUiPalette = LocalPlaybackUiPalette.current
    val activeIndicatorColor = playbackUiPalette?.navIndicator ?: indicatorColor
    val selectedContentColor = if (playbackUiPalette != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val count = items.size.coerceAtLeast(1)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val contentPadding = 8.dp
                val indicatorInset = 4.dp

                val availableWidth = maxWidth - (contentPadding * 2)
                val itemWidth = availableWidth / count
                val rawIndicatorWidth = itemWidth - (indicatorInset * 2)
                val minIndicatorWidth = 36.dp.coerceAtMost(itemWidth)
                val indicatorWidth = rawIndicatorWidth.coerceIn(minIndicatorWidth, itemWidth)
                val indicatorHeight = 50.dp

                val indicatorXTarget = (itemWidth * safeSelectedIndex) + indicatorInset
                val indicatorX by animateDpAsState(
                    targetValue = indicatorXTarget,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "nav-indicator-x"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = contentPadding, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(indicatorX.roundToPx(), 0) }
                            .height(indicatorHeight)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(26.dp))
                            .background(activeIndicatorColor)
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            val selected = index == safeSelectedIndex
                            val interactionSource = remember { MutableInteractionSource() }

                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.10f else 1.0f,
                                animationSpec = motionScheme.fastSpatialSpec(),
                                label = "nav-icon-scale-$index"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .pressScale(interactionSource)
                                    .hapticClickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = item.onClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val tint = if (selected) selectedContentColor else unselectedContentColor
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.contentDescription,
                                        tint = tint,
                                        modifier = Modifier
                                            .height(27.dp)
                                            .width(27.dp)
                                            .offset(y = (-1).dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
