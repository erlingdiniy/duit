package com.diniy.duit.feature.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.domain.calendar.CalendarEventType
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.calendar.CalendarEventProjector
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CalendarViewModelTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 11, 21)
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(kl).toInstant().toEpochMilli()

    private fun txn(id: Long, type: TransactionType, sen: Long, ms: Long) =
        FinanceTransaction(id, 1, if (type == TransactionType.TRANSFER) 2 else null, type, sen, "Food", "t$id", "", null, ms, ms)

    private val all = MutableStateFlow<List<FinanceTransaction>>(emptyList())
    private val commitments = MutableStateFlow<List<Commitment>>(emptyList())
    private val requestedRanges = mutableListOf<Pair<Long, Long>>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(between: (Long, Long) -> Flow<List<FinanceTransaction>> = { s, e ->
        requestedRanges += s to e
        all.map { list -> list.filter { it.occurredAtEpochMs >= s && it.occurredAtEpochMs < e } }
    }) = CalendarViewModel(
        transactionsBetween = between,
        salaryCycles = MutableStateFlow<List<SalaryCycle>>(emptyList()),
        commitments = commitments,
        goals = MutableStateFlow<List<SavingsGoal>>(emptyList()),
        isSamplePreview = MutableStateFlow(false),
        zone = kl,
        today = { today }
    )

    @Test
    fun opensOnToday_andQueriesOnlyThatMonth() = runTest {
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val state = model.uiState.value
        assertEquals(YearMonth.of(2026, 11), state.month)
        assertEquals(today.toEpochDay(), state.selectedEpochDay)
        assertEquals(at(2026, 11, 1, 0) to at(2026, 12, 1, 0), requestedRanges.single())
    }

    @Test
    fun switchingMonths_movesRangeAndSelection() = runTest {
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        model.nextMonth()
        assertEquals(YearMonth.of(2026, 12), model.uiState.value.month)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), model.uiState.value.selectedEpochDay)
        assertEquals(at(2026, 12, 1, 0) to at(2027, 1, 1, 0), requestedRanges.last())
        model.previousMonth(); model.previousMonth()
        assertEquals(YearMonth.of(2026, 10), model.uiState.value.month)
        model.goToToday()
        assertEquals(today.toEpochDay(), model.uiState.value.selectedEpochDay)
    }

    @Test
    fun selectingADay_inAnotherMonth_followsIt() = runTest {
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val jan3 = LocalDate.of(2027, 1, 3).toEpochDay()
        model.selectDay(jan3)
        assertEquals(YearMonth.of(2027, 1), model.uiState.value.month)
        assertEquals(jan3, model.uiState.value.selectedEpochDay)
    }

    @Test
    fun dailyTotals_excludeTransfers_andOtherMonths() = runTest {
        all.value = listOf(
            txn(1, TransactionType.INCOME, 10_000, at(2026, 11, 21, 9)),
            txn(2, TransactionType.EXPENSE, 2_500, at(2026, 11, 21, 23, 59)),
            txn(3, TransactionType.TRANSFER, 7_000, at(2026, 11, 21, 12)),
            txn(4, TransactionType.EXPENSE, 999, at(2026, 11, 22, 0, 0))
        )
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val s = model.uiState.value.selectedSummary!!
        assertEquals(10_000L, s.incomeSen)
        assertEquals(2_500L, s.expenseSen)
        assertEquals(3, model.uiState.value.selectedEvents.size)
    }

    @Test
    fun emptyMonth_isEmpty_notLoading() = runTest {
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
        assertNull(state.selectedSummary)
    }

    @Test
    fun loadFailure_showsAnError() = runTest {
        val model = vm(between = { _, _ -> flow { throw IllegalStateException("db") } })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.errorMessage != null)
    }

    @Test
    fun repeatedCommitments_areShownOnce() = runTest {
        val bill = Commitment(3, "Rent", 120_000, today.toEpochDay(), CommitmentStatus.UPCOMING, 1, false, null)
        commitments.value = listOf(bill, bill)
        val model = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        assertEquals(1, model.uiState.value.selectedEvents.count { it.type == CalendarEventType.BILL })
        assertTrue(model.uiState.value.selectedSummary == null) // an upcoming bill is not an expense
    }

    @Test
    fun daoRangeQuery_includesStart_excludesEnd() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val wallet = db.walletDao().upsert(WalletEntity(name = "Cash", type = WalletType.CASH, balanceSen = 0,
                isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA, createdAtEpochMs = 0, updatedAtEpochMs = 0))
            val (start, end) = CalendarEventProjector.monthRangeEpochMs(YearMonth.of(2026, 11), kl)
            listOf(start - 1, start, end - 1, end).forEachIndexed { i, ms ->
                db.transactionDao().insert(TransactionEntity(walletId = wallet, targetWalletId = null, type = TransactionType.EXPENSE,
                    amountSen = 100L + i, category = "Food", title = "t$i", note = "", commitmentId = null,
                    occurredAtEpochMs = ms, createdAtEpochMs = ms))
            }
            assertEquals(setOf(start, end - 1), db.transactionDao().observeBetween(start, end).first().map { it.occurredAtEpochMs }.toSet())
        } finally {
            db.close()
        }
    }
}
