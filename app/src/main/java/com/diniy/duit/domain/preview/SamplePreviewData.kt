package com.diniy.duit.domain.preview

import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DashboardSummary
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Clearly labeled sample data strictly for Compose UI previews and an explicit
 * in-memory Preview Mode toggle. Never persisted to Room as real user data.
 */
object SamplePreviewData {

    private val todayEpochDay: Long = LocalDate.now().toEpochDay()
    private val nowEpochMs: Long = System.currentTimeMillis()

    val wallets: List<Wallet> = listOf(
        Wallet(
            id = -1L,
            name = "Maybank MAE (Sample)",
            type = WalletType.BANK,
            balanceSen = 428_500L, // RM 4,285.00
            isExcludedFromSafeToSpend = false,
            accentToken = PrismaticAccentToken.AQUA,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        Wallet(
            id = -2L,
            name = "TNG eWallet (Sample)",
            type = WalletType.EWALLET,
            balanceSen = 36_450L, // RM 364.50
            isExcludedFromSafeToSpend = false,
            accentToken = PrismaticAccentToken.LAVENDER,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        Wallet(
            id = -3L,
            name = "ASB & Tabung (Sample)",
            type = WalletType.SAVINGS,
            balanceSen = 1_250_000L, // RM 12,500.00
            isExcludedFromSafeToSpend = true,
            accentToken = PrismaticAccentToken.SKY_BLUE,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val commitments: List<Commitment> = listOf(
        Commitment(
            id = -11L,
            title = "Home Rent (Sample)",
            amountSen = 135_000L, // RM 1,350.00
            dueDateEpochDay = todayEpochDay + 4L,
            status = CommitmentStatus.UPCOMING,
            walletId = -1L,
            isRecurring = true,
            salaryCycleId = -1L
        ),
        Commitment(
            id = -12L,
            title = "Unifi Fibre (Sample)",
            amountSen = 13_900L, // RM 139.00
            dueDateEpochDay = todayEpochDay + 8L,
            status = CommitmentStatus.UPCOMING,
            walletId = -1L,
            isRecurring = true,
            salaryCycleId = -1L
        ),
        Commitment(
            id = -13L,
            title = "Car Insurance Reserve (Sample)",
            amountSen = 45_000L, // RM 450.00 (already reserved)
            dueDateEpochDay = todayEpochDay + 14L,
            status = CommitmentStatus.RESERVED,
            walletId = -3L,
            isRecurring = false,
            salaryCycleId = -1L
        )
    )

    val commissions: List<CommissionRecord> = listOf(
        CommissionRecord(
            id = -21L,
            title = "November Tier Payout (Sample)",
            sourceLabel = "Pending settlement",
            amountSen = 84_000L, // RM 840.00 (excluded from Safe to Spend)
            status = CommissionStatus.PENDING,
            earnedDateEpochDay = todayEpochDay - 2L,
            payoutDateEpochDay = todayEpochDay + 12L,
            linkedTransactionId = null,
            notes = "Preview only — pending commissions are excluded from available balance"
        )
    )

    val transactions: List<FinanceTransaction> = listOf(
        FinanceTransaction(
            id = -31L,
            walletId = -1L,
            targetWalletId = null,
            type = TransactionType.INCOME,
            amountSen = 520_000L, // RM 5,200.00
            category = "Salary",
            title = "Monthly Salary (Sample)",
            note = "Preview sample income",
            commitmentId = null,
            occurredAtEpochMs = nowEpochMs - 3600_000L * 48,
            createdAtEpochMs = nowEpochMs - 3600_000L * 48
        ),
        FinanceTransaction(
            id = -32L,
            walletId = -1L,
            targetWalletId = -2L,
            type = TransactionType.TRANSFER,
            amountSen = 30_000L, // RM 300.00 (never counted as expense or income)
            category = "Wallet Transfer",
            title = "Top up TNG eWallet (Sample)",
            note = "Internal transfer",
            commitmentId = null,
            occurredAtEpochMs = nowEpochMs - 3600_000L * 20,
            createdAtEpochMs = nowEpochMs - 3600_000L * 20
        ),
        FinanceTransaction(
            id = -33L,
            walletId = -2L,
            targetWalletId = null,
            type = TransactionType.EXPENSE,
            amountSen = 2_480L, // RM 24.80
            category = "Food & Dining",
            title = "Kopitiam Lunch (Sample)",
            note = "Preview sample expense",
            commitmentId = null,
            occurredAtEpochMs = nowEpochMs - 3600_000L * 4,
            createdAtEpochMs = nowEpochMs - 3600_000L * 4
        )
    )

    val activeCycle: SalaryCycle = SalaryCycle(
        id = -1L,
        label = "Nov Cycle (Sample)",
        startDateEpochDay = todayEpochDay - 10L,
        endDateEpochDay = todayEpochDay + 20L,
        paydayEpochDay = todayEpochDay + 20L,
        baseSalarySen = 520_000L,
        committedBudgetSen = 193_900L,
        isActive = true
    )

    val dailySales: List<DailySalesRecord> = listOf(
        DailySalesRecord(
            id = -41L,
            recordDateEpochDay = todayEpochDay - 1L,
            grossSalesSen = 315_000L,
            netSalesSen = 298_000L,
            unitsSold = 14,
            notes = "Sample preview day",
            createdAtEpochMs = nowEpochMs - 86_400_000L,
            grossProfitSen = 25_200L
        )
    )

    val savingsGoals: List<SavingsGoal> = listOf(
        SavingsGoal(
            id = -51L,
            name = "Emergency Buffer (Sample)",
            targetAmountSen = 1_500_000L,
            targetDateEpochDay = todayEpochDay + 180L,
            description = "6-month essential expenses",
            iconKey = "emergency",
            accentToken = PrismaticAccentToken.SKY_BLUE,
            status = SavingsGoalStatus.ACTIVE,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        SavingsGoal(
            id = -52L,
            name = "Roadtax & Insurance (Sample)",
            targetAmountSen = 450_000L,
            targetDateEpochDay = todayEpochDay + 90L,
            description = "Annual vehicle renewal",
            iconKey = "car",
            accentToken = PrismaticAccentToken.AQUA,
            status = SavingsGoalStatus.ACTIVE,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val savingsAllocations: List<SavingsAllocation> = listOf(
        SavingsAllocation(
            id = -61L,
            goalId = -51L,
            walletId = -3L, // Linked inside protected savings wallet (no double count!)
            allocatedSen = 900_000L,
            updatedAtEpochMs = nowEpochMs
        ),
        SavingsAllocation(
            id = -62L,
            goalId = -52L,
            walletId = -3L,
            allocatedSen = 350_000L,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val budgets: List<Budget> = listOf(
        Budget(
            id = -71L,
            name = "Monthly Overall (Sample)",
            scope = BudgetScope.OVERALL,
            periodType = BudgetPeriodType.MONTHLY,
            yearMonth = YearMonth.now().toString(),
            salaryCycleId = null,
            categoryId = null,
            categoryName = "",
            limitSen = 250_000L,
            accentToken = PrismaticAccentToken.LAVENDER,
            note = "Sample overall monthly cap",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        Budget(
            id = -72L,
            name = "Food & Dining (Sample)",
            scope = BudgetScope.CATEGORY,
            periodType = BudgetPeriodType.MONTHLY,
            yearMonth = YearMonth.now().toString(),
            salaryCycleId = null,
            categoryId = null,
            categoryName = "Food & Dining",
            limitSen = 60_000L,
            accentToken = PrismaticAccentToken.PEACH,
            note = "",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        Budget(
            id = -73L,
            name = "Transport (Sample)",
            scope = BudgetScope.CATEGORY,
            periodType = BudgetPeriodType.MONTHLY,
            yearMonth = YearMonth.now().toString(),
            salaryCycleId = null,
            categoryId = null,
            categoryName = "Transport",
            limitSen = 35_000L,
            accentToken = PrismaticAccentToken.SKY_BLUE,
            note = "",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val payLaterPlans: List<PayLaterPlan> = listOf(
        PayLaterPlan(
            id = -81L,
            title = "Ergonomic Chair (Sample)",
            providerName = "SPayLater",
            purchaseAmountSen = 60_000L,
            feeOrInterestSen = 1_500L,
            paidSen = 20_500L,
            instalmentCount = 3,
            firstDueDateEpochDay = todayEpochDay - 20L,
            nextDueDateEpochDay = todayEpochDay + 10L,
            accentToken = PrismaticAccentToken.PEACH,
            note = "3-month plan with fee",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val debts: List<DebtRecord> = listOf(
        DebtRecord(
            id = -91L,
            direction = DebtDirection.OWED_TO_ME,
            counterpartyName = "Aiman (Sample)",
            title = "Group dinner split",
            originalAmountSen = 12_000L,
            paidSen = 5_000L,
            dueDateEpochDay = todayEpochDay + 5L,
            accentToken = PrismaticAccentToken.AQUA,
            note = "",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        ),
        DebtRecord(
            id = -92L,
            direction = DebtDirection.I_OWE,
            counterpartyName = "Farah (Sample)",
            title = "Concert ticket",
            originalAmountSen = 25_000L,
            paidSen = 0L,
            dueDateEpochDay = todayEpochDay + 12L,
            accentToken = PrismaticAccentToken.LAVENDER,
            note = "",
            isArchived = false,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
    )

    val summary: DashboardSummary = FinanceSummaryCalculator.calculate(
        wallets = wallets,
        transactions = transactions,
        commitments = commitments,
        commissions = commissions,
        savingsAllocations = savingsAllocations,
        savingsGoals = savingsGoals,
        payLaterPlans = payLaterPlans,
        debts = debts,
        activeCycle = activeCycle
    )
}
