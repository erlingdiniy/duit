package com.diniy.duit.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass

/**
 * Frosted rounded text input for Prismatic Glass forms.
 */
@Composable
fun PrismaticTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefixText: String? = null,
    suffixText: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val glass = LocalPrismaticGlass.current
    val isError = errorText != null

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyLarge) }
            },
            prefix = prefixText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            suffix = suffixText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassSurfaceElevated,
                unfocusedContainerColor = glass.glassSurface,
                disabledContainerColor = glass.glassSurfaceMuted,
                errorContainerColor = glass.glassSurfaceElevated,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = glass.glassBorderSubtle,
                errorBorderColor = MaterialTheme.colorScheme.error
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        )

        val caption = errorText ?: supportingText
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Decimal MYR input field that restricts input characters to digits and up to 2 decimal places.
 * Downstream parsers convert the string directly into integer minor units (sen).
 */
@Composable
fun PrismaticMoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done
) {
    PrismaticTextField(
        value = value,
        onValueChange = { incoming ->
            val filtered = sanitizeDecimalInput(incoming)
            if (filtered != null) onValueChange(filtered)
        },
        label = label,
        placeholder = "0.00",
        prefixText = "RM ",
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        modifier = modifier
    )
}

internal fun sanitizeDecimalInput(raw: String): String? {
    if (raw.isEmpty()) return ""
    val normalized = raw.replace(",", "")
    if (normalized.count { it == '.' } > 1) return null
    val parts = normalized.split(".")
    if (!parts[0].all { it.isDigit() }) return null
    if (parts.size == 2 && (!parts[1].all { it.isDigit() } || parts[1].length > 2)) return null
    return normalized
}
