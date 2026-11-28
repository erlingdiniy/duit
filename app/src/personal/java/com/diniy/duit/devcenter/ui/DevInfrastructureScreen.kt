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
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.InfraProbeKind

@Composable
fun DevInfrastructureScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    val infra = uiState.infraSnapshot
    val services = infra.services
    val healthyCount = services.count { it.state == "healthy" }
    val issueCount = services.count { it.state == "error" || it.state == "warning" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "infra_summary") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = if (issueCount == 0 && healthyCount > 0) tokens.healthyTint else tokens.warningTint
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("System Health")
                    PrismaticButton(
                        text = "Run Checks",
                        onClick = viewModel::runConnectionHealthCheck,
                        enabled = !uiState.isRefreshing,
                        isLoading = uiState.isRefreshing,
                        variant = PrismaticButtonVariant.Primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevMetricTile(
                        title = "Healthy Services",
                        value = "$healthyCount / ${services.size}",
                        sub = if (issueCount == 0) "No active alerts" else "$issueCount attention needed",
                        modifier = Modifier.weight(1f)
                    )
                    DevMetricTile(
                        title = "Last Full Sync",
                        value = formatIsoShort(infra.lastSyncIso ?: uiState.vault.lastFullSyncIso),
                        sub = "${services.count { it.probeKind == InfraProbeKind.VERIFIED_REMOTE }} remote verified",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "infra_auth_expiry") {
            val v = uiState.vault
            val now = System.currentTimeMillis()
            val expiryStatus = when {
                v.encryptedAnalyticsToken.isNullOrBlank() && v.encryptedAnalyticsRefreshToken.isNullOrBlank() -> "Not signed in"
                infra.oauthExpiresAtMs <= 0L -> "Manual bearer"
                infra.oauthExpiresAtMs <= now -> if (infra.oauthAutoRefreshReady) "Expired (auto-refresh ready)" else "Expired"
                else -> "${((infra.oauthExpiresAtMs - now) / 60_000L).coerceAtLeast(1L)}m left"
            }
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Sign-In & Keys")
                    PrismaticButton(
                        text = "Sign In",
                        onClick = onOpenSettings,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(label = "GitHub PAT", value = v.githubPatHint)
                DevKeyValueRow(label = "Google OAuth Token", value = expiryStatus)
                DevKeyValueRow(label = "Silent Refresh", value = if (infra.oauthAutoRefreshReady) "Ready" else "Unavailable")
                DevKeyValueRow(label = "Granted Scopes", value = "${v.googleGrantedScopes.size} scope(s)")
            }
        }

        if (services.isNotEmpty()) {
            item(key = "infra_services_header") {
                DevSectionTitle("Live Probe Matrix", modifier = Modifier.padding(top = 4.dp))
            }
            items(services, key = { it.id }) { svc ->
                val kindTone = when (svc.probeKind) {
                    InfraProbeKind.VERIFIED_REMOTE -> "verified_remote"
                    InfraProbeKind.LOCAL_SDK -> "neutral"
                    InfraProbeKind.UNCONFIGURED -> "unconfigured"
                }
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = svc.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DevToneBadge(label = svc.probeKind.label, tone = kindTone)
                            DevToneBadge(label = svc.state.replaceFirstChar { it.uppercase() }, tone = svc.state)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(svc.summary, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                    if (svc.detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(svc.detail, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                    svc.quotaText?.let { q ->
                        Spacer(Modifier.height(6.dp))
                        DevKeyValueRow(label = "Quota", value = q)
                    }
                }
            }
        }

        item(key = "infra_free_tier_quotas") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Free-Tier Limits")
                Spacer(Modifier.height(6.dp))
                DevKeyValueRow(
                    label = "GitHub Core Limit",
                    value = infra.githubRateRemaining?.let { "$it / ${infra.githubRateLimit ?: 5000}" } ?: "Unverified"
                )
                infra.githubRateResetIso?.let { DevKeyValueRow(label = "GitHub Reset", value = formatIsoShort(it)) }
                DevKeyValueRow(
                    label = "Analytics Hourly",
                    value = infra.ga4HourlyQuotaRemaining?.toString() ?: "40,000 / hr tier"
                )
                DevKeyValueRow(
                    label = "Analytics Daily",
                    value = infra.ga4DailyQuotaRemaining?.toString() ?: "200,000 / day tier"
                )
                DevKeyValueRow(label = "Remote Flags (Free)", value = "100k fetches / day, \$0 billing")
            }
        }
    }
}
