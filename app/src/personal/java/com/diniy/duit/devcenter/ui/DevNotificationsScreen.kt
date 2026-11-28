package com.diniy.duit.devcenter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
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
    SCHEDULED("Scheduled"),
    LIBRARY("Library"),
    HISTORY("History")
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
    var showSingleDeviceTest by rememberSaveable { mutableStateOf(false) }
    var showEmergencySwitches by rememberSaveable { mutableStateOf(false) }
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
        contentPadding = devListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "notif_tabs") {
            val scheduledCount = uiState.backendScheduledBroadcasts.count { it.status == "scheduled" } +
                uiState.vault.scheduledBroadcasts.count { it.status == "scheduled" }
            DevSegmentedBar(
                items = NotificationsTab.entries,
                selected = activeTab,
                labelOf = { it.label },
                badgeCountOf = { tab ->
                    if (tab == NotificationsTab.SCHEDULED) scheduledCount else 0
                },
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    DevSectionTitle("Connection Required")
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Enroll or add a PAT in Settings to broadcast.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                                PrismaticButton(
                                    text = "Settings",
                                    onClick = onOpenSettings,
                                    variant = PrismaticButtonVariant.Primary
                                )
                            }
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
                                true -> "Live Ready"
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
                        var showDeliveryOptions by rememberSaveable { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .candyPressable(
                                    onClick = { showDeliveryOptions = !showDeliveryOptions },
                                    onClickLabel = "Toggle delivery options"
                                )
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Delivery Options",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.secondaryText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DevToneBadge(label = composer.route.label, tone = "neutral")
                                Icon(
                                    imageVector = if (showDeliveryOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.secondaryText
                                )
                            }
                        }
                        AnimatedVisibility(visible = showDeliveryOptions) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tap Destination",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.secondaryText
                                )
                                Spacer(Modifier.height(4.dp))
                                DevChoiceChipRow(
                                    items = BroadcastDestination.entries,
                                    selected = composer.route,
                                    labelOf = { it.label },
                                    onSelect = viewModel::updateComposerRoute
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
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var showComposeMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            PrismaticButton(
                                text = "Options",
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
                        DevSectionTitle("Notification Preview")
                        Spacer(Modifier.height(8.dp))
                        DevSegmentedBar(
                            items = PreviewSurfaceMode.entries,
                            selected = previewMode,
                            labelOf = { it.label },
                            onSelect = { previewMode = it }
                        )
                        Spacer(Modifier.height(10.dp))
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
                                            text = "duit., Lockscreen",
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
                                        DevToneBadge(label = "Opens ${composer.route.label}", tone = "neutral")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        DevKeyValueRow(
                            label = "Channel",
                            value = ANDROID_BROADCAST_CHANNEL_ID,
                            monospaced = true
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .candyPressable(
                                    onClick = { showEmergencySwitches = !showEmergencySwitches },
                                    onClickLabel = "Toggle emergency switches"
                                )
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle("Emergency Switches", modifier = Modifier.weight(1f).padding(end = 8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DevToneBadge(
                                    label = if (customKillEnabled == true && releaseKillEnabled == true) "Armed" else "Check",
                                    tone = if (customKillEnabled == true && releaseKillEnabled == true) "enabled" else "warning"
                                )
                                Icon(
                                    imageVector = if (showEmergencySwitches) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.secondaryText
                                )
                            }
                        }
                        AnimatedVisibility(visible = showEmergencySwitches) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                NotifyKillSwitchRow(
                                    title = "Custom Broadcasts",
                                    variableName = KillSwitchTarget.CUSTOM_BROADCAST.variableName,
                                    enabled = customKillEnabled,
                                    canMutate = hasPat && customKillEnabled != null && !uiState.isUpdatingKillSwitch,
                                    onClick = {
                                        viewModel.requestKillSwitchToggle(
                                            KillSwitchTarget.CUSTOM_BROADCAST,
                                            customKillEnabled != true
                                        )
                                    }
                                )
                                Spacer(Modifier.height(10.dp))
                                NotifyKillSwitchRow(
                                    title = "Release Automation",
                                    variableName = KillSwitchTarget.RELEASE_AUTOMATION.variableName,
                                    enabled = releaseKillEnabled,
                                    canMutate = hasPat && releaseKillEnabled != null && !uiState.isUpdatingKillSwitch,
                                    onClick = {
                                        viewModel.requestKillSwitchToggle(
                                            KillSwitchTarget.RELEASE_AUTOMATION,
                                            releaseKillEnabled != true
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            NotificationsTab.SCHEDULED -> {
                item(key = "sched_actions") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Schedule From Composer")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Local Reminder",
                                onClick = { showScheduleDialog = true },
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isBackendEnrolled) {
                                PrismaticButton(
                                    text = "Cloud Cron",
                                    onClick = { showCloudAutoScheduleDialog = true },
                                    variant = PrismaticButtonVariant.Primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (uiState.backendScheduledBroadcasts.isNotEmpty()) {
                    item(key = "cloud_auto_scheduled_header") {
                        DevSectionTitle("Cloud Auto-Scheduled (${uiState.backendScheduledBroadcasts.size})")
                    }
                    items(uiState.backendScheduledBroadcasts, key = { "auto-${it.id}" }) { sched ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sched.title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText, modifier = Modifier.weight(1f))
                                DevToneBadge(
                                    label = sched.status,
                                    tone = when (sched.status) {
                                        "dispatched" -> "ok"
                                        "cancelled" -> "paused"
                                        else -> "warning"
                                    }
                                )
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

                item(key = "lib_scheduled_header") {
                    DevSectionTitle("Local Reminders (${uiState.vault.scheduledBroadcasts.size})")
                }
                if (uiState.vault.scheduledBroadcasts.isEmpty() && uiState.backendScheduledBroadcasts.isEmpty()) {
                    item(key = "sched_empty") {
                        DevEmptyStateCard(
                            icon = Icons.Rounded.Schedule,
                            title = "No Scheduled Broadcasts",
                            subtitle = "Local reminders and Worker cron sends appear here."
                        )
                    }
                } else if (uiState.vault.scheduledBroadcasts.isNotEmpty()) {
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
            }

            NotificationsTab.LIBRARY -> {
                val noDraftsAndTemplates = uiState.vault.broadcastDrafts.isEmpty() &&
                    uiState.vault.broadcastTemplates.isEmpty()

                if (noDraftsAndTemplates) {
                    item(key = "library_empty") {
                        DevEmptyStateCard(
                            icon = Icons.Rounded.BookmarkBorder,
                            title = "Library Empty",
                            subtitle = "Save drafts or reusable broadcast templates from Compose."
                        )
                    }
                } else {
                    item(key = "lib_drafts_header") {
                        DevSectionTitle("Saved Drafts (${uiState.vault.broadcastDrafts.size})")
                    }
                    if (uiState.vault.broadcastDrafts.isEmpty()) {
                        item(key = "lib_drafts_empty") {
                            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                                Text("No saved drafts.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            }
                        }
                    } else {
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
                    }

                    item(key = "lib_templates_header") {
                        DevSectionTitle("Templates (${uiState.vault.broadcastTemplates.size})")
                    }
                    if (uiState.vault.broadcastTemplates.isEmpty()) {
                        item(key = "lib_tpl_empty") {
                            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                                Text("No saved templates.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                            }
                        }
                    } else {
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
                }
            }

            NotificationsTab.HISTORY -> {
                item(key = "single_device_test") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Elevated) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .candyPressable(
                                    onClick = { showSingleDeviceTest = !showSingleDeviceTest },
                                    onClickLabel = "Toggle single-device FCM test"
                                )
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                DevSectionTitle("Single-Device Test")
                                Text(
                                    text = "Send QA push to one device token",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Icon(
                                imageVector = if (showSingleDeviceTest) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = colors.secondaryText
                            )
                        }
                        AnimatedVisibility(visible = showSingleDeviceTest) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (isBackendEnrolled) {
                                        "Dispatches a single-device test via keyless WIF workflow. Tokens are never saved on disk."
                                    } else {
                                        "Targets one QA token directly via FCM HTTP v1. Ephemeral bearer tokens are never saved."
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
                    DevSegmentedBar(
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
                        DevEmptyStateCard(
                            icon = Icons.Rounded.History,
                            title = "No Broadcast History",
                            subtitle = "Dry-run and live notification dispatches appear here."
                        )
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
                            DevKeyValueRow(label = "Broadcast ID", value = item.broadcastId, monospaced = true)
                            DevKeyValueRow(label = "Route", value = "${item.route} (${item.topics.joinToString(", ")})")
                            DevKeyValueRow(label = "Sent", value = formatIsoShort(item.timestampIso))
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
private fun NotifyKillSwitchRow(
    title: String,
    variableName: String,
    enabled: Boolean?,
    canMutate: Boolean,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DevToneBadge(
                    label = when (enabled) {
                        true -> "Enabled"
                        false -> "Paused"
                        null -> "Unknown"
                    },
                    tone = when (enabled) {
                        true -> "enabled"
                        false -> "paused"
                        null -> "warning"
                    }
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = variableName,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PrismaticButton(
            text = if (enabled == true) "Pause" else "Enable",
            onClick = onClick,
            enabled = canMutate,
            variant = PrismaticButtonVariant.Secondary
        )
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
