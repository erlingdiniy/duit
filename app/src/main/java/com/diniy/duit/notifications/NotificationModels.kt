package com.diniy.duit.notifications

enum class NotificationCategory(
    val displayName: String,
    val description: String,
    val channelId: String
) {
    COMMITMENTS(
        displayName = "Commitments & Recurring Bills",
        description = "Upcoming and overdue monthly bills, subscriptions, rent & insurance",
        channelId = "duit_commitments"
    ),
    PAYLATER(
        displayName = "PayLater Instalments",
        description = "Upcoming SPayLater, Atome & custom instalment due dates",
        channelId = "duit_paylater"
    ),
    DEBT_REPAYMENTS(
        displayName = "Scheduled Debt Repayments",
        description = "Personal debts with an explicit scheduled due date",
        channelId = "duit_debts"
    ),
    SAVINGS_GOALS(
        displayName = "Savings Goal Reminders",
        description = "Target-date check-ins for active savings goals",
        channelId = "duit_savings"
    ),
    SALARY_CYCLE(
        displayName = "Salary-Cycle Reminders",
        description = "Expected payday and new cycle confirmation reminders",
        channelId = "duit_salary_cycle"
    ),
    COMMISSIONS(
        displayName = "Commission Payouts",
        description = "Pending commissions with a scheduled expected payout date",
        channelId = "duit_commissions"
    ),
    BUDGETS(
        displayName = "Budget Threshold Alerts",
        description = "Notify when budget spending crosses 50%, 80%, 90%, or 100%",
        channelId = "duit_budgets"
    ),
    DAILY(
        displayName = "Daily Reminders",
        description = "A gentle daily check-in to log your spending",
        channelId = "duit_daily"
    ),
    GENERAL(
        displayName = "General Updates",
        description = "Monthly money recap",
        channelId = "duit_general"
    );

    companion object {
        /** Daily check-in and recap are opt-in so updating never starts new daily pings. */
        val DEFAULT_ENABLED: Set<NotificationCategory> = entries.toSet() - DAILY - GENERAL
    }
}

enum class BudgetAlertThreshold(
    val percent: Int,
    val label: String
) {
    PERCENT_50(50, "50%"),
    PERCENT_80(80, "80%"),
    PERCENT_90(90, "90%"),
    PERCENT_100(100, "100%")
}

enum class ReminderOffset(
    val daysBefore: Int,
    val code: String,
    val label: String
) {
    DAYS_7(7, "D7", "7 days before"),
    DAYS_3(3, "D3", "3 days before"),
    DAY_1(1, "D1", "1 day before"),
    DUE_DAY(0, "D0", "On due date")
}

enum class NotificationDeepLink(val planningTabKey: String) {
    PLANNING_COMMITMENTS("COMMITMENTS"),
    PLANNING_PAYLATER("COMMITMENTS"),
    PLANNING_DEBTS("COMMITMENTS"),
    PLANNING_GOALS("GOALS"),
    PLANNING_BUDGETS("BUDGETS"),
    PLANNING_SALARY("SALARY"),
    PLANNING_COMMISSIONS("COMMISSIONS"),
    HOME_ADD_TRANSACTION(""),
    INSIGHTS("")
}

data class NotificationSettingsSnapshot(
    val masterEnabled: Boolean = true,
    val enabledCategories: Set<NotificationCategory> = NotificationCategory.DEFAULT_ENABLED,
    val enabledOffsets: Set<ReminderOffset> = setOf(
        ReminderOffset.DAYS_7,
        ReminderOffset.DAYS_3,
        ReminderOffset.DAY_1,
        ReminderOffset.DUE_DAY
    ),
    val enabledBudgetThresholds: Set<BudgetAlertThreshold> = BudgetAlertThreshold.entries.toSet(),
    val overdueEnabled: Boolean = true,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val hideLockScreenAmounts: Boolean = true
) {
    val formattedReminderTime: String
        get() {
            val h12 = when {
                reminderHour == 0 -> 12
                reminderHour > 12 -> reminderHour - 12
                else -> reminderHour
            }
            val amPm = if (reminderHour < 12) "AM" else "PM"
            return "$h12:${reminderMinute.toString().padStart(2, '0')} $amPm"
        }
}

data class NotificationHistoryItem(
    val id: Long,
    val dedupKey: String,
    val notificationId: Int,
    val category: NotificationCategory,
    val entityId: Long,
    val targetEpochDay: Long,
    val title: String,
    val message: String,
    val amountSen: Long?,
    val isOverdue: Boolean,
    val isRead: Boolean,
    val deepLink: NotificationDeepLink,
    val deliveredAtEpochMs: Long
)
