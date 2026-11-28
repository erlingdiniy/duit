package com.diniy.duit.devcenter

enum class FeedbackPriority(val label: String, val rank: Int) {
    CRITICAL("Critical", 4),
    HIGH("High", 3),
    NORMAL("Normal", 2),
    LOW("Low", 1);

    companion object {
        fun fromStored(raw: String?): FeedbackPriority =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NORMAL
    }
}

enum class FeedbackTriageStatus(val label: String, val sheetStatus: String) {
    PENDING("Pending", "New"),
    IN_PROGRESS("In Progress", "Reviewed"),
    RESOLVED("Resolved", "Done"),
    ARCHIVED("Archived", "Won't Fix");

    companion object {
        fun fromStored(raw: String?, fallbackSheetStatus: String = ""): FeedbackTriageStatus {
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }?.let { return it }
            return when (fallbackSheetStatus.trim().lowercase()) {
                "done", "resolved" -> RESOLVED
                "reviewed", "planned", "in progress" -> IN_PROGRESS
                "won't fix", "wont fix", "archived" -> ARCHIVED
                else -> PENDING
            }
        }
    }
}

enum class FeedbackSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    PRIORITY("Priority"),
    UNREAD_FIRST("Unread First")
}

data class FeedbackTriageRecord(
    val feedbackId: String,
    val isRead: Boolean = false,
    val priority: FeedbackPriority = FeedbackPriority.NORMAL,
    val status: FeedbackTriageStatus = FeedbackTriageStatus.PENDING,
    val notes: String = "",
    val linkedIssueNumber: Int? = null,
    val linkedIssueUrl: String? = null,
    val updatedAtIso: String = ""
)

data class TriagedFeedbackItem(
    val entry: FeedbackEntry,
    val triage: FeedbackTriageRecord,
    val duplicateGroupId: String? = null,
    val duplicateOfIds: List<String> = emptyList()
) {
    val isUnread: Boolean get() = !triage.isRead
    val isBug: Boolean get() = entry.category == "Bug Report"
    val isOpenBug: Boolean get() = isBug && (triage.status == FeedbackTriageStatus.PENDING || triage.status == FeedbackTriageStatus.IN_PROGRESS)
    val hasContactEmail: Boolean get() = entry.email.isNotBlank()
}

data class BroadcastTemplate(
    val id: String,
    val name: String,
    val category: BroadcastCategory,
    val audience: BroadcastAudience,
    val route: BroadcastDestination,
    val title: String,
    val body: String
)

data class BroadcastDraft(
    val id: String,
    val category: BroadcastCategory,
    val audience: BroadcastAudience,
    val route: BroadcastDestination,
    val title: String,
    val body: String,
    val updatedAtIso: String
)

data class ScheduledBroadcast(
    val id: String,
    val category: BroadcastCategory,
    val audience: BroadcastAudience,
    val route: BroadcastDestination,
    val title: String,
    val body: String,
    val scheduledEpochMs: Long,
    val status: String,
    val createdAtIso: String
)

data class WorkflowJobInfo(
    val id: String,
    val name: String,
    val status: String,
    val conclusion: String,
    val failedSteps: List<String> = emptyList(),
    val htmlUrl: String = ""
)

data class InstalledApkIntegrity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: String,
    val matchesOriginalCert: Boolean,
    val hasPrivateDevMeta: Boolean,
    val statusSummary: String
)

data class RemoteConfigParam(
    val key: String,
    val valueType: String,
    val defaultValue: String,
    val description: String = "",
    val isSafeKey: Boolean = true
)

data class RemoteConfigCondition(
    val name: String,
    val expression: String,
    val tagColor: String = ""
)

data class RemoteConfigVersionInfo(
    val versionNumber: String,
    val updateTimeIso: String,
    val updateUserEmail: String = "",
    val updateOrigin: String = "",
    val updateType: String = "",
    val description: String = ""
)

sealed interface RemoteConfigSourceState {
    data object Loading : RemoteConfigSourceState
    data class Unavailable(val reason: String, val canSignInWithGoogle: Boolean) : RemoteConfigSourceState
    data class Ready(
        val etag: String,
        val versionNumber: String,
        val parameters: List<RemoteConfigParam>,
        val conditions: List<RemoteConfigCondition>,
        val versions: List<RemoteConfigVersionInfo>,
        val syncedAtIso: String
    ) : RemoteConfigSourceState
    data class Error(val message: String, val syncedAtIso: String? = null) : RemoteConfigSourceState
}

data class GitHubRepoOverview(
    val fullName: String,
    val isPrivate: Boolean,
    val visibility: String,
    val stars: Int,
    val forks: Int,
    val watchers: Int,
    val openIssuesCount: Int,
    val defaultBranch: String,
    val updatedAtIso: String?,
    val htmlUrl: String,
    val privacyAlert: String? = null
)

data class GitHubIssueItem(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val author: String,
    val labels: List<String>,
    val commentsCount: Int,
    val updatedAtIso: String?,
    val htmlUrl: String
)

data class GitHubPrItem(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean,
    val author: String,
    val updatedAtIso: String?,
    val htmlUrl: String
)

data class GitHubCommitItem(
    val shaShort: String,
    val messageHeadline: String,
    val authorName: String,
    val timestampIso: String?,
    val htmlUrl: String
)

data class GitHubRepoSnapshot(
    val repoSlug: String,
    val overview: GitHubRepoOverview? = null,
    val issues: List<GitHubIssueItem> = emptyList(),
    val pullRequests: List<GitHubPrItem> = emptyList(),
    val commits: List<GitHubCommitItem> = emptyList(),
    val releases: List<ReleaseInfo> = emptyList(),
    val workflowRuns: List<WorkflowRunInfo> = emptyList(),
    val failedJobDetails: Map<String, List<WorkflowJobInfo>> = emptyMap(),
    val rateLimitLimit: Int? = null,
    val rateLimitRemaining: Int? = null,
    val rateLimitResetIso: String? = null,
    val syncedAtIso: String? = null,
    val error: String? = null
)

enum class InfraProbeKind(val label: String) {
    VERIFIED_REMOTE("Verified Remote"),
    LOCAL_SDK("Local SDK Only"),
    UNCONFIGURED("Unconfigured")
}

data class InfraServiceStatus(
    val id: String,
    val name: String,
    val state: String,
    val probeKind: InfraProbeKind,
    val summary: String,
    val detail: String = "",
    val checkedAtIso: String? = null,
    val quotaText: String? = null
)

data class InfraMonitorSnapshot(
    val services: List<InfraServiceStatus> = emptyList(),
    val githubRateLimit: Int? = null,
    val githubRateRemaining: Int? = null,
    val githubRateResetIso: String? = null,
    val ga4HourlyQuotaRemaining: Int? = null,
    val ga4DailyQuotaRemaining: Int? = null,
    val oauthExpiresAtMs: Long = 0L,
    val oauthAutoRefreshReady: Boolean = false,
    val lastSyncIso: String? = null
)
