package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.InsightsAnalyticsCalculator
import com.diniy.duit.domain.calculator.InsightsPeriodPreset
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4DInsightsRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var repo: OfflineFirstFinanceRepository
    private val zoneId: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OfflineFirstFinanceRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun endToEndRoomLedgerAndAnalytics_preservesPhase1To4CInvariantsWithAccurateInsights(): Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        repo.ensurePhase3DefaultsSeeded()
        val now = System.currentTimeMillis()
        val nov5 = LocalDate.of(2026, 11, 5)
        val nov10 = LocalDate.of(2026, 11, 10)
        val nov16 = LocalDate.of(2026, 11, 16)
        val nov5Ms = nov5.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nov10Ms = nov10.atTime(12, 30).atZone(zoneId).toInstant().toEpochMilli()

        // 1. Wallets: MAE RM 1,000 + Vault RM 500
        val maeId = repo.upsertWallet(Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now))
        val vaultId = repo.upsertWallet(Wallet(0L, "Tabung", WalletType.SAVINGS, 50_000L, true, PrismaticAccentToken.SKY_BLUE, now, now))

        // 2. Record salary income & start active cycle (RM 3,000 salary -> MAE becomes RM 4,000)
        repo.recordSalaryTransactionAndStartCycle(
            transaction = FinanceTransaction(0L, maeId, null, TransactionType.INCOME, 300_000L, "Salary", "Nov Salary", "", null, nov5Ms, now),
            cycleLabel = "Nov 2026 Cycle"
        )

        // 3. Expense & Transfer & Commission payout
        val foodCat = repo.observeCategories().first().first { it.name == "Food & Dining" }
        repo.recordTransaction(
            FinanceTransaction(0L, maeId, null, TransactionType.EXPENSE, 45_000L, foodCat.name, "Dinner", "", null, nov10Ms, now, foodCat.id)
        )

        // Pending commission RM 600 (excluded from income/wallets) + Received commission RM 200 (added to MAE)
        repo.upsertCommission(
            CommissionRecord(0L, "Pending Tier", "Company", 60_000L, CommissionStatus.PENDING, nov10.toEpochDay(), null, null, "", yearMonth = "2026-11")
        )
        val commPaidId = repo.upsertCommission(
            CommissionRecord(0L, "Oct Commission", "Agency", 20_000L, CommissionStatus.PENDING, nov10.toEpochDay(), null, null, "", yearMonth = "2026-11")
        )
        repo.recordCommissionPayment(
            payment = CommissionPayment(
                id = 0L,
                commissionId = commPaidId,
                sourceId = null,
                sourceName = "Agency",
                walletId = maeId,
                linkedTransactionId = null,
                receivedAtEpochMs = nov10Ms,
                actualGrossSen = 22_000L,
                epfDeductionSen = 2_000L,
                socsoDeductionSen = null,
                eisDeductionSen = null,
                actualNetSen = 20_000L,
                notes = "Payslip net",
                submissionId = "p4d-comm-1"
            ),
            markAsFullyReceived = true
        )
        repo.upsertDailySales(DailySalesRecord(0L, nov10.toEpochDay(), 250_000L, 235_000L, 12, "", now, 30_000L))

        // 4A. Goal + transfer RM 300 from MAE -> Tabung vault (MAE: 4000 - 450 + 200 - 300 = 3,450; Vault: 500 + 300 = 800)
        val goalId = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Japan", 150_000L, null, "", "travel", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, now, now)
        )
        repo.transferToProtectedWalletAndAllocate(goalId, maeId, vaultId, 30_000L, "Save Nov", "p4d-save-1")

        // 4B. Budget (Food RM 400 -> overspent by RM 50)
        val activeCycle = repo.observeActiveCycle().first()!!
        repo.upsertBudget(
            Budget(0L, "Food Cap", BudgetScope.CATEGORY, BudgetPeriodType.SALARY_CYCLE, "2026-11", activeCycle.id, foodCat.id, foodCat.name, 40_000L, PrismaticAccentToken.PEACH, "", false, now, now)
        )

        // 4C. Commitment (Nov RM 100 unpaid) + PayLater (3x RM 200 starting Nov 20) + Debt (I owe RM 500, no schedule)
        repo.upsertCommitmentDetail(
            Commitment(0L, "Maxis", 10_000L, LocalDate.of(2026, 11, 25).toEpochDay(), CommitmentStatus.UPCOMING, maeId, true, activeCycle.id, frequency = CommitmentFrequency.MONTHLY)
        )
        repo.upsertPayLaterPlan(
            PayLaterPlan(0L, "Monitor", "SPayLater", 60_000L, 0L, 0L, 3, LocalDate.of(2026, 11, 20).toEpochDay(), LocalDate.of(2026, 11, 20).toEpochDay(), PrismaticAccentToken.PEACH, "", false, now, now)
        )
        repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Aiman", "Ticket", 50_000L, 0L, null, PrismaticAccentToken.PEACH, "", false, now, now)
        )

        val wallets = repo.observeWallets().first()
        val mae = wallets.first { it.id == maeId }
        val vault = wallets.first { it.id == vaultId }
        assertEquals(345_000L, mae.balanceSen)
        assertEquals(80_000L, vault.balanceSen)

        val bundle = InsightsAnalyticsCalculator.calculate(
            preset = InsightsPeriodPreset.SALARY_CYCLE,
            selectedMonth = YearMonth.of(2026, 11),
            trendHorizonMonths = 6,
            customStartEpochDay = null,
            customEndEpochDay = null,
            wallets = wallets,
            transactions = repo.observeTransactions().first(),
            categories = repo.observeCategories().first(),
            commitments = repo.observeCommitments().first(),
            commissions = repo.observeCommissions().first(),
            salaryCycles = repo.observeSalaryCycles().first(),
            activeCycle = activeCycle,
            savingsGoals = repo.observeSavingsGoals().first(),
            savingsAllocations = repo.observeSavingsAllocations().first(),
            savingsMovements = repo.observeSavingsMovements().first(),
            budgets = repo.observeBudgets().first(),
            payLaterPlans = repo.observePayLaterPlans().first(),
            debts = repo.observeDebts().first(),
            today = nov16,
            zoneId = zoneId
        )

        // Income = RM 3,000 salary + RM 200 received net commission = RM 3,200 (RM 600 pending commission & RM 300 transfer EXCLUDED!)
        assertEquals(320_000L, bundle.overview.totalIncomeSen)
        // Expense = RM 450 Food only (RM 300 transfer, RM 500 debt, RM 600 PayLater, RM 100 commitment EXCLUDED!)
        assertEquals(45_000L, bundle.overview.totalExpenseSen)
        assertEquals(275_000L, bundle.overview.netCashFlowSen)
        // Safe to Spend = MAE RM 3,450 - (Maxis RM 100 + SPayLater cycle #1 RM 200) = RM 3,150
        assertEquals(315_000L, bundle.overview.safeToSpendSen)
        assertEquals(80_000L, bundle.savingsAnalytics.overview.totalProtectedSavingsSen)
        assertEquals(30_000L, bundle.savingsAnalytics.overview.totalAllocatedToGoalsSen)
        assertEquals(5_000L, bundle.budgetOverview.effectiveOverspentSen)
        assertEquals(30_000L, bundle.obligationsOverview.currentCycleReservedSen)
        assertEquals(60_000L, bundle.dashboardSummary.pendingCommissionsSen)
        assertTrue(bundle.smartInsights.isNotEmpty())
    }
}
