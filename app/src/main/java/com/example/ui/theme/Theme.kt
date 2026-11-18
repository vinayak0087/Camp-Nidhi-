package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val CampNidhiLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = NavyOnPrimaryContainer,
    secondary = ForestGreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = ForestGreenContainer,
    onSecondaryContainer = ForestGreenOnContainer,
    tertiary = WarmGoldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = WarmGoldContainer,
    onTertiaryContainer = WarmGoldOnContainer,
    background = WarmOffWhiteBg,
    onBackground = InkCharcoal,
    surface = WarmSurface,
    onSurface = InkCharcoal,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = WarmOutline,
    error = StatusDangerCrimson,
    onError = Color.White,
    errorContainer = StatusDangerContainer,
    onErrorContainer = StatusDangerCrimson
)

private val CampNidhiDarkColorScheme = darkColorScheme(
    primary = DarkNavyPrimary,
    onPrimary = Color(0xFF061A2E),
    primaryContainer = DarkNavyContainer,
    onPrimaryContainer = Color(0xFFD6E8FA),
    secondary = DarkForestGreen,
    onSecondary = Color(0xFF042617),
    secondaryContainer = DarkForestContainer,
    onSecondaryContainer = Color(0xFFC5F2DA),
    tertiary = DarkGoldTertiary,
    onTertiary = Color(0xFF2B1E00),
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = Color(0xFFFAE6AD),
    background = DarkNavyBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkOutline,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val CampNidhiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CampNidhiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) CampNidhiDarkColorScheme else CampNidhiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CampNidhiTypography,
        shapes = CampNidhiShapes,
        content = content
    )
}
