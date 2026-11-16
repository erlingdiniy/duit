package com.diniy.duit.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.CompanyCommissionEngine
import com.diniy.duit.domain.model.DailySalesRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesSheet(
    records: List<DailySalesRecord>,
    initialEditingRecord: DailySalesRecord? = null,
    onSaveRecord: (
        record: DailySalesRecord,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var editingId by rememberSaveable(initialEditingRecord?.id) {
        mutableStateOf(initialEditingRecord?.id)
    }
    var dateEpochDay by rememberSaveable(initialEditingRecord?.id) {
        mutableLongStateOf(initialEditingRecord?.recordDateEpochDay ?: LocalDate.now().toEpochDay())
    }
    var salesInput by rememberSaveable(initialEditingRecord?.id) {
        mutableStateOf(
            initialEditingRecord?.let { MyrFormatter.formatInputValue(it.grossSalesSen) } ?: ""
        )
    }
    var gpInput by rememberSaveable(initialEditingRecord?.id) {
        mutableStateOf(
            initialEditingRecord?.let { MyrFormatter.formatInputValue(it.grossProfitSen) } ?: ""
        )
    }
    var notesInput by rememberSaveable(initialEditingRecord?.id) {
        mutableStateOf(initialEditingRecord?.notes ?: "")
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val parsedSales = MyrFormatter.parseToSen(salesInput) ?: 0L
    val parsedGp = MyrFormatter.parseToSen(gpInput) ?: 0L
    val liveMargin = CompanyCommissionEngine.formatGpMargin(parsedSales, parsedGp)

    PrismaticModalBottomSheet(
        title = "Daily Sales & GP Tracker",
        subtitle = "Log daily sales and gross profit in MYR. Backdated entries aggregate automatically by month.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (editingId == null) "Log Daily Sales & GP" else "Edit Daily Record",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Date: ${DateTimeFormatters.formatEpochDay(dateEpochDay)}",
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
                    value = salesInput,
                    onValueChange = {
                        salesInput = it
                        errorMessage = null
                    },
                    label = "Daily sales (MYR)"
                )
                Spacer(Modifier.height(10.dp))
                PrismaticMoneyField(
                    value = gpInput,
                    onValueChange = {
                        gpInput = it
                        errorMessage = null
                    },
                    label = "Daily gross profit (MYR)",
                    supportingText = "Daily GP Margin: $liveMargin",
                    errorText = errorMessage
                )
                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = "Notes (optional)",
                    placeholder = "e.g. Corporate order, promo day"
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingId != null) {
                        PrismaticButton(
                            text = "Cancel Edit",
                            onClick = {
                                editingId = null
                                salesInput = ""
                                gpInput = ""
                                notesInput = ""
                                errorMessage = null
                            },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrismaticButton(
                        text = if (editingId == null) "Save Day" else "Update Day",
                        onClick = {
                            val salesSen = MyrFormatter.parseToSen(salesInput) ?: run {
                                errorMessage = "Enter a valid daily sales RM amount."
                                return@PrismaticButton
                            }
                            val gpSen = MyrFormatter.parseToSen(gpInput) ?: run {
                                errorMessage = "Enter a valid daily gross profit RM amount."
                                return@PrismaticButton
                            }
                            val existing = editingId?.let { id -> records.firstOrNull { it.id == id } }
                            onSaveRecord(
                                DailySalesRecord(
                                    id = editingId ?: 0L,
                                    recordDateEpochDay = dateEpochDay,
                                    grossSalesSen = salesSen,
                                    netSalesSen = salesSen,
                                    unitsSold = existing?.unitsSold ?: 0,
                                    notes = notesInput.trim(),
                                    createdAtEpochMs = existing?.createdAtEpochMs ?: System.currentTimeMillis(),
                                    grossProfitSen = gpSen
                                ),
                                { errorMessage = it },
                                {
                                    editingId = null
                                    salesInput = ""
                                    gpInput = ""
                                    notesInput = ""
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
                text = "History (${records.size} entries)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (records.isEmpty()) {
                Text(
                    text = "No daily sales entries logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                records.forEach { rec ->
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = GlassCardTier.Standard,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DateTimeFormatters.formatEpochDay(rec.recordDateEpochDay),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sales ${MyrFormatter.format(rec.grossSalesSen)} · GP ${MyrFormatter.format(rec.grossProfitSen)} (${CompanyCommissionEngine.formatGpMargin(rec.grossSalesSen, rec.grossProfitSen)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (rec.notes.isNotBlank()) {
                                    Text(
                                        text = rec.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PrismaticButton(
                                text = "Edit",
                                onClick = {
                                    editingId = rec.id
                                    dateEpochDay = rec.recordDateEpochDay
                                    salesInput = MyrFormatter.formatInputValue(rec.grossSalesSen)
                                    gpInput = MyrFormatter.formatInputValue(rec.grossProfitSen)
                                    notesInput = rec.notes
                                    errorMessage = null
                                },
                                variant = PrismaticButtonVariant.Ghost
                            )
                            PrismaticButton(
                                text = "Delete",
                                onClick = { pendingDeleteId = rec.id },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = LocalDate.ofEpochDay(dateEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            dateEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
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

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete Daily Sales Entry?") },
            text = { Text("Removes this daily sales and GP record from monthly aggregation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecord(id)
                        if (editingId == id) {
                            editingId = null
                            salesInput = ""
                            gpInput = ""
                            notesInput = ""
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
