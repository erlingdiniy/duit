package com.diniy.duit.core.ui.glass

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.theme.DarkSpatialColors
import com.diniy.duit.core.ui.theme.LightSpatialColors
import com.diniy.duit.core.ui.theme.LiquidGlassDarkSpatialColors
import com.diniy.duit.core.ui.theme.LiquidGlassLightSpatialColors
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.VisualTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassArchitectureTest {

    @Test
    fun resolveTier_mapsAndroidApiLevelsCorrectly() {
        assertEquals(
            LiquidGlassTier.FullAgsl,
            LiquidGlassCapabilities.resolveTier(sdkInt = Build.VERSION_CODES.TIRAMISU)
        )
        assertEquals(
            LiquidGlassTier.FullAgsl,
            LiquidGlassCapabilities.resolveTier(sdkInt = 35)
        )
        assertEquals(
            LiquidGlassTier.FrostedRenderEffect,
            LiquidGlassCapabilities.resolveTier(sdkInt = Build.VERSION_CODES.S)
        )
        assertEquals(
            LiquidGlassTier.FrostedRenderEffect,
            LiquidGlassCapabilities.resolveTier(sdkInt = Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            LiquidGlassTier.OpticalFallback,
            LiquidGlassCapabilities.resolveTier(sdkInt = Build.VERSION_CODES.R)
        )
        assertEquals(
            LiquidGlassTier.OpticalFallback,
            LiquidGlassCapabilities.resolveTier(sdkInt = Build.VERSION_CODES.Q)
        )
    }

    @Test
    fun resolveTier_lowRamDropsToOpticalFallbackInAutoMode() {
        assertEquals(
            LiquidGlassTier.OpticalFallback,
            LiquidGlassCapabilities.resolveTier(
                sdkInt = 35,
                isLowRamDevice = true,
                qualityMode = LiquidGlassQualityMode.AUTO
            )
        )
    }

    @Test
    fun resolveTier_manualOverridesRespectHardwareCeiling() {
        assertEquals(
            LiquidGlassTier.FrostedRenderEffect,
            LiquidGlassCapabilities.resolveTier(
                sdkInt = 35,
                qualityMode = LiquidGlassQualityMode.FROSTED
            )
        )
        assertEquals(
            LiquidGlassTier.OpticalFallback,
            LiquidGlassCapabilities.resolveTier(
                sdkInt = Build.VERSION_CODES.Q,
                qualityMode = LiquidGlassQualityMode.FULL
            )
        )
    }

    @Test
    fun scrollGovernor_attenuatesRefractionDuringScrollAndReduceMotion() {
        val resting = LiquidGlassCapabilities.resolveRefractionFactor(
            tier = LiquidGlassTier.FullAgsl,
            isScrolling = false,
            reduceMotion = false
        )
        val scrolling = LiquidGlassCapabilities.resolveRefractionFactor(
            tier = LiquidGlassTier.FullAgsl,
            isScrolling = true,
            reduceMotion = false
        )
        val reduced = LiquidGlassCapabilities.resolveRefractionFactor(
            tier = LiquidGlassTier.FullAgsl,
            isScrolling = false,
            reduceMotion = true
        )
        val fallback = LiquidGlassCapabilities.resolveRefractionFactor(
            tier = LiquidGlassTier.OpticalFallback,
            isScrolling = false,
            reduceMotion = false
        )
        assertEquals(1f, resting, 0.001f)
        assertTrue(scrolling in 0.25f..0.85f && scrolling < resting)
        assertTrue(reduced < resting)
        assertEquals(0f, fallback, 0.001f)
    }

    @Test
    fun glassIntensityPreference_mapsToReversibleQualityModes() {
        assertEquals(
            LiquidGlassQualityMode.FALLBACK,
            LiquidGlassCapabilities.qualityModeFor(GlassIntensity.SUBTLE)
        )
        assertEquals(
            LiquidGlassQualityMode.AUTO,
            LiquidGlassCapabilities.qualityModeFor(GlassIntensity.BALANCED)
        )
        assertEquals(
            LiquidGlassQualityMode.FULL,
            LiquidGlassCapabilities.qualityModeFor(GlassIntensity.RICH)
        )
    }

    @Test
    fun visualTheme_defaultsToCandyBubbleAndHasDistinctLiquidGlassPalette() {
        assertEquals(VisualTheme.CANDY_BUBBLE, AppSettingsSnapshot().visualTheme)
        assertNotEquals(LightSpatialColors.appBackground, LiquidGlassLightSpatialColors.appBackground)
        assertNotEquals(LightSpatialColors.primaryAction, LiquidGlassLightSpatialColors.primaryAction)
        assertNotEquals(DarkSpatialColors.appBackground, LiquidGlassDarkSpatialColors.appBackground)
        assertNotEquals(DarkSpatialColors.primaryAction, LiquidGlassDarkSpatialColors.primaryAction)
    }

    @Test
    fun liquidGlassThemePresets_maintainWcagAaTextContrastInLightAndDark() {
        val lightNav = LiquidGlassPresets.floatingNavBar(LiquidGlassLightSpatialColors)
        val darkNav = LiquidGlassPresets.floatingNavBar(LiquidGlassDarkSpatialColors)

        val lightNavBg = lightNav.tintColor.copy(alpha = lightNav.tintAlpha)
            .compositeOver(LiquidGlassLightSpatialColors.appBackground)
        val darkNavBg = darkNav.tintColor.copy(alpha = darkNav.tintAlpha)
            .compositeOver(LiquidGlassDarkSpatialColors.appBackground)

        assertTrue(
            "Light Liquid Glass nav active contrast >= 4.5",
            contrastRatio(LiquidGlassLightSpatialColors.primaryAction, lightNavBg) >= 4.5
        )
        assertTrue(
            "Dark Liquid Glass nav active contrast >= 4.5",
            contrastRatio(LiquidGlassDarkSpatialColors.primaryAction, darkNavBg) >= 4.5
        )

        val lightFab = LiquidGlassPresets.quickAddFab(LiquidGlassLightSpatialColors)
        val darkFab = LiquidGlassPresets.quickAddFab(LiquidGlassDarkSpatialColors)
        val lightFabBg = lightFab.tintColor.copy(alpha = lightFab.tintAlpha)
            .compositeOver(LiquidGlassLightSpatialColors.appBackground)
        val darkFabBg = darkFab.tintColor.copy(alpha = darkFab.tintAlpha)
            .compositeOver(LiquidGlassDarkSpatialColors.appBackground)

        assertTrue(
            "Light Liquid Glass FAB + contrast >= 4.5",
            contrastRatio(LiquidGlassLightSpatialColors.primaryAction, lightFabBg) >= 4.5
        )
        assertTrue(
            "Dark Liquid Glass FAB + contrast >= 4.5",
            contrastRatio(LiquidGlassDarkSpatialColors.primaryAction, darkFabBg) >= 4.5
        )

        assertTrue(
            "Light Liquid Glass body contrast >= 4.5",
            contrastRatio(LiquidGlassLightSpatialColors.primaryText, LiquidGlassLightSpatialColors.glassSurface) >= 4.5
        )
        assertTrue(
            "Dark Liquid Glass body contrast >= 4.5",
            contrastRatio(LiquidGlassDarkSpatialColors.primaryText, LiquidGlassDarkSpatialColors.glassSurface) >= 4.5
        )
    }

    @Test
    fun glassIntensityLevels_produceDistinctLowMidHighOpticsAndMaintainAaContrast() {
        val palettes = listOf(LiquidGlassLightSpatialColors, LiquidGlassDarkSpatialColors)
        palettes.forEach { palette ->
            val lowBar = LiquidGlassPresets.floatingNavBar(palette, GlassIntensity.SUBTLE)
            val lowLens = LiquidGlassPresets.floatingNavLens(palette, GlassIntensity.SUBTLE)
            val lowFab = LiquidGlassPresets.quickAddFab(palette, GlassIntensity.SUBTLE)
            listOf(lowBar, lowLens, lowFab).forEach { spec ->
                assertEquals(1.0f, spec.tintAlpha, 0.001f)
                assertEquals(0.dp, spec.blurRadius)
                assertEquals(0.dp, spec.bezel)
                assertEquals(0f, spec.dispersion, 0.001f)
                assertEquals(0f, spec.rimAlpha, 0.001f)
            }

            val midBar = LiquidGlassPresets.floatingNavBar(palette, GlassIntensity.BALANCED)
            val midLens = LiquidGlassPresets.floatingNavLens(palette, GlassIntensity.BALANCED)
            val midFab = LiquidGlassPresets.quickAddFab(palette, GlassIntensity.BALANCED)
            listOf(midBar, midLens, midFab).forEach { spec ->
                assertTrue(spec.blurRadius > 0.dp)
                assertTrue(spec.bezel > 0.dp)
                assertEquals(0f, spec.dispersion, 0.001f)
                assertTrue(spec.rimAlpha > 0f)
            }

            val highBar = LiquidGlassPresets.floatingNavBar(palette, GlassIntensity.RICH)
            val highLens = LiquidGlassPresets.floatingNavLens(palette, GlassIntensity.RICH)
            val highFab = LiquidGlassPresets.quickAddFab(palette, GlassIntensity.RICH)
            assertTrue(highBar.thickness > midBar.thickness)
            assertEquals(LiquidGlassKind.Lens, highLens.kind)
            assertTrue(highLens.dispersion in 0.35f..0.80f)
            assertEquals(0.dp, highLens.blurRadius)
            assertEquals(LiquidGlassKind.Lens, highFab.kind)
            assertTrue(highFab.dispersion in 0.30f..0.75f)

            // WCAG AA >= 4.5:1 across all 3 intensities in Light & Dark
            listOf(GlassIntensity.SUBTLE, GlassIntensity.BALANCED, GlassIntensity.RICH).forEach { level ->
                val navBg = LiquidGlassPresets.floatingNavBar(palette, level).let {
                    it.tintColor.copy(alpha = it.tintAlpha).compositeOver(palette.appBackground)
                }
                val fabBg = LiquidGlassPresets.quickAddFab(palette, level).let {
                    it.tintColor.copy(alpha = it.tintAlpha).compositeOver(palette.appBackground)
                }
                assertTrue(
                    "Nav active contrast >= 4.5 for $level (isDark=${palette.isDark})",
                    contrastRatio(palette.primaryAction, navBg) >= 4.5
                )
                assertTrue(
                    "FAB + contrast >= 4.5 for $level (isDark=${palette.isDark})",
                    contrastRatio(palette.primaryAction, fabBg) >= 4.5
                )
            }
            assertTrue(
                "LOW active pill contrast >= 4.5 (isDark=${palette.isDark})",
                contrastRatio(palette.primaryAction, lowLens.tintColor) >= 4.5
            )
            assertTrue(
                "LOW idle tab contrast >= 4.5 (isDark=${palette.isDark})",
                contrastRatio(palette.secondaryText, lowBar.tintColor) >= 4.5
            )
        }
    }

    @Test
    fun homePanelAndOrbPresets_scaleAcrossLowMidHighAndPreserveAaContrast() {
        val palettes = listOf(LiquidGlassLightSpatialColors, LiquidGlassDarkSpatialColors)
        palettes.forEach { palette ->
            val lowCard = LiquidGlassPanelPresets.card(palette, GlassIntensity.SUBTLE)
            val midCard = LiquidGlassPanelPresets.card(palette, GlassIntensity.BALANCED)
            val highCard = LiquidGlassPanelPresets.card(palette, GlassIntensity.RICH)

            assertEquals(1f, lowCard.fillColor.alpha, 0.001f)
            assertEquals(0f, lowCard.topSheenAlpha, 0.001f)
            assertEquals(0f, lowCard.chromaticGlintAlpha, 0.001f)
            assertEquals(false, lowCard.usesLiquidSpring)

            assertTrue(midCard.topSheenAlpha > 0f)
            assertEquals(0f, midCard.chromaticGlintAlpha, 0.001f)
            assertTrue(midCard.rimHighlightAlpha > 0f)

            assertTrue(highCard.topSheenAlpha > midCard.topSheenAlpha)
            assertEquals(0f, highCard.chromaticGlintAlpha, 0.001f)
            assertTrue(highCard.rimHighlightAlpha > midCard.rimHighlightAlpha)
            assertTrue(highCard.usesLiquidSpring)

            val lowOrb = LiquidGlassPanelPresets.orbButton(palette, GlassIntensity.SUBTLE)
            val midOrb = LiquidGlassPanelPresets.orbButton(palette, GlassIntensity.BALANCED)
            val highOrb = LiquidGlassPanelPresets.orbButton(palette, GlassIntensity.RICH)
            assertEquals(1f, lowOrb.fillColor.alpha, 0.001f)
            assertEquals(0f, lowOrb.chromaticGlintAlpha, 0.001f)
            assertEquals(0f, midOrb.chromaticGlintAlpha, 0.001f)
            assertEquals(0f, highOrb.chromaticGlintAlpha, 0.001f)
            assertTrue(highOrb.rimHighlightAlpha > midOrb.rimHighlightAlpha)

            val heroLens = LiquidGlassPanelPresets.heroMedallionLens(palette)
            assertEquals(LiquidGlassKind.Lens, heroLens.kind)
            assertTrue(heroLens.dispersion >= 1.0f)

            // WCAG AA >= 4.5:1 on composited card background across all 3 intensities
            listOf(lowCard, midCard, highCard).forEach { cardSpec ->
                val bg = cardSpec.fillColor.compositeOver(palette.appBackground)
                assertTrue(
                    "Card primaryText contrast >= 4.5 (isDark=${palette.isDark})",
                    contrastRatio(palette.primaryText, bg) >= 4.5
                )
                assertTrue(
                    "Card secondaryText contrast >= 4.5 (isDark=${palette.isDark})",
                    contrastRatio(palette.secondaryText, bg) >= 4.5
                )
            }
        }
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = maxOf(fg.luminance(), bg.luminance()).toDouble()
        val l2 = minOf(fg.luminance(), bg.luminance()).toDouble()
        return (l1 + 0.05) / (l2 + 0.05)
    }
}
