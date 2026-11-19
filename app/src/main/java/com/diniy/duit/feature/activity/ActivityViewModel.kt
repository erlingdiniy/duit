package com.diniy.duit.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.preview.SamplePreviewData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ActivityFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSE("Expenses"),
    TRANSFER("Transfers")
}

data class ActivityDateSection(
    val date: LocalDate,
    val headerLabel: String,
    val transactions: List<FinanceTransaction>
)

data class ActivityUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isSamplePreview: Boolean = false,
    val selectedFilter: ActivityFilter = ActivityFilter.ALL,
    val searchQuery: String = "",
    val dateSections: List<ActivityDateSection> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val walletsById: Map<Long, Wallet> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap()
)

class ActivityViewModel(
    private val transactionRepository: TransactionRepository,
    walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val planningRepository: PlanningRepository,
    private val previewModeController: PreviewModeController
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ActivityFilter.ALL)
    private val searchQueryFlow = MutableStateFlow("")
    private val submittingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            runCatching { categoryRepository.ensureDefaultCategoriesSeeded() }
        }
    }

    private val baseDataFlow = combine(
        transactionRepository.observeTransactions(),
        walletRepository.observeWallets(),
        categoryRepository.observeCategories(),
        previewModeController.isSamplePreviewEnabled
    ) { tx, wallets, categories, isPreview ->
        BaseDataBundle(tx, wallets, categories, isPreview)
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        baseDataFlow,
        filterFlow,
        searchQueryFlow,
        submittingFlow,
        errorFlow
    ) { base, filter, query, isSubmitting, error ->
        val sourceTx = if (base.isPreview) SamplePreviewData.transactions else base.transactions
        val sourceWallets = if (base.isPreview) SamplePreviewData.wallets else base.wallets
        val walletsById = sourceWallets.associateBy { it.id }
        val trimmedQuery = query.trim().lowercase()

        val filteredByType = when (filter) {
            ActivityFilter.ALL -> sourceTx
            ActivityFilter.INCOME -> sourceTx.filter { it.type == TransactionType.INCOME }
            ActivityFilter.EXPENSE -> sourceTx.filter { it.type == TransactionType.EXPENSE }
            ActivityFilter.TRANSFER -> sourceTx.filter { it.type == TransactionType.TRANSFER }
        }

        val filtered = if (trimmedQuery.isEmpty()) {
            filteredByType
        } else {
            filteredByType.filter { tx ->
                val srcName = walletsById[tx.walletId]?.name?.lowercase().orEmpty()
                val dstName = tx.targetWalletId?.let { walletsById[it]?.name?.lowercase() }.orEmpty()
                tx.title.lowercase().contains(trimmedQuery) ||
                    tx.category.lowercase().contains(trimmedQuery) ||
                    tx.note.lowercase().contains(trimmedQuery) ||
                    srcName.contains(trimmedQuery) ||
                    dstName.contains(trimmedQuery)
            }
        }

        val today = LocalDate.now()
        val sections = filtered
            .groupBy { DateTimeFormatters.toLocalDate(it.occurredAtEpochMs) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                ActivityDateSection(
                    date = date,
                    headerLabel = DateTimeFormatters.formatDateGroupHeader(date, today),
                    transactions = items
                )
            }

        ActivityUiState(
            isLoading = false,
            isSubmitting = isSubmitting,
            errorMessage = error,
            isSamplePreview = base.isPreview,
            selectedFilter = filter,
            searchQuery = query,
            dateSections = sections,
            wallets = sourceWallets,
            walletsById = walletsById,
            categories = base.categories,
            categoriesById = base.categories.associateBy { it.id }
        )
    }.catch { throwable ->
        emit(
            ActivityUiState(
                isLoading = false,
                errorMessage = throwable.message ?: "Unable to load transactions."
            )
        )
    // Financial state is derived off the main thread so DB writes never stall frames (Phase 11P).
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState(isLoading = true)
    )

    fun selectFilter(filter: ActivityFilter) {
        filterFlow.value = filter
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun saveTransaction(
        transaction: FinanceTransaction,
        startNewSalaryCycle: Boolean = false,
        salaryCycleLabel: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (submittingFlow.value) return
        submittingFlow.value = true

        viewModelScope.launch {
            runCatching {
                if (transaction.id == 0L) {
                    if (startNewSalaryCycle && transaction.type == TransactionType.INCOME) {
                        planningRepository.recordSalaryTransactionAndStartCycle(
                            transaction = transaction,
                            cycleLabel = salaryCycleLabel
                        )
                    } else {
                        transactionRepository.recordTransaction(transaction)
                    }
                } else {
                    transactionRepository.updateTransaction(transaction)
                }
            }.onSuccess {
                submittingFlow.value = false
                previewModeController.setSamplePreviewEnabled(false)
                onSuccess()
            }.onFailure { throwable ->
                submittingFlow.value = false
                onError(throwable.message ?: "Unable to save transaction.")
            }
        }
    }

    fun deleteTransaction(
        transactionId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (submittingFlow.value) return
        submittingFlow.value = true

        viewModelScope.launch {
            runCatching { transactionRepository.deleteTransaction(transactionId) }
                .onSuccess {
                    submittingFlow.value = false
                    onSuccess()
                }
                .onFailure { throwable ->
                    submittingFlow.value = false
                    onError(throwable.message ?: "Unable to delete transaction.")
                }
        }
    }

    fun createCategory(
        name: String,
        type: TransactionType,
        iconKey: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                categoryRepository.createCategory(name, type, iconKey, accent)
            }.onSuccess {
                onDone()
            }.onFailure { err ->
                onError(err.message ?: "Could not create category.")
            }
        }
    }

    fun renameCategory(
        id: Long,
        newName: String,
        iconKey: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                categoryRepository.renameCategory(id, newName, iconKey, accent)
            }.onSuccess {
                onDone()
            }.onFailure { err ->
                onError(err.message ?: "Could not rename category.")
            }
        }
    }

    fun setCategoryArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            runCatching { categoryRepository.setCategoryArchived(id, archived) }
                .onFailure { errorFlow.value = it.message ?: "Unable to update category." }
        }
    }

    fun exitSamplePreview() {
        previewModeController.setSamplePreviewEnabled(false)
    }

    fun enableSamplePreview() {
        previewModeController.setSamplePreviewEnabled(true)
    }

    fun dismissError() {
        errorFlow.value = null
    }

    private data class BaseDataBundle(
        val transactions: List<FinanceTransaction>,
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val isPreview: Boolean
    )

    companion object {
        fun factory(
            transactionRepository: TransactionRepository,
            walletRepository: WalletRepository,
            categoryRepository: CategoryRepository,
            planningRepository: PlanningRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActivityViewModel(
                    transactionRepository = transactionRepository,
                    walletRepository = walletRepository,
                    categoryRepository = categoryRepository,
                    planningRepository = planningRepository,
                    previewModeController = previewModeController
                ) as T
            }
        }
    }
}
