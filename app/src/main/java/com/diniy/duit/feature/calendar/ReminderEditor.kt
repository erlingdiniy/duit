package com.diniy.duit.feature.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.CandyDialog
import com.diniy.duit.core.ui.components.CandyFullScreenForm
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun CommitmentFrequency.repeatLabel() = when (this) {
    CommitmentFrequency.ONE_TIME -> "Once"
    CommitmentFrequency.WEEKLY -> "Weekly"
    CommitmentFrequency.MONTHLY -> "Monthly"
    CommitmentFrequency.YEARLY -> "Yearly"
    else -> displayName
}

/** Create or edit a custom money reminder. Informational only: saving never moves money. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEditor(
    existing: MoneyReminder?,
    defaultDay: Long,
    onClose: () -> Unit,
    onSave: (MoneyReminder?, String, Long, CommitmentFrequency, String, () -> Unit) -> String?,
    onDelete: (Long, () -> Unit) -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var day by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: defaultDay) }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeat ?: CommitmentFrequency.ONE_TIME) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    CandyFullScreenForm(
        title = if (existing == null) "New Reminder" else "Edit Reminder",
        subtitle = "Just a nudge. It never adds a transaction or moves money.",
        onDismissRequest = onClose
    ) {
        PrismaticTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = "Title",
            placeholder = "e.g., Check bonus, Renew road tax",
            errorText = error?.takeIf { ReminderRules.titleError(title) != null },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(16.dp))
        Text("Date", style = type.inputLabel, color = colors.secondaryText)
        Spacer(Modifier.height(6.dp))
        SpatialGlassButton(
            text = DateTimeFormatters.formatEpochDay(day),
            onClick = { showDatePicker = true },
            variant = SpatialButtonVariant.SecondaryGlass,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text("Repeat", style = type.inputLabel, color = colors.secondaryText)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderRules.REPEATS.forEach { option ->
                SpatialGlassPill(label = option.repeatLabel(), selected = option == repeat, onClick = { repeat = option })
            }
        }
        Spacer(Modifier.height(16.dp))
        PrismaticTextField(
            value = note,
            onValueChange = { note = it; error = null },
            label = "Notes (optional)",
            singleLine = false,
            supportingText = "${note.trim().length}/${ReminderRules.NOTE_MAX}",
            errorText = error?.takeIf { ReminderRules.noteError(note) != null }
        )
        Spacer(Modifier.height(24.dp))
        SpatialGlassButton(
            text = if (existing == null) "Save Reminder" else "Save Changes",
            onClick = { error = onSave(existing, title, day, repeat, note, onClose) },
            modifier = Modifier.fillMaxWidth()
        )
        if (existing != null) {
            Spacer(Modifier.height(12.dp))
            SpatialGlassButton(
                text = "Delete Reminder",
                onClick = { confirmDelete = true },
                variant = SpatialButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight; keep it as a plain calendar date.
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (confirmDelete && existing != null) {
        CandyDialog(onDismiss = { confirmDelete = false }) {
            Text(
                "Delete this reminder?",
                style = type.sectionHeading,
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "\"${existing.title}\" will be removed from your calendar. Your money and transactions aren't affected.",
                style = type.supportingInfo,
                color = colors.secondaryText
            )
            Spacer(Modifier.height(20.dp))
            SpatialGlassButton(
                text = "Delete Reminder",
                onClick = { confirmDelete = false; onDelete(existing.id, onClose) },
                variant = SpatialButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SpatialGlassButton(
                text = "Keep It",
                onClick = { confirmDelete = false },
                variant = SpatialButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
