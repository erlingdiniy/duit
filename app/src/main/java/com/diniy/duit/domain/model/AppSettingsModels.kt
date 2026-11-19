package com.diniy.duit.domain.model

enum class GlassIntensity(
    val label: String,
    val alphaScale: Float,
    val summary: String
) {
    SUBTLE("Subtle", 0.5f, "Flatter candy surfaces with the lightest shadows"),
    BALANCED("Balanced", 1.0f, "Soft candy shadows"),
    RICH("Rich", 1.6f, "Deeper shadows for extra pop")
}

enum class AnimationIntensity(
    val label: String,
    val durationScale: Float,
    val summary: String
) {
    SUBTLE("Subtle", 0.6f, "Fast, minimal transitions"),
    BALANCED("Balanced", 1.0f, "Standard responsive transitions"),
    EXPRESSIVE("Expressive", 1.35f, "Bouncier, richer motion")
}

enum class DashboardSectionKey(
    val title: String,
    val description: String
) {
    SECONDARY_METRICS(
        title = "Savings & Obligations Summary",
        description = "Protected Savings and Current Cycle Reserved cards"
    ),
    QUICK_ACTIONS(
        title = "Quick Actions",
        description = "Expense, Income, Transfer, and Commission shortcuts"
    ),
    PENDING_COMMISSIONS(
        title = "Pending Commissions",
        description = "Summary banner when unsettled commissions exist"
    ),
    WALLETS(
        title = "Wallets",
        description = "Active and archived MYR wallet accounts"
    ),
    UPCOMING_COMMITMENTS(
        title = "Upcoming Commitments",
        description = "Bills due in the active horizon"
    )
}

data class AppSettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glassIntensity: GlassIntensity = GlassIntensity.BALANCED,
    val hapticFeedbackEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val animationIntensity: AnimationIntensity = AnimationIntensity.BALANCED,
    val hiddenDashboardSections: Set<DashboardSectionKey> = emptySet(),
    val dashboardSectionOrder: List<DashboardSectionKey> = DashboardSectionKey.entries.toList(),
    val defaultWalletId: Long? = null,
    val defaultBudgetPeriodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val autoAdvanceRecurringCommitments: Boolean = true,
    val commissionTrackingEnabled: Boolean = true,
    /** Public edition only: Settings > Optional Features > Commission Tracking. Always true in the personal edition. */
    val commissionFeatureEnabled: Boolean = true,
    val commissionFormulaConfig: CommissionFormulaConfig = CommissionFormulaConfig(),
    val hasCompletedOnboarding: Boolean = false
)
