package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.CommitmentDisplayState
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4CObligationsRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var repo: OfflineFirstFinanceRepository
    private val zoneId = ZoneId.systemDefault()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OfflineFirstFinanceRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun recurringDueDates_preserveMonthEndBoundariesIncludingLeapYears() {
        val jan31 = LocalDate.of(2024, 1, 31).toEpochDay() // 2024 is leap year
        val feb29 = ObligationCalculator.advanceDueDate(jan31, CommitmentFrequency.MONTHLY, anchorDayOfMonth = 31)
        assertEquals(LocalDate.of(2024, 2, 29), LocalDate.ofEpochDay(feb29))

        val mar31 = ObligationCalculator.advanceDueDate(feb29, CommitmentFrequency.MONTHLY, anchorDayOfMonth = 31)
        assertEquals(LocalDate.of(2024, 3, 31), LocalDate.ofEpochDay(mar31))

        val apr30 = ObligationCalculator.advanceDueDate(mar31, CommitmentFrequency.MONTHLY, anchorDayOfMonth = 31)
        assertEquals(LocalDate.of(2024, 4, 30), LocalDate.ofEpochDay(apr30))

        // Non-leap year Feb
        val jan31NonLeap = LocalDate.of(2026, 1, 31).toEpochDay()
        val feb28 = ObligationCalculator.advanceDueDate(jan31NonLeap, CommitmentFrequency.MONTHLY, anchorDayOfMonth = 31)
        assertEquals(LocalDate.of(2026, 2, 28), LocalDate.ofEpochDay(feb28))

        // Custom interval days
        val plus45 = ObligationCalculator.advanceDueDate(LocalDate.of(2026, 11, 1).toEpochDay(), CommitmentFrequency.CUSTOM_DAYS, customIntervalDays = 45)
        assertEquals(LocalDate.of(2026, 12, 16), LocalDate.ofEpochDay(plus45))
    }

    @Test
    fun commitments_partialAndFullRecurringPayments_preserveHistoryAndSafeToSpendNoDoubleCount() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val billsCat = repo.observeCategories().first().first { it.name == "Bills & Utilities" }
        val nov15 = LocalDate.of(2026, 11, 15).toEpochDay()
        val novToday = LocalDate.of(2026, 11, 15)

        // Creating commitment must NOT change wallet balance
        val commId = repo.upsertCommitmentDetail(
            Commitment(
                id = 0L,
                title = "Unifi Fibre",
                amountSen = 14_000L, // RM 140.00
                dueDateEpochDay = nov15,
                status = CommitmentStatus.UPCOMING,
                walletId = bankId,
                isRecurring = true,
                salaryCycleId = null,
                category = billsCat.name,
                categoryId = billsCat.id,
                frequency = CommitmentFrequency.MONTHLY
            )
        )
        assertEquals(200_000L, repo.observeWallets().first().single().balanceSen)

        var summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            today = novToday
        )
        assertEquals(186_000L, summary.safeToSpendSen) // 2,000 - 140

        // Overpayment (> RM 140) rejected cleanly
        var overpayRejected = false
        try {
            repo.recordCommitmentPayment(commId, bankId, 15_000L, "Too much", now, "sub-comm-over")
        } catch (_: FinanceOperationException.InvalidTransaction) {
            overpayRejected = true
        }
        assertTrue(overpayRejected)

        // Partial RM 40 payment: Safe to Spend remains RM 1,860 (RM 1,960 wallet - RM 100 remaining unpaid bill = RM 1,860!)
        repo.recordCommitmentPayment(commId, bankId, 4_000L, "Partial 1", now, "sub-comm-p1")
        assertEquals(196_000L, repo.observeWallets().first().single().balanceSen)
        val partialComm = repo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.PARTIALLY_PAID, partialComm.status)
        assertEquals(4_000L, partialComm.paidSen)

        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            today = novToday
        )
        assertEquals(186_000L, summary.safeToSpendSen)

        // Duplicate submissionId idempotency check
        val dupId = repo.recordCommitmentPayment(commId, bankId, 4_000L, "Duplicate", now, "sub-comm-p1")
        assertEquals(196_000L, repo.observeWallets().first().single().balanceSen)
        assertTrue(dupId > 0L)

        // Pay remaining RM 100 -> settles Nov 15 occurrence and advances recurring bill to Dec 15 UPCOMING
        // without deducting Dec 15 from November's Safe to Spend!
        repo.recordCommitmentPayment(commId, bankId, 10_000L, "Final", now + 1000L, "sub-comm-p2")
        assertEquals(186_000L, repo.observeWallets().first().single().balanceSen)
        val rolledComm = repo.observeCommitments().first().single()
        assertEquals(LocalDate.of(2026, 12, 15).toEpochDay(), rolledComm.dueDateEpochDay)
        assertEquals(CommitmentStatus.UPCOMING, rolledComm.status)
        assertEquals(0L, rolledComm.paidSen)

        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            today = novToday
        )
        assertEquals(0L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(14_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(186_000L, summary.safeToSpendSen)

        // Edit commitment metadata (name & amount to RM 150) -> historical Nov payments remain preserved!
        repo.upsertCommitmentDetail(rolledComm.copy(title = "Unifi 500Mbps", amountSen = 15_000L))
        val payments = repo.observeObligationPayments().first()
            .filter { it.obligationType == ObligationType.COMMITMENT && it.obligationId == commId }
        assertEquals(2, payments.size)
        assertEquals(14_000L, payments.sumOf { it.amountSen })
    }

    @Test
    fun payLater_instalmentCalculationsFeesPartialEarlyAndOverpaymentProtection() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val firstDue = LocalDate.of(2026, 11, 1).toEpochDay()

        // Create SPayLater plan: RM 95.00 + RM 5.00 fee = RM 100.00 / 3 instalments -> 3334, 3333, 3333 sen
        val planId = repo.upsertPayLaterPlan(
            PayLaterPlan(
                id = 0L,
                title = "Headphones",
                providerName = "SPayLater",
                purchaseAmountSen = 9_500L,
                feeOrInterestSen = 500L,
                paidSen = 0L,
                instalmentCount = 3,
                firstDueDateEpochDay = firstDue,
                nextDueDateEpochDay = firstDue,
                accentToken = PrismaticAccentToken.PEACH,
                note = "Includes admin fee",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        // Plan creation does NOT alter wallet balance
        assertEquals(200_000L, repo.observeWallets().first().single().balanceSen)

        var summary = ObligationCalculator.summarizePayLater(repo.observePayLaterPlans().first().single())
        assertEquals(10_000L, summary.totalPayableSen)
        assertEquals(10_000L, summary.outstandingSen)
        assertEquals(listOf(3334L, 3333L, 3333L), summary.schedule.map { it.amountSen })
        assertEquals(3334L, summary.nextInstalmentSen)

        // Overpayment > RM 100 rejected cleanly
        var overpayRejected = false
        try {
            repo.recordPayLaterPayment(planId, bankId, 10_001L, "Overpay", now, "pl-over")
        } catch (_: FinanceOperationException.InvalidTransaction) {
            overpayRejected = true
        }
        assertTrue(overpayRejected)
        assertEquals(200_000L, repo.observeWallets().first().single().balanceSen)

        // Pay #1 (3334 sen) + partial #2 (1666 sen) = RM 50.00 total paid
        repo.recordPayLaterPayment(planId, bankId, 3334L, "Instalment 1", now, "pl-1")
        repo.recordPayLaterPayment(planId, bankId, 1666L, "Partial #2", now + 1000L, "pl-2")

        summary = ObligationCalculator.summarizePayLater(repo.observePayLaterPlans().first().single())
        assertEquals(5_000L, summary.outstandingSen)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), summary.plan.nextDueDateEpochDay)
        assertEquals(1667L, summary.nextInstalmentSen)
        assertEquals(195_000L, repo.observeWallets().first().single().balanceSen)

        // Early settle remaining RM 50.00
        repo.recordPayLaterPayment(planId, bankId, 5_000L, "Early settle", now + 2000L, "pl-3")
        summary = ObligationCalculator.summarizePayLater(repo.observePayLaterPlans().first().single())
        assertEquals(0L, summary.outstandingSen)
        assertTrue(summary.isSettled)
        assertEquals(190_000L, repo.observeWallets().first().single().balanceSen)
    }

    @Test
    fun debts_separateLiabilitiesFromReceivables_neverInflateSafeToSpendUntilReceived() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val oweId = repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Farah", "Ticket", 30_000L, 0L, null, PrismaticAccentToken.PEACH, "", false, now, now)
        )
        val recId = repo.upsertDebt(
            DebtRecord(0L, DebtDirection.OWED_TO_ME, "Aiman", "Dinner", 20_000L, 0L, null, PrismaticAccentToken.AQUA, "", false, now, now)
        )

        // Creating debts & receivables MUST NOT change wallet balance or Safe to Spend
        assertEquals(100_000L, repo.observeWallets().first().single().balanceSen)
        val summaryBefore = FinanceSummaryCalculator.calculate(
            repo.observeWallets().first(), repo.observeTransactions().first(), emptyList(), emptyList()
        )
        assertEquals(100_000L, summaryBefore.safeToSpendSen)

        // Over-collection rejected
        var overCollectFailed = false
        try {
            repo.recordDebtSettlement(recId, bankId, 25_000L, "Too much", now, "d-over")
        } catch (_: FinanceOperationException.InvalidTransaction) {
            overCollectFailed = true
        }
        assertTrue(overCollectFailed)

        // Receive RM 120 of RM 200 receivable -> wallet & Safe to Spend increase by RM 120!
        repo.recordDebtSettlement(recId, bankId, 12_000L, "Part receipt", now, "d-rec-1")
        assertEquals(112_000L, repo.observeWallets().first().single().balanceSen)

        // Repay RM 100 of RM 300 liability -> wallet decreases by RM 100!
        repo.recordDebtSettlement(oweId, bankId, 10_000L, "Part repay", now + 1000L, "d-owe-1")
        assertEquals(102_000L, repo.observeWallets().first().single().balanceSen)

        val overview = ObligationCalculator.calculateOverview(
            emptyList(), emptyList(), repo.observeDebts().first()
        )
        assertEquals(20_000L, overview.debtOwedByMeSen)
        assertEquals(8_000L, overview.debtOwedToMeSen)
    }

    @Test
    fun linkedTransactionEditsAndDeletions_reconcileObligationsAndBudgetsConsistently() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val categories = repo.observeCategories().first()
        val billsCat = categories.first { it.name == "Bills & Utilities" }
        val nov10 = LocalDate.of(2026, 11, 10).toEpochDay()
        val nov10Ms = LocalDate.of(2026, 11, 10).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

        // Create Nov 2026 Overall Budget of RM 1,000.00
        repo.upsertBudget(
            Budget(0L, "Nov Overall", BudgetScope.OVERALL, BudgetPeriodType.MONTHLY, "2026-11", null, null, "", 100_000L, PrismaticAccentToken.LAVENDER, "", false, now, now)
        )

        val commId = repo.upsertCommitmentDetail(
            Commitment(0L, "TNB Bill", 20_000L, nov10, CommitmentStatus.UPCOMING, bankId, true, null, billsCat.name, billsCat.id, CommitmentFrequency.MONTHLY)
        )
        val debtId = repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Ali", "Loan", 15_000L, 0L, nov10, PrismaticAccentToken.PEACH, "", false, now, now)
        )

        // Before payments: Budget spent = 0
        var budgetOverview = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.MONTHLY, YearMonth.of(2026, 11), null,
            repo.observeBudgets().first(), repo.observeTransactions().first(), emptyList(), categories, zoneId
        )
        assertEquals(0L, budgetOverview.totalPeriodExpenseSen)

        // Pay TNB Bill in full (RM 200) -> rolls due date to Dec 10, Budget shows RM 200 spent!
        repo.recordCommitmentPayment(commId, bankId, 20_000L, "Paid TNB", nov10Ms, "rec-comm")
        repo.recordDebtSettlement(debtId, bankId, 10_000L, "Repaid Ali", nov10Ms, "rec-debt")

        val txs = repo.observeTransactions().first()
        val tnbTx = txs.first { it.title.contains("TNB") }
        val debtTx = txs.first { it.title.contains("Ali") }
        assertEquals(170_000L, repo.observeWallets().first().single().balanceSen)

        // Edit Debt tx from RM 100 -> RM 120 -> debt.paidSen updates to RM 120, wallet updates cleanly
        repo.updateTransaction(debtTx.copy(amountSen = 12_000L))
        assertEquals(12_000L, repo.observeDebts().first().single().paidSen)
        assertEquals(168_000L, repo.observeWallets().first().single().balanceSen)

        // Edit Debt tx above RM 150 must FAIL cleanly without corrupting debt or wallet!
        var editExceedFailed = false
        try {
            repo.updateTransaction(debtTx.copy(amountSen = 16_000L))
        } catch (_: FinanceOperationException.InvalidTransaction) {
            editExceedFailed = true
        }
        assertTrue(editExceedFailed)
        assertEquals(12_000L, repo.observeDebts().first().single().paidSen)

        // Delete TNB linked transaction -> restores Nov 10 UPCOMING (paidSen = 0, nextDue = null) & restores RM 200!
        repo.deleteTransaction(tnbTx.id)
        val restoredComm = repo.observeCommitments().first().single()
        assertEquals(nov10, restoredComm.dueDateEpochDay)
        assertEquals(CommitmentStatus.UPCOMING, restoredComm.status)
        assertEquals(0L, restoredComm.paidSen)
        assertEquals(null, restoredComm.nextDueDateEpochDay)
        assertEquals(188_000L, repo.observeWallets().first().single().balanceSen)

        // Delete Debt linked transaction -> resets debt.paidSen to 0 & restores wallet to RM 2000
        repo.deleteTransaction(debtTx.id)
        assertEquals(0L, repo.observeDebts().first().single().paidSen)
        assertEquals(200_000L, repo.observeWallets().first().single().balanceSen)

        budgetOverview = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.MONTHLY, YearMonth.of(2026, 11), null,
            repo.observeBudgets().first(), repo.observeTransactions().first(), emptyList(), categories, zoneId
        )
        assertEquals(0L, budgetOverview.totalPeriodExpenseSen)
    }

    @Test
    fun v5ToV6Migration_preservesPhase1To4BDataAndLegacyCommitments() : Unit = runBlocking {
        val dbFile = File(context.cacheDir, "test_phase4c_migration_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
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

                        DuitDatabase.MIGRATION_2_3.migrate(db)
                        DuitDatabase.MIGRATION_3_4.migrate(db)
                        DuitDatabase.MIGRATION_4_5.migrate(db)

                        db.execSQL("INSERT INTO wallets VALUES (1, 'MAE', 'BANK', 250000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO savings_goals VALUES (10, 'Japan', 100000, NULL, '', 'travel', 'SKY_BLUE', 'ACTIVE', 1000, 1000)")
                        db.execSQL("INSERT INTO savings_wallet_allocations VALUES (1, 10, 1, 50000, 1000)")
                        db.execSQL("INSERT INTO budgets VALUES (1, 'Overall', 'OVERALL', 'MONTHLY', '2026-11', NULL, NULL, '', 150000, 'LAVENDER', '', 0, 1000, 1000)")
                        // Legacy v5 commitments: 1 recurring upcoming, 1 one-time already PAID
                        db.execSQL("INSERT INTO commitments VALUES (1, 'Legacy Rent', 120000, 20000, 'UPCOMING', 1, 1, NULL)")
                        db.execSQL("INSERT INTO commitments VALUES (2, 'Legacy Paid Bill', 15000, 19990, 'PAID', 1, 0, NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
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
        val migratedRepo = OfflineFirstFinanceRepository(migratedDb)

        // Phase 1-4B records intact
        assertEquals(250_000L, migratedRepo.observeWallets().first().single().balanceSen)
        assertEquals(50_000L, migratedRepo.observeSavingsAllocations().first().single().allocatedSen)
        assertEquals(150_000L, migratedRepo.observeBudgets().first().single().limitSen)

        // Legacy v5 commitments migrated losslessly
        val comms = migratedRepo.observeCommitments().first()
        assertEquals(2, comms.size)
        val rent = comms.first { it.id == 1L }
        assertEquals(CommitmentFrequency.MONTHLY, rent.frequency)
        assertEquals(0L, rent.paidSen)
        val paidBill = comms.first { it.id == 2L }
        assertEquals(CommitmentFrequency.ONE_TIME, paidBill.frequency)
        assertEquals(15_000L, paidBill.paidSen)

        // Verify CommitmentDisplayState computation works seamlessly
        val overdueSummary = ObligationCalculator.summarizeCommitment(rent, today = LocalDate.ofEpochDay(20005))
        assertEquals(CommitmentDisplayState.OVERDUE, overdueSummary.displayState)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun bug4c001_paidMonthlyRecurringCommitmentExcludedFromReserve_preservesSafeToSpendAndFutureOccurrence() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val now = System.currentTimeMillis()
        // Exact Xiaomi 17T QA scenario:
        // Test wallet: RM 2,108, Second spending wallet: RM 30, Protected savings vault: RM 400 -> Total RM 2,538
        val testWalletId = repo.upsertWallet(
            Wallet(0L, "Test wallet", WalletType.CASH, 210_800L, false, PrismaticAccentToken.PEACH, now, now)
        )
        repo.upsertWallet(
            Wallet(0L, "Test bank", WalletType.BANK, 3_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        repo.upsertWallet(
            Wallet(0L, "Test saving vault", WalletType.SAVINGS, 40_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )

        val novToday = LocalDate.of(2026, 11, 16)
        val novCycle = SalaryCycle(
            id = 1L,
            label = "Nov 2026 Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            endDateEpochDay = Long.MAX_VALUE, // Open-ended cycle stored by Phase 3 cycle reconciliation
            paydayEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            baseSalarySen = 250_000L,
            committedBudgetSen = 10_000L,
            isActive = true,
            isOpenEnded = true
        )
        val nov16 = LocalDate.of(2026, 11, 16).toEpochDay()
        val commId = repo.upsertCommitmentDetail(
            Commitment(
                id = 0L,
                title = "Test Maxis Fibre",
                amountSen = 10_000L, // RM 100
                dueDateEpochDay = nov16,
                status = CommitmentStatus.UPCOMING,
                walletId = testWalletId,
                isRecurring = true,
                salaryCycleId = null,
                frequency = CommitmentFrequency.MONTHLY
            )
        )

        // Before payment: Total RM 2,538, Savings RM 400, Current Cycle Reserved RM 100, Safe to Spend RM 2,038
        var summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(253_800L, summary.totalBalanceSen)
        assertEquals(40_000L, summary.savingsBalanceSen)
        assertEquals(10_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        // Record RM 100 payment from Test wallet -> advances recurring commitment cleanly to Dec 16 UPCOMING
        repo.recordCommitmentPayment(commId, testWalletId, 10_000L, "Nov fibre", now, "bug4c001-pay")

        // After payment: Total RM 2,438, Test wallet RM 2,008, Savings RM 400,
        // Current Cycle Reserved RM 0 (Dec 16 > Dec 4 horizon), Upcoming Future Bills RM 100, Safe to Spend STILL RM 2,038!
        val txs = repo.observeTransactions().first()
        assertEquals(1, txs.size)
        assertEquals(10_000L, txs.single().amountSen)
        assertEquals(TransactionType.EXPENSE, txs.single().type)
        assertEquals(200_800L, repo.observeWallets().first().first { it.id == testWalletId }.balanceSen)

        val rolledMaxis = repo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.UPCOMING, rolledMaxis.status)
        assertEquals(0L, rolledMaxis.paidSen)
        assertEquals(LocalDate.of(2026, 12, 16).toEpochDay(), rolledMaxis.dueDateEpochDay)

        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = txs,
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(243_800L, summary.totalBalanceSen)
        assertEquals(40_000L, summary.savingsBalanceSen)
        assertEquals(0L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(10_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        // Reconcile on tx edit: lower RM 100 -> RM 40 -> rolls back to Nov 16 PARTIALLY_PAID (RM 60 remaining in-cycle) -> Safe to Spend STILL RM 2,038!
        repo.updateTransaction(txs.single().copy(amountSen = 4_000L))
        val partialMaxis = repo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.PARTIALLY_PAID, partialMaxis.status)
        assertEquals(nov16, partialMaxis.dueDateEpochDay)
        assertEquals(4_000L, partialMaxis.paidSen)

        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(249_800L, summary.totalBalanceSen)
        assertEquals(6_000L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(0L, summary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        // Reconcile on tx edit back up RM 40 -> RM 100 -> advances to Dec 16 UPCOMING, Current Cycle Reserved RM 0, Safe to Spend RM 2,038
        repo.updateTransaction(repo.observeTransactions().first().single().copy(amountSen = 10_000L))
        val reRolled = repo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.UPCOMING, reRolled.status)
        assertEquals(LocalDate.of(2026, 12, 16).toEpochDay(), reRolled.dueDateEpochDay)
        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(0L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(10_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, summary.safeToSpendSen)
        assertEquals(1, repo.observeObligationPayments().first().size)
    }

    @Test
    fun bug4c001_v6ToV7MigrationRepairsAlreadyPaidRolledCommitmentWithoutCreatingDuplicateExpense() : Unit = runBlocking {
        context.cacheDir.mkdirs()
        val dbFile = File(context.cacheDir, "test_bug4c001_v6_v7_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val nov16 = LocalDate.of(2026, 11, 16).toEpochDay()
        val dec16 = LocalDate.of(2026, 12, 16).toEpochDay()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
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

                        DuitDatabase.MIGRATION_2_3.migrate(db)
                        DuitDatabase.MIGRATION_3_4.migrate(db)
                        DuitDatabase.MIGRATION_4_5.migrate(db)
                        DuitDatabase.MIGRATION_5_6.migrate(db)

                        // Exact Xiaomi 17T broken v6 state:
                        // Test wallet RM 2,008 + Test bank RM 30 + Saving vault RM 400 = RM 2,438
                        db.execSQL("INSERT INTO wallets VALUES (1, 'Test wallet', 'CASH', 200800, 0, 'PEACH', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO wallets VALUES (2, 'Test bank', 'BANK', 3000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO wallets VALUES (3, 'Test saving vault', 'SAVINGS', 40000, 1, 'SKY_BLUE', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO transactions VALUES (1, 1, NULL, 'EXPENSE', 10000, 'Bills & Utilities', 'Payment · Test Maxis Fibre', '', NULL, 2000000, 2000000, NULL, 'ob_tx_bug')")
                        // Prematurely rolled-over recurring commitment in v6: due Dec 16, UPCOMING, paidSen = 0
                        db.execSQL("INSERT INTO commitments VALUES (1, 'Test Maxis Fibre', 10000, $dec16, 'UPCOMING', 1, 1, NULL, 'Bills & Utilities', NULL, 'MONTHLY', NULL, 16, 0, '', 0, 1000, 1000)")
                        // Payment row for Nov 16
                        db.execSQL("INSERT INTO obligation_payments VALUES (1, 'COMMITMENT', 1, 1, 1, 10000, $nov16, 2000000, '', 'bug-sub-1')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
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
        val migratedRepo = OfflineFirstFinanceRepository(migratedDb)

        // Zero duplicate transactions created, wallet RM 2,008 untouched!
        val txs = migratedRepo.observeTransactions().first()
        assertEquals(1, txs.size)
        assertEquals(200_800L, migratedRepo.observeWallets().first().first { it.id == 1L }.balanceSen)

        // v7->v8 restores recurring commitment cleanly to Dec 25/16 UPCOMING with paidSen = 0
        val repaired = migratedRepo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.UPCOMING, repaired.status)
        assertEquals(0L, repaired.paidSen)
        assertEquals(dec16, repaired.dueDateEpochDay)
        assertEquals(null, repaired.nextDueDateEpochDay)

        val summary = FinanceSummaryCalculator.calculate(
            wallets = migratedRepo.observeWallets().first(),
            transactions = txs,
            commitments = migratedRepo.observeCommitments().first(),
            commissions = emptyList(),
            today = LocalDate.of(2026, 11, 16)
        )
        assertEquals(243_800L, summary.totalBalanceSen)
        assertEquals(40_000L, summary.savingsBalanceSen)
        assertEquals(0L, summary.upcomingCommitmentsUnreservedSen)
        assertEquals(10_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun cycleAware_salaryCycleAndCalendarFallback_excludeNextCycleBillsFromSafeToSpend() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val now = System.currentTimeMillis()
        val walletId = repo.upsertWallet(
            Wallet(0L, "Test wallet", WalletType.CASH, 210_800L, false, PrismaticAccentToken.PEACH, now, now)
        )
        repo.upsertWallet(
            Wallet(0L, "Test bank", WalletType.BANK, 3_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        repo.upsertWallet(
            Wallet(0L, "Test saving vault", WalletType.SAVINGS, 40_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )

        val nov25 = LocalDate.of(2026, 11, 25).toEpochDay()
        val dec25 = LocalDate.of(2026, 12, 25).toEpochDay()
        val novToday = LocalDate.of(2026, 11, 16)
        val closedNovCycle = SalaryCycle(
            id = 10L,
            label = "Nov Salary Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 12, 4).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            baseSalarySen = 250_000L,
            committedBudgetSen = 10_000L,
            isActive = true,
            isOpenEnded = false
        )

        val maxisId = repo.upsertCommitmentDetail(
            Commitment(
                id = 0L,
                title = "Test Maxis Fibre",
                amountSen = 10_000L,
                dueDateEpochDay = nov25,
                status = CommitmentStatus.UPCOMING,
                walletId = walletId,
                isRecurring = true,
                salaryCycleId = closedNovCycle.id,
                frequency = CommitmentFrequency.MONTHLY
            )
        )

        // Before paying Nov 25: RM 100 reserved, Safe to Spend = RM 2,038
        var summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = closedNovCycle,
            today = novToday
        )
        assertEquals(10_000L, summary.currentCycleCommitmentsReservedSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        // Record RM 100 payment -> rolls commitment to 25 Dec 2026 UPCOMING
        repo.recordCommitmentPayment(maxisId, walletId, 10_000L, "Paid Nov 25", now, "maxis-nov25")
        val updated = repo.observeCommitments().first().single()
        assertEquals(dec25, updated.dueDateEpochDay)
        assertEquals(CommitmentStatus.UPCOMING, updated.status)

        // Active closed cycle (Nov 5 - Dec 4): Dec 25 is OUTSIDE horizon -> Safe to Spend remains RM 2,038!
        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = closedNovCycle,
            today = novToday
        )
        assertEquals(243_800L, summary.totalBalanceSen)
        assertEquals(0L, summary.currentCycleCommitmentsReservedSen)
        assertEquals(10_000L, summary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, summary.safeToSpendSen)

        // Calendar month fallback (activeCycle = null on Nov 16 -> ends Nov 30): Dec 25 ALSO excluded!
        val fallbackSummary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = null,
            today = novToday
        )
        assertEquals(0L, fallbackSummary.currentCycleCommitmentsReservedSen)
        assertEquals(10_000L, fallbackSummary.upcomingFutureCommitmentsSen)
        assertEquals(203_800L, fallbackSummary.safeToSpendSen)
        assertEquals("Nov 2026", fallbackSummary.activeReservePeriodLabel)

        // When Dec cycle becomes active (Dec 5 - Jan 4), Dec 25 enters current cycle and reserves RM 100!
        val decCycle = SalaryCycle(
            id = 11L,
            label = "Dec Salary Cycle",
            startDateEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            endDateEpochDay = Long.MAX_VALUE,
            paydayEpochDay = LocalDate.of(2027, 1, 5).toEpochDay(),
            baseSalarySen = 250_000L,
            committedBudgetSen = 10_000L,
            isActive = true,
            isOpenEnded = true
        )
        val decSummary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = repo.observeCommitments().first(),
            commissions = emptyList(),
            activeCycle = decCycle,
            today = LocalDate.of(2026, 12, 6)
        )
        assertEquals(10_000L, decSummary.currentCycleCommitmentsReservedSen)
        assertEquals(0L, decSummary.upcomingFutureCommitmentsSen)
        assertEquals(193_800L, decSummary.safeToSpendSen) // RM 2,038 - RM 100
    }

    @Test
    fun cycleAware_payLaterReservesOnlyCurrentCycleInstalmentNotFullBalance() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val walletId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val novToday = LocalDate.of(2026, 11, 16)
        val novCycle = SalaryCycle(
            id = 1L,
            label = "Nov Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 12, 4).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            baseSalarySen = 300_000L,
            committedBudgetSen = 0L,
            isActive = true,
            isOpenEnded = false
        )

        // 3 instalments of RM 200 due Nov 20, Dec 20, Jan 20 -> RM 600 total, only RM 200 in Nov cycle!
        val planId = repo.upsertPayLaterPlan(
            PayLaterPlan(
                id = 0L,
                title = "Monitor",
                providerName = "SPayLater",
                purchaseAmountSen = 60_000L,
                feeOrInterestSen = 0L,
                paidSen = 0L,
                instalmentCount = 3,
                firstDueDateEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
                nextDueDateEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
                accentToken = PrismaticAccentToken.PEACH,
                note = "",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        var summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            payLaterPlans = repo.observePayLaterPlans().first(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(20_000L, summary.currentCyclePayLaterReservedSen)
        assertEquals(60_000L, summary.totalPayLaterOutstandingSen)
        assertEquals(20_000L, summary.nextPayLaterInstalmentSen)
        assertEquals(180_000L, summary.safeToSpendSen) // RM 2,000 - RM 200 (NOT RM 600!)

        // Pay Nov 20 instalment (RM 200) -> next instalment is Dec 20 (outside Nov cycle)
        repo.recordPayLaterPayment(planId, walletId, 20_000L, "Instalment 1", now, "pl-cycle-1")

        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            payLaterPlans = repo.observePayLaterPlans().first(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(180_000L, summary.totalBalanceSen)
        assertEquals(0L, summary.currentCyclePayLaterReservedSen)
        assertEquals(40_000L, summary.totalPayLaterOutstandingSen)
        assertEquals(20_000L, summary.nextPayLaterInstalmentSen)
        assertEquals(180_000L, summary.safeToSpendSen) // Remains RM 1,800 without double deducting Dec/Jan instalments!
    }

    @Test
    fun cycleAware_debtReservesOnlyScheduledInCycleRepaymentNotOpenEndedBalance() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val walletId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val novToday = LocalDate.of(2026, 11, 16)
        val novCycle = SalaryCycle(
            id = 1L,
            label = "Nov Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 12, 4).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            baseSalarySen = 300_000L,
            committedBudgetSen = 0L,
            isActive = true,
            isOpenEnded = false
        )

        // Debt 1: RM 1,000 I_OWE open-ended (no scheduled repayment) -> reserves RM 0
        repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Friend", "Laptop", 100_000L, 0L, LocalDate.of(2026, 11, 25).toEpochDay(), PrismaticAccentToken.PEACH, "", false, now, now, scheduledRepaymentSen = null)
        )
        // Debt 2: RM 800 I_OWE with RM 200 scheduled repayment due Nov 28 (in Nov cycle) -> reserves RM 200
        val scheduledInCycleId = repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Sibling", "Loan", 80_000L, 0L, LocalDate.of(2026, 11, 28).toEpochDay(), PrismaticAccentToken.PEACH, "", false, now, now, scheduledRepaymentSen = 20_000L)
        )
        // Debt 3: RM 500 I_OWE with RM 150 scheduled due Dec 20 (next cycle) -> reserves RM 0 in Nov cycle
        repo.upsertDebt(
            DebtRecord(0L, DebtDirection.I_OWE, "Colleague", "Advance", 50_000L, 0L, LocalDate.of(2026, 12, 20).toEpochDay(), PrismaticAccentToken.PEACH, "", false, now, now, scheduledRepaymentSen = 15_000L)
        )
        // Debt 4: RM 300 OWED_TO_ME due Nov 20 -> receivables never reserve or inflate Safe to Spend
        repo.upsertDebt(
            DebtRecord(0L, DebtDirection.OWED_TO_ME, "Aiman", "Split", 30_000L, 0L, LocalDate.of(2026, 11, 20).toEpochDay(), PrismaticAccentToken.AQUA, "", false, now, now, scheduledRepaymentSen = 30_000L)
        )

        var summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            debts = repo.observeDebts().first(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(20_000L, summary.currentCycleDebtReservedSen)
        assertEquals(180_000L, summary.safeToSpendSen) // RM 2,000 - RM 200 scheduled this cycle

        // Settle the scheduled RM 200 -> scheduled reserve clears cleanly, Safe to Spend remains RM 1,800
        repo.recordDebtSettlement(scheduledInCycleId, walletId, 20_000L, "Nov repayment", now, "debt-sched-1")
        summary = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            debts = repo.observeDebts().first(),
            activeCycle = novCycle,
            today = novToday
        )
        assertEquals(180_000L, summary.totalBalanceSen)
        assertEquals(0L, summary.currentCycleDebtReservedSen)
        assertEquals(180_000L, summary.safeToSpendSen)
    }

    @Test
    fun v7ToV8Migration_preservesAllDataAndAdvancesPaidRecurringCommitmentsLosslessly() : Unit = runBlocking {
        context.cacheDir.mkdirs()
        val dbFile = File(context.cacheDir, "test_phase4c_v7_v8_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val nov25 = LocalDate.of(2026, 11, 25).toEpochDay()
        val dec25 = LocalDate.of(2026, 12, 25).toEpochDay()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
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

                        DuitDatabase.MIGRATION_2_3.migrate(db)
                        DuitDatabase.MIGRATION_3_4.migrate(db)
                        DuitDatabase.MIGRATION_4_5.migrate(db)
                        DuitDatabase.MIGRATION_5_6.migrate(db)
                        DuitDatabase.MIGRATION_6_7.migrate(db)

                        db.execSQL("INSERT INTO wallets VALUES (1, 'Test wallet', 'CASH', 200800, 0, 'PEACH', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO transactions VALUES (1, 1, NULL, 'EXPENSE', 10000, 'Bills & Utilities', 'Payment · Test Maxis Fibre', '', NULL, 2000000, 2000000, NULL, 'ob_tx_v7')")
                        // v7 PAID recurring commitment waiting to advance to Dec 25
                        db.execSQL(
                            "INSERT INTO commitments(id, title, amountSen, dueDateEpochDay, status, walletId, isRecurring, salaryCycleId, category, categoryId, frequency, customIntervalDays, anchorDayOfMonth, paidSen, note, isArchived, createdAtEpochMs, updatedAtEpochMs, nextDueDateEpochDay) " +
                                "VALUES (1, 'Test Maxis Fibre', 10000, $nov25, 'PAID', 1, 1, NULL, 'Bills & Utilities', NULL, 'MONTHLY', NULL, 25, 10000, '', 0, 1000, 1000, $dec25)"
                        )
                        db.execSQL("INSERT INTO debts VALUES (1, 'I_OWE', 'Farah', 'Ticket', 25000, 0, $dec25, 'PEACH', '', 0, 1000, 1000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
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
        val migratedRepo = OfflineFirstFinanceRepository(migratedDb)

        assertEquals(200_800L, migratedRepo.observeWallets().first().single().balanceSen)
        assertEquals(1, migratedRepo.observeTransactions().first().size)

        val migratedComm = migratedRepo.observeCommitments().first().single()
        assertEquals(CommitmentStatus.UPCOMING, migratedComm.status)
        assertEquals(dec25, migratedComm.dueDateEpochDay)
        assertEquals(0L, migratedComm.paidSen)

        val migratedDebt = migratedRepo.observeDebts().first().single()
        assertEquals(null, migratedDebt.scheduledRepaymentSen)

        migratedDb.close()
        dbFile.delete()
    }
}
