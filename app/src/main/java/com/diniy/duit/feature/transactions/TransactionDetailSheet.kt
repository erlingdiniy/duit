package com.diniy.duit.feature.transactions

import com.diniy.duit.core.ui.state.maskedBalance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: FinanceTransaction,
    walletsById: Map<Long, Wallet>,
    isReadOnlyPreview: Boolean = false,
    deleteError: String? = null,
    linkedReceiptCount: Int = 0,
    onOpenReceiptsForTransaction: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onConfirmDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val sourceName = walletsById[transaction.walletId]?.name ?: "Wallet"
    val targetName = transaction.targetWalletId?.let { walletsById[it]?.name ?: "Wallet" }

    val (badgeBg, badgeFg) = when (transaction.type) {
        TransactionType.INCOME -> glass.aquaContainer to glass.onAquaContainer
        TransactionType.EXPENSE -> glass.peachContainer to glass.onPeachContainer
        TransactionType.TRANSFER -> glass.skyBlueContainer to glass.onSkyBlueContainer
    }

    PrismaticModalBottomSheet(
        title = transaction.title,
        subtitle = "${DateTimeFormatters.formatDateOnly(transaction.occurredAtEpochMs)} · ${DateTimeFormatters.formatTimeOnly(transaction.occurredAtEpochMs)}",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Elevated,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrismaticBadge(
                        label = transaction.type.displayName,
                        containerColor = badgeBg,
                        contentColor = badgeFg
                    )
                    if (transaction.type == TransactionType.TRANSFER) {
                        PrismaticBadge(
                            label = "Excluded from cashflow",
                            containerColor = glass.lavenderContainer,
                            contentColor = glass.onLavenderContainer
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (transaction.type) {
                        TransactionType.INCOME -> maskedBalance(transaction.amountSen, includePlusSign = true)
                        TransactionType.EXPENSE -> maskedBalance(-transaction.amountSen)
                        TransactionType.TRANSFER -> maskedBalance(transaction.amountSen)
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (transaction.type == TransactionType.INCOME) {
                        glass.positiveText
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Standard,
                contentPadding = PaddingValues(16.dp)
            ) {
                DetailLine("Category", transaction.category)
                HorizontalDivider(modifier = Modifier.paddingVertical(), color = glass.glassBorderSubtle)
                if (transaction.type == TransactionType.TRANSFER) {
                    DetailLine("From wallet", sourceName)
                    HorizontalDivider(modifier = Modifier.paddingVertical(), color = glass.glassBorderSubtle)
                    DetailLine("To wallet", targetName ?: "Destination wallet")
                } else {
                    DetailLine(
                        label = if (transaction.type == TransactionType.INCOME) "Credited wallet" else "Paid from",
                        value = sourceName
                    )
                }
                if (transaction.note.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.paddingVertical(), color = glass.glassBorderSubtle)
                    DetailLine("Notes", transaction.note)
                }
                if (onOpenReceiptsForTransaction != null && !isReadOnlyPreview) {
                    HorizontalDivider(modifier = Modifier.paddingVertical(), color = glass.glassBorderSubtle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Receipts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (linkedReceiptCount > 0) "$linkedReceiptCount attached" else "None attached",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = onOpenReceiptsForTransaction) {
                            Text(if (linkedReceiptCount > 0) "Manage" else "Attach")
                        }
                    }
                }
            }

            if (deleteError != null) {
                Text(
                    text = deleteError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!isReadOnlyPreview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrismaticButton(
                        text = "Delete",
                        onClick = { showDeleteConfirm = true },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = "Edit",
                        onClick = onEdit,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = {
                Text("Deleting \"${transaction.title}\" will reverse ${maskedBalance(transaction.amountSen)} on the affected wallet balance(s).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onConfirmDelete(transaction.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Modifier.paddingVertical(): Modifier = padding(vertical = 10.dp)
