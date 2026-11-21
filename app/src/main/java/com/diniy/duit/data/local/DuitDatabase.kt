package com.diniy.duit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.diniy.duit.data.local.dao.SavingsChallengeDao
import com.diniy.duit.data.local.entity.ChallengeSkippedSlotEntity
import com.diniy.duit.data.local.entity.ChallengeStickerEntity
import com.diniy.duit.data.local.entity.SavingsChallengeEntity
import com.diniy.duit.data.local.dao.MoneyReminderDao
import com.diniy.duit.data.local.entity.MoneyReminderEntity
import androidx.sqlite.db.SupportSQLiteDatabase
import com.diniy.duit.data.local.converter.DuitTypeConverters
import com.diniy.duit.data.local.dao.BackupDao
import com.diniy.duit.data.local.dao.BudgetDao
import com.diniy.duit.data.local.dao.CategoryDao
import com.diniy.duit.data.local.dao.CommissionDao
import com.diniy.duit.data.local.dao.CommissionPaymentDao
import com.diniy.duit.data.local.dao.CommissionRuleVersionDao
import com.diniy.duit.data.local.dao.CommissionSourceDao
import com.diniy.duit.data.local.dao.CommitmentDao
import com.diniy.duit.data.local.dao.DailySalesRecordDao
import com.diniy.duit.data.local.dao.DebtDao
import com.diniy.duit.data.local.dao.NotificationHistoryDao
import com.diniy.duit.data.local.dao.ObligationPaymentDao
import com.diniy.duit.data.local.dao.PayLaterDao
import com.diniy.duit.data.local.dao.SalaryCycleDao
import com.diniy.duit.data.local.dao.SavingsAllocationDao
import com.diniy.duit.data.local.dao.SavingsGoalDao
import com.diniy.duit.data.local.dao.SavingsMovementDao
import com.diniy.duit.data.local.dao.TransactionDao
import com.diniy.duit.data.local.dao.WalletDao
import com.diniy.duit.data.local.entity.BudgetEntity
import com.diniy.duit.data.local.entity.CategoryEntity
import com.diniy.duit.data.local.entity.CommissionEntity
import com.diniy.duit.data.local.entity.CommissionPaymentEntity
import com.diniy.duit.data.local.entity.CommissionRuleVersionEntity
import com.diniy.duit.data.local.entity.CommissionSourceEntity
import com.diniy.duit.data.local.entity.CommitmentEntity
import com.diniy.duit.data.local.entity.DailySalesRecordEntity
import com.diniy.duit.data.local.entity.DebtEntity
import com.diniy.duit.data.local.entity.NotificationHistoryEntity
import com.diniy.duit.data.local.entity.ObligationPaymentEntity
import com.diniy.duit.data.local.entity.PayLaterPlanEntity
import com.diniy.duit.data.local.entity.SalaryCycleEntity
import com.diniy.duit.data.local.entity.SavingsAllocationEntity
import com.diniy.duit.data.local.entity.SavingsGoalEntity
import com.diniy.duit.data.local.entity.SavingsMovementEntity
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.WalletEntity

/**
 * Offline-first Room database for duit.
 * Starts empty of financial transactions; uses explicit lossless migrations to preserve user data.
 */
@Database(
    entities = [
        WalletEntity::class,
        TransactionEntity::class,
        SalaryCycleEntity::class,
        CommitmentEntity::class,
        CommissionEntity::class,
        DailySalesRecordEntity::class,
        CategoryEntity::class,
        CommissionSourceEntity::class,
        CommissionPaymentEntity::class,
        CommissionRuleVersionEntity::class,
        SavingsGoalEntity::class,
        SavingsAllocationEntity::class,
        SavingsMovementEntity::class,
        BudgetEntity::class,
        PayLaterPlanEntity::class,
        DebtEntity::class,
        ObligationPaymentEntity::class,
        NotificationHistoryEntity::class,
        MoneyReminderEntity::class,
        SavingsChallengeEntity::class,
        ChallengeSkippedSlotEntity::class,
        ChallengeStickerEntity::class,
        com.diniy.duit.data.local.entity.ReceiptEntity::class,
        com.diniy.duit.data.local.entity.EarnedMilestoneEntity::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(DuitTypeConverters::class)
abstract class DuitDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao
    abstract fun salaryCycleDao(): SalaryCycleDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun commissionDao(): CommissionDao
    abstract fun dailySalesRecordDao(): DailySalesRecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun commissionSourceDao(): CommissionSourceDao
    abstract fun commissionPaymentDao(): CommissionPaymentDao
    abstract fun commissionRuleVersionDao(): CommissionRuleVersionDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun savingsAllocationDao(): SavingsAllocationDao
    abstract fun savingsMovementDao(): SavingsMovementDao
    abstract fun budgetDao(): BudgetDao
    abstract fun payLaterDao(): PayLaterDao
    abstract fun debtDao(): DebtDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun obligationPaymentDao(): ObligationPaymentDao
    abstract fun backupDao(): BackupDao
    abstract fun moneyReminderDao(): MoneyReminderDao
    abstract fun savingsChallengeDao(): SavingsChallengeDao
    abstract fun receiptDao(): com.diniy.duit.data.local.dao.ReceiptDao
    abstract fun milestoneDao(): com.diniy.duit.data.local.dao.MilestoneDao

    companion object {
        private const val DATABASE_NAME = "duit_ledger.db"

        /**
         * Lossless migration from Phase 1 (v1) to Phase 2 (v2).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallets ADD COLUMN isIncludedInTotalBalance INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE wallets ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN categoryId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN submissionId TEXT DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_submissionId ON transactions(submissionId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        accentToken TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_type_isArchived ON categories(type, isArchived)")
            }
        }

        /**
         * Lossless migration from Phase 2 (v2) to Phase 3 (v3).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE salary_cycles ADD COLUMN receivedSalarySen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE salary_cycles ADD COLUMN salaryTransactionId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE salary_cycles ADD COLUMN isOpenEnded INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE daily_sales_records ADD COLUMN grossProfitSen INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE commissions ADD COLUMN sourceId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE commissions ADD COLUMN yearMonth TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE commissions ADD COLUMN estimatedGrossSen INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE commissions ADD COLUMN actualGrossSen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commissions ADD COLUMN receivedNetSen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commissions ADD COLUMN adjustmentNote TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS commission_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        accentToken TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commission_sources_isArchived ON commission_sources(isArchived)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS commission_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        commissionId INTEGER NOT NULL,
                        sourceId INTEGER,
                        sourceName TEXT NOT NULL,
                        walletId INTEGER NOT NULL,
                        linkedTransactionId INTEGER,
                        receivedAtEpochMs INTEGER NOT NULL,
                        actualGrossSen INTEGER NOT NULL,
                        epfDeductionSen INTEGER,
                        socsoDeductionSen INTEGER,
                        eisDeductionSen INTEGER,
                        actualNetSen INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        submissionId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commission_payments_commissionId ON commission_payments(commissionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commission_payments_receivedAtEpochMs ON commission_payments(receivedAtEpochMs)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_commission_payments_submissionId ON commission_payments(submissionId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS commission_rule_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleName TEXT NOT NULL,
                        effectiveFromEpochDay INTEGER NOT NULL,
                        basicSalarySen INTEGER NOT NULL,
                        minMonthlySalesSen INTEGER NOT NULL,
                        employerCostRateBps INTEGER NOT NULL,
                        standardRateBps INTEGER NOT NULL,
                        incentiveBaselineThresholdSen INTEGER NOT NULL,
                        incentiveRateBps INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commission_rule_versions_effectiveFromEpochDay ON commission_rule_versions(effectiveFromEpochDay)")
            }
        }

        /**
         * Lossless migration from Phase 3 (v3) to Phase 4A (v4).
         * Adds Savings Goals, per-(goal, wallet) Allocation Ledger, and Savings Movements audit log.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetAmountSen INTEGER NOT NULL,
                        targetDateEpochDay INTEGER,
                        description TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        accentToken TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_status ON savings_goals(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_wallet_allocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        walletId INTEGER NOT NULL,
                        allocatedSen INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES savings_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(walletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_savings_wallet_allocations_goalId_walletId ON savings_wallet_allocations(goalId, walletId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_wallet_allocations_walletId ON savings_wallet_allocations(walletId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        sourceWalletId INTEGER,
                        targetWalletId INTEGER,
                        relatedGoalId INTEGER,
                        type TEXT NOT NULL,
                        amountSen INTEGER NOT NULL,
                        linkedTransactionId INTEGER,
                        note TEXT NOT NULL,
                        occurredAtEpochMs INTEGER NOT NULL,
                        submissionId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_movements_goalId ON savings_movements(goalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_movements_occurredAtEpochMs ON savings_movements(occurredAtEpochMs)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_savings_movements_submissionId ON savings_movements(submissionId)")
            }
        }

        /**
         * Lossless migration from Phase 4A (v4) to Phase 4B (v5).
         * Adds Monthly and Salary Cycle spending budgets without altering existing balances, goals, or transactions.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        periodType TEXT NOT NULL,
                        yearMonth TEXT NOT NULL,
                        salaryCycleId INTEGER,
                        categoryId INTEGER,
                        categoryName TEXT NOT NULL,
                        limitSen INTEGER NOT NULL,
                        accentToken TEXT NOT NULL,
                        note TEXT NOT NULL,
                        isArchived INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(salaryCycleId) REFERENCES salary_cycles(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_periodType_yearMonth_salaryCycleId_isArchived ON budgets(periodType, yearMonth, salaryCycleId, isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_scope_categoryId ON budgets(scope, categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_salaryCycleId ON budgets(salaryCycleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)")
            }
        }

        /**
         * Lossless migration from Phase 4B (v5) to Phase 4C (v6).
         * Extends commitments with frequency/category/paid tracking and adds PayLater, Debts, and Obligation Payments.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN category TEXT NOT NULL DEFAULT 'Bills & Utilities'")
                db.execSQL("ALTER TABLE commitments ADD COLUMN categoryId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE commitments ADD COLUMN frequency TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE commitments ADD COLUMN customIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE commitments ADD COLUMN anchorDayOfMonth INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE commitments ADD COLUMN paidSen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commitments ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE commitments ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commitments ADD COLUMN createdAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commitments ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE commitments SET frequency = 'ONE_TIME' WHERE isRecurring = 0")
                db.execSQL("UPDATE commitments SET paidSen = amountSen WHERE status = 'PAID'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS paylater_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        providerName TEXT NOT NULL,
                        purchaseAmountSen INTEGER NOT NULL,
                        feeOrInterestSen INTEGER NOT NULL,
                        paidSen INTEGER NOT NULL,
                        instalmentCount INTEGER NOT NULL,
                        firstDueDateEpochDay INTEGER NOT NULL,
                        nextDueDateEpochDay INTEGER NOT NULL,
                        accentToken TEXT NOT NULL,
                        note TEXT NOT NULL,
                        isArchived INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_paylater_plans_isArchived_nextDueDateEpochDay ON paylater_plans(isArchived, nextDueDateEpochDay)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS debts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        direction TEXT NOT NULL,
                        counterpartyName TEXT NOT NULL,
                        title TEXT NOT NULL,
                        originalAmountSen INTEGER NOT NULL,
                        paidSen INTEGER NOT NULL,
                        dueDateEpochDay INTEGER,
                        accentToken TEXT NOT NULL,
                        note TEXT NOT NULL,
                        isArchived INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_direction_isArchived_dueDateEpochDay ON debts(direction, isArchived, dueDateEpochDay)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS obligation_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        obligationType TEXT NOT NULL,
                        obligationId INTEGER NOT NULL,
                        walletId INTEGER NOT NULL,
                        linkedTransactionId INTEGER,
                        amountSen INTEGER NOT NULL,
                        occurrenceEpochDay INTEGER,
                        paidAtEpochMs INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        submissionId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_obligation_payments_obligationType_obligationId ON obligation_payments(obligationType, obligationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_obligation_payments_linkedTransactionId ON obligation_payments(linkedTransactionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_obligation_payments_submissionId ON obligation_payments(submissionId)")
            }
        }

        /**
         * Lossless migration from Phase 4C (v6) to v7 (BUG-4C-001 fix).
         * Adds [nextDueDateEpochDay] to commitments and repairs any recurring commitment
         * that was marked UPCOMING in v6 immediately after its current occurrence was paid in full,
         * without creating or modifying any ledger transactions or wallet balances.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN nextDueDateEpochDay INTEGER DEFAULT NULL")
                db.execSQL(
                    """
                    UPDATE commitments
                    SET
                        nextDueDateEpochDay = dueDateEpochDay,
                        dueDateEpochDay = (
                            SELECT MAX(op.occurrenceEpochDay)
                            FROM obligation_payments op
                            WHERE op.obligationType = 'COMMITMENT'
                              AND op.obligationId = commitments.id
                              AND op.occurrenceEpochDay IS NOT NULL
                              AND op.occurrenceEpochDay < commitments.dueDateEpochDay
                        ),
                        paidSen = amountSen,
                        status = 'PAID'
                    WHERE isRecurring = 1
                      AND status = 'UPCOMING'
                      AND paidSen = 0
                      AND (
                          SELECT COALESCE(SUM(op2.amountSen), 0)
                          FROM obligation_payments op2
                          WHERE op2.obligationType = 'COMMITMENT'
                            AND op2.obligationId = commitments.id
                            AND op2.occurrenceEpochDay = (
                                SELECT MAX(op3.occurrenceEpochDay)
                                FROM obligation_payments op3
                                WHERE op3.obligationType = 'COMMITMENT'
                                  AND op3.obligationId = commitments.id
                                  AND op3.occurrenceEpochDay IS NOT NULL
                                  AND op3.occurrenceEpochDay < commitments.dueDateEpochDay
                            )
                      ) >= commitments.amountSen
                    """.trimIndent()
                )
            }
        }

        /**
         * Lossless migration from v7 to v8 (Cycle-Aware Safe to Spend refinement).
         * Adds optional [scheduledRepaymentSen] on debts and advances any recurring commitment
         * that holds a [nextDueDateEpochDay] forward to its scheduled next due date as UPCOMING
         * so it remains visible in Upcoming Future Bills without reducing current-cycle Safe to Spend.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN scheduledRepaymentSen INTEGER DEFAULT NULL")
                db.execSQL(
                    """
                    UPDATE commitments
                    SET
                        dueDateEpochDay = nextDueDateEpochDay,
                        nextDueDateEpochDay = NULL,
                        paidSen = 0,
                        status = 'UPCOMING'
                    WHERE isRecurring = 1
                      AND status = 'PAID'
                      AND nextDueDateEpochDay IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        /**
         * Lossless migration from v8 to v9 (Phase 5B Smart Notifications).
         * Adds [notification_history] table for deduplicated reminder history and read states
         * without touching any existing financial tables or balances.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dedupKey TEXT NOT NULL,
                        notificationId INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        entityId INTEGER NOT NULL,
                        targetEpochDay INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        amountSen INTEGER,
                        isOverdue INTEGER NOT NULL,
                        isRead INTEGER NOT NULL,
                        deepLink TEXT NOT NULL,
                        deliveredAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_history_dedupKey ON notification_history(dedupKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_category_entityId ON notification_history(category, entityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_isRead_deliveredAtEpochMs ON notification_history(isRead, deliveredAtEpochMs)")
            }
        }

        /** Additive: new money_reminders table only; every existing table and row is untouched. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `money_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `repeatFrequency` TEXT NOT NULL DEFAULT 'ONE_TIME',
                        `customIntervalDays` INTEGER DEFAULT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_reminders_dateEpochDay` ON `money_reminders` (`dateEpochDay`)")
            }
        }

        /** Additive: savings challenge tables only; every existing table and row is untouched. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `savings_challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `type` TEXT NOT NULL, `baseAmountSen` INTEGER NOT NULL, `startEpochDay` INTEGER NOT NULL, `slotCount` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `randomSeed` INTEGER NOT NULL, `isPaused` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_challenges_goalId` ON `savings_challenges` (`goalId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `challenge_skipped_slots` (`challengeId` INTEGER NOT NULL, `slotIndex` INTEGER NOT NULL, PRIMARY KEY(`challengeId`, `slotIndex`), FOREIGN KEY(`challengeId`) REFERENCES `savings_challenges`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `challenge_stickers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `challengeId` INTEGER NOT NULL, `stage` TEXT NOT NULL, `earnedAtEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_challenge_stickers_challengeId_stage` ON `challenge_stickers` (`challengeId`, `stage`)")
            }
        }

        /** Additive: Receipt Vault table only; every existing table and row is untouched. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `receipts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`transactionId` INTEGER, " +
                        "`merchant` TEXT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`amountSen` INTEGER, " +
                        "`category` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`taxTag` TEXT NOT NULL, " +
                        "`fileRelativePath` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "`originalFileName` TEXT NOT NULL, " +
                        "`fileSizeBytes` INTEGER NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_transactionId` ON `receipts` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_dateEpochDay` ON `receipts` (`dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_taxTag` ON `receipts` (`taxTag`)")
            }
        }

        /** Additive: Little Wins Club milestone stickers only; all existing tables and rows stay untouched. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `earned_milestones` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`milestoneKey` TEXT NOT NULL, " +
                        "`earnedAtEpochMs` INTEGER NOT NULL, " +
                        "`celebratedAtEpochMs` INTEGER)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_earned_milestones_milestoneKey` ON `earned_milestones` (`milestoneKey`)")
            }
        }

        @Volatile
        private var instance: DuitDatabase? = null

        fun getInstance(context: Context): DuitDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DuitDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
