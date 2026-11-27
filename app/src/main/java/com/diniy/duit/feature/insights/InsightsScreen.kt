package com.diniy.duit.feature.insights

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.R
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.SamplePreviewBanner
import com.diniy.duit.core.ui.components.SpatialBentoMetricItem
import com.diniy.duit.core.ui.components.SpatialBentoSummaryRow
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.SpatialGlassSection
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.CompanyCommissionEngine
import com.diniy.duit.domain.calculator.InsightsPeriodPreset
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.DailySalesRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

enum class InsightsSectionTab(val label: String) {
    OVERVIEW("Overview"),
    SPENDING("Spending"),
    INCOME("Income"),
    COMMITMENTS("Commitments")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    uiState: InsightsUiState,
    /** False hides salary-and-commission cards; every other insight is unchanged. */
    commissionEnabled: Boolean = true,
    contentPadding: PaddingValues,
    onShiftMonth: (Long) -> Unit,
    onSelectPeriodPreset: (InsightsPeriodPreset) -> Unit,
    onSetCustomDateRange: (startEpochDay: Long, endEpochDay: Long) -> Unit,
    onSelectTrendHorizon: (Int) -> Unit,
    onInspectCategory: (String) -> Unit,
    onDismissCategorySheet: () -> Unit,
    onEnablePreview: () -> Unit,
    onExitPreview: () -> Unit,
    onAddTransaction: () -> Unit = {},
    personality: com.diniy.duit.feature.personality.MoneyPersonalityBinding? = null,
    littleWins: com.diniy.duit.feature.wins.LittleWinsBinding? = null,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing

    var selectedTab by rememberSaveable { mutableStateOf(InsightsSectionTab.OVERVIEW) }
    var showPersonality by rememberSaveable { mutableStateOf(false) }
    var showLittleWins by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(showPersonality) {
        if (showPersonality) {
            com.diniy.duit.analytics.UsageAnalytics.logFeatureUsed(com.diniy.duit.analytics.AnalyticsFeature.MONEY_PERSONALITY)
        }
    }
    androidx.compose.runtime.LaunchedEffect(showLittleWins) {
        if (showLittleWins) {
            com.diniy.duit.analytics.UsageAnalytics.logFeatureUsed(com.diniy.duit.analytics.AnalyticsFeature.LITTLE_WINS_CLUB)
        }
    }

    androidx.activity.compose.BackHandler(
        enabled = !showPersonality && !showLittleWins && selectedTab != InsightsSectionTab.OVERVIEW
    ) {
        selectedTab = InsightsSectionTab.OVERVIEW
    }

    if (showPersonality && personality != null) {
        com.diniy.duit.feature.personality.MoneyPersonalityScreen(
            state = personality.state,
            onClose = { showPersonality = false },
            onPreviousMonth = personality.onPreviousMonth,
            onNextMonth = personality.onNextMonth,
            onRetry = personality.onRetry
        )
    }
    if (showLittleWins && littleWins != null) {
        com.diniy.duit.feature.wins.LittleWinsClubScreen(
            state = littleWins.state,
            onSelectCategory = littleWins.onSelectCategory,
            onMarkCelebrated = littleWins.onMarkCelebrated,
            onClose = { showLittleWins = false }
        )
    }
    var showCustomRangePicker by rememberSaveable { mutableStateOf(false) }
    var showPeriodSheet by rememberSaveable { mutableStateOf(false) }
    var showCommissionHistory by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.sectionSpacing)
    ) {
        // 1. Spatial Header
        item(key = "insights_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Insights",
                        style = typography.screenHeading,
                        color = colors.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Understand your money",
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                }
                // Phase 11H: period presets moved into this filter sheet (shared Mint Bubble button)
                com.diniy.duit.core.ui.components.CandyMintIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Choose period",
                    onClick = { showPeriodSheet = true },
                    pressedTilt = 0f
                )
            }
        }

        item(key = "preview_banner", contentType = "banner") {
            AnimatedVisibility(visible = uiState.isSamplePreview) {
                SamplePreviewBanner(onExitPreview = onExitPreview)
            }
        }

        if (uiState.errorMessage != null) {
            item(key = "error", contentType = "error") {
                PrismaticErrorBanner(message = uiState.errorMessage)
            }
        }

        if (uiState.isLoading) {
            item(key = "loading", contentType = "loading") { PrismaticLoadingCard() }
        } else {
            // 2. Compact Period Navigator + Spatial Preset Pills
            item(key = "period_switcher", contentType = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Standard,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onShiftMonth(-1) }) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronLeft,
                                    contentDescription = "Previous month",
                                    tint = colors.primaryText
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${uiState.selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${uiState.selectedMonth.year}",
                                    style = typography.cardTitle,
                                    color = colors.primaryText
                                )
                                val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
                                    com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
                                // Active period type stays visible (plus dates for a custom range)
                                Text(
                                    text = if (uiState.periodPreset == InsightsPeriodPreset.CUSTOM_RANGE &&
                                        uiState.customStartEpochDay != null &&
                                        uiState.customEndEpochDay != null
                                    ) {
                                        "${uiState.periodPreset.label} · ${DateTimeFormatters.formatEpochDay(uiState.customStartEpochDay)} – ${DateTimeFormatters.formatEpochDay(uiState.customEndEpochDay)}"
                                    } else {
                                        uiState.periodPreset.label
                                    },
                                    style = typography.caption,
                                    color = if (isLiquidGlass) colors.primaryAction else colors.coral
                                )
                            }
                            IconButton(onClick = { onShiftMonth(1) }) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Next month",
                                    tint = colors.primaryText
                                )
                            }
                        }
                    }

                }
            }

            // 3. Organized 4-Section Spatial Tab Bar: Overview | Spending | Income | Commitments
            item(key = "insights_section_tabs", contentType = "tabs") {
                InsightsSectionPillBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }

            when (selectedTab) {
                InsightsSectionTab.OVERVIEW -> {
                    item(key = "financial_overview", contentType = "overview") {
                        FinancialOverviewHeroCard(
                            overview = uiState.overview,
                            isSamplePreview = uiState.isSamplePreview
                        )
                    }

                    if (personality != null || littleWins != null) {
                        item(key = "habits_milestones_header", contentType = "section_header") {
                            com.diniy.duit.core.ui.components.CandySectionHeader(
                                title = "Habits & Wins"
                            )
                        }
                        if (personality != null) item(key = "money_personality", contentType = "personality") {
                            com.diniy.duit.feature.personality.MoneyPersonalityTeaserCard(personality.state, onOpen = { showPersonality = true })
                        }
                        if (littleWins != null) item(key = "little_wins_club", contentType = "little_wins") {
                            com.diniy.duit.feature.wins.LittleWinsTeaserCard(littleWins.state, onOpen = { showLittleWins = true })
                        }
                        item(key = "trends_analytics_header", contentType = "section_header") {
                            com.diniy.duit.core.ui.components.CandySectionHeader(
                                title = "Trends & Breakdown"
                            )
                        }
                    }

                    item(key = "income_vs_expenses", contentType = "cashflow_chart") {
                        IncomeVsExpensesChartCard(
                            monthlyTrends = uiState.monthlyTrends,
                            horizonMonths = uiState.trendHorizonMonths,
                            onSelectHorizon = onSelectTrendHorizon
                        )
                    }

                    item(key = "savings_analytics", contentType = "savings") {
                        SavingsAnalyticsCard(savingsAnalytics = uiState.savingsAnalytics)
                    }

                    item(key = "smart_insights", contentType = "smart_insights") {
                        SmartInsightsCard(insights = uiState.smartInsights)
                    }

                    // Hidden commission data doesn't count when Commission Tracking is off.
                    if (((!commissionEnabled && com.diniy.duit.BuildConfig.PUBLIC_EDITION) ||
                            (uiState.commissions.isEmpty() && uiState.dailySales.isEmpty())) &&
                        uiState.overview.totalIncomeSen == 0L &&
                        uiState.overview.totalExpenseSen == 0L
                    ) {
                        item(key = "empty_records", contentType = "empty") {
                            PrismaticEmptyStateCard(
                                iconRes = R.drawable.ic_solar_insights,
                                title = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Nothing to chart for this period" else "Insights build from real history",
                                message = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                                    "Insights appear once you record income or expenses in ${uiState.periodCashflow.periodLabel}. Try another period with the arrows above, or add a transaction."
                                } else {
                                    "Charts and breakdowns reflect your recorded transactions, daily sales, and commissions."
                                },
                                actionLabel = when {
                                    uiState.isSamplePreview -> null
                                    com.diniy.duit.BuildConfig.PUBLIC_EDITION -> "Add Transaction"
                                    else -> "View Sample"
                                },
                                onActionClick = when {
                                    uiState.isSamplePreview -> null
                                    com.diniy.duit.BuildConfig.PUBLIC_EDITION -> onAddTransaction
                                    else -> onEnablePreview
                                },
                                secondaryActionLabel = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION && !uiState.isSamplePreview) "View Sample" else null,
                                onSecondaryActionClick = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION && !uiState.isSamplePreview) onEnablePreview else null
                            )
                        }
                    }
                }

                InsightsSectionTab.SPENDING -> {
                    item(key = "spending_bento", contentType = "spending_bento") {
                        SpatialBentoSummaryRow(
                            leftMetric = SpatialBentoMetricItem(
                                label = "Period Expenses",
                                amountSen = uiState.overview.totalExpenseSen,
                                caption = uiState.overview.resolvedPeriod.periodLabel,
                                accentColor = colors.expense,
                                isBalanceMask = true
                            ),
                            rightMetric = SpatialBentoMetricItem(
                                label = "Budget Remaining",
                                amountSen = uiState.budgetOverview.effectiveRemainingSen,
                                caption = if (uiState.budgetOverview.effectiveLimitSen > 0L) {
                                    "${uiState.budgetOverview.overallUsedPercentDisplay} used"
                                } else {
                                    "No budget cap set"
                                },
                                accentColor = colors.lavender,
                                isBalanceMask = true
                            )
                        )
                    }

                    item(key = "spending_breakdown", contentType = "spending") {
                        SpendingBreakdownDonutCard(
                            periodLabel = uiState.overview.resolvedPeriod.periodLabel,
                            totalExpenseSen = uiState.overview.totalExpenseSen,
                            categories = uiState.categoryBreakdown,
                            onInspectCategory = onInspectCategory
                        )
                    }

                    item(key = "budget_performance", contentType = "budgets") {
                        BudgetPerformanceCard(
                            overview = uiState.budgetOverview,
                            history = uiState.budgetHistory
                        )
                    }

                    item(key = "spending_trends", contentType = "cashflow_chart") {
                        IncomeVsExpensesChartCard(
                            monthlyTrends = uiState.monthlyTrends,
                            horizonMonths = uiState.trendHorizonMonths,
                            onSelectHorizon = onSelectTrendHorizon
                        )
                    }
                }

                InsightsSectionTab.INCOME -> {
                    if (commissionEnabled) {
                    item(key = "income_bento", contentType = "income_bento") {
                        SpatialBentoSummaryRow(
                            leftMetric = SpatialBentoMetricItem(
                                label = "Cycle Salary",
                                amountSen = uiState.salaryCycleIncomeSen,
                                caption = uiState.periodCashflow.periodLabel,
                                accentColor = colors.income,
                                isBalanceMask = true
                            ),
                            rightMetric = SpatialBentoMetricItem(
                                label = "Pending Commissions",
                                amountSen = uiState.pendingCommissionsSen,
                                caption = "Excluded until received",
                                accentColor = colors.lavender,
                                isBalanceMask = true
                            )
                        )
                    }

                    item(key = "commission_verification", contentType = "commission_perf") {
                        SalaryAndCommissionInsightsCard(
                            uiState = uiState,
                            showHistory = showCommissionHistory,
                            onToggleHistory = { showCommissionHistory = !showCommissionHistory }
                        )
                    }

                    item(key = "monthly_sales_gp", contentType = "monthly_sales") {
                        SpatialSalesAndGrossProfitCard(uiState = uiState)
                    }

                    if (uiState.sourceSummaries.isNotEmpty()) {
                        item(key = "source_breakdown", contentType = "sources") {
                            SpatialCommissionSourcesGroupCard(uiState = uiState)
                        }
                    }

                    if (uiState.dailySales.isNotEmpty()) {
                        item(key = "daily_sales_group", contentType = "sales_group") {
                            SpatialDailySalesGroupSection(
                                records = uiState.dailySales.take(7),
                                isSamplePreview = uiState.isSamplePreview
                            )
                        }
                    }
                    }
                }

                InsightsSectionTab.COMMITMENTS -> {
                    item(key = "commitments_bento", contentType = "commitments_bento") {
                        SpatialBentoSummaryRow(
                            leftMetric = SpatialBentoMetricItem(
                                label = "Cycle Reserved",
                                amountSen = uiState.obligationsOverview.currentCycleReservedSen,
                                caption = "${uiState.obligationsOverview.currentCycleReservedCount} due in cycle",
                                accentColor = colors.peach,
                                isBalanceMask = true
                            ),
                            rightMetric = SpatialBentoMetricItem(
                                label = "Future Cycle Bills",
                                amountSen = uiState.obligationsOverview.upcomingFutureBillsSen,
                                caption = "${uiState.obligationsOverview.upcomingFutureBillsCount} next cycle · Not reserved",
                                accentColor = colors.skyBlue,
                                isBalanceMask = true
                            )
                        )
                    }

                    item(key = "obligations_analytics", contentType = "obligations") {
                        CommitmentsAndDebtAnalyticsCard(overview = uiState.obligationsOverview)
                    }
                }
            }
        }
    }

    uiState.inspectedCategoryName?.let { categoryName ->
        CategoryTransactionsSheet(
            categoryName = categoryName,
            periodLabel = uiState.overview.resolvedPeriod.periodLabel,
            transactions = uiState.inspectedCategoryTransactions,
            walletNamesById = uiState.walletNamesById,
            onDismiss = onDismissCategorySheet
        )
    }

    if (showPeriodSheet) {
        com.diniy.duit.core.ui.components.PrismaticModalBottomSheet(
            title = "Period",
            subtitle = "Choose what Insights covers",
            onDismissRequest = { showPeriodSheet = false }
        ) {
            InsightsPeriodPreset.entries.forEach { preset ->
                val selected = uiState.periodPreset == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) colors.selectedStateOverlay else Color.Transparent)
                        .clickable(role = Role.RadioButton) {
                            showPeriodSheet = false
                            if (preset == InsightsPeriodPreset.CUSTOM_RANGE) {
                                showCustomRangePicker = true // existing picker + validation
                            } else {
                                onSelectPeriodPreset(preset)
                            }
                        }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (preset == InsightsPeriodPreset.CUSTOM_RANGE) Icons.Rounded.DateRange else Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = if (selected) colors.primaryAction else colors.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        preset.label,
                        style = typography.transactionDescription,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primaryAction)
                    }
                }
            }
        }
    }

    if (showCustomRangePicker) {
        val initStart = (uiState.customStartEpochDay ?: LocalDate.now().withDayOfMonth(1).toEpochDay())
        val initEnd = (uiState.customEndEpochDay ?: LocalDate.now().toEpochDay())
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.ofEpochDay(initStart).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = LocalDate.ofEpochDay(initEnd).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val s = Instant.ofEpochMilli(rangeState.selectedStartDateMillis!!)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        val e = Instant.ofEpochMilli(rangeState.selectedEndDateMillis ?: rangeState.selectedStartDateMillis!!)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        onSetCustomDateRange(s, e)
                        showCustomRangePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState)
        }
    }
}

@Composable
private fun InsightsSectionPillBar(
    selectedTab: InsightsSectionTab,
    onSelectTab: (InsightsSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val geometry = SpatialTheme.geometry
    val haptic = com.diniy.duit.core.ui.components.rememberDuitHaptic()
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val isLiquidLow = com.diniy.duit.core.ui.theme.LocalGlassIntensity.current ==
        com.diniy.duit.domain.model.GlassIntensity.SUBTLE
    val trackShape = RoundedCornerShape(if (isLiquidGlass) 16.dp else geometry.radiusPill)
    val itemShape = RoundedCornerShape(if (isLiquidGlass) 12.dp else geometry.radiusPill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(if (isLiquidGlass) colors.baseSurface else if (colors.isDark) colors.glassSurfaceSubtle else colors.baseSurface)
            .then(if (isLiquidGlass) Modifier.border(0.75.dp, colors.divider, trackShape) else Modifier)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        InsightsSectionTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val bgColor by animateColorAsState(
                targetValue = when {
                    selected && isLiquidGlass -> colors.elevatedSurface
                    selected -> if (colors.isDark) colors.floatingNavActivePill else colors.glassSurfaceElevated
                    else -> Color.Transparent
                },
                label = "insights_tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colors.primaryAction else colors.secondaryText,
                label = "insights_tab_fg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = if (isLiquidGlass) 44.dp else 42.dp)
                    .then(
                        if (selected && (!isLiquidGlass || !isLiquidLow)) {
                            Modifier.shadow(
                                elevation = if (isLiquidGlass) 2.dp else 3.dp,
                                shape = itemShape,
                                ambientColor = colors.glassShadowAmbient,
                                spotColor = colors.glassShadowSpot
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(itemShape)
                    .background(bgColor)
                    .then(
                        if (selected && isLiquidGlass) {
                            Modifier.border(0.75.dp, colors.divider, itemShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.Tab) {
                        if (!selected) haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onSelectTab(tab)
                    }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = typography.caption,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpatialSalesAndGrossProfitCard(uiState: InsightsUiState) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        accentTint = colors.peach
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sales Performance & GP",
                style = typography.cardTitle,
                color = colors.primaryText
            )
            SpatialGlassPill(
                label = "Margin ${uiState.commissionEstimate.gpMarginPercentDisplay}",
                selected = true,
                accentColor = colors.peach,
                containerColor = colors.warningContainer,
                contentColor = colors.onWarningContainer
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Sales", style = typography.caption, color = colors.secondaryText)
                Text(
                    text = maskedAmount(uiState.commissionEstimate.monthlySalesSen),
                    style = typography.cardBalance,
                    color = colors.primaryText
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Gross Profit", style = typography.caption, color = colors.secondaryText)
                Text(
                    text = maskedAmount(uiState.commissionEstimate.monthlyGrossProfitSen),
                    style = typography.cardBalance,
                    color = colors.primaryText
                )
            }
        }
    }
}

@Composable
private fun SpatialCommissionSourcesGroupCard(uiState: InsightsUiState) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard
    ) {
        Text(
            text = "Commission by Source",
            style = typography.cardTitle,
            color = colors.primaryText
        )
        Spacer(Modifier.height(10.dp))
        uiState.sourceSummaries.forEachIndexed { idx, src ->
            if (idx > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colors.divider
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(src.sourceName, style = typography.transactionDescription, color = colors.primaryText)
                    Text(
                        text = "${src.count} entry(s) · Gross ${maskedAmount(src.actualGrossSen)}",
                        style = typography.caption,
                        color = colors.secondaryText
                    )
                }
                Text(
                    text = maskedAmount(src.receivedNetSen),
                    style = typography.transactionAmount,
                    color = colors.income
                )
            }
        }
    }
}

@Composable
private fun SpatialDailySalesGroupSection(
    records: List<DailySalesRecord>,
    isSamplePreview: Boolean
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassSection(
        title = if (isSamplePreview) "Daily Sales · Sample" else "Recent Daily Sales",
        subtitle = "Gross sales, profit margin, and GP recorded per day"
    ) {
        SpatialGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            records.forEachIndexed { idx, record ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateTimeFormatters.formatEpochDay(record.recordDateEpochDay),
                            style = typography.transactionDescription,
                            color = colors.primaryText
                        )
                        Text(
                            text = "Sales ${maskedAmount(record.grossSalesSen)} · Margin ${CompanyCommissionEngine.formatGpMargin(record.grossSalesSen, record.grossProfitSen)}",
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                    Text(
                        text = "GP ${maskedAmount(record.grossProfitSen)}",
                        style = typography.transactionAmount,
                        color = colors.primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun SalaryAndCommissionInsightsCard(
    uiState: InsightsUiState,
    showHistory: Boolean,
    onToggleHistory: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        accentTint = colors.lavender
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Salary & Commission Insights",
                    style = typography.cardTitle,
                    color = colors.primaryText
                )
                Text(
                    text = "${uiState.periodCashflow.periodLabel} · Cycle salary ${maskedBalance(uiState.salaryCycleIncomeSen)}",
                    style = typography.caption,
                    color = colors.secondaryText
                )
            }
            SpatialGlassPill(
                label = "Pending: ${maskedBalance(uiState.pendingCommissionsSen)}",
                selected = true,
                accentColor = colors.lavender
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Est. Gross (Memo)", style = typography.caption, color = colors.secondaryText)
                Text(
                    text = maskedAmount(uiState.commissionEstimate.estimatedGrossCommissionSen),
                    style = typography.transactionAmount,
                    color = colors.primaryText
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Actual Gross", style = typography.caption, color = colors.secondaryText)
                Text(
                    text = if (uiState.actualCompanyGrossSen > 0L) maskedAmount(uiState.actualCompanyGrossSen) else "—",
                    style = typography.transactionAmount,
                    color = colors.primaryText
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Actual Net", style = typography.caption, color = colors.secondaryText)
                Text(
                    text = when {
                        uiState.actualCompanyNetSen <= 0L -> "Unavailable"
                        !uiState.deductionsAvailable -> "${maskedAmount(uiState.actualCompanyNetSen)}*"
                        else -> maskedAmount(uiState.actualCompanyNetSen)
                    },
                    style = typography.transactionAmount,
                    color = colors.primaryText
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (uiState.deductionsAvailable) {
                "Net reflects payslip EPF, SOCSO & EIS deductions. Pending commissions are excluded from available wallets."
            } else {
                "Pending commissions are excluded from available wallet balances until received."
            },
            style = typography.supportingInfo,
            color = colors.secondaryText
        )

        if (uiState.commissions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PrismaticButton(
                text = if (showHistory) "Hide Commission History (${uiState.commissions.size})"
                else "Show Commission History (${uiState.commissions.size})",
                onClick = onToggleHistory,
                variant = PrismaticButtonVariant.Ghost
            )
            AnimatedVisibility(visible = showHistory) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.commissions.take(8).forEach { record ->
                        CommissionHistoryRow(record = record)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommissionHistoryRow(record: CommissionRecord) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = typography.transactionDescription,
                color = colors.primaryText
            )
            Text(
                text = "${record.sourceLabel.ifBlank { "Commission" }} · ${DateTimeFormatters.formatEpochDay(record.earnedDateEpochDay)} · ${record.status.displayName}",
                style = typography.caption,
                color = colors.secondaryText
            )
        }
        Text(
            text = when (record.status) {
                CommissionStatus.RECEIVED -> maskedAmount(record.receivedNetSen)
                CommissionStatus.PARTIALLY_RECEIVED -> "${maskedAmount(record.receivedNetSen)} / ${maskedAmount(record.amountSen)}"
                CommissionStatus.PENDING -> maskedAmount(record.amountSen)
                else -> maskedAmount(record.amountSen)
            },
            style = typography.transactionAmount,
            color = if (record.status == CommissionStatus.PENDING) colors.secondaryText else colors.income
        )
    }
}
