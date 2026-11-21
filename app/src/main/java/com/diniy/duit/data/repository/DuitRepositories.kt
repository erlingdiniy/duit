package com.diniy.duit.data.repository

import androidx.room.withTransaction
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.dao.DefaultCategorySeed
import com.diniy.duit.data.local.dao.DefaultCommissionSourceSeed
import com.diniy.duit.data.local.entity.CategoryEntity
import com.diniy.duit.data.local.entity.toDomain
import com.diniy.duit.data.local.entity.toEntity
import com.diniy.duit.data.local.entity.CommissionSourceEntity
import com.diniy.duit.data.local.entity.DebtEntity
import com.diniy.duit.data.local.entity.ObligationPaymentEntity
import com.diniy.duit.data.local.entity.PayLaterPlanEntity
import com.diniy.duit.data.local.entity.SavingsAllocationEntity
import com.diniy.duit.data.local.entity.SavingsMovementEntity
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.toEntity
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.domain.calculator.CompanyCommissionEngine
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.calculator.PeriodReportingCalculator
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionRuleVersion
import com.diniy.duit.domain.model.CommissionSource
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceOperationException
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ObligationPayment
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

interface WalletRepository {
    fun observeWallets(): Flow<List<Wallet>>
    fun observeActiveWallets(): Flow<List<Wallet>>
    suspend fun upsertWallet(wallet: Wallet): Long
    suspend fun updateWalletDetails(wallet: Wallet)
    suspend fun setWalletArchived(walletId: Long, archived: Boolean)
    suspend fun deleteWalletSafely(walletId: Long)
    suspend fun getTransactionCountForWallet(walletId: Long): Int
}

interface TransactionRepository {
    fun observeTransactions(): Flow<List<FinanceTransaction>>
    fun observeRecentTransactions(limit: Int): Flow<List<FinanceTransaction>>
    fun observeTransactionsForWallet(walletId: Long): Flow<List<FinanceTransaction>>
    fun observeTransactionsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<FinanceTransaction>>
    suspend fun recordTransaction(transaction: FinanceTransaction): Long
    suspend fun updateTransaction(updated: FinanceTransaction)
    suspend fun deleteTransaction(transactionId: Long)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun ensureDefaultCategoriesSeeded()
    suspend fun createCategory(
        name: String,
        type: TransactionType,
        iconKey: String,
        accentToken: PrismaticAccentToken
    ): Long
    suspend fun renameCategory(
        id: Long,
        newName: String,
        iconKey: String,
        accentToken: PrismaticAccentToken
    )
    suspend fun setCategoryArchived(id: Long, archived: Boolean)
}

interface PlanningRepository {
    fun observeSalaryCycles(): Flow<List<SalaryCycle>>
    fun observeActiveCycle(): Flow<SalaryCycle?>
    fun observeCommitments(): Flow<List<Commitment>>
    fun observeMoneyReminders(): Flow<List<com.diniy.duit.domain.model.MoneyReminder>>
    /** Inserts when id == 0, else updates. Touches only money_reminders. */
    suspend fun saveMoneyReminder(reminder: com.diniy.duit.domain.model.MoneyReminder): Long
    suspend fun deleteMoneyReminder(id: Long)
    suspend fun upsertSalaryCycle(cycle: SalaryCycle): Long
    suspend fun recordSalaryTransactionAndStartCycle(
        transaction: FinanceTransaction,
        cycleLabel: String
    ): Long
    suspend fun upsertAndReconcileSalaryCycle(cycle: SalaryCycle): Long
    suspend fun deleteSalaryCycle(id: Long)
    suspend fun upsertCommitment(commitment: Commitment): Long
    suspend fun updateCommitmentStatus(id: Long, status: CommitmentStatus)
}

interface CommissionRepository {
    fun observeCommissions(): Flow<List<CommissionRecord>>
    fun observeCommissionSources(): Flow<List<CommissionSource>>
    fun observeCommissionPayments(): Flow<List<CommissionPayment>>
    fun observeCommissionRuleVersions(): Flow<List<CommissionRuleVersion>>
    fun observeDailySales(): Flow<List<DailySalesRecord>>

    suspend fun ensurePhase3DefaultsSeeded()
    suspend fun createCommissionSource(name: String, accentToken: PrismaticAccentToken): Long
    suspend fun renameCommissionSource(id: Long, newName: String, accentToken: PrismaticAccentToken)
    suspend fun setCommissionSourceArchived(id: Long, archived: Boolean)

    suspend fun upsertCommission(record: CommissionRecord): Long
    suspend fun updateCommissionStatus(id: Long, status: CommissionStatus, payoutDateEpochDay: Long?)
    suspend fun deleteCommissionRecordSafely(commissionId: Long)

    suspend fun recordCommissionPayment(
        payment: CommissionPayment,
        markAsFullyReceived: Boolean
    ): Long
    suspend fun deleteCommissionPayment(paymentId: Long)

    suspend fun saveCommissionRuleVersion(rule: CommissionRuleVersion): Long

    suspend fun upsertDailySales(record: DailySalesRecord): Long
    suspend fun deleteDailySales(id: Long)
}

interface SavingsRepository {
    fun observeSavingsGoals(): Flow<List<SavingsGoal>>
    fun observeSavingsAllocations(): Flow<List<SavingsAllocation>>
    fun observeSavingsMovements(): Flow<List<SavingsMovement>>

    suspend fun upsertSavingsGoal(goal: SavingsGoal): Long
    suspend fun completeSavingsGoal(goalId: Long)
    suspend fun archiveSavingsGoal(goalId: Long)
    suspend fun deleteSavingsGoalSafely(goalId: Long)

    suspend fun allocateToGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long

    suspend fun transferToProtectedWalletAndAllocate(
        goalId: Long,
        sourceWalletId: Long,
        protectedWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long

    suspend fun withdrawFromGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long

    suspend fun transferFromGoalToSpendingWallet(
        goalId: Long,
        protectedWalletId: Long,
        spendingWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long

    suspend fun reassignGoalAllocation(
        fromGoalId: Long,
        toGoalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long
}

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget): Long
    suspend fun setBudgetArchived(id: Long, archived: Boolean)
    suspend fun deleteBudget(id: Long)
    suspend fun copyMonthlyBudgets(sourceYearMonth: String, targetYearMonth: String): Int
}

interface ObligationRepository {
    fun observePayLaterPlans(): Flow<List<PayLaterPlan>>
    fun observeDebts(): Flow<List<DebtRecord>>
    fun observeObligationPayments(): Flow<List<ObligationPayment>>

    suspend fun upsertCommitmentDetail(commitment: Commitment): Long
    suspend fun setCommitmentArchived(id: Long, archived: Boolean)
    suspend fun deleteCommitment(id: Long)
    suspend fun recordCommitmentPayment(
        commitmentId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String,
        autoAdvanceRecurring: Boolean = true
    ): Long

    suspend fun openNextRecurringOccurrence(commitmentId: Long)

    suspend fun upsertPayLaterPlan(plan: PayLaterPlan): Long
    suspend fun setPayLaterArchived(id: Long, archived: Boolean)
    suspend fun deletePayLaterPlan(id: Long)
    suspend fun recordPayLaterPayment(
        planId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String
    ): Long

    suspend fun upsertDebt(debt: DebtRecord): Long
    suspend fun setDebtArchived(id: Long, archived: Boolean)
    suspend fun deleteDebt(id: Long)
    suspend fun recordDebtSettlement(
        debtId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String
    ): Long
}

interface UserPreferencesRepository {
    fun observeAppSettings(): Flow<AppSettingsSnapshot>
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeCommissionFormulaConfig(): Flow<CommissionFormulaConfig>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setGlassIntensity(intensity: GlassIntensity)
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setAnimationIntensity(intensity: AnimationIntensity)
    suspend fun setDashboardSectionVisible(section: DashboardSectionKey, visible: Boolean)
    suspend fun setDashboardSectionOrder(order: List<DashboardSectionKey>)
    suspend fun restoreDefaultDashboardLayout()
    suspend fun setDefaultWalletId(walletId: Long?)
    suspend fun setDefaultBudgetPeriodType(periodType: BudgetPeriodType)
    suspend fun setAutoAdvanceRecurringCommitments(enabled: Boolean)
    suspend fun setCommissionTrackingEnabled(enabled: Boolean)
    // Defaults keep test fakes compiling; a fake reports "already chosen" so startup never changes it.
    suspend fun setCommissionFeatureEnabled(enabled: Boolean) = Unit
    suspend fun hasCommissionFeatureChoice(): Boolean = true
    suspend fun saveCommissionFormulaConfig(config: CommissionFormulaConfig)
    suspend fun resetCommissionFormulaConfig()
    suspend fun applyPortableSettings(snapshot: AppSettingsSnapshot)
    suspend fun restoreDefaultPreferences()
    // Defaults treat an unknown state as an existing user (never onboards) and keep test fakes compiling.
    fun observeOnboardingCompleted(): Flow<Boolean?> = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setOnboardingCompleted(completed: Boolean) = Unit
    suspend fun hasStoredPreferences(): Boolean = true
}

class OfflineFirstFinanceRepository(
    private val database: DuitDatabase
) : WalletRepository, TransactionRepository, CategoryRepository, PlanningRepository, CommissionRepository, SavingsRepository, BudgetRepository, ObligationRepository {

    private val walletDao = database.walletDao()
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val salaryCycleDao = database.salaryCycleDao()
    private val commitmentDao = database.commitmentDao()
    private val commissionDao = database.commissionDao()
    private val commissionSourceDao = database.commissionSourceDao()
    private val commissionPaymentDao = database.commissionPaymentDao()
    private val commissionRuleVersionDao = database.commissionRuleVersionDao()
    private val dailySalesDao = database.dailySalesRecordDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val savingsAllocationDao = database.savingsAllocationDao()
    private val savingsMovementDao = database.savingsMovementDao()
    private val budgetDao = database.budgetDao()
    private val payLaterDao = database.payLaterDao()
    private val debtDao = database.debtDao()
    private val obligationPaymentDao = database.obligationPaymentDao()

    override fun observeWallets(): Flow<List<Wallet>> =
        walletDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveWallets(): Flow<List<Wallet>> =
        walletDao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertWallet(wallet: Wallet): Long {
        if (wallet.balanceSen < 0L) {
            throw FinanceOperationException.InvalidTransaction("Opening balance cannot be negative.")
        }
        return walletDao.upsert(wallet.toEntity())
    }

    override suspend fun updateWalletDetails(wallet: Wallet) {
        database.withTransaction {
            val existing = walletDao.getById(wallet.id)
                ?: throw FinanceOperationException.InvalidTransaction("Wallet not found.")
            val txCount = transactionDao.countForWallet(wallet.id)
            val reservedSen = savingsAllocationDao.sumForWallet(wallet.id)
            val resolvedBalanceSen = if (txCount == 0) {
                if (wallet.balanceSen < 0L) {
                    throw FinanceOperationException.InvalidTransaction("Opening balance cannot be negative.")
                }
                if (wallet.balanceSen < reservedSen) {
                    throw FinanceOperationException.InsufficientBalance(
                        "Opening balance cannot be below ${MyrFormatter.format(reservedSen)} reserved for savings goals."
                    )
                }
                wallet.balanceSen
            } else {
                existing.balanceSen
            }
            walletDao.update(
                wallet.copy(
                    balanceSen = resolvedBalanceSen,
                    updatedAtEpochMs = System.currentTimeMillis()
                ).toEntity()
            )
        }
    }

    override suspend fun setWalletArchived(walletId: Long, archived: Boolean) {
        walletDao.setArchived(walletId, archived, System.currentTimeMillis())
    }

    override suspend fun deleteWalletSafely(walletId: Long) {
        database.withTransaction {
            val txCount = transactionDao.countForWallet(walletId)
            val reservedSen = savingsAllocationDao.sumForWallet(walletId)
            val movementCount = savingsMovementDao.countForWallet(walletId)
            if (txCount > 0 || reservedSen > 0L || movementCount > 0) {
                throw FinanceOperationException.WalletHasHistory(
                    "This wallet has transaction or savings history and cannot be deleted. Archive it instead to preserve records."
                )
            }
            walletDao.deleteById(walletId)
        }
    }

    override suspend fun getTransactionCountForWallet(walletId: Long): Int =
        transactionDao.countForWallet(walletId)

    override fun observeTransactions(): Flow<List<FinanceTransaction>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRecentTransactions(limit: Int): Flow<List<FinanceTransaction>> =
        transactionDao.observeRecent(limit).map { entities -> entities.map { it.toDomain() } }

    override fun observeTransactionsForWallet(walletId: Long): Flow<List<FinanceTransaction>> =
        transactionDao.observeForWallet(walletId).map { entities -> entities.map { it.toDomain() } }

    override fun observeTransactionsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<FinanceTransaction>> =
        transactionDao.observeBetween(startEpochMs, endEpochMs).map { entities -> entities.map { it.toDomain() } }

    override suspend fun recordTransaction(transaction: FinanceTransaction): Long {
        validateTransactionShape(transaction)
        return database.withTransaction {
            insertTransactionLocked(transaction)
        }
    }

    private suspend fun insertTransactionLocked(transaction: FinanceTransaction): Long {
        val existingId = transaction.submissionId?.let { token ->
            transactionDao.findBySubmissionId(token)?.id
        }
        if (existingId != null) {
            return existingId
        }

        val deltas = balanceDeltasFor(transaction, sign = 1)
        applyWalletDeltasSafely(deltas)

        val id = transactionDao.insert(transaction.toEntity())
        if (transaction.type == TransactionType.EXPENSE) {
            transaction.commitmentId?.let { commitmentId ->
                commitmentDao.getById(commitmentId)?.toDomain()?.let { c ->
                    val newPaid = (c.paidSen + transaction.amountSen).coerceAtMost(c.amountSen)
                    val isSettled = newPaid >= c.amountSen
                    if (isSettled && c.isRecurring && c.frequency != CommitmentFrequency.ONE_TIME) {
                        val nextDue = ObligationCalculator.advanceDueDate(
                            c.dueDateEpochDay,
                            c.frequency,
                            c.customIntervalDays,
                            c.anchorDayOfMonth
                        )
                        commitmentDao.update(
                            c.copy(
                                dueDateEpochDay = nextDue,
                                paidSen = 0L,
                                status = CommitmentStatus.UPCOMING,
                                nextDueDateEpochDay = null,
                                walletId = transaction.walletId,
                                updatedAtEpochMs = System.currentTimeMillis()
                            ).toEntity()
                        )
                    } else {
                        commitmentDao.update(
                            c.copy(
                                paidSen = newPaid,
                                status = if (isSettled) CommitmentStatus.PAID else CommitmentStatus.PARTIALLY_PAID,
                                nextDueDateEpochDay = null,
                                walletId = transaction.walletId,
                                updatedAtEpochMs = System.currentTimeMillis()
                            ).toEntity()
                        )
                    }
                }
            }
        }
        return id
    }

    override suspend fun updateTransaction(updated: FinanceTransaction) {
        validateTransactionShape(updated)
        database.withTransaction {
            val existing = transactionDao.getById(updated.id)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Transaction no longer exists.")

            val combinedDeltas = mutableMapOf<Long, Long>()
            balanceDeltasFor(existing, sign = -1).forEach { (walletId, delta) ->
                combinedDeltas[walletId] = (combinedDeltas[walletId] ?: 0L) + delta
            }
            balanceDeltasFor(updated, sign = 1).forEach { (walletId, delta) ->
                combinedDeltas[walletId] = (combinedDeltas[walletId] ?: 0L) + delta
            }

            applyWalletDeltasSafely(combinedDeltas)
            reconcileLinkedObligationOnTransactionUpdateLocked(existing, updated)
            transactionDao.update(
                updated.copy(
                    createdAtEpochMs = existing.createdAtEpochMs,
                    submissionId = existing.submissionId
                ).toEntity()
            )
        }
    }

    override suspend fun deleteTransaction(transactionId: Long) {
        database.withTransaction {
            val existing = transactionDao.getById(transactionId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Transaction not found.")
            reconcileLinkedObligationOnTransactionDeleteLocked(existing)
            val reversalDeltas = balanceDeltasFor(existing, sign = -1)
            applyWalletDeltasSafely(reversalDeltas)
            transactionDao.deleteById(transactionId)
        }
    }

    private fun validateTransactionShape(transaction: FinanceTransaction) {
        if (transaction.amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Amount must be greater than RM 0.00.")
        }
        if (transaction.title.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a title for this transaction.")
        }
        if (transaction.type == TransactionType.TRANSFER) {
            val targetId = transaction.targetWalletId
                ?: throw FinanceOperationException.InvalidTransaction("Select a destination wallet for the transfer.")
            if (targetId == transaction.walletId) {
                throw FinanceOperationException.InvalidTransaction("Source and destination wallets cannot be the same.")
            }
        }
    }

    private fun balanceDeltasFor(
        transaction: FinanceTransaction,
        sign: Int
    ): Map<Long, Long> {
        val signedAmount = transaction.amountSen * sign
        return when (transaction.type) {
            TransactionType.INCOME -> mapOf(transaction.walletId to signedAmount)
            TransactionType.EXPENSE -> mapOf(transaction.walletId to -signedAmount)
            TransactionType.TRANSFER -> {
                val targetId = requireNotNull(transaction.targetWalletId) {
                    "Transfer requires a destination wallet."
                }
                mapOf(
                    transaction.walletId to -signedAmount,
                    targetId to signedAmount
                )
            }
        }
    }

    private suspend fun applyWalletDeltasSafely(deltas: Map<Long, Long>) {
        val now = System.currentTimeMillis()
        for ((walletId, deltaSen) in deltas) {
            if (deltaSen == 0L) continue
            val wallet = walletDao.getById(walletId)
                ?: throw FinanceOperationException.InvalidTransaction("Selected wallet was not found.")
            val resultingBalance = wallet.balanceSen + deltaSen
            if (resultingBalance < 0L) {
                throw FinanceOperationException.InsufficientBalance(
                    "Insufficient balance in ${wallet.name} (${MyrFormatter.format(wallet.balanceSen)} available)."
                )
            }
            val reservedSen = savingsAllocationDao.sumForWallet(walletId)
            if (deltaSen < 0L && resultingBalance < reservedSen) {
                val spendable = (wallet.balanceSen - reservedSen).coerceAtLeast(0L)
                throw FinanceOperationException.InsufficientBalance(
                    "Only ${MyrFormatter.format(spendable)} available in ${wallet.name} (${MyrFormatter.format(reservedSen)} reserved in savings goals)."
                )
            }
            walletDao.adjustBalance(walletId, deltaSen, now)
        }
    }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureDefaultCategoriesSeeded() {
        database.withTransaction {
            if (categoryDao.countAll() == 0) {
                categoryDao.insertAll(DefaultCategorySeed.defaults())
            }
        }
    }

    override suspend fun createCategory(
        name: String,
        type: TransactionType,
        iconKey: String,
        accentToken: PrismaticAccentToken
    ): Long {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) {
            throw FinanceOperationException.InvalidTransaction("Enter a category name.")
        }
        if (type == TransactionType.TRANSFER) {
            throw FinanceOperationException.InvalidTransaction("Custom categories apply to Income or Expense.")
        }
        return categoryDao.upsert(
            CategoryEntity(
                id = 0L,
                name = cleaned,
                type = type,
                iconKey = iconKey,
                accentToken = accentToken,
                isDefault = false,
                isArchived = false,
                sortOrder = 100
            )
        )
    }

    override suspend fun renameCategory(
        id: Long,
        newName: String,
        iconKey: String,
        accentToken: PrismaticAccentToken
    ) {
        val cleaned = newName.trim()
        if (cleaned.isEmpty()) {
            throw FinanceOperationException.InvalidTransaction("Category name cannot be blank.")
        }
        database.withTransaction {
            val existing = categoryDao.getById(id)
                ?: throw FinanceOperationException.InvalidTransaction("Category not found.")
            categoryDao.update(
                existing.copy(
                    name = cleaned,
                    iconKey = iconKey,
                    accentToken = accentToken
                )
            )
        }
    }

    override suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        categoryDao.setArchived(id, archived)
    }

    override fun observeSalaryCycles(): Flow<List<SalaryCycle>> =
        salaryCycleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveCycle(): Flow<SalaryCycle?> =
        salaryCycleDao.observeActive().map { it?.toDomain() }

    override fun observeCommitments(): Flow<List<Commitment>> =
        commitmentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeMoneyReminders(): Flow<List<com.diniy.duit.domain.model.MoneyReminder>> =
        database.moneyReminderDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveMoneyReminder(reminder: com.diniy.duit.domain.model.MoneyReminder): Long {
        val dao = database.moneyReminderDao()
        val entity = reminder.toEntity()
        return if (reminder.id == 0L) dao.insert(entity) else { dao.update(entity); reminder.id }
    }

    override suspend fun deleteMoneyReminder(id: Long) = database.moneyReminderDao().deleteById(id)

    override suspend fun upsertSalaryCycle(cycle: SalaryCycle): Long =
        salaryCycleDao.upsert(cycle.toEntity())

    /**
     * Atomically records a Salary INCOME transaction and starts a new salary cycle on the
     * actual receipt date. Closes any preceding cycle the day before and reconciles backdated cycles.
     */
    override suspend fun recordSalaryTransactionAndStartCycle(
        transaction: FinanceTransaction,
        cycleLabel: String
    ): Long {
        if (transaction.type != TransactionType.INCOME) {
            throw FinanceOperationException.InvalidTransaction("Only income transactions can start a salary cycle.")
        }
        validateTransactionShape(transaction)
        val trimmedLabel = cycleLabel.trim().ifEmpty {
            val date = DateTimeFormatters.toLocalDate(transaction.occurredAtEpochMs)
            "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year} Cycle"
        }

        return database.withTransaction {
            val existingTxId = transaction.submissionId?.let { transactionDao.findBySubmissionId(it)?.id }
            if (existingTxId != null) {
                return@withTransaction existingTxId
            }

            val txId = insertTransactionLocked(transaction)
            val startEpochDay = DateTimeFormatters.toLocalDate(transaction.occurredAtEpochMs).toEpochDay()
            val newCycle = SalaryCycle(
                id = 0L,
                label = trimmedLabel,
                startDateEpochDay = startEpochDay,
                endDateEpochDay = Long.MAX_VALUE,
                paydayEpochDay = startEpochDay,
                baseSalarySen = transaction.amountSen,
                committedBudgetSen = 0L,
                isActive = true,
                receivedSalarySen = transaction.amountSen,
                salaryTransactionId = txId,
                isOpenEnded = true
            )
            salaryCycleDao.upsert(newCycle.toEntity())
            reconcileCyclesLocked()
            txId
        }
    }

    override suspend fun upsertAndReconcileSalaryCycle(cycle: SalaryCycle): Long {
        if (cycle.label.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a salary cycle label.")
        }
        return database.withTransaction {
            val id = salaryCycleDao.upsert(cycle.toEntity())
            reconcileCyclesLocked()
            id
        }
    }

    override suspend fun deleteSalaryCycle(id: Long) {
        database.withTransaction {
            salaryCycleDao.deleteById(id)
            reconcileCyclesLocked()
        }
    }

    private suspend fun reconcileCyclesLocked() {
        val current = salaryCycleDao.getAllOnce().map { it.toDomain() }
        if (current.isEmpty()) return
        val reconciled = PeriodReportingCalculator.reconcileCycleBoundaries(current)
        salaryCycleDao.updateAll(reconciled.map { it.toEntity() })
    }

    override suspend fun upsertCommitment(commitment: Commitment): Long =
        commitmentDao.upsert(commitment.toEntity())

    override suspend fun updateCommitmentStatus(id: Long, status: CommitmentStatus) =
        commitmentDao.updateStatus(id, status)

    override fun observeCommissions(): Flow<List<CommissionRecord>> =
        commissionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCommissionSources(): Flow<List<CommissionSource>> =
        commissionSourceDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCommissionPayments(): Flow<List<CommissionPayment>> =
        commissionPaymentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCommissionRuleVersions(): Flow<List<CommissionRuleVersion>> =
        commissionRuleVersionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDailySales(): Flow<List<DailySalesRecord>> =
        dailySalesDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun ensurePhase3DefaultsSeeded() {
        database.withTransaction {
            if (commissionSourceDao.countAll() == 0) {
                commissionSourceDao.insertAll(DefaultCommissionSourceSeed.defaults())
            }
            if (commissionRuleVersionDao.countAll() == 0) {
                commissionRuleVersionDao.upsert(
                    CompanyCommissionEngine.defaultMemoRule(System.currentTimeMillis()).toEntity()
                )
            }
        }
    }

    override suspend fun createCommissionSource(name: String, accentToken: PrismaticAccentToken): Long {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) {
            throw FinanceOperationException.InvalidTransaction("Enter a commission source name.")
        }
        return commissionSourceDao.upsert(
            CommissionSourceEntity(
                id = 0L,
                name = cleaned,
                accentToken = accentToken,
                isDefault = false,
                isArchived = false,
                sortOrder = 100
            )
        )
    }

    override suspend fun renameCommissionSource(
        id: Long,
        newName: String,
        accentToken: PrismaticAccentToken
    ) {
        val cleaned = newName.trim()
        if (cleaned.isEmpty()) {
            throw FinanceOperationException.InvalidTransaction("Commission source name cannot be blank.")
        }
        database.withTransaction {
            val existing = commissionSourceDao.getById(id)
                ?: throw FinanceOperationException.InvalidTransaction("Commission source not found.")
            commissionSourceDao.update(existing.copy(name = cleaned, accentToken = accentToken))
        }
    }

    override suspend fun setCommissionSourceArchived(id: Long, archived: Boolean) {
        commissionSourceDao.setArchived(id, archived)
    }

    override suspend fun upsertCommission(record: CommissionRecord): Long {
        if (record.title.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a title for this commission.")
        }
        if (record.amountSen < 0L) {
            throw FinanceOperationException.InvalidTransaction("Commission amount cannot be negative.")
        }
        return commissionDao.upsert(record.toEntity())
    }

    override suspend fun updateCommissionStatus(
        id: Long,
        status: CommissionStatus,
        payoutDateEpochDay: Long?
    ) = commissionDao.updateStatus(id, status, payoutDateEpochDay)

    override suspend fun deleteCommissionRecordSafely(commissionId: Long) {
        database.withTransaction {
            val payments = commissionPaymentDao.getForCommission(commissionId)
            if (payments.isNotEmpty()) {
                throw FinanceOperationException.WalletHasHistory(
                    "This commission has ${payments.size} received payment(s). Reverse the payments first before deleting."
                )
            }
            commissionDao.deleteById(commissionId)
        }
    }

    /**
     * Atomically records an actual commission payment (partial or full), creates a wallet
     * INCOME transaction for ONLY [CommissionPayment.actualNetSen], credits the wallet, and
     * updates the parent commission totals and status.
     */
    override suspend fun recordCommissionPayment(
        payment: CommissionPayment,
        markAsFullyReceived: Boolean
    ): Long {
        if (payment.actualGrossSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Actual gross commission must be greater than RM 0.00.")
        }
        if (payment.actualNetSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Actual net commission must be greater than RM 0.00.")
        }
        val deductionSum = (payment.epfDeductionSen ?: 0L) +
            (payment.socsoDeductionSen ?: 0L) +
            (payment.eisDeductionSen ?: 0L)
        if (deductionSum > payment.actualGrossSen) {
            throw FinanceOperationException.InvalidTransaction("Total deductions cannot exceed actual gross commission.")
        }

        return database.withTransaction {
            val existingPaymentId = payment.submissionId?.let {
                commissionPaymentDao.findBySubmissionId(it)?.id
            }
            if (existingPaymentId != null) {
                return@withTransaction existingPaymentId
            }

            val parent = commissionDao.getById(payment.commissionId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Commission record not found.")

            val txSubmissionId = payment.submissionId?.let { "comm_pay_$it" }
            val incomeTx = FinanceTransaction(
                id = 0L,
                walletId = payment.walletId,
                targetWalletId = null,
                type = TransactionType.INCOME,
                amountSen = payment.actualNetSen,
                category = "Commission",
                title = "${payment.sourceName} Commission",
                note = payment.notes.ifBlank { parent.title },
                commitmentId = null,
                occurredAtEpochMs = payment.receivedAtEpochMs,
                createdAtEpochMs = System.currentTimeMillis(),
                submissionId = txSubmissionId
            )
            val txId = insertTransactionLocked(incomeTx)

            val savedPaymentId = commissionPaymentDao.insert(
                payment.copy(linkedTransactionId = txId).toEntity()
            )

            recalculateCommissionParentLocked(
                parent = parent,
                forceFull = markAsFullyReceived,
                latestPayoutDay = DateTimeFormatters.toLocalDate(payment.receivedAtEpochMs).toEpochDay()
            )
            savedPaymentId
        }
    }

    override suspend fun deleteCommissionPayment(paymentId: Long) {
        database.withTransaction {
            val existingPayment = commissionPaymentDao.getById(paymentId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Commission payment not found.")

            existingPayment.linkedTransactionId?.let { txId ->
                val linkedTx = transactionDao.getById(txId)?.toDomain()
                if (linkedTx != null) {
                    val reversal = balanceDeltasFor(linkedTx, sign = -1)
                    applyWalletDeltasSafely(reversal)
                    transactionDao.deleteById(txId)
                }
            }

            commissionPaymentDao.deleteById(paymentId)

            val parent = commissionDao.getById(existingPayment.commissionId)?.toDomain()
            if (parent != null) {
                recalculateCommissionParentLocked(
                    parent = parent,
                    forceFull = false,
                    latestPayoutDay = null
                )
            }
        }
    }

    private suspend fun recalculateCommissionParentLocked(
        parent: CommissionRecord,
        forceFull: Boolean,
        latestPayoutDay: Long?
    ) {
        val remainingPayments = commissionPaymentDao.getForCommission(parent.id)
        val totalActualGross = remainingPayments.sumOf { it.actualGrossSen }
        val totalReceivedNet = remainingPayments.sumOf { it.actualNetSen }

        val targetExpected = parent.estimatedGrossSen ?: parent.amountSen
        val newStatus = when {
            remainingPayments.isEmpty() -> {
                if (parent.amountSen > 0L || parent.estimatedGrossSen != null) CommissionStatus.PENDING
                else CommissionStatus.UNKNOWN
            }
            forceFull -> CommissionStatus.RECEIVED
            targetExpected > 0L && totalActualGross >= targetExpected -> CommissionStatus.RECEIVED
            else -> CommissionStatus.PARTIALLY_RECEIVED
        }

        val lastDay = latestPayoutDay ?: remainingPayments.maxOfOrNull {
            Instant.ofEpochMilli(it.receivedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        }

        commissionDao.update(
            parent.copy(
                actualGrossSen = totalActualGross,
                receivedNetSen = totalReceivedNet,
                status = newStatus,
                payoutDateEpochDay = lastDay,
                linkedTransactionId = remainingPayments.firstOrNull()?.linkedTransactionId
            ).toEntity()
        )
    }

    override suspend fun saveCommissionRuleVersion(rule: CommissionRuleVersion): Long {
        if (rule.ruleName.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a rule version name.")
        }
        if (rule.basicSalarySen < 0L || rule.minMonthlySalesSen < 0L || rule.incentiveBaselineThresholdSen < 0L) {
            throw FinanceOperationException.InvalidTransaction("Rule amounts cannot be negative.")
        }
        if (rule.employerCostRateBps !in 0..10_000 ||
            rule.standardRateBps !in 0..10_000 ||
            rule.incentiveRateBps !in 0..10_000
        ) {
            throw FinanceOperationException.InvalidTransaction("Rates must be between 0.00% and 100.00%.")
        }
        return commissionRuleVersionDao.upsert(rule.toEntity())
    }

    override suspend fun upsertDailySales(record: DailySalesRecord): Long {
        if (record.grossSalesSen < 0L || record.grossProfitSen < 0L) {
            throw FinanceOperationException.InvalidTransaction("Daily sales and gross profit cannot be negative.")
        }
        return dailySalesDao.upsert(record.toEntity())
    }

    override suspend fun deleteDailySales(id: Long) {
        dailySalesDao.deleteById(id)
    }

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSavingsAllocations(): Flow<List<SavingsAllocation>> =
        savingsAllocationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSavingsMovements(): Flow<List<SavingsMovement>> =
        savingsMovementDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertSavingsGoal(goal: SavingsGoal): Long {
        if (goal.name.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a savings goal name.")
        }
        if (goal.targetAmountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Target amount must be greater than RM 0.00.")
        }
        val now = System.currentTimeMillis()
        return database.withTransaction {
            if (goal.id == 0L) {
                val created = goal.copy(
                    name = goal.name.trim(),
                    description = goal.description.trim(),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                ).toEntity()
                savingsGoalDao.insert(created)
            } else {
                val existing = savingsGoalDao.getById(goal.id)
                    ?: throw FinanceOperationException.InvalidTransaction("Savings goal not found.")
                val updated = existing.copy(
                    name = goal.name.trim(),
                    targetAmountSen = goal.targetAmountSen,
                    targetDateEpochDay = goal.targetDateEpochDay,
                    description = goal.description.trim(),
                    iconKey = goal.iconKey,
                    accentToken = goal.accentToken,
                    status = goal.status,
                    updatedAtEpochMs = now
                )
                val rowsUpdated = savingsGoalDao.update(updated)
                if (rowsUpdated == 0) {
                    throw FinanceOperationException.InvalidTransaction("Savings goal not found.")
                }
                existing.id
            }
        }
    }

    override suspend fun completeSavingsGoal(goalId: Long) {
        savingsGoalDao.updateStatus(goalId, SavingsGoalStatus.COMPLETED, System.currentTimeMillis())
    }

    override suspend fun archiveSavingsGoal(goalId: Long) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            releaseGoalAllocationsLocked(goalId, "Released on goal archive", now)
            savingsGoalDao.updateStatus(goalId, SavingsGoalStatus.ARCHIVED, now)
        }
    }

    override suspend fun deleteSavingsGoalSafely(goalId: Long) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            releaseGoalAllocationsLocked(goalId, "Released on goal deletion", now)
            savingsGoalDao.deleteById(goalId)
        }
    }

    private suspend fun releaseGoalAllocationsLocked(goalId: Long, reasonNote: String, now: Long) {
        val buckets = savingsAllocationDao.getForGoal(goalId)
        for (bucket in buckets) {
            if (bucket.allocatedSen > 0L) {
                savingsMovementDao.insert(
                    SavingsMovementEntity(
                        id = 0L,
                        goalId = goalId,
                        sourceWalletId = bucket.walletId,
                        targetWalletId = null,
                        relatedGoalId = null,
                        type = SavingsMovementType.WITHDRAW_TO_WALLET,
                        amountSen = bucket.allocatedSen,
                        linkedTransactionId = null,
                        note = reasonNote,
                        occurredAtEpochMs = now,
                        submissionId = null
                    )
                )
            }
        }
        savingsAllocationDao.deleteAllForGoal(goalId)
    }

    override suspend fun allocateToGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Allocation amount must be greater than RM 0.00.")
        }
        return database.withTransaction {
            savingsMovementDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            savingsGoalDao.getById(goalId)
                ?: throw FinanceOperationException.InvalidTransaction("Savings goal not found.")
            val wallet = walletDao.getById(walletId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Wallet not found.")

            val currentlyAllocatedInWallet = savingsAllocationDao.sumForWallet(walletId)
            val availableToAllocate = wallet.balanceSen - currentlyAllocatedInWallet
            if (amountSen > availableToAllocate) {
                throw FinanceOperationException.InsufficientBalance(
                    "Only ${MyrFormatter.format(availableToAllocate.coerceAtLeast(0L))} unallocated in ${wallet.name}."
                )
            }

            val now = System.currentTimeMillis()
            val existingBucket = savingsAllocationDao.getBucket(goalId, walletId)
            val updatedAmount = (existingBucket?.allocatedSen ?: 0L) + amountSen
            savingsAllocationDao.upsert(
                SavingsAllocationEntity(
                    id = existingBucket?.id ?: 0L,
                    goalId = goalId,
                    walletId = walletId,
                    allocatedSen = updatedAmount,
                    updatedAtEpochMs = now
                )
            )

            val movementType = if (FinanceSummaryCalculator.isProtectedSavingsWallet(wallet)) {
                SavingsMovementType.LINK_PROTECTED
            } else {
                SavingsMovementType.ALLOCATE
            }

            savingsMovementDao.insert(
                SavingsMovementEntity(
                    id = 0L,
                    goalId = goalId,
                    sourceWalletId = walletId,
                    targetWalletId = walletId,
                    relatedGoalId = null,
                    type = movementType,
                    amountSen = amountSen,
                    linkedTransactionId = null,
                    note = note.trim(),
                    occurredAtEpochMs = now,
                    submissionId = submissionId
                )
            )
        }
    }

    override suspend fun transferToProtectedWalletAndAllocate(
        goalId: Long,
        sourceWalletId: Long,
        protectedWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Transfer amount must be greater than RM 0.00.")
        }
        if (sourceWalletId == protectedWalletId) {
            throw FinanceOperationException.InvalidTransaction("Source and destination wallets must differ.")
        }
        return database.withTransaction {
            savingsMovementDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val goal = savingsGoalDao.getById(goalId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Savings goal not found.")
            val targetWallet = walletDao.getById(protectedWalletId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Protected wallet not found.")
            if (!FinanceSummaryCalculator.isProtectedSavingsWallet(targetWallet)) {
                throw FinanceOperationException.InvalidTransaction("Destination wallet must be a protected savings wallet.")
            }

            val now = System.currentTimeMillis()
            val txId = insertTransactionLocked(
                FinanceTransaction(
                    id = 0L,
                    walletId = sourceWalletId,
                    targetWalletId = protectedWalletId,
                    type = TransactionType.TRANSFER,
                    amountSen = amountSen,
                    category = "Wallet Transfer",
                    title = "Save to ${goal.name}",
                    note = note.trim(),
                    commitmentId = null,
                    occurredAtEpochMs = now,
                    createdAtEpochMs = now,
                    submissionId = "sav_tx_$submissionId"
                )
            )

            val existingBucket = savingsAllocationDao.getBucket(goalId, protectedWalletId)
            savingsAllocationDao.upsert(
                SavingsAllocationEntity(
                    id = existingBucket?.id ?: 0L,
                    goalId = goalId,
                    walletId = protectedWalletId,
                    allocatedSen = (existingBucket?.allocatedSen ?: 0L) + amountSen,
                    updatedAtEpochMs = now
                )
            )

            savingsMovementDao.insert(
                SavingsMovementEntity(
                    id = 0L,
                    goalId = goalId,
                    sourceWalletId = sourceWalletId,
                    targetWalletId = protectedWalletId,
                    relatedGoalId = null,
                    type = SavingsMovementType.TRANSFER_AND_ALLOCATE,
                    amountSen = amountSen,
                    linkedTransactionId = txId,
                    note = note.trim(),
                    occurredAtEpochMs = now,
                    submissionId = submissionId
                )
            )
        }
    }

    override suspend fun withdrawFromGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Withdrawal amount must be greater than RM 0.00.")
        }
        return database.withTransaction {
            savingsMovementDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val bucket = savingsAllocationDao.getBucket(goalId, walletId)
                ?: throw FinanceOperationException.InsufficientBalance("No allocation found in the selected wallet.")
            if (amountSen > bucket.allocatedSen) {
                throw FinanceOperationException.InsufficientBalance(
                    "Only ${MyrFormatter.format(bucket.allocatedSen)} allocated to this goal in the selected wallet."
                )
            }

            val now = System.currentTimeMillis()
            val remaining = bucket.allocatedSen - amountSen
            if (remaining == 0L) {
                savingsAllocationDao.deleteById(bucket.id)
            } else {
                savingsAllocationDao.upsert(bucket.copy(allocatedSen = remaining, updatedAtEpochMs = now))
            }

            savingsMovementDao.insert(
                SavingsMovementEntity(
                    id = 0L,
                    goalId = goalId,
                    sourceWalletId = walletId,
                    targetWalletId = walletId,
                    relatedGoalId = null,
                    type = SavingsMovementType.WITHDRAW_TO_WALLET,
                    amountSen = amountSen,
                    linkedTransactionId = null,
                    note = note.trim(),
                    occurredAtEpochMs = now,
                    submissionId = submissionId
                )
            )
        }
    }

    override suspend fun transferFromGoalToSpendingWallet(
        goalId: Long,
        protectedWalletId: Long,
        spendingWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Transfer amount must be greater than RM 0.00.")
        }
        if (protectedWalletId == spendingWalletId) {
            throw FinanceOperationException.InvalidTransaction("Source and destination wallets must differ.")
        }
        return database.withTransaction {
            savingsMovementDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val goal = savingsGoalDao.getById(goalId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Savings goal not found.")
            val bucket = savingsAllocationDao.getBucket(goalId, protectedWalletId)
                ?: throw FinanceOperationException.InsufficientBalance("No allocation found in the selected protected wallet.")
            if (amountSen > bucket.allocatedSen) {
                throw FinanceOperationException.InsufficientBalance(
                    "Only ${MyrFormatter.format(bucket.allocatedSen)} allocated to ${goal.name} in this wallet."
                )
            }

            val now = System.currentTimeMillis()
            val remaining = bucket.allocatedSen - amountSen
            if (remaining == 0L) {
                savingsAllocationDao.deleteById(bucket.id)
            } else {
                savingsAllocationDao.upsert(bucket.copy(allocatedSen = remaining, updatedAtEpochMs = now))
            }

            val txId = insertTransactionLocked(
                FinanceTransaction(
                    id = 0L,
                    walletId = protectedWalletId,
                    targetWalletId = spendingWalletId,
                    type = TransactionType.TRANSFER,
                    amountSen = amountSen,
                    category = "Wallet Transfer",
                    title = "Withdraw from ${goal.name}",
                    note = note.trim(),
                    commitmentId = null,
                    occurredAtEpochMs = now,
                    createdAtEpochMs = now,
                    submissionId = "sav_tx_$submissionId"
                )
            )

            savingsMovementDao.insert(
                SavingsMovementEntity(
                    id = 0L,
                    goalId = goalId,
                    sourceWalletId = protectedWalletId,
                    targetWalletId = spendingWalletId,
                    relatedGoalId = null,
                    type = SavingsMovementType.TRANSFER_TO_SPENDING,
                    amountSen = amountSen,
                    linkedTransactionId = txId,
                    note = note.trim(),
                    occurredAtEpochMs = now,
                    submissionId = submissionId
                )
            )
        }
    }

    override suspend fun reassignGoalAllocation(
        fromGoalId: Long,
        toGoalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Amount must be greater than RM 0.00.")
        }
        if (fromGoalId == toGoalId) {
            throw FinanceOperationException.InvalidTransaction("Select a different destination savings goal.")
        }
        return database.withTransaction {
            savingsMovementDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            savingsGoalDao.getById(toGoalId)
                ?: throw FinanceOperationException.InvalidTransaction("Target savings goal not found.")
            val fromBucket = savingsAllocationDao.getBucket(fromGoalId, walletId)
                ?: throw FinanceOperationException.InsufficientBalance("No allocation in that wallet to reassign.")
            if (amountSen > fromBucket.allocatedSen) {
                throw FinanceOperationException.InsufficientBalance(
                    "Only ${MyrFormatter.format(fromBucket.allocatedSen)} available to reassign."
                )
            }

            val now = System.currentTimeMillis()
            val remaining = fromBucket.allocatedSen - amountSen
            if (remaining == 0L) {
                savingsAllocationDao.deleteById(fromBucket.id)
            } else {
                savingsAllocationDao.upsert(fromBucket.copy(allocatedSen = remaining, updatedAtEpochMs = now))
            }

            val toBucket = savingsAllocationDao.getBucket(toGoalId, walletId)
            savingsAllocationDao.upsert(
                SavingsAllocationEntity(
                    id = toBucket?.id ?: 0L,
                    goalId = toGoalId,
                    walletId = walletId,
                    allocatedSen = (toBucket?.allocatedSen ?: 0L) + amountSen,
                    updatedAtEpochMs = now
                )
            )

            savingsMovementDao.insert(
                SavingsMovementEntity(
                    id = 0L,
                    goalId = fromGoalId,
                    sourceWalletId = walletId,
                    targetWalletId = walletId,
                    relatedGoalId = toGoalId,
                    type = SavingsMovementType.REASSIGN,
                    amountSen = amountSen,
                    linkedTransactionId = null,
                    note = note.trim(),
                    occurredAtEpochMs = now,
                    submissionId = submissionId
                )
            )
        }
    }

    override fun observeBudgets(): Flow<List<Budget>> =
        budgetDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertBudget(budget: Budget): Long {
        if (budget.limitSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Budget limit must be greater than RM 0.00.")
        }

        val normalizedYearMonth: String
        val normalizedCycleId: Long?
        when (budget.periodType) {
            BudgetPeriodType.MONTHLY -> {
                val parsed = runCatching { YearMonth.parse(budget.yearMonth.trim()) }.getOrNull()
                    ?: throw FinanceOperationException.InvalidTransaction("Select a valid calendar month (YYYY-MM).")
                normalizedYearMonth = parsed.toString()
                normalizedCycleId = null
            }
            BudgetPeriodType.SALARY_CYCLE -> {
                val cycleId = budget.salaryCycleId
                    ?: throw FinanceOperationException.InvalidTransaction("Select a salary cycle for this budget.")
                normalizedYearMonth = ""
                normalizedCycleId = cycleId
            }
        }

        val now = System.currentTimeMillis()
        return database.withTransaction {
            if (normalizedCycleId != null && salaryCycleDao.getById(normalizedCycleId) == null) {
                throw FinanceOperationException.InvalidTransaction("Selected salary cycle does not exist.")
            }

            var resolvedCategoryId = if (budget.scope == BudgetScope.CATEGORY) budget.categoryId else null
            var resolvedCategoryName = if (budget.scope == BudgetScope.CATEGORY) budget.categoryName.trim() else ""

            if (budget.scope == BudgetScope.CATEGORY) {
                if (resolvedCategoryId != null) {
                    val cat = categoryDao.getById(resolvedCategoryId)
                        ?: throw FinanceOperationException.InvalidTransaction("Selected category not found.")
                    resolvedCategoryName = cat.name
                } else if (resolvedCategoryName.isEmpty()) {
                    throw FinanceOperationException.InvalidTransaction("Choose an expense category for this budget.")
                }
            }

            val defaultName = when (budget.scope) {
                BudgetScope.OVERALL -> if (budget.periodType == BudgetPeriodType.MONTHLY) {
                    "Overall Spending"
                } else {
                    "Cycle Overall Spending"
                }
                BudgetScope.CATEGORY -> resolvedCategoryName
            }
            val cleanName = budget.name.trim().ifEmpty { defaultName }

            val candidate = budget.copy(
                name = cleanName,
                yearMonth = normalizedYearMonth,
                salaryCycleId = normalizedCycleId,
                categoryId = resolvedCategoryId,
                categoryName = resolvedCategoryName,
                note = budget.note.trim(),
                updatedAtEpochMs = now,
                createdAtEpochMs = if (budget.id == 0L) now else budget.createdAtEpochMs
            )

            if (!candidate.isArchived) {
                validateNoActiveDuplicateBudgetLocked(candidate)
            }

            if (candidate.id == 0L) {
                budgetDao.insert(candidate.toEntity())
            } else {
                val existing = budgetDao.getById(candidate.id)
                    ?: throw FinanceOperationException.InvalidTransaction("Budget not found.")
                val updated = existing.copy(
                    name = candidate.name,
                    scope = candidate.scope,
                    periodType = candidate.periodType,
                    yearMonth = candidate.yearMonth,
                    salaryCycleId = candidate.salaryCycleId,
                    categoryId = candidate.categoryId,
                    categoryName = candidate.categoryName,
                    limitSen = candidate.limitSen,
                    accentToken = candidate.accentToken,
                    note = candidate.note,
                    isArchived = candidate.isArchived,
                    updatedAtEpochMs = now
                )
                budgetDao.update(updated)
                existing.id
            }
        }
    }

    override suspend fun setBudgetArchived(id: Long, archived: Boolean) {
        database.withTransaction {
            val existing = budgetDao.getById(id)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Budget not found.")
            if (!archived) {
                validateNoActiveDuplicateBudgetLocked(existing.copy(isArchived = false))
            }
            budgetDao.setArchived(id, archived, System.currentTimeMillis())
        }
    }

    override suspend fun deleteBudget(id: Long) {
        budgetDao.deleteById(id)
    }

    override suspend fun copyMonthlyBudgets(sourceYearMonth: String, targetYearMonth: String): Int {
        val sourceYm = runCatching { YearMonth.parse(sourceYearMonth.trim()) }.getOrNull()
            ?: throw FinanceOperationException.InvalidTransaction("Invalid source month.")
        val targetYm = runCatching { YearMonth.parse(targetYearMonth.trim()) }.getOrNull()
            ?: throw FinanceOperationException.InvalidTransaction("Invalid target month.")
        if (sourceYm == targetYm) return 0

        return database.withTransaction {
            val sourceBudgets = budgetDao.getActiveForPeriod(BudgetPeriodType.MONTHLY, sourceYm.toString(), null)
            val targetExisting = budgetDao.getActiveForPeriod(BudgetPeriodType.MONTHLY, targetYm.toString(), null)
            val now = System.currentTimeMillis()
            var copiedCount = 0

            for (src in sourceBudgets) {
                val hasDuplicate = targetExisting.any { existing ->
                    if (src.scope == BudgetScope.OVERALL) {
                        existing.scope == BudgetScope.OVERALL
                    } else {
                        existing.scope == BudgetScope.CATEGORY && (
                            (src.categoryId != null && existing.categoryId == src.categoryId) ||
                                existing.categoryName.trim().equals(src.categoryName.trim(), ignoreCase = true)
                            )
                    }
                }
                if (!hasDuplicate) {
                    budgetDao.insert(
                        src.copy(
                            id = 0L,
                            yearMonth = targetYm.toString(),
                            salaryCycleId = null,
                            isArchived = false,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now
                        )
                    )
                    copiedCount++
                }
            }
            copiedCount
        }
    }

    private suspend fun validateNoActiveDuplicateBudgetLocked(candidate: Budget) {
        val activeForPeriod = budgetDao.getActiveForPeriod(
            periodType = candidate.periodType,
            yearMonth = candidate.yearMonth,
            salaryCycleId = candidate.salaryCycleId
        )
        val duplicate = activeForPeriod.firstOrNull { existing ->
            if (existing.id == candidate.id) return@firstOrNull false
            when (candidate.scope) {
                BudgetScope.OVERALL -> existing.scope == BudgetScope.OVERALL
                BudgetScope.CATEGORY -> existing.scope == BudgetScope.CATEGORY && (
                    (candidate.categoryId != null && existing.categoryId != null && candidate.categoryId == existing.categoryId) ||
                        existing.categoryName.trim().equals(candidate.categoryName.trim(), ignoreCase = true)
                    )
            }
        }
        if (duplicate != null) {
            val label = if (candidate.scope == BudgetScope.OVERALL) {
                "Overall Spending"
            } else {
                candidate.categoryName.ifBlank { "this category" }
            }
            throw FinanceOperationException.DuplicateBudget(
                "An active $label budget already exists for this period."
            )
        }
    }

    override fun observePayLaterPlans(): Flow<List<PayLaterPlan>> =
        payLaterDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDebts(): Flow<List<DebtRecord>> =
        debtDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeObligationPayments(): Flow<List<ObligationPayment>> =
        obligationPaymentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertCommitmentDetail(commitment: Commitment): Long {
        if (commitment.title.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a commitment name.")
        }
        if (commitment.amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Commitment amount must be greater than RM 0.00.")
        }
        if (commitment.frequency == CommitmentFrequency.CUSTOM_DAYS && (commitment.customIntervalDays ?: 0) < 1) {
            throw FinanceOperationException.InvalidTransaction("Custom recurrence interval must be at least 1 day.")
        }

        val now = System.currentTimeMillis()
        return database.withTransaction {
            val isRecurring = commitment.frequency != CommitmentFrequency.ONE_TIME
            val anchor = commitment.anchorDayOfMonth
                ?: LocalDate.ofEpochDay(commitment.dueDateEpochDay).dayOfMonth
            val resolvedCategory = commitment.category.trim().ifEmpty { "Bills & Utilities" }

            if (commitment.id == 0L) {
                val created = commitment.copy(
                    title = commitment.title.trim(),
                    isRecurring = isRecurring,
                    category = resolvedCategory,
                    anchorDayOfMonth = anchor,
                    paidSen = 0L,
                    note = commitment.note.trim(),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                ).toEntity()
                commitmentDao.insert(created)
            } else {
                val existing = commitmentDao.getById(commitment.id)
                    ?: throw FinanceOperationException.InvalidTransaction("Commitment not found.")
                val preservedPaidSen = existing.paidSen.coerceAtLeast(0L)
                val updatedStatus = when {
                    existing.status == CommitmentStatus.RESERVED && preservedPaidSen == 0L -> CommitmentStatus.RESERVED
                    preservedPaidSen >= commitment.amountSen -> CommitmentStatus.PAID
                    preservedPaidSen > 0L -> CommitmentStatus.PARTIALLY_PAID
                    commitment.status == CommitmentStatus.RESERVED -> CommitmentStatus.RESERVED
                    else -> CommitmentStatus.UPCOMING
                }
                val nextDue = if (updatedStatus == CommitmentStatus.PAID && isRecurring && commitment.frequency != CommitmentFrequency.ONE_TIME) {
                    ObligationCalculator.advanceDueDate(
                        commitment.dueDateEpochDay,
                        commitment.frequency,
                        if (commitment.frequency == CommitmentFrequency.CUSTOM_DAYS) (commitment.customIntervalDays ?: 30).coerceAtLeast(1) else null,
                        anchor
                    )
                } else {
                    null
                }
                val updated = existing.copy(
                    title = commitment.title.trim(),
                    amountSen = commitment.amountSen,
                    dueDateEpochDay = commitment.dueDateEpochDay,
                    status = updatedStatus,
                    walletId = commitment.walletId,
                    isRecurring = isRecurring,
                    salaryCycleId = commitment.salaryCycleId,
                    category = resolvedCategory,
                    categoryId = commitment.categoryId,
                    frequency = commitment.frequency,
                    customIntervalDays = if (commitment.frequency == CommitmentFrequency.CUSTOM_DAYS) {
                        (commitment.customIntervalDays ?: 30).coerceAtLeast(1)
                    } else {
                        null
                    },
                    anchorDayOfMonth = anchor,
                    paidSen = preservedPaidSen,
                    nextDueDateEpochDay = nextDue,
                    note = commitment.note.trim(),
                    isArchived = commitment.isArchived,
                    updatedAtEpochMs = now
                )
                commitmentDao.update(updated)
                existing.id
            }
        }
    }

    override suspend fun setCommitmentArchived(id: Long, archived: Boolean) {
        commitmentDao.setArchived(id, archived, System.currentTimeMillis())
    }

    override suspend fun deleteCommitment(id: Long) {
        database.withTransaction {
            obligationPaymentDao.deleteForObligation(ObligationType.COMMITMENT, id)
            commitmentDao.deleteById(id)
        }
    }

    override suspend fun recordCommitmentPayment(
        commitmentId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String,
        autoAdvanceRecurring: Boolean
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Payment amount must be greater than RM 0.00.")
        }
        return database.withTransaction {
            obligationPaymentDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val commitment = commitmentDao.getById(commitmentId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Commitment not found.")
            val remainingForCycle = (commitment.amountSen - commitment.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
            if (remainingForCycle <= 0L || commitment.status == CommitmentStatus.PAID) {
                throw FinanceOperationException.InvalidTransaction("This commitment is already paid in full.")
            }
            if (amountSen > remainingForCycle) {
                throw FinanceOperationException.InvalidTransaction(
                    "Payment exceeds remaining commitment balance (${MyrFormatter.format(remainingForCycle)})."
                )
            }

            val txId = insertTransactionLocked(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = amountSen,
                    category = commitment.category.ifBlank { "Bills & Utilities" },
                    title = "Payment · ${commitment.title}",
                    note = note.trim(),
                    commitmentId = null, // Managed via ObligationPaymentEntity to support partial & multi-cycle payments cleanly
                    occurredAtEpochMs = paidAtEpochMs,
                    createdAtEpochMs = paidAtEpochMs,
                    categoryId = commitment.categoryId,
                    submissionId = "ob_tx_$submissionId"
                )
            )

            val occurrenceDay = commitment.dueDateEpochDay
            val paymentId = obligationPaymentDao.insert(
                ObligationPaymentEntity(
                    id = 0L,
                    obligationType = ObligationType.COMMITMENT,
                    obligationId = commitmentId,
                    walletId = walletId,
                    linkedTransactionId = txId,
                    amountSen = amountSen,
                    occurrenceEpochDay = occurrenceDay,
                    paidAtEpochMs = paidAtEpochMs,
                    note = note.trim(),
                    submissionId = submissionId
                )
            )

            val now = System.currentTimeMillis()
            val newPaidSen = (commitment.paidSen + amountSen).coerceAtMost(commitment.amountSen)
            val isFullySettled = newPaidSen >= commitment.amountSen
            if (autoAdvanceRecurring && isFullySettled && commitment.isRecurring && commitment.frequency != CommitmentFrequency.ONE_TIME) {
                val nextDue = ObligationCalculator.advanceDueDate(
                    currentEpochDay = commitment.dueDateEpochDay,
                    frequency = commitment.frequency,
                    customIntervalDays = commitment.customIntervalDays,
                    anchorDayOfMonth = commitment.anchorDayOfMonth
                )
                commitmentDao.update(
                    commitment.copy(
                        dueDateEpochDay = nextDue,
                        status = CommitmentStatus.UPCOMING,
                        paidSen = 0L,
                        nextDueDateEpochDay = null,
                        walletId = walletId,
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            } else {
                val nextStatus = if (isFullySettled) CommitmentStatus.PAID else CommitmentStatus.PARTIALLY_PAID
                commitmentDao.update(
                    commitment.copy(
                        status = nextStatus,
                        paidSen = newPaidSen,
                        nextDueDateEpochDay = null,
                        walletId = walletId,
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            }

            paymentId
        }
    }

    override suspend fun openNextRecurringOccurrence(commitmentId: Long) {
        database.withTransaction {
            val existing = commitmentDao.getById(commitmentId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Commitment not found.")
            if (!existing.isRecurring || existing.frequency == CommitmentFrequency.ONE_TIME) {
                throw FinanceOperationException.InvalidTransaction("Only recurring commitments have a next occurrence.")
            }
            if (existing.status != CommitmentStatus.PAID) {
                throw FinanceOperationException.InvalidTransaction("Finish settling the current occurrence before opening the next one.")
            }
            val nextDue = existing.nextDueDateEpochDay ?: ObligationCalculator.advanceDueDate(
                currentEpochDay = existing.dueDateEpochDay,
                frequency = existing.frequency,
                customIntervalDays = existing.customIntervalDays,
                anchorDayOfMonth = existing.anchorDayOfMonth
            )
            commitmentDao.update(
                existing.copy(
                    dueDateEpochDay = nextDue,
                    status = CommitmentStatus.UPCOMING,
                    paidSen = 0L,
                    nextDueDateEpochDay = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                ).toEntity()
            )
        }
    }

    override suspend fun upsertPayLaterPlan(plan: PayLaterPlan): Long {
        if (plan.title.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a PayLater item name.")
        }
        if (plan.providerName.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a PayLater provider name.")
        }
        if (plan.purchaseAmountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Purchase amount must be greater than RM 0.00.")
        }
        if (plan.feeOrInterestSen < 0L) {
            throw FinanceOperationException.InvalidTransaction("Fee or interest cannot be negative.")
        }
        if (plan.instalmentCount < 1) {
            throw FinanceOperationException.InvalidTransaction("Number of instalments must be at least 1.")
        }

        val totalPayable = plan.purchaseAmountSen + plan.feeOrInterestSen
        val now = System.currentTimeMillis()
        return database.withTransaction {
            if (plan.id == 0L) {
                if (plan.paidSen < 0L || plan.paidSen > totalPayable) {
                    throw FinanceOperationException.InvalidTransaction("Initial paid amount cannot exceed total payable.")
                }
                val preliminary = plan.copy(
                    title = plan.title.trim(),
                    providerName = plan.providerName.trim(),
                    note = plan.note.trim(),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val summary = ObligationCalculator.summarizePayLater(preliminary)
                val resolvedNextDue = summary.schedule.firstOrNull { !it.isCovered }?.dueDateEpochDay
                    ?: preliminary.firstDueDateEpochDay
                payLaterDao.insert(preliminary.copy(nextDueDateEpochDay = resolvedNextDue).toEntity())
            } else {
                val existing = payLaterDao.getById(plan.id)
                    ?: throw FinanceOperationException.InvalidTransaction("PayLater plan not found.")
                if (totalPayable < existing.paidSen) {
                    throw FinanceOperationException.InvalidTransaction(
                        "Total payable cannot be lower than already paid (${MyrFormatter.format(existing.paidSen)})."
                    )
                }
                val merged = existing.toDomain().copy(
                    title = plan.title.trim(),
                    providerName = plan.providerName.trim(),
                    purchaseAmountSen = plan.purchaseAmountSen,
                    feeOrInterestSen = plan.feeOrInterestSen,
                    instalmentCount = plan.instalmentCount,
                    firstDueDateEpochDay = plan.firstDueDateEpochDay,
                    accentToken = plan.accentToken,
                    note = plan.note.trim(),
                    isArchived = plan.isArchived,
                    updatedAtEpochMs = now
                )
                val summary = ObligationCalculator.summarizePayLater(merged)
                val resolvedNextDue = summary.schedule.firstOrNull { !it.isCovered }?.dueDateEpochDay
                    ?: merged.firstDueDateEpochDay
                payLaterDao.update(merged.copy(nextDueDateEpochDay = resolvedNextDue).toEntity())
                existing.id
            }
        }
    }

    override suspend fun setPayLaterArchived(id: Long, archived: Boolean) {
        payLaterDao.setArchived(id, archived, System.currentTimeMillis())
    }

    override suspend fun deletePayLaterPlan(id: Long) {
        database.withTransaction {
            obligationPaymentDao.deleteForObligation(ObligationType.PAYLATER, id)
            payLaterDao.deleteById(id)
        }
    }

    override suspend fun recordPayLaterPayment(
        planId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Payment amount must be greater than RM 0.00.")
        }
        return database.withTransaction {
            obligationPaymentDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val plan = payLaterDao.getById(planId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("PayLater plan not found.")
            val totalPayable = plan.purchaseAmountSen + plan.feeOrInterestSen
            val outstanding = (totalPayable - plan.paidSen).coerceAtLeast(0L)
            if (outstanding <= 0L) {
                throw FinanceOperationException.InvalidTransaction("This PayLater plan is already settled.")
            }
            if (amountSen > outstanding) {
                throw FinanceOperationException.InvalidTransaction(
                    "Payment exceeds outstanding PayLater balance (${MyrFormatter.format(outstanding)})."
                )
            }

            val txId = insertTransactionLocked(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = amountSen,
                    category = "PayLater & Instalments",
                    title = "${plan.providerName} · ${plan.title}",
                    note = note.trim(),
                    commitmentId = null,
                    occurredAtEpochMs = paidAtEpochMs,
                    createdAtEpochMs = paidAtEpochMs,
                    categoryId = null,
                    submissionId = "ob_tx_$submissionId"
                )
            )

            val paymentId = obligationPaymentDao.insert(
                ObligationPaymentEntity(
                    id = 0L,
                    obligationType = ObligationType.PAYLATER,
                    obligationId = planId,
                    walletId = walletId,
                    linkedTransactionId = txId,
                    amountSen = amountSen,
                    occurrenceEpochDay = plan.nextDueDateEpochDay,
                    paidAtEpochMs = paidAtEpochMs,
                    note = note.trim(),
                    submissionId = submissionId
                )
            )

            val updatedPaid = plan.paidSen + amountSen
            val updatedCandidate = plan.copy(paidSen = updatedPaid, updatedAtEpochMs = System.currentTimeMillis())
            val summary = ObligationCalculator.summarizePayLater(updatedCandidate)
            val nextDue = summary.schedule.firstOrNull { !it.isCovered }?.dueDateEpochDay
                ?: plan.nextDueDateEpochDay
            payLaterDao.update(updatedCandidate.copy(nextDueDateEpochDay = nextDue).toEntity())

            paymentId
        }
    }

    override suspend fun upsertDebt(debt: DebtRecord): Long {
        if (debt.counterpartyName.isBlank()) {
            throw FinanceOperationException.InvalidTransaction("Enter a creditor or debtor name.")
        }
        if (debt.originalAmountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Original amount must be greater than RM 0.00.")
        }
        val cleanScheduled = debt.scheduledRepaymentSen?.takeIf { it > 0L }
        if (debt.scheduledRepaymentSen != null && debt.scheduledRepaymentSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Scheduled cycle repayment must be greater than RM 0.00.")
        }

        val now = System.currentTimeMillis()
        return database.withTransaction {
            val resolvedTitle = debt.title.trim().ifEmpty {
                if (debt.direction == DebtDirection.I_OWE) "Owed to ${debt.counterpartyName.trim()}"
                else "Lent to ${debt.counterpartyName.trim()}"
            }
            if (debt.id == 0L) {
                debtDao.insert(
                    debt.copy(
                        counterpartyName = debt.counterpartyName.trim(),
                        title = resolvedTitle,
                        paidSen = 0L,
                        scheduledRepaymentSen = cleanScheduled,
                        note = debt.note.trim(),
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            } else {
                val existing = debtDao.getById(debt.id)
                    ?: throw FinanceOperationException.InvalidTransaction("Debt record not found.")
                if (debt.originalAmountSen < existing.paidSen) {
                    throw FinanceOperationException.InvalidTransaction(
                        "Original amount cannot be lower than already settled (${MyrFormatter.format(existing.paidSen)})."
                    )
                }
                debtDao.update(
                    existing.copy(
                        direction = debt.direction,
                        counterpartyName = debt.counterpartyName.trim(),
                        title = resolvedTitle,
                        originalAmountSen = debt.originalAmountSen,
                        dueDateEpochDay = debt.dueDateEpochDay,
                        scheduledRepaymentSen = cleanScheduled,
                        accentToken = debt.accentToken,
                        note = debt.note.trim(),
                        isArchived = debt.isArchived,
                        updatedAtEpochMs = now
                    )
                )
                existing.id
            }
        }
    }

    override suspend fun setDebtArchived(id: Long, archived: Boolean) {
        debtDao.setArchived(id, archived, System.currentTimeMillis())
    }

    override suspend fun deleteDebt(id: Long) {
        database.withTransaction {
            obligationPaymentDao.deleteForObligation(ObligationType.DEBT, id)
            debtDao.deleteById(id)
        }
    }

    override suspend fun recordDebtSettlement(
        debtId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtEpochMs: Long,
        submissionId: String
    ): Long {
        if (amountSen <= 0L) {
            throw FinanceOperationException.InvalidTransaction("Settlement amount must be greater than RM 0.00.")
        }
        return database.withTransaction {
            obligationPaymentDao.findBySubmissionId(submissionId)?.let { return@withTransaction it.id }

            val debt = debtDao.getById(debtId)?.toDomain()
                ?: throw FinanceOperationException.InvalidTransaction("Debt record not found.")
            val outstanding = (debt.originalAmountSen - debt.paidSen).coerceAtLeast(0L)
            if (outstanding <= 0L) {
                throw FinanceOperationException.InvalidTransaction("This debt is already settled in full.")
            }
            if (amountSen > outstanding) {
                throw FinanceOperationException.InvalidTransaction(
                    "Amount exceeds remaining balance (${MyrFormatter.format(outstanding)})."
                )
            }

            val txType = if (debt.direction == DebtDirection.I_OWE) TransactionType.EXPENSE else TransactionType.INCOME
            val txCategory = if (debt.direction == DebtDirection.I_OWE) "Debt Repayment" else "Debt Collection"
            val txTitle = if (debt.direction == DebtDirection.I_OWE) {
                "Repay · ${debt.counterpartyName}"
            } else {
                "Received · ${debt.counterpartyName}"
            }

            val txId = insertTransactionLocked(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = txType,
                    amountSen = amountSen,
                    category = txCategory,
                    title = txTitle,
                    note = note.trim(),
                    commitmentId = null,
                    occurredAtEpochMs = paidAtEpochMs,
                    createdAtEpochMs = paidAtEpochMs,
                    categoryId = null,
                    submissionId = "ob_tx_$submissionId"
                )
            )

            val paymentId = obligationPaymentDao.insert(
                ObligationPaymentEntity(
                    id = 0L,
                    obligationType = ObligationType.DEBT,
                    obligationId = debtId,
                    walletId = walletId,
                    linkedTransactionId = txId,
                    amountSen = amountSen,
                    occurrenceEpochDay = debt.dueDateEpochDay,
                    paidAtEpochMs = paidAtEpochMs,
                    note = note.trim(),
                    submissionId = submissionId
                )
            )

            val newPaidSen = debt.paidSen + amountSen
            val remainingScheduled = if (debt.direction == DebtDirection.I_OWE) {
                debt.scheduledRepaymentSen?.let { (it - amountSen).takeIf { rem -> rem > 0L } }
            } else {
                debt.scheduledRepaymentSen
            }
            debtDao.update(
                debt.copy(
                    paidSen = newPaidSen,
                    scheduledRepaymentSen = remainingScheduled,
                    updatedAtEpochMs = System.currentTimeMillis()
                ).toEntity()
            )
            paymentId
        }
    }

    private suspend fun reconcileLinkedObligationOnTransactionUpdateLocked(
        existingTx: FinanceTransaction,
        updatedTx: FinanceTransaction
    ) {
        val obPayment = obligationPaymentDao.findByTransactionId(updatedTx.id) ?: return
        if (updatedTx.type != existingTx.type) {
            throw FinanceOperationException.InvalidTransaction(
                "Cannot change transaction type while linked to an obligation payment."
            )
        }
        val delta = updatedTx.amountSen - obPayment.amountSen
        val now = System.currentTimeMillis()

        when (obPayment.obligationType) {
            ObligationType.PAYLATER -> {
                val plan = payLaterDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val totalPayable = plan.purchaseAmountSen + plan.feeOrInterestSen
                val newPaid = plan.paidSen + delta
                if (newPaid > totalPayable) {
                    throw FinanceOperationException.InvalidTransaction(
                        "Edited amount exceeds remaining PayLater balance."
                    )
                }
                obligationPaymentDao.update(
                    obPayment.copy(
                        walletId = updatedTx.walletId,
                        amountSen = updatedTx.amountSen,
                        paidAtEpochMs = updatedTx.occurredAtEpochMs
                    )
                )
                val updatedPlan = plan.copy(paidSen = newPaid.coerceAtLeast(0L), updatedAtEpochMs = now)
                val summary = ObligationCalculator.summarizePayLater(updatedPlan)
                val nextDue = summary.schedule.firstOrNull { !it.isCovered }?.dueDateEpochDay
                    ?: plan.nextDueDateEpochDay
                payLaterDao.update(updatedPlan.copy(nextDueDateEpochDay = nextDue).toEntity())
            }

            ObligationType.DEBT -> {
                val debt = debtDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val newPaid = debt.paidSen + delta
                if (newPaid > debt.originalAmountSen) {
                    throw FinanceOperationException.InvalidTransaction(
                        "Edited amount exceeds original debt balance."
                    )
                }
                obligationPaymentDao.update(
                    obPayment.copy(
                        walletId = updatedTx.walletId,
                        amountSen = updatedTx.amountSen,
                        paidAtEpochMs = updatedTx.occurredAtEpochMs
                    )
                )
                val clampedPaid = newPaid.coerceAtLeast(0L)
                val newOutstanding = (debt.originalAmountSen - clampedPaid).coerceAtLeast(0L)
                debtDao.update(
                    debt.copy(
                        paidSen = clampedPaid,
                        scheduledRepaymentSen = debt.scheduledRepaymentSen?.coerceAtMost(newOutstanding)?.takeIf { it > 0L },
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            }

            ObligationType.COMMITMENT -> {
                val commitment = commitmentDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val occDay = obPayment.occurrenceEpochDay ?: commitment.dueDateEpochDay
                if (occDay == commitment.dueDateEpochDay) {
                    val newPaid = commitment.paidSen + delta
                    if (newPaid > commitment.amountSen) {
                        throw FinanceOperationException.InvalidTransaction(
                            "Edited amount exceeds commitment balance."
                        )
                    }
                    obligationPaymentDao.update(
                        obPayment.copy(
                            walletId = updatedTx.walletId,
                            amountSen = updatedTx.amountSen,
                            paidAtEpochMs = updatedTx.occurredAtEpochMs
                        )
                    )
                    val isSettled = newPaid >= commitment.amountSen
                    if (isSettled && commitment.isRecurring && commitment.frequency != CommitmentFrequency.ONE_TIME) {
                        val nextDue = ObligationCalculator.advanceDueDate(
                            commitment.dueDateEpochDay,
                            commitment.frequency,
                            commitment.customIntervalDays,
                            commitment.anchorDayOfMonth
                        )
                        commitmentDao.update(
                            commitment.copy(
                                dueDateEpochDay = nextDue,
                                paidSen = 0L,
                                status = CommitmentStatus.UPCOMING,
                                nextDueDateEpochDay = null,
                                updatedAtEpochMs = now
                            ).toEntity()
                        )
                    } else {
                        val nextStatus = when {
                            newPaid <= 0L -> CommitmentStatus.UPCOMING
                            isSettled -> CommitmentStatus.PAID
                            else -> CommitmentStatus.PARTIALLY_PAID
                        }
                        commitmentDao.update(
                            commitment.copy(
                                paidSen = newPaid.coerceAtLeast(0L),
                                status = nextStatus,
                                nextDueDateEpochDay = null,
                                updatedAtEpochMs = now
                            ).toEntity()
                        )
                    }
                } else {
                    // Payment was for a previously rolled-over recurring occurrence
                    val occPayments = obligationPaymentDao.getForObligation(ObligationType.COMMITMENT, commitment.id)
                    val sameOccOtherTotal = occPayments
                        .filter { it.id != obPayment.id && it.occurrenceEpochDay == occDay }
                        .sumOf { it.amountSen }
                    val newOccTotal = sameOccOtherTotal + updatedTx.amountSen
                    if (newOccTotal > commitment.amountSen) {
                        throw FinanceOperationException.InvalidTransaction(
                            "Edited amount exceeds commitment balance for that cycle."
                        )
                    }
                    obligationPaymentDao.update(
                        obPayment.copy(
                            walletId = updatedTx.walletId,
                            amountSen = updatedTx.amountSen,
                            paidAtEpochMs = updatedTx.occurredAtEpochMs
                        )
                    )
                    val hasLaterPayments = occPayments.any { (it.occurrenceEpochDay ?: Long.MIN_VALUE) > occDay }
                    if (!hasLaterPayments && newOccTotal < commitment.amountSen) {
                        commitmentDao.update(
                            commitment.copy(
                                dueDateEpochDay = occDay,
                                paidSen = newOccTotal.coerceAtLeast(0L),
                                status = if (newOccTotal == 0L) CommitmentStatus.UPCOMING else CommitmentStatus.PARTIALLY_PAID,
                                updatedAtEpochMs = now
                            ).toEntity()
                        )
                    }
                }
            }
        }
    }

    private suspend fun reconcileLinkedObligationOnTransactionDeleteLocked(existingTx: FinanceTransaction) {
        val obPayment = obligationPaymentDao.findByTransactionId(existingTx.id)
        if (obPayment == null) {
            existingTx.commitmentId?.let { cId ->
                commitmentDao.updateStatus(cId, CommitmentStatus.UPCOMING)
            }
            return
        }

        val now = System.currentTimeMillis()
        obligationPaymentDao.deleteById(obPayment.id)

        when (obPayment.obligationType) {
            ObligationType.PAYLATER -> {
                val plan = payLaterDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val updatedPaid = (plan.paidSen - obPayment.amountSen).coerceAtLeast(0L)
                val updatedPlan = plan.copy(paidSen = updatedPaid, updatedAtEpochMs = now)
                val summary = ObligationCalculator.summarizePayLater(updatedPlan)
                val nextDue = summary.schedule.firstOrNull { !it.isCovered }?.dueDateEpochDay
                    ?: plan.firstDueDateEpochDay
                payLaterDao.update(updatedPlan.copy(nextDueDateEpochDay = nextDue).toEntity())
            }

            ObligationType.DEBT -> {
                val debt = debtDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val updatedPaid = (debt.paidSen - obPayment.amountSen).coerceAtLeast(0L)
                debtDao.update(debt.copy(paidSen = updatedPaid, updatedAtEpochMs = now).toEntity())
            }

            ObligationType.COMMITMENT -> {
                val commitment = commitmentDao.getById(obPayment.obligationId)?.toDomain() ?: return
                val occDay = obPayment.occurrenceEpochDay ?: commitment.dueDateEpochDay
                if (occDay == commitment.dueDateEpochDay) {
                    val newPaid = (commitment.paidSen - obPayment.amountSen).coerceAtLeast(0L)
                    val isSettled = newPaid >= commitment.amountSen
                    commitmentDao.update(
                        commitment.copy(
                            paidSen = newPaid,
                            status = when {
                                isSettled -> CommitmentStatus.PAID
                                newPaid == 0L -> CommitmentStatus.UPCOMING
                                else -> CommitmentStatus.PARTIALLY_PAID
                            },
                            nextDueDateEpochDay = if (isSettled) commitment.nextDueDateEpochDay else null,
                            updatedAtEpochMs = now
                        ).toEntity()
                    )
                } else {
                    val remainingPayments = obligationPaymentDao.getForObligation(ObligationType.COMMITMENT, commitment.id)
                    val hasLaterPayments = remainingPayments.any { (it.occurrenceEpochDay ?: Long.MIN_VALUE) > occDay }
                    if (!hasLaterPayments) {
                        val remainingForOcc = remainingPayments
                            .filter { it.occurrenceEpochDay == occDay }
                            .sumOf { it.amountSen }
                        if (remainingForOcc < commitment.amountSen) {
                            commitmentDao.update(
                                commitment.copy(
                                    dueDateEpochDay = occDay,
                                    paidSen = remainingForOcc.coerceAtLeast(0L),
                                    status = if (remainingForOcc == 0L) CommitmentStatus.UPCOMING else CommitmentStatus.PARTIALLY_PAID,
                                    nextDueDateEpochDay = null,
                                    updatedAtEpochMs = now
                                ).toEntity()
                            )
                        }
                    }
                }
            }
        }
    }
}

class OfflineUserPreferencesRepository(
    private val dataStore: UserPreferencesDataStore
) : UserPreferencesRepository {
    override fun observeAppSettings(): Flow<AppSettingsSnapshot> = dataStore.appSettings
    override fun observeThemeMode(): Flow<ThemeMode> = dataStore.themeMode
    override fun observeCommissionFormulaConfig(): Flow<CommissionFormulaConfig> =
        dataStore.commissionFormulaConfig

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setGlassIntensity(intensity: GlassIntensity) = dataStore.setGlassIntensity(intensity)
    override suspend fun setHapticFeedbackEnabled(enabled: Boolean) = dataStore.setHapticFeedbackEnabled(enabled)
    override suspend fun setReduceMotion(enabled: Boolean) = dataStore.setReduceMotion(enabled)
    override suspend fun setAnimationIntensity(intensity: AnimationIntensity) = dataStore.setAnimationIntensity(intensity)
    override suspend fun setDashboardSectionVisible(section: DashboardSectionKey, visible: Boolean) =
        dataStore.setDashboardSectionVisible(section, visible)
    override suspend fun setDashboardSectionOrder(order: List<DashboardSectionKey>) =
        dataStore.setDashboardSectionOrder(order)
    override suspend fun restoreDefaultDashboardLayout() = dataStore.restoreDefaultDashboardLayout()
    override suspend fun setDefaultWalletId(walletId: Long?) = dataStore.setDefaultWalletId(walletId)
    override suspend fun setDefaultBudgetPeriodType(periodType: BudgetPeriodType) =
        dataStore.setDefaultBudgetPeriodType(periodType)
    override suspend fun setAutoAdvanceRecurringCommitments(enabled: Boolean) =
        dataStore.setAutoAdvanceRecurringCommitments(enabled)
    override suspend fun setCommissionTrackingEnabled(enabled: Boolean) =
        dataStore.setCommissionTrackingEnabled(enabled)

    override suspend fun setCommissionFeatureEnabled(enabled: Boolean) =
        dataStore.setCommissionFeatureEnabled(enabled)

    override suspend fun hasCommissionFeatureChoice(): Boolean = dataStore.hasCommissionFeatureChoice()
    override suspend fun saveCommissionFormulaConfig(config: CommissionFormulaConfig) =
        dataStore.saveCommissionFormulaConfig(config)

    override suspend fun resetCommissionFormulaConfig() =
        dataStore.resetCommissionFormulaConfig()

    override suspend fun applyPortableSettings(snapshot: AppSettingsSnapshot) =
        dataStore.applyPortableSettings(snapshot)

    override suspend fun restoreDefaultPreferences() =
        dataStore.restoreDefaultPreferences()

    override fun observeOnboardingCompleted(): Flow<Boolean?> =
        dataStore.onboardingCompletedFlow

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        dataStore.setOnboardingCompleted(completed)

    override suspend fun hasStoredPreferences(): Boolean = dataStore.hasStoredPreferences()
}
