package com.diniy.duit.feature.planning

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.text.style.TextOverflow
import com.diniy.duit.core.ui.components.candyToneFor
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandySticker
import com.diniy.duit.core.ui.components.CandyProgressRing
import com.diniy.duit.core.ui.components.CandyDecor
import com.diniy.duit.core.ui.components.CandyBubbleCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.R
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.SamplePreviewBanner
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.BudgetPeriodOverview
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.calculator.CompanyCommissionEstimate
import com.diniy.duit.domain.calculator.OverallSavingsOverview
import com.diniy.duit.domain.calculator.SavingsGoalProgress
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionRuleVersion
import com.diniy.duit.domain.model.CommissionSource
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DailySalesRecord
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.ReportingPeriodMode
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.feature.budgets.BudgetDetailSheet
import com.diniy.duit.feature.budgets.BudgetEditorSheet
import com.diniy.duit.feature.budgets.BudgetOverviewCard
import com.diniy.duit.feature.budgets.BudgetPeriodNavigator
import com.diniy.duit.feature.budgets.BudgetProgressCard
import com.diniy.duit.feature.budgets.BudgetWarningCalloutCard
import com.diniy.duit.feature.budgets.PlanningSectionPillBar
import com.diniy.duit.feature.budgets.PlanningSectionTab
import com.diniy.duit.feature.obligations.CommitmentDetailSheet
import com.diniy.duit.feature.obligations.CommitmentEditorSheet
import com.diniy.duit.feature.obligations.CommitmentObligationCard
import com.diniy.duit.feature.obligations.DebtDetailSheet
import com.diniy.duit.feature.obligations.DebtEditorSheet
import com.diniy.duit.feature.obligations.DebtRecordCard
import com.diniy.duit.feature.obligations.ObligationsOverviewGrid
import com.diniy.duit.feature.obligations.PayLaterDetailSheet
import com.diniy.duit.feature.obligations.PayLaterEditorSheet
import com.diniy.duit.feature.obligations.PayLaterPlanCard
import com.diniy.duit.feature.savings.SavingsActionMode
import com.diniy.duit.feature.savings.SavingsActionSheet
import com.diniy.duit.feature.savings.SavingsGoalDetailSheet
import com.diniy.duit.feature.savings.SavingsGoalEditorSheet
import com.diniy.duit.feature.savings.SavingsIconMapper
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(
    uiState: PlanningUiState,
    contentPadding: PaddingValues,
    onShiftMonth: (Long) -> Unit,
    onSelectPeriodMode: (ReportingPeriodMode) -> Unit,
    onSaveSalaryCycle: (SalaryCycle, (String) -> Unit, () -> Unit) -> Unit,
    onDeleteSalaryCycle: (Long) -> Unit,
    onSaveDailySales: (DailySalesRecord, (String) -> Unit, () -> Unit) -> Unit,
    onDeleteDailySales: (Long) -> Unit,
    onSaveRuleVersion: (CommissionRuleVersion, (String) -> Unit, () -> Unit) -> Unit,
    onCreateSource: (String, PrismaticAccentToken, (String) -> Unit, () -> Unit) -> Unit,
    onRenameSource: (Long, String, PrismaticAccentToken, (String) -> Unit, () -> Unit) -> Unit,
    onToggleArchiveSource: (Long, Boolean) -> Unit,
    onSaveCommission: (CommissionRecord, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteCommission: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRecordPayment: (CommissionPayment, Boolean, () -> Unit, (String) -> Unit) -> Unit,
    onDeletePayment: (Long, (String) -> Unit) -> Unit,
    onSaveSavingsGoal: (SavingsGoal, () -> Unit, (String) -> Unit) -> Unit,
    onCompleteSavingsGoal: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onArchiveSavingsGoal: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteSavingsGoal: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onAllocateToGoal: (Long, Long, Long, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onTransferToProtectedAndAllocate: (Long, Long, Long, Long, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onWithdrawFromGoal: (Long, Long, Long, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onTransferGoalToSpending: (Long, Long, Long, Long, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onReassignGoalAllocation: (Long, Long, Long, Long, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onSelectBudgetPeriodType: (BudgetPeriodType) -> Unit,
    onShiftBudgetPeriod: (Int) -> Unit,
    onSaveBudget: (Budget, () -> Unit, (String) -> Unit) -> Unit,
    onSetBudgetArchived: (Long, Boolean, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteBudget: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onCopyPreviousMonthBudgets: (() -> Unit, (String) -> Unit) -> Unit,
    onSaveCommitmentDetail: (Commitment, () -> Unit, (String) -> Unit) -> Unit,
    onSetCommitmentArchived: (Long, Boolean, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteCommitment: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRecordCommitmentPayment: (Long, Long, Long, String, Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onOpenNextCommitmentOccurrence: (Long, () -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> },
    onSavePayLaterPlan: (PayLaterPlan, () -> Unit, (String) -> Unit) -> Unit,
    onSetPayLaterArchived: (Long, Boolean, () -> Unit, (String) -> Unit) -> Unit,
    onDeletePayLaterPlan: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRecordPayLaterPayment: (Long, Long, Long, String, Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onSaveDebt: (DebtRecord, () -> Unit, (String) -> Unit) -> Unit,
    onSetDebtArchived: (Long, Boolean, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteDebt: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRecordDebtSettlement: (Long, Long, Long, String, Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFinancialPreferences: () -> Unit = {},
    requestCommissionSection: Boolean = false,
    onConsumeCommissionSection: () -> Unit = {},
    onEnablePreview: () -> Unit,
    onExitPreview: () -> Unit,
    onDismissError: () -> Unit,
    requestedSectionTab: PlanningSectionTab? = null,
    onConsumeRequestedSectionTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val glass = LocalPrismaticGlass.current
    var selectedSectionTab by rememberSaveable { mutableStateOf(PlanningSectionTab.GOALS) }
    androidx.compose.runtime.LaunchedEffect(requestedSectionTab) {
        if (requestedSectionTab != null) {
            selectedSectionTab = requestedSectionTab
            onConsumeRequestedSectionTab()
        }
    }
    var showCycleSheet by rememberSaveable { mutableStateOf(false) }
    val celebrate = com.diniy.duit.core.ui.components.LocalCandyCelebrate.current
    // Phase 7B: one Bills & Commitments section visible at a time (less scrolling).
    var billsSection by rememberSaveable { mutableStateOf(BillsSection.BILLS) }
    androidx.compose.runtime.LaunchedEffect(requestCommissionSection) {
        if (requestCommissionSection) {
            selectedSectionTab = PlanningSectionTab.COMMITMENTS
            billsSection = BillsSection.INCOME
            onConsumeCommissionSection()
        }
    }
    var showDailySalesSheet by rememberSaveable { mutableStateOf(false) }
    var showRuleEditorSheet by rememberSaveable { mutableStateOf(false) }
    var showSourcesSheet by rememberSaveable { mutableStateOf(false) }
    var showCommissionEditor by rememberSaveable { mutableStateOf(false) }
    var editingCommissionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var payingCommissionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showGoalEditor by rememberSaveable { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectedGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionSheetMode by rememberSaveable { mutableStateOf<SavingsActionMode?>(null) }
    var actionGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showInactiveGoals by rememberSaveable { mutableStateOf(false) }
    var showBudgetEditor by rememberSaveable { mutableStateOf(false) }
    var editingBudgetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectedBudgetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBudgetHistory by rememberSaveable { mutableStateOf(false) }
    var showCommitmentEditor by rememberSaveable { mutableStateOf(false) }
    var editingCommitmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectedCommitmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showArchivedCommitments by rememberSaveable { mutableStateOf(false) }
    var showPayLaterEditor by rememberSaveable { mutableStateOf(false) }
    var editingPayLaterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectedPayLaterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showArchivedPayLater by rememberSaveable { mutableStateOf(false) }
    var showDebtEditor by rememberSaveable { mutableStateOf(false) }
    var editingDebtId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectedDebtId by rememberSaveable { mutableStateOf<Long?>(null) }
    var debtFilterDirection by rememberSaveable { mutableStateOf<DebtDirection?>(null) }
    var showArchivedDebts by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "planning_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Planning",
                        style = com.diniy.duit.core.ui.theme.SpatialTheme.typography.screenHeading,
                        color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Your goals, budgets & bills",
                        style = com.diniy.duit.core.ui.theme.SpatialTheme.typography.supportingInfo,
                        color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.secondaryText,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Phase 11G: shared Mint Bubble button (opens money preferences, as before)
                com.diniy.duit.core.ui.components.CandyMintIconButton(
                    icon = androidx.compose.material.icons.Icons.Rounded.Tune,
                    contentDescription = "Money preferences",
                    onClick = onOpenFinancialPreferences,
                    pressedTilt = 0f
                )
            }
        }

        // Second row: cycle selector + Categories (scrolls sideways instead of wrapping on small screens)
        item(key = "planning_header_controls", contentType = "header_controls") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.diniy.duit.core.ui.components.SpatialGlassPill(
                    label = uiState.activeCycle?.label ?: "Salary Cycles",
                    accentColor = com.diniy.duit.core.ui.theme.SpatialTheme.colors.aqua,
                    onClick = { showCycleSheet = true }
                )
                com.diniy.duit.core.ui.components.SpatialGlassPill(
                    label = "Categories",
                    accentColor = com.diniy.duit.core.ui.theme.SpatialTheme.colors.lavender,
                    onClick = onOpenCategories
                )
            }
        }

        item(key = "planning_tabs", contentType = "pill_tabs") {
            PlanningSectionPillBar(
                selectedTab = selectedSectionTab,
                onSelectTab = { selectedSectionTab = it }
            )
        }

        item(key = "preview_banner", contentType = "banner") {
            AnimatedVisibility(visible = uiState.isSamplePreview) {
                SamplePreviewBanner(onExitPreview = onExitPreview)
            }
        }

        if (uiState.errorMessage != null) {
            item(key = "error", contentType = "error") {
                PrismaticErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = onDismissError
                )
            }
        }

        if (uiState.isLoading) {
            item(key = "loading", contentType = "loading") { PrismaticLoadingCard() }
        } else {
            when (selectedSectionTab) {
                PlanningSectionTab.GOALS -> {
                    // 0. Savings Goals & Protected Savings Section
                    item(key = "savings_header", contentType = "section_heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "Savings & Goals · Sample" else "Savings & Goals",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!uiState.isSamplePreview) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (uiState.savingsGoals.any { it.status == SavingsGoalStatus.ACTIVE }) {
                                        PrismaticButton(
                                            text = "Move Funds",
                                            onClick = {
                                                actionGoalId = null
                                                actionSheetMode = SavingsActionMode.ALLOCATE
                                            },
                                            variant = PrismaticButtonVariant.Ghost
                                        )
                                    }
                                    PrismaticButton(
                                        text = "+ New Goal",
                                        onClick = {
                                            editingGoalId = null
                                            showGoalEditor = true
                                        },
                                        variant = PrismaticButtonVariant.Secondary
                                    )
                                }
                            }
                        }
                    }

                    item(key = "savings_overview_card", contentType = "savings_overview") {
                        SavingsOverviewDashboardCard(overview = uiState.savingsOverview)
                    }

                    val activeGoalProgress = uiState.savingsGoalProgressList.filter {
                        it.goal.status == SavingsGoalStatus.ACTIVE
                    }
                    val inactiveGoalProgress = uiState.savingsGoalProgressList.filter {
                        it.goal.status != SavingsGoalStatus.ACTIVE
                    }

                    if (activeGoalProgress.isEmpty() && inactiveGoalProgress.isEmpty()) {
                        item(key = "savings_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_planning,
                                title = "Start your first savings goal",
                                message = "Reserve funds inside spending wallets or link money held in protected savings vaults without double-counting.",
                                actionLabel = if (!uiState.isSamplePreview) "Create Goal" else "View Sample",
                                onActionClick = {
                                    if (!uiState.isSamplePreview) {
                                        editingGoalId = null
                                        showGoalEditor = true
                                    }
                                }
                            )
                        }
                    } else {
                        items(
                            items = activeGoalProgress,
                            key = { "savings_goal_${it.goal.id}" },
                            contentType = { "savings_goal_row" }
                        ) { goalProgress ->
                            SavingsGoalCard(
                                progress = goalProgress,
                                isPreview = uiState.isSamplePreview,
                                onInspect = { inspectedGoalId = goalProgress.goal.id },
                                onQuickAdd = {
                                    actionGoalId = goalProgress.goal.id
                                    actionSheetMode = SavingsActionMode.ALLOCATE
                                }
                            )
                        }

                        if (inactiveGoalProgress.isNotEmpty()) {
                            item(key = "inactive_goals_toggle", contentType = "toggle") {
                                PrismaticButton(
                                    text = if (showInactiveGoals) {
                                        "Hide Completed & Archived (${inactiveGoalProgress.size})"
                                    } else {
                                        "Show Completed & Archived (${inactiveGoalProgress.size})"
                                    },
                                    onClick = { showInactiveGoals = !showInactiveGoals },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                            }
                            if (showInactiveGoals) {
                                items(
                                    items = inactiveGoalProgress,
                                    key = { "savings_goal_${it.goal.id}" },
                                    contentType = { "savings_goal_row" }
                                ) { goalProgress ->
                                    SavingsGoalCard(
                                        progress = goalProgress,
                                        isPreview = uiState.isSamplePreview,
                                        onInspect = { inspectedGoalId = goalProgress.goal.id },
                                        onQuickAdd = null
                                    )
                                }
                            }
                        }
                    }
                }

                PlanningSectionTab.BUDGETS -> {
                    item(key = "budgets_header", contentType = "section_heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "Budgets · Sample" else "Budgets",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!uiState.isSamplePreview) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (uiState.budgetPeriodType == BudgetPeriodType.MONTHLY &&
                                        uiState.previousMonthHasBudgets &&
                                        uiState.budgetOverview.overallBudget == null &&
                                        uiState.budgetOverview.categoryBudgets.isEmpty()
                                    ) {
                                        PrismaticButton(
                                            text = "Copy Last Month",
                                            onClick = { onCopyPreviousMonthBudgets({}, {}) },
                                            variant = PrismaticButtonVariant.Ghost
                                        )
                                    }
                                    PrismaticButton(
                                        text = "+ New Budget",
                                        onClick = {
                                            editingBudgetId = null
                                            showBudgetEditor = true
                                        },
                                        variant = PrismaticButtonVariant.Secondary
                                    )
                                }
                            }
                        }
                    }

                    item(key = "budget_period_nav", contentType = "controls") {
                        BudgetPeriodNavigator(
                            periodType = uiState.budgetPeriodType,
                            selectedMonth = uiState.budgetSelectedMonth,
                            selectedCycle = uiState.budgetSelectedCycle,
                            salaryCycles = uiState.salaryCycles,
                            onSelectPeriodType = onSelectBudgetPeriodType,
                            onShiftPeriod = onShiftBudgetPeriod
                        )
                    }

                    if (uiState.budgetOverview.exceededCount > 0 ||
                        uiState.budgetOverview.warningCount100 > 0 ||
                        uiState.budgetOverview.warningCount80 > 0
                    ) {
                        item(key = "budget_warning_banner", contentType = "warning") {
                            BudgetWarningCalloutCard(overview = uiState.budgetOverview)
                        }
                    }

                    item(key = "budget_overview_card", contentType = "budget_overview") {
                        BudgetOverviewCard(overview = uiState.budgetOverview)
                    }

                    val overall = uiState.budgetOverview.overallBudget
                    val categoryBudgets = uiState.budgetOverview.categoryBudgets
                    val periodBudgets = buildList {
                        if (overall != null) add(overall)
                        addAll(categoryBudgets)
                    }

                    if (periodBudgets.isEmpty()) {
                        item(key = "budgets_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_planning,
                                title = "No budgets for ${uiState.budgetOverview.periodLabel}",
                                message = "Create an overall cap or category spending limits. Budgets never modify wallet balances or Safe to Spend.",
                                actionLabel = if (!uiState.isSamplePreview) "Create Budget" else "View Sample",
                                onActionClick = {
                                    if (!uiState.isSamplePreview) {
                                        editingBudgetId = null
                                        showBudgetEditor = true
                                    }
                                },
                                secondaryActionLabel = if (!uiState.isSamplePreview) "View Sample" else null,
                                onSecondaryActionClick = if (!uiState.isSamplePreview) onEnablePreview else null
                            )
                        }
                    } else {
                        items(
                            items = periodBudgets,
                            key = { "budget_row_${it.budget.id}" },
                            contentType = { "budget_row" }
                        ) { progress ->
                            BudgetProgressCard(
                                progress = progress,
                                categories = uiState.categories,
                                showPeriodTag = false,
                                onClick = { inspectedBudgetId = progress.budget.id }
                            )
                        }
                    }

                    if (uiState.budgetHistory.isNotEmpty()) {
                        item(key = "budget_history_toggle", contentType = "toggle") {
                            PrismaticButton(
                                text = if (showBudgetHistory) {
                                    "Hide History & Archived (${uiState.budgetHistory.size})"
                                } else {
                                    "Show History & Archived (${uiState.budgetHistory.size})"
                                },
                                onClick = { showBudgetHistory = !showBudgetHistory },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        if (showBudgetHistory) {
                            items(
                                items = uiState.budgetHistory,
                                key = { "budget_hist_${it.budget.id}" },
                                contentType = { "budget_row" }
                            ) { progress ->
                                BudgetProgressCard(
                                    progress = progress,
                                    categories = uiState.categories,
                                    showPeriodTag = true,
                                    onClick = { inspectedBudgetId = progress.budget.id }
                                )
                            }
                        }
                    }
                }

                PlanningSectionTab.COMMITMENTS -> {
                    // 1. Obligations Overview Cards
                    item(key = "obligations_overview", contentType = "overview") {
                        ObligationsOverviewGrid(overview = uiState.obligationsOverview)
                    }

                    item(key = "bills_sections", contentType = "filter_chips") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BillsSection.entries.forEach { section ->
                                com.diniy.duit.core.ui.components.SpatialGlassPill(
                                    label = section.label,
                                    selected = billsSection == section,
                                    onClick = { billsSection = section }
                                )
                            }
                        }
                    }

                    if (billsSection == BillsSection.BILLS) {

                    // 2. Monthly & Fixed Commitments
                    item(key = "commitments_title", contentType = "section_heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "Commitments · Sample" else "Monthly Commitments",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!uiState.isSamplePreview) {
                                PrismaticButton(
                                    text = "+ Add",
                                    onClick = {
                                        editingCommitmentId = null
                                        showCommitmentEditor = true
                                    },
                                    variant = PrismaticButtonVariant.Secondary
                                )
                            }
                        }
                    }

                    val activeCommitments = uiState.commitmentSummaries.filter { !it.commitment.isArchived }
                    val archivedCommitments = uiState.commitmentSummaries.filter { it.commitment.isArchived }

                    if (activeCommitments.isEmpty()) {
                        item(key = "commitments_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_planning,
                                title = "No recurring or fixed commitments",
                                message = "Add rent, subscriptions, bills & insurance without touching wallet balances until paid.",
                                actionLabel = if (!uiState.isSamplePreview) "Add Commitment" else "View Sample",
                                onActionClick = {
                                    if (!uiState.isSamplePreview) {
                                        editingCommitmentId = null
                                        showCommitmentEditor = true
                                    }
                                }
                            )
                        }
                    } else {
                        items(
                            items = activeCommitments,
                            key = { "plan_commitment_${it.commitment.id}" },
                            contentType = { "commitment_row" }
                        ) { summary ->
                            CommitmentObligationCard(
                                summary = summary,
                                onClick = { inspectedCommitmentId = summary.commitment.id }
                            )
                        }
                    }

                    if (archivedCommitments.isNotEmpty()) {
                        item(key = "archived_commitments_toggle", contentType = "toggle") {
                            PrismaticButton(
                                text = if (showArchivedCommitments) "Hide Archived Commitments (${archivedCommitments.size})"
                                else "Show Archived Commitments (${archivedCommitments.size})",
                                onClick = { showArchivedCommitments = !showArchivedCommitments },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        if (showArchivedCommitments) {
                            items(
                                items = archivedCommitments,
                                key = { "arch_commitment_${it.commitment.id}" },
                                contentType = { "commitment_row" }
                            ) { summary ->
                                CommitmentObligationCard(
                                    summary = summary,
                                    onClick = { inspectedCommitmentId = summary.commitment.id }
                                )
                            }
                        }
                    }

                    }

                    if (billsSection == BillsSection.PAYLATER) {
                    // 3. PayLater & Instalments
                    item(key = "paylater_title", contentType = "section_heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "PayLater & Instalments · Sample" else "PayLater & Instalments",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!uiState.isSamplePreview) {
                                PrismaticButton(
                                    text = "+ Add Plan",
                                    onClick = {
                                        editingPayLaterId = null
                                        showPayLaterEditor = true
                                    },
                                    variant = PrismaticButtonVariant.Secondary
                                )
                            }
                        }
                    }

                    val activePayLater = uiState.payLaterSummaries.filter { !it.plan.isArchived }
                    val archivedPayLater = uiState.payLaterSummaries.filter { it.plan.isArchived }

                    if (activePayLater.isEmpty()) {
                        item(key = "paylater_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_planning,
                                title = "No PayLater plans",
                                message = "Track SPayLater, Atome & instalment plans including fees and partial payments.",
                                actionLabel = if (!uiState.isSamplePreview) "Add Plan" else "View Sample",
                                onActionClick = {
                                    if (!uiState.isSamplePreview) {
                                        editingPayLaterId = null
                                        showPayLaterEditor = true
                                    }
                                }
                            )
                        }
                    } else {
                        items(
                            items = activePayLater,
                            key = { "paylater_${it.plan.id}" },
                            contentType = { "paylater_row" }
                        ) { summary ->
                            PayLaterPlanCard(
                                summary = summary,
                                onClick = { inspectedPayLaterId = summary.plan.id }
                            )
                        }
                    }

                    if (archivedPayLater.isNotEmpty()) {
                        item(key = "archived_paylater_toggle", contentType = "toggle") {
                            PrismaticButton(
                                text = if (showArchivedPayLater) "Hide Archived PayLater (${archivedPayLater.size})"
                                else "Show Archived PayLater (${archivedPayLater.size})",
                                onClick = { showArchivedPayLater = !showArchivedPayLater },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        if (showArchivedPayLater) {
                            items(
                                items = archivedPayLater,
                                key = { "arch_paylater_${it.plan.id}" },
                                contentType = { "paylater_row" }
                            ) { summary ->
                                PayLaterPlanCard(
                                    summary = summary,
                                    onClick = { inspectedPayLaterId = summary.plan.id }
                                )
                            }
                        }
                    }

                    }

                    if (billsSection == BillsSection.DEBTS) {
                    // 4. Debt Tracker (I Owe & Owed to Me)
                    item(key = "debts_title", contentType = "section_heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "Debt Tracker · Sample" else "Debt Tracker",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!uiState.isSamplePreview) {
                                PrismaticButton(
                                    text = "+ Add Debt",
                                    onClick = {
                                        editingDebtId = null
                                        showDebtEditor = true
                                    },
                                    variant = PrismaticButtonVariant.Secondary
                                )
                            }
                        }
                    }

                    item(key = "debt_direction_filters", contentType = "filter_chips") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = debtFilterDirection == null,
                                onClick = { debtFilterDirection = null },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = debtFilterDirection == DebtDirection.I_OWE,
                                onClick = { debtFilterDirection = DebtDirection.I_OWE },
                                label = { Text("I Owe") }
                            )
                            FilterChip(
                                selected = debtFilterDirection == DebtDirection.OWED_TO_ME,
                                onClick = { debtFilterDirection = DebtDirection.OWED_TO_ME },
                                label = { Text("Owed to Me") }
                            )
                        }
                    }

                    val activeDebts = uiState.debtSummaries.filter {
                        !it.debt.isArchived && (debtFilterDirection == null || it.debt.direction == debtFilterDirection)
                    }
                    val archivedDebts = uiState.debtSummaries.filter { it.debt.isArchived }

                    if (activeDebts.isEmpty()) {
                        item(key = "debts_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_planning,
                                title = "No active debts recorded",
                                message = "Separate money you owe from receivables owed to you. Expected receipts never inflate Safe to Spend.",
                                actionLabel = if (!uiState.isSamplePreview) "Add Debt" else "View Sample",
                                onActionClick = {
                                    if (!uiState.isSamplePreview) {
                                        editingDebtId = null
                                        showDebtEditor = true
                                    }
                                }
                            )
                        }
                    } else {
                        items(
                            items = activeDebts,
                            key = { "debt_${it.debt.id}" },
                            contentType = { "debt_row" }
                        ) { summary ->
                            DebtRecordCard(
                                summary = summary,
                                onClick = { inspectedDebtId = summary.debt.id }
                            )
                        }
                    }

                    if (archivedDebts.isNotEmpty()) {
                        item(key = "archived_debts_toggle", contentType = "toggle") {
                            PrismaticButton(
                                text = if (showArchivedDebts) "Hide Archived Debts (${archivedDebts.size})"
                                else "Show Archived Debts (${archivedDebts.size})",
                                onClick = { showArchivedDebts = !showArchivedDebts },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        if (showArchivedDebts) {
                            items(
                                items = archivedDebts,
                                key = { "arch_debt_${it.debt.id}" },
                                contentType = { "debt_row" }
                            ) { summary ->
                                DebtRecordCard(
                                    summary = summary,
                                    onClick = { inspectedDebtId = summary.debt.id }
                                )
                            }
                        }
                    }

                    }

                    if (billsSection == BillsSection.INCOME) {
                    // 5. Salary Cycle & Commission Tools (Moved out of Goals; kept accessible in Commitments)
                    item(key = "salary_cycle_card", contentType = "salary_cycle") {
                        SmartSalaryCycleCard(
                            periodMode = uiState.periodMode,
                            activeCycle = uiState.activeCycle,
                            report = uiState.periodCashflow,
                            onManageCycles = { showCycleSheet = true }
                        )
                    }

                    item(key = "period_controls", contentType = "controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonthSelectorCard(
                                selectedMonth = uiState.selectedMonth,
                                onPreviousMonth = { onShiftMonth(-1) },
                                onNextMonth = { onShiftMonth(1) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportingPeriodMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = uiState.periodMode == mode,
                                        onClick = { onSelectPeriodMode(mode) },
                                        label = { Text(mode.label) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Daily Sales & GP Tracker Card
                    item(key = "daily_sales_card", contentType = "daily_sales") {
                        DailySalesSummaryCard(
                            selectedMonth = uiState.selectedMonth,
                            estimate = uiState.commissionEstimate,
                            loggedDaysCount = uiState.monthDailySalesRecords.size,
                            onOpenDailyTracker = { showDailySalesSheet = true }
                        )
                    }

                    // Company Commission Calculator (Memo Rules + Estimated vs Actual Verification)
                    item(key = "company_commission_card", contentType = "company_commission") {
                        CompanyCommissionMemoCard(
                            estimate = uiState.commissionEstimate,
                            actualGrossSen = uiState.companyActualGrossSen,
                            actualNetSen = uiState.companyActualNetSen,
                            deductionsEntered = uiState.companyDeductionsEntered,
                            epfSen = uiState.companyEpfSen,
                            socsoSen = uiState.companySocsoSen,
                            eisSen = uiState.companyEisSen,
                            onEditRules = { showRuleEditorSheet = true }
                        )
                    }

                    // Multi-Source Commission Tracking
                    item(key = "commissions_header", contentType = "section_heading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSamplePreview) "Commissions · Sample" else "Multi-Source Commissions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PrismaticButton(
                                    text = "Sources",
                                    onClick = { showSourcesSheet = true },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                                PrismaticButton(
                                    text = "Add",
                                    onClick = {
                                        editingCommissionId = null
                                        showCommissionEditor = true
                                    },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                            }
                        }
                    }

                    if (uiState.commissions.isEmpty()) {
                        item(key = "commissions_empty", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_calculator,
                                title = "Track Company, Lenovo, ASUS & Acer payouts",
                                message = "Record irregular or partial commissions from any source. Only actual net payouts received credit your wallet.",
                                actionLabel = "Add Commission",
                                onActionClick = {
                                    editingCommissionId = null
                                    showCommissionEditor = true
                                },
                                secondaryActionLabel = if (!uiState.isSamplePreview) "View Sample" else null,
                                onSecondaryActionClick = if (!uiState.isSamplePreview) onEnablePreview else null
                            )
                        }
                    } else {
                        items(
                            items = uiState.commissions,
                            key = { "comm_rec_${it.id}" },
                            contentType = { "commission_row" }
                        ) { record ->
                            MultiSourceCommissionCard(
                                record = record,
                                isPreview = uiState.isSamplePreview,
                                onEdit = {
                                    editingCommissionId = record.id
                                    showCommissionEditor = true
                                },
                                onRecordPayment = {
                                    payingCommissionId = record.id
                                }
                            )
                        }
                    }

                    }
                }
            }
        }
    }

    if (showCycleSheet) {
        SalaryCycleSheet(
            cycles = uiState.salaryCycles,
            onSaveCycle = onSaveSalaryCycle,
            onDeleteCycle = onDeleteSalaryCycle,
            onDismiss = { showCycleSheet = false }
        )
    }

    if (showDailySalesSheet) {
        DailySalesSheet(
            records = uiState.dailySalesRecords,
            onSaveRecord = onSaveDailySales,
            onDeleteRecord = onDeleteDailySales,
            onDismiss = { showDailySalesSheet = false }
        )
    }

    if (showRuleEditorSheet) {
        CommissionRuleEditorSheet(
            currentRule = uiState.commissionEstimate.ruleVersion,
            ruleVersions = uiState.ruleVersions,
            onSaveRuleVersion = onSaveRuleVersion,
            onDismiss = { showRuleEditorSheet = false }
        )
    }

    if (showSourcesSheet) {
        CommissionSourcesSheet(
            sources = uiState.commissionSources,
            onCreateSource = onCreateSource,
            onRenameSource = onRenameSource,
            onToggleArchive = onToggleArchiveSource,
            onDismiss = { showSourcesSheet = false }
        )
    }

    if (showCommissionEditor) {
        val editingRecord = editingCommissionId?.let { id ->
            uiState.commissions.firstOrNull { it.id == id }
        }
        CommissionRecordEditorSheet(
            sources = uiState.commissionSources,
            selectedMonth = uiState.selectedMonth,
            existingRecord = editingRecord,
            defaultCompanyEstimatedSen = uiState.commissionEstimate.estimatedGrossCommissionSen,
            onSave = { record, onError ->
                onSaveCommission(
                    record,
                    {
                        showCommissionEditor = false
                        editingCommissionId = null
                    },
                    onError
                )
            },
            onDelete = { id, onError ->
                onDeleteCommission(
                    id,
                    {
                        showCommissionEditor = false
                        editingCommissionId = null
                    },
                    onError
                )
            },
            onDismiss = {
                showCommissionEditor = false
                editingCommissionId = null
            }
        )
    }

    payingCommissionId?.let { cId ->
        val commission = uiState.commissions.firstOrNull { it.id == cId }
        if (commission != null) {
            val payments = uiState.commissionPayments.filter { it.commissionId == cId }
            RecordCommissionPaymentSheet(
                commission = commission,
                payments = payments,
                wallets = uiState.wallets,
                onRecordPayment = { payment, markFull, onError ->
                    onRecordPayment(
                        payment,
                        markFull,
                        { payingCommissionId = null },
                        onError
                    )
                },
                onDeletePayment = onDeletePayment,
                onDismiss = { payingCommissionId = null }
            )
        }
    }

    if (showGoalEditor) {
        val editingGoal = editingGoalId?.let { id ->
            uiState.savingsGoals.firstOrNull { it.id == id }
        }
        SavingsGoalEditorSheet(
            existingGoal = editingGoal,
            onSave = { goal, onError ->
                onSaveSavingsGoal(
                    goal,
                    {
                        showGoalEditor = false
                        editingGoalId = null
                    },
                    onError
                )
            },
            onDismiss = {
                showGoalEditor = false
                editingGoalId = null
            }
        )
    }

    inspectedGoalId?.let { gId ->
        val goalProgress = uiState.savingsGoalProgressList.firstOrNull { it.goal.id == gId }
        if (goalProgress != null) {
            SavingsGoalDetailSheet(
                progress = goalProgress,
                wallets = uiState.wallets,
                movements = uiState.savingsMovements,
                isPreview = uiState.isSamplePreview,
                onEdit = {
                    inspectedGoalId = null
                    editingGoalId = gId
                    showGoalEditor = true
                },
                onAction = { mode ->
                    inspectedGoalId = null
                    actionGoalId = gId
                    actionSheetMode = mode
                },
                onComplete = { id, onError ->
                    onCompleteSavingsGoal(
                        id,
                        { inspectedGoalId = null },
                        onError
                    )
                },
                onArchive = { id, onError ->
                    onArchiveSavingsGoal(
                        id,
                        { inspectedGoalId = null },
                        onError
                    )
                },
                onDelete = { id, onError ->
                    onDeleteSavingsGoal(
                        id,
                        { inspectedGoalId = null },
                        onError
                    )
                },
                onDismiss = { inspectedGoalId = null }
            )
        }
    }

    actionSheetMode?.let { mode ->
        SavingsActionSheet(
            initialMode = mode,
            initialGoalId = actionGoalId,
            goals = uiState.savingsGoals,
            allocations = uiState.savingsAllocations,
            wallets = uiState.wallets,
            onAllocate = { goalId, walletId, amountSen, note, submissionId, onError ->
                onAllocateToGoal(
                    goalId,
                    walletId,
                    amountSen,
                    note,
                    submissionId,
                    {
                        actionSheetMode = null
                        celebrate("Added to your goal")
                    },
                    onError
                )
            },
            onTransferAndAllocate = { goalId, sourceWalletId, protectedWalletId, amountSen, note, submissionId, onError ->
                onTransferToProtectedAndAllocate(
                    goalId,
                    sourceWalletId,
                    protectedWalletId,
                    amountSen,
                    note,
                    submissionId,
                    {
                        actionSheetMode = null
                        celebrate("Added to your goal")
                    },
                    onError
                )
            },
            onWithdraw = { goalId, walletId, amountSen, note, submissionId, onError ->
                onWithdrawFromGoal(
                    goalId,
                    walletId,
                    amountSen,
                    note,
                    submissionId,
                    { actionSheetMode = null },
                    onError
                )
            },
            onTransferToSpending = { goalId, protectedWalletId, spendingWalletId, amountSen, note, submissionId, onError ->
                onTransferGoalToSpending(
                    goalId,
                    protectedWalletId,
                    spendingWalletId,
                    amountSen,
                    note,
                    submissionId,
                    { actionSheetMode = null },
                    onError
                )
            },
            onReassign = { fromGoalId, toGoalId, walletId, amountSen, note, submissionId, onError ->
                onReassignGoalAllocation(
                    fromGoalId,
                    toGoalId,
                    walletId,
                    amountSen,
                    note,
                    submissionId,
                    { actionSheetMode = null },
                    onError
                )
            },
            onDismiss = { actionSheetMode = null }
        )
    }

    if (showBudgetEditor) {
        val editingBudget = editingBudgetId?.let { id ->
            uiState.budgets.firstOrNull { it.id == id }
        }
        BudgetEditorSheet(
            existingBudget = editingBudget,
            initialPeriodType = uiState.budgetPeriodType,
            initialYearMonth = uiState.budgetSelectedMonth,
            initialCycleId = uiState.budgetSelectedCycle?.id,
            salaryCycles = uiState.salaryCycles,
            categories = uiState.categories,
            onSave = { budget, onError ->
                onSaveBudget(
                    budget,
                    {
                        showBudgetEditor = false
                        editingBudgetId = null
                    },
                    onError
                )
            },
            onDismiss = {
                showBudgetEditor = false
                editingBudgetId = null
            }
        )
    }

    inspectedBudgetId?.let { bId ->
        val progress = uiState.budgetOverview.overallBudget?.takeIf { it.budget.id == bId }
            ?: uiState.budgetOverview.categoryBudgets.firstOrNull { it.budget.id == bId }
            ?: uiState.budgetHistory.firstOrNull { it.budget.id == bId }
        if (progress != null) {
            BudgetDetailSheet(
                progress = progress,
                categories = uiState.categories,
                isPreview = uiState.isSamplePreview,
                onEdit = {
                    inspectedBudgetId = null
                    editingBudgetId = bId
                    showBudgetEditor = true
                },
                onToggleArchive = { id, archived, onError ->
                    onSetBudgetArchived(
                        id,
                        archived,
                        { inspectedBudgetId = null },
                        onError
                    )
                },
                onDelete = { id, onError ->
                    onDeleteBudget(
                        id,
                        { inspectedBudgetId = null },
                        onError
                    )
                },
                onDismiss = { inspectedBudgetId = null }
            )
        }
    }

    if (showCommitmentEditor) {
        val editingCommitment = editingCommitmentId?.let { id -> uiState.commitments.firstOrNull { it.id == id } }
        CommitmentEditorSheet(
            existing = editingCommitment,
            categories = uiState.categories,
            onSave = { item, onError ->
                onSaveCommitmentDetail(item, { showCommitmentEditor = false; editingCommitmentId = null }, onError)
            },
            onDismiss = { showCommitmentEditor = false; editingCommitmentId = null }
        )
    }

    inspectedCommitmentId?.let { cId ->
        val summary = uiState.commitmentSummaries.firstOrNull { it.commitment.id == cId }
        if (summary != null) {
            CommitmentDetailSheet(
                summary = summary,
                wallets = uiState.wallets,
                payments = uiState.obligationPayments.filter {
                    it.obligationType == ObligationType.COMMITMENT && it.obligationId == cId
                },
                isPreview = uiState.isSamplePreview,
                onRecordPayment = { id, wId, amt, note, paidAt, subId, onError ->
                    onRecordCommitmentPayment(id, wId, amt, note, paidAt, subId, {
                        inspectedCommitmentId = null
                        celebrate("Payment recorded")
                    }, onError)
                },
                onOpenNextOccurrence = { id, onError ->
                    onOpenNextCommitmentOccurrence(id, { inspectedCommitmentId = null }, onError)
                },
                onEdit = {
                    inspectedCommitmentId = null
                    editingCommitmentId = cId
                    showCommitmentEditor = true
                },
                onToggleArchive = { id, archived, onError ->
                    onSetCommitmentArchived(id, archived, { inspectedCommitmentId = null }, onError)
                },
                onDelete = { id, onError ->
                    onDeleteCommitment(id, { inspectedCommitmentId = null }, onError)
                },
                onDismiss = { inspectedCommitmentId = null }
            )
        }
    }

    if (showPayLaterEditor) {
        val editingPlan = editingPayLaterId?.let { id -> uiState.payLaterSummaries.firstOrNull { it.plan.id == id }?.plan }
        PayLaterEditorSheet(
            existing = editingPlan,
            onSave = { plan, onError ->
                onSavePayLaterPlan(plan, { showPayLaterEditor = false; editingPayLaterId = null }, onError)
            },
            onDismiss = { showPayLaterEditor = false; editingPayLaterId = null }
        )
    }

    inspectedPayLaterId?.let { pId ->
        val summary = uiState.payLaterSummaries.firstOrNull { it.plan.id == pId }
        if (summary != null) {
            PayLaterDetailSheet(
                summary = summary,
                wallets = uiState.wallets,
                payments = uiState.obligationPayments.filter {
                    it.obligationType == ObligationType.PAYLATER && it.obligationId == pId
                },
                isPreview = uiState.isSamplePreview,
                onRecordPayment = { id, wId, amt, note, paidAt, subId, onError ->
                    onRecordPayLaterPayment(id, wId, amt, note, paidAt, subId, { inspectedPayLaterId = null }, onError)
                },
                onEdit = {
                    inspectedPayLaterId = null
                    editingPayLaterId = pId
                    showPayLaterEditor = true
                },
                onToggleArchive = { id, archived, onError ->
                    onSetPayLaterArchived(id, archived, { inspectedPayLaterId = null }, onError)
                },
                onDelete = { id, onError ->
                    onDeletePayLaterPlan(id, { inspectedPayLaterId = null }, onError)
                },
                onDismiss = { inspectedPayLaterId = null }
            )
        }
    }

    if (showDebtEditor) {
        val editingDebt = editingDebtId?.let { id -> uiState.debtSummaries.firstOrNull { it.debt.id == id }?.debt }
        DebtEditorSheet(
            existing = editingDebt,
            onSave = { debt, onError ->
                onSaveDebt(debt, { showDebtEditor = false; editingDebtId = null }, onError)
            },
            onDismiss = { showDebtEditor = false; editingDebtId = null }
        )
    }

    inspectedDebtId?.let { dId ->
        val summary = uiState.debtSummaries.firstOrNull { it.debt.id == dId }
        if (summary != null) {
            DebtDetailSheet(
                summary = summary,
                wallets = uiState.wallets,
                payments = uiState.obligationPayments.filter {
                    it.obligationType == ObligationType.DEBT && it.obligationId == dId
                },
                isPreview = uiState.isSamplePreview,
                onRecordSettlement = { id, wId, amt, note, paidAt, subId, onError ->
                    onRecordDebtSettlement(id, wId, amt, note, paidAt, subId, { inspectedDebtId = null }, onError)
                },
                onEdit = {
                    inspectedDebtId = null
                    editingDebtId = dId
                    showDebtEditor = true
                },
                onToggleArchive = { id, archived, onError ->
                    onSetDebtArchived(id, archived, { inspectedDebtId = null }, onError)
                },
                onDelete = { id, onError ->
                    onDeleteDebt(id, { inspectedDebtId = null }, onError)
                },
                onDismiss = { inspectedDebtId = null }
            )
        }
    }
}

@Composable
private fun SavingsOverviewDashboardCard(
    overview: OverallSavingsOverview
) {
    val glass = LocalPrismaticGlass.current
    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = glass.skyBlueAccent,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Protected Savings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskedBalance(overview.totalProtectedSavingsSen),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            PrismaticBadge(
                label = "${overview.activeGoalsCount} active",
                containerColor = glass.skyBlueContainer,
                contentColor = glass.onSkyBlueContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { overview.overallProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = glass.skyBlueAccent,
            trackColor = glass.glassSurfaceMuted,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Allocated to Goals", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedBalance(overview.totalAllocatedToGoalsSen), style = MaterialTheme.typography.titleSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unlinked Vaults", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedBalance(overview.unallocatedProtectedWalletSen), style = MaterialTheme.typography.titleSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Goal Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(overview.overallProgressPercentDisplay, style = MaterialTheme.typography.titleSmall, color = glass.positiveText)
            }
        }
    }
}

@Composable
private fun SavingsGoalCard(
    progress: SavingsGoalProgress,
    isPreview: Boolean,
    onInspect: () -> Unit,
    onQuickAdd: (() -> Unit)?
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val goal = progress.goal
    val tone = candyToneFor(goal.accentToken)
    val t = candyTone(tone)

    // Candy Bubble goal card: progress ring around the goal icon + percent sticker.
    CandyBubbleCard(
        tone = tone,
        decor = CandyDecor.Corner,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        onClick = onInspect,
        onClickLabel = "Inspect ${goal.name}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CandyProgressRing(fraction = progress.progressFraction, tone = tone, size = 58.dp, stroke = 7.dp) {
                Icon(
                    imageVector = SavingsIconMapper.iconFor(goal.iconKey),
                    contentDescription = null,
                    tint = t.ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.name,
                    style = typography.cardTitle,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CandyMoneyText(maskedBalance(progress.savedSen), typography.cardBalance, colors.primaryText)
                Text(
                    text = "of ${maskedBalance(goal.targetAmountSen)}",
                    style = typography.caption,
                    color = colors.secondaryText
                )
            }
            Spacer(Modifier.width(8.dp))
            when (goal.status) {
                SavingsGoalStatus.ACTIVE -> CandySticker(
                    text = if (progress.overfundedSen > 0L) "${progress.progressPercentDisplay} +" else progress.progressPercentDisplay,
                    tone = tone,
                    tilt = 5f
                )
                SavingsGoalStatus.COMPLETED -> CandySticker(text = "Completed", tone = CandyTone.Mint, tilt = 5f)
                SavingsGoalStatus.ARCHIVED -> CandyStatusBadge("Archived", CandyTone.Neutral)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusSubtext = buildString {
                if (progress.overfundedSen > 0L) {
                    append("Overfunded by ${maskedBalance(progress.overfundedSen)}")
                } else if (progress.remainingSen == 0L) {
                    append("Goal reached")
                } else {
                    append("${maskedBalance(progress.remainingSen)} left")
                }
                if (progress.suggestedMonthlySen != null && progress.suggestedMonthlySen > 0L) {
                    append(" · ${maskedBalance(progress.suggestedMonthlySen)}/mo")
                } else if (goal.targetDateEpochDay == null) {
                    append(" · Flexible deadline")
                }
            }
            Text(
                text = statusSubtext,
                style = typography.supportingInfo,
                color = t.ink,
                modifier = Modifier.weight(1f)
            )
            if (!isPreview && onQuickAdd != null && goal.status == SavingsGoalStatus.ACTIVE) {
                Spacer(Modifier.width(8.dp))
                PrismaticButton(
                    text = "Add Funds",
                    onClick = onQuickAdd,
                    variant = PrismaticButtonVariant.Secondary
                )
            }
        }
    }
}

@Composable
private fun MonthSelectorCard(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val label = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${selectedMonth.year}"
    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
            }
        }
    }
}

@Composable
private fun SmartSalaryCycleCard(
    periodMode: ReportingPeriodMode,
    activeCycle: SalaryCycle?,
    report: com.diniy.duit.domain.calculator.PeriodCashflowReport,
    onManageCycles: () -> Unit
) {
    val glass = LocalPrismaticGlass.current

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = glass.skyBlueAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (periodMode == ReportingPeriodMode.SALARY_CYCLE) {
                        activeCycle?.label ?: "No Active Salary Cycle"
                    } else {
                        report.periodLabel
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val dateSubtitle = if (periodMode == ReportingPeriodMode.SALARY_CYCLE && activeCycle != null) {
                    if (activeCycle.isOpenEnded) {
                        "Started ${DateTimeFormatters.formatEpochDay(activeCycle.startDateEpochDay)} · Active until next salary"
                    } else {
                        "${DateTimeFormatters.formatEpochDay(activeCycle.startDateEpochDay)} – ${DateTimeFormatters.formatEpochDay(activeCycle.endDateEpochDay)}"
                    }
                } else if (periodMode == ReportingPeriodMode.CALENDAR_MONTH) {
                    "${DateTimeFormatters.formatCompactEpochDay(report.startEpochDay)} – ${report.endEpochDay?.let { DateTimeFormatters.formatEpochDay(it) } ?: "Present"}"
                } else {
                    "Starts when you record Salary with 'Save & Start New Cycle'"
                }
                Text(
                    text = dateSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticButton(
                text = "Cycles",
                onClick = onManageCycles,
                variant = PrismaticButtonVariant.Ghost
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = glass.glassBorderSubtle)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Received Salary",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = maskedBalance(report.receivedSalarySen),
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.positiveText
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (periodMode == ReportingPeriodMode.SALARY_CYCLE) "Cycle Spending" else "Month Spending",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = maskedBalance(report.expenseSen),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DailySalesSummaryCard(
    selectedMonth: YearMonth,
    estimate: CompanyCommissionEstimate,
    loggedDaysCount: Int,
    onOpenDailyTracker: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val targetSen = estimate.ruleVersion.minMonthlySalesSen.coerceAtLeast(1L)
    val progress = ((estimate.monthlySalesSen.coerceAtMost(targetSen) * 100L) / targetSen).toInt() / 100f

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = glass.aquaAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Sales & GP Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$loggedDaysCount day(s) logged in ${selectedMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${selectedMonth.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticBadge(
                label = if (estimate.isSalesThresholdMet) "Eligible" else "Below min",
                containerColor = if (estimate.isSalesThresholdMet) glass.aquaContainer else glass.peachContainer,
                contentColor = if (estimate.isSalesThresholdMet) glass.onAquaContainer else glass.onPeachContainer
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Monthly Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedAmount(estimate.monthlySalesSen), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Monthly GP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedAmount(estimate.monthlyGrossProfitSen), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("GP Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(estimate.gpMarginPercentDisplay, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(glass.glassSurfaceMuted)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (estimate.isSalesThresholdMet) glass.aquaAccent else glass.peachAccent)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Minimum target: ${maskedBalance(estimate.ruleVersion.minMonthlySalesSen)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        PrismaticButton(
            text = "Log or Edit Daily Sales",
            onClick = onOpenDailyTracker,
            variant = PrismaticButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompanyCommissionMemoCard(
    estimate: CompanyCommissionEstimate,
    actualGrossSen: Long,
    actualNetSen: Long,
    deductionsEntered: Boolean,
    epfSen: Long,
    socsoSen: Long,
    eisSen: Long,
    onEditRules: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val differenceSen = actualGrossSen - estimate.estimatedGrossCommissionSen

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = glass.lavenderAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Company Commission Estimator",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = estimate.ruleVersion.ruleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticButton(
                text = "Rules",
                onClick = onEditRules,
                variant = PrismaticButtonVariant.Ghost
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Estimated Gross Commission",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = maskedBalance(estimate.estimatedGrossCommissionSen),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("Salary Cost ${maskedBalance(estimate.salaryCostSen)}")
                append(" · Baseline ${maskedBalance(estimate.baselineSen)}")
                if (estimate.appliedRateBps > 0) {
                    append(" · Rate ${MyrFormatter.formatBpsAsPercent(estimate.appliedRateBps)}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (estimate.isNegativeBaselineAssumptionApplied) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Assumption: negative baseline is estimated as RM 0.00.",
                style = MaterialTheme.typography.bodySmall,
                color = glass.cautionText
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = glass.glassBorderSubtle)
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Estimated vs Actual (Company)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Actual Gross", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (actualGrossSen > 0L) maskedBalance(actualGrossSen) else "Not received",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Actual Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = when {
                        actualNetSen <= 0L -> "Unavailable"
                        !deductionsEntered -> "${maskedBalance(actualNetSen)} (no ded.)"
                        else -> maskedBalance(actualNetSen)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Difference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (actualGrossSen > 0L) maskedBalance(differenceSen, includePlusSign = true) else "—",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        if (deductionsEntered) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Payslip deductions · EPF ${maskedBalance(epfSen)} · SOCSO ${maskedBalance(socsoSen)} · EIS ${maskedBalance(eisSen)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Net deductions (EPF/SOCSO/EIS) unavailable until entered from payslip.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MultiSourceCommissionCard(
    record: CommissionRecord,
    isPreview: Boolean,
    onEdit: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val (statusBg, statusFg) = when (record.status) {
        CommissionStatus.UNKNOWN -> glass.glassSurfaceMuted to MaterialTheme.colorScheme.onSurfaceVariant
        CommissionStatus.PENDING -> glass.peachContainer to glass.onPeachContainer
        CommissionStatus.PARTIALLY_RECEIVED -> glass.skyBlueContainer to glass.onSkyBlueContainer
        CommissionStatus.RECEIVED -> glass.aquaContainer to glass.onAquaContainer
    }

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(record.sourceLabel)
                        if (record.yearMonth.isNotBlank()) append(" · ${record.yearMonth}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticBadge(
                label = record.status.displayName,
                containerColor = statusBg,
                contentColor = statusFg
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Expected/Est.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val expected = record.estimatedGrossSen ?: record.amountSen
                Text(
                    text = if (expected > 0L) maskedBalance(expected) else "Variable",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Actual Gross", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = maskedBalance(record.actualGrossSen),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Net Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = maskedBalance(record.receivedNetSen),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (record.receivedNetSen > 0L) glass.positiveText else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (record.adjustmentNote.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Note: ${record.adjustmentNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isPreview) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrismaticButton(
                    text = "Edit",
                    onClick = onEdit,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                PrismaticButton(
                    text = "Record Payment",
                    onClick = onRecordPayment,
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}

/** Phase 7B: sub-sections of the Bills & Commitments tab. */
private enum class BillsSection(val label: String) {
    BILLS("Bills"),
    PAYLATER("PayLater"),
    DEBTS("Debts"),
    INCOME("Salary & commission")
}
