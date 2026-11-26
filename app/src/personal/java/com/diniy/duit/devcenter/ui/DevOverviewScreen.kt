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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.diagnostics.CrashReporting

private const val CRASHLYTICS_CONSOLE_URL =
    "https://console.firebase.google.com/project/duit-beta/crashlytics/app/android:com.diniy.duit.beta/issues"

@Composable
fun DevOverviewScreen(
    uiState: DevCenterUiState,
    onOpenNotifications: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val dash = uiState.dashboard
    val health = dash.health

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "health_card") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (health.connected) colors.success else colors.warning
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Health",
                        style = typography.cardTitle,
                        color = colors.primaryText
                    )
                    DevToneBadge(
                        label = if (health.connected) "Live" else "Setup Needed",
                        tone = if (health.connected) "healthy" else "warning"
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = health.statusSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Spacer(Modifier.height(8.dp))
                DevKeyValueRow(
                    label = "GitHub PAT",
                    value = uiState.vault.githubPatHint
                )
                health.rateLimitRemaining?.let { limit ->
                    DevKeyValueRow(label = "API Quota Left", value = "$limit req/hr")
                }
                if (!health.connected) {
                    Spacer(Modifier.height(10.dp))
                    PrismaticButton(
                        text = "Connect GitHub",
                        onClick = onOpenSettings,
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "metrics_row_1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Latest Stable",
                    value = dash.latestStable?.version ?: "—",
                    sub = "${dash.latestStable?.downloadCount ?: 0} downloads",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Latest Beta",
                    value = dash.latestBeta?.version ?: "—",
                    sub = "${dash.latestBeta?.downloadCount ?: 0} downloads",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "metrics_row_2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "APK Downloads",
                    value = dash.totalApkDownloads.toString(),
                    sub = "${dash.releases.size} tracked releases",
                    modifier = Modifier.weight(1f)
                )
                val autoLabel = when (dash.killSwitches.releaseAutomationEnabled) {
                    true -> "Auto: ON"
                    false -> "Auto: PAUSED"
                    null -> "Auto: Unknown"
                }
                val customLabel = when (dash.killSwitches.customBroadcastEnabled) {
                    true -> "Custom: ON"
                    false -> "Custom: PAUSED"
                    null -> "Custom: Sign In"
                }
                MetricTile(
                    title = "Automation",
                    value = autoLabel,
                    sub = customLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "quick_compose") {
            PrismaticButton(
                text = "Compose Broadcast",
                onClick = onOpenNotifications,
                variant = PrismaticButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (BuildConfig.CRASHLYTICS_ENABLED) {
            item(key = "crash_monitoring") {
                val context = LocalContext.current
                val optInEnabled = CrashReporting.isCollectionEnabled()
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevSectionTitle("Crash Monitoring")
                        DevToneBadge(
                            label = if (optInEnabled) "Opt-In ON" else "Opt-In OFF",
                            tone = if (optInEnabled) "healthy" else "paused"
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    DevKeyValueRow(label = "Channel", value = BuildConfig.RELEASE_CHANNEL)
                    DevKeyValueRow(label = "Version", value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    DevKeyValueRow(label = "Analytics", value = "Disabled")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Firebase Console shows crash-free users, issue lists, affected versions, and recent trends without extra billing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    PrismaticButton(
                        text = "Open Console",
                        onClick = { context.openHttpsUrl(CRASHLYTICS_CONSOLE_URL) },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "workflows_section") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Recent Workflows")
                    PrismaticButton(
                        text = "Releases",
                        onClick = onOpenReleases,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                Spacer(Modifier.height(6.dp))
                val runs = dash.workflowRuns.take(4)
                if (runs.isEmpty()) {
                    Text(
                        text = "No workflow runs loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        runs.forEach { run ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = run.workflowName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${run.repo.substringAfter('/')}, ${formatIsoShort(run.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText,
                                        maxLines = 1
                                    )
                                }
                                DevToneBadge(label = run.conclusion, tone = run.conclusion)
                            }
                        }
                    }
                }
            }
        }

        item(key = "broadcasts_section") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Recent Broadcasts")
                    PrismaticButton(
                        text = "History",
                        onClick = onOpenHistory,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                Spacer(Modifier.height(6.dp))
                val items = uiState.combinedBroadcastHistory.take(4)
                if (items.isEmpty()) {
                    Text(
                        text = "No broadcast records loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.audience}, ${entry.broadcastId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DevToneBadge(label = entry.statusLabel, tone = entry.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    SpatialGlassCard(
        modifier = modifier,
        tier = SpatialGlassTier.Standard,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryText,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
