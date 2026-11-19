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
    private val DAILY_TITLES = listOf("Hey, money buddy! ☀️", "Anything to log today? ✍️", "Let's catch up! 🌼", "Little habits add up ✨")
    private val DAILY_BODIES = listOf(
        "A little spending check-in today? Let's keep things tidy.",
        "Your money diary is ready whenever you are.",
        "Got a transaction to record? Add it in a few taps."
    )

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
                        when {
                            isOverdue -> "Payment overdue" to "Your ${c.title} payment is overdue. Check its status."
                            daysLeft == 0L -> "Today's the due date" to "Your ${c.title} payment is due today."
                            daysLeft == 1L -> "Tomorrow's the day! 📅" to "Your ${c.title} payment is due tomorrow."
                            else -> "Psst, a bill is coming! 🔔" to
                                "Your ${c.title} bill of ${MyrFormatter.format(remaining)} is due in $daysLeft days."
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        when {
                            isOverdue -> "Payment overdue" to "A payment is overdue. Open duit. to check its status."
                            daysLeft == 0L -> "Today's the due date" to "A payment is due today."
                            else -> "A bill is coming" to "A payment is due ${duePhrase(daysLeft)}. Open duit. to view."
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
                            "An installment is coming" to "Your ${plan.title} payment is due soon."
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue PayLater instalment" to "A PayLater instalment is past due. Open duit. to review."
                        } else {
                            "An installment is coming" to "A payment is due soon. Open duit. to view."
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
                            "An installment is coming" to "Your ${debt.counterpartyName} payment is due soon."
                        }
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        if (isOverdue) {
                            "Overdue repayment reminder" to "A scheduled debt repayment is past due. Open duit. to review."
                        } else {
                            "An installment is coming" to "A payment is due soon. Open duit. to view."
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
                    "Payday check-in! 💸" to "Time to check whether your salary has arrived."
                },
                buildPrivateCopy = { _, daysLeft ->
                    "Payday check-in! 💸" to "Time to check whether your salary has arrived."
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
                        "Commission check-in ✨" to "Got a commission to record? Add it to duit."
                    },
                    buildPrivateCopy = { isOverdue, daysLeft ->
                        "Commission check-in ✨" to "Got a commission to record? Add it to duit."
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

                    val over = pct > 100L && BudgetAlertThreshold.PERCENT_100 in settings.enabledBudgetThresholds
                    val tier = if (over) 101 else crossed.percent
                    val notifId = stableNotificationId(NotificationCategory.BUDGETS, budget.id * 1000L + tier)
                    activeIds += notifId
                    val dedupKey = "BUDGETS:${budget.id}:${budget.yearMonth.ifBlank { budget.salaryCycleId ?: 0L }}:T$tier"
                    val (bTitle, bBody) = when {
                        over -> "Over your budget" to "Your ${budget.name} spending has exceeded its budget."
                        crossed == BudgetAlertThreshold.PERCENT_100 -> "Budget limit reached" to "You've reached your ${budget.name} budget limit."
                        crossed == BudgetAlertThreshold.PERCENT_50 -> "Looking good so far! ✨" to "You've used 50% of your ${budget.name} budget."
                        else -> "A little budget nudge 👀" to "You've used ${crossed.label} of your ${budget.name} budget."
                    }
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
                        detailedTitle = bTitle,
                        detailedBody = bBody,
                        privateTitle = if (over || crossed == BudgetAlertThreshold.PERCENT_100) bTitle else "Budget check-in",
                        privateBody = "One of your budgets needs a look. Open duit. to review."
                    )
                }
        }

        // 8. Savings milestones (25/50/75/100%). Only when funds were moved in the last 2 days, so an
        // update never replays old milestones; dedup via history key per goal + milestone.
        if (NotificationCategory.SAVINGS_GOALS in settings.enabledCategories) {
            savingsGoals.filter { it.targetAmountSen > 0L && it.status != SavingsGoalStatus.ARCHIVED }.forEach { goal ->
                val goalAllocs = savingsAllocations.filter { it.goalId == goal.id }
                val latest = goalAllocs.maxOfOrNull { it.updatedAtEpochMs } ?: return@forEach
                if (nowMs - latest > 2 * 86_400_000L) return@forEach
                val saved = goalAllocs.filter { it.allocatedSen > 0L }.sumOf { it.allocatedSen }
                val pct = saved * 100L / goal.targetAmountSen
                val milestone = listOf(25, 50, 75, 100).lastOrNull { pct >= it } ?: return@forEach
                val (mTitle, mBody) = when (milestone) {
                    25 -> "Tiny steps, big dreams! 🌱" to "Your ${goal.name} savings goal reached 25%."
                    50 -> "Halfway there! 🎉" to "Your ${goal.name} savings goal reached 50%."
                    75 -> "Almost there! ⭐" to "Your ${goal.name} savings goal reached 75%."
                    else -> "You did it! 🥳" to "Your ${goal.name} savings goal is complete!"
                }
                val notifId = stableNotificationId(NotificationCategory.SAVINGS_GOALS, goal.id * 1000L + milestone)
                activeIds += notifId
                dueNow += ReminderCandidate(
                    dedupKey = "SAVINGS_GOALS:${goal.id}:M$milestone",
                    notificationId = notifId, category = NotificationCategory.SAVINGS_GOALS, entityId = goal.id,
                    itemTitle = goal.name, targetEpochDay = todayEpochDay, reminderEpochDay = todayEpochDay,
                    triggerAtEpochMs = nowMs, offsetDaysBefore = 0, isOverdue = false, amountSen = null,
                    deepLink = NotificationDeepLink.PLANNING_GOALS,
                    detailedTitle = mTitle, detailedBody = mBody,
                    privateTitle = mTitle, privateBody = "A savings goal hit a milestone. Open duit. to see it."
                )
            }
        }

        // 9. Daily check-in (rotating copy; skipped today if something was already logged today).
        if (NotificationCategory.DAILY in settings.enabledCategories) {
            val dailyId = stableNotificationId(NotificationCategory.DAILY, 0L)
            val loggedToday = transactions.any {
                Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zoneId).toLocalDate() == todayDate
            }
            for (day in todayEpochDay..todayEpochDay + 1) {
                if (day == todayEpochDay && loggedToday) continue
                val triggerMs = ZonedDateTime.of(LocalDate.ofEpochDay(day), reminderTime, zoneId).toInstant().toEpochMilli()
                val title = DAILY_TITLES[(day % DAILY_TITLES.size).toInt()]
                val body = DAILY_BODIES[(day % DAILY_BODIES.size).toInt()]
                val c = ReminderCandidate(
                    dedupKey = "DAILY:$day", notificationId = dailyId, category = NotificationCategory.DAILY,
                    entityId = 0L, itemTitle = "Daily check-in", targetEpochDay = day, reminderEpochDay = day,
                    triggerAtEpochMs = triggerMs, offsetDaysBefore = 0, isOverdue = false, amountSen = null,
                    deepLink = NotificationDeepLink.HOME_ADD_TRANSACTION,
                    detailedTitle = title, detailedBody = body, privateTitle = title, privateBody = body
                )
                activeIds += dailyId
                // Only today's slot may fire now: a missed earlier slot is never replayed later.
                if (triggerMs <= nowMs && day == todayEpochDay) dueNow += c else if (triggerMs > nowMs) upcoming += c
            }
        }

        // 10. Monthly recap: first 3 days of a month, only if last month really has transactions.
        if (NotificationCategory.GENERAL in settings.enabledCategories && todayDate.dayOfMonth <= 3) {
            val prev = YearMonth.from(todayDate).minusMonths(1)
            val hasData = transactions.any {
                YearMonth.from(Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zoneId).toLocalDate()) == prev
            }
            val triggerMs = ZonedDateTime.of(todayDate, reminderTime, zoneId).toInstant().toEpochMilli()
            if (hasData && triggerMs <= nowMs) {
                val id = stableNotificationId(NotificationCategory.GENERAL, 0L)
                val monthName = prev.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                activeIds += id
                dueNow += ReminderCandidate(
                    dedupKey = "GENERAL:RECAP:$prev", notificationId = id, category = NotificationCategory.GENERAL,
                    entityId = 0L, itemTitle = "Monthly recap", targetEpochDay = todayEpochDay,
                    reminderEpochDay = todayEpochDay, triggerAtEpochMs = triggerMs, offsetDaysBefore = 0,
                    isOverdue = false, amountSen = null, deepLink = NotificationDeepLink.INSIGHTS,
                    detailedTitle = "Your money recap is ready 📊",
                    detailedBody = "Take a look at your spending and income for $monthName.",
                    privateTitle = "Your money recap is ready 📊",
                    privateBody = "Take a look at your spending and income for $monthName."
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
