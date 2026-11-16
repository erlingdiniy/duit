package com.diniy.duit.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditWalletSheet(
    wallet: Wallet,
    transactionCount: Int,
    onSave: (
        updatedWallet: Wallet,
        onError: (String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(wallet.id) { mutableStateOf(wallet.name) }
    var selectedType by rememberSaveable(wallet.id) { mutableStateOf(wallet.type) }
    var selectedAccent by rememberSaveable(wallet.id) { mutableStateOf(wallet.accentToken) }
    var openingBalanceInput by rememberSaveable(wallet.id) {
        mutableStateOf(MyrFormatter.formatInputValue(wallet.balanceSen))
    }
    var includeInTotal by rememberSaveable(wallet.id) {
        mutableStateOf(wallet.isIncludedInTotalBalance)
    }
    var excludeFromSafe by rememberSaveable(wallet.id) {
        mutableStateOf(wallet.isExcludedFromSafeToSpend)
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val canEditOpeningBalance = transactionCount == 0
    val effectiveExcludedFromSafe = excludeFromSafe || selectedType == WalletType.SAVINGS || !includeInTotal

    PrismaticModalBottomSheet(
        title = "Edit Wallet",
        subtitle = if (canEditOpeningBalance) {
            "Update wallet details, inclusion rules, or starting balance."
        } else {
            "This wallet has $transactionCount transaction(s); balance updates are driven by your activity."
        },
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = "Wallet name",
                errorText = errorMessage
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Wallet type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WalletType.entries.forEach { walletType ->
                        FilterChip(
                            selected = selectedType == walletType,
                            onClick = { selectedType = walletType },
                            label = { Text(walletType.displayName) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Accent color",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticAccentToken.entries.forEach { token ->
                        FilterChip(
                            selected = selectedAccent == token,
                            onClick = { selectedAccent = token },
                            label = {
                                Text(
                                    token.name.lowercase().replace("_", " ")
                                        .replaceFirstChar { it.uppercase() }
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            PrismaticMoneyField(
                value = openingBalanceInput,
                onValueChange = {
                    if (canEditOpeningBalance) {
                        openingBalanceInput = it
                        errorMessage = null
                    }
                },
                enabled = canEditOpeningBalance,
                label = if (canEditOpeningBalance) "Opening balance" else "Current balance",
                supportingText = if (canEditOpeningBalance) {
                    "Editable while this wallet has no recorded transactions."
                } else {
                    "Locked to preserve transaction audit integrity. Record Income, Expense, or Transfer to change balance."
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = includeInTotal,
                        role = Role.Checkbox,
                        onValueChange = { includeInTotal = it }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeInTotal,
                    onCheckedChange = null
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include in Total Balance",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Turn off to hide this wallet from Total Balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = effectiveExcludedFromSafe,
                        enabled = selectedType != WalletType.SAVINGS && includeInTotal,
                        role = Role.Checkbox,
                        onValueChange = { excludeFromSafe = it }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = effectiveExcludedFromSafe,
                    onCheckedChange = null,
                    enabled = selectedType != WalletType.SAVINGS && includeInTotal
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exclude from Safe to Spend",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Protected funds stay separate from spendable cash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismaticButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                PrismaticButton(
                    text = "Save Wallet",
                    onClick = {
                        val trimmedName = name.trim()
                        if (trimmedName.isEmpty()) {
                            errorMessage = "Enter a wallet name."
                            return@PrismaticButton
                        }
                        val resolvedBalance = if (canEditOpeningBalance) {
                            if (openingBalanceInput.isBlank()) {
                                0L
                            } else {
                                MyrFormatter.parseToSen(openingBalanceInput) ?: run {
                                    errorMessage = "Enter a valid RM amount."
                                    return@PrismaticButton
                                }
                            }
                        } else {
                            wallet.balanceSen
                        }

                        onSave(
                            wallet.copy(
                                name = trimmedName,
                                type = selectedType,
                                accentToken = selectedAccent,
                                balanceSen = resolvedBalance,
                                isIncludedInTotalBalance = includeInTotal,
                                isExcludedFromSafeToSpend = effectiveExcludedFromSafe
                            )
                        ) { err -> errorMessage = err }
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
