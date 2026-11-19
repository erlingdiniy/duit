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

val LocalPrismaticGlass = staticCompositionLocalOf { LightGlassTokens }

@Composable
fun DuitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    glassIntensity: GlassIntensity = GlassIntensity.BALANCED,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val baseSpatialColors = if (isDark) DarkSpatialColors else LightSpatialColors
    val spatialColors = baseSpatialColors.withGlassIntensity(glassIntensity)
    val glassTokens = spatialColors.toPrismaticGlassTokens()

    CompositionLocalProvider(
        LocalPrismaticGlass provides glassTokens,
        LocalSpatialColors provides spatialColors,
        LocalSpatialTypography provides DefaultSpatialTypography,
        LocalSpatialSpacing provides SpatialSpacingTokens(),
        LocalSpatialGeometry provides SpatialGeometryTokens(),
        LocalSpatialMotion provides SpatialMotionTokens()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuitTypography,
            shapes = DuitShapes,
            content = content
        )
    }
}
