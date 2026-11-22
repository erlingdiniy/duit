package com.diniy.duit.feature.budgets

import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyDecor
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandySticker
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.components.candyToneFor
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.BudgetPeriodOverview
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.BudgetWarningLevel
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.feature.savings.SavingsIconMapper
import com.diniy.duit.feature.transactions.CategoryIconMapper
import java.time.YearMonth

enum class PlanningSectionTab(val label: String) {
    GOALS("Savings"),
    BUDGETS("Budgets"),
    COMMITMENTS("Bills & Debt")
}

@Composable
fun PlanningSectionPillBar(
    selectedTab: PlanningSectionTab,
    onSelectTab: (PlanningSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val geometry = com.diniy.duit.core.ui.theme.SpatialTheme.geometry
    val haptic = com.diniy.duit.core.ui.components.rememberDuitHaptic()
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val isLiquidLow = com.diniy.duit.core.ui.theme.LocalGlassIntensity.current ==
        com.diniy.duit.domain.model.GlassIntensity.SUBTLE
    val scrollable = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
    val trackShape = RoundedCornerShape(if (isLiquidGlass) 16.dp else geometry.radiusPill)
    val itemShape = RoundedCornerShape(if (isLiquidGlass) 12.dp else geometry.radiusPill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(if (isLiquidGlass) colors.baseSurface else if (colors.isDark) colors.glassSurfaceSubtle else colors.baseSurface)
            .then(if (isLiquidGlass) Modifier.border(0.75.dp, colors.divider, trackShape) else Modifier)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlanningSectionTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val bgColor by animateColorAsState(
                targetValue = when {
                    selected && isLiquidGlass -> colors.elevatedSurface
                    selected -> if (colors.isDark) colors.floatingNavActivePill else colors.glassSurfaceElevated
                    else -> Color.Transparent
                },
                label = "planning_pill_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colors.primaryAction else colors.secondaryText,
                label = "planning_pill_fg"
            )
            Box(
                modifier = Modifier
                    .then(if (scrollable) Modifier else Modifier.weight(1f))
                    .heightIn(min = if (isLiquidGlass) 44.dp else 44.dp)
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
                    .padding(horizontal = if (scrollable) 14.dp else 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = typography.buttonLabel,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BudgetPeriodNavigator(
    periodType: BudgetPeriodType,
    selectedMonth: YearMonth,
    selectedCycle: SalaryCycle?,
    salaryCycles: List<SalaryCycle>,
    onSelectPeriodType: (BudgetPeriodType) -> Unit,
    onShiftPeriod: (Int) -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val sortedCycles = salaryCycles.sortedWith(
        compareBy<SalaryCycle> { it.startDateEpochDay }.thenBy { it.id }
    )
    val cycleIdx = selectedCycle?.let { sel -> sortedCycles.indexOfFirst { it.id == sel.id } } ?: -1
    val canPrevCycle = periodType == BudgetPeriodType.MONTHLY || cycleIdx > 0
    val canNextCycle = periodType == BudgetPeriodType.MONTHLY || (cycleIdx in 0 until sortedCycles.lastIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BudgetPeriodType.entries.forEach { mode ->
                FilterChip(
                    selected = periodType == mode,
                    onClick = { onSelectPeriodType(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                )
            }
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Standard,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onShiftPeriod(-1) },
                    enabled = canPrevCycle
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous budget period"
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (periodType == BudgetPeriodType.MONTHLY) {
                            BudgetCalculator.formatYearMonthLabel(selectedMonth.toString())
                        } else {
                            selectedCycle?.label ?: "No salary cycle created"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (periodType == BudgetPeriodType.SALARY_CYCLE && selectedCycle != null) {
                        val range = if (selectedCycle.isOpenEnded) {
                            "Since ${DateTimeFormatters.formatCompactEpochDay(selectedCycle.startDateEpochDay)}"
                        } else {
                            "${DateTimeFormatters.formatCompactEpochDay(selectedCycle.startDateEpochDay)} - " +
                                DateTimeFormatters.formatCompactEpochDay(selectedCycle.endDateEpochDay)
                        }
                        Text(
                            text = range,
                            style = MaterialTheme.typography.labelSmall,
                            color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.readableOn(glass.aquaAccent)
                        )
                    }
                }
                IconButton(
                    onClick = { onShiftPeriod(1) },
                    enabled = canNextCycle
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next budget period"
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetWarningCalloutCard(
    overview: BudgetPeriodOverview
) {
    val glass = LocalPrismaticGlass.current
    val hasExceeded = overview.exceededCount > 0
    val tint = if (hasExceeded) MaterialTheme.colorScheme.error else glass.peachAccent
    val parts = buildList {
        if (overview.exceededCount > 0) {
            add("${overview.exceededCount} over limit")
        }
        if (overview.warningCount100 > 0) {
            add("${overview.warningCount100} at 100%")
        }
        if (overview.warningCount80 > 0) {
            add("${overview.warningCount80} at 80%+")
        }
    }
    if (parts.isEmpty()) return

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = tint,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (hasExceeded) MaterialTheme.colorScheme.errorContainer else glass.peachContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (hasExceeded) MaterialTheme.colorScheme.onErrorContainer else glass.onPeachContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasExceeded) "Budget limit exceeded" else "Budget threshold alert",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (hasExceeded) MaterialTheme.colorScheme.error else glass.cautionText
                )
                Text(
                    text = "${parts.joinToString(" · ")} in ${overview.periodLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BudgetOverviewCard(
    overview: BudgetPeriodOverview
) {
    val glass = LocalPrismaticGlass.current
    val hasBudgets = overview.effectiveLimitSen > 0L
    val isOver = overview.effectiveOverspentSen > 0L

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = if (isOver) MaterialTheme.colorScheme.error else glass.lavenderAccent,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${overview.periodLabel} Spending Limits",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (hasBudgets) {
                        "${maskedBalance(overview.effectiveCoveredSpentSen)} / ${maskedBalance(overview.effectiveLimitSen)}"
                    } else {
                        maskedBalance(overview.totalPeriodExpenseSen)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            PrismaticBadge(
                label = if (hasBudgets) overview.overallUsedPercentDisplay else "No limit",
                containerColor = if (isOver) MaterialTheme.colorScheme.errorContainer else glass.lavenderContainer,
                contentColor = if (isOver) MaterialTheme.colorScheme.onErrorContainer else glass.onLavenderContainer
            )
        }

        if (hasBudgets) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { overview.overallProgressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (isOver) MaterialTheme.colorScheme.error else glass.lavenderAccent,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Period Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedBalance(overview.totalPeriodExpenseSen), style = MaterialTheme.typography.titleSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isOver) "Overspent" else "Remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (hasBudgets) {
                        if (isOver) maskedBalance(overview.effectiveOverspentSen)
                        else maskedBalance(overview.effectiveRemainingSen)
                    } else {
                        "Set a budget"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isOver) MaterialTheme.colorScheme.error else glass.positiveText
                )
            }
        }

        if (overview.overallBudget != null && overview.categoryBudgets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (overview.categoryLimitsExceedOverall) {
                    "Category limits total ${maskedBalance(overview.sumCategoryLimitsSen)} (above ${maskedBalance(overview.overallBudget.limitSen)} overall cap) · Expenses counted once"
                } else {
                    "Category limits total ${maskedBalance(overview.sumCategoryLimitsSen)} of ${maskedBalance(overview.overallBudget.limitSen)} overall limit · No double counting"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overview.categoryLimitsExceedOverall) glass.cautionText else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BudgetProgressCard(
    progress: BudgetProgress,
    categories: List<Category>,
    showPeriodTag: Boolean,
    onClick: () -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val type = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val reduced = com.diniy.duit.core.ui.components.rememberReducedMotionEnabled()
    val budget = progress.budget
    val category = budget.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    // Healthy = mint, approaching = sunny, at/over limit = coral. Thresholds come from progress.warningLevel.
    val stateTone = when (progress.warningLevel) {
        BudgetWarningLevel.SAFE -> CandyTone.Mint
        BudgetWarningLevel.APPROACHING_80 -> CandyTone.Sunny
        BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.EXCEEDED -> CandyTone.Coral
    }
    val st = candyTone(stateTone)
    val fill by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.progressFraction.coerceIn(0f, 1f),
        animationSpec = com.diniy.duit.core.ui.components.prismaticSpring(
            reduced,
            com.diniy.duit.core.ui.theme.SpatialTheme.motion.spatialDampingRatio,
            androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "budget_fill"
    )
    val track = colors.glassSurfaceElevated
    val marker = colors.sticker.copy(alpha = 0.35f)

    CandyBubbleCard(
        tone = stateTone,
        decor = if (budget.isArchived) CandyDecor.None else CandyDecor.Corner,
        containerColor = if (budget.isArchived) colors.glassSurfaceSubtle else null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        onClick = onClick,
        onClickLabel = "Inspect ${budget.name} budget"
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(
                icon = if (budget.scope == BudgetScope.OVERALL) Icons.Rounded.AccountBalanceWallet
                else CategoryIconMapper.iconFor(category?.iconKey, TransactionType.EXPENSE),
                tone = candyToneFor(budget.accentToken),
                size = 42.dp,
                tilt = -6f,
                solid = true
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.name, style = type.cardTitle, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = buildString {
                    append("${maskedAmount(progress.spentSen)} of ${maskedAmount(progress.limitSen)}")
                    if (showPeriodTag) append(" · ${progress.periodLabel}")
                }
                Text(meta, style = type.caption, color = colors.secondaryText, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            val pct = if (budget.isArchived) "Archived · ${progress.usedPercentDisplay}" else progress.usedPercentDisplay
            if (progress.warningLevel == BudgetWarningLevel.SAFE || budget.isArchived) {
                CandyStatusBadge(pct, stateTone)
            } else {
                CandySticker(pct, stateTone, tilt = 5f)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Candy tube gauge with an 80% warning notch
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = r)
            if (fill > 0f) {
                drawRoundRect(st.fill, size = Size((size.width * fill).coerceAtLeast(size.height), size.height), cornerRadius = r)
            }
            val x = size.width * 0.8f
            drawLine(marker, Offset(x, 3.dp.toPx()), Offset(x, size.height - 3.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (progress.warningLevel) {
                    BudgetWarningLevel.EXCEEDED -> "Over budget by ${maskedAmount(progress.overspentSen)}"
                    BudgetWarningLevel.AT_LIMIT_100 -> "100% reached · ${maskedAmount(0L)} left"
                    BudgetWarningLevel.APPROACHING_80 -> "${maskedAmount(progress.remainingSen)} left · 80%+ warning"
                    BudgetWarningLevel.SAFE -> "${maskedAmount(progress.remainingSen)} left"
                },
                style = type.supportingInfo,
                color = when (progress.warningLevel) {
                    BudgetWarningLevel.EXCEEDED -> colors.error
                    BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.APPROACHING_80 -> colors.warning
                    BudgetWarningLevel.SAFE -> st.ink
                },
                modifier = Modifier.weight(1f)
            )
            if (budget.scope == BudgetScope.OVERALL) {
                CandyStatusBadge("Overall cap", CandyTone.Neutral)
            }
        }
    }
}
