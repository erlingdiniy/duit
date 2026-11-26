package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.ANDROID_BROADCAST_CHANNEL_ID
import com.diniy.duit.devcenter.BroadcastAudience
import com.diniy.duit.devcenter.BroadcastCategory
import com.diniy.duit.devcenter.BroadcastDestination
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterValidators
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.ValidationResult

@Composable
fun DevNotificationsScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val composer = uiState.composer
    val hasPat = !uiState.vault.encryptedGithubPat.isNullOrBlank()
    val customKillEnabled = uiState.dashboard.killSwitches.customBroadcastEnabled

    if (composer.showLiveConfirmDialog) {
        LiveBroadcastConfirmDialog(
            audience = composer.audience,
            title = composer.title,
            onDismiss = viewModel::dismissLiveConfirmDialog,
            onConfirm = { phrase, reason ->
                viewModel.submitBroadcast(
                    validateOnly = false,
                    confirmationPhrase = phrase,
                    reason = reason
                )
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasPat) {
            item(key = "no_pat_notice") {
                SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = SpatialGlassTier.Elevated,
                    accentTint = colors.warning
                ) {
                    Text(
                        text = "GitHub PAT Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primaryText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Connect your fine-grained GitHub PAT in Developer Settings to trigger custom FCM relays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    PrismaticButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "composer_card") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Compose Broadcast")
                    val badgeLabel = when (customKillEnabled) {
                        true -> "Live Enabled"
                        false -> "Live Paused"
                        null -> "Dry-Run Ready"
                    }
                    val badgeTone = when (customKillEnabled) {
                        true -> "enabled"
                        false -> "paused"
                        null -> "warning"
                    }
                    DevToneBadge(label = badgeLabel, tone = badgeTone)
                }

                Spacer(Modifier.height(10.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                Spacer(Modifier.height(4.dp))
                DevChoiceChipRow(
                    items = BroadcastCategory.entries,
                    selected = composer.category,
                    labelOf = { it.label },
                    onSelect = viewModel::updateComposerCategory
                )

                Spacer(Modifier.height(10.dp))
                Text("Audience", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                Spacer(Modifier.height(4.dp))
                DevChoiceChipRow(
                    items = BroadcastAudience.entries,
                    selected = composer.audience,
                    labelOf = { it.label },
                    onSelect = viewModel::updateComposerAudience
                )

                Spacer(Modifier.height(10.dp))
                Text("Tap Destination", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                Spacer(Modifier.height(4.dp))
                DevChoiceChipRow(
                    items = BroadcastDestination.entries,
                    selected = composer.route,
                    labelOf = { it.label },
                    onSelect = viewModel::updateComposerRoute
                )

                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = composer.title,
                    onValueChange = viewModel::updateComposerTitle,
                    label = "Title (${composer.title.length}/100)",
                    placeholder = "Maintenance or release update"
                )

                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = composer.body,
                    onValueChange = viewModel::updateComposerBody,
                    label = "Message (${composer.body.length}/220)",
                    placeholder = "Offline budgets & receipts stay available locally.",
                    singleLine = false
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrismaticTextField(
                        value = composer.broadcastId,
                        onValueChange = viewModel::updateComposerBroadcastId,
                        label = "Broadcast ID",
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = "New ID",
                        onClick = viewModel::regenerateBroadcastId,
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
            }
        }

        item(key = "preview_card") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = colors.aqua
            ) {
                DevSectionTitle("Notification Preview")
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.baseSurface)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryAction.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = colors.primaryAction,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "duit., now, ${composer.category.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.secondaryText
                            )
                        }
                        Text(
                            text = composer.title.ifBlank { "Notification title preview" },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText
                        )
                        Text(
                            text = composer.body.ifBlank { "Notification message body preview." },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DevToneBadge(label = composer.audience.topics.joinToString("+"), tone = "neutral")
                            DevToneBadge(label = "Opens: ${composer.route.label}", tone = "neutral")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Channel: $ANDROID_BROADCAST_CHANNEL_ID. FCM acceptance does not guarantee delivery on sleeping devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            }
        }

        item(key = "actions_card") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                if (uiState.cooldownRemainingSeconds > 0) {
                    DevKeyValueRow(
                        label = "Live Cooldown",
                        value = "${uiState.cooldownRemainingSeconds}s remaining",
                        valueColor = colors.warning
                    )
                    Spacer(Modifier.height(6.dp))
                }

                uiState.latestCustomRun?.let { run ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Latest Relay Run #${run.id}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primaryText
                            )
                            Text(
                                text = "${run.status}, ${formatIsoShort(run.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        DevToneBadge(label = run.conclusion, tone = run.conclusion)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrismaticButton(
                        text = "Dry-Run",
                        onClick = { viewModel.submitBroadcast(validateOnly = true) },
                        enabled = hasPat && !uiState.isDispatching,
                        isLoading = uiState.isDispatching,
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = "Send Live",
                        onClick = viewModel::openLiveConfirmDialog,
                        enabled = hasPat &&
                            customKillEnabled == true &&
                            uiState.cooldownRemainingSeconds == 0 &&
                            !uiState.isDispatching,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBroadcastConfirmDialog(
    audience: BroadcastAudience,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (phrase: String, reason: String) -> Unit
) {
    var phraseInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    val ready = phraseInput.trim() == audience.confirmationPhrase &&
        DevCenterValidators.cleanInputText(reasonInput, 240).length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Public Broadcast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target: ${audience.label} (${audience.topics.joinToString()}). Title: \"$title\". Cannot be recalled after FCM accepts it.",
                    style = MaterialTheme.typography.bodySmall
                )
                PrismaticTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = "Type ${audience.confirmationPhrase}",
                    placeholder = audience.confirmationPhrase
                )
                PrismaticTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = "Operator reason",
                    placeholder = "Scheduled announcement"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onConfirm(phraseInput, reasonInput) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Send Now")
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
