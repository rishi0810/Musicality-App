package com.example.musicality.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Section header with title on left and optional "See all" action on right.
 * Used in Library screen for Albums, Artists, Playlists sections.
 */
@Composable
fun SectionHeaderWithAction(
    title: String,
    onSeeAllClick: () -> Unit,
    showSeeAll: Boolean = true,
    textColor: Color = Color.White,
    actionColor: Color = Color.White.copy(alpha = 0.7f),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        
        if (showSeeAll) {
            Text(
                text = "See all",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = actionColor,
                modifier = Modifier.clickable(onClick = onSeeAllClick)
            )
        }
    }
}
