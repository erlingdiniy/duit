package com.diniy.duit.feature.savings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsGoalFormTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun fullScreenForm_candyControls_andValidationUnchanged() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val saved = mutableListOf<SavingsGoal>()
            var dismissed = 0
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            controller.get().setContent {
                DuitTheme(themeMode = mode) {
                    SavingsGoalEditorSheet(existingGoal = null, onSave = { goal, _ -> saved += goal }, onDismiss = { dismissed++ })
                }
            }
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            compose.onNodeWithText("New Savings Goal").assertExists()
            compose.onNodeWithContentDescription("Close").assertExists()

            // Validation still blocks an empty goal.
            compose.onNodeWithText("Create Goal").performScrollTo()
            compose.onNodeWithText("Create Goal").performSemanticsAction(SemanticsActions.OnClick)
            assertTrue(saved.isEmpty())

            // Candy colour swatch is a selectable radio with a spoken name.
            compose.onNodeWithText("Accent colour").performScrollTo()
            compose.onNodeWithContentDescription("Peach colour").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithContentDescription("Peach colour").assertIsSelected()

            // A valid goal saves once.
            compose.onNodeWithText("Goal name").performScrollTo()
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("Japan Trip")
            compose.onAllNodes(hasSetTextAction())[1].performTextInput("5000")
            compose.onNodeWithText("Create Goal").performScrollTo()
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            compose.onNodeWithText("Create Goal").performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(listOf("Japan Trip"), saved.map { it.name })
            assertEquals(500_000L, saved.single().targetAmountSen)
            controller.pause().stop().destroy()
        }
    }
}
