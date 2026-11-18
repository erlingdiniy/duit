package com.diniy.duit.feature.savings

import com.diniy.duit.core.ui.state.maskedBalance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.SavingsGoalProgress
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.Wallet

private enum class PendingGoalConfirmAction {
    ARCHIVE,
    DELETE
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalDetailSheet(
    progress: SavingsGoalProgress,
    wallets: List<Wallet>,
    movements: List<SavingsMovement>,
    isPreview: Boolean,
    onEdit: () -> Unit,
    onAction: (SavingsActionMode) -> Unit,
    onComplete: (goalId: Long, onError: (String) -> Unit) -> Unit,
    onArchive: (goalId: Long, onError: (String) -> Unit) -> Unit,
    onDelete: (goalId: Long, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val goal = progress.goal
    val (accentColor, badgeBg, badgeFg) = SavingsIconMapper.accentColors(goal.accentToken)
    val walletById = wallets.associateBy { it.id }

    var pendingConfirm by rememberSaveable { mutableStateOf<PendingGoalConfirmAction?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }

    PrismaticModalBottomSheet(
        title = goal.name,
        subtitle = goal.description.takeIf { it.isNotBlank() } ?: "Savings goal details & history",
        onDismissRequest = onDismiss
    ) {
        if (actionError != null) {
            PrismaticErrorBanner(
                message = actionError!!,
                onDismiss = { actionError = null }
            )
            Spacer(Modifier.height(10.dp))
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Elevated,
            accentTint = accentColor,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = SavingsIconMapper.iconFor(goal.iconKey),
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = maskedBalance(progress.savedSen),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "of ${maskedBalance(goal.targetAmountSen)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PrismaticBadge(
                    label = when (goal.status) {
                        SavingsGoalStatus.ACTIVE -> progress.progressPercentDisplay
                        SavingsGoalStatus.COMPLETED -> "Completed"
                        SavingsGoalStatus.ARCHIVED -> "Archived"
                    },
                    containerColor = badgeBg,
                    contentColor = badgeFg
                )
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = accentColor,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (progress.overfundedSen > 0L) {
                        "Overfunded by ${maskedBalance(progress.overfundedSen)}"
                    } else if (progress.remainingSen == 0L) {
                        "Target reached"
                    } else {
                        "Remaining ${maskedBalance(progress.remainingSen)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.overfundedSen > 0L || progress.remainingSen == 0L) {
                        glass.positiveText
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = goal.targetDateEpochDay?.let {
                        "Due ${DateTimeFormatters.formatEpochDay(it)}"
                    } ?: "No deadline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress.suggestedMonthlySen != null && progress.suggestedMonthlySen > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Suggested pace: ${maskedBalance(progress.suggestedMonthlySen)} / month",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.readableOn(accentColor)
                )
            }
        }

        if (!isPreview) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (goal.status == SavingsGoalStatus.ACTIVE) {
                    PrismaticButton(
                        text = "Add / Link",
                        onClick = { onAction(SavingsActionMode.ALLOCATE) },
                        variant = PrismaticButtonVariant.Primary
                    )
                    PrismaticButton(
                        text = "Move to Vault",
                        onClick = { onAction(SavingsActionMode.TRANSFER_PROTECTED) },
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
                if (progress.savedSen > 0L) {
                    PrismaticButton(
                        text = "Withdraw",
                        onClick = { onAction(SavingsActionMode.WITHDRAW) },
                        variant = PrismaticButtonVariant.Secondary
                    )
                    PrismaticButton(
                        text = "Reassign",
                        onClick = { onAction(SavingsActionMode.REASSIGN) },
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Where it's stored",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        if (progress.allocations.isEmpty()) {
            Text(
                text = "No funds allocated to this goal yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    progress.allocations.forEachIndexed { index, alloc ->
                        val wallet = walletById[alloc.walletId]
                        val isProtected = wallet != null && FinanceSummaryCalculator.isProtectedSavingsWallet(wallet)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wallet?.name ?: "Wallet #${alloc.walletId}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isProtected) "Protected savings wallet" else "Reserved from spending balance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = maskedBalance(alloc.allocatedSen),
                                style = MaterialTheme.typography.titleSmall,
                                color = glass.positiveText
                            )
                        }
                        if (index < progress.allocations.lastIndex) {
                            HorizontalDivider(color = glass.glassBorderSubtle)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Goal history",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))

        val goalMovements = movements.filter {
            it.goalId == goal.id || it.relatedGoalId == goal.id
        }
        if (goalMovements.isEmpty()) {
            Text(
                text = "No movements recorded for this goal yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goalMovements.take(15).forEach { move ->
                    val typeLabel = when (move.type) {
                        SavingsMovementType.ALLOCATE -> "Reserved in wallet"
                        SavingsMovementType.TRANSFER_AND_ALLOCATE -> "Moved to protected vault"
                        SavingsMovementType.LINK_PROTECTED -> "Linked protected savings"
                        SavingsMovementType.WITHDRAW_TO_WALLET -> "Released to wallet"
                        SavingsMovementType.TRANSFER_TO_SPENDING -> "Returned to spending"
                        SavingsMovementType.REASSIGN ->
                            if (move.goalId == goal.id) "Reassigned out" else "Reassigned in"
                    }
                    val walletLabel = buildString {
                        val src = move.sourceWalletId?.let { walletById[it]?.name }
                        val dst = move.targetWalletId?.let { walletById[it]?.name }
                        if (src != null && dst != null && src != dst) append("$src -> $dst")
                        else if (dst != null) append(dst)
                        else if (src != null) append(src)
                    }
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = GlassCardTier.Muted,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                val subtitleText = buildString {
                                    if (walletLabel.isNotBlank()) append("$walletLabel · ")
                                    append(DateTimeFormatters.formatTimestamp(move.occurredAtEpochMs))
                                    if (move.note.isNotBlank()) append(" · ${move.note}")
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            val isOutflow = (move.type == SavingsMovementType.WITHDRAW_TO_WALLET ||
                                move.type == SavingsMovementType.TRANSFER_TO_SPENDING) ||
                                (move.type == SavingsMovementType.REASSIGN && move.goalId == goal.id)
                            Text(
                                text = (if (isOutflow) "-" else "+") + maskedBalance(move.amountSen),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isOutflow) glass.cautionText else glass.positiveText
                            )
                        }
                    }
                }
            }
        }

        if (!isPreview) {
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrismaticButton(
                    text = "Edit Goal",
                    onClick = onEdit,
                    variant = PrismaticButtonVariant.Secondary
                )
                if (goal.status == SavingsGoalStatus.ACTIVE) {
                    PrismaticButton(
                        text = "Mark Completed",
                        onClick = {
                            onComplete(goal.id) { actionError = it }
                        },
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
                if (goal.status != SavingsGoalStatus.ARCHIVED) {
                    PrismaticButton(
                        text = "Archive",
                        onClick = { pendingConfirm = PendingGoalConfirmAction.ARCHIVE },
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                PrismaticButton(
                    text = "Delete",
                    onClick = { pendingConfirm = PendingGoalConfirmAction.DELETE },
                    variant = PrismaticButtonVariant.Ghost
                )
            }
        }
    }

    pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = {
                Text(
                    if (confirm == PendingGoalConfirmAction.ARCHIVE) {
                        "Archive \"${goal.name}\"?"
                    } else {
                        "Delete \"${goal.name}\"?"
                    }
                )
            },
            text = {
                Text(
                    if (confirm == PendingGoalConfirmAction.ARCHIVE) {
                        if (progress.savedSen > 0L) {
                            "Archiving releases ${maskedBalance(progress.savedSen)} allocated to this goal back into unreserved wallet funds and preserves its history."
                        } else {
                            "Archived goals are hidden from your active goal list while preserving history."
                        }
                    } else {
                        if (progress.savedSen > 0L) {
                            "Deleting permanently removes this goal and releases ${maskedBalance(progress.savedSen)} back into unreserved wallet funds."
                        } else {
                            "This permanently removes the savings goal and its audit history. Wallet balances are unaffected."
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirm = null
                        if (confirm == PendingGoalConfirmAction.ARCHIVE) {
                            onArchive(goal.id) { actionError = it }
                        } else {
                            onDelete(goal.id) { actionError = it }
                        }
                    }
                ) {
                    Text(if (confirm == PendingGoalConfirmAction.ARCHIVE) "Archive" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
