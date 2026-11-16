package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexzyLightColorScheme = lightColorScheme(
  primary = BrandTealPrimary,
  onPrimary = Color.White,
  primaryContainer = BrandMintBackground,
  onPrimaryContainer = BrandNavyText,
  secondary = BrandTealLight,
  onSecondary = Color.White,
  secondaryContainer = BrandMintSurface,
  onSecondaryContainer = BrandNavyText,
  tertiary = StatusAttentionAmber,
  onTertiary = Color.White,
  tertiaryContainer = StatusAttentionHoney,
  onTertiaryContainer = BrandNavyText,
  background = BackgroundWhite,
  onBackground = BrandNavyText,
  surface = BackgroundWhite,
  onSurface = BrandNavyText,
  surfaceVariant = SurfaceCard,
  onSurfaceVariant = BrandNavyMuted,
  outline = SurfaceBorder,
  outlineVariant = SurfaceDivider
)

private val NexzyDarkColorScheme = darkColorScheme(
  primary = BrandTealLight,
  onPrimary = Color(0xFF003833),
  primaryContainer = BrandTealDark,
  onPrimaryContainer = BrandMintBackground,
  secondary = BrandTealLight,
  onSecondary = Color(0xFF003833),
  secondaryContainer = BrandTealDark,
  onSecondaryContainer = BrandMintBackground,
  background = Color(0xFF0B191E),
  onBackground = Color(0xFFE2F1F3),
  surface = Color(0xFF10252D),
  onSurface = Color(0xFFE2F1F3),
  surfaceVariant = Color(0xFF16323B),
  onSurfaceVariant = Color(0xFF90B4BD),
  outline = Color(0xFF264C58)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep brand integrity
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) NexzyDarkColorScheme else NexzyLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
