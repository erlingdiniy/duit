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

/** Quick date windows for Activity search; [matches] uses the transaction's local calendar date. */
enum class ActivityDateRange(val label: String) {
    ALL_TIME("All time"),
    LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_90_DAYS("Last 90 days");

    fun matches(date: LocalDate, today: LocalDate): Boolean = when (this) {
        ALL_TIME -> true
        LAST_7_DAYS -> !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
        THIS_MONTH -> date.year == today.year && date.month == today.month
        LAST_MONTH -> today.minusMonths(1).let { date.year == it.year && date.month == it.month }
        LAST_90_DAYS -> !date.isBefore(today.minusDays(89)) && !date.isAfter(today)
    }
}

/** Extra Activity filters (category name, wallet, date). Display-only: records are never changed. */
data class ActivityExtraFilters(
    val category: String? = null,
    val walletId: Long? = null,
    val dateRange: ActivityDateRange = ActivityDateRange.ALL_TIME
) {
    val isActive: Boolean get() = category != null || walletId != null || dateRange != ActivityDateRange.ALL_TIME

    /** Clears a wallet or category choice that no longer exists, so a stale filter can't hide everything. */
    fun sanitized(walletIds: Set<Long>, categoryOptions: List<String>): ActivityExtraFilters = copy(
        walletId = walletId?.takeIf { it in walletIds },
        category = category?.takeIf { chosen -> categoryOptions.any { it.equals(chosen, ignoreCase = true) } }
    )
}

/**
 * Pure search used by Activity. Keyword matches title, category, note, wallet names or the amount
 * (e.g. "24.80" or "24"); a transfer matches a wallet filter on either side.
 */
fun filterActivity(
    transactions: List<FinanceTransaction>,
    type: ActivityFilter,
    query: String,
    extra: ActivityExtraFilters,
    walletsById: Map<Long, Wallet>,
    today: LocalDate
): List<FinanceTransaction> {
    val q = query.trim().lowercase()
    return transactions.filter { tx ->
        val typeOk = when (type) {
            ActivityFilter.ALL -> true
            ActivityFilter.INCOME -> tx.type == TransactionType.INCOME
            ActivityFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
            ActivityFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
        }
        typeOk &&
            (extra.category == null || tx.category.equals(extra.category, ignoreCase = true)) &&
            (extra.walletId == null || tx.walletId == extra.walletId || tx.targetWalletId == extra.walletId) &&
            extra.dateRange.matches(DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs), today) &&
            (q.isEmpty() || tx.title.lowercase().contains(q) || tx.category.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                walletsById[tx.walletId]?.name?.lowercase()?.contains(q) == true ||
                tx.targetWalletId?.let { walletsById[it]?.name?.lowercase()?.contains(q) } == true ||
                // Amount search is a public-edition addition; the personal edition keeps its original matching.
                (com.diniy.duit.BuildConfig.PUBLIC_EDITION && String.format(java.util.Locale.ROOT, "%d.%02d", tx.amountSen / 100, tx.amountSen % 100).contains(q)))
    }
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
    val categoriesById: Map<Long, Category> = emptyMap(),
    val extraFilters: ActivityExtraFilters = ActivityExtraFilters(),
    /** Category names that actually appear in this ledger, for the Category filter. */
    val categoryOptions: List<String> = emptyList()
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
    private val extraFiltersFlow = MutableStateFlow(ActivityExtraFilters())
    private val criteriaFlow = combine(filterFlow, searchQueryFlow, extraFiltersFlow) { f, q, e -> Triple(f, q, e) }
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
        criteriaFlow,
        submittingFlow,
        errorFlow
    ) { base, (filter, query, rawExtra), isSubmitting, error ->
        val sourceTx = if (base.isPreview) SamplePreviewData.transactions else base.transactions
        val sourceWallets = if (base.isPreview) SamplePreviewData.wallets else base.wallets
        val walletsById = sourceWallets.associateBy { it.id }
        // 2. One entry per category name, whatever its letter case.
        val categoryOptions = sourceTx.map { it.category }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()
        val extra = rawExtra.sanitized(walletsById.keys, categoryOptions)
        val filtered = filterActivity(sourceTx, filter, query, extra, walletsById, LocalDate.now())

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
            extraFilters = extra,
            categoryOptions = categoryOptions,
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

    fun updateExtraFilters(filters: ActivityExtraFilters) {
        extraFiltersFlow.value = filters
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
