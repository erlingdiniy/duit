package com.diniy.duit.backup

import com.diniy.duit.data.local.dao.BackupDao
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.notifications.NotificationHistoryRepository
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Coordinates encrypted local backup, validated atomic restore, plaintext CSV export,
 * storage statistics, preference resets, and authenticated data deletion.
 */
class BackupRestoreCoordinator(
    private val backupDao: BackupDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val securityRepository: SecurityRepository,
    private val reminderScheduler: ReminderScheduler,
    private val widgetUpdateCoordinator: WidgetUpdateCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val receiptFileStorage: ReceiptFileStorage? = null
) {

    suspend fun loadStorageSummary(): LedgerStorageSummary = withContext(ioDispatcher) {
        val snap = backupDao.exportFullSnapshot()
        LedgerStorageSummary(
            walletCount = snap.wallets.size,
            transactionCount = snap.transactions.size,
            commitmentCount = snap.commitments.size,
            payLaterPlanCount = snap.payLaterPlans.size,
            debtCount = snap.debts.size,
            savingsGoalCount = snap.savingsGoals.size,
            budgetCount = snap.budgets.size,
            commissionCount = snap.commissions.size,
            salaryCycleCount = snap.salaryCycles.size,
            notificationHistoryCount = snap.notificationHistory.size,
            receiptCount = snap.receipts.size
        )
    }

    suspend fun createEncryptedBackup(password: CharArray): ByteArray = withContext(ioDispatcher) {
        val passwordCopy = password.copyOf()
        try {
            val ledger = backupDao.exportFullSnapshot()
            val receiptFiles = buildMap<String, ByteArray> {
                val storage = receiptFileStorage ?: return@buildMap
                // Size check before reading any bytes: fail with a clear message instead of OOM.
                val totalBytes = ledger.receipts.sumOf { storage.storedFileSize(it.fileRelativePath) }
                if (totalBytes > ReceiptFileStorage.MAX_BACKUP_RECEIPT_BYTES) {
                    throw BackupFormatException(
                        "Backup not created: receipt files total ${BackupCryptoEngine.ceilMb(totalBytes)} MB, " +
                            "but a backup can hold up to " +
                            "${BackupCryptoEngine.ceilMb(ReceiptFileStorage.MAX_BACKUP_RECEIPT_BYTES)} MB of receipts. " +
                            "Remove or replace large receipt files in Receipt Vault, then try again. Nothing was changed."
                    )
                }
                ledger.receipts.forEach { r ->
                    if (r.fileRelativePath.isNotBlank()) {
                        storage.readFileBytes(r.fileRelativePath)?.let { put(r.fileRelativePath, it) }
                    }
                }
            }
            val appSettings = userPreferencesRepository.observeAppSettings().first()
            val notifSettings = notificationPreferencesRepository.observeSettings().first()
            val sec = securityRepository.observeSecurityConfig().first()

            val portablePrefs = PortablePreferencesBackup(
                appSettings = appSettings,
                notificationSettings = notifSettings,
                privacyPreferences = PortablePrivacyPreferences(
                    hideSensitiveBalances = sec.hideSensitiveBalances,
                    privacyModeEnabled = sec.privacyModeEnabled,
                    alwaysMaskWidgetAmounts = sec.alwaysMaskWidgetAmounts
                )
            )
            val nowMs = System.currentTimeMillis()
            val payloadJson = BackupSerializer.encodePayload(
                ledger = ledger,
                preferences = portablePrefs,
                createdAtEpochMs = nowMs,
                receiptFiles = receiptFiles
            )
            val encrypted = BackupCryptoEngine.encrypt(
                plaintextJson = payloadJson,
                password = passwordCopy,
                createdAtEpochMs = nowMs
            )
            // Never hand out a backup this app would refuse to restore.
            if (encrypted.size.toLong() > BackupCryptoEngine.MAX_BACKUP_FILE_BYTES) {
                throw BackupFormatException(
                    "Backup not created: it would be ${BackupCryptoEngine.ceilMb(encrypted.size.toLong())} MB, " +
                        "over the ${BackupCryptoEngine.ceilMb(BackupCryptoEngine.MAX_BACKUP_FILE_BYTES)} MB limit. " +
                        "Remove some receipt files, then try again. Nothing was changed."
                )
            }
            encrypted
        } finally {
            passwordCopy.fill('\u0000')
        }
    }

    suspend fun inspectBackupFile(rawBytes: ByteArray): BackupEnvelopeHeader = withContext(ioDispatcher) {
        BackupCryptoEngine.requireBackupSizeWithinLimit(rawBytes.size.toLong())
        BackupCryptoEngine.inspectEnvelope(rawBytes)
    }

    suspend fun decryptAndPreviewBackup(
        rawBytes: ByteArray,
        password: CharArray
    ): DecryptedBackupBundle = withContext(ioDispatcher) {
        val passwordCopy = password.copyOf()
        try {
            BackupCryptoEngine.requireBackupSizeWithinLimit(rawBytes.size.toLong())
            val header = BackupCryptoEngine.inspectEnvelope(rawBytes)
            val plaintext = BackupCryptoEngine.decrypt(rawBytes, passwordCopy)
            BackupSerializer.decodePayload(header, plaintext)
        } finally {
            passwordCopy.fill('\u0000')
        }
    }

    /**
     * Atomically replaces all Room financial records inside one SQLite transaction,
     * then restores portable DataStore preferences without overwriting current device PIN/biometric state.
     */
    suspend fun restoreBackupAtomically(bundle: DecryptedBackupBundle) = withContext(ioDispatcher) {
        if (bundle.preview.dbSchemaVersion > BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION) {
            throw BackupVersionException(
                "Backup requires newer app version (schema v${bundle.preview.dbSchemaVersion})."
            )
        }

        // Stage receipt files first: a write failure aborts before any Room or live-file change.
        val staging = receiptFileStorage?.stageRestoredFiles(bundle.receiptFiles)
        // Once Room commit starts, run commit/preference swap non-cancellably so navigation cannot split state.
        withContext(NonCancellable) {
            try {
                // Atomic Room replacement: throws & rolls back cleanly on any SQLite/FK failure.
                backupDao.replaceAllLedgerDataAtomically(bundle.ledger)
            } catch (t: Throwable) {
                staging?.let { receiptFileStorage?.discardStagedRestore(it) }
                throw t
            }
            staging?.let { receiptFileStorage?.commitStagedRestore(it) }

            val validWalletIds = bundle.ledger.wallets.map { it.id }.toSet()
            val sanitizedAppSettings = bundle.preferences.appSettings.let { prefs ->
                if (prefs.defaultWalletId != null && prefs.defaultWalletId !in validWalletIds) {
                    prefs.copy(defaultWalletId = null)
                } else {
                    prefs
                }
            }

            userPreferencesRepository.applyPortableSettings(sanitizedAppSettings)
            notificationPreferencesRepository.applyPortableSettings(bundle.preferences.notificationSettings)
            securityRepository.setHideSensitiveBalances(bundle.preferences.privacyPreferences.hideSensitiveBalances)
            securityRepository.setPrivacyModeEnabled(bundle.preferences.privacyPreferences.privacyModeEnabled)
            securityRepository.setAlwaysMaskWidgetAmounts(bundle.preferences.privacyPreferences.alwaysMaskWidgetAmounts)

            reminderScheduler.enqueueImmediateSync()
            widgetUpdateCoordinator.refreshAllWidgetsNow()
        }
    }

    suspend fun exportTransactionsCsv(): String = withContext(ioDispatcher) {
        CsvExporter.exportTransactionsCsv(backupDao.exportFullSnapshot())
    }

    suspend fun exportFullLedgerCsv(): String = withContext(ioDispatcher) {
        CsvExporter.exportFullLedgerCsv(backupDao.exportFullSnapshot())
    }

    suspend fun restoreDefaultPreferences() = withContext(ioDispatcher) {
        userPreferencesRepository.restoreDefaultPreferences()
        notificationPreferencesRepository.resetDefaults()
        securityRepository.resetPrivacyDefaults()
        reminderScheduler.enqueueImmediateSync()
        widgetUpdateCoordinator.refreshAllWidgetsNow()
    }

    suspend fun clearNonEssentialData() = withContext(ioDispatcher) {
        notificationHistoryRepository.clearHistory()
        reminderScheduler.enqueueImmediateSync()
    }

    /**
     * Deletes all financial records atomically while preserving database schema integrity,
     * user's current PIN / biometric lock configuration, and notification preferences.
     */
    suspend fun deleteAllFinancialData() = withContext(ioDispatcher) {
        backupDao.deleteAllLedgerDataAtomically()
        receiptFileStorage?.clearAllFiles()
        userPreferencesRepository.setDefaultWalletId(null)
        reminderScheduler.enqueueImmediateSync()
        widgetUpdateCoordinator.refreshAllWidgetsNow()
    }
}

