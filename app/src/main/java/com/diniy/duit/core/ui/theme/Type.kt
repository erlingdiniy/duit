package com.diniy.duit.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Semantic typography tokens for Prismatic Spatial Glass (Phase 6A Section 6).
 * - Uses `sp` throughout so Android nonlinear font scaling (up to 200%) works cleanly.
 * - Uses OpenType tabular numerals (`tnum`) on financial balances, amounts, and counters
 *   so currency digits align vertically and never jitter during counter transitions.
 * - Avoids tiny sub-11sp text to preserve readability on translucent glass surfaces.
 */
@Immutable
data class SpatialTypographyTokens(
    val heroBalance: TextStyle,
    val largeBalance: TextStyle,
    val cardBalance: TextStyle,
    val screenHeading: TextStyle,
    val sectionHeading: TextStyle,
    val cardTitle: TextStyle,
    val transactionDescription: TextStyle,
    val transactionAmount: TextStyle,
    val supportingInfo: TextStyle,
    val caption: TextStyle,
    val buttonLabel: TextStyle,
    val inputLabel: TextStyle
)

val DefaultSpatialTypography = SpatialTypographyTokens(
    heroBalance = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.75).sp,
        fontFeatureSettings = "tnum"
    ),
    largeBalance = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.45).sp,
        fontFeatureSettings = "tnum"
    ),
    cardBalance = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.15).sp,
        fontFeatureSettings = "tnum"
    ),
    screenHeading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    sectionHeading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum"
    ),
    cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    transactionDescription = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    transactionAmount = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum"
    ),
    supportingInfo = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    buttonLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum"
    ),
    inputLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

val LocalSpatialTypography = staticCompositionLocalOf { DefaultSpatialTypography }

/**
 * Material3 Typography bridged to [DefaultSpatialTypography] so existing screens
 * and new Spatial Glass components share a unified visual hierarchy.
 */
val DuitTypography = Typography(
    displayMedium = DefaultSpatialTypography.heroBalance,
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = DefaultSpatialTypography.screenHeading,
    headlineSmall = DefaultSpatialTypography.cardBalance,
    titleLarge = DefaultSpatialTypography.sectionHeading,
    titleMedium = DefaultSpatialTypography.cardTitle,
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = DefaultSpatialTypography.caption,
    labelLarge = DefaultSpatialTypography.buttonLabel,
    labelMedium = DefaultSpatialTypography.caption,
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = "tnum"
    )
)
