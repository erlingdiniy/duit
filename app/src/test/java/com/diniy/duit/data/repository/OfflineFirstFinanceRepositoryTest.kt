package com.diniy.duit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstFinanceRepositoryTest {

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

    private suspend fun createWallet(
        name: String,
        balanceSen: Long,
        type: WalletType = WalletType.BANK,
        excludedFromSafe: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return repository.upsertWallet(
            Wallet(
                id = 0L,
                name = name,
                type = type,
                balanceSen = balanceSen,
                isExcludedFromSafeToSpend = excludedFromSafe,
                accentToken = PrismaticAccentToken.AQUA,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    @Test
    fun `income and expense update wallet balances accurately in integer sen`() = runTest {
        val walletId = createWallet("Maybank", balanceSen = 100_000L) // RM 1,000.00

        repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = walletId,
                targetWalletId = null,
                type = TransactionType.INCOME,
                amountSen = 25_050L,
                category = "Salary",
                title = "Allowance",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 1_000L,
                createdAtEpochMs = 1_000L,
                submissionId = "sub-income-1"
            )
        )
        assertEquals(125_050L, database.walletDao().getById(walletId)!!.balanceSen)

        repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = walletId,
                targetWalletId = null,
                type = TransactionType.EXPENSE,
                amountSen = 4_550L,
                category = "Food & Dining",
                title = "Lunch",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 2_000L,
                createdAtEpochMs = 2_000L,
                submissionId = "sub-expense-1"
            )
        )
        assertEquals(120_500L, database.walletDao().getById(walletId)!!.balanceSen)
    }

    @Test
    fun `transfers move funds between wallets and are excluded from income and expense totals`() = runTest {
        val bankId = createWallet("Bank", balanceSen = 300_000L)
        val eWalletId = createWallet("TNG", balanceSen = 50_000L, type = WalletType.EWALLET)

        repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = bankId,
                targetWalletId = eWalletId,
                type = TransactionType.TRANSFER,
                amountSen = 80_000L,
                category = "Wallet Transfer",
                title = "Top up TNG",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 3_000L,
                createdAtEpochMs = 3_000L,
                submissionId = "sub-transfer-1"
            )
        )

        val wallets = repository.observeWallets().first()
        val transactions = repository.observeTransactions().first()
        assertEquals(220_000L, wallets.first { it.id == bankId }.balanceSen)
        assertEquals(130_000L, wallets.first { it.id == eWalletId }.balanceSen)

        val summary = FinanceSummaryCalculator.calculate(wallets, transactions, emptyList(), emptyList())
        assertEquals(0L, summary.cycleIncomeSen)
        assertEquals(0L, summary.cycleExpenseSen)
        assertEquals(350_000L, summary.totalBalanceSen)
    }

    @Test
    fun `same wallet cannot be both source and destination on transfer`() = runTest {
        val bankId = createWallet("Bank", balanceSen = 100_000L)
        try {
            repository.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = bankId,
                    targetWalletId = bankId,
                    type = TransactionType.TRANSFER,
                    amountSen = 10_000L,
                    category = "Wallet Transfer",
                    title = "Invalid self transfer",
                    note = "",
                    commitmentId = null,
                    occurredAtEpochMs = 1_000L,
                    createdAtEpochMs = 1_000L
                )
            )
            fail("Expected InvalidTransaction when source and destination wallets match")
        } catch (expected: FinanceOperationException.InvalidTransaction) {
            assertEquals(100_000L, database.walletDao().getById(bankId)!!.balanceSen)
        }
    }

    @Test
    fun `editing and deleting transactions recalculate affected wallet balances`() = runTest {
        val bankId = createWallet("Bank", balanceSen = 200_000L)
        val cashId = createWallet("Cash", balanceSen = 50_000L, type = WalletType.CASH)

        val txId = repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = bankId,
                targetWalletId = null,
                type = TransactionType.EXPENSE,
                amountSen = 30_000L,
                category = "Bills & Utilities",
                title = "Internet",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 1_000L,
                createdAtEpochMs = 1_000L,
                submissionId = "sub-edit-1"
            )
        )
        assertEquals(170_000L, database.walletDao().getById(bankId)!!.balanceSen)

        // Edit: switch from Bank RM 300 expense to Cash RM 120 expense
        val original = database.transactionDao().getById(txId)!!.toDomain()
        repository.updateTransaction(
            original.copy(
                walletId = cashId,
                amountSen = 12_000L,
                title = "Internet (Cash)"
            )
        )
        assertEquals(200_000L, database.walletDao().getById(bankId)!!.balanceSen)
        assertEquals(38_000L, database.walletDao().getById(cashId)!!.balanceSen)

        // Delete restores Cash back to 50_000L
        repository.deleteTransaction(txId)
        assertEquals(50_000L, database.walletDao().getById(cashId)!!.balanceSen)
        assertTrue(repository.observeTransactions().first().isEmpty())
    }

    @Test
    fun `duplicate submissionId is prevented idempotently`() = runTest {
        val bankId = createWallet("Bank", balanceSen = 100_000L)
        val tx = FinanceTransaction(
            id = 0L,
            walletId = bankId,
            targetWalletId = null,
            type = TransactionType.EXPENSE,
            amountSen = 25_000L,
            category = "Shopping",
            title = "Shoes",
            note = "",
            commitmentId = null,
            occurredAtEpochMs = 1_000L,
            createdAtEpochMs = 1_000L,
            submissionId = "idempotent-key-42"
        )

        val firstId = repository.recordTransaction(tx)
        val secondId = repository.recordTransaction(tx)

        assertEquals(firstId, secondId)
        assertEquals(75_000L, database.walletDao().getById(bankId)!!.balanceSen)
        assertEquals(1, repository.observeTransactions().first().size)
    }

    @Test
    fun `invalid amounts and insufficient balance roll back atomically`() = runTest {
        val bankId = createWallet("Bank", balanceSen = 20_000L) // RM 200.00

        try {
            repository.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = bankId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 0L,
                    category = "Food",
                    title = "Zero",
                    note = "",
                    commitmentId = null,
                    occurredAtEpochMs = 1_000L,
                    createdAtEpochMs = 1_000L
                )
            )
            fail("Expected InvalidTransaction for 0 sen")
        } catch (_: FinanceOperationException.InvalidTransaction) {
            // Expected
        }

        try {
            repository.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = bankId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 25_000L,
                    category = "Food",
                    title = "Overdraft",
                    note = "",
                    commitmentId = null,
                    occurredAtEpochMs = 2_000L,
                    createdAtEpochMs = 2_000L
                )
            )
            fail("Expected InsufficientBalance when amount exceeds wallet balance")
        } catch (_: FinanceOperationException.InsufficientBalance) {
            assertEquals(20_000L, database.walletDao().getById(bankId)!!.balanceSen)
            assertTrue(repository.observeTransactions().first().isEmpty())
        }
    }

    @Test
    fun `wallet deletion is blocked when transactions exist and archiving preserves history`() = runTest {
        val usedWalletId = createWallet("Used Bank", balanceSen = 100_000L)
        val unusedWalletId = createWallet("Unused Cash", balanceSen = 10_000L, type = WalletType.CASH)

        repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = usedWalletId,
                targetWalletId = null,
                type = TransactionType.EXPENSE,
                amountSen = 5_000L,
                category = "Food",
                title = "Coffee",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 1_000L,
                createdAtEpochMs = 1_000L
            )
        )

        try {
            repository.deleteWalletSafely(usedWalletId)
            fail("Expected WalletHasHistory when deleting wallet with transactions")
        } catch (_: FinanceOperationException.WalletHasHistory) {
            // Expected safeguard
        }

        // Archiving is safe and hides wallet from active list while preserving balance & history
        repository.setWalletArchived(usedWalletId, true)
        assertTrue(repository.observeActiveWallets().first().none { it.id == usedWalletId })
        assertEquals(95_000L, database.walletDao().getById(usedWalletId)!!.balanceSen)

        // Unused wallet with 0 transactions can be deleted safely
        repository.deleteWalletSafely(unusedWalletId)
        assertFalse(repository.observeWallets().first().any { it.id == unusedWalletId })
    }

    @Test
    fun `renaming or archiving custom categories does not corrupt historical transactions`() = runTest {
        repository.ensureDefaultCategoriesSeeded()
        val catId = repository.createCategory(
            name = "Bubble Tea",
            type = TransactionType.EXPENSE,
            iconKey = "food",
            accentToken = PrismaticAccentToken.PEACH
        )
        val bankId = createWallet("Bank", balanceSen = 50_000L)
        repository.recordTransaction(
            FinanceTransaction(
                id = 0L,
                walletId = bankId,
                targetWalletId = null,
                type = TransactionType.EXPENSE,
                amountSen = 1_200L,
                category = "Bubble Tea",
                title = "Tealive",
                note = "",
                commitmentId = null,
                occurredAtEpochMs = 1_000L,
                createdAtEpochMs = 1_000L,
                categoryId = catId
            )
        )

        repository.renameCategory(catId, "Drinks & Cafe", "food", PrismaticAccentToken.LAVENDER)
        repository.setCategoryArchived(catId, true)

        val storedTx = repository.observeTransactions().first().single()
        assertEquals("Bubble Tea", storedTx.category)
        assertEquals(1_200L, storedTx.amountSen)
    }

    @Test
    fun `migration from v1 to v2 preserves existing wallet balances and persists across reopen`() = runTest {
        val dbFile = context.getDatabasePath("migration_test_duit.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a real v1 SQLite database with a wallet row
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS wallets (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                type TEXT NOT NULL,
                                balanceSen INTEGER NOT NULL,
                                isExcludedFromSafeToSpend INTEGER NOT NULL,
                                accentToken TEXT NOT NULL,
                                createdAtEpochMs INTEGER NOT NULL,
                                updatedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS transactions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                walletId INTEGER NOT NULL,
                                targetWalletId INTEGER,
                                type TEXT NOT NULL,
                                amountSen INTEGER NOT NULL,
                                category TEXT NOT NULL,
                                title TEXT NOT NULL,
                                note TEXT NOT NULL,
                                commitmentId INTEGER,
                                occurredAtEpochMs INTEGER NOT NULL,
                                createdAtEpochMs INTEGER NOT NULL,
                                FOREIGN KEY(walletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                                FOREIGN KEY(targetWalletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletId ON transactions(walletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_targetWalletId ON transactions(targetWalletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAtEpochMs ON transactions(occurredAtEpochMs)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_commitmentId ON transactions(commitmentId)")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS salary_cycles (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                label TEXT NOT NULL,
                                startDateEpochDay INTEGER NOT NULL,
                                endDateEpochDay INTEGER NOT NULL,
                                paydayEpochDay INTEGER NOT NULL,
                                baseSalarySen INTEGER NOT NULL,
                                committedBudgetSen INTEGER NOT NULL,
                                isActive INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS commitments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                amountSen INTEGER NOT NULL,
                                dueDateEpochDay INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                walletId INTEGER,
                                isRecurring INTEGER NOT NULL,
                                salaryCycleId INTEGER
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS commissions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                sourceLabel TEXT NOT NULL,
                                amountSen INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                earnedDateEpochDay INTEGER NOT NULL,
                                payoutDateEpochDay INTEGER,
                                linkedTransactionId INTEGER,
                                notes TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS daily_sales_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                recordDateEpochDay INTEGER NOT NULL,
                                grossSalesSen INTEGER NOT NULL,
                                netSalesSen INTEGER NOT NULL,
                                unitsSold INTEGER NOT NULL,
                                notes TEXT NOT NULL,
                                createdAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO wallets (id, name, type, balanceSen, isExcludedFromSafeToSpend, accentToken, createdAtEpochMs, updatedAtEpochMs) " +
                                "VALUES (1, 'Existing Xiaomi Wallet', 'BANK', 350000, 0, 'AQUA', 1000, 1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        // Reopen with Room v4 + MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4 and verify persistence
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
                DuitDatabase.MIGRATION_9_10,
                DuitDatabase.MIGRATION_10_11,
                DuitDatabase.MIGRATION_11_12,
                DuitDatabase.MIGRATION_12_13,
                DuitDatabase.MIGRATION_13_14
            )
            .allowMainThreadQueries()
            .build()

        val migratedWallet = migratedDb.walletDao().getById(1L)!!.toDomain()
        assertEquals("Existing Xiaomi Wallet", migratedWallet.name)
        assertEquals(350_000L, migratedWallet.balanceSen)
        assertTrue(migratedWallet.isIncludedInTotalBalance)
        assertFalse(migratedWallet.isArchived)

        migratedDb.close()
        dbFile.delete()
    }
}
