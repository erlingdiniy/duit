package com.diniy.duit.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.diniy.duit.core.ui.state.maskedBalance
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletDetailSheet(
    wallet: Wallet,
    walletTransactions: List<FinanceTransaction>,
    walletsById: Map<Long, Wallet>,
    isReadOnlyPreview: Boolean = false,
    actionError: String? = null,
    onEditWallet: () -> Unit,
    onToggleArchive: (Boolean) -> Unit,
    onDeleteWallet: () -> Unit,
    onSelectTransaction: (FinanceTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val hasHistory = walletTransactions.isNotEmpty()

    PrismaticModalBottomSheet(
        title = wallet.name,
        subtitle = wallet.type.displayName,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Elevated,
                contentPadding = PaddingValues(18.dp)
            ) {
                Text(
                    text = "Current Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskedBalance(wallet.balanceSen),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticBadge(
                        label = if (wallet.isIncludedInTotalBalance) "In Total Balance" else "Hidden from Total",
                        containerColor = if (wallet.isIncludedInTotalBalance) glass.aquaContainer else glass.peachContainer,
                        contentColor = if (wallet.isIncludedInTotalBalance) glass.onAquaContainer else glass.onPeachContainer
                    )
                    PrismaticBadge(
                        label = if (wallet.isExcludedFromSafeToSpend) "Excluded from Safe to Spend" else "In Safe to Spend",
                        containerColor = glass.skyBlueContainer,
                        contentColor = glass.onSkyBlueContainer
                    )
                    if (wallet.isArchived) {
                        PrismaticBadge(
                            label = "Archived",
                            containerColor = glass.peachContainer,
                            contentColor = glass.onPeachContainer
                        )
                    }
                }
            }

            if (actionError != null) {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!isReadOnlyPreview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticButton(
                        text = "Edit",
                        onClick = onEditWallet,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = if (wallet.isArchived) "Restore" else "Archive",
                        onClick = { onToggleArchive(!wallet.isArchived) },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                PrismaticButton(
                    text = if (hasHistory) "Delete Disabled (Has History)" else "Delete Wallet",
                    onClick = { showDeleteConfirm = true },
                    enabled = !hasHistory,
                    variant = PrismaticButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasHistory) {
                    Text(
                        text = "Wallets with transaction history cannot be deleted. Archive this wallet to hide it safely without losing records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Wallet History (${walletTransactions.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (walletTransactions.isEmpty()) {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Muted,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "No transactions recorded for this wallet yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    walletTransactions.forEach { tx ->
                        val isIncomingTransfer = tx.type == TransactionType.TRANSFER && tx.targetWalletId == wallet.id
                        val signedDisplay = when {
                            tx.type == TransactionType.INCOME || isIncomingTransfer ->
                                maskedBalance(tx.amountSen, includePlusSign = true)
                            else ->
                                maskedBalance(-tx.amountSen)
                        }
                        val amountColor = if (tx.type == TransactionType.INCOME || isIncomingTransfer) {
                            glass.positiveText
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                        PrismaticGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tier = GlassCardTier.Standard,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            onClick = if (!isReadOnlyPreview) ({ onSelectTransaction(tx) }) else null,
                            onClickLabel = "Inspect ${tx.title}"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val detail = if (tx.type == TransactionType.TRANSFER) {
                                        val otherId = if (tx.walletId == wallet.id) tx.targetWalletId else tx.walletId
                                        val otherName = otherId?.let { walletsById[it]?.name } ?: "Wallet"
                                        if (isIncomingTransfer) "From $otherName" else "To $otherName"
                                    } else {
                                        tx.category
                                    }
                                    Text(
                                        text = "$detail · ${DateTimeFormatters.formatTimestamp(tx.occurredAtEpochMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = signedDisplay,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = amountColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Wallet?") },
            text = {
                Text("Delete \"${wallet.name}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteWallet()
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
