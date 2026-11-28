package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.AnalyticsSourceState
import com.diniy.duit.devcenter.DevCenterSection
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.RemoteConfigSourceState
import java.util.Locale

private data class MoreModuleRowData(
    val section: DevCenterSection,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color,
    val badgeLabel: String? = null,
    val badgeTone: String = "neutral"
)

@Composable
fun DevMoreHubScreen(
    uiState: DevCenterUiState,
    onOpenSection: (DevCenterSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = rememberDevThemeTokens()
    val dash = uiState.dashboard
    val gaSnap = (uiState.analyticsState as? AnalyticsSourceState.Ready)?.snapshot
    val rcState = uiState.remoteConfigState
    val services = uiState.infraSnapshot.services
    val healthyCount = services.count { it.state == "healthy" }
    val issueCount = services.count { it.state == "error" || it.state == "warning" }

    val latestTag = dash.latestStable?.version
        ?: dash.latestBeta?.version
        ?: dash.latestPrivateDev?.version
    val releasesDetail = if (latestTag != null) {
        "v$latestTag, ${dash.totalApkDownloads} downloads"
    } else {
        "${dash.releases.size} tracked releases"
    }

    val rcDetail = when (rcState) {
        is RemoteConfigSourceState.Ready -> "Template v${rcState.versionNumber.ifBlank { "1" }}"
        RemoteConfigSourceState.Loading -> "Loading flags"
        else -> "Manage remote flags"
    }

    val crashDetail = gaSnap?.crashFreeUsersPercent?.let {
        String.format(Locale.US, "%.2f%% crash-free", it)
    } ?: "Firebase Crashlytics links"

    val infraDetail = if (services.isNotEmpty()) {
        "$healthyCount of ${services.size} healthy"
    } else {
        "Worker & API checks"
    }

    val repoShort = uiState.selectedGitHubRepo.substringAfter('/').ifBlank { "Repositories" }
    val settingsDetail = if (uiState.vault.isBackendEnrolled) {
        uiState.vault.enrolledDeviceLabel.ifBlank { "Owner device active" }
    } else {
        "Security & connection"
    }

    val deliveryRows = listOf(
        MoreModuleRowData(
            section = DevCenterSection.RELEASES,
            title = "Releases",
            detail = releasesDetail,
            icon = Icons.Rounded.NewReleases,
            tint = tokens.primaryAccent,
            badgeLabel = latestTag?.let { "v$it" },
            badgeTone = "ok"
        ),
        MoreModuleRowData(
            section = DevCenterSection.REMOTE_CONFIG,
            title = "Remote Config",
            detail = rcDetail,
            icon = Icons.Rounded.Tune,
            tint = tokens.secondaryAccent
        ),
        MoreModuleRowData(
            section = DevCenterSection.GITHUB,
            title = "GitHub",
            detail = repoShort,
            icon = Icons.Rounded.Code,
            tint = tokens.primaryAccent
        )
    )

    val reliabilityRows = listOf(
        MoreModuleRowData(
            section = DevCenterSection.CRASH_REPORTS,
            title = "Crash Reports",
            detail = crashDetail,
            icon = Icons.Rounded.BugReport,
            tint = tokens.warningTint
        ),
        MoreModuleRowData(
            section = DevCenterSection.INFRASTRUCTURE,
            title = "Infrastructure",
            detail = infraDetail,
            icon = Icons.Rounded.MonitorHeart,
            tint = if (issueCount == 0) tokens.healthyTint else tokens.warningTint,
            badgeLabel = if (services.isNotEmpty()) (if (issueCount == 0) "Healthy" else "$issueCount alert") else null,
            badgeTone = if (issueCount == 0) "healthy" else "warning"
        ),
        MoreModuleRowData(
            section = DevCenterSection.AUDIT_LOGS,
            title = "Audit Logs",
            detail = "${uiState.combinedAuditLog.size} recorded events",
            icon = Icons.Rounded.History,
            tint = tokens.secondaryAccent
        )
    )

    val adminRows = listOf(
        MoreModuleRowData(
            section = DevCenterSection.SETTINGS,
            title = "Settings",
            detail = settingsDetail,
            icon = Icons.Rounded.Settings,
            tint = if (uiState.vault.isBackendEnrolled) tokens.healthyTint else tokens.warningTint,
            badgeLabel = if (uiState.vault.isBackendEnrolled) "Enrolled" else "Manual",
            badgeTone = if (uiState.vault.isBackendEnrolled) "healthy" else "warning"
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = devListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "more_delivery") {
            MoreModuleGroupCard(
                title = "Delivery",
                rows = deliveryRows,
                onOpenSection = onOpenSection
            )
        }
        item(key = "more_reliability") {
            MoreModuleGroupCard(
                title = "Reliability",
                rows = reliabilityRows,
                onOpenSection = onOpenSection
            )
        }
        item(key = "more_admin") {
            MoreModuleGroupCard(
                title = "Administration",
                rows = adminRows,
                onOpenSection = onOpenSection
            )
        }
    }
}

@Composable
private fun MoreModuleGroupCard(
    title: String,
    rows: List<MoreModuleRowData>,
    onOpenSection: (DevCenterSection) -> Unit
) {
    val colors = SpatialTheme.colors
    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        DevSectionTitle(
            title = title,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .candyPressable(
                        onClick = { onOpenSection(item.section) },
                        onClickLabel = "Open ${item.title}"
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(item.tint.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (colors.isDark) item.tint else colors.readableOn(item.tint),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.badgeLabel?.let { badge ->
                    DevToneBadge(label = badge, tone = item.badgeTone)
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    color = colors.divider.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp)
                )
            }
        }
    }
}
