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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.BuildConfig
import com.diniy.duit.analytics.UsageAnalytics
import com.diniy.duit.analytics.UsageAnalyticsPolicy
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.AnalyticsDateRange
import com.diniy.duit.devcenter.AnalyticsSourceState
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.EXPERIMENTAL_GA4_API_ENABLED

private const val ANALYTICS_OVERVIEW_URL =
    "https://console.firebase.google.com/project/duit-beta/analytics/app/android:com.diniy.duit.beta/overview"
private const val ANALYTICS_DEBUGVIEW_URL =
    "https://console.firebase.google.com/project/duit-beta/analytics/app/android:com.diniy.duit.beta/debugview"

@Composable
fun DevAnalyticsScreen(
    uiState: DevCenterUiState,
    onSelectDateRange: (AnalyticsDateRange) -> Unit,
    onRefresh: () -> Unit,
    onSignInWithGoogle: ((String) -> Unit) -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val context = LocalContext.current
    val optInEnabled = UsageAnalytics.isCollectionEnabled()
    val channel = UsageAnalyticsPolicy.resolveReleaseChannel(BuildConfig.VERSION_NAME, BuildConfig.PRIVATE_DEV_EDITION)
    val trafficType = UsageAnalyticsPolicy.resolveTrafficType(BuildConfig.PRIVATE_DEV_EDITION, BuildConfig.ANALYTICS_QA_TEST)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "analytics_status_card") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (optInEnabled) colors.success else colors.warning
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Usage Analytics")
                    DevToneBadge(
                        label = if (optInEnabled) "Opted in" else "Opted out",
                        tone = if (optInEnabled) "healthy" else "paused"
                    )
                }
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(label = "Channel", value = channel)
                DevKeyValueRow(label = "Traffic Marker", value = trafficType ?: "public")
                DevKeyValueRow(label = "Ad tracking", value = "Disabled")
                if (EXPERIMENTAL_GA4_API_ENABLED) {
                    val checkedIso = when (val s = uiState.analyticsState) {
                        is AnalyticsSourceState.Ready -> s.snapshot.syncedAtIso
                        is AnalyticsSourceState.Empty -> s.syncedAtIso
                        is AnalyticsSourceState.Error -> s.syncedAtIso
                        else -> null
                    }
                    DevKeyValueRow(label = "Last checked", value = checkedIso?.let(::formatIsoShort) ?: "Not checked")
                }
            }
        }

        if (!EXPERIMENTAL_GA4_API_ENABLED) {
            item(key = "analytics_console_links") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    DevSectionTitle("Firebase Console")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Open live charts or debug events in Firebase.",
                        style = typography.caption,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Open Analytics",
                            onClick = { context.openHttpsUrl(ANALYTICS_OVERVIEW_URL) },
                            variant = PrismaticButtonVariant.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        PrismaticButton(
                            text = "Open DebugView",
                            onClick = { context.openHttpsUrl(ANALYTICS_DEBUGVIEW_URL) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "analytics_experimental_notice") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevSectionTitle("In-App Report")
                        DevToneBadge(label = "Experimental", tone = "warning")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Unverified GA4 sync is unavailable for production use. Saved for a future update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }
        } else {
            item(key = "analytics_filters") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    DevSectionTitle("Date Range")
                    Spacer(Modifier.height(6.dp))
                    DevChoiceChipRow(
                        items = AnalyticsDateRange.entries,
                        selected = uiState.analyticsDateRange,
                        labelOf = { it.label },
                        onSelect = onSelectDateRange
                    )
                }
            }

            when (val state = uiState.analyticsState) {
            AnalyticsSourceState.Loading -> {
                item(key = "analytics_loading") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Loading Insights")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            is AnalyticsSourceState.Unavailable -> {
                item(key = "analytics_unavailable") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Analytics Report")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(10.dp))
                        if (state.canSignInWithGoogle) {
                            PrismaticButton(
                                text = "Google Sign-In",
                                onClick = { onSignInWithGoogle { url -> context.openHttpsUrl(url) } },
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            PrismaticButton(
                                text = "Open Settings",
                                onClick = onOpenSettings,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            is AnalyticsSourceState.Empty -> {
                item(key = "analytics_empty") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("No Data Yet")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No usage events recorded in the last ${state.dateRange.days} days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            is AnalyticsSourceState.Error -> {
                item(key = "analytics_error") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Standard,
                        accentTint = colors.error
                    ) {
                        DevSectionTitle("Report Failed")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Try Again",
                                onClick = onRefresh,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Open Settings",
                                onClick = onOpenSettings,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            is AnalyticsSourceState.Ready -> {
                val snap = state.snapshot
                if (snap.realtimeActiveUsers > 0L || snap.realtimeEventCount > 0L || snap.realtimeEvents.isNotEmpty()) {
                    item(key = "analytics_realtime") {
                        SpatialGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tier = SpatialGlassTier.Standard,
                            accentTint = colors.success
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DevSectionTitle("Live (Last 30 Min)")
                                DevToneBadge(label = "Realtime", tone = "healthy")
                            }
                            Spacer(Modifier.height(6.dp))
                            DevKeyValueRow(label = "Active users", value = snap.realtimeActiveUsers.toString())
                            DevKeyValueRow(label = "Event count", value = snap.realtimeEventCount.toString())
                            snap.realtimeEvents.take(6).forEach { ev ->
                                DevKeyValueRow(label = ev.label, value = "${ev.count} events")
                            }
                        }
                    }
                }
                item(key = "analytics_metrics_1") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsMetricTile(
                            title = "Active Users",
                            value = snap.activeUsers.toString(),
                            sub = "Last ${snap.dateRange.days} days",
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMetricTile(
                            title = "New Users",
                            value = snap.newUsers.toString(),
                            sub = "${snap.returningRatePercent}% returning",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "analytics_metrics_2") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsMetricTile(
                            title = "Avg Session",
                            value = "${snap.avgEngagementSeconds}s",
                            sub = "Per active user",
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMetricTile(
                            title = "Update Adoption",
                            value = snap.updateAdoptions.toString(),
                            sub = "app_updated events",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "analytics_growth") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("User Growth Trend")
                        Spacer(Modifier.height(6.dp))
                        if (snap.growthTrend.isEmpty()) {
                            Text("No daily points.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        } else {
                            snap.growthTrend.forEach { pt ->
                                DevKeyValueRow(
                                    label = pt.dateLabel,
                                    value = "${pt.activeUsers} active · ${pt.newUsers} new"
                                )
                            }
                        }
                    }
                }
                item(key = "analytics_features") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Popular Features")
                        Spacer(Modifier.height(6.dp))
                        if (snap.popularFeatures.isEmpty()) {
                            Text("No feature usage recorded yet.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        } else {
                            snap.popularFeatures.forEach { item ->
                                DevKeyValueRow(
                                    label = item.label,
                                    value = "${item.count} uses · ${item.users} users"
                                )
                            }
                        }
                    }
                }
                item(key = "analytics_versions") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("App Versions")
                        Spacer(Modifier.height(6.dp))
                        if (snap.appVersions.isEmpty()) {
                            Text("No version breakdown available.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        } else {
                            snap.appVersions.forEach { v ->
                                DevKeyValueRow(label = v.label, value = "${v.users} users")
                            }
                        }
                    }
                }
            }
        }

            item(key = "analytics_console_links") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    DevSectionTitle("Firebase Console")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Open full charts or live event stream in Firebase.",
                        style = typography.caption,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Open Analytics",
                            onClick = { context.openHttpsUrl(ANALYTICS_OVERVIEW_URL) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        PrismaticButton(
                            text = "Open DebugView",
                            onClick = { context.openHttpsUrl(ANALYTICS_DEBUGVIEW_URL) },
                            variant = PrismaticButtonVariant.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricTile(
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
