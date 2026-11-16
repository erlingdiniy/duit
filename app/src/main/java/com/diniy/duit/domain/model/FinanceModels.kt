package com.diniy.duit.domain.model

/**
 * Supported wallet classifications.
 */
enum class WalletType(val displayName: String) {
    CASH("Cash"),
    BANK("Bank Account"),
    EWALLET("e-Wallet"),
    SAVINGS("Savings Vault")
}

/**
 * Accents from the Prismatic Glass palette used to tag wallets, categories, and sources.
 */
enum class PrismaticAccentToken {
    AQUA,
    LAVENDER,
    SKY_BLUE,
    PEACH,
    SLATE
}

/**
 * Transaction classification.
 * Transfers move funds between wallets and must never be counted as income or expense.
 */
enum class TransactionType(val displayName: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    TRANSFER("Transfer")
}

/**
 * Settlement lifecycle of a financial commitment.
 * RESERVED or PAID commitments must not be subtracted a second time from Safe to Spend.
 */
enum class CommitmentStatus(val displayName: String) {
    UPCOMING("Upcoming"),
    PARTIALLY_PAID("Partially Paid"),
    RESERVED("Reserved"),
    PAID("Paid")
}

enum class CommitmentFrequency(val displayName: String) {
    ONE_TIME("One-time"),
    WEEKLY("Weekly"),
    BIWEEKLY("Every 2 Weeks"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    CUSTOM_DAYS("Custom Days")
}

enum class PayLaterProviderPreset(val displayName: String) {
    SPAYLATER("SPayLater"),
    ATOME("Atome"),
    GRAB_PAYLATER("Grab PayLater"),
    BOOST_PAYFLEX("Boost PayFlex"),
    CUSTOM("Custom")
}

enum class DebtDirection(val displayName: String) {
    I_OWE("I Owe"),
    OWED_TO_ME("Owed to Me")
}

enum class ObligationType {
    COMMITMENT,
    PAYLATER,
    DEBT
}

/**
 * Settlement lifecycle of a commission record.
 * Non-received amounts must never be counted as available money or Safe to Spend.
 */
enum class CommissionStatus(val displayName: String) {
    UNKNOWN("Unknown / Not Recorded"),
    PENDING("Pending"),
    PARTIALLY_RECEIVED("Partially Received"),
    RECEIVED("Received")
}

/**
 * Period basis for Planning and Insights cashflow reporting.
 */
enum class ReportingPeriodMode(val label: String) {
    SALARY_CYCLE("Salary Cycle"),
    CALENDAR_MONTH("Calendar Month")
}

/**
 * Scope for a spending budget. Budgets act strictly as spending limits, never reserving cash.
 */
enum class BudgetScope(val displayName: String) {
    OVERALL("Overall Spending"),
    CATEGORY("Category Limit")
}

/**
 * Time horizon for a budget.
 */
enum class BudgetPeriodType(val label: String) {
    MONTHLY("Monthly"),
    SALARY_CYCLE("Salary Cycle")
}

/**
 * In-app threshold warning bands evaluated with exact integer-sen math.
 */
enum class BudgetWarningLevel {
    SAFE,
    APPROACHING_80,
    AT_LIMIT_100,
    EXCEEDED
}

enum class SavingsGoalStatus(val displayName: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

enum class SavingsMovementType(val displayName: String) {
    ALLOCATE("Allocated to Goal"),
    TRANSFER_AND_ALLOCATE("Transferred & Allocated"),
    LINK_PROTECTED("Linked Protected Funds"),
    WITHDRAW_TO_WALLET("Released to Wallet"),
    TRANSFER_TO_SPENDING("Transferred to Spending"),
    REASSIGN("Reassigned Between Goals")
}

data class Wallet(
    val id: Long,
    val name: String,
    val type: WalletType,
    val balanceSen: Long,
    val isExcludedFromSafeToSpend: Boolean,
    val accentToken: PrismaticAccentToken,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val isIncludedInTotalBalance: Boolean = true,
    val isArchived: Boolean = false
)

data class Category(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val accentToken: PrismaticAccentToken,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

data class FinanceTransaction(
    val id: Long,
    val walletId: Long,
    val targetWalletId: Long?,
    val type: TransactionType,
    val amountSen: Long,
    val category: String,
    val title: String,
    val note: String,
    val commitmentId: Long?,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val categoryId: Long? = null,
    val submissionId: String? = null
)

data class SalaryCycle(
    val id: Long,
    val label: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val paydayEpochDay: Long,
    val baseSalarySen: Long,
    val committedBudgetSen: Long,
    val isActive: Boolean,
    val receivedSalarySen: Long = 0L,
    val salaryTransactionId: Long? = null,
    val isOpenEnded: Boolean = true
)

data class Commitment(
    val id: Long,
    val title: String,
    val amountSen: Long,
    val dueDateEpochDay: Long,
    val status: CommitmentStatus,
    val walletId: Long?,
    val isRecurring: Boolean,
    val salaryCycleId: Long?,
    val category: String = "Bills & Utilities",
    val categoryId: Long? = null,
    val frequency: CommitmentFrequency = if (isRecurring) CommitmentFrequency.MONTHLY else CommitmentFrequency.ONE_TIME,
    val customIntervalDays: Int? = null,
    val anchorDayOfMonth: Int? = null,
    val paidSen: Long = 0L,
    val nextDueDateEpochDay: Long? = null,
    val note: String = "",
    val isArchived: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

data class PayLaterPlan(
    val id: Long,
    val title: String,
    val providerName: String,
    val purchaseAmountSen: Long,
    val feeOrInterestSen: Long,
    val paidSen: Long,
    val instalmentCount: Int,
    val firstDueDateEpochDay: Long,
    val nextDueDateEpochDay: Long,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class DebtRecord(
    val id: Long,
    val direction: DebtDirection,
    val counterpartyName: String,
    val title: String,
    val originalAmountSen: Long,
    val paidSen: Long,
    val dueDateEpochDay: Long?,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val scheduledRepaymentSen: Long? = null
)

data class ObligationPayment(
    val id: Long,
    val obligationType: ObligationType,
    val obligationId: Long,
    val walletId: Long,
    val linkedTransactionId: Long?,
    val amountSen: Long,
    val occurrenceEpochDay: Long?,
    val paidAtEpochMs: Long,
    val note: String,
    val submissionId: String? = null
)

data class CommissionSource(
    val id: Long,
    val name: String,
    val accentToken: PrismaticAccentToken,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

data class CommissionRecord(
    val id: Long,
    val title: String,
    val sourceLabel: String,
    val amountSen: Long,
    val status: CommissionStatus,
    val earnedDateEpochDay: Long,
    val payoutDateEpochDay: Long?,
    val linkedTransactionId: Long?,
    val notes: String,
    val sourceId: Long? = null,
    val yearMonth: String = "",
    val estimatedGrossSen: Long? = null,
    val actualGrossSen: Long = 0L,
    val receivedNetSen: Long = 0L,
    val adjustmentNote: String = ""
)

data class CommissionPayment(
    val id: Long,
    val commissionId: Long,
    val sourceId: Long?,
    val sourceName: String,
    val walletId: Long,
    val linkedTransactionId: Long?,
    val receivedAtEpochMs: Long,
    val actualGrossSen: Long,
    val epfDeductionSen: Long?,
    val socsoDeductionSen: Long?,
    val eisDeductionSen: Long?,
    val actualNetSen: Long,
    val notes: String,
    val submissionId: String? = null
)

data class CommissionRuleVersion(
    val id: Long,
    val ruleName: String,
    val effectiveFromEpochDay: Long,
    val basicSalarySen: Long,
    val minMonthlySalesSen: Long,
    val employerCostRateBps: Int,
    val standardRateBps: Int,
    val incentiveBaselineThresholdSen: Long,
    val incentiveRateBps: Int,
    val createdAtEpochMs: Long
)

data class DailySalesRecord(
    val id: Long,
    val recordDateEpochDay: Long,
    val grossSalesSen: Long,
    val netSalesSen: Long,
    val unitsSold: Int,
    val notes: String,
    val createdAtEpochMs: Long,
    val grossProfitSen: Long = 0L
)

/**
 * Aggregated snapshot for the Home dashboard.
 * All monetary amounts are in integer minor units (sen).
 */
data class DashboardSummary(
    val totalBalanceSen: Long,
    val liquidSpendableBalanceSen: Long,
    val savingsBalanceSen: Long,
    val upcomingCommitmentsUnreservedSen: Long,
    val reservedCommitmentsSen: Long,
    val paidCommitmentsSen: Long,
    val safeToSpendSen: Long,
    val pendingCommissionsSen: Long,
    val cycleIncomeSen: Long,
    val cycleExpenseSen: Long,
    val walletCount: Int,
    val upcomingCommitmentsCount: Int,
    val goalAllocatedSen: Long = 0L,
    val unallocatedProtectedWalletSen: Long = 0L,
    val activeGoalsCount: Int = 0,
    val currentCycleCommitmentsReservedSen: Long = 0L,
    val currentCyclePayLaterReservedSen: Long = 0L,
    val currentCycleDebtReservedSen: Long = 0L,
    val upcomingFutureCommitmentsSen: Long = 0L,
    val upcomingFutureCommitmentsCount: Int = 0,
    val totalPayLaterOutstandingSen: Long = 0L,
    val nextPayLaterInstalmentSen: Long = 0L,
    val activeReservePeriodLabel: String = ""
)

data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmountSen: Long,
    val targetDateEpochDay: Long?,
    val description: String,
    val iconKey: String,
    val accentToken: PrismaticAccentToken,
    val status: SavingsGoalStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class SavingsAllocation(
    val id: Long,
    val goalId: Long,
    val walletId: Long,
    val allocatedSen: Long,
    val updatedAtEpochMs: Long
)

data class SavingsMovement(
    val id: Long,
    val goalId: Long,
    val sourceWalletId: Long?,
    val targetWalletId: Long?,
    val relatedGoalId: Long?,
    val type: SavingsMovementType,
    val amountSen: Long,
    val linkedTransactionId: Long?,
    val note: String,
    val occurredAtEpochMs: Long,
    val submissionId: String? = null
)

data class Budget(
    val id: Long,
    val name: String,
    val scope: BudgetScope,
    val periodType: BudgetPeriodType,
    val yearMonth: String,
    val salaryCycleId: Long?,
    val categoryId: Long?,
    val categoryName: String,
    val limitSen: Long,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/**
 * Legacy/custom single-rule summary kept for compatibility alongside [CommissionRuleVersion].
 */
data class CommissionFormulaConfig(
    val formulaName: String = "",
    val isConfigured: Boolean = false,
    val isEnabled: Boolean = false,
    val minimumSalesThresholdSen: Long = 0L,
    val rateBps: Int = 0,
    val flatBonusSen: Long = 0L
)

/**
 * Explicit domain errors enforcing financial accuracy and data safeguards.
 */
sealed class FinanceOperationException(message: String) : IllegalStateException(message) {
    class InsufficientBalance(message: String) : FinanceOperationException(message)
    class InvalidTransaction(message: String) : FinanceOperationException(message)
    class WalletHasHistory(message: String) : FinanceOperationException(message)
    class DuplicateCategory(message: String) : FinanceOperationException(message)
    class DuplicateBudget(message: String) : FinanceOperationException(message)
}
