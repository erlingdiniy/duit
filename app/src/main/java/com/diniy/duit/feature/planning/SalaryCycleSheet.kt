package com.diniy.duit.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.diniy.duit.domain.model.SalaryCycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryCycleSheet(
    cycles: List<SalaryCycle>,
    onSaveCycle: (
        cycle: SalaryCycle,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteCycle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var editingCycleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var labelInput by rememberSaveable { mutableStateOf("") }
    var startEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var salaryInput by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    PrismaticModalBottomSheet(
        title = "Salary Cycles",
        subtitle = "Cycles start on the actual receipt date and close the day before the next confirmed cycle.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (editingCycleId == null) "Add or Backdate Cycle" else "Correct Cycle Dates",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = labelInput,
                    onValueChange = {
                        labelInput = it
                        errorMessage = null
                    },
                    label = "Cycle label",
                    placeholder = "e.g. Nov 2026 Cycle"
                )
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Start Date: ${DateTimeFormatters.formatEpochDay(startEpochDay)}",
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
                Spacer(Modifier.height(10.dp))
                PrismaticMoneyField(
                    value = salaryInput,
                    onValueChange = {
                        salaryInput = it
                        errorMessage = null
                    },
                    label = "Received salary reference (optional)",
                    supportingText = "Does not alter wallet balances; use Income -> Save & Start Cycle to credit a wallet.",
                    errorText = errorMessage
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingCycleId != null) {
                        PrismaticButton(
                            text = "Cancel Edit",
                            onClick = {
                                editingCycleId = null
                                labelInput = ""
                                salaryInput = ""
                                errorMessage = null
                            },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrismaticButton(
                        text = if (editingCycleId == null) "Save Cycle" else "Update Cycle",
                        onClick = {
                            val trimmed = labelInput.trim()
                            if (trimmed.isEmpty()) {
                                errorMessage = "Enter a cycle label."
                                return@PrismaticButton
                            }
                            val salarySen = if (salaryInput.isBlank()) {
                                0L
                            } else {
                                MyrFormatter.parseToSen(salaryInput) ?: run {
                                    errorMessage = "Enter a valid RM amount."
                                    return@PrismaticButton
                                }
                            }
                            val existing = editingCycleId?.let { id -> cycles.firstOrNull { it.id == id } }
                            onSaveCycle(
                                SalaryCycle(
                                    id = editingCycleId ?: 0L,
                                    label = trimmed,
                                    startDateEpochDay = startEpochDay,
                                    endDateEpochDay = existing?.endDateEpochDay ?: Long.MAX_VALUE,
                                    paydayEpochDay = startEpochDay,
                                    baseSalarySen = salarySen,
                                    committedBudgetSen = existing?.committedBudgetSen ?: 0L,
                                    isActive = existing?.isActive ?: true,
                                    receivedSalarySen = salarySen,
                                    salaryTransactionId = existing?.salaryTransactionId,
                                    isOpenEnded = existing?.isOpenEnded ?: true
                                ),
                                { errorMessage = it },
                                {
                                    editingCycleId = null
                                    labelInput = ""
                                    salaryInput = ""
                                    errorMessage = null
                                }
                            )
                        },
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = glass.glassBorderSubtle)

            Text(
                text = "Recorded Cycles (${cycles.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (cycles.isEmpty()) {
                Text(
                    text = "No salary cycles yet. Record a Salary income with 'Save & Start New Cycle' or add one above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                cycles.forEach { cycle ->
                    val rangeText = if (cycle.isOpenEnded) {
                        "${DateTimeFormatters.formatEpochDay(cycle.startDateEpochDay)} – Present"
                    } else {
                        "${DateTimeFormatters.formatEpochDay(cycle.startDateEpochDay)} – ${DateTimeFormatters.formatEpochDay(cycle.endDateEpochDay)}"
                    }
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = if (cycle.isActive) GlassCardTier.Elevated else GlassCardTier.Standard,
                        accentTint = if (cycle.isActive) glass.skyBlueAccent else null,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cycle.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = rangeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (cycle.isActive) {
                                PrismaticBadge(
                                    label = "Active",
                                    containerColor = glass.aquaContainer,
                                    contentColor = glass.onAquaContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PrismaticButton(
                                text = "Edit",
                                onClick = {
                                    editingCycleId = cycle.id
                                    labelInput = cycle.label
                                    startEpochDay = cycle.startDateEpochDay
                                    salaryInput = if (cycle.receivedSalarySen > 0L) {
                                        MyrFormatter.formatInputValue(cycle.receivedSalarySen)
                                    } else {
                                        ""
                                    }
                                    errorMessage = null
                                },
                                variant = PrismaticButtonVariant.Ghost
                            )
                            PrismaticButton(
                                text = "Delete",
                                onClick = { pendingDeleteId = cycle.id },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = LocalDate.ofEpochDay(startEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            startEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
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

    pendingDeleteId?.let { deleteId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete Salary Cycle?") },
            text = { Text("Removes this cycle boundary only. Underlying wallet transactions remain untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCycle(deleteId)
                        if (editingCycleId == deleteId) {
                            editingCycleId = null
                            labelInput = ""
                            salaryInput = ""
                        }
                        pendingDeleteId = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}
