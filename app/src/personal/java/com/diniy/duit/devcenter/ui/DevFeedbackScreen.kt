package com.diniy.duit.devcenter.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterAnalyticsOAuth
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.FeedbackPriority
import com.diniy.duit.devcenter.FeedbackSortOrder
import com.diniy.duit.devcenter.FeedbackSourceState
import com.diniy.duit.devcenter.FeedbackTriageStatus
import com.diniy.duit.devcenter.TriagedFeedbackItem

private val FEEDBACK_CATEGORIES = listOf("All", "Bug Report", "Feature Request", "General Feedback")
private const val MAX_CSV_BYTES = 2 * 1024 * 1024L

@Composable
fun DevFeedbackScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    val context = LocalContext.current
    var selectedItemId by remember { mutableStateOf<String?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.authManager.clearPickerBackgroundSuppression()
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            var label = "feedback.csv"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) label = c.getString(nameIdx) ?: label
                    if (sizeIdx >= 0 && c.getLong(sizeIdx) > MAX_CSV_BYTES) {
                        error("CSV exceeds 2 MB limit.")
                    }
                }
            }
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }.orEmpty()
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_CSV_BYTES) { "CSV exceeds 2 MB limit." }
            viewModel.importFeedbackCsv(raw, label)
        }.onFailure { err ->
            viewModel.reportFeedbackImportError(err.message ?: "Unable to read CSV file.")
        }
    }

    val launchCsvPicker = {
        viewModel.authManager.suppressNextBackgroundLockForPicker()
        csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
    }

    val detailItem = selectedItemId?.let { id ->
        uiState.triagedFeedback.firstOrNull { it.entry.id == id }
    }
    if (detailItem != null) {
        FeedbackDetailDialog(
            item = detailItem,
            uiState = uiState,
            onDismiss = { selectedItemId = null },
            onSaveTriage = { read, prio, status, notes, syncSheet ->
                viewModel.updateFeedbackTriage(
                    item = detailItem,
                    isRead = read,
                    priority = prio,
                    status = status,
                    notes = notes,
                    syncToSheetIfPermitted = syncSheet
                )
            },
            onCreateIssue = { viewModel.createGitHubIssueFromFeedback(detailItem) },
            onOpenLinkedIssue = { url -> context.openHttpsUrl(url) },
            onReplyEmail = { email ->
                context.openMailto(email, "Re: duit. ${detailItem.entry.category} (v${detailItem.entry.appVersion})")
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val fb = uiState.feedbackState) {
            is FeedbackSourceState.Unconfigured -> {
                item(key = "fb_unconfigured") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.skyBlue
                    ) {
                        DevSectionTitle("Feedback Setup")
                        Spacer(Modifier.height(6.dp))
                        Text(fb.guideMessage, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
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

            is FeedbackSourceState.Error -> {
                item(key = "fb_error") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.error
                    ) {
                        DevSectionTitle("Feedback Unavailable")
                        Spacer(Modifier.height(6.dp))
                        Text(fb.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
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

            is FeedbackSourceState.Ready -> {
                val triaged = uiState.triagedFeedback
                val filtered = uiState.filteredFeedback
                val dupCount = triaged.count { it.duplicateOfIds.isNotEmpty() }

                item(key = "fb_inbox_summary") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Elevated) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle(fb.sourceLabel)
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DevMetricTile(
                                title = "Unread",
                                value = uiState.unreadFeedbackCount.toString(),
                                sub = "${triaged.size} total items",
                                accentTint = if (uiState.unreadFeedbackCount > 0) tokens.warningTint else null,
                                modifier = Modifier.weight(1f)
                            )
                            DevMetricTile(
                                title = "Open Bugs",
                                value = uiState.openBugReportCount.toString(),
                                sub = "$dupCount duplicate links",
                                accentTint = if (uiState.openBugReportCount > 0) tokens.dangerTint else tokens.healthyTint,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = uiState.vault.feedbackAlertsEnabled,
                                    role = Role.Switch,
                                    onValueChange = viewModel::setFeedbackAlertsEnabled
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Background New-Feedback Alerts (6h)", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                            Switch(checked = uiState.vault.feedbackAlertsEnabled, onCheckedChange = null)
                        }
                    }
                }

                item(key = "fb_filters") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        PrismaticTextField(
                            value = uiState.feedbackSearchQuery,
                            onValueChange = viewModel::updateFeedbackSearch,
                            label = "Search feedback",
                            placeholder = "Keyword, version, priority or issue"
                        )
                        Spacer(Modifier.height(8.dp))
                        DevChoiceChipRow(
                            items = FEEDBACK_CATEGORIES,
                            selected = uiState.feedbackCategoryFilter,
                            labelOf = { it },
                            onSelect = viewModel::updateFeedbackCategoryFilter
                        )
                        Spacer(Modifier.height(6.dp))
                        val statusOptions: List<FeedbackTriageStatus?> = listOf(null) + FeedbackTriageStatus.entries
                        DevChoiceChipRow(
                            items = statusOptions,
                            selected = uiState.feedbackStatusFilter,
                            labelOf = { it?.label ?: "All Status" },
                            onSelect = viewModel::updateFeedbackStatusFilter
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = uiState.feedbackUnreadOnly,
                                onClick = { viewModel.toggleFeedbackUnreadOnly(!uiState.feedbackUnreadOnly) },
                                label = { Text("Unread Only") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            Text(
                                text = "Showing ${filtered.size} of ${triaged.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        DevChoiceChipRow(
                            items = FeedbackSortOrder.entries,
                            selected = uiState.feedbackSortOrder,
                            labelOf = { it.label },
                            onSelect = viewModel::updateFeedbackSortOrder
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "fb_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text(
                                text = "No feedback entries match your filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.entry.id }) { item ->
                        val entry = item.entry
                        val triage = item.triage
                        SpatialGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .candyPressable(
                                    onClick = { selectedItemId = entry.id },
                                    onClickLabel = "Inspect feedback item"
                                ),
                            tier = SpatialGlassTier.Standard,
                            accentTint = if (item.isUnread) tokens.primaryAccent else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.isUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(tokens.primaryAccent)
                                        )
                                    }
                                    Text(
                                        text = entry.category,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primaryText
                                    )
                                    if (triage.priority != FeedbackPriority.NORMAL) {
                                        DevToneBadge(label = triage.priority.label, tone = triage.priority.name)
                                    }
                                }
                                DevToneBadge(label = triage.status.label, tone = triage.status.name)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "v${entry.appVersion}, ${formatIsoShort(entry.timestampIso)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (item.duplicateOfIds.isNotEmpty()) {
                                        DevToneBadge(label = "${item.duplicateOfIds.size} similar", tone = "warning")
                                    }
                                    triage.linkedIssueNumber?.let { num ->
                                        DevToneBadge(label = "Issue #$num", tone = "ok")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackDetailDialog(
    item: TriagedFeedbackItem,
    uiState: DevCenterUiState,
    onDismiss: () -> Unit,
    onSaveTriage: (
        isRead: Boolean,
        priority: FeedbackPriority,
        status: FeedbackTriageStatus,
        notes: String,
        syncSheet: Boolean
    ) -> Unit,
    onCreateIssue: () -> Unit,
    onOpenLinkedIssue: (String) -> Unit,
    onReplyEmail: (String) -> Unit
) {
    val entry = item.entry
    val triage = item.triage
    var isRead by remember(item.entry.id, triage.isRead) { mutableStateOf(true) }
    var priority by remember(item.entry.id, triage.priority) { mutableStateOf(triage.priority) }
    var status by remember(item.entry.id, triage.status) { mutableStateOf(triage.status) }
    var notes by remember(item.entry.id, triage.notes) { mutableStateOf(triage.notes) }
    val canSyncSheet = entry.sheetRowNumber != null &&
        uiState.vault.feedbackSheetId.isNotBlank() &&
        DevCenterAnalyticsOAuth.SHEETS_READWRITE_SCOPE in uiState.vault.googleGrantedScopes
    var syncSheet by remember { mutableStateOf(canSyncSheet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.category} (v${entry.appVersion})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Submitted: ${formatIsoShort(entry.timestampIso)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.hasContactEmail) {
                    Text(
                        text = "Contact: ${entry.email}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.duplicateOfIds.isNotEmpty()) {
                    Text(
                        text = "Similar items linked: ${item.duplicateOfIds.size} (${item.duplicateGroupId ?: "cluster"})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                DevChoiceChipRow(
                    items = FeedbackPriority.entries,
                    selected = priority,
                    labelOf = { it.label },
                    onSelect = { priority = it }
                )
                DevChoiceChipRow(
                    items = FeedbackTriageStatus.entries,
                    selected = status,
                    labelOf = { it.label },
                    onSelect = { status = it }
                )
                PrismaticTextField(
                    value = notes,
                    onValueChange = { notes = it.take(600) },
                    label = "Developer Notes",
                    placeholder = "Repro status, root cause, target release",
                    singleLine = false
                )
                if (canSyncSheet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = syncSheet, role = Role.Switch, onValueChange = { syncSheet = it }),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync status to Sheet", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncSheet, onCheckedChange = null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (triage.linkedIssueUrl != null && triage.linkedIssueNumber != null) {
                        PrismaticButton(
                            text = "Open #${triage.linkedIssueNumber}",
                            onClick = { onOpenLinkedIssue(triage.linkedIssueUrl) },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrismaticButton(
                            text = "Create Issue",
                            onClick = onCreateIssue,
                            enabled = !uiState.vault.encryptedGithubPat.isNullOrBlank() && !uiState.isFeedbackMutating,
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (item.hasContactEmail) {
                        PrismaticButton(
                            text = "Reply Email",
                            onClick = { onReplyEmail(entry.email) },
                            variant = PrismaticButtonVariant.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (triage.linkedIssueUrl == null) {
                    Text(
                        text = "GitHub issues strip email addresses automatically.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveTriage(isRead, priority, status, notes, syncSheet)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save Triage")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    isRead = !item.triage.isRead
                    onSaveTriage(isRead, priority, status, notes, false)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (item.isUnread) "Mark Read" else "Mark Unread")
            }
        }
    )
}
