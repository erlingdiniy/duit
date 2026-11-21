package com.diniy.duit.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.domain.calendar.CalendarEvent
import com.diniy.duit.domain.calendar.CalendarEventProjector
import com.diniy.duit.domain.calendar.CalendarMonth
import com.diniy.duit.domain.calendar.DailyMoneySummary
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.preview.SamplePreviewData
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val month: YearMonth,
    val selectedEpochDay: Long,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val calendar: CalendarMonth? = null,
    val isSamplePreview: Boolean = false
) {
    val selectedEvents: List<CalendarEvent> get() = calendar?.eventsByDay?.get(selectedEpochDay).orEmpty()
    val selectedSummary: DailyMoneySummary? get() = calendar?.summaries?.get(selectedEpochDay)
    /** Loaded and nothing at all to show this month. */
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && calendar?.eventsByDay.isNullOrEmpty()
}

private sealed interface MonthLoad {
    data object Loading : MonthLoad
    data class Ready(val calendar: CalendarMonth, val preview: Boolean) : MonthLoad
    data class Failed(val message: String) : MonthLoad
}

/**
 * Read-only Smart Money Calendar state: one month of transactions (range query, not full history)
 * plus salary dates, bills and goal deadlines, projected by [CalendarEventProjector].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val transactionsBetween: (Long, Long) -> Flow<List<FinanceTransaction>>,
    private val salaryCycles: Flow<List<SalaryCycle>>,
    private val commitments: Flow<List<Commitment>>,
    private val goals: Flow<List<SavingsGoal>>,
    private val isSamplePreview: Flow<Boolean>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) }
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val selectedDay = MutableStateFlow(today().toEpochDay())

    private val monthLoad: Flow<MonthLoad> = month.flatMapLatest { m ->
        val (start, end) = CalendarEventProjector.monthRangeEpochMs(m, zone)
        combine(transactionsBetween(start, end), salaryCycles, commitments, goals, isSamplePreview) { t, s, c, g, preview ->
            val calendar = if (preview) {
                CalendarEventProjector.project(m, SamplePreviewData.transactions, listOf(SamplePreviewData.activeCycle),
                    SamplePreviewData.commitments, SamplePreviewData.savingsGoals, zone)
            } else {
                CalendarEventProjector.project(m, t, s, c, g, zone)
            }
            MonthLoad.Ready(calendar, preview) as MonthLoad
        }
            .onStart { emit(MonthLoad.Loading) }
            .catch { emit(MonthLoad.Failed("Couldn't load this month. Please try again.")) }
    }

    val uiState: StateFlow<CalendarUiState> = combine(month, selectedDay, monthLoad) { m, day, load ->
        val base = CalendarUiState(month = m, selectedEpochDay = day)
        when (load) {
            MonthLoad.Loading -> base
            is MonthLoad.Failed -> base.copy(isLoading = false, errorMessage = load.message)
            // Ignore a result still in flight for the previous month.
            is MonthLoad.Ready -> if (load.calendar.month != m) base
                else base.copy(isLoading = false, calendar = load.calendar, isSamplePreview = load.preview)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(month = month.value, selectedEpochDay = selectedDay.value)
    )

    fun previousMonth() = showMonth(month.value.minusMonths(1))

    fun nextMonth() = showMonth(month.value.plusMonths(1))

    fun goToToday() = showMonth(YearMonth.from(today()))

    /** Selects a day; a day outside the shown month also moves the calendar to that month. */
    fun selectDay(epochDay: Long) {
        val target = YearMonth.from(LocalDate.ofEpochDay(epochDay))
        if (target != month.value) month.value = target
        selectedDay.value = epochDay
    }

    /** Today when it is in [target], otherwise the 1st, so the details panel always has a day. */
    private fun showMonth(target: YearMonth) {
        val now = today()
        selectedDay.value = (if (YearMonth.from(now) == target) now else target.atDay(1)).toEpochDay()
        month.value = target
    }

    companion object {
        fun factory(
            transactionRepository: TransactionRepository,
            planningRepository: PlanningRepository,
            savingsRepository: SavingsRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(
                transactionsBetween = transactionRepository::observeTransactionsBetween,
                salaryCycles = planningRepository.observeSalaryCycles(),
                commitments = planningRepository.observeCommitments(),
                goals = savingsRepository.observeSavingsGoals(),
                isSamplePreview = previewModeController.isSamplePreviewEnabled
            ) as T
        }
    }
}
