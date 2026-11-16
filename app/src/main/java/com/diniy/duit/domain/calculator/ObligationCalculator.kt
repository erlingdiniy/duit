package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class CommitmentDisplayState(val label: String) {
    UPCOMING("Upcoming"),
    DUE_TODAY("Due Today"),
    OVERDUE("Overdue"),
    PARTIALLY_PAID("Partially Paid"),
    RESERVED("Reserved"),
    PAID("Paid")
}

data class CommitmentSummary(
    val commitment: Commitment,
    val displayState: CommitmentDisplayState,
    val remainingSen: Long,
    val progressFraction: Float,
    val frequencyLabel: String,
    val isWithinCurrentCycle: Boolean = true
)

data class ScheduledInstalment(
    val index: Int,
    val dueDateEpochDay: Long,
    val amountSen: Long,
    val paidSen: Long,
    val remainingSen: Long,
    val isCovered: Boolean
)

data class PayLaterSummary(
    val plan: PayLaterPlan,
    val totalPayableSen: Long,
    val outstandingSen: Long,
    val remainingInstalments: Int,
    val nextInstalmentSen: Long,
    val inCycleDueSen: Long,
    val progressFraction: Float,
    val progressPercentDisplay: String,
    val isOverdue: Boolean,
    val isSettled: Boolean,
    val schedule: List<ScheduledInstalment>
)

data class DebtSummary(
    val debt: DebtRecord,
    val outstandingSen: Long,
    val inCycleScheduledSen: Long,
    val progressFraction: Float,
    val progressPercentDisplay: String,
    val isOverdue: Boolean,
    val isSettled: Boolean
)

data class ObligationsOverview(
    val currentCycleReservedSen: Long,
    val currentCycleReservedCount: Int,
    val upcomingFutureBillsSen: Long,
    val upcomingFutureBillsCount: Int,
    val upcomingDueSen: Long,
    val upcomingDueCount: Int,
    val overdueSen: Long,
    val overdueCount: Int,
    val payLaterOutstandingSen: Long,
    val nextPayLaterInstalmentSen: Long,
    val payLaterCycleReservedSen: Long,
    val payLaterActiveCount: Int,
    val debtOwedByMeSen: Long,
    val debtOwedByMeCount: Int,
    val debtOwedToMeSen: Long,
    val debtOwedToMeCount: Int,
    val debtCycleReservedSen: Long,
    val reserveHorizonLabel: String
)

/**
 * Pure integer-sen and BigDecimal calculator for Cycle-Aware Recurring Commitments,
 * PayLater Instalment plans, and Personal Debts / Receivables.
 */
object ObligationCalculator {

    private val ONE_HUNDRED = BigDecimal(100)

    /**
     * Resolves the inclusive end epoch day of the current Safe to Spend reservation horizon.
     * - Active closed salary cycle: uses its reconciled [SalaryCycle.endDateEpochDay].
     * - Active open-ended salary cycle: uses 1 calendar cycle month from [SalaryCycle.startDateEpochDay]
     *   (i.e. `startDate.plusMonths(1).minusDays(1)`), extended only if [today] is already past it.
     * - No active salary cycle: falls back to the end of [today]'s calendar month.
     */
    fun resolveActiveCycleHorizonEndEpochDay(
        activeCycle: SalaryCycle?,
        today: LocalDate = LocalDate.now()
    ): Long {
        if (activeCycle == null) {
            return YearMonth.from(today).atEndOfMonth().toEpochDay()
        }
        return if (!activeCycle.isOpenEnded && activeCycle.endDateEpochDay < Long.MAX_VALUE) {
            activeCycle.endDateEpochDay
        } else {
            val startDate = LocalDate.ofEpochDay(activeCycle.startDateEpochDay)
            val nominalCycleEnd = startDate.plusMonths(1).minusDays(1).toEpochDay()
            maxOf(nominalCycleEnd, today.toEpochDay())
        }
    }

    fun resolveHorizonLabel(
        activeCycle: SalaryCycle?,
        today: LocalDate = LocalDate.now()
    ): String {
        if (activeCycle != null) return activeCycle.label
        val ym = YearMonth.from(today)
        return "${ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${ym.year}"
    }

    fun isDueWithinHorizon(
        dueDateEpochDay: Long,
        activeCycle: SalaryCycle?,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val horizonEnd = resolveActiveCycleHorizonEndEpochDay(activeCycle, today)
        return dueDateEpochDay <= horizonEnd
    }

    fun formatFrequencyLabel(frequency: CommitmentFrequency, customDays: Int?): String {
        return when (frequency) {
            CommitmentFrequency.CUSTOM_DAYS -> "Every ${(customDays ?: 30).coerceAtLeast(1)}d"
            else -> frequency.displayName
        }
    }

    /**
     * Advances a recurring commitment due date safely across short months and leap years.
     * Passing [anchorDayOfMonth] keeps a day-31 bill on the 31st after passing through 28/29/30-day months.
     */
    fun advanceDueDate(
        currentEpochDay: Long,
        frequency: CommitmentFrequency,
        customIntervalDays: Int? = null,
        anchorDayOfMonth: Int? = null
    ): Long {
        val current = LocalDate.ofEpochDay(currentEpochDay)
        val anchor = (anchorDayOfMonth ?: current.dayOfMonth).coerceIn(1, 31)
        val next = when (frequency) {
            CommitmentFrequency.ONE_TIME -> current
            CommitmentFrequency.WEEKLY -> current.plusWeeks(1)
            CommitmentFrequency.BIWEEKLY -> current.plusWeeks(2)
            CommitmentFrequency.MONTHLY -> addMonthsWithAnchor(current, 1L, anchor)
            CommitmentFrequency.QUARTERLY -> addMonthsWithAnchor(current, 3L, anchor)
            CommitmentFrequency.YEARLY -> addMonthsWithAnchor(current, 12L, anchor)
            CommitmentFrequency.CUSTOM_DAYS -> current.plusDays((customIntervalDays ?: 30).coerceAtLeast(1).toLong())
        }
        return next.toEpochDay()
    }

    private fun addMonthsWithAnchor(date: LocalDate, monthsToAdd: Long, anchorDay: Int): LocalDate {
        val targetYm = YearMonth.from(date).plusMonths(monthsToAdd)
        val clampedDay = anchorDay.coerceAtMost(targetYm.lengthOfMonth())
        return targetYm.atDay(clampedDay)
    }

    fun summarizeCommitment(
        commitment: Commitment,
        activeCycle: SalaryCycle? = null,
        today: LocalDate = LocalDate.now()
    ): CommitmentSummary {
        val todayEpochDay = today.toEpochDay()
        val remainingSen = if (commitment.status == CommitmentStatus.PAID) {
            0L
        } else {
            (commitment.amountSen - commitment.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
        }

        val displayState = when (commitment.status) {
            CommitmentStatus.PAID -> CommitmentDisplayState.PAID
            CommitmentStatus.RESERVED -> CommitmentDisplayState.RESERVED
            CommitmentStatus.PARTIALLY_PAID -> {
                if (commitment.dueDateEpochDay < todayEpochDay) CommitmentDisplayState.OVERDUE
                else CommitmentDisplayState.PARTIALLY_PAID
            }
            CommitmentStatus.UPCOMING -> when {
                commitment.dueDateEpochDay < todayEpochDay -> CommitmentDisplayState.OVERDUE
                commitment.dueDateEpochDay == todayEpochDay -> CommitmentDisplayState.DUE_TODAY
                else -> CommitmentDisplayState.UPCOMING
            }
        }

        val effectivePaid = if (commitment.status == CommitmentStatus.PAID) {
            commitment.amountSen
        } else {
            commitment.paidSen.coerceIn(0L, commitment.amountSen)
        }
        val fraction = if (commitment.amountSen > 0L) {
            ((effectivePaid * 1000L) / commitment.amountSen).toInt() / 1000f
        } else {
            0f
        }

        val inHorizon = isDueWithinHorizon(commitment.dueDateEpochDay, activeCycle, today)

        return CommitmentSummary(
            commitment = commitment,
            displayState = displayState,
            remainingSen = remainingSen,
            progressFraction = fraction,
            frequencyLabel = formatFrequencyLabel(commitment.frequency, commitment.customIntervalDays),
            isWithinCurrentCycle = inHorizon
        )
    }

    fun summarizePayLater(
        plan: PayLaterPlan,
        activeCycle: SalaryCycle? = null,
        today: LocalDate = LocalDate.now()
    ): PayLaterSummary {
        val totalPayableSen = (plan.purchaseAmountSen.coerceAtLeast(0L) + plan.feeOrInterestSen.coerceAtLeast(0L))
        val clampedPaidSen = plan.paidSen.coerceIn(0L, totalPayableSen)
        val outstandingSen = (totalPayableSen - clampedPaidSen).coerceAtLeast(0L)
        val count = plan.instalmentCount.coerceAtLeast(1)
        val horizonEnd = resolveActiveCycleHorizonEndEpochDay(activeCycle, today)

        val baseSen = totalPayableSen / count
        val remainderSen = totalPayableSen % count
        val firstDate = LocalDate.ofEpochDay(plan.firstDueDateEpochDay)
        val anchor = firstDate.dayOfMonth

        var cumulativePaidRemaining = clampedPaidSen
        var coveredCount = 0
        var inCycleDueSen = 0L
        val schedule = List(count) { idx ->
            val amount = baseSen + if (idx < remainderSen) 1L else 0L
            val dueEpoch = if (idx == 0) {
                plan.firstDueDateEpochDay
            } else {
                addMonthsWithAnchor(firstDate, idx.toLong(), anchor).toEpochDay()
            }
            val paidForThis = minOf(cumulativePaidRemaining, amount)
            val remainingForThis = (amount - paidForThis).coerceAtLeast(0L)
            cumulativePaidRemaining -= paidForThis
            val isCovered = remainingForThis == 0L && amount > 0L
            if (isCovered) coveredCount++
            if (remainingForThis > 0L && dueEpoch <= horizonEnd) {
                inCycleDueSen += remainingForThis
            }
            ScheduledInstalment(
                index = idx + 1,
                dueDateEpochDay = dueEpoch,
                amountSen = amount,
                paidSen = paidForThis,
                remainingSen = remainingForThis,
                isCovered = isCovered
            )
        }

        val remainingInstalments = (count - coveredCount).coerceAtLeast(if (outstandingSen > 0L) 1 else 0)
        val nextInstalmentSen = schedule.firstOrNull { !it.isCovered }?.remainingSen ?: 0L

        val fraction = if (totalPayableSen > 0L) {
            ((clampedPaidSen * 1000L) / totalPayableSen).toInt() / 1000f
        } else {
            0f
        }

        return PayLaterSummary(
            plan = plan,
            totalPayableSen = totalPayableSen,
            outstandingSen = outstandingSen,
            remainingInstalments = remainingInstalments,
            nextInstalmentSen = nextInstalmentSen,
            inCycleDueSen = inCycleDueSen,
            progressFraction = fraction,
            progressPercentDisplay = formatPercent(clampedPaidSen, totalPayableSen),
            isOverdue = outstandingSen > 0L && plan.nextDueDateEpochDay < today.toEpochDay(),
            isSettled = outstandingSen == 0L,
            schedule = schedule
        )
    }

    fun summarizeDebt(
        debt: DebtRecord,
        activeCycle: SalaryCycle? = null,
        today: LocalDate = LocalDate.now()
    ): DebtSummary {
        val original = debt.originalAmountSen.coerceAtLeast(0L)
        val clampedPaid = debt.paidSen.coerceIn(0L, original)
        val outstanding = (original - clampedPaid).coerceAtLeast(0L)
        val horizonEnd = resolveActiveCycleHorizonEndEpochDay(activeCycle, today)

        val scheduledAmt = debt.scheduledRepaymentSen ?: 0L
        val inCycleScheduled = if (
            !debt.isArchived &&
            debt.direction == DebtDirection.I_OWE &&
            outstanding > 0L &&
            scheduledAmt > 0L &&
            debt.dueDateEpochDay != null &&
            debt.dueDateEpochDay <= horizonEnd
        ) {
            scheduledAmt.coerceAtMost(outstanding)
        } else {
            0L
        }

        val fraction = if (original > 0L) {
            ((clampedPaid * 1000L) / original).toInt() / 1000f
        } else {
            0f
        }
        val overdue = outstanding > 0L && debt.dueDateEpochDay != null && debt.dueDateEpochDay < today.toEpochDay()
        return DebtSummary(
            debt = debt,
            outstandingSen = outstanding,
            inCycleScheduledSen = inCycleScheduled,
            progressFraction = fraction,
            progressPercentDisplay = formatPercent(clampedPaid, original),
            isOverdue = overdue,
            isSettled = outstanding == 0L
        )
    }

    fun calculateOverview(
        commitments: List<Commitment>,
        payLaterPlans: List<PayLaterPlan>,
        debts: List<DebtRecord>,
        activeCycle: SalaryCycle? = null,
        today: LocalDate = LocalDate.now()
    ): ObligationsOverview {
        val commitmentSummaries = commitments
            .filter { !it.isArchived }
            .map { summarizeCommitment(it, activeCycle, today) }

        val unpaidCommitments = commitmentSummaries.filter {
            it.commitment.status == CommitmentStatus.UPCOMING ||
                it.commitment.status == CommitmentStatus.PARTIALLY_PAID
        }
        val currentCycleCommitments = unpaidCommitments.filter { it.isWithinCurrentCycle }
        val futureCommitments = unpaidCommitments.filter { !it.isWithinCurrentCycle }
        val overdue = commitmentSummaries.filter { it.displayState == CommitmentDisplayState.OVERDUE }

        val activePlans = payLaterPlans
            .filter { !it.isArchived }
            .map { summarizePayLater(it, activeCycle, today) }
            .filter { !it.isSettled }

        val activeDebts = debts
            .filter { !it.isArchived }
            .map { summarizeDebt(it, activeCycle, today) }
            .filter { !it.isSettled }

        val iOwe = activeDebts.filter { it.debt.direction == DebtDirection.I_OWE }
        val owedToMe = activeDebts.filter { it.debt.direction == DebtDirection.OWED_TO_ME }

        val payLaterInCycleSen = activePlans.sumOf { it.inCycleDueSen }
        val debtInCycleSen = iOwe.sumOf { it.inCycleScheduledSen }
        val nextPayLater = activePlans
            .minByOrNull { it.plan.nextDueDateEpochDay }
            ?.nextInstalmentSen ?: 0L

        val commitmentsInCycleSen = currentCycleCommitments.sumOf { it.remainingSen }

        return ObligationsOverview(
            currentCycleReservedSen = commitmentsInCycleSen + payLaterInCycleSen + debtInCycleSen,
            currentCycleReservedCount = currentCycleCommitments.size +
                activePlans.count { it.inCycleDueSen > 0L } +
                iOwe.count { it.inCycleScheduledSen > 0L },
            upcomingFutureBillsSen = futureCommitments.sumOf { it.remainingSen },
            upcomingFutureBillsCount = futureCommitments.size,
            upcomingDueSen = commitmentsInCycleSen,
            upcomingDueCount = currentCycleCommitments.size,
            overdueSen = overdue.sumOf { it.remainingSen },
            overdueCount = overdue.size,
            payLaterOutstandingSen = activePlans.sumOf { it.outstandingSen },
            nextPayLaterInstalmentSen = nextPayLater,
            payLaterCycleReservedSen = payLaterInCycleSen,
            payLaterActiveCount = activePlans.size,
            debtOwedByMeSen = iOwe.sumOf { it.outstandingSen },
            debtOwedByMeCount = iOwe.size,
            debtOwedToMeSen = owedToMe.sumOf { it.outstandingSen },
            debtOwedToMeCount = owedToMe.size,
            debtCycleReservedSen = debtInCycleSen,
            reserveHorizonLabel = resolveHorizonLabel(activeCycle, today)
        )
    }

    fun formatPercent(partSen: Long, wholeSen: Long): String {
        if (wholeSen <= 0L) return "0%"
        val pct = BigDecimal(partSen)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal(wholeSen), 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${pct.toPlainString()}%"
    }
}
