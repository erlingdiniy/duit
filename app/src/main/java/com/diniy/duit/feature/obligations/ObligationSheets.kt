package com.diniy.duit.feature.obligations

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.CommitmentSummary
import com.diniy.duit.domain.calculator.DebtSummary
import com.diniy.duit.domain.calculator.PayLaterSummary
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.ObligationPayment
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PayLaterProviderPreset
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.feature.savings.SavingsIconMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommitmentEditorSheet(
    existing: Commitment?,
    categories: List<Category>,
    onSave: (Commitment, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE && !it.isArchived }

    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var amountInput by rememberSaveable(existing?.id) {
        mutableStateOf(
            if (existing != null && existing.amountSen > 0L) MyrFormatter.formatInputValue(existing.amountSen) else ""
        )
    }
    var dueDateEpochDay by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.dueDateEpochDay ?: LocalDate.now().plusDays(7).toEpochDay())
    }
    var selectedCategoryName by rememberSaveable(existing?.id) {
        mutableStateOf(
            existing?.category?.takeIf { it.isNotBlank() }
                ?: expenseCategories.firstOrNull { it.name.contains("Bill", true) }?.name
                ?: expenseCategories.firstOrNull()?.name
                ?: "Bills & Utilities"
        )
    }
    var frequency by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.frequency ?: CommitmentFrequency.MONTHLY)
    }
    var customDaysInput by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.customIntervalDays ?: 30).toString())
    }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    PrismaticModalBottomSheet(
        title = if (existing == null) "New Commitment" else "Edit Commitment",
        subtitle = "Track rent, bills, subscriptions & insurance. Balances only change when paid.",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        PrismaticTextField(
            value = title,
            onValueChange = { title = it; errorText = null },
            label = "Commitment name",
            placeholder = "e.g., Home Rent, Unifi, Car Insurance",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = amountInput,
            onValueChange = { amountInput = it; errorText = null },
            label = "Amount",
            placeholder = "139.00",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))

        Text("Due date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        PrismaticButton(
            text = DateTimeFormatters.formatEpochDay(dueDateEpochDay),
            onClick = { showDatePicker = true },
            variant = PrismaticButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Text("Frequency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CommitmentFrequency.entries.forEach { freq ->
                FilterChip(
                    selected = frequency == freq,
                    onClick = { frequency = freq; errorText = null },
                    label = { Text(freq.displayName) }
                )
            }
        }
        if (frequency == CommitmentFrequency.CUSTOM_DAYS) {
            Spacer(Modifier.height(8.dp))
            PrismaticTextField(
                value = customDaysInput,
                onValueChange = { customDaysInput = it; errorText = null },
                label = "Every N days",
                placeholder = "30",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        if (expenseCategories.isEmpty()) {
            PrismaticTextField(
                value = selectedCategoryName,
                onValueChange = { selectedCategoryName = it },
                label = "Category name"
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                expenseCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryName.equals(cat.name, ignoreCase = true),
                        onClick = { selectedCategoryName = cat.name; errorText = null },
                        label = { Text(cat.name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = note,
            onValueChange = { note = it },
            label = "Notes (optional)",
            placeholder = "Account ref, auto-debit reminder",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
        )

        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrismaticButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = PrismaticButtonVariant.Secondary,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f)
            )
            PrismaticButton(
                text = if (existing == null) "Save Commitment" else "Save Changes",
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    if (title.trim().isEmpty()) {
                        errorText = "Enter a commitment name."
                        return@PrismaticButton
                    }
                    val amountSen = MyrFormatter.parseToSen(amountInput)
                    if (amountSen == null || amountSen <= 0L) {
                        errorText = "Enter a valid amount greater than RM 0.00."
                        return@PrismaticButton
                    }
                    val customDays = if (frequency == CommitmentFrequency.CUSTOM_DAYS) {
                        customDaysInput.trim().toIntOrNull()?.takeIf { it >= 1 } ?: run {
                            errorText = "Enter at least 1 day for custom interval."
                            return@PrismaticButton
                        }
                    } else null

                    val matchedCategory = expenseCategories.firstOrNull { it.name.equals(selectedCategoryName, ignoreCase = true) }
                    val now = System.currentTimeMillis()
                    val candidate = Commitment(
                        id = existing?.id ?: 0L,
                        title = title.trim(),
                        amountSen = amountSen,
                        dueDateEpochDay = dueDateEpochDay,
                        status = existing?.status ?: CommitmentStatus.UPCOMING,
                        walletId = existing?.walletId,
                        isRecurring = frequency != CommitmentFrequency.ONE_TIME,
                        salaryCycleId = existing?.salaryCycleId,
                        category = matchedCategory?.name ?: selectedCategoryName.trim().ifEmpty { "Bills & Utilities" },
                        categoryId = matchedCategory?.id ?: existing?.categoryId,
                        frequency = frequency,
                        customIntervalDays = customDays,
                        anchorDayOfMonth = LocalDate.ofEpochDay(dueDateEpochDay).dayOfMonth,
                        paidSen = existing?.paidSen ?: 0L,
                        note = note.trim(),
                        isArchived = existing?.isArchived ?: false,
                        createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                        updatedAtEpochMs = now
                    )
                    isSubmitting = true
                    onSave(candidate) { msg ->
                        isSubmitting = false
                        errorText = msg
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (showDatePicker) {
        val initMs = LocalDate.ofEpochDay(dueDateEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dueDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommitmentDetailSheet(
    summary: CommitmentSummary,
    wallets: List<Wallet>,
    payments: List<ObligationPayment>,
    isPreview: Boolean,
    onRecordPayment: (commitmentId: Long, walletId: Long, amountSen: Long, note: String, paidAtMs: Long, submissionId: String, onError: (String) -> Unit) -> Unit,
    onOpenNextOccurrence: (commitmentId: Long, onError: (String) -> Unit) -> Unit = { _, _ -> },
    onEdit: () -> Unit,
    onToggleArchive: (id: Long, archived: Boolean, onError: (String) -> Unit) -> Unit,
    onDelete: (id: Long, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val c = summary.commitment
    val activeWallets = wallets.filter { !it.isArchived }
    val walletById = wallets.associateBy { it.id }

    var showPayForm by rememberSaveable { mutableStateOf(false) }
    var selectedWalletId by rememberSaveable { mutableStateOf(c.walletId ?: activeWallets.firstOrNull()?.id) }
    var payAmountInput by rememberSaveable {
        mutableStateOf(if (summary.remainingSen > 0L) MyrFormatter.formatInputValue(summary.remainingSen) else "")
    }
    var payNote by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val submissionId by rememberSaveable(c.id, showPayForm) { mutableStateOf(UUID.randomUUID().toString()) }

    PrismaticModalBottomSheet(
        title = c.title,
        subtitle = "${summary.frequencyLabel} · ${c.category} · Due ${DateTimeFormatters.formatEpochDay(c.dueDateEpochDay)}",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Elevated,
            accentTint = glass.peachAccent,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = MyrFormatter.format(c.amountSen), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = if (c.status == CommitmentStatus.PAID) "Paid in full"
                        else "${MyrFormatter.format(summary.remainingSen)} remaining this cycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrismaticBadge(
                    label = if (c.isArchived) "Archived" else summary.displayState.label,
                    containerColor = glass.peachContainer,
                    contentColor = glass.onPeachContainer
                )
            }
            if (c.paidSen > 0L && summary.remainingSen > 0L) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { summary.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                    color = glass.peachAccent,
                    trackColor = glass.glassSurfaceMuted,
                    strokeCap = StrokeCap.Round
                )
            }
            if (c.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

            if (!isPreview && summary.remainingSen > 0L && !c.isArchived) {
            Spacer(Modifier.height(12.dp))
            if (!showPayForm) {
                PrismaticButton(
                    text = "Record Payment",
                    onClick = {
                        payAmountInput = MyrFormatter.formatInputValue(summary.remainingSen)
                        showPayForm = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Muted,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text("Pay from wallet", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeWallets.forEach { w ->
                            FilterChip(
                                selected = selectedWalletId == w.id,
                                onClick = { selectedWalletId = w.id },
                                label = { Text("${w.name} (${MyrFormatter.format(w.balanceSen)})") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(
                        value = payAmountInput,
                        onValueChange = { payAmountInput = it; errorText = null },
                        label = "Payment amount (partial or full)",
                        prefixText = "RM ",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(
                        value = payNote,
                        onValueChange = { payNote = it },
                        label = "Payment note (optional)"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrismaticButton(
                            text = "Cancel",
                            onClick = { showPayForm = false },
                            variant = PrismaticButtonVariant.Secondary,
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f)
                        )
                        PrismaticButton(
                            text = "Confirm Pay",
                            isLoading = isSubmitting,
                            enabled = !isSubmitting,
                            onClick = {
                                val wId = selectedWalletId ?: run { errorText = "Select a wallet."; return@PrismaticButton }
                                val amt = MyrFormatter.parseToSen(payAmountInput) ?: run { errorText = "Enter valid amount."; return@PrismaticButton }
                                isSubmitting = true
                                onRecordPayment(c.id, wId, amt, payNote.trim(), System.currentTimeMillis(), submissionId) { msg ->
                                    isSubmitting = false
                                    errorText = msg
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        } else if (!isPreview && !c.isArchived && c.status == CommitmentStatus.PAID && c.isRecurring) {
            Spacer(Modifier.height(12.dp))
            val nextLabel = c.nextDueDateEpochDay?.let {
                "Open Next Occurrence (${DateTimeFormatters.formatCompactEpochDay(it)})"
            } ?: "Open Next Occurrence"
            PrismaticButton(
                text = nextLabel,
                onClick = {
                    onOpenNextOccurrence(c.id) { msg -> errorText = msg }
                },
                variant = PrismaticButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        ObligationPaymentHistoryList(payments = payments, walletById = walletById, isIncome = false)

        if (!isPreview) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrismaticButton(text = "Edit", onClick = onEdit, variant = PrismaticButtonVariant.Secondary)
                PrismaticButton(
                    text = if (c.isArchived) "Restore" else "Archive",
                    onClick = { onToggleArchive(c.id, !c.isArchived) { errorText = it } },
                    variant = PrismaticButtonVariant.Ghost
                )
                PrismaticButton(text = "Delete", onClick = { showDeleteConfirm = true }, variant = PrismaticButtonVariant.Ghost)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${c.title}\"?") },
            text = { Text("Removes this commitment definition and its link records. Past ledger transactions remain in Activity.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(c.id) { errorText = it } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PayLaterEditorSheet(
    existing: PayLaterPlan?,
    onSave: (PayLaterPlan, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val presetNames = PayLaterProviderPreset.entries.filter { it != PayLaterProviderPreset.CUSTOM }.map { it.displayName }
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var selectedPreset by rememberSaveable(existing?.id) {
        mutableStateOf(
            if (existing == null) "SPayLater"
            else if (existing.providerName in presetNames) existing.providerName else "Custom"
        )
    }
    var customProvider by rememberSaveable(existing?.id) {
        mutableStateOf(if (existing != null && existing.providerName !in presetNames) existing.providerName else "")
    }
    var purchaseInput by rememberSaveable(existing?.id) {
        mutableStateOf(if (existing != null) MyrFormatter.formatInputValue(existing.purchaseAmountSen) else "")
    }
    var feeInput by rememberSaveable(existing?.id) {
        mutableStateOf(if (existing != null) MyrFormatter.formatInputValue(existing.feeOrInterestSen) else "0.00")
    }
    var instalmentCountInput by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.instalmentCount ?: 3).toString())
    }
    var firstDueEpochDay by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.firstDueDateEpochDay ?: LocalDate.now().plusMonths(1).toEpochDay())
    }
    var accentToken by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.accentToken ?: PrismaticAccentToken.PEACH)
    }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    PrismaticModalBottomSheet(
        title = if (existing == null) "New PayLater Plan" else "Edit PayLater Plan",
        subtitle = "Track instalments, admin fees/interest & upcoming payments.",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        PrismaticTextField(
            value = title,
            onValueChange = { title = it; errorText = null },
            label = "Purchase item / description",
            placeholder = "e.g., Monitor, Shoes, Electronics"
        )
        Spacer(Modifier.height(10.dp))
        Text("Provider", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayLaterProviderPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset.displayName,
                    onClick = { selectedPreset = preset.displayName; errorText = null },
                    label = { Text(preset.displayName) }
                )
            }
        }
        if (selectedPreset == PayLaterProviderPreset.CUSTOM.displayName) {
            Spacer(Modifier.height(8.dp))
            PrismaticTextField(
                value = customProvider,
                onValueChange = { customProvider = it; errorText = null },
                label = "Custom provider name",
                placeholder = "e.g., Pace, AEON Credit"
            )
        }

        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = purchaseInput,
            onValueChange = { purchaseInput = it; errorText = null },
            label = "Purchase amount",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = feeInput,
            onValueChange = { feeInput = it; errorText = null },
            label = "Processing fee / interest",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrismaticTextField(
                value = instalmentCountInput,
                onValueChange = { instalmentCountInput = it; errorText = null },
                label = "Instalments",
                placeholder = "3",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Column(modifier = Modifier.weight(1.3f)) {
                Text("First due date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                PrismaticButton(
                    text = DateTimeFormatters.formatEpochDay(firstDueEpochDay),
                    onClick = { showDatePicker = true },
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(value = note, onValueChange = { note = it }, label = "Notes (optional)")

        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrismaticAccentToken.entries.forEach { token ->
                val (color, _, _) = SavingsIconMapper.accentColors(token)
                FilterChip(
                    selected = accentToken == token,
                    onClick = { accentToken = token },
                    label = { Text(token.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrismaticButton(text = "Cancel", onClick = onDismiss, variant = PrismaticButtonVariant.Secondary, enabled = !isSubmitting, modifier = Modifier.weight(1f))
            PrismaticButton(
                text = if (existing == null) "Save Plan" else "Save Changes",
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    val purchaseSen = MyrFormatter.parseToSen(purchaseInput)
                    if (title.trim().isEmpty()) { errorText = "Enter purchase description."; return@PrismaticButton }
                    if (purchaseSen == null || purchaseSen <= 0L) { errorText = "Enter valid purchase amount."; return@PrismaticButton }
                    val feeSen = MyrFormatter.parseToSen(feeInput.ifBlank { "0" })
                    if (feeSen == null || feeSen < 0L) { errorText = "Enter valid fee/interest amount."; return@PrismaticButton }
                    val count = instalmentCountInput.trim().toIntOrNull()?.takeIf { it >= 1 }
                    if (count == null) { errorText = "Enter at least 1 instalment."; return@PrismaticButton }
                    val provider = if (selectedPreset == PayLaterProviderPreset.CUSTOM.displayName) customProvider.trim() else selectedPreset
                    if (provider.isEmpty()) { errorText = "Enter provider name."; return@PrismaticButton }

                    val now = System.currentTimeMillis()
                    isSubmitting = true
                    onSave(
                        PayLaterPlan(
                            id = existing?.id ?: 0L,
                            title = title.trim(),
                            providerName = provider,
                            purchaseAmountSen = purchaseSen,
                            feeOrInterestSen = feeSen,
                            paidSen = existing?.paidSen ?: 0L,
                            instalmentCount = count,
                            firstDueDateEpochDay = firstDueEpochDay,
                            nextDueDateEpochDay = existing?.nextDueDateEpochDay ?: firstDueEpochDay,
                            accentToken = accentToken,
                            note = note.trim(),
                            isArchived = existing?.isArchived ?: false,
                            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    ) { msg ->
                        isSubmitting = false
                        errorText = msg
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (showDatePicker) {
        val initMs = LocalDate.ofEpochDay(firstDueEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        firstDueEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PayLaterDetailSheet(
    summary: PayLaterSummary,
    wallets: List<Wallet>,
    payments: List<ObligationPayment>,
    isPreview: Boolean,
    onRecordPayment: (planId: Long, walletId: Long, amountSen: Long, note: String, paidAtMs: Long, submissionId: String, onError: (String) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: (id: Long, archived: Boolean, onError: (String) -> Unit) -> Unit,
    onDelete: (id: Long, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val plan = summary.plan
    val activeWallets = wallets.filter { !it.isArchived }
    val walletById = wallets.associateBy { it.id }

    var showPayForm by rememberSaveable { mutableStateOf(false) }
    var selectedWalletId by rememberSaveable { mutableStateOf(activeWallets.firstOrNull()?.id) }
    var amountInput by rememberSaveable {
        mutableStateOf(if (summary.nextInstalmentSen > 0L) MyrFormatter.formatInputValue(summary.nextInstalmentSen) else "")
    }
    var payNote by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val submissionId by rememberSaveable(plan.id, showPayForm) { mutableStateOf(UUID.randomUUID().toString()) }

    PrismaticModalBottomSheet(
        title = plan.title,
        subtitle = "${plan.providerName} · ${plan.instalmentCount} instalments",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Elevated,
            accentTint = glass.lavenderAccent,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(MyrFormatter.format(summary.outstandingSen), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Outstanding of ${MyrFormatter.format(summary.totalPayableSen)} (Principal ${MyrFormatter.format(plan.purchaseAmountSen)} + Fees ${MyrFormatter.format(plan.feeOrInterestSen)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrismaticBadge(
                    label = if (summary.isSettled) "Settled" else summary.progressPercentDisplay,
                    containerColor = glass.lavenderContainer,
                    contentColor = glass.onLavenderContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { summary.progressFraction },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = glass.lavenderAccent,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )
        }

        if (!isPreview && !summary.isSettled && !plan.isArchived) {
            Spacer(Modifier.height(12.dp))
            if (!showPayForm) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismaticButton(
                        text = "Pay Instalment (${MyrFormatter.format(summary.nextInstalmentSen)})",
                        onClick = {
                            amountInput = MyrFormatter.formatInputValue(summary.nextInstalmentSen)
                            showPayForm = true
                        },
                        modifier = Modifier.weight(1.4f)
                    )
                    PrismaticButton(
                        text = "Pay Full",
                        onClick = {
                            amountInput = MyrFormatter.formatInputValue(summary.outstandingSen)
                            showPayForm = true
                        },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Muted, contentPadding = PaddingValues(14.dp)) {
                    Text("Pay from wallet", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeWallets.forEach { w ->
                            FilterChip(
                                selected = selectedWalletId == w.id,
                                onClick = { selectedWalletId = w.id },
                                label = { Text("${w.name} (${MyrFormatter.format(w.balanceSen)})") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it; errorText = null },
                        label = "Amount (partial, instalment, or early settlement)",
                        prefixText = "RM ",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(value = payNote, onValueChange = { payNote = it }, label = "Note (optional)")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrismaticButton(text = "Cancel", onClick = { showPayForm = false }, variant = PrismaticButtonVariant.Secondary, enabled = !isSubmitting, modifier = Modifier.weight(1f))
                        PrismaticButton(
                            text = "Confirm Pay",
                            isLoading = isSubmitting,
                            enabled = !isSubmitting,
                            onClick = {
                                val wId = selectedWalletId ?: run { errorText = "Select a wallet."; return@PrismaticButton }
                                val amt = MyrFormatter.parseToSen(amountInput) ?: run { errorText = "Enter valid amount."; return@PrismaticButton }
                                isSubmitting = true
                                onRecordPayment(plan.id, wId, amt, payNote.trim(), System.currentTimeMillis(), submissionId) { msg ->
                                    isSubmitting = false
                                    errorText = msg
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Instalment schedule", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Muted, contentPadding = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.schedule.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "#${item.index} · ${DateTimeFormatters.formatEpochDay(item.dueDateEpochDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isCovered) glass.positiveText else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (item.isCovered) "${MyrFormatter.format(item.amountSen)} · Paid" else MyrFormatter.format(item.amountSen),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.isCovered) glass.positiveText else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ObligationPaymentHistoryList(payments = payments, walletById = walletById, isIncome = false)

        if (!isPreview) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrismaticButton(text = "Edit", onClick = onEdit, variant = PrismaticButtonVariant.Secondary)
                PrismaticButton(
                    text = if (plan.isArchived) "Restore" else "Archive",
                    onClick = { onToggleArchive(plan.id, !plan.isArchived) { errorText = it } },
                    variant = PrismaticButtonVariant.Ghost
                )
                PrismaticButton(text = "Delete", onClick = { showDeleteConfirm = true }, variant = PrismaticButtonVariant.Ghost)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${plan.title}\"?") },
            text = { Text("Removes this PayLater plan record. Past ledger transactions in Activity are untouched.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(plan.id) { errorText = it } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtEditorSheet(
    existing: DebtRecord?,
    onSave: (DebtRecord, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var direction by rememberSaveable(existing?.id) { mutableStateOf(existing?.direction ?: DebtDirection.I_OWE) }
    var counterparty by rememberSaveable(existing?.id) { mutableStateOf(existing?.counterpartyName.orEmpty()) }
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var amountInput by rememberSaveable(existing?.id) {
        mutableStateOf(if (existing != null) MyrFormatter.formatInputValue(existing.originalAmountSen) else "")
    }
    var scheduledInput by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.scheduledRepaymentSen?.let { MyrFormatter.formatInputValue(it) } ?: "")
    }
    var dueDateEpochDay by rememberSaveable(existing?.id) { mutableStateOf(existing?.dueDateEpochDay) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    PrismaticModalBottomSheet(
        title = if (existing == null) "New Debt / Receivable" else "Edit Debt Record",
        subtitle = "Separate money you owe from money owed to you. Balances update only when settled.",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DebtDirection.entries.forEach { dir ->
                FilterChip(
                    selected = direction == dir,
                    onClick = { direction = dir },
                    label = { Text(dir.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = counterparty,
            onValueChange = { counterparty = it; errorText = null },
            label = if (direction == DebtDirection.I_OWE) "Creditor name (who you owe)" else "Debtor name (who owes you)",
            placeholder = "e.g., Aiman, Bank, Sibling"
        )
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = amountInput,
            onValueChange = { amountInput = it; errorText = null },
            label = "Original amount",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(
            value = title,
            onValueChange = { title = it },
            label = "Reason / item (optional)",
            placeholder = "e.g., Dinner split, Laptop advance"
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrismaticButton(
                text = dueDateEpochDay?.let { DateTimeFormatters.formatEpochDay(it) } ?: "Set due date (optional)",
                onClick = { showDatePicker = true },
                variant = PrismaticButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            )
            if (dueDateEpochDay != null) {
                PrismaticButton(text = "Clear", onClick = { dueDateEpochDay = null }, variant = PrismaticButtonVariant.Ghost)
            }
        }
        if (direction == DebtDirection.I_OWE) {
            Spacer(Modifier.height(10.dp))
            PrismaticTextField(
                value = scheduledInput,
                onValueChange = { scheduledInput = it; errorText = null },
                label = "Scheduled cycle repayment (optional)",
                placeholder = "e.g., 200.00 (reserves only when due this cycle)",
                prefixText = "RM ",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        Spacer(Modifier.height(10.dp))
        PrismaticTextField(value = note, onValueChange = { note = it }, label = "Notes (optional)")

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrismaticButton(text = "Cancel", onClick = onDismiss, variant = PrismaticButtonVariant.Secondary, enabled = !isSubmitting, modifier = Modifier.weight(1f))
            PrismaticButton(
                text = if (existing == null) "Save Debt" else "Save Changes",
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    if (counterparty.trim().isEmpty()) { errorText = "Enter creditor or debtor name."; return@PrismaticButton }
                    val amt = MyrFormatter.parseToSen(amountInput)
                    if (amt == null || amt <= 0L) { errorText = "Enter valid amount greater than RM 0.00."; return@PrismaticButton }
                    val scheduledSen = if (direction == DebtDirection.I_OWE && scheduledInput.trim().isNotEmpty()) {
                        val parsed = MyrFormatter.parseToSen(scheduledInput)
                        if (parsed == null || parsed <= 0L) {
                            errorText = "Scheduled repayment must be greater than RM 0.00 or left blank."
                            return@PrismaticButton
                        }
                        val remainingTarget = (amt - (existing?.paidSen ?: 0L)).coerceAtLeast(0L)
                        if (parsed > remainingTarget) {
                            errorText = "Scheduled repayment exceeds remaining debt (${MyrFormatter.format(remainingTarget)})."
                            return@PrismaticButton
                        }
                        parsed
                    } else {
                        null
                    }
                    val now = System.currentTimeMillis()
                    isSubmitting = true
                    onSave(
                        DebtRecord(
                            id = existing?.id ?: 0L,
                            direction = direction,
                            counterpartyName = counterparty.trim(),
                            title = title.trim(),
                            originalAmountSen = amt,
                            paidSen = existing?.paidSen ?: 0L,
                            dueDateEpochDay = dueDateEpochDay,
                            scheduledRepaymentSen = scheduledSen,
                            accentToken = if (direction == DebtDirection.I_OWE) PrismaticAccentToken.PEACH else PrismaticAccentToken.AQUA,
                            note = note.trim(),
                            isArchived = existing?.isArchived ?: false,
                            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    ) { msg ->
                        isSubmitting = false
                        errorText = msg
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (showDatePicker) {
        val initDate = dueDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(7)
        val state = rememberDatePickerState(initialSelectedDateMillis = initDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dueDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtDetailSheet(
    summary: DebtSummary,
    wallets: List<Wallet>,
    payments: List<ObligationPayment>,
    isPreview: Boolean,
    onRecordSettlement: (debtId: Long, walletId: Long, amountSen: Long, note: String, paidAtMs: Long, submissionId: String, onError: (String) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: (id: Long, archived: Boolean, onError: (String) -> Unit) -> Unit,
    onDelete: (id: Long, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val debt = summary.debt
    val isOwe = debt.direction == DebtDirection.I_OWE
    val activeWallets = wallets.filter { !it.isArchived }
    val walletById = wallets.associateBy { it.id }

    var showSettleForm by rememberSaveable { mutableStateOf(false) }
    var selectedWalletId by rememberSaveable { mutableStateOf(activeWallets.firstOrNull()?.id) }
    var amountInput by rememberSaveable {
        mutableStateOf(if (summary.outstandingSen > 0L) MyrFormatter.formatInputValue(summary.outstandingSen) else "")
    }
    var settleNote by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val submissionId by rememberSaveable(debt.id, showSettleForm) { mutableStateOf(UUID.randomUUID().toString()) }

    PrismaticModalBottomSheet(
        title = debt.counterpartyName,
        subtitle = "${debt.direction.displayName} · ${debt.title.ifBlank { "Personal debt" }}",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(message = errorText!!, onDismiss = { errorText = null })
            Spacer(Modifier.height(10.dp))
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Elevated,
            accentTint = if (isOwe) glass.peachAccent else glass.aquaAccent,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(MyrFormatter.format(summary.outstandingSen), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${if (isOwe) "Paid" else "Received"} ${MyrFormatter.format(debt.paidSen)} of ${MyrFormatter.format(debt.originalAmountSen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrismaticBadge(
                    label = if (summary.isSettled) "Settled" else summary.progressPercentDisplay,
                    containerColor = if (isOwe) glass.peachContainer else glass.aquaContainer,
                    contentColor = if (isOwe) glass.onPeachContainer else glass.onAquaContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { summary.progressFraction },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = if (isOwe) glass.peachAccent else glass.aquaAccent,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )
            if (debt.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(debt.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!isPreview && !summary.isSettled && !debt.isArchived) {
            Spacer(Modifier.height(12.dp))
            if (!showSettleForm) {
                PrismaticButton(
                    text = if (isOwe) "Record Repayment" else "Record Receipt",
                    onClick = {
                        amountInput = MyrFormatter.formatInputValue(summary.outstandingSen)
                        showSettleForm = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Muted, contentPadding = PaddingValues(14.dp)) {
                    Text(if (isOwe) "Pay from wallet" else "Deposit into wallet", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeWallets.forEach { w ->
                            FilterChip(
                                selected = selectedWalletId == w.id,
                                onClick = { selectedWalletId = w.id },
                                label = { Text("${w.name} (${MyrFormatter.format(w.balanceSen)})") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it; errorText = null },
                        label = "Amount (partial or full)",
                        prefixText = "RM ",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrismaticTextField(value = settleNote, onValueChange = { settleNote = it }, label = "Note (optional)")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrismaticButton(text = "Cancel", onClick = { showSettleForm = false }, variant = PrismaticButtonVariant.Secondary, enabled = !isSubmitting, modifier = Modifier.weight(1f))
                        PrismaticButton(
                            text = if (isOwe) "Confirm Pay" else "Confirm Receipt",
                            isLoading = isSubmitting,
                            enabled = !isSubmitting,
                            onClick = {
                                val wId = selectedWalletId ?: run { errorText = "Select a wallet."; return@PrismaticButton }
                                val amt = MyrFormatter.parseToSen(amountInput) ?: run { errorText = "Enter valid amount."; return@PrismaticButton }
                                isSubmitting = true
                                onRecordSettlement(debt.id, wId, amt, settleNote.trim(), System.currentTimeMillis(), submissionId) { msg ->
                                    isSubmitting = false
                                    errorText = msg
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ObligationPaymentHistoryList(payments = payments, walletById = walletById, isIncome = !isOwe)

        if (!isPreview) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrismaticButton(text = "Edit", onClick = onEdit, variant = PrismaticButtonVariant.Secondary)
                PrismaticButton(
                    text = if (debt.isArchived) "Restore" else "Archive",
                    onClick = { onToggleArchive(debt.id, !debt.isArchived) { errorText = it } },
                    variant = PrismaticButtonVariant.Ghost
                )
                PrismaticButton(text = "Delete", onClick = { showDeleteConfirm = true }, variant = PrismaticButtonVariant.Ghost)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete debt with \"${debt.counterpartyName}\"?") },
            text = { Text("Removes this debt record. Existing ledger transactions in Activity remain untouched.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(debt.id) { errorText = it } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ObligationPaymentHistoryList(
    payments: List<ObligationPayment>,
    walletById: Map<Long, Wallet>,
    isIncome: Boolean
) {
    val glass = LocalPrismaticGlass.current
    Text("Payment history (${payments.size})", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    if (payments.isEmpty()) {
        Text(
            "No payments recorded yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            payments.take(15).forEach { p ->
                val walletName = walletById[p.walletId]?.name ?: "Wallet #${p.walletId}"
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
                            Text(walletName, style = MaterialTheme.typography.labelLarge)
                            val sub = buildString {
                                append(DateTimeFormatters.formatTimestamp(p.paidAtEpochMs))
                                if (p.note.isNotBlank()) append(" · ${p.note}")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = (if (isIncome) "+" else "-") + MyrFormatter.format(p.amountSen),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isIncome) glass.positiveText else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
