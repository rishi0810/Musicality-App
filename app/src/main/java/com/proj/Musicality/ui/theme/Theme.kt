package com.proj.Musicality.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.proj.Musicality.R

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}

private val GoogleSansFlexFamily = FontFamily(
    Font(R.font.google_sans_flex_variable)
)

private fun typographyWithFontFamily(base: Typography, fontFamily: FontFamily): Typography {
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MusicAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themeMode by com.proj.Musicality.config.AppConfig.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        com.proj.Musicality.config.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.proj.Musicality.config.ThemeMode.DARK -> true
        com.proj.Musicality.config.ThemeMode.LIGHT -> false
    }

    val appBackground = if (darkTheme) Color(0xFF000000) else Color(0xFFFFFBF5)

    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context).copy(background = appBackground, surface = appBackground)
    } else {
        dynamicLightColorScheme(context).copy(background = appBackground, surface = appBackground)
    }

    val cornerRadiusPreset by com.proj.Musicality.config.AppConfig.cornerRadius.collectAsState()

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typographyWithFontFamily(Typography(), GoogleSansFlexFamily),
        motionScheme = MotionScheme.expressive()
    ) {
        CompositionLocalProvider(
            com.proj.Musicality.config.LocalCornerRadius provides cornerRadiusPreset,
            content = content
        )
    }
}

@Composable
fun GradientTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    if (darkTheme) {
        content()
    } else {
        val context = LocalContext.current
        val darkBackground = Color(0xFF000000)
        val darkColorScheme = dynamicDarkColorScheme(context).copy(
            background = darkBackground,
            surface = darkBackground
        )
        MaterialTheme(colorScheme = darkColorScheme, content = content)
    }
}

@Composable
fun ForceGradientStatusBar() {
    val darkTheme = isSystemInDarkTheme()
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(darkTheme, activity) {
        if (activity == null || darkTheme) return@DisposableEffect onDispose {}
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        onDispose {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            )
        }
    }
}
