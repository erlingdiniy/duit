package com.diniy.duit.feature.planning

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.CommissionPayment
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionSource
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.Wallet
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommissionSourcesSheet(
    sources: List<CommissionSource>,
    onCreateSource: (String, PrismaticAccentToken, (String) -> Unit, () -> Unit) -> Unit,
    onRenameSource: (Long, String, PrismaticAccentToken, (String) -> Unit, () -> Unit) -> Unit,
    onToggleArchive: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var selectedAccent by rememberSaveable { mutableStateOf(PrismaticAccentToken.LAVENDER) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    PrismaticModalBottomSheet(
        title = "Commission Sources",
        subtitle = "Manage Company, Lenovo, ASUS, Acer, or custom partner sources.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (editingId == null) "Add Commission Source" else "Rename Source",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorMessage = null
                    },
                    label = "Source name",
                    placeholder = "e.g. HP, Dell, Partner Rebate",
                    errorText = errorMessage
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticAccentToken.entries.forEach { token ->
                        FilterChip(
                            selected = selectedAccent == token,
                            onClick = { selectedAccent = token },
                            label = {
                                Text(token.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() })
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingId != null) {
                        PrismaticButton(
                            text = "Cancel",
                            onClick = {
                                editingId = null
                                nameInput = ""
                                errorMessage = null
                            },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrismaticButton(
                        text = if (editingId == null) "Add Source" else "Save Name",
                        onClick = {
                            val currentId = editingId
                            if (currentId == null) {
                                onCreateSource(
                                    nameInput,
                                    selectedAccent,
                                    { errorMessage = it },
                                    { nameInput = ""; errorMessage = null }
                                )
                            } else {
                                onRenameSource(
                                    currentId,
                                    nameInput,
                                    selectedAccent,
                                    { errorMessage = it },
                                    { editingId = null; nameInput = ""; errorMessage = null }
                                )
                            }
                        },
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = glass.glassBorderSubtle)

            sources.forEach { source ->
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = if (source.isArchived) GlassCardTier.Muted else GlassCardTier.Standard,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (source.isArchived) {
                                PrismaticBadge(
                                    label = "Archived",
                                    containerColor = glass.peachContainer,
                                    contentColor = glass.onPeachContainer
                                )
                            }
                        }
                        Row {
                            PrismaticButton(
                                text = "Rename",
                                onClick = {
                                    editingId = source.id
                                    nameInput = source.name
                                    selectedAccent = source.accentToken
                                    errorMessage = null
                                },
                                variant = PrismaticButtonVariant.Ghost
                            )
                            PrismaticButton(
                                text = if (source.isArchived) "Restore" else "Archive",
                                onClick = { onToggleArchive(source.id, !source.isArchived) },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommissionRecordEditorSheet(
    sources: List<CommissionSource>,
    selectedMonth: YearMonth,
    existingRecord: CommissionRecord? = null,
    defaultCompanyEstimatedSen: Long? = null,
    onSave: (CommissionRecord, (String) -> Unit) -> Unit,
    onDelete: (Long, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val activeSources = sources.filter { !it.isArchived || it.id == existingRecord?.sourceId }

    var selectedSourceId by rememberSaveable(existingRecord?.id) {
        mutableStateOf(existingRecord?.sourceId ?: activeSources.firstOrNull()?.id)
    }
    var titleInput by rememberSaveable(existingRecord?.id) {
        mutableStateOf(existingRecord?.title ?: "")
    }
    var status by rememberSaveable(existingRecord?.id) {
        mutableStateOf(existingRecord?.status ?: CommissionStatus.PENDING)
    }
    var expectedInput by rememberSaveable(existingRecord?.id) {
        val initialSen = existingRecord?.estimatedGrossSen ?: existingRecord?.amountSen ?: 0L
        mutableStateOf(if (initialSen > 0L) MyrFormatter.formatInputValue(initialSen) else "")
    }
    var adjustmentNote by rememberSaveable(existingRecord?.id) {
        mutableStateOf(existingRecord?.adjustmentNote ?: "")
    }
    var notesInput by rememberSaveable(existingRecord?.id) {
        mutableStateOf(existingRecord?.notes ?: "")
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val selectedSource = activeSources.firstOrNull { it.id == selectedSourceId }

    PrismaticModalBottomSheet(
        title = if (existingRecord == null) "Track Commission" else "Edit Commission",
        subtitle = "Pending or unknown commissions never change wallet balances. Expected amounts and dates are optional.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Commission source",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeSources.forEach { src ->
                    FilterChip(
                        selected = selectedSourceId == src.id,
                        onClick = {
                            selectedSourceId = src.id
                            if (existingRecord == null && src.name.equals("Company", ignoreCase = true) &&
                                expectedInput.isBlank() && defaultCompanyEstimatedSen != null && defaultCompanyEstimatedSen > 0L
                            ) {
                                expectedInput = MyrFormatter.formatInputValue(defaultCompanyEstimatedSen)
                            }
                        },
                        label = { Text(src.name) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            PrismaticTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it
                    errorMessage = null
                },
                label = "Title",
                placeholder = "e.g. ${selectedSource?.name ?: "Partner"} ${selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} Claim"
            )

            Text(
                text = "Status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommissionStatus.entries.forEach { item ->
                    FilterChip(
                        selected = status == item,
                        onClick = { status = item },
                        label = { Text(item.displayName) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            PrismaticMoneyField(
                value = expectedInput,
                onValueChange = {
                    expectedInput = it
                    errorMessage = null
                },
                label = "Expected / Estimated gross (optional)",
                supportingText = "Leave blank if unknown or variable.",
                errorText = errorMessage
            )

            PrismaticTextField(
                value = adjustmentNote,
                onValueChange = { adjustmentNote = it },
                label = "Adjustment / variance note (optional)",
                placeholder = "Record payslip difference or manual note"
            )

            PrismaticTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = "Notes (optional)",
                singleLine = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existingRecord != null) {
                    PrismaticButton(
                        text = "Delete",
                        onClick = { showDeleteConfirm = true },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                PrismaticButton(
                    text = if (existingRecord == null) "Save Commission" else "Update",
                    onClick = {
                        val expectedSen = if (expectedInput.isBlank()) {
                            0L
                        } else {
                            MyrFormatter.parseToSen(expectedInput) ?: run {
                                errorMessage = "Enter a valid RM amount."
                                return@PrismaticButton
                            }
                        }
                        val sourceName = selectedSource?.name ?: existingRecord?.sourceLabel ?: "Company"
                        val resolvedTitle = titleInput.trim().ifEmpty {
                            "$sourceName ${selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} Commission"
                        }
                        val defaultDay = existingRecord?.earnedDateEpochDay ?: selectedMonth.atDay(1).toEpochDay()
                        onSave(
                            CommissionRecord(
                                id = existingRecord?.id ?: 0L,
                                title = resolvedTitle,
                                sourceLabel = sourceName,
                                amountSen = expectedSen,
                                status = status,
                                earnedDateEpochDay = defaultDay,
                                payoutDateEpochDay = existingRecord?.payoutDateEpochDay,
                                linkedTransactionId = existingRecord?.linkedTransactionId,
                                notes = notesInput.trim(),
                                sourceId = selectedSource?.id ?: existingRecord?.sourceId,
                                yearMonth = existingRecord?.yearMonth?.ifBlank { selectedMonth.toString() } ?: selectedMonth.toString(),
                                estimatedGrossSen = if (expectedSen > 0L) expectedSen else existingRecord?.estimatedGrossSen,
                                actualGrossSen = existingRecord?.actualGrossSen ?: 0L,
                                receivedNetSen = existingRecord?.receivedNetSen ?: 0L,
                                adjustmentNote = adjustmentNote.trim()
                            )
                        ) { err -> errorMessage = err }
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showDeleteConfirm && existingRecord != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Commission Record?") },
            text = { Text("Deletes \"${existingRecord.title}\". Records with received payments must reverse those payments first.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(existingRecord.id) { err -> errorMessage = err }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordCommissionPaymentSheet(
    commission: CommissionRecord,
    payments: List<CommissionPayment>,
    wallets: List<Wallet>,
    onRecordPayment: (
        payment: CommissionPayment,
        markAsFull: Boolean,
        onError: (String) -> Unit
    ) -> Unit,
    onDeletePayment: (Long, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val activeWallets = wallets.filter { !it.isArchived }
    val submissionId = rememberSaveable(commission.id) { UUID.randomUUID().toString() }

    var actualGrossInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var epfInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var socsoInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var eisInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var netOverrideInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var selectedWalletId by rememberSaveable(commission.id, activeWallets.size) {
        mutableLongStateOf(
            activeWallets.firstOrNull { !it.isExcludedFromSafeToSpend }?.id
                ?: activeWallets.firstOrNull()?.id
                ?: 0L
        )
    }
    var receivedEpochMs by rememberSaveable(commission.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var markAsFull by rememberSaveable(commission.id) { mutableStateOf(true) }
    var notesInput by rememberSaveable(commission.id) { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val grossSen = MyrFormatter.parseToSen(actualGrossInput) ?: 0L
    val epfSen = if (epfInput.isBlank()) null else MyrFormatter.parseToSen(epfInput)
    val socsoSen = if (socsoInput.isBlank()) null else MyrFormatter.parseToSen(socsoInput)
    val eisSen = if (eisInput.isBlank()) null else MyrFormatter.parseToSen(eisInput)
    val hasDeductions = epfSen != null || socsoSen != null || eisSen != null
    val computedNetSen = (grossSen - (epfSen ?: 0L) - (socsoSen ?: 0L) - (eisSen ?: 0L)).coerceAtLeast(0L)

    PrismaticModalBottomSheet(
        title = "Record Commission Payment",
        subtitle = "${commission.sourceLabel} · Only actual net commission received increases the selected wallet.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (activeWallets.isEmpty()) {
                Text(
                    text = "Add or restore an active wallet first so the net commission payout can be credited.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                return@PrismaticModalBottomSheet
            }

            PrismaticMoneyField(
                value = actualGrossInput,
                onValueChange = {
                    actualGrossInput = it
                    errorMessage = null
                },
                label = "Actual gross commission"
            )

            Text(
                text = "Payslip Deductions (optional — never assumed as flat %)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrismaticMoneyField(
                    value = epfInput,
                    onValueChange = {
                        epfInput = it
                        errorMessage = null
                    },
                    label = "EPF",
                    modifier = Modifier.weight(1f)
                )
                PrismaticMoneyField(
                    value = socsoInput,
                    onValueChange = {
                        socsoInput = it
                        errorMessage = null
                    },
                    label = "SOCSO",
                    modifier = Modifier.weight(1f)
                )
            }

            PrismaticMoneyField(
                value = eisInput,
                onValueChange = {
                    eisInput = it
                    errorMessage = null
                },
                label = "EIS"
            )

            PrismaticMoneyField(
                value = netOverrideInput,
                onValueChange = {
                    netOverrideInput = it
                    errorMessage = null
                },
                label = "Actual net received (leave blank to auto-subtract deductions)",
                supportingText = when {
                    netOverrideInput.isNotBlank() -> "Using explicit net payslip amount"
                    hasDeductions && grossSen > 0L -> "Calculated Net: ${MyrFormatter.format(computedNetSen)}"
                    grossSen > 0L -> "No deductions entered — Net equals Gross (${MyrFormatter.format(grossSen)})"
                    else -> "Enter gross and optional payslip deductions"
                },
                errorText = errorMessage
            )

            Text(
                text = "Credit to wallet",
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
                        onClick = { selectedWalletId = wallet.id },
                        label = { Text(wallet.name) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            PrismaticButton(
                text = "Payment Date: ${DateTimeFormatters.formatDateOnly(receivedEpochMs)}",
                onClick = { showDatePicker = true },
                variant = PrismaticButtonVariant.Secondary,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = markAsFull,
                        role = Role.Switch,
                        onValueChange = { markAsFull = it }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mark commission fully received",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Turn off for a partial payout when more installments may arrive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = markAsFull, onCheckedChange = null, colors = com.diniy.duit.core.ui.components.candySwitchColors())
            }

            PrismaticTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = "Payslip / payout note (optional)"
            )

            PrismaticButton(
                text = "Save Payment & Credit Wallet",
                onClick = {
                    val parsedGross = MyrFormatter.parseToSen(actualGrossInput)
                    if (parsedGross == null || parsedGross <= 0L) {
                        errorMessage = "Enter a valid actual gross RM amount."
                        return@PrismaticButton
                    }
                    val parsedEpf = if (epfInput.isBlank()) null else MyrFormatter.parseToSen(epfInput) ?: run {
                        errorMessage = "Invalid EPF amount."
                        return@PrismaticButton
                    }
                    val parsedSocso = if (socsoInput.isBlank()) null else MyrFormatter.parseToSen(socsoInput) ?: run {
                        errorMessage = "Invalid SOCSO amount."
                        return@PrismaticButton
                    }
                    val parsedEis = if (eisInput.isBlank()) null else MyrFormatter.parseToSen(eisInput) ?: run {
                        errorMessage = "Invalid EIS amount."
                        return@PrismaticButton
                    }
                    val resolvedNet = if (netOverrideInput.isNotBlank()) {
                        MyrFormatter.parseToSen(netOverrideInput) ?: run {
                            errorMessage = "Invalid net amount."
                            return@PrismaticButton
                        }
                    } else {
                        parsedGross - (parsedEpf ?: 0L) - (parsedSocso ?: 0L) - (parsedEis ?: 0L)
                    }
                    if (resolvedNet <= 0L) {
                        errorMessage = "Actual net commission must be greater than RM 0.00."
                        return@PrismaticButton
                    }
                    if (selectedWalletId == 0L) {
                        errorMessage = "Select a destination wallet."
                        return@PrismaticButton
                    }

                    onRecordPayment(
                        CommissionPayment(
                            id = 0L,
                            commissionId = commission.id,
                            sourceId = commission.sourceId,
                            sourceName = commission.sourceLabel,
                            walletId = selectedWalletId,
                            linkedTransactionId = null,
                            receivedAtEpochMs = receivedEpochMs,
                            actualGrossSen = parsedGross,
                            epfDeductionSen = parsedEpf,
                            socsoDeductionSen = parsedSocso,
                            eisDeductionSen = parsedEis,
                            actualNetSen = resolvedNet,
                            notes = notesInput.trim(),
                            submissionId = submissionId
                        ),
                        markAsFull
                    ) { err -> errorMessage = err }
                },
                variant = PrismaticButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )

            if (payments.isNotEmpty()) {
                HorizontalDivider(color = glass.glassBorderSubtle)
                Text(
                    text = "Received Payments (${payments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                payments.forEach { pay ->
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
                                    text = "Net ${MyrFormatter.format(pay.actualNetSen)} (Gross ${MyrFormatter.format(pay.actualGrossSen)})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = DateTimeFormatters.formatDateOnly(pay.receivedAtEpochMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PrismaticButton(
                                text = "Reverse",
                                onClick = { onDeletePayment(pay.id) { err -> errorMessage = err } },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val currentDate = Instant.ofEpochMilli(receivedEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val initialUtcMillis = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            receivedEpochMs = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
