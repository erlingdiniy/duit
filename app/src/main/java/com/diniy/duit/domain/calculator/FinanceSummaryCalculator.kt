package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DashboardSummary
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import java.time.LocalDate

/**
 * Pure domain calculator enforcing all financial safety invariants in integer minor units (sen):
 * 1. Wallet transfers are never counted as income or expense.
 * 2. Pending / unreceived commissions are never counted as available money.
 * 3. Commitments already RESERVED or PAID are never double-counted against Safe to Spend.
 * 4. Protected Savings = Protected Savings Wallets + Goal Allocations in Ordinary Wallets
 *    (zero double-counting when goals are funded from or linked to a Protected Savings Wallet).
 * 5. Safe to Spend = Liquid Spendable Funds (after Protected Savings) - Current-Cycle Unpaid Obligations
 *    (Commitments due in cycle + PayLater instalments due in cycle + Explicitly Scheduled Debt repayments due in cycle).
 *    Future commitments outside the active cycle remain visible in Upcoming without reducing Safe to Spend.
 */
object FinanceSummaryCalculator {

    fun isProtectedSavingsWallet(wallet: Wallet): Boolean =
        wallet.type == WalletType.SAVINGS || wallet.isExcludedFromSafeToSpend

    fun calculate(
        wallets: List<Wallet>,
        transactions: List<FinanceTransaction>,
        commitments: List<Commitment>,
        commissions: List<CommissionRecord>,
        savingsAllocations: List<SavingsAllocation> = emptyList(),
        savingsGoals: List<SavingsGoal> = emptyList(),
        payLaterPlans: List<PayLaterPlan> = emptyList(),
        debts: List<DebtRecord> = emptyList(),
        activeCycle: SalaryCycle? = null,
        today: LocalDate = LocalDate.now()
    ): DashboardSummary {
        val includedWallets = wallets.filter { it.isIncludedInTotalBalance }

        // Total Balance reflects settled wallets included by the user (never pending commissions or receivables).
        val totalBalanceSen = includedWallets.sumOf { it.balanceSen }

        val allocationsByWallet = savingsAllocations
            .filter { it.allocatedSen > 0L }
            .groupBy { it.walletId }
            .mapValues { (_, list) -> list.sumOf { it.allocatedSen } }

        var protectedSavingsSen = 0L
        var liquidSpendableBalanceSen = 0L
        var protectedWalletTotalSen = 0L
        var protectedWalletLinkedGoalSen = 0L

        for (wallet in includedWallets) {
            val allocatedInWallet = (allocationsByWallet[wallet.id] ?: 0L).coerceAtMost(wallet.balanceSen.coerceAtLeast(0L))
            if (isProtectedSavingsWallet(wallet)) {
                protectedSavingsSen += wallet.balanceSen
                protectedWalletTotalSen += wallet.balanceSen
                protectedWalletLinkedGoalSen += allocatedInWallet
            } else {
                protectedSavingsSen += allocatedInWallet
                if (!wallet.isArchived) {
                    liquidSpendableBalanceSen += (wallet.balanceSen - allocatedInWallet)
                }
            }
        }

        // Cycle-aware obligations:
        val activeCommitments = commitments.filter { !it.isArchived }
        val unpaidCommitments = activeCommitments.filter {
            it.status == CommitmentStatus.UPCOMING || it.status == CommitmentStatus.PARTIALLY_PAID
        }
        val inCycleCommitments = unpaidCommitments.filter {
            ObligationCalculator.isDueWithinHorizon(it.dueDateEpochDay, activeCycle, today)
        }
        val futureCommitments = unpaidCommitments.filter {
            !ObligationCalculator.isDueWithinHorizon(it.dueDateEpochDay, activeCycle, today)
        }

        val currentCycleCommitmentsSen = inCycleCommitments.sumOf {
            (it.amountSen - it.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
        val upcomingFutureCommitmentsSen = futureCommitments.sumOf {
            (it.amountSen - it.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
        }

        val reservedCommitmentsSen = activeCommitments
            .filter { it.status == CommitmentStatus.RESERVED }
            .sumOf { it.amountSen }
        val paidCommitmentsSen = activeCommitments
            .filter { it.status == CommitmentStatus.PAID }
            .sumOf { it.amountSen }

        // PayLater: only unpaid instalments due within active cycle
        val payLaterSummaries = payLaterPlans
            .filter { !it.isArchived }
            .map { ObligationCalculator.summarizePayLater(it, activeCycle, today) }
            .filter { !it.isSettled }
        val currentCyclePayLaterSen = payLaterSummaries.sumOf { it.inCycleDueSen }
        val totalPayLaterOutstandingSen = payLaterSummaries.sumOf { it.outstandingSen }
        val nextPayLaterInstalmentSen = payLaterSummaries
            .minByOrNull { it.plan.nextDueDateEpochDay }
            ?.nextInstalmentSen ?: 0L

        // Debts: only explicitly scheduled I_OWE repayments due within active cycle (never full balance automatically)
        val debtSummaries = debts
            .filter { !it.isArchived }
            .map { ObligationCalculator.summarizeDebt(it, activeCycle, today) }
            .filter { !it.isSettled && it.debt.direction == DebtDirection.I_OWE }
        val currentCycleDebtSen = debtSummaries.sumOf { it.inCycleScheduledSen }

        val totalCurrentCycleReservedSen =
            currentCycleCommitmentsSen + currentCyclePayLaterSen + currentCycleDebtSen

        val safeToSpendSen = liquidSpendableBalanceSen - totalCurrentCycleReservedSen

        val pendingCommissionsSen = commissions.sumOf { record ->
            when (record.status) {
                CommissionStatus.PENDING -> record.amountSen.coerceAtLeast(0L)
                CommissionStatus.PARTIALLY_RECEIVED -> {
                    val expected = record.amountSen.coerceAtLeast(0L)
                    if (expected > record.actualGrossSen) expected - record.actualGrossSen else 0L
                }
                CommissionStatus.UNKNOWN,
                CommissionStatus.RECEIVED -> 0L
            }
        }

        // Transfers never count as income or expenses.
        val cycleIncomeSen = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountSen }

        val cycleExpenseSen = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountSen }

        val totalGoalAllocated = savingsAllocations.sumOf { it.allocatedSen }
        val unallocatedProtectedWallet = (protectedWalletTotalSen - protectedWalletLinkedGoalSen).coerceAtLeast(0L)

        val currentCycleCount = inCycleCommitments.size +
            payLaterSummaries.count { it.inCycleDueSen > 0L } +
            debtSummaries.count { it.inCycleScheduledSen > 0L }

        return DashboardSummary(
            totalBalanceSen = totalBalanceSen,
            liquidSpendableBalanceSen = liquidSpendableBalanceSen,
            savingsBalanceSen = protectedSavingsSen,
            upcomingCommitmentsUnreservedSen = totalCurrentCycleReservedSen,
            reservedCommitmentsSen = reservedCommitmentsSen,
            paidCommitmentsSen = paidCommitmentsSen,
            safeToSpendSen = safeToSpendSen,
            pendingCommissionsSen = pendingCommissionsSen,
            cycleIncomeSen = cycleIncomeSen,
            cycleExpenseSen = cycleExpenseSen,
            walletCount = wallets.count { !it.isArchived },
            upcomingCommitmentsCount = currentCycleCount,
            goalAllocatedSen = totalGoalAllocated,
            unallocatedProtectedWalletSen = unallocatedProtectedWallet,
            activeGoalsCount = savingsGoals.count { it.status == SavingsGoalStatus.ACTIVE },
            currentCycleCommitmentsReservedSen = currentCycleCommitmentsSen,
            currentCyclePayLaterReservedSen = currentCyclePayLaterSen,
            currentCycleDebtReservedSen = currentCycleDebtSen,
            upcomingFutureCommitmentsSen = upcomingFutureCommitmentsSen,
            upcomingFutureCommitmentsCount = futureCommitments.size,
            totalPayLaterOutstandingSen = totalPayLaterOutstandingSen,
            nextPayLaterInstalmentSen = nextPayLaterInstalmentSen,
            activeReservePeriodLabel = ObligationCalculator.resolveHorizonLabel(activeCycle, today)
        )
    }
}
