package com.diniy.duit.feature.planning

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
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.BudgetPeriodOverview
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.calculator.CommitmentSummary
import com.diniy.duit.domain.calculator.CompanyCommissionEngine
import com.diniy.duit.domain.calculator.CompanyCommissionEstimate
import com.diniy.duit.domain.calculator.DebtSummary
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.calculator.ObligationsOverview
import com.diniy.duit.domain.calculator.OverallSavingsOverview
import com.diniy.duit.domain.calculator.PayLaterSummary
import com.diniy.duit.domain.calculator.PeriodCashflowReport
import com.diniy.duit.domain.calculator.PeriodReportingCalculator
import com.diniy.duit.domain.calculator.SavingsCalculator
import com.diniy.duit.domain.calculator.SavingsGoalProgress
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionRuleVersion
import com.diniy.duit.domain.model.CommissionSource
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.ObligationPayment
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.ReportingPeriodMode
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.preview.SamplePreviewData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class PlanningUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSamplePreview: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val periodMode: ReportingPeriodMode = ReportingPeriodMode.SALARY_CYCLE,
    val activeCycle: SalaryCycle? = null,
    val salaryCycles: List<SalaryCycle> = emptyList(),
    val periodCashflow: PeriodCashflowReport = PeriodCashflowReport(
        periodLabel = "No active salary cycle",
        startEpochDay = 0L,
        endEpochDay = null,
        incomeSen = 0L,
        expenseSen = 0L,
        receivedSalarySen = 0L
    ),
    val commitments: List<Commitment> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val dailySalesRecords: List<DailySalesRecord> = emptyList(),
    val monthDailySalesRecords: List<DailySalesRecord> = emptyList(),
    val commissionEstimate: CompanyCommissionEstimate = CompanyCommissionEngine.calculateMonthly(
        monthlySalesSen = 0L,
        monthlyGrossProfitSen = 0L,
        rule = CompanyCommissionEngine.defaultMemoRule()
    ),
    val ruleVersions: List<CommissionRuleVersion> = emptyList(),
    val commissionSources: List<CommissionSource> = emptyList(),
    val commissions: List<CommissionRecord> = emptyList(),
    val commissionPayments: List<CommissionPayment> = emptyList(),
    val companyActualGrossSen: Long = 0L,
    val companyActualNetSen: Long = 0L,
    val companyDeductionsEntered: Boolean = false,
    val companyEpfSen: Long = 0L,
    val companySocsoSen: Long = 0L,
    val companyEisSen: Long = 0L,
    val savingsOverview: OverallSavingsOverview = OverallSavingsOverview(
        totalProtectedSavingsSen = 0L,
        totalAllocatedToGoalsSen = 0L,
        unallocatedProtectedWalletSen = 0L,
        activeGoalsCount = 0,
        totalActiveTargetSen = 0L,
        totalActiveSavedSen = 0L,
        overallProgressFraction = 0f,
        overallProgressPercentDisplay = "0%"
    ),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val savingsGoalProgressList: List<SavingsGoalProgress> = emptyList(),
    val savingsAllocations: List<SavingsAllocation> = emptyList(),
    val savingsMovements: List<SavingsMovement> = emptyList(),
    val budgetPeriodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val budgetSelectedMonth: YearMonth = YearMonth.now(),
    val budgetSelectedCycle: SalaryCycle? = null,
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
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
    val previousMonthHasBudgets: Boolean = false,
    val obligationsOverview: ObligationsOverview = ObligationCalculator.calculateOverview(
        commitments = emptyList(),
        payLaterPlans = emptyList(),
        debts = emptyList()
    ),
    val commitmentSummaries: List<CommitmentSummary> = emptyList(),
    val payLaterSummaries: List<PayLaterSummary> = emptyList(),
    val debtSummaries: List<DebtSummary> = emptyList(),
    val obligationPayments: List<ObligationPayment> = emptyList()
)

class PlanningViewModel(
    private val planningRepository: PlanningRepository,
    private val commissionRepository: CommissionRepository,
    private val savingsRepository: SavingsRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val obligationRepository: ObligationRepository,
    transactionRepository: TransactionRepository,
    walletRepository: WalletRepository,
    private val previewModeController: PreviewModeController,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val selectedMonthFlow = MutableStateFlow(YearMonth.now())
    private val periodModeFlow = MutableStateFlow(ReportingPeriodMode.SALARY_CYCLE)
    private val budgetPeriodTypeFlow = MutableStateFlow(BudgetPeriodType.MONTHLY)
    private val budgetSelectedMonthFlow = MutableStateFlow(YearMonth.now())
    private val budgetSelectedCycleIdFlow = MutableStateFlow<Long?>(null)
    private val actionErrorFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            runCatching { commissionRepository.ensurePhase3DefaultsSeeded() }
            runCatching { categoryRepository.ensureDefaultCategoriesSeeded() }
        }
        userPreferencesRepository?.let { prefsRepo ->
            viewModelScope.launch {
                prefsRepo.observeAppSettings()
                    .map { it.defaultBudgetPeriodType }
                    .distinctUntilChanged()
                    .collectLatest { defaultPeriod ->
                        budgetPeriodTypeFlow.value = defaultPeriod
                    }
            }
        }
    }

    private val corePlanningFlow = combine(
        planningRepository.observeSalaryCycles(),
        planningRepository.observeActiveCycle(),
        planningRepository.observeCommitments(),
        transactionRepository.observeTransactions(),
        walletRepository.observeWallets()
    ) { cycles, activeCycle, commitments, transactions, wallets ->
        CorePlanningBundle(cycles, activeCycle, commitments, transactions, wallets)
    }

    private val commissionBundleFlow = combine(
        commissionRepository.observeCommissionSources(),
        commissionRepository.observeCommissions(),
        commissionRepository.observeCommissionPayments(),
        commissionRepository.observeCommissionRuleVersions(),
        commissionRepository.observeDailySales()
    ) { sources, commissions, payments, ruleVersions, dailySales ->
        CommissionBundle(sources, commissions, payments, ruleVersions, dailySales)
    }

    private val savingsBundleFlow = combine(
        savingsRepository.observeSavingsGoals(),
        savingsRepository.observeSavingsAllocations(),
        savingsRepository.observeSavingsMovements()
    ) { goals, allocations, movements ->
        SavingsBundle(goals, allocations, movements)
    }

    private val budgetBundleFlow = combine(
        budgetRepository.observeBudgets(),
        categoryRepository.observeCategories(),
        budgetPeriodTypeFlow,
        budgetSelectedMonthFlow,
        budgetSelectedCycleIdFlow
    ) { budgets, categories, periodType, budgetMonth, budgetCycleId ->
        BudgetBundle(budgets, categories, periodType, budgetMonth, budgetCycleId)
    }

    private val obligationBundleFlow = combine(
        obligationRepository.observePayLaterPlans(),
        obligationRepository.observeDebts(),
        obligationRepository.observeObligationPayments()
    ) { plans, debts, payments ->
        ObligationBundle(plans, debts, payments)
    }

    val uiState: StateFlow<PlanningUiState> = combine(
        corePlanningFlow,
        commissionBundleFlow,
        savingsBundleFlow,
        budgetBundleFlow,
        obligationBundleFlow,
        selectedMonthFlow,
        periodModeFlow,
        previewModeController.isSamplePreviewEnabled,
        actionErrorFlow
    ) { values ->
        val core = values[0] as CorePlanningBundle
        val comm = values[1] as CommissionBundle
        val savings = values[2] as SavingsBundle
        val budgetBundle = values[3] as BudgetBundle
        val obBundle = values[4] as ObligationBundle
        val selectedMonth = values[5] as YearMonth
        val periodMode = values[6] as ReportingPeriodMode
        val isPreview = values[7] as Boolean
        val error = values[8] as String?

        val cycles = if (isPreview) listOf(SamplePreviewData.activeCycle) else core.cycles
        val activeCycle = if (isPreview) SamplePreviewData.activeCycle else core.activeCycle
        val commitments = if (isPreview) SamplePreviewData.commitments else core.commitments
        val transactions = if (isPreview) SamplePreviewData.transactions else core.transactions
        val wallets = if (isPreview) SamplePreviewData.wallets else core.wallets
        val dailySales = if (isPreview) SamplePreviewData.dailySales else comm.dailySales
        val commissions = if (isPreview) SamplePreviewData.commissions else comm.commissions
        val savingsGoals = if (isPreview) SamplePreviewData.savingsGoals else savings.goals
        val savingsAllocations = if (isPreview) SamplePreviewData.savingsAllocations else savings.allocations
        val savingsMovements = if (isPreview) emptyList() else savings.movements
        val budgets = if (isPreview) SamplePreviewData.budgets else budgetBundle.budgets
        val payLaterPlans = if (isPreview) SamplePreviewData.payLaterPlans else obBundle.payLaterPlans
        val debts = if (isPreview) SamplePreviewData.debts else obBundle.debts
        val obligationPayments = if (isPreview) emptyList() else obBundle.payments
        val categories = budgetBundle.categories

        val obligationsOverview = ObligationCalculator.calculateOverview(
            commitments = commitments,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = activeCycle
        )
        val commitmentSummaries = commitments.map {
            ObligationCalculator.summarizeCommitment(it, activeCycle = activeCycle)
        }
        val payLaterSummaries = payLaterPlans.map {
            ObligationCalculator.summarizePayLater(it, activeCycle = activeCycle)
        }
        val debtSummaries = debts.map {
            ObligationCalculator.summarizeDebt(it, activeCycle = activeCycle)
        }

        val sortedCyclesAsc = cycles.sortedWith(
            compareBy<SalaryCycle> { it.startDateEpochDay }.thenBy { it.id }
        )
        val resolvedBudgetCycle = budgetBundle.selectedCycleId?.let { id ->
            sortedCyclesAsc.firstOrNull { it.id == id }
        } ?: activeCycle ?: sortedCyclesAsc.lastOrNull()

        val budgetOverview = BudgetCalculator.calculatePeriodOverview(
            periodType = budgetBundle.periodType,
            selectedMonth = budgetBundle.selectedMonth,
            selectedCycle = resolvedBudgetCycle,
            budgets = budgets,
            transactions = transactions,
            cycles = sortedCyclesAsc,
            categories = categories
        )
        val budgetHistory = BudgetCalculator.calculateHistoryEntries(
            periodType = budgetBundle.periodType,
            selectedMonth = budgetBundle.selectedMonth,
            selectedCycle = resolvedBudgetCycle,
            budgets = budgets,
            transactions = transactions,
            cycles = sortedCyclesAsc,
            categories = categories
        )
        val prevMonthKey = budgetBundle.selectedMonth.minusMonths(1).toString()
        val prevMonthHasBudgets = !isPreview && budgets.any {
            !it.isArchived && it.periodType == BudgetPeriodType.MONTHLY && it.yearMonth == prevMonthKey
        }

        val dashboardSummary = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = transactions,
            commitments = commitments,
            commissions = commissions,
            savingsAllocations = savingsAllocations,
            savingsGoals = savingsGoals,
            payLaterPlans = payLaterPlans,
            debts = debts,
            activeCycle = activeCycle
        )
        val savingsOverview = SavingsCalculator.calculateOverview(
            totalProtectedSavingsSen = dashboardSummary.savingsBalanceSen,
            goals = savingsGoals,
            allocations = savingsAllocations
        )
        val goalProgressList = savingsGoals.map { goal ->
            SavingsCalculator.calculateGoalProgress(goal, savingsAllocations)
        }

        val periodCashflow = PeriodReportingCalculator.calculatePeriodReport(
            mode = periodMode,
            selectedMonth = selectedMonth,
            activeCycle = activeCycle,
            transactions = transactions
        )

        val monthStart = selectedMonth.atDay(1).toEpochDay()
        val monthEnd = selectedMonth.atEndOfMonth().toEpochDay()
        val monthDailySales = dailySales.filter { it.recordDateEpochDay in monthStart..monthEnd }
        val (monthlySalesSen, monthlyGpSen) = CompanyCommissionEngine.aggregateMonthSales(dailySales, selectedMonth)

        val effectiveRule = CompanyCommissionEngine.resolveRuleForMonth(selectedMonth, comm.ruleVersions)
        val estimate = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = monthlySalesSen,
            monthlyGrossProfitSen = monthlyGpSen,
            rule = effectiveRule
        )

        val monthKey = selectedMonth.toString()
        val companyMonthCommissions = commissions.filter {
            it.sourceLabel.equals("Company", ignoreCase = true) &&
                (it.yearMonth == monthKey || (it.yearMonth.isBlank() && it.earnedDateEpochDay in monthStart..monthEnd))
        }
        val companyMonthIds = companyMonthCommissions.map { it.id }.toSet()
        val companyMonthPayments = comm.payments.filter { it.commissionId in companyMonthIds }

        val companyActualGrossSen = companyMonthCommissions.sumOf { it.actualGrossSen }
        val companyActualNetSen = companyMonthCommissions.sumOf { it.receivedNetSen }
        val hasPayslipDeductions = companyMonthPayments.any {
            it.epfDeductionSen != null || it.socsoDeductionSen != null || it.eisDeductionSen != null
        }

        PlanningUiState(
            isLoading = false,
            errorMessage = error,
            isSamplePreview = isPreview,
            selectedMonth = selectedMonth,
            periodMode = periodMode,
            activeCycle = activeCycle,
            salaryCycles = cycles,
            periodCashflow = periodCashflow,
            commitments = commitments,
            wallets = wallets,
            dailySalesRecords = dailySales,
            monthDailySalesRecords = monthDailySales,
            commissionEstimate = estimate,
            ruleVersions = comm.ruleVersions,
            commissionSources = comm.sources,
            commissions = commissions,
            commissionPayments = comm.payments,
            companyActualGrossSen = companyActualGrossSen,
            companyActualNetSen = companyActualNetSen,
            companyDeductionsEntered = hasPayslipDeductions,
            companyEpfSen = companyMonthPayments.sumOf { it.epfDeductionSen ?: 0L },
            companySocsoSen = companyMonthPayments.sumOf { it.socsoDeductionSen ?: 0L },
            companyEisSen = companyMonthPayments.sumOf { it.eisDeductionSen ?: 0L },
            savingsOverview = savingsOverview,
            savingsGoals = savingsGoals,
            savingsGoalProgressList = goalProgressList,
            savingsAllocations = savingsAllocations,
            savingsMovements = savingsMovements,
            budgetPeriodType = budgetBundle.periodType,
            budgetSelectedMonth = budgetBundle.selectedMonth,
            budgetSelectedCycle = resolvedBudgetCycle,
            categories = categories,
            budgets = budgets,
            budgetOverview = budgetOverview,
            budgetHistory = budgetHistory,
            previousMonthHasBudgets = prevMonthHasBudgets,
            obligationsOverview = obligationsOverview,
            commitmentSummaries = commitmentSummaries,
            payLaterSummaries = payLaterSummaries,
            debtSummaries = debtSummaries,
            obligationPayments = obligationPayments
        )
    }.catch { throwable ->
        emit(
            PlanningUiState(
                isLoading = false,
                errorMessage = throwable.message ?: "Unable to load planning data."
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanningUiState(isLoading = true)
    )

    fun shiftMonth(deltaMonths: Long) {
        selectedMonthFlow.value = selectedMonthFlow.value.plusMonths(deltaMonths)
    }

    fun selectPeriodMode(mode: ReportingPeriodMode) {
        periodModeFlow.value = mode
    }

    fun saveSalaryCycle(
        cycle: SalaryCycle,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { planningRepository.upsertAndReconcileSalaryCycle(cycle) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not save salary cycle.") }
        }
    }

    fun deleteSalaryCycle(id: Long) {
        viewModelScope.launch {
            runCatching { planningRepository.deleteSalaryCycle(id) }
                .onFailure { actionErrorFlow.value = it.message ?: "Could not delete cycle." }
        }
    }

    fun saveDailySalesRecord(
        record: DailySalesRecord,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.upsertDailySales(record) }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Could not save daily sales entry.") }
        }
    }

    fun deleteDailySalesRecord(id: Long) {
        viewModelScope.launch {
            runCatching { commissionRepository.deleteDailySales(id) }
                .onFailure { actionErrorFlow.value = it.message ?: "Could not delete daily sales entry." }
        }
    }

    fun saveCommissionRuleVersion(
        rule: CommissionRuleVersion,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.saveCommissionRuleVersion(rule) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not save rule version.") }
        }
    }

    fun createCommissionSource(
        name: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.createCommissionSource(name, accent) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not add commission source.") }
        }
    }

    fun renameCommissionSource(
        id: Long,
        newName: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.renameCommissionSource(id, newName, accent) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not rename commission source.") }
        }
    }

    fun setCommissionSourceArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            runCatching { commissionRepository.setCommissionSourceArchived(id, archived) }
                .onFailure { actionErrorFlow.value = it.message ?: "Unable to update commission source." }
        }
    }

    fun saveCommissionRecord(
        record: CommissionRecord,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.upsertCommission(record) }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onSuccess()
                }
                .onFailure { onError(it.message ?: "Could not save commission record.") }
        }
    }

    fun deleteCommissionRecord(
        commissionId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.deleteCommissionRecordSafely(commissionId) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Unable to delete commission.") }
        }
    }

    fun recordCommissionPayment(
        payment: CommissionPayment,
        markAsFull: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.recordCommissionPayment(payment, markAsFull) }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onSuccess()
                }
                .onFailure { onError(it.message ?: "Could not record commission payment.") }
        }
    }

    fun deleteCommissionPayment(
        paymentId: Long,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { commissionRepository.deleteCommissionPayment(paymentId) }
                .onFailure { onError(it.message ?: "Could not reverse payment.") }
        }
    }

    fun saveSavingsGoal(
        goal: SavingsGoal,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { savingsRepository.upsertSavingsGoal(goal) }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Could not save savings goal.") }
        }
    }

    fun completeSavingsGoal(
        goalId: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { savingsRepository.completeSavingsGoal(goalId) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not complete savings goal.") }
        }
    }

    fun archiveSavingsGoal(
        goalId: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { savingsRepository.archiveSavingsGoal(goalId) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not archive savings goal.") }
        }
    }

    fun deleteSavingsGoal(
        goalId: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { savingsRepository.deleteSavingsGoalSafely(goalId) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not delete savings goal.") }
        }
    }

    fun allocateToGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                savingsRepository.allocateToGoal(goalId, walletId, amountSen, note, submissionId)
            }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Could not allocate funds to goal.") }
        }
    }

    fun transferToProtectedWalletAndAllocate(
        goalId: Long,
        sourceWalletId: Long,
        protectedWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                savingsRepository.transferToProtectedWalletAndAllocate(
                    goalId,
                    sourceWalletId,
                    protectedWalletId,
                    amountSen,
                    note,
                    submissionId
                )
            }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Could not transfer and allocate savings.") }
        }
    }

    fun withdrawFromGoal(
        goalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                savingsRepository.withdrawFromGoal(goalId, walletId, amountSen, note, submissionId)
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not withdraw from goal.") }
        }
    }

    fun transferFromGoalToSpendingWallet(
        goalId: Long,
        protectedWalletId: Long,
        spendingWalletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                savingsRepository.transferFromGoalToSpendingWallet(
                    goalId,
                    protectedWalletId,
                    spendingWalletId,
                    amountSen,
                    note,
                    submissionId
                )
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not move funds to spending wallet.") }
        }
    }

    fun reassignGoalAllocation(
        fromGoalId: Long,
        toGoalId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                savingsRepository.reassignGoalAllocation(
                    fromGoalId,
                    toGoalId,
                    walletId,
                    amountSen,
                    note,
                    submissionId
                )
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not reassign goal allocation.") }
        }
    }

    fun selectBudgetPeriodType(type: BudgetPeriodType) {
        budgetPeriodTypeFlow.value = type
    }

    fun shiftBudgetPeriod(delta: Int) {
        if (budgetPeriodTypeFlow.value == BudgetPeriodType.MONTHLY) {
            budgetSelectedMonthFlow.value = budgetSelectedMonthFlow.value.plusMonths(delta.toLong())
        } else {
            val cycles = uiState.value.salaryCycles.sortedWith(
                compareBy<SalaryCycle> { it.startDateEpochDay }.thenBy { it.id }
            )
            if (cycles.isEmpty()) return
            val currentId = budgetSelectedCycleIdFlow.value
                ?: uiState.value.budgetSelectedCycle?.id
                ?: cycles.last().id
            val idx = cycles.indexOfFirst { it.id == currentId }
            if (idx >= 0) {
                val targetIdx = (idx + delta).coerceIn(0, cycles.lastIndex)
                budgetSelectedCycleIdFlow.value = cycles[targetIdx].id
            }
        }
    }

    fun saveBudget(
        budget: Budget,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { budgetRepository.upsertBudget(budget) }
                .onSuccess {
                    previewModeController.setSamplePreviewEnabled(false)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Could not save budget.") }
        }
    }

    fun setBudgetArchived(
        id: Long,
        archived: Boolean,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { budgetRepository.setBudgetArchived(id, archived) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not update budget archive status.") }
        }
    }

    fun deleteBudget(
        id: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { budgetRepository.deleteBudget(id) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not delete budget.") }
        }
    }

    fun copyPreviousMonthBudgets(
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val targetYm = budgetSelectedMonthFlow.value
        val sourceYm = targetYm.minusMonths(1)
        viewModelScope.launch {
            runCatching { budgetRepository.copyMonthlyBudgets(sourceYm.toString(), targetYm.toString()) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not copy previous month budgets.") }
        }
    }

    fun saveCommitmentDetail(commitment: Commitment, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.upsertCommitmentDetail(commitment) }
                .onSuccess { previewModeController.setSamplePreviewEnabled(false); onDone() }
                .onFailure { onError(it.message ?: "Could not save commitment.") }
        }
    }

    fun setCommitmentArchived(id: Long, archived: Boolean, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.setCommitmentArchived(id, archived) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not update commitment.") }
        }
    }

    fun deleteCommitment(id: Long, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.deleteCommitment(id) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not delete commitment.") }
        }
    }

    fun recordCommitmentPayment(
        commitmentId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtMs: Long,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val autoAdvance = userPreferencesRepository
                    ?.observeAppSettings()
                    ?.firstOrNull()
                    ?.autoAdvanceRecurringCommitments
                    ?: true
                obligationRepository.recordCommitmentPayment(
                    commitmentId = commitmentId,
                    walletId = walletId,
                    amountSen = amountSen,
                    note = note,
                    paidAtEpochMs = paidAtMs,
                    submissionId = submissionId,
                    autoAdvanceRecurring = autoAdvance
                )
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not record commitment payment.") }
        }
    }

    fun openNextRecurringOccurrence(
        commitmentId: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { obligationRepository.openNextRecurringOccurrence(commitmentId) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not open next occurrence.") }
        }
    }

    fun savePayLaterPlan(plan: PayLaterPlan, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.upsertPayLaterPlan(plan) }
                .onSuccess { previewModeController.setSamplePreviewEnabled(false); onDone() }
                .onFailure { onError(it.message ?: "Could not save PayLater plan.") }
        }
    }

    fun setPayLaterArchived(id: Long, archived: Boolean, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.setPayLaterArchived(id, archived) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not update PayLater plan.") }
        }
    }

    fun deletePayLaterPlan(id: Long, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.deletePayLaterPlan(id) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not delete PayLater plan.") }
        }
    }

    fun recordPayLaterPayment(
        planId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtMs: Long,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                obligationRepository.recordPayLaterPayment(
                    planId,
                    walletId,
                    amountSen,
                    note,
                    paidAtMs,
                    submissionId
                )
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not record PayLater payment.") }
        }
    }

    fun saveDebt(debt: DebtRecord, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.upsertDebt(debt) }
                .onSuccess { previewModeController.setSamplePreviewEnabled(false); onDone() }
                .onFailure { onError(it.message ?: "Could not save debt record.") }
        }
    }

    fun setDebtArchived(id: Long, archived: Boolean, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.setDebtArchived(id, archived) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not update debt record.") }
        }
    }

    fun deleteDebt(id: Long, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { obligationRepository.deleteDebt(id) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not delete debt record.") }
        }
    }

    fun recordDebtSettlement(
        debtId: Long,
        walletId: Long,
        amountSen: Long,
        note: String,
        paidAtMs: Long,
        submissionId: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                obligationRepository.recordDebtSettlement(
                    debtId,
                    walletId,
                    amountSen,
                    note,
                    paidAtMs,
                    submissionId
                )
            }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Could not record debt settlement.") }
        }
    }

    fun enableSamplePreview() {
        previewModeController.setSamplePreviewEnabled(true)
    }

    fun exitSamplePreview() {
        previewModeController.setSamplePreviewEnabled(false)
    }

    fun dismissError() {
        actionErrorFlow.value = null
    }

    private data class CorePlanningBundle(
        val cycles: List<SalaryCycle>,
        val activeCycle: SalaryCycle?,
        val commitments: List<Commitment>,
        val transactions: List<FinanceTransaction>,
        val wallets: List<Wallet>
    )

    private data class CommissionBundle(
        val sources: List<CommissionSource>,
        val commissions: List<CommissionRecord>,
        val payments: List<CommissionPayment>,
        val ruleVersions: List<CommissionRuleVersion>,
        val dailySales: List<DailySalesRecord>
    )

    private data class SavingsBundle(
        val goals: List<SavingsGoal>,
        val allocations: List<SavingsAllocation>,
        val movements: List<SavingsMovement>
    )

    private data class BudgetBundle(
        val budgets: List<Budget>,
        val categories: List<Category>,
        val periodType: BudgetPeriodType,
        val selectedMonth: YearMonth,
        val selectedCycleId: Long?
    )

    private data class ObligationBundle(
        val payLaterPlans: List<PayLaterPlan>,
        val debts: List<DebtRecord>,
        val payments: List<ObligationPayment>
    )

    companion object {
        fun factory(
            planningRepository: PlanningRepository,
            commissionRepository: CommissionRepository,
            savingsRepository: SavingsRepository,
            budgetRepository: BudgetRepository,
            categoryRepository: CategoryRepository,
            obligationRepository: ObligationRepository,
            transactionRepository: TransactionRepository,
            walletRepository: WalletRepository,
            previewModeController: PreviewModeController,
            userPreferencesRepository: UserPreferencesRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanningViewModel(
                    planningRepository = planningRepository,
                    commissionRepository = commissionRepository,
                    savingsRepository = savingsRepository,
                    budgetRepository = budgetRepository,
                    categoryRepository = categoryRepository,
                    obligationRepository = obligationRepository,
                    transactionRepository = transactionRepository,
                    walletRepository = walletRepository,
                    previewModeController = previewModeController,
                    userPreferencesRepository = userPreferencesRepository
                ) as T
            }
        }
    }
}
