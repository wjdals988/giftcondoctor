package com.giftcondoctor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val GDPrimary = Color(0xFF00B4A6)
val GDPrimaryDark = Color(0xFF007A73)
val GDSecondary = Color(0xFFA7E8DB)
val GDTertiary = Color(0xFF0095D6)
val GDAccent = Color(0xFFFFC247)
val GDSurface = Color(0xFFFFFFFF)
val GDBackground = Color(0xFFFBFCFE)
val GDSoftMint = Color(0xFFE7F8F4)
val GDOutline = Color(0xFFE2E6EA)
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
    error = Color(0xFFE5484D),
    onError = Color.White
)

private val GDShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun GDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GDColorScheme,
        shapes = GDShapes,
        content = content
    )
}
