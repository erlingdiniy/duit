package com.diniy.duit.devcenter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevAuthUiState
import com.diniy.duit.devcenter.DevCenterSection
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel

private fun iconForSection(section: DevCenterSection): ImageVector = when (section) {
    DevCenterSection.OVERVIEW -> Icons.Rounded.Dashboard
    DevCenterSection.ANALYTICS -> Icons.Rounded.Analytics
    DevCenterSection.FEEDBACK -> Icons.Rounded.Feedback
    DevCenterSection.CRASH_REPORTS -> Icons.Rounded.BugReport
    DevCenterSection.NOTIFICATIONS -> Icons.Rounded.Notifications
    DevCenterSection.RELEASES -> Icons.Rounded.NewReleases
    DevCenterSection.REMOTE_CONFIG -> Icons.Rounded.Build
    DevCenterSection.GITHUB -> Icons.Rounded.Code
    DevCenterSection.INFRASTRUCTURE -> Icons.Rounded.MonitorHeart
    DevCenterSection.AUDIT_LOGS -> Icons.Rounded.History
    DevCenterSection.SETTINGS -> Icons.Rounded.Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCenterShell(
    uiState: DevCenterUiState,
    authState: DevAuthUiState,
    viewModel: DevCenterViewModel,
    onBack: () -> Unit,
    onLockNow: () -> Unit
) {
    BackHandler {
        if (uiState.activeSection != DevCenterSection.OVERVIEW) {
            viewModel.selectSection(DevCenterSection.OVERVIEW)
        } else {
            onBack()
        }
    }

    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    var showModulesSheet by remember { mutableStateOf(false) }

    uiState.pendingKillSwitchModal?.let { pending ->
        KillSwitchConfirmDialog(
            pending = pending,
            onDismiss = viewModel::dismissKillSwitchModal,
            onConfirm = viewModel::confirmKillSwitchToggle
        )
    }

    if (showModulesSheet) {
        ModalBottomSheet(onDismissRequest = { showModulesSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mission Control Modules",
                    style = typography.sectionHeading,
                    color = colors.primaryText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                val rows = DevCenterSection.entries.chunked(2)
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { sec ->
                            val isSelected = sec == uiState.activeSection
                            SpatialGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .candyPressable(
                                        onClick = {
                                            viewModel.selectSection(sec)
                                            showModulesSheet = false
                                        },
                                        onClickLabel = "Open ${sec.label}"
                                    ),
                                tier = if (isSelected) SpatialGlassTier.Elevated else SpatialGlassTier.Standard,
                                accentTint = if (isSelected) colors.primaryAction else null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = iconForSection(sec),
                                        contentDescription = null,
                                        tint = if (isSelected) colors.primaryAction else colors.secondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sec.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) colors.primaryAction else colors.primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (sec == DevCenterSection.FEEDBACK && uiState.unreadFeedbackCount > 0) {
                                            Text(
                                                text = "${uiState.unreadFeedbackCount} unread",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.warning
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    PrismaticBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            viewModel.authManager.recordUserActivity()
                        }
                    }
                }
        ) {
            val isExpanded = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DevCenterSection.entries.forEach { section ->
                            val labelText = if (section == DevCenterSection.FEEDBACK && uiState.unreadFeedbackCount > 0) {
                                "${section.label} (${uiState.unreadFeedbackCount})"
                            } else {
                                section.label
                            }
                            NavigationRailItem(
                                selected = section == uiState.activeSection,
                                onClick = { viewModel.selectSection(section) },
                                icon = {
                                    Icon(
                                        imageVector = iconForSection(section),
                                        contentDescription = section.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = labelText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Settings",
                                tint = colors.primaryText
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                text = "Developer Center",
                                style = typography.screenHeading,
                                color = colors.primaryText,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        if (!isExpanded) {
                            IconButton(
                                onClick = { showModulesSheet = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = "All modules",
                                    tint = colors.primaryText
                                )
                            }
                        }
                        if (uiState.isRefreshing) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(
                                onClick = viewModel::refreshAll,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh status",
                                    tint = colors.primaryText
                                )
                            }
                        }
                        IconButton(
                            onClick = onLockNow,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Lock Developer Center",
                                tint = colors.primaryText
                            )
                        }
                    }

                    if (!isExpanded) {
                        SpatialGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .candyPressable(
                                    onClick = { showModulesSheet = true },
                                    onClickLabel = "Switch module"
                                ),
                            tier = SpatialGlassTier.Standard,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = iconForSection(uiState.activeSection),
                                    contentDescription = null,
                                    tint = colors.primaryAction,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Module: ${uiState.activeSection.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.unreadFeedbackCount > 0) {
                                    Text(
                                        text = "${uiState.unreadFeedbackCount} unread",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.warning
                                    )
                                }
                                Text(
                                    text = "Switch",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryAction
                                )
                            }
                        }
                    }

                    uiState.tokenExpirationNotice?.let { notice ->
                        DevStatusBanner(
                            message = notice,
                            isSuccess = false,
                            onDismiss = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    uiState.statusBanner?.let { banner ->
                        DevStatusBanner(
                            message = banner,
                            isSuccess = true,
                            onDismiss = viewModel::clearBanners,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    uiState.errorBanner?.let { err ->
                        DevStatusBanner(
                            message = err,
                            isSuccess = false,
                            onDismiss = viewModel::clearBanners,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = viewModel::refreshAll,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        when (uiState.activeSection) {
                            DevCenterSection.OVERVIEW -> DevOverviewScreen(
                                uiState = uiState,
                                onSelectSection = viewModel::selectSection,
                                onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.ANALYTICS -> DevAnalyticsScreen(
                                uiState = uiState,
                                onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                onRefresh = viewModel::refreshAnalytics,
                                onSignInWithGoogle = { launchUrl ->
                                    viewModel.startAnalyticsGoogleSignIn(onLaunchBrowser = launchUrl)
                                },
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.FEEDBACK -> DevFeedbackScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.CRASH_REPORTS -> DevCrashReportsScreen(
                                uiState = uiState,
                                onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.NOTIFICATIONS -> DevNotificationsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.RELEASES -> DevReleasesScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onSelectFilter = viewModel::selectReleaseFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.REMOTE_CONFIG -> DevRemoteConfigScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.GITHUB -> DevGitHubScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.INFRASTRUCTURE -> DevInfrastructureScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.AUDIT_LOGS -> DevAuditLogsScreen(
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize()
                            )
                            DevCenterSection.SETTINGS -> DevSettingsScreen(
                                uiState = uiState,
                                authState = authState,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
