package com.diniy.duit.feature.insights

import com.diniy.duit.core.ui.components.rememberCandyReveal
import com.diniy.duit.core.ui.components.candyEntrance
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.feature.transactions.CategoryIconMapper
import kotlin.math.roundToInt
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyDecor
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyProgressRing
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandySticker
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassSurface
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.components.rememberDuitHaptic
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
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val masked = LocalPrivacySettings.current.isAnyMaskActive
    val resolved = overview.resolvedPeriod
    val net = overview.netCashFlowSen
    val heroFill = if (net >= 0L) colors.aqua else colors.coral
    val heroInk = colors.onCoral
    val income = overview.totalIncomeSen.coerceAtLeast(0L)
    val expense = overview.totalExpenseSen.coerceAtLeast(0L)
    val flowTotal = income + expense
    val inShare = if (flowTotal > 0L) income.toFloat() / flowTotal.toFloat() else 0f
    val savingsFraction = if (income > 0L) ((income - expense).toFloat() / income.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.candyEntrance("insights_overview_${resolved.startEpochDay}_${resolved.endEpochDayInclusive}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Net cash-flow hero: sticker card with an in/out flow meter
        SpatialGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = heroFill,
            sticker = true,
            shape = RoundedCornerShape(30.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(Color.White.copy(alpha = 0.22f), radius = size.width * 0.30f, center = Offset(size.width * 0.96f, size.height * 0.06f))
                        drawCircle(Color.White.copy(alpha = 0.14f), radius = size.width * 0.12f, center = Offset(size.width * 0.06f, size.height * 0.96f))
                    }
            )
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Net cash flow", style = type.cardTitle.copy(fontSize = 18.sp), color = heroInk)
                        Text(
                            text = "${DateTimeFormatters.formatEpochDay(resolved.startEpochDay)} – ${DateTimeFormatters.formatEpochDay(resolved.endEpochDayInclusive)}",
                            style = type.caption,
                            color = heroInk.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CandySticker(text = if (isSamplePreview) "Sample" else resolved.preset.label, tone = CandyTone.Sunny, tilt = 4f)
                }
                Spacer(Modifier.height(10.dp))
                CandyMoneyText(
                    maskedBalance(net, includePlusSign = true),
                    type.heroBalance.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    heroInk,
                    minorScale = 0.5f
                )
                Spacer(Modifier.height(12.dp))
                // Flow meter: share of money in vs out this period (real totals)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.45f))
                ) {
                    if (flowTotal > 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth(inShare.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(heroInk)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (flowTotal == 0L) "No income or spending yet" else if (masked) "In ••%" else "In ${(inShare * 100).roundToInt()}%",
                        style = type.caption,
                        color = heroInk
                    )
                    if (flowTotal > 0L) {
                        Text(if (masked) "Out ••%" else "Out ${100 - (inShare * 100).roundToInt()}%", style = type.caption, color = heroInk)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InsightStatBubble(
                label = "Income",
                value = maskedBalance(overview.totalIncomeSen),
                valueColor = colors.income,
                icon = Icons.Rounded.ArrowDownward,
                tone = CandyTone.Mint,
                tilt = -6f,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InsightStatBubble(
                label = "Expenses",
                value = maskedBalance(overview.totalExpenseSen),
                valueColor = colors.primaryText,
                icon = Icons.Rounded.ArrowUpward,
                tone = CandyTone.Coral,
                tilt = 6f,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.glassSurface)
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CandyProgressRing(fraction = savingsFraction, tone = CandyTone.Sunny, size = 46.dp, stroke = 6.dp) {
                    Text(if (masked) "••" else overview.savingsRateDisplay, style = type.caption.copy(fontSize = 12.sp), color = colors.primaryText, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Text("Savings rate", style = type.caption, color = colors.secondaryText, maxLines = 1)
            }
            InsightMiniStat(
                label = "Avg / day",
                value = if (resolved.elapsedDays > 0L) maskedBalance(overview.avgDailySpendingSen) else "—",
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
            InsightMiniStat(
                label = "Safe to spend",
                value = maskedBalance(overview.safeToSpendSen),
                color = if (overview.safeToSpendSen < 0L) colors.warning else colors.income,
                modifier = Modifier.weight(1f)
            )
        }

        if (overview.transferCountExcluded > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(candyTone(CandyTone.Sky).container)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = candyTone(CandyTone.Sky).ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${overview.transferCountExcluded} internal transfer(s) (${maskedBalance(overview.transferTotalExcludedSen)}) aren't counted as income or spending.",
                    style = type.caption,
                    color = candyTone(CandyTone.Sky).ink
                )
            }
        }
    }
}

@Composable
private fun InsightStatBubble(
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
    tone: CandyTone,
    tilt: Float,
    modifier: Modifier = Modifier
) {
    val type = SpatialTheme.typography
    CandyBubbleCard(tone = tone, decor = CandyDecor.Corner, modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(icon, tone, size = 34.dp, tilt = tilt, solid = true)
            Spacer(Modifier.width(8.dp))
            Text(label, style = type.caption, color = SpatialTheme.colors.secondaryText)
        }
        Spacer(Modifier.height(10.dp))
        CandyMoneyText(value, type.cardBalance, valueColor)
    }
}

/** Candy stat tile shared by the analytics cards (label + split money value). */
@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val type = SpatialTheme.typography
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SpatialTheme.colors.glassSurfaceSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = type.caption, color = SpatialTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        CandyMoneyText(value, type.transactionAmount.copy(fontSize = 16.sp), valueColor, minorScale = 0.7f)
    }
}

@Composable
private fun InsightMiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val type = SpatialTheme.typography
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CandyMoneyText(value, type.transactionAmount.copy(fontSize = 16.sp), color, minorScale = 0.7f)
        Spacer(Modifier.height(4.dp))
        Text(label, style = type.caption, color = SpatialTheme.colors.secondaryText, maxLines = 1)
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
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    var selectedMonthIdx by remember(monthlyTrends) {
        mutableStateOf((monthlyTrends.size - 1).coerceAtLeast(0))
    }
    val maxSen = remember(monthlyTrends) {
        monthlyTrends.maxOfOrNull { maxOf(it.incomeSen, it.expenseSen) } ?: 0L
    }
    val hasAnyActivity = maxSen > 0L
    // Bars grow from the baseline once per data set (not on scroll or recomposition).
    val chartReveal = rememberCandyReveal(monthlyTrends.map { it.incomeSen to it.expenseSen } to horizonMonths)

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        Text("Income vs expenses", style = type.sectionHeading, color = colors.primaryText)
        Text("Monthly cashflow · transfers excluded", style = type.caption, color = colors.secondaryText)

        Spacer(Modifier.height(12.dp))

        // Candy segmented horizon picker (3M / 6M / 12M)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(colors.baseSurface)
                .padding(4.dp)
        ) {
            listOf(3, 6, 12).forEach { months ->
                val selected = horizonMonths == months
                val bg by animateColorAsState(if (selected) colors.coral else Color.Transparent, label = "horizon_bg")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .candyPressable(onClick = { onSelectHorizon(months) }, onClickLabel = "Show $months months")
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${months}M", style = type.buttonLabel, color = if (selected) colors.onCoral else colors.secondaryText)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CandyStatusBadge("Income", CandyTone.Mint)
            Spacer(Modifier.width(6.dp))
            CandyStatusBadge("Expenses", CandyTone.Coral)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (hasAnyActivity) "Peak ${maskedAmount(maxSen)}" else "No records",
                style = type.caption,
                color = colors.secondaryText
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!hasAnyActivity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.glassSurfaceSubtle)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CandyIconBlob(Icons.Rounded.BarChart, CandyTone.Sunny, size = 44.dp, tilt = -6f, solid = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No income or expenses in the last $horizonMonths months yet.",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
            }
        } else {
            val incomeColor = colors.aqua
            val expenseColor = colors.coral
            val gridColor = colors.divider
            val highlight = candyTone(CandyTone.Peach).container
            val ink = colors.sticker

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = monthlyTrends.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val usableHeight = (size.height - 4.dp.toPx()).coerceAtLeast(1f)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                    listOf(0.5f, 1f).forEach { f ->
                        drawLine(gridColor, Offset(0f, usableHeight * (1f - f) + 1f), Offset(size.width, usableHeight * (1f - f) + 1f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    }
                    drawLine(gridColor, Offset(0f, usableHeight), Offset(size.width, usableHeight), strokeWidth = 1.5.dp.toPx())

                    monthlyTrends.forEachIndexed { idx, point ->
                        val slotLeft = idx * slotWidth
                        val isSelected = idx == selectedMonthIdx
                        if (isSelected) {
                            drawRoundRect(
                                color = highlight,
                                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                                size = Size((slotWidth - 4.dp.toPx()).coerceAtLeast(4f), usableHeight),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                        }
                        val barWidth = (slotWidth * if (count >= 12) 0.30f else 0.26f).coerceIn(5.dp.toPx(), 20.dp.toPx())
                        val gap = (barWidth * 0.25f).coerceAtLeast(2.dp.toPx())
                        val groupStart = slotLeft + (slotWidth - (barWidth * 2f + gap)) / 2f
                        val fade = if (isSelected) 1f else 0.7f
                        fun bar(value: Long, x: Float, color: Color) {
                            if (value <= 0L) return
                            val h = ((value.toFloat() / maxSen.toFloat()) * usableHeight * chartReveal.value).coerceAtLeast(barWidth * chartReveal.value)
                            val topLeft = Offset(x, usableHeight - h)
                            val sz = Size(barWidth, h)
                            val r = CornerRadius(barWidth / 2f)
                            drawRoundRect(color.copy(alpha = fade), topLeft, sz, r)
                            if (isSelected) {
                                drawRoundRect(ink, topLeft, sz, r, style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                        bar(point.incomeSen, groupStart, incomeColor)
                        bar(point.expenseSen, groupStart + barWidth + gap, expenseColor)
                    }
                }

                // Touch columns for tap-to-inspect & TalkBack
                Row(modifier = Modifier.fillMaxSize()) {
                    monthlyTrends.forEachIndexed { idx, point ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(role = Role.Button, onClickLabel = "Inspect ${point.shortLabel}") {
                                    if (idx != selectedMonthIdx) haptic(HapticFeedbackType.TextHandleMove)
                                    selectedMonthIdx = idx
                                }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val step = if (monthlyTrends.size >= 12) 2 else 1
                monthlyTrends.forEachIndexed { idx, point ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (idx % step == 0 || idx == selectedMonthIdx || idx == monthlyTrends.lastIndex) {
                            Text(
                                text = if (monthlyTrends.size >= 12) {
                                    point.yearMonth.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                                } else {
                                    point.shortLabel
                                },
                                style = type.caption,
                                color = if (idx == selectedMonthIdx) colors.primaryText else colors.secondaryText,
                                fontWeight = if (idx == selectedMonthIdx) FontWeight.Black else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            val inspected = monthlyTrends.getOrNull(selectedMonthIdx)
            if (inspected != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(highlight)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CandySticker(
                        text = "${inspected.yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${inspected.yearMonth.year % 100}",
                        tone = CandyTone.Coral,
                        tilt = -4f
                    )
                    Spacer(Modifier.width(10.dp))
                    MonthFigure("In", maskedAmount(inspected.incomeSen), colors.income, Modifier.weight(1f))
                    MonthFigure("Out", maskedAmount(inspected.expenseSen), colors.primaryText, Modifier.weight(1f))
                    MonthFigure(
                        "Net",
                        maskedAmount(inspected.netSen, includePlusSign = true),
                        if (inspected.netSen >= 0L) colors.income else colors.warning,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(label, style = SpatialTheme.typography.caption, color = SpatialTheme.colors.secondaryText)
        CandyMoneyText(value, SpatialTheme.typography.transactionAmount.copy(fontSize = 14.sp), color, minorScale = 0.72f)
    }
}

@Composable
fun SpendingBreakdownDonutCard(
    periodLabel: String,
    totalExpenseSen: Long,
    categories: List<CategorySpendingItem>,
    onInspectCategory: (String) -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()
    // Readable candy palette, distinct in both themes.
    val palette = listOf(colors.coral, colors.aqua, colors.sunny, colors.skyBlue, colors.peach, colors.expense)
    val hasData = totalExpenseSen > 0L && categories.isNotEmpty()
    // Segments sweep in once per data set; proportions are always the real ones.
    val donutReveal = rememberCandyReveal(categories.map { it.categoryName to it.amountSen } to totalExpenseSen, durationMs = 750)
    val sweepFactor = if (hasData) donutReveal.value else 0f
    val topCategory = categories.maxByOrNull { it.amountSen }

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        Text("Spending breakdown", style = type.sectionHeading, color = colors.primaryText)
        Text("$periodLabel · tap a category for its transactions", style = type.caption, color = colors.secondaryText)

        Spacer(Modifier.height(14.dp))

        if (!hasData) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.glassSurfaceSubtle)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CandyIconBlob(Icons.Rounded.PieChart, CandyTone.Coral, size = 44.dp, tilt = 6f, solid = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your spending donut appears after the first expense in this period.",
                    style = type.supportingInfo,
                    color = colors.secondaryText
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp),
                contentAlignment = Alignment.Center
            ) {
                val track = colors.baseSurface
                val bubble = colors.glassSurfaceSubtle
                Canvas(modifier = Modifier.size(200.dp)) {
                    val stroke = 30.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    val arcSize = Size(radius * 2f, radius * 2f)
                    // Soft inner bubble + track
                    drawCircle(bubble, radius = radius - stroke / 2f - 6.dp.toPx())
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

                    val gapDeg = if (categories.size > 1) 4f else 0f
                    val capDeg = Math.toDegrees((stroke / 2f / radius).toDouble()).toFloat()
                    var start = -90f
                    categories.forEach { item ->
                        val raw = (item.amountSen.toFloat() / totalExpenseSen.toFloat()) * 360f * sweepFactor
                        val color = palette[item.colorSlot % palette.size]
                        if (categories.size == 1) {
                            drawArc(color, start, raw, false, topLeft, arcSize, style = Stroke(stroke))
                        } else {
                            // Round caps extend past the arc; trim them so gaps stay visible.
                            // Tiny slices collapse to a round dot so they stay identifiable.
                            val sweep = (raw - gapDeg - 2f * capDeg).coerceAtLeast(0.01f)
                            drawArc(color, start + gapDeg / 2f + capDeg, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        start += raw
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                    Text("Spent", style = type.caption, color = colors.secondaryText)
                    CandyMoneyText(maskedBalance(totalExpenseSen), type.cardBalance, colors.primaryText, minorScale = 0.55f)
                    Text(
                        "${categories.size} categor${if (categories.size == 1) "y" else "ies"}",
                        style = type.caption,
                        color = colors.secondaryText
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { item ->
                    val slice = palette[item.colorSlot % palette.size]
                    val iconKey = CategoryIconMapper.inferIconKeyFromName(item.categoryName, TransactionType.EXPENSE)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .candyPressable(onClick = { onInspectCategory(item.categoryName) }, onClickLabel = "View ${item.categoryName} transactions")
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .rotate(if (item.colorSlot % 2 == 0) -6f else 5f)
                                .clip(RoundedCornerShape(15.dp))
                                .background(slice.copy(alpha = if (colors.isDark) 0.30f else 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                CategoryIconMapper.iconFor(iconKey, TransactionType.EXPENSE),
                                contentDescription = null,
                                tint = colors.readableOn(slice),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.categoryName,
                                    style = type.transactionDescription,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (item == topCategory && categories.size > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    CandySticker("Top", CandyTone.Sunny, tilt = 6f)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.baseSurface)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((item.fraction * sweepFactor).coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(slice)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            CandyMoneyText(maskedAmount(item.amountSen), type.transactionAmount, colors.primaryText, minorScale = 0.72f)
                            Text("${item.percentageDisplay} · ${item.transactionCount} tx", style = type.caption, color = colors.secondaryText, maxLines = 1)
                        }
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
