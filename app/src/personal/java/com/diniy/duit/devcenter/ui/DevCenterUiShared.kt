package com.diniy.duit.devcenter.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterValidators
import com.diniy.duit.devcenter.PendingKillSwitchModal
import com.diniy.duit.devcenter.ValidationResult

@Composable
internal fun DevSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    Text(
        text = title,
        style = typography.sectionHeading,
        color = colors.primaryText,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
internal fun DevStatusBanner(
    message: String,
    isSuccess: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tint = if (isSuccess) colors.success else colors.error
    val textColor = if (isSuccess) colors.onSuccessContainer else colors.onErrorContainer
    PrismaticGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        tier = GlassCardTier.Elevated,
        accentTint = tint,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Dismiss")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> DevChoiceChipRow(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(item) },
                label = { Text(labelOf(item), maxLines = 1) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primaryAction.copy(alpha = 0.16f),
                    selectedLabelColor = colors.primaryAction
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

@Composable
internal fun DevToneBadge(
    label: String,
    tone: String
) {
    val colors = SpatialTheme.colors
    val (bg, fg) = when (tone.lowercase()) {
        "success", "ok", "healthy", "sent", "accepted_by_fcm", "done", "reviewed", "enabled" ->
            colors.successContainer to colors.onSuccessContainer
        "warning", "partial", "ambiguous", "paused", "preseeded_skip", "blocked", "planned" ->
            colors.warningContainer to colors.onWarningContainer
        "error", "failed", "failure" ->
            colors.errorContainer to colors.onErrorContainer
        else ->
            colors.savingsContainer to colors.onSavingsContainer
    }
    PrismaticBadge(label = label, containerColor = bg, contentColor = fg)
}

@Composable
internal fun DevKeyValueRow(
    label: String,
    value: String,
    valueColor: Color = SpatialTheme.colors.primaryText
) {
    val colors = SpatialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor
        )
    }
}

@Composable
internal fun KillSwitchConfirmDialog(
    pending: PendingKillSwitchModal,
    onDismiss: () -> Unit,
    onConfirm: (phrase: String, reason: String) -> Unit
) {
    val expectedPhrase = pending.target.confirmationPhrase(pending.nextEnabled)
    var phraseInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    val ready = DevCenterValidators.validateKillSwitchConfirmation(
        target = pending.target,
        enable = pending.nextEnabled,
        confirmationPhrase = phraseInput,
        rawReason = reasonInput
    ) is ValidationResult.Valid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (pending.nextEnabled) "Enable ${pending.target.label}"
                else "Pause ${pending.target.label}"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Updates ${pending.target.variableName} on GitHub. Type $expectedPhrase and a short reason.",
                    style = MaterialTheme.typography.bodySmall
                )
                PrismaticTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = "Confirmation phrase",
                    placeholder = expectedPhrase
                )
                PrismaticTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = "Operator reason",
                    placeholder = "Scheduled check"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onConfirm(phraseInput, reasonInput) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (pending.nextEnabled) "Enable" else "Pause")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

internal fun formatIsoShort(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return iso.replace("T", " ").removeSuffix("Z").take(16)
}

internal fun formatApkSizeMb(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "—"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.2f MB", mb)
}

internal fun android.content.Context.openHttpsUrl(url: String) {
    if (!url.startsWith("https://")) return
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
