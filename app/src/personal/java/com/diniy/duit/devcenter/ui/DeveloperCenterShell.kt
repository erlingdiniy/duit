package com.diniy.duit.devcenter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevAuthUiState
import com.diniy.duit.devcenter.DevCenterSection
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel

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

    uiState.pendingKillSwitchModal?.let { pending ->
        KillSwitchConfirmDialog(
            pending = pending,
            onDismiss = viewModel::dismissKillSwitchModal,
            onConfirm = viewModel::confirmKillSwitchToggle
        )
    }

    PrismaticBackground {
        Column(
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
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
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

            // Two unclipped rows of 3 equal-width chips so all 6 modules stay 1-tap reachable with full touch area.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DevCenterSection.entries.chunked(3).forEach { rowSections ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSections.forEach { section ->
                            val isSelected = section == uiState.activeSection
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectSection(section) },
                                label = {
                                    Text(
                                        text = section.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryAction.copy(alpha = 0.16f),
                                    selectedLabelColor = colors.primaryAction
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        }
                    }
                }
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

            when (uiState.activeSection) {
                DevCenterSection.OVERVIEW -> DevOverviewScreen(
                    uiState = uiState,
                    onOpenNotifications = { viewModel.selectSection(DevCenterSection.NOTIFICATIONS) },
                    onOpenHistory = { viewModel.selectSection(DevCenterSection.HISTORY) },
                    onOpenReleases = { viewModel.selectSection(DevCenterSection.RELEASES) },
                    onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
                DevCenterSection.NOTIFICATIONS -> DevNotificationsScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
                DevCenterSection.HISTORY -> DevHistoryScreen(
                    uiState = uiState,
                    onSelectFilter = viewModel::selectHistoryFilter,
                    modifier = Modifier.weight(1f)
                )
                DevCenterSection.RELEASES -> DevReleasesScreen(
                    uiState = uiState,
                    onSelectFilter = viewModel::selectReleaseFilter,
                    modifier = Modifier.weight(1f)
                )
                DevCenterSection.FEEDBACK -> DevFeedbackScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSettings = { viewModel.selectSection(DevCenterSection.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
                DevCenterSection.SETTINGS -> DevSettingsScreen(
                    uiState = uiState,
                    authState = authState,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
