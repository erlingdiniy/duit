package com.diniy.duit.domain.calculator

import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.BudgetWarningLevel
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class BudgetProgress(
    val budget: Budget,
    val periodLabel: String,
    val limitSen: Long,
    val spentSen: Long,
    val remainingSen: Long,
    val overspentSen: Long,
    val usedPercentDisplay: String,
    val progressFraction: Float,
    val warningLevel: BudgetWarningLevel,
    val matchingTransactions: List<FinanceTransaction>
)

data class BudgetPeriodOverview(
    val periodLabel: String,
    val periodType: BudgetPeriodType,
    val overallBudget: BudgetProgress?,
    val categoryBudgets: List<BudgetProgress>,
    val totalPeriodExpenseSen: Long,
    val effectiveLimitSen: Long,
    val effectiveCoveredSpentSen: Long,
    val effectiveRemainingSen: Long,
    val effectiveOverspentSen: Long,
    val sumCategoryLimitsSen: Long,
    val categoryLimitsExceedOverall: Boolean,
    val overallUsedPercentDisplay: String,
    val overallProgressFraction: Float,
    val warningCount80: Int,
    val warningCount100: Int,
    val exceededCount: Int
)

/**
 * Pure integer-sen and BigDecimal calculator for Monthly & Salary Cycle budgets.
 * - Strictly sums eligible [TransactionType.EXPENSE] records inside each period boundary.
 * - Excludes income, transfers, savings allocations, and pending commissions.
 * - Prevents double counting when Overall and Category budgets coexist in the same period.
 */
object BudgetCalculator {

    private val ONE_HUNDRED = BigDecimal(100)

    fun computeWarningLevel(spentSen: Long, limitSen: Long): BudgetWarningLevel {
        if (limitSen <= 0L) return BudgetWarningLevel.SAFE
        return when {
            spentSen > limitSen -> BudgetWarningLevel.EXCEEDED
            spentSen == limitSen -> BudgetWarningLevel.AT_LIMIT_100
            // Exact integer-sen check for >= 80%: spent / limit >= 4 / 5
            spentSen * 5L >= limitSen * 4L -> BudgetWarningLevel.APPROACHING_80
            else -> BudgetWarningLevel.SAFE
        }
    }

    fun formatPercent(partSen: Long, wholeSen: Long): String {
        if (wholeSen <= 0L) return "0%"
        val pct = BigDecimal(partSen)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal(wholeSen), 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${pct.toPlainString()}%"
    }

    fun formatYearMonthLabel(rawYearMonth: String): String {
        return runCatching {
            val ym = YearMonth.parse(rawYearMonth)
            "${ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${ym.year}"
        }.getOrDefault(rawYearMonth.ifBlank { "Month" })
    }

    fun resolvePeriodLabel(
        budget: Budget,
        cyclesById: Map<Long, SalaryCycle>
    ): String {
        return when (budget.periodType) {
            BudgetPeriodType.MONTHLY -> formatYearMonthLabel(budget.yearMonth)
            BudgetPeriodType.SALARY_CYCLE ->
                budget.salaryCycleId?.let { cyclesById[it]?.label } ?: "Salary Cycle"
        }
    }

    fun filterEligiblePeriodExpenses(
        periodType: BudgetPeriodType,
        selectedMonth: YearMonth,
        selectedCycle: SalaryCycle?,
        transactions: List<FinanceTransaction>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<FinanceTransaction> {
        return when (periodType) {
            BudgetPeriodType.MONTHLY -> {
                val startDay = selectedMonth.atDay(1).toEpochDay()
                val endDay = selectedMonth.atEndOfMonth().toEpochDay()
                transactions.filter { tx ->
                    if (tx.type != TransactionType.EXPENSE) return@filter false
                    val txDay = DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs, zoneId).toEpochDay()
                    txDay in startDay..endDay
                }
            }
            BudgetPeriodType.SALARY_CYCLE -> {
                if (selectedCycle == null) return emptyList()
                transactions.filter { tx ->
                    if (tx.type != TransactionType.EXPENSE) return@filter false
                    val txDay = DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs, zoneId).toEpochDay()
                    txDay >= selectedCycle.startDateEpochDay &&
                        (selectedCycle.isOpenEnded || txDay <= selectedCycle.endDateEpochDay)
                }
            }
        }
    }

    fun matchesCategory(
        tx: FinanceTransaction,
        budgetCategoryId: Long?,
        budgetCategoryName: String,
        categoriesById: Map<Long, Category>
    ): Boolean {
        if (tx.type != TransactionType.EXPENSE) return false
        if (budgetCategoryId != null && tx.categoryId != null) {
            return tx.categoryId == budgetCategoryId
        }
        val targetName = budgetCategoryId?.let { categoriesById[it]?.name } ?: budgetCategoryName
        if (targetName.isBlank()) return false
        return tx.category.trim().equals(targetName.trim(), ignoreCase = true)
    }

    fun calculateBudgetProgress(
        budget: Budget,
        transactions: List<FinanceTransaction>,
        cyclesById: Map<Long, SalaryCycle>,
        categoriesById: Map<Long, Category>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BudgetProgress {
        val periodTransactions = when (budget.periodType) {
            BudgetPeriodType.MONTHLY -> {
                val ym = runCatching { YearMonth.parse(budget.yearMonth) }.getOrNull()
                if (ym == null) emptyList()
                else filterEligiblePeriodExpenses(BudgetPeriodType.MONTHLY, ym, null, transactions, zoneId)
            }
            BudgetPeriodType.SALARY_CYCLE -> {
                val cycle = budget.salaryCycleId?.let { cyclesById[it] }
                filterEligiblePeriodExpenses(BudgetPeriodType.SALARY_CYCLE, YearMonth.now(), cycle, transactions, zoneId)
            }
        }

        val matching = when (budget.scope) {
            BudgetScope.OVERALL -> periodTransactions
            BudgetScope.CATEGORY -> periodTransactions.filter { tx ->
                matchesCategory(tx, budget.categoryId, budget.categoryName, categoriesById)
            }
        }.sortedByDescending { it.occurredAtEpochMs }

        val spentSen = matching.sumOf { it.amountSen }
        val limitSen = budget.limitSen.coerceAtLeast(0L)
        val remainingSen = (limitSen - spentSen).coerceAtLeast(0L)
        val overspentSen = (spentSen - limitSen).coerceAtLeast(0L)
        val fraction = if (limitSen > 0L) {
            ((spentSen.coerceAtMost(limitSen) * 1000L) / limitSen).toInt() / 1000f
        } else {
            0f
        }

        return BudgetProgress(
            budget = budget,
            periodLabel = resolvePeriodLabel(budget, cyclesById),
            limitSen = limitSen,
            spentSen = spentSen,
            remainingSen = remainingSen,
            overspentSen = overspentSen,
            usedPercentDisplay = formatPercent(spentSen, limitSen),
            progressFraction = fraction,
            warningLevel = computeWarningLevel(spentSen, limitSen),
            matchingTransactions = matching
        )
    }

    fun calculatePeriodOverview(
        periodType: BudgetPeriodType,
        selectedMonth: YearMonth,
        selectedCycle: SalaryCycle?,
        budgets: List<Budget>,
        transactions: List<FinanceTransaction>,
        cycles: List<SalaryCycle>,
        categories: List<Category>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BudgetPeriodOverview {
        val cyclesById = cycles.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val monthKey = selectedMonth.toString()

        val activeForPeriod = budgets.filter { b ->
            !b.isArchived && b.periodType == periodType && when (periodType) {
                BudgetPeriodType.MONTHLY -> b.yearMonth == monthKey
                BudgetPeriodType.SALARY_CYCLE -> selectedCycle != null && b.salaryCycleId == selectedCycle.id
            }
        }

        val periodExpenses = filterEligiblePeriodExpenses(
            periodType = periodType,
            selectedMonth = selectedMonth,
            selectedCycle = selectedCycle,
            transactions = transactions,
            zoneId = zoneId
        )
        val totalPeriodExpenseSen = periodExpenses.sumOf { it.amountSen }

        val progressList = activeForPeriod.map { budget ->
            calculateBudgetProgress(budget, periodExpenses, cyclesById, categoriesById, zoneId)
        }

        val overall = progressList.firstOrNull { it.budget.scope == BudgetScope.OVERALL }
        val categoryBudgets = progressList
            .filter { it.budget.scope == BudgetScope.CATEGORY }
            .sortedWith(
                compareByDescending<BudgetProgress> {
                    if (it.limitSen > 0L) (it.spentSen * 1000L) / it.limitSen else 0L
                }.thenBy { it.budget.categoryName.lowercase() }
            )

        val sumCategoryLimitsSen = categoryBudgets.sumOf { it.limitSen }

        // Prevent double counting between Overall and Category budgets!
        val (effectiveLimitSen, effectiveCoveredSpentSen) = if (overall != null) {
            overall.limitSen to totalPeriodExpenseSen
        } else if (categoryBudgets.isNotEmpty()) {
            // De-duplicate matching transaction IDs across category budgets
            val uniqueMatchedTxIds = categoryBudgets
                .flatMap { it.matchingTransactions }
                .distinctBy { it.id }
            val uniqueCoveredSpend = uniqueMatchedTxIds.sumOf { it.amountSen }
            sumCategoryLimitsSen to uniqueCoveredSpend
        } else {
            0L to totalPeriodExpenseSen
        }

        val effectiveRemainingSen = (effectiveLimitSen - effectiveCoveredSpentSen).coerceAtLeast(0L)
        val effectiveOverspentSen = (effectiveCoveredSpentSen - effectiveLimitSen).coerceAtLeast(0L)
        val fraction = if (effectiveLimitSen > 0L) {
            ((effectiveCoveredSpentSen.coerceAtMost(effectiveLimitSen) * 1000L) / effectiveLimitSen).toInt() / 1000f
        } else {
            0f
        }

        val label = when (periodType) {
            BudgetPeriodType.MONTHLY -> formatYearMonthLabel(monthKey)
            BudgetPeriodType.SALARY_CYCLE -> selectedCycle?.label ?: "No salary cycle selected"
        }

        return BudgetPeriodOverview(
            periodLabel = label,
            periodType = periodType,
            overallBudget = overall,
            categoryBudgets = categoryBudgets,
            totalPeriodExpenseSen = totalPeriodExpenseSen,
            effectiveLimitSen = effectiveLimitSen,
            effectiveCoveredSpentSen = effectiveCoveredSpentSen,
            effectiveRemainingSen = effectiveRemainingSen,
            effectiveOverspentSen = effectiveOverspentSen,
            sumCategoryLimitsSen = sumCategoryLimitsSen,
            categoryLimitsExceedOverall = overall != null && sumCategoryLimitsSen > overall.limitSen,
            overallUsedPercentDisplay = formatPercent(effectiveCoveredSpentSen, effectiveLimitSen),
            overallProgressFraction = fraction,
            warningCount80 = progressList.count { it.warningLevel == BudgetWarningLevel.APPROACHING_80 },
            warningCount100 = progressList.count { it.warningLevel == BudgetWarningLevel.AT_LIMIT_100 },
            exceededCount = progressList.count { it.warningLevel == BudgetWarningLevel.EXCEEDED }
        )
    }

    fun calculateHistoryEntries(
        periodType: BudgetPeriodType,
        selectedMonth: YearMonth,
        selectedCycle: SalaryCycle?,
        budgets: List<Budget>,
        transactions: List<FinanceTransaction>,
        cycles: List<SalaryCycle>,
        categories: List<Category>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<BudgetProgress> {
        val cyclesById = cycles.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val monthKey = selectedMonth.toString()

        val historicalOrArchived = budgets.filter { b ->
            val isCurrentSelectedActive = !b.isArchived && b.periodType == periodType && when (periodType) {
                BudgetPeriodType.MONTHLY -> b.yearMonth == monthKey
                BudgetPeriodType.SALARY_CYCLE -> selectedCycle != null && b.salaryCycleId == selectedCycle.id
            }
            !isCurrentSelectedActive
        }

        return historicalOrArchived
            .map { calculateBudgetProgress(it, transactions, cyclesById, categoriesById, zoneId) }
            .sortedWith(
                compareBy<BudgetProgress> { it.budget.isArchived }
                    .thenByDescending { it.budget.updatedAtEpochMs }
            )
    }
}
