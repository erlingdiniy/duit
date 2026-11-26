package com.diniy.duit.devcenter

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupRestoreCoordinator
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.data.repository.OfflineUserPreferencesRepository
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.notifications.RoomNotificationHistoryRepository
import com.diniy.duit.security.AutoLockTimeout
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import com.diniy.duit.security.SecurityConfigSnapshot
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal class InMemoryDevCenterStorage : DevCenterStorage {
    private val flow = MutableStateFlow(DevCenterVaultState())
    override fun observe(): Flow<DevCenterVaultState> = flow
    override suspend fun read(): DevCenterVaultState = flow.value
    override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
        flow.value = transform(flow.value)
    }
}

internal class FakeSecretProtector : SecretProtector {
    override fun seal(plaintext: String): String = "sealed::${plaintext.reversed()}"
    override fun unseal(ciphertext: String): String? =
        if (ciphertext.startsWith("sealed::")) ciphertext.removePrefix("sealed::").reversed() else null
}

internal class FakeBiometricGateway(
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DevCenterVaultAndAuthTest {

    @Test
    fun vault_sealsTokensAndRedactsSecretsInAuditLogs() = runTest {
        val storage = InMemoryDevCenterStorage()
        val protector = FakeSecretProtector()
        val vault = DevCenterVault(
            storage = storage,
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 10_000)
        )

        val secretPat = "github_pat_11ABCDEFG_secretValue12345"
        vault.saveGithubPat(secretPat)

        val stored = vault.getState()
        assertNotEquals(secretPat, stored.encryptedGithubPat)
        assertTrue(stored.encryptedGithubPat!!.startsWith("sealed::"))
        assertEquals("••••2345", stored.githubPatHint)
        assertEquals(secretPat, vault.readGithubPat())

        vault.appendAuditEvent(
            AuditLogEntry(
                id = "aud-1",
                timestampIso = "2026-11-26T12:00:00Z",
                actor = "Bearer $secretPat",
                action = "test.redaction",
                target = "repo",
                status = "error",
                summary = "Failed using token $secretPat"
            ),
            extraSecrets = listOf(secretPat)
        )
        val logged = vault.getState().auditLog.first()
        assertFalse(logged.summary.contains(secretPat))
        assertFalse(logged.actor.contains(secretPat))
        assertTrue(logged.summary.contains("[REDACTED]"))

        vault.clearAllCredentials()
        assertNull(vault.readGithubPat())
        assertNull(vault.readSheetsToken())
        assertEquals("Not configured", vault.getState().githubPatHint)
    }

    @Test
    fun authManager_requiresPinSetupLockoutAndInactivityAutoLock() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = InMemoryDevCenterStorage()
        val protector = FakeSecretProtector()
        val vault = DevCenterVault(
            storage = storage,
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 10_000)
        )
        val baseTimeMs = 1_000_000L
        val auth = DevCenterAuthManager(
            vault = vault,
            biometricGateway = FakeBiometricGateway(),
            scope = backgroundScope,
            currentTimeMs = { baseTimeMs + testScheduler.currentTime },
            cryptoDispatcher = dispatcher
        )
        testScheduler.runCurrent()

        assertEquals(DevLockStage.NEEDS_PIN_SETUP, auth.uiState.value.stage)

        auth.setupNewPin("246810", "246810")
        testScheduler.runCurrent()
        assertEquals(DevLockStage.UNLOCKED, auth.uiState.value.stage)

        // Foreground inactivity past 5 minutes triggers auto-lock
        testScheduler.advanceTimeBy(301_000L)
        auth.checkInactivityTimeout()
        assertEquals(DevLockStage.LOCKED, auth.uiState.value.stage)

        // Wrong PIN 5 times locks for 30s
        repeat(5) {
            auth.verifyPinAndUnlock("000000")
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(100L)
        }
        assertTrue(auth.uiState.value.isThrottled)
        assertTrue(auth.uiState.value.lockoutRemainingSeconds in 1..30)

        // Wait past throttle then unlock cleanly
        testScheduler.advanceTimeBy(35_000L)
        testScheduler.runCurrent()
        auth.verifyPinAndUnlock("246810")
        testScheduler.runCurrent()
        assertEquals(DevLockStage.UNLOCKED, auth.uiState.value.stage)
    }

    @Test
    fun encryptedFinancialBackup_excludesDevCenterSecretsAndDecouplesLifecycle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val protector = FakeSecretProtector()
        val devVault = DevCenterVault(
            storage = DataStoreDevCenterStorage(context),
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 1_000)
        )
        try {
            val secretPat = "github_pat_11BACKUPEXCLUSION_998877"
            val secretSheets = "ya29.backupExclusionReadOnlyTokenCheck9988"
            devVault.saveDeveloperPin("135790")
            devVault.saveGithubPat(secretPat)
            devVault.saveFeedbackConfig("sheet-isolation-id-99", "Form Responses 1!A1:F50", secretSheets)
            val pinEnv = devVault.getState().pinEnvelope!!

            db.backupDao().insertWallets(
                listOf(
                    WalletEntity(
                        id = 1L,
                        name = "Personal Main",
                        type = WalletType.BANK,
                        balanceSen = 123_456L,
                        isExcludedFromSafeToSpend = false,
                        accentToken = PrismaticAccentToken.AQUA,
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L,
                        isIncludedInTotalBalance = true,
                        isArchived = false
                    )
                )
            )

            val secState = MutableStateFlow(SecurityConfigSnapshot())
            val secRepo = object : SecurityRepository {
                override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = secState.asStateFlow()
                override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) = Unit
                override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) = Unit
                override suspend fun disableAppLock() = Unit
                override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
                override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = Unit
                override suspend fun setHideSensitiveBalances(hidden: Boolean) = Unit
                override suspend fun setPrivacyModeEnabled(enabled: Boolean) = Unit
                override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) = Unit
                override suspend fun resetPrivacyDefaults() = Unit
                override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) = Unit
                override suspend fun clearFailedAttempts() = Unit
                override suspend fun recordBackgroundedAt(epochMs: Long) = Unit
                override suspend fun clearAllSecurityState() = Unit
            }
            val notifState = MutableStateFlow(NotificationSettingsSnapshot())
            val notifRepo = object : NotificationPreferencesRepository {
                override fun observeSettings(): Flow<NotificationSettingsSnapshot> = notifState.asStateFlow()
                override suspend fun setMasterEnabled(enabled: Boolean) = Unit
                override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) = Unit
                override suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) = Unit
                override suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) = Unit
                override suspend fun setOverdueEnabled(enabled: Boolean) = Unit
                override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
                override suspend fun setHideLockScreenAmounts(hide: Boolean) = Unit
                override suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot) = Unit
                override suspend fun resetDefaults() = Unit
            }
            val scheduler = object : ReminderScheduler {
                override fun ensureDailySafetyNet() = Unit
                override fun enqueueImmediateSync() = Unit
                override fun scheduleNextTriggerAt(triggerAtEpochMs: Long, nowEpochMs: Long) = Unit
                override fun cancelScheduledFutureTrigger() = Unit
                override fun cancelAllWork() = Unit
            }
            val widgetCoordinator = object : WidgetUpdateCoordinator {
                override fun enqueueRefresh() = Unit
                override suspend fun refreshAllWidgetsNow() = Unit
            }

            val coordinator = BackupRestoreCoordinator(
                backupDao = db.backupDao(),
                userPreferencesRepository = OfflineUserPreferencesRepository(UserPreferencesDataStore(context)),
                notificationPreferencesRepository = notifRepo,
                notificationHistoryRepository = RoomNotificationHistoryRepository(db.notificationHistoryDao()),
                securityRepository = secRepo,
                reminderScheduler = scheduler,
                widgetUpdateCoordinator = widgetCoordinator,
                ioDispatcher = Dispatchers.Unconfined
            )

            val backupBytes = coordinator.createEncryptedBackup("StrongPw#2026".toCharArray())
            val decodedBundle = coordinator.decryptAndPreviewBackup(backupBytes, "StrongPw#2026".toCharArray())
            val plainPayload = BackupSerializer.encodePayload(
                ledger = decodedBundle.ledger,
                preferences = decodedBundle.preferences,
                createdAtEpochMs = decodedBundle.header.createdAtEpochMs,
                receiptFiles = decodedBundle.receiptFiles
            )

            assertTrue(plainPayload.contains("Personal Main"))
            listOf(
                secretPat,
                secretSheets,
                pinEnv,
                "sheet-isolation-id-99",
                "duit_dev_center_prefs",
                "devcenter",
                "sealed::"
            ).forEach { forbidden ->
                assertFalse("Encrypted backup payload must exclude '$forbidden'", plainPayload.contains(forbidden))
            }

            // Clearing financial ledger does not wipe Developer Center Vault.
            coordinator.deleteAllFinancialData()
            assertTrue(db.backupDao().exportFullSnapshot().wallets.isEmpty())
            assertEquals(secretPat, devVault.readGithubPat())
            assertEquals(secretSheets, devVault.readSheetsToken())

            // Re-seed wallet and clear Developer Center credentials -> financial ledger remains untouched.
            db.backupDao().insertWallets(
                listOf(
                    WalletEntity(
                        id = 2L,
                        name = "Untouched Savings",
                        type = WalletType.BANK,
                        balanceSen = 500_000L,
                        isExcludedFromSafeToSpend = false,
                        accentToken = PrismaticAccentToken.AQUA,
                        createdAtEpochMs = 2L,
                        updatedAtEpochMs = 2L,
                        isIncludedInTotalBalance = true,
                        isArchived = false
                    )
                )
            )
            devVault.clearAllCredentials()
            assertNull(devVault.readGithubPat())
            assertNull(devVault.readSheetsToken())
            assertEquals("Untouched Savings", db.backupDao().exportFullSnapshot().wallets.single().name)
        } finally {
            devVault.clearAllCredentials()
            db.close()
        }
    }
}
