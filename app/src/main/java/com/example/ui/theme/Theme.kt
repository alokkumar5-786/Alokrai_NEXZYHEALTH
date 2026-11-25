package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class NexzyExtendedColors(
  val heroGradientStart: Color,
  val heroGradientMid: Color,
  val heroGradientEnd: Color,
  val heroCardSurface: Color,
  val heroCardBorder: Color,
  val heroSupportingText: Color,
  val dividerBorder: Color,
  val statusNormal: Color,
  val statusNormalContainer: Color,
  val statusNormalBorder: Color,
  val statusNormalText: Color,
  val statusAttention: Color,
  val statusAttentionContainer: Color,
  val statusAttentionBorder: Color,
  val statusAttentionText: Color,
  val statusAlert: Color,
  val statusAlertContainer: Color,
  val statusAlertBorder: Color,
  val statusAlertText: Color,
  val statusNeutral: Color,
  val statusNeutralContainer: Color,
  val statusNeutralBorder: Color,
  val statusNeutralText: Color,
  val statusInfoContainer: Color,
  val statusInfoBorder: Color,
  val statusInfoAccent: Color,
  val statusInfoText: Color,
  val disabledContainer: Color,
  val disabledContent: Color
) {
  val heroGradientBrush: Brush
    get() = Brush.linearGradient(
      colors = listOf(heroGradientStart, heroGradientMid, heroGradientEnd)
    )

  val verticalHeroBrush: Brush
    get() = Brush.verticalGradient(
      colors = listOf(heroGradientStart, heroGradientMid, heroGradientEnd)
    )
}

private val LightExtendedColors = NexzyExtendedColors(
  heroGradientStart = MidnightNavy,
  heroGradientMid = DeepNavy,
  heroGradientEnd = RoyalBlue,
  heroCardSurface = Color(0x1FFFFFFF),
  heroCardBorder = MistBlue.copy(alpha = 0.28f),
  heroSupportingText = MistBlue,
  dividerBorder = DividerBorder,
  statusNormal = StatusNormal,
  statusNormalContainer = StatusNormalContainer,
  statusNormalBorder = StatusNormalBorder,
  statusNormalText = StatusNormalText,
  statusAttention = StatusAttention,
  statusAttentionContainer = StatusAttentionContainer,
  statusAttentionBorder = StatusAttentionBorder,
  statusAttentionText = StatusAttentionText,
  statusAlert = StatusAlert,
  statusAlertContainer = StatusAlertContainer,
  statusAlertBorder = StatusAlertBorder,
  statusAlertText = StatusAlertText,
  statusNeutral = SecondaryText,
  statusNeutralContainer = AppBackground,
  statusNeutralBorder = DividerBorder,
  statusNeutralText = PrimaryText,
  statusInfoContainer = PaleBlue,
  statusInfoBorder = MistBlue,
  statusInfoAccent = RoyalBlue,
  statusInfoText = DeepNavy,
  disabledContainer = DisabledSurface,
  disabledContent = DisabledContent
)

private val DarkExtendedColors = NexzyExtendedColors(
  heroGradientStart = MidnightNavy,
  heroGradientMid = DeepNavy,
  heroGradientEnd = RoyalBlue,
  heroCardSurface = Color(0x26FFFFFF),
  heroCardBorder = MistBlue.copy(alpha = 0.30f),
  heroSupportingText = MistBlue,
  dividerBorder = DarkBorder,
  statusNormal = Color(0xFF4FB996),
  statusNormalContainer = Color(0xFF10352A),
  statusNormalBorder = Color(0xFF238566),
  statusNormalText = Color(0xFFD0F0E4),
  statusAttention = Color(0xFFE0A64B),
  statusAttentionContainer = Color(0xFF3A270B),
  statusAttentionBorder = Color(0xFFB7791F),
  statusAttentionText = Color(0xFFFAE4BF),
  statusAlert = Color(0xFFE27C7C),
  statusAlertContainer = Color(0xFF3D1616),
  statusAlertBorder = Color(0xFFB54747),
  statusAlertText = Color(0xFFFAD4D4),
  statusNeutral = MistBlue,
  statusNeutralContainer = DarkSurfaceVariant,
  statusNeutralBorder = DarkBorder,
  statusNeutralText = TextOnDark,
  statusInfoContainer = DarkSurfaceVariant,
  statusInfoBorder = SteelBlue,
  statusInfoAccent = MistBlue,
  statusInfoText = PaleBlue,
  disabledContainer = DarkSurfaceVariant,
  disabledContent = SteelBlue
)

private val LocalNexzyColors = staticCompositionLocalOf { LightExtendedColors }

object NexzyTheme {
  val colors: NexzyExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNexzyColors.current
}

private val NexzyLightColorScheme = lightColorScheme(
  primary = RoyalBlue,
  onPrimary = TextOnDark,
  primaryContainer = PaleBlue,
  onPrimaryContainer = DeepNavy,
  secondary = SteelBlue,
  onSecondary = TextOnDark,
  secondaryContainer = MistBlue,
  onSecondaryContainer = MidnightNavy,
  tertiary = DeepNavy,
  onTertiary = TextOnDark,
  tertiaryContainer = PaleBlue,
  onTertiaryContainer = PrimaryText,
  background = AppBackground,
  onBackground = PrimaryText,
  surface = CardBackground,
  onSurface = PrimaryText,
  surfaceVariant = PaleBlue,
  onSurfaceVariant = SecondaryText,
  outline = DividerBorder,
  outlineVariant = MistBlue,
  error = StatusAlert,
  onError = TextOnDark,
  errorContainer = StatusAlertContainer,
  onErrorContainer = StatusAlertText
)

private val NexzyDarkColorScheme = darkColorScheme(
  primary = MistBlue,
  onPrimary = MidnightNavy,
  primaryContainer = DarkSurfaceVariant,
  onPrimaryContainer = PaleBlue,
  secondary = SteelBlue,
  onSecondary = MidnightNavy,
  secondaryContainer = RoyalBlue,
  onSecondaryContainer = TextOnDark,
  tertiary = RoyalBlue,
  onTertiary = TextOnDark,
  tertiaryContainer = DeepNavy,
  onTertiaryContainer = MistBlue,
  background = MidnightNavy,
  onBackground = TextOnDark,
  surface = DeepNavy,
  onSurface = TextOnDark,
  surfaceVariant = DarkCardSurface,
  onSurfaceVariant = MistBlue,
  outline = DarkBorder,
  outlineVariant = SteelBlue,
  error = Color(0xFFE27C7C),
  onError = MidnightNavy,
  errorContainer = Color(0xFF3D1616),
  onErrorContainer = Color(0xFFFAD4D4)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Never override NexzyHealth brand palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) NexzyDarkColorScheme else NexzyLightColorScheme
  val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

  CompositionLocalProvider(LocalNexzyColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
