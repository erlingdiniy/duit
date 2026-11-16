package com.diniy.duit.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.sanitizeDecimalInput
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.CommissionRuleVersion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Edits effective-dated Company Memo commission rule versions without rewriting
 * past months' historical estimates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionRuleEditorSheet(
    currentRule: CommissionRuleVersion,
    ruleVersions: List<CommissionRuleVersion>,
    onSaveRuleVersion: (
        rule: CommissionRuleVersion,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current

    var ruleName by rememberSaveable(currentRule.id) { mutableStateOf(currentRule.ruleName) }
    var effectiveEpochDay by rememberSaveable(currentRule.id) {
        mutableLongStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay())
    }
    var basicSalaryInput by rememberSaveable(currentRule.id) {
        mutableStateOf(MyrFormatter.formatInputValue(currentRule.basicSalarySen))
    }
    var minSalesInput by rememberSaveable(currentRule.id) {
        mutableStateOf(MyrFormatter.formatInputValue(currentRule.minMonthlySalesSen))
    }
    var employerCostPctInput by rememberSaveable(currentRule.id) {
        mutableStateOf(bpsToEditableString(currentRule.employerCostRateBps))
    }
    var standardRatePctInput by rememberSaveable(currentRule.id) {
        mutableStateOf(bpsToEditableString(currentRule.standardRateBps))
    }
    var incentiveThresholdInput by rememberSaveable(currentRule.id) {
        mutableStateOf(MyrFormatter.formatInputValue(currentRule.incentiveBaselineThresholdSen))
    }
    var incentiveRatePctInput by rememberSaveable(currentRule.id) {
        mutableStateOf(bpsToEditableString(currentRule.incentiveRateBps))
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statusNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    PrismaticModalBottomSheet(
        title = "Company Commission Rules",
        subtitle = "Save effective-dated rule versions so future changes never overwrite past months.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrismaticTextField(
                value = ruleName,
                onValueChange = {
                    ruleName = it
                    errorMessage = null
                    statusNotice = null
                },
                label = "Rule version label",
                placeholder = "e.g. Updated Memo 2026"
            )

            PrismaticButton(
                text = "Effective From: ${DateTimeFormatters.formatEpochDay(effectiveEpochDay)}",
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

            PrismaticMoneyField(
                value = basicSalaryInput,
                onValueChange = {
                    basicSalaryInput = it
                    errorMessage = null
                },
                label = "Basic salary (MYR)"
            )

            PrismaticTextField(
                value = employerCostPctInput,
                onValueChange = { raw ->
                    sanitizeDecimalInput(raw)?.let {
                        employerCostPctInput = it
                        errorMessage = null
                    }
                },
                label = "Employer cost factor",
                suffixText = "%",
                supportingText = "Salary Cost = Basic Salary x (1 + factor)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            PrismaticMoneyField(
                value = minSalesInput,
                onValueChange = {
                    minSalesInput = it
                    errorMessage = null
                },
                label = "Minimum monthly personal sales (MYR)"
            )

            PrismaticTextField(
                value = standardRatePctInput,
                onValueChange = { raw ->
                    sanitizeDecimalInput(raw)?.let {
                        standardRatePctInput = it
                        errorMessage = null
                    }
                },
                label = "Standard commission rate",
                suffixText = "%",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            PrismaticMoneyField(
                value = incentiveThresholdInput,
                onValueChange = {
                    incentiveThresholdInput = it
                    errorMessage = null
                },
                label = "Incentive baseline threshold (> RM)"
            )

            PrismaticTextField(
                value = incentiveRatePctInput,
                onValueChange = { raw ->
                    sanitizeDecimalInput(raw)?.let {
                        incentiveRatePctInput = it
                        errorMessage = null
                    }
                },
                label = "Incentive rate (on full baseline)",
                suffixText = "%",
                errorText = errorMessage,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            PrismaticButton(
                text = "Save New Rule Version",
                onClick = {
                    val name = ruleName.trim()
                    if (name.isEmpty()) {
                        errorMessage = "Enter a rule version name."
                        return@PrismaticButton
                    }
                    val basicSen = MyrFormatter.parseToSen(basicSalaryInput) ?: run {
                        errorMessage = "Invalid basic salary amount."
                        return@PrismaticButton
                    }
                    val minSalesSen = MyrFormatter.parseToSen(minSalesInput) ?: run {
                        errorMessage = "Invalid minimum sales amount."
                        return@PrismaticButton
                    }
                    val employerBps = MyrFormatter.parsePercentToBps(employerCostPctInput) ?: run {
                        errorMessage = "Invalid employer cost percentage."
                        return@PrismaticButton
                    }
                    val standardBps = MyrFormatter.parsePercentToBps(standardRatePctInput) ?: run {
                        errorMessage = "Invalid standard rate percentage."
                        return@PrismaticButton
                    }
                    val thresholdSen = MyrFormatter.parseToSen(incentiveThresholdInput) ?: run {
                        errorMessage = "Invalid incentive baseline threshold."
                        return@PrismaticButton
                    }
                    val incentiveBps = MyrFormatter.parsePercentToBps(incentiveRatePctInput) ?: run {
                        errorMessage = "Invalid incentive rate percentage."
                        return@PrismaticButton
                    }

                    onSaveRuleVersion(
                        CommissionRuleVersion(
                            id = 0L, // Always creates a new effective-dated version to preserve history
                            ruleName = name,
                            effectiveFromEpochDay = effectiveEpochDay,
                            basicSalarySen = basicSen,
                            minMonthlySalesSen = minSalesSen,
                            employerCostRateBps = employerBps,
                            standardRateBps = standardBps,
                            incentiveBaselineThresholdSen = thresholdSen,
                            incentiveRateBps = incentiveBps,
                            createdAtEpochMs = System.currentTimeMillis()
                        ),
                        { errorMessage = it },
                        { statusNotice = "Rule version saved for ${DateTimeFormatters.formatEpochDay(effectiveEpochDay)} onwards." }
                    )
                },
                variant = PrismaticButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )

            if (statusNotice != null) {
                Text(
                    text = statusNotice!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.positiveText
                )
            }

            HorizontalDivider(color = glass.glassBorderSubtle)

            Text(
                text = "Effective-Dated Rule History (${ruleVersions.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            ruleVersions.forEach { version ->
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Muted,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = version.ruleName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "From ${DateTimeFormatters.formatEpochDay(version.effectiveFromEpochDay)} · Basic ${MyrFormatter.format(version.basicSalarySen)} · ${MyrFormatter.formatBpsAsPercent(version.standardRateBps)} / ${MyrFormatter.formatBpsAsPercent(version.incentiveRateBps)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = LocalDate.ofEpochDay(effectiveEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            effectiveEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
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

private fun bpsToEditableString(bps: Int): String {
    val whole = bps / 100
    val frac = bps % 100
    return "$whole.${frac.toString().padStart(2, '0')}"
}
