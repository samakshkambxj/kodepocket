package dev.kodepocket.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette used when dynamic color is unavailable. The accent is a
 * terminal green so the app reads as a developer tool rather than a chat app.
 */
val BrandLightColors = lightColorScheme(
    primary = Color(0xFF2F6B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F26B),
    onPrimaryContainer = Color(0xFF0C2000),
    inversePrimary = Color(0xFF9CD65B),
    secondary = Color(0xFF57624A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE7C8),
    onSecondaryContainer = Color(0xFF151E0C),
    tertiary = Color(0xFF386660),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBECE4),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF8FAF3),
    onBackground = Color(0xFF191D16),
    surface = Color(0xFFF8FAF3),
    onSurface = Color(0xFF191D16),
    surfaceVariant = Color(0xFFE0E4D6),
    onSurfaceVariant = Color(0xFF44483D),
    surfaceTint = Color(0xFF2F6B00),
    inverseSurface = Color(0xFF2E322A),
    inverseOnSurface = Color(0xFFEFF2E6),
    outline = Color(0xFF74796B),
    outlineVariant = Color(0xFFC4C8B8),
    surfaceDim = Color(0xFFD8DCD0),
    surfaceBright = Color(0xFFF8FAF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5EA),
    surfaceContainer = Color(0xFFECEFE3),
    surfaceContainerHigh = Color(0xFFE6E9DD),
    surfaceContainerHighest = Color(0xFFE0E4D6),
)

val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF9CD65B),
    onPrimary = Color(0xFF163700),
    primaryContainer = Color(0xFF2F4F00),
    onPrimaryContainer = Color(0xFFB7F26B),
    inversePrimary = Color(0xFF2F6B00),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331F),
    secondaryContainer = Color(0xFF404A34),
    onSecondaryContainer = Color(0xFFDBE7C8),
    tertiary = Color(0xFF9FD0C8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF1F4E48),
    onTertiaryContainer = Color(0xFFBBECE4),
    background = Color(0xFF11140E),
    onBackground = Color(0xFFE2E4D9),
    surface = Color(0xFF11140E),
    onSurface = Color(0xFFE2E4D9),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC4C8B8),
    surfaceTint = Color(0xFF9CD65B),
    inverseSurface = Color(0xFFE2E4D9),
    inverseOnSurface = Color(0xFF2E322A),
    outline = Color(0xFF8E9284),
    outlineVariant = Color(0xFF44483D),
    surfaceDim = Color(0xFF11140E),
    surfaceBright = Color(0xFF373B32),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF191D16),
    surfaceContainer = Color(0xFF1D211A),
    surfaceContainerHigh = Color(0xFF272B24),
    surfaceContainerHighest = Color(0xFF32362E),
)
