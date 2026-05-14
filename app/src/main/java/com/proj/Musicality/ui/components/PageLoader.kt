package com.proj.Musicality.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Single Material3 loader used across all screens — replaces the previous skeleton/shimmer
// loaders so the loading affordance stays consistent everywhere.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageLoader(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(56.dp),
            color = accentColor
        )
    }
}
