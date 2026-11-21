package com.diniy.duit.feature.wins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.MilestoneRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.domain.wins.LittleWinItem
import com.diniy.duit.domain.wins.LittleWinsCalculator
import com.diniy.duit.domain.wins.LittleWinsInputs
import com.diniy.duit.domain.wins.WinCategory
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LittleWinsUiState(
    val isLoading: Boolean = true,
    val items: List<LittleWinItem> = emptyList(),
    val selectedCategory: WinCategory? = null,
    val isSamplePreview: Boolean = false,
    val errorMessage: String? = null
) {
    val earnedCount: Int get() = items.count { it.isEarned }
    val totalCount: Int get() = items.size
    val uncelebratedItems: List<LittleWinItem> get() = items.filter { it.needsCelebration }
    val filteredItems: List<LittleWinItem>
        get() = if (selectedCategory == null) items else items.filter { it.type.category == selectedCategory }
}

data class LittleWinsBinding(
    val state: LittleWinsUiState,
    val onSelectCategory: (WinCategory?) -> Unit,
    val onMarkCelebrated: (List<String>) -> Unit
)

/**
 * Offline Little Wins Club ViewModel.
 * Persists newly unlocked milestones once (unique index -> zero duplicates) while always
 * preserving historical stickers and recalculating current live progress.
 */
class LittleWinsViewModel(
    inputsFlow: Flow<LittleWinsInputs>,
    private val milestoneRepository: MilestoneRepository,
    isSamplePreview: Flow<Boolean>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currentMonth: () -> YearMonth = { YearMonth.now(zone) }
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<WinCategory?>(null)

    val uiState: StateFlow<LittleWinsUiState> = combine(
        inputsFlow,
        milestoneRepository.observeEarnedMilestones(),
        isSamplePreview,
        selectedCategory
    ) { inputs, persisted, preview, category ->
        if (preview) {
            LittleWinsUiState(isLoading = false, items = emptyList(), selectedCategory = category, isSamplePreview = true)
        } else {
            val evaluated = LittleWinsCalculator.evaluate(inputs, persisted, currentMonth(), zone)
            val persistedKeys = persisted.map { it.milestoneKey }.toSet()
            val newlyUnlocked = evaluated
                .filter { it.isCurrentlyMet && it.type.key !in persistedKeys }
                .map { it.type.key }
                .toSet()
            if (newlyUnlocked.isNotEmpty()) {
                viewModelScope.launch {
                    runCatching { milestoneRepository.recordEarnedMilestones(newlyUnlocked) }
                }
            }
            LittleWinsUiState(
                isLoading = false,
                items = evaluated,
                selectedCategory = category,
                isSamplePreview = false
            )
        }
    }
        .onEach { }
        .catch { emit(LittleWinsUiState(isLoading = false, errorMessage = "Couldn't load Little Wins right now.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LittleWinsUiState())

    fun selectCategory(category: WinCategory?) {
        selectedCategory.value = category
    }

    fun markCelebrated(keys: List<String>) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            runCatching { milestoneRepository.markCelebrated(keys) }
        }
    }

    companion object {
        fun factory(
            savingsRepository: SavingsRepository,
            budgetRepository: BudgetRepository,
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            planningRepository: PlanningRepository,
            obligationRepository: ObligationRepository,
            milestoneRepository: MilestoneRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val savingsPart = combine(
                    savingsRepository.observeSavingsGoals(),
                    savingsRepository.observeSavingsAllocations(),
                    savingsRepository.observeSavingsMovements(),
                    savingsRepository.observeChallengeStickers()
                ) { goals, allocs, moves, stickers ->
                    SavingsPart(goals, allocs, moves, stickers)
                }
                val budgetPart = combine(
                    budgetRepository.observeBudgets(),
                    transactionRepository.observeTransactions(),
                    categoryRepository.observeCategories()
                ) { budgets, txs, cats ->
                    Triple(budgets, txs, cats)
                }
                val obligationPart = combine(
                    planningRepository.observeCommitments(),
                    obligationRepository.observePayLaterPlans(),
                    obligationRepository.observeDebts(),
                    obligationRepository.observeObligationPayments()
                ) { commitments, payLater, debts, payments ->
                    ObligationPart(commitments, payLater, debts, payments)
                }
                val combinedInputs = combine(savingsPart, budgetPart, obligationPart) { s, (b, t, c), o ->
                    LittleWinsInputs(
                        goals = s.goals,
                        allocations = s.allocs,
                        movements = s.moves,
                        budgets = b,
                        transactions = t,
                        categories = c,
                        commitments = o.commitments,
                        payLaterPlans = o.payLater,
                        debts = o.debts,
                        obligationPayments = o.payments,
                        challengeStickers = s.stickers
                    )
                }
                return LittleWinsViewModel(
                    inputsFlow = combinedInputs,
                    milestoneRepository = milestoneRepository,
                    isSamplePreview = previewModeController.isSamplePreviewEnabled
                ) as T
            }
        }
    }

    private data class SavingsPart(
        val goals: List<com.diniy.duit.domain.model.SavingsGoal>,
        val allocs: List<com.diniy.duit.domain.model.SavingsAllocation>,
        val moves: List<com.diniy.duit.domain.model.SavingsMovement>,
        val stickers: List<com.diniy.duit.domain.challenge.EarnedSticker>
    )

    private data class ObligationPart(
        val commitments: List<com.diniy.duit.domain.model.Commitment>,
        val payLater: List<com.diniy.duit.domain.model.PayLaterPlan>,
        val debts: List<com.diniy.duit.domain.model.DebtRecord>,
        val payments: List<com.diniy.duit.domain.model.ObligationPayment>
    )
}
