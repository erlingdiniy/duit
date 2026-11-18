package com.diniy.duit.ui

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.SpatialBlurCapabilities
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.ui.theme.DarkSpatialColors
import com.diniy.duit.core.ui.theme.DefaultSpatialTypography
import com.diniy.duit.core.ui.theme.LightSpatialColors
import com.diniy.duit.core.ui.theme.PrismaticPalette
import com.diniy.duit.core.ui.theme.SpatialGeometryTokens
import com.diniy.duit.core.ui.theme.SpatialMotionTokens
import com.diniy.duit.core.ui.theme.SpatialSpacingTokens
import com.diniy.duit.core.ui.theme.toPrismaticGlassTokens
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.GlassIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class Phase6ASpatialDesignSystemTest {

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun brandPalette_matchesCandyPopSpecificationExactly() {
        // Phase 6C Candy Pop brand
        assertEquals(Color(0xFF7B3CFF), PrismaticPalette.Grape)
        assertEquals(Color(0xFFFF5C8A), PrismaticPalette.Bubblegum)
        assertEquals(Color(0xFFFFD84D), PrismaticPalette.Sunny)
        assertEquals(Color(0xFF5EE0C1), PrismaticPalette.Mint)
        assertEquals(Color(0xFF2A1650), PrismaticPalette.GrapeInk)
        assertEquals(Color(0xFFF3ECFF), PrismaticPalette.LightCanvas)
        // Legacy accent names persisted by wallets/goals still resolve to candy colours
        assertEquals(PrismaticPalette.Mint, PrismaticPalette.Aqua)
        assertEquals(PrismaticPalette.Bubblegum, PrismaticPalette.Peach)
        assertEquals(PrismaticPalette.Sunny, PrismaticPalette.Slate)
    }

    @Test
    fun candyPopActionAndMoneyColours_meetWcagAa() {
        for (c in listOf(LightSpatialColors, DarkSpatialColors)) {
            val mode = if (c.isDark) "dark" else "light"
            val onPrimary = contrastRatio(c.onPrimaryAction, c.primaryAction)
            assertTrue("$mode primary button/hero text ($onPrimary) must be >= 4.5:1", onPrimary >= 4.5)
            for ((name, fg) in listOf("income" to c.income, "expense" to c.expense, "savings" to c.savings, "warning" to c.warning)) {
                val onCanvas = contrastRatio(fg, c.appBackground)
                val onCard = contrastRatio(fg, c.glassSurface)
                assertTrue("$mode $name on canvas ($onCanvas) must be >= 4.5:1", onCanvas >= 4.5)
                assertTrue("$mode $name on card ($onCard) must be >= 4.5:1", onCard >= 4.5)
            }
            val onOvercommittedHero = contrastRatio(PrismaticPalette.GrapeInk, c.bubblegum)
            assertTrue("$mode ink on bubblegum hero ($onOvercommittedHero) must be >= 4.5:1", onOvercommittedHero >= 4.5)
            for (accent in listOf(c.aqua, c.lavender, c.skyBlue, c.peach, c.sunny, c.bubblegum)) {
                // worst case: accent text on its own 22% tint over the card
                val tint = androidx.compose.ui.graphics.lerp(c.glassSurface, accent, 0.22f)
                val r = contrastRatio(c.readableOn(accent), tint)
                assertTrue("$mode readable accent $accent on its tint ($r) must be >= 4.5:1", r >= 4.5)
            }
            val onSunny = contrastRatio(c.onSunny, c.sunny)
            assertTrue("$mode ink on sunny sticker ($onSunny) must be >= 4.5:1", onSunny >= 4.5)
        }
    }

    @Test
    fun spatialColorTokens_lightAndDarkAreIndependentlyTunedAndMeetContrastRequirements() {
        assertFalse(LightSpatialColors.isDark)
        assertTrue(DarkSpatialColors.isDark)

        // Ensure Dark Mode is independently tuned rather than a naive RGB inversion of Light Mode
        val invertedLightGlassBlue = 1f - LightSpatialColors.glassSurface.blue
        assertNotEquals(invertedLightGlassBlue, DarkSpatialColors.glassSurface.blue, 0.05f)
        val invertedLightPrimaryRed = 1f - LightSpatialColors.primaryAction.red
        assertNotEquals(invertedLightPrimaryRed, DarkSpatialColors.primaryAction.red, 0.05f)

        // WCAG AA (>= 4.5:1) contrast checks for primary & secondary text in Light Mode
        val lightPrimaryContrast = contrastRatio(LightSpatialColors.primaryText, LightSpatialColors.appBackground)
        val lightSecondaryContrast = contrastRatio(LightSpatialColors.secondaryText, LightSpatialColors.appBackground)
        val lightCardSecondaryContrast = contrastRatio(LightSpatialColors.secondaryText, LightSpatialColors.elevatedSurface)
        assertTrue("Light primary text contrast ($lightPrimaryContrast) must exceed 7:1", lightPrimaryContrast >= 7.0)
        assertTrue("Light secondary text contrast ($lightSecondaryContrast) must exceed 4.5:1", lightSecondaryContrast >= 4.5)
        assertTrue("Light card secondary contrast ($lightCardSecondaryContrast) must exceed 4.5:1", lightCardSecondaryContrast >= 4.5)

        // WCAG AA (>= 4.5:1) contrast checks for primary & secondary text in Dark Mode
        val darkPrimaryContrast = contrastRatio(DarkSpatialColors.primaryText, DarkSpatialColors.appBackground)
        val darkSecondaryContrast = contrastRatio(DarkSpatialColors.secondaryText, DarkSpatialColors.appBackground)
        val darkCardSecondaryContrast = contrastRatio(DarkSpatialColors.secondaryText, DarkSpatialColors.elevatedSurface)
        assertTrue("Dark primary text contrast ($darkPrimaryContrast) must exceed 7:1", darkPrimaryContrast >= 7.0)
        assertTrue("Dark secondary text contrast ($darkSecondaryContrast) must exceed 4.5:1", darkSecondaryContrast >= 4.5)
        assertTrue("Dark card secondary contrast ($darkCardSecondaryContrast) must exceed 4.5:1", darkCardSecondaryContrast >= 4.5)

        // Distinct semantic tokens for income, expense, savings, warning, error, success
        assertNotEquals(LightSpatialColors.income, LightSpatialColors.expense)
        assertNotEquals(LightSpatialColors.income, LightSpatialColors.savings)
        assertNotEquals(DarkSpatialColors.income, DarkSpatialColors.expense)
        assertNotEquals(DarkSpatialColors.warning, DarkSpatialColors.error)
    }

    @Test
    fun depthSetting_scalesShadowsAndKeepsCandySurfacesOpaque() {
        // Phase 6C: the persisted GlassIntensity setting now controls shadow depth.
        val subtle = LightSpatialColors.withGlassIntensity(GlassIntensity.SUBTLE)
        val balanced = LightSpatialColors.withGlassIntensity(GlassIntensity.BALANCED)
        val rich = LightSpatialColors.withGlassIntensity(GlassIntensity.RICH)

        assertTrue(subtle.glassShadowSpot.alpha < balanced.glassShadowSpot.alpha)
        assertTrue(rich.glassShadowSpot.alpha > balanced.glassShadowSpot.alpha)
        assertTrue(rich.glassShadowSpot.alpha in 0.03f..0.60f)
        for (t in listOf(subtle, balanced, rich)) {
            assertEquals(1f, t.glassSurface.alpha, 0.0001f)
            assertEquals(1f, t.glassSurfaceElevated.alpha, 0.0001f)
            assertEquals(1f, t.floatingNavSurface.alpha, 0.0001f)
        }

        // Bridge to PrismaticGlassTokens preserves dark mode and glass surface alphas
        val bridgedDark = DarkSpatialColors.withGlassIntensity(GlassIntensity.RICH).toPrismaticGlassTokens()
        assertTrue(bridgedDark.isDark)
        assertEquals(
            DarkSpatialColors.withGlassIntensity(GlassIntensity.RICH).glassSurface,
            bridgedDark.glassSurface
        )
    }

    @Test
    fun spatialTypographyTokens_enforceTabularNumeralsAndReadableScale() {
        val t = DefaultSpatialTypography
        assertEquals("tnum", t.heroBalance.fontFeatureSettings)
        assertEquals("tnum", t.largeBalance.fontFeatureSettings)
        assertEquals("tnum", t.cardBalance.fontFeatureSettings)
        assertEquals("tnum", t.transactionAmount.fontFeatureSettings)
        assertEquals("tnum", t.caption.fontFeatureSettings)
        assertEquals("tnum", t.buttonLabel.fontFeatureSettings)

        // Verify hierarchy & minimum legible font size (>= 12.sp on spatial tokens)
        assertTrue(t.heroBalance.fontSize.value > t.largeBalance.fontSize.value)
        assertTrue(t.largeBalance.fontSize.value > t.cardBalance.fontSize.value)
        assertTrue(t.screenHeading.fontSize.value > t.sectionHeading.fontSize.value)
        assertTrue(t.sectionHeading.fontSize.value > t.cardTitle.fontSize.value)
        assertTrue(t.caption.fontSize >= 12.sp)
    }

    @Test
    fun spatialSpacingAndGeometryTokens_guaranteeAccessibleTouchTargetsAndNavClearance() {
        val spacing = SpatialSpacingTokens()
        val geometry = SpatialGeometryTokens()

        assertTrue(geometry.minTouchTarget >= 48.dp)
        assertTrue(geometry.buttonHeight >= 48.dp)
        assertTrue(geometry.iconButtonSize >= 48.dp)
        assertTrue(spacing.floatingNavBottomClearance > geometry.floatingNavHeight)

        assertTrue(geometry.radiusXs < geometry.radiusSm)
        assertTrue(geometry.radiusSm < geometry.radiusMd)
        assertTrue(geometry.radiusMd < geometry.radiusLg)
        assertTrue(geometry.radiusLg < geometry.radiusXl)
        assertTrue(geometry.radiusXl < geometry.radiusFloatingNav)
    }

    @Test
    fun spatialMotionTokens_respectReduceMotionAndAnimationIntensity() {
        val motion = SpatialMotionTokens()

        assertEquals(0, motion.resolveDurationMs(baseDurationMs = 220, reduceMotion = true))
        val subtleMs = motion.resolveDurationMs(220, reduceMotion = false, intensity = AnimationIntensity.SUBTLE)
        val balancedMs = motion.resolveDurationMs(220, reduceMotion = false, intensity = AnimationIntensity.BALANCED)
        val expressiveMs = motion.resolveDurationMs(220, reduceMotion = false, intensity = AnimationIntensity.EXPRESSIVE)
        assertTrue(subtleMs < balancedMs)
        assertTrue(expressiveMs > balancedMs)

        // Press scale disabled when reduceMotion = true or enabled = false
        assertEquals(1f, motion.resolvePressScale(isPressed = true, enabled = true, reduceMotion = true), 0.0001f)
        assertEquals(1f, motion.resolvePressScale(isPressed = true, enabled = false, reduceMotion = false), 0.0001f)
        assertEquals(
            motion.pressedScaleCard,
            motion.resolvePressScale(isPressed = true, enabled = true, reduceMotion = false),
            0.0001f
        )
    }

    @Test
    fun privacyMaskAccessibility_neverExposesHiddenAmountsInTalkBackDescriptions() {
        val visibleSettings = PrivacySettings(hideSensitiveBalances = false, privacyModeEnabled = false)
        val hideBalancesOnly = PrivacySettings(hideSensitiveBalances = true, privacyModeEnabled = false)
        val fullPrivacyMode = PrivacySettings(hideSensitiveBalances = false, privacyModeEnabled = true)

        val visibleDesc = PrivacyMask.accessibleBalanceDescription("Safe to Spend", 183_800L, visibleSettings)
        assertTrue(visibleDesc.contains("Safe to Spend"))
        assertTrue(visibleDesc.contains("1,838.00"))

        val hiddenBalanceDesc = PrivacyMask.accessibleBalanceDescription("Safe to Spend", 183_800L, hideBalancesOnly)
        assertFalse(hiddenBalanceDesc.contains("1,838"))
        assertFalse(hiddenBalanceDesc.contains("1838"))
        assertTrue(hiddenBalanceDesc.contains("hidden for privacy"))

        val hiddenTxDesc = PrivacyMask.accessibleAmountDescription("Food & Dining", 2_850L, fullPrivacyMode)
        assertFalse(hiddenTxDesc.contains("28.50"))
        assertTrue(hiddenTxDesc.contains("hidden for privacy"))
    }

    @Test
    fun spatialBlurCapabilities_correctlyDistinguishesApi29FromApi31Plus() {
        assertFalse(SpatialBlurCapabilities.supportsHardwareRenderEffect(Build.VERSION_CODES.Q))
        assertFalse(SpatialBlurCapabilities.supportsHardwareRenderEffect(Build.VERSION_CODES.R))
        assertTrue(SpatialBlurCapabilities.supportsHardwareRenderEffect(Build.VERSION_CODES.S))
        assertTrue(SpatialBlurCapabilities.supportsHardwareRenderEffect(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }
}
