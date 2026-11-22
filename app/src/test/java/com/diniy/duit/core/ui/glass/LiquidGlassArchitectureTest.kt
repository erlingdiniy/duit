package com.diniy.duit.core.ui.glass

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = maxOf(fg.luminance(), bg.luminance()).toDouble()
        val l2 = minOf(fg.luminance(), bg.luminance()).toDouble()
        return (l1 + 0.05) / (l2 + 0.05)
    }
}
