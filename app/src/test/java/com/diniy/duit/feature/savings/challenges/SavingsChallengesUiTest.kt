package com.diniy.duit.feature.savings.challenges

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.challenge.ChallengeCalculator
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.ThemeMode
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsChallengesUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val goal = SavingsGoal(1, "Japan Trip", 500_000, null, "", "star", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1, 1)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent { DuitTheme(themeMode = ThemeMode.LIGHT) { content() } }
    }

    @Test
    fun emptyState_withoutGoals_asksForAGoalFirst() {
        show { SavingsChallengesSection(ChallengesUiState(isLoading = false), isPreview = false, onCreate = {}, onOpen = {}, onRetry = {}) }
        rule.onNodeWithText("Create a savings goal first, then turn it into a fun challenge.").assertIsDisplayed()
    }

    @Test
    fun card_showsGardenProgressFromRealSavings() {
        val c = SavingsChallenge(1, 1, ChallengeType.DAILY, 1_000, 0, slotCount = 4)
        val item = ChallengeItem(c, goal, ChallengeCalculator.progress(c, emptyList()), emptySet(), setOf(GardenStage.SPROUT))
        show { SavingsChallengesSection(ChallengesUiState(isLoading = false, items = listOf(item), activeGoals = listOf(goal)), false, {}, {}, {}) }
        rule.onNodeWithText("Daily Challenge").assertIsDisplayed()
        rule.onNodeWithText("For Japan Trip").assertIsDisplayed()
        rule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun editor_showsValidationError() {
        var error: String? = null
        show {
            ChallengeEditor(existing = null, activeGoals = listOf(goal, goal.copy(id = 2, name = "Laptop")), onClose = {},
                onSave = { _, goalId, type, amount, rounds, every, _ -> ChallengeRules.error(goalId, amount, type, rounds, every).also { error = it } })
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        rule.onNodeWithText("Start Challenge").performClick()
        rule.onNodeWithText("Pick a savings goal for this challenge.").assertIsDisplayed()
    }
}
