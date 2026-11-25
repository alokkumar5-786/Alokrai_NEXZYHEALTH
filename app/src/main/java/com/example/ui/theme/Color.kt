package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Approved NexzyHealth Primary Brand Palette
val MidnightNavy = Color(0xFF07152E)
val DeepNavy = Color(0xFF0B1C4A)
val RoyalBlue = Color(0xFF24499B)
val SteelBlue = Color(0xFF6F8FBF)

// Approved NexzyHealth Secondary Palette
val MistBlue = Color(0xFFBDD8EA)
val PaleBlue = Color(0xFFEAF2F9)

// Surfaces & Borders (Light Theme Defaults)
val AppBackground = Color(0xFFF5F8FC)
val CardBackground = Color(0xFFFFFFFF)
val DividerBorder = Color(0xFFDCE5EF)

// Text Hierarchy
val PrimaryText = Color(0xFF14233B)
val SecondaryText = Color(0xFF5C6B80)
val TextOnDark = Color(0xFFFFFFFF)

// Semantic Healthcare Status Colours (Distinct from blue brand identity)
val StatusNormal = Color(0xFF238566)
val StatusNormalContainer = Color(0xFFE9F5F0)
val StatusNormalBorder = Color(0xFFA7D7C5)
val StatusNormalText = Color(0xFF14533E)

val StatusAttention = Color(0xFFB7791F)
val StatusAttentionContainer = Color(0xFFFEF6E7)
val StatusAttentionBorder = Color(0xFFF3D19C)
val StatusAttentionText = Color(0xFF784B0F)

val StatusAlert = Color(0xFFB54747)
val StatusAlertContainer = Color(0xFFFDF0F0)
val StatusAlertBorder = Color(0xFFF0BABA)
val StatusAlertText = Color(0xFF7E2B2B)

// Neutral & Dark-Surface Supporting Tones derived from the brand palette
val DarkCardSurface = Color(0xFF102454)
val DarkSurfaceVariant = Color(0xFF162E69)
val DarkBorder = Color(0xFF2B4B8C)
val DisabledSurface = Color(0xFFDCE5EF)
val DisabledContent = Color(0xFF5C6B80)

// Backward-compatible legacy token aliases mapped to the approved NexzyHealth palette
// Keeps un-migrated screens compiling with consistent Navy / Royal Blue / Icy Blue styling.
val BrandTealPrimary = RoyalBlue
val BrandMintBackground = MistBlue
val BrandMintSurface = PaleBlue
val BrandNavyText = PrimaryText
val BrandNavyMuted = SecondaryText
val SurfaceCard = CardBackground
val SurfaceBorder = DividerBorder
val StatusAttentionAmber = StatusAttention
val StatusAttentionHoney = StatusAttentionContainer
val StatusNeutralBg = AppBackground
val StatusNeutralGrey = SecondaryText
val VoiceWaveformBg = PaleBlue

