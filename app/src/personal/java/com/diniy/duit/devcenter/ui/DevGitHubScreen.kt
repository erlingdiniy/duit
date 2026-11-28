package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.devcenter.GitHubIssueItem

private enum class GitHubModuleTab(val label: String) {
    ISSUES("Issues"),
    PRS("PRs"),
    COMMITS("Commits"),
    WORKFLOWS("Workflows")
}

private enum class IssueStateFilter(val label: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    ALL("All")
}

@Composable
fun DevGitHubScreen(
    uiState: DevCenterUiState,
    viewModel: DevCenterViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val context = LocalContext.current
    val v = uiState.vault
    val repos = listOf(v.sourceRepo, v.distributionRepo, v.consoleRepo).filter { it.isNotBlank() }.distinct()
    val snap = uiState.gitHubRepoSnapshot

    var activeTab by rememberSaveable { mutableStateOf(GitHubModuleTab.ISSUES) }
    var issueFilter by rememberSaveable { mutableStateOf(IssueStateFilter.OPEN) }
    var showCreateIssueDialog by remember { mutableStateOf(false) }
    var commentTargetIssue by remember { mutableStateOf<GitHubIssueItem?>(null) }

    if (showCreateIssueDialog) {
        CreateGitHubIssueDialog(
            repoSlug = snap.repoSlug,
            onDismiss = { showCreateIssueDialog = false },
            onSubmit = { title, body, labels ->
                showCreateIssueDialog = false
                viewModel.createGitHubIssue(snap.repoSlug, title, body, labels)
            }
        )
    }

    commentTargetIssue?.let { issue ->
        AddIssueCommentDialog(
            repoSlug = snap.repoSlug,
            issueNumber = issue.number,
            onDismiss = { commentTargetIssue = null },
            onSubmit = { body ->
                commentTargetIssue = null
                viewModel.commentOnGitHubIssue(snap.repoSlug, issue.number, body)
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "gh_repo_switcher") {
            SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                DevSectionTitle("Repositories")
                Spacer(Modifier.height(6.dp))
                DevChoiceChipRow(
                    items = repos,
                    selected = uiState.selectedGitHubRepo,
                    labelOf = { it.substringAfter('/') },
                    onSelect = viewModel::selectGitHubRepo
                )
            }
        }

        snap.error?.let { err ->
            item(key = "gh_error") {
                SpatialGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tier = SpatialGlassTier.Standard,
                    accentTint = colors.error
                ) {
                    DevSectionTitle("Repository Access")
                    Spacer(Modifier.height(4.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = colors.error)
                    if (!uiState.vault.isBackendEnrolled && uiState.vault.encryptedGithubPat.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PrismaticButton(
                            text = "Open Settings",
                            onClick = onOpenSettings,
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        snap.overview?.let { ov ->
            ov.privacyAlert?.let { alert ->
                item(key = "gh_privacy_alert") {
                    SpatialGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = SpatialGlassTier.Elevated,
                        accentTint = colors.error
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle("Privacy Alert")
                            DevToneBadge(label = "Exposed", tone = "error")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(alert, style = MaterialTheme.typography.bodySmall, color = colors.error)
                    }
                }
            }

            item(key = "gh_overview_card") {
                SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Elevated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = ov.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText
                            )
                            Text(
                                text = "Branch: ${ov.defaultBranch}, Updated: ${formatIsoShort(ov.updatedAtIso)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        DevToneBadge(
                            label = if (ov.isPrivate) "Private" else "Public",
                            tone = if (ov.isPrivate) "private" else "neutral"
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevMetricTile(
                            title = "Stars",
                            value = ov.stars.toString(),
                            sub = "${ov.forks} forks",
                            modifier = Modifier.weight(1f)
                        )
                        DevMetricTile(
                            title = "Open Issues",
                            value = ov.openIssuesCount.toString(),
                            sub = "${ov.watchers} watchers",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    snap.rateLimitRemaining?.let { rem ->
                        Spacer(Modifier.height(8.dp))
                        DevKeyValueRow(
                            label = "Limit Remaining",
                            value = "$rem / ${snap.rateLimitLimit ?: "?"}"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PrismaticButton(
                        text = "Open Repo",
                        onClick = { context.openHttpsUrl(ov.htmlUrl) },
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "gh_sub_tabs") {
            DevChoiceChipRow(
                items = GitHubModuleTab.entries,
                selected = activeTab,
                labelOf = { it.label },
                onSelect = { activeTab = it }
            )
        }

        when (activeTab) {
            GitHubModuleTab.ISSUES -> {
                val filteredIssues = when (issueFilter) {
                    IssueStateFilter.OPEN -> snap.issues.filter { it.state.equals("open", true) }
                    IssueStateFilter.CLOSED -> snap.issues.filter { it.state.equals("closed", true) }
                    IssueStateFilter.ALL -> snap.issues
                }
                item(key = "gh_issues_bar") {
                    SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DevSectionTitle("Issues (${filteredIssues.size})")
                            PrismaticButton(
                                text = "New Issue",
                                onClick = { showCreateIssueDialog = true },
                                enabled = !uiState.vault.encryptedGithubPat.isNullOrBlank() && !uiState.isGitHubMutating,
                                variant = PrismaticButtonVariant.Primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        DevChoiceChipRow(
                            items = IssueStateFilter.entries,
                            selected = issueFilter,
                            labelOf = { it.label },
                            onSelect = { issueFilter = it }
                        )
                    }
                }
                if (filteredIssues.isEmpty()) {
                    item(key = "gh_issues_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No issues matching filter.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(filteredIssues, key = { "iss-${it.number}" }) { issue ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${issue.number} ${issue.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                DevToneBadge(
                                    label = issue.state.replaceFirstChar { it.uppercase() },
                                    tone = issue.state
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${issue.author}, ${issue.commentsCount} comments, ${formatIsoShort(issue.updatedAtIso)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                            if (issue.labels.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Labels: ${issue.labels.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondaryText
                                )
                            }
                            if (issue.body.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = issue.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primaryText,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isOpen = issue.state.equals("open", true)
                                PrismaticButton(
                                    text = if (isOpen) "Close" else "Reopen",
                                    onClick = {
                                        viewModel.toggleGitHubIssueState(snap.repoSlug, issue.number, open = !isOpen)
                                    },
                                    enabled = !uiState.vault.encryptedGithubPat.isNullOrBlank() && !uiState.isGitHubMutating,
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                PrismaticButton(
                                    text = "Comment",
                                    onClick = { commentTargetIssue = issue },
                                    enabled = !uiState.vault.encryptedGithubPat.isNullOrBlank() && !uiState.isGitHubMutating,
                                    variant = PrismaticButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            GitHubModuleTab.PRS -> {
                if (snap.pullRequests.isEmpty()) {
                    item(key = "gh_prs_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No pull requests observed.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(snap.pullRequests, key = { "pr-${it.number}" }) { pr ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${pr.number} ${pr.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                DevToneBadge(
                                    label = if (pr.draft) "Draft" else pr.state.replaceFirstChar { it.uppercase() },
                                    tone = pr.state
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${pr.author}, ${formatIsoShort(pr.updatedAtIso)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                            Spacer(Modifier.height(6.dp))
                            PrismaticButton(
                                text = "Open PR",
                                onClick = { context.openHttpsUrl(pr.htmlUrl) },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }

            GitHubModuleTab.COMMITS -> {
                if (snap.commits.isEmpty()) {
                    item(key = "gh_commits_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No commits loaded.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(snap.commits, key = { "cmt-${it.shaShort}-${it.timestampIso.orEmpty()}" }) { cmt ->
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cmt.messageHeadline,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                DevToneBadge(label = cmt.shaShort, tone = "neutral")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${cmt.authorName}, ${formatIsoShort(cmt.timestampIso)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                            Spacer(Modifier.height(6.dp))
                            PrismaticButton(
                                text = "Open Commit",
                                onClick = { context.openHttpsUrl(cmt.htmlUrl) },
                                variant = PrismaticButtonVariant.Ghost
                            )
                        }
                    }
                }
            }

            GitHubModuleTab.WORKFLOWS -> {
                if (snap.workflowRuns.isEmpty()) {
                    item(key = "gh_wf_empty") {
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Text("No Actions workflow runs loaded.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                } else {
                    items(snap.workflowRuns, key = { "ghwf-${it.id}" }) { run ->
                        val jobs = uiState.inspectedWorkflowJobs[run.id].orEmpty()
                        SpatialGlassCard(modifier = Modifier.fillMaxWidth(), tier = SpatialGlassTier.Standard) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${run.workflowName} #${run.id}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "${run.branchOrTag}, ${formatIsoShort(run.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                                DevToneBadge(label = run.conclusion, tone = run.conclusion)
                            }
                            if (jobs.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                jobs.forEach { job ->
                                    val failedNote = if (job.failedSteps.isNotEmpty()) {
                                        "Failed steps: ${job.failedSteps.joinToString(", ")}"
                                    } else job.conclusion
                                    DevKeyValueRow(label = job.name, value = failedNote)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrismaticButton(
                                    text = "Inspect Jobs",
                                    onClick = { viewModel.inspectWorkflowJobs(snap.repoSlug, run.id) },
                                    variant = PrismaticButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                PrismaticButton(
                                    text = "Open Logs",
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
}

@Composable
private fun CreateGitHubIssueDialog(
    repoSlug: String,
    onDismiss: () -> Unit,
    onSubmit: (title: String, body: String, labels: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var labelsInput by remember { mutableStateOf("bug") }
    val ready = title.trim().length >= 3

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Issue ($repoSlug)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrismaticTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = "Title",
                    placeholder = "Short issue summary"
                )
                PrismaticTextField(
                    value = body,
                    onValueChange = { body = it.take(2000) },
                    label = "Details",
                    placeholder = "Steps, version, expected behavior",
                    singleLine = false
                )
                PrismaticTextField(
                    value = labelsInput,
                    onValueChange = { labelsInput = it },
                    label = "Labels (comma separated)",
                    placeholder = "bug, triage"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    val labels = labelsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSubmit(title.trim(), body.trim(), labels)
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddIssueCommentDialog(
    repoSlug: String,
    issueNumber: Int,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comment #$issueNumber ($repoSlug)") },
        text = {
            PrismaticTextField(
                value = body,
                onValueChange = { body = it.take(1500) },
                label = "Comment",
                placeholder = "Triage update or verification note",
                singleLine = false
            )
        },
        confirmButton = {
            TextButton(
                enabled = body.trim().length >= 2,
                onClick = { onSubmit(body.trim()) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
