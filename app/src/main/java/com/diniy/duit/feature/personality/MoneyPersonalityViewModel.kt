package com.diniy.duit.feature.personality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calendar.CalendarEventProjector
import com.diniy.duit.domain.calendar.CalendarEventStatus
import com.diniy.duit.domain.calendar.CalendarEventType
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.personality.MoneyPersonality
import com.diniy.duit.domain.personality.MoneyPersonalityCalculator
import com.diniy.duit.domain.personality.PersonalityResult
import com.diniy.duit.domain.personality.PlanningOutcome
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class MoneyPersonalityUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val result: PersonalityResult? = null,
    /** Personalities earned in the 12 months up to the shown one: derived from data, never stored. */
    val stickers: Map<MoneyPersonality, Int> = emptyMap(),
    val isSamplePreview: Boolean = false,
    val canGoForward: Boolean = false
)

/** What Insights needs to show Money Personality (public edition only). */
data class MoneyPersonalityBinding(
    val state: MoneyPersonalityUiState,
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onRetry: () -> Unit
)

/** Everything the calculator reads for a run of months. */
data class PersonalityInputs(
    val transactions: List<FinanceTransaction>,
    val categories: List<Category>,
    val movements: List<SavingsMovement>,
    val budgets: List<Budget>,
    val salaryCycles: List<SalaryCycle>,
    val commitments: List<Commitment>,
    val payLaterPlans: List<PayLaterPlan>,
    val debts: List<DebtRecord>
)

/**
 * Offline, read-only Money Personality. Budget results reuse [BudgetCalculator]; bills reuse the
 * Calendar's bill events (commitments, PayLater, debts I owe). Nothing here changes any money.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyPersonalityViewModel(
    private val transactionsBetween: (Long, Long) -> Flow<List<FinanceTransaction>>,
    private val others: Flow<PersonalityInputs>,
    private val isSamplePreview: Flow<Boolean> = flowOf(false),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) }
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val reloads = MutableStateFlow(0)

    val uiState: StateFlow<MoneyPersonalityUiState> = combine(month, reloads) { m, _ -> m }.flatMapLatest { m ->
        // One query covers the shown month and the 11 before it (for the sticker collection).
        val (start, _) = CalendarEventProjector.monthRangeEpochMs(m.minusMonths(11), zone)
        val (_, end) = CalendarEventProjector.monthRangeEpochMs(m, zone)
        combine(transactionsBetween(start, end), others, isSamplePreview) { t, rest, preview ->
            val inputs = rest.copy(transactions = t)
            val todayDay = today().toEpochDay()
            val result = evaluate(m, inputs, todayDay)
            val stickers = if (preview) emptyMap() else (0L..11L)
                .map { m.minusMonths(it) }
                .mapNotNull { (evaluate(it, inputs, todayDay) as? PersonalityResult.Assigned)?.personality }
                .groupingBy { it }.eachCount()
            MoneyPersonalityUiState(
                month = m, isLoading = false, result = if (preview) null else result, stickers = stickers,
                isSamplePreview = preview, canGoForward = m < YearMonth.from(today())
            )
        }
            .onStart { emit(MoneyPersonalityUiState(month = m)) }
            .catch { emit(MoneyPersonalityUiState(month = m, isLoading = false, errorMessage = "Couldn't work out your Money Personality. Please try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyPersonalityUiState(month = month.value))

    private fun evaluate(m: YearMonth, inputs: PersonalityInputs, todayDay: Long): PersonalityResult =
        MoneyPersonalityCalculator.evaluate(m, inputs.transactions, inputs.categories, inputs.movements,
            planningOutcome(m, inputs, todayDay), todayDay, zone)

    private fun planningOutcome(m: YearMonth, inputs: PersonalityInputs, todayDay: Long): PlanningOutcome {
        val monthBudgets = inputs.budgets.filter { !it.isArchived && it.periodType == BudgetPeriodType.MONTHLY && it.yearMonth == m.toString() }
        val categoriesById = inputs.categories.associateBy { it.id }
        val within = monthBudgets.count { b ->
            BudgetCalculator.calculateBudgetProgress(b, inputs.transactions, emptyMap(), categoriesById, zone).overspentSen == 0L
        }
        // Bills already due (up to today) in this month; paid ones are marked PAID by the shared schedule.
        val lastDue = minOf(todayDay, m.atEndOfMonth().toEpochDay())
        val bills = CalendarEventProjector.project(m, emptyList(), inputs.salaryCycles, inputs.commitments, emptyList(), zone,
            payLaterPlans = inputs.payLaterPlans, debts = inputs.debts)
            .eventsByDay.values.flatten()
            .filter { it.type == CalendarEventType.BILL && it.epochDay <= lastDue }
        return PlanningOutcome(
            budgetsTotal = monthBudgets.size,
            budgetsWithinLimit = within,
            billsDue = bills.size,
            billsPaid = bills.count { it.status == CalendarEventStatus.PAID }
        )
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.from(today())) month.value = month.value.plusMonths(1)
    }

    fun retry() {
        reloads.value++
    }

    companion object {
        fun factory(
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            savingsRepository: SavingsRepository,
            budgetRepository: BudgetRepository,
            planningRepository: PlanningRepository,
            obligationRepository: ObligationRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MoneyPersonalityViewModel(
                transactionsBetween = transactionRepository::observeTransactionsBetween,
                others = combine(
                    combine(categoryRepository.observeCategories(), savingsRepository.observeSavingsMovements(), budgetRepository.observeBudgets()) { c, m, b -> Triple(c, m, b) },
                    combine(planningRepository.observeSalaryCycles(), planningRepository.observeCommitments()) { s, c -> s to c },
                    combine(obligationRepository.observePayLaterPlans(), obligationRepository.observeDebts()) { p, d -> p to d }
                ) { (c, m, b), (s, cm), (p, d) -> PersonalityInputs(emptyList(), c, m, b, s, cm, p, d) },
                isSamplePreview = previewModeController.isSamplePreviewEnabled
            ) as T
        }
    }
}
