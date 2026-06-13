package com.proj.Musicality.ui.theme

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.proj.Musicality.R

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}

private val RobotoFlexFamily = FontFamily(
    Font(R.font.roboto_flex_variable)
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
    val appBackground = Color(0xFF000000)
    val colorScheme = darkColorScheme()
        .copy(background = appBackground, surface = appBackground)

    val cornerRadiusPreset by com.proj.Musicality.config.AppConfig.cornerRadius.collectAsState()

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typographyWithFontFamily(Typography(), RobotoFlexFamily),
        motionScheme = MotionScheme.expressive()
    ) {
        CompositionLocalProvider(
            com.proj.Musicality.config.LocalCornerRadius provides cornerRadiusPreset,
            content = content
        )
    }
}

// No-op since the app is dark-only; kept so gradient detail screens keep a clear theme boundary.
@Composable
fun GradientTheme(content: @Composable () -> Unit) {
    content()
}
