package com.diniy.duit.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.feature.activity.ActivityFilter
import com.diniy.duit.feature.activity.ActivityViewModel
import com.diniy.duit.feature.budgets.PlanningSectionTab
import com.diniy.duit.feature.insights.InsightsSectionTab
import com.diniy.duit.feature.navigation.DuitRoute
import com.diniy.duit.feature.settings.SettingsHubRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase6BScreenRedesignTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var financeRepo: OfflineFirstFinanceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = OfflineFirstFinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun homeRedesign_safeToSpendAndSecondaryBentoRemainDistinct_andFutureCycleBillsAreNotReserved() {
        val today = LocalDate.of(2026, 11, 16)
        val activeCycle = SalaryCycle(
            id = 1L,
            label = "Nov 2026 Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
            baseSalarySen = 450_000L,
            committedBudgetSen = 120_000L,
            isActive = true,
            isOpenEnded = false
        )
        val wallets = listOf(
            Wallet(1L, "Maybank", WalletType.BANK, 300_000L, false, PrismaticAccentToken.AQUA, 1L, 1L),
            Wallet(2L, "Protected Vault", WalletType.SAVINGS, 150_000L, true, PrismaticAccentToken.LAVENDER, 1L, 1L)
        )
        val commitments = listOf(
            Commitment(
                id = 10L, title = "Current Cycle Fibre", amountSen = 15_000L,
                dueDateEpochDay = LocalDate.of(2026, 11, 22).toEpochDay(),
                status = CommitmentStatus.UPCOMING, walletId = 1L, isRecurring = true,
                salaryCycleId = 1L, category = "Utilities", frequency = CommitmentFrequency.MONTHLY
            ),
            Commitment(
                id = 11L, title = "Next Month Insurance", amountSen = 25_000L,
                dueDateEpochDay = LocalDate.of(2026, 12, 18).toEpochDay(),
                status = CommitmentStatus.UPCOMING, walletId = 1L, isRecurring = true,
                salaryCycleId = null, category = "Insurance", frequency = CommitmentFrequency.MONTHLY
            )
        )

        val summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = emptyList(),
            commitments = commitments,
            commissions = emptyList(),
            activeCycle = activeCycle,
            today = today
        )

        // Total Balance = 300k + 150k = 450k
        // Protected Savings = 150k
        // Current Cycle Reserved = 15k (excludes 25k future cycle bill)
        // Safe to Spend = 300k - 15k = 285k
        assertEquals(450_000L, summary.totalBalanceSen)
        assertEquals(150_000L, summary.savingsBalanceSen)
        assertEquals(15_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(25_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(285_000L, summary.safeToSpendSen)
        assertNotEquals(summary.totalBalanceSen, summary.safeToSpendSen)
    }

    @Test
    fun activityRedesign_searchFilterAndDateGroupingWorkAcrossGroupedRows() = runBlocking(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val day1Ms = LocalDate.of(2026, 11, 15).atTime(14, 30).atZone(zone).toInstant().toEpochMilli()
        val day2Ms = LocalDate.of(2026, 11, 16).atTime(9, 15).atZone(zone).toInstant().toEpochMilli()

        db.backupDao().insertWallets(
            listOf(
                WalletEntity(
                    id = 1L, name = "Maybank", type = WalletType.BANK, balanceSen = 200_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L, updatedAtEpochMs = 1L, isIncludedInTotalBalance = true, isArchived = false
                ),
                WalletEntity(
                    id = 2L, name = "Touch 'n Go", type = WalletType.EWALLET, balanceSen = 30_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.SKY_BLUE,
                    createdAtEpochMs = 1L, updatedAtEpochMs = 1L, isIncludedInTotalBalance = true, isArchived = false
                )
            )
        )
        db.backupDao().insertTransactions(
            listOf(
                TransactionEntity(
                    id = 101L, walletId = 1L, targetWalletId = null, type = TransactionType.EXPENSE,
                    amountSen = 2_850L, category = "Food & Dining", title = "Nasi Kandar",
                    note = "Lunch with team", commitmentId = null,
                    occurredAtEpochMs = day1Ms, createdAtEpochMs = day1Ms
                ),
                TransactionEntity(
                    id = 102L, walletId = 1L, targetWalletId = null, type = TransactionType.INCOME,
                    amountSen = 320_000L, category = "Salary", title = "Monthly Salary",
                    note = "Nov payroll", commitmentId = null,
                    occurredAtEpochMs = day2Ms, createdAtEpochMs = day2Ms
                ),
                TransactionEntity(
                    id = 103L, walletId = 1L, targetWalletId = 2L, type = TransactionType.TRANSFER,
                    amountSen = 10_000L, category = "Transfer", title = "Topup eWallet",
                    note = "Toll reload", commitmentId = null,
                    occurredAtEpochMs = day2Ms + 1_000L, createdAtEpochMs = day2Ms + 1_000L
                )
            )
        )

        val store = ViewModelStore()
        try {
            val vm = ActivityViewModel.factory(
                transactionRepository = financeRepo,
                walletRepository = financeRepo,
                categoryRepository = financeRepo,
                planningRepository = financeRepo,
                previewModeController = PreviewModeController()
            ).create(ActivityViewModel::class.java)

            val allState = withTimeout(5_000) {
                vm.uiState.first { !it.isLoading && it.dateSections.size == 2 }
            }
            assertEquals(2, allState.dateSections.size)
            assertEquals(3, allState.dateSections.sumOf { it.transactions.size })

            // Filter by EXPENSE
            vm.selectFilter(ActivityFilter.EXPENSE)
            val expenseState = withTimeout(5_000) {
                vm.uiState.first { it.selectedFilter == ActivityFilter.EXPENSE && it.dateSections.size == 1 }
            }
            assertEquals("Nasi Kandar", expenseState.dateSections.first().transactions.first().title)

            // Filter ALL + Search by target wallet name "Touch 'n Go"
            vm.selectFilter(ActivityFilter.ALL)
            vm.updateSearchQuery("Touch")
            val searchState = withTimeout(5_000) {
                vm.uiState.first { it.searchQuery == "Touch" && it.dateSections.size == 1 }
            }
            assertEquals(TransactionType.TRANSFER, searchState.dateSections.first().transactions.first().type)
        } finally {
            store.clear()
        }
    }

    @Test
    fun planningAndInsightsTabs_andObligationSeparation_preserveAllDomainRules() {
        // Planning maintains exactly 3 primary tabs: Goals | Budgets | Commitments
        assertEquals(
            listOf(PlanningSectionTab.GOALS, PlanningSectionTab.BUDGETS, PlanningSectionTab.COMMITMENTS),
            PlanningSectionTab.entries
        )

        // Insights maintains the 4 organized sections: Overview | Spending | Income | Commitments
        assertEquals(
            listOf(
                InsightsSectionTab.OVERVIEW,
                InsightsSectionTab.SPENDING,
                InsightsSectionTab.INCOME,
                InsightsSectionTab.COMMITMENTS
            ),
            InsightsSectionTab.entries
        )

        // Navigation & Settings Hub routes remain intact
        assertEquals(listOf("home", "activity", "planning", "insights"), listOf(
            DuitRoute.HOME, DuitRoute.ACTIVITY, DuitRoute.PLANNING, DuitRoute.INSIGHTS
        ))
        assertEquals(8, SettingsHubRoute.entries.size)

        // Obligations overview keeps Recurring Commitments, PayLater instalments, and Debts distinct
        val today = LocalDate.of(2026, 11, 16)
        val activeCycle = SalaryCycle(
            id = 1L, label = "Nov Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
            baseSalarySen = 400_000L, committedBudgetSen = 100_000L,
            isActive = true, isOpenEnded = false
        )
        val payLater = PayLaterPlan(
            id = 1L, title = "Laptop", providerName = "SPayLater",
            purchaseAmountSen = 300_000L, feeOrInterestSen = 0L, paidSen = 100_000L,
            instalmentCount = 3,
            firstDueDateEpochDay = LocalDate.of(2026, 10, 15).toEpochDay(),
            nextDueDateEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
            accentToken = PrismaticAccentToken.PEACH,
            note = "", isArchived = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L
        )
        val debt = DebtRecord(
            id = 2L, direction = DebtDirection.I_OWE, counterpartyName = "Ahmad",
            title = "Car repair", originalAmountSen = 80_000L, paidSen = 20_000L,
            dueDateEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
            scheduledRepaymentSen = 15_000L, accentToken = PrismaticAccentToken.SKY_BLUE,
            note = "", isArchived = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L
        )

        val overview = ObligationCalculator.calculateOverview(
            commitments = emptyList(),
            payLaterPlans = listOf(payLater),
            debts = listOf(debt),
            activeCycle = activeCycle,
            today = today
        )
        // PayLater reserves only the current-cycle instalment (100k), not the full 200k outstanding
        // Debt reserves only the scheduled repayment (15k), not the full 60k outstanding
        assertEquals(200_000L, overview.payLaterOutstandingSen)
        assertEquals(100_000L, overview.nextPayLaterInstalmentSen)
        assertEquals(60_000L, overview.debtOwedByMeSen)
        assertEquals(15_000L, overview.debtCycleReservedSen)
        assertEquals(115_000L, overview.currentCycleReservedSen)

        // Privacy masking hides all balances and amounts when enabled
        val maskedSettings = PrivacySettings(hideSensitiveBalances = true, privacyModeEnabled = true)
        assertEquals(PrivacyMask.MASKED_MYR, PrivacyMask.formatBalance(overview.currentCycleReservedSen, maskedSettings))
        assertEquals(PrivacyMask.MASKED_MYR, PrivacyMask.formatAmount(overview.nextPayLaterInstalmentSen, maskedSettings))
        assertFalse(
            PrivacyMask.accessibleBalanceDescription("Cycle Reserved", overview.currentCycleReservedSen, maskedSettings)
                .contains("1,150")
        )
    }
}
