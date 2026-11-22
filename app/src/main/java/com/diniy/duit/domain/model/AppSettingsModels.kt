package com.diniy.duit.domain.model

enum class VisualTheme(
    val label: String,
    val summary: String
) {
    CANDY_BUBBLE("Candy Bubble", "Playful pastel cards, sticker outlines & soft shadows"),
    LIQUID_GLASS("Liquid Glass", "Translucent optical glass, bevel refraction & specular rims")
}

enum class GlassIntensity(
    val label: String,
    val alphaScale: Float,
    val summary: String
) {
    SUBTLE("Low", 0.5f, "Solid surfaces, no blur or refraction"),
    BALANCED("Mid", 1.0f, "Soft frosted glass with subtle refraction"),
    RICH("High", 1.6f, "Full Liquid Glass with chromatic lens")
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
    val visualTheme: VisualTheme = VisualTheme.CANDY_BUBBLE,
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
