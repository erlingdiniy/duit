package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceSummaryCalculatorTest {

    private fun wallet(
        id: Long,
        type: WalletType,
        balanceSen: Long,
        excluded: Boolean = false
    ) = Wallet(
        id = id,
        name = "W$id",
        type = type,
        balanceSen = balanceSen,
        isExcludedFromSafeToSpend = excluded,
        accentToken = PrismaticAccentToken.AQUA,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L
    )

    @Test
    fun `wallet transfers are never counted as income or expense`() {
        val transactions = listOf(
            FinanceTransaction(1L, 1L, null, TransactionType.INCOME, 500_000L, "Salary", "Pay", "", null, 1L, 1L),
            FinanceTransaction(2L, 1L, 2L, TransactionType.TRANSFER, 200_000L, "Transfer", "Move", "", null, 2L, 2L),
            FinanceTransaction(3L, 2L, null, TransactionType.EXPENSE, 45_000L, "Food", "Dinner", "", null, 3L, 3L)
        )

        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(wallet(1L, WalletType.BANK, 300_000L), wallet(2L, WalletType.EWALLET, 155_000L)),
            transactions = transactions,
            commitments = emptyList(),
            commissions = emptyList()
        )

        assertEquals(500_000L, summary.cycleIncomeSen)
        assertEquals(45_000L, summary.cycleExpenseSen)
    }

    @Test
    fun `pending commissions are never counted as available money or safe to spend`() {
        val commissions = listOf(
            CommissionRecord(1L, "Pending Deal", "Company", 180_000L, CommissionStatus.PENDING, 100L, null, null, ""),
            CommissionRecord(2L, "Unknown Payout", "Lenovo", 60_000L, CommissionStatus.UNKNOWN, 100L, null, null, "")
        )

        val summary = FinanceSummaryCalculator.calculate(
            wallets = listOf(wallet(1L, WalletType.BANK, 250_000L)),
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = commissions
        )

        assertEquals(250_000L, summary.totalBalanceSen)
        assertEquals(250_000L, summary.safeToSpendSen)
        assertEquals(180_000L, summary.pendingCommissionsSen)
    }

    @Test
    fun `reserved and paid commitments are not double counted against safe to spend`() {
        val wallets = listOf(
            wallet(1L, WalletType.BANK, 400_000L),
            wallet(2L, WalletType.SAVINGS, 1_000_000L, excluded = true)
        )
        val commitments = listOf(
            Commitment(1L, "Rent", 120_000L, 10L, CommitmentStatus.UPCOMING, 1L, true, null),
            Commitment(2L, "Reserved Tax", 80_000L, 15L, CommitmentStatus.RESERVED, 2L, false, null),
            Commitment(3L, "Paid Internet", 13_900L, 2L, CommitmentStatus.PAID, 1L, true, null)
        )

        val summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = emptyList(),
            commitments = commitments,
            commissions = emptyList()
        )

        assertEquals(1_400_000L, summary.totalBalanceSen)
        assertEquals(1_000_000L, summary.savingsBalanceSen)
        assertEquals(400_000L, summary.liquidSpendableBalanceSen)
        assertEquals(120_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(80_000L, summary.reservedCommitmentsSen)
        assertEquals(13_900L, summary.paidCommitmentsSen)
        // 400,000 liquid - 120,000 upcoming = 280,000 (reserved & paid are NOT deducted again)
        assertEquals(280_000L, summary.safeToSpendSen)
    }
}
