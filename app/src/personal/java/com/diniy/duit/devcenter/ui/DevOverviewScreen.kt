package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.AnalyticsDateRange
import com.diniy.duit.devcenter.AnalyticsSourceState
import com.diniy.duit.devcenter.DevCenterSection
import com.diniy.duit.devcenter.DevCenterUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevOverviewScreen(
    uiState: DevCenterUiState,
    onSelectSection: (DevCenterSection) -> Unit,
    onSelectDateRange: (AnalyticsDateRange) -> Unit,
    onTriggerSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    val dash = uiState.dashboard
    val health = dash.health
    val gaReady = (uiState.analyticsState as? AnalyticsSourceState.Ready)?.snapshot
    val isEnrolled = uiState.vault.isBackendEnrolled
    val hasOAuth = isEnrolled ||
        !uiState.vault.encryptedAnalyticsToken.isNullOrBlank() ||
        !uiState.vault.encryptedAnalyticsRefreshToken.isNullOrBlank()

    val services = uiState.infraSnapshot.services
    val healthyCount = services.count { it.state == "healthy" }
    val issueCount = services.count { it.state == "error" || it.state == "warning" }

    val statusTitle = when {
        uiState.isRefreshing -> "Syncing Telemetry"
        health.connected && isEnrolled -> "Mission Control Live"
        health.connected -> "GitHub Connected"
        gaReady?.isCached == true -> "Cached Snapshot"
        isEnrolled -> "Worker Offline"
        else -> "Setup Required"
    }
    val statusDotColor = when {
        uiState.errorBanner != null -> tokens.dangerTint
        uiState.isRefreshing || gaReady?.isCached == true -> tokens.warningTint
        health.connected -> tokens.healthyTint
        else -> tokens.warningTint
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = devListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "ov_sync_status") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (health.connected) tokens.healthyTint else tokens.warningTint
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText
                        )
                    }
                    if (isEnrolled) {
                        DevToneBadge(label = "Hardware Key", tone = "healthy")
                    }
                    if (gaReady?.isCached == true) {
                        DevToneBadge(label = "Cached", tone = "cached")
                    }
                }
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(
                    label = "Last Sync",
                    value = formatIsoShort(uiState.vault.lastFullSyncIso ?: health.checkedAtIso)
                )
                Spacer(Modifier.height(8.dp))
                DevSegmentedBar(
                    items = AnalyticsDateRange.entries,
                    selected = uiState.analyticsDateRange,
                    labelOf = { it.label },
                    onSelect = onSelectDateRange
                )
                if (!health.connected || !hasOAuth) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Retry Sync",
                            onClick = onTriggerSync,
                            enabled = !uiState.isRefreshing,
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (!hasOAuth) {
                            PrismaticButton(
                                text = "Settings",
                                onClick = { onSelectSection(DevCenterSection.SETTINGS) },
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item(key = "ov_users_kpis") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMetricTile(
                    title = "Observed Users",
                    value = gaReady?.totalUsers?.toString() ?: "—",
                    sub = gaReady?.let { "DAU ${it.dau}, WAU ${it.wau}" } ?: "Opted-in telemetry",
                    icon = Icons.Rounded.People,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                            onClickLabel = "Open Analytics"
                        )
                )
                DevMetricTile(
                    title = "New Users",
                    value = gaReady?.newUsers?.toString() ?: "—",
                    sub = gaReady?.let { "${it.returningRatePercent}% returning" }
                        ?: if (isEnrolled) "Awaiting cloud sync" else "Enroll owner device",
                    icon = Icons.Rounded.PersonAdd,
                    accentTint = tokens.secondaryAccent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                            onClickLabel = "Open Analytics"
                        )
                )
            }
        }

        item(key = "ov_quality_kpis") {
            val unread = uiState.unreadFeedbackCount
            val openBugs = uiState.openBugReportCount
            val openFeatures = uiState.openFeatureRequestCount
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMetricTile(
                    title = "Crash-Free",
                    value = gaReady?.crashFreeUsersPercent?.let {
                        String.format(Locale.US, "%.2f%%", it)
                    } ?: "—",
                    sub = gaReady?.let { "${it.crashAffectedUsers} affected" } ?: "Not measured yet",
                    accentTint = tokens.healthyTint,
                    icon = Icons.Rounded.VerifiedUser,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.CRASH_REPORTS) },
                            onClickLabel = "Open Crash Reports"
                        )
                )
                DevMetricTile(
                    title = "Unread Inbox",
                    value = unread.toString(),
                    sub = "$openBugs bugs, $openFeatures ideas",
                    accentTint = if (unread > 0) tokens.warningTint else null,
                    icon = Icons.Rounded.MarkEmailUnread,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.FEEDBACK) },
                            onClickLabel = "Open Inbox"
                        )
                )
            }
        }

        item(key = "ov_growth_chart") {
            when (val aState = uiState.analyticsState) {
                AnalyticsSourceState.Loading -> {
                    DevLoadingSkeletonCard(lines = 3)
                }
                is AnalyticsSourceState.Error -> {
                    DevErrorStateCard(
                        title = "Activity Unavailable",
                        message = aState.message,
                        primaryLabel = "Retry",
                        onPrimary = onTriggerSync,
                        secondaryLabel = "Analytics",
                        onSecondary = { onSelectSection(DevCenterSection.ANALYTICS) }
                    )
                }
                is AnalyticsSourceState.Ready if aState.snapshot.growthTrend.isNotEmpty() -> {
                    SpatialGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                            .candyPressable(
                                onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                                onClickLabel = "Open Analytics"
                            ),
                        tier = SpatialGlassTier.Standard
                    ) {
                        DevSectionHeader(
                            title = "User Activity (${aState.snapshot.dateRange.label})",
                            trailingLabel = "Analytics",
                            onTrailingClick = { onSelectSection(DevCenterSection.ANALYTICS) }
                        )
                        Spacer(Modifier.height(8.dp))
                        DevSparklineChart(
                            labels = aState.snapshot.growthTrend.map { it.dateLabel },
                            values = aState.snapshot.growthTrend.map { it.activeUsers.toFloat() },
                            unitSuffix = " users"
                        )
                    }
                }
                else -> {
                    DevEmptyStateCard(
                        icon = Icons.Rounded.QueryStats,
                        title = "No Activity Trend Yet",
                        subtitle = if (isEnrolled) {
                            "Awaiting scheduled keyless WIF analytics sync."
                        } else {
                            "Connect Google Analytics in Settings."
                        },
                        actionLabel = if (isEnrolled) "Sync Cloud" else "Open Settings",
                        onAction = {
                            if (isEnrolled) onTriggerSync() else onSelectSection(DevCenterSection.SETTINGS)
                        }
                    )
                }
            }
        }

        item(key = "ov_quick_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DevSectionTitle(
                    title = "Quick Access",
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevQuickActionTile(
                        icon = Icons.Rounded.Campaign,
                        label = "Broadcast",
                        subtitle = "Send or schedule",
                        accentTint = tokens.primaryAccent,
                        onClick = { onSelectSection(DevCenterSection.NOTIFICATIONS) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    DevQuickActionTile(
                        icon = Icons.Rounded.NewReleases,
                        label = "Releases",
                        subtitle = dash.latestStable?.version?.let { "Stable $it" }
                            ?: dash.latestBeta?.version?.let { "Beta $it" }
                            ?: "Build ledger",
                        accentTint = tokens.secondaryAccent,
                        onClick = { onSelectSection(DevCenterSection.RELEASES) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevQuickActionTile(
                        icon = Icons.Rounded.MonitorHeart,
                        label = "System Health",
                        subtitle = if (services.isNotEmpty()) "$healthyCount/${services.size} healthy" else "Run checks",
                        accentTint = if (issueCount == 0) tokens.healthyTint else tokens.warningTint,
                        onClick = { onSelectSection(DevCenterSection.INFRASTRUCTURE) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    DevQuickActionTile(
                        icon = Icons.Rounded.Sync,
                        label = "Sync Cloud",
                        subtitle = if (uiState.isRefreshing) "Refreshing..." else "Update snapshot",
                        accentTint = tokens.healthyTint,
                        onClick = onTriggerSync,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        item(key = "ov_releases_summary") {
            SpatialGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                    .candyPressable(
                        onClick = { onSelectSection(DevCenterSection.RELEASES) },
                        onClickLabel = "Open Releases"
                    ),
                tier = SpatialGlassTier.Standard
            ) {
                DevSectionHeader(
                    title = "Releases",
                    trailingLabel = "View All",
                    onTrailingClick = { onSelectSection(DevCenterSection.RELEASES) }
                )
                Spacer(Modifier.height(4.dp))
                DevKeyValueRow(label = "Latest Stable", value = dash.latestStable?.version ?: "—")
                DevKeyValueRow(label = "Latest Beta", value = dash.latestBeta?.version ?: "—")
                DevKeyValueRow(label = "Private Build", value = dash.latestPrivateDev?.version ?: "—")
                DevKeyValueRow(label = "Total Downloads", value = dash.totalApkDownloads.toString())
                val topVer = gaReady?.appVersions?.firstOrNull()
                if (topVer != null && gaReady.totalUsers > 0L) {
                    val share = ((topVer.users * 100L) / gaReady.totalUsers).coerceIn(0L, 100L)
                    DevKeyValueRow(label = "Top Version", value = "v${topVer.label} ($share%)")
                }
            }
        }

        item(key = "ov_recent_activity") {
            SpatialGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
                    .candyPressable(
                        onClick = { onSelectSection(DevCenterSection.AUDIT_LOGS) },
                        onClickLabel = "Open Audit Logs"
                    ),
                tier = SpatialGlassTier.Standard
            ) {
                DevSectionHeader(
                    title = "Recent Activity",
                    trailingLabel = "Audit Logs",
                    onTrailingClick = { onSelectSection(DevCenterSection.AUDIT_LOGS) }
                )
                Spacer(Modifier.height(6.dp))
                val recents = uiState.combinedAuditLog.take(4)
                if (recents.isEmpty()) {
                    Text(
                        text = "No developer actions recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recents.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = entry.action,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.summary}, ${formatIsoShort(entry.timestampIso)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DevToneBadge(label = entry.status, tone = entry.status)
                            }
                        }
                    }
                }
            }
        }
    }
}
