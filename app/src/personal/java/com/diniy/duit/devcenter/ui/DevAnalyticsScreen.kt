package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val tokens = rememberDevThemeTokens()
    val context = LocalContext.current
    val optInEnabled = UsageAnalytics.isCollectionEnabled()
    val channel = UsageAnalyticsPolicy.resolveReleaseChannel(BuildConfig.VERSION_NAME, BuildConfig.PRIVATE_DEV_EDITION)
    val trafficType = UsageAnalyticsPolicy.resolveTrafficType(BuildConfig.PRIVATE_DEV_EDITION, BuildConfig.ANALYTICS_QA_TEST)
    val projectId = uiState.vault.firebaseProjectId.ifBlank { "duit-beta" }
    val overviewUrl = "https://console.firebase.google.com/project/$projectId/analytics/app/android:com.diniy.duit.beta/overview"
    val debugViewUrl = "https://console.firebase.google.com/project/$projectId/analytics/app/android:com.diniy.duit.beta/debugview"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "an_scope_card") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (optInEnabled) tokens.healthyTint else tokens.warningTint
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
                DevKeyValueRow(label = "Scope", value = "Opted-in users only (internal excluded)")
                DevKeyValueRow(label = "App Downloads", value = uiState.dashboard.totalApkDownloads.toString())
                DevKeyValueRow(label = "Channel", value = "$channel, ${trafficType ?: "public"}")
                DevKeyValueRow(label = "Ad Tracking", value = "Disabled")
                val checkedIso = when (val s = uiState.analyticsState) {
                    is AnalyticsSourceState.Ready -> s.snapshot.syncedAtIso
                    is AnalyticsSourceState.Empty -> s.syncedAtIso
                    is AnalyticsSourceState.Error -> s.syncedAtIso
                    else -> null
                }
                DevKeyValueRow(label = "Updated", value = formatIsoShort(checkedIso))
            }
        }

        item(key = "an_filters") {
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
                item(key = "an_loading") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Loading Analytics")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            is AnalyticsSourceState.Unavailable -> {
                item(key = "an_unavailable") {
                    DevUnavailableCard(
                        title = "Analytics Report",
                        summary = state.summary,
                        actionLabel = if (state.canSignInWithGoogle) "Sign In" else "Settings",
                        onAction = {
                            if (state.canSignInWithGoogle) {
                                onSignInWithGoogle { url -> context.openHttpsUrl(url) }
                            } else {
                                onOpenSettings()
                            }
                        }
                    )
                }
            }

            is AnalyticsSourceState.Empty -> {
                item(key = "an_empty") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("No Observed Events")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No events in last ${state.dateRange.days} days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            is AnalyticsSourceState.Error -> {
                item(key = "an_error") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Standard,
                        accentTint = colors.error
                    ) {
                        DevSectionTitle("Analytics Error")
                        Spacer(Modifier.height(6.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrismaticButton(
                                text = "Retry",
                                onClick = onRefresh,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Settings",
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
                    item(key = "an_realtime") {
                        SpatialGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tier = SpatialGlassTier.Standard,
                            accentTint = tokens.healthyTint
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DevSectionTitle("Live (30 min)")
                                DevToneBadge(label = "Realtime", tone = "healthy")
                            }
                            Spacer(Modifier.height(6.dp))
                            DevKeyValueRow(label = "Active Users", value = snap.realtimeActiveUsers.toString())
                            DevKeyValueRow(label = "Events", value = snap.realtimeEventCount.toString())
                            snap.realtimeEvents.take(6).forEach { ev ->
                                DevKeyValueRow(label = ev.label, value = "${ev.count} events")
                            }
                        }
                    }
                }

                item(key = "an_kpi_1") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DevMetricTile(
                            title = "Total Observed",
                            value = snap.totalUsers.toString(),
                            sub = "${snap.sessions} visits",
                            modifier = Modifier.weight(1f)
                        )
                        DevMetricTile(
                            title = "New / Returning",
                            value = "${snap.newUsers} / ${snap.returningUsers}",
                            sub = "${snap.returningRatePercent}% returning",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "an_kpi_2") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DevMetricTile(
                            title = "DAU / WAU / MAU",
                            value = "${snap.dau} / ${snap.wau} / ${snap.mau}",
                            sub = "Retention ${snap.dauPerMauPercent}%",
                            modifier = Modifier.weight(1f)
                        )
                        DevMetricTile(
                            title = "Avg Visit",
                            value = "${snap.avgEngagementSeconds}s",
                            sub = "Updates ${snap.updateAdoptions}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "an_growth_chart") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle("User Growth")
                            if (snap.isCached) DevToneBadge(label = "Cached", tone = "cached")
                        }
                        Spacer(Modifier.height(8.dp))
                        DevSparklineChart(
                            labels = snap.growthTrend.map { it.dateLabel },
                            values = snap.growthTrend.map { it.activeUsers.toFloat() },
                            unitSuffix = " users"
                        )
                    }
                }

                item(key = "an_features") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Feature Usage")
                        Spacer(Modifier.height(8.dp))
                        DevRankedBarList(
                            rows = snap.popularFeatures.map { Triple(it.label, it.count, "${it.count} uses, ${it.users} users") },
                            accent = tokens.primaryAccent
                        )
                    }
                }

                item(key = "an_themes_widgets") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Themes & Widgets")
                        Spacer(Modifier.height(6.dp))
                        DevKeyValueRow(
                            label = "Home Widget Opens",
                            value = "${snap.widgetUsageCount} (${snap.widgetUsers} users)"
                        )
                        Spacer(Modifier.height(8.dp))
                        DevRankedBarList(
                            rows = snap.themePopularity.map { Triple(it.label, it.count, "${it.count} picks, ${it.users} users") },
                            accent = tokens.secondaryAccent
                        )
                    }
                }

                item(key = "an_versions_channels") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("App Versions & Channels")
                        Spacer(Modifier.height(8.dp))
                        DevRankedBarList(
                            rows = snap.appVersions.map { Triple("v${it.label}", it.users, "${it.users} users") },
                            accent = tokens.primaryAccent
                        )
                        if (snap.releaseChannels.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            DevRankedBarList(
                                rows = snap.releaseChannels.map { Triple("Channel: ${it.label}", it.users, "${it.users} users") },
                                accent = tokens.healthyTint
                            )
                        }
                    }
                }

                item(key = "an_geo_device") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        DevSectionTitle("Countries, Android & Devices")
                        Spacer(Modifier.height(8.dp))
                        DevRankedBarList(
                            rows = snap.countries.map { Triple(it.label, it.users, "${it.users} users") },
                            accent = tokens.secondaryAccent
                        )
                        if (snap.androidVersions.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            DevRankedBarList(
                                rows = snap.androidVersions.map { Triple("Android ${it.label}", it.users, "${it.users} users") },
                                accent = tokens.primaryAccent
                            )
                        }
                        if (snap.deviceModels.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            DevRankedBarList(
                                rows = snap.deviceModels.map { Triple(it.label, it.users, "${it.users} users") },
                                accent = tokens.warningTint
                            )
                        }
                    }
                }

                if (snap.quotaHourlyRemaining != null || snap.quotaDailyRemaining != null) {
                    item(key = "an_quota") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            DevSectionTitle("GA4 Limits")
                            Spacer(Modifier.height(6.dp))
                            snap.quotaHourlyRemaining?.let { DevKeyValueRow("Hourly Remaining", it.toString()) }
                            snap.quotaDailyRemaining?.let { DevKeyValueRow("Daily Remaining", it.toString()) }
                        }
                    }
                }
            }
        }

        item(key = "an_console_links") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Firebase Console")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismaticButton(
                        text = "Open Analytics",
                        onClick = { context.openHttpsUrl(overviewUrl) },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    PrismaticButton(
                        text = "DebugView",
                        onClick = { context.openHttpsUrl(debugViewUrl) },
                        variant = PrismaticButtonVariant.Ghost,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
