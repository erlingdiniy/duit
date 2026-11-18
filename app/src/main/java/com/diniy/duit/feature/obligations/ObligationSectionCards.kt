package com.diniy.duit.feature.obligations

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
    val glass = LocalPrismaticGlass.current
    val c = summary.commitment
    val (badgeBg, badgeFg, note) = when (summary.displayState) {
        CommitmentDisplayState.OVERDUE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Overdue · Reserved in Safe to Spend"
        )
        CommitmentDisplayState.DUE_TODAY -> Triple(
            glass.peachContainer,
            glass.onPeachContainer,
            "Due today · Reserved in Safe to Spend"
        )
        CommitmentDisplayState.PARTIALLY_PAID -> if (summary.isWithinCurrentCycle) {
            Triple(
                glass.skyBlueContainer,
                glass.onSkyBlueContainer,
                "Paid ${maskedAmount(c.paidSen)} · ${maskedBalance(summary.remainingSen)} reserved in cycle"
            )
        } else {
            Triple(
                glass.lavenderContainer,
                glass.onLavenderContainer,
                "Paid ${maskedAmount(c.paidSen)} · Next cycle · Not reserved"
            )
        }
        CommitmentDisplayState.UPCOMING -> if (summary.isWithinCurrentCycle) {
            Triple(
                glass.peachContainer,
                glass.onPeachContainer,
                "Reserved in cycle · Deducted from Safe to Spend"
            )
        } else {
            Triple(
                glass.lavenderContainer,
                glass.onLavenderContainer,
                "Next cycle · Not reserved"
            )
        }
        CommitmentDisplayState.RESERVED -> Triple(
            glass.skyBlueContainer,
            glass.onSkyBlueContainer,
            "Reserved · Not double-counted"
        )
        CommitmentDisplayState.PAID -> Triple(
            glass.aquaContainer,
            glass.onAquaContainer,
            c.nextDueDateEpochDay?.let {
                "Paid · Next due ${DateTimeFormatters.formatEpochDay(it)}"
            } ?: "Paid · Excluded from upcoming reserve"
        )
    }

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = if (c.isArchived) GlassCardTier.Muted else GlassCardTier.Standard,
        accentTint = when (summary.displayState) {
            CommitmentDisplayState.OVERDUE -> MaterialTheme.colorScheme.error
            CommitmentDisplayState.PAID -> glass.aquaAccent
            else -> glass.peachAccent
        },
        onClick = onClick,
        onClickLabel = "Inspect ${c.title}",
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = c.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${DateTimeFormatters.formatEpochDay(c.dueDateEpochDay)} · ${summary.frequencyLabel} · ${c.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (summary.displayState == CommitmentDisplayState.OVERDUE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = maskedBalance(c.amountSen),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                PrismaticBadge(
                    label = if (c.isArchived) "Archived" else summary.displayState.label,
                    containerColor = badgeBg,
                    contentColor = badgeFg
                )
            }
        }
        if (c.paidSen > 0L && summary.displayState == CommitmentDisplayState.PARTIALLY_PAID) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { summary.progressFraction },
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
