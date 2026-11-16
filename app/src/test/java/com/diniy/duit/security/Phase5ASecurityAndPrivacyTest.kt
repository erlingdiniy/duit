package com.diniy.duit.security

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5ASecurityAndPrivacyTest {

    private class InMemorySecretProtector : SecretProtector {
        override fun seal(plaintext: String): String = "test_sealed::$plaintext"
        override fun unseal(ciphertext: String): String? =
            if (ciphertext.startsWith("test_sealed::")) ciphertext.removePrefix("test_sealed::") else null
    }

    private class FakeSecurityRepository(
        initial: SecurityConfigSnapshot = SecurityConfigSnapshot()
    ) : SecurityRepository {
        private val state = MutableStateFlow(initial)
        override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = state.asStateFlow()

        override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) {
            state.value = state.value.copy(
                encryptedPinEnvelope = encryptedPinEnvelope,
                isAppLockEnabled = true,
                failedAttempts = 0,
                lockoutUntilEpochMs = 0L
            )
        }

        override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) {
            state.value = state.value.copy(
                encryptedPinEnvelope = encryptedPinEnvelope,
                failedAttempts = 0,
                lockoutUntilEpochMs = 0L
            )
        }

        override suspend fun disableAppLock() {
            state.value = state.value.copy(
                isAppLockEnabled = false,
                isBiometricEnabled = false,
                encryptedPinEnvelope = null,
                failedAttempts = 0,
                lockoutUntilEpochMs = 0L,
                lastBackgroundedEpochMs = 0L
            )
        }

        override suspend fun setBiometricEnabled(enabled: Boolean) {
            state.value = state.value.copy(isBiometricEnabled = enabled)
        }

        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
            state.value = state.value.copy(autoLockTimeout = timeout)
        }

        override suspend fun setHideSensitiveBalances(hidden: Boolean) {
            state.value = state.value.copy(hideSensitiveBalances = hidden)
        }

        override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
            state.value = state.value.copy(privacyModeEnabled = enabled)
        }

        override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
            state.value = state.value.copy(alwaysMaskWidgetAmounts = alwaysMask)
        }

        override suspend fun resetPrivacyDefaults() {
            state.value = state.value.copy(
                hideSensitiveBalances = false,
                privacyModeEnabled = false,
                alwaysMaskWidgetAmounts = false
            )
        }

        override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) {
            state.value = state.value.copy(
                failedAttempts = failedAttempts,
                lockoutUntilEpochMs = lockoutUntilEpochMs
            )
        }

        override suspend fun clearFailedAttempts() {
            state.value = state.value.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L)
        }

        override suspend fun recordBackgroundedAt(epochMs: Long) {
            state.value = state.value.copy(lastBackgroundedEpochMs = epochMs)
        }

        override suspend fun clearAllSecurityState() {
            state.value = SecurityConfigSnapshot()
        }

        fun current(): SecurityConfigSnapshot = state.value
    }

    private class FakeBiometricGateway(
        var availability: BiometricAvailability = BiometricAvailability.AVAILABLE
    ) : BiometricAuthGateway {
        override fun checkAvailability(): BiometricAvailability = availability
        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            negativeButtonText: String,
            onResult: (BiometricAuthResult) -> Unit
        ) = Unit
    }

    @Test
    fun pinHashEngine_saltedSlowKdf_validatesFormatAndNeverStoresPlaintextPin() {
        val engine = PinHashEngine(InMemorySecretProtector(), iterations = 20_000)

        assertTrue(engine.isValidPinFormat("246810"))
        assertFalse(engine.isValidPinFormat("12345"))
        assertFalse(engine.isValidPinFormat("1234567"))
        assertFalse(engine.isValidPinFormat("12ab56"))

        val env1 = engine.createEncryptedEnvelope("482910".toCharArray())
        val env2 = engine.createEncryptedEnvelope("482910".toCharArray())

        // Never plaintext, and unique per-PIN random salts
        assertFalse(env1.contains("482910"))
        assertNotEquals(env1, env2)

        assertTrue(engine.verifyPin("482910".toCharArray(), env1))
        assertTrue(engine.verifyPin("482910".toCharArray(), env2))
        assertFalse(engine.verifyPin("482911".toCharArray(), env1))
        assertFalse(engine.verifyPin("000000".toCharArray(), null))
    }

    @Test
    fun appLockManager_wrongPinThrottlingLockTimeoutAndBiometricFallbackWorkCleanly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val startBaseMs = 1_000_000L
        val repo = FakeSecurityRepository()
        val engine = PinHashEngine(InMemorySecretProtector(), iterations = 10_000)
        val bio = FakeBiometricGateway(BiometricAvailability.AVAILABLE)
        var wipedCount = 0

        val manager = AppLockManager(
            securityRepository = repo,
            pinHashEngine = engine,
            biometricGateway = bio,
            scope = managerScope,
            onFactoryResetFinancialData = { wipedCount++ },
            currentTimeMs = { startBaseMs + testScheduler.currentTime },
            cryptoDispatcher = dispatcher
        )
        advanceUntilIdle()

        // Default: optional app lock starts OFF and unlocked
        assertTrue(manager.uiState.value.isReady)
        assertFalse(manager.uiState.value.isLocked)
        assertFalse(manager.uiState.value.isAppLockEnabled)

        // Enable 6-digit PIN & 30-second auto-lock timeout
        assertTrue(manager.setupPinAndEnableAppLock("135790").isSuccess)
        manager.setAutoLockTimeout(AutoLockTimeout.SECONDS_30)
        assertTrue(manager.setBiometricEnabled(true).isSuccess)
        advanceUntilIdle()

        assertTrue(manager.uiState.value.isAppLockEnabled)
        assertFalse(manager.uiState.value.isLocked)

        // Background for 10s (< 30s timeout) -> remains unlocked on return
        manager.onAppBackgrounded()
        advanceUntilIdle()
        advanceTimeBy(10_000L)
        manager.onAppForegrounded()
        advanceUntilIdle()
        assertFalse(manager.uiState.value.isLocked)

        // Background for 35s (>= 30s timeout) -> locks on return and queues biometric prompt
        manager.onAppBackgrounded()
        advanceUntilIdle()
        advanceTimeBy(35_000L)
        manager.onAppForegrounded()
        advanceUntilIdle()
        assertTrue(manager.uiState.value.isLocked)
        assertTrue(manager.uiState.value.shouldPromptBiometricAutomatically)

        // Cancelled biometric prompt falls back to PIN without looping
        manager.onBiometricCancelled()
        assertTrue(manager.uiState.value.isLocked)
        assertFalse(manager.uiState.value.shouldPromptBiometricAutomatically)
        assertTrue(manager.uiState.value.authErrorMessage!!.contains("PIN"))

        // 5 consecutive wrong PIN attempts trigger 30s lockout throttle
        repeat(4) { attempt ->
            manager.verifyPinAndUnlock("999999")
            advanceUntilIdle()
            assertTrue(manager.uiState.value.isLocked)
            assertEquals(attempt + 1, manager.uiState.value.failedAttempts)
            assertEquals(0, manager.uiState.value.lockoutRemainingSeconds)
        }
        manager.verifyPinAndUnlock("999999") // 5th wrong attempt -> 30s cooldown
        runCurrent()
        assertEquals(5, manager.uiState.value.failedAttempts)
        assertEquals(30, manager.uiState.value.lockoutRemainingSeconds)
        assertTrue(manager.uiState.value.isThrottled)

        // Even correct PIN is rejected while throttle timer is active
        manager.verifyPinAndUnlock("135790")
        runCurrent()
        assertTrue(manager.uiState.value.isLocked)

        // Advance virtual clock through the 30s cooldown -> countdown completes cleanly, then valid PIN unlocks
        advanceUntilIdle()
        assertFalse(manager.uiState.value.isThrottled)
        manager.verifyPinAndUnlock("135790")
        advanceUntilIdle()
        assertFalse(manager.uiState.value.isLocked)
        assertEquals(0, manager.uiState.value.failedAttempts)

        // Manual "Lock Now" locks immediately without auto-triggering biometrics
        manager.lockNow()
        assertTrue(manager.uiState.value.isLocked)
        assertFalse(manager.uiState.value.shouldPromptBiometricAutomatically)

        // Biometric hardware removed/unenrolled while locked -> graceful fallback notice
        bio.availability = BiometricAvailability.NONE_ENROLLED
        manager.refreshBiometricStatus()
        assertEquals(BiometricAvailability.NONE_ENROLLED, manager.uiState.value.biometricAvailability)
        assertFalse(manager.uiState.value.canUseBiometricNow)

        // Unlock with PIN, change PIN, verify old PIN no longer works and disable requires PIN
        manager.verifyPinAndUnlock("135790")
        advanceUntilIdle()
        assertFalse(manager.uiState.value.isLocked)

        assertTrue(manager.changePin("000000", "246810").isFailure)
        assertTrue(manager.changePin("135790", "246810").isSuccess)
        assertFalse(manager.verifyCurrentPin("135790"))
        assertTrue(manager.verifyCurrentPin("246810"))

        assertTrue(manager.disableAppLockWithPin("135790").isFailure)
        assertTrue(manager.disableAppLockWithPin("246810").isSuccess)
        advanceUntilIdle()
        assertFalse(manager.uiState.value.isAppLockEnabled)
        assertEquals(0, wipedCount)

        managerScope.cancel()
    }

    @Test
    fun processDeathRecreation_locksImmediatelyWhenProtectionWasActiveAndPreservesThrottle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var clockMs = 5_000_000L
        val persistedRepo = FakeSecurityRepository()
        val engine = PinHashEngine(InMemorySecretProtector(), iterations = 10_000)
        val bio = FakeBiometricGateway(BiometricAvailability.CHANGED_OR_INVALIDATED)

        // Session 1: setup PIN & record 2 wrong attempts
        val scope1 = CoroutineScope(SupervisorJob() + dispatcher)
        val session1 = AppLockManager(persistedRepo, engine, bio, scope1, {}, { clockMs }, dispatcher)
        advanceUntilIdle()
        session1.setupPinAndEnableAppLock("554433")
        advanceUntilIdle()
        session1.lockNow()
        session1.verifyPinAndUnlock("111111")
        advanceUntilIdle()
        scope1.cancel()

        // Session 2 (simulates full Android process death + cold relaunch over same persisted storage)
        val scope2 = CoroutineScope(SupervisorJob() + dispatcher)
        val session2 = AppLockManager(persistedRepo, engine, bio, scope2, {}, { clockMs + 2_000L }, dispatcher)
        advanceUntilIdle()

        assertTrue(session2.uiState.value.isReady)
        assertTrue(session2.uiState.value.isLocked)
        assertEquals(1, session2.uiState.value.failedAttempts)
        // Changed biometric must NOT auto-prompt; must stay ready for PIN fallback
        assertFalse(session2.uiState.value.shouldPromptBiometricAutomatically)

        session2.verifyPinAndUnlock("554433")
        advanceUntilIdle()
        assertFalse(session2.uiState.value.isLocked)
        scope2.cancel()
    }

    @Test
    fun dataStoreSecurityRepositoryAndReset_preserveIsolationAndClearDataExplicitly(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityRepo = DataStoreSecurityRepository(context)
        securityRepo.clearAllSecurityState()

        val engine = PinHashEngine(InMemorySecretProtector(), iterations = 10_000)
        val envelope = engine.createEncryptedEnvelope("789012".toCharArray())
        securityRepo.savePinAndEnableAppLock(envelope)
        securityRepo.setAutoLockTimeout(AutoLockTimeout.MINUTE_1)
        securityRepo.setHideSensitiveBalances(true)
        securityRepo.setPrivacyModeEnabled(true)

        val loaded = securityRepo.observeSecurityConfig().first()
        assertTrue(loaded.isAppLockEnabled)
        assertTrue(loaded.hasPinConfigured)
        assertEquals(AutoLockTimeout.MINUTE_1, loaded.autoLockTimeout)
        assertTrue(loaded.hideSensitiveBalances)
        assertTrue(loaded.privacyModeEnabled)
        assertTrue(engine.verifyPin("789012".toCharArray(), loaded.encryptedPinEnvelope))

        // Verify confirmed factory reset wipes Room data cleanly without touching unconfirmed state
        val db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val financeRepo = OfflineFirstFinanceRepository(db)
        val now = System.currentTimeMillis()
        financeRepo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 120_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        assertEquals(1, financeRepo.observeWallets().first().size)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = AppLockManager(
            securityRepository = securityRepo,
            pinHashEngine = engine,
            biometricGateway = FakeBiometricGateway(BiometricAvailability.UNAVAILABLE),
            scope = scope,
            onFactoryResetFinancialData = { db.clearAllTables() }
        )
        manager.resetProtectedAccessAndWipeData()

        assertTrue(financeRepo.observeWallets().first().isEmpty())
        assertFalse(securityRepo.observeSecurityConfig().first().isAppLockEnabled)

        scope.cancel()
        securityRepo.clearAllSecurityState()
        db.close()
    }

    @Test
    fun privacyMask_masksBalancesAndAmountsConsistently() {
        val visible = PrivacySettings(hideSensitiveBalances = false, privacyModeEnabled = false)
        assertEquals("RM 1,234.50", PrivacyMask.formatBalance(123_450L, visible))
        assertEquals("+RM 50.00", PrivacyMask.formatAmount(5_000L, visible, includePlusSign = true))
        assertEquals(MyrFormatter.format(5_000L), PrivacyMask.formatAmount(5_000L, visible))

        val balancesHidden = PrivacySettings(hideSensitiveBalances = true, privacyModeEnabled = false)
        assertEquals(PrivacyMask.MASKED_MYR, PrivacyMask.formatBalance(123_450L, balancesHidden))
        assertTrue(balancesHidden.isAnyMaskActive)
        // Individual non-balance amounts still obey formatAmount unless privacyMode is on
        assertEquals("RM 50.00", PrivacyMask.formatAmount(5_000L, balancesHidden))

        val fullPrivacy = PrivacySettings(hideSensitiveBalances = false, privacyModeEnabled = true)
        assertEquals(PrivacyMask.MASKED_MYR, PrivacyMask.formatBalance(123_450L, fullPrivacy))
        assertEquals(PrivacyMask.MASKED_MYR, PrivacyMask.formatAmount(5_000L, fullPrivacy))
        assertTrue(fullPrivacy.isAnyMaskActive)
    }
}
