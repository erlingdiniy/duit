package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun DevOverviewScreen(
    uiState: DevCenterUiState,
    onSelectSection: (DevCenterSection) -> Unit,
    onSelectDateRange: (AnalyticsDateRange) -> Unit,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "ov_sync_status") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (health.connected) tokens.healthyTint else tokens.warningTint
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Overview")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isEnrolled) {
                            DevToneBadge(
                                label = if (health.connected) "Mission Control Live" else "Syncing",
                                tone = if (health.connected) "healthy" else "cached"
                            )
                            DevToneBadge(label = "Hardware Key", tone = "healthy")
                        } else {
                            DevToneBadge(
                                label = if (health.connected) "GitHub Live" else "GitHub Unset",
                                tone = if (health.connected) "healthy" else "warning"
                            )
                            DevToneBadge(
                                label = if (hasOAuth) "Google Linked" else "Google Unset",
                                tone = if (hasOAuth) "healthy" else "warning"
                            )
                        }
                        if (gaReady?.isCached == true) {
                            DevToneBadge(label = "Cached", tone = "cached")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(
                    label = "Updated",
                    value = formatIsoShort(uiState.vault.lastFullSyncIso ?: health.checkedAtIso)
                )
                Spacer(Modifier.height(6.dp))
                DevChoiceChipRow(
                    items = AnalyticsDateRange.entries,
                    selected = uiState.analyticsDateRange,
                    labelOf = { it.label },
                    onSelect = onSelectDateRange
                )
                Spacer(Modifier.height(8.dp))
                PrismaticButton(
                    text = if (!health.connected || !hasOAuth) "Open Settings" else "Broadcast",
                    onClick = {
                        onSelectSection(
                            if (!health.connected || !hasOAuth) DevCenterSection.SETTINGS
                            else DevCenterSection.NOTIFICATIONS
                        )
                    },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "ov_users_kpis") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMetricTile(
                    title = "Observed Users",
                    value = gaReady?.totalUsers?.toString() ?: "—",
                    sub = gaReady?.let { "Daily ${it.dau}, Weekly ${it.wau}, Monthly ${it.mau}" } ?: "Opted-in analytics",
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                            onClickLabel = "Open Analytics"
                        )
                )
                DevMetricTile(
                    title = "New Users",
                    value = gaReady?.newUsers?.toString() ?: "—",
                    sub = gaReady?.let { "${it.returningRatePercent}% returning" } ?: "Awaiting Google sign-in",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "ov_quality_kpis") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMetricTile(
                    title = "Crash-Free Users",
                    value = gaReady?.crashFreeUsersPercent?.let {
                        String.format(Locale.US, "%.2f%%", it)
                    } ?: "—",
                    sub = gaReady?.let { "${it.crashAffectedUsers} affected" } ?: "Not measured yet",
                    accentTint = tokens.healthyTint,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.CRASH_REPORTS) },
                            onClickLabel = "Open Crash Reports"
                        )
                )
                    val unread = uiState.unreadFeedbackCount
                val openBugs = uiState.openBugReportCount
                val openFeatures = uiState.openFeatureRequestCount
                DevMetricTile(
                    title = "Unread Feedback",
                    value = unread.toString(),
                    sub = "$openBugs open bugs, $openFeatures features",
                    accentTint = if (unread > 0) tokens.warningTint else null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.FEEDBACK) },
                            onClickLabel = "Open Feedback"
                        )
                )
            }
        }

        item(key = "ov_growth_chart") {
            if (gaReady != null && gaReady.growthTrend.isNotEmpty()) {
                SpatialGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                            onClickLabel = "Open Analytics"
                        ),
                    tier = SpatialGlassTier.Standard
                ) {
                    DevSectionTitle("User Growth (${gaReady.dateRange.label})")
                    Spacer(Modifier.height(6.dp))
                    DevSparklineChart(
                        labels = gaReady.growthTrend.map { it.dateLabel },
                        values = gaReady.growthTrend.map { it.activeUsers.toFloat() },
                        unitSuffix = " users"
                    )
                }
            } else {
                SpatialGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .candyPressable(
                            onClick = { onSelectSection(DevCenterSection.ANALYTICS) },
                            onClickLabel = "Open Analytics"
                        ),
                    tier = SpatialGlassTier.Standard,
                    accentTint = colors.warning
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevSectionTitle("User Growth")
                        DevToneBadge(label = "Unavailable", tone = "warning")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnrolled) "Awaiting scheduled keyless WIF analytics sync." else "Connect Google Analytics in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }
        }

        item(key = "ov_releases_summary") {
            SpatialGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .candyPressable(
                        onClick = { onSelectSection(DevCenterSection.RELEASES) },
                        onClickLabel = "Open Releases"
                    ),
                tier = SpatialGlassTier.Standard
            ) {
                DevSectionTitle("Releases")
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(label = "Latest Stable", value = dash.latestStable?.version ?: "—")
                DevKeyValueRow(label = "Latest Beta", value = dash.latestBeta?.version ?: "—")
                DevKeyValueRow(label = "Private Build", value = dash.latestPrivateDev?.version ?: "—")
                DevKeyValueRow(label = "App Downloads", value = dash.totalApkDownloads.toString())
                val topVer = gaReady?.appVersions?.firstOrNull()
                if (topVer != null && gaReady.totalUsers > 0L) {
                    val share = ((topVer.users * 100L) / gaReady.totalUsers).coerceIn(0L, 100L)
                    DevKeyValueRow(label = "Top Version Share", value = "${topVer.label} ($share%)")
                }
            }
        }

        item(key = "ov_infra_summary") {
            val services = uiState.infraSnapshot.services
            val healthy = services.count { it.state == "healthy" }
            val warnOrErr = services.count { it.state == "warning" || it.state == "error" }
            SpatialGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .candyPressable(
                        onClick = { onSelectSection(DevCenterSection.INFRASTRUCTURE) },
                        onClickLabel = "Open Infrastructure"
                    ),
                tier = SpatialGlassTier.Standard
            ) {
                DevSectionTitle("Service Status")
                Spacer(Modifier.height(4.dp))
                DevKeyValueRow(label = "Healthy Checks", value = "$healthy / ${services.size}")
                DevKeyValueRow(label = "Alerts", value = warnOrErr.toString())
            }
        }

        item(key = "ov_recent_activity") {
            SpatialGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .candyPressable(
                        onClick = { onSelectSection(DevCenterSection.AUDIT_LOGS) },
                        onClickLabel = "Open Audit Logs"
                    ),
                tier = SpatialGlassTier.Standard
            ) {
                DevSectionTitle("Recent Activity")
                Spacer(Modifier.height(6.dp))
                val recents = uiState.combinedAuditLog.take(4)
                if (recents.isEmpty()) {
                    Text(
                        text = "No activity recorded yet.",
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
