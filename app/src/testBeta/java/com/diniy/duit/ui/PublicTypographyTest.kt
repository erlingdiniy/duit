package com.diniy.duit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.diniy.duit.core.ui.theme.CandyBodyFamily
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.DefaultSpatialTypography
import com.diniy.duit.core.ui.theme.DuitPublicTypography
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every text style the public app can reach must use a bundled font, never the phone maker's system font. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublicTypographyTest {

    private val bundled = setOf(CandyDisplayFamily, CandyBodyFamily)

    private fun Typography.slots(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall
    )

    @Test
    fun everyMaterialSlot_usesABundledFont_andScalableSp() {
        DuitPublicTypography.slots().forEach { (name, style) ->
            assertTrue("$name uses ${style.fontFamily}", style.fontFamily in bundled)
            assertTrue("$name size must be sp so font scaling works", style.fontSize.isSp)
        }
    }

    @Test
    fun everySpatialToken_usesABundledFont() {
        with(DefaultSpatialTypography) {
            listOf(heroBalance, largeBalance, cardBalance, screenHeading, sectionHeading, cardTitle,
                transactionDescription, transactionAmount, supportingInfo, caption, buttonLabel, inputLabel)
        }.forEach { assertTrue(it.toString(), it.fontFamily in bundled && it.fontSize.isSp) }
    }

    @Test
    fun publicTheme_providesTheBundledTypography() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var seen: Typography? = null
        activity.setContent { DuitTheme(themeMode = ThemeMode.LIGHT) { seen = MaterialTheme.typography } }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(DuitPublicTypography, seen)
    }
}
