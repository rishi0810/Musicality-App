package com.proj.Musicality.ui.theme

import android.os.Build
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MusicAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    val appBackground = if (darkTheme) Color(0xFF000000) else Color(0xFFFFFBF5)

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context).copy(background = appBackground, surface = appBackground)
            else dynamicLightColorScheme(context).copy(background = appBackground, surface = appBackground)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFBB86FC),
            background = appBackground,
            surface = appBackground,
            surfaceContainerHigh = Color(0xFF1E1E1E)
        )
        else -> lightColorScheme(
            background = appBackground,
            surface = appBackground
        )
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
