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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterRedactor
import com.diniy.duit.devcenter.DevCenterUiState

private enum class AuditCategoryFilter(val label: String) {
    ALL("All"),
    AUTH("Sign-In"),
    CREDENTIALS("Keys"),
    BROADCASTS("Broadcasts"),
    REMOTE_CONFIG("Remote Flags"),
    GITHUB("GitHub"),
    FEEDBACK("Feedback")
}

private enum class AuditStatusFilter(val label: String) {
    ALL("All"),
    OK("OK"),
    WARNING("Warning"),
    ERROR("Error")
}

@Composable
fun DevAuditLogsScreen(
    uiState: DevCenterUiState,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf(AuditCategoryFilter.ALL) }
    var statusFilter by rememberSaveable { mutableStateOf(AuditStatusFilter.ALL) }

    val q = searchQuery.trim().lowercase()
    val filtered = uiState.combinedAuditLog.filter { entry ->
        val matchesCat = when (categoryFilter) {
            AuditCategoryFilter.ALL -> true
            AuditCategoryFilter.AUTH -> entry.action.startsWith("auth.") || entry.action.startsWith("google.") || entry.action.startsWith("analytics.token")
            AuditCategoryFilter.CREDENTIALS -> entry.action.startsWith("credentials.")
            AuditCategoryFilter.BROADCASTS -> entry.action.startsWith("broadcast.") || entry.action.startsWith("kill_switch.")
            AuditCategoryFilter.REMOTE_CONFIG -> entry.action.startsWith("remote_config.")
            AuditCategoryFilter.GITHUB -> entry.action.startsWith("github.")
            AuditCategoryFilter.FEEDBACK -> entry.action.startsWith("feedback.")
        }
        val matchesStatus = when (statusFilter) {
            AuditStatusFilter.ALL -> true
            AuditStatusFilter.OK -> entry.status.equals("ok", true)
            AuditStatusFilter.WARNING -> entry.status.equals("warning", true)
            AuditStatusFilter.ERROR -> entry.status.equals("error", true)
        }
        val matchesQuery = q.isEmpty() ||
            entry.action.lowercase().contains(q) ||
            entry.summary.lowercase().contains(q) ||
            entry.target.lowercase().contains(q) ||
            entry.actor.lowercase().contains(q)
        matchesCat && matchesStatus && matchesQuery
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "audit_filters") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DevSectionTitle("Security & Action Logs")
                    PrismaticButton(
                        text = "Copy Logs",
                        onClick = {
                            val summaryText = filtered.take(25).joinToString("\n") { e ->
                                DevCenterRedactor.redact("${e.timestampIso} [${e.status}] ${e.action} (${e.target}): ${e.summary}")
                            }
                            context.copyToClipboard("audit logs", summaryText)
                        },
                        enabled = filtered.isNotEmpty(),
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
                Spacer(Modifier.height(8.dp))
                PrismaticTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search logs",
                    placeholder = "Action, target, actor or summary"
                )
                Spacer(Modifier.height(8.dp))
                DevChoiceChipRow(
                    items = AuditCategoryFilter.entries,
                    selected = categoryFilter,
                    labelOf = { it.label },
                    onSelect = { categoryFilter = it }
                )
                Spacer(Modifier.height(6.dp))
                DevChoiceChipRow(
                    items = AuditStatusFilter.entries,
                    selected = statusFilter,
                    labelOf = { it.label },
                    onSelect = { statusFilter = it }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${filtered.size} of ${uiState.combinedAuditLog.size} recorded events",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            }
        }

        if (filtered.isEmpty()) {
            item(key = "audit_empty") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Text(
                        text = "No audit events match this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { event ->
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
                        Text(
                            text = event.action,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        DevToneBadge(label = event.status, tone = event.status)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = event.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primaryText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${event.actor}, ${event.target}, ${formatIsoShort(event.timestampIso)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText
                    )
                }
            }
        }
    }
}
