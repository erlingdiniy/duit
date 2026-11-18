package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 9A regression tests for Safe to Spend invariants. Deterministic fixtures only
 * (fixed dates, synthetic wallets); never real user data.
 */
class Phase9AFinancialInvariantsTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private val spending = Wallet(1L, "Spending", WalletType.BANK, 300_000L, false, PrismaticAccentToken.AQUA, 1L, 1L)
    private val vault = Wallet(2L, "Vault", WalletType.SAVINGS, 100_000L, true, PrismaticAccentToken.PEACH, 1L, 1L)

    private fun bill(id: Long, dueEpochDay: Long, amountSen: Long = 20_000L, status: CommitmentStatus = CommitmentStatus.UPCOMING) =
        Commitment(
            id = id, title = "Bill $id", amountSen = amountSen, dueDateEpochDay = dueEpochDay,
            status = status, walletId = 1L, isRecurring = true, salaryCycleId = null,
            category = "Utilities", frequency = CommitmentFrequency.MONTHLY
        )

    private val novCycle = SalaryCycle(
        id = 1L, label = "Nov Cycle",
        startDateEpochDay = day(2026, 11, 5), endDateEpochDay = day(2026, 12, 4),
        paydayEpochDay = day(2026, 11, 5), baseSalarySen = 400_000L, committedBudgetSen = 0L,
        isActive = true, isOpenEnded = false
    )

    @Test
    fun staleCycle_afterItsEnd_fallsBackToCalendarMonth_soCurrentBillsAreReserved() {
        // BUG-9A-001: the active cycle ended 4 Dec but no new cycle was started.
        // On 10 Dec a bill due 15 Dec is in the current period and must be reserved.
        val today = LocalDate.of(2026, 12, 10)
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(spending),
            transactions = emptyList(),
            commitments = listOf(bill(1L, day(2026, 12, 15))),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = today
        )
        assertEquals(20_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(280_000L, summary.safeToSpendSen)
        assertEquals("Dec 2026", summary.activeReservePeriodLabel)
    }

    @Test
    fun activeCycle_stillRunning_keepsCycleBoundary_andFutureBillsAreNotDeducted() {
        val today = LocalDate.of(2026, 11, 20)
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(spending),
            transactions = emptyList(),
            commitments = listOf(bill(1L, day(2026, 12, 3)), bill(2L, day(2026, 12, 18))),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = today
        )
        assertEquals(20_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(20_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(280_000L, summary.safeToSpendSen)
        assertEquals("Nov Cycle", summary.activeReservePeriodLabel)
    }

    @Test
    fun noCycle_usesCalendarMonth_andOverdueBillsStayReserved() {
        val today = LocalDate.of(2027, 2, 10)
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(spending),
            transactions = emptyList(),
            commitments = listOf(
                bill(1L, day(2027, 1, 20)), // overdue from last month
                bill(2L, day(2027, 2, 28)), // due this month
                bill(3L, day(2027, 3, 1))   // next month
            ),
            commissions = emptyList(),
            activeCycle = null,
            today = today
        )
        assertEquals(40_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(20_000L, summary.upcomingFutureCommitmentsSen)
    }

    @Test
    fun protectedSavings_transfers_andPendingCommissions_neverInflateSafeToSpend() {
        val transfer = FinanceTransaction(
            id = 1L, walletId = 1L, targetWalletId = 2L, type = TransactionType.TRANSFER,
            amountSen = 50_000L, category = "Transfer", title = "Save", note = "",
            commitmentId = null, occurredAtEpochMs = 0L, createdAtEpochMs = 0L
        )
        val pending = CommissionRecord(
            id = 1L, title = "Nov sales", sourceLabel = "Store", amountSen = 90_000L,
            status = CommissionStatus.PENDING, earnedDateEpochDay = day(2026, 11, 10),
            payoutDateEpochDay = null, linkedTransactionId = null, notes = ""
        )
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(spending, vault),
            transactions = listOf(transfer),
            commitments = emptyList(),
            commissions = listOf(pending),
            activeCycle = null,
            today = LocalDate.of(2026, 11, 16)
        )
        assertEquals(400_000L, summary.totalBalanceSen)
        assertEquals(100_000L, summary.savingsBalanceSen)
        assertEquals(300_000L, summary.safeToSpendSen)
        assertEquals(0L, summary.cycleIncomeSen)
        assertEquals(0L, summary.cycleExpenseSen)
        assertEquals(90_000L, summary.pendingCommissionsSen)
    }

    @Test
    fun negativeAndLargeBalances_stayExactInMinorUnits() {
        val overdrawn = spending.copy(id = 3L, balanceSen = -5_000L)
        val huge = spending.copy(id = 4L, balanceSen = 900_000_000_000_00L) // RM 900 billion
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(overdrawn, huge),
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            activeCycle = null,
            today = LocalDate.of(2026, 11, 16)
        )
        assertEquals(900_000_000_000_00L - 5_000L, summary.totalBalanceSen)
        assertEquals(900_000_000_000_00L - 5_000L, summary.safeToSpendSen)
    }

    @Test
    fun paidAndReservedBills_areNeverDeductedAgain() {
        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(spending),
            transactions = emptyList(),
            commitments = listOf(
                bill(1L, day(2026, 11, 20), status = CommitmentStatus.PAID),
                bill(2L, day(2026, 11, 21), status = CommitmentStatus.RESERVED)
            ),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = LocalDate.of(2026, 11, 16)
        )
        assertEquals(0L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(300_000L, summary.safeToSpendSen)
    }
}
