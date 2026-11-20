package com.diniy.duit.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticMoneyField
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.sanitizeDecimalInput
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.calculator.CommissionCalculationResult
import com.diniy.duit.domain.model.CommissionFormulaConfig

/**
 * Configurable Company Commission Calculator bottom sheet.
 * Ships completely unconfigured by default without any invented or pre-activated formula.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionCalculatorSheet(
    config: CommissionFormulaConfig,
    salesInput: String,
    calculationResult: CommissionCalculationResult,
    onSalesInputChange: (String) -> Unit,
    onSaveFormula: (
        formulaName: String,
        thresholdInput: String,
        ratePercentInput: String,
        flatBonusInput: String,
        enableImmediately: Boolean,
        onValidationError: (String) -> Unit,
        onSaved: () -> Unit
    ) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onResetFormula: () -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current

    var formulaName by rememberSaveable(config.formulaName) { mutableStateOf(config.formulaName) }
    var thresholdInput by rememberSaveable(config.minimumSalesThresholdSen) {
        mutableStateOf(
            if (config.isConfigured && config.minimumSalesThresholdSen > 0L) {
                MyrFormatter.formatInputValue(config.minimumSalesThresholdSen)
            } else {
                ""
            }
        )
    }
    var ratePercentInput by rememberSaveable(config.rateBps) {
        mutableStateOf(
            if (config.isConfigured && config.rateBps > 0) {
                (config.rateBps / 100).toString() + "." + (config.rateBps % 100).toString().padStart(2, '0')
            } else {
                ""
            }
        )
    }
    var flatBonusInput by rememberSaveable(config.flatBonusSen) {
        mutableStateOf(
            if (config.isConfigured && config.flatBonusSen > 0L) {
                MyrFormatter.formatInputValue(config.flatBonusSen)
            } else {
                ""
            }
        )
    }
    var enableOnSave by rememberSaveable(config.isEnabled) { mutableStateOf(config.isEnabled) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var statusNotice by rememberSaveable { mutableStateOf<String?>(null) }

    PrismaticModalBottomSheet(
        title = "Company Commission Calculator",
        subtitle = "No preset formula is built in. Configure your own company rule to run local estimates.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Module status",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    when {
                        !config.isConfigured -> PrismaticBadge(
                            label = "Unconfigured",
                            containerColor = glass.peachContainer,
                            contentColor = glass.onPeachContainer
                        )

                        config.isEnabled -> PrismaticBadge(
                            label = "Active",
                            containerColor = glass.aquaContainer,
                            contentColor = glass.onAquaContainer
                        )

                        else -> PrismaticBadge(
                            label = "Paused",
                            containerColor = glass.lavenderContainer,
                            contentColor = glass.onLavenderContainer
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pending or estimated commissions are never counted as Safe to Spend until settled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (config.isConfigured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = config.isEnabled,
                            role = Role.Switch,
                            onValueChange = onToggleEnabled
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Activate saved formula",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${config.formulaName} · ${MyrFormatter.formatBpsAsPercent(config.rateBps)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = null,
                        colors = com.diniy.duit.core.ui.components.candySwitchColors()
                    )
                }
            }

            PrismaticTextField(
                value = formulaName,
                onValueChange = {
                    formulaName = it
                    validationError = null
                    statusNotice = null
                },
                label = "Company rule label",
                placeholder = "e.g. Retail Tier Q4"
            )

            PrismaticMoneyField(
                value = thresholdInput,
                onValueChange = {
                    thresholdInput = it
                    validationError = null
                    statusNotice = null
                },
                label = "Minimum sales threshold",
                supportingText = "Optional minimum sales before rate applies"
            )

            PrismaticTextField(
                value = ratePercentInput,
                onValueChange = { incoming ->
                    val filtered = sanitizeDecimalInput(incoming)
                    if (filtered != null) {
                        ratePercentInput = filtered
                        validationError = null
                        statusNotice = null
                    }
                },
                label = "Commission rate",
                placeholder = "0.00",
                suffixText = "%",
                supportingText = "Supports up to two decimal places (e.g. 3.50%)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            PrismaticMoneyField(
                value = flatBonusInput,
                onValueChange = {
                    flatBonusInput = it
                    validationError = null
                    statusNotice = null
                },
                label = "Flat bonus (optional)",
                errorText = validationError
            )

            if (!config.isConfigured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = enableOnSave,
                            role = Role.Switch,
                            onValueChange = { enableOnSave = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activate immediately after saving",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enableOnSave,
                        onCheckedChange = null,
                        colors = com.diniy.duit.core.ui.components.candySwitchColors()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (config.isConfigured) {
                    PrismaticButton(
                        text = "Reset",
                        onClick = {
                            onResetFormula()
                            formulaName = ""
                            thresholdInput = ""
                            ratePercentInput = ""
                            flatBonusInput = ""
                            enableOnSave = false
                            statusNotice = "Formula cleared."
                        },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                PrismaticButton(
                    text = "Save Formula",
                    onClick = {
                        onSaveFormula(
                            formulaName,
                            thresholdInput,
                            ratePercentInput,
                            flatBonusInput,
                            enableOnSave,
                            { validationError = it },
                            { statusNotice = "Company formula saved locally." }
                        )
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }

            if (statusNotice != null) {
                Text(
                    text = statusNotice!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.positiveText
                )
            }

            HorizontalDivider(color = glass.glassBorderSubtle)

            Text(
                text = "Estimate Payout",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            PrismaticMoneyField(
                value = salesInput,
                onValueChange = onSalesInputChange,
                label = "Sales volume to test",
                enabled = config.isConfigured && config.isEnabled,
                supportingText = if (config.isConfigured && config.isEnabled) {
                    "Preview an estimate without saving to your activity"
                } else {
                    "Configure and activate your company formula above to run estimates"
                }
            )

            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Standard,
                accentTint = if (calculationResult is CommissionCalculationResult.Calculated) {
                    glass.aquaAccent
                } else {
                    null
                }
            ) {
                when (calculationResult) {
                    is CommissionCalculationResult.NotConfigured -> {
                        Text(
                            text = "Estimator Inactive",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = calculationResult.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is CommissionCalculationResult.Calculated -> {
                        Text(
                            text = if (calculationResult.thresholdMet) {
                                "Estimated Commission"
                            } else {
                                "Threshold Not Met"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = MyrFormatter.format(calculationResult.totalEstimatedCommissionSen),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Rate: ${MyrFormatter.format(calculationResult.rateCommissionSen)} · Bonus: ${MyrFormatter.format(calculationResult.flatBonusAppliedSen)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
