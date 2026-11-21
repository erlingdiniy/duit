package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.SavingsCalculator
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4ASavingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var repo: OfflineFirstFinanceRepository

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
    fun scenario1_emptyStateHasNoFakeGoalsOrAllocations() : Unit = runBlocking {
        assertTrue(repo.observeSavingsGoals().first().isEmpty())
        assertTrue(repo.observeSavingsAllocations().first().isEmpty())
        assertTrue(repo.observeSavingsMovements().first().isEmpty())

        val summary = FinanceSummaryCalculator.calculate(
            wallets = emptyList(),
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = emptyList(),
            savingsGoals = emptyList()
        )
        assertEquals(0L, summary.savingsBalanceSen)
        assertEquals(0L, summary.goalAllocatedSen)
        assertEquals(0, summary.activeGoalsCount)
    }

    @Test
    fun scenario2_and_3_goalLifecycleAndOverfundedMathAndSuggestedMonthly() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val futureDate = LocalDate.now().plusMonths(5).toEpochDay()

        val id1 = repo.upsertSavingsGoal(
            SavingsGoal(
                id = 0L,
                name = "Emergency Fund",
                targetAmountSen = 100_000L, // RM 1,000.00
                targetDateEpochDay = futureDate,
                description = "Core buffer",
                iconKey = "emergency",
                accentToken = PrismaticAccentToken.SKY_BLUE,
                status = SavingsGoalStatus.ACTIVE,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        val id2 = repo.upsertSavingsGoal(
            SavingsGoal(
                id = 0L,
                name = "Open Travel Vault",
                targetAmountSen = 50_000L, // RM 500.00
                targetDateEpochDay = null, // No deadline
                description = "",
                iconKey = "travel",
                accentToken = PrismaticAccentToken.AQUA,
                status = SavingsGoalStatus.ACTIVE,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        val walletId = repo.upsertWallet(
            Wallet(0L, "Main Bank", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        // Overfund goal 2 with RM 650.00 against RM 500.00 target
        repo.allocateToGoal(id2, walletId, 65_000L, "Bonus top-up", "sub-overfund")

        val goals = repo.observeSavingsGoals().first()
        val allocs = repo.observeSavingsAllocations().first()
        assertEquals(2, goals.size)

        val p1 = SavingsCalculator.calculateGoalProgress(goals.first { it.id == id1 }, allocs)
        assertEquals(0L, p1.savedSen)
        assertEquals(100_000L, p1.remainingSen)
        assertTrue((p1.suggestedMonthlySen ?: 0L) > 0L)

        val p2 = SavingsCalculator.calculateGoalProgress(goals.first { it.id == id2 }, allocs)
        assertEquals(65_000L, p2.savedSen)
        assertEquals(0L, p2.remainingSen)
        assertEquals(15_000L, p2.overfundedSen)
        assertEquals(1.0f, p2.progressFraction)
        assertNull(p2.suggestedMonthlySen)
        assertEquals("130%", p2.progressPercentDisplay)

        // Complete goal 2 keeps allocation locked
        repo.completeSavingsGoal(id2)
        assertEquals(SavingsGoalStatus.COMPLETED, repo.observeSavingsGoals().first().first { it.id == id2 }.status)
        assertEquals(65_000L, repo.observeSavingsAllocations().first().sumOf { it.allocatedSen })
    }

    @Test
    fun scenario4_ordinaryWalletAllocationReducesSafeToSpendWithoutChangingTotalBalance() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val goalId = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Laptop", 300_000L, null, "", "gadget", PrismaticAccentToken.LAVENDER, SavingsGoalStatus.ACTIVE, now, now)
        )

        repo.allocateToGoal(goalId, bankId, 20_000L, "Reserve RM 200", "sub-ord-1")

        val wallets = repo.observeWallets().first()
        val allocs = repo.observeSavingsAllocations().first()
        val goals = repo.observeSavingsGoals().first()

        // Money remains inside the wallet
        assertEquals(100_000L, wallets.first().balanceSen)

        val summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = allocs,
            savingsGoals = goals
        )
        assertEquals(100_000L, summary.totalBalanceSen)
        assertEquals(20_000L, summary.savingsBalanceSen)
        assertEquals(80_000L, summary.safeToSpendSen)
        assertEquals(20_000L, summary.goalAllocatedSen)
    }

    @Test
    fun scenario5_and_6_transferToProtectedAndLinkProtectedNeverDoubleCounts() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val vaultId = repo.upsertWallet(
            Wallet(0L, "ASB Vault", WalletType.SAVINGS, 30_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )
        val goalId = repo.upsertSavingsGoal(
            SavingsGoal(0L, "House DP", 500_000L, null, "", "home", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, now, now)
        )

        // Scenario 5: Transfer RM 200 from MAE to ASB Vault + allocate to goal
        repo.transferToProtectedWalletAndAllocate(goalId, bankId, vaultId, 20_000L, "Monthly save", "sub-tx-alloc")

        val txsAfterMove = repo.observeTransactions().first()
        assertEquals(1, txsAfterMove.size)
        assertEquals(TransactionType.TRANSFER, txsAfterMove.first().type)

        var wallets = repo.observeWallets().first()
        assertEquals(80_000L, wallets.first { it.id == bankId }.balanceSen)
        assertEquals(50_000L, wallets.first { it.id == vaultId }.balanceSen)

        var summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = txsAfterMove,
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = repo.observeSavingsAllocations().first(),
            savingsGoals = repo.observeSavingsGoals().first()
        )
        // Total balance still RM 1,300.00; Savings RM 500.00 (RM 200 allocated + RM 300 unallocated); Safe to Spend RM 800.00
        assertEquals(130_000L, summary.totalBalanceSen)
        assertEquals(50_000L, summary.savingsBalanceSen)
        assertEquals(80_000L, summary.safeToSpendSen)
        assertEquals(20_000L, summary.goalAllocatedSen)
        assertEquals(30_000L, summary.unallocatedProtectedWalletSen)
        assertEquals(0L, summary.cycleIncomeSen)
        assertEquals(0L, summary.cycleExpenseSen)

        // Scenario 6: Link remaining RM 200 already inside ASB Vault to the goal -> must NOT reduce Safe to Spend!
        repo.allocateToGoal(goalId, vaultId, 20_000L, "Link vault funds", "sub-link-vault")
        wallets = repo.observeWallets().first()
        summary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = repo.observeTransactions().first(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = repo.observeSavingsAllocations().first(),
            savingsGoals = repo.observeSavingsGoals().first()
        )
        assertEquals(130_000L, summary.totalBalanceSen)
        assertEquals(50_000L, summary.savingsBalanceSen) // Still RM 500, NO double count!
        assertEquals(80_000L, summary.safeToSpendSen) // Safe to Spend untouched!
        assertEquals(40_000L, summary.goalAllocatedSen)
        assertEquals(10_000L, summary.unallocatedProtectedWalletSen)
        assertEquals(
            SavingsMovementType.LINK_PROTECTED,
            repo.observeSavingsMovements().first().first().type
        )
    }

    @Test
    fun scenario7_8_9_withdrawReturnToSpendingAndReassignGoals() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val vaultId = repo.upsertWallet(
            Wallet(0L, "Vault", WalletType.SAVINGS, 50_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )
        val g1 = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Goal A", 100_000L, null, "", "savings", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, now, now)
        )
        val g2 = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Goal B", 100_000L, null, "", "car", PrismaticAccentToken.PEACH, SavingsGoalStatus.ACTIVE, now, now)
        )

        repo.allocateToGoal(g1, bankId, 20_000L, "Reserve RM 200", "sub-w1")
        // Scenario 7: Withdraw RM 50 from Goal A in ordinary wallet -> Safe to Spend returns RM 50
        repo.withdrawFromGoal(g1, bankId, 5_000L, "Release RM 50", "sub-w2")
        assertEquals(15_000L, repo.observeSavingsAllocations().first().first().allocatedSen)

        // Scenario 9: Reassign RM 50 from Goal A to Goal B inside same wallet
        repo.reassignGoalAllocation(g1, g2, bankId, 5_000L, "Shift", "sub-reassign")
        val allocsAfterReassign = repo.observeSavingsAllocations().first()
        assertEquals(10_000L, allocsAfterReassign.first { it.goalId == g1 }.allocatedSen)
        assertEquals(5_000L, allocsAfterReassign.first { it.goalId == g2 }.allocatedSen)

        // Scenario 8: Protected vault funds allocated to Goal A -> Transfer RM 100 back to spending
        repo.allocateToGoal(g1, vaultId, 10_000L, "Link vault", "sub-link-v")
        repo.transferFromGoalToSpendingWallet(g1, vaultId, bankId, 10_000L, "Use protected", "sub-return")

        val wallets = repo.observeWallets().first()
        assertEquals(110_000L, wallets.first { it.id == bankId }.balanceSen)
        assertEquals(40_000L, wallets.first { it.id == vaultId }.balanceSen)
    }

    @Test
    fun scenario10_and_11_overAllocationSpendingGuardsAndArchiveReleases() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 50_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val g1 = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Guard Goal", 80_000L, null, "", "emergency", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, now, now)
        )
        repo.allocateToGoal(g1, bankId, 30_000L, "Lock RM 300", "sub-lock")

        // Over-allocation > unreserved (500 - 300 = 200 available) must fail
        var overAllocFailed = false
        try {
            repo.allocateToGoal(g1, bankId, 25_000L, "Too much", "sub-fail-1")
        } catch (_: FinanceOperationException.InsufficientBalance) {
            overAllocFailed = true
        }
        assertTrue(overAllocFailed)

        // Spending > unreserved (trying to spend RM 250 leaving only RM 250 < RM 300 reserved) must fail
        var spendReservedFailed = false
        try {
            repo.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = bankId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 25_000L,
                    category = "Shopping",
                    title = "Blocked",
                    note = "Blocked",
                    commitmentId = null,
                    occurredAtEpochMs = now,
                    createdAtEpochMs = now,
                    submissionId = "tx-fail-reserved"
                )
            )
        } catch (_: FinanceOperationException.InsufficientBalance) {
            spendReservedFailed = true
        }
        assertTrue(spendReservedFailed)

        // Wallet deletion while allocations/history exist must fail
        var deleteWalletFailed = false
        try {
            repo.deleteWalletSafely(bankId)
        } catch (_: FinanceOperationException.WalletHasHistory) {
            deleteWalletFailed = true
        }
        assertTrue(deleteWalletFailed)

        // Scenario 11: Archiving goal safely releases RM 300 back to unreserved funds
        repo.archiveSavingsGoal(g1)
        assertTrue(repo.observeSavingsAllocations().first().isEmpty())
        assertEquals(SavingsGoalStatus.ARCHIVED, repo.observeSavingsGoals().first().first().status)

        // Now spending RM 250 succeeds cleanly
        repo.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = bankId,
                targetWalletId = null,
                type = TransactionType.EXPENSE,
                amountSen = 25_000L,
                category = "Shopping",
                title = "Allowed after archive",
                note = "Allowed after archive",
                commitmentId = null,
                occurredAtEpochMs = now,
                createdAtEpochMs = now,
                submissionId = "tx-ok-after-archive"
            )
        )
        assertEquals(25_000L, repo.observeWallets().first().first().balanceSen)

        // Delete goal cleanly
        repo.deleteSavingsGoalSafely(g1)
        assertTrue(repo.observeSavingsGoals().first().isEmpty())
    }

    @Test
    fun scenario12_duplicateSubmissionIdIdempotencyAndAtomicRollback() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now)
        )
        val g1 = repo.upsertSavingsGoal(
            SavingsGoal(0L, "Idempotent Goal", 100_000L, null, "", "savings", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, now, now)
        )

        val move1 = repo.allocateToGoal(g1, bankId, 10_000L, "First tap", "dup-sub-123")
        val move2 = repo.allocateToGoal(g1, bankId, 10_000L, "Repeated tap", "dup-sub-123")
        assertEquals(move1, move2)
        assertEquals(10_000L, repo.observeSavingsAllocations().first().first().allocatedSen)

        // Rollback check: transferring to non-existent wallet must fail cleanly without leaving partial state
        var rolledBack = false
        try {
            repo.transferToProtectedWalletAndAllocate(g1, bankId, 9999L, 10_000L, "Bad dest", "sub-bad-dest")
        } catch (_: FinanceOperationException.InvalidTransaction) {
            rolledBack = true
        }
        assertTrue(rolledBack)
        assertEquals(100_000L, repo.observeWallets().first().first().balanceSen)
        assertEquals(10_000L, repo.observeSavingsAllocations().first().first().allocatedSen)
    }

    @Test
    fun scenario13_persistenceAcrossReopenAndV3ToV4Migration() : Unit = runBlocking {
        val dbFile = File(context.cacheDir, "test_phase4a_migration_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        // Create genuine pre-existing v3 database with Phase 2/3 data
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                        db.execSQL("INSERT INTO wallets VALUES (1, 'Phase 3 MAE', 'BANK', 500000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        DuitDatabase.MIGRATION_2_3.migrate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val dbV4First = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                DuitDatabase.MIGRATION_1_2,
                DuitDatabase.MIGRATION_2_3,
                DuitDatabase.MIGRATION_3_4,
                DuitDatabase.MIGRATION_4_5,
                DuitDatabase.MIGRATION_5_6,
                DuitDatabase.MIGRATION_6_7,
                DuitDatabase.MIGRATION_7_8,
                DuitDatabase.MIGRATION_8_9,
                DuitDatabase.MIGRATION_9_10,
                DuitDatabase.MIGRATION_10_11,
                DuitDatabase.MIGRATION_11_12
            )
            .allowMainThreadQueries()
            .build()
        val repo1 = OfflineFirstFinanceRepository(dbV4First)

        // Existing Phase 3 wallet is intact
        val existingWallet = dbV4First.walletDao().getById(1L)!!.toDomain()
        assertEquals("Phase 3 MAE", existingWallet.name)
        assertEquals(500_000L, existingWallet.balanceSen)

        // Create goal and allocate RM 1,000.00
        val now = System.currentTimeMillis()
        val goalId = repo1.upsertSavingsGoal(
            SavingsGoal(0L, "Persisted Goal", 250_000L, null, "Keep", "savings", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, now, now)
        )
        repo1.allocateToGoal(goalId, 1L, 100_000L, "First save", "persist-sub-1")
        dbV4First.close()

        // Reopen database and verify savings goal + allocation + movement persisted
        val dbV4Reopen = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                DuitDatabase.MIGRATION_1_2,
                DuitDatabase.MIGRATION_2_3,
                DuitDatabase.MIGRATION_3_4,
                DuitDatabase.MIGRATION_4_5,
                DuitDatabase.MIGRATION_5_6,
                DuitDatabase.MIGRATION_6_7,
                DuitDatabase.MIGRATION_7_8,
                DuitDatabase.MIGRATION_8_9,
                DuitDatabase.MIGRATION_9_10,
                DuitDatabase.MIGRATION_10_11,
                DuitDatabase.MIGRATION_11_12
            )
            .allowMainThreadQueries()
            .build()
        val repo2 = OfflineFirstFinanceRepository(dbV4Reopen)
        assertEquals(1, repo2.observeSavingsGoals().first().size)
        assertEquals(100_000L, repo2.observeSavingsAllocations().first().first().allocatedSen)
        assertEquals(1, repo2.observeSavingsMovements().first().size)

        dbV4Reopen.close()
        dbFile.delete()
    }

    @Test
    fun bug4a001_editingFundedGoalMetadataPreservesAllocationsAndProgress() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val vaultId = repo.upsertWallet(
            Wallet(0L, "Savings Vault", WalletType.SAVINGS, 50_000L, true, PrismaticAccentToken.SKY_BLUE, now, now)
        )
        val bankId = repo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 40_000L, false, PrismaticAccentToken.AQUA, now, now)
        )

        // Step 1: Create savings goal with RM 500 target
        val goalId = repo.upsertSavingsGoal(
            SavingsGoal(
                id = 0L,
                name = "Emergency Fund",
                targetAmountSen = 50_000L, // RM 500.00
                targetDateEpochDay = null,
                description = "Initial description",
                iconKey = "emergency",
                accentToken = PrismaticAccentToken.SKY_BLUE,
                status = SavingsGoalStatus.ACTIVE,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        // Step 2: Allocate RM 300 from protected Savings Vault
        repo.allocateToGoal(goalId, vaultId, 30_000L, "Link RM 300 from vault", "bug4a001-vault")

        // Verify initial 60% progress
        val initialGoal = repo.observeSavingsGoals().first().single()
        val initialAllocs = repo.observeSavingsAllocations().first()
        val initialProgress = SavingsCalculator.calculateGoalProgress(initialGoal, initialAllocs)
        assertEquals(30_000L, initialProgress.savedSen)
        assertEquals("60%", initialProgress.progressPercentDisplay)

        // Step 3: Edit goal name, target to RM 600, target date, description, icon, and accent colour
        val newTargetDay = LocalDate.now().plusMonths(6).toEpochDay()
        val returnedId = repo.upsertSavingsGoal(
            initialGoal.copy(
                name = "Emergency & Safety Buffer",
                targetAmountSen = 60_000L, // RM 600.00
                targetDateEpochDay = newTargetDay,
                description = "Updated description",
                iconKey = "home",
                accentToken = PrismaticAccentToken.LAVENDER
            )
        )

        assertEquals(goalId, returnedId)

        // Step 4: Verify RM 300 allocation remains linked and progress updates cleanly to 50%
        val editedGoal = repo.observeSavingsGoals().first().single()
        val allocsAfterEdit = repo.observeSavingsAllocations().first()
        val movementsAfterEdit = repo.observeSavingsMovements().first()
        val walletsAfterEdit = repo.observeWallets().first()

        assertEquals(goalId, editedGoal.id)
        assertEquals("Emergency & Safety Buffer", editedGoal.name)
        assertEquals(60_000L, editedGoal.targetAmountSen)
        assertEquals(newTargetDay, editedGoal.targetDateEpochDay)
        assertEquals("Updated description", editedGoal.description)
        assertEquals("home", editedGoal.iconKey)
        assertEquals(PrismaticAccentToken.LAVENDER, editedGoal.accentToken)
        assertEquals(initialGoal.createdAtEpochMs, editedGoal.createdAtEpochMs)

        assertEquals(1, allocsAfterEdit.size)
        assertEquals(goalId, allocsAfterEdit.single().goalId)
        assertEquals(vaultId, allocsAfterEdit.single().walletId)
        assertEquals(30_000L, allocsAfterEdit.single().allocatedSen)
        assertEquals(1, movementsAfterEdit.size)

        val progressAfterEdit = SavingsCalculator.calculateGoalProgress(editedGoal, allocsAfterEdit)
        assertEquals(30_000L, progressAfterEdit.savedSen)
        assertEquals(30_000L, progressAfterEdit.remainingSen)
        assertEquals("50%", progressAfterEdit.progressPercentDisplay)
        assertEquals(0.5f, progressAfterEdit.progressFraction)

        // Also allocate RM 100 from ordinary bank wallet and edit again: both allocations stay intact
        repo.allocateToGoal(goalId, bankId, 10_000L, "Reserve RM 100 from MAE", "bug4a001-bank")
        repo.upsertSavingsGoal(
            editedGoal.copy(
                name = "Renamed Multi-Bucket Goal",
                targetAmountSen = 80_000L, // RM 800.00
                targetDateEpochDay = null
            )
        )
        val allocsMulti = repo.observeSavingsAllocations().first()
        val finalGoal = repo.observeSavingsGoals().first().single()
        val finalProgress = SavingsCalculator.calculateGoalProgress(finalGoal, allocsMulti)
        assertEquals(2, allocsMulti.size)
        assertEquals(40_000L, finalProgress.savedSen)
        assertEquals("50%", finalProgress.progressPercentDisplay)

        val summary = FinanceSummaryCalculator.calculate(
            wallets = walletsAfterEdit,
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = allocsMulti,
            savingsGoals = listOf(finalGoal)
        )
        // Vault RM500 + Bank RM400 = RM900 Total; Savings = RM500 vault + RM100 bank alloc = RM600; Safe to Spend = RM300
        assertEquals(90_000L, summary.totalBalanceSen)
        assertEquals(60_000L, summary.savingsBalanceSen)
        assertEquals(30_000L, summary.safeToSpendSen)
        assertEquals(40_000L, summary.goalAllocatedSen)
        assertEquals(20_000L, summary.unallocatedProtectedWalletSen)
    }
}
