package com.example.musicality.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8F529), // Yellow accent for buttons
    onPrimary = Color.Black,
    secondary = Color(0xFF9000FF),
    onSecondary = Color.White,
    tertiary = Color(0xFF00D1FF),
    onTertiary = Color.Black,
    background = Color(0xFF0B0E14), // DeepSpace
    onBackground = Color.White,
    surface = Color(0xFF1A1C2E), // MidnightBlue
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2D3E),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = SoftWhite,
    surface = Color.White

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MusicalityTheme(
    darkTheme: Boolean = true, // Force dark theme for consistent look
    // Dynamic color disabled to prevent system colors from affecting the app
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use our custom color scheme, never dynamic colors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
