package com.diniy.duit.feature.tax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.ReceiptRepository
import com.diniy.duit.data.repository.TaxReliefRepository
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.tax.TaxReliefCalculator
import com.diniy.duit.domain.tax.TaxReliefCatalog
import com.diniy.duit.domain.tax.TaxReliefEntry
import com.diniy.duit.domain.tax.TaxReliefReviewStatus
import com.diniy.duit.domain.tax.TaxYearSummary
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaxPocketUiState(
    val isLoading: Boolean = true,
    val selectedYear: Int = TaxReliefCatalog.defaultYear(),
    val supportedYears: List<Int> = TaxReliefCatalog.supportedYears,
    val summary: TaxYearSummary = TaxReliefCalculator.summarize(TaxReliefCatalog.defaultYear(), emptyList(), emptyList()),
    val receipts: List<ReceiptRecord> = emptyList(),
    val isSamplePreview: Boolean = false,
    val errorMessage: String? = null
)

data class TaxPocketBinding(
    val state: TaxPocketUiState,
    val onSelectYear: (Int) -> Unit,
    val onSaveEntry: (TaxReliefEntry, () -> Unit, (String) -> Unit) -> Unit,
    val onUpdateStatus: (Long, TaxReliefReviewStatus) -> Unit,
    val onDeleteEntry: (Long) -> Unit
)

class TaxReliefViewModel(
    private val taxReliefRepository: TaxReliefRepository,
    receiptRepository: ReceiptRepository,
    isSamplePreview: Flow<Boolean>,
    initialYear: Int = TaxReliefCatalog.defaultYear(LocalDate.now())
) : ViewModel() {

    private val selectedYear = MutableStateFlow(
        initialYear.coerceIn(TaxReliefCatalog.supportedYears.first(), TaxReliefCatalog.supportedYears.last())
    )

    val uiState: StateFlow<TaxPocketUiState> = combine(
        selectedYear,
        taxReliefRepository.observeAllEntries(),
        receiptRepository.observeReceipts(),
        isSamplePreview
    ) { ya, entries, receipts, preview ->
        if (preview) {
            TaxPocketUiState(
                isLoading = false,
                selectedYear = ya,
                summary = TaxReliefCalculator.summarize(ya, emptyList(), emptyList()),
                receipts = emptyList(),
                isSamplePreview = true
            )
        } else {
            TaxPocketUiState(
                isLoading = false,
                selectedYear = ya,
                summary = TaxReliefCalculator.summarize(ya, entries, receipts),
                receipts = receipts,
                isSamplePreview = false
            )
        }
    }
        .catch {
            emit(
                TaxPocketUiState(
                    isLoading = false,
                    selectedYear = selectedYear.value,
                    errorMessage = "Couldn't load My Tax Pocket right now."
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaxPocketUiState(selectedYear = selectedYear.value))

    fun selectYear(year: Int) {
        if (year in TaxReliefCatalog.supportedYears) {
            selectedYear.value = year
        }
    }

    fun saveEntry(
        entry: TaxReliefEntry,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { taxReliefRepository.saveEntry(entry) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Couldn't save tax entry.") }
        }
    }

    fun updateStatus(id: Long, status: TaxReliefReviewStatus) {
        viewModelScope.launch {
            runCatching { taxReliefRepository.updateReviewStatus(id, status) }
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            runCatching { taxReliefRepository.deleteEntry(id) }
        }
    }

    companion object {
        fun factory(
            taxReliefRepository: TaxReliefRepository,
            receiptRepository: ReceiptRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TaxReliefViewModel(
                    taxReliefRepository = taxReliefRepository,
                    receiptRepository = receiptRepository,
                    isSamplePreview = previewModeController.isSamplePreviewEnabled
                ) as T
        }
    }
}
