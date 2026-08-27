package com.giftcondoctor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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

// 다크 토큰. 대비 검증값은 UIUX_REVIEW_2026-08-28.md 2절 기준이며 13개 조합 전부
// WCAG AA 이상이다. 라이트에서 primary 는 어둡게, 다크에서는 밝게 뒤집는다.
val GDDarkBackground = Color(0xFF101314)
val GDDarkSurface = Color(0xFF171A1B)
val GDDarkSurfaceVariant = Color(0xFF22272A)
val GDDarkOnSurface = Color(0xFFE6E9EA)
val GDDarkOnSurfaceVariant = Color(0xFFB4BCC1)
val GDDarkPrimary = Color(0xFF4FD8C9)
val GDDarkOnPrimary = Color(0xFF00332F)
val GDDarkPrimaryContainer = Color(0xFF00504A)
val GDDarkOnPrimaryContainer = Color(0xFFA7EFE5)
val GDDarkTertiary = Color(0xFF7CC9F0)
val GDDarkError = Color(0xFFFF8A85)

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

private val GDDarkColorScheme = darkColorScheme(
    primary = GDDarkPrimary,
    onPrimary = GDDarkOnPrimary,
    primaryContainer = GDDarkPrimaryContainer,
    onPrimaryContainer = GDDarkOnPrimaryContainer,
    secondary = Color(0xFF7FD8CE),
    onSecondary = Color(0xFF00332F),
    secondaryContainer = Color(0xFF0B3B36),
    onSecondaryContainer = Color(0xFF8FE3D6),
    tertiary = GDDarkTertiary,
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF0A2A3D),
    onTertiaryContainer = Color(0xFF8FD0F0),
    background = GDDarkBackground,
    onBackground = GDDarkOnSurface,
    surface = GDDarkSurface,
    onSurface = GDDarkOnSurface,
    surfaceVariant = GDDarkSurfaceVariant,
    onSurfaceVariant = GDDarkOnSurfaceVariant,
    outline = GDOutline,
    outlineVariant = Color(0xFF2E3538),
    error = GDDarkError,
    onError = Color(0xFF4A0004)
)

private val GDShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) GDDarkColorScheme else GDColorScheme,
        typography = GDTypography,
        shapes = GDShapes,
        content = content
    )
}
