package com.diniy.duit.domain.personality

import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Positive, original personalities only. Listed in tie-break priority order. */
enum class MoneyPersonality(val title: String, val tagline: String) {
    SAVINGS_SUPERSTAR("Savings Superstar", "You kept putting money aside for your goals."),
    CAREFUL_PLANNER("Careful Planner", "You stayed on top of your budgets and bills."),
    LITTLE_EXPLORER("Little Explorer", "You spread your spending across lots of things."),
    HAPPY_FOODIE("Happy Foodie", "Good food was a big part of your month.")
}

/** Budget and bill outcomes for the month, worked out by the existing calculators. */
data class PlanningOutcome(val budgetsTotal: Int = 0, val budgetsWithinLimit: Int = 0, val billsDue: Int = 0, val billsPaid: Int = 0)

/** Habit facts shown with any result. Counts and percentages only: never money amounts. */
data class MonthlyHabits(
    val expenseCount: Int,
    val activeDays: Int,
    val noSpendDays: Int,
    val distinctCategories: Int,
    val topCategory: String?,
    val topCategorySharePercent: Int
)

sealed interface PersonalityResult {
    val month: YearMonth
    val habits: MonthlyHabits

    /** Too little data: nothing is guessed. */
    data class NotEnoughData(override val month: YearMonth, override val habits: MonthlyHabits, val missing: List<String>) : PersonalityResult

    /** Enough data, but no personality's rule clearly fits: shown neutrally, never as a label. */
    data class NoClearPattern(override val month: YearMonth, override val habits: MonthlyHabits) : PersonalityResult

    data class Assigned(
        override val month: YearMonth,
        override val habits: MonthlyHabits,
        val personality: MoneyPersonality,
        /** Plain-language reasons from real data, without money amounts. */
        val reasons: List<String>
    ) : PersonalityResult
}

/**
 * Deterministic, offline Money Personality for one month (no AI, no network). Uses recorded INCOME and
 * EXPENSE only; transfers are ignored, and savings come from goal movements, so nothing counts twice.
 */
object MoneyPersonalityCalculator {
    const val MIN_EXPENSES = 10
    const val MIN_ACTIVE_DAYS = 7
    const val MIN_ELAPSED_DAYS = 14
    const val SAVINGS_RATE_PERCENT = 20
    const val FOODIE_SHARE_PERCENT = 30
    const val EXPLORER_MIN_CATEGORIES = 6
    const val EXPLORER_MAX_SHARE_PERCENT = 35
    const val PLANNER_BUDGET_PERCENT = 80
    const val PLANNER_BILL_PERCENT = 90

    private val FOOD_ICON_KEYS = setOf("food")
    private val FOOD_WORDS = listOf("food", "dining", "makan", "restaurant", "cafe", "meal")

    fun evaluate(
        month: YearMonth,
        transactions: List<FinanceTransaction>,
        categories: List<Category>,
        savingsMovements: List<SavingsMovement>,
        planning: PlanningOutcome = PlanningOutcome(),
        todayEpochDay: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): PersonalityResult {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

        val inMonth = transactions.distinctBy { it.id }.filter { dayOf(it.occurredAtEpochMs) in first..last }
        val expenses = inMonth.filter { it.type == TransactionType.EXPENSE && it.amountSen > 0L }
        val incomeSen = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountSen.coerceAtLeast(0L) }
        val expenseSen = expenses.sumOf { it.amountSen }

        val elapsedDays = (minOf(todayEpochDay, last) - first + 1).coerceAtLeast(0L).toInt()
        val activeDays = expenses.map { dayOf(it.occurredAtEpochMs) }.toSet()
        val byCategory = expenses.groupBy { it.category.ifBlank { "Other" } }.mapValues { (_, l) -> l.sumOf { it.amountSen } }
        val top = byCategory.maxWithOrNull(compareBy<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        fun share(sen: Long) = if (expenseSen > 0L) ((sen * 100L) / expenseSen).toInt() else 0
        val habits = MonthlyHabits(
            expenseCount = expenses.size,
            activeDays = activeDays.size,
            noSpendDays = (elapsedDays - activeDays.size).coerceAtLeast(0),
            distinctCategories = byCategory.size,
            topCategory = top?.key,
            topCategorySharePercent = top?.let { share(it.value) } ?: 0
        )

        val missing = buildList {
            if (expenses.size < MIN_EXPENSES) add("at least $MIN_EXPENSES expenses (you have ${expenses.size})")
            if (activeDays.size < MIN_ACTIVE_DAYS) add("spending on at least $MIN_ACTIVE_DAYS different days (you have ${activeDays.size})")
            if (todayEpochDay < last && elapsedDays < MIN_ELAPSED_DAYS) add("at least $MIN_ELAPSED_DAYS days of the month")
        }
        if (missing.isNotEmpty()) return PersonalityResult.NotEnoughData(month, habits, missing)

        // Each rule gives a strength >= 1.0 only when it is clearly met; the strongest wins.
        val strengths = mutableMapOf<MoneyPersonality, Double>()
        val reasons = mutableMapOf<MoneyPersonality, List<String>>()

        // Net new money into all goals this month. Moving money between goals (REASSIGN) adds nothing.
        val savedSen = savingsMovements
            .distinctBy { it.id }
            .filter { dayOf(it.occurredAtEpochMs) in first..last }
            .sumOf { m ->
                when (m.type) {
                    SavingsMovementType.ALLOCATE, SavingsMovementType.TRANSFER_AND_ALLOCATE, SavingsMovementType.LINK_PROTECTED -> m.amountSen.coerceAtLeast(0L)
                    SavingsMovementType.WITHDRAW_TO_WALLET, SavingsMovementType.TRANSFER_TO_SPENDING -> -m.amountSen.coerceAtLeast(0L)
                    SavingsMovementType.REASSIGN -> 0L
                }
            }
            .coerceAtLeast(0L)
        if (incomeSen > 0L && savedSen > 0L) {
            val rate = ((savedSen * 100L) / incomeSen).toInt()
            if (rate >= SAVINGS_RATE_PERCENT) {
                strengths[MoneyPersonality.SAVINGS_SUPERSTAR] = rate / SAVINGS_RATE_PERCENT.toDouble()
                reasons[MoneyPersonality.SAVINGS_SUPERSTAR] = listOf("You moved about $rate% of this month's income into your savings goals.")
            }
        }

        if (planning.budgetsTotal > 0) {
            val budgetPct = planning.budgetsWithinLimit * 100 / planning.budgetsTotal
            val billPct = if (planning.billsDue > 0) planning.billsPaid * 100 / planning.billsDue else 100
            if (budgetPct >= PLANNER_BUDGET_PERCENT && billPct >= PLANNER_BILL_PERCENT) {
                strengths[MoneyPersonality.CAREFUL_PLANNER] = minOf(budgetPct / PLANNER_BUDGET_PERCENT.toDouble(), billPct / PLANNER_BILL_PERCENT.toDouble())
                reasons[MoneyPersonality.CAREFUL_PLANNER] = buildList {
                    add("${planning.budgetsWithinLimit} of ${planning.budgetsTotal} budgets stayed within their limit.")
                    if (planning.billsDue > 0) add("You paid ${planning.billsPaid} of ${planning.billsDue} bills due this month.")
                }
            }
        }

        val maxShare = habits.topCategorySharePercent
        if (byCategory.size >= EXPLORER_MIN_CATEGORIES && maxShare <= EXPLORER_MAX_SHARE_PERCENT) {
            strengths[MoneyPersonality.LITTLE_EXPLORER] = byCategory.size / EXPLORER_MIN_CATEGORIES.toDouble()
            reasons[MoneyPersonality.LITTLE_EXPLORER] = listOf(
                "You spent across ${byCategory.size} different categories.",
                "No single category took more than $maxShare% of your spending."
            )
        }

        val foodCategoryIds = categories.filter { it.iconKey in FOOD_ICON_KEYS }.map { it.id }.toSet()
        val foodSen = expenses.filter { tx ->
            (tx.categoryId != null && tx.categoryId in foodCategoryIds) || FOOD_WORDS.any { tx.category.contains(it, ignoreCase = true) }
        }.sumOf { it.amountSen }
        val foodShare = share(foodSen)
        if (top != null && foodSen >= top.value && foodShare >= FOODIE_SHARE_PERCENT) {
            strengths[MoneyPersonality.HAPPY_FOODIE] = foodShare / FOODIE_SHARE_PERCENT.toDouble()
            reasons[MoneyPersonality.HAPPY_FOODIE] = listOf("Food was your top category, about $foodShare% of your spending.")
        }

        val winner = strengths.entries
            .sortedWith(compareByDescending<Map.Entry<MoneyPersonality, Double>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()?.key
            ?: return PersonalityResult.NoClearPattern(month, habits)
        return PersonalityResult.Assigned(month, habits, winner, reasons.getValue(winner))
    }
}
