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
    val selectedStateOverlay: Color,
    // Candy Pop extras
    val sticker: Color = PrismaticPalette.LightStickerInk,
    val sunny: Color = PrismaticPalette.Sunny,
    val onSunny: Color = PrismaticPalette.DeepTeal,
    /** Bright coral fill (hero, quick action). Text on it must be [onCoral]. */
    val coral: Color = PrismaticPalette.Coral,
    val onCoral: Color = PrismaticPalette.DeepTeal
) {
    /**
     * Candy accents (mint, sky, bubblegum…) are too light to be text. This mixes an accent
     * toward the primary ink so it keeps its hue but stays readable (>= 4.5:1, see tests).
     */
    fun readableOn(accent: Color): Color = androidx.compose.ui.graphics.lerp(accent, primaryText, 0.6f)

    /**
     * Phase 6C: surfaces are opaque candy, so the persisted "glass intensity" setting now
     * controls shadow depth (Subtle = flatter, Rich = more pop). The DataStore key is unchanged.
     */
    fun withGlassIntensity(intensity: GlassIntensity): SpatialColorTokens {
        if (intensity == GlassIntensity.BALANCED) return this
        return copy(
            glassShadowAmbient = glassShadowAmbient.copy(
                alpha = (glassShadowAmbient.alpha * intensity.alphaScale).coerceIn(0.03f, 0.60f)
            ),
            glassShadowSpot = glassShadowSpot.copy(
                alpha = (glassShadowSpot.alpha * intensity.alphaScale).coerceIn(0.03f, 0.60f)
            )
        )
    }
}

val LightSpatialColors = SpatialColorTokens(
    isDark = false,
    appBackground = PrismaticPalette.LightCanvas,
    baseSurface = PrismaticPalette.LightSurfaceSubtle,
    elevatedSurface = PrismaticPalette.LightCanvasElevated,
    glassSurface = PrismaticPalette.LightGlassBase,
    glassSurfaceElevated = PrismaticPalette.LightGlassBase,
    glassSurfaceSubtle = PrismaticPalette.LightGlassSubtle,
    glassBorder = PrismaticPalette.DeepTeal.copy(alpha = 0.10f),
    glassHighlight = Color.Transparent,
    glassInnerGlow = Color.Transparent,
    glassShadowAmbient = PrismaticPalette.DeepTeal.copy(alpha = 0.12f),
    glassShadowSpot = PrismaticPalette.DeepTeal.copy(alpha = 0.14f),
    floatingNavSurface = Color.White,
    floatingNavActivePill = PrismaticPalette.LightSurfaceSubtle,
    bottomSheetSurface = PrismaticPalette.LightSheetSurface,
    scrim = PrismaticPalette.DeepTeal.copy(alpha = 0.34f),
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
    divider = PrismaticPalette.DeepTeal.copy(alpha = 0.10f),
    pressedStateOverlay = PrismaticPalette.DeepTeal.copy(alpha = 0.08f),
    selectedStateOverlay = PrismaticPalette.Coral.copy(alpha = 0.16f)
)

val DarkSpatialColors = SpatialColorTokens(
    isDark = true,
    appBackground = PrismaticPalette.DarkCanvas,
    baseSurface = PrismaticPalette.DarkSurfaceSubtle,
    elevatedSurface = PrismaticPalette.DarkCanvasElevated,
    glassSurface = PrismaticPalette.DarkGlassBase,
    glassSurfaceElevated = PrismaticPalette.DarkGlassElevated,
    glassSurfaceSubtle = PrismaticPalette.DarkGlassSubtle,
    glassBorder = Color.White.copy(alpha = 0.08f),
    glassHighlight = Color.Transparent,
    glassInnerGlow = Color.Transparent,
    glassShadowAmbient = Color.Black.copy(alpha = 0.40f),
    glassShadowSpot = Color.Black.copy(alpha = 0.36f),
    floatingNavSurface = PrismaticPalette.DarkSurfaceSubtle,
    floatingNavActivePill = PrismaticPalette.DarkPrimaryContainer,
    bottomSheetSurface = PrismaticPalette.DarkSheetSurface,
    scrim = Color.Black.copy(alpha = 0.58f),
    primaryText = PrismaticPalette.DarkInk,
    secondaryText = PrismaticPalette.DarkInkMuted,
    disabledText = PrismaticPalette.DarkInkDisabled,
    primaryAction = PrismaticPalette.DarkPrimary,
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
    selectedStateOverlay = PrismaticPalette.Coral.copy(alpha = 0.22f),
    sticker = PrismaticPalette.DarkStickerInk,
    sunny = PrismaticPalette.DarkSavings,
    onSunny = PrismaticPalette.DarkTertiaryInk,
    coral = PrismaticPalette.Coral,
    onCoral = PrismaticPalette.DarkPrimaryInk
)

/**
 * Distinct Liquid Glass semantic colour tokens (Light) — modeled on `zakisheriff/Liquid-Glass`.
 */
val LiquidGlassLightSpatialColors = SpatialColorTokens(
    isDark = false,
    aqua = Color(0xFF34C759),
    lavender = LiquidGlassPalette.LightAccentFill,
    skyBlue = Color(0xFF32ADE6),
    peach = Color(0xFFFF6B6B),
    slate = Color(0xFF5E7CE2),
    appBackground = LiquidGlassPalette.LightCanvas,
    baseSurface = LiquidGlassPalette.LightSurfaceSubtle,
    elevatedSurface = LiquidGlassPalette.LightCanvasElevated,
    glassSurface = LiquidGlassPalette.LightGlassBase,
    glassSurfaceElevated = LiquidGlassPalette.LightGlassBase,
    glassSurfaceSubtle = LiquidGlassPalette.LightGlassSubtle,
    glassBorder = Color(0x16000000),
    glassHighlight = Color(0xF0FFFFFF),
    glassInnerGlow = Color(0x8CFFFFFF),
    glassShadowAmbient = Color(0x14000000),
    glassShadowSpot = Color(0x12000000),
    floatingNavSurface = Color.White,
    floatingNavActivePill = LiquidGlassPalette.LightSurfaceSubtle,
    bottomSheetSurface = LiquidGlassPalette.LightSheetSurface,
    scrim = Color(0x2E000000),
    primaryText = LiquidGlassPalette.LightInk,
    secondaryText = LiquidGlassPalette.LightInkMuted,
    disabledText = LiquidGlassPalette.LightInkDisabled,
    primaryAction = LiquidGlassPalette.LightAccentText,
    onPrimaryAction = Color.White,
    income = LiquidGlassPalette.LightIncome,
    incomeContainer = LiquidGlassPalette.LightIncomeContainer,
    onIncomeContainer = LiquidGlassPalette.LightOnIncomeContainer,
    expense = LiquidGlassPalette.LightExpense,
    expenseContainer = LiquidGlassPalette.LightExpenseContainer,
    onExpenseContainer = LiquidGlassPalette.LightOnExpenseContainer,
    savings = LiquidGlassPalette.LightSavings,
    savingsContainer = LiquidGlassPalette.LightSavingsContainer,
    onSavingsContainer = LiquidGlassPalette.LightOnSavingsContainer,
    warning = LiquidGlassPalette.LightWarning,
    warningContainer = LiquidGlassPalette.LightWarningContainer,
    onWarningContainer = LiquidGlassPalette.LightOnWarningContainer,
    error = LiquidGlassPalette.LightError,
    errorContainer = LiquidGlassPalette.LightErrorContainer,
    onErrorContainer = LiquidGlassPalette.LightOnErrorContainer,
    success = LiquidGlassPalette.LightIncome,
    successContainer = LiquidGlassPalette.LightIncomeContainer,
    onSuccessContainer = LiquidGlassPalette.LightOnIncomeContainer,
    divider = Color(0x14000000),
    pressedStateOverlay = Color(0x0D000000),
    selectedStateOverlay = LiquidGlassPalette.LightAccentFill.copy(alpha = 0.14f),
    sticker = Color(0x22000000),
    sunny = Color(0xFF007AFF),
    onSunny = Color.White,
    coral = LiquidGlassPalette.LightAccentFill,
    onCoral = Color.White
)

/**
 * Distinct Liquid Glass semantic colour tokens (Dark) — modeled on `zakisheriff/Liquid-Glass`.
 */
val LiquidGlassDarkSpatialColors = SpatialColorTokens(
    isDark = true,
    aqua = Color(0xFF30D158),
    lavender = LiquidGlassPalette.DarkAccentFill,
    skyBlue = Color(0xFF64D2FF),
    peach = Color(0xFFFF6961),
    slate = Color(0xFF7D9BFF),
    appBackground = LiquidGlassPalette.DarkCanvas,
    baseSurface = LiquidGlassPalette.DarkSurfaceSubtle,
    elevatedSurface = LiquidGlassPalette.DarkCanvasElevated,
    glassSurface = LiquidGlassPalette.DarkGlassBase,
    glassSurfaceElevated = LiquidGlassPalette.DarkGlassElevated,
    glassSurfaceSubtle = LiquidGlassPalette.DarkGlassSubtle,
    glassBorder = Color(0x1FFFFFFF),
    glassHighlight = Color(0x9EFFFFFF),
    glassInnerGlow = Color(0x29FFFFFF),
    glassShadowAmbient = Color(0x61000000),
    glassShadowSpot = Color(0x4D000000),
    floatingNavSurface = LiquidGlassPalette.DarkGlassElevated,
    floatingNavActivePill = LiquidGlassPalette.DarkAccentContainer,
    bottomSheetSurface = LiquidGlassPalette.DarkSheetSurface,
    scrim = Color(0x66000000),
    primaryText = LiquidGlassPalette.DarkInk,
    secondaryText = LiquidGlassPalette.DarkInkMuted,
    disabledText = LiquidGlassPalette.DarkInkDisabled,
    primaryAction = LiquidGlassPalette.DarkAccentText,
    onPrimaryAction = Color(0xFF001B38),
    income = LiquidGlassPalette.DarkIncome,
    incomeContainer = LiquidGlassPalette.DarkIncomeContainer,
    onIncomeContainer = LiquidGlassPalette.DarkOnIncomeContainer,
    expense = LiquidGlassPalette.DarkExpense,
    expenseContainer = LiquidGlassPalette.DarkExpenseContainer,
    onExpenseContainer = LiquidGlassPalette.DarkOnExpenseContainer,
    savings = LiquidGlassPalette.DarkSavings,
    savingsContainer = LiquidGlassPalette.DarkSavingsContainer,
    onSavingsContainer = LiquidGlassPalette.DarkOnSavingsContainer,
    warning = LiquidGlassPalette.DarkWarning,
    warningContainer = LiquidGlassPalette.DarkWarningContainer,
    onWarningContainer = LiquidGlassPalette.DarkOnWarningContainer,
    error = LiquidGlassPalette.DarkError,
    errorContainer = LiquidGlassPalette.DarkErrorContainer,
    onErrorContainer = LiquidGlassPalette.DarkOnErrorContainer,
    success = LiquidGlassPalette.DarkIncome,
    successContainer = LiquidGlassPalette.DarkIncomeContainer,
    onSuccessContainer = LiquidGlassPalette.DarkOnIncomeContainer,
    divider = Color(0x1AFFFFFF),
    pressedStateOverlay = Color(0x14FFFFFF),
    selectedStateOverlay = LiquidGlassPalette.DarkAccentFill.copy(alpha = 0.22f),
    sticker = Color(0x29FFFFFF),
    sunny = LiquidGlassPalette.DarkAccentFill,
    onSunny = Color.White,
    coral = LiquidGlassPalette.DarkAccentFill,
    onCoral = Color.White
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
    val radiusXs: Dp = 12.dp,
    val radiusSm: Dp = 16.dp,
    val radiusMd: Dp = 22.dp,
    val radiusLg: Dp = 26.dp,
    val radiusXl: Dp = 32.dp,
    val radiusFloatingNav: Dp = 36.dp,
    val stickerBorder: Dp = 2.5.dp,
    val stickerOffset: Dp = 4.dp,
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
    val durationFastMs: Int = 150, // quick interaction
    val durationStandardMs: Int = 250, // standard transition
    val durationEmphasizedMs: Int = 320,
    val durationCounterMs: Int = 420,
    // Candy Pop: bouncy springs with visible overshoot (squash and stretch on press/release)
    val pressDampingRatio: Float = 0.7f, // controlled return, no wobble
    val pressStiffness: Float = Spring.StiffnessMedium,
    val spatialDampingRatio: Float = 0.8f, // gentle spring for selection
    val spatialStiffness: Float = Spring.StiffnessMediumLow,
    val morphDampingRatio: Float = 0.82f,
    val morphStiffness: Float = Spring.StiffnessLow,
    val pressedScaleCard: Float = 0.97f,
    val pressedScaleButton: Float = 0.96f,
    val selectedScaleIcon: Float = 1.08f,
    // Squash & stretch targets for press (prep for the Phase 6C animation pass)
    val squashScaleX: Float = 1.04f,
    val squashScaleY: Float = 0.94f
) {
    /** Press squash for sticker buttons; returns (scaleX, scaleY). Identity under Reduce Motion. */
    fun resolveSquash(pressFraction: Float, reduceMotion: Boolean): Pair<Float, Float> {
        if (reduceMotion) return 1f to 1f
        val f = pressFraction.coerceIn(0f, 1f)
        return (1f + (squashScaleX - 1f) * f) to (1f + (squashScaleY - 1f) * f)
    }

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
