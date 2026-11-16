package com.diniy.duit.feature.savings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.Wallet
import java.util.UUID

enum class SavingsActionMode(val label: String, val subtitle: String) {
    ALLOCATE(
        label = "Add / Link",
        subtitle = "Reserve cash in a spending wallet or link unallocated savings inside a protected wallet."
    ),
    TRANSFER_PROTECTED(
        label = "Move to Vault",
        subtitle = "Transfer from a spending wallet into a protected savings wallet and assign it to this goal."
    ),
    WITHDRAW(
        label = "Withdraw",
        subtitle = "Release reserved funds back into the wallet's unreserved balance."
    ),
    TRANSFER_TO_SPENDING(
        label = "Move to Spending",
        subtitle = "Transfer protected goal funds out into an ordinary spending wallet."
    ),
    REASSIGN(
        label = "Reassign",
        subtitle = "Move reserved savings inside the same wallet to another savings goal."
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavingsActionSheet(
    initialMode: SavingsActionMode,
    initialGoalId: Long?,
    goals: List<SavingsGoal>,
    allocations: List<SavingsAllocation>,
    wallets: List<Wallet>,
    onAllocate: (goalId: Long, walletId: Long, amountSen: Long, note: String, submissionId: String, onError: (String) -> Unit) -> Unit,
    onTransferAndAllocate: (goalId: Long, sourceWalletId: Long, protectedWalletId: Long, amountSen: Long, note: String, submissionId: String, onError: (String) -> Unit) -> Unit,
    onWithdraw: (goalId: Long, walletId: Long, amountSen: Long, note: String, submissionId: String, onError: (String) -> Unit) -> Unit,
    onTransferToSpending: (goalId: Long, protectedWalletId: Long, spendingWalletId: Long, amountSen: Long, note: String, submissionId: String, onError: (String) -> Unit) -> Unit,
    onReassign: (fromGoalId: Long, toGoalId: Long, walletId: Long, amountSen: Long, note: String, submissionId: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val activeWallets = wallets.filter { !it.isArchived }
    val protectedWallets = activeWallets.filter { FinanceSummaryCalculator.isProtectedSavingsWallet(it) }
    val spendingWallets = activeWallets.filter { !FinanceSummaryCalculator.isProtectedSavingsWallet(it) }
    val activeGoals = goals.filter { it.status == SavingsGoalStatus.ACTIVE }

    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedGoalId by rememberSaveable {
        mutableStateOf(initialGoalId ?: activeGoals.firstOrNull()?.id ?: goals.firstOrNull()?.id)
    }

    val selectablePrimaryGoals = if (mode == SavingsActionMode.ALLOCATE || mode == SavingsActionMode.TRANSFER_PROTECTED) {
        activeGoals.ifEmpty { goals.filter { it.status != SavingsGoalStatus.ARCHIVED } }
    } else {
        val withAllocs = goals.filter { g -> allocations.any { it.goalId == g.id && it.allocatedSen > 0L } }
        withAllocs.ifEmpty { activeGoals }
    }

    val resolvedGoalId = if (selectablePrimaryGoals.any { it.id == selectedGoalId }) {
        selectedGoalId
    } else {
        selectablePrimaryGoals.firstOrNull()?.id
    }

    var primaryWalletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var secondaryWalletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reassignTargetGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var noteInput by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val submissionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    fun unallocatedSenFor(wallet: Wallet): Long {
        val allocatedInWallet = allocations.filter { it.walletId == wallet.id }.sumOf { it.allocatedSen }
        return (wallet.balanceSen - allocatedInWallet).coerceAtLeast(0L)
    }

    fun goalAllocationInWallet(goalId: Long?, walletId: Long?): Long {
        if (goalId == null || walletId == null) return 0L
        return allocations.firstOrNull { it.goalId == goalId && it.walletId == walletId }?.allocatedSen ?: 0L
    }

    val candidatePrimaryWallets = when (mode) {
        SavingsActionMode.ALLOCATE -> activeWallets
        SavingsActionMode.TRANSFER_PROTECTED -> spendingWallets
        SavingsActionMode.WITHDRAW -> {
            val goalAllocWallets = allocations
                .filter { it.goalId == resolvedGoalId && it.allocatedSen > 0L }
                .map { it.walletId }
                .toSet()
            activeWallets.filter { it.id in goalAllocWallets }
        }
        SavingsActionMode.TRANSFER_TO_SPENDING -> {
            val goalAllocWallets = allocations
                .filter { it.goalId == resolvedGoalId && it.allocatedSen > 0L }
                .map { it.walletId }
                .toSet()
            protectedWallets.filter { it.id in goalAllocWallets }
        }
        SavingsActionMode.REASSIGN -> {
            val goalAllocWallets = allocations
                .filter { it.goalId == resolvedGoalId && it.allocatedSen > 0L }
                .map { it.walletId }
                .toSet()
            activeWallets.filter { it.id in goalAllocWallets }
        }
    }

    val resolvedPrimaryWallet = candidatePrimaryWallets.firstOrNull { it.id == primaryWalletId }
        ?: candidatePrimaryWallets.firstOrNull()

    val candidateSecondaryWallets = when (mode) {
        SavingsActionMode.TRANSFER_PROTECTED -> protectedWallets
        SavingsActionMode.TRANSFER_TO_SPENDING -> spendingWallets
        else -> emptyList()
    }
    val resolvedSecondaryWallet = candidateSecondaryWallets.firstOrNull { it.id == secondaryWalletId }
        ?: candidateSecondaryWallets.firstOrNull()

    val candidateTargetGoals = activeGoals.filter { it.id != resolvedGoalId }
    val resolvedTargetGoal = candidateTargetGoals.firstOrNull { it.id == reassignTargetGoalId }
        ?: candidateTargetGoals.firstOrNull()

    val availableCeilingSen = when (mode) {
        SavingsActionMode.ALLOCATE, SavingsActionMode.TRANSFER_PROTECTED ->
            resolvedPrimaryWallet?.let { unallocatedSenFor(it) } ?: 0L
        SavingsActionMode.WITHDRAW, SavingsActionMode.TRANSFER_TO_SPENDING, SavingsActionMode.REASSIGN ->
            goalAllocationInWallet(resolvedGoalId, resolvedPrimaryWallet?.id)
    }

    PrismaticModalBottomSheet(
        title = "Move Savings Funds",
        subtitle = mode.subtitle,
        onDismissRequest = onDismiss
    ) {
        if (errorMessage != null) {
            PrismaticErrorBanner(
                message = errorMessage!!,
                onDismiss = { errorMessage = null }
            )
            Spacer(Modifier.height(10.dp))
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SavingsActionMode.entries.forEach { entry ->
                FilterChip(
                    selected = mode == entry,
                    onClick = {
                        mode = entry
                        errorMessage = null
                    },
                    label = { Text(entry.label) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (selectablePrimaryGoals.isEmpty()) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (mode == SavingsActionMode.WITHDRAW ||
                        mode == SavingsActionMode.TRANSFER_TO_SPENDING ||
                        mode == SavingsActionMode.REASSIGN
                    ) {
                        "This goal does not have any allocated funds yet."
                    } else {
                        "Create an active savings goal first before adding funds."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            PrismaticButton(
                text = "Close",
                onClick = onDismiss,
                variant = PrismaticButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
            return@PrismaticModalBottomSheet
        }

        Text(
            text = if (mode == SavingsActionMode.REASSIGN) "From goal" else "Savings goal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            selectablePrimaryGoals.forEach { goal ->
                FilterChip(
                    selected = resolvedGoalId == goal.id,
                    onClick = {
                        selectedGoalId = goal.id
                        errorMessage = null
                    },
                    label = { Text(goal.name) }
                )
            }
        }

        if (mode == SavingsActionMode.REASSIGN) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "To destination goal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (candidateTargetGoals.isEmpty()) {
                Text(
                    text = "Create another active savings goal to reassign funds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.cautionText
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    candidateTargetGoals.forEach { goal ->
                        FilterChip(
                            selected = resolvedTargetGoal?.id == goal.id,
                            onClick = {
                                reassignTargetGoalId = goal.id
                                errorMessage = null
                            },
                            label = { Text(goal.name) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val primaryWalletLabel = when (mode) {
            SavingsActionMode.ALLOCATE -> "Source wallet"
            SavingsActionMode.TRANSFER_PROTECTED -> "From spending wallet"
            SavingsActionMode.WITHDRAW -> "Wallet holding allocation"
            SavingsActionMode.TRANSFER_TO_SPENDING -> "From protected savings wallet"
            SavingsActionMode.REASSIGN -> "Shared wallet"
        }
        Text(
            text = primaryWalletLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        if (candidatePrimaryWallets.isEmpty()) {
            Text(
                text = when (mode) {
                    SavingsActionMode.TRANSFER_PROTECTED -> "Add a spending wallet (Bank, eWallet, or Cash) first."
                    SavingsActionMode.TRANSFER_TO_SPENDING -> "No funds for this goal are currently stored inside a Protected Savings wallet."
                    SavingsActionMode.WITHDRAW, SavingsActionMode.REASSIGN -> "No wallet currently holds funds for this goal."
                    SavingsActionMode.ALLOCATE -> "Add an active wallet first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = glass.cautionText
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                candidatePrimaryWallets.forEach { wallet ->
                    val amountTag = when (mode) {
                        SavingsActionMode.ALLOCATE, SavingsActionMode.TRANSFER_PROTECTED ->
                            MyrFormatter.format(unallocatedSenFor(wallet))
                        SavingsActionMode.WITHDRAW, SavingsActionMode.TRANSFER_TO_SPENDING, SavingsActionMode.REASSIGN ->
                            MyrFormatter.format(goalAllocationInWallet(resolvedGoalId, wallet.id))
                    }
                    FilterChip(
                        selected = resolvedPrimaryWallet?.id == wallet.id,
                        onClick = {
                            primaryWalletId = wallet.id
                            errorMessage = null
                        },
                        label = { Text("${wallet.name} ($amountTag)") }
                    )
                }
            }
        }

        if (mode == SavingsActionMode.TRANSFER_PROTECTED || mode == SavingsActionMode.TRANSFER_TO_SPENDING) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (mode == SavingsActionMode.TRANSFER_PROTECTED) {
                    "Destination protected savings wallet"
                } else {
                    "Destination spending wallet"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (candidateSecondaryWallets.isEmpty()) {
                Text(
                    text = if (mode == SavingsActionMode.TRANSFER_PROTECTED) {
                        "Create a Savings wallet (or mark a wallet as Protected Savings) first."
                    } else {
                        "Create a spending wallet first."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.cautionText
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    candidateSecondaryWallets.forEach { wallet ->
                        FilterChip(
                            selected = resolvedSecondaryWallet?.id == wallet.id,
                            onClick = {
                                secondaryWalletId = wallet.id
                                errorMessage = null
                            },
                            label = { Text("${wallet.name} (${MyrFormatter.format(wallet.balanceSen)})") }
                        )
                    }
                }
            }
        }

        if (resolvedPrimaryWallet != null) {
            Spacer(Modifier.height(10.dp))
            val isProtectedPrimary = FinanceSummaryCalculator.isProtectedSavingsWallet(resolvedPrimaryWallet)
            val explanation = when (mode) {
                SavingsActionMode.ALLOCATE -> if (isProtectedPrimary) {
                    "Links unallocated funds already inside ${resolvedPrimaryWallet.name} to this goal without deducting Safe to Spend again."
                } else {
                    "Money stays in ${resolvedPrimaryWallet.name} and is reserved from Safe to Spend."
                }
                SavingsActionMode.TRANSFER_PROTECTED ->
                    "Creates an internal transfer out of ${resolvedPrimaryWallet.name}. Total Balance stays unchanged."
                SavingsActionMode.WITHDRAW -> if (isProtectedPrimary) {
                    "Unlinks funds back to ${resolvedPrimaryWallet.name}'s unallocated pool (still protected inside Savings)."
                } else {
                    "Releases reserved funds in ${resolvedPrimaryWallet.name} back into Safe to Spend."
                }
                SavingsActionMode.TRANSFER_TO_SPENDING ->
                    "Transfers funds from ${resolvedPrimaryWallet.name} to spending and reduces this goal's saved total."
                SavingsActionMode.REASSIGN ->
                    "Moves goal ownership inside ${resolvedPrimaryWallet.name} without altering wallet balances."
            }
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Available: ${MyrFormatter.format(availableCeilingSen)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.positiveText
                    )
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrismaticTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    errorMessage = null
                },
                label = "Amount",
                placeholder = "200.00",
                prefixText = "RM ",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            if (availableCeilingSen > 0L) {
                PrismaticButton(
                    text = "Max",
                    onClick = {
                        amountInput = MyrFormatter.formatInputValue(availableCeilingSen)
                        errorMessage = null
                    },
                    variant = PrismaticButtonVariant.Secondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        PrismaticTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = "Note (optional)",
            placeholder = "e.g., Monthly payday top-up",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            )
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrismaticButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = PrismaticButtonVariant.Secondary,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f)
            )
            PrismaticButton(
                text = when (mode) {
                    SavingsActionMode.ALLOCATE -> "Confirm Allocation"
                    SavingsActionMode.TRANSFER_PROTECTED -> "Move & Save"
                    SavingsActionMode.WITHDRAW -> "Withdraw"
                    SavingsActionMode.TRANSFER_TO_SPENDING -> "Move to Spending"
                    SavingsActionMode.REASSIGN -> "Reassign Funds"
                },
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    val goalId = resolvedGoalId
                    if (goalId == null) {
                        errorMessage = "Select a savings goal."
                        return@PrismaticButton
                    }
                    val primary = resolvedPrimaryWallet
                    if (primary == null) {
                        errorMessage = "Select an eligible wallet."
                        return@PrismaticButton
                    }
                    val amountSen = MyrFormatter.parseToSen(amountInput)
                    if (amountSen == null || amountSen <= 0L) {
                        errorMessage = "Enter a valid amount greater than RM 0.00."
                        return@PrismaticButton
                    }
                    val cleanNote = noteInput.trim()

                    when (mode) {
                        SavingsActionMode.ALLOCATE -> {
                            isSubmitting = true
                            onAllocate(goalId, primary.id, amountSen, cleanNote, submissionId) { msg ->
                                isSubmitting = false
                                errorMessage = msg
                            }
                        }
                        SavingsActionMode.TRANSFER_PROTECTED -> {
                            val secondary = resolvedSecondaryWallet
                            if (secondary == null) {
                                errorMessage = "Select a protected savings wallet."
                                return@PrismaticButton
                            }
                            isSubmitting = true
                            onTransferAndAllocate(goalId, primary.id, secondary.id, amountSen, cleanNote, submissionId) { msg ->
                                isSubmitting = false
                                errorMessage = msg
                            }
                        }
                        SavingsActionMode.WITHDRAW -> {
                            isSubmitting = true
                            onWithdraw(goalId, primary.id, amountSen, cleanNote, submissionId) { msg ->
                                isSubmitting = false
                                errorMessage = msg
                            }
                        }
                        SavingsActionMode.TRANSFER_TO_SPENDING -> {
                            val secondary = resolvedSecondaryWallet
                            if (secondary == null) {
                                errorMessage = "Select a destination spending wallet."
                                return@PrismaticButton
                            }
                            isSubmitting = true
                            onTransferToSpending(goalId, primary.id, secondary.id, amountSen, cleanNote, submissionId) { msg ->
                                isSubmitting = false
                                errorMessage = msg
                            }
                        }
                        SavingsActionMode.REASSIGN -> {
                            val destGoal = resolvedTargetGoal
                            if (destGoal == null) {
                                errorMessage = "Select another active goal to reassign funds to."
                                return@PrismaticButton
                            }
                            isSubmitting = true
                            onReassign(goalId, destGoal.id, primary.id, amountSen, cleanNote, submissionId) { msg ->
                                isSubmitting = false
                                errorMessage = msg
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}
