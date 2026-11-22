package com.diniy.duit.feature.obligations

import androidx.compose.foundation.layout.padding
import com.diniy.duit.core.ui.components.CandyDateTile
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyPerforation
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandySticker
import com.diniy.duit.core.ui.components.CandyTicketShape
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialGlassSurface
import com.diniy.duit.core.ui.components.candyTone
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
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
import com.diniy.duit.domain.calculator.CommitmentDisplayState
import com.diniy.duit.domain.calculator.CommitmentSummary
import com.diniy.duit.domain.calculator.DebtSummary
import com.diniy.duit.domain.calculator.ObligationsOverview
import com.diniy.duit.domain.calculator.PayLaterSummary
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.feature.savings.SavingsIconMapper

@Composable
fun ObligationsOverviewGrid(
    overview: ObligationsOverview
) {
    val glass = LocalPrismaticGlass.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (overview.overdueCount > 0) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Elevated,
                accentTint = MaterialTheme.colorScheme.error,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overdue Obligations (${overview.overdueCount})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Included in current cycle reserve until paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = maskedBalance(overview.overdueSen),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrismaticGlassCard(
                modifier = Modifier.weight(1f),
                tier = GlassCardTier.Elevated,
                accentTint = glass.peachAccent,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Current Cycle Reserved",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskedBalance(overview.currentCycleReservedSen),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${overview.reserveHorizonLabel} · ${overview.currentCycleReservedCount} due",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PrismaticGlassCard(
                modifier = Modifier.weight(1f),
                tier = GlassCardTier.Elevated,
                accentTint = glass.skyBlueAccent,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Upcoming Future Bills",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskedBalance(overview.upcomingFutureBillsSen),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (overview.upcomingFutureBillsCount > 0) {
                        "${overview.upcomingFutureBillsCount} next cycle · Not reserved"
                    } else {
                        "None outside current cycle"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrismaticGlassCard(
                modifier = Modifier.weight(1f),
                tier = GlassCardTier.Standard,
                accentTint = glass.lavenderAccent,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Total PayLater Outstanding",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskedBalance(overview.payLaterOutstandingSen),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (overview.nextPayLaterInstalmentSen > 0L) {
                        "Next PayLater Instalment: ${maskedBalance(overview.nextPayLaterInstalmentSen)}"
                    } else {
                        "${overview.payLaterActiveCount} active plan${if (overview.payLaterActiveCount == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PrismaticGlassCard(
                modifier = Modifier.weight(1f),
                tier = GlassCardTier.Standard,
                accentTint = glass.skyBlueAccent,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Debts Tracked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Owe ${maskedBalance(overview.debtOwedByMeSen)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (overview.debtOwedByMeSen > 0L) glass.cautionText else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (overview.debtCycleReservedSen > 0L) {
                        "Cycle scheduled ${maskedBalance(overview.debtCycleReservedSen)} · Recv ${maskedBalance(overview.debtOwedToMeSen)}"
                    } else {
                        "Receivable ${maskedBalance(overview.debtOwedToMeSen)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overview.debtOwedToMeSen > 0L) glass.positiveText else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CommitmentObligationCard(
    summary: CommitmentSummary,
    onClick: () -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val type = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val c = summary.commitment
    val (tone, note) = when (summary.displayState) {
        CommitmentDisplayState.OVERDUE -> CandyTone.Coral to "Overdue · Reserved in Safe to Spend"
        CommitmentDisplayState.DUE_TODAY -> CandyTone.Sunny to "Due today · Reserved in Safe to Spend"
        CommitmentDisplayState.PARTIALLY_PAID -> if (summary.isWithinCurrentCycle) {
            CandyTone.Sky to "Paid ${maskedAmount(c.paidSen)} · ${maskedBalance(summary.remainingSen)} reserved in cycle"
        } else {
            CandyTone.Sky to "Paid ${maskedAmount(c.paidSen)} · Next cycle · Not reserved"
        }
        CommitmentDisplayState.UPCOMING -> if (summary.isWithinCurrentCycle) {
            CandyTone.Peach to "Reserved in cycle · Deducted from Safe to Spend"
        } else {
            CandyTone.Sky to "Next cycle · Not reserved"
        }
        CommitmentDisplayState.RESERVED -> CandyTone.Sky to "Reserved · Not double-counted"
        CommitmentDisplayState.PAID -> CandyTone.Mint to (
            c.nextDueDateEpochDay?.let { "Paid · Next due ${DateTimeFormatters.formatEpochDay(it)}" }
                ?: "Paid · Excluded from upcoming reserve"
            )
    }
    val t = candyTone(tone)
    val urgent = summary.displayState == CommitmentDisplayState.OVERDUE || summary.displayState == CommitmentDisplayState.DUE_TODAY
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS

    // Bill ticket in Candy Bubble; clean architectural card with hairline divider in Pure.
    SpatialGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isLiquidGlass) RoundedCornerShape(20.dp) else CandyTicketShape(cornerRadius = 24.dp, notchRadius = 10.dp, notchFraction = 0.70f),
        containerColor = when {
            isLiquidGlass && c.isArchived -> colors.baseSurface
            isLiquidGlass -> colors.elevatedSurface
            c.isArchived -> colors.glassSurfaceSubtle
            else -> t.container
        },
        onClick = onClick,
        onClickLabel = "Inspect ${c.title}"
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(0.70f), verticalAlignment = Alignment.CenterVertically) {
                    CandyDateTile(epochDay = c.dueDateEpochDay, tone = tone)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = type.cardTitle, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${summary.frequencyLabel} · ${c.category}", style = type.caption, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            note,
                            style = type.caption,
                            color = if (summary.displayState == CommitmentDisplayState.OVERDUE) colors.error else if (isLiquidGlass) colors.secondaryText else t.ink,
                            maxLines = 2
                        )
                    }
                }
                if (isLiquidGlass) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(colors.divider)
                    )
                } else {
                    CandyPerforation(t.fill, Modifier.fillMaxHeight().padding(horizontal = 8.dp))
                }
                Column(Modifier.weight(0.30f), horizontalAlignment = Alignment.CenterHorizontally) {
                    CandyMoneyText(maskedBalance(c.amountSen), type.transactionAmount, colors.primaryText, minorScale = 0.7f)
                    Spacer(Modifier.height(6.dp))
                    val label = if (c.isArchived) "Archived" else summary.displayState.label
                    if (urgent && !c.isArchived) CandySticker(label, tone, tilt = -4f) else CandyStatusBadge(label, tone)
                }
            }
            if (c.paidSen > 0L && summary.displayState == CommitmentDisplayState.PARTIALLY_PAID) {
                Box(
                    Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.glassSurfaceElevated)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(summary.progressFraction.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(t.fill)
                    )
                }
            }
        }
    }
}

@Composable
fun PayLaterPlanCard(
    summary: PayLaterSummary,
    onClick: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val plan = summary.plan
    val (accentColor, badgeBg, badgeFg) = SavingsIconMapper.accentColors(plan.accentToken)

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = if (plan.isArchived) GlassCardTier.Muted else GlassCardTier.Standard,
        accentTint = if (summary.isOverdue) MaterialTheme.colorScheme.error else accentColor,
        onClick = onClick,
        onClickLabel = "Inspect ${plan.title}",
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreditScore,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${plan.providerName} · Paid ${maskedBalance(plan.paidSen)} / ${maskedBalance(summary.totalPayableSen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PrismaticBadge(
                label = when {
                    plan.isArchived -> "Archived"
                    summary.isSettled -> "Settled"
                    else -> summary.progressPercentDisplay
                },
                containerColor = if (summary.isOverdue) MaterialTheme.colorScheme.errorContainer else badgeBg,
                contentColor = if (summary.isOverdue) MaterialTheme.colorScheme.onErrorContainer else badgeFg
            )
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { summary.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = if (summary.isOverdue) MaterialTheme.colorScheme.error else accentColor,
            trackColor = glass.glassSurfaceMuted,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                summary.isSettled -> "All ${plan.instalmentCount} instalments paid"
                summary.isOverdue -> "Overdue · ${maskedBalance(summary.outstandingSen)} total left · ${maskedBalance(summary.inCycleDueSen)} due in cycle"
                summary.inCycleDueSen > 0L -> "${maskedBalance(summary.outstandingSen)} total left · Next ${maskedBalance(summary.nextInstalmentSen)} (${maskedBalance(summary.inCycleDueSen)} in cycle)"
                else -> "${maskedBalance(summary.outstandingSen)} total left · Next ${maskedBalance(summary.nextInstalmentSen)} due ${DateTimeFormatters.formatEpochDay(plan.nextDueDateEpochDay)} · Next cycle"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DebtRecordCard(
    summary: DebtSummary,
    onClick: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val debt = summary.debt
    val isOwe = debt.direction == DebtDirection.I_OWE
    val (_, _, _) = SavingsIconMapper.accentColors(debt.accentToken)
    val badgeBg = if (isOwe) glass.peachContainer else glass.aquaContainer
    val badgeFg = if (isOwe) glass.onPeachContainer else glass.onAquaContainer
    val barColor = if (isOwe) glass.peachAccent else glass.aquaAccent

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = if (debt.isArchived) GlassCardTier.Muted else GlassCardTier.Standard,
        accentTint = if (summary.isOverdue) MaterialTheme.colorScheme.error else barColor,
        onClick = onClick,
        onClickLabel = "Inspect ${debt.counterpartyName}",
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.counterpartyName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val desc = debt.title.ifBlank { debt.direction.displayName }
                    Text(
                        text = "$desc · ${maskedBalance(debt.paidSen)} / ${maskedBalance(debt.originalAmountSen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PrismaticBadge(
                label = when {
                    debt.isArchived -> "Archived"
                    summary.isSettled -> "Settled"
                    else -> debt.direction.displayName
                },
                containerColor = badgeBg,
                contentColor = badgeFg
            )
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { summary.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = if (summary.isOverdue) MaterialTheme.colorScheme.error else barColor,
            trackColor = glass.glassSurfaceMuted,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(8.dp))

        val dueText = debt.dueDateEpochDay?.let { " · Due ${DateTimeFormatters.formatEpochDay(it)}" } ?: ""
        val scheduledText = if (isOwe && summary.inCycleScheduledSen > 0L) {
            " · ${maskedBalance(summary.inCycleScheduledSen)} in cycle"
        } else {
            ""
        }
        Text(
            text = if (summary.isSettled) {
                "Settled in full (${summary.progressPercentDisplay})"
            } else {
                "${maskedBalance(summary.outstandingSen)} ${if (isOwe) "remaining" else "to collect"}$dueText$scheduledText"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
