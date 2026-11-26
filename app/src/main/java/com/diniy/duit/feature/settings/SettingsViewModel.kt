package com.diniy.duit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.backup.BackupEnvelopeHeader
import com.diniy.duit.backup.BackupException
import com.diniy.duit.backup.BackupRestoreCoordinator
import com.diniy.duit.backup.DecryptedBackupBundle
import com.diniy.duit.backup.LedgerStorageSummary
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.security.AppLockManager
import com.diniy.duit.security.AppLockUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class RestoreStage {
    data object Idle : RestoreStage()
    data class AwaitingPassword(
        val rawBytes: ByteArray,
        val header: BackupEnvelopeHeader,
        val fileName: String
    ) : RestoreStage()
    data class PreviewReady(
        val bundle: DecryptedBackupBundle,
        val fileName: String
    ) : RestoreStage()
}

data class SettingsHubUiState(
    val isLoading: Boolean = true,
    val appSettings: AppSettingsSnapshot = AppSettingsSnapshot(),
    val notificationSettings: NotificationSettingsSnapshot = NotificationSettingsSnapshot(),
    val securityState: AppLockUiState = AppLockUiState(),
    val wallets: List<Wallet> = emptyList(),
    val storageSummary: LedgerStorageSummary = LedgerStorageSummary(),
    val restoreStage: RestoreStage = RestoreStage.Idle,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    notificationPreferencesRepository: NotificationPreferencesRepository,
    walletRepository: WalletRepository,
    private val appLockManager: AppLockManager,
    private val backupRestoreCoordinator: BackupRestoreCoordinator
) : ViewModel() {

    private val storageSummaryFlow = MutableStateFlow(LedgerStorageSummary())
    private val restoreStageFlow = MutableStateFlow<RestoreStage>(RestoreStage.Idle)
    private val busyFlow = MutableStateFlow(false)
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<SettingsHubUiState> = combine(
        userPreferencesRepository.observeAppSettings(),
        notificationPreferencesRepository.observeSettings(),
        appLockManager.uiState,
        walletRepository.observeWallets(),
        combine(storageSummaryFlow, restoreStageFlow, busyFlow, feedbackFlow) { storage, restore, busy, feedback ->
            OpState(storage, restore, busy, feedback.first, feedback.second)
        }
    ) { appSettings, notifSettings, secState, wallets, op ->
        SettingsHubUiState(
            isLoading = false,
            appSettings = appSettings,
            notificationSettings = notifSettings,
            securityState = secState,
            wallets = wallets.filter { !it.isArchived },
            storageSummary = op.storage,
            restoreStage = op.restoreStage,
            isBusy = op.busy,
            statusMessage = op.status,
            errorMessage = op.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsHubUiState(isLoading = true)
    )

    init {
        refreshStorageSummary()
    }

    fun refreshStorageSummary() {
        viewModelScope.launch {
            runCatching { backupRestoreCoordinator.loadStorageSummary() }
                .onSuccess { storageSummaryFlow.value = it }
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null to null
    }

    fun reportError(message: String) {
        feedbackFlow.value = null to message
    }

    // Appearance
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setVisualTheme(theme: com.diniy.duit.domain.model.VisualTheme) {
        viewModelScope.launch { userPreferencesRepository.setVisualTheme(theme) }
    }

    fun setGlassIntensity(intensity: GlassIntensity) {
        viewModelScope.launch { userPreferencesRepository.setGlassIntensity(intensity) }
    }

    // Motion & Haptics
    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setHapticFeedbackEnabled(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setReduceMotion(enabled) }
    }

    fun setAnimationIntensity(intensity: AnimationIntensity) {
        viewModelScope.launch { userPreferencesRepository.setAnimationIntensity(intensity) }
    }

    // Dashboard Customization
    fun setDashboardSectionVisible(section: DashboardSectionKey, visible: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDashboardSectionVisible(section, visible) }
    }

    fun moveDashboardSection(section: DashboardSectionKey, delta: Int) {
        viewModelScope.launch {
            val current = uiState.value.appSettings.dashboardSectionOrder.toMutableList()
            val idx = current.indexOf(section)
            val target = idx + delta
            if (idx >= 0 && target in current.indices) {
                val item = current.removeAt(idx)
                current.add(target, item)
                userPreferencesRepository.setDashboardSectionOrder(current)
            }
        }
    }

    fun restoreDefaultDashboardLayout() {
        viewModelScope.launch {
            userPreferencesRepository.restoreDefaultDashboardLayout()
            feedbackFlow.value = "Dashboard layout restored to default." to null
        }
    }

    // Financial Preferences
    fun setDefaultWalletId(walletId: Long?) {
        viewModelScope.launch { userPreferencesRepository.setDefaultWalletId(walletId) }
    }

    fun setDefaultBudgetPeriodType(periodType: BudgetPeriodType) {
        viewModelScope.launch { userPreferencesRepository.setDefaultBudgetPeriodType(periodType) }
    }

    fun setAutoAdvanceRecurringCommitments(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setAutoAdvanceRecurringCommitments(enabled) }
    }

    fun setCommissionFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setCommissionFeatureEnabled(enabled) }
    }

    fun setCommissionTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setCommissionTrackingEnabled(enabled) }
    }

    fun saveCommissionFormulaConfig(config: CommissionFormulaConfig) {
        viewModelScope.launch { userPreferencesRepository.saveCommissionFormulaConfig(config) }
    }

    fun resetCommissionFormulaConfig() {
        viewModelScope.launch { userPreferencesRepository.resetCommissionFormulaConfig() }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setCrashReportingEnabled(enabled) }
    }

    // Encrypted Backup
    fun buildEncryptedBackup(
        password: CharArray,
        onReady: (ByteArray) -> Unit
    ) {
        if (busyFlow.value) return
        viewModelScope.launch {
            busyFlow.value = true
            feedbackFlow.value = null to null
            com.diniy.duit.diagnostics.CrashReporting.logEvent(
                com.diniy.duit.diagnostics.CrashBreadcrumbEvent.BACKUP_STARTED
            )
            runCatching {
                backupRestoreCoordinator.createEncryptedBackup(password)
            }.onSuccess { bytes ->
                busyFlow.value = false
                onReady(bytes)
            }.onFailure { err ->
                busyFlow.value = false
                if (err !is BackupException) {
                    com.diniy.duit.diagnostics.CrashReporting.recordNonFatal(
                        com.diniy.duit.diagnostics.CrashDiagnosticCategory.BACKUP_ENGINE,
                        err
                    )
                }
                feedbackFlow.value = null to (err.message ?: "Unable to create encrypted backup.")
            }
        }
    }

    fun notifyBackupWritten(fileName: String) {
        feedbackFlow.value = "Encrypted backup saved ($fileName)." to null
    }

    // Safe Restore Workflow
    fun inspectSelectedBackup(rawBytes: ByteArray, fileName: String) {
        if (busyFlow.value) return
        viewModelScope.launch {
            busyFlow.value = true
            feedbackFlow.value = null to null
            runCatching {
                backupRestoreCoordinator.inspectBackupFile(rawBytes)
            }.onSuccess { header ->
                busyFlow.value = false
                restoreStageFlow.value = RestoreStage.AwaitingPassword(rawBytes, header, fileName)
            }.onFailure { err ->
                busyFlow.value = false
                restoreStageFlow.value = RestoreStage.Idle
                feedbackFlow.value = null to (err.message ?: "Selected file is not a valid duit. backup.")
            }
        }
    }

    fun decryptSelectedBackup(password: CharArray) {
        if (busyFlow.value) return
        val stage = restoreStageFlow.value as? RestoreStage.AwaitingPassword ?: return
        viewModelScope.launch {
            busyFlow.value = true
            feedbackFlow.value = null to null
            runCatching {
                backupRestoreCoordinator.decryptAndPreviewBackup(stage.rawBytes, password)
            }.onSuccess { bundle ->
                busyFlow.value = false
                restoreStageFlow.value = RestoreStage.PreviewReady(bundle, stage.fileName)
            }.onFailure { err ->
                busyFlow.value = false
                val msg = if (err is BackupException) err.message else "Unable to decrypt backup."
                feedbackFlow.value = null to (msg ?: "Incorrect password or corrupted backup.")
            }
        }
    }

    fun confirmRestoreBackup() {
        if (busyFlow.value) return
        val stage = restoreStageFlow.value as? RestoreStage.PreviewReady ?: return
        viewModelScope.launch {
            busyFlow.value = true
            feedbackFlow.value = null to null
            com.diniy.duit.diagnostics.CrashReporting.logEvent(
                com.diniy.duit.diagnostics.CrashBreadcrumbEvent.RESTORE_STARTED
            )
            runCatching {
                backupRestoreCoordinator.restoreBackupAtomically(stage.bundle)
                backupRestoreCoordinator.loadStorageSummary()
            }.onSuccess { updatedSummary ->
                busyFlow.value = false
                restoreStageFlow.value = RestoreStage.Idle
                storageSummaryFlow.value = updatedSummary
                val preview = stage.bundle.preview
                feedbackFlow.value = (
                    if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Backup restored cleanly (${countLabel(preview.transactionCount, "transaction", "transactions")}, ${countLabel(preview.walletCount, "wallet", "wallets")})."
                    else "Backup restored cleanly (${preview.transactionCount} transactions, ${preview.walletCount} wallets)."
                    ) to null
            }.onFailure { err ->
                busyFlow.value = false
                if (err !is BackupException) {
                    com.diniy.duit.diagnostics.CrashReporting.recordNonFatal(
                        com.diniy.duit.diagnostics.CrashDiagnosticCategory.RESTORE_ENGINE,
                        err
                    )
                }
                feedbackFlow.value = null to (err.message ?: "Restore failed cleanly. Existing database preserved.")
            }
        }
    }

    fun cancelRestoreFlow() {
        restoreStageFlow.value = RestoreStage.Idle
    }

    // CSV Export
    fun buildCsvExport(
        includeAllRecords: Boolean,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            busyFlow.value = true
            feedbackFlow.value = null to null
            runCatching {
                if (includeAllRecords) backupRestoreCoordinator.exportFullLedgerCsv()
                else backupRestoreCoordinator.exportTransactionsCsv()
            }.onSuccess { csv ->
                busyFlow.value = false
                onReady(csv)
            }.onFailure { err ->
                busyFlow.value = false
                feedbackFlow.value = null to (err.message ?: "Unable to prepare CSV export.")
            }
        }
    }

    fun notifyCsvWritten(fileName: String) {
        feedbackFlow.value = "CSV export saved ($fileName)." to null
    }

    // Data Management
    fun restoreDefaultPreferences() {
        viewModelScope.launch {
            busyFlow.value = true
            runCatching {
                backupRestoreCoordinator.restoreDefaultPreferences()
            }.onSuccess {
                busyFlow.value = false
                feedbackFlow.value = "Default preferences restored (financial records and PIN unchanged)." to null
            }.onFailure { err ->
                busyFlow.value = false
                feedbackFlow.value = null to (err.message ?: "Failed to restore default preferences.")
            }
        }
    }

    fun clearNonEssentialData() {
        viewModelScope.launch {
            busyFlow.value = true
            runCatching {
                backupRestoreCoordinator.clearNonEssentialData()
                backupRestoreCoordinator.loadStorageSummary()
            }.onSuccess { summary ->
                busyFlow.value = false
                storageSummaryFlow.value = summary
                feedbackFlow.value = "Cleared non-essential notification history." to null
            }.onFailure { err ->
                busyFlow.value = false
                feedbackFlow.value = null to (err.message ?: "Failed to clear temporary data.")
            }
        }
    }

    fun deleteAllFinancialData() {
        viewModelScope.launch {
            busyFlow.value = true
            runCatching {
                backupRestoreCoordinator.deleteAllFinancialData()
                backupRestoreCoordinator.loadStorageSummary()
            }.onSuccess { summary ->
                busyFlow.value = false
                storageSummaryFlow.value = summary
                feedbackFlow.value = "All financial records deleted cleanly (PIN and lock preferences preserved)." to null
            }.onFailure { err ->
                busyFlow.value = false
                feedbackFlow.value = null to (err.message ?: "Unable to delete financial data.")
            }
        }
    }

    private data class OpState(
        val storage: LedgerStorageSummary,
        val restoreStage: RestoreStage,
        val busy: Boolean,
        val status: String?,
        val error: String?
    )

    companion object {
        fun factory(
            userPreferencesRepository: UserPreferencesRepository,
            notificationPreferencesRepository: NotificationPreferencesRepository,
            walletRepository: WalletRepository,
            appLockManager: AppLockManager,
            backupRestoreCoordinator: BackupRestoreCoordinator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    userPreferencesRepository = userPreferencesRepository,
                    notificationPreferencesRepository = notificationPreferencesRepository,
                    walletRepository = walletRepository,
                    appLockManager = appLockManager,
                    backupRestoreCoordinator = backupRestoreCoordinator
                ) as T
            }
        }
    }
}
