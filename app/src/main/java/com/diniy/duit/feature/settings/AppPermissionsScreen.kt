package com.diniy.duit.feature.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberSpatialScreenPadding
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.feature.notifications.ObservePermissionRefreshOnResume
import com.diniy.duit.feature.notifications.openAppNotificationSettings
import com.diniy.duit.feature.notifications.rememberNotificationPermissionRequester
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.BiometricAuthGateway

/**
 * App Permissions destination in Settings.
 * Displays real OS permission states for Notifications, Biometrics, and Storage Access Framework.
 */
@Composable
fun AppPermissionsScreen(
    hasOsNotificationPermission: Boolean,
    biometricAvailability: BiometricAvailability,
    isBiometricAuthEnabled: Boolean,
    onPermissionUpdated: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    val screenPadding = rememberSpatialScreenPadding(hasFloatingBottomBar = false)

    val permissionRequester = rememberNotificationPermissionRequester(
        hasOsPermission = hasOsNotificationPermission,
        onPermissionUpdated = onPermissionUpdated
    )
    ObservePermissionRefreshOnResume(onResumeAfterPause = onPermissionUpdated)

    BackHandler(onBack = onBack)

    PrismaticBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = "App Permissions",
                            style = typography.screenHeading.copy(fontFamily = CandyDisplayFamily),
                            color = colors.primaryText,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Review device access and privacy controls",
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            // Introduction Card
            item(key = "intro_card") {
                CandyBubbleCard(
                    tone = CandyTone.Neutral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CandyIconBlob(
                            icon = Icons.Rounded.Shield,
                            tone = CandyTone.Mint,
                            size = 44.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero Invasive Permissions",
                                style = typography.cardTitle.copy(fontSize = 15.sp),
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "duit. never asks for camera, microphone, contacts, location, or broad file storage access.",
                                style = typography.supportingInfo.copy(fontSize = 13.sp),
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }

            // 1. Notifications Permission
            item(key = "permission_notifications") {
                val notifStatus = if (hasOsNotificationPermission) "Allowed" else "Not Allowed"
                val notifTone = if (hasOsNotificationPermission) CandyTone.Mint else CandyTone.Coral

                PermissionItemCard(
                    icon = Icons.Rounded.Notifications,
                    tone = notifTone,
                    title = "Notifications & Reminders",
                    badgeText = notifStatus,
                    badgeTone = notifTone,
                    description = "Used to alert you before bill due dates, commitment deadlines, and budget threshold warnings.",
                    actionLabel = if (hasOsNotificationPermission) "Android Notification Settings" else "Grant Notification Access",
                    actionIcon = if (hasOsNotificationPermission) Icons.Rounded.Settings else Icons.Rounded.CheckCircle,
                    onAction = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        if (hasOsNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            openAppNotificationSettings(context)
                        } else {
                            permissionRequester.requestOrOpenSettings()
                        }
                    }
                )
            }

            // 2. Biometrics Capability
            item(key = "permission_biometrics") {
                val (bioStatus, bioTone, bioDescription, bioActionLabel) = when (biometricAvailability) {
                    BiometricAvailability.AVAILABLE -> {
                        if (isBiometricAuthEnabled) {
                            Quadruple("Available", CandyTone.Mint, "Fingerprint authentication is configured and active for app unlock.", "Manage Security Settings")
                        } else {
                            Quadruple("Available", CandyTone.Mint, "Device sensor ready. You can enable biometric unlock in Security settings.", "Configure Biometric Unlock")
                        }
                    }
                    BiometricAvailability.NONE_ENROLLED -> {
                        Quadruple("Not set up", CandyTone.Sunny, "Device supports biometrics, but no fingerprints are registered in Android settings.", "Add Fingerprint in Android")
                    }
                    BiometricAvailability.UNAVAILABLE -> {
                        Quadruple("Unavailable", CandyTone.Neutral, "This device does not have biometric authentication hardware. PIN unlock is used instead.", "Manage PIN Settings")
                    }
                    BiometricAvailability.CHANGED_OR_INVALIDATED -> {
                        Quadruple("Needs update", CandyTone.Coral, "Biometric enrollment changed. Please re-verify your PIN to update biometric access.", "Manage Security Settings")
                    }
                }

                PermissionItemCard(
                    icon = Icons.Rounded.Fingerprint,
                    tone = bioTone,
                    title = "Biometrics & App Lock",
                    badgeText = bioStatus,
                    badgeTone = bioTone,
                    description = bioDescription,
                    actionLabel = bioActionLabel,
                    actionIcon = Icons.Rounded.Lock,
                    onAction = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        if (biometricAvailability == BiometricAvailability.NONE_ENROLLED) {
                            // Fingerprints are enrolled in Android itself, not inside duit.
                            val enroll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                            }
                            runCatching { context.startActivity(enroll) }.onFailure { onOpenSecuritySettings() }
                        } else {
                            onOpenSecuritySettings()
                        }
                    }
                )
            }

            // 3. Storage & Documents (SAF)
            item(key = "permission_storage") {
                PermissionItemCard(
                    icon = Icons.Rounded.FolderShared,
                    tone = CandyTone.Mint,
                    title = "Storage & Document Picker",
                    badgeText = "Not needed",
                    badgeTone = CandyTone.Mint,
                    description = "Uses Android's official Storage Access Framework (SAF). Only files you specifically choose for backup or CSV export are accessed.",
                    actionLabel = null,
                    actionIcon = null,
                    onAction = null
                )
            }

            // 4. Background Scheduling (WorkManager)
            item(key = "permission_background") {
                PermissionItemCard(
                    icon = Icons.Rounded.Schedule,
                    tone = CandyTone.Sky,
                    title = "Background Reminders",
                    badgeText = "Not needed",
                    badgeTone = CandyTone.Sky,
                    description = "Reminders use Android's WorkManager. No exact-alarm or battery permission is requested.",
                    actionLabel = null,
                    actionIcon = null,
                    onAction = null
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    tone: CandyTone,
    title: String,
    badgeText: String,
    badgeTone: CandyTone,
    description: String,
    actionLabel: String?,
    actionIcon: ImageVector?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    CandyBubbleCard(
        tone = tone,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CandyIconBlob(
                    icon = icon,
                    tone = tone,
                    size = 42.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = typography.cardTitle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primaryText
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.floatingNavSurface)
                            .border(BorderStroke(1.dp, colors.sticker), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primaryText
                        )
                    }
                }
            }

            Text(
                text = description,
                style = typography.supportingInfo.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.secondaryText
            )

            if (actionLabel != null && onAction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.floatingNavSurface)
                        .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(14.dp))
                        .candyPressable(onClick = onAction, onClickLabel = actionLabel),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (actionIcon != null) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = actionLabel,
                            style = typography.buttonLabel.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primaryText
                        )
                    }
                }
            }
        }
    }
}
