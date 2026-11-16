package com.diniy.duit.domain.calculator

import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
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
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderCandidate(
    val dedupKey: String,
    val notificationId: Int,
    val category: NotificationCategory,
    val entityId: Long,
    val itemTitle: String,
    val targetEpochDay: Long,
    val reminderEpochDay: Long,
    val triggerAtEpochMs: Long,
    val offsetDaysBefore: Int?,
    val isOverdue: Boolean,
    val amountSen: Long?,
    val deepLink: NotificationDeepLink,
    val detailedTitle: String,
    val detailedBody: String,
    val privateTitle: String,
    val privateBody: String
) {
    fun resolveTitle(maskSensitiveContent: Boolean): String =
        if (maskSensitiveContent) privateTitle else detailedTitle

    fun resolveBody(maskSensitiveContent: Boolean): String =
        if (maskSensitiveContent) privateBody else detailedBody
}

data class ReminderEvaluationResult(
    val dueNowCandidates: List<ReminderCandidate>,
    val upcomingCandidates: List<ReminderCandidate>,
    val overdueItems: List<ReminderCandidate>,
    val nextScheduledTriggerEpochMs: Long?,
    val activeNotificationIds: Set<Int>
)

/**
 * Pure domain calculator for Phase 5B Smart Notifications.
 * - Produces deterministic deduplication keys, stable notification IDs, and timezone-aware trigger instants.
 * - Automatically excludes paid, archived, deleted, and unscheduled items.
 * - Strictly excludes debts without a due date and receivables (OWED_TO_ME).
 */
object ReminderCalculator {

    private const val UPCOMING_WINDOW_DAYS = 14L

    fun stableNotificationId(category: NotificationCategory, entityId: Long): Int {
        val raw = "${category.name}:$entityId".hashCode()
        val positive = raw and 0x7FFF_FFFF
        return if (positive == 0) category.ordinal + 100 else positive
    }

    fun evaluate(
        settings: NotificationSettingsSnapshot,
        commitments: List<Commitment>,
        payLaterPlans: List<PayLaterPlan>,
        debts: List<DebtRecord>,
        savingsGoals: List<SavingsGoal>,
        savingsAllocations: List<SavingsAllocation>,
        activeCycle: SalaryCycle?,
        commissions: List<CommissionRecord>,
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        budgets: List<Budget> = emptyList(),
        transactions: List<FinanceTransaction> = emptyList(),
        categories: List<Category> = emptyList()
    ): ReminderEvaluationResult {
        if (!settings.masterEnabled) {
            return ReminderEvaluationResult(
                dueNowCandidates = emptyList(),
                upcomingCandidates = emptyList(),
                overdueItems = emptyList(),
                nextScheduledTriggerEpochMs = null,
                activeNotificationIds = emptySet()
            )
        }

        val todayDate = nowInstant.atZone(zoneId).toLocalDate()
        val todayEpochDay = todayDate.toEpochDay()
        val nowMs = nowInstant.toEpochMilli()
        val reminderTime = LocalTime.of(settings.reminderHour.coerceIn(0, 23), settings.reminderMinute.coerceIn(0, 59))

        val dueNow = mutableListOf<ReminderCandidate>()
        val upcoming = mutableListOf<ReminderCandidate>()
        val overdueItems = mutableListOf<ReminderCandidate>()
        val activeIds = mutableSetOf<Int>()

        fun registerEventsForTarget(
            category: NotificationCategory,
            entityId: Long,
            itemTitle: String,
            targetEpochDay: Long,
            amountSen: Long?,
            deepLink: NotificationDeepLink,
            allowOverdue: Boolean,
            buildDetailedCopy: (isOverdue: Boolean, daysUntil: Long) -> Pair<String, String>,
            buildPrivateCopy: (isOverdue: Boolean, daysUntil: Long) -> Pair<String, String>
        ) {
            if (category !in settings.enabledCategories) return

            val notifId = stableNotificationId(category, entityId)
            val daysUntilDue = targetEpochDay - todayEpochDay

            if (daysUntilDue < 0L) {
                if (!allowOverdue || !settings.overdueEnabled) return
                val (dTitle, dBody) = buildDetailedCopy(true, daysUntilDue)
                val (pTitle, pBody) = buildPrivateCopy(true, daysUntilDue)
                val overdueTriggerMs = ZonedDateTime.of(todayDate, reminderTime, zoneId).toInstant().toEpochMilli()
                val candidate = ReminderCandidate(
                    dedupKey = "${category.name}:$entityId:$targetEpochDay:OVERDUE",
                    notificationId = notifId,
                    category = category,
                    entityId = entityId,
                    itemTitle = itemTitle,
                    targetEpochDay = targetEpochDay,
                    reminderEpochDay = todayEpochDay,
                    triggerAtEpochMs = overdueTriggerMs,
                    offsetDaysBefore = null,
                    isOverdue = true,
                    amountSen = amountSen,
                    deepLink = deepLink,
                    detailedTitle = dTitle,
                    detailedBody = dBody,
                    privateTitle = pTitle,
                    privateBody = pBody
                )
                activeIds += notifId
                overdueItems += candidate
                if (overdueTriggerMs <= nowMs) {
                    dueNow += candidate
                } else {
                    upcoming += candidate
                }
                return
            }

            // Upcoming or due today offsets
            settings.enabledOffsets
                .sortedByDescending { it.daysBefore }
                .forEach { offset ->
                    val reminderDay = targetEpochDay - offset.daysBefore.toLong()
                    if (reminderDay < todayEpochDay) {
                        // Past reminder offset already elapsed cleanly (still retain if it's due today & offset == DUE_DAY)
                        return@forEach
                    }
                    if (reminderDay > todayEpochDay + UPCOMING_WINDOW_DAYS) {
                        return@forEach
                    }
                    val triggerMs = ZonedDateTime.of(LocalDate.ofEpochDay(reminderDay), reminderTime, zoneId)
                        .toInstant()
                        .toEpochMilli()
                    val (dTitle, dBody) = buildDetailedCopy(false, offset.daysBefore.toLong())
                    val (pTitle, pBody) = buildPrivateCopy(false, offset.daysBefore.toLong())

                    val candidate = ReminderCandidate(
                        dedupKey = "${category.name}:$entityId:$targetEpochDay:${offset.code}",
                        notificationId = notifId,
                        category = category,
                        entityId = entityId,
                        itemTitle = itemTitle,
                        targetEpochDay = targetEpochDay,
                        reminderEpochDay = reminderDay,
                        triggerAtEpochMs = triggerMs,
                        offsetDaysBefore = offset.daysBefore,
                        isOverdue = false,
                        amountSen = amountSen,
                        deepLink = deepLink,
                        detailedTitle = dTitle,
                        detailedBody = dBody,
                        privateTitle = pTitle,
                        privateBody = pBody
                    )
                    activeIds += notifId
                    if (triggerMs <= nowMs) {
                        dueNow += candidate
                    } else {
                        upcoming += candidate
                    }
                }
        }

        // 1. Monthly commitments & recurring bills (exclude paid, reserved, or archived)
        commitments
            .filter {
                !it.isArchived &&
                    (it.status == CommitmentStatus.UPCOMING || it.status == CommitmentStatus.PARTIALLY_PAID) &&
                    (it.amountSen - it.paidSen.coerceAtLeast(0L)) > 0L
            }
            .forEach { c ->
                val remaining = (c.amountSen - c.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
                val dueStr = DateTimeFormatters.formatEpochDay(c.dueDateEpochDay)
                registerEventsForTarget(
                    category = NotificationCategory.COMMITMENTS,
                    entityId = c.id,
                    itemTitle = c.title,
                    targetEpochDay = c.dueDateEpochDay,
                    amountSen = remaining,
                    deepLink = NotificationDeepLink.PLANNING_COMMITMENTS,
                    allowOverdue = true,
                    buildDetailedCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue bill · ${c.title}" to "${MyrFormatter.format(remaining)} was due $dueStr"
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Bill $timing · ${c.title}" to "${MyrFormatter.format(remaining)} due $dueStr"
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue commitment reminder" to "An unpaid commitment is overdue. Open duit. to review."
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Commitment $timing" to "You have a scheduled commitment $timing ($dueStr)."
                        }
                    }
                )
            }

        // 2. PayLater instalments (next uncovered instalment only)
        payLaterPlans
            .filter { !it.isArchived }
            .forEach { plan ->
                val summary = ObligationCalculator.summarizePayLater(plan, activeCycle, todayDate)
                if (summary.isSettled) return@forEach
                val nextInst = summary.schedule.firstOrNull { !it.isCovered } ?: return@forEach
                val dueStr = DateTimeFormatters.formatEpochDay(nextInst.dueDateEpochDay)
                registerEventsForTarget(
                    category = NotificationCategory.PAYLATER,
                    entityId = plan.id,
                    itemTitle = "${plan.providerName} · ${plan.title}",
                    targetEpochDay = nextInst.dueDateEpochDay,
                    amountSen = nextInst.remainingSen,
                    deepLink = NotificationDeepLink.PLANNING_PAYLATER,
                    allowOverdue = true,
                    buildDetailedCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue ${plan.providerName} · ${plan.title}" to "Instalment #${nextInst.index} (${MyrFormatter.format(nextInst.remainingSen)}) was due $dueStr"
                        } else {
                            val timing = duePhrase(daysLeft)
                            "${plan.providerName} instalment $timing" to "${plan.title} · ${MyrFormatter.format(nextInst.remainingSen)} due $dueStr"
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue PayLater instalment" to "A PayLater instalment is past due. Open duit. to review."
                        } else {
                            val timing = duePhrase(daysLeft)
                            "PayLater instalment $timing" to "You have a PayLater instalment scheduled $timing ($dueStr)."
                        }
                    }
                )
            }

        // 3. Scheduled debt repayments (I_OWE only, MUST have explicit dueDateEpochDay)
        debts
            .filter {
                !it.isArchived &&
                    it.direction == DebtDirection.I_OWE &&
                    it.dueDateEpochDay != null &&
                    (it.originalAmountSen - it.paidSen.coerceAtLeast(0L)) > 0L
            }
            .forEach { debt ->
                val dueEpoch = debt.dueDateEpochDay ?: return@forEach
                val outstanding = (debt.originalAmountSen - debt.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
                val targetSen = (debt.scheduledRepaymentSen?.takeIf { it > 0L } ?: outstanding).coerceAtMost(outstanding)
                val dueStr = DateTimeFormatters.formatEpochDay(dueEpoch)
                registerEventsForTarget(
                    category = NotificationCategory.DEBT_REPAYMENTS,
                    entityId = debt.id,
                    itemTitle = "Repay ${debt.counterpartyName}",
                    targetEpochDay = dueEpoch,
                    amountSen = targetSen,
                    deepLink = NotificationDeepLink.PLANNING_DEBTS,
                    allowOverdue = true,
                    buildDetailedCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue debt repayment · ${debt.counterpartyName}" to "${MyrFormatter.format(targetSen)} was due $dueStr"
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Debt repayment $timing · ${debt.counterpartyName}" to "${MyrFormatter.format(targetSen)} scheduled for $dueStr"
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue repayment reminder" to "A scheduled debt repayment is past due. Open duit. to review."
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Scheduled repayment $timing" to "You have a scheduled debt repayment $timing ($dueStr)."
                        }
                    }
                )
            }

        // 4. Savings goals with explicit target dates and remaining target > 0
        savingsGoals
            .filter { it.status == SavingsGoalStatus.ACTIVE && it.targetDateEpochDay != null }
            .forEach { goal ->
                val progress = SavingsCalculator.calculateGoalProgress(goal, savingsAllocations, todayDate)
                val targetDay = goal.targetDateEpochDay ?: return@forEach
                if (progress.remainingSen <= 0L) return@forEach
                val dueStr = DateTimeFormatters.formatEpochDay(targetDay)
                registerEventsForTarget(
                    category = NotificationCategory.SAVINGS_GOALS,
                    entityId = goal.id,
                    itemTitle = goal.name,
                    targetEpochDay = targetDay,
                    amountSen = progress.remainingSen,
                    deepLink = NotificationDeepLink.PLANNING_GOALS,
                    allowOverdue = false,
                    buildDetailedCopy = { _, daysLeft ->
                        val timing = duePhrase(daysLeft)
                        "Savings goal target $timing · ${goal.name}" to "${MyrFormatter.format(progress.remainingSen)} remaining to reach target ($dueStr)"
                    },
                    buildPrivateCopy = { _, daysLeft ->
                        val timing = duePhrase(daysLeft)
                        "Savings goal reminder" to "A savings goal target date is $timing ($dueStr). Open duit. to review."
                    }
                )
            }

        // 5. Salary cycle reminder (expected payday on active cycle)
        if (activeCycle != null && activeCycle.paydayEpochDay > 0L) {
            val paydayStr = DateTimeFormatters.formatEpochDay(activeCycle.paydayEpochDay)
            registerEventsForTarget(
                category = NotificationCategory.SALARY_CYCLE,
                entityId = activeCycle.id,
                itemTitle = activeCycle.label,
                targetEpochDay = activeCycle.paydayEpochDay,
                amountSen = activeCycle.baseSalarySen.takeIf { it > 0L },
                deepLink = NotificationDeepLink.PLANNING_SALARY,
                allowOverdue = false,
                buildDetailedCopy = { _, daysLeft ->
                    val timing = duePhrase(daysLeft)
                    "Salary cycle payday $timing" to "Expected payday for ${activeCycle.label} is $paydayStr. Record salary income to start the next cycle."
                },
                buildPrivateCopy = { _, daysLeft ->
                    val timing = duePhrase(daysLeft)
                    "Salary cycle reminder" to "Expected payday check-in is $timing ($paydayStr)."
                }
            )
        }

        // 6. Commission payout reminders (only when pending/partial & payoutDateEpochDay exists)
        commissions
            .filter {
                it.status != CommissionStatus.RECEIVED &&
                    it.payoutDateEpochDay != null &&
                    (it.amountSen - it.receivedNetSen.coerceAtLeast(0L)) > 0L
            }
            .forEach { comm ->
                val payoutDay = comm.payoutDateEpochDay ?: return@forEach
                val remaining = (comm.amountSen - comm.receivedNetSen.coerceAtLeast(0L)).coerceAtLeast(0L)
                val dateStr = DateTimeFormatters.formatEpochDay(payoutDay)
                registerEventsForTarget(
                    category = NotificationCategory.COMMISSIONS,
                    entityId = comm.id,
                    itemTitle = comm.title,
                    targetEpochDay = payoutDay,
                    amountSen = remaining,
                    deepLink = NotificationDeepLink.PLANNING_COMMISSIONS,
                    allowOverdue = true,
                    buildDetailedCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Commission payout pending · ${comm.title}" to "Expected $dateStr (${MyrFormatter.format(remaining)} awaiting payout)"
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Commission payout $timing · ${comm.title}" to "${MyrFormatter.format(remaining)} expected $dateStr"
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Pending commission reminder" to "An expected commission payout date has passed. Open duit. to update status."
                        } else {
                            val timing = duePhrase(daysLeft)
                            "Commission payout $timing" to "An expected commission payout is scheduled $timing ($dateStr)."
                        }
                    }
                )
            }

        // 7. Budget threshold alerts (80%, 90%, 100%)
        if (NotificationCategory.BUDGETS in settings.enabledCategories &&
            settings.enabledBudgetThresholds.isNotEmpty() &&
            budgets.isNotEmpty()
        ) {
            val cyclesMap = if (activeCycle != null) mapOf(activeCycle.id to activeCycle) else emptyMap()
            val categoriesMap = categories.associateBy { it.id }
            val currentYm = YearMonth.from(todayDate).toString()

            budgets
                .filter { budget ->
                    !budget.isArchived && budget.limitSen > 0L && when (budget.periodType) {
                        BudgetPeriodType.MONTHLY -> budget.yearMonth == currentYm
                        BudgetPeriodType.SALARY_CYCLE -> activeCycle != null && budget.salaryCycleId == activeCycle.id
                    }
                }
                .forEach { budget ->
                    val progress = BudgetCalculator.calculateBudgetProgress(
                        budget = budget,
                        transactions = transactions,
                        cyclesById = cyclesMap,
                        categoriesById = categoriesMap,
                        zoneId = zoneId
                    )
                    val pct = (progress.spentSen * 100L) / budget.limitSen
                    val crossed = BudgetAlertThreshold.entries
                        .filter { it in settings.enabledBudgetThresholds && pct >= it.percent }
                        .maxByOrNull { it.percent }
                        ?: return@forEach

                    val notifId = stableNotificationId(NotificationCategory.BUDGETS, budget.id * 1000L + crossed.percent)
                    activeIds += notifId
                    val dedupKey = "BUDGETS:${budget.id}:${budget.yearMonth.ifBlank { budget.salaryCycleId ?: 0L }}:T${crossed.percent}"
                    dueNow += ReminderCandidate(
                        dedupKey = dedupKey,
                        notificationId = notifId,
                        category = NotificationCategory.BUDGETS,
                        entityId = budget.id,
                        itemTitle = budget.name,
                        targetEpochDay = todayEpochDay,
                        reminderEpochDay = todayEpochDay,
                        triggerAtEpochMs = nowMs,
                        offsetDaysBefore = 0,
                        isOverdue = crossed == BudgetAlertThreshold.PERCENT_100,
                        amountSen = progress.spentSen,
                        deepLink = NotificationDeepLink.PLANNING_BUDGETS,
                        detailedTitle = "Budget ${crossed.label} alert · ${budget.name}",
                        detailedBody = "${MyrFormatter.format(progress.spentSen)} of ${MyrFormatter.format(budget.limitSen)} used (${progress.usedPercentDisplay})",
                        privateTitle = "Budget threshold reached (${crossed.label})",
                        privateBody = "One of your tracked budgets has reached ${crossed.label}. Open duit. to review."
                    )
                }
        }

        val sortedDueNow = dueNow.sortedWith(compareBy<ReminderCandidate> { !it.isOverdue }.thenBy { it.targetEpochDay })
        val sortedUpcoming = upcoming.sortedWith(compareBy<ReminderCandidate> { it.triggerAtEpochMs }.thenBy { it.targetEpochDay })
        val sortedOverdue = overdueItems.sortedBy { it.targetEpochDay }
        val nextTrigger = sortedUpcoming.firstOrNull()?.triggerAtEpochMs

        return ReminderEvaluationResult(
            dueNowCandidates = sortedDueNow,
            upcomingCandidates = sortedUpcoming,
            overdueItems = sortedOverdue,
            nextScheduledTriggerEpochMs = nextTrigger,
            activeNotificationIds = activeIds
        )
    }

    private fun duePhrase(daysBefore: Long): String = when (daysBefore) {
        0L -> "due today"
        1L -> "due tomorrow"
        else -> "due in $daysBefore days"
    }
}
