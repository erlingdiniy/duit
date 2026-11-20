package com.diniy.duit.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.diniy.duit.R

/**
 * Candy Pop typography (Phase 6C).
 * - Fredoka (rounded display) for headings, titles and button labels.
 * - Nunito for body text and ALL money: its digits share one advance width, so amounts stay
 *   aligned and animated counters never jitter. Fredoka digits are proportional, so it is
 *   never used for amounts. "tnum" is kept as a harmless hint for fallback fonts.
 * - `sp` throughout so Android font scaling up to 200% works.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val CandyDisplayFamily = FontFamily(
    variable(R.font.fredoka, 400),
    variable(R.font.fredoka, 500),
    variable(R.font.fredoka, 600),
    variable(R.font.fredoka, 700)
)

val CandyBodyFamily = FontFamily(
    variable(R.font.nunito, 400),
    variable(R.font.nunito, 500),
    variable(R.font.nunito, 600),
    variable(R.font.nunito, 700),
    variable(R.font.nunito, 800),
    variable(R.font.nunito, 900)
)

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
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum"
    ),
    largeBalance = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    cardBalance = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    ),
    screenHeading = TextStyle(
        fontFamily = CandyDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    sectionHeading = TextStyle(
        fontFamily = CandyDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    cardTitle = TextStyle(
        fontFamily = CandyDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    transactionDescription = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    transactionAmount = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum"
    ),
    supportingInfo = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    caption = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    buttonLabel = TextStyle(
        fontFamily = CandyDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    inputLabel = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

val LocalSpatialTypography = staticCompositionLocalOf { DefaultSpatialTypography }

/** Material3 Typography bridged to [DefaultSpatialTypography] so every screen shares one hierarchy. */
val DuitTypography = Typography(
    displayMedium = DefaultSpatialTypography.heroBalance,
    displaySmall = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = DefaultSpatialTypography.screenHeading,
    headlineSmall = DefaultSpatialTypography.cardBalance,
    titleLarge = DefaultSpatialTypography.sectionHeading,
    titleMedium = DefaultSpatialTypography.cardTitle,
    titleSmall = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = DefaultSpatialTypography.caption,
    labelLarge = DefaultSpatialTypography.buttonLabel,
    labelMedium = DefaultSpatialTypography.caption,
    labelSmall = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    )
)

/**
 * Public edition: fills the two Material slots [DuitTypography] leaves at the platform default, so
 * DatePicker headlines (headlineLarge) and the TimePicker clock (displayLarge) use the bundled fonts
 * instead of the phone maker's system font. Sizes are Material's own defaults, so layouts don't shift.
 */
val DuitPublicTypography = DuitTypography.copy(
    displayLarge = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = CandyDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    )
)
