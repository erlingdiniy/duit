package com.diniy.duit.widgets

import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.calculator.SavingsCalculator
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.security.SecurityConfigSnapshot
import java.time.LocalDate

enum class WidgetObligationKind(val shortTag: String) {
    COMMITMENT("Bill"),
    PAYLATER_INSTALMENT("PayLater"),
    DEBT_REPAYMENT("Debt")
}

data class WidgetObligationItem(
    val id: Long,
    val kind: WidgetObligationKind,
    val title: String,
    val dueDateEpochDay: Long,
    val dueLabel: String,
    val amountSen: Long,
    val statusLabel: String,
    val isOverdue: Boolean,
    val isCurrentCycle: Boolean
)

data class WidgetSavingsGoalItem(
    val id: Long,
    val name: String,
    val savedSen: Long,
    val targetSen: Long,
    val remainingSen: Long,
    val progressFraction: Float,
    val progressPercentDisplay: String,
    val targetDateLabel: String?
)

data class BalanceWidgetData(
    val safeToSpendSen: Long,
    val totalBalanceSen: Long,
    val protectedSavingsSen: Long,
    val currentCycleReservedSen: Long,
    val upcomingFutureCommitmentsSen: Long,
    val cycleIncomeSen: Long,
    val cycleExpenseSen: Long,
    val walletCount: Int,
    val cycleLabel: String,
    val lastUpdatedLabel: String,
    val isEmptyLedger: Boolean
)

data class UpcomingPaymentsWidgetData(
    val items: List<WidgetObligationItem>,
    val currentCycleReservedSen: Long,
    val upcomingFutureBillsSen: Long,
    val overdueCount: Int,
    val overdueSen: Long,
    val reserveHorizonLabel: String
)

data class SavingsGoalsWidgetData(
    val goals: List<WidgetSavingsGoalItem>,
    val totalProtectedSavingsSen: Long,
    val totalAllocatedToGoalsSen: Long,
    val unallocatedProtectedWalletSen: Long,
    val overallProgressPercentDisplay: String,
    val activeGoalsCount: Int
)

data class QuickAddWidgetData(
    val hasActiveWallets: Boolean,
    val isAppLockEnabled: Boolean
)

/**
 * Unified snapshot rendered by all four duit. widgets.
 *
 * Security & Privacy Policy:
 * When [hideSensitiveBalances], [privacyModeEnabled], OR [isAppLockEnabled] is active,
 * monetary values and exact goal percentages are masked in widget text and accessibility labels
 * so locked/protected balances are never leaked onto the Android home screen.
 */
data class DuitWidgetsSnapshot(
    val themeMode: ThemeMode,
    val isAppLockEnabled: Boolean,
    val shouldMaskAmounts: Boolean,
    val balance: BalanceWidgetData,
    val upcomingPayments: UpcomingPaymentsWidgetData,
    val savingsGoals: SavingsGoalsWidgetData,
    val quickAdd: QuickAddWidgetData
) {
    fun formatMoney(amountSen: Long, includePlusSign: Boolean = false): String =
        if (shouldMaskAmounts) PrivacyMask.MASKED_MYR
        else MyrFormatter.format(amountSen, includePlusSign = includePlusSign)

    fun formatPercent(rawPercent: String): String =
        if (shouldMaskAmounts) "••%" else rawPercent

    fun accessibleMoney(amountSen: Long): String =
        if (shouldMaskAmounts) "hidden for privacy" else MyrFormatter.format(amountSen)
}

object WidgetSnapshotCalculator {

    fun build(
        wallets: List<Wallet>,
        transactions: List<FinanceTransaction>,
        commitments: List<Commitment>,
        commissions: List<CommissionRecord>,
        savingsAllocations: List<SavingsAllocation>,
        savingsGoals: List<SavingsGoal>,
        payLaterPlans: List<PayLaterPlan>,
        debts: List<DebtRecord>,
        activeCycle: SalaryCycle?,
        security: SecurityConfigSnapshot,
        themeMode: ThemeMode,
        today: LocalDate = LocalDate.now(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): DuitWidgetsSnapshot {
        val privacySettings = PrivacySettings(
            hideSensitiveBalances = security.hideSensitiveBalances,
            privacyModeEnabled = security.privacyModeEnabled
        )
        // Mask home-screen widget amounts when Privacy Mode, Hide Balances, Always-Mask Widget option, or App Lock is active.
        val shouldMask = privacySettings.isAnyMaskActive || security.alwaysMaskWidgetAmounts || security.isAppLockEnabled

        val summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = transactions,
            commitments = commitments,
            commissions = commissions,
            savingsAllocations = savingsAllocations,
            savingsGoals = savingsGoals,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = activeCycle,
            today = today
        )

        val obligationsOverview = ObligationCalculator.calculateOverview(
            commitments = commitments,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = activeCycle,
            today = today
        )

        val commitmentItems = commitments
            .filter { !it.isArchived && (it.status == CommitmentStatus.UPCOMING || it.status == CommitmentStatus.PARTIALLY_PAID) }
            .map { commitment ->
                val cs = ObligationCalculator.summarizeCommitment(commitment, activeCycle, today)
                WidgetObligationItem(
                    id = commitment.id,
                    kind = WidgetObligationKind.COMMITMENT,
                    title = commitment.title,
                    dueDateEpochDay = commitment.dueDateEpochDay,
                    dueLabel = DateTimeFormatters.formatEpochDay(commitment.dueDateEpochDay),
                    amountSen = cs.remainingSen,
                    statusLabel = cs.displayState.label,
                    isOverdue = cs.displayState.name == "OVERDUE",
                    isCurrentCycle = cs.isWithinCurrentCycle
                )
            }
            .filter { it.amountSen > 0L }

        val payLaterItems = payLaterPlans
            .filter { !it.isArchived }
            .map { ObligationCalculator.summarizePayLater(it, activeCycle, today) }
            .filter { !it.isSettled && it.nextInstalmentSen > 0L }
            .map { pl ->
                val status = when {
                    pl.isOverdue -> "Overdue · ${pl.remainingInstalments} left"
                    pl.inCycleDueSen > 0L -> "Due in cycle · ${pl.remainingInstalments} left"
                    else -> "${pl.remainingInstalments} left"
                }
                WidgetObligationItem(
                    id = pl.plan.id,
                    kind = WidgetObligationKind.PAYLATER_INSTALMENT,
                    title = "${pl.plan.providerName} · ${pl.plan.title}",
                    dueDateEpochDay = pl.plan.nextDueDateEpochDay,
                    dueLabel = DateTimeFormatters.formatEpochDay(pl.plan.nextDueDateEpochDay),
                    amountSen = pl.nextInstalmentSen,
                    statusLabel = status,
                    isOverdue = pl.isOverdue,
                    isCurrentCycle = pl.inCycleDueSen > 0L
                )
            }

        // Only explicitly scheduled I_OWE debts belong in Upcoming Payments widget.
        val debtItems = debts
            .filter { !it.isArchived && it.direction == DebtDirection.I_OWE && it.dueDateEpochDay != null }
            .map { ObligationCalculator.summarizeDebt(it, activeCycle, today) }
            .filter { !it.isSettled && it.outstandingSen > 0L }
            .map { d ->
                val dueDay = d.debt.dueDateEpochDay!!
                val status = when {
                    d.isOverdue -> "Overdue"
                    d.inCycleScheduledSen > 0L -> "Due in cycle"
                    else -> "Scheduled"
                }
                WidgetObligationItem(
                    id = d.debt.id,
                    kind = WidgetObligationKind.DEBT_REPAYMENT,
                    title = d.debt.counterpartyName,
                    dueDateEpochDay = dueDay,
                    dueLabel = DateTimeFormatters.formatEpochDay(dueDay),
                    amountSen = d.outstandingSen,
                    statusLabel = status,
                    isOverdue = d.isOverdue,
                    isCurrentCycle = d.inCycleScheduledSen > 0L
                )
            }

        val sortedObligations = (commitmentItems + payLaterItems + debtItems).sortedWith(
            compareByDescending<WidgetObligationItem> { it.isOverdue }
                .thenByDescending { it.isCurrentCycle }
                .thenBy { it.dueDateEpochDay }
                .thenBy { it.kind.ordinal }
        )

        val activeGoals = savingsGoals
            .filter { it.status == SavingsGoalStatus.ACTIVE }
            .sortedWith(
                compareBy<SavingsGoal> { it.targetDateEpochDay ?: Long.MAX_VALUE }
                    .thenBy { it.createdAtEpochMs }
            )
            .map { goal ->
                val progress = SavingsCalculator.calculateGoalProgress(goal, savingsAllocations, today)
                WidgetSavingsGoalItem(
                    id = goal.id,
                    name = goal.name,
                    savedSen = progress.savedSen,
                    targetSen = goal.targetAmountSen,
                    remainingSen = progress.remainingSen,
                    progressFraction = progress.progressFraction,
                    progressPercentDisplay = progress.progressPercentDisplay,
                    targetDateLabel = goal.targetDateEpochDay?.let { DateTimeFormatters.formatEpochDay(it) }
                )
            }

        val savingsOverview = SavingsCalculator.calculateOverview(
            totalProtectedSavingsSen = summary.savingsBalanceSen,
            goals = savingsGoals,
            allocations = savingsAllocations
        )

        return DuitWidgetsSnapshot(
            themeMode = themeMode,
            isAppLockEnabled = security.isAppLockEnabled,
            shouldMaskAmounts = shouldMask,
            balance = BalanceWidgetData(
                safeToSpendSen = summary.safeToSpendSen,
                totalBalanceSen = summary.totalBalanceSen,
                protectedSavingsSen = summary.savingsBalanceSen,
                currentCycleReservedSen = summary.upcomingCommitmentsUnreservedSen,
                upcomingFutureCommitmentsSen = summary.upcomingFutureCommitmentsSen,
                cycleIncomeSen = summary.cycleIncomeSen,
                cycleExpenseSen = summary.cycleExpenseSen,
                walletCount = summary.walletCount,
                cycleLabel = summary.activeReservePeriodLabel,
                lastUpdatedLabel = DateTimeFormatters.formatTimestamp(nowEpochMs),
                isEmptyLedger = wallets.none { !it.isArchived }
            ),
            upcomingPayments = UpcomingPaymentsWidgetData(
                items = sortedObligations,
                currentCycleReservedSen = obligationsOverview.currentCycleReservedSen,
                upcomingFutureBillsSen = obligationsOverview.upcomingFutureBillsSen,
                overdueCount = obligationsOverview.overdueCount,
                overdueSen = obligationsOverview.overdueSen,
                reserveHorizonLabel = obligationsOverview.reserveHorizonLabel
            ),
            savingsGoals = SavingsGoalsWidgetData(
                goals = activeGoals,
                totalProtectedSavingsSen = savingsOverview.totalProtectedSavingsSen,
                totalAllocatedToGoalsSen = savingsOverview.totalAllocatedToGoalsSen,
                unallocatedProtectedWalletSen = savingsOverview.unallocatedProtectedWalletSen,
                overallProgressPercentDisplay = savingsOverview.overallProgressPercentDisplay,
                activeGoalsCount = savingsOverview.activeGoalsCount
            ),
            quickAdd = QuickAddWidgetData(
                hasActiveWallets = wallets.any { !it.isArchived },
                isAppLockEnabled = security.isAppLockEnabled
            )
        )
    }
}
