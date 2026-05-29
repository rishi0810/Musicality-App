package com.proj.Musicality.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proj.Musicality.config.LocalCornerRadius
import com.proj.Musicality.config.scaled

object AppTypography {
    val PageTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)

    val PageTitleSecondary: TextStyle
        @Composable get() = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)

    val DetailTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

    val SectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)

    val PersonalizedSectionName: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

    val CardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val CardSubtitle: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    val ListTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    val ListSubtitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val SheetTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium
}

object AppSpacing {
    val ScreenHorizontalPadding = 16.dp
    val SectionGap = 14.dp
    val ItemGap = 12.dp
    val ItemGapTight = 8.dp
    val SectionHeaderPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val SheetHeaderPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    val MiniPlayerBottomExtra = 8.dp
}

object AppShapes {
    @Composable
    fun card(): Shape {
        val r = 8.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun cardLarge(): Shape {
        val r = 16.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun settingsRow(): Shape {
        val r = 12.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun chip(): Shape {
        val r = 10.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun searchBar(): Shape {
        val r = 22.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun bottomSheet(): Shape {
        val r = 16.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(topStart = r, topEnd = r)
    }

    @Composable
    fun thumbnailSmall(): Shape {
        val r = 4.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun thumbnailMedium(): Shape {
        val r = 8.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun thumbnailLarge(): Shape {
        val r = 12.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }

    @Composable
    fun navIndicator(): Shape {
        val r = 26.dp.scaled(LocalCornerRadius.current)
        return RoundedCornerShape(r)
    }
}

object AppColors {
    val ScrimLight = Color(0x28000000)
    val ScrimMedium = Color(0x99000000)
    val ScrimHeavy = Color(0xF2000000)

    val ArtistCtaContainer = Color(0xFF07080B)
    val ArtistCtaContainerVariant = Color(0xCC090A0D)
    val ArtistCtaContent = Color(0xFFF6F7FB)
    val ArtistCtaBorder: Color = Color.White.copy(alpha = 0.14f)
    val ArtistGradientBase = Color(0xFF0D0D0D)

    val PlayerTrack = Color(0xFF6A6A6A)
    val PlayerProgress: Color = Color.White
    val PlayerLabelDim = Color(0xFFB9BDC2)
}
