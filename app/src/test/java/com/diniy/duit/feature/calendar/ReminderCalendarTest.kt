package com.diniy.duit.feature.calendar

import com.diniy.duit.domain.calendar.CalendarEventType
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.MoneyReminder
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderCalendarTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val store = MutableStateFlow<List<MoneyReminder>>(emptyList())
    private val transactions = MutableStateFlow<List<FinanceTransaction>>(emptyList())
    private var nextId = 1L
    private var failSaves = false

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // In-memory stand-in for the reminder DAO: insert when id == 0, else update.
    private fun vm() = CalendarViewModel(
        transactionsBetween = { _, _ -> transactions },
        salaryCycles = MutableStateFlow<List<SalaryCycle>>(emptyList()),
        commitments = MutableStateFlow<List<Commitment>>(emptyList()),
        goals = MutableStateFlow<List<SavingsGoal>>(emptyList()),
        isSamplePreview = MutableStateFlow(false),
        reminders = store,
        saveReminder = { r ->
            check(!failSaves) { "disk full" }
            store.value = if (r.id == 0L) store.value + r.copy(id = nextId++) else store.value.map { if (it.id == r.id) r else it }
        },
        deleteReminderById = { id -> store.value = store.value.filterNot { it.id == id } },
        nowEpochMs = { 1_000L },
        zone = ZoneId.of("Asia/Kuala_Lumpur"),
        today = { today }
    )

    private fun kotlinx.coroutines.test.TestScope.started(): CalendarViewModel = vm().also { m ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { m.uiState.collect {} }
    }

    @Test
    fun create_edit_delete_updateTheCalendar() = runTest {
        val model = started()
        var closed = 0
        assertNull(model.saveReminder(null, "  Check bonus ", today.toEpochDay(), CommitmentFrequency.ONE_TIME, "HR portal", onSaved = { closed++ }))
        val saved = model.uiState.value.reminders.single()
        assertEquals("Check bonus", saved.title)
        assertEquals(1, closed)
        assertEquals(CalendarEventType.REMINDER, model.uiState.value.selectedEvents.single().type)

        val newDay = LocalDate.of(2026, 11, 28).toEpochDay()
        model.saveReminder(saved, "Check bonus slip", newDay, CommitmentFrequency.ONE_TIME, "")
        assertEquals(newDay, model.uiState.value.selectedEpochDay)
        assertEquals("Check bonus slip", model.uiState.value.selectedEvents.single().title)
        assertEquals(1_000L, store.value.single().createdAtEpochMs)

        model.deleteReminder(saved.id)
        assertTrue(model.uiState.value.reminders.isEmpty())
        assertTrue(model.uiState.value.selectedEvents.isEmpty())
    }

    @Test
    fun validation_blocksBlankTitle_andLongNotes() = runTest {
        val model = started()
        assertEquals("Give your reminder a title.", model.saveReminder(null, "   ", today.toEpochDay(), CommitmentFrequency.ONE_TIME, ""))
        assertTrue(model.saveReminder(null, "x".repeat(61), today.toEpochDay(), CommitmentFrequency.ONE_TIME, "") != null)
        assertTrue(model.saveReminder(null, "Ok", today.toEpochDay(), CommitmentFrequency.ONE_TIME, "n".repeat(201)) != null)
        assertTrue(store.value.isEmpty())
    }

    @Test
    fun saveFailure_showsAnError_andKeepsTheForm() = runTest {
        val model = started()
        failSaves = true
        var closed = false
        model.saveReminder(null, "Pay zakat", today.toEpochDay(), CommitmentFrequency.YEARLY, "", onSaved = { closed = true })
        assertTrue(model.uiState.value.reminderMessage != null)
        assertTrue(!closed)
        model.dismissReminderMessage()
        assertNull(model.uiState.value.reminderMessage)
    }

    @Test
    fun monthlyRepeat_showsInLaterMonths_onTheAnchorDay() = runTest {
        val model = started()
        model.saveReminder(null, "Top up ASB", LocalDate.of(2026, 10, 31).toEpochDay(), CommitmentFrequency.MONTHLY, "")
        model.selectDay(LocalDate.of(2026, 11, 30).toEpochDay()) // November has 30 days
        assertEquals("Top up ASB", model.uiState.value.selectedEvents.single().title)
        model.selectDay(LocalDate.of(2026, 12, 31).toEpochDay())
        assertEquals(1, model.uiState.value.selectedEvents.size)
    }

    @Test
    fun reminders_haveNoFinancialSideEffects() = runTest {
        val model = started()
        model.saveReminder(null, "Check bonus", today.toEpochDay(), CommitmentFrequency.WEEKLY, "")
        assertTrue(transactions.value.isEmpty())
        assertTrue(model.uiState.value.calendar!!.summaries.isEmpty())
        assertTrue(model.uiState.value.selectedEvents.all { it.amountSen == null })
    }

    @Test
    fun sixthFilter_isReminders() {
        assertEquals(6, CalendarLayer.entries.size)
        assertEquals(CalendarEventType.REMINDER, CalendarLayer.entries.last().type)
        assertEquals("Reminders", CalendarLayer.REMINDERS.label)
    }
}
