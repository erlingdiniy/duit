package com.diniy.duit.feature.onboarding

enum class OnboardingStage { UNKNOWN, TOUR, SETUP, DONE }

/**
 * Decides whether onboarding is shown. A missing "completed" flag alone proves nothing (every
 * install that predates onboarding has no flag), so any sign of earlier use counts as an existing user.
 */
object OnboardingGate {
    fun isNewUser(
        completedFlag: Boolean?,
        financialRecordCount: Int,
        hasStoredPreferences: Boolean,
        isAppLockEnabled: Boolean
    ): Boolean =
        completedFlag != true &&
            financialRecordCount == 0 &&
            !hasStoredPreferences &&
            !isAppLockEnabled
}
