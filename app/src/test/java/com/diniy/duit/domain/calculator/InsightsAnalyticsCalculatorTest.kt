package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsAnalyticsCalculatorTest {

    private val zoneId: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    private fun msOn(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun financialOverviewAndCategoryBreakdown_excludeTransfersSavingsAndPendingCommissions() {
        val wallets = listOf(
            Wallet(1L, "MAE", WalletType.BANK, 400_000L, false, PrismaticAccentToken.AQUA, 1L, 1L),
            Wallet(2L, "Vault", WalletType.SAVINGS, 100_000L, true, PrismaticAccentToken.SKY_BLUE, 1L, 1L)
        )
        val txs = listOf(
            // Real Income: RM 3,000 Salary
            FinanceTransaction(1L, 1L, null, TransactionType.INCOME, 300_000L, "Salary", "Nov Salary", "", null, msOn(LocalDate.of(2026, 11, 5)), 1L),
            // Real Expenses: Food RM 600 + Transport RM 400 = RM 1,000
            FinanceTransaction(2L, 1L, null, TransactionType.EXPENSE, 60_000L, "Food & Dining", "Groceries", "", null, msOn(LocalDate.of(2026, 11, 6)), 1L),
            FinanceTransaction(3L, 1L, null, TransactionType.EXPENSE, 40_000L, "Transport", "Fuel", "", null, msOn(LocalDate.of(2026, 11, 10)), 1L),
            // Internal Transfer to Protected Vault: RM 500 -> MUST NEVER inflate Income or Expense!
            FinanceTransaction(4L, 1L, 2L, TransactionType.TRANSFER, 50_000L, "Wallet Transfer", "Move to Vault", "", null, msOn(LocalDate.of(2026, 11, 8)), 1L)
        )
        val pendingCommissions = listOf(
            CommissionRecord(1L, "Nov Bonus", "Company", 80_000L, CommissionStatus.PENDING, LocalDate.of(2026, 11, 12).toEpochDay(), null, null, "")
        )
        val goal = SavingsGoal(1L, "Emergency", 200_000L, null, "", "savings", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, 1L, 1L)
        val allocs = listOf(SavingsAllocation(1L, 1L, 2L, 100_000L, 1L))
        val movements = listOf(
            SavingsMovement(1L, 1L, 1L, 2L, null, SavingsMovementType.TRANSFER_AND_ALLOCATE, 50_000L, 4L, "Save Nov", msOn(LocalDate.of(2026, 11, 8)), "s1")
        )

        val bundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.CALENDAR_MONTH,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 6,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = wallets,
            transactions = txs,
            categories = emptyList(),
            commitments = emptyList(),
            commissions = pendingCommissions,
            salaryCycles = emptyList(),
            activeCycle = null,
            savingsGoals = listOf(goal),
            savingsAllocations = allocs,
            savingsMovements = movements,
            budgets = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            today = today,
            zoneId = zoneId
        )

        // 1. Income, Expenses, Net Cashflow, Savings Rate, Avg Daily Spending, Safe to Spend
        assertEquals(300_000L, bundle.overview.totalIncomeSen)
        assertEquals(100_000L, bundle.overview.totalExpenseSen)
        assertEquals(200_000L, bundle.overview.netCashFlowSen)
        assertEquals("66.7%", bundle.overview.savingsRateDisplay)
        // Nov 1..16 = 16 elapsed days -> 100,000 / 16 = 6,250 sen/day
        assertEquals(6_250L, bundle.overview.avgDailySpendingSen)
        // Liquid MAE = RM 4,000, Vault RM 1,000 excluded, Pending commission RM 800 excluded -> Safe to Spend = RM 4,000
        assertEquals(400_000L, bundle.overview.safeToSpendSen)
        assertEquals(1, bundle.overview.transferCountExcluded)
        assertEquals(50_000L, bundle.overview.transferTotalExcludedSen)

        // 3. Category breakdown: 60% Food, 40% Transport
        assertEquals(2, bundle.categoryBreakdown.size)
        assertEquals("Food & Dining", bundle.categoryBreakdown[0].categoryName)
        assertEquals(60_000L, bundle.categoryBreakdown[0].amountSen)
        assertEquals("60%", bundle.categoryBreakdown[0].percentageDisplay)
        assertEquals("Transport", bundle.categoryBreakdown[1].categoryName)
        assertEquals(40_000L, bundle.categoryBreakdown[1].amountSen)
        assertEquals("40%", bundle.categoryBreakdown[1].percentageDisplay)

        // 4 & 5. Savings analytics: protected balance RM 1,000, period contribution = RM 500 (not double counted)
        assertEquals(100_000L, bundle.savingsAnalytics.overview.totalProtectedSavingsSen)
        assertEquals(50_000L, bundle.savingsAnalytics.periodContributionsSen)
        assertEquals(50_000L, bundle.savingsAnalytics.periodNetSavingsSen)
        // Working backward from RM 1,000 after RM 500 contribution in Nov -> Oct end balance = RM 500
        assertEquals(50_000L, bundle.savingsAnalytics.monthlyTrend[4].endBalanceSen)
        assertEquals(100_000L, bundle.savingsAnalytics.monthlyTrend[5].endBalanceSen)
    }

    @Test
    fun salaryCyclePreviousPeriodAndCustomRangeBoundaries_aggregateAccurately() {
        val wallets = listOf(Wallet(1L, "MAE", WalletType.BANK, 500_000L, false, PrismaticAccentToken.AQUA, 1L, 1L))
        val octCycle = SalaryCycle(
            id = 1L,
            label = "Oct Cycle",
            startDateEpochDay = LocalDate.of(2026, 10, 5).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 11, 4).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            baseSalarySen = 400_000L,
            committedBudgetSen = 0L,
            isActive = false
        )
        val novCycle = SalaryCycle(
            id = 2L,
            label = "Nov Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            endDateEpochDay = Long.MAX_VALUE,
            paydayEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            baseSalarySen = 420_000L,
            committedBudgetSen = 0L,
            isActive = true,
            isOpenEnded = true
        )
        val txs = listOf(
            // Oct Cycle: Oct 5..Nov 4 -> RM 4,000 income, RM 1,500 expense
            FinanceTransaction(1L, 1L, null, TransactionType.INCOME, 400_000L, "Salary", "Oct Sal", "", null, msOn(LocalDate.of(2026, 10, 5)), 1L),
            FinanceTransaction(2L, 1L, null, TransactionType.EXPENSE, 100_000L, "Rent", "Oct Rent", "", null, msOn(LocalDate.of(2026, 10, 20)), 1L),
            FinanceTransaction(3L, 1L, null, TransactionType.EXPENSE, 50_000L, "Food & Dining", "Nov 3 Dinner", "", null, msOn(LocalDate.of(2026, 11, 3)), 1L),
            // Nov Cycle: Nov 5+ -> RM 4,200 income, RM 800 expense
            FinanceTransaction(4L, 1L, null, TransactionType.INCOME, 420_000L, "Salary", "Nov Sal", "", null, msOn(LocalDate.of(2026, 11, 5)), 1L),
            FinanceTransaction(5L, 1L, null, TransactionType.EXPENSE, 80_000L, "Food & Dining", "Nov 6 Groceries", "", null, msOn(LocalDate.of(2026, 11, 6)), 1L)
        )

        val currentBundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.SALARY_CYCLE,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 3,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = wallets,
            transactions = txs,
            categories = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            salaryCycles = listOf(octCycle, novCycle),
            activeCycle = novCycle,
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            savingsMovements = emptyList(),
            budgets = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            today = today,
            zoneId = zoneId
        )
        assertEquals(420_000L, currentBundle.overview.totalIncomeSen)
        assertEquals(80_000L, currentBundle.overview.totalExpenseSen)
        assertEquals(3, currentBundle.monthlyTrends.size) // Sep, Oct, Nov

        val prevBundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.PREVIOUS_PERIOD,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 12,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = wallets,
            transactions = txs,
            categories = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            salaryCycles = listOf(octCycle, novCycle),
            activeCycle = novCycle,
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            savingsMovements = emptyList(),
            budgets = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            today = today,
            zoneId = zoneId
        )
        assertEquals(400_000L, prevBundle.overview.totalIncomeSen)
        assertEquals(150_000L, prevBundle.overview.totalExpenseSen) // Includes Nov 3 expense inside Oct Cycle!
        assertEquals(12, prevBundle.monthlyTrends.size)

        val customBundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.CUSTOM_RANGE,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 6,
            customStartEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            customEndEpochDay = LocalDate.of(2026, 11, 4).toEpochDay(),
            wallets = wallets,
            transactions = txs,
            categories = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            salaryCycles = listOf(octCycle, novCycle),
            activeCycle = novCycle,
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            savingsMovements = emptyList(),
            budgets = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            today = today,
            zoneId = zoneId
        )
        assertEquals(0L, customBundle.overview.totalIncomeSen)
        assertEquals(50_000L, customBundle.overview.totalExpenseSen)
        assertEquals("—", customBundle.overview.savingsRateDisplay) // Zero denominator safe!
    }

    @Test
    fun emptyPeriodAndZeroDenominators_returnSafeZeroesAndNoFabricatedSmartInsights() {
        val bundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.CALENDAR_MONTH,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 6,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = emptyList(),
            transactions = emptyList(),
            categories = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            salaryCycles = emptyList(),
            activeCycle = null,
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            savingsMovements = emptyList(),
            budgets = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            today = today,
            zoneId = zoneId
        )

        assertEquals(0L, bundle.overview.totalIncomeSen)
        assertEquals(0L, bundle.overview.totalExpenseSen)
        assertEquals(0L, bundle.overview.netCashFlowSen)
        assertEquals("—", bundle.overview.savingsRateDisplay)
        assertFalse(bundle.overview.hasIncomeForSavingsRate)
        assertEquals(0L, bundle.overview.avgDailySpendingSen)
        assertEquals(0L, bundle.overview.safeToSpendSen)
        assertEquals(6, bundle.monthlyTrends.size)
        assertTrue(bundle.monthlyTrends.all { it.incomeSen == 0L && it.expenseSen == 0L })
        assertTrue(bundle.categoryBreakdown.isEmpty())
        assertTrue(bundle.smartInsights.isEmpty())
    }

    @Test
    fun budgetsObligationsDebtsAndSmartInsights_reflectActualsAndCycleBoundariesWithoutInflation() {
        val wallets = listOf(Wallet(1L, "MAE", WalletType.BANK, 300_000L, false, PrismaticAccentToken.AQUA, 1L, 1L))
        val categories = listOf(
            Category(1L, "Food & Dining", TransactionType.EXPENSE, "food", PrismaticAccentToken.PEACH, true, false, 1)
        )
        val txs = listOf(
            // Oct 1..16 expenses = RM 1,000
            FinanceTransaction(1L, 1L, null, TransactionType.EXPENSE, 100_000L, "Food & Dining", "Oct Food", "", 1L, msOn(LocalDate.of(2026, 10, 10)), 1L),
            // Nov 1..16 expenses = RM 500 (50% less than Oct 1..16!)
            FinanceTransaction(2L, 1L, null, TransactionType.EXPENSE, 50_000L, "Food & Dining", "Nov Food", "", 1L, msOn(LocalDate.of(2026, 11, 10)), 1L)
        )
        val budgets = listOf(
            Budget(1L, "Nov Food Cap", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-11", null, 1L, "Food & Dining", 40_000L, PrismaticAccentToken.PEACH, "", false, 1L, 1L),
            Budget(2L, "Oct Food Cap", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-10", null, 1L, "Food & Dining", 120_000L, PrismaticAccentToken.LAVENDER, "", false, 1L, 1L)
        )
        val commitments = listOf(
            Commitment(1L, "Rent", 50_000L, LocalDate.of(2026, 11, 25).toEpochDay(), CommitmentStatus.UPCOMING, 1L, true, null, frequency = CommitmentFrequency.MONTHLY),
            Commitment(2L, "Dec Internet", 10_000L, LocalDate.of(2026, 12, 25).toEpochDay(), CommitmentStatus.UPCOMING, 1L, true, null, frequency = CommitmentFrequency.MONTHLY)
        )
        val payLaterPlans = listOf(
            PayLaterPlan(1L, "Desk", "Atome", 60_000L, 0L, 0L, 3, LocalDate.of(2026, 11, 20).toEpochDay(), LocalDate.of(2026, 11, 20).toEpochDay(), PrismaticAccentToken.PEACH, "", false, 1L, 1L)
        )
        val debts = listOf(
            DebtRecord(1L, DebtDirection.I_OWE, "Ali", "Loan", 80_000L, 0L, LocalDate.of(2026, 11, 28).toEpochDay(), PrismaticAccentToken.PEACH, "", false, 1L, 1L, scheduledRepaymentSen = 10_000L),
            DebtRecord(2L, DebtDirection.OWED_TO_ME, "Budi", "Dinner", 25_000L, 0L, null, PrismaticAccentToken.AQUA, "", false, 1L, 1L)
        )

        val bundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.CALENDAR_MONTH,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 6,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = wallets,
            transactions = txs,
            categories = categories,
            commitments = commitments,
            commissions = emptyList(),
            salaryCycles = emptyList(),
            activeCycle = null,
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            savingsMovements = emptyList(),
            budgets = budgets,
            payLaterPlans = payLaterPlans,
            debts = debts,
            today = today,
            zoneId = zoneId
        )

        // Expenses strictly exclude obligations/debts unless settled (still RM 500!)
        assertEquals(50_000L, bundle.overview.totalExpenseSen)
        // Budget performance & overspending & history
        assertEquals(40_000L, bundle.budgetOverview.effectiveLimitSen)
        assertEquals(50_000L, bundle.budgetOverview.effectiveCoveredSpentSen)
        assertEquals(10_000L, bundle.budgetOverview.effectiveOverspentSen)
        assertEquals(1, bundle.budgetOverview.exceededCount)
        assertEquals(1, bundle.budgetHistory.size) // Oct budget history!
        // Obligations overview: Rent RM 500 + Atome #1 RM 200 + Ali scheduled RM 100 = RM 800 cycle reserved; Dec Internet RM 100 in future bills
        assertEquals(80_000L, bundle.obligationsOverview.currentCycleReservedSen)
        assertEquals(10_000L, bundle.obligationsOverview.upcomingFutureBillsSen)
        assertEquals(60_000L, bundle.obligationsOverview.payLaterOutstandingSen)
        assertEquals(20_000L, bundle.obligationsOverview.nextPayLaterInstalmentSen)
        assertEquals(80_000L, bundle.obligationsOverview.debtOwedByMeSen)
        assertEquals(25_000L, bundle.obligationsOverview.debtOwedToMeSen)
        // Safe to Spend = RM 3,000 - RM 800 = RM 2,200
        assertEquals(220_000L, bundle.overview.safeToSpendSen)

        // Smart insights: 50% less vs Oct 1..16, Atome RM 200 upcoming instalment, Food & Dining top category, 1 exceeded budget
        assertTrue(bundle.smartInsights.any { it.id == "spending_delta" && it.title.contains("50% less") })
        assertTrue(bundle.smartInsights.any { it.id == "cycle_paylater" && it.title.contains("RM 200.00") })
        assertTrue(bundle.smartInsights.any { it.id == "budget_exceeded" })
    }
}
