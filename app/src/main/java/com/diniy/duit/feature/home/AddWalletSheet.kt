package com.diniy.duit.feature.home

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.WalletType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddWalletSheet(
    onSaveWallet: (
        name: String,
        type: WalletType,
        openingBalanceInput: String,
        excludeFromSafeToSpend: Boolean,
        includeInTotalBalance: Boolean,
        accent: PrismaticAccentToken,
        onValidationError: (String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(WalletType.BANK) }
    var openingBalance by rememberSaveable { mutableStateOf("") }
    var excludeFromSafe by rememberSaveable { mutableStateOf(false) }
    var includeInTotal by rememberSaveable { mutableStateOf(true) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val effectiveExcludedFromSafe = excludeFromSafe || selectedType == WalletType.SAVINGS || !includeInTotal

    PrismaticModalBottomSheet(
        title = "Add Wallet",
        subtitle = "Track balances in Malaysian Ringgit (MYR) offline on your device.",
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
                placeholder = "e.g. Maybank Savings, Cash, TNG"
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

            PrismaticMoneyField(
                value = openingBalance,
                onValueChange = {
                    openingBalance = it
                    errorMessage = null
                },
                label = "Opening balance",
                supportingText = "Optional starting balance. Leave blank to start at RM 0.00.",
                errorText = errorMessage
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
                        text = "Uncheck to keep this wallet hidden from your overall balance.",
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
                    )
                    .semantics {
                        toggleableState = if (effectiveExcludedFromSafe) ToggleableState.On else ToggleableState.Off
                    },
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
                        text = "Savings vaults are automatically held separate from spendable cash.",
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
                        val accent = when (selectedType) {
                            WalletType.BANK -> PrismaticAccentToken.AQUA
                            WalletType.EWALLET -> PrismaticAccentToken.LAVENDER
                            WalletType.SAVINGS -> PrismaticAccentToken.SKY_BLUE
                            WalletType.CASH -> PrismaticAccentToken.PEACH
                        }
                        onSaveWallet(
                            name,
                            selectedType,
                            openingBalance,
                            effectiveExcludedFromSafe,
                            includeInTotal,
                            accent
                        ) { err -> errorMessage = err }
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
