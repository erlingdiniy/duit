package com.diniy.duit.feature.notifications

import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import com.diniy.duit.core.ui.components.candyPressable
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
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
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

            // A. Notifications: master switch, permission state, centre, Android settings, lock-screen privacy
            item(key = "section_notifications") {
                NotifCard {
                    NotifSectionHeader(icon = Icons.Rounded.NotificationsActive, accent = colors.aqua, title = "Notifications") {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (uiState.hasOsPermission) colors.incomeContainer else colors.warningContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.hasOsPermission) "Allowed" else "Not allowed",
                            style = typography.caption,
                            color = if (uiState.hasOsPermission) colors.onIncomeContainer else colors.onWarningContainer
                        )
                    }
                    }
                        NotifSwitchRow(
                            title = "Enable notifications",
                            subtitle = "Reminders are scheduled on this phone",
                            checked = uiState.settings.masterEnabled,
                            onCheckedChange = { enabled ->
                                onSetMasterEnabled(enabled)
                                if (enabled && !uiState.hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionRequester.requestOrOpenSettings()
                                }
                            }
                        )
                        if (!uiState.hasOsPermission) {
                            Text(
                                text = if (permissionRequester.isPromptBlockedBySystem) {
                                    "Android is blocking duit.'s notifications. Allow them in Android settings to get reminders."
                                } else {
                                    "duit. needs your permission to show reminders."
                                },
                                style = typography.caption,
                                color = colors.warning,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionRequester.isPromptBlockedBySystem) {
                                PrismaticButton(
                                    text = "Allow notifications",
                                    onClick = permissionRequester.requestOrOpenSettings,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                            }
                        }
                        if (onOpenNotificationCentre != null) {
                            NotifNavRow("Notification Centre", "Recent reminders and alerts", onOpenNotificationCentre)
                        }
                        NotifNavRow("Android notification settings", "Sound, pop-ups and channels", permissionRequester.openNotificationSettings)
                        NotifSwitchRow(
                            title = "Hide amounts on lock screen",
                            subtitle = if (uiState.isAppLockOrPrivacyActive) {
                                "On automatically while App Lock or Privacy Mode is active"
                            } else {
                                "Show reminders without Ringgit amounts"
                            },
                            checked = uiState.settings.hideLockScreenAmounts || uiState.isAppLockOrPrivacyActive,
                            onCheckedChange = onSetHideLockScreenAmounts
                        )
                }
            }

            // B. Reminder schedule: time, offsets, overdue, and what to be reminded about
            item(key = "section_schedule") {
                NotifCard {
                    NotifSectionHeader(icon = Icons.Rounded.Schedule, accent = colors.coral, title = "Reminder schedule")
                    NotifNavRow("Reminder time", uiState.settings.formattedReminderTime, { showTimePicker = true })
                    Text("Remind me before due date", style = typography.caption, color = colors.secondaryText, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderOffset.entries.forEach { offset ->
                            val selected = offset in uiState.settings.enabledOffsets
                            com.diniy.duit.core.ui.components.SpatialGlassPill(
                                label = offset.label,
                                selected = selected,
                                onClick = { onSetOffsetEnabled(offset, !selected) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    NotifSwitchRow(
                        title = "Overdue reminders",
                        subtitle = "When a bill passes its due date unpaid",
                        checked = uiState.settings.overdueEnabled,
                        onCheckedChange = onSetOverdueEnabled
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.divider)
                    Text("Remind me about", style = typography.caption, color = colors.secondaryText, modifier = Modifier.padding(bottom = 2.dp))
                    NotificationCategory.forThisEdition.forEach { category ->
                        NotifSwitchRow(
                            title = category.displayName,
                            subtitle = category.description,
                            checked = category in uiState.settings.enabledCategories,
                            onCheckedChange = { enabled -> onSetCategoryEnabled(category, enabled) }
                        )
                        if (category == NotificationCategory.BUDGETS && category in uiState.settings.enabledCategories) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BudgetAlertThreshold.entries.forEach { threshold ->
                                    val selected = threshold in uiState.settings.enabledBudgetThresholds
                                    com.diniy.duit.core.ui.components.SpatialGlassPill(
                                        label = "${threshold.label} alert",
                                        selected = selected,
                                        onClick = { onSetBudgetThresholdEnabled(threshold, !selected) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // C. Test notification with honest feedback
            item(key = "section_test") {
                NotifCard {
                    NotifSectionHeader(
                        icon = if (uiState.hasOsPermission) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsPaused,
                        accent = colors.sunny,
                        title = "Test notification"
                    )
                    Text(
                        "Sends one notification now. Nothing is saved.",
                        style = typography.caption,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PrismaticButton(
                        text = "Send test notification",
                        onClick = onSendTestNotification,
                        enabled = !uiState.isTestNotificationCoolingDown,
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!uiState.hasOsPermission || uiState.testNotificationStatus == TestNotificationStatus.PERMISSION_DENIED) {
                        PrismaticButton(
                            text = "Open notification settings",
                            onClick = permissionRequester.openNotificationSettings,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    val feedbackMessage = when (uiState.testNotificationStatus) {
                        TestNotificationStatus.SENT -> "Sent. Check your notification shade."
                        TestNotificationStatus.THROTTLED -> "Just sent one. Try again in a moment."
                        TestNotificationStatus.PERMISSION_DENIED -> "Not sent: notifications aren't allowed for duit."
                        TestNotificationStatus.FAILED -> "Not sent: this phone couldn't deliver it."
                        null -> null
                    }
                    if (feedbackMessage != null) {
                        Text(
                            text = feedbackMessage,
                            style = typography.caption,
                            color = when (uiState.testNotificationStatus) {
                                TestNotificationStatus.PERMISSION_DENIED,
                                TestNotificationStatus.FAILED -> colors.error
                                else -> colors.income
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
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
private fun NotifCard(content: @Composable ColumnScope.() -> Unit) {
    com.diniy.duit.core.ui.components.SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

@Composable
private fun NotifSectionHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    trailing: @Composable () -> Unit = {}
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .rotate(if (isLiquidGlass) 0f else -6f)
                .clip(RoundedCornerShape(if (isLiquidGlass) 11.dp else 13.dp))
                .background(if (isLiquidGlass) accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f) else accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isLiquidGlass) colors.readableOn(accent) else colors.onCoral,
                modifier = Modifier.size(18.dp).rotate(if (isLiquidGlass) 0f else 6f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = com.diniy.duit.core.ui.theme.SpatialTheme.typography.cardTitle,
            color = colors.primaryText,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        trailing()
    }
}

/** Whole-row tappable navigation with a chevron, so it reads clearly as interactive. */
@Composable
private fun NotifNavRow(title: String, value: String, onClick: () -> Unit) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .candyPressable(onClick = onClick, onClickLabel = title)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = typography.transactionDescription, color = colors.primaryText)
            Text(value, style = typography.caption, color = colors.secondaryText)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.secondaryText)
    }
}

@Composable
private fun NotifSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = typography.transactionDescription, color = colors.primaryText)
            Text(subtitle, style = typography.caption, color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = com.diniy.duit.core.ui.components.candySwitchColors(personal = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = colors.coral,
                checkedThumbColor = Color.White,
                checkedBorderColor = colors.sticker,
                uncheckedTrackColor = colors.baseSurface,
                uncheckedThumbColor = colors.secondaryText,
                uncheckedBorderColor = Color.Transparent
            ))
        )
    }
}
