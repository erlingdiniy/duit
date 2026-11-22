package com.diniy.duit.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.VisualTheme

@Immutable
data class PrismaticGlassTokens(
    val isDark: Boolean,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassSurfaceMuted: Color,
    val glassBorderHighlight: Color,
    val glassBorderSubtle: Color,
    val floatingNavContainer: Color,
    val floatingNavActivePill: Color,
    val sheetContainer: Color,
    val scrim: Color,
    val aquaAccent: Color,
    val aquaContainer: Color,
    val onAquaContainer: Color,
    val lavenderAccent: Color,
    val lavenderContainer: Color,
    val onLavenderContainer: Color,
    val skyBlueAccent: Color,
    val skyBlueContainer: Color,
    val onSkyBlueContainer: Color,
    val peachAccent: Color,
    val peachContainer: Color,
    val onPeachContainer: Color,
    val slateAccent: Color,
    val slateContainer: Color,
    val onSlateContainer: Color,
    val positiveText: Color,
    val cautionText: Color
)

internal fun SpatialColorTokens.toPrismaticGlassTokens(): PrismaticGlassTokens = PrismaticGlassTokens(
    isDark = isDark,
    glassSurface = glassSurface,
    glassSurfaceElevated = glassSurfaceElevated,
    glassSurfaceMuted = glassSurfaceSubtle,
    glassBorderHighlight = glassHighlight,
    glassBorderSubtle = glassBorder,
    floatingNavContainer = floatingNavSurface,
    floatingNavActivePill = floatingNavActivePill,
    sheetContainer = bottomSheetSurface,
    scrim = scrim,
    aquaAccent = aqua,
    aquaContainer = aqua.copy(alpha = 0.18f),
    onAquaContainer = onIncomeContainer,
    lavenderAccent = lavender,
    lavenderContainer = lavender.copy(alpha = if (isDark) 0.22f else 0.18f),
    onLavenderContainer = if (isDark) PrismaticPalette.Cream else PrismaticPalette.DeepTeal,
    skyBlueAccent = skyBlue,
    skyBlueContainer = skyBlue.copy(alpha = 0.20f),
    onSkyBlueContainer = onSavingsContainer,
    peachAccent = peach,
    peachContainer = peach.copy(alpha = if (isDark) 0.20f else 0.24f),
    onPeachContainer = onExpenseContainer,
    slateAccent = slate,
    slateContainer = slate.copy(alpha = if (isDark) 0.16f else 0.30f),
    onSlateContainer = onSavingsContainer,
    positiveText = income,
    cautionText = warning
)

private val LightGlassTokens = LightSpatialColors.toPrismaticGlassTokens()

private val LightColorScheme = lightColorScheme(
    primary = PrismaticPalette.LightPrimary,
    onPrimary = PrismaticPalette.LightOnPrimary,
    primaryContainer = PrismaticPalette.LightPrimaryContainer,
    onPrimaryContainer = PrismaticPalette.LightOnPrimaryContainer,
    secondary = PrismaticPalette.LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = PrismaticPalette.LightIncomeContainer,
    onSecondaryContainer = PrismaticPalette.LightOnIncomeContainer,
    tertiary = PrismaticPalette.LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = PrismaticPalette.LightSavingsContainer,
    onTertiaryContainer = PrismaticPalette.LightOnSavingsContainer,
    background = PrismaticPalette.LightCanvas,
    onBackground = PrismaticPalette.LightInk,
    surface = PrismaticPalette.LightCanvasElevated,
    onSurface = PrismaticPalette.LightInk,
    surfaceVariant = PrismaticPalette.LightSurfaceSubtle,
    onSurfaceVariant = PrismaticPalette.LightInkMuted,
    outline = PrismaticPalette.DeepTeal.copy(alpha = 0.24f),
    outlineVariant = PrismaticPalette.DeepTeal.copy(alpha = 0.12f),
    error = PrismaticPalette.LightError,
    onError = Color.White,
    errorContainer = PrismaticPalette.LightErrorContainer,
    onErrorContainer = PrismaticPalette.LightOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = PrismaticPalette.DarkPrimary,
    onPrimary = PrismaticPalette.DarkPrimaryInk,
    primaryContainer = PrismaticPalette.DarkPrimaryContainer,
    onPrimaryContainer = PrismaticPalette.DarkOnPrimaryContainer,
    secondary = PrismaticPalette.DarkIncome,
    onSecondary = PrismaticPalette.DarkSecondaryInk,
    secondaryContainer = PrismaticPalette.DarkIncomeContainer,
    onSecondaryContainer = PrismaticPalette.DarkOnIncomeContainer,
    tertiary = PrismaticPalette.DarkSavings,
    onTertiary = PrismaticPalette.DarkTertiaryInk,
    tertiaryContainer = PrismaticPalette.DarkSavingsContainer,
    onTertiaryContainer = PrismaticPalette.DarkOnSavingsContainer,
    background = PrismaticPalette.DarkCanvas,
    onBackground = PrismaticPalette.DarkInk,
    surface = PrismaticPalette.DarkCanvasElevated,
    onSurface = PrismaticPalette.DarkInk,
    surfaceVariant = PrismaticPalette.DarkSurfaceSubtle,
    onSurfaceVariant = PrismaticPalette.DarkInkMuted,
    outline = Color.White.copy(alpha = 0.24f),
    outlineVariant = Color.White.copy(alpha = 0.10f),
    error = PrismaticPalette.DarkError,
    onError = Color(0xFF3B080E),
    errorContainer = PrismaticPalette.DarkErrorContainer,
    onErrorContainer = PrismaticPalette.DarkOnErrorContainer
)

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = LiquidGlassPalette.LightAccentText,
    onPrimary = Color.White,
    primaryContainer = LiquidGlassPalette.LightAccentContainer,
    onPrimaryContainer = LiquidGlassPalette.LightOnAccentContainer,
    secondary = LiquidGlassPalette.LightIncome,
    onSecondary = Color.White,
    secondaryContainer = LiquidGlassPalette.LightIncomeContainer,
    onSecondaryContainer = LiquidGlassPalette.LightOnIncomeContainer,
    tertiary = LiquidGlassPalette.LightSavings,
    onTertiary = Color.White,
    tertiaryContainer = LiquidGlassPalette.LightSavingsContainer,
    onTertiaryContainer = LiquidGlassPalette.LightOnSavingsContainer,
    background = LiquidGlassPalette.LightCanvas,
    onBackground = LiquidGlassPalette.LightInk,
    surface = LiquidGlassPalette.LightCanvasElevated,
    onSurface = LiquidGlassPalette.LightInk,
    surfaceVariant = LiquidGlassPalette.LightSurfaceSubtle,
    onSurfaceVariant = LiquidGlassPalette.LightInkMuted,
    outline = Color(0x2E000000),
    outlineVariant = Color(0x14000000),
    error = LiquidGlassPalette.LightError,
    onError = Color.White,
    errorContainer = LiquidGlassPalette.LightErrorContainer,
    onErrorContainer = LiquidGlassPalette.LightOnErrorContainer
)

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = LiquidGlassPalette.DarkAccentText,
    onPrimary = Color(0xFF001B38),
    primaryContainer = LiquidGlassPalette.DarkAccentContainer,
    onPrimaryContainer = LiquidGlassPalette.DarkOnAccentContainer,
    secondary = LiquidGlassPalette.DarkIncome,
    onSecondary = Color(0xFF052E14),
    secondaryContainer = LiquidGlassPalette.DarkIncomeContainer,
    onSecondaryContainer = LiquidGlassPalette.DarkOnIncomeContainer,
    tertiary = LiquidGlassPalette.DarkSavings,
    onTertiary = Color(0xFF042636),
    tertiaryContainer = LiquidGlassPalette.DarkSavingsContainer,
    onTertiaryContainer = LiquidGlassPalette.DarkOnSavingsContainer,
    background = LiquidGlassPalette.DarkCanvas,
    onBackground = LiquidGlassPalette.DarkInk,
    surface = LiquidGlassPalette.DarkCanvasElevated,
    onSurface = LiquidGlassPalette.DarkInk,
    surfaceVariant = LiquidGlassPalette.DarkSurfaceSubtle,
    onSurfaceVariant = LiquidGlassPalette.DarkInkMuted,
    outline = Color(0x38FFFFFF),
    outlineVariant = Color(0x1AFFFFFF),
    error = LiquidGlassPalette.DarkError,
    onError = Color(0xFF3B080E),
    errorContainer = LiquidGlassPalette.DarkErrorContainer,
    onErrorContainer = LiquidGlassPalette.DarkOnErrorContainer
)

val LocalPrismaticGlass = staticCompositionLocalOf { LightGlassTokens }
val LocalVisualTheme = staticCompositionLocalOf { VisualTheme.CANDY_BUBBLE }
val LocalGlassIntensity = staticCompositionLocalOf { GlassIntensity.BALANCED }

@Composable
fun DuitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    visualTheme: VisualTheme = VisualTheme.CANDY_BUBBLE,
    glassIntensity: GlassIntensity = GlassIntensity.BALANCED,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when (visualTheme) {
        VisualTheme.CANDY_BUBBLE -> if (isDark) DarkColorScheme else LightColorScheme
        VisualTheme.LIQUID_GLASS -> if (isDark) LiquidGlassDarkColorScheme else LiquidGlassLightColorScheme
    }
    val baseSpatialColors = when (visualTheme) {
        VisualTheme.CANDY_BUBBLE -> if (isDark) DarkSpatialColors else LightSpatialColors
        VisualTheme.LIQUID_GLASS -> if (isDark) LiquidGlassDarkSpatialColors else LiquidGlassLightSpatialColors
    }
    val spatialColors = baseSpatialColors.withGlassIntensity(glassIntensity)
    val glassTokens = spatialColors.toPrismaticGlassTokens()

    CompositionLocalProvider(
        LocalPrismaticGlass provides glassTokens,
        LocalVisualTheme provides visualTheme,
        LocalGlassIntensity provides glassIntensity,
        LocalSpatialColors provides spatialColors,
        LocalSpatialTypography provides DefaultSpatialTypography,
        LocalSpatialSpacing provides SpatialSpacingTokens(),
        LocalSpatialGeometry provides SpatialGeometryTokens(),
        LocalSpatialMotion provides SpatialMotionTokens()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) DuitPublicTypography else DuitTypography,
            shapes = DuitShapes,
            content = content
        )
    }
}
