package com.diniy.duit.feature.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.BudgetPeriodOverview
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.calculator.CategorySpendingItem
import com.diniy.duit.domain.calculator.InsightsFinancialOverview
import com.diniy.duit.domain.calculator.MonthlyCashflowPoint
import com.diniy.duit.domain.calculator.MonthlySavingsTrendPoint
import com.diniy.duit.domain.calculator.ObligationsOverview
import com.diniy.duit.domain.calculator.SavingsAnalyticsSummary
import com.diniy.duit.domain.calculator.SmartInsight
import com.diniy.duit.domain.calculator.SmartInsightTone
import com.diniy.duit.domain.model.BudgetWarningLevel
import com.diniy.duit.domain.model.FinanceTransaction
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FinancialOverviewHeroCard(
    overview: InsightsFinancialOverview,
    isSamplePreview: Boolean
) {
    val glass = LocalPrismaticGlass.current
    val resolved = overview.resolvedPeriod

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = glass.aquaAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Financial Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${resolved.periodLabel} · ${DateTimeFormatters.formatEpochDay(resolved.startEpochDay)} – ${DateTimeFormatters.formatEpochDay(resolved.endEpochDayInclusive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            PrismaticBadge(
                label = if (isSamplePreview) "Sample" else resolved.preset.label,
                containerColor = glass.aquaContainer,
                contentColor = glass.onAquaContainer
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = "Total Income",
                value = maskedBalance(overview.totalIncomeSen),
                valueColor = glass.positiveText,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Total Expenses",
                value = maskedBalance(overview.totalExpenseSen),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = "Net Cash Flow",
                value = maskedBalance(overview.netCashFlowSen, includePlusSign = true),
                valueColor = when {
                    overview.netCashFlowSen > 0L -> glass.positiveText
                    overview.netCashFlowSen < 0L -> glass.cautionText
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Savings Rate",
                value = if (LocalPrivacySettings.current.isAnyMaskActive) "••%" else overview.savingsRateDisplay,
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = "Avg Daily Spend",
                value = if (resolved.elapsedDays > 0L) "${maskedBalance(overview.avgDailySpendingSen)}/d" else "—",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Available Safe to Spend",
                value = maskedBalance(overview.safeToSpendSen),
                valueColor = if (overview.safeToSpendSen < 0L) glass.cautionText else glass.positiveText,
                modifier = Modifier.weight(1f)
            )
        }

        if (overview.transferCountExcluded > 0) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Excluded ${overview.transferCountExcluded} internal transfer(s) totaling ${maskedBalance(overview.transferTotalExcludedSen)} from income and expenses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val glass = LocalPrismaticGlass.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(glass.glassSurfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SmartInsightsCard(
    insights: List<SmartInsight>
) {
    val glass = LocalPrismaticGlass.current

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = glass.lavenderAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoGraph,
                    contentDescription = null,
                    tint = glass.lavenderAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Smart Insights",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            PrismaticBadge(
                label = "On-device rules",
                containerColor = glass.lavenderContainer,
                contentColor = glass.onLavenderContainer
            )
        }

        Spacer(Modifier.height(10.dp))

        if (insights.isEmpty()) {
            Text(
                text = "Observations will appear here once transactions, savings movements, budgets, or obligations are recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.forEach { insight ->
                    val (bg, fg, icon) = when (insight.tone) {
                        SmartInsightTone.POSITIVE -> Triple(glass.aquaContainer, glass.onAquaContainer, Icons.AutoMirrored.Rounded.TrendingUp)
                        SmartInsightTone.WARNING -> Triple(glass.peachContainer, glass.onPeachContainer, Icons.Rounded.WarningAmber)
                        SmartInsightTone.INFO -> Triple(glass.skyBlueContainer, glass.onSkyBlueContainer, Icons.Rounded.Info)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(glass.glassSurfaceMuted)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        val hideNumbers = LocalPrivacySettings.current.isAnyMaskActive
                        val amountRegex = remember { Regex("""-?RM\s[\d,]+\.\d{2}""") }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hideNumbers) insight.title.replace(amountRegex, PrivacyMask.MASKED_MYR) else insight.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (hideNumbers) insight.detail.replace(amountRegex, PrivacyMask.MASKED_MYR) else insight.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeVsExpensesChartCard(
    monthlyTrends: List<MonthlyCashflowPoint>,
    horizonMonths: Int,
    onSelectHorizon: (Int) -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val reducedMotion = rememberReducedMotionEnabled()
    var selectedMonthIdx by remember(monthlyTrends) {
        mutableStateOf((monthlyTrends.size - 1).coerceAtLeast(0))
    }

    val maxSen = remember(monthlyTrends) {
        monthlyTrends.maxOfOrNull { maxOf(it.incomeSen, it.expenseSen) } ?: 0L
    }
    val hasAnyActivity = maxSen > 0L
    val chartProgress by animateFloatAsState(
        targetValue = if (hasAnyActivity) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 420),
        label = "incomeExpenseChartProgress"
    )

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
                    text = "Income vs Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Monthly cashflow trends (transfers excluded)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(3, 6, 12).forEach { months ->
                FilterChip(
                    selected = horizonMonths == months,
                    onClick = { onSelectHorizon(months) },
                    label = { Text("${months}M") },
                    modifier = Modifier.heightIn(min = 40.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Legend + scale cap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = glass.aquaAccent, label = "Income")
                LegendDot(color = glass.peachAccent, label = "Expenses")
            }
            Text(
                text = if (hasAnyActivity) "Peak ${maskedAmount(maxSen)}" else "No records",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!hasAnyActivity) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(glass.glassSurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No income or expenses recorded across the last $horizonMonths months.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val incomeColor = glass.aquaAccent
            val expenseColor = glass.peachAccent
            val gridColor = glass.glassBorderSubtle
            val highlightBg = glass.glassSurfaceElevated

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = monthlyTrends.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val bottomPad = 6.dp.toPx()
                    val usableHeight = (size.height - bottomPad).coerceAtLeast(1f)

                    // Subtle horizontal baseline & midpoint grid lines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, usableHeight),
                        end = Offset(size.width, usableHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(0f, usableHeight * 0.5f),
                        end = Offset(size.width, usableHeight * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )

                    monthlyTrends.forEachIndexed { idx, point ->
                        val slotLeft = idx * slotWidth
                        if (idx == selectedMonthIdx) {
                            drawRoundRect(
                                color = highlightBg.copy(alpha = 0.5f),
                                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                                size = Size((slotWidth - 4.dp.toPx()).coerceAtLeast(4f), usableHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                        val barWidth = (slotWidth * if (count >= 12) 0.28f else 0.24f).coerceIn(4.dp.toPx(), 16.dp.toPx())
                        val gap = (barWidth * 0.35f).coerceAtLeast(2.dp.toPx())
                        val groupWidth = barWidth * 2f + gap
                        val groupStart = slotLeft + (slotWidth - groupWidth) / 2f

                        val inHeight = if (point.incomeSen > 0L) {
                            ((point.incomeSen.toFloat() / maxSen.toFloat()) * usableHeight * chartProgress)
                                .coerceAtLeast(4.dp.toPx())
                        } else {
                            0f
                        }
                        val outHeight = if (point.expenseSen > 0L) {
                            ((point.expenseSen.toFloat() / maxSen.toFloat()) * usableHeight * chartProgress)
                                .coerceAtLeast(4.dp.toPx())
                        } else {
                            0f
                        }

                        if (inHeight > 0f) {
                            drawRoundRect(
                                color = incomeColor,
                                topLeft = Offset(groupStart, usableHeight - inHeight),
                                size = Size(barWidth, inHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (outHeight > 0f) {
                            drawRoundRect(
                                color = expenseColor,
                                topLeft = Offset(groupStart + barWidth + gap, usableHeight - outHeight),
                                size = Size(barWidth, outHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                // Transparent interactive touch columns per month for tap-to-inspect & TalkBack
                Row(modifier = Modifier.fillMaxSize()) {
                    monthlyTrends.forEachIndexed { idx, point ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Inspect ${point.shortLabel}"
                                ) { selectedMonthIdx = idx }
                        )
                    }
                }
            }

            // X-axis month labels
            Row(modifier = Modifier.fillMaxWidth()) {
                val step = if (monthlyTrends.size >= 12) 2 else 1
                monthlyTrends.forEachIndexed { idx, point ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (idx % step == 0 || idx == selectedMonthIdx || idx == monthlyTrends.lastIndex) {
                            Text(
                                text = if (monthlyTrends.size >= 12) {
                                    point.yearMonth.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                                } else {
                                    point.shortLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx == selectedMonthIdx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (idx == selectedMonthIdx) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            val inspected = monthlyTrends.getOrNull(selectedMonthIdx)
            if (inspected != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.glassSurfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${inspected.yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${inspected.yearMonth.year}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "In ${maskedAmount(inspected.incomeSen)} · Out ${maskedAmount(inspected.expenseSen)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Net ${maskedAmount(inspected.netSen, includePlusSign = true)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (inspected.netSen >= 0L) glass.positiveText else glass.cautionText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SpendingBreakdownDonutCard(
    periodLabel: String,
    totalExpenseSen: Long,
    categories: List<CategorySpendingItem>,
    onInspectCategory: (String) -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val reducedMotion = rememberReducedMotionEnabled()
    val palette = listOf(
        glass.aquaAccent,
        glass.skyBlueAccent,
        glass.lavenderAccent,
        glass.peachAccent,
        glass.positiveText,
        glass.cautionText
    )

    val sweepFactor by animateFloatAsState(
        targetValue = if (totalExpenseSen > 0L && categories.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 480),
        label = "donutSweep"
    )

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = glass.skyBlueAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spending Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$periodLabel · Tap a category for transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (totalExpenseSen <= 0L || categories.isEmpty()) {
            Text(
                text = "Category proportions and interactive donut chart will appear after your first expense in this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(glass.glassSurfaceMuted)
            )
        } else {
            // Centered Donut Chart matching Prismatic Glass aesthetic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = glass.glassSurfaceMuted
                Canvas(modifier = Modifier.size(148.dp)) {
                    val strokePx = 22.dp.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )

                    var startAngle = -90f
                    val gapDegrees = if (categories.size > 1) 2.5f else 0f
                    categories.forEach { item ->
                        val rawSweep = (item.amountSen.toFloat() / totalExpenseSen.toFloat()) * 360f * sweepFactor
                        val drawSweep = (rawSweep - gapDegrees).coerceAtLeast(1.5f)
                        val color = palette[item.colorSlot % palette.size]
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = drawSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        )
                        startAngle += rawSweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = maskedBalance(totalExpenseSen),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { item ->
                    val sliceColor = palette[item.colorSlot % palette.size]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "View ${item.categoryName} transactions"
                            ) { onInspectCategory(item.categoryName) }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(sliceColor)
                                )
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "(${item.transactionCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.percentageDisplay,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = maskedAmount(item.amountSen),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { item.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = sliceColor,
                            trackColor = glass.glassSurfaceMuted,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsSheet(
    categoryName: String,
    periodLabel: String,
    transactions: List<FinanceTransaction>,
    walletNamesById: Map<Long, String>,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val totalSen = transactions.sumOf { it.amountSen }

    PrismaticModalBottomSheet(
        title = categoryName,
        subtitle = "${transactions.size} expense(s) · ${maskedAmount(totalSen)} in $periodLabel",
        onDismissRequest = onDismiss
    ) {
        if (transactions.isEmpty()) {
            Text(
                text = "No expense transactions found in this category for $periodLabel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                transactions.forEach { tx ->
                    val walletName = walletNamesById[tx.walletId] ?: "Wallet"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(glass.glassSurfaceMuted)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.title.ifBlank { tx.category },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${DateTimeFormatters.formatTimestamp(tx.occurredAtEpochMs)} · $walletName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.note.isNotBlank()) {
                                Text(
                                    text = tx.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (LocalPrivacySettings.current.isAnyMaskActive) PrivacyMask.MASKED_MYR else "- ${MyrFormatter.format(tx.amountSen)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrismaticButton(
            text = "Close",
            onClick = onDismiss,
            variant = PrismaticButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SavingsAnalyticsCard(
    savingsAnalytics: SavingsAnalyticsSummary
) {
    val glass = LocalPrismaticGlass.current
    val overview = savingsAnalytics.overview

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = glass.skyBlueAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Savings Analytics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Protected savings, goal allocations & growth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticBadge(
                label = "${overview.activeGoalsCount} active goal${if (overview.activeGoalsCount == 1) "" else "s"}",
                containerColor = glass.skyBlueContainer,
                contentColor = glass.onSkyBlueContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Total Protected Savings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = maskedBalance(overview.totalProtectedSavingsSen),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Allocated ${maskedBalance(overview.totalAllocatedToGoalsSen)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unlinked ${maskedBalance(overview.unallocatedProtectedWalletSen)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Contribution pattern tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Saved in Period (${savingsAnalytics.periodContributionCount})",
                value = maskedBalance(savingsAnalytics.periodContributionsSen),
                valueColor = glass.positiveText,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Net Period Change",
                value = maskedBalance(savingsAnalytics.periodNetSavingsSen, includePlusSign = true),
                valueColor = if (savingsAnalytics.periodNetSavingsSen >= 0L) glass.positiveText else glass.cautionText,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (savingsAnalytics.hasMovementHistory && savingsAnalytics.monthlyTrend.isNotEmpty()) {
            Text(
                text = "Protected Savings Balance Trend (6M)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SavingsSparkline(points = savingsAnalytics.monthlyTrend)
        } else {
            Text(
                text = "Savings growth trends appear as you transfer or allocate funds into protected savings over time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (savingsAnalytics.goalProgressList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savingsAnalytics.goalProgressList.take(4).forEach { gp ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = gp.goal.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${maskedBalance(gp.savedSen)} / ${maskedBalance(gp.goal.targetAmountSen)} (${gp.progressPercentDisplay})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { gp.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = glass.skyBlueAccent,
                            trackColor = glass.glassSurfaceMuted,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsSparkline(points: List<MonthlySavingsTrendPoint>) {
    val glass = LocalPrismaticGlass.current
    val lineColor = glass.skyBlueAccent
    val fillColor = glass.skyBlueAccent.copy(alpha = 0.16f)
    val maxVal = (points.maxOfOrNull { it.endBalanceSen } ?: 0L).coerceAtLeast(1L)
    val minVal = (points.minOfOrNull { it.endBalanceSen } ?: 0L).coerceAtMost(maxVal)
    val span = (maxVal - minVal).coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(glass.glassSurfaceMuted)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (points.size < 2) return@Canvas
        val stepX = size.width / (points.size - 1).toFloat()
        val coords = points.mapIndexed { idx, p ->
            val ratio = if (maxVal == minVal) 0.5f else (p.endBalanceSen - minVal).toFloat() / span.toFloat()
            val y = size.height - (ratio * size.height)
            Offset(idx * stepX, y)
        }
        val fillPath = Path().apply {
            moveTo(coords.first().x, size.height)
            coords.forEach { lineTo(it.x, it.y) }
            lineTo(coords.last().x, size.height)
            close()
        }
        drawPath(path = fillPath, color = fillColor)

        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            coords.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        coords.forEach { pt ->
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = pt)
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        points.forEach { pt ->
            Text(
                text = pt.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BudgetPerformanceCard(
    overview: BudgetPeriodOverview,
    history: List<BudgetProgress>
) {
    val glass = LocalPrismaticGlass.current
    var showHistory by rememberSaveable { mutableStateOf(false) }

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = if (overview.exceededCount > 0) glass.peachAccent else glass.lavenderAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Budget Performance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${overview.periodLabel} · Spending limits never lock wallet cash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (overview.effectiveLimitSen > 0L) {
                PrismaticBadge(
                    label = "${overview.overallUsedPercentDisplay} used",
                    containerColor = if (overview.effectiveOverspentSen > 0L) glass.peachContainer else glass.lavenderContainer,
                    contentColor = if (overview.effectiveOverspentSen > 0L) glass.onPeachContainer else glass.onLavenderContainer
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (overview.effectiveLimitSen <= 0L && overview.categoryBudgets.isEmpty()) {
            Text(
                text = "No budgets configured for ${overview.periodLabel}. Set budgets in Planning to compare limits against actual expenses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Budget vs Actual",
                    value = "${maskedBalance(overview.effectiveCoveredSpentSen)} / ${maskedBalance(overview.effectiveLimitSen)}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = if (overview.effectiveOverspentSen > 0L) "Overspent" else "Remaining",
                    value = maskedBalance(
                        if (overview.effectiveOverspentSen > 0L) overview.effectiveOverspentSen else overview.effectiveRemainingSen
                    ),
                    valueColor = if (overview.effectiveOverspentSen > 0L) MaterialTheme.colorScheme.error else glass.positiveText,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { overview.overallProgressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (overview.effectiveOverspentSen > 0L) MaterialTheme.colorScheme.error else glass.lavenderAccent,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )

            if (overview.categoryBudgets.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    overview.categoryBudgets.forEach { progress ->
                        val isOver = progress.warningLevel == BudgetWarningLevel.EXCEEDED
                        val barColor = when (progress.warningLevel) {
                            BudgetWarningLevel.EXCEEDED -> MaterialTheme.colorScheme.error
                            BudgetWarningLevel.AT_LIMIT_100 -> glass.peachAccent
                            BudgetWarningLevel.APPROACHING_80 -> glass.peachAccent
                            BudgetWarningLevel.SAFE -> glass.aquaAccent
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = progress.budget.categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isOver) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isOver) {
                                        "${maskedAmount(progress.spentSen)} / ${maskedAmount(progress.limitSen)} (+${maskedAmount(progress.overspentSen)})"
                                    } else {
                                        "${maskedAmount(progress.spentSen)} / ${maskedAmount(progress.limitSen)} (${progress.usedPercentDisplay})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = barColor,
                                trackColor = glass.glassSurfaceMuted,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { showHistory = !showHistory }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historical Budget Periods (${history.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = showHistory) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.take(8).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${entry.budget.name} · ${entry.periodLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${maskedAmount(entry.spentSen)} / ${maskedAmount(entry.limitSen)} (${entry.usedPercentDisplay})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.overspentSen > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitmentsAndDebtAnalyticsCard(
    overview: ObligationsOverview
) {
    val glass = LocalPrismaticGlass.current

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = glass.peachAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Commitments & Debt Analytics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Obligations reserved for ${overview.reserveHorizonLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrismaticBadge(
                label = "${overview.currentCycleReservedCount} due",
                containerColor = glass.peachContainer,
                contentColor = glass.onPeachContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Current Cycle Reserved",
                value = maskedBalance(overview.currentCycleReservedSen),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Upcoming Future Bills",
                value = maskedBalance(overview.upcomingFutureBillsSen),
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Total PayLater Outstanding",
                value = maskedBalance(overview.payLaterOutstandingSen),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Next PayLater Instalment",
                value = maskedBalance(overview.nextPayLaterInstalmentSen),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Debts I Owe (${overview.debtOwedByMeCount})",
                value = maskedBalance(overview.debtOwedByMeSen),
                valueColor = if (overview.debtOwedByMeSen > 0L) glass.cautionText else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Debts Owed to Me (${overview.debtOwedToMeCount})",
                value = maskedBalance(overview.debtOwedToMeSen),
                valueColor = if (overview.debtOwedToMeSen > 0L) glass.positiveText else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Outstanding debt and PayLater balances become expenses only when an actual payment transaction is recorded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
