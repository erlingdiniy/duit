package com.diniy.duit.devcenter

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.BuildConfig
import com.diniy.duit.backup.BackupRestoreCoordinator
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.entity.BudgetEntity
import com.diniy.duit.data.local.entity.CommitmentEntity
import com.diniy.duit.data.local.entity.EarnedMilestoneEntity
import com.diniy.duit.data.local.entity.MoneyReminderEntity
import com.diniy.duit.data.local.entity.TaxReliefEntryEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.OfflineFirstReceiptRepository
import com.diniy.duit.data.repository.OfflineUserPreferencesRepository
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import com.diniy.duit.feature.settings.activeLicenseNotices
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.notifications.RoomNotificationHistoryRepository
import com.diniy.duit.security.AutoLockTimeout
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecurityConfigSnapshot
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalEditionParityTest {

    private fun fakeSecurityRepo(): SecurityRepository {
        val state = MutableStateFlow(SecurityConfigSnapshot())
        return object : SecurityRepository {
            override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = state.asStateFlow()
            override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) = Unit
            override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) = Unit
            override suspend fun disableAppLock() = Unit
            override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
            override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = Unit
            override suspend fun setHideSensitiveBalances(hidden: Boolean) {
                state.value = state.value.copy(hideSensitiveBalances = hidden)
            }
            override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
                state.value = state.value.copy(privacyModeEnabled = enabled)
            }
            override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
                state.value = state.value.copy(alwaysMaskWidgetAmounts = alwaysMask)
            }
            override suspend fun resetPrivacyDefaults() = Unit
            override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) = Unit
            override suspend fun clearFailedAttempts() = Unit
            override suspend fun recordBackgroundedAt(epochMs: Long) = Unit
            override suspend fun clearAllSecurityState() = Unit
        }
    }

    private fun fakeNotifPrefsRepo(): NotificationPreferencesRepository {
        val state = MutableStateFlow(NotificationSettingsSnapshot())
        return object : NotificationPreferencesRepository {
            override fun observeSettings(): Flow<NotificationSettingsSnapshot> = state.asStateFlow()
            override suspend fun setMasterEnabled(enabled: Boolean) = Unit
            override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) = Unit
            override suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) = Unit
            override suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) = Unit
            override suspend fun setOverdueEnabled(enabled: Boolean) = Unit
            override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
            override suspend fun setHideLockScreenAmounts(hide: Boolean) = Unit
            override suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot) {
                state.value = snapshot
            }
            override suspend fun resetDefaults() = Unit
        }
    }

    @Test
    fun personalEdition_matchesPublicV120FlagsFeaturesAndLicenseNotices() = runBlocking {
        assertEquals("com.diniy.duit", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.PUBLIC_EDITION)
        assertTrue(BuildConfig.OPTIONAL_COMMISSION)
        assertTrue(BuildConfig.DEVELOPER_CENTER_ENABLED)
        assertTrue(DeveloperCenterEntryPoint.isAvailable)
        assertEquals("", BuildConfig.FEEDBACK_URL)

        assertTrue(NotificationCategory.MONEY_REMINDERS in NotificationCategory.forThisEdition)
        assertFalse(activeLicenseNotices().any { it.name.contains("Firebase", ignoreCase = true) })

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)
        prefs.setCommissionFeatureEnabled(false)
        assertFalse(prefs.appSettings.first().commissionFeatureEnabled)
        prefs.setCommissionFeatureEnabled(true)
        assertTrue(prefs.appSettings.first().commissionFeatureEnabled)
    }

    @Test
    fun crossEditionBackupRestore_transfersAllV120FinancialAndReceiptDataIntoPersonal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val srcDb = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()
        val dstDb = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()
        val srcDir = File(context.cacheDir, "parity-src-receipts-${System.nanoTime()}").apply { mkdirs() }
        val dstDir = File(context.cacheDir, "parity-dst-receipts-${System.nanoTime()}").apply { mkdirs() }
        val srcStorage = ReceiptFileStorage(srcDir)
        val dstStorage = ReceiptFileStorage(dstDir)
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
        val protector = FakeSecretProtector()
        val devVault = DevCenterVault(
            storage = DataStoreDevCenterStorage(context),
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 10_000)
        )

        try {
            val prefsRepo = OfflineUserPreferencesRepository(UserPreferencesDataStore(context))
            val srcFinance = OfflineFirstFinanceRepository(srcDb)
            val srcReceipts = OfflineFirstReceiptRepository(srcDb.receiptDao(), srcStorage)

            val walletId = srcDb.walletDao().upsert(
                WalletEntity(
                    name = "Maybank MAE",
                    type = WalletType.BANK,
                    balanceSen = 450_000L,
                    isExcludedFromSafeToSpend = false,
                    accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L
                )
            )
            srcFinance.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 3_500L,
                    category = "Medical",
                    title = "Klinik Pergigian",
                    note = "Checkup",
                    commitmentId = null,
                    occurredAtEpochMs = 1_750_000_000_000L,
                    createdAtEpochMs = 1_750_000_000_000L
                )
            )
            val txId = srcFinance.observeTransactions().first().single().id

            srcDb.backupDao().insertBudgets(
                listOf(
                    BudgetEntity(
                        name = "Medical Budget",
                        scope = BudgetScope.OVERALL,
                        periodType = BudgetPeriodType.MONTHLY,
                        yearMonth = "2026-11",
                        salaryCycleId = null,
                        categoryId = null,
                        categoryName = "",
                        limitSen = 80_000L,
                        accentToken = PrismaticAccentToken.AQUA,
                        note = "",
                        isArchived = false,
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L
                    )
                )
            )
            srcDb.backupDao().insertCommitments(
                listOf(
                    CommitmentEntity(
                        title = "Unifi Fiber",
                        amountSen = 12_900L,
                        dueDateEpochDay = 20_420L,
                        status = CommitmentStatus.UPCOMING,
                        walletId = walletId,
                        isRecurring = true,
                        salaryCycleId = null,
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L
                    )
                )
            )
            val goalId = srcFinance.upsertSavingsGoal(
                SavingsGoal(0L, "Emergency Fund", 1_000_000L, null, "", "star", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1L, 1L)
            )
            srcFinance.saveSavingsChallenge(
                SavingsChallenge(0L, goalId, ChallengeType.WEEK_52, 1_000L, 20_400L, randomSeed = 5)
            )
            srcDb.backupDao().insertMoneyReminders(
                listOf(
                    MoneyReminderEntity(
                        title = "Quit Rent",
                        dateEpochDay = 20_425L,
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L
                    )
                )
            )

            val pdfBytes = "%PDF-1.4\n%v120-public-receipt".encodeToByteArray()
            assertTrue(srcStorage.writeRestoredFile("receipt_v120_clinic.pdf", pdfBytes))
            val receiptId = srcReceipts.saveReceipt(
                ReceiptRecord(
                    transactionId = txId,
                    merchant = "Klinik Pergigian",
                    dateEpochDay = 20_418L,
                    amountSen = 3_500L,
                    category = "Medical",
                    note = "Receipt vault parity",
                    taxTag = ReceiptTaxTag.MEDICAL,
                    fileRelativePath = "receipt_v120_clinic.pdf",
                    mimeType = "application/pdf",
                    originalFileName = "clinic.pdf",
                    fileSizeBytes = pdfBytes.size.toLong()
                )
            )
            srcDb.backupDao().insertEarnedMilestones(
                listOf(EarnedMilestoneEntity(milestoneKey = "FIRST_GOAL", earnedAtEpochMs = 100L))
            )
            srcDb.backupDao().insertTaxReliefEntries(
                listOf(
                    TaxReliefEntryEntity(
                        assessmentYear = 2026,
                        categoryKey = "MEDICAL_SELF_FAMILY",
                        title = "Dental relief",
                        dateEpochDay = 20_418L,
                        totalSpentSen = 3_500L,
                        eligibleAmountSen = 3_500L,
                        receiptId = receiptId,
                        reviewStatus = "READY",
                        note = "",
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L
                    )
                )
            )

            val srcCoordinator = BackupRestoreCoordinator(
                backupDao = srcDb.backupDao(),
                userPreferencesRepository = prefsRepo,
                notificationPreferencesRepository = fakeNotifPrefsRepo(),
                notificationHistoryRepository = RoomNotificationHistoryRepository(srcDb.notificationHistoryDao()),
                securityRepository = fakeSecurityRepo(),
                reminderScheduler = scheduler,
                widgetUpdateCoordinator = widgetCoordinator,
                ioDispatcher = Dispatchers.Unconfined,
                receiptFileStorage = srcStorage
            )
            val exportedBytes = srcCoordinator.createEncryptedBackup("CrossEdition#2026".toCharArray())

            // Pre-populate Developer Center credentials in Personal Edition before restoring public backup.
            val devPat = "github_pat_11PARITYKEEP_98765"
            devVault.saveDeveloperPin("246810")
            devVault.saveGithubPat(devPat)

            val dstCoordinator = BackupRestoreCoordinator(
                backupDao = dstDb.backupDao(),
                userPreferencesRepository = prefsRepo,
                notificationPreferencesRepository = fakeNotifPrefsRepo(),
                notificationHistoryRepository = RoomNotificationHistoryRepository(dstDb.notificationHistoryDao()),
                securityRepository = fakeSecurityRepo(),
                reminderScheduler = scheduler,
                widgetUpdateCoordinator = widgetCoordinator,
                ioDispatcher = Dispatchers.Unconfined,
                receiptFileStorage = dstStorage
            )
            val decrypted = dstCoordinator.decryptAndPreviewBackup(exportedBytes, "CrossEdition#2026".toCharArray())
            val payloadJson = BackupSerializer.encodePayload(
                ledger = decrypted.ledger,
                preferences = decrypted.preferences,
                createdAtEpochMs = decrypted.header.createdAtEpochMs,
                receiptFiles = decrypted.receiptFiles
            )
            assertFalse(payloadJson.contains(devPat))
            assertFalse(payloadJson.contains("duit_dev_center_prefs"))

            dstCoordinator.restoreBackupAtomically(decrypted)

            val dstSnap = dstDb.backupDao().exportFullSnapshot()
            assertEquals("Maybank MAE", dstSnap.wallets.single().name)
            assertEquals(446_500L, dstSnap.wallets.single().balanceSen)
            assertEquals("Klinik Pergigian", dstSnap.transactions.single().title)
            assertEquals("Medical Budget", dstSnap.budgets.single().name)
            assertEquals("Unifi Fiber", dstSnap.commitments.single().title)
            assertEquals("Emergency Fund", dstSnap.savingsGoals.single().name)
            assertEquals(1, dstSnap.savingsChallenges.size)
            assertEquals("Quit Rent", dstSnap.moneyReminders.single().title)
            assertEquals("FIRST_GOAL", dstSnap.earnedMilestones.single().milestoneKey)
            assertEquals(3_500L, dstSnap.taxReliefEntries.single().eligibleAmountSen)
            assertEquals("receipt_v120_clinic.pdf", dstSnap.receipts.single().fileRelativePath)
            assertArrayEquals(pdfBytes, dstStorage.readFileBytes("receipt_v120_clinic.pdf"))

            // DevCenter credentials remain intact after restore.
            assertTrue(devVault.verifyDeveloperPin("246810"))
            assertEquals(devPat, devVault.readGithubPat())
        } finally {
            devVault.clearAllCredentials()
            srcDb.close()
            dstDb.close()
            srcDir.deleteRecursively()
            dstDir.deleteRecursively()
        }
    }
}
