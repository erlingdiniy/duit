package com.diniy.duit.feature.notifications

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.ReminderOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onSetMasterEnabled: (Boolean) -> Unit,
    onSetCategoryEnabled: (NotificationCategory, Boolean) -> Unit,
    onSetOffsetEnabled: (ReminderOffset, Boolean) -> Unit,
    onSetBudgetThresholdEnabled: (BudgetAlertThreshold, Boolean) -> Unit = { _, _ -> },
    onSetOverdueEnabled: (Boolean) -> Unit,
    onSetReminderTime: (Int, Int) -> Unit,
    onSetHideLockScreenAmounts: (Boolean) -> Unit,
    onPermissionUpdated: () -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenNotificationCentre: (() -> Unit)? = null
) {
    val glass = LocalPrismaticGlass.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val permissionRequester = rememberNotificationPermissionRequester(
        hasOsPermission = uiState.hasOsPermission,
        onPermissionUpdated = onPermissionUpdated
    )
    ObservePermissionRefreshOnResume(onResumeAfterPause = onPermissionUpdated)

    PrismaticBackground {
        val screenPadding = com.diniy.duit.core.ui.components.rememberSpatialScreenPadding(hasFloatingBottomBar = false)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Offline reminders for bills, instalments, savings & payday",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (onOpenNotificationCentre != null) {
                item(key = "centre_link") {
                    com.diniy.duit.core.ui.components.SpatialCompactListRow(
                        title = "Notification Centre",
                        subtitle = "Recent reminders and alerts",
                        trailingPrimaryText = "Open",
                        trailingPrimaryColor = com.diniy.duit.core.ui.theme.SpatialTheme.colors.primaryAction,
                        onClick = onOpenNotificationCentre
                    )
                }
            }

            // Permission & Master card
            item(key = "master_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Elevated,
                    accentTint = glass.aquaAccent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = glass.aquaAccent)
                            Text("Smart Reminders", style = MaterialTheme.typography.titleMedium)
                        }
                        PrismaticBadge(
                            label = if (uiState.hasOsPermission) "Permission Granted" else "Permission Needed",
                            containerColor = if (uiState.hasOsPermission) glass.aquaContainer else glass.peachContainer,
                            contentColor = if (uiState.hasOsPermission) glass.onAquaContainer else glass.onPeachContainer
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    NotifSwitchRow(
                        title = "Enable Notifications",
                        subtitle = "Schedule local WorkManager reminders with zero cloud tracking.",
                        checked = uiState.settings.masterEnabled,
                        onCheckedChange = { enabled ->
                            onSetMasterEnabled(enabled)
                            if (enabled && !uiState.hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionRequester.requestOrOpenSettings()
                            }
                        }
                    )

                    if (!uiState.hasOsPermission) {
                        Spacer(Modifier.height(10.dp))
                        if (permissionRequester.isPromptBlockedBySystem) {
                            Text(
                                text = "Android suppressed the permission prompt. Open Notification Settings to allow notifications for duit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionRequester.isPromptBlockedBySystem) {
                                PrismaticButton(
                                    text = "Grant Permission",
                                    onClick = permissionRequester.requestOrOpenSettings,
                                    variant = PrismaticButtonVariant.Secondary
                                )
                            }
                            PrismaticButton(
                                text = if (permissionRequester.isPromptBlockedBySystem) "Open Notification Settings" else "System Settings",
                                onClick = permissionRequester.openNotificationSettings,
                                variant = if (permissionRequester.isPromptBlockedBySystem) {
                                    PrismaticButtonVariant.Primary
                                } else {
                                    PrismaticButtonVariant.Ghost
                                }
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        PrismaticButton(
                            text = "Android Notification Settings",
                            onClick = permissionRequester.openNotificationSettings,
                            variant = PrismaticButtonVariant.Ghost
                        )
                    }
                }
            }

            // Verification & Test Notification card
            item(key = "test_notification_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.aquaAccent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.hasOsPermission) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsPaused
                            },
                            contentDescription = null,
                            tint = glass.aquaAccent
                        )
                        Text("Verify Delivery", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Send one immediate local notification without creating any transaction, bill, or scheduled reminder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Send Test Notification",
                            onClick = onSendTestNotification,
                            enabled = !uiState.isTestNotificationCoolingDown,
                            variant = PrismaticButtonVariant.Secondary
                        )
                        if (!uiState.hasOsPermission || uiState.testNotificationStatus == TestNotificationStatus.PERMISSION_DENIED) {
                            PrismaticButton(
                                text = "Open Notification Settings",
                                onClick = permissionRequester.openNotificationSettings,
                                variant = PrismaticButtonVariant.Primary
                            )
                        }
                    }

                    val feedbackMessage = when (uiState.testNotificationStatus) {
                        TestNotificationStatus.SENT ->
                            "Test notification sent (\"duit. Test Notification\"). Check your notification shade."
                        TestNotificationStatus.THROTTLED ->
                            "A test notification was just sent — please wait a moment before sending another."
                        TestNotificationStatus.PERMISSION_DENIED ->
                            "Notification permission is currently disabled. Open Notification Settings to allow alerts."
                        TestNotificationStatus.FAILED ->
                            "Unable to deliver test notification on this device."
                        null -> null
                    }
                    if (feedbackMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.testNotificationStatus) {
                                TestNotificationStatus.PERMISSION_DENIED,
                                TestNotificationStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> glass.aquaAccent
                            }
                        )
                    }
                }
            }

            // Schedule & offsets card
            item(key = "schedule_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.lavenderAccent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = glass.lavenderAccent)
                        Text("Reminder Schedule", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Preferred Reminder Time", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = uiState.settings.formattedReminderTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PrismaticButton(
                            text = "Change Time",
                            onClick = { showTimePicker = true },
                            variant = PrismaticButtonVariant.Secondary
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = glass.glassBorderSubtle)

                    Text("Reminder Offsets", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReminderOffset.entries.forEach { offset ->
                            val selected = offset in uiState.settings.enabledOffsets
                            FilterChip(
                                selected = selected,
                                onClick = { onSetOffsetEnabled(offset, !selected) },
                                label = { Text(offset.label) },
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = glass.glassBorderSubtle)

                    NotifSwitchRow(
                        title = "Overdue Reminders",
                        subtitle = "Send a reminder when unpaid obligations pass their scheduled due date.",
                        checked = uiState.settings.overdueEnabled,
                        onCheckedChange = onSetOverdueEnabled
                    )
                }
            }

            // Category toggles card
            item(key = "categories_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.skyBlueAccent
                ) {
                    Text("Notification Categories", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    NotificationCategory.entries.forEachIndexed { idx, category ->
                        if (idx > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = glass.glassBorderSubtle)
                        }
                        NotifSwitchRow(
                            title = category.displayName,
                            subtitle = category.description,
                            checked = category in uiState.settings.enabledCategories,
                            onCheckedChange = { enabled -> onSetCategoryEnabled(category, enabled) }
                        )
                        if (category == NotificationCategory.BUDGETS && category in uiState.settings.enabledCategories) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BudgetAlertThreshold.entries.forEach { threshold ->
                                    val selected = threshold in uiState.settings.enabledBudgetThresholds
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSetBudgetThresholdEnabled(threshold, !selected) },
                                        label = { Text("${threshold.label} alert") },
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Privacy control card
            item(key = "privacy_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.peachAccent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = glass.peachAccent)
                        Text("Notification Privacy", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    NotifSwitchRow(
                        title = "Hide Amounts on Lock Screen & Previews",
                        subtitle = if (uiState.isAppLockOrPrivacyActive) {
                            "App Lock / Privacy Mode is active — monetary amounts are automatically masked in notifications."
                        } else {
                            "Use generic reminder text without Ringgit figures in system notifications."
                        },
                        checked = uiState.settings.hideLockScreenAmounts || uiState.isAppLockOrPrivacyActive,
                        onCheckedChange = onSetHideLockScreenAmounts
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = uiState.settings.reminderHour,
            initialMinute = uiState.settings.reminderMinute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reminder Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetReminderTime(timeState.hour, timeState.minute)
                        showTimePicker = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotifSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
