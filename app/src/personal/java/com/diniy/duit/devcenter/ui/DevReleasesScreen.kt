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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.diniy.duit.devcenter.AnalyticsSourceState
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.ReleaseChannelFilter

@Composable
fun DevReleasesScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onSelectFilter: (ReleaseChannelFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val dash = uiState.dashboard
    val gaSnap = (uiState.analyticsState as? AnalyticsSourceState.Ready)?.snapshot
    val expandedNotes = remember { mutableStateMapOf<String, Boolean>() }

    val releases = when (uiState.releaseFilter) {
        ReleaseChannelFilter.ALL -> dash.releases
        ReleaseChannelFilter.STABLE -> dash.releases.filter { !it.isPrivate && !it.prerelease }
        ReleaseChannelFilter.BETA -> dash.releases.filter { !it.isPrivate && it.prerelease }
        ReleaseChannelFilter.PRIVATE_DEV -> dash.releases.filter { it.isPrivate }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rel_summary") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevMetricTile(
                    title = "Stable",
                    value = dash.latestStable?.version ?: "—",
                    sub = "${dash.latestStable?.downloadCount ?: 0} downloads",
                    modifier = Modifier.weight(1f)
                )
                DevMetricTile(
                    title = "Private Dev",
                    value = dash.latestPrivateDev?.version ?: "—",
                    sub = "${dash.latestBeta?.version ?: "No beta"} beta",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        uiState.apkIntegrity?.let { integrity ->
            item(key = "rel_apk_integrity") {
                SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = SpatialGlassTier.Elevated,
                    accentTint = if (integrity.matchesOriginalCert) colors.success else colors.error
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevSectionTitle("Installed APK Integrity")
                        DevToneBadge(
                            label = if (integrity.matchesOriginalCert) "Verified" else "Mismatch",
                            tone = if (integrity.matchesOriginalCert) "verified" else "mismatch"
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    DevKeyValueRow(label = "Package", value = integrity.packageName)
                    DevKeyValueRow(label = "Version", value = "v${integrity.versionName} (${integrity.versionCode})")
                    DevKeyValueRow(label = "Private Meta", value = if (integrity.hasPrivateDevMeta) "Present" else "Absent")
                    DevKeyValueRow(label = "Signer SHA-256", value = "${integrity.signerSha256.take(16)}...")
                    Spacer(Modifier.height(6.dp))
                    PrismaticButton(
                        text = "Copy Cert",
                        onClick = { context.copyToClipboard("signer SHA-256", integrity.signerSha256) },
                        variant = PrismaticButtonVariant.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (BuildConfig.PRIVATE_DEV_EDITION) {
            item(key = "rel_private_ota") {
                com.diniy.duit.update.EditionUpdateSettingsCard()
            }
        }

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
            items(releases, key = { "${it.repoSlug}-${it.tag}" }) { release ->
                val isNotesExpanded = expandedNotes[release.tag] == true
                val channelBadge = when {
                    release.isPrivate -> "Private Dev" to "private"
                    release.prerelease -> "Beta" to "warning"
                    else -> "Stable" to "success"
                }
                val adoptionUsers = gaSnap?.appVersions?.firstOrNull {
                    it.label.equals(release.version, ignoreCase = true)
                }?.users
                val adoptionText = if (adoptionUsers != null && gaSnap.totalUsers > 0L) {
                    val pct = ((adoptionUsers * 100L) / gaSnap.totalUsers).coerceIn(0L, 100L)
                    "$adoptionUsers users ($pct%)"
                } else "Unavailable"

                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(release.title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Text(
                                text = "${release.tag}, ${formatIsoShort(release.publishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        DevToneBadge(label = channelBadge.first, tone = channelBadge.second)
                    }
                    Spacer(Modifier.height(8.dp))
                    DevKeyValueRow(label = "Repository", value = release.repoSlug)
                    DevKeyValueRow(label = "APK File", value = release.assetName ?: "No APK attached")
                    DevKeyValueRow(label = "APK Size", value = formatApkSizeMb(release.assetSizeBytes))
                    DevKeyValueRow(label = "Downloads", value = release.downloadCount.toString())
                    DevKeyValueRow(label = "GA4 Adoption", value = adoptionText)
                    release.versionCode?.let { DevKeyValueRow(label = "Version Code", value = it.toString()) }
                    release.sha256?.let { DevKeyValueRow(label = "SHA-256", value = "${it.take(16)}...") }
                    DevKeyValueRow(label = "Broadcast Ledger", value = release.notificationStatus)

                    if (release.changelogBullets.isNotEmpty() || release.rawBody.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        val bulletsToShow = if (isNotesExpanded) release.changelogBullets else release.changelogBullets.take(3)
                        bulletsToShow.forEach { bullet ->
                            Text(
                                text = "- $bullet",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        if (release.changelogBullets.size > 3 || release.rawBody.isNotBlank()) {
                            PrismaticButton(
                                text = if (isNotesExpanded) "Hide Notes" else "Full Notes",
                                onClick = { expandedNotes[release.tag] = !isNotesExpanded },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Open Release",
                            onClick = { context.openHttpsUrl(release.htmlUrl) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        release.sha256?.let { sha ->
                            PrismaticButton(
                                text = "Copy SHA",
                                onClick = { context.copyToClipboard("APK SHA-256", sha) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        } ?: release.assetDownloadUrl?.let { assetUrl ->
                            PrismaticButton(
                                text = "Download",
                                onClick = { context.openHttpsUrl(assetUrl) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (dash.workflowRuns.isNotEmpty()) {
            item(key = "release_workflows_header") {
                DevSectionTitle("Actions Workflows", modifier = Modifier.padding(top = 4.dp))
            }
            items(dash.workflowRuns.take(8), key = { "${it.repo}-${it.id}" }) { run ->
                val jobs = uiState.inspectedWorkflowJobs[run.id].orEmpty()
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
                    if (jobs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        jobs.forEach { job ->
                            DevKeyValueRow(
                                label = job.name,
                                value = if (job.failedSteps.isNotEmpty()) "Failed: ${job.failedSteps.joinToString()}" else job.conclusion
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismaticButton(
                            text = "Inspect Jobs",
                            onClick = { viewModel.inspectWorkflowJobs(run.repo, run.id) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (run.htmlUrl.startsWith("https://")) {
                            PrismaticButton(
                                text = "View Run",
                                onClick = { context.openHttpsUrl(run.htmlUrl) },
                                variant = PrismaticButtonVariant.Ghost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
