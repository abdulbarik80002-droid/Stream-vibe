package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StreamVibeColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = Color.White,
    primaryContainer = CyberViolet,
    onPrimaryContainer = Color.White,
    secondary = ElectricCyan,
    onSecondary = DarkStudioBg,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = ElectricCyan,
    tertiary = RoyalGold,
    onTertiary = DarkStudioBg,
    background = DarkStudioBg,
    onBackground = TextPrimaryWhite,
    surface = DarkSurfaceCard,
    onSurface = TextPrimaryWhite,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryMuted
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StreamVibeColorScheme,
        typography = Typography,
        content = content
    )
}
