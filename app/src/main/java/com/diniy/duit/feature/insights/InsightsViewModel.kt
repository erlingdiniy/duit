package com.diniy.duit.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.BudgetPeriodOverview
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.calculator.CategorySpendingItem
import com.diniy.duit.domain.calculator.CompanyCommissionEngine
import com.diniy.duit.domain.calculator.CompanyCommissionEstimate
import com.diniy.duit.domain.calculator.InsightsAnalyticsCalculator
import com.diniy.duit.domain.calculator.InsightsFinancialOverview
import com.diniy.duit.domain.calculator.InsightsPeriodPreset
import com.diniy.duit.domain.calculator.InsightsResolvedPeriod
import com.diniy.duit.domain.calculator.MonthlyCashflowPoint
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.calculator.ObligationsOverview
import com.diniy.duit.domain.calculator.OverallSavingsOverview
import com.diniy.duit.domain.calculator.PeriodCashflowReport
import com.diniy.duit.domain.calculator.PeriodReportingCalculator
import com.diniy.duit.domain.calculator.SavingsAnalyticsSummary
import com.diniy.duit.domain.calculator.SmartInsight
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionRuleVersion
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.ReportingPeriodMode
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.preview.SamplePreviewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class SourceCommissionSummary(
    val sourceName: String,
    val actualGrossSen: Long,
    val receivedNetSen: Long,
    val count: Int
)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSamplePreview: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val periodPreset: InsightsPeriodPreset = InsightsPeriodPreset.SALARY_CYCLE,
    val trendHorizonMonths: Int = 6,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val overview: InsightsFinancialOverview = emptyOverview(),
    val monthlyTrends: List<MonthlyCashflowPoint> = emptyList(),
    val categoryBreakdown: List<CategorySpendingItem> = emptyList(),
    val inspectedCategoryName: String? = null,
    val inspectedCategoryTransactions: List<FinanceTransaction> = emptyList(),
    val walletNamesById: Map<Long, String> = emptyMap(),
    val savingsAnalytics: SavingsAnalyticsSummary = emptySavingsAnalytics(),
    val budgetOverview: BudgetPeriodOverview = BudgetCalculator.calculatePeriodOverview(
        periodType = BudgetPeriodType.MONTHLY,
        selectedMonth = YearMonth.now(),
        selectedCycle = null,
        budgets = emptyList(),
        transactions = emptyList(),
        cycles = emptyList(),
        categories = emptyList()
    ),
    val budgetHistory: List<BudgetProgress> = emptyList(),
    val obligationsOverview: ObligationsOverview = ObligationCalculator.calculateOverview(
        emptyList(),
        emptyList(),
        emptyList()
    ),
    val smartInsights: List<SmartInsight> = emptyList(),
    val periodCashflow: PeriodCashflowReport = PeriodCashflowReport(
        periodLabel = "No active salary cycle",
        startEpochDay = 0L,
        endEpochDay = null,
        incomeSen = 0L,
        expenseSen = 0L,
        receivedSalarySen = 0L
    ),
    val salaryCycleIncomeSen: Long = 0L,
    val pendingCommissionsSen: Long = 0L,
    val commissionEstimate: CompanyCommissionEstimate = CompanyCommissionEngine.calculateMonthly(
        0L,
        0L,
        CompanyCommissionEngine.defaultMemoRule()
    ),
    val actualCompanyGrossSen: Long = 0L,
    val actualCompanyNetSen: Long = 0L,
    val deductionsAvailable: Boolean = false,
    val sourceSummaries: List<SourceCommissionSummary> = emptyList(),
    val commissions: List<CommissionRecord> = emptyList(),
    val dailySales: List<DailySalesRecord> = emptyList()
) {
    companion object {
        private fun emptyOverview(): InsightsFinancialOverview {
            val today = LocalDate.now()
            return InsightsFinancialOverview(
                resolvedPeriod = InsightsResolvedPeriod(
                    preset = InsightsPeriodPreset.SALARY_CYCLE,
                    periodLabel = "Current Period",
                    startEpochDay = today.withDayOfMonth(1).toEpochDay(),
                    endEpochDayInclusive = today.toEpochDay(),
                    isOpenEnded = false,
                    isOngoing = true,
                    elapsedDays = today.dayOfMonth.toLong(),
                    totalDaysInPeriod = today.lengthOfMonth().toLong(),
                    hasConfiguredCycle = false
                ),
                totalIncomeSen = 0L,
                totalExpenseSen = 0L,
                netCashFlowSen = 0L,
                savingsRateDisplay = "—",
                hasIncomeForSavingsRate = false,
                avgDailySpendingSen = 0L,
                safeToSpendSen = 0L,
                transferCountExcluded = 0,
                transferTotalExcludedSen = 0L
            )
        }

        private fun emptySavingsAnalytics(): SavingsAnalyticsSummary = SavingsAnalyticsSummary(
            overview = OverallSavingsOverview(0L, 0L, 0L, 0, 0L, 0L, 0f, "0%"),
            goalProgressList = emptyList(),
            periodContributionsSen = 0L,
            periodWithdrawalsSen = 0L,
            periodNetSavingsSen = 0L,
            periodContributionCount = 0,
            monthlyTrend = emptyList(),
            hasMovementHistory = false
        )
    }
}

class InsightsViewModel(
    walletRepository: WalletRepository,
    transactionRepository: TransactionRepository,
    planningRepository: PlanningRepository,
    commissionRepository: CommissionRepository,
    savingsRepository: SavingsRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    obligationRepository: ObligationRepository,
    private val previewModeController: PreviewModeController
) : ViewModel() {

    private val filterStateFlow = MutableStateFlow(InsightsFilterState())
    private val inspectedCategoryFlow = MutableStateFlow<String?>(null)

    private val ledgerBaseFlow = combine(
        walletRepository.observeWallets(),
        transactionRepository.observeTransactions(),
        planningRepository.observeCommitments(),
        planningRepository.observeSalaryCycles(),
        planningRepository.observeActiveCycle()
    ) { wallets, transactions, commitments, cycles, activeCycle ->
        LedgerBase(wallets, transactions, commitments, cycles, activeCycle)
    }

    private val planningAnalyticsBaseFlow = combine(
        categoryRepository.observeCategories(),
        savingsRepository.observeSavingsGoals(),
        savingsRepository.observeSavingsAllocations(),
        savingsRepository.observeSavingsMovements(),
        budgetRepository.observeBudgets()
    ) { categories, goals, allocations, movements, budgets ->
        PlanningAnalyticsBase(categories, goals, allocations, movements, budgets)
    }

    private val obligationBaseFlow = combine(
        obligationRepository.observePayLaterPlans(),
        obligationRepository.observeDebts()
    ) { plans, debts ->
        ObligationBase(plans, debts)
    }

    private val commissionBaseFlow = combine(
        commissionRepository.observeCommissions(),
        commissionRepository.observeCommissionPayments(),
        commissionRepository.observeCommissionRuleVersions(),
        commissionRepository.observeDailySales()
    ) { commissions, payments, ruleVersions, dailySales ->
        CommissionBase(commissions, payments, ruleVersions, dailySales)
    }

    private val domainDataFlow = combine(
        ledgerBaseFlow,
        planningAnalyticsBaseFlow,
        obligationBaseFlow,
        commissionBaseFlow
    ) { ledger, planning, obligations, commissions ->
        CombinedData(ledger, planning, obligations, commissions)
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        domainDataFlow,
        filterStateFlow,
        inspectedCategoryFlow,
        previewModeController.isSamplePreviewEnabled
    ) { data, filter, inspectedCategory, isPreview ->
        val wallets = if (isPreview) SamplePreviewData.wallets else data.ledger.wallets
        val transactions = if (isPreview) SamplePreviewData.transactions else data.ledger.transactions
        val commitments = if (isPreview) SamplePreviewData.commitments else data.ledger.commitments
        val salaryCycles = if (isPreview) listOf(SamplePreviewData.activeCycle) else data.ledger.salaryCycles
        val activeCycle = if (isPreview) SamplePreviewData.activeCycle else data.ledger.activeCycle
        val categories = data.planning.categories
        val savingsGoals = if (isPreview) SamplePreviewData.savingsGoals else data.planning.goals
        val savingsAllocations = if (isPreview) SamplePreviewData.savingsAllocations else data.planning.allocations
        val savingsMovements = if (isPreview) emptyList() else data.planning.movements
        val budgets = if (isPreview) SamplePreviewData.budgets else data.planning.budgets
        val payLaterPlans = if (isPreview) SamplePreviewData.payLaterPlans else data.obligations.payLaterPlans
        val debts = if (isPreview) SamplePreviewData.debts else data.obligations.debts
        val commissions = if (isPreview) SamplePreviewData.commissions else data.commissions.commissions
        val dailySales = if (isPreview) SamplePreviewData.dailySales else data.commissions.dailySales

        val bundle = InsightsAnalyticsCalculator.calculate(
            preset = filter.periodPreset,
            selectedMonth = filter.selectedMonth,
            trendHorizonMonths = filter.trendHorizonMonths,
            customStartEpochDay = filter.customStartEpochDay,
            customEndEpochDay = filter.customEndEpochDay,
            wallets = wallets,
            transactions = transactions,
            categories = categories,
            commitments = commitments,
            commissions = commissions,
            salaryCycles = salaryCycles,
            activeCycle = activeCycle,
            savingsGoals = savingsGoals,
            savingsAllocations = savingsAllocations,
            savingsMovements = savingsMovements,
            budgets = budgets,
            payLaterPlans = payLaterPlans,
            debts = debts
        )

        val salaryCycleReport = PeriodReportingCalculator.calculatePeriodReport(
            mode = ReportingPeriodMode.SALARY_CYCLE,
            selectedMonth = filter.selectedMonth,
            activeCycle = activeCycle,
            transactions = transactions
        )

        val (monthlySalesSen, monthlyGpSen) = CompanyCommissionEngine.aggregateMonthSales(dailySales, filter.selectedMonth)
        val effectiveRule = CompanyCommissionEngine.resolveRuleForMonth(filter.selectedMonth, data.commissions.ruleVersions)
        val estimate = CompanyCommissionEngine.calculateMonthly(monthlySalesSen, monthlyGpSen, effectiveRule)

        val monthStart = filter.selectedMonth.atDay(1).toEpochDay()
        val monthEnd = filter.selectedMonth.atEndOfMonth().toEpochDay()
        val monthKey = filter.selectedMonth.toString()
        val companyMonth = commissions.filter {
            it.sourceLabel.equals("Company", ignoreCase = true) &&
                (it.yearMonth == monthKey || (it.yearMonth.isBlank() && it.earnedDateEpochDay in monthStart..monthEnd))
        }
        val companyIds = companyMonth.map { it.id }.toSet()
        val companyPayments = data.commissions.payments.filter { it.commissionId in companyIds }

        val sourceSummaries = commissions
            .groupBy { it.sourceLabel.ifBlank { "Other" } }
            .entries
            .map { (sourceName, list) ->
                SourceCommissionSummary(
                    sourceName = sourceName,
                    actualGrossSen = list.sumOf { it.actualGrossSen },
                    receivedNetSen = list.sumOf { it.receivedNetSen },
                    count = list.size
                )
            }
            .sortedByDescending { it.receivedNetSen }

        val categoryTxs = if (inspectedCategory != null) {
            bundle.periodExpenseTransactions.filter {
                it.category.ifBlank { "Other" }.equals(inspectedCategory, ignoreCase = true)
            }
        } else {
            emptyList()
        }

        InsightsUiState(
            isLoading = false,
            isSamplePreview = isPreview,
            selectedMonth = filter.selectedMonth,
            periodPreset = filter.periodPreset,
            trendHorizonMonths = filter.trendHorizonMonths,
            customStartEpochDay = filter.customStartEpochDay,
            customEndEpochDay = filter.customEndEpochDay,
            overview = bundle.overview,
            monthlyTrends = bundle.monthlyTrends,
            categoryBreakdown = bundle.categoryBreakdown,
            inspectedCategoryName = inspectedCategory,
            inspectedCategoryTransactions = categoryTxs,
            walletNamesById = wallets.associate { it.id to it.name },
            savingsAnalytics = bundle.savingsAnalytics,
            budgetOverview = bundle.budgetOverview,
            budgetHistory = bundle.budgetHistory,
            obligationsOverview = bundle.obligationsOverview,
            smartInsights = bundle.smartInsights,
            periodCashflow = salaryCycleReport,
            salaryCycleIncomeSen = salaryCycleReport.receivedSalarySen,
            pendingCommissionsSen = bundle.dashboardSummary.pendingCommissionsSen,
            commissionEstimate = estimate,
            actualCompanyGrossSen = companyMonth.sumOf { it.actualGrossSen },
            actualCompanyNetSen = companyMonth.sumOf { it.receivedNetSen },
            deductionsAvailable = companyPayments.any {
                it.epfDeductionSen != null || it.socsoDeductionSen != null || it.eisDeductionSen != null
            },
            sourceSummaries = sourceSummaries,
            commissions = commissions.sortedByDescending { it.earnedDateEpochDay },
            dailySales = dailySales.sortedByDescending { it.recordDateEpochDay }
        )
    }
        .flowOn(Dispatchers.Default)
        .catch { throwable ->
            emit(
                InsightsUiState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Unable to calculate insights."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightsUiState(isLoading = true)
        )

    fun shiftMonth(deltaMonths: Long) {
        val current = filterStateFlow.value
        filterStateFlow.value = current.copy(
            selectedMonth = current.selectedMonth.plusMonths(deltaMonths),
            periodPreset = if (current.periodPreset == InsightsPeriodPreset.SALARY_CYCLE) {
                InsightsPeriodPreset.CALENDAR_MONTH
            } else {
                current.periodPreset
            }
        )
    }

    fun selectPeriodPreset(preset: InsightsPeriodPreset) {
        filterStateFlow.value = filterStateFlow.value.copy(periodPreset = preset)
    }

    fun setCustomDateRange(startEpochDay: Long, endEpochDay: Long) {
        filterStateFlow.value = filterStateFlow.value.copy(
            periodPreset = InsightsPeriodPreset.CUSTOM_RANGE,
            customStartEpochDay = minOf(startEpochDay, endEpochDay),
            customEndEpochDay = maxOf(startEpochDay, endEpochDay)
        )
    }

    fun selectTrendHorizon(months: Int) {
        filterStateFlow.value = filterStateFlow.value.copy(trendHorizonMonths = months)
    }

    fun openCategoryBreakdown(categoryName: String) {
        inspectedCategoryFlow.value = categoryName
    }

    fun dismissCategoryBreakdown() {
        inspectedCategoryFlow.value = null
    }

    fun enableSamplePreview() {
        previewModeController.setSamplePreviewEnabled(true)
    }

    fun exitSamplePreview() {
        previewModeController.setSamplePreviewEnabled(false)
    }

    private data class InsightsFilterState(
        val selectedMonth: YearMonth = YearMonth.now(),
        val periodPreset: InsightsPeriodPreset = InsightsPeriodPreset.SALARY_CYCLE,
        val trendHorizonMonths: Int = 6,
        val customStartEpochDay: Long? = null,
        val customEndEpochDay: Long? = null
    )

    private data class LedgerBase(
        val wallets: List<Wallet>,
        val transactions: List<FinanceTransaction>,
        val commitments: List<Commitment>,
        val salaryCycles: List<SalaryCycle>,
        val activeCycle: SalaryCycle?
    )

    private data class PlanningAnalyticsBase(
        val categories: List<Category>,
        val goals: List<SavingsGoal>,
        val allocations: List<SavingsAllocation>,
        val movements: List<SavingsMovement>,
        val budgets: List<Budget>
    )

    private data class ObligationBase(
        val payLaterPlans: List<PayLaterPlan>,
        val debts: List<DebtRecord>
    )

    private data class CommissionBase(
        val commissions: List<CommissionRecord>,
        val payments: List<CommissionPayment>,
        val ruleVersions: List<CommissionRuleVersion>,
        val dailySales: List<DailySalesRecord>
    )

    private data class CombinedData(
        val ledger: LedgerBase,
        val planning: PlanningAnalyticsBase,
        val obligations: ObligationBase,
        val commissions: CommissionBase
    )

    companion object {
        fun factory(
            walletRepository: WalletRepository,
            transactionRepository: TransactionRepository,
            planningRepository: PlanningRepository,
            commissionRepository: CommissionRepository,
            savingsRepository: SavingsRepository,
            budgetRepository: BudgetRepository,
            categoryRepository: CategoryRepository,
            obligationRepository: ObligationRepository,
            previewModeController: PreviewModeController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InsightsViewModel(
                    walletRepository = walletRepository,
                    transactionRepository = transactionRepository,
                    planningRepository = planningRepository,
                    commissionRepository = commissionRepository,
                    savingsRepository = savingsRepository,
                    budgetRepository = budgetRepository,
                    categoryRepository = categoryRepository,
                    obligationRepository = obligationRepository,
                    previewModeController = previewModeController
                ) as T
            }
        }
    }
}
