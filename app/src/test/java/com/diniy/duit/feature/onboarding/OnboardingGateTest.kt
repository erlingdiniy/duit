package com.diniy.duit.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {

    @Test
    fun freshInstall_noFlagNoDataNoSettingsNoLock_isNewUser() {
        assertTrue(OnboardingGate.isNewUser(null, 0, hasStoredPreferences = false, isAppLockEnabled = false))
    }

    @Test
    fun completedFlag_neverOnboardsAgain() {
        assertFalse(OnboardingGate.isNewUser(true, 0, hasStoredPreferences = false, isAppLockEnabled = false))
    }

    @Test
    fun missingFlagOnUpgradedInstall_isNotTreatedAsNewUser() {
        // Existing install updating to this version: no flag, but it has wallets/records.
        assertFalse(OnboardingGate.isNewUser(null, 12, hasStoredPreferences = false, isAppLockEnabled = false))
        // Existing install with only customised settings.
        assertFalse(OnboardingGate.isNewUser(null, 0, hasStoredPreferences = true, isAppLockEnabled = false))
        // Existing install protected by App Lock.
        assertFalse(OnboardingGate.isNewUser(null, 0, hasStoredPreferences = false, isAppLockEnabled = true))
    }

    @Test
    fun interruptedOnboarding_afterWalletCreated_resumesAsExistingUser() {
        // Process died after the wallet was saved but before the flag was written.
        assertFalse(OnboardingGate.isNewUser(null, 1, hasStoredPreferences = false, isAppLockEnabled = false))
    }

    @Test
    fun interruptedOnboarding_beforeWallet_showsOnboardingAgain() {
        assertTrue(OnboardingGate.isNewUser(false, 0, hasStoredPreferences = false, isAppLockEnabled = false))
    }
}
