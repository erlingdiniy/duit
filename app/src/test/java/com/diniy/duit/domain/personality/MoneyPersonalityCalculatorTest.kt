package com.diniy.duit.domain.personality

import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyPersonalityCalculatorTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val oct = YearMonth.of(2026, 10)
    private val afterOct = LocalDate.of(2026, 11, 21).toEpochDay()
    private val food = Category(1, "Food & Dining", TransactionType.EXPENSE, "food", PrismaticAccentToken.PEACH)
    private var id = 1L
    private fun at(d: Int, h: Int = 12, m: Int = 10) = LocalDateTime.of(2026, m, d, h, 0).atZone(kl).toInstant().toEpochMilli()
    private fun tx(type: TransactionType, sen: Long, day: Int, cat: String = "Shopping", catId: Long? = null, ms: Long = at(day)) =
        FinanceTransaction(id++, 1, if (type == TransactionType.TRANSFER) 2 else null, type, sen, cat, "t", "", null, ms, ms, categoryId = catId)
    private fun move(type: SavingsMovementType, sen: Long, day: Int, goal: Long = 1, related: Long? = null) =
        SavingsMovement(id++, goal, 1, 1, related, type, sen, null, "", at(day))

    /** 12 expenses on 12 days, spread over [cats] evenly. */
    private fun spread(cats: List<String>, each: Long = 1_000) = (1..12).map { d -> tx(TransactionType.EXPENSE, each, d, cats[d % cats.size]) }

    private fun eval(t: List<FinanceTransaction>, m: List<SavingsMovement> = emptyList(), p: PlanningOutcome = PlanningOutcome(), today: Long = afterOct) =
        MoneyPersonalityCalculator.evaluate(oct, t, listOf(food), m, p, today, kl)

    @Test
    fun tooLittleData_neverGuesses() {
        val r = eval((1..5).map { tx(TransactionType.EXPENSE, 1_000, it) })
        assertTrue(r is PersonalityResult.NotEnoughData)
        assertEquals(2, (r as PersonalityResult.NotEnoughData).missing.size)
        // Enough spending but only 10 days into the current month.
        val early = eval(spread(listOf("A", "B")).take(10), today = LocalDate.of(2026, 10, 10).toEpochDay())
        assertTrue(early is PersonalityResult.NotEnoughData)
    }

    @Test
    fun happyFoodie_fromCategoryIconOrName() {
        val t = (1..12).map { d -> if (d <= 5) tx(TransactionType.EXPENSE, 2_000, d, "Makan", catId = 1) else tx(TransactionType.EXPENSE, 500, d, "Bills") }
        val r = eval(t) as PersonalityResult.Assigned
        assertEquals(MoneyPersonality.HAPPY_FOODIE, r.personality)
        assertTrue(r.reasons.none { it.contains("RM") })
    }

    @Test
    fun littleExplorer_manyCategoriesNoneDominant() {
        val r = eval(spread(listOf("A", "B", "C", "D", "E", "F"))) as PersonalityResult.Assigned
        assertEquals(MoneyPersonality.LITTLE_EXPLORER, r.personality)
        assertEquals(6, r.habits.distinctCategories)
    }

    @Test
    fun savingsSuperstar_usesRealGoalDeposits_reassignAddsNothing_withdrawalsSubtract() {
        val t = spread(listOf("A", "B")) + tx(TransactionType.INCOME, 100_000, 1)
        val moves = listOf(
            move(SavingsMovementType.ALLOCATE, 30_000, 3),
            move(SavingsMovementType.WITHDRAW_TO_WALLET, 5_000, 4),
            move(SavingsMovementType.REASSIGN, 20_000, 5, goal = 1, related = 2)
        )
        val r = eval(t, moves) as PersonalityResult.Assigned
        assertEquals(MoneyPersonality.SAVINGS_SUPERSTAR, r.personality)
        assertTrue(r.reasons.single().contains("25%"))
        // Only 15% saved: no Superstar.
        val low = eval(t, listOf(move(SavingsMovementType.ALLOCATE, 15_000, 3)))
        assertFalse(low is PersonalityResult.Assigned && low.personality == MoneyPersonality.SAVINGS_SUPERSTAR)
    }

    @Test
    fun carefulPlanner_needsBudgetsAndBills() {
        val t = spread(listOf("A", "B"))
        assertEquals(MoneyPersonality.CAREFUL_PLANNER,
            (eval(t, p = PlanningOutcome(budgetsTotal = 5, budgetsWithinLimit = 5, billsDue = 4, billsPaid = 4)) as PersonalityResult.Assigned).personality)
        assertTrue(eval(t, p = PlanningOutcome(budgetsTotal = 5, budgetsWithinLimit = 2)) is PersonalityResult.NoClearPattern)
    }

    @Test
    fun transfersIgnored_andOtherMonthsExcluded_noClearPatternIsNeutral() {
        val t = spread(listOf("A", "B")) +
            (1..20).map { tx(TransactionType.TRANSFER, 50_000, (it % 28) + 1, "Food") } +
            tx(TransactionType.EXPENSE, 99_000, 1, "Makan", ms = at(30, 23, 9)) // 30 Sep, KL time
        val r = eval(t)
        assertTrue(r is PersonalityResult.NoClearPattern)
        assertEquals(12, r.habits.expenseCount)
        assertEquals(2, r.habits.distinctCategories)
    }

    @Test
    fun ties_resolveByFixedPriority_deterministically() {
        // Savings rate exactly 20% (strength 1.0) and Explorer with exactly 6 categories (strength 1.0).
        val t = spread(listOf("A", "B", "C", "D", "E", "F")) + tx(TransactionType.INCOME, 100_000, 1)
        val m = listOf(move(SavingsMovementType.ALLOCATE, 20_000, 2))
        repeat(3) { assertEquals(MoneyPersonality.SAVINGS_SUPERSTAR, (eval(t, m) as PersonalityResult.Assigned).personality) }
    }

    @Test
    fun internalSavingsMovements_duplicateIds_andRefundIncome_noDoubleCounting() {
        val dupExpense = tx(TransactionType.EXPENSE, 1_000, 1, "A")
        val salary = tx(TransactionType.INCOME, 100_000, 1, "Salary")
        val refund = tx(TransactionType.INCOME, 50_000, 10, "Refund")
        val base = spread(listOf("A", "B")) + dupExpense + dupExpense + salary
        val deposit = move(SavingsMovementType.TRANSFER_AND_ALLOCATE, 20_000, 2)
        val moves = listOf(
            deposit,
            deposit, // duplicate id must not count twice
            move(SavingsMovementType.LINK_PROTECTED, 15_000, 3),
            move(SavingsMovementType.TRANSFER_TO_SPENDING, 5_000, 4),
            move(SavingsMovementType.REASSIGN, 40_000, 5, goal = 1, related = 2)
        )
        // Net saved = 20,000 + 15,000 - 5,000 = 30,000 (30% of 100,000 salary)
        val r = eval(base, moves) as PersonalityResult.Assigned
        assertEquals(MoneyPersonality.SAVINGS_SUPERSTAR, r.personality)
        assertEquals(13, r.habits.expenseCount) // 12 spread + 1 deduplicated dupExpense
        assertTrue(r.reasons.single().contains("30%"))
        // Refund logged as income raises denominator to 150,000 -> 20% still passes cleanly, no crash or double count
        val withRefund = eval(base + refund, moves) as PersonalityResult.Assigned
        assertEquals(MoneyPersonality.SAVINGS_SUPERSTAR, withRefund.personality)
        assertTrue(withRefund.reasons.single().contains("20%"))
    }
}
