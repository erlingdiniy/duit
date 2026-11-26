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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.ReleaseChannelFilter

@Composable
fun DevReleasesScreen(
    uiState: DevCenterUiState,
    onSelectFilter: (ReleaseChannelFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val releases = when (uiState.releaseFilter) {
        ReleaseChannelFilter.ALL -> uiState.dashboard.releases
        ReleaseChannelFilter.STABLE -> uiState.dashboard.releases.filter { !it.prerelease }
        ReleaseChannelFilter.BETA -> uiState.dashboard.releases.filter { it.prerelease }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "release_filter_chips") {
            DevChoiceChipRow(
                items = ReleaseChannelFilter.entries,
                selected = uiState.releaseFilter,
                labelOf = { it.label },
                onSelect = onSelectFilter
            )
        }

        if (releases.isEmpty()) {
            item(key = "releases_empty") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Text(
                        text = "No releases found. Tap refresh to query GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }
            }
        } else {
            items(releases, key = { it.tag }) { release ->
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = release.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primaryText
                            )
                            Text(
                                text = "${release.tag}, ${formatIsoShort(release.publishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        DevToneBadge(
                            label = if (release.prerelease) "Beta" else "Stable",
                            tone = if (release.prerelease) "warning" else "success"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    DevKeyValueRow(label = "APK File", value = release.assetName ?: "No APK attached")
                    DevKeyValueRow(label = "APK Size", value = formatApkSizeMb(release.assetSizeBytes))
                    DevKeyValueRow(label = "Downloads", value = release.downloadCount.toString())
                    release.versionCode?.let { DevKeyValueRow(label = "Version Code", value = it.toString()) }
                    release.sha256?.let { DevKeyValueRow(label = "SHA-256", value = "${it.take(16)}…") }
                    DevKeyValueRow(label = "Broadcast Ledger", value = release.notificationStatus)

                    if (release.changelogBullets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Release Notes", style = MaterialTheme.typography.labelLarge, color = colors.primaryText)
                        Spacer(Modifier.height(4.dp))
                        release.changelogBullets.take(4).forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Release Page",
                            onClick = { context.openHttpsUrl(release.htmlUrl) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        release.assetDownloadUrl?.let { assetUrl ->
                            PrismaticButton(
                                text = "Download APK",
                                onClick = { context.openHttpsUrl(assetUrl) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.dashboard.workflowRuns.isNotEmpty()) {
            item(key = "release_workflows_header") {
                DevSectionTitle("Actions Workflows", modifier = Modifier.padding(top = 4.dp))
            }
            items(uiState.dashboard.workflowRuns.take(8), key = { "${it.repo}-${it.id}" }) { run ->
                SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = SpatialGlassTier.Standard,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "${run.workflowName} #${run.id}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${run.repo}, ${run.branchOrTag}, ${formatIsoShort(run.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DevToneBadge(label = run.conclusion, tone = run.conclusion)
                    }
                    if (run.htmlUrl.startsWith("https://")) {
                        Spacer(Modifier.height(6.dp))
                        PrismaticButton(
                            text = "View Run",
                            onClick = { context.openHttpsUrl(run.htmlUrl) },
                            variant = PrismaticButtonVariant.Ghost
                        )
                    }
                }
            }
        }
    }
}
