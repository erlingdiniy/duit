package com.diniy.duit.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.feature.settings.AboutDuitScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HiddenOwnerGestureTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun aboutLogo_requiresSevenTapsToRevealOwnerEnrollment_andLeavesNoUiHintsForPublicUsers() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var triggeredCount = 0
        activity.setContent {
            DuitTheme {
                AboutDuitScreen(
                    onBack = {},
                    onOpenOwnerMode = { triggeredCount++ }
                )
            }
        }

        // Ordinary About screen never displays Developer Center or Owner hints
        compose.onAllNodesWithText("Developer Center", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Owner", substring = true, ignoreCase = true).assertCountEquals(0)

        val logo = compose.onNodeWithContentDescription("duit. logo")
        repeat(6) {
            logo.performClick()
            compose.waitForIdle()
            assertEquals("Must not trigger before 7 taps", 0, triggeredCount)
        }

        logo.performClick()
        compose.waitForIdle()
        assertEquals("7th tap must open Owner enrollment", 1, triggeredCount)
    }
}
