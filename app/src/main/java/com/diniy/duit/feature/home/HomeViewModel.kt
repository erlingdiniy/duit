package com.diniy.duit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.DashboardSummary
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.preview.SamplePreviewData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSamplePreview: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appSettings: AppSettingsSnapshot = AppSettingsSnapshot(),
    val summary: DashboardSummary = FinanceSummaryCalculator.calculate(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
    ),
    val wallets: List<Wallet> = emptyList(),
    val archivedWallets: List<Wallet> = emptyList(),
    val transactions: List<FinanceTransaction> = emptyList(),
    val upcomingCommitments: List<Commitment> = emptyList(),
    val activeSalaryCycle: SalaryCycle? = null
)

class HomeViewModel(
    private val walletRepository: WalletRepository,
    transactionRepository: TransactionRepository,
    planningRepository: PlanningRepository,
    commissionRepository: CommissionRepository,
    savingsRepository: SavingsRepository,
    obligationRepository: ObligationRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val previewModeController: PreviewModeController
) : ViewModel() {

    private val actionError = MutableStateFlow<String?>(null)

    private val coreLedgerFlow = combine(
        walletRepository.observeWallets(),
        transactionRepository.observeTransactions(),
        planningRepository.observeCommitments(),
        commissionRepository.observeCommissions(),
        planningRepository.observeActiveCycle()
    ) { wallets, transactions, commitments, commissions, activeCycle ->
        CoreLedgerBundle(wallets, transactions, commitments, commissions, activeCycle)
    }

    private val obligationBundleFlow = combine(
        savingsRepository.observeSavingsGoals(),
        savingsRepository.observeSavingsAllocations(),
        obligationRepository.observePayLaterPlans(),
        obligationRepository.observeDebts()
    ) { savingsGoals, savingsAllocations, payLaterPlans, debts ->
        ObligationBundle(
            savingsGoals = savingsGoals,
            savingsAllocations = savingsAllocations,
            payLaterPlans = payLaterPlans,
            debts = debts
        )
    }

    private val ledgerFlow = combine(
        coreLedgerFlow,
        obligationBundleFlow
    ) { core, obligations ->
        LedgerBundle(
            wallets = core.wallets,
            transactions = core.transactions,
            commitments = core.commitments,
            commissions = core.commissions,
            activeCycle = core.activeCycle,
            savingsGoals = obligations.savingsGoals,
            savingsAllocations = obligations.savingsAllocations,
            payLaterPlans = obligations.payLaterPlans,
            debts = obligations.debts
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        ledgerFlow,
        preferencesRepository.observeAppSettings(),
        previewModeController.isSamplePreviewEnabled,
        actionError
    ) { ledger, appSettings, isPreview, err ->
        if (isPreview) {
            HomeUiState(
                isLoading = false,
                errorMessage = err,
                isSamplePreview = true,
                themeMode = appSettings.themeMode,
                appSettings = appSettings,
                summary = SamplePreviewData.summary,
                wallets = SamplePreviewData.wallets.filter { !it.isArchived },
                archivedWallets = emptyList(),
                transactions = SamplePreviewData.transactions,
                upcomingCommitments = SamplePreviewData.commitments.filter {
                    !it.isArchived && (it.status == CommitmentStatus.UPCOMING || it.status == CommitmentStatus.PARTIALLY_PAID)
                },
                activeSalaryCycle = SamplePreviewData.activeCycle
            )
        } else {
            val summary = FinanceSummaryCalculator.calculate(
                wallets = ledger.wallets,
                transactions = ledger.transactions,
                commitments = ledger.commitments,
                commissions = ledger.commissions,
                savingsAllocations = ledger.savingsAllocations,
                savingsGoals = ledger.savingsGoals,
                payLaterPlans = ledger.payLaterPlans,
                debts = ledger.debts,
                activeCycle = ledger.activeCycle
            )
            HomeUiState(
                isLoading = false,
                errorMessage = err,
                isSamplePreview = false,
                themeMode = appSettings.themeMode,
                appSettings = appSettings,
                summary = summary,
                wallets = ledger.wallets.filter { !it.isArchived },
                archivedWallets = ledger.wallets.filter { it.isArchived },
                transactions = ledger.transactions,
                upcomingCommitments = ledger.commitments.filter {
                    !it.isArchived && (it.status == CommitmentStatus.UPCOMING || it.status == CommitmentStatus.PARTIALLY_PAID)
                },
                activeSalaryCycle = ledger.activeCycle
            )
        }
    }.catch { throwable ->
        emit(
            HomeUiState(
                isLoading = false,
                errorMessage = throwable.message ?: "Unable to read local ledger data."
            )
        )
    // Financial state is derived off the main thread so DB writes never stall frames (Phase 11P).
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            runCatching { preferencesRepository.setThemeMode(mode) }
                .onFailure { actionError.value = "Failed to save theme preference." }
        }
    }

    fun setSamplePreview(enabled: Boolean) {
        previewModeController.setSamplePreviewEnabled(enabled)
    }

    fun addWallet(
        name: String,
        type: WalletType,
        openingBalanceInput: String,
        excludeFromSafeToSpend: Boolean,
        includeInTotalBalance: Boolean,
        accentToken: PrismaticAccentToken,
        onSuccess: () -> Unit,
        onValidationError: (String) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onValidationError("Enter a wallet name.")
            return
        }
        val balanceSen = if (openingBalanceInput.isBlank()) {
            0L
        } else {
            MyrFormatter.parseToSen(openingBalanceInput) ?: run {
                onValidationError("Enter a valid RM amount with up to 2 decimal places.")
                return
            }
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                walletRepository.upsertWallet(
                    Wallet(
                        id = 0L,
                        name = trimmedName,
                        type = type,
                        balanceSen = balanceSen,
                        isExcludedFromSafeToSpend = excludeFromSafeToSpend || type == WalletType.SAVINGS || !includeInTotalBalance,
                        accentToken = accentToken,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        isIncludedInTotalBalance = includeInTotalBalance,
                        isArchived = false
                    )
                )
            }.onSuccess {
                previewModeController.setSamplePreviewEnabled(false)
                onSuccess()
            }.onFailure {
                actionError.value = it.message ?: "Could not save wallet."
            }
        }
    }

    fun updateWallet(
        wallet: Wallet,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { walletRepository.updateWalletDetails(wallet) }
                .onSuccess { onSuccess() }
                .onFailure { err ->
                    onError(err.message ?: "Could not update wallet.")
                }
        }
    }

    fun setWalletArchived(
        walletId: Long,
        archived: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { walletRepository.setWalletArchived(walletId, archived) }
                .onSuccess { onSuccess() }
                .onFailure { err ->
                    onError(err.message ?: "Could not update archive status.")
                }
        }
    }

    fun deleteWalletSafely(
        walletId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { walletRepository.deleteWalletSafely(walletId) }
                .onSuccess { onSuccess() }
                .onFailure { err ->
                    val message = when (err) {
                        is FinanceOperationException.WalletHasHistory -> err.message
                        else -> err.message
                    } ?: "Could not delete wallet."
                    onError(message)
                }
        }
    }

    fun dismissError() {
        actionError.value = null
    }

    private data class CoreLedgerBundle(
        val wallets: List<Wallet>,
        val transactions: List<FinanceTransaction>,
        val commitments: List<Commitment>,
        val commissions: List<com.diniy.duit.domain.model.CommissionRecord>,
        val activeCycle: SalaryCycle?
    )

    private data class ObligationBundle(
        val savingsGoals: List<SavingsGoal>,
        val savingsAllocations: List<SavingsAllocation>,
        val payLaterPlans: List<PayLaterPlan>,
        val debts: List<DebtRecord>
    )

    private data class LedgerBundle(
        val wallets: List<Wallet>,
        val transactions: List<FinanceTransaction>,
        val commitments: List<Commitment>,
        val commissions: List<com.diniy.duit.domain.model.CommissionRecord>,
        val activeCycle: SalaryCycle?,
        val savingsGoals: List<SavingsGoal>,
        val savingsAllocations: List<SavingsAllocation>,
        val payLaterPlans: List<PayLaterPlan>,
        val debts: List<DebtRecord>
    )

    companion object {
        fun factory(
            walletRepository: WalletRepository,
            transactionRepository: TransactionRepository,
            planningRepository: PlanningRepository,
            commissionRepository: CommissionRepository,
            savingsRepository: SavingsRepository,
            obligationRepository: ObligationRepository,
            preferencesRepository: UserPreferencesRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    walletRepository = walletRepository,
                    transactionRepository = transactionRepository,
                    planningRepository = planningRepository,
                    commissionRepository = commissionRepository,
                    savingsRepository = savingsRepository,
                    obligationRepository = obligationRepository,
                    preferencesRepository = preferencesRepository,
                    previewModeController = previewModeController
                ) as T
            }
        }
    }
}
