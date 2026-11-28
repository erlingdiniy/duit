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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.diniy.duit.devcenter.HistoryFilter
import com.diniy.duit.devcenter.KillSwitchTarget

private enum class NotificationsTab(val label: String) {
    COMPOSE("Compose"),
    LIBRARY("Templates & Drafts"),
    HISTORY("History & Test")
}

private enum class PreviewSurfaceMode(val label: String) {
    STANDARD("Unlocked"),
    LOCKSCREEN("Lockscreen")
}

@Composable
fun DevNotificationsScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val composer = uiState.composer
    val isBackendEnrolled = uiState.vault.isBackendEnrolled
    val hasPat = isBackendEnrolled || !uiState.vault.encryptedGithubPat.isNullOrBlank()
    val customKillEnabled = uiState.dashboard.killSwitches.customBroadcastEnabled
    val releaseKillEnabled = uiState.dashboard.killSwitches.releaseAutomationEnabled

    var activeTab by rememberSaveable { mutableStateOf(NotificationsTab.COMPOSE) }
    var previewMode by rememberSaveable { mutableStateOf(PreviewSurfaceMode.STANDARD) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showCloudAutoScheduleDialog by remember { mutableStateOf(false) }

    // Ephemeral credentials for single-device test ONLY; never rememberSaveable or persisted.
    var testDeviceToken by remember { mutableStateOf("") }
    var testEphemeralBearer by remember { mutableStateOf("") }

    if (composer.showLiveConfirmDialog) {
        LiveBroadcastConfirmDialog(
            audience = composer.audience,
            title = composer.title,
            onDismiss = viewModel::dismissLiveConfirmDialog,
            onConfirm = { phrase, reason ->
                viewModel.submitBroadcast(validateOnly = false, confirmationPhrase = phrase, reason = reason)
            }
        )
    }

    if (showSaveTemplateDialog) {
        SaveTemplateDialog(
            onDismiss = { showSaveTemplateDialog = false },
            onSave = { name ->
                showSaveTemplateDialog = false
                viewModel.saveCurrentAsTemplate(name)
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleDelayDialog(
            onDismiss = { showScheduleDialog = false },
            onSchedule = { mins ->
                showScheduleDialog = false
                viewModel.scheduleAnnouncement(mins)
            }
        )
    }

    if (showCloudAutoScheduleDialog) {
        CloudAutoScheduleDialog(
            audience = composer.audience,
            title = composer.title,
            onDismiss = { showCloudAutoScheduleDialog = false },
            onSchedule = { mins, phrase, reason ->
                showCloudAutoScheduleDialog = false
                viewModel.scheduleAutoBroadcast(mins, phrase, reason)
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "notif_tabs") {
            DevChoiceChipRow(
                items = NotificationsTab.entries,
                selected = activeTab,
                labelOf = { it.label },
                onSelect = { activeTab = it }
            )
        }

        when (activeTab) {
            NotificationsTab.COMPOSE -> {
                if (!hasPat) {
                    item(key = "no_pat_notice") {
                        SpatialGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tier = SpatialGlassTier.Elevated,
                            accentTint = colors.warning
                        ) {
                            DevSectionTitle("GitHub PAT Required")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Connect a fine-grained GitHub PAT in Settings to trigger public relay workflows.",
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
                            IconButton(
                                onClick = viewModel::regenerateBroadcastId,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Generate new ID",
                                    tint = colors.primaryAction
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var showComposeMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            PrismaticButton(
                                text = "Save Options",
                                onClick = { showComposeMenu = true },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = showComposeMenu,
                                onDismissRequest = { showComposeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save Draft") },
                                    onClick = {
                                        showComposeMenu = false
                                        viewModel.saveCurrentAsDraft()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Template") },
                                    onClick = {
                                        showComposeMenu = false
                                        showSaveTemplateDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Schedule Reminder") },
                                    onClick = {
                                        showComposeMenu = false
                                        showScheduleDialog = true
                                    }
                                )
                                if (isBackendEnrolled) {
                                    DropdownMenuItem(
                                        text = { Text("Auto-Schedule (Cloud Cron)") },
                                        onClick = {
                                            showComposeMenu = false
                                            showCloudAutoScheduleDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "preview_card") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.aqua
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle("Notification Preview")
                            DevChoiceChipRow(
                                items = PreviewSurfaceMode.entries,
                                selected = previewMode,
                                labelOf = { it.label },
                                onSelect = { previewMode = it },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.baseSurface)
                                .padding(14.dp)
                        ) {
                            if (previewMode == PreviewSurfaceMode.LOCKSCREEN) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = colors.primaryAction,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "duit., Lockscreen (VISIBILITY_PRIVATE)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.secondaryText
                                        )
                                    }
                                    Text(
                                        text = "New duit. ${composer.category.label.lowercase()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "Unlock device to view full details.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            } else {
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
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Channel: $ANDROID_BROADCAST_CHANNEL_ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }

                item(key = "dispatch_actions_card") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        if (uiState.cooldownRemainingSeconds > 0) {
                            DevKeyValueRow(
                                label = "Live Cooldown",
                                value = "${uiState.cooldownRemainingSeconds}s remaining",
                                valueColor = colors.warning
                            )
                            Spacer(Modifier.height(6.dp))
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

                item(key = "notif_kill_switches") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Emergency Kill Switches")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Custom Broadcasts", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                Text(KillSwitchTarget.CUSTOM_BROADCAST.variableName, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            }
                            PrismaticButton(
                                text = if (customKillEnabled == true) "Pause" else "Enable",
                                onClick = {
                                    viewModel.requestKillSwitchToggle(
                                        KillSwitchTarget.CUSTOM_BROADCAST,
                                        customKillEnabled != true
                                    )
                                },
                                enabled = hasPat && customKillEnabled != null && !uiState.isUpdatingKillSwitch,
                                variant = PrismaticButtonVariant.Secondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Release Automation", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                Text(KillSwitchTarget.RELEASE_AUTOMATION.variableName, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            }
                            PrismaticButton(
                                text = if (releaseKillEnabled == true) "Pause" else "Enable",
                                onClick = {
                                    viewModel.requestKillSwitchToggle(
                                        KillSwitchTarget.RELEASE_AUTOMATION,
                                        releaseKillEnabled != true
                                    )
                                },
                                enabled = hasPat && releaseKillEnabled != null && !uiState.isUpdatingKillSwitch,
                                variant = PrismaticButtonVariant.Secondary
                            )
                        }
                    }
                }
            }

            NotificationsTab.LIBRARY -> {
                item(key = "lib_scheduled_header") {
                    DevSectionTitle("Scheduled Reminders (${uiState.vault.scheduledBroadcasts.size})")
                }
                if (uiState.vault.scheduledBroadcasts.isEmpty()) {
                    item(key = "lib_sched_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No scheduled announcements. Reminders never send live pushes automatically.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(uiState.vault.scheduledBroadcasts, key = { it.id }) { sched ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sched.title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText, modifier = Modifier.weight(1f))
                                DevToneBadge(label = sched.status, tone = if (sched.status == "cancelled") "neutral" else "warning")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(sched.body, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Load",
                                    onClick = {
                                        viewModel.loadScheduledIntoComposer(sched)
                                        activeTab = NotificationsTab.COMPOSE
                                    },
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (sched.status == "scheduled") {
                                    PrismaticButton(
                                        text = "Cancel",
                                        onClick = { viewModel.cancelScheduledAnnouncement(sched.id) },
                                        variant = PrismaticButtonVariant.Ghost,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "lib_drafts_header") {
                    DevSectionTitle("Saved Drafts (${uiState.vault.broadcastDrafts.size})")
                }
                items(uiState.vault.broadcastDrafts, key = { it.id }) { draft ->
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Text(draft.title.ifBlank { "(Untitled)" }, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                        Text(draft.body, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Load Draft",
                                onClick = {
                                    viewModel.loadDraftIntoComposer(draft)
                                    activeTab = NotificationsTab.COMPOSE
                                },
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Delete",
                                onClick = { viewModel.deleteDraft(draft.id) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item(key = "lib_templates_header") {
                    DevSectionTitle("Templates (${uiState.vault.broadcastTemplates.size})")
                }
                items(uiState.vault.broadcastTemplates, key = { it.id }) { tpl ->
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tpl.name, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            DevToneBadge(label = tpl.audience.label, tone = "neutral")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(tpl.title, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                        Text(tpl.body, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Use Template",
                                onClick = {
                                    viewModel.applyBroadcastTemplate(tpl)
                                    activeTab = NotificationsTab.COMPOSE
                                },
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Delete",
                                onClick = { viewModel.deleteTemplate(tpl.id) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            NotificationsTab.HISTORY -> {
                if (uiState.backendScheduledBroadcasts.isNotEmpty()) {
                    item(key = "cloud_auto_scheduled_header") {
                        DevSectionTitle("Cloud Auto-Scheduled Broadcasts (${uiState.backendScheduledBroadcasts.size})")
                    }
                    items(uiState.backendScheduledBroadcasts, key = { "auto-${it.id}" }) { sched ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sched.title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText, modifier = Modifier.weight(1f))
                                DevToneBadge(label = sched.status, tone = if (sched.status == "dispatched") "ok" else if (sched.status == "cancelled") "paused" else "warning")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(sched.body, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            DevKeyValueRow(label = "Audience", value = sched.audience.label)
                            if (sched.status == "scheduled") {
                                Spacer(Modifier.height(6.dp))
                                PrismaticButton(
                                    text = "Cancel Scheduled",
                                    onClick = { viewModel.cancelBackendScheduledBroadcast(sched.id) },
                                    variant = PrismaticButtonVariant.Ghost,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item(key = "single_device_test") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Elevated) {
                        DevSectionTitle("Single-Device FCM Test")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isBackendEnrolled) {
                                "Dispatches a single-device test to one QA token via keyless WIF workflow. Tokens are never saved on disk."
                            } else {
                                "Targets one QA device registration token directly via FCM HTTP v1. Ephemeral OAuth bearer token is never saved."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testDeviceToken,
                            onValueChange = { testDeviceToken = it },
                            label = { Text("Device FCM Token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        )
                        if (!isBackendEnrolled) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testEphemeralBearer,
                                onValueChange = { testEphemeralBearer = it },
                                label = { Text("Ephemeral Messaging Bearer Token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Dry-Run Device",
                                onClick = {
                                    viewModel.sendSingleDeviceTest(testDeviceToken, testEphemeralBearer, validateOnly = true)
                                    testEphemeralBearer = ""
                                },
                                enabled = !uiState.isDispatching,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Send Device",
                                onClick = {
                                    viewModel.sendSingleDeviceTest(testDeviceToken, testEphemeralBearer, validateOnly = false)
                                    testEphemeralBearer = ""
                                },
                                enabled = !uiState.isDispatching,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                uiState.latestCustomRun?.let { run ->
                    item(key = "latest_relay_run") {
                        val jobs = uiState.inspectedWorkflowJobs[run.id].orEmpty()
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Latest Relay #${run.id}", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                    Text("${run.status}, ${formatIsoShort(run.createdAt)}", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                                }
                                DevToneBadge(label = run.conclusion, tone = run.conclusion)
                            }
                            if (jobs.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                jobs.forEach { job ->
                                    DevKeyValueRow(
                                        label = job.name,
                                        value = if (job.failedSteps.isNotEmpty()) "Failed: ${job.failedSteps.joinToString()}" else job.conclusion
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Inspect Jobs",
                                    onClick = { viewModel.inspectWorkflowJobs(uiState.vault.consoleRepo, run.id) },
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                PrismaticButton(
                                    text = "Open Run",
                                    onClick = { context.openHttpsUrl(run.htmlUrl) },
                                    variant = PrismaticButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item(key = "hist_filter") {
                    val bFilters = listOf(HistoryFilter.ALL, HistoryFilter.CUSTOM, HistoryFilter.RELEASES)
                    DevChoiceChipRow(
                        items = bFilters,
                        selected = if (uiState.historyFilter == HistoryFilter.AUDIT) HistoryFilter.ALL else uiState.historyFilter,
                        labelOf = { it.label },
                        onSelect = viewModel::selectHistoryFilter
                    )
                }

                val entries = when (uiState.historyFilter) {
                    HistoryFilter.CUSTOM -> uiState.vault.customHistory
                    HistoryFilter.RELEASES -> uiState.dashboard.releaseBroadcasts
                    else -> uiState.combinedBroadcastHistory
                }
                if (entries.isEmpty()) {
                    item(key = "hist_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No broadcast history recorded.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(entries, key = { it.id }) { item ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DevToneBadge(label = item.kind.uppercase(), tone = "neutral")
                                    DevToneBadge(label = item.audience, tone = "neutral")
                                }
                                DevToneBadge(label = item.statusLabel, tone = item.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Text(item.body, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            Spacer(Modifier.height(6.dp))
                            DevKeyValueRow(label = "ID", value = item.broadcastId)
                            DevKeyValueRow(label = "Route / Topics", value = "${item.route} (${item.topics.joinToString(", ")})")
                            DevKeyValueRow(label = "Time", value = formatIsoShort(item.timestampIso))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Retry Copy",
                                    onClick = {
                                        BroadcastCategory.fromApiValue(item.category)?.let(viewModel::updateComposerCategory)
                                        BroadcastAudience.fromApiValue(item.audience)?.let(viewModel::updateComposerAudience)
                                        BroadcastDestination.fromApiValue(item.route)?.let(viewModel::updateComposerRoute)
                                        viewModel.updateComposerTitle(item.title)
                                        viewModel.updateComposerBody(item.body)
                                        viewModel.regenerateBroadcastId()
                                        activeTab = NotificationsTab.COMPOSE
                                    },
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                item.workflowUrl?.let { url ->
                                    PrismaticButton(
                                        text = "Open Workflow",
                                        onClick = { context.openHttpsUrl(url) },
                                        variant = PrismaticButtonVariant.Ghost,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
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

@Composable
private fun SaveTemplateDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Broadcast Template") },
        text = {
            PrismaticTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "Template Name",
                placeholder = "Hotfix Notice"
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().length >= 2,
                onClick = { onSave(name) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ScheduleDelayDialog(onDismiss: () -> Unit, onSchedule: (Int) -> Unit) {
    val presets = listOf(15 to "15m", 60 to "1h", 180 to "3h", 1440 to "24h")
    var selectedMins by remember { mutableStateOf(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WorkManager posts a local alert at the scheduled time. Live broadcasts still require manual confirmation.",
                    style = MaterialTheme.typography.bodySmall
                )
                DevChoiceChipRow(
                    items = presets,
                    selected = presets.first { it.first == selectedMins },
                    labelOf = { it.second },
                    onSelect = { selectedMins = it.first }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSchedule(selectedMins) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CloudAutoScheduleDialog(
    audience: BroadcastAudience,
    title: String,
    onDismiss: () -> Unit,
    onSchedule: (minutes: Int, phrase: String, reason: String) -> Unit
) {
    val presets = listOf(15 to "15m", 60 to "1h", 180 to "3h", 1440 to "24h")
    var selectedMins by remember { mutableStateOf(60) }
    var phraseInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    val ready = phraseInput.trim() == audience.confirmationPhrase &&
        DevCenterValidators.cleanInputText(reasonInput, 240).length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Cloud Broadcast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cloudflare Worker cron dispatches \"$title\" to ${audience.label} automatically at the scheduled time.",
                    style = MaterialTheme.typography.bodySmall
                )
                DevChoiceChipRow(
                    items = presets,
                    selected = presets.first { it.first == selectedMins },
                    labelOf = { it.second },
                    onSelect = { selectedMins = it.first }
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
                    placeholder = "Timed release announcement"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onSchedule(selectedMins, phraseInput, reasonInput) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Schedule Auto-Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}
