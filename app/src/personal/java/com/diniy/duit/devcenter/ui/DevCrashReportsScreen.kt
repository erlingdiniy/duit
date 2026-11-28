package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.AnalyticsDateRange
import com.diniy.duit.devcenter.AnalyticsSourceState
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.diagnostics.CrashReporting
import java.util.Locale

private enum class CrashBreakdownTab(val label: String) {
    APP_VERSION("App Version"),
    ANDROID_VERSION("Android"),
    DEVICE("Device Model")
}

private enum class CrashConsoleTarget(val label: String, val query: String) {
    ISSUES("Crash Issues", ""),
    NON_FATALS("Non-Fatals", "?types=non-fatal"),
    ANRS("ANRs", "?types=anr")
}

@Composable
fun DevCrashReportsScreen(
    uiState: DevCenterUiState,
    onSelectDateRange: (AnalyticsDateRange) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val tokens = rememberDevThemeTokens()
    var breakdownTab by rememberSaveable { mutableStateOf(CrashBreakdownTab.APP_VERSION) }
    var consoleTarget by rememberSaveable { mutableStateOf(CrashConsoleTarget.ISSUES) }

    val projectId = uiState.vault.firebaseProjectId.ifBlank { "duit-beta" }
    val consoleBaseUrl = "https://console.firebase.google.com/project/$projectId/crashlytics/app/android:com.diniy.duit.beta/issues"
    val readySnap = (uiState.analyticsState as? AnalyticsSourceState.Ready)?.snapshot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "crash_period") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Crash Metrics Window")
                Spacer(Modifier.height(6.dp))
                DevChoiceChipRow(
                    items = AnalyticsDateRange.entries,
                    selected = uiState.analyticsDateRange,
                    labelOf = { it.label },
                    onSelect = onSelectDateRange
                )
            }
        }

        if (readySnap == null) {
            item(key = "crash_ga_unavailable") {
                DevUnavailableCard(
                    title = "Observed Exception Telemetry",
                    summary = "Connect Google Analytics read-only access in Settings for crash-free metrics.",
                    actionLabel = "Settings",
                    onAction = onOpenSettings
                )
            }
        } else {
            item(key = "crash_kpis_1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevMetricTile(
                        title = "Crash-Free Users",
                        value = readySnap.crashFreeUsersPercent?.let {
                            String.format(Locale.US, "%.2f%%", it)
                        } ?: "—",
                        sub = "${readySnap.activeUsers} observed users",
                        accentTint = tokens.healthyTint,
                        modifier = Modifier.weight(1f)
                    )
                    DevMetricTile(
                        title = "Crash-Free Visits",
                        value = readySnap.crashFreeSessionsPercent?.let {
                            String.format(Locale.US, "%.2f%%", it)
                        } ?: "—",
                        sub = "${readySnap.sessions} visits",
                        accentTint = tokens.secondaryAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "crash_kpis_2") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevMetricTile(
                        title = "Affected Users",
                        value = readySnap.crashAffectedUsers.toString(),
                        sub = "Opted-in app_exception",
                        accentTint = if (readySnap.crashAffectedUsers > 0L) tokens.warningTint else tokens.healthyTint,
                        modifier = Modifier.weight(1f)
                    )
                    DevMetricTile(
                        title = "Exception Events",
                        value = readySnap.crashEventCount.toString(),
                        sub = if (readySnap.isCached) "Cached snapshot" else "Live signal",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "crash_breakdowns") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    DevSectionTitle("Exception Breakdown")
                    Spacer(Modifier.height(6.dp))
                    DevChoiceChipRow(
                        items = CrashBreakdownTab.entries,
                        selected = breakdownTab,
                        labelOf = { it.label },
                        onSelect = { breakdownTab = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    val rows = when (breakdownTab) {
                        CrashBreakdownTab.APP_VERSION -> readySnap.crashesByAppVersion
                        CrashBreakdownTab.ANDROID_VERSION -> readySnap.crashesByOsVersion
                        CrashBreakdownTab.DEVICE -> readySnap.crashesByDevice
                    }.map { Triple(it.label, it.count, "${it.count} events, ${it.users} users") }
                    DevRankedBarList(rows = rows, accent = tokens.warningTint)
                }
            }
        }

        item(key = "crashlytics_sdk_local") {
            val optIn = CrashReporting.isCollectionEnabled()
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Crashlytics Local Diagnostics")
                    DevToneBadge(
                        label = if (optIn) "Opted In" else "Opted Out",
                        tone = if (optIn) "healthy" else "paused"
                    )
                }
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(label = "Channel", value = BuildConfig.RELEASE_CHANNEL)
                DevKeyValueRow(label = "Version", value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                DevKeyValueRow(label = "Firebase Project", value = projectId)
            }
        }

        item(key = "crashlytics_spark_console") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = tokens.primaryAccent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Crashlytics Console")
                    DevToneBadge(label = "Free Plan", tone = "neutral")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Stack traces, non-fatals, and ANRs open directly in Firebase Console on the free plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Spacer(Modifier.height(8.dp))
                DevChoiceChipRow(
                    items = CrashConsoleTarget.entries,
                    selected = consoleTarget,
                    labelOf = { it.label },
                    onSelect = { consoleTarget = it }
                )
                Spacer(Modifier.height(8.dp))
                PrismaticButton(
                    text = "Open Console",
                    onClick = { context.openHttpsUrl("$consoleBaseUrl${consoleTarget.query}") },
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
