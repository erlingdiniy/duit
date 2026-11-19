package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.security.SecurityConfigSnapshot
import com.diniy.duit.widgets.WidgetSnapshotCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 11Q QA: gaps not covered by earlier phase suites. Isolated in-memory database only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase11QQaTest {

    private lateinit var context: Context
    private lateinit var database: DuitDatabase
    private lateinit var repository: OfflineFirstFinanceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()
        repository = OfflineFirstFinanceRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun wallet(name: String, balanceSen: Long): Long {
        val now = System.currentTimeMillis()
        return repository.upsertWallet(
            Wallet(0L, name, WalletType.BANK, balanceSen, false, PrismaticAccentToken.AQUA, now, now)
        )
    }

    private suspend fun balance(id: Long) = database.walletDao().getById(id)!!.balanceSen

    private fun tx(type: TransactionType, from: Long, to: Long?, amount: Long, sub: String) = FinanceTransaction(
        id = 0L, walletId = from, targetWalletId = to, type = type, amountSen = amount,
        category = if (type == TransactionType.TRANSFER) "Transfer" else "Food", title = "t", note = "",
        commitmentId = null, occurredAtEpochMs = 1_000L, createdAtEpochMs = 1_000L, submissionId = sub
    )

    @Test
    fun editingTransfer_changingDestinationAndAmount_movesMoneyExactlyOnce() = runTest {
        val a = wallet("A", 100_000L)
        val b = wallet("B", 10_000L)
        val c = wallet("C", 0L)
        val id = repository.recordTransaction(tx(TransactionType.TRANSFER, a, b, 30_000L, "t1"))
        assertEquals(70_000L, balance(a)); assertEquals(40_000L, balance(b))

        val original = database.transactionDao().getById(id)!!.toDomain()
        repository.updateTransaction(original.copy(targetWalletId = c, amountSen = 25_000L))
        assertEquals(75_000L, balance(a))
        assertEquals(10_000L, balance(b)) // fully reversed
        assertEquals(25_000L, balance(c))
        assertEquals(110_000L, balance(a) + balance(b) + balance(c)) // transfers never create or destroy money

        repository.deleteTransaction(id)
        assertEquals(100_000L, balance(a)); assertEquals(10_000L, balance(b)); assertEquals(0L, balance(c))
    }

    @Test
    fun editingIncomeIntoExpense_reversesIncomeThenAppliesExpense() = runTest {
        val a = wallet("A", 50_000L)
        val id = repository.recordTransaction(tx(TransactionType.INCOME, a, null, 20_000L, "i1"))
        assertEquals(70_000L, balance(a))
        val original = database.transactionDao().getById(id)!!.toDomain()
        repository.updateTransaction(original.copy(type = TransactionType.EXPENSE, amountSen = 5_000L))
        assertEquals(45_000L, balance(a))
    }

    @Test
    fun editThatWouldOverdrawWallet_isRejected_andLeavesBalancesUntouched() = runTest {
        val a = wallet("A", 10_000L)
        val id = repository.recordTransaction(tx(TransactionType.EXPENSE, a, null, 4_000L, "e1"))
        val original = database.transactionDao().getById(id)!!.toDomain()
        try {
            repository.updateTransaction(original.copy(amountSen = 50_000L))
            fail("Expected InsufficientBalance")
        } catch (_: FinanceOperationException.InsufficientBalance) {
        }
        assertEquals(6_000L, balance(a))
        assertEquals(4_000L, database.transactionDao().getById(id)!!.amountSen)
    }

    @Test
    fun editingTransferIntoSameWallet_isRejected() = runTest {
        val a = wallet("A", 50_000L)
        val b = wallet("B", 0L)
        val id = repository.recordTransaction(tx(TransactionType.TRANSFER, a, b, 10_000L, "t2"))
        val original = database.transactionDao().getById(id)!!.toDomain()
        val rejected = runCatching { repository.updateTransaction(original.copy(targetWalletId = a)) }.isFailure
        assertTrue(rejected)
        assertEquals(40_000L, balance(a)); assertEquals(10_000L, balance(b))
    }

    @Test
    fun androidAutoBackup_isDisabled_soFinancialDataNeverLeavesTheDevice() {
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun widgetSnapshot_newFields_useRealDataOnly() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 3, 1) // month boundary
        fun at(d: LocalDate) = d.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        val a = wallet("A", 100_000L)
        val b = wallet("B", 0L)
        val txs = listOf(
            tx(TransactionType.EXPENSE, a, null, 1_000L, "x").copy(occurredAtEpochMs = at(today)),
            tx(TransactionType.EXPENSE, a, null, 2_500L, "y").copy(occurredAtEpochMs = at(today.minusDays(1))), // Feb 28
            tx(TransactionType.EXPENSE, a, null, 9_999L, "z").copy(occurredAtEpochMs = at(today.minusDays(7))), // outside window
            tx(TransactionType.TRANSFER, a, b, 7_000L, "t").copy(occurredAtEpochMs = at(today)),
            tx(TransactionType.INCOME, a, null, 50_000L, "i").copy(occurredAtEpochMs = at(today))
        )
        val wallets = repository.observeWallets().first()
        fun build(cycle: SalaryCycle?) = WidgetSnapshotCalculator.build(
            wallets = wallets, transactions = txs, commitments = emptyList(),
            commissions = emptyList(), savingsAllocations = emptyList(), savingsGoals = emptyList(),
            payLaterPlans = emptyList(), debts = emptyList(), activeCycle = cycle,
            security = SecurityConfigSnapshot(), themeMode = ThemeMode.SYSTEM, today = today
        )
        val snap = build(null)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 2_500L, 1_000L), snap.spendingLast7DaysSen) // no transfer, no income
        assertNull(snap.payday) // no salary cycle: setup state, never a guess

        val day = today.toEpochDay()
        val openEndedPast = SalaryCycle(1, "Feb", day - 20, day + 10, day - 20, 0, 0, true, isOpenEnded = true)
        assertNull(build(openEndedPast).payday)
        val fixedEnd = openEndedPast.copy(isOpenEnded = false)
        assertEquals(11L, build(fixedEnd).payday!!.daysUntil)
        val upcoming = openEndedPast.copy(paydayEpochDay = day + 5)
        assertEquals(5L, build(upcoming).payday!!.daysUntil)
    }
}
