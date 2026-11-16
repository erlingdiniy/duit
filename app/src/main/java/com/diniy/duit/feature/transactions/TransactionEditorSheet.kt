package com.diniy.duit.feature.transactions

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Full-featured modal bottom sheet for creating or editing Income, Expense, and Transfer entries.
 * Uses integer minor units (sen) and unique submissionId tokens to prevent duplicate submissions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
    wallets: List<Wallet>,
    categories: List<Category>,
    initialType: TransactionType = TransactionType.EXPENSE,
    existingTransaction: FinanceTransaction? = null,
    defaultWalletId: Long? = null,
    isSubmitting: Boolean = false,
    externalError: String? = null,
    onManageCategories: (TransactionType) -> Unit,
    onSubmit: (
        transaction: FinanceTransaction,
        startNewSalaryCycle: Boolean,
        salaryCycleLabel: String,
        onError: (String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val view = LocalView.current
    val activeWallets = remember(wallets, existingTransaction) {
        wallets.filter {
            !it.isArchived ||
                it.id == existingTransaction?.walletId ||
                it.id == existingTransaction?.targetWalletId
        }
    }

    val isEditMode = existingTransaction != null
    val sessionSubmissionId = rememberSaveable(existingTransaction?.id) {
        existingTransaction?.submissionId ?: UUID.randomUUID().toString()
    }

    var selectedType by rememberSaveable(existingTransaction?.id) {
        mutableStateOf(existingTransaction?.type ?: initialType)
    }
    var amountInput by rememberSaveable(existingTransaction?.id) {
        mutableStateOf(
            existingTransaction?.let { MyrFormatter.formatInputValue(it.amountSen) } ?: ""
        )
    }
    var titleInput by rememberSaveable(existingTransaction?.id) {
        mutableStateOf(existingTransaction?.title ?: "")
    }
    var noteInput by rememberSaveable(existingTransaction?.id) {
        mutableStateOf(existingTransaction?.note ?: "")
    }
    var selectedWalletId by rememberSaveable(existingTransaction?.id, activeWallets.size, defaultWalletId) {
        mutableLongStateOf(
            existingTransaction?.walletId
                ?: defaultWalletId?.let { defId -> activeWallets.firstOrNull { it.id == defId }?.id }
                ?: activeWallets.firstOrNull { !it.isExcludedFromSafeToSpend }?.id
                ?: activeWallets.firstOrNull()?.id
                ?: 0L
        )
    }
    var targetWalletId by rememberSaveable(existingTransaction?.id, activeWallets.size) {
        mutableLongStateOf(
            existingTransaction?.targetWalletId
                ?: activeWallets.firstOrNull { it.id != selectedWalletId }?.id
                ?: 0L
        )
    }

    val availableCategories = remember(categories, selectedType) {
        categories.filter { it.type == selectedType && !it.isArchived }
    }

    var selectedCategoryId by rememberSaveable(existingTransaction?.id, selectedType) {
        val matched = existingTransaction?.takeIf { it.type == selectedType }?.let { tx ->
            availableCategories.firstOrNull {
                (tx.categoryId != null && it.id == tx.categoryId) || it.name == tx.category
            }?.id
        }
        mutableStateOf(matched ?: availableCategories.firstOrNull()?.id)
    }

    var occurredAtEpochMs by rememberSaveable(existingTransaction?.id) {
        mutableLongStateOf(existingTransaction?.occurredAtEpochMs ?: System.currentTimeMillis())
    }
    var startNewSalaryCycle by rememberSaveable(existingTransaction?.id) { mutableStateOf(false) }
    var salaryCycleLabel by rememberSaveable(existingTransaction?.id) { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val selectedWallet = activeWallets.firstOrNull { it.id == selectedWalletId }
    val selectedCategory = availableCategories.firstOrNull { it.id == selectedCategoryId }
    val displayError = localError ?: externalError

    PrismaticModalBottomSheet(
        title = if (isEditMode) "Edit Transaction" else "New Transaction",
        subtitle = when (selectedType) {
            TransactionType.EXPENSE -> "Deducts from the selected wallet balance."
            TransactionType.INCOME -> "Adds directly to the selected wallet balance."
            TransactionType.TRANSFER -> "Moves funds between wallets without changing income or expenses."
        },
        onDismissRequest = { if (!isSubmitting) onDismiss() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (activeWallets.isEmpty()) {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Muted
                ) {
                    Text(
                        text = "No active wallets available",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add or restore a wallet before recording transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrismaticButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
                return@PrismaticModalBottomSheet
            }

            // Transaction Type Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (selectedType != type) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedType = type
                                localError = null
                                if (type == TransactionType.TRANSFER && targetWalletId == selectedWalletId) {
                                    targetWalletId = activeWallets.firstOrNull { it.id != selectedWalletId }?.id ?: 0L
                                }
                                if (type != TransactionType.TRANSFER) {
                                    selectedCategoryId = categories.firstOrNull {
                                        it.type == type && !it.isArchived
                                    }?.id
                                }
                            }
                        },
                        label = { Text(type.displayName) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = glass.lavenderContainer,
                            selectedLabelColor = glass.onLavenderContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    )
                }
            }

            // Amount Field
            val walletHint = selectedWallet?.let {
                "Balance in ${it.name}: ${MyrFormatter.format(it.balanceSen)}"
            }
            PrismaticMoneyField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    localError = null
                },
                label = "Amount",
                supportingText = walletHint,
                errorText = displayError
            )

            // Title Field
            PrismaticTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it
                    localError = null
                },
                label = "Title",
                placeholder = when (selectedType) {
                    TransactionType.EXPENSE -> "e.g. Lunch, Petrol, Electricity"
                    TransactionType.INCOME -> "e.g. Monthly Salary, Commission"
                    TransactionType.TRANSFER -> "e.g. Top up e-Wallet"
                }
            )

            // Source / Destination Wallets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when (selectedType) {
                        TransactionType.EXPENSE -> "Pay from wallet"
                        TransactionType.INCOME -> "Deposit to wallet"
                        TransactionType.TRANSFER -> "From wallet"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeWallets.forEach { wallet ->
                        FilterChip(
                            selected = selectedWalletId == wallet.id,
                            onClick = {
                                selectedWalletId = wallet.id
                                localError = null
                                if (selectedType == TransactionType.TRANSFER && targetWalletId == wallet.id) {
                                    targetWalletId = activeWallets.firstOrNull { it.id != wallet.id }?.id ?: 0L
                                }
                            },
                            label = { Text("${wallet.name} (${MyrFormatter.format(wallet.balanceSen)})") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (selectedType == TransactionType.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "To wallet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeWallets.size < 2) {
                        Text(
                            text = "Add a second wallet to transfer funds between wallets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeWallets.forEach { wallet ->
                                val isSameAsSource = wallet.id == selectedWalletId
                                FilterChip(
                                    selected = targetWalletId == wallet.id && !isSameAsSource,
                                    enabled = !isSameAsSource,
                                    onClick = {
                                        targetWalletId = wallet.id
                                        localError = null
                                    },
                                    label = { Text(wallet.name) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Category selector for Income / Expense
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PrismaticButton(
                            text = "Manage",
                            onClick = { onManageCategories(selectedType) },
                            variant = PrismaticButtonVariant.Ghost,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = cat.id
                                    localError = null
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = CategoryIconMapper.iconFor(cat.iconKey, selectedType),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(cat.name) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }

            // Date & Time Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Date & Time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticButton(
                        text = DateTimeFormatters.formatDateOnly(occurredAtEpochMs),
                        onClick = { showDatePicker = true },
                        variant = PrismaticButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = DateTimeFormatters.formatTimeOnly(occurredAtEpochMs),
                        onClick = { showTimePicker = true },
                        variant = PrismaticButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val isSalaryIncomeNew = !isEditMode &&
                selectedType == TransactionType.INCOME &&
                (selectedCategory?.name?.equals("Salary", ignoreCase = true) == true)

            if (isSalaryIncomeNew) {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Muted,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = startNewSalaryCycle,
                                role = Role.Switch,
                                onValueChange = { startNewSalaryCycle = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save & Start New Cycle",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Begins a new salary cycle on this receipt date and closes the previous cycle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = startNewSalaryCycle,
                            onCheckedChange = null
                        )
                    }
                    if (startNewSalaryCycle) {
                        Spacer(Modifier.height(10.dp))
                        val suggestedDate = DateTimeFormatters.toLocalDate(occurredAtEpochMs)
                        val defaultCycleHint = "${suggestedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${suggestedDate.year} Cycle"
                        PrismaticTextField(
                            value = salaryCycleLabel,
                            onValueChange = { salaryCycleLabel = it },
                            label = "Cycle label (optional)",
                            placeholder = defaultCycleHint
                        )
                    }
                }
            }

            // Optional Note
            PrismaticTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = "Notes (optional)",
                placeholder = "Reference, merchant, or memo",
                singleLine = false
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismaticButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                PrismaticButton(
                    text = when {
                        isEditMode -> "Save Changes"
                        isSalaryIncomeNew && startNewSalaryCycle -> "Save & Start Cycle"
                        else -> "Save"
                    },
                    isLoading = isSubmitting,
                    enabled = !isSubmitting && (selectedType != TransactionType.TRANSFER || activeWallets.size >= 2),
                    onClick = {
                        val parsedAmount = MyrFormatter.parseToSen(amountInput)
                        if (parsedAmount == null || parsedAmount <= 0L) {
                            localError = "Enter an amount greater than RM 0.00."
                            return@PrismaticButton
                        }
                        if (selectedWallet == null) {
                            localError = "Select a wallet."
                            return@PrismaticButton
                        }
                        if (selectedType == TransactionType.TRANSFER) {
                            if (targetWalletId == 0L || targetWalletId == selectedWalletId) {
                                localError = "Select a different destination wallet."
                                return@PrismaticButton
                            }
                        }
                        val resolvedCategoryName = if (selectedType == TransactionType.TRANSFER) {
                            "Wallet Transfer"
                        } else {
                            selectedCategory?.name
                                ?: existingTransaction?.category
                                ?: selectedType.displayName
                        }
                        val resolvedTitle = titleInput.trim().ifEmpty {
                            if (selectedType == TransactionType.TRANSFER) {
                                val targetName = activeWallets.firstOrNull { it.id == targetWalletId }?.name ?: "Wallet"
                                "Transfer to $targetName"
                            } else {
                                resolvedCategoryName
                            }
                        }

                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSubmit(
                            FinanceTransaction(
                                id = existingTransaction?.id ?: 0L,
                                walletId = selectedWalletId,
                                targetWalletId = if (selectedType == TransactionType.TRANSFER) targetWalletId else null,
                                type = selectedType,
                                amountSen = parsedAmount,
                                category = resolvedCategoryName,
                                title = resolvedTitle,
                                note = noteInput.trim(),
                                commitmentId = existingTransaction?.commitmentId,
                                occurredAtEpochMs = occurredAtEpochMs,
                                createdAtEpochMs = existingTransaction?.createdAtEpochMs ?: System.currentTimeMillis(),
                                categoryId = if (selectedType == TransactionType.TRANSFER) null else selectedCategory?.id,
                                submissionId = sessionSubmissionId
                            ),
                            isSalaryIncomeNew && startNewSalaryCycle,
                            salaryCycleLabel
                        ) { err -> localError = err }
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showDatePicker) {
        val currentZoned = Instant.ofEpochMilli(occurredAtEpochMs).atZone(ZoneId.systemDefault())
        val initialUtcMillis = currentZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedUtc = datePickerState.selectedDateMillis
                        if (pickedUtc != null) {
                            val pickedDate = Instant.ofEpochMilli(pickedUtc).atZone(ZoneOffset.UTC).toLocalDate()
                            val combined = LocalDateTime.of(pickedDate, currentZoned.toLocalTime())
                            occurredAtEpochMs = combined.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentZoned = Instant.ofEpochMilli(occurredAtEpochMs).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = currentZoned.hour,
            initialMinute = currentZoned.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        val combined = LocalDateTime.of(currentZoned.toLocalDate(), pickedTime)
                        occurredAtEpochMs = combined.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
