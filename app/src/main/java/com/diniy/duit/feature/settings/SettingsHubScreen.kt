package com.diniy.duit.feature.settings

import com.diniy.duit.core.ui.components.candyPressable
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ThemeMode

enum class SettingsHubRoute {
    HUB,
    APPEARANCE,
    MOTION_HAPTICS,
    DASHBOARD,
    FINANCIAL,
    BACKUP_RESTORE,
    DATA_MANAGEMENT,
    ABOUT
}

@Composable
fun SettingsHubContainerScreen(
    uiState: SettingsHubUiState,
    route: SettingsHubRoute,
    onNavigateRoute: (SettingsHubRoute) -> Unit,
    onBackToApp: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSalaryCycles: () -> Unit,
    onOpenNotificationCentre: () -> Unit = {},
    onOpenCategories: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    viewModel: SettingsViewModel
) {
    when (route) {
        SettingsHubRoute.HUB -> SettingsHubOverviewScreen(
            uiState = uiState,
            onBack = onBackToApp,
            onSelectRoute = onNavigateRoute,
            onOpenNotifications = onOpenNotifications,
            onOpenNotificationCentre = onOpenNotificationCentre,
            onOpenSecurity = onOpenSecurity,
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity
        )
        SettingsHubRoute.APPEARANCE -> AppearanceSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onOpenMotion = { onNavigateRoute(SettingsHubRoute.MOTION_HAPTICS) },
            onOpenHomeLayout = { onNavigateRoute(SettingsHubRoute.DASHBOARD) }
        )
        // Motion and Home layout are reached from Appearance, so Back returns there.
        SettingsHubRoute.MOTION_HAPTICS -> MotionHapticsSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.APPEARANCE) },
            onSetHaptics = viewModel::setHapticFeedbackEnabled,
            onSetReduceMotion = viewModel::setReduceMotion,
            onSetAnimationIntensity = viewModel::setAnimationIntensity
        )
        SettingsHubRoute.DASHBOARD -> DashboardCustomizationScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.APPEARANCE) },
            onSetVisible = viewModel::setDashboardSectionVisible,
            onMove = viewModel::moveDashboardSection,
            onRestoreDefault = viewModel::restoreDefaultDashboardLayout,
            onClearFeedback = viewModel::clearFeedback
        )
        // Money preferences are opened from Planning; Back returns to the app.
        SettingsHubRoute.FINANCIAL -> FinancialPreferencesScreen(
            uiState = uiState,
            onBack = onBackToApp,
            onSetDefaultWallet = viewModel::setDefaultWalletId,
            onSetBudgetPeriod = viewModel::setDefaultBudgetPeriodType,
            onSetAutoAdvanceRecurring = viewModel::setAutoAdvanceRecurringCommitments,
            onSetCommissionTracking = viewModel::setCommissionTrackingEnabled,
            onResetCommissionFormula = viewModel::resetCommissionFormulaConfig,
            onOpenSalaryCycles = onOpenSalaryCycles,
            onOpenCategories = onOpenCategories
        )
        SettingsHubRoute.BACKUP_RESTORE -> BackupAndRestoreScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onVerifyPin = onVerifyPin,
            viewModel = viewModel
        )
        SettingsHubRoute.DATA_MANAGEMENT -> DataManagementScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onVerifyPin = onVerifyPin,
            viewModel = viewModel
        )
        SettingsHubRoute.ABOUT -> AboutDuitScreen(
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) }
        )
    }
}

@Composable
private fun SettingsHubOverviewScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSelectRoute: (SettingsHubRoute) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotificationCentre: () -> Unit,
    onOpenSecurity: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectGlassIntensity: (GlassIntensity) -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val app = uiState.appSettings
    val defaultWalletName = uiState.wallets.firstOrNull { it.id == app.defaultWalletId }?.name ?: "First liquid wallet"
    val visibleCount = DashboardSectionKey.entries.size - app.hiddenDashboardSections.size

    SettingsPageScaffold(
        title = "Settings",
        subtitle = "Preferences, privacy, encrypted backup & data tools",
        onBack = onBack,
        statusMessage = uiState.statusMessage,
        errorMessage = uiState.errorMessage,
        onClearFeedback = {}
    ) {
        // Phase 7A: exactly six destinations. Motion, Home layout live in Appearance;
        // Notification Centre in Notifications; money preferences in Planning.
        item(key = "group_personal") {
            com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Make it yours") {
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = com.diniy.duit.core.ui.components.SpatialGlassTier.Standard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Palette,
                        accent = colors.coral,
                        title = "Appearance",
                        subtitle = "${app.themeMode.label} · ${app.glassIntensity.label} depth · ${if (app.reduceMotion) "Reduce motion" else app.animationIntensity.label + " motion"}",
                        onClick = { onSelectRoute(SettingsHubRoute.APPEARANCE) }
                    )
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Notifications,
                        accent = colors.aqua,
                        title = "Notifications",
                        subtitle = if (uiState.notificationSettings.masterEnabled) {
                            "${uiState.notificationSettings.enabledCategories.size} categories · ${uiState.notificationSettings.formattedReminderTime}"
                        } else {
                            "Smart reminders paused"
                        },
                        onClick = onOpenNotifications
                    )
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Shield,
                        accent = colors.sunny,
                        title = "Security",
                        subtitle = buildString {
                            append(if (uiState.securityState.isAppLockEnabled) "PIN lock on" else "PIN lock off")
                            append(" · ")
                            append(if (uiState.securityState.privacySettings.isAnyMaskActive) "Amounts hidden" else "Amounts visible")
                        },
                        onClick = onOpenSecurity
                    )
                }
            }
        }

        item(key = "group_data") {
            com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Your data") {
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = com.diniy.duit.core.ui.components.SpatialGlassTier.Standard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Backup,
                        accent = colors.skyBlue,
                        title = "Backup & Restore",
                        subtitle = "Encrypted local backup and safe restore",
                        onClick = { onSelectRoute(SettingsHubRoute.BACKUP_RESTORE) }
                    )
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Storage,
                        accent = colors.peach,
                        title = "Data Management",
                        subtitle = "${uiState.storageSummary.totalFinancialRecords} records on this device · export and tools",
                        onClick = { onSelectRoute(SettingsHubRoute.DATA_MANAGEMENT) }
                    )
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Info,
                        accent = colors.aqua,
                        title = "About duit.",
                        subtitle = "Version and app information",
                        onClick = { onSelectRoute(SettingsHubRoute.ABOUT) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectGlassIntensity: (GlassIntensity) -> Unit,
    onOpenMotion: () -> Unit = {},
    onOpenHomeLayout: () -> Unit = {}
) {
    val glass = LocalPrismaticGlass.current
    val settings = uiState.appSettings
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val visibleCount = DashboardSectionKey.entries.size - settings.hiddenDashboardSections.size

    SettingsPageScaffold(
        title = "Appearance",
        subtitle = "Theme, card depth, motion and Home layout",
        onBack = onBack
    ) {
        item(key = "theme_mode") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.lavenderAccent) {
                Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Applies immediately across the app and home screen widgets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }
        }

        item(key = "glass_intensity") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.aquaAccent) {
                Text("Card depth", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "How much soft shadow candy cards have.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIntensity.entries.forEach { level ->
                        FilterChip(
                            selected = settings.glassIntensity == level,
                            onClick = { onSelectGlassIntensity(level) },
                            label = { Text(level.label) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = settings.glassIntensity.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.readableOn(glass.aquaAccent)
                )
            }
        }

        item(key = "appearance_more") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
            ) {
                SettingsGroupedRowItem(
                    icon = Icons.Rounded.Animation,
                    accent = colors.skyBlue,
                    title = "Motion & Haptics",
                    subtitle = "${if (settings.hapticFeedbackEnabled) "Haptics on" else "Haptics off"} · ${if (settings.reduceMotion) "Reduce motion" else settings.animationIntensity.label}",
                    onClick = onOpenMotion
                )
                SettingsGroupedRowItem(
                    icon = Icons.Rounded.DashboardCustomize,
                    accent = colors.aqua,
                    title = "Home layout",
                    subtitle = "$visibleCount/${DashboardSectionKey.entries.size} Home sections shown · Safe to Spend pinned",
                    onClick = onOpenHomeLayout
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionHapticsSettingsScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
    onSetAnimationIntensity: (AnimationIntensity) -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val context = LocalContext.current
    val settings = uiState.appSettings
    val systemReduced = remember(context) {
        runCatching {
            AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    SettingsPageScaffold(
        title = "Motion & Haptics",
        subtitle = "Touch feedback, reduced motion & accessibility pacing",
        onBack = onBack
    ) {
        item(key = "haptics_motion_toggles") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.skyBlueAccent) {
                SettingsSwitchRow(
                    title = "Haptic Feedback",
                    subtitle = "Subtle tactile confirmation on privacy eye toggles and primary controls.",
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = onSetHaptics
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = glass.glassBorderSubtle)
                SettingsSwitchRow(
                    title = "Reduce Motion",
                    subtitle = if (systemReduced) {
                        "System animator scale is off — transitions are already snapped."
                    } else {
                        "Replaces spring & fade animations with instant state changes."
                    },
                    checked = settings.reduceMotion || systemReduced,
                    onCheckedChange = onSetReduceMotion
                )
            }
        }

        item(key = "anim_intensity") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.lavenderAccent) {
                Text("Animation Intensity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Persists transition pacing preferences and informs Phase 6 polish. Follows Android system display refresh capabilities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimationIntensity.entries.forEach { level ->
                        FilterChip(
                            selected = settings.animationIntensity == level,
                            onClick = { onSetAnimationIntensity(level) },
                            label = { Text(level.label) },
                            enabled = !settings.reduceMotion && !systemReduced,
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = settings.animationIntensity.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardCustomizationScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSetVisible: (DashboardSectionKey, Boolean) -> Unit,
    onMove: (DashboardSectionKey, Int) -> Unit,
    onRestoreDefault: () -> Unit,
    onClearFeedback: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val settings = uiState.appSettings

    SettingsPageScaffold(
        title = "Dashboard Customization",
        subtitle = "Reorder or hide optional Home cards without altering calculations",
        onBack = onBack,
        statusMessage = uiState.statusMessage,
        onClearFeedback = onClearFeedback
    ) {
        item(key = "pinned_safe_to_spend") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.aquaAccent) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safe to Spend Overview", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Always pinned at the top of Home for financial safety.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PrismaticBadge(label = "Pinned", containerColor = glass.aquaContainer, contentColor = glass.onAquaContainer)
                }
            }
        }

        item(key = "sections_list") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.lavenderAccent) {
                val order = settings.dashboardSectionOrder
                order.forEachIndexed { idx, section ->
                    if (idx > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = glass.glassBorderSubtle)
                    }
                    val visible = section !in settings.hiddenDashboardSections
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                            Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onMove(section, -1) }, enabled = idx > 0) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move ${section.title} up")
                        }
                        IconButton(onClick = { onMove(section, +1) }, enabled = idx < order.lastIndex) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move ${section.title} down")
                        }
                        Switch(checked = visible, onCheckedChange = { onSetVisible(section, it) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrismaticButton(
                    text = "Restore Default Layout",
                    onClick = onRestoreDefault,
                    variant = PrismaticButtonVariant.Secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinancialPreferencesScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSetDefaultWallet: (Long?) -> Unit,
    onSetBudgetPeriod: (BudgetPeriodType) -> Unit,
    onSetAutoAdvanceRecurring: (Boolean) -> Unit,
    onSetCommissionTracking: (Boolean) -> Unit,
    onResetCommissionFormula: () -> Unit,
    onOpenSalaryCycles: () -> Unit,
    onOpenCategories: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val settings = uiState.appSettings
    var pendingBudgetPeriod by remember { mutableStateOf<BudgetPeriodType?>(null) }
    var pendingAutoAdvance by remember { mutableStateOf<Boolean?>(null) }
    var showConfirmResetCommission by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(
        title = "Financial Preferences",
        subtitle = "Wallet defaults, cycles, budgets, categories & integer-sen MYR rules",
        onBack = onBack
    ) {
        item(key = "currency_safety") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.aquaAccent) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Currency Display", style = MaterialTheme.typography.titleMedium)
                    PrismaticBadge(label = "MYR (RM)", containerColor = glass.aquaContainer, contentColor = glass.onAquaContainer)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "All amounts use integer sen arithmetic. Cycle-aware Safe to Spend excludes Protected Savings and pending commissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "default_wallet") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.skyBlueAccent) {
                Text("Default Source Wallet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pre-selected when opening the transaction editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.defaultWalletId == null,
                        onClick = { onSetDefaultWallet(null) },
                        label = { Text("Auto (First Liquid)") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    uiState.wallets.forEach { wallet ->
                        FilterChip(
                            selected = settings.defaultWalletId == wallet.id,
                            onClick = { onSetDefaultWallet(wallet.id) },
                            label = { Text(wallet.name) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }
        }

        item(key = "budget_recurring") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.peachAccent) {
                Text("Default Budget Period", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sets the default period view for new Planning budgets without altering historical records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetPeriodType.entries.forEach { period ->
                        FilterChip(
                            selected = settings.defaultBudgetPeriodType == period,
                            onClick = {
                                if (settings.defaultBudgetPeriodType != period) {
                                    pendingBudgetPeriod = period
                                }
                            },
                            label = { Text(period.label) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = glass.glassBorderSubtle)
                SettingsSwitchRow(
                    title = "Auto-Advance Recurring Bills",
                    subtitle = "Automatically schedule next recurring due date when a commitment is paid in full.",
                    checked = settings.autoAdvanceRecurringCommitments,
                    onCheckedChange = { next -> pendingAutoAdvance = next }
                )
            }
        }

        item(key = "commission_cycles_categories") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.lavenderAccent) {
                SettingsSwitchRow(
                    title = "Show Commission Notice on Home",
                    subtitle = "Displays pending commission totals on Home without counting them in Safe to Spend.",
                    checked = settings.commissionTrackingEnabled,
                    onCheckedChange = onSetCommissionTracking
                )
                if (settings.commissionFormulaConfig.isConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Saved formula: ${settings.commissionFormulaConfig.formulaName.ifBlank { "Custom rule" }} (${settings.commissionFormulaConfig.rateBps / 100.0}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    PrismaticButton(
                        text = "Reset Commission Formula",
                        onClick = { showConfirmResetCommission = true },
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = glass.glassBorderSubtle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismaticButton(
                        text = "Salary Cycles",
                        onClick = onOpenSalaryCycles,
                        variant = PrismaticButtonVariant.Secondary
                    )
                    PrismaticButton(
                        text = "Categories",
                        onClick = onOpenCategories,
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
            }
        }
    }

    pendingBudgetPeriod?.let { chosen ->
        AlertDialog(
            onDismissRequest = { pendingBudgetPeriod = null },
            title = { Text("Change Default Budget Period?") },
            text = {
                Text("Switching default budget period to ${chosen.label} updates future budget defaults and displayed Planning views. Existing persisted transactions and historical budgets are not modified.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetBudgetPeriod(chosen)
                    pendingBudgetPeriod = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBudgetPeriod = null }) { Text("Cancel") }
            }
        )
    }

    pendingAutoAdvance?.let { nextValue ->
        AlertDialog(
            onDismissRequest = { pendingAutoAdvance = null },
            title = { Text(if (nextValue) "Enable Auto-Advance?" else "Disable Auto-Advance?") },
            text = {
                Text(
                    if (nextValue) {
                        "Paid recurring commitments will automatically advance to the next cycle's due date while keeping current-cycle Safe to Spend accurate."
                    } else {
                        "Paid commitments stay marked Paid until you manually open the next occurrence. Past payment history remains intact."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetAutoAdvanceRecurring(nextValue)
                    pendingAutoAdvance = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAutoAdvance = null }) { Text("Cancel") }
            }
        )
    }

    if (showConfirmResetCommission) {
        AlertDialog(
            onDismissRequest = { showConfirmResetCommission = false },
            title = { Text("Reset Commission Formula?") },
            text = { Text("Clears the saved commission estimator defaults. Existing commission records and payouts are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetCommissionFormula()
                    showConfirmResetCommission = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetCommission = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AboutDuitScreen(onBack: () -> Unit) {
    val glass = LocalPrismaticGlass.current
    val context = LocalContext.current
    val pkgInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "0.1.0"
    val versionCode = pkgInfo?.longVersionCode ?: 1L

    SettingsPageScaffold(
        title = "About duit.",
        subtitle = "Offline-first personal finance for Malaysia (MYR)",
        onBack = onBack
    ) {
        item(key = "app_info") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Elevated, accentTint = glass.aquaAccent) {
                Text("duit.", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version $versionName (Build $versionCode) · Min SDK 29 · Target SDK ${context.applicationInfo.targetSdkVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "privacy_guarantee") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.lavenderAccent) {
                Text("Offline-First Privacy Guarantee", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "• Zero internet permissions — your ledger never leaves this device.\n" +
                        "• All balances use integer sen arithmetic with Room SQLite transactions.\n" +
                        "• App PIN protection uses per-device salted PBKDF2-HMAC-SHA256 sealed in Android Keystore.\n" +
                        "• Portable local backups use password-derived PBKDF2-HMAC-SHA256 + AES-256-GCM authenticated encryption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "oss_notices") {
            PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.skyBlueAccent) {
                Text("Open-Source Acknowledgements", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Built with Kotlin, Kotlin Coroutines, AndroidX Jetpack Compose, Material Design 3, AndroidX Room, DataStore Preferences, WorkManager, BiometricPrompt, and Jetpack Glance (Apache License 2.0).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsPageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    statusMessage: String? = null,
    errorMessage: String? = null,
    onClearFeedback: () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val glass = LocalPrismaticGlass.current
    BackHandler(onBack = onBack)

    PrismaticBackground {
        val screenPadding = com.diniy.duit.core.ui.components.rememberSpatialScreenPadding(hasFloatingBottomBar = false)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "settings_header") {
                val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
                val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    com.diniy.duit.core.ui.components.SpatialGlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = typography.screenHeading,
                            color = colors.primaryText,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = subtitle,
                            style = typography.supportingInfo,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            if (errorMessage != null) {
                item(key = "settings_error") {
                    PrismaticErrorBanner(message = errorMessage, onDismiss = onClearFeedback)
                }
            }

            if (statusMessage != null) {
                item(key = "settings_status") {
                    PrismaticGlassCard(modifier = Modifier.fillMaxWidth(), tier = GlassCardTier.Standard, accentTint = glass.aquaAccent) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = com.diniy.duit.core.ui.theme.SpatialTheme.colors.readableOn(glass.aquaAccent), modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearFeedback) { Text("Dismiss") }
                        }
                    }
                }
            }

            content()
        }
    }
}

@Composable
private fun SettingsGroupedRowItem(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val geometry = com.diniy.duit.core.ui.theme.SpatialTheme.geometry

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .candyPressable(onClick = onClick, onClickLabel = "Open $title")
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Candy icon blob: solid accent squircle, slightly tilted, dark ink icon (readable in both modes)
        Box(
            modifier = Modifier
                .size(40.dp)
                .rotate(-6f)
                .clip(RoundedCornerShape(14.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onCoral,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(6f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.transactionDescription,
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                style = typography.caption,
                color = colors.secondaryText
            )
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.baseSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            val type = com.diniy.duit.core.ui.theme.SpatialTheme.typography
            Text(title, style = type.transactionDescription, color = colors.primaryText)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = type.caption, color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = colors.coral,
                checkedThumbColor = Color.White,
                checkedBorderColor = colors.sticker,
                uncheckedTrackColor = colors.baseSurface,
                uncheckedThumbColor = colors.secondaryText,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
