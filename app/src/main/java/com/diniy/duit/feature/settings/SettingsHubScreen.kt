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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Calculate
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
    APP_PERMISSIONS,
    PRIVACY_DATA,
    HELP_CENTRE
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
    onPermissionUpdated: () -> Unit = {},
    onOpenDeveloperCenter: () -> Unit = {}
) {
    var financialOpenedFromHub by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(route) {
        if (route == SettingsHubRoute.BACKUP_RESTORE) {
            com.diniy.duit.analytics.UsageAnalytics.logFeatureUsed(com.diniy.duit.analytics.AnalyticsFeature.BACKUP_AND_RESTORE)
        }
    }

    when (route) {
        SettingsHubRoute.HUB -> SettingsHubOverviewScreen(
            uiState = uiState,
            onBack = onBackToApp,
            onSelectRoute = { next ->
                if (next == SettingsHubRoute.FINANCIAL) financialOpenedFromHub = true
                onNavigateRoute(next)
            },
            onOpenNotifications = onOpenNotifications,
            onOpenNotificationCentre = onOpenNotificationCentre,
            onOpenSecurity = onOpenSecurity,
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onSetHaptics = viewModel::setHapticFeedbackEnabled,
            onSetCommissionFeature = viewModel::setCommissionFeatureEnabled,
            onSetUsageAnalytics = viewModel::setUsageAnalyticsEnabled,
            onOpenDeveloperCenter = onOpenDeveloperCenter
        )
        SettingsHubRoute.APPEARANCE -> AppearanceSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onSelectVisualTheme = viewModel::setVisualTheme,
            onSelectTheme = viewModel::setThemeMode,
            onSelectGlassIntensity = viewModel::setGlassIntensity,
            onOpenMotion = { onNavigateRoute(SettingsHubRoute.MOTION_HAPTICS) },
            onOpenHomeLayout = { onNavigateRoute(SettingsHubRoute.DASHBOARD) }
        )
        // Phase 11J: Motion & Haptics screen removed; the legacy route opens Appearance.
        SettingsHubRoute.MOTION_HAPTICS -> AppearanceSettingsScreen(
            uiState = uiState,
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onSelectVisualTheme = viewModel::setVisualTheme,
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
        // Money preferences can be opened from Settings Hub or directly from Planning.
        SettingsHubRoute.FINANCIAL -> FinancialPreferencesScreen(
            uiState = uiState,
            onBack = {
                if (financialOpenedFromHub) {
                    financialOpenedFromHub = false
                    onNavigateRoute(SettingsHubRoute.HUB)
                } else {
                    onBackToApp()
                }
            },
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
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onOpenOwnerMode = onOpenDeveloperCenter
        )
        SettingsHubRoute.HELP_CENTRE -> HelpCentreScreen(
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onOpenRoute = onNavigateRoute
        )
        SettingsHubRoute.PRIVACY_DATA -> PrivacyDataScreen(
            onBack = { onNavigateRoute(SettingsHubRoute.HUB) },
            onOpenRoute = onNavigateRoute,
            crashReportingEnabled = uiState.appSettings.crashReportingEnabled,
            onSetCrashReporting = viewModel::setCrashReportingEnabled,
            usageAnalyticsEnabled = uiState.appSettings.usageAnalyticsEnabled,
            onSetUsageAnalytics = viewModel::setUsageAnalyticsEnabled
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
    onSetHaptics: (Boolean) -> Unit,
    onSetCommissionFeature: (Boolean) -> Unit = {},
    onSetUsageAnalytics: (Boolean) -> Unit = {},
    onOpenDeveloperCenter: () -> Unit = {}
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val app = uiState.appSettings
    // Public edition only (the personal build has no feedback URL and no network access).
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    if (showFeedback) com.diniy.duit.feedback.HelpFeedbackScreen(onClose = { showFeedback = false })
    // Public edition with a real QR configured only; never shown as a popup elsewhere.
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    val supportAvailable = remember { com.diniy.duit.support.isSupportPageAvailable(hostContext) }
    var showSupport by rememberSaveable { mutableStateOf(false) }
    if (showSupport) com.diniy.duit.support.SupportDuitScreen(onClose = { showSupport = false })
    val defaultWalletName = uiState.wallets.firstOrNull { it.id == app.defaultWalletId }?.name ?: "First liquid wallet"
    val visibleCount = DashboardSectionKey.entries.size - app.hiddenDashboardSections.size
    val showOwnerSection = com.diniy.duit.devcenter.DeveloperCenterEntryPoint.isAvailable &&
        com.diniy.duit.devcenter.DeveloperCenterEntryPoint.rememberIsOwnerVisible()

    SettingsPageScaffold(
        title = "Settings",
        subtitle = "Preferences, privacy, encrypted backup & data tools",
        onBack = onBack,
        statusMessage = uiState.statusMessage,
        errorMessage = uiState.errorMessage,
        onClearFeedback = {}
    ) {
        // Phase 9: organized into clear categories (Preferences, Security & Privacy, Backup & Data, Help & About).
        item(key = "group_personal") {
            com.diniy.duit.core.ui.components.SpatialGlassSection(title = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Preferences" else "Make it yours") {
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
                        subtitle = "${app.visualTheme.label} · ${app.themeMode.label}",
                        onClick = { onSelectRoute(SettingsHubRoute.APPEARANCE) }
                    )
                    if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                        val budgetPeriodLabel = if (app.defaultBudgetPeriodType == BudgetPeriodType.MONTHLY) "Monthly" else "Salary cycle"
                        SettingsGroupedRowItem(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            accent = colors.sunny,
                            title = "Money Preferences",
                            subtitle = "$defaultWalletName · $budgetPeriodLabel budgets",
                            onClick = { onSelectRoute(SettingsHubRoute.FINANCIAL) }
                        )
                    }
                    SettingsGroupedRowItem(
                        icon = Icons.Rounded.Notifications,
                        accent = colors.aqua,
                        title = "Notifications",
                        subtitle = if (uiState.notificationSettings.masterEnabled) {
                            (if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) countLabel(uiState.notificationSettings.enabledCategories.size, "category", "categories")
                            else "${uiState.notificationSettings.enabledCategories.size} categories") + " · ${uiState.notificationSettings.formattedReminderTime}"
                        } else {
                            "Smart reminders paused"
                        },
                        onClick = onOpenNotifications
                    )
                    if (!com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
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
                    SettingsGroupedSwitchItem(
                        icon = Icons.Rounded.Vibration,
                        accent = colors.peach,
                        title = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Haptic Feedback" else "Haptic feedback",
                        subtitle = "Gentle vibration on taps",
                        checked = app.hapticFeedbackEnabled,
                        onCheckedChange = onSetHaptics
                    )
                    if (com.diniy.duit.BuildConfig.PUBLIC_EDITION && com.diniy.duit.BuildConfig.OPTIONAL_COMMISSION) {
                        SettingsGroupedSwitchItem(
                            icon = Icons.Rounded.Calculate,
                            accent = colors.lavender,
                            title = "Commission Tracking",
                            subtitle = if (app.commissionFeatureEnabled) {
                                "Sales, commission and payouts in Planning, Quick Add and Insights"
                            } else {
                                "Off. Turn on to track sales and commission. Saved records are kept"
                            },
                            checked = app.commissionFeatureEnabled,
                            onCheckedChange = onSetCommissionFeature
                        )
                    }
                }
            }
        }

        if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
            item(key = "group_security_privacy") {
                com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Security & Privacy") {
                    com.diniy.duit.core.ui.components.SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = com.diniy.duit.core.ui.components.SpatialGlassTier.Standard,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                    ) {
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
                        SettingsGroupedRowItem(
                            icon = Icons.Rounded.Shield,
                            accent = colors.aqua,
                            title = "Privacy & Your Data",
                            subtitle = "What's stored, what's sent and how to delete it",
                            onClick = { onSelectRoute(SettingsHubRoute.PRIVACY_DATA) }
                        )
                        if (com.diniy.duit.BuildConfig.ANALYTICS_ENABLED) {
                            SettingsGroupedSwitchItem(
                                icon = Icons.Rounded.Info,
                                accent = colors.skyBlue,
                                title = "Usage Analytics",
                                subtitle = if (app.usageAnalyticsEnabled) "On · Anonymous feature counts" else "Off · Anonymous feature counts",
                                checked = app.usageAnalyticsEnabled,
                                onCheckedChange = onSetUsageAnalytics
                            )
                        }
                        SettingsGroupedRowItem(
                            icon = Icons.Rounded.VerifiedUser,
                            accent = colors.aqua,
                            title = "App Permissions",
                            subtitle = "Notifications, biometrics and storage",
                            onClick = { onSelectRoute(SettingsHubRoute.APP_PERMISSIONS) }
                        )
                    }
                }
            }
            item(key = "group_data") {
                com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Backup & Data") {
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
                            subtitle = recordCountLabel(uiState.storageSummary.totalFinancialRecords) + " on this device · Export and tools",
                            onClick = { onSelectRoute(SettingsHubRoute.DATA_MANAGEMENT) }
                        )
                    }
                }
            }
            item(key = "group_help") {
                com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Help & About") {
                    com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = com.diniy.duit.core.ui.components.SpatialGlassTier.Standard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) {
                        SettingsGroupedRowItem(
                            icon = Icons.Rounded.AutoStories,
                            accent = colors.peach,
                            title = "Help Centre",
                            subtitle = "Quick guides for every part of duit.",
                            onClick = { onSelectRoute(SettingsHubRoute.HELP_CENTRE) }
                        )
                        if (com.diniy.duit.BuildConfig.FEEDBACK_URL.isNotEmpty()) {
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.Forum,
                                accent = colors.coral,
                                title = "Help & Feedback",
                                subtitle = "Report a bug, suggest a feature or say hi",
                                onClick = { showFeedback = true }
                            )
                        }
                        if (supportAvailable) {
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.LocalCafe,
                                accent = colors.sunny,
                                title = "Support duit. ☕",
                                subtitle = "Donations temporarily unavailable · 100% optional",
                                onClick = { showSupport = true }
                            )
                        }
                        SettingsGroupedRowItem(
                            icon = Icons.Rounded.AutoStories,
                            accent = colors.sunny,
                            title = "Getting Started & Feature Guide",
                            subtitle = "Learn how to use duit. · Replay tour",
                            onClick = { onSelectRoute(SettingsHubRoute.FEATURE_GUIDE) }
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
        } else {
            // Personal edition: unchanged layout.
            // Public edition only. Turning it off hides commission tools; no record is ever deleted.
            if (com.diniy.duit.BuildConfig.OPTIONAL_COMMISSION) {
                item(key = "group_optional_features") {
                    com.diniy.duit.core.ui.components.SpatialGlassSection(title = "Optional Features") {
                        SettingsSwitchRow(
                            title = "Commission Tracking",
                            subtitle = if (app.commissionFeatureEnabled) {
                                "Sales, commission and payouts in Planning, Quick Add and Insights."
                            } else {
                                "Off. Turn on to track sales and commission. Any saved records are kept."
                            },
                            checked = app.commissionFeatureEnabled,
                            onCheckedChange = onSetCommissionFeature
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
                        if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.AutoStories,
                                accent = colors.peach,
                                title = "Help Centre",
                                subtitle = "Quick guides for every part of duit.",
                                onClick = { onSelectRoute(SettingsHubRoute.HELP_CENTRE) }
                            )
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.Shield,
                                accent = colors.aqua,
                                title = "Privacy & Your Data",
                                subtitle = "What's stored, what's sent and how to delete it",
                                onClick = { onSelectRoute(SettingsHubRoute.PRIVACY_DATA) }
                            )
                        }
                        if (com.diniy.duit.BuildConfig.FEEDBACK_URL.isNotEmpty()) {
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.Forum,
                                accent = colors.coral,
                                title = "Help & Feedback",
                                subtitle = "Report a bug, suggest a feature or say hi",
                                onClick = { showFeedback = true }
                            )
                        }
                        if (supportAvailable) {
                            SettingsGroupedRowItem(
                                icon = Icons.Rounded.LocalCafe,
                                accent = colors.sunny,
                                title = "Support duit. ☕",
                                subtitle = "Donations temporarily unavailable · 100% optional",
                                onClick = { showSupport = true }
                            )
                        }
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
        if (showOwnerSection) {
            item(key = "group_developer") {
                com.diniy.duit.devcenter.DeveloperCenterEntryPoint.SettingsDeveloperSection(
                    onOpenDeveloperCenter = onOpenDeveloperCenter
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsScreen(
    uiState: SettingsHubUiState,
    onBack: () -> Unit,
    onSelectVisualTheme: (com.diniy.duit.domain.model.VisualTheme) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectGlassIntensity: (GlassIntensity) -> Unit,
    onOpenMotion: () -> Unit = {},
    onOpenHomeLayout: () -> Unit = {}
) {
    val settings = uiState.appSettings
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val visibleCount = DashboardSectionKey.entries.size - settings.hiddenDashboardSections.size

    SettingsPageScaffold(
        title = "Appearance",
        subtitle = "Design theme, color mode & Home layout",
        onBack = onBack
    ) {
        item(key = "visual_theme") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLiquid = settings.visualTheme == com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
                    Box(
                        modifier = Modifier
                            .size(if (isLiquid) 38.dp else 40.dp)
                            .rotate(if (isLiquid) 0f else -6f)
                            .clip(RoundedCornerShape(if (isLiquid) 12.dp else 14.dp))
                            .background(if (isLiquid) colors.primaryAction.copy(alpha = if (colors.isDark) 0.16f else 0.10f) else colors.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = if (isLiquid) colors.primaryAction else colors.onCoral,
                            modifier = Modifier
                                .size(if (isLiquid) 19.dp else 20.dp)
                                .rotate(if (isLiquid) 0f else 6f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Design Theme", style = typography.cardTitle, color = colors.primaryText)
                        Text(
                            text = settings.visualTheme.summary,
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.diniy.duit.domain.model.VisualTheme.entries.forEach { style ->
                        com.diniy.duit.core.ui.components.SpatialGlassPill(
                            label = style.label,
                            modifier = Modifier.weight(1f),
                            selected = settings.visualTheme == style,
                            onClick = { onSelectVisualTheme(style) }
                        )
                    }
                }
            }
        }

        item(key = "theme_mode") {
            com.diniy.duit.core.ui.components.SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Color Mode", style = typography.cardTitle, color = colors.primaryText)
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

        if (settings.visualTheme == com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS) {
            item(key = "glass_intensity") {
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Glass Intensity", style = typography.cardTitle, color = colors.primaryText)
                        Text(
                            text = settings.glassIntensity.summary,
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassIntensity.entries.forEach { level ->
                            com.diniy.duit.core.ui.components.SpatialGlassPill(
                                label = level.label,
                                modifier = Modifier.weight(1f),
                                selected = settings.glassIntensity == level,
                                onClick = { onSelectGlassIntensity(level) }
                            )
                        }
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
                        Switch(checked = visible, onCheckedChange = { onSetVisible(section, it) }, colors = com.diniy.duit.core.ui.components.candySwitchColors())
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
            if (!settings.commissionFeatureEnabled) return@item // public edition with Commission Tracking off
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
internal fun AboutDuitScreen(onBack: () -> Unit, onOpenOwnerMode: () -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf(AboutPage.MAIN) }
    when (page) {
        AboutPage.MAIN -> AboutMainPage(
            onBack = onBack,
            onOpenPrivacy = { page = AboutPage.PRIVACY },
            onOpenLicenses = { page = AboutPage.LICENSES },
            onOpenOwnerMode = onOpenOwnerMode
        )
        AboutPage.PRIVACY -> AboutPrivacyPage(onBack = { page = AboutPage.MAIN })
        AboutPage.LICENSES -> AboutLicensesPage(onBack = { page = AboutPage.MAIN })
    }
}

@Composable
private fun AboutMainPage(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenOwnerMode: () -> Unit = {}
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    val context = LocalContext.current
    // Version comes from the installed package (build metadata), never hardcoded.
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var logoTapCount by remember { mutableStateOf(0) }
    var lastLogoTapMs by remember { mutableStateOf(0L) }

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
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (com.diniy.duit.devcenter.DeveloperCenterEntryPoint.isAvailable) {
                                val now = android.os.SystemClock.elapsedRealtime()
                                val nextCount = if (now - lastLogoTapMs <= 2_500L) logoTapCount + 1 else 1
                                lastLogoTapMs = now
                                if (nextCount >= 7) {
                                    logoTapCount = 0
                                    onOpenOwnerMode()
                                } else {
                                    logoTapCount = nextCount
                                }
                            }
                        }
                )
                Spacer(Modifier.height(14.dp))
                val isLiquidAbout = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
                    com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append("duit")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = if (isLiquidAbout) colors.primaryAction else colors.coral)) { append(".") }
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

        // Beta builds show update status and a manual check here; other builds render nothing.
        item(key = "app_updates") { com.diniy.duit.update.EditionUpdateSettingsCard() }

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
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    com.diniy.duit.core.ui.components.SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isLiquidGlass) 20.dp else 26.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .rotate(if (isLiquidGlass) 0f else -6f)
                    .clip(RoundedCornerShape(if (isLiquidGlass) 12.dp else 13.dp))
                    .background(if (isLiquidGlass) accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f) else accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isLiquidGlass) colors.readableOn(accent) else colors.onCoral,
                    modifier = Modifier.size(19.dp).rotate(if (isLiquidGlass) 0f else 6f)
                )
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
                com.diniy.duit.devcenter.DeveloperCenterEntryPoint.privacySummaryOrNull(com.diniy.duit.BuildConfig.FCM_ENABLED)
                    ?: if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                        "Your wallets, transactions, plans and saved receipts are stored only on this phone. duit. goes online only to check for app updates, receive developer broadcast announcements, send feedback you choose to submit, and send optional crash reports or anonymous feature counts only if you opt in. Advertising IDs are disabled and your financial records are never sent."
                    } else {
                        "Your wallets, transactions, plans and saved receipts are stored only on this phone. duit. doesn't ask for internet access, so your records aren't sent anywhere."
                    }
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

internal data class LicenseNotice(val name: String, val copyright: String, val license: String)

// Only components shipped in the app. Font notices are copied from the font files' own metadata.
private val BaseLicenseNotices = listOf(
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

internal fun activeLicenseNotices(
    publicEdition: Boolean = com.diniy.duit.BuildConfig.FCM_ENABLED
): List<LicenseNotice> =
    if (publicEdition) {
        BaseLicenseNotices + listOf(
            LicenseNotice(
                "Firebase Analytics SDK",
                "Copyright Google LLC. Bundled strictly for optional opt-in anonymous feature analytics (off by default, ad IDs disabled).",
                "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"
            ),
            LicenseNotice(
                "Firebase Cloud Messaging SDK",
                "Copyright Google LLC. Bundled only in public builds to deliver optional Developer Broadcast announcements.",
                "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"
            ),
            LicenseNotice(
                "Firebase Crashlytics SDK",
                "Copyright Google LLC. Bundled strictly for optional opt-in crash and ANR diagnostics (zero financial data).",
                "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"
            )
        )
    } else {
        BaseLicenseNotices
    }

@Composable
internal fun AboutLicensesPage(onBack: () -> Unit) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
    SettingsPageScaffold(
        title = "Open-Source Licenses",
        subtitle = "Third-party acknowledgements",
        onBack = onBack
    ) {
        activeLicenseNotices().forEach { notice ->
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
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(if (isLiquidGlass) 14.dp else 20.dp))
            .candyPressable(onClick = onClick, onClickLabel = "Open $title")
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLiquidGlass) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.readableOn(accent),
                    modifier = Modifier.size(19.dp)
                )
            }
        } else {
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
        }
        Column(modifier = Modifier.weight(1f).padding(end = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) 8.dp else 0.dp)) {
            Text(
                text = title,
                style = typography.transactionDescription,
                color = colors.primaryText
            )
            // Public edition: a little air between title and subtitle, and room before the chevron.
            if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = typography.caption,
                color = colors.secondaryText
            )
        }
        if (isLiquidGlass) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.secondaryText.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        } else {
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
    val haptic = com.diniy.duit.core.ui.components.rememberDuitHaptic()
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { next ->
                // Public edition: a light tick on toggle (uses the setting as it was before this tap).
                if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn.takeIf { next } ?: androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
                onCheckedChange(next)
            })
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLiquidGlass) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.readableOn(accent), modifier = Modifier.size(19.dp))
            }
        } else {
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
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.transactionDescription, color = colors.primaryText)
            if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) Spacer(Modifier.height(2.dp))
            Text(subtitle, style = typography.caption, color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles (single, accessible target)
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

/**
 * Settings > Privacy & Your Data (public edition). Every statement here matches the code:
 * Room/DataStore and Receipt Vault private files on device with Android backup off; AES-256-GCM
 * password backups via the system file picker; PIN kept as a salted PBKDF2 value sealed by Android
 * Keystore; FLAG_SECURE and masked widgets/notifications while App Lock is on; feedback fields as
 * sent by FeedbackClient; FCM topic broadcasts without analytics; Support donations unavailable.
 */
@Composable
internal fun PrivacyDataScreen(
    onBack: () -> Unit,
    onOpenRoute: (SettingsHubRoute) -> Unit,
    crashReportingEnabled: Boolean = false,
    onSetCrashReporting: (Boolean) -> Unit = {},
    usageAnalyticsEnabled: Boolean = false,
    onSetUsageAnalytics: (Boolean) -> Unit = {}
) {
    val colors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    if (showSummary) {
        androidx.activity.compose.BackHandler { showSummary = false }
        AboutPrivacyPage(onBack = { showSummary = false })
        return
    }
    if (showFeedback) com.diniy.duit.feedback.HelpFeedbackScreen(onClose = { showFeedback = false })
    SettingsPageScaffold(
        title = "Privacy & Your Data",
        subtitle = "Plain answers about what duit. keeps and shares",
        onBack = onBack
    ) {
        item(key = "where") {
            AboutInfoCard(
                Icons.Rounded.Storage, colors.aqua, "Where your data lives",
                "Wallets, transactions, budgets, goals, settings and Receipt Vault files are saved in duit.'s private storage on this phone. " +
                    "There's no account and no cloud sync. On a standard phone, Android keeps this storage private from other apps. " +
                    "Android's automatic cloud backup is turned off for duit., so your records don't leave your phone that way."
            )
        }
        item(key = "backup") {
            AboutInfoCard(
                Icons.Rounded.Backup, colors.skyBlue, "Backup & restore",
                "Backups are files you create and save wherever you choose. Each one is encrypted (AES-256-GCM) with a password you set " +
                    "and can include saved Receipt Vault attachments (up to 10 MB per file, 12 MB total per backup). " +
                    "duit. can't recover a lost password. Restoring validates the password and staged data first, so if anything fails your current records stay untouched. " +
                    "Restoring doesn't change your PIN. CSV exports are plain spreadsheets and are not encrypted."
            )
        }
        item(key = "lock") {
            AboutInfoCard(
                Icons.Rounded.Shield, colors.coral, "PIN & fingerprint",
                "With App Lock on, duit. asks for your 6-digit PIN or fingerprint when you open it. Your PIN is never stored as plain text; " +
                    "only a salted, scrambled value is kept, protected by Android's secure keystore. While App Lock is on, duit. also hides its screen " +
                    "in recent apps, blocks screenshots, and hides amounts in widgets and notifications."
            )
        }
        item(key = "feedback") {
            AboutInfoCard(
                Icons.Rounded.Forum, colors.peach, "When you send feedback",
                "Only what you choose to send: your message, the category, the app version, your email if you add one, and a random ID made for this install " +
                    "to limit spam (it isn't saved with your message). It goes to the developer through Google Apps Script and is kept in a private Google Sheet, " +
                    "so Google's own terms apply. Your balances, transactions and backups are never included."
            )
        }
        item(key = "updates") {
            AboutInfoCard(
                Icons.Rounded.SystemUpdate, colors.aqua, "Update checks",
                "duit. checks GitHub for new versions. Like visiting any website, GitHub can see your phone's internet address, " +
                    "but duit. sends no personal or financial information."
            )
        }
        item(key = "broadcasts") {
            AboutInfoCard(
                Icons.Rounded.Notifications, colors.skyBlue, "Developer broadcasts",
                "Optional release and maintenance announcements use Firebase Cloud Messaging topic subscriptions with advertising IDs disabled. " +
                    "Public builds listen only to duit.'s active announcement channel without sending personal or financial records."
            )
        }
        if (com.diniy.duit.BuildConfig.CRASHLYTICS_ENABLED) {
            item(key = "crash_reports") {
                val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("Crash reports", style = typography.cardTitle, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (crashReportingEnabled) {
                            "Status: On — sends crash traces after restart."
                        } else {
                            "Status: Off — no reports collected."
                        },
                        style = typography.caption,
                        color = if (crashReportingEnabled) colors.readableOn(colors.aqua) else colors.secondaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = crashReportingEnabled,
                                role = Role.Switch,
                                onValueChange = onSetCrashReporting
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Crash reporting",
                                style = typography.transactionDescription,
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Shares stack traces and app version via Firebase Crashlytics (Google). Never includes balances, transactions, receipts, names, emails, or PINs. Turning off deletes unsent reports.",
                                style = typography.caption,
                                color = colors.secondaryText
                            )
                        }
                        Switch(
                            checked = crashReportingEnabled,
                            onCheckedChange = null,
                            colors = com.diniy.duit.core.ui.components.candySwitchColors(
                                personal = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedTrackColor = colors.coral,
                                    checkedThumbColor = Color.White,
                                    checkedBorderColor = colors.sticker,
                                    uncheckedTrackColor = colors.baseSurface,
                                    uncheckedThumbColor = colors.secondaryText,
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        )
                    }
                }
            }
        }
        if (com.diniy.duit.BuildConfig.ANALYTICS_ENABLED) {
            item(key = "usage_analytics") {
                val typography = com.diniy.duit.core.ui.theme.SpatialTheme.typography
                com.diniy.duit.core.ui.components.SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("Usage analytics", style = typography.cardTitle, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (usageAnalyticsEnabled) {
                            "Status: On — anonymous feature counts."
                        } else {
                            "Status: Off — no analytics collected."
                        },
                        style = typography.caption,
                        color = if (usageAnalyticsEnabled) colors.readableOn(colors.aqua) else colors.secondaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = usageAnalyticsEnabled,
                                role = Role.Switch,
                                onValueChange = onSetUsageAnalytics
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Usage Analytics",
                                style = typography.transactionDescription,
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Shares anonymous feature counts via Firebase Analytics (Google). Never collects balances, transactions, receipts, names, emails, or ad IDs. Turning off clears analytics data.",
                                style = typography.caption,
                                color = colors.secondaryText
                            )
                        }
                        Switch(
                            checked = usageAnalyticsEnabled,
                            onCheckedChange = null,
                            colors = com.diniy.duit.core.ui.components.candySwitchColors(
                                personal = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedTrackColor = colors.coral,
                                    checkedThumbColor = Color.White,
                                    checkedBorderColor = colors.sticker,
                                    uncheckedTrackColor = colors.baseSurface,
                                    uncheckedThumbColor = colors.secondaryText,
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        )
                    }
                }
            }
        }
        item(key = "support") {
            AboutInfoCard(
                Icons.Rounded.LocalCafe, colors.sunny, "Supporting duit.",
                "Donations in Support duit. are temporarily unavailable. No payment code or payment details are included, " +
                    "and duit. stays completely free with zero payment tracking."
            )
        }
        item(key = "delete") {
            AboutInfoCard(
                Icons.Rounded.DeleteSweep, colors.lavender, "Managing & deleting your data",
                "Export or delete your records in Data Management. Deleting all financial data clears your records and private Receipt Vault files after you confirm and can't be undone; " +
                    "your PIN and app settings are kept. Uninstalling duit. removes everything it stored on this phone. Backups and CSV files you saved elsewhere " +
                    "stay until you delete them. To remove feedback you've sent, ask through Help & Feedback."
            )
        }
        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrismaticButton(text = "Privacy & Security summary", onClick = { showSummary = true }, variant = PrismaticButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
                val context = androidx.compose.ui.platform.LocalContext.current
                PrismaticButton(
                    text = "Read the full Privacy Policy",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (com.diniy.duit.BuildConfig.FEEDBACK_URL.isNotEmpty()) {
                    PrismaticButton(text = "Help & Feedback", onClick = { showFeedback = true }, variant = PrismaticButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrismaticButton(text = "Backup & Restore", onClick = { onOpenRoute(SettingsHubRoute.BACKUP_RESTORE) }, variant = PrismaticButtonVariant.Ghost, modifier = Modifier.weight(1f))
                    PrismaticButton(text = "Data Management", onClick = { onOpenRoute(SettingsHubRoute.DATA_MANAGEMENT) }, variant = PrismaticButtonVariant.Ghost, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Public Privacy Policy (published with the release on the distribution repository). */
internal const val PRIVACY_POLICY_URL = "https://github.com/erlingdiniy/duit-beta/blob/main/PRIVACY.md"

/** "1 record", "2 records". */
internal fun recordCountLabel(count: Int): String = countLabel(count, "record", "records")

/** "1 wallet", "0 wallets", "3 wallets". */
internal fun countLabel(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"
