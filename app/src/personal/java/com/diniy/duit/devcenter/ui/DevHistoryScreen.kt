package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.HistoryFilter

@Composable
fun DevHistoryScreen(
    uiState: DevCenterUiState,
    onSelectFilter: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val combined = uiState.combinedBroadcastHistory
    val filteredBroadcasts = when (uiState.historyFilter) {
        HistoryFilter.ALL -> combined
        HistoryFilter.CUSTOM -> uiState.vault.customHistory
        HistoryFilter.RELEASES -> uiState.dashboard.releaseBroadcasts
        HistoryFilter.AUDIT -> emptyList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "filter_chips") {
            DevChoiceChipRow(
                items = HistoryFilter.entries,
                selected = uiState.historyFilter,
                labelOf = { it.label },
                onSelect = onSelectFilter
            )
        }

        if (uiState.historyFilter == HistoryFilter.AUDIT) {
            val auditItems = uiState.vault.auditLog
            if (auditItems.isEmpty()) {
                item(key = "audit_empty") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Text(
                            text = "No audit events recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
                    }
                }
            } else {
                items(auditItems, key = { it.id }) { event ->
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = event.action,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primaryText
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
        } else if (filteredBroadcasts.isEmpty()) {
            item(key = "history_empty") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Text(
                        text = "No broadcast history matching this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }
            }
        } else {
            items(filteredBroadcasts, key = { it.id }) { item ->
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DevToneBadge(label = item.kind.uppercase(), tone = "neutral")
                            DevToneBadge(label = item.audience, tone = "neutral")
                        }
                        DevToneBadge(label = item.statusLabel, tone = item.status)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primaryText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    DevKeyValueRow(label = "Broadcast ID", value = item.broadcastId)
                    DevKeyValueRow(label = "Route & Topics", value = "${item.route} (${item.topics.joinToString(", ")})")
                    DevKeyValueRow(label = "Timestamp", value = formatIsoShort(item.timestampIso))
                    if (item.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                    item.workflowUrl?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        PrismaticButton(
                            text = "Open Workflow",
                            onClick = { context.openHttpsUrl(url) },
                            variant = PrismaticButtonVariant.Ghost
                        )
                    }
                }
            }
        }
    }
}
