package com.diniy.duit.devcenter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
internal fun DevSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DevSectionTitle(
            title = title,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        if (trailingLabel != null && onTrailingClick != null) {
            TextButton(
                onClick = onTrailingClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = trailingLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
    SpatialGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        tier = SpatialGlassTier.Elevated,
        accentTint = tint,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Dismiss", maxLines = 1)
            }
        }
    }
}

@Composable
internal fun <T> DevSegmentedBar(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    badgeCountOf: ((T) -> Int)? = null
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    val trackShape = RoundedCornerShape(18.dp)
    val pillShape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(colors.baseSurface.copy(alpha = if (colors.isDark) 0.9f else 0.96f))
            .border(BorderStroke(1.dp, tokens.dividerColor), trackShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val badgeCount = badgeCountOf?.invoke(item) ?: 0
            val containerColor = if (isSelected) {
                if (tokens.style == DevVisualSurfaceStyle.CANDY_BUBBLE) colors.coral else colors.elevatedSurface
            } else {
                Color.Transparent
            }
            val labelColor = if (isSelected) {
                if (tokens.style == DevVisualSurfaceStyle.CANDY_BUBBLE) colors.onCoral else colors.primaryAction
            } else {
                colors.secondaryText
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(pillShape)
                    .background(containerColor)
                    .then(
                        if (isSelected && tokens.style == DevVisualSurfaceStyle.LIQUID_GLASS) {
                            Modifier.border(BorderStroke(1.dp, colors.glassBorder), pillShape)
                        } else {
                            Modifier
                        }
                    )
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                    }
                    .clickable(
                        role = Role.Tab,
                        onClickLabel = labelOf(item),
                        onClick = { onSelect(item) }
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = labelOf(item),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isSelected) labelColor.copy(alpha = 0.16f)
                                    else colors.warningContainer
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) labelColor else colors.onWarningContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DevSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primaryAction,
            unfocusedBorderColor = colors.divider,
            focusedContainerColor = colors.baseSurface.copy(alpha = 0.65f),
            unfocusedContainerColor = colors.baseSurface.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    )
}

@Composable
internal fun DevQuickActionTile(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    badgeText: String? = null,
    accentTint: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    val tint = accentTint ?: tokens.primaryAccent
    SpatialGlassCard(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(20.dp))
            .candyPressable(
                onClick = onClick,
                onClickLabel = "Open $label"
            ),
        tier = SpatialGlassTier.Standard,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (colors.isDark) tint else colors.readableOn(tint),
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subText = badgeText ?: subtitle
                if (!subText.isNullOrBlank()) {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                label = { Text(labelOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        "success", "ok", "healthy", "sent", "accepted_by_fcm", "done", "reviewed",
        "enabled", "resolved", "open", "private", "verified", "verified_remote" ->
            colors.successContainer to colors.onSuccessContainer
        "warning", "partial", "ambiguous", "paused", "preseeded_skip", "blocked",
        "planned", "degraded", "pending", "in_progress", "high", "unconfigured", "cached" ->
            colors.warningContainer to colors.onWarningContainer
        "error", "failed", "failure", "offline", "critical", "public", "mismatch" ->
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
    valueColor: Color = SpatialTheme.colors.primaryText,
    monospaced: Boolean = false,
    stacked: Boolean = false
) {
    val colors = SpatialTheme.colors
    val autoStack = stacked || (value.length > 26 && (value.startsWith("http") || value.contains("/") || !value.contains(" ")))
    val valueStyle = if (monospaced) {
        MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.labelLarge
    }

    if (autoStack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.25f, fill = false)
            )
        }
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

internal fun formatIsoCompact(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val clean = iso.replace("T", " ").removeSuffix("Z")
    return if (clean.length >= 16) clean.substring(5, 16) else clean
}

internal fun formatApkSizeMb(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "—"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.2f MB", mb)
}

internal fun Context.openHttpsUrl(url: String) {
    if (!url.startsWith("https://")) return
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun Context.copyToClipboard(label: String, value: String) {
    if (value.isBlank()) return
    runCatching {
        val mgr = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        mgr.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "Copied $label", Toast.LENGTH_SHORT).show()
    }
}

internal fun Context.openMailto(email: String, subject: String) {
    val cleanEmail = email.trim()
    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) return
    runCatching {
        val uri = Uri.parse("mailto:${Uri.encode(cleanEmail)}?subject=${Uri.encode(subject)}")
        startActivity(
            Intent(Intent.ACTION_SENDTO, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
