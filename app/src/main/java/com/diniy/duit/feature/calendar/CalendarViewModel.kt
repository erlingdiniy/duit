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
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
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
import kotlinx.coroutines.launch

/** Validation for custom money reminders (title required, short note). */
object ReminderRules {
    const val TITLE_MAX = 60
    const val NOTE_MAX = 200
    val REPEATS = listOf(CommitmentFrequency.ONE_TIME, CommitmentFrequency.WEEKLY, CommitmentFrequency.MONTHLY, CommitmentFrequency.YEARLY)

    fun titleError(title: String): String? = when {
        title.isBlank() -> "Give your reminder a title."
        title.trim().length > TITLE_MAX -> "Keep the title under $TITLE_MAX characters."
        else -> null
    }

    fun noteError(note: String): String? =
        if (note.trim().length > NOTE_MAX) "Keep the note under $NOTE_MAX characters." else null
}

data class CalendarUiState(
    val month: YearMonth,
    val selectedEpochDay: Long,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val calendar: CalendarMonth? = null,
    val isSamplePreview: Boolean = false,
    val reminders: List<MoneyReminder> = emptyList(),
    val reminderMessage: String? = null
) {
    val selectedEvents: List<CalendarEvent> get() = calendar?.eventsByDay?.get(selectedEpochDay).orEmpty()
    val selectedSummary: DailyMoneySummary? get() = calendar?.summaries?.get(selectedEpochDay)
    /** Loaded and nothing at all to show this month. */
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && calendar?.eventsByDay.isNullOrEmpty()
}

private data class Inputs(
    val transactions: List<FinanceTransaction>,
    val salaryCycles: List<SalaryCycle>,
    val commitments: List<Commitment>,
    val goals: List<SavingsGoal>
)

private sealed interface MonthLoad {
    data object Loading : MonthLoad
    data class Ready(val calendar: CalendarMonth, val preview: Boolean, val reminders: List<MoneyReminder>) : MonthLoad
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
    private val reminders: Flow<List<MoneyReminder>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    private val payLaterPlans: Flow<List<PayLaterPlan>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    private val debts: Flow<List<DebtRecord>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    private val saveReminder: suspend (MoneyReminder) -> Unit = {},
    private val deleteReminderById: suspend (Long) -> Unit = {},
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) }
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val selectedDay = MutableStateFlow(today().toEpochDay())
    private val reloads = MutableStateFlow(0)
    private val reminderMessage = MutableStateFlow<String?>(null)

    private val monthLoad: Flow<MonthLoad> = combine(month, reloads) { m, _ -> m }.flatMapLatest { m ->
        val (start, end) = CalendarEventProjector.monthRangeEpochMs(m, zone)
        combine(
            combine(transactionsBetween(start, end), salaryCycles, commitments, goals) { t, s, c, g -> Inputs(t, s, c, g) },
            combine(payLaterPlans, debts) { p, d -> p to d },
            reminders,
            isSamplePreview
        ) { (t, s, c, g), (p, d), r, preview ->
            val calendar = if (preview) {
                CalendarEventProjector.project(m, SamplePreviewData.transactions, listOf(SamplePreviewData.activeCycle),
                    SamplePreviewData.commitments, SamplePreviewData.savingsGoals, zone, payLaterPlans = SamplePreviewData.payLaterPlans)
            } else {
                CalendarEventProjector.project(m, t, s, c, g, zone, r, p, d)
            }
            MonthLoad.Ready(calendar, preview, if (preview) emptyList() else r) as MonthLoad
        }
            .onStart { emit(MonthLoad.Loading) }
            .catch { emit(MonthLoad.Failed("Couldn't load this month. Please try again.")) }
    }

    val uiState: StateFlow<CalendarUiState> = combine(month, selectedDay, monthLoad, reminderMessage) { m, day, load, message ->
        val base = CalendarUiState(month = m, selectedEpochDay = day, reminderMessage = message)
        when (load) {
            MonthLoad.Loading -> base
            is MonthLoad.Failed -> base.copy(isLoading = false, errorMessage = load.message)
            // Ignore a result still in flight for the previous month.
            is MonthLoad.Ready -> if (load.calendar.month != m) base
                else base.copy(isLoading = false, calendar = load.calendar, isSamplePreview = load.preview, reminders = load.reminders)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(month = month.value, selectedEpochDay = selectedDay.value)
    )

    /**
     * Validates and saves a reminder (new when [existing] is null). Returns a validation error, or null
     * when the save was started. Reminders only touch money_reminders: no transaction, no balance change.
     */
    fun saveReminder(
        existing: MoneyReminder?,
        title: String,
        dateEpochDay: Long,
        repeat: CommitmentFrequency,
        note: String,
        onSaved: () -> Unit = {}
    ): String? {
        ReminderRules.titleError(title)?.let { return it }
        ReminderRules.noteError(note)?.let { return it }
        val now = nowEpochMs()
        val reminder = MoneyReminder(
            id = existing?.id ?: 0L,
            title = title.trim(),
            dateEpochDay = dateEpochDay,
            repeat = if (repeat in ReminderRules.REPEATS) repeat else CommitmentFrequency.ONE_TIME,
            note = note.trim(),
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now
        )
        viewModelScope.launch {
            runCatching { saveReminder(reminder) }
                .onSuccess { reminderMessage.value = null; selectDay(dateEpochDay); onSaved() }
                .onFailure { reminderMessage.value = "Couldn't save the reminder. Please try again." }
        }
        return null
    }

    fun deleteReminder(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { deleteReminderById(id) }
                .onSuccess { reminderMessage.value = null; onDeleted() }
                .onFailure { reminderMessage.value = "Couldn't delete the reminder. Please try again." }
        }
    }

    fun dismissReminderMessage() {
        reminderMessage.value = null
    }

    /** Re-runs the month query after an error. */
    fun retry() {
        reloads.value++
    }

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
            previewModeController: PreviewModeController,
            obligationRepository: com.diniy.duit.data.repository.ObligationRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(
                transactionsBetween = transactionRepository::observeTransactionsBetween,
                salaryCycles = planningRepository.observeSalaryCycles(),
                commitments = planningRepository.observeCommitments(),
                goals = savingsRepository.observeSavingsGoals(),
                isSamplePreview = previewModeController.isSamplePreviewEnabled,
                reminders = planningRepository.observeMoneyReminders(),
                payLaterPlans = obligationRepository?.observePayLaterPlans() ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                debts = obligationRepository?.observeDebts() ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                saveReminder = { planningRepository.saveMoneyReminder(it) },
                deleteReminderById = planningRepository::deleteMoneyReminder
            ) as T
        }
    }
}
