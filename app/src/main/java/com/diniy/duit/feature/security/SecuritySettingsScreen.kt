package com.diniy.duit.feature.security

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.security.AppLockUiState
import com.diniy.duit.security.AutoLockTimeout
import com.diniy.duit.security.BiometricAvailability
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySettingsScreen(
    appLockState: AppLockUiState,
    onBack: () -> Unit,
    onOpenPinFlow: (PinFlowMode) -> Unit,
    onToggleBiometric: suspend (Boolean) -> Result<Unit>,
    onSelectAutoLockTimeout: suspend (AutoLockTimeout) -> Unit,
    onToggleHideBalances: suspend (Boolean) -> Unit,
    onTogglePrivacyMode: suspend (Boolean) -> Unit,
    onToggleAlwaysMaskWidgetAmounts: suspend (Boolean) -> Unit = {},
    onLockNow: () -> Unit,
    onOpenNotificationsSettings: () -> Unit = {}
) {
    val glass = LocalPrismaticGlass.current
    val scope = rememberCoroutineScope()
    var bannerError by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

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
                            text = "Security & Privacy",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Offline app lock, PIN security & balance masking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (bannerError != null) {
                item(key = "error") {
                    PrismaticErrorBanner(
                        message = bannerError!!,
                        onDismiss = { bannerError = null }
                    )
                }
            }

            item(key = "app_lock_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Elevated,
                    accentTint = glass.lavenderAccent
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
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = glass.lavenderAccent)
                            Text(
                                text = "App Lock",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        PrismaticBadge(
                            label = if (appLockState.isAppLockEnabled) "Protected" else "Off",
                            containerColor = if (appLockState.isAppLockEnabled) glass.aquaContainer else glass.glassSurfaceMuted,
                            contentColor = if (appLockState.isAppLockEnabled) glass.onAquaContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "Require 6-Digit PIN",
                        subtitle = "Optional protection for opening duit. Keeps recent-app previews hidden while enabled.",
                        checked = appLockState.isAppLockEnabled,
                        onCheckedChange = { enable ->
                            bannerError = null
                            if (enable) {
                                onOpenPinFlow(PinFlowMode.SETUP_NEW)
                            } else {
                                onOpenPinFlow(PinFlowMode.DISABLE_EXISTING)
                            }
                        }
                    )

                    if (appLockState.isAppLockEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = glass.glassBorderSubtle
                        )

                        SettingSwitchRow(
                            title = "Biometric Unlock",
                            subtitle = when (appLockState.biometricAvailability) {
                                BiometricAvailability.AVAILABLE -> "Use fingerprint or face unlock alongside your 6-digit PIN fallback."
                                BiometricAvailability.NONE_ENROLLED -> "No fingerprint or face enrolled on this device yet."
                                BiometricAvailability.CHANGED_OR_INVALIDATED -> "Biometric credentials changed. Re-check Android Settings."
                                BiometricAvailability.UNAVAILABLE -> "Biometric hardware unavailable; PIN remains active."
                            },
                            checked = appLockState.isBiometricEnabled,
                            onCheckedChange = { enable ->
                                scope.launch {
                                    val res = onToggleBiometric(enable)
                                    bannerError = res.exceptionOrNull()?.message
                                }
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = glass.glassBorderSubtle
                        )

                        Text(
                            text = "Auto-Lock Timeout",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Lock automatically when returning after leaving duit. in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AutoLockTimeout.entries.forEach { timeout ->
                                FilterChip(
                                    selected = appLockState.autoLockTimeout == timeout,
                                    onClick = {
                                        scope.launch { onSelectAutoLockTimeout(timeout) }
                                    },
                                    label = { Text(timeout.label) },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = glass.glassBorderSubtle
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrismaticButton(
                                text = "Change PIN",
                                onClick = { onOpenPinFlow(PinFlowMode.CHANGE_EXISTING) },
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Lock Now",
                                onClick = onLockNow,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "privacy_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.aquaAccent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = glass.aquaAccent)
                        Text(
                            text = "Privacy Masking",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "Hide Sensitive Balances",
                        subtitle = "Masks wallet balances, Safe to Spend, Savings totals, and summary figures across Home, Activity, Planning, and Insights.",
                        checked = appLockState.hideSensitiveBalances,
                        onCheckedChange = { hidden ->
                            scope.launch { onToggleHideBalances(hidden) }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = glass.glassBorderSubtle
                    )

                    SettingSwitchRow(
                        title = "Privacy Mode for Amounts",
                        subtitle = "Masks all financial amounts across transactions, budgets, obligations, sales, commissions, and analytics.",
                        checked = appLockState.privacyModeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { onTogglePrivacyMode(enabled) }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = glass.glassBorderSubtle
                    )

                    SettingSwitchRow(
                        title = "Always Mask Home Screen Widgets",
                        subtitle = "Keep amounts masked on launcher widgets even when amounts are shown inside the app.",
                        checked = appLockState.alwaysMaskWidgetAmounts,
                        onCheckedChange = { alwaysMask ->
                            scope.launch { onToggleAlwaysMaskWidgetAmounts(alwaysMask) }
                        }
                    )
                }
            }

            item(key = "notifications_entry_card") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.lavenderAccent,
                    onClick = onOpenNotificationsSettings,
                    onClickLabel = "Open Notification preferences"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = glass.lavenderAccent)
                            Column {
                                Text(
                                    text = "Notifications & Reminders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Configure due-date offsets, reminder time, categories & lock-screen privacy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "security_architecture_note") {
                PrismaticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassCardTier.Standard,
                    accentTint = glass.skyBlueAccent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = glass.skyBlueAccent)
                        Text(
                            text = "How Your Security Works",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• Your PIN is never saved in plaintext. It uses per-device salted PBKDF2-HMAC-SHA256 (120,000 iterations) sealed inside an Android Keystore AES-256-GCM envelope.\n" +
                            "• Repeated wrong PIN attempts trigger automatic temporary cooldowns.\n" +
                            "• Authentication preferences are stored completely separate from your Room financial ledger.\n" +
                            "• When App Lock is active, recent-app preview capture is disabled and financial screens remain unloaded until unlocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = com.diniy.duit.core.ui.components.candySwitchColors()
        )
    }
}
