package com.diniy.duit.feature.personality

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.personality.MoneyPersonality
import com.diniy.duit.domain.personality.PersonalityResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyPersonalityViewModelTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 11, 21)
    private var id = 1L
    private val all = MutableStateFlow<List<FinanceTransaction>>(emptyList())
    private val others = MutableStateFlow(PersonalityInputs(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    private val preview = MutableStateFlow(false)
    private val ranges = mutableListOf<Pair<Long, Long>>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(kl).toInstant().toEpochMilli()
    private fun expense(m: Int, d: Int, cat: String, sen: Long = 1_000) =
        FinanceTransaction(id++, 1, null, TransactionType.EXPENSE, sen, cat, "t", "", null, ms(m, d), ms(m, d))
    /** 12 expenses on 12 days of month [m] across [cats]. */
    private fun month(m: Int, cats: List<String>) = (1..12).map { expense(m, it, cats[it % cats.size]) }

    private fun TestScope.vm(between: (Long, Long) -> Flow<List<FinanceTransaction>> = { s, e ->
        ranges += s to e; all.map { l -> l.filter { it.occurredAtEpochMs in s until e } }
    }) = MoneyPersonalityViewModel(between, others, preview, kl) { today }
        .also { m -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { m.uiState.collect {} } }

    @Test
    fun queriesTwelveMonths_andDerivesStickersFromPastMonths() = runTest {
        all.value = month(9, listOf("A", "B", "C", "D", "E", "F")) + month(10, listOf("A", "B", "C", "D", "E", "F")) + month(11, listOf("A", "B"))
        val m = vm()
        val (start, end) = ranges.single()
        assertEquals(LocalDate.of(2025, 12, 1).atStartOfDay(kl).toInstant().toEpochMilli(), start)
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(kl).toInstant().toEpochMilli(), end)
        assertTrue(m.uiState.value.result is PersonalityResult.NoClearPattern) // November: only 2 categories
        assertEquals(mapOf(MoneyPersonality.LITTLE_EXPLORER to 2), m.uiState.value.stickers)
        m.previousMonth()
        assertEquals(MoneyPersonality.LITTLE_EXPLORER, (m.uiState.value.result as PersonalityResult.Assigned).personality)
        assertTrue(m.uiState.value.canGoForward)
        m.nextMonth(); m.nextMonth() // cannot go past the current month
        assertEquals(YearMonth.of(2026, 11), m.uiState.value.month)
    }

    @Test
    fun carefulPlanner_reusesBudgetAndBillCalculations() = runTest {
        all.value = month(11, listOf("A", "B"))
        val budget = Budget(1, "All", BudgetScope.OVERALL, BudgetPeriodType.MONTHLY, "2026-11", null, null, "", 50_000,
            PrismaticAccentToken.AQUA, "", false, 0, 0)
        val bill = Commitment(1, "Rent", 100_000, LocalDate.of(2026, 11, 5).toEpochDay(), CommitmentStatus.PAID, 1, false, null)
        others.value = others.value.copy(budgets = listOf(budget), commitments = listOf(bill))
        val m = vm()
        assertEquals(MoneyPersonality.CAREFUL_PLANNER, (m.uiState.value.result as PersonalityResult.Assigned).personality)
        // Overspent budget (12,000 spent of 5,000) -> no Careful Planner.
        others.value = others.value.copy(budgets = listOf(budget.copy(limitSen = 5_000)))
        assertTrue(m.uiState.value.result is PersonalityResult.NoClearPattern)
    }

    @Test
    fun notEnoughData_samplePreview_andErrors() = runTest {
        all.value = (1..4).map { expense(11, it, "A") }
        val m = vm()
        assertTrue(m.uiState.value.result is PersonalityResult.NotEnoughData)
        preview.value = true
        assertNull(m.uiState.value.result)
        assertTrue(m.uiState.value.stickers.isEmpty())
        val failing = vm { _, _ -> flow { throw IllegalStateException("db") } }
        assertTrue(failing.uiState.value.errorMessage != null)
    }

    @Test
    fun screen_showsResult_withoutMoneyAmounts() = runTest {
        all.value = month(11, listOf("A", "B", "C", "D", "E", "F"))
        val m = vm()
        val state = m.uiState.value
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            DuitTheme(themeMode = ThemeMode.LIGHT) { MoneyPersonalityScreen(state, {}, {}, {}, {}) }
        }
        rule.onNodeWithText("Little Explorer").assertIsDisplayed()
        rule.onNodeWithText("Share My Card").assertExists()
        val texts = (state.result as PersonalityResult.Assigned).reasons + MoneyPersonality.LITTLE_EXPLORER.tagline
        assertFalse(texts.any { it.contains("RM") })
    }
}
