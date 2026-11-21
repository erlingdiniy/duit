package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.PeriodReportingCalculator
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.ReportingPeriodMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3SalaryAndCommissionRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: DuitDatabase
    private lateinit var repository: OfflineFirstFinanceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflineFirstFinanceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createBankWallet(balanceSen: Long = 0L): Long =
        repository.upsertWallet(
            Wallet(
                id = 0L,
                name = "Maybank",
                type = WalletType.BANK,
                balanceSen = balanceSen,
                isExcludedFromSafeToSpend = false,
                accentToken = PrismaticAccentToken.AQUA,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L
            )
        )

    private fun epochMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `smart salary cycle transitions close prior cycle on previous day and support late and backdated salary`() = runTest {
        val walletId = createBankWallet(0L)

        // October salary arrives late on Oct 6
        repository.recordSalaryTransactionAndStartCycle(
            transaction = FinanceTransaction(
                id = 0L,
                walletId = walletId,
                targetWalletId = null,
                type = TransactionType.INCOME,
                amountSen = 170_000L,
                category = "Salary",
                title = "Oct Salary",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 10, 6),
                createdAtEpochMs = 1L,
                submissionId = "sal-oct"
            ),
            cycleLabel = "Oct Cycle"
        )

        // November salary arrives on Nov 4 -> Oct Cycle closes on Nov 3!
        repository.recordSalaryTransactionAndStartCycle(
            transaction = FinanceTransaction(
                id = 0L,
                walletId = walletId,
                targetWalletId = null,
                type = TransactionType.INCOME,
                amountSen = 170_000L,
                category = "Salary",
                title = "Nov Salary",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 11, 4),
                createdAtEpochMs = 2L,
                submissionId = "sal-nov"
            ),
            cycleLabel = "Nov Cycle"
        )

        // Backdated September salary added later on Sep 3 -> closes Sep 3..Oct 5 automatically!
        repository.recordSalaryTransactionAndStartCycle(
            transaction = FinanceTransaction(
                id = 0L,
                walletId = walletId,
                targetWalletId = null,
                type = TransactionType.INCOME,
                amountSen = 170_000L,
                category = "Salary",
                title = "Sep Salary",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 9, 3),
                createdAtEpochMs = 3L,
                submissionId = "sal-sep"
            ),
            cycleLabel = "Sep Cycle"
        )

        val cycles = repository.observeSalaryCycles().first().sortedBy { it.startDateEpochDay }
        assertEquals(3, cycles.size)
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), cycles[0].endDateEpochDay)
        assertFalse(cycles[0].isActive)
        assertEquals(LocalDate.of(2026, 11, 3).toEpochDay(), cycles[1].endDateEpochDay)
        assertFalse(cycles[1].isActive)
        assertTrue(cycles[2].isActive)
        assertTrue(cycles[2].isOpenEnded)
        assertEquals(510_000L, database.walletDao().getById(walletId)!!.balanceSen)
    }

    @Test
    fun `salary cycle and calendar month reporting use same transactions without counting transfers`() = runTest {
        val bankId = createBankWallet(100_000L)
        val eWalletId = repository.upsertWallet(
            Wallet(0L, "TNG", WalletType.EWALLET, 0L, false, PrismaticAccentToken.LAVENDER, 1L, 1L)
        )

        repository.recordSalaryTransactionAndStartCycle(
            transaction = FinanceTransaction(
                id = 0L, walletId = bankId, targetWalletId = null,
                type = TransactionType.INCOME, amountSen = 200_000L, category = "Salary",
                title = "Nov Salary", note = "", commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 11, 5), createdAtEpochMs = 1L, submissionId = "s-nov"
            ),
            cycleLabel = "Nov Cycle"
        )
        // Expense on Nov 2 (before Nov 5 cycle start, but inside Nov calendar month!)
        repository.recordTransaction(
            FinanceTransaction(
                id = 0L, walletId = bankId, targetWalletId = null,
                type = TransactionType.EXPENSE, amountSen = 10_000L, category = "Food & Dining",
                title = "Nov 2 Meal", note = "", commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 11, 2), createdAtEpochMs = 2L
            )
        )
        // Expense on Nov 6 (inside both Nov Cycle and Nov calendar month)
        repository.recordTransaction(
            FinanceTransaction(
                id = 0L, walletId = bankId, targetWalletId = null,
                type = TransactionType.EXPENSE, amountSen = 15_000L, category = "Food & Dining",
                title = "Nov 6 Meal", note = "", commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 11, 6), createdAtEpochMs = 3L
            )
        )
        // Transfer on Nov 7 (must never count as income or expense)
        repository.recordTransaction(
            FinanceTransaction(
                id = 0L, walletId = bankId, targetWalletId = eWalletId,
                type = TransactionType.TRANSFER, amountSen = 50_000L, category = "Wallet Transfer",
                title = "Move", note = "", commitmentId = null,
                occurredAtEpochMs = epochMs(2026, 11, 7), createdAtEpochMs = 4L
            )
        )

        val txs = repository.observeTransactions().first()
        val activeCycle = repository.observeActiveCycle().first()
        val cycleReport = PeriodReportingCalculator.calculatePeriodReport(
            ReportingPeriodMode.SALARY_CYCLE,
            YearMonth.of(2026, 11),
            activeCycle,
            txs
        )
        val monthReport = PeriodReportingCalculator.calculatePeriodReport(
            ReportingPeriodMode.CALENDAR_MONTH,
            YearMonth.of(2026, 11),
            activeCycle,
            txs
        )

        assertEquals(15_000L, cycleReport.expenseSen)
        assertEquals(25_000L, monthReport.expenseSen)
        assertEquals(200_000L, cycleReport.incomeSen)
        assertEquals(200_000L, monthReport.incomeSen)
    }

    @Test
    fun `pending commissions never change wallets while partial and full net payments credit wallet idempotently`() = runTest {
        repository.ensurePhase3DefaultsSeeded()
        val sources = repository.observeCommissionSources().first()
        assertEquals(listOf("Company", "Lenovo", "ASUS", "Acer"), sources.map { it.name })

        val walletId = createBankWallet(100_000L)
        val lenovo = sources.first { it.name == "Lenovo" }

        val commId = repository.upsertCommission(
            CommissionRecord(
                id = 0L,
                title = "Lenovo Q4 Incentive",
                sourceLabel = "Lenovo",
                amountSen = 100_000L, // RM 1,000 expected
                status = CommissionStatus.PENDING,
                earnedDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                payoutDateEpochDay = null,
                linkedTransactionId = null,
                notes = "",
                sourceId = lenovo.id,
                yearMonth = "2026-11",
                estimatedGrossSen = 100_000L
            )
        )

        // Pending commission does not touch wallet balance
        assertEquals(100_000L, database.walletDao().getById(walletId)!!.balanceSen)

        // Partial payout #1: Gross RM 600, EPF RM 66, SOCSO RM 3.50, EIS RM 1.20 -> Net RM 529.30
        val pay1 = CommissionPayment(
            id = 0L,
            commissionId = commId,
            sourceId = lenovo.id,
            sourceName = "Lenovo",
            walletId = walletId,
            linkedTransactionId = null,
            receivedAtEpochMs = epochMs(2026, 11, 10),
            actualGrossSen = 60_000L,
            epfDeductionSen = 6_600L,
            socsoDeductionSen = 350L,
            eisDeductionSen = 120L,
            actualNetSen = 52_930L,
            notes = "Tranche 1",
            submissionId = "pay-dup-1"
        )
        val firstPayId = repository.recordCommissionPayment(pay1, markAsFullyReceived = false)
        // Duplicate submission is ignored idempotently
        val dupPayId = repository.recordCommissionPayment(pay1, markAsFullyReceived = false)
        assertEquals(firstPayId, dupPayId)

        var parent = database.commissionDao().getById(commId)!!.toDomain()
        assertEquals(CommissionStatus.PARTIALLY_RECEIVED, parent.status)
        assertEquals(60_000L, parent.actualGrossSen)
        assertEquals(52_930L, parent.receivedNetSen)
        // Only actual net received credited to wallet
        assertEquals(152_930L, database.walletDao().getById(walletId)!!.balanceSen)

        // Second payout from same source in same month completes it
        repository.recordCommissionPayment(
            CommissionPayment(
                id = 0L,
                commissionId = commId,
                sourceId = lenovo.id,
                sourceName = "Lenovo",
                walletId = walletId,
                linkedTransactionId = null,
                receivedAtEpochMs = epochMs(2026, 11, 20),
                actualGrossSen = 40_000L,
                epfDeductionSen = null,
                socsoDeductionSen = null,
                eisDeductionSen = null,
                actualNetSen = 40_000L,
                notes = "Tranche 2",
                submissionId = "pay-2"
            ),
            markAsFullyReceived = true
        )

        parent = database.commissionDao().getById(commId)!!.toDomain()
        assertEquals(CommissionStatus.RECEIVED, parent.status)
        assertEquals(100_000L, parent.actualGrossSen)
        assertEquals(92_930L, parent.receivedNetSen)
        assertEquals(192_930L, database.walletDao().getById(walletId)!!.balanceSen)

        // Reversing payment #1 rolls back linked income transaction & updates parent back to PARTIALLY_RECEIVED
        repository.deleteCommissionPayment(firstPayId)
        parent = database.commissionDao().getById(commId)!!.toDomain()
        assertEquals(CommissionStatus.PARTIALLY_RECEIVED, parent.status)
        assertEquals(140_000L, database.walletDao().getById(walletId)!!.balanceSen)
    }

    @Test
    fun `migration from v2 to v3 preserves existing wallets and transactions`() = runTest {
        val dbFile = context.getDatabasePath("migration_v2_v3_duit.db")
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS wallets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, balanceSen INTEGER NOT NULL, isExcludedFromSafeToSpend INTEGER NOT NULL, accentToken TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, isIncludedInTotalBalance INTEGER NOT NULL, isArchived INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, walletId INTEGER NOT NULL, targetWalletId INTEGER, type TEXT NOT NULL, amountSen INTEGER NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, commitmentId INTEGER, occurredAtEpochMs INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, categoryId INTEGER, submissionId TEXT, FOREIGN KEY(walletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(targetWalletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletId ON transactions(walletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_targetWalletId ON transactions(targetWalletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAtEpochMs ON transactions(occurredAtEpochMs)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_commitmentId ON transactions(commitmentId)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_submissionId ON transactions(submissionId)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS salary_cycles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, startDateEpochDay INTEGER NOT NULL, endDateEpochDay INTEGER NOT NULL, paydayEpochDay INTEGER NOT NULL, baseSalarySen INTEGER NOT NULL, committedBudgetSen INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_salary_cycles_isActive ON salary_cycles(isActive)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS commitments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountSen INTEGER NOT NULL, dueDateEpochDay INTEGER NOT NULL, status TEXT NOT NULL, walletId INTEGER, isRecurring INTEGER NOT NULL, salaryCycleId INTEGER)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_status ON commitments(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_dueDateEpochDay ON commitments(dueDateEpochDay)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_salaryCycleId ON commitments(salaryCycleId)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS commissions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, sourceLabel TEXT NOT NULL, amountSen INTEGER NOT NULL, status TEXT NOT NULL, earnedDateEpochDay INTEGER NOT NULL, payoutDateEpochDay INTEGER, linkedTransactionId INTEGER, notes TEXT NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commissions_status ON commissions(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commissions_earnedDateEpochDay ON commissions(earnedDateEpochDay)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS daily_sales_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recordDateEpochDay INTEGER NOT NULL, grossSalesSen INTEGER NOT NULL, netSalesSen INTEGER NOT NULL, unitsSold INTEGER NOT NULL, notes TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_records_recordDateEpochDay ON daily_sales_records(recordDateEpochDay)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, iconKey TEXT NOT NULL, accentToken TEXT NOT NULL, isDefault INTEGER NOT NULL, isArchived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_type_isArchived ON categories(type, isArchived)")
                        db.execSQL("INSERT INTO wallets VALUES (1, 'Phase 2 Wallet', 'BANK', 480000, 0, 'AQUA', 1000, 1000, 1, 0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                DuitDatabase.MIGRATION_1_2,
                DuitDatabase.MIGRATION_2_3,
                DuitDatabase.MIGRATION_3_4,
                DuitDatabase.MIGRATION_4_5,
                DuitDatabase.MIGRATION_5_6,
                DuitDatabase.MIGRATION_6_7,
                DuitDatabase.MIGRATION_7_8,
                DuitDatabase.MIGRATION_8_9,
                DuitDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

        val wallet = migratedDb.walletDao().getById(1L)!!.toDomain()
        assertEquals("Phase 2 Wallet", wallet.name)
        assertEquals(480_000L, wallet.balanceSen)

        val repo = OfflineFirstFinanceRepository(migratedDb)
        repo.ensurePhase3DefaultsSeeded()
        assertEquals(4, repo.observeCommissionSources().first().size)
        assertEquals(1, repo.observeCommissionRuleVersions().first().size)

        migratedDb.close()
        dbFile.delete()
    }
}
