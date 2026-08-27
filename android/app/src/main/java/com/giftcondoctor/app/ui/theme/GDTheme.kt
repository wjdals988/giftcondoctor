package com.giftcondoctor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 대비 검증값은 UIUX_REVIEW_2026-08-28.md 1절 기준이다.
// GDBrand 는 브랜드 색조 보존용이며 텍스트·아이콘을 얹지 않는다.
val GDBrand = Color(0xFF00B4A6)
val GDPrimary = Color(0xFF00786F)
val GDPrimaryText = Color(0xFF006B63)
val GDPrimaryDark = Color(0xFF007A73)
val GDSecondary = Color(0xFFA7E8DB)
val GDTertiary = Color(0xFF0070A3)
val GDAccent = Color(0xFFFFC247)
val GDSurface = Color(0xFFFFFFFF)
val GDBackground = Color(0xFFFBFCFE)
val GDSoftMint = Color(0xFFE7F8F4)
val GDOutline = Color(0xFF8A9299)
val GDOutlineVariant = Color(0xFFE2E6EA)
val GDOnSurface = Color(0xFF1A1C1E)

private val GDColorScheme = lightColorScheme(
    primary = GDPrimary,
    onPrimary = Color.White,
    primaryContainer = GDSoftMint,
    onPrimaryContainer = GDPrimaryDark,
    secondary = GDSecondary,
    onSecondary = Color(0xFF083A36),
    secondaryContainer = Color(0xFFEAFBF6),
    onSecondaryContainer = Color(0xFF143D38),
    tertiary = GDTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F3FF),
    onTertiaryContainer = Color(0xFF00364F),
    background = GDBackground,
    onBackground = GDOnSurface,
    surface = GDSurface,
    onSurface = GDOnSurface,
    surfaceVariant = Color(0xFFF4F7F9),
    onSurfaceVariant = Color(0xFF616A73),
    outline = GDOutline,
    outlineVariant = GDOutlineVariant,
    error = Color(0xFFC4262B),
    onError = Color.White
)

private val GDShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GDColorScheme,
        typography = GDTypography,
        shapes = GDShapes,
        content = content
    )
}
