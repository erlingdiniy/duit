package com.diniy.duit.devcenter

/**
 * Sections inside the Personal Edition Developer Center.
 */
enum class DevCenterSection(val label: String) {
    OVERVIEW("Overview"),
    NOTIFICATIONS("Notifications"),
    HISTORY("History"),
    RELEASES("Releases"),
    FEEDBACK("Feedback"),
    SETTINGS("Settings")
}

enum class DevAutoLockTimeout(val timeoutMs: Long, val label: String) {
    IMMEDIATE(0L, "Immediate"),
    ONE_MINUTE(60_000L, "1 min"),
    FIVE_MINUTES(300_000L, "5 min"),
    FIFTEEN_MINUTES(900_000L, "15 min");

    companion object {
        fun fromTimeoutMs(ms: Long): DevAutoLockTimeout =
            entries.firstOrNull { it.timeoutMs == ms } ?: FIVE_MINUTES
    }
}

enum class BroadcastCategory(val apiValue: String, val label: String) {
    ANNOUNCEMENT("announcement", "Announcement"),
    MAINTENANCE("maintenance", "Maintenance"),
    UPDATE("update", "Update"),
    GENERAL("general", "General");

    companion object {
        fun fromApiValue(raw: String): BroadcastCategory? =
            entries.firstOrNull { it.apiValue.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class BroadcastAudience(
    val apiValue: String,
    val label: String,
    val topics: List<String>,
    val confirmationPhrase: String
) {
    BETA("beta", "Beta", listOf("duit_beta_broadcasts"), "SEND BETA"),
    STABLE("stable", "Stable", listOf("duit_announcements"), "SEND STABLE"),
    BOTH("both", "Both", listOf("duit_announcements", "duit_beta_broadcasts"), "SEND ALL");

    companion object {
        fun fromApiValue(raw: String): BroadcastAudience? =
            entries.firstOrNull { it.apiValue.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class BroadcastDestination(val apiValue: String, val label: String) {
    HOME("home", "Home"),
    ACTIVITY("activity", "Activity"),
    PLANNING("planning", "Planning"),
    BUDGETS("budgets", "Budgets"),
    BILLS("bills", "Bills"),
    INSIGHTS("insights", "Insights"),
    CALENDAR("calendar", "Calendar");

    companion object {
        fun fromApiValue(raw: String): BroadcastDestination? =
            entries.firstOrNull { it.apiValue.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class KillSwitchTarget(
    val id: String,
    val label: String,
    val variableName: String,
    val defaultRepo: String,
    val enablePhrase: String,
    val disablePhrase: String
) {
    CUSTOM_BROADCAST(
        id = "custom_broadcast",
        label = "Custom Broadcasts",
        variableName = "ENABLE_CUSTOM_FCM_DISPATCH",
        defaultRepo = DEFAULT_CONSOLE_REPO,
        enablePhrase = "ENABLE CUSTOM BROADCAST",
        disablePhrase = "DISABLE CUSTOM BROADCAST"
    ),
    RELEASE_AUTOMATION(
        id = "release_automation",
        label = "Release Automation",
        variableName = "ENABLE_LIVE_FCM_DISPATCH",
        defaultRepo = DEFAULT_DISTRIBUTION_REPO,
        enablePhrase = "ENABLE RELEASE AUTOMATION",
        disablePhrase = "DISABLE RELEASE AUTOMATION"
    );

    fun confirmationPhrase(enable: Boolean): String = if (enable) enablePhrase else disablePhrase
}

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val versionCode: Long?,
    val title: String,
    val channel: String,
    val prerelease: Boolean,
    val publishedAt: String?,
    val htmlUrl: String,
    val assetName: String?,
    val assetSizeBytes: Long?,
    val downloadCount: Int,
    val assetDownloadUrl: String?,
    val sha256: String?,
    val notificationStatus: String,
    val changelogBullets: List<String>
)

data class WorkflowRunInfo(
    val id: String,
    val repo: String,
    val workflowName: String,
    val eventTitle: String,
    val status: String,
    val conclusion: String,
    val createdAt: String?,
    val htmlUrl: String,
    val branchOrTag: String
)

data class BroadcastHistoryEntry(
    val id: String,
    val kind: String,
    val category: String,
    val broadcastId: String,
    val title: String,
    val body: String,
    val audience: String,
    val topics: List<String>,
    val route: String,
    val status: String,
    val statusLabel: String,
    val timestampIso: String,
    val note: String,
    val workflowUrl: String? = null
)

data class AuditLogEntry(
    val id: String,
    val timestampIso: String,
    val actor: String,
    val action: String,
    val target: String,
    val status: String,
    val summary: String
)

data class FeedbackEntry(
    val id: String,
    val timestampIso: String,
    val category: String,
    val message: String,
    val email: String,
    val appVersion: String,
    val status: String
)

sealed interface FeedbackSourceState {
    data class Unconfigured(val guideMessage: String) : FeedbackSourceState
    data class Ready(
        val sourceLabel: String,
        val items: List<FeedbackEntry>,
        val notice: String? = null
    ) : FeedbackSourceState
    data class Error(val message: String) : FeedbackSourceState
}

data class ConnectionHealth(
    val connected: Boolean = false,
    val authenticatedUser: String? = null,
    val distributionRepoOk: Boolean = false,
    val consoleRepoOk: Boolean = false,
    val ledgerOk: Boolean = false,
    val rateLimitRemaining: Int? = null,
    val statusSummary: String = "Connect a fine-grained GitHub token in Settings to verify live infrastructure.",
    val checkedAtIso: String? = null
)

data class AutomationKillSwitches(
    val customBroadcastEnabled: Boolean? = null,
    val releaseAutomationEnabled: Boolean? = null
)

data class DashboardSnapshot(
    val health: ConnectionHealth = ConnectionHealth(),
    val releases: List<ReleaseInfo> = emptyList(),
    val workflowRuns: List<WorkflowRunInfo> = emptyList(),
    val releaseBroadcasts: List<BroadcastHistoryEntry> = emptyList(),
    val killSwitches: AutomationKillSwitches = AutomationKillSwitches(),
    val minExclusiveVersionCode: Long? = null,
    val degradedNotice: String? = null
) {
    val latestStable: ReleaseInfo? get() = releases.firstOrNull { !it.prerelease }
    val latestBeta: ReleaseInfo? get() = releases.firstOrNull { it.prerelease }
    val totalApkDownloads: Int get() = releases.sumOf { it.downloadCount }
}

const val DEFAULT_DISTRIBUTION_REPO = "erlingdiniy/duit-beta"
const val DEFAULT_CONSOLE_REPO = "erlingdiniy/duit-developer-console"
const val DEFAULT_FEEDBACK_RANGE = "Feedback!A1:F200"
const val ANDROID_BROADCAST_CHANNEL_ID = "duit_developer_broadcasts"
