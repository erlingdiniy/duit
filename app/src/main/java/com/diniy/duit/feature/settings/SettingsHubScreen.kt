package com.diniy.duit.feature.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.VerifiedUser
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
    ABOUT,
    FEATURE_GUIDE,
    APP_PERMISSIONS
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
    viewModel: SettingsViewModel,
    onReplayTour: () -> Unit = {},
    hasOsNotificationPermission: Boolean = false,
    onPermissionUpdated: () -> Unit = {}
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
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onSetHaptics = viewModel::setHapticFeedbackEnabled
        )
        SettingsHubRoute.APPEARANCE -> AppearanceSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onOpenMotion = { onNavigateRoute(SettingsHubRoute.MOTION_HAPTICS) },
            onOpenHomeLayout = { onNavigateRoute(SettingsHubRoute.DASHBOARD) }
        )
        // Phase 11J: Motion & Haptics screen removed; the legacy route opens Appearance.
        SettingsHubRoute.MOTION_HAPTICS -> AppearanceSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onOpenHomeLayout = { onNavigateRoute(SettingsHubRoute.DASHBOARD) }
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
        SettingsHubRoute.FEATURE_GUIDE -> FeatureGuideScreen(
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onReplayTour = onReplayTour
        )
        SettingsHubRoute.APP_PERMISSIONS -> AppPermissionsScreen(
            hasOsNotificationPermission = hasOsNotificationPermission,
            biometricAvailability = uiState.securityState.biometricAvailability,
            isBiometricAuthEnabled = uiState.securityState.isBiometricEnabled,
            onPermissionUpdated = onPermissionUpdated,
            onOpenSecuritySettings = onOpenSecurity,
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
    onSelectGlassIntensity: (GlassIntensity) -> Unit,
    onSetHaptics: (Boolean) -> Unit
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
                        subtitle = "${app.themeMode.label} theme · Home layout",
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
                    // Phase 11J: haptics moved here from the removed Motion & Haptics screen
                    SettingsGroupedSwitchItem(
                        icon = Icons.Rounded.Vibration,
                        accent = colors.peach,
                        title = "Haptic feedback",
                        subtitle = "Gentle vibration on taps",
                        checked = app.hapticFeedbackEnabled,
                        onCheckedChange = onSetHaptics
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
                        icon = Icons.Rounded.AutoStories,
                        accent = colors.sunny,
                        title = "Getting Started & Feature Guide",
                        subtitle = "Learn how to use duit. · Replay tour",
                        onClick = { onSelectRoute(SettingsHubRoute.FEATURE_GUIDE) }
                    )
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.VerifiedUser,
                        accent = colors.aqua,
                        title = "App Permissions",
                        subtitle = "Notifications, biometrics and storage",
                        onClick = { onSelectRoute(SettingsHubRoute.APP_PERMISSIONS) }
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
    // Phase 11A: Card depth is no longer shown here. The stored preference (default Balanced
    // when none is saved) is left untouched for backward compatibility.
    val settings = uiState.appSettings
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val visibleCount = DashboardSectionKey.entries.size - settings.hiddenDashboardSections.size

    SettingsPageScaffold(
        title = "Appearance",
        subtitle = "Theme and Home layout",
        onBack = onBack
    ) {
        item(key = "theme_mode") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .rotate(-6f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = colors.onCoral,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(6f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Theme", style = typography.cardTitle, color = colors.primaryText)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        com.diniy.duit.core.ui.components.SpatialGlassPill(
                            label = mode.label,
                            modifier = Modifier.weight(1f),
                            selected = settings.themeMode == mode,
                            onClick = { onSelectTheme(mode) }
                        )
                    }
                }
            }
        }

        item(key = "appearance_more") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
            ) {
                SettingsGroupedRowItem(
                    icon = Icons.Rounded.DashboardCustomize,
                    accent = colors.aqua,
                    title = "Home layout",
                    subtitle = "$visibleCount/${DashboardSectionKey.entries.size} sections shown",
                    onClick = onOpenHomeLayout
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

/** Phase 11B: About has its own small sub-pages; Back (system or toolbar) returns to About. */
private enum class AboutPage { MAIN, PRIVACY, LICENSES }

@Composable
private fun AboutDuitScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(AboutPage.MAIN) }
    when (page) {
        AboutPage.MAIN -> AboutMainPage(
            onBack = onBack,
            onOpenPrivacy = { page = AboutPage.PRIVACY },
            onOpenLicenses = { page = AboutPage.LICENSES }
        )
        AboutPage.PRIVACY -> AboutPrivacyPage(onBack = { page = AboutPage.MAIN })
        AboutPage.LICENSES -> AboutLicensesPage(onBack = { page = AboutPage.MAIN })
    }
}

@Composable
private fun AboutMainPage(onBack: () -> Unit, onOpenPrivacy: () -> Unit, onOpenLicenses: () -> Unit) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val context = LocalContext.current
    // Version comes from the installed package (build metadata), never hardcoded.
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    SettingsPageScaffold(
        title = "About duit.",
        subtitle = "App information",
        onBack = onBack
    ) {
        item(key = "identity") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.diniy.duit.R.drawable.duit_logo),
                    contentDescription = "duit. logo",
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(30.dp))
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append("duit")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = colors.coral)) { append(".") }
                    },
                    style = typography.screenHeading,
                    color = colors.primaryText
                )
                Text("Your money, made simple.", style = typography.supportingInfo, color = colors.secondaryText)
                if (!versionName.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.baseSurface)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Version $versionName", style = typography.caption, color = colors.secondaryText)
                    }
                }
            }
        }

        item(key = "info_menu") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
            ) {
                SettingsGroupedRowItem(
                    icon = Icons.Rounded.Shield,
                    accent = colors.aqua,
                    title = "Privacy & Security",
                    subtitle = "Your financial data stays on your device",
                    onClick = onOpenPrivacy
                )
                SettingsGroupedRowItem(
                    icon = Icons.Rounded.Info,
                    accent = colors.skyBlue,
                    title = "Open-Source Licenses",
                    subtitle = "Third-party acknowledgements",
                    onClick = onOpenLicenses
                )
            }
        }

        item(key = "credit") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Designed & developed by", style = typography.caption, color = colors.secondaryText)
                Spacer(Modifier.height(2.dp))
                // Deep teal in light mode; the theme's primary ink keeps it readable in dark mode.
                Text("Erling Diniy", style = typography.cardTitle, color = colors.primaryText)
                Spacer(Modifier.height(2.dp))
                Text("Made with care in Malaysia 🇲🇾", style = typography.caption, color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun AboutInfoCard(icon: ImageVector, accent: Color, title: String, body: String) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    com.diniy.duit.core.ui.components.SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .rotate(-6f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.onCoral, modifier = Modifier.size(19.dp).rotate(6f))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = typography.cardTitle, color = colors.primaryText)
                Spacer(Modifier.height(4.dp))
                Text(body, style = typography.supportingInfo, color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun AboutPrivacyPage(onBack: () -> Unit) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    SettingsPageScaffold(
        title = "Privacy & Security",
        subtitle = "How duit. looks after your money data",
        onBack = onBack
    ) {
        item(key = "on_device") {
            AboutInfoCard(
                Icons.Rounded.Storage, colors.aqua, "Stays on your device",
                "Your wallets, transactions and plans are saved only on this phone. duit. doesn't ask for internet access, so your records aren't sent anywhere."
            )
        }
        item(key = "lock") {
            AboutInfoCard(
                Icons.Rounded.Shield, colors.coral, "Lock the app",
                "Turn on a PIN in Settings → Security, and use your fingerprint where your phone supports it. While App Lock is on, duit. also hides its screen in the recent-apps view."
            )
        }
        item(key = "mask") {
            AboutInfoCard(
                Icons.Rounded.Palette, colors.sunny, "Hide amounts",
                "Privacy Mode hides balances and amounts on screen when you want a little discretion."
            )
        }
        item(key = "backup") {
            AboutInfoCard(
                Icons.Rounded.Backup, colors.skyBlue, "Encrypted backups",
                "Backups you create are encrypted (AES-256) with a password you choose. Keep that password safe — duit. can't recover it for you."
            )
        }
    }
}

private data class LicenseNotice(val name: String, val copyright: String, val license: String)

// Only components shipped in the app. Font notices are copied from the font files' own metadata.
private val LicenseNotices = listOf(
    LicenseNotice(
        "Fredoka (font)",
        "Copyright 2016 The Fredoka Project Authors (https://github.com/hafontia/Fredoka-One)",
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is available with a FAQ at: http://scripts.sil.org/OFL"
    ),
    LicenseNotice(
        "Nunito (font)",
        "Copyright 2014 The Nunito Project Authors (https://github.com/googlefonts/nunito)",
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is available with a FAQ at: https://scripts.sil.org/OFL"
    ),
    LicenseNotice(
        "AndroidX & Jetpack Compose",
        "Copyright The Android Open Source Project. Includes Compose UI, Material 3, Material Icons, Activity, Lifecycle, Navigation, Room, DataStore, WorkManager, Glance, Biometric, Fragment and Core.",
        "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"
    ),
    LicenseNotice(
        "Kotlin & kotlinx.coroutines",
        "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.",
        "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"
    )
)

@Composable
private fun AboutLicensesPage(onBack: () -> Unit) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    SettingsPageScaffold(
        title = "Open-Source Licenses",
        subtitle = "Third-party acknowledgements",
        onBack = onBack
    ) {
        LicenseNotices.forEach { notice ->
            item(key = "license_${notice.name}") {
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(notice.name, style = typography.cardTitle, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(notice.copyright, style = typography.caption, color = colors.secondaryText)
                    Spacer(Modifier.height(6.dp))
                    Text(notice.license, style = typography.supportingInfo, color = colors.secondaryText)
                }
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
private fun SettingsGroupedSwitchItem(
    icon: ImageVector,
    accent: Color,
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
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .rotate(-6f)
                .clip(RoundedCornerShape(14.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.onCoral, modifier = Modifier.size(20.dp).rotate(6f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.transactionDescription, color = colors.primaryText)
            Text(subtitle, style = typography.caption, color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles (single, accessible target)
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
