package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SavingsGoalProgress(
    val goal: SavingsGoal,
    val savedSen: Long,
    val remainingSen: Long,
    val overfundedSen: Long,
    val progressPercentDisplay: String,
    val progressFraction: Float,
    val suggestedMonthlySen: Long?,
    val allocations: List<SavingsAllocation>
)

data class OverallSavingsOverview(
    val totalProtectedSavingsSen: Long,
    val totalAllocatedToGoalsSen: Long,
    val unallocatedProtectedWalletSen: Long,
    val activeGoalsCount: Int,
    val totalActiveTargetSen: Long,
    val totalActiveSavedSen: Long,
    val overallProgressFraction: Float,
    val overallProgressPercentDisplay: String
)

/**
 * Pure integer-sen and BigDecimal calculator for savings goal progress,
 * overfunded amounts, and suggested monthly savings targets.
 */
object SavingsCalculator {

    private val ONE_HUNDRED = BigDecimal(100)

    fun calculateGoalProgress(
        goal: SavingsGoal,
        allocations: List<SavingsAllocation>,
        today: LocalDate = LocalDate.now()
    ): SavingsGoalProgress {
        val goalAllocs = allocations.filter { it.goalId == goal.id && it.allocatedSen > 0L }
        val savedSen = goalAllocs.sumOf { it.allocatedSen }
        val targetSen = goal.targetAmountSen.coerceAtLeast(0L)
        val remainingSen = (targetSen - savedSen).coerceAtLeast(0L)
        val overfundedSen = (savedSen - targetSen).coerceAtLeast(0L)

        val percentDisplay = formatPercent(savedSen, targetSen)
        val fraction = if (targetSen > 0L) {
            ((savedSen.coerceAtMost(targetSen) * 1000L) / targetSen).toInt() / 1000f
        } else {
            0f
        }

        val suggestedMonthlySen = computeSuggestedMonthlySen(
            remainingSen = remainingSen,
            targetDateEpochDay = goal.targetDateEpochDay,
            today = today
        )

        return SavingsGoalProgress(
            goal = goal,
            savedSen = savedSen,
            remainingSen = remainingSen,
            overfundedSen = overfundedSen,
            progressPercentDisplay = percentDisplay,
            progressFraction = fraction,
            suggestedMonthlySen = suggestedMonthlySen,
            allocations = goalAllocs
        )
    }

    fun calculateOverview(
        totalProtectedSavingsSen: Long,
        goals: List<SavingsGoal>,
        allocations: List<SavingsAllocation>
    ): OverallSavingsOverview {
        val activeGoals = goals.filter { it.status == SavingsGoalStatus.ACTIVE }
        val activeIds = activeGoals.map { it.id }.toSet()
        val totalAllocated = allocations.sumOf { it.allocatedSen }
        val activeSaved = allocations.filter { it.goalId in activeIds }.sumOf { it.allocatedSen }
        val activeTarget = activeGoals.sumOf { it.targetAmountSen }
        val unallocatedProtected = (totalProtectedSavingsSen - totalAllocated).coerceAtLeast(0L)

        val fraction = if (activeTarget > 0L) {
            ((activeSaved.coerceAtMost(activeTarget) * 1000L) / activeTarget).toInt() / 1000f
        } else {
            0f
        }

        return OverallSavingsOverview(
            totalProtectedSavingsSen = totalProtectedSavingsSen,
            totalAllocatedToGoalsSen = totalAllocated,
            unallocatedProtectedWalletSen = unallocatedProtected,
            activeGoalsCount = activeGoals.size,
            totalActiveTargetSen = activeTarget,
            totalActiveSavedSen = activeSaved,
            overallProgressFraction = fraction,
            overallProgressPercentDisplay = formatPercent(activeSaved, activeTarget)
        )
    }

    fun computeSuggestedMonthlySen(
        remainingSen: Long,
        targetDateEpochDay: Long?,
        today: LocalDate = LocalDate.now()
    ): Long? {
        if (targetDateEpochDay == null || remainingSen <= 0L) return null
        val targetDate = LocalDate.ofEpochDay(targetDateEpochDay)
        if (!targetDate.isAfter(today)) return remainingSen
        val months = ChronoUnit.MONTHS.between(
            today.withDayOfMonth(1),
            targetDate.withDayOfMonth(1)
        ).coerceAtLeast(1L)
        // Ceiling integer division in minor units
        return (remainingSen + months - 1L) / months
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
