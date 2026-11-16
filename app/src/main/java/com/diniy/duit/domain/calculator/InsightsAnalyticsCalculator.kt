package com.diniy.duit.domain.calculator

import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DashboardSummary
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class InsightsPeriodPreset(val label: String) {
    SALARY_CYCLE("Salary Cycle"),
    CALENDAR_MONTH("Calendar Month"),
    PREVIOUS_PERIOD("Previous Period"),
    CUSTOM_RANGE("Custom Range")
}

data class InsightsResolvedPeriod(
    val preset: InsightsPeriodPreset,
    val periodLabel: String,
    val startEpochDay: Long,
    val endEpochDayInclusive: Long,
    val isOpenEnded: Boolean,
    val isOngoing: Boolean,
    val elapsedDays: Long,
    val totalDaysInPeriod: Long,
    val hasConfiguredCycle: Boolean,
    val resolvedCycle: SalaryCycle? = null
)

data class InsightsFinancialOverview(
    val resolvedPeriod: InsightsResolvedPeriod,
    val totalIncomeSen: Long,
    val totalExpenseSen: Long,
    val netCashFlowSen: Long,
    val savingsRateDisplay: String,
    val hasIncomeForSavingsRate: Boolean,
    val avgDailySpendingSen: Long,
    val safeToSpendSen: Long,
    val transferCountExcluded: Int,
    val transferTotalExcludedSen: Long
)

data class MonthlyCashflowPoint(
    val yearMonth: YearMonth,
    val shortLabel: String,
    val incomeSen: Long,
    val expenseSen: Long,
    val netSen: Long
)

data class CategorySpendingItem(
    val categoryName: String,
    val amountSen: Long,
    val transactionCount: Int,
    val percentageDisplay: String,
    val fraction: Float,
    val colorSlot: Int
)

data class MonthlySavingsTrendPoint(
    val yearMonth: YearMonth,
    val shortLabel: String,
    val inflowSen: Long,
    val outflowSen: Long,
    val netChangeSen: Long,
    val endBalanceSen: Long
)

data class SavingsAnalyticsSummary(
    val overview: OverallSavingsOverview,
    val goalProgressList: List<SavingsGoalProgress>,
    val periodContributionsSen: Long,
    val periodWithdrawalsSen: Long,
    val periodNetSavingsSen: Long,
    val periodContributionCount: Int,
    val monthlyTrend: List<MonthlySavingsTrendPoint>,
    val hasMovementHistory: Boolean
)

enum class SmartInsightTone {
    POSITIVE,
    INFO,
    WARNING
}

data class SmartInsight(
    val id: String,
    val title: String,
    val detail: String,
    val tone: SmartInsightTone
)

data class InsightsAnalyticsBundle(
    val dashboardSummary: DashboardSummary,
    val overview: InsightsFinancialOverview,
    val monthlyTrends: List<MonthlyCashflowPoint>,
    val categoryBreakdown: List<CategorySpendingItem>,
    val periodExpenseTransactions: List<FinanceTransaction>,
    val savingsAnalytics: SavingsAnalyticsSummary,
    val budgetOverview: BudgetPeriodOverview,
    val budgetHistory: List<BudgetProgress>,
    val obligationsOverview: ObligationsOverview,
    val smartInsights: List<SmartInsight>
)

/**
 * Pure domain calculator for Phase 4D Insights & Analytics.
 * Uses integer minor units (sen) and BigDecimal for human-readable percentages.
 * Guarantees transfers, protected savings movements, budgets, unpaid obligations,
 * and pending commissions never inflate income or expenses.
 */
object InsightsAnalyticsCalculator {

    private val ONE_HUNDRED = BigDecimal(100)

    fun resolvePeriod(
        preset: InsightsPeriodPreset,
        selectedMonth: YearMonth,
        activeCycle: SalaryCycle?,
        salaryCycles: List<SalaryCycle>,
        customStartEpochDay: Long?,
        customEndEpochDay: Long?,
        today: LocalDate = LocalDate.now()
    ): InsightsResolvedPeriod {
        val todayEpoch = today.toEpochDay()
        val reconciledAsc = PeriodReportingCalculator.reconcileCycleBoundaries(salaryCycles)
        val effectiveActiveCycle = reconciledAsc.lastOrNull { it.isActive } ?: activeCycle

        return when (preset) {
            InsightsPeriodPreset.SALARY_CYCLE -> {
                if (effectiveActiveCycle != null) {
                    val endInclusive = if (effectiveActiveCycle.isOpenEnded) {
                        maxOf(
                            LocalDate.ofEpochDay(effectiveActiveCycle.startDateEpochDay)
                                .plusMonths(1)
                                .minusDays(1)
                                .toEpochDay(),
                            todayEpoch
                        )
                    } else {
                        effectiveActiveCycle.endDateEpochDay
                    }
                    buildResolvedPeriod(
                        preset = preset,
                        label = effectiveActiveCycle.label,
                        start = effectiveActiveCycle.startDateEpochDay,
                        endInclusive = endInclusive,
                        isOpenEnded = effectiveActiveCycle.isOpenEnded,
                        hasConfiguredCycle = true,
                        resolvedCycle = effectiveActiveCycle,
                        todayEpoch = todayEpoch
                    )
                } else {
                    val monthFromToday = YearMonth.from(today)
                    buildResolvedPeriod(
                        preset = preset,
                        label = "${monthFromToday.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${monthFromToday.year} (No active cycle)",
                        start = monthFromToday.atDay(1).toEpochDay(),
                        endInclusive = monthFromToday.atEndOfMonth().toEpochDay(),
                        isOpenEnded = false,
                        hasConfiguredCycle = false,
                        resolvedCycle = null,
                        todayEpoch = todayEpoch
                    )
                }
            }

            InsightsPeriodPreset.CALENDAR_MONTH -> {
                val monthLabel = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${selectedMonth.year}"
                buildResolvedPeriod(
                    preset = preset,
                    label = monthLabel,
                    start = selectedMonth.atDay(1).toEpochDay(),
                    endInclusive = selectedMonth.atEndOfMonth().toEpochDay(),
                    isOpenEnded = false,
                    hasConfiguredCycle = effectiveActiveCycle != null,
                    resolvedCycle = null,
                    todayEpoch = todayEpoch
                )
            }

            InsightsPeriodPreset.PREVIOUS_PERIOD -> {
                val prevCycle = if (effectiveActiveCycle != null && reconciledAsc.size >= 2) {
                    val activeIdx = reconciledAsc.indexOfFirst { it.id == effectiveActiveCycle.id }
                    if (activeIdx > 0) reconciledAsc[activeIdx - 1] else reconciledAsc.dropLast(1).lastOrNull()
                } else {
                    null
                }
                if (prevCycle != null) {
                    buildResolvedPeriod(
                        preset = preset,
                        label = "Previous Cycle · ${prevCycle.label}",
                        start = prevCycle.startDateEpochDay,
                        endInclusive = prevCycle.endDateEpochDay,
                        isOpenEnded = false,
                        hasConfiguredCycle = true,
                        resolvedCycle = prevCycle,
                        todayEpoch = todayEpoch
                    )
                } else {
                    val prevMonth = selectedMonth.minusMonths(1)
                    val prevLabel = "${prevMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${prevMonth.year}"
                    buildResolvedPeriod(
                        preset = preset,
                        label = "Previous Month · $prevLabel",
                        start = prevMonth.atDay(1).toEpochDay(),
                        endInclusive = prevMonth.atEndOfMonth().toEpochDay(),
                        isOpenEnded = false,
                        hasConfiguredCycle = effectiveActiveCycle != null,
                        resolvedCycle = null,
                        todayEpoch = todayEpoch
                    )
                }
            }

            InsightsPeriodPreset.CUSTOM_RANGE -> {
                val defaultStart = selectedMonth.atDay(1).toEpochDay()
                val defaultEnd = minOf(selectedMonth.atEndOfMonth().toEpochDay(), todayEpoch).coerceAtLeast(defaultStart)
                val rawStart = customStartEpochDay ?: defaultStart
                val rawEnd = customEndEpochDay ?: defaultEnd
                val start = minOf(rawStart, rawEnd)
                val end = maxOf(rawStart, rawEnd)
                val label = "${DateTimeFormatters.formatEpochDay(start)} – ${DateTimeFormatters.formatEpochDay(end)}"
                buildResolvedPeriod(
                    preset = preset,
                    label = label,
                    start = start,
                    endInclusive = end,
                    isOpenEnded = false,
                    hasConfiguredCycle = effectiveActiveCycle != null,
                    resolvedCycle = null,
                    todayEpoch = todayEpoch
                )
            }
        }
    }

    private fun buildResolvedPeriod(
        preset: InsightsPeriodPreset,
        label: String,
        start: Long,
        endInclusive: Long,
        isOpenEnded: Boolean,
        hasConfiguredCycle: Boolean,
        resolvedCycle: SalaryCycle?,
        todayEpoch: Long
    ): InsightsResolvedPeriod {
        val clampedEnd = maxOf(start, endInclusive)
        val isOngoing = todayEpoch in start..clampedEnd
        val elapsedDays = when {
            todayEpoch < start -> 0L
            todayEpoch <= clampedEnd -> (todayEpoch - start + 1L).coerceAtLeast(1L)
            else -> (clampedEnd - start + 1L).coerceAtLeast(1L)
        }
        val totalDays = (clampedEnd - start + 1L).coerceAtLeast(1L)
        return InsightsResolvedPeriod(
            preset = preset,
            periodLabel = label,
            startEpochDay = start,
            endEpochDayInclusive = clampedEnd,
            isOpenEnded = isOpenEnded,
            isOngoing = isOngoing,
            elapsedDays = elapsedDays,
            totalDaysInPeriod = totalDays,
            hasConfiguredCycle = hasConfiguredCycle,
            resolvedCycle = resolvedCycle
        )
    }

    fun calculate(
        preset: InsightsPeriodPreset,
        selectedMonth: YearMonth,
        trendHorizonMonths: Int,
        customStartEpochDay: Long?,
        customEndEpochDay: Long?,
        wallets: List<Wallet>,
        transactions: List<FinanceTransaction>,
        categories: List<Category>,
        commitments: List<Commitment>,
        commissions: List<CommissionRecord>,
        salaryCycles: List<SalaryCycle>,
        activeCycle: SalaryCycle?,
        savingsGoals: List<SavingsGoal>,
        savingsAllocations: List<SavingsAllocation>,
        savingsMovements: List<SavingsMovement>,
        budgets: List<Budget>,
        payLaterPlans: List<PayLaterPlan>,
        debts: List<DebtRecord>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): InsightsAnalyticsBundle {
        val reconciledCycles = PeriodReportingCalculator.reconcileCycleBoundaries(salaryCycles)
        val effectiveActiveCycle = reconciledCycles.lastOrNull { it.isActive } ?: activeCycle

        val resolvedPeriod = resolvePeriod(
            preset = preset,
            selectedMonth = selectedMonth,
            activeCycle = effectiveActiveCycle,
            salaryCycles = reconciledCycles,
            customStartEpochDay = customStartEpochDay,
            customEndEpochDay = customEndEpochDay,
            today = today
        )

        val dashboardSummary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = transactions,
            commitments = commitments,
            commissions = commissions,
            savingsAllocations = savingsAllocations,
            savingsGoals = savingsGoals,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = effectiveActiveCycle,
            today = today
        )

        val txByEpochDay = transactions.map { tx ->
            tx to DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs, zoneId).toEpochDay()
        }

        val periodTxs = txByEpochDay.filter { (tx, day) ->
            val inStart = day >= resolvedPeriod.startEpochDay
            val inEnd = if (resolvedPeriod.isOpenEnded && resolvedPeriod.preset == InsightsPeriodPreset.SALARY_CYCLE) {
                true
            } else {
                day <= resolvedPeriod.endEpochDayInclusive
            }
            inStart && inEnd
        }.map { it.first }

        val totalIncomeSen = periodTxs
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountSen }
        val periodExpenses = periodTxs
            .filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.occurredAtEpochMs }
        val totalExpenseSen = periodExpenses.sumOf { it.amountSen }
        val transfersInPeriod = periodTxs.filter { it.type == TransactionType.TRANSFER }

        val netCashFlowSen = totalIncomeSen - totalExpenseSen
        val hasIncomeForRate = totalIncomeSen > 0L
        val savingsRateDisplay = if (hasIncomeForRate) {
            formatSignedPercent(netCashFlowSen, totalIncomeSen)
        } else {
            "—"
        }

        val elapsedDays = resolvedPeriod.elapsedDays
        val avgDailySpendingSen = if (elapsedDays > 0L && totalExpenseSen > 0L) {
            (totalExpenseSen + elapsedDays / 2L) / elapsedDays
        } else {
            0L
        }

        val overview = InsightsFinancialOverview(
            resolvedPeriod = resolvedPeriod,
            totalIncomeSen = totalIncomeSen,
            totalExpenseSen = totalExpenseSen,
            netCashFlowSen = netCashFlowSen,
            savingsRateDisplay = savingsRateDisplay,
            hasIncomeForSavingsRate = hasIncomeForRate,
            avgDailySpendingSen = avgDailySpendingSen,
            safeToSpendSen = dashboardSummary.safeToSpendSen,
            transferCountExcluded = transfersInPeriod.size,
            transferTotalExcludedSen = transfersInPeriod.sumOf { it.amountSen }
        )

        val safeHorizon = when (trendHorizonMonths) {
            3, 6, 12 -> trendHorizonMonths
            else -> 6
        }
        val anchorMonth = when (preset) {
            InsightsPeriodPreset.CALENDAR_MONTH -> selectedMonth
            InsightsPeriodPreset.PREVIOUS_PERIOD ->
                resolvedPeriod.resolvedCycle?.let { YearMonth.from(LocalDate.ofEpochDay(it.startDateEpochDay)) }
                    ?: selectedMonth.minusMonths(1)
            else -> YearMonth.from(today)
        }

        val monthlyTrends = calculateMonthlyTrends(
            anchorMonth = anchorMonth,
            monthsCount = safeHorizon,
            txByEpochDay = txByEpochDay
        )

        val categoryBreakdown = calculateCategoryBreakdown(
            expenses = periodExpenses,
            totalExpenseSen = totalExpenseSen
        )

        val savingsAnalytics = calculateSavingsAnalytics(
            wallets = wallets,
            totalProtectedSavingsSen = dashboardSummary.savingsBalanceSen,
            goals = savingsGoals,
            allocations = savingsAllocations,
            movements = savingsMovements,
            txByEpochDay = txByEpochDay,
            periodStartDay = resolvedPeriod.startEpochDay,
            periodEndDay = if (resolvedPeriod.isOpenEnded) today.toEpochDay() else resolvedPeriod.endEpochDayInclusive,
            anchorMonth = anchorMonth,
            today = today,
            zoneId = zoneId
        )

        val budgetPeriodType = when {
            resolvedPeriod.resolvedCycle != null -> BudgetPeriodType.SALARY_CYCLE
            else -> BudgetPeriodType.MONTHLY
        }
        val budgetTargetMonth = when (preset) {
            InsightsPeriodPreset.PREVIOUS_PERIOD -> selectedMonth.minusMonths(1)
            InsightsPeriodPreset.CUSTOM_RANGE -> YearMonth.from(LocalDate.ofEpochDay(resolvedPeriod.startEpochDay))
            else -> selectedMonth
        }
        val budgetOverview = BudgetCalculator.calculatePeriodOverview(
            periodType = budgetPeriodType,
            selectedMonth = budgetTargetMonth,
            selectedCycle = resolvedPeriod.resolvedCycle,
            budgets = budgets,
            transactions = transactions,
            cycles = reconciledCycles,
            categories = categories,
            zoneId = zoneId
        )
        val budgetHistory = BudgetCalculator.calculateHistoryEntries(
            periodType = budgetPeriodType,
            selectedMonth = budgetTargetMonth,
            selectedCycle = resolvedPeriod.resolvedCycle,
            budgets = budgets,
            transactions = transactions,
            cycles = reconciledCycles,
            categories = categories,
            zoneId = zoneId
        )

        val obligationsOverview = ObligationCalculator.calculateOverview(
            commitments = commitments,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = effectiveActiveCycle,
            today = today
        )

        val smartInsights = buildSmartInsights(
            resolvedPeriod = resolvedPeriod,
            overview = overview,
            categoryBreakdown = categoryBreakdown,
            txByEpochDay = txByEpochDay,
            wallets = wallets,
            movements = savingsMovements,
            reconciledCycles = reconciledCycles,
            budgetOverview = budgetOverview,
            obligationsOverview = obligationsOverview,
            today = today,
            zoneId = zoneId
        )

        return InsightsAnalyticsBundle(
            dashboardSummary = dashboardSummary,
            overview = overview,
            monthlyTrends = monthlyTrends,
            categoryBreakdown = categoryBreakdown,
            periodExpenseTransactions = periodExpenses,
            savingsAnalytics = savingsAnalytics,
            budgetOverview = budgetOverview,
            budgetHistory = budgetHistory,
            obligationsOverview = obligationsOverview,
            smartInsights = smartInsights
        )
    }

    fun calculateMonthlyTrends(
        anchorMonth: YearMonth,
        monthsCount: Int,
        txByEpochDay: List<Pair<FinanceTransaction, Long>>
    ): List<MonthlyCashflowPoint> {
        val count = monthsCount.coerceIn(1, 24)
        val startMonth = anchorMonth.minusMonths((count - 1).toLong())
        val firstDay = startMonth.atDay(1).toEpochDay()
        val lastDay = anchorMonth.atEndOfMonth().toEpochDay()

        val monthBuckets = linkedMapOf<YearMonth, Pair<Long, Long>>()
        for (i in 0 until count) {
            monthBuckets[startMonth.plusMonths(i.toLong())] = 0L to 0L
        }

        for ((tx, day) in txByEpochDay) {
            if (day !in firstDay..lastDay) continue
            if (tx.type == TransactionType.TRANSFER) continue
            val ym = YearMonth.from(LocalDate.ofEpochDay(day))
            val current = monthBuckets[ym] ?: continue
            monthBuckets[ym] = when (tx.type) {
                TransactionType.INCOME -> (current.first + tx.amountSen) to current.second
                TransactionType.EXPENSE -> current.first to (current.second + tx.amountSen)
                TransactionType.TRANSFER -> current
            }
        }

        return monthBuckets.entries.mapIndexed { idx, (ym, sums) ->
            val short = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val label = if (idx == 0 || ym.monthValue == 1) "$short '${(ym.year % 100).toString().padStart(2, '0')}" else short
            MonthlyCashflowPoint(
                yearMonth = ym,
                shortLabel = label,
                incomeSen = sums.first,
                expenseSen = sums.second,
                netSen = sums.first - sums.second
            )
        }
    }

    fun calculateCategoryBreakdown(
        expenses: List<FinanceTransaction>,
        totalExpenseSen: Long
    ): List<CategorySpendingItem> {
        if (expenses.isEmpty() || totalExpenseSen <= 0L) return emptyList()
        return expenses
            .groupBy { it.category.ifBlank { "Other" } }
            .entries
            .sortedByDescending { (_, list) -> list.sumOf { it.amountSen } }
            .mapIndexed { index, (catName, list) ->
                val sumSen = list.sumOf { it.amountSen }
                val fraction = ((sumSen * 1000L) / totalExpenseSen).toInt().coerceIn(0, 1000) / 1000f
                CategorySpendingItem(
                    categoryName = catName,
                    amountSen = sumSen,
                    transactionCount = list.size,
                    percentageDisplay = formatSignedPercent(sumSen, totalExpenseSen),
                    fraction = fraction,
                    colorSlot = index
                )
            }
    }

    private fun calculateSavingsAnalytics(
        wallets: List<Wallet>,
        totalProtectedSavingsSen: Long,
        goals: List<SavingsGoal>,
        allocations: List<SavingsAllocation>,
        movements: List<SavingsMovement>,
        txByEpochDay: List<Pair<FinanceTransaction, Long>>,
        periodStartDay: Long,
        periodEndDay: Long,
        anchorMonth: YearMonth,
        today: LocalDate,
        zoneId: ZoneId
    ): SavingsAnalyticsSummary {
        val overview = SavingsCalculator.calculateOverview(
            totalProtectedSavingsSen = totalProtectedSavingsSen,
            goals = goals,
            allocations = allocations
        )
        val goalProgressList = goals.map { goal ->
            SavingsCalculator.calculateGoalProgress(goal, allocations, today)
        }

        val protectedWalletIds = wallets
            .filter { it.isIncludedInTotalBalance && FinanceSummaryCalculator.isProtectedSavingsWallet(it) }
            .map { it.id }
            .toSet()

        // Collect all savings cashflow events without double-counting TRANSFER_AND_ALLOCATE / TRANSFER_TO_SPENDING
        val savingsFlowEvents = mutableListOf<Triple<Long, Long, Long>>() // epochDay, inflowSen, outflowSen

        for ((tx, day) in txByEpochDay) {
            when (tx.type) {
                TransactionType.TRANSFER -> {
                    val fromProtected = tx.walletId in protectedWalletIds
                    val toProtected = tx.targetWalletId != null && tx.targetWalletId in protectedWalletIds
                    if (toProtected && !fromProtected) {
                        savingsFlowEvents += Triple(day, tx.amountSen, 0L)
                    } else if (fromProtected && !toProtected) {
                        savingsFlowEvents += Triple(day, 0L, tx.amountSen)
                    }
                }
                TransactionType.INCOME -> {
                    if (tx.walletId in protectedWalletIds) {
                        savingsFlowEvents += Triple(day, tx.amountSen, 0L)
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.walletId in protectedWalletIds) {
                        savingsFlowEvents += Triple(day, 0L, tx.amountSen)
                    }
                }
            }
        }

        for (mv in movements) {
            val day = DateTimeFormatters.toLocalDate(mv.occurredAtEpochMs, zoneId).toEpochDay()
            when (mv.type) {
                SavingsMovementType.ALLOCATE -> {
                    if (mv.sourceWalletId !in protectedWalletIds) {
                        savingsFlowEvents += Triple(day, mv.amountSen, 0L)
                    }
                }
                SavingsMovementType.WITHDRAW_TO_WALLET -> {
                    if (mv.sourceWalletId !in protectedWalletIds) {
                        savingsFlowEvents += Triple(day, 0L, mv.amountSen)
                    }
                }
                // TRANSFER_AND_ALLOCATE & TRANSFER_TO_SPENDING already have a linked TRANSFER tx above.
                // LINK_PROTECTED & REASSIGN do not change aggregate protected savings.
                SavingsMovementType.TRANSFER_AND_ALLOCATE,
                SavingsMovementType.TRANSFER_TO_SPENDING,
                SavingsMovementType.LINK_PROTECTED,
                SavingsMovementType.REASSIGN -> Unit
            }
        }

        val periodEvents = savingsFlowEvents.filter { (day, _, _) -> day in periodStartDay..periodEndDay }
        val periodContributions = periodEvents.sumOf { it.second }
        val periodWithdrawals = periodEvents.sumOf { it.third }
        val periodContributionCount = periodEvents.count { it.second > 0L }

        // Monthly trend across last 6 months up to anchorMonth
        val startMonth = anchorMonth.minusMonths(5)
        val monthlyBuckets = linkedMapOf<YearMonth, Pair<Long, Long>>()
        for (i in 0 until 6) {
            monthlyBuckets[startMonth.plusMonths(i.toLong())] = 0L to 0L
        }
        val firstTrendDay = startMonth.atDay(1).toEpochDay()
        val lastTrendDay = anchorMonth.atEndOfMonth().toEpochDay()
        var futureNetAfterAnchor = 0L

        for ((day, inflow, outflow) in savingsFlowEvents) {
            if (day > lastTrendDay) {
                futureNetAfterAnchor += (inflow - outflow)
            } else if (day in firstTrendDay..lastTrendDay) {
                val ym = YearMonth.from(LocalDate.ofEpochDay(day))
                val cur = monthlyBuckets[ym] ?: continue
                monthlyBuckets[ym] = (cur.first + inflow) to (cur.second + outflow)
            }
        }

        // Work backward from current total protected savings to derive authentic month-end balances
        val orderedMonths = monthlyBuckets.entries.toList()
        var runningEndBalance = (totalProtectedSavingsSen - futureNetAfterAnchor).coerceAtLeast(0L)
        val reversedPoints = mutableListOf<MonthlySavingsTrendPoint>()
        for (entry in orderedMonths.asReversed()) {
            val ym = entry.key
            val inflow = entry.value.first
            val outflow = entry.value.second
            val net = inflow - outflow
            val short = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            reversedPoints += MonthlySavingsTrendPoint(
                yearMonth = ym,
                shortLabel = short,
                inflowSen = inflow,
                outflowSen = outflow,
                netChangeSen = net,
                endBalanceSen = runningEndBalance
            )
            runningEndBalance = (runningEndBalance - net).coerceAtLeast(0L)
        }
        val trendPoints = reversedPoints.asReversed()

        return SavingsAnalyticsSummary(
            overview = overview,
            goalProgressList = goalProgressList,
            periodContributionsSen = periodContributions,
            periodWithdrawalsSen = periodWithdrawals,
            periodNetSavingsSen = periodContributions - periodWithdrawals,
            periodContributionCount = periodContributionCount,
            monthlyTrend = trendPoints,
            hasMovementHistory = savingsFlowEvents.isNotEmpty()
        )
    }

    private fun buildSmartInsights(
        resolvedPeriod: InsightsResolvedPeriod,
        overview: InsightsFinancialOverview,
        categoryBreakdown: List<CategorySpendingItem>,
        txByEpochDay: List<Pair<FinanceTransaction, Long>>,
        wallets: List<Wallet>,
        movements: List<SavingsMovement>,
        reconciledCycles: List<SalaryCycle>,
        budgetOverview: BudgetPeriodOverview,
        obligationsOverview: ObligationsOverview,
        today: LocalDate,
        zoneId: ZoneId
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()

        // 1. Highest spending category in current period
        val topCategory = categoryBreakdown.firstOrNull()
        if (topCategory != null && overview.totalExpenseSen > 0L) {
            insights += SmartInsight(
                id = "top_category",
                title = "${topCategory.categoryName} is your highest spending category",
                detail = "${MyrFormatter.format(topCategory.amountSen)} (${topCategory.percentageDisplay} of total expenses) in ${resolvedPeriod.periodLabel}.",
                tone = SmartInsightTone.INFO
            )
        }

        // 2. Fair period-over-period spending comparison (matched elapsed window for ongoing periods)
        val todayEpoch = today.toEpochDay()
        if (resolvedPeriod.elapsedDays >= 2L) {
            val currentEnd = minOf(resolvedPeriod.endEpochDayInclusive, todayEpoch)
            val currentWindowExpense = txByEpochDay
                .filter { (tx, day) -> tx.type == TransactionType.EXPENSE && day in resolvedPeriod.startEpochDay..currentEnd }
                .sumOf { it.first.amountSen }

            val prevWindowRange: Pair<Long, Long>? = when (resolvedPeriod.preset) {
                InsightsPeriodPreset.CALENDAR_MONTH -> {
                    val curStartDate = LocalDate.ofEpochDay(resolvedPeriod.startEpochDay)
                    val prevMonth = YearMonth.from(curStartDate).minusMonths(1)
                    val windowDays = if (resolvedPeriod.isOngoing) resolvedPeriod.elapsedDays.toInt() else prevMonth.lengthOfMonth()
                    val prevEndDate = prevMonth.atDay(minOf(windowDays, prevMonth.lengthOfMonth()))
                    prevMonth.atDay(1).toEpochDay() to prevEndDate.toEpochDay()
                }
                InsightsPeriodPreset.SALARY_CYCLE -> {
                    val curCycle = resolvedPeriod.resolvedCycle
                    val idx = curCycle?.let { c -> reconciledCycles.indexOfFirst { it.id == c.id } } ?: -1
                    val prevCycle = if (idx > 0) reconciledCycles[idx - 1] else null
                    if (prevCycle != null) {
                        val endDay = if (resolvedPeriod.isOngoing) {
                            minOf(prevCycle.endDateEpochDay, prevCycle.startDateEpochDay + resolvedPeriod.elapsedDays - 1L)
                        } else {
                            prevCycle.endDateEpochDay
                        }
                        if (endDay >= prevCycle.startDateEpochDay) prevCycle.startDateEpochDay to endDay else null
                    } else {
                        null
                    }
                }
                InsightsPeriodPreset.PREVIOUS_PERIOD,
                InsightsPeriodPreset.CUSTOM_RANGE -> null
            }

            if (prevWindowRange != null) {
                val prevExpense = txByEpochDay
                    .filter { (tx, day) -> tx.type == TransactionType.EXPENSE && day in prevWindowRange.first..prevWindowRange.second }
                    .sumOf { it.first.amountSen }

                if (prevExpense > 0L) {
                    val diff = currentWindowExpense - prevExpense
                    val absDiff = kotlin.math.abs(diff)
                    val pctDisplay = formatWholePercent(absDiff, prevExpense)
                    val timeframeHint = if (resolvedPeriod.isOngoing) "the first ${resolvedPeriod.elapsedDays} days of the previous period" else "the previous period"
                    if (diff < 0L && absDiff > 0L) {
                        insights += SmartInsight(
                            id = "spending_delta",
                            title = "You spent $pctDisplay less than $timeframeHint",
                            detail = "${MyrFormatter.format(currentWindowExpense)} vs ${MyrFormatter.format(prevExpense)} (${MyrFormatter.format(absDiff)} saved).",
                            tone = SmartInsightTone.POSITIVE
                        )
                    } else if (diff > 0L) {
                        insights += SmartInsight(
                            id = "spending_delta",
                            title = "Spending is $pctDisplay higher than $timeframeHint",
                            detail = "${MyrFormatter.format(currentWindowExpense)} vs ${MyrFormatter.format(prevExpense)} (${MyrFormatter.format(absDiff)} more).",
                            tone = SmartInsightTone.WARNING
                        )
                    }
                }
            }
        }

        // 3. Savings comparison vs previous month (matched elapsed window)
        val curYm = YearMonth.from(today)
        val prevYm = curYm.minusMonths(1)
        val curDayLimit = minOf(today.dayOfMonth, prevYm.lengthOfMonth())
        val curMonthWindowStart = curYm.atDay(1).toEpochDay()
        val curMonthWindowEnd = curYm.atDay(today.dayOfMonth).toEpochDay()
        val prevMonthWindowStart = prevYm.atDay(1).toEpochDay()
        val prevMonthWindowEnd = prevYm.atDay(curDayLimit).toEpochDay()

        val protectedIds = wallets
            .filter { it.isIncludedInTotalBalance && FinanceSummaryCalculator.isProtectedSavingsWallet(it) }
            .map { it.id }
            .toSet()

        fun netSavingsInRange(startDay: Long, endDay: Long): Long {
            var net = 0L
            for ((tx, day) in txByEpochDay) {
                if (day !in startDay..endDay) continue
                when (tx.type) {
                    TransactionType.TRANSFER -> {
                        val fromProt = tx.walletId in protectedIds
                        val toProt = tx.targetWalletId != null && tx.targetWalletId in protectedIds
                        if (toProt && !fromProt) net += tx.amountSen
                        else if (fromProt && !toProt) net -= tx.amountSen
                    }
                    TransactionType.INCOME -> if (tx.walletId in protectedIds) net += tx.amountSen
                    TransactionType.EXPENSE -> if (tx.walletId in protectedIds) net -= tx.amountSen
                }
            }
            for (mv in movements) {
                val day = DateTimeFormatters.toLocalDate(mv.occurredAtEpochMs, zoneId).toEpochDay()
                if (day !in startDay..endDay) continue
                if (mv.type == SavingsMovementType.ALLOCATE && mv.sourceWalletId !in protectedIds) net += mv.amountSen
                if (mv.type == SavingsMovementType.WITHDRAW_TO_WALLET && mv.sourceWalletId !in protectedIds) net -= mv.amountSen
            }
            return net
        }

        val curNetSavings = netSavingsInRange(curMonthWindowStart, curMonthWindowEnd)
        val prevNetSavings = netSavingsInRange(prevMonthWindowStart, prevMonthWindowEnd)
        if (curNetSavings > 0L && curNetSavings > prevNetSavings) {
            insights += SmartInsight(
                id = "savings_growth",
                title = "Your savings increased compared with last month",
                detail = "Net protected savings contributions reached ${MyrFormatter.format(curNetSavings)} so far this month vs ${MyrFormatter.format(prevNetSavings)} over the same days last month.",
                tone = SmartInsightTone.POSITIVE
            )
        }

        // 4. Current-cycle unpaid obligations & PayLater instalments
        if (obligationsOverview.payLaterCycleReservedSen > 0L) {
            insights += SmartInsight(
                id = "cycle_paylater",
                title = "You have ${MyrFormatter.format(obligationsOverview.payLaterCycleReservedSen)} in upcoming instalments this cycle",
                detail = "Total current-cycle obligations reserved against Safe to Spend: ${MyrFormatter.format(obligationsOverview.currentCycleReservedSen)} (${obligationsOverview.reserveHorizonLabel}).",
                tone = SmartInsightTone.INFO
            )
        } else if (obligationsOverview.currentCycleReservedSen > 0L) {
            insights += SmartInsight(
                id = "cycle_obligations",
                title = "${MyrFormatter.format(obligationsOverview.currentCycleReservedSen)} reserved for current-cycle obligations",
                detail = "${obligationsOverview.currentCycleReservedCount} obligation(s) due in ${obligationsOverview.reserveHorizonLabel}.",
                tone = SmartInsightTone.INFO
            )
        }

        // 5. Budget warning summary
        if (budgetOverview.exceededCount > 0) {
            insights += SmartInsight(
                id = "budget_exceeded",
                title = "${budgetOverview.exceededCount} budget${if (budgetOverview.exceededCount == 1) "" else "s"} exceeded in ${budgetOverview.periodLabel}",
                detail = "Actual spending exceeded limits by ${MyrFormatter.format(budgetOverview.effectiveOverspentSen)}.",
                tone = SmartInsightTone.WARNING
            )
        } else if (budgetOverview.warningCount80 + budgetOverview.warningCount100 > 0 && budgetOverview.effectiveLimitSen > 0L) {
            insights += SmartInsight(
                id = "budget_approaching",
                title = "${budgetOverview.overallUsedPercentDisplay} of ${budgetOverview.periodLabel} budget used",
                detail = "${MyrFormatter.format(budgetOverview.effectiveRemainingSen)} remaining within your spending cap.",
                tone = SmartInsightTone.INFO
            )
        }

        // 6. Cashflow surplus check when income exists
        if (overview.totalIncomeSen > 0L && overview.netCashFlowSen > 0L && insights.size < 4) {
            insights += SmartInsight(
                id = "positive_cashflow",
                title = "Positive net cash flow of ${MyrFormatter.format(overview.netCashFlowSen)}",
                detail = "Savings rate is ${overview.savingsRateDisplay} across ${resolvedPeriod.periodLabel}.",
                tone = SmartInsightTone.POSITIVE
            )
        }

        return insights
    }

    fun formatSignedPercent(partSen: Long, wholeSen: Long): String {
        if (wholeSen <= 0L) return "0%"
        val pct = BigDecimal(partSen)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal(wholeSen), 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${pct.toPlainString()}%"
    }

    private fun formatWholePercent(partSen: Long, wholeSen: Long): String {
        if (wholeSen <= 0L) return "0%"
        val pct = BigDecimal(partSen)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal(wholeSen), 0, RoundingMode.HALF_UP)
        return "${pct.toPlainString()}%"
    }
}
