package com.diniy.duit.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.GlassIntensity

/**
 * Centralized semantic colour tokens for Prismatic Spatial Glass (Phase 6A Section 4).
 * Independently tuned for Light Mode and Dark Mode rather than naively inverted.
 */
@Immutable
data class SpatialColorTokens(
    val isDark: Boolean,
    // Brand palette
    val aqua: Color = PrismaticPalette.Aqua,
    val lavender: Color = PrismaticPalette.Lavender,
    val skyBlue: Color = PrismaticPalette.SkyBlue,
    val peach: Color = PrismaticPalette.Peach,
    val slate: Color = PrismaticPalette.Slate,
    // Surfaces & Glass
    val appBackground: Color,
    val baseSurface: Color,
    val elevatedSurface: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassSurfaceSubtle: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassInnerGlow: Color,
    val glassShadowAmbient: Color,
    val glassShadowSpot: Color,
    val floatingNavSurface: Color,
    val floatingNavActivePill: Color,
    val bottomSheetSurface: Color,
    val scrim: Color,
    // Typography / Ink
    val primaryText: Color,
    val secondaryText: Color,
    val disabledText: Color,
    // Actions & Financial semantics
    val primaryAction: Color,
    val onPrimaryAction: Color,
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val savings: Color,
    val savingsContainer: Color,
    val onSavingsContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    // Structure & interactive states
    val divider: Color,
    val pressedStateOverlay: Color,
    val selectedStateOverlay: Color
) {
    fun withGlassIntensity(intensity: GlassIntensity): SpatialColorTokens {
        if (intensity == GlassIntensity.BALANCED) return this
        return copy(
            glassSurface = glassSurface.copy(
                alpha = (glassSurface.alpha * intensity.alphaScale).coerceIn(0.60f, 0.96f)
            ),
            glassSurfaceElevated = glassSurfaceElevated.copy(
                alpha = (glassSurfaceElevated.alpha * intensity.alphaScale).coerceIn(0.72f, 0.98f)
            ),
            glassSurfaceSubtle = glassSurfaceSubtle.copy(
                alpha = (glassSurfaceSubtle.alpha * intensity.alphaScale).coerceIn(0.54f, 0.92f)
            ),
            floatingNavSurface = floatingNavSurface.copy(
                alpha = (floatingNavSurface.alpha * intensity.alphaScale).coerceIn(0.76f, 0.98f)
            )
        )
    }
}

val LightSpatialColors = SpatialColorTokens(
    isDark = false,
    appBackground = PrismaticPalette.LightCanvas,
    baseSurface = PrismaticPalette.LightSurfaceSubtle,
    elevatedSurface = PrismaticPalette.LightCanvasElevated,
    glassSurface = PrismaticPalette.LightGlassBase.copy(alpha = 0.80f),
    glassSurfaceElevated = PrismaticPalette.LightGlassBase.copy(alpha = 0.91f),
    glassSurfaceSubtle = PrismaticPalette.LightGlassSubtle.copy(alpha = 0.75f),
    glassBorder = PrismaticPalette.Slate.copy(alpha = 0.12f),
    glassHighlight = Color.White.copy(alpha = 0.96f),
    glassInnerGlow = Color.White.copy(alpha = 0.55f),
    glassShadowAmbient = PrismaticPalette.Slate.copy(alpha = 0.11f),
    glassShadowSpot = PrismaticPalette.Slate.copy(alpha = 0.09f),
    floatingNavSurface = Color(0xFFFAFBFD).copy(alpha = 0.97f),
    floatingNavActivePill = PrismaticPalette.Lavender.copy(alpha = 0.18f),
    bottomSheetSurface = PrismaticPalette.LightSheetSurface,
    scrim = PrismaticPalette.Slate.copy(alpha = 0.30f),
    primaryText = PrismaticPalette.LightInk,
    secondaryText = PrismaticPalette.LightInkMuted,
    disabledText = PrismaticPalette.LightInkDisabled,
    primaryAction = PrismaticPalette.LightPrimary,
    onPrimaryAction = PrismaticPalette.LightOnPrimary,
    income = PrismaticPalette.LightIncome,
    incomeContainer = PrismaticPalette.LightIncomeContainer,
    onIncomeContainer = PrismaticPalette.LightOnIncomeContainer,
    expense = PrismaticPalette.LightExpense,
    expenseContainer = PrismaticPalette.LightExpenseContainer,
    onExpenseContainer = PrismaticPalette.LightOnExpenseContainer,
    savings = PrismaticPalette.LightSavings,
    savingsContainer = PrismaticPalette.LightSavingsContainer,
    onSavingsContainer = PrismaticPalette.LightOnSavingsContainer,
    warning = PrismaticPalette.LightWarning,
    warningContainer = PrismaticPalette.LightWarningContainer,
    onWarningContainer = PrismaticPalette.LightOnWarningContainer,
    error = PrismaticPalette.LightError,
    errorContainer = PrismaticPalette.LightErrorContainer,
    onErrorContainer = PrismaticPalette.LightOnErrorContainer,
    success = PrismaticPalette.LightSuccess,
    successContainer = PrismaticPalette.LightSuccessContainer,
    onSuccessContainer = PrismaticPalette.LightOnSuccessContainer,
    divider = PrismaticPalette.Slate.copy(alpha = 0.10f),
    pressedStateOverlay = PrismaticPalette.Slate.copy(alpha = 0.07f),
    selectedStateOverlay = PrismaticPalette.Lavender.copy(alpha = 0.16f)
)

val DarkSpatialColors = SpatialColorTokens(
    isDark = true,
    appBackground = PrismaticPalette.DarkCanvas,
    baseSurface = PrismaticPalette.DarkSurfaceSubtle,
    elevatedSurface = PrismaticPalette.DarkCanvasElevated,
    glassSurface = PrismaticPalette.DarkGlassBase.copy(alpha = 0.78f),
    glassSurfaceElevated = PrismaticPalette.DarkGlassElevated.copy(alpha = 0.88f),
    glassSurfaceSubtle = PrismaticPalette.DarkGlassSubtle.copy(alpha = 0.72f),
    glassBorder = Color.White.copy(alpha = 0.09f),
    glassHighlight = Color.White.copy(alpha = 0.22f),
    glassInnerGlow = Color.White.copy(alpha = 0.07f),
    glassShadowAmbient = Color.Black.copy(alpha = 0.42f),
    glassShadowSpot = Color.Black.copy(alpha = 0.34f),
    floatingNavSurface = Color(0xFF162235).copy(alpha = 0.97f),
    floatingNavActivePill = PrismaticPalette.Lavender.copy(alpha = 0.24f),
    bottomSheetSurface = PrismaticPalette.DarkSheetSurface,
    scrim = Color.Black.copy(alpha = 0.58f),
    primaryText = PrismaticPalette.DarkInk,
    secondaryText = PrismaticPalette.DarkInkMuted,
    disabledText = PrismaticPalette.DarkInkDisabled,
    primaryAction = PrismaticPalette.Lavender,
    onPrimaryAction = PrismaticPalette.DarkPrimaryInk,
    income = PrismaticPalette.DarkIncome,
    incomeContainer = PrismaticPalette.DarkIncomeContainer,
    onIncomeContainer = PrismaticPalette.DarkOnIncomeContainer,
    expense = PrismaticPalette.DarkExpense,
    expenseContainer = PrismaticPalette.DarkExpenseContainer,
    onExpenseContainer = PrismaticPalette.DarkOnExpenseContainer,
    savings = PrismaticPalette.DarkSavings,
    savingsContainer = PrismaticPalette.DarkSavingsContainer,
    onSavingsContainer = PrismaticPalette.DarkOnSavingsContainer,
    warning = PrismaticPalette.DarkWarning,
    warningContainer = PrismaticPalette.DarkWarningContainer,
    onWarningContainer = PrismaticPalette.DarkOnWarningContainer,
    error = PrismaticPalette.DarkError,
    errorContainer = PrismaticPalette.DarkErrorContainer,
    onErrorContainer = PrismaticPalette.DarkOnErrorContainer,
    success = PrismaticPalette.DarkSuccess,
    successContainer = PrismaticPalette.DarkSuccessContainer,
    onSuccessContainer = PrismaticPalette.DarkOnSuccessContainer,
    divider = Color.White.copy(alpha = 0.09f),
    pressedStateOverlay = Color.White.copy(alpha = 0.08f),
    selectedStateOverlay = PrismaticPalette.Lavender.copy(alpha = 0.22f)
)

/**
 * Centralized spacing & content density tokens (Phase 6A Section 7).
 */
@Immutable
data class SpatialSpacingTokens(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screenMarginCompact: Dp = 16.dp,
    val screenMarginExpanded: Dp = 24.dp,
    val screenTopPadding: Dp = 12.dp,
    val screenBottomPadding: Dp = 20.dp,
    val floatingNavBottomClearance: Dp = 96.dp,
    val sectionSpacing: Dp = 16.dp,
    val cardSpacing: Dp = 12.dp,
    val bentoGap: Dp = 12.dp,
    val compactRowVerticalPadding: Dp = 12.dp,
    val compactRowHorizontalPadding: Dp = 16.dp,
    val cardPaddingCompact: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    val cardPaddingStandard: PaddingValues = PaddingValues(18.dp),
    val cardPaddingHero: PaddingValues = PaddingValues(20.dp)
)

/**
 * Centralized geometry, corner radii, component heights, touch targets,
 * elevation, and border thickness tokens (Phase 6A Section 7).
 */
@Immutable
data class SpatialGeometryTokens(
    val radiusXs: Dp = 10.dp,
    val radiusSm: Dp = 14.dp,
    val radiusMd: Dp = 18.dp,
    val radiusLg: Dp = 24.dp,
    val radiusXl: Dp = 28.dp,
    val radiusFloatingNav: Dp = 32.dp,
    val radiusPill: Dp = 999.dp,
    val minTouchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 48.dp,
    val buttonHeightCompact: Dp = 40.dp,
    val iconButtonSize: Dp = 48.dp,
    val pillHeight: Dp = 34.dp,
    val inputFieldHeight: Dp = 54.dp,
    val floatingNavHeight: Dp = 68.dp,
    val maxFloatingNavWidth: Dp = 468.dp,
    val elevationNone: Dp = 0.dp,
    val elevationSubtle: Dp = 2.dp,
    val elevationCardLight: Dp = 4.dp,
    val elevationCardDark: Dp = 2.dp,
    val elevationElevatedLight: Dp = 9.dp,
    val elevationElevatedDark: Dp = 6.dp,
    val elevationFloatingLight: Dp = 14.dp,
    val elevationFloatingDark: Dp = 10.dp,
    val borderHairline: Dp = 0.75.dp,
    val borderStandard: Dp = 1.dp,
    val borderFocused: Dp = 1.5.dp
)

/**
 * Centralized motion token system for durations, springs, and scale parameters (Phase 6A Section 9).
 * Designed to prepare Phase 6C fluid spatial transitions while strictly honoring Reduce Motion.
 */
@Immutable
data class SpatialMotionTokens(
    val durationInstantMs: Int = 0,
    val durationFastMs: Int = 140,
    val durationStandardMs: Int = 220,
    val durationEmphasizedMs: Int = 320,
    val durationCounterMs: Int = 420,
    val pressDampingRatio: Float = 0.78f,
    val pressStiffness: Float = Spring.StiffnessMedium,
    val spatialDampingRatio: Float = 0.82f,
    val spatialStiffness: Float = Spring.StiffnessMediumLow,
    val morphDampingRatio: Float = 0.85f,
    val morphStiffness: Float = Spring.StiffnessLow,
    val pressedScaleCard: Float = 0.985f,
    val pressedScaleButton: Float = 0.97f,
    val selectedScaleIcon: Float = 1.06f
) {
    fun resolveDurationMs(
        baseDurationMs: Int = durationStandardMs,
        reduceMotion: Boolean = false,
        intensity: AnimationIntensity = AnimationIntensity.BALANCED
    ): Int {
        if (reduceMotion || baseDurationMs <= 0) return 0
        return (baseDurationMs * intensity.durationScale).toInt().coerceIn(70, 600)
    }

    fun resolvePressScale(
        isPressed: Boolean,
        enabled: Boolean = true,
        reduceMotion: Boolean = false,
        targetScale: Float = pressedScaleCard
    ): Float {
        if (!isPressed || !enabled || reduceMotion) return 1f
        return targetScale
    }
}

val LocalSpatialColors = staticCompositionLocalOf { LightSpatialColors }
val LocalSpatialSpacing = staticCompositionLocalOf { SpatialSpacingTokens() }
val LocalSpatialGeometry = staticCompositionLocalOf { SpatialGeometryTokens() }
val LocalSpatialMotion = staticCompositionLocalOf { SpatialMotionTokens() }

/**
 * Unified accessor for the Prismatic Spatial Glass design system.
 */
object SpatialTheme {
    val colors: SpatialColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpatialColors.current

    val typography: SpatialTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpatialTypography.current

    val spacing: SpatialSpacingTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpatialSpacing.current

    val geometry: SpatialGeometryTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpatialGeometry.current

    val motion: SpatialMotionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpatialMotion.current
}
