package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.BudgetWarningLevel
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
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
class Phase4BBudgetRepositoryTest {

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

    private fun epochMsFor(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun emptyState_hasZeroBudgetsAndZeroLimits() : Unit = runBlocking {
        assertTrue(repo.observeBudgets().first().isEmpty())
        val ym = YearMonth.of(2026, 11)
        val overview = BudgetCalculator.calculatePeriodOverview(
            periodType = BudgetPeriodType.MONTHLY,
            selectedMonth = ym,
            selectedCycle = null,
            budgets = emptyList(),
            transactions = emptyList(),
            cycles = emptyList(),
            categories = emptyList(),
            zoneId = zoneId
        )
        assertEquals(0L, overview.effectiveLimitSen)
        assertEquals(0L, overview.totalPeriodExpenseSen)
        assertEquals("0%", overview.overallUsedPercentDisplay)
    }

    @Test
    fun duplicateValidation_blocksSameActiveScopeAndPeriod_allowsDifferentPeriodOrArchived() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val categories = repo.observeCategories().first()
        val foodCat = categories.first { it.name == "Food & Dining" && it.type == TransactionType.EXPENSE }
        val now = System.currentTimeMillis()

        val novOverallId = repo.upsertBudget(
            Budget(
                id = 0L,
                name = "",
                scope = BudgetScope.OVERALL,
                periodType = BudgetPeriodType.MONTHLY,
                yearMonth = "2026-11",
                salaryCycleId = null,
                categoryId = null,
                categoryName = "",
                limitSen = 200_000L,
                accentToken = PrismaticAccentToken.LAVENDER,
                note = "",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        assertTrue(novOverallId > 0L)

        // Duplicate Overall in Nov 2026 must fail
        var dupOverall = false
        try {
            repo.upsertBudget(
                Budget(
                    id = 0L,
                    name = "Second Overall",
                    scope = BudgetScope.OVERALL,
                    periodType = BudgetPeriodType.MONTHLY,
                    yearMonth = "2026-11",
                    salaryCycleId = null,
                    categoryId = null,
                    categoryName = "",
                    limitSen = 150_000L,
                    accentToken = PrismaticAccentToken.AQUA,
                    note = "",
                    isArchived = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
        } catch (_: FinanceOperationException.DuplicateBudget) {
            dupOverall = true
        }
        assertTrue(dupOverall)

        // Category budget in Nov 2026 succeeds
        val foodNovId = repo.upsertBudget(
            Budget(
                id = 0L,
                name = "",
                scope = BudgetScope.CATEGORY,
                periodType = BudgetPeriodType.MONTHLY,
                yearMonth = "2026-11",
                salaryCycleId = null,
                categoryId = foodCat.id,
                categoryName = foodCat.name,
                limitSen = 50_000L,
                accentToken = PrismaticAccentToken.PEACH,
                note = "",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        // Editing the same Food budget preserves its ID and does NOT trigger duplicate error
        repo.upsertBudget(
            repo.observeBudgets().first().first { it.id == foodNovId }.copy(limitSen = 60_000L)
        )
        assertEquals(60_000L, repo.observeBudgets().first().first { it.id == foodNovId }.limitSen)

        // Duplicate active Food budget in Nov 2026 fails
        var dupFood = false
        try {
            repo.upsertBudget(
                Budget(
                    id = 0L,
                    name = "Extra Food",
                    scope = BudgetScope.CATEGORY,
                    periodType = BudgetPeriodType.MONTHLY,
                    yearMonth = "2026-11",
                    salaryCycleId = null,
                    categoryId = foodCat.id,
                    categoryName = foodCat.name,
                    limitSen = 30_000L,
                    accentToken = PrismaticAccentToken.PEACH,
                    note = "",
                    isArchived = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
        } catch (_: FinanceOperationException.DuplicateBudget) {
            dupFood = true
        }
        assertTrue(dupFood)

        // Food budget in Dec 2026 succeeds
        val foodDecId = repo.upsertBudget(
            Budget(
                id = 0L,
                name = "",
                scope = BudgetScope.CATEGORY,
                periodType = BudgetPeriodType.MONTHLY,
                yearMonth = "2026-12",
                salaryCycleId = null,
                categoryId = foodCat.id,
                categoryName = foodCat.name,
                limitSen = 55_000L,
                accentToken = PrismaticAccentToken.PEACH,
                note = "",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        assertTrue(foodDecId > 0L)

        // Archiving Nov Food allows creating a new active Nov Food; then restoring archived Nov Food fails cleanly
        repo.setBudgetArchived(foodNovId, true)
        val foodNovNewId = repo.upsertBudget(
            Budget(
                id = 0L,
                name = "",
                scope = BudgetScope.CATEGORY,
                periodType = BudgetPeriodType.MONTHLY,
                yearMonth = "2026-11",
                salaryCycleId = null,
                categoryId = foodCat.id,
                categoryName = foodCat.name,
                limitSen = 70_000L,
                accentToken = PrismaticAccentToken.PEACH,
                note = "",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        assertTrue(foodNovNewId != foodNovId)

        var restoreBlocked = false
        try {
            repo.setBudgetArchived(foodNovId, false)
        } catch (_: FinanceOperationException.DuplicateBudget) {
            restoreBlocked = true
        }
        assertTrue(restoreBlocked)
    }

    @Test
    fun warningThresholds_and_noDoubleCounting_and_financialInvariantsPreserved() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val categories = repo.observeCategories().first()
        val foodCat = categories.first { it.name == "Food & Dining" && it.type == TransactionType.EXPENSE }
        val transportCat = categories.first { it.name == "Transport" && it.type == TransactionType.EXPENSE }
        val shoppingCat = categories.first { it.name == "Shopping" && it.type == TransactionType.EXPENSE }
        val now = System.currentTimeMillis()

        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 300_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val vaultId = repo.upsertWallet(
            Wallet(0L, "Vault", WalletType.SAVINGS, 100_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )
        val goalId = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Japan", 200_000L, null, "", "travel", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, now, now)
        )
        repo.allocateToGoal(goalId, bankId, 50_000L, "Reserve RM500 in MAE", "alloc-1")

        // Pending commission & Income & Wallet Transfer must NEVER count toward budget spending
        repo.upsertCommission(
            CommissionRecord(
                id = 0L,
                title = "Pending Nov Payout",
                sourceLabel = "Lenovo",
                amountSen = 90_000L,
                status = CommissionStatus.PENDING,
                earnedDateEpochDay = LocalDate.of(2026, 11, 10).toEpochDay(),
                payoutDateEpochDay = null,
                linkedTransactionId = null,
                notes = ""
            )
        )
        val novDateMs = epochMsFor(LocalDate.of(2026, 11, 10))
        repo.recordTransaction(
            FinanceTransaction(0L, bankId, null, TransactionType.INCOME, 150_000L, "Salary", "Salary", "", null, novDateMs, now, null, "inc-1")
        )
        repo.recordTransaction(
            FinanceTransaction(0L, bankId, vaultId, TransactionType.TRANSFER, 20_000L, "Wallet Transfer", "Save", "", null, novDateMs, now, null, "tr-1")
        )

        // Expenses in Nov 2026:
        // Food: RM 80.00 against RM 100.00 -> exact 80% (APPROACHING_80)
        // Transport: RM 100.00 against RM 100.00 -> exact 100% (AT_LIMIT_100)
        // Shopping: RM 125.00 against RM 100.00 -> 125% (EXCEEDED)
        repo.recordTransaction(
            FinanceTransaction(0L, bankId, null, TransactionType.EXPENSE, 8_000L, foodCat.name, "Dinner", "", null, novDateMs, now, foodCat.id, "exp-f1")
        )
        repo.recordTransaction(
            FinanceTransaction(0L, bankId, null, TransactionType.EXPENSE, 10_000L, transportCat.name, "Fuel", "", null, novDateMs, now, transportCat.id, "exp-t1")
        )
        repo.recordTransaction(
            FinanceTransaction(0L, bankId, null, TransactionType.EXPENSE, 12_500L, shoppingCat.name, "Shoes", "", null, novDateMs, now, shoppingCat.id, "exp-s1")
        )

        val summaryBeforeBudgets = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = repo.observeCommissions().first(),
            savingsAllocations = repo.observeSavingsAllocations().first(),
            savingsGoals = repo.observeSavingsGoals().first()
        )

        // Create Overall RM 500 + 3 Category limits
        repo.upsertBudget(Budget(0L, "", BudgetScope.OVERALL, BudgetPeriodType.MONTHLY, "2026-11", null, null, "", 50_000L, PrismaticAccentToken.LAVENDER, "", false, now, now))
        repo.upsertBudget(Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-11", null, foodCat.id, foodCat.name, 10_000L, PrismaticAccentToken.PEACH, "", false, now, now))
        repo.upsertBudget(Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-11", null, transportCat.id, transportCat.name, 10_000L, PrismaticAccentToken.SKY_BLUE, "", false, now, now))
        repo.upsertBudget(Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-11", null, shoppingCat.id, shoppingCat.name, 10_000L, PrismaticAccentToken.AQUA, "", false, now, now))

        val summaryAfterBudgets = FinanceSummaryCalculator.calculate(
            wallets = repo.observeWallets().first(),
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = repo.observeCommissions().first(),
            savingsAllocations = repo.observeSavingsAllocations().first(),
            savingsGoals = repo.observeSavingsGoals().first()
        )
        // Financial Integrity: budgets NEVER change Total Balance, Safe to Spend, or Protected Savings!
        assertEquals(summaryBeforeBudgets.totalBalanceSen, summaryAfterBudgets.totalBalanceSen)
        assertEquals(summaryBeforeBudgets.safeToSpendSen, summaryAfterBudgets.safeToSpendSen)
        assertEquals(summaryBeforeBudgets.savingsBalanceSen, summaryAfterBudgets.savingsBalanceSen)
        assertEquals(summaryBeforeBudgets.goalAllocatedSen, summaryAfterBudgets.goalAllocatedSen)

        val overview = BudgetCalculator.calculatePeriodOverview(
            periodType = BudgetPeriodType.MONTHLY,
            selectedMonth = YearMonth.of(2026, 11),
            selectedCycle = null,
            budgets = repo.observeBudgets().first(),
            transactions = repo.observeTransactions().first(),
            cycles = emptyList(),
            categories = categories,
            zoneId = zoneId
        )

        // Total eligible expenses = 8,000 + 10,000 + 12,500 = 30,500 (RM 305.00), counted ONCE!
        assertEquals(30_500L, overview.totalPeriodExpenseSen)
        assertEquals(30_500L, overview.overallBudget!!.spentSen)
        assertEquals(19_500L, overview.overallBudget!!.remainingSen)
        assertEquals(50_000L, overview.effectiveLimitSen)
        assertEquals(30_500L, overview.effectiveCoveredSpentSen)
        assertEquals(30_000L, overview.sumCategoryLimitsSen)
        assertFalse(overview.categoryLimitsExceedOverall)

        assertEquals(BudgetWarningLevel.APPROACHING_80, overview.categoryBudgets.first { it.budget.categoryId == foodCat.id }.warningLevel)
        assertEquals(BudgetWarningLevel.AT_LIMIT_100, overview.categoryBudgets.first { it.budget.categoryId == transportCat.id }.warningLevel)
        val shopProgress = overview.categoryBudgets.first { it.budget.categoryId == shoppingCat.id }
        assertEquals(BudgetWarningLevel.EXCEEDED, shopProgress.warningLevel)
        assertEquals(2_500L, shopProgress.overspentSen)

        assertEquals(1, overview.warningCount80)
        assertEquals(1, overview.warningCount100)
        assertEquals(1, overview.exceededCount)
    }

    @Test
    fun transactionEditsDeletesAndBackdating_plusSalaryCycleBudgets_recalculateAccurately() : Unit = runBlocking {
        repo.ensureDefaultCategoriesSeeded()
        val categories = repo.observeCategories().first()
        val foodCat = categories.first { it.name == "Food & Dining" && it.type == TransactionType.EXPENSE }
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 500_000L, false, PrismaticAccentToken.AQUA, now, now)
        )

        // Two reconciled salary cycles: Oct 4..Nov 3 (closed) and Nov 4..open (active)
        val octStart = LocalDate.of(2026, 10, 4).toEpochDay()
        val novStart = LocalDate.of(2026, 11, 4).toEpochDay()
        val octCycleId = repo.upsertAndReconcileSalaryCycle(
            SalaryCycle(0L, "Oct Cycle", octStart, Long.MAX_VALUE, octStart, 300_000L, 0L, false)
        )
        val novCycleId = repo.upsertAndReconcileSalaryCycle(
            SalaryCycle(0L, "Nov Cycle", novStart, Long.MAX_VALUE, novStart, 300_000L, 0L, true)
        )
        val reconciledCycles = repo.observeSalaryCycles().first()
        val octCycle = reconciledCycles.first { it.id == octCycleId }
        val novCycle = reconciledCycles.first { it.id == novCycleId }

        repo.upsertBudget(Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.SALARY_CYCLE, "", octCycleId, foodCat.id, foodCat.name, 40_000L, PrismaticAccentToken.PEACH, "", false, now, now))
        repo.upsertBudget(Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.SALARY_CYCLE, "", novCycleId, foodCat.id, foodCat.name, 50_000L, PrismaticAccentToken.PEACH, "", false, now, now))

        // Record RM 120 in Nov 10 (belongs to Nov Cycle)
        val txId = repo.recordTransaction(
            FinanceTransaction(0L, bankId, null, TransactionType.EXPENSE, 12_000L, foodCat.name, "Lunch", "", null, epochMsFor(LocalDate.of(2026, 11, 10)), now, foodCat.id, "tx-cyc-1")
        )

        var novOverview = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.SALARY_CYCLE, YearMonth.of(2026, 11), novCycle,
            repo.observeBudgets().first(), repo.observeTransactions().first(), reconciledCycles, categories, zoneId
        )
        assertEquals(12_000L, novOverview.categoryBudgets.single().spentSen)

        // Backdate that transaction to Nov 2 (still Nov month, but belongs to Oct Cycle!) and increase to RM 180
        val existingTx = repo.observeTransactions().first().single()
        repo.updateTransaction(
            existingTx.copy(amountSen = 18_000L, occurredAtEpochMs = epochMsFor(LocalDate.of(2026, 11, 2)))
        )

        novOverview = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.SALARY_CYCLE, YearMonth.of(2026, 11), novCycle,
            repo.observeBudgets().first(), repo.observeTransactions().first(), reconciledCycles, categories, zoneId
        )
        val octOverview = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.SALARY_CYCLE, YearMonth.of(2026, 10), octCycle,
            repo.observeBudgets().first(), repo.observeTransactions().first(), reconciledCycles, categories, zoneId
        )
        assertEquals(0L, novOverview.categoryBudgets.single().spentSen)
        assertEquals(18_000L, octOverview.categoryBudgets.single().spentSen)

        // Delete transaction -> Oct Cycle returns cleanly to RM 0.00
        repo.deleteTransaction(txId)
        val octAfterDelete = BudgetCalculator.calculatePeriodOverview(
            BudgetPeriodType.SALARY_CYCLE, YearMonth.of(2026, 10), octCycle,
            repo.observeBudgets().first(), repo.observeTransactions().first(), reconciledCycles, categories, zoneId
        )
        assertEquals(0L, octAfterDelete.categoryBudgets.single().spentSen)
    }

    @Test
    fun v4ToV5Migration_preservesAllPhase1To4ADataAndSavingsAllocations() : Unit = runBlocking {
        val dbFile = File(context.cacheDir, "test_phase4b_migration_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        // Create genuine v4 schema via v2 SQL + MIGRATION_2_3 + MIGRATION_3_4
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
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

                        db.execSQL("INSERT INTO wallets VALUES (1, 'Migrated Vault', 'SAVINGS', 60000, 1, 'SKY_BLUE', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO savings_goals VALUES (10, 'Japan Trip', 100000, NULL, 'Holiday', 'travel', 'SKY_BLUE', 'ACTIVE', 1000, 1000)")
                        db.execSQL("INSERT INTO savings_wallet_allocations VALUES (1, 10, 1, 30000, 1000)")
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
        val migratedRepo = OfflineFirstFinanceRepository(migratedDb)
        migratedRepo.ensureDefaultCategoriesSeeded()

        // Existing wallet, savings goal, and RM300 allocation are intact
        assertEquals(60_000L, migratedRepo.observeWallets().first().single().balanceSen)
        assertEquals(1, migratedRepo.observeSavingsGoals().first().size)
        assertEquals(30_000L, migratedRepo.observeSavingsAllocations().first().single().allocatedSen)

        // Create and edit budget in v5 without altering goal allocations
        val now = System.currentTimeMillis()
        val cat = migratedRepo.observeCategories().first().first { it.type == TransactionType.EXPENSE }
        val budgetId = migratedRepo.upsertBudget(
            Budget(0L, "", BudgetScope.CATEGORY, BudgetPeriodType.MONTHLY, "2026-11", null, cat.id, cat.name, 45_000L, PrismaticAccentToken.AQUA, "", false, now, now)
        )
        assertTrue(budgetId > 0L)
        assertEquals(30_000L, migratedRepo.observeSavingsAllocations().first().single().allocatedSen)

        // Copy Nov budgets -> Dec budgets works cleanly
        val copied = migratedRepo.copyMonthlyBudgets("2026-11", "2026-12")
        assertEquals(1, copied)
        assertEquals(2, migratedRepo.observeBudgets().first().size)

        migratedDb.close()
        dbFile.delete()
    }
}
