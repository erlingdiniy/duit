package com.diniy.duit.feature.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.ReceiptRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.receipt.ReceiptFilterTab
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptSearchFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReceiptVaultUiState(
    val isLoading: Boolean = true,
    val allReceipts: List<ReceiptRecord> = emptyList(),
    val filteredReceipts: List<ReceiptRecord> = emptyList(),
    val selectedTab: ReceiptFilterTab = ReceiptFilterTab.ALL,
    val searchQuery: String = "",
    val transactionsById: Map<Long, FinanceTransaction> = emptyMap(),
    val linkableTransactions: List<FinanceTransaction> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val isSamplePreview: Boolean = false,
    val errorMessage: String? = null
) {
    val linkedCount: Int get() = allReceipts.count { it.isLinked }
    val taxTaggedCount: Int get() = allReceipts.count { it.isTaxTagged }
}

class ReceiptVaultViewModel(
    private val receiptRepository: ReceiptRepository,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    previewModeController: PreviewModeController
) : ViewModel() {

    private val selectedTab = MutableStateFlow(ReceiptFilterTab.ALL)
    private val searchQuery = MutableStateFlow("")
    private val errorMessage = MutableStateFlow<String?>(null)

    private val queryState = combine(selectedTab, searchQuery, errorMessage) { tab, q, err ->
        Triple(tab, q, err)
    }

    val uiState: StateFlow<ReceiptVaultUiState> = combine(
        receiptRepository.observeReceipts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
        previewModeController.isSamplePreviewEnabled,
        queryState
    ) { receipts, transactions, categories, isPreview, (tab, query, err) ->
        val safeReceipts = if (isPreview) emptyList() else receipts
        val txMap = transactions.associateBy { it.id }
        val linkable = transactions
            .filter { it.type != TransactionType.TRANSFER }
            .sortedByDescending { it.occurredAtEpochMs }
            .take(80)
        val expenseCats = categories.filter { !it.isArchived && it.type == TransactionType.EXPENSE }
        ReceiptVaultUiState(
            isLoading = false,
            allReceipts = safeReceipts,
            filteredReceipts = ReceiptSearchFilter.filter(safeReceipts, tab, query),
            selectedTab = tab,
            searchQuery = query,
            transactionsById = txMap,
            linkableTransactions = linkable,
            expenseCategories = expenseCats,
            isSamplePreview = isPreview,
            errorMessage = err
        )
    }
        .catch { emit(ReceiptVaultUiState(isLoading = false, errorMessage = "Couldn't load receipts. Please try again.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptVaultUiState())

    fun selectTab(tab: ReceiptFilterTab) {
        selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun saveReceipt(
        record: ReceiptRecord,
        onSaved: (Long) -> Unit = {},
        onError: (String) -> Unit = { errorMessage.value = it }
    ) {
        val cleanMerchant = record.merchant.trim()
        if (cleanMerchant.isEmpty()) {
            onError("Enter a merchant or receipt title.")
            return
        }
        viewModelScope.launch {
            runCatching {
                receiptRepository.saveReceipt(record.copy(merchant = cleanMerchant))
            }.onSuccess { id ->
                errorMessage.value = null
                onSaved(id)
            }.onFailure { err ->
                onError(err.message ?: "Couldn't save receipt.")
            }
        }
    }

    fun linkToTransaction(receiptId: Long, transactionId: Long?) {
        viewModelScope.launch {
            runCatching {
                receiptRepository.updateTransactionLink(receiptId, transactionId)
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Couldn't update receipt link."
            }
        }
    }

    fun deleteReceipt(receiptId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                receiptRepository.deleteReceipt(receiptId)
            }.onSuccess {
                errorMessage.value = null
                onDeleted()
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Couldn't delete receipt."
            }
        }
    }

    companion object {
        fun factory(
            receiptRepository: ReceiptRepository,
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReceiptVaultViewModel(
                receiptRepository = receiptRepository,
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                previewModeController = previewModeController
            ) as T
        }
    }
}
