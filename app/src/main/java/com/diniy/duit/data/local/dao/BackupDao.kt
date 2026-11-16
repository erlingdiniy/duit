package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

data class LedgerDatabaseSnapshot(
    val wallets: List<WalletEntity>,
    val categories: List<CategoryEntity>,
    val salaryCycles: List<SalaryCycleEntity>,
    val commitments: List<CommitmentEntity>,
    val commissionSources: List<CommissionSourceEntity>,
    val commissions: List<CommissionEntity>,
    val commissionPayments: List<CommissionPaymentEntity>,
    val commissionRuleVersions: List<CommissionRuleVersionEntity>,
    val dailySalesRecords: List<DailySalesRecordEntity>,
    val savingsGoals: List<SavingsGoalEntity>,
    val savingsAllocations: List<SavingsAllocationEntity>,
    val savingsMovements: List<SavingsMovementEntity>,
    val budgets: List<BudgetEntity>,
    val payLaterPlans: List<PayLaterPlanEntity>,
    val debts: List<DebtEntity>,
    val obligationPayments: List<ObligationPaymentEntity>,
    val transactions: List<TransactionEntity>,
    val notificationHistory: List<NotificationHistoryEntity>
)

@Dao
interface BackupDao {

    @Query("SELECT * FROM wallets ORDER BY id ASC")
    suspend fun getAllWallets(): List<WalletEntity>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM salary_cycles ORDER BY id ASC")
    suspend fun getAllSalaryCycles(): List<SalaryCycleEntity>

    @Query("SELECT * FROM commitments ORDER BY id ASC")
    suspend fun getAllCommitments(): List<CommitmentEntity>

    @Query("SELECT * FROM commission_sources ORDER BY id ASC")
    suspend fun getAllCommissionSources(): List<CommissionSourceEntity>

    @Query("SELECT * FROM commissions ORDER BY id ASC")
    suspend fun getAllCommissions(): List<CommissionEntity>

    @Query("SELECT * FROM commission_payments ORDER BY id ASC")
    suspend fun getAllCommissionPayments(): List<CommissionPaymentEntity>

    @Query("SELECT * FROM commission_rule_versions ORDER BY id ASC")
    suspend fun getAllCommissionRuleVersions(): List<CommissionRuleVersionEntity>

    @Query("SELECT * FROM daily_sales_records ORDER BY id ASC")
    suspend fun getAllDailySalesRecords(): List<DailySalesRecordEntity>

    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    suspend fun getAllSavingsGoals(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_wallet_allocations ORDER BY id ASC")
    suspend fun getAllSavingsAllocations(): List<SavingsAllocationEntity>

    @Query("SELECT * FROM savings_movements ORDER BY id ASC")
    suspend fun getAllSavingsMovements(): List<SavingsMovementEntity>

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAllBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM paylater_plans ORDER BY id ASC")
    suspend fun getAllPayLaterPlans(): List<PayLaterPlanEntity>

    @Query("SELECT * FROM debts ORDER BY id ASC")
    suspend fun getAllDebts(): List<DebtEntity>

    @Query("SELECT * FROM obligation_payments ORDER BY id ASC")
    suspend fun getAllObligationPayments(): List<ObligationPaymentEntity>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM notification_history ORDER BY id ASC")
    suspend fun getAllNotificationHistory(): List<NotificationHistoryEntity>

    @Transaction
    suspend fun exportFullSnapshot(): LedgerDatabaseSnapshot = LedgerDatabaseSnapshot(
        wallets = getAllWallets(),
        categories = getAllCategories(),
        salaryCycles = getAllSalaryCycles(),
        commitments = getAllCommitments(),
        commissionSources = getAllCommissionSources(),
        commissions = getAllCommissions(),
        commissionPayments = getAllCommissionPayments(),
        commissionRuleVersions = getAllCommissionRuleVersions(),
        dailySalesRecords = getAllDailySalesRecords(),
        savingsGoals = getAllSavingsGoals(),
        savingsAllocations = getAllSavingsAllocations(),
        savingsMovements = getAllSavingsMovements(),
        budgets = getAllBudgets(),
        payLaterPlans = getAllPayLaterPlans(),
        debts = getAllDebts(),
        obligationPayments = getAllObligationPayments(),
        transactions = getAllTransactions(),
        notificationHistory = getAllNotificationHistory()
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWallets(items: List<WalletEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSalaryCycles(items: List<SalaryCycleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommitments(items: List<CommitmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommissionSources(items: List<CommissionSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommissions(items: List<CommissionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommissionPayments(items: List<CommissionPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommissionRuleVersions(items: List<CommissionRuleVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailySalesRecords(items: List<DailySalesRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSavingsGoals(items: List<SavingsGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSavingsAllocations(items: List<SavingsAllocationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSavingsMovements(items: List<SavingsMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayLaterPlans(items: List<PayLaterPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebts(items: List<DebtEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObligationPayments(items: List<ObligationPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotificationHistory(items: List<NotificationHistoryEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM savings_wallet_allocations")
    suspend fun deleteAllSavingsAllocations()

    @Query("DELETE FROM savings_movements")
    suspend fun deleteAllSavingsMovements()

    @Query("DELETE FROM obligation_payments")
    suspend fun deleteAllObligationPayments()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM commission_payments")
    suspend fun deleteAllCommissionPayments()

    @Query("DELETE FROM commission_rule_versions")
    suspend fun deleteAllCommissionRuleVersions()

    @Query("DELETE FROM commissions")
    suspend fun deleteAllCommissions()

    @Query("DELETE FROM daily_sales_records")
    suspend fun deleteAllDailySalesRecords()

    @Query("DELETE FROM commitments")
    suspend fun deleteAllCommitments()

    @Query("DELETE FROM paylater_plans")
    suspend fun deleteAllPayLaterPlans()

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllSavingsGoals()

    @Query("DELETE FROM salary_cycles")
    suspend fun deleteAllSalaryCycles()

    @Query("DELETE FROM commission_sources")
    suspend fun deleteAllCommissionSources()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM notification_history")
    suspend fun deleteAllNotificationHistory()

    @Query("DELETE FROM wallets")
    suspend fun deleteAllWallets()

    @Transaction
    suspend fun deleteAllLedgerDataAtomically() {
        // Delete child/FK-referencing tables first to honor ForeignKey.RESTRICT constraints.
        deleteAllTransactions()
        deleteAllSavingsAllocations()
        deleteAllSavingsMovements()
        deleteAllObligationPayments()
        deleteAllBudgets()
        deleteAllCommissionPayments()
        deleteAllCommissionRuleVersions()
        deleteAllCommissions()
        deleteAllDailySalesRecords()
        deleteAllCommitments()
        deleteAllPayLaterPlans()
        deleteAllDebts()
        deleteAllSavingsGoals()
        deleteAllSalaryCycles()
        deleteAllCommissionSources()
        deleteAllCategories()
        deleteAllNotificationHistory()
        deleteAllWallets()
    }

    @Transaction
    suspend fun replaceAllLedgerDataAtomically(snapshot: LedgerDatabaseSnapshot) {
        deleteAllLedgerDataAtomically()
        // Insert parent tables before child tables to satisfy foreign key constraints.
        insertWallets(snapshot.wallets)
        insertCategories(snapshot.categories)
        insertSalaryCycles(snapshot.salaryCycles)
        insertCommissionSources(snapshot.commissionSources)
        insertSavingsGoals(snapshot.savingsGoals)
        insertCommitments(snapshot.commitments)
        insertPayLaterPlans(snapshot.payLaterPlans)
        insertDebts(snapshot.debts)
        insertCommissions(snapshot.commissions)
        insertBudgets(snapshot.budgets)
        insertTransactions(snapshot.transactions)
        insertSavingsAllocations(snapshot.savingsAllocations)
        insertSavingsMovements(snapshot.savingsMovements)
        insertObligationPayments(snapshot.obligationPayments)
        insertCommissionPayments(snapshot.commissionPayments)
        insertCommissionRuleVersions(snapshot.commissionRuleVersions)
        insertDailySalesRecords(snapshot.dailySalesRecords)
        insertNotificationHistory(snapshot.notificationHistory)
    }
}
