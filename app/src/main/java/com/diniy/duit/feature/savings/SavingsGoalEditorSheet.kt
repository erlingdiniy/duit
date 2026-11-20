package com.diniy.duit.feature.savings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavingsGoalEditorSheet(
    existingGoal: SavingsGoal?,
    onSave: (SavingsGoal, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.name.orEmpty()) }
    var targetInput by rememberSaveable(existingGoal?.id) {
        mutableStateOf(
            if (existingGoal != null && existingGoal.targetAmountSen > 0L) {
                MyrFormatter.formatInputValue(existingGoal.targetAmountSen)
            } else {
                ""
            }
        )
    }
    var targetDateEpochDay by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.targetDateEpochDay)
    }
    var description by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.description.orEmpty())
    }
    var iconKey by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.iconKey ?: "savings")
    }
    var accentToken by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.accentToken ?: PrismaticAccentToken.SKY_BLUE)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    // Public edition: a multi-field form gets a full screen; the personal edition keeps its sheet.
    val title = if (existingGoal == null) "New Savings Goal" else "Edit Savings Goal"
    val subtitle = "Set a Ringgit target with an optional timeline and accent."
    val container: @Composable (@Composable ColumnScope.() -> Unit) -> Unit = { body ->
        if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
            com.diniy.duit.core.ui.components.CandyFullScreenForm(title = title, subtitle = subtitle, onDismissRequest = onDismiss, content = body)
        } else {
            PrismaticModalBottomSheet(title = title, subtitle = subtitle, onDismissRequest = onDismiss, content = body)
        }
    }
    container {
        if (errorMessage != null) {
            PrismaticErrorBanner(
                message = errorMessage!!,
                onDismiss = { errorMessage = null }
            )
            Spacer(Modifier.height(12.dp))
        }

        PrismaticTextField(
            value = name,
            onValueChange = {
                name = it
                errorMessage = null
            },
            label = "Goal name",
            placeholder = "e.g., Emergency Fund, Japan Trip",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(12.dp))

        PrismaticTextField(
            value = targetInput,
            onValueChange = {
                targetInput = it
                errorMessage = null
            },
            label = "Target amount",
            placeholder = "5000.00",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Target date (optional)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrismaticButton(
                text = targetDateEpochDay?.let { DateTimeFormatters.formatEpochDay(it) } ?: "Pick target date",
                onClick = { showDatePicker = true },
                variant = PrismaticButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            )
            if (targetDateEpochDay != null) {
                PrismaticButton(
                    text = "No deadline",
                    onClick = { targetDateEpochDay = null },
                    variant = PrismaticButtonVariant.Ghost
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PrismaticTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description (optional)",
            placeholder = "Why this milestone matters",
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            )
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Icon",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SavingsIconMapper.selectableIcons.forEach { (key, label) ->
                if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                    com.diniy.duit.core.ui.components.SpatialGlassPill(
                        label = label,
                        selected = iconKey == key,
                        onClick = { iconKey = key },
                        leadingIcon = { Icon(SavingsIconMapper.iconFor(key), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                } else FilterChip(
                    selected = iconKey == key,
                    onClick = { iconKey = key },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = SavingsIconMapper.iconFor(key),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PrismaticAccentToken.entries.forEach { token ->
                val (accentColor, _, _) = SavingsIconMapper.accentColors(token)
                val tokenName = token.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                    val selected = accentToken == token
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) Modifier.border(3.dp, com.diniy.duit.core.ui.theme.SpatialTheme.colors.primaryText, CircleShape)
                                else Modifier
                            )
                            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { accentToken = token })
                            .semantics { contentDescription = "$tokenName colour" }
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(accentColor))
                    }
                } else FilterChip(
                    selected = accentToken == token,
                    onClick = { accentToken = token },
                    label = {
                        Text(
                            token.name.lowercase()
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

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
                text = if (existingGoal == null) "Create Goal" else "Save Changes",
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Enter a savings goal name."
                        return@PrismaticButton
                    }
                    val targetSen = MyrFormatter.parseToSen(targetInput)
                    if (targetSen == null || targetSen <= 0L) {
                        errorMessage = "Enter a valid target amount greater than RM 0.00."
                        return@PrismaticButton
                    }

                    isSubmitting = true
                    val now = System.currentTimeMillis()
                    val goal = SavingsGoal(
                        id = existingGoal?.id ?: 0L,
                        name = trimmedName,
                        targetAmountSen = targetSen,
                        targetDateEpochDay = targetDateEpochDay,
                        description = description.trim(),
                        iconKey = iconKey,
                        accentToken = accentToken,
                        status = existingGoal?.status ?: SavingsGoalStatus.ACTIVE,
                        createdAtEpochMs = existingGoal?.createdAtEpochMs ?: now,
                        updatedAtEpochMs = now
                    )
                    onSave(goal) { msg ->
                        isSubmitting = false
                        errorMessage = msg
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (showDatePicker) {
        val initialDate = targetDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusMonths(3)
        val initialUtcMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            targetDateEpochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
