package com.diniy.duit.devcenter.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

private data class InboxCategoryTab(
    val filterValue: String,
    val label: String
)

private val INBOX_TABS = listOf(
    InboxCategoryTab("All", "All"),
    InboxCategoryTab("Bug Report", "Bugs"),
    InboxCategoryTab("Feature Request", "Features"),
    InboxCategoryTab("General Feedback", "General")
)

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
    var showMoreFilters by remember { mutableStateOf(false) }

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
        contentPadding = devListContentPadding(),
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
                        DevSectionTitle("Feedback Inbox")
                        Spacer(Modifier.height(6.dp))
                        Text(fb.guideMessage, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismaticButton(
                                text = "Refresh",
                                onClick = viewModel::refreshAll,
                                variant = PrismaticButtonVariant.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            PrismaticButton(
                                text = "Import CSV",
                                onClick = launchCsvPicker,
                                variant = PrismaticButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            is FeedbackSourceState.Error -> {
                item(key = "fb_error") {
                    DevErrorStateCard(
                        title = "Inbox Unavailable",
                        message = fb.message,
                        primaryLabel = "Import CSV",
                        onPrimary = launchCsvPicker,
                        secondaryLabel = "Settings",
                        onSecondary = onOpenSettings
                    )
                }
            }

            is FeedbackSourceState.Ready -> {
                val triaged = uiState.triagedFeedback
                val filtered = uiState.filteredFeedback
                val dupCount = triaged.count { it.duplicateOfIds.isNotEmpty() }
                val activeTab = INBOX_TABS.firstOrNull { it.filterValue == uiState.feedbackCategoryFilter } ?: INBOX_TABS.first()

                item(key = "fb_category_tabs") {
                    DevSegmentedBar(
                        items = INBOX_TABS,
                        selected = activeTab,
                        labelOf = { it.label },
                        badgeCountOf = { tab ->
                            when (tab.filterValue) {
                                "All" -> uiState.unreadFeedbackCount
                                else -> triaged.count { it.entry.category == tab.filterValue && it.isUnread }
                            }
                        },
                        onSelect = { viewModel.updateFeedbackCategoryFilter(it.filterValue) }
                    )
                }

                item(key = "fb_inbox_summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DevMetricTile(
                                title = "Unread",
                                value = uiState.unreadFeedbackCount.toString(),
                                sub = "${triaged.size} total, ${uiState.openFeatureRequestCount} ideas",
                                accentTint = if (uiState.unreadFeedbackCount > 0) tokens.warningTint else null,
                                icon = Icons.Rounded.MarkEmailUnread,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            DevMetricTile(
                                title = "Open Bugs",
                                value = uiState.openBugReportCount.toString(),
                                sub = "$dupCount similar, ${uiState.spamFeedbackCount} spam",
                                accentTint = if (uiState.openBugReportCount > 0) tokens.dangerTint else tokens.healthyTint,
                                icon = Icons.Rounded.BugReport,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        SpatialGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tier = SpatialGlassTier.Standard
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Source",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondaryText
                                )
                                Text(
                                    text = fb.sourceLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.unreadFeedbackCount > 0) {
                                    PrismaticButton(
                                        text = "Mark Read",
                                        onClick = viewModel::markAllFeedbackRead,
                                        variant = PrismaticButtonVariant.Ghost,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                PrismaticButton(
                                    text = "Import CSV",
                                    onClick = launchCsvPicker,
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            DevSearchField(
                                value = uiState.feedbackSearchQuery,
                                onValueChange = viewModel::updateFeedbackSearch,
                                placeholder = "Search messages, versions or issues"
                            )
                            Spacer(Modifier.height(8.dp))
                            val extraActiveCount = (if (uiState.feedbackStatusFilter != null) 1 else 0) +
                                (if (uiState.feedbackSortOrder != FeedbackSortOrder.NEWEST) 1 else 0)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = uiState.feedbackUnreadOnly,
                                    onClick = { viewModel.toggleFeedbackUnreadOnly(!uiState.feedbackUnreadOnly) },
                                    label = { Text("Unread", maxLines = 1) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                                FilterChip(
                                    selected = showMoreFilters || extraActiveCount > 0,
                                    onClick = { showMoreFilters = !showMoreFilters },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = if (extraActiveCount > 0) "Filters ($extraActiveCount)" else "Filters",
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${filtered.size} items",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondaryText,
                                    maxLines = 1
                                )
                            }
                            AnimatedVisibility(visible = showMoreFilters) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = colors.divider.copy(alpha = 0.65f))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Triage Status", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                                    Spacer(Modifier.height(4.dp))
                                    val statusOptions: List<FeedbackTriageStatus?> = listOf(null) + FeedbackTriageStatus.entries
                                    DevChoiceChipRow(
                                        items = statusOptions,
                                        selected = uiState.feedbackStatusFilter,
                                        labelOf = { it?.label ?: "All Status" },
                                        onSelect = viewModel::updateFeedbackStatusFilter
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Sort By", style = MaterialTheme.typography.labelLarge, color = colors.secondaryText)
                                    Spacer(Modifier.height(4.dp))
                                    DevChoiceChipRow(
                                        items = FeedbackSortOrder.entries,
                                        selected = uiState.feedbackSortOrder,
                                        labelOf = { it.label },
                                        onSelect = viewModel::updateFeedbackSortOrder
                                    )
                                    Spacer(Modifier.height(6.dp))
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
                                        Text(
                                            text = "Background Alerts (6h)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                        Switch(checked = uiState.vault.feedbackAlertsEnabled, onCheckedChange = null)
                                    }
                                }
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "fb_empty") {
                        DevEmptyStateCard(
                            icon = Icons.Rounded.Inbox,
                            title = if (triaged.isEmpty()) "Inbox Empty" else "No Matching Items",
                            subtitle = if (triaged.isEmpty()) {
                                "User feedback, bug reports and ideas appear here once synced."
                            } else {
                                "Adjust your category tab, search text or status filter."
                            },
                            actionLabel = if (triaged.isNotEmpty()) "Clear Filters" else null,
                            onAction = if (triaged.isNotEmpty()) {
                                {
                                    viewModel.updateFeedbackSearch("")
                                    viewModel.updateFeedbackCategoryFilter("All")
                                    viewModel.updateFeedbackStatusFilter(null)
                                    viewModel.toggleFeedbackUnreadOnly(false)
                                }
                            } else null
                        )
                    }
                } else {
                    items(filtered, key = { it.entry.id }) { item ->
                        val entry = item.entry
                        val triage = item.triage
                        SpatialGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.cardCornerDp.dp))
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
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
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
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            enabled = (uiState.vault.isBackendEnrolled || !uiState.vault.encryptedGithubPat.isNullOrBlank()) &&
                                !uiState.isFeedbackMutating,
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
