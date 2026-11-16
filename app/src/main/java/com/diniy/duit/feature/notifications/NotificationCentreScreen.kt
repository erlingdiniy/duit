package com.diniy.duit.feature.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.R
import com.diniy.duit.domain.calculator.ReminderCandidate
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.notifications.NotificationHistoryItem

@Composable
fun NotificationCentreScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onClearHistory: () -> Unit,
    onNavigateToTarget: (NotificationDeepLink) -> Unit,
    onPermissionUpdated: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val privacySettings = LocalPrivacySettings.current

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text(
                                text = "Notification Centre",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = if (uiState.unreadCount > 0) "${uiState.unreadCount} unread reminder(s)" else "All caught up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Notification settings")
                    }
                }
            }

            if (!uiState.hasOsPermission && uiState.settings.masterEnabled) {
                item(key = "permission_banner") {
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = GlassCardTier.Elevated,
                        accentTint = glass.peachAccent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = glass.peachAccent)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System notifications are off", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (permissionRequester.isPromptBlockedBySystem) {
                                        "Android blocked the permission prompt. Open Notification Settings to allow notifications for duit."
                                    } else {
                                        "Allow notification permission so bills, instalments & payday alerts appear on schedule."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionRequester.isPromptBlockedBySystem) {
                                PrismaticButton(
                                    text = "Allow Notifications",
                                    onClick = permissionRequester.requestOrOpenSettings,
                                    variant = PrismaticButtonVariant.Primary
                                )
                            }
                            PrismaticButton(
                                text = if (permissionRequester.isPromptBlockedBySystem) "Open Notification Settings" else "Notification Settings",
                                onClick = permissionRequester.openNotificationSettings,
                                variant = if (permissionRequester.isPromptBlockedBySystem) {
                                    PrismaticButtonVariant.Primary
                                } else {
                                    PrismaticButtonVariant.Secondary
                                }
                            )
                        }
                    }
                }
            }

            // 1. Overdue items
            if (uiState.overdueItems.isNotEmpty()) {
                item(key = "overdue_heading") {
                    Text(
                        text = "Overdue (${uiState.overdueItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                items(uiState.overdueItems, key = { "ov_${it.dedupKey}" }) { item ->
                    ReminderCandidateCard(
                        candidate = item,
                        maskAmounts = privacySettings.isAnyMaskActive,
                        onClick = { onNavigateToTarget(item.deepLink) }
                    )
                }
            }

            // 2. Upcoming reminders
            item(key = "upcoming_heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming Reminders (${uiState.upcomingReminders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!uiState.settings.masterEnabled) {
                        PrismaticBadge(
                            label = "Paused",
                            containerColor = glass.glassSurfaceMuted,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.upcomingReminders.isEmpty()) {
                item(key = "upcoming_empty") {
                    PrismaticEmptyStateCard(
                        iconRes = R.drawable.ic_solar_planning,
                        title = "No upcoming reminders",
                        message = if (uiState.settings.masterEnabled) {
                            "Reminders appear automatically from unpaid commitments, PayLater instalments, scheduled debts, goals, cycles & commissions."
                        } else {
                            "Notifications are currently turned off in Notification Settings."
                        },
                        actionLabel = "Notification Settings",
                        onActionClick = onOpenSettings
                    )
                }
            } else {
                items(uiState.upcomingReminders.take(15), key = { "up_${it.dedupKey}" }) { item ->
                    ReminderCandidateCard(
                        candidate = item,
                        maskAmounts = privacySettings.isAnyMaskActive,
                        onClick = { onNavigateToTarget(item.deepLink) }
                    )
                }
            }

            // 3. Delivered notification history
            item(key = "history_heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notification History (${uiState.history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row {
                        if (uiState.unreadCount > 0) {
                            TextButton(onClick = onMarkAllAsRead) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Read all")
                            }
                        }
                        if (uiState.history.isNotEmpty()) {
                            TextButton(onClick = onClearHistory) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            if (uiState.history.isEmpty()) {
                item(key = "history_empty") {
                    PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Muted) {
                        Text(
                            text = "Delivered reminder logs appear here without duplicating real financial transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.history, key = { "hist_${it.id}" }) { entry ->
                    NotificationHistoryRowCard(
                        item = entry,
                        maskAmounts = privacySettings.isAnyMaskActive,
                        onClick = {
                            if (!entry.isRead) onMarkAsRead(entry.id)
                            onNavigateToTarget(entry.deepLink)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCandidateCard(
    candidate: ReminderCandidate,
    maskAmounts: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val badgeBg = if (candidate.isOverdue) MaterialTheme.colorScheme.errorContainer else glass.aquaContainer
    val badgeFg = if (candidate.isOverdue) MaterialTheme.colorScheme.onErrorContainer else glass.onAquaContainer

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard,
        accentTint = if (candidate.isOverdue) MaterialTheme.colorScheme.error else glass.aquaAccent,
        onClick = onClick,
        onClickLabel = "Open ${candidate.itemTitle}",
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.resolveTitle(maskAmounts),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = candidate.resolveBody(maskAmounts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${candidate.category.displayName} · Scheduled ${DateTimeFormatters.formatEpochDay(candidate.reminderEpochDay)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                candidate.amountSen?.let { amt ->
                    Text(
                        text = maskedBalance(amt),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (candidate.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                }
                PrismaticBadge(
                    label = when (candidate.offsetDaysBefore) {
                        null -> "Overdue"
                        0 -> "Due today"
                        else -> "${candidate.offsetDaysBefore}d before"
                    },
                    containerColor = badgeBg,
                    contentColor = badgeFg
                )
            }
        }
    }
}

@Composable
private fun NotificationHistoryRowCard(
    item: NotificationHistoryItem,
    maskAmounts: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalPrismaticGlass.current

    PrismaticGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = if (item.isRead) GlassCardTier.Muted else GlassCardTier.Standard,
        accentTint = if (!item.isRead) glass.skyBlueAccent else null,
        onClick = onClick,
        onClickLabel = "Open ${item.title}",
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(glass.aquaAccent)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                val safeTitle = if (maskAmounts) PrivacyMask.MASKED_MYR else item.title
                Text(
                    text = if (maskAmounts && item.title.contains("RM")) "duit. reminder" else item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (maskAmounts) "Amounts hidden by Privacy settings · Open to review" else item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.category.displayName} · ${DateTimeFormatters.formatTimestamp(item.deliveredAtEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.amountSen != null) {
                Text(
                    text = maskedBalance(item.amountSen),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Refreshes OS notification permission state when returning from the system prompt or Settings.
 */
@Composable
internal fun ObservePermissionRefreshOnResume(onResumeAfterPause: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCallback by rememberUpdatedState(onResumeAfterPause)
    var sawPause by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> sawPause = true
                Lifecycle.Event.ON_RESUME -> if (sawPause) {
                    sawPause = false
                    currentCallback()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

internal class NotificationPermissionRequester(
    val isPromptBlockedBySystem: Boolean,
    val requestOrOpenSettings: () -> Unit,
    val openNotificationSettings: () -> Unit
)

@Composable
internal fun rememberNotificationPermissionRequester(
    hasOsPermission: Boolean,
    onPermissionUpdated: () -> Unit
): NotificationPermissionRequester {
    val context = LocalContext.current
    val currentOnUpdated by rememberUpdatedState(onPermissionUpdated)
    var promptBlockedBySystem by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasOsPermission) {
        if (hasOsPermission) {
            promptBlockedBySystem = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        currentOnUpdated()
        if (granted) {
            promptBlockedBySystem = false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.findActivity()
            val rationaleAfter = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } ?: false
            // Either USER_FIXED suppressed the OS prompt or user denied twice -> direct Settings fallback.
            if (!rationaleAfter) {
                promptBlockedBySystem = true
            }
        }
    }

    return NotificationPermissionRequester(
        isPromptBlockedBySystem = promptBlockedBySystem && !hasOsPermission,
        requestOrOpenSettings = {
            if (hasOsPermission) {
                currentOnUpdated()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || promptBlockedBySystem) {
                openAppNotificationSettings(context)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openNotificationSettings = { openAppNotificationSettings(context) }
    )
}

internal fun openAppNotificationSettings(context: Context) {
    val notifSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching {
        context.startActivity(notifSettingsIntent)
    }.onFailure {
        context.startActivity(fallbackIntent)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
