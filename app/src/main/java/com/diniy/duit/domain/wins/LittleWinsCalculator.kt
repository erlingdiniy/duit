package com.diniy.duit.domain.wins

import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.challenge.EarnedSticker
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.ObligationPayment
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class WinCategory(val label: String) {
    SAVINGS("Savings"),
    BUDGET("Budgets"),
    COMMITMENT("Bills"),
    CHALLENGE("Challenges")
}

enum class WinIconStyle {
    SEED, CLIMB, SPARK, CROWN, CUSHION,
    PLAN, CALM, CALENDAR_STAR,
    SHIELD, CHECK_RIBBON, FEATHER,
    SPROUT, BUD, BLOOM, HARVEST
}

/**
 * Approved Little Wins Club milestones. Positive, gentle, non-shaming copy only.
 */
enum class LittleWinType(
    val key: String,
    val category: WinCategory,
    val title: String,
    val description: String,
    val iconStyle: WinIconStyle
) {
    // Savings Milestones
    SAVINGS_FIRST_DEPOSIT(
        key = "savings_first_deposit",
        category = WinCategory.SAVINGS,
        title = "First Seed",
        description = "Made your first goal deposit.",
        iconStyle = WinIconStyle.SEED
    ),
    SAVINGS_GOAL_25(
        key = "savings_goal_25",
        category = WinCategory.SAVINGS,
        title = "Quarter Climb",
        description = "Reached 25% of a goal.",
        iconStyle = WinIconStyle.CLIMB
    ),
    SAVINGS_GOAL_50(
        key = "savings_goal_50",
        category = WinCategory.SAVINGS,
        title = "Halfway Spark",
        description = "Reached 50% of a goal.",
        iconStyle = WinIconStyle.SPARK
    ),
    SAVINGS_GOAL_100(
        key = "savings_goal_100",
        category = WinCategory.SAVINGS,
        title = "Goal Reached",
        description = "Completed a savings goal target.",
        iconStyle = WinIconStyle.CROWN
    ),
    SAVINGS_CUSHION_500(
        key = "savings_cushion_500",
        category = WinCategory.SAVINGS,
        title = "Steady Cushion",
        description = "Built RM 500+ protected savings.",
        iconStyle = WinIconStyle.CUSHION
    ),

    // Budget Achievements
    BUDGET_FIRST_PLAN(
        key = "budget_first_plan",
        category = WinCategory.BUDGET,
        title = "Budget Buddy",
        description = "Created a monthly spending plan.",
        iconStyle = WinIconStyle.PLAN
    ),
    BUDGET_ON_TRACK(
        key = "budget_on_track",
        category = WinCategory.BUDGET,
        title = "Calm Spender",
        description = "Stayed within a monthly budget.",
        iconStyle = WinIconStyle.CALM
    ),
    BUDGET_COMPLETED_MONTH(
        key = "budget_completed_month",
        category = WinCategory.BUDGET,
        title = "Balanced Month",
        description = "Finished a month within budget.",
        iconStyle = WinIconStyle.CALENDAR_STAR
    ),

    // Commitment Achievements
    COMMITMENT_FIRST_PAID(
        key = "commitment_first_paid",
        category = WinCategory.COMMITMENT,
        title = "Bill Tamer",
        description = "Marked your first bill paid.",
        iconStyle = WinIconStyle.SHIELD
    ),
    COMMITMENT_CLEAR_MONTH(
        key = "commitment_clear_month",
        category = WinCategory.COMMITMENT,
        title = "Clear Month",
        description = "Paid every bill in a month.",
        iconStyle = WinIconStyle.CHECK_RIBBON
    ),
    COMMITMENT_LIGHTER_LOAD(
        key = "commitment_lighter_load",
        category = WinCategory.COMMITMENT,
        title = "Lighter Load",
        description = "Logged a PayLater or debt payment.",
        iconStyle = WinIconStyle.FEATHER
    ),

    // Savings Challenge Stickers (integrated with Phase 3 challenge_stickers)
    CHALLENGE_SPROUT(
        key = "challenge_sprout",
        category = WinCategory.CHALLENGE,
        title = "Challenge Sprout",
        description = "Reached 25% in a challenge.",
        iconStyle = WinIconStyle.SPROUT
    ),
    CHALLENGE_BUD(
        key = "challenge_bud",
        category = WinCategory.CHALLENGE,
        title = "Challenge Bud",
        description = "Reached 50% in a challenge.",
        iconStyle = WinIconStyle.BUD
    ),
    CHALLENGE_BLOOM(
        key = "challenge_bloom",
        category = WinCategory.CHALLENGE,
        title = "Challenge Bloom",
        description = "Reached 75% in a challenge.",
        iconStyle = WinIconStyle.BLOOM
    ),
    CHALLENGE_HARVEST(
        key = "challenge_harvest",
        category = WinCategory.CHALLENGE,
        title = "Challenge Harvest",
        description = "Finished a 100% savings challenge.",
        iconStyle = WinIconStyle.HARVEST
    )
}

data class PersistedMilestone(
    val milestoneKey: String,
    val earnedAtEpochMs: Long,
    val celebratedAtEpochMs: Long?
)

data class LittleWinItem(
    val type: LittleWinType,
    /** True if currently met OR preserved historically in Room / challenge_stickers. */
    val isEarned: Boolean,
    val isCurrentlyMet: Boolean,
    val earnedAtEpochMs: Long?,
    val needsCelebration: Boolean,
    /** Current live progress (0..100), always recalculated from verified records. */
    val progressPercent: Int,
    val progressLabel: String
)

data class LittleWinsInputs(
    val goals: List<SavingsGoal>,
    val allocations: List<SavingsAllocation>,
    val movements: List<SavingsMovement>,
    val budgets: List<Budget>,
    val transactions: List<FinanceTransaction>,
    val categories: List<Category>,
    val commitments: List<Commitment>,
    val payLaterPlans: List<PayLaterPlan>,
    val debts: List<DebtRecord>,
    val obligationPayments: List<ObligationPayment>,
    val challengeStickers: List<EarnedSticker>
)

/**
 * Pure, deterministic Little Wins Club evaluator.
 * - Awards strictly from verified financial records (no transfers counted as spending, no REASSIGN double-counting).
 * - Preserves historical stickers even if live progress later changes.
 * - Recalculates current live progress honestly without shame or pressure.
 */
object LittleWinsCalculator {
    const val CUSHION_TARGET_SEN = 50_000L // RM 500.00

    fun evaluate(
        inputs: LittleWinsInputs,
        persisted: List<PersistedMilestone>,
        currentMonth: YearMonth,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<LittleWinItem> {
        val persistedByKey = persisted.associateBy { it.milestoneKey }

        // 1. Verified savings metrics (goal allocations + real net deposit movements; REASSIGN excluded)
        val allocByGoal = inputs.allocations.groupBy { it.goalId }.mapValues { (_, list) ->
            list.sumOf { it.allocatedSen.coerceAtLeast(0L) }
        }
        val totalProtectedSen = allocByGoal.values.sum()
        val hasRealDeposit = totalProtectedSen > 0L || inputs.movements.any { m ->
            m.amountSen > 0L && (
                m.type == SavingsMovementType.ALLOCATE ||
                    m.type == SavingsMovementType.TRANSFER_AND_ALLOCATE ||
                    m.type == SavingsMovementType.LINK_PROTECTED
                )
        }
        val bestGoalPercent = inputs.goals.maxOfOrNull { g ->
            if (g.status == SavingsGoalStatus.COMPLETED) 100
            else if (g.targetAmountSen <= 0L) 0
            else (((allocByGoal[g.id] ?: 0L) * 100L) / g.targetAmountSen).toInt().coerceIn(0, 100)
        } ?: 0
        val hasCompletedGoal = inputs.goals.any { g ->
            g.status == SavingsGoalStatus.COMPLETED || (g.targetAmountSen > 0L && (allocByGoal[g.id] ?: 0L) >= g.targetAmountSen)
        }
        val cushionPercent = ((totalProtectedSen * 100L) / CUSHION_TARGET_SEN).toInt().coerceIn(0, 100)

        // 2. Verified budget metrics (reusing BudgetCalculator)
        val categoriesById = inputs.categories.associateBy { it.id }
        val monthlyBudgets = inputs.budgets.filter { !it.isArchived && it.periodType == BudgetPeriodType.MONTHLY }
        val budgetProgressList = monthlyBudgets.map { b ->
            val ym = runCatching { YearMonth.parse(b.yearMonth) }.getOrNull()
            val prog = BudgetCalculator.calculateBudgetProgress(b, inputs.transactions, emptyMap(), categoriesById, zone)
            Triple(b, ym, prog)
        }
        val hasBudgetOnTrack = budgetProgressList.any { (_, _, prog) -> prog.spentSen > 0L && prog.overspentSen == 0L }
        val hasCompletedMonthBudgetWin = budgetProgressList.any { (_, ym, prog) ->
            ym != null && ym < currentMonth && prog.overspentSen == 0L
        }

        // 3. Verified commitment / obligation metrics
        val paidCommitments = inputs.commitments.filter { it.status == CommitmentStatus.PAID }
        val byMonthCommitments = inputs.commitments.groupBy { c ->
            YearMonth.from(java.time.LocalDate.ofEpochDay(c.dueDateEpochDay))
        }
        val hasClearBillMonth = byMonthCommitments.values.any { list ->
            list.isNotEmpty() && list.all { it.status == CommitmentStatus.PAID }
        }
        val currentMonthBills = byMonthCommitments[currentMonth].orEmpty()
        val currentMonthPaid = currentMonthBills.count { it.status == CommitmentStatus.PAID }
        val clearMonthPercent = when {
            hasClearBillMonth -> 100
            currentMonthBills.isNotEmpty() -> (currentMonthPaid * 100 / currentMonthBills.size).coerceIn(0, 99)
            else -> 0
        }
        val hasObligationRepayment = inputs.obligationPayments.any { it.amountSen > 0L } ||
            inputs.payLaterPlans.any { it.paidSen > 0L } ||
            inputs.debts.any { it.paidSen > 0L }

        // 4. Existing persisted Savings Challenge stickers (GardenStage)
        val challengeEarnedByStage = inputs.challengeStickers
            .groupBy { it.stage }
            .mapValues { (_, list) -> list.minOf { it.earnedAtEpochMs } }

        fun buildItem(
            type: LittleWinType,
            currentlyMet: Boolean,
            progressPercent: Int,
            progressLabel: String,
            externalEarnedAtMs: Long? = null
        ): LittleWinItem {
            val saved = persistedByKey[type.key]
            val historicalMs = saved?.earnedAtEpochMs ?: externalEarnedAtMs
            val isEarned = currentlyMet || historicalMs != null
            val needsCelebration = saved != null && saved.celebratedAtEpochMs == null
            return LittleWinItem(
                type = type,
                isEarned = isEarned,
                isCurrentlyMet = currentlyMet,
                earnedAtEpochMs = historicalMs,
                needsCelebration = needsCelebration,
                progressPercent = progressPercent.coerceIn(0, 100),
                progressLabel = progressLabel
            )
        }

        return listOf(
            buildItem(
                LittleWinType.SAVINGS_FIRST_DEPOSIT,
                currentlyMet = hasRealDeposit,
                progressPercent = if (hasRealDeposit) 100 else 0,
                progressLabel = if (hasRealDeposit) "Deposit recorded" else "Add to any goal"
            ),
            buildItem(
                LittleWinType.SAVINGS_GOAL_25,
                currentlyMet = bestGoalPercent >= 25,
                progressPercent = (bestGoalPercent * 100 / 25).coerceIn(0, 100),
                progressLabel = if (bestGoalPercent >= 25) "$bestGoalPercent% of goal" else "$bestGoalPercent% of 25%"
            ),
            buildItem(
                LittleWinType.SAVINGS_GOAL_50,
                currentlyMet = bestGoalPercent >= 50,
                progressPercent = (bestGoalPercent * 100 / 50).coerceIn(0, 100),
                progressLabel = if (bestGoalPercent >= 50) "$bestGoalPercent% of goal" else "$bestGoalPercent% of 50%"
            ),
            buildItem(
                LittleWinType.SAVINGS_GOAL_100,
                currentlyMet = hasCompletedGoal,
                progressPercent = if (hasCompletedGoal) 100 else bestGoalPercent,
                progressLabel = if (hasCompletedGoal) "Target reached" else "$bestGoalPercent% of goal"
            ),
            buildItem(
                LittleWinType.SAVINGS_CUSHION_500,
                currentlyMet = totalProtectedSen >= CUSHION_TARGET_SEN,
                progressPercent = cushionPercent,
                progressLabel = if (totalProtectedSen >= CUSHION_TARGET_SEN) "Cushion built" else "$cushionPercent% of milestone"
            ),
            buildItem(
                LittleWinType.BUDGET_FIRST_PLAN,
                currentlyMet = monthlyBudgets.isNotEmpty(),
                progressPercent = if (monthlyBudgets.isNotEmpty()) 100 else 0,
                progressLabel = if (monthlyBudgets.isNotEmpty()) "${monthlyBudgets.size} active" else "Set a budget"
            ),
            buildItem(
                LittleWinType.BUDGET_ON_TRACK,
                currentlyMet = hasBudgetOnTrack,
                progressPercent = if (hasBudgetOnTrack) 100 else if (monthlyBudgets.isNotEmpty()) 50 else 0,
                progressLabel = if (hasBudgetOnTrack) "Within limit" else "Track a budget"
            ),
            buildItem(
                LittleWinType.BUDGET_COMPLETED_MONTH,
                currentlyMet = hasCompletedMonthBudgetWin,
                progressPercent = if (hasCompletedMonthBudgetWin) 100 else if (hasBudgetOnTrack) 60 else 0,
                progressLabel = if (hasCompletedMonthBudgetWin) "Month complete" else "Finish a month on plan"
            ),
            buildItem(
                LittleWinType.COMMITMENT_FIRST_PAID,
                currentlyMet = paidCommitments.isNotEmpty(),
                progressPercent = if (paidCommitments.isNotEmpty()) 100 else 0,
                progressLabel = if (paidCommitments.isNotEmpty()) "${paidCommitments.size} paid" else "Mark a bill paid"
            ),
            buildItem(
                LittleWinType.COMMITMENT_CLEAR_MONTH,
                currentlyMet = hasClearBillMonth,
                progressPercent = clearMonthPercent,
                progressLabel = when {
                    hasClearBillMonth -> "All month bills paid"
                    currentMonthBills.isNotEmpty() -> "$currentMonthPaid of ${currentMonthBills.size} paid"
                    else -> "Track monthly bills"
                }
            ),
            buildItem(
                LittleWinType.COMMITMENT_LIGHTER_LOAD,
                currentlyMet = hasObligationRepayment,
                progressPercent = if (hasObligationRepayment) 100 else 0,
                progressLabel = if (hasObligationRepayment) "Payment recorded" else "Log a repayment"
            ),
            buildItem(
                LittleWinType.CHALLENGE_SPROUT,
                currentlyMet = GardenStage.SPROUT in challengeEarnedByStage,
                progressPercent = if (GardenStage.SPROUT in challengeEarnedByStage) 100 else 0,
                progressLabel = if (GardenStage.SPROUT in challengeEarnedByStage) "Earned in Challenges" else "Reach 25% in a challenge",
                externalEarnedAtMs = challengeEarnedByStage[GardenStage.SPROUT]
            ),
            buildItem(
                LittleWinType.CHALLENGE_BUD,
                currentlyMet = GardenStage.BUD in challengeEarnedByStage,
                progressPercent = if (GardenStage.BUD in challengeEarnedByStage) 100 else 0,
                progressLabel = if (GardenStage.BUD in challengeEarnedByStage) "Earned in Challenges" else "Reach 50% in a challenge",
                externalEarnedAtMs = challengeEarnedByStage[GardenStage.BUD]
            ),
            buildItem(
                LittleWinType.CHALLENGE_BLOOM,
                currentlyMet = GardenStage.BLOOM in challengeEarnedByStage,
                progressPercent = if (GardenStage.BLOOM in challengeEarnedByStage) 100 else 0,
                progressLabel = if (GardenStage.BLOOM in challengeEarnedByStage) "Earned in Challenges" else "Reach 75% in a challenge",
                externalEarnedAtMs = challengeEarnedByStage[GardenStage.BLOOM]
            ),
            buildItem(
                LittleWinType.CHALLENGE_HARVEST,
                currentlyMet = GardenStage.HARVEST in challengeEarnedByStage,
                progressPercent = if (GardenStage.HARVEST in challengeEarnedByStage) 100 else 0,
                progressLabel = if (GardenStage.HARVEST in challengeEarnedByStage) "Earned in Challenges" else "Finish a challenge",
                externalEarnedAtMs = challengeEarnedByStage[GardenStage.HARVEST]
            )
        )
    }

    fun formatEarnedDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
    }
}
