package com.diniy.duit.backup

import com.diniy.duit.data.local.dao.BackupDao
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.notifications.NotificationHistoryRepository
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
            notificationHistoryCount = snap.notificationHistory.size
        )
    }

    suspend fun createEncryptedBackup(password: CharArray): ByteArray = withContext(ioDispatcher) {
        val passwordCopy = password.copyOf()
        try {
            val ledger = backupDao.exportFullSnapshot()
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
                createdAtEpochMs = nowMs
            )
            BackupCryptoEngine.encrypt(
                plaintextJson = payloadJson,
                password = passwordCopy,
                createdAtEpochMs = nowMs
            )
        } finally {
            passwordCopy.fill('\u0000')
        }
    }

    suspend fun inspectBackupFile(rawBytes: ByteArray): BackupEnvelopeHeader = withContext(ioDispatcher) {
        BackupCryptoEngine.inspectEnvelope(rawBytes)
    }

    suspend fun decryptAndPreviewBackup(
        rawBytes: ByteArray,
        password: CharArray
    ): DecryptedBackupBundle = withContext(ioDispatcher) {
        val passwordCopy = password.copyOf()
        try {
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

        // Atomic Room replacement: throws & rolls back cleanly on any SQLite/FK failure.
        backupDao.replaceAllLedgerDataAtomically(bundle.ledger)

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
        userPreferencesRepository.setDefaultWalletId(null)
        reminderScheduler.enqueueImmediateSync()
        widgetUpdateCoordinator.refreshAllWidgetsNow()
    }
}
