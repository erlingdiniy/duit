package com.diniy.duit.devcenter

import com.diniy.duit.BuildConfig
import com.diniy.duit.analytics.UsageAnalytics
import com.diniy.duit.diagnostics.CrashReporting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) {
    ALL("All"),
    CUSTOM("Custom"),
    RELEASES("Releases"),
    AUDIT("Audit Log")
}

enum class ReleaseChannelFilter(val label: String) {
    ALL("All"),
    STABLE("Stable"),
    BETA("Beta"),
    PRIVATE_DEV("Private Dev")
}

data class PendingKillSwitchModal(
    val target: KillSwitchTarget,
    val nextEnabled: Boolean
)

data class ComposerFormState(
    val category: BroadcastCategory = BroadcastCategory.ANNOUNCEMENT,
    val audience: BroadcastAudience = BroadcastAudience.BETA,
    val route: BroadcastDestination = BroadcastDestination.HOME,
    val title: String = "",
    val body: String = "",
    val broadcastId: String = DevCenterValidators.generateBroadcastId(),
    val showLiveConfirmDialog: Boolean = false
)

data class DevCenterUiState(
    val activeSection: DevCenterSection = DevCenterSection.OVERVIEW,
    val isRefreshing: Boolean = false,
    val isDispatching: Boolean = false,
    val isUpdatingKillSwitch: Boolean = false,
    val statusBanner: String? = null,
    val errorBanner: String? = null,
    val vault: DevCenterVaultState = DevCenterVaultState(),
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val latestCustomRun: WorkflowRunInfo? = null,
    val composer: ComposerFormState = ComposerFormState(),
    val cooldownRemainingSeconds: Int = 0,
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val releaseFilter: ReleaseChannelFilter = ReleaseChannelFilter.ALL,
    val feedbackState: FeedbackSourceState = FeedbackSourceState.Unconfigured(
        "Import a CSV export or configure Google Sheets access in Settings."
    ),
    val feedbackSearchQuery: String = "",
    val feedbackCategoryFilter: String = "All",
    val feedbackStatusFilter: FeedbackTriageStatus? = null,
    val feedbackPriorityFilter: FeedbackPriority? = null,
    val feedbackUnreadOnly: Boolean = false,
    val feedbackSortOrder: FeedbackSortOrder = FeedbackSortOrder.NEWEST,
    val isFeedbackMutating: Boolean = false,
    val analyticsDateRange: AnalyticsDateRange = AnalyticsDateRange.THIRTY_DAYS,
    val analyticsState: AnalyticsSourceState = AnalyticsSourceState.Unavailable(
        summary = "Connect Google Analytics read-only access in Settings or view charts in Firebase.",
        hasPropertyId = false,
        hasToken = false
    ),
    val isRefreshingAnalytics: Boolean = false,
    val remoteConfigState: RemoteConfigSourceState = RemoteConfigSourceState.Unavailable(
        reason = "Sign in with Google Remote Config access in Settings to manage flags.",
        canSignInWithGoogle = false
    ),
    val editedRemoteParams: Map<String, String> = emptyMap(),
    val isPublishingRemoteConfig: Boolean = false,
    val selectedGitHubRepo: String = DEFAULT_DISTRIBUTION_REPO,
    val gitHubRepoSnapshot: GitHubRepoSnapshot = GitHubRepoSnapshot(repoSlug = DEFAULT_DISTRIBUTION_REPO),
    val inspectedWorkflowJobs: Map<String, List<WorkflowJobInfo>> = emptyMap(),
    val isGitHubMutating: Boolean = false,
    val apkIntegrity: InstalledApkIntegrity? = null,
    val infraSnapshot: InfraMonitorSnapshot = InfraMonitorSnapshot(),
    val pendingKillSwitchModal: PendingKillSwitchModal? = null,
    val isEnrolling: Boolean = false,
    val enrollmentError: String? = null,
    val backendScheduledBroadcasts: List<BackendAutoScheduledBroadcast> = emptyList(),
    val backendHistory: List<BroadcastHistoryEntry> = emptyList(),
    val backendAuditLogs: List<AuditLogEntry> = emptyList(),
    val enrolledDevices: List<EnrolledDeviceInfo> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val openFeatureRequestCount: Int = 0,
    val spamFeedbackCount: Int = 0
) {
    val backendMode: ZeroConfigBackendMode
        get() = when {
            vault.isBackendEnrolled -> ZeroConfigBackendMode.CONNECTED
            vault.legacyManualBypass -> ZeroConfigBackendMode.LEGACY_MANUAL
            vault.backendUrlOverride.isNotBlank() || BuildConfig.DEV_BACKEND_URL.isNotBlank() -> ZeroConfigBackendMode.NEEDS_ENROLLMENT
            else -> ZeroConfigBackendMode.NOT_CONFIGURED
        }

    val combinedBroadcastHistory: List<BroadcastHistoryEntry>
        get() = (backendHistory + vault.customHistory + dashboard.releaseBroadcasts)
            .distinctBy { it.broadcastId.ifBlank { it.id } }
            .sortedByDescending { it.timestampIso }

    val combinedAuditLog: List<AuditLogEntry>
        get() = (backendAuditLogs + vault.auditLog)
            .distinctBy { it.id }
            .sortedByDescending { it.timestampIso }

    val triagedFeedback: List<TriagedFeedbackItem>
        get() = when (val fb = feedbackState) {
            is FeedbackSourceState.Ready -> DevFeedbackTriageEngine.mergeAndCluster(fb.items, vault.feedbackTriageMap)
            else -> emptyList()
        }

    val filteredFeedback: List<TriagedFeedbackItem>
        get() = DevFeedbackTriageEngine.filterAndSort(
            items = triagedFeedback,
            searchQuery = feedbackSearchQuery,
            categoryFilter = feedbackCategoryFilter,
            statusFilter = feedbackStatusFilter,
            priorityFilter = feedbackPriorityFilter,
            unreadOnly = feedbackUnreadOnly,
            sortOrder = feedbackSortOrder
        )

    val unreadFeedbackCount: Int get() = triagedFeedback.count { it.isUnread }
    val openBugReportCount: Int get() = triagedFeedback.count { it.isOpenBug }

    val tokenExpirationNotice: String?
        get() {
            if (vault.isBackendEnrolled) return null
            val exp = vault.analyticsTokenExpiresAtMs
            if (exp <= 0L || vault.encryptedAnalyticsToken.isNullOrBlank()) return null
            val hasRefresh = !vault.encryptedAnalyticsRefreshToken.isNullOrBlank()
            val remainingMs = exp - System.currentTimeMillis()
            return when {
                remainingMs <= 0L && !hasRefresh -> "Google OAuth access token expired. Re-sign in under Developer Settings."
                remainingMs in 1L..600_000L && !hasRefresh -> "Google OAuth access token expires soon."
                else -> null
            }
        }
}

/**
 * Coordinates Developer Center 2.0 Mission Control telemetry, analytics, feedback triage,
 * Remote Config, multi-repo GitHub management, notification 2.0 composer, APK integrity,
 * infrastructure probes, and security vault controls.
 */
class DevCenterViewModel(
    private val vault: DevCenterVault,
    private val githubClient: DevCenterGitHubClient,
    private val feedbackClient: DevCenterFeedbackClient,
    private val analyticsClient: DevCenterAnalyticsClient = DevCenterAnalyticsClient(HttpsUrlConnectionTransport()),
    private val analyticsOAuth: DevCenterAnalyticsOAuth = DevCenterAnalyticsOAuth(HttpsUrlConnectionTransport()),
    val authManager: DevCenterAuthManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val remoteConfigClient: DevCenterRemoteConfigClient = DevCenterRemoteConfigClient(HttpsUrlConnectionTransport()),
    private val githubManager: DevCenterGitHubManager = DevCenterGitHubManager(HttpsUrlConnectionTransport(), githubClient),
    private val singleDeviceFcmClient: DevCenterSingleDeviceFcmClient = DevCenterSingleDeviceFcmClient(HttpsUrlConnectionTransport()),
    private val infraMonitor: DevCenterInfraMonitor = DevCenterInfraMonitor(HttpsUrlConnectionTransport()),
    private val apkIntegrityProvider: () -> InstalledApkIntegrity? = { null },
    private val feedbackUrlProvider: () -> String = { BuildConfig.FEEDBACK_URL },
    private val onScheduleBroadcast: (ScheduledBroadcast) -> Unit = {},
    private val onCancelScheduledBroadcast: (String) -> Unit = {},
    private val onSyncFeedbackAlertsWorker: (Boolean) -> Unit = {},
    private val onPostUnseenFeedbackAlert: (Int) -> Unit = {},
    private val backendClient: DevBackendClient? = null,
    private val onClearHardwareKey: () -> Unit = {}
) {
    private val _uiState = MutableStateFlow(DevCenterUiState())
    val uiState: StateFlow<DevCenterUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            vault.observeState().collectLatest { v ->
                _uiState.update { cur ->
                    val hydratedAnalytics = if (cur.analyticsState is AnalyticsSourceState.Unavailable && !v.cachedAnalyticsJson.isNullOrBlank()) {
                        DevCenterAnalyticsClient.decodeCachedSnapshot(v.cachedAnalyticsJson)?.let {
                            AnalyticsSourceState.Ready(it)
                        } ?: cur.analyticsState
                    } else cur.analyticsState
                    cur.copy(
                        vault = v,
                        analyticsState = hydratedAnalytics,
                        cooldownRemainingSeconds = DevCenterValidators.cooldownRemainingSeconds(
                            v.lastLiveDispatchMs,
                            nowMs()
                        )
                    )
                }
            }
        }
        scope.launch {
            while (true) {
                delay(1_000L)
                val lastLive = _uiState.value.vault.lastLiveDispatchMs
                val cooldown = DevCenterValidators.cooldownRemainingSeconds(lastLive, nowMs())
                if (cooldown != _uiState.value.cooldownRemainingSeconds) {
                    _uiState.update { it.copy(cooldownRemainingSeconds = cooldown) }
                }
                authManager.checkInactivityTimeout()
            }
        }
    }

    fun selectSection(section: DevCenterSection) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(activeSection = section, errorBanner = null) }
    }

    fun clearBanners() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null, enrollmentError = null) }
    }

    fun enrollDevice(backendUrl: String, code: String, label: String) {
        authManager.recordUserActivity()
        val client = backendClient
        if (client == null) {
            _uiState.update { it.copy(enrollmentError = "Backend client unavailable.") }
            return
        }
        if (_uiState.value.isEnrolling) return
        _uiState.update { it.copy(isEnrolling = true, enrollmentError = null, errorBanner = null) }
        scope.launch {
            val res = client.enrollDevice(backendUrl, code, label)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isEnrolling = false,
                        enrollmentError = null,
                        statusBanner = "Connected to Mission Control."
                    )
                }
                if (authManager.uiState.value.stage == DevLockStage.UNLOCKED) {
                    refreshAll()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isEnrolling = false,
                        enrollmentError = DevCenterRedactor.redact(err.message ?: "Enrollment failed.", listOf(code))
                    )
                }
            }
        }
    }

    fun enterLegacyManualMode() {
        authManager.recordUserActivity()
        scope.launch {
            vault.setLegacyManualBypass(true)
            _uiState.update { it.copy(enrollmentError = null) }
            refreshAll()
        }
    }

    fun unenrollThisDevice() {
        authManager.recordUserActivity()
        val v = _uiState.value.vault
        val deviceId = v.enrolledDeviceId
        scope.launch {
            if (backendClient != null && deviceId.isNotBlank()) {
                runCatching { backendClient.revokeDevice(deviceId) }
            }
            runCatching { onClearHardwareKey() }
            vault.clearBackendEnrollment()
            _uiState.update {
                it.copy(
                    enrolledDevices = emptyList(),
                    backendAuditLogs = emptyList(),
                    backendHistory = emptyList(),
                    backendScheduledBroadcasts = emptyList(),
                    statusBanner = "Device disconnected and hardware key cleared."
                )
            }
        }
    }

    fun triggerBackendSync() {
        authManager.recordUserActivity()
        val client = backendClient ?: return
        if (!_uiState.value.vault.isBackendEnrolled) return
        scope.launch {
            runCatching { client.triggerSync() }
                .onSuccess { msg -> _uiState.update { it.copy(statusBanner = msg, errorBanner = null) } }
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.message ?: "Cloud sync trigger failed.") } }
        }
    }

    fun refreshEnrolledDevices() {
        authManager.recordUserActivity()
        val client = backendClient ?: return
        if (!_uiState.value.vault.isBackendEnrolled) return
        _uiState.update { it.copy(isLoadingDevices = true) }
        scope.launch {
            runCatching { client.fetchEnrolledDevices() }
                .onSuccess { (_, list) -> _uiState.update { it.copy(isLoadingDevices = false, enrolledDevices = list) } }
                .onFailure { err -> _uiState.update { it.copy(isLoadingDevices = false, errorBanner = err.message) } }
        }
    }

    fun revokeEnrolledDevice(targetDeviceId: String) {
        authManager.recordUserActivity()
        val client = backendClient ?: return
        if (!_uiState.value.vault.isBackendEnrolled) return
        scope.launch {
            runCatching { client.revokeDevice(targetDeviceId) }
                .onSuccess { revokedSelf ->
                    if (revokedSelf) {
                        runCatching { onClearHardwareKey() }
                        _uiState.update {
                            it.copy(enrolledDevices = emptyList(), statusBanner = "Current device revoked and unlinked.")
                        }
                    } else {
                        _uiState.update { it.copy(statusBanner = "Revoked device $targetDeviceId.") }
                        refreshEnrolledDevices()
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.message ?: "Device revoke failed.") } }
        }
    }

    fun refreshAll() {
        authManager.recordUserActivity()
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorBanner = null) }

        scope.launch {
            val v = vault.getState()
            val syncIso = isoUtcNow(nowMs())
            val integrity = runCatching { apkIntegrityProvider() }.getOrNull()
            if (backendClient != null && v.isBackendEnrolled) {
                refreshAllViaBackend(v, integrity, syncIso)
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()

            coroutineScope {
                val snapDef = async {
                    githubClient.fetchDashboardSnapshot(
                        pat = pat,
                        distributionRepo = v.distributionRepo,
                        consoleRepo = v.consoleRepo,
                        privateUpdateRepo = v.privateUpdateRepo
                    )
                }
                val customRunDef = async {
                    if (pat.isNotEmpty()) githubClient.fetchLatestCustomWorkflowRun(pat, v.consoleRepo) else null
                }
                val fbDef = async {
                    val sheetsToken = vault.readSheetsToken()
                    feedbackClient.loadFeedback(
                        sheetId = v.feedbackSheetId,
                        range = v.feedbackSheetRange,
                        sheetsAccessToken = sheetsToken,
                        importedCsvRaw = v.importedCsvRaw,
                        importedCsvLabel = v.importedCsvLabel,
                        tokenResolver = { force -> resolveValidAnalyticsToken(force) }
                    )
                }
                val gaDef = async { loadAnalyticsWithAutoRefresh(v) }
                val rcDef = async { loadRemoteConfigInternal(v, syncIso) }
                val repoSnapDef = async {
                    githubManager.fetchRepoSnapshot(pat, _uiState.value.selectedGitHubRepo)
                }

                val snapshot = snapDef.await()
                val customRun = customRunDef.await()
                val fbState = fbDef.await()
                val rawGaState = gaDef.await()
                val rcState = rcDef.await()
                val repoSnap = repoSnapDef.await()

                val gaState = resolveAnalyticsWithCache(v, rawGaState, syncIso)
                if (gaState is AnalyticsSourceState.Ready && !gaState.snapshot.isCached) {
                    vault.saveCachedAnalyticsJson(
                        DevCenterAnalyticsClient.encodeSnapshot(gaState.snapshot),
                        syncIso
                    )
                } else {
                    vault.saveLastFullSyncIso(syncIso)
                }

                if (fbState is FeedbackSourceState.Ready) {
                    val unseen = fbState.items.filter { it.id !in v.seenFeedbackIds }
                    if (unseen.isNotEmpty() && v.seenFeedbackIds.isNotEmpty()) {
                        runCatching { onPostUnseenFeedbackAlert(unseen.size) }
                    }
                    vault.markFeedbackSeen(fbState.items.map { it.id })
                }

                val infra = infraMonitor.checkInfrastructure(
                    vaultState = v,
                    pat = pat,
                    feedbackUrl = feedbackUrlProvider(),
                    ga4State = gaState,
                    remoteConfigState = rcState,
                    integrity = integrity,
                    fcmInitialized = BuildConfig.FCM_ENABLED,
                    crashlyticsOptedIn = CrashReporting.isCollectionEnabled(),
                    analyticsOptedIn = UsageAnalytics.isCollectionEnabled(),
                    nowMs = nowMs()
                )

                val editedMap = if (rcState is RemoteConfigSourceState.Ready) {
                    rcState.parameters.associate { it.key to it.defaultValue }
                } else _uiState.value.editedRemoteParams

                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        dashboard = snapshot,
                        latestCustomRun = customRun,
                        feedbackState = fbState,
                        analyticsState = gaState,
                        remoteConfigState = rcState,
                        editedRemoteParams = editedMap,
                        gitHubRepoSnapshot = repoSnap,
                        apkIntegrity = integrity,
                        infraSnapshot = infra,
                        errorBanner = snapshot.degradedNotice
                    )
                }
            }
        }
    }

    private suspend fun refreshAllViaBackend(
        v: DevCenterVaultState,
        integrity: InstalledApkIntegrity?,
        syncIso: String
    ) {
        val client = backendClient ?: return
        val dateRange = _uiState.value.analyticsDateRange
        val selectedRepo = _uiState.value.selectedGitHubRepo
        try {
            coroutineScope {
                val overviewDef = async { runCatching { client.fetchOverviewBundle(dateRange.days, dateRange) } }
                val fbDef = async { runCatching { client.fetchFeedback() } }
                val notifDef = async { runCatching { client.fetchNotificationsState() } }
                val ghDef = async { runCatching { client.fetchGitHubOverview(selectedRepo) } }
                val infraDef = async {
                    runCatching {
                        client.fetchInfraSnapshot(
                            integrity = integrity,
                            fcmInitialized = BuildConfig.FCM_ENABLED,
                            crashlyticsOptedIn = CrashReporting.isCollectionEnabled(),
                            analyticsOptedIn = UsageAnalytics.isCollectionEnabled()
                        )
                    }
                }
                val auditDef = async { runCatching { client.fetchAuditLogs() } }
                val devDef = async { runCatching { client.fetchEnrolledDevices() } }

                val overviewRes = overviewDef.await()
                val overviewErr = overviewRes.exceptionOrNull()
                if (overviewErr is DeviceRevokedException) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorBanner = overviewErr.message ?: "Device revoked. Re-enroll to continue."
                        )
                    }
                    return@coroutineScope
                }

                val overview = overviewRes.getOrNull()
                val fbRes = fbDef.await().getOrNull()
                val notifRes = notifDef.await().getOrNull()
                val ghRes = ghDef.await().getOrNull()
                val infraRes = infraDef.await().getOrNull()
                val auditRes = auditDef.await().getOrNull()
                val devicesRes = devDef.await().getOrNull()

                if (fbRes != null) {
                    val mergedItems = fbRes.entries.map { entry ->
                        val rec = fbRes.triageMap[entry.id] ?: FeedbackTriageRecord(feedbackId = entry.id)
                        TriagedFeedbackItem(entry = entry, triage = rec)
                    }
                    vault.syncFeedbackTriageFromBackend(mergedItems)
                    val unseen = fbRes.entries.filter { it.id !in v.seenFeedbackIds }
                    if (unseen.isNotEmpty() && v.seenFeedbackIds.isNotEmpty()) {
                        runCatching { onPostUnseenFeedbackAlert(unseen.size) }
                    }
                    vault.markFeedbackSeen(fbRes.entries.map { it.id })
                }

                val rawGa = overview?.analytics ?: AnalyticsSourceState.Error("Offline — showing cached analytics.", syncIso)
                val finalGa = resolveAnalyticsWithCache(v, rawGa, syncIso)
                if (finalGa is AnalyticsSourceState.Ready && !finalGa.snapshot.isCached) {
                    vault.saveCachedAnalyticsJson(
                        DevCenterAnalyticsClient.encodeSnapshot(finalGa.snapshot),
                        overview?.syncedAtIso ?: syncIso
                    )
                } else {
                    vault.saveLastFullSyncIso(overview?.syncedAtIso ?: syncIso)
                }

                val fbState = if (fbRes != null) {
                    FeedbackSourceState.Ready(sourceLabel = "Mission Control D1", items = fbRes.entries)
                } else {
                    _uiState.value.feedbackState
                }

                val dashBase = overview?.dashboard ?: _uiState.value.dashboard
                val updatedDash = dashBase.copy(
                    workflowRuns = notifRes?.workflowRuns ?: dashBase.workflowRuns,
                    killSwitches = notifRes?.killSwitches ?: dashBase.killSwitches,
                    degradedNotice = overviewErr?.let { DevCenterRedactor.redact(it.message ?: "Backend sync error.") }
                )

                val rcState = overview?.remoteConfig ?: _uiState.value.remoteConfigState
                val editedMap = if (rcState is RemoteConfigSourceState.Ready) {
                    rcState.parameters.associate { it.key to it.defaultValue }
                } else {
                    _uiState.value.editedRemoteParams
                }

                _uiState.update { cur ->
                    cur.copy(
                        isRefreshing = false,
                        dashboard = updatedDash,
                        latestCustomRun = notifRes?.workflowRuns?.firstOrNull() ?: cur.latestCustomRun,
                        feedbackState = fbState,
                        openFeatureRequestCount = fbRes?.openFeatureCount ?: cur.openFeatureRequestCount,
                        spamFeedbackCount = fbRes?.spamCount ?: cur.spamFeedbackCount,
                        analyticsState = finalGa,
                        remoteConfigState = rcState,
                        editedRemoteParams = editedMap,
                        gitHubRepoSnapshot = ghRes ?: cur.gitHubRepoSnapshot,
                        apkIntegrity = integrity,
                        infraSnapshot = infraRes ?: cur.infraSnapshot,
                        backendScheduledBroadcasts = notifRes?.autoScheduled ?: cur.backendScheduledBroadcasts,
                        backendHistory = notifRes?.history ?: cur.backendHistory,
                        backendAuditLogs = auditRes ?: overview?.recentAudit ?: cur.backendAuditLogs,
                        enrolledDevices = devicesRes?.second ?: cur.enrolledDevices,
                        errorBanner = updatedDash.degradedNotice
                    )
                }
            }
        } catch (err: Exception) {
            val cachedAnalytics = resolveAnalyticsWithCache(v, AnalyticsSourceState.Error(err.message ?: "Offline"), syncIso)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    analyticsState = cachedAnalytics,
                    errorBanner = DevCenterRedactor.redact(err.message ?: "Backend refresh failed.")
                )
            }
        }
    }

    private fun resolveAnalyticsWithCache(
        v: DevCenterVaultState,
        live: AnalyticsSourceState,
        syncIso: String
    ): AnalyticsSourceState {
        if (live is AnalyticsSourceState.Error) {
            val cached = DevCenterAnalyticsClient.decodeCachedSnapshot(v.cachedAnalyticsJson)
            if (cached != null) return AnalyticsSourceState.Ready(cached)
        }
        return live
    }

    // --- GitHub & Infrastructure ---

    fun saveGithubCredentials(
        rawPat: String,
        distributionRepo: String,
        consoleRepo: String,
        privateUpdateRepo: String = _uiState.value.vault.privateUpdateRepo,
        sourceRepo: String = _uiState.value.vault.sourceRepo,
        firebaseProjectId: String = _uiState.value.vault.firebaseProjectId
    ) {
        authManager.recordUserActivity()
        val cleanPat = rawPat.trim()
        if (cleanPat.isEmpty() && vault.getStateSyncOrNull()?.encryptedGithubPat == null) {
            _uiState.update { it.copy(errorBanner = "Enter a fine-grained GitHub PAT.") }
            return
        }
        if (!DevCenterValidators.isValidRepoSlug(distributionRepo) || !DevCenterValidators.isValidRepoSlug(consoleRepo)) {
            _uiState.update { it.copy(errorBanner = "Repository slugs must use owner/repo format.") }
            return
        }
        if (!DevCenterValidators.isValidPrivateUpdateRepo(privateUpdateRepo)) {
            _uiState.update { it.copy(errorBanner = "Private updates repo cannot be a public duit repository.") }
            return
        }

        scope.launch {
            val currentSecrets = buildList { if (cleanPat.isNotEmpty()) add(cleanPat) }
            runCatching {
                vault.saveRepositories(distributionRepo, consoleRepo, privateUpdateRepo)
                vault.saveSourceAndFirebaseConfig(sourceRepo, firebaseProjectId)
                if (cleanPat.isNotEmpty()) vault.saveGithubPat(cleanPat)
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = "operator",
                        action = "credentials.github_saved",
                        target = consoleRepo.trim(),
                        status = "ok",
                        summary = if (cleanPat.isNotEmpty()) {
                            "Saved Keystore-sealed GitHub PAT (${DevCenterRedactor.maskToken(cleanPat)})."
                        } else {
                            "Updated GitHub & Firebase repository configuration."
                        }
                    ),
                    extraSecrets = currentSecrets
                )
            }.onSuccess {
                _uiState.update { it.copy(statusBanner = "GitHub configuration saved.", errorBanner = null) }
                refreshAll()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorBanner = DevCenterRedactor.redact(err.message ?: "Save failed.", currentSecrets))
                }
            }
        }
    }

    fun runConnectionHealthCheck() {
        authManager.recordUserActivity()
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, statusBanner = null, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                val integrity = runCatching { apkIntegrityProvider() }.getOrNull()
                runCatching {
                    clientCheckHealthAndInfra(integrity)
                }.onSuccess { infra ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            infraSnapshot = infra,
                            statusBanner = "Mission Control Worker & WIF services verified."
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(isRefreshing = false, errorBanner = DevCenterRedactor.redact(err.message ?: "Health check failed."))
                    }
                }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val health = githubClient.checkConnectionHealth(pat, v.distributionRepo, v.consoleRepo)
            val infra = infraMonitor.checkInfrastructure(
                vaultState = v,
                pat = pat,
                feedbackUrl = feedbackUrlProvider(),
                ga4State = _uiState.value.analyticsState,
                remoteConfigState = _uiState.value.remoteConfigState,
                integrity = runCatching { apkIntegrityProvider() }.getOrNull(),
                fcmInitialized = BuildConfig.FCM_ENABLED,
                crashlyticsOptedIn = CrashReporting.isCollectionEnabled(),
                analyticsOptedIn = UsageAnalytics.isCollectionEnabled(),
                nowMs = nowMs()
            )
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = health.authenticatedUser ?: "operator",
                    action = "health.check",
                    target = v.consoleRepo,
                    status = if (health.connected) "ok" else "warning",
                    summary = health.statusSummary
                )
            )
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    dashboard = it.dashboard.copy(health = health),
                    infraSnapshot = infra,
                    statusBanner = if (health.connected) health.statusSummary else null,
                    errorBanner = if (!health.connected) health.statusSummary else null
                )
            }
        }
    }

    private suspend fun clientCheckHealthAndInfra(integrity: InstalledApkIntegrity?): InfraMonitorSnapshot {
        val client = requireNotNull(backendClient)
        return client.fetchInfraSnapshot(
            integrity = integrity,
            fcmInitialized = BuildConfig.FCM_ENABLED,
            crashlyticsOptedIn = CrashReporting.isCollectionEnabled(),
            analyticsOptedIn = UsageAnalytics.isCollectionEnabled()
        )
    }

    fun removeGithubPat() {
        authManager.recordUserActivity()
        scope.launch {
            vault.removeGithubPat()
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "credentials.github_removed",
                    target = "github_pat",
                    status = "ok",
                    summary = "Removed GitHub PAT from Developer Center vault."
                )
            )
            _uiState.update {
                it.copy(
                    statusBanner = "GitHub PAT removed.",
                    errorBanner = null,
                    dashboard = it.dashboard.copy(
                        health = ConnectionHealth(connected = false, statusSummary = "GitHub PAT removed."),
                        killSwitches = AutomationKillSwitches()
                    )
                )
            }
        }
    }

    fun clearAllCredentials() {
        authManager.recordUserActivity()
        scope.launch {
            val activeAccess = vault.readAnalyticsToken()
            if (!activeAccess.isNullOrBlank()) {
                runCatching { analyticsOAuth.revokeToken(activeAccess) }
            }
            vault.clearAllCredentials()
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "credentials.cleared_all",
                    target = "vault",
                    status = "ok",
                    summary = "Cleared and revoked Developer Center credentials (financial data untouched)."
                )
            )
            _uiState.update {
                it.copy(
                    statusBanner = "Developer credentials wiped & Google session revoked (financial data untouched).",
                    errorBanner = null,
                    dashboard = it.dashboard.copy(
                        health = ConnectionHealth(connected = false),
                        killSwitches = AutomationKillSwitches()
                    ),
                    remoteConfigState = RemoteConfigSourceState.Unavailable("Credentials cleared.", false)
                )
            }
            refreshAll()
        }
    }

    // --- Notifications 2.0 ---

    fun updateComposerCategory(cat: BroadcastCategory) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(category = cat)) }
    }

    fun updateComposerAudience(aud: BroadcastAudience) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(audience = aud)) }
    }

    fun updateComposerRoute(route: BroadcastDestination) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(route = route)) }
    }

    fun updateComposerTitle(title: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(title = title.take(100))) }
    }

    fun updateComposerBody(body: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(body = body.take(220))) }
    }

    fun updateComposerBroadcastId(id: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(composer = it.composer.copy(broadcastId = id.take(64))) }
    }

    fun regenerateBroadcastId() {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(composer = it.composer.copy(broadcastId = DevCenterValidators.generateBroadcastId(nowMs())))
        }
    }

    fun applyBroadcastTemplate(template: BroadcastTemplate) {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    category = template.category,
                    audience = template.audience,
                    route = template.route,
                    title = template.title,
                    body = template.body,
                    broadcastId = DevCenterValidators.generateBroadcastId(nowMs())
                ),
                statusBanner = "Loaded template \"${template.name}\"."
            )
        }
    }

    fun saveCurrentAsTemplate(templateName: String) {
        authManager.recordUserActivity()
        val cleanName = DevCenterValidators.cleanInputText(templateName, 40)
        val c = _uiState.value.composer
        if (cleanName.length < 2 || c.title.isBlank() || c.body.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Enter template name, title, and message.") }
            return
        }
        scope.launch {
            vault.saveBroadcastTemplate(
                BroadcastTemplate(
                    id = "tpl-${nowMs()}",
                    name = cleanName,
                    category = c.category,
                    audience = c.audience,
                    route = c.route,
                    title = c.title.trim(),
                    body = c.body.trim()
                )
            )
            _uiState.update { it.copy(statusBanner = "Saved template \"$cleanName\".") }
        }
    }

    fun deleteTemplate(id: String) {
        authManager.recordUserActivity()
        scope.launch { vault.deleteBroadcastTemplate(id) }
    }

    fun saveCurrentAsDraft() {
        authManager.recordUserActivity()
        val c = _uiState.value.composer
        if (c.title.isBlank() && c.body.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Enter a title or message before saving draft.") }
            return
        }
        val draft = BroadcastDraft(
            id = "draft-${nowMs()}",
            category = c.category,
            audience = c.audience,
            route = c.route,
            title = c.title.trim(),
            body = c.body.trim(),
            updatedAtIso = isoUtcNow(nowMs())
        )
        scope.launch {
            vault.saveBroadcastDraft(draft)
            if (backendClient != null && vault.getState().isBackendEnrolled) {
                runCatching { backendClient.saveRemoteDraft(draft) }
            }
            _uiState.update { it.copy(statusBanner = "Saved broadcast draft.") }
        }
    }

    fun loadDraftIntoComposer(draft: BroadcastDraft) {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    category = draft.category,
                    audience = draft.audience,
                    route = draft.route,
                    title = draft.title,
                    body = draft.body,
                    broadcastId = DevCenterValidators.generateBroadcastId(nowMs())
                ),
                statusBanner = "Loaded draft into composer."
            )
        }
    }

    fun deleteDraft(id: String) {
        authManager.recordUserActivity()
        scope.launch {
            vault.deleteBroadcastDraft(id)
            if (backendClient != null && vault.getState().isBackendEnrolled) {
                runCatching { backendClient.deleteRemoteDraft(id) }
            }
        }
    }

    fun scheduleAutoBroadcast(delayMinutes: Int, confirmationPhrase: String, reason: String) {
        authManager.recordUserActivity()
        val client = backendClient
        if (client == null || !_uiState.value.vault.isBackendEnrolled) {
            _uiState.update { it.copy(errorBanner = "Enroll this device to schedule automatic cloud broadcasts.") }
            return
        }
        val state = _uiState.value
        val validation = DevCenterValidators.validateBroadcastDraft(
            category = state.composer.category,
            audience = state.composer.audience,
            route = state.composer.route,
            rawTitle = state.composer.title,
            rawBody = state.composer.body,
            rawBroadcastId = state.composer.broadcastId,
            validateOnly = false,
            confirmationPhrase = confirmationPhrase,
            rawReason = reason,
            usedBroadcastIds = state.vault.usedBroadcastIds,
            lastLiveDispatchMs = 0L,
            customKillSwitchEnabled = true,
            nowMs = nowMs()
        )
        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = validation.errors.joinToString(" ")) }
            return
        }
        val req = (validation as ValidationResult.Valid).value
        val mins = delayMinutes.coerceIn(5, 10_080)
        val scheduledAtMs = nowMs() + mins * 60_000L
        scope.launch {
            _uiState.update { it.copy(isDispatching = true, errorBanner = null) }
            runCatching { client.scheduleAutoBroadcast(req, scheduledAtMs) }
                .onSuccess {
                    val notif = runCatching { client.fetchNotificationsState() }.getOrNull()
                    _uiState.update { cur ->
                        cur.copy(
                            isDispatching = false,
                            backendScheduledBroadcasts = notif?.autoScheduled ?: cur.backendScheduledBroadcasts,
                            composer = cur.composer.copy(broadcastId = DevCenterValidators.generateBroadcastId(nowMs())),
                            statusBanner = "Scheduled cloud auto-broadcast for +${mins}m (Worker cron)."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isDispatching = false,
                            errorBanner = DevCenterRedactor.redact(err.message ?: "Failed to schedule auto-broadcast.")
                        )
                    }
                }
        }
    }

    fun cancelBackendScheduledBroadcast(id: String) {
        authManager.recordUserActivity()
        val client = backendClient ?: return
        scope.launch {
            runCatching { client.cancelAutoScheduledBroadcast(id) }
                .onSuccess {
                    val notif = runCatching { client.fetchNotificationsState() }.getOrNull()
                    _uiState.update { cur ->
                        cur.copy(
                            backendScheduledBroadcasts = notif?.autoScheduled
                                ?: cur.backendScheduledBroadcasts.map { if (it.id == id) it.copy(status = "cancelled") else it },
                            statusBanner = "Cancelled cloud scheduled broadcast."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorBanner = err.message ?: "Failed to cancel schedule.") }
                }
        }
    }

    fun scheduleAnnouncement(delayMinutes: Int) {
        authManager.recordUserActivity()
        val c = _uiState.value.composer
        if (c.title.trim().length < 3 || c.body.trim().length < 8) {
            _uiState.update { it.copy(errorBanner = "Title (3+ chars) and message (8+ chars) required to schedule.") }
            return
        }
        val mins = delayMinutes.coerceIn(5, 10_080)
        val now = nowMs()
        val targetMs = now + mins * 60_000L
        val item = ScheduledBroadcast(
            id = "sched-$now",
            category = c.category,
            audience = c.audience,
            route = c.route,
            title = c.title.trim(),
            body = c.body.trim(),
            scheduledEpochMs = targetMs,
            status = "scheduled",
            createdAtIso = isoUtcNow(now)
        )
        scope.launch {
            vault.saveScheduledBroadcast(item)
            runCatching { onScheduleBroadcast(item) }
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-$now",
                    timestampIso = isoUtcNow(now),
                    actor = "operator",
                    action = "broadcast.scheduled",
                    target = item.id,
                    status = "ok",
                    summary = "Scheduled \"${item.title}\" reminder in ${mins}m (explicit confirmation required to send live)."
                )
            )
            _uiState.update { it.copy(statusBanner = "Scheduled reminder in ${mins}m. Live confirmation will be requested when ready.") }
        }
    }

    fun loadScheduledIntoComposer(item: ScheduledBroadcast) {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    category = item.category,
                    audience = item.audience,
                    route = item.route,
                    title = item.title,
                    body = item.body,
                    broadcastId = DevCenterValidators.generateBroadcastId(nowMs())
                ),
                statusBanner = "Loaded scheduled announcement. Review & send cleanly."
            )
        }
    }

    fun cancelScheduledAnnouncement(id: String) {
        authManager.recordUserActivity()
        scope.launch {
            vault.updateScheduledBroadcastStatus(id, "cancelled")
            runCatching { onCancelScheduledBroadcast(id) }
            _uiState.update { it.copy(statusBanner = "Cancelled scheduled announcement.") }
        }
    }

    fun sendSingleDeviceTest(deviceToken: String, ephemeralBearerToken: String, validateOnly: Boolean) {
        authManager.recordUserActivity()
        if (_uiState.value.isDispatching) return
        val c = _uiState.value.composer
        val secrets = listOf(deviceToken, ephemeralBearerToken)
        _uiState.update { it.copy(isDispatching = true, statusBanner = null, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                if (c.title.trim().length < 3 || c.body.trim().length < 8) {
                    _uiState.update { it.copy(isDispatching = false, errorBanner = "Title (3+) and message (8+) required.") }
                    return@launch
                }
                if (deviceToken.trim().length < 20) {
                    _uiState.update { it.copy(isDispatching = false, errorBanner = "Enter QA device FCM token.") }
                    return@launch
                }
                val req = ValidatedBroadcastRequest(
                    category = c.category,
                    audience = c.audience,
                    route = c.route,
                    title = c.title.trim(),
                    body = c.body.trim(),
                    broadcastId = c.broadcastId,
                    validateOnly = true,
                    confirmationPhrase = "",
                    reason = "Single-device QA test"
                )
                runCatching {
                    backendClient.dispatchBroadcast("single_device_test", req, deviceToken.trim())
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            isDispatching = false,
                            statusBanner = "Single-device test dispatched via keyless WIF workflow.",
                            errorBanner = null,
                            composer = it.composer.copy(broadcastId = DevCenterValidators.generateBroadcastId(nowMs()))
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isDispatching = false,
                            errorBanner = DevCenterRedactor.redact(err.message ?: "Single-device test failed.", secrets)
                        )
                    }
                }
                return@launch
            }
            val res = singleDeviceFcmClient.sendSingleDeviceTest(
                projectId = v.firebaseProjectId,
                deviceToken = deviceToken,
                ephemeralAccessToken = ephemeralBearerToken,
                title = c.title,
                body = c.body,
                category = c.category,
                route = c.route,
                broadcastId = c.broadcastId,
                validateOnly = validateOnly
            )
            val ts = isoUtcNow(nowMs())
            res.onSuccess { summary ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = ts,
                        actor = "operator",
                        action = "broadcast.single_device_test",
                        target = c.broadcastId,
                        status = "ok",
                        summary = summary
                    ),
                    extraSecrets = secrets
                )
                _uiState.update { it.copy(isDispatching = false, statusBanner = summary, errorBanner = null) }
            }.onFailure { err ->
                val clean = DevCenterRedactor.redact(err.message ?: "Single-device test failed.", secrets)
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = ts,
                        actor = "operator",
                        action = "broadcast.single_device_test",
                        target = c.broadcastId,
                        status = "error",
                        summary = clean
                    ),
                    extraSecrets = secrets
                )
                _uiState.update { it.copy(isDispatching = false, errorBanner = clean) }
            }
        }
    }

    fun openLiveConfirmDialog() {
        authManager.recordUserActivity()
        val state = _uiState.value
        val draftCheck = DevCenterValidators.validateBroadcastDraft(
            category = state.composer.category,
            audience = state.composer.audience,
            route = state.composer.route,
            rawTitle = state.composer.title,
            rawBody = state.composer.body,
            rawBroadcastId = state.composer.broadcastId,
            validateOnly = false,
            confirmationPhrase = state.composer.audience.confirmationPhrase,
            rawReason = "pre-dialog validation check",
            usedBroadcastIds = state.vault.usedBroadcastIds,
            lastLiveDispatchMs = state.vault.lastLiveDispatchMs,
            customKillSwitchEnabled = state.dashboard.killSwitches.customBroadcastEnabled,
            nowMs = nowMs()
        )
        if (draftCheck is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = draftCheck.errors.first()) }
            return
        }
        _uiState.update { it.copy(composer = it.composer.copy(showLiveConfirmDialog = true), errorBanner = null) }
    }

    fun dismissLiveConfirmDialog() {
        _uiState.update { it.copy(composer = it.composer.copy(showLiveConfirmDialog = false)) }
    }

    fun submitBroadcast(
        validateOnly: Boolean,
        confirmationPhrase: String = "",
        reason: String = ""
    ) {
        authManager.recordUserActivity()
        if (_uiState.value.isDispatching) return
        val state = _uiState.value
        val validation = DevCenterValidators.validateBroadcastDraft(
            category = state.composer.category,
            audience = state.composer.audience,
            route = state.composer.route,
            rawTitle = state.composer.title,
            rawBody = state.composer.body,
            rawBroadcastId = state.composer.broadcastId,
            validateOnly = validateOnly,
            confirmationPhrase = confirmationPhrase,
            rawReason = reason,
            usedBroadcastIds = state.vault.usedBroadcastIds,
            lastLiveDispatchMs = state.vault.lastLiveDispatchMs,
            customKillSwitchEnabled = state.dashboard.killSwitches.customBroadcastEnabled,
            nowMs = nowMs()
        )

        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = validation.errors.joinToString(" ")) }
            return
        }

        val req = (validation as ValidationResult.Valid).value
        _uiState.update {
            it.copy(
                isDispatching = true,
                statusBanner = null,
                errorBanner = null,
                composer = it.composer.copy(showLiveConfirmDialog = false)
            )
        }

        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                val mode = if (req.validateOnly) "dry_run" else "public_live"
                val timestamp = isoUtcNow(nowMs())
                runCatching { backendClient.dispatchBroadcast(mode = mode, req = req) }
                    .onSuccess {
                        val historyEntry = BroadcastHistoryEntry(
                            id = "custom-${nowMs()}",
                            kind = "custom",
                            category = req.category.apiValue,
                            broadcastId = req.broadcastId,
                            title = req.title,
                            body = req.body,
                            audience = req.audience.apiValue,
                            topics = req.audience.topics,
                            route = req.route.apiValue,
                            status = if (req.validateOnly) "validate_only" else "live_dispatched",
                            statusLabel = if (req.validateOnly) "Dry-Run Dispatched" else "Live Dispatched",
                            timestampIso = timestamp,
                            note = "Dispatched via Zero-Config Worker WIF."
                        )
                        vault.recordBroadcast(historyEntry, isLiveDispatch = !req.validateOnly, nowMs = nowMs())
                        _uiState.update {
                            it.copy(
                                isDispatching = false,
                                statusBanner = if (req.validateOnly) {
                                    "Dry-run dispatched via Mission Control WIF."
                                } else {
                                    "Live broadcast dispatched via Mission Control WIF."
                                },
                                composer = it.composer.copy(broadcastId = DevCenterValidators.generateBroadcastId(nowMs()))
                            )
                        }
                        delay(1_500L)
                        runCatching { backendClient.fetchNotificationsState() }.getOrNull()?.let { notif ->
                            _uiState.update { cur ->
                                cur.copy(
                                    backendHistory = notif.history,
                                    latestCustomRun = notif.workflowRuns.firstOrNull() ?: cur.latestCustomRun
                                )
                            }
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isDispatching = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "Broadcast request failed.")
                            )
                        }
                    }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val dispatchRes = githubClient.dispatchCustomBroadcast(pat, v.consoleRepo, req)
            val timestamp = isoUtcNow(nowMs())

            dispatchRes.onSuccess { workflowUrl ->
                val historyEntry = BroadcastHistoryEntry(
                    id = "custom-${nowMs()}",
                    kind = "custom",
                    category = req.category.apiValue,
                    broadcastId = req.broadcastId,
                    title = req.title,
                    body = req.body,
                    audience = req.audience.apiValue,
                    topics = req.audience.topics,
                    route = req.route.apiValue,
                    status = if (req.validateOnly) "validate_only" else "dispatched_to_relay",
                    statusLabel = if (req.validateOnly) "Dry-Run Dispatched" else "Relay Requested",
                    timestampIso = timestamp,
                    note = if (req.validateOnly) {
                        "Workflow custom-broadcast.yml requested (validate_only=true). No public notification sent."
                    } else {
                        "Workflow custom-broadcast.yml requested (${req.reason}). Verify outcome in workflow runs."
                    },
                    workflowUrl = workflowUrl
                )
                vault.recordBroadcast(historyEntry, isLiveDispatch = !req.validateOnly, nowMs = nowMs())
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = timestamp,
                        actor = _uiState.value.dashboard.health.authenticatedUser ?: "operator",
                        action = if (req.validateOnly) "broadcast.dry_run" else "broadcast.live_request",
                        target = req.broadcastId,
                        status = "ok",
                        summary = "${req.category.apiValue}/${req.audience.apiValue} -> ${req.route.apiValue}: ${req.reason}"
                    )
                )
                _uiState.update {
                    it.copy(
                        isDispatching = false,
                        statusBanner = if (req.validateOnly) {
                            "Dry-run dispatched to GitHub Actions (validate_only=true). Polling status..."
                        } else {
                            "Live broadcast requested via GitHub Actions relay. Confirm workflow run status."
                        },
                        composer = it.composer.copy(broadcastId = DevCenterValidators.generateBroadcastId(nowMs()))
                    )
                }
                delay(2_500L)
                val latestRun = githubClient.fetchLatestCustomWorkflowRun(pat, v.consoleRepo)
                val updatedSnapshot = githubClient.fetchDashboardSnapshot(pat, v.distributionRepo, v.consoleRepo, v.privateUpdateRepo)
                _uiState.update { it.copy(latestCustomRun = latestRun, dashboard = updatedSnapshot) }
            }.onFailure { err ->
                val cleanErr = DevCenterRedactor.redact(err.message ?: "Broadcast request failed.", listOf(pat))
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = timestamp,
                        actor = "operator",
                        action = if (req.validateOnly) "broadcast.dry_run" else "broadcast.live_request",
                        target = req.broadcastId,
                        status = "error",
                        summary = cleanErr
                    ),
                    extraSecrets = listOf(pat)
                )
                _uiState.update { it.copy(isDispatching = false, errorBanner = cleanErr) }
            }
        }
    }

    fun refreshLatestCustomRun() {
        authManager.recordUserActivity()
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.fetchNotificationsState() }.getOrNull()?.let { notif ->
                    _uiState.update {
                        it.copy(
                            latestCustomRun = notif.workflowRuns.firstOrNull(),
                            backendHistory = notif.history,
                            backendScheduledBroadcasts = notif.autoScheduled
                        )
                    }
                }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val run = githubClient.fetchLatestCustomWorkflowRun(pat, v.consoleRepo)
            _uiState.update { it.copy(latestCustomRun = run) }
        }
    }

    fun requestKillSwitchToggle(target: KillSwitchTarget, nextEnabled: Boolean) {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(pendingKillSwitchModal = PendingKillSwitchModal(target, nextEnabled), errorBanner = null)
        }
    }

    fun dismissKillSwitchModal() {
        _uiState.update { it.copy(pendingKillSwitchModal = null) }
    }

    fun confirmKillSwitchToggle(confirmationPhrase: String, reason: String) {
        authManager.recordUserActivity()
        val pending = _uiState.value.pendingKillSwitchModal ?: return
        val check = DevCenterValidators.validateKillSwitchConfirmation(
            target = pending.target,
            enable = pending.nextEnabled,
            confirmationPhrase = confirmationPhrase,
            rawReason = reason
        )
        if (check is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = check.errors.first()) }
            return
        }
        val cleanReason = (check as ValidationResult.Valid).value
        if (_uiState.value.isUpdatingKillSwitch) return
        _uiState.update { it.copy(isUpdatingKillSwitch = true, pendingKillSwitchModal = null, errorBanner = null) }

        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.toggleKillSwitch(pending.target, pending.nextEnabled) }
                    .onSuccess { nextKs ->
                        _uiState.update { cur ->
                            cur.copy(
                                isUpdatingKillSwitch = false,
                                dashboard = cur.dashboard.copy(killSwitches = nextKs),
                                statusBanner = "${pending.target.label} set to ${if (pending.nextEnabled) "ENABLED" else "PAUSED"}."
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isUpdatingKillSwitch = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "Kill switch update failed.")
                            )
                        }
                    }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val repo = when (pending.target) {
                KillSwitchTarget.CUSTOM_BROADCAST -> v.consoleRepo
                KillSwitchTarget.RELEASE_AUTOMATION -> v.distributionRepo
            }
            val result = githubClient.updateKillSwitch(pat, pending.target, repo, pending.nextEnabled)
            val ts = isoUtcNow(nowMs())
            result.onSuccess { updatedVal ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = ts,
                        actor = _uiState.value.dashboard.health.authenticatedUser ?: "operator",
                        action = "kill_switch.${pending.target.id}",
                        target = pending.target.variableName,
                        status = "ok",
                        summary = "${pending.target.variableName}=$updatedVal ($cleanReason)"
                    )
                )
                _uiState.update { cur ->
                    val ks = cur.dashboard.killSwitches
                    val nextKs = when (pending.target) {
                        KillSwitchTarget.CUSTOM_BROADCAST -> ks.copy(customBroadcastEnabled = updatedVal)
                        KillSwitchTarget.RELEASE_AUTOMATION -> ks.copy(releaseAutomationEnabled = updatedVal)
                    }
                    cur.copy(
                        isUpdatingKillSwitch = false,
                        dashboard = cur.dashboard.copy(killSwitches = nextKs),
                        statusBanner = "${pending.target.label} set to ${if (updatedVal) "ENABLED" else "PAUSED"}."
                    )
                }
            }.onFailure { err ->
                val cleanErr = DevCenterRedactor.redact(err.message ?: "Kill switch update failed.", listOf(pat))
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = ts,
                        actor = "operator",
                        action = "kill_switch.${pending.target.id}",
                        target = pending.target.variableName,
                        status = "error",
                        summary = cleanErr
                    ),
                    extraSecrets = listOf(pat)
                )
                _uiState.update { it.copy(isUpdatingKillSwitch = false, errorBanner = cleanErr) }
            }
        }
    }

    fun selectHistoryFilter(filter: HistoryFilter) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun selectReleaseFilter(filter: ReleaseChannelFilter) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(releaseFilter = filter) }
    }

    // --- Feedback 2.0 Triage & Actions ---

    fun updateFeedbackSearch(query: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackSearchQuery = query) }
    }

    fun updateFeedbackCategoryFilter(category: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackCategoryFilter = category) }
    }

    fun updateFeedbackStatusFilter(status: FeedbackTriageStatus?) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackStatusFilter = status) }
    }

    fun updateFeedbackPriorityFilter(priority: FeedbackPriority?) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackPriorityFilter = priority) }
    }

    fun toggleFeedbackUnreadOnly(unreadOnly: Boolean) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackUnreadOnly = unreadOnly) }
    }

    fun updateFeedbackSortOrder(order: FeedbackSortOrder) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackSortOrder = order) }
    }

    fun updateFeedbackTriage(
        item: TriagedFeedbackItem,
        isRead: Boolean = item.triage.isRead,
        priority: FeedbackPriority = item.triage.priority,
        status: FeedbackTriageStatus = item.triage.status,
        notes: String = item.triage.notes,
        syncToSheetIfPermitted: Boolean = false
    ) {
        authManager.recordUserActivity()
        val nowIso = isoUtcNow(nowMs())
        val updated = item.triage.copy(
            isRead = isRead,
            priority = priority,
            status = status,
            notes = notes.take(600),
            updatedAtIso = nowIso
        )
        scope.launch {
            vault.saveFeedbackTriage(updated)
            val v = vault.getState()
            var note = "Updated feedback triage (${status.label}, ${priority.label})."
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching {
                    backendClient.updateFeedbackTriage(
                        feedbackId = item.entry.id,
                        isUnread = !isRead,
                        priority = priority,
                        status = status,
                        developerNotes = notes.take(600)
                    )
                }.onSuccess {
                    note = "Synced triage (${status.label}, ${priority.label}) to Mission Control."
                }.onFailure { err ->
                    note = "Updated locally (${DevCenterRedactor.redact(err.message)})."
                }
            } else if (syncToSheetIfPermitted && item.entry.sheetRowNumber != null && v.feedbackSheetId.isNotBlank() &&
                DevCenterAnalyticsOAuth.SHEETS_READWRITE_SCOPE in v.googleGrantedScopes
            ) {
                val res = feedbackClient.updateSheetRowStatus(
                    sheetId = v.feedbackSheetId,
                    range = v.feedbackSheetRange,
                    rowNumber = item.entry.sheetRowNumber,
                    newSheetStatus = status.sheetStatus,
                    tokenResolver = { force -> resolveValidAnalyticsToken(force) }
                )
                res.onSuccess { note = "Updated local & Google Sheet status to ${status.label}." }
                    .onFailure { note = "Updated locally (${it.message})." }
            }
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = nowIso,
                    actor = "operator",
                    action = "feedback.triage",
                    target = item.entry.id,
                    status = "ok",
                    summary = note
                )
            )
            _uiState.update { it.copy(statusBanner = note) }
        }
    }

    fun markAllFeedbackRead() {
        authManager.recordUserActivity()
        val items = _uiState.value.triagedFeedback
        if (items.isEmpty()) return
        scope.launch {
            val v = vault.getState()
            val ts = isoUtcNow(nowMs())
            for (item in items) {
                if (!item.triage.isRead) {
                    vault.saveFeedbackTriage(item.triage.copy(isRead = true, updatedAtIso = ts))
                }
            }
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.markAllFeedbackRead() }
            }
            _uiState.update { it.copy(statusBanner = "Marked all feedback items read.") }
        }
    }

    fun createGitHubIssueFromFeedback(item: TriagedFeedbackItem, targetRepo: String? = null) {
        authManager.recordUserActivity()
        if (_uiState.value.isFeedbackMutating) return
        _uiState.update { it.copy(isFeedbackMutating = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            val repo = targetRepo?.trim()?.takeIf { it.isNotBlank() } ?: v.sourceRepo
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.createIssueFromFeedback(item.entry.id, repo) }
                    .onSuccess { (issueNum, issueUrl) ->
                        vault.saveFeedbackTriage(
                            item.triage.copy(
                                isRead = true,
                                status = if (item.triage.status == FeedbackTriageStatus.PENDING) FeedbackTriageStatus.IN_PROGRESS else item.triage.status,
                                linkedIssueNumber = issueNum.takeIf { it > 0 },
                                linkedIssueUrl = issueUrl.takeIf { it.isNotBlank() },
                                updatedAtIso = isoUtcNow(nowMs())
                            )
                        )
                        _uiState.update {
                            it.copy(
                                isFeedbackMutating = false,
                                statusBanner = "Created $repo #$issueNum (contact email excluded)."
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isFeedbackMutating = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "Failed to create GitHub issue.")
                            )
                        }
                    }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val (title, body, labels) = DevFeedbackTriageEngine.buildSanitizedGitHubIssue(item)
            val res = githubManager.createIssue(pat, repo, title, body, labels)
            res.onSuccess { created ->
                vault.saveFeedbackTriage(
                    item.triage.copy(
                        isRead = true,
                        status = if (item.triage.status == FeedbackTriageStatus.PENDING) FeedbackTriageStatus.IN_PROGRESS else item.triage.status,
                        linkedIssueNumber = created.number,
                        linkedIssueUrl = created.htmlUrl,
                        updatedAtIso = isoUtcNow(nowMs())
                    )
                )
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = _uiState.value.dashboard.health.authenticatedUser ?: "operator",
                        action = "feedback.issue_created",
                        target = "$repo#${created.number}",
                        status = "ok",
                        summary = "Created sanitized GitHub issue #${created.number} on $repo."
                    )
                )
                _uiState.update {
                    it.copy(
                        isFeedbackMutating = false,
                        statusBanner = "Created $repo #${created.number} (contact email excluded)."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isFeedbackMutating = false,
                        errorBanner = err.message ?: "Failed to create GitHub issue."
                    )
                }
            }
        }
    }

    fun setFeedbackAlertsEnabled(enabled: Boolean) {
        authManager.recordUserActivity()
        scope.launch {
            vault.setFeedbackAlertsEnabled(enabled)
            runCatching { onSyncFeedbackAlertsWorker(enabled) }
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "feedback.alerts_toggled",
                    target = "feedback_alerts",
                    status = "ok",
                    summary = "New feedback background alerts ${if (enabled) "enabled (6h)" else "disabled"}."
                )
            )
            _uiState.update {
                it.copy(statusBanner = if (enabled) "Background feedback alerts enabled (every 6h on network)." else "Background feedback alerts disabled.")
            }
        }
    }

    fun saveFeedbackSheetConfig(sheetId: String, range: String, rawSheetsToken: String?) {
        authManager.recordUserActivity()
        scope.launch {
            vault.saveFeedbackConfig(sheetId, range, rawSheetsToken)
            _uiState.update { it.copy(statusBanner = "Feedback source updated.", errorBanner = null) }
            refreshAll()
        }
    }

    fun importFeedbackCsv(rawCsv: String, fileName: String) {
        authManager.recordUserActivity()
        scope.launch {
            vault.saveImportedCsv(rawCsv, fileName)
            val fbState = feedbackClient.loadFeedback(
                sheetId = "",
                range = DEFAULT_FEEDBACK_RANGE,
                sheetsAccessToken = null,
                importedCsvRaw = rawCsv,
                importedCsvLabel = fileName
            )
            _uiState.update {
                it.copy(
                    feedbackState = fbState,
                    statusBanner = "Imported CSV feedback ($fileName).",
                    errorBanner = null
                )
            }
        }
    }

    fun reportFeedbackImportError(message: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(errorBanner = message) }
    }

    fun clearFeedbackSource() {
        authManager.recordUserActivity()
        scope.launch {
            vault.clearFeedbackSource()
            _uiState.update {
                it.copy(
                    feedbackState = FeedbackSourceState.Unconfigured(
                        "Feedback source cleared. Import a CSV or configure Sheets access in Settings."
                    ),
                    statusBanner = "Feedback source cleared."
                )
            }
        }
    }

    // --- Analytics & Unified Google OAuth 2.0 PKCE ---

    fun selectAnalyticsDateRange(range: AnalyticsDateRange) {
        authManager.recordUserActivity()
        if (_uiState.value.analyticsDateRange == range) return
        _uiState.update { it.copy(analyticsDateRange = range) }
        refreshAnalytics()
    }

    fun refreshAnalytics() {
        authManager.recordUserActivity()
        if (_uiState.value.isRefreshingAnalytics) return
        _uiState.update { it.copy(isRefreshingAnalytics = true, analyticsState = AnalyticsSourceState.Loading) }
        scope.launch {
            val v = vault.getState()
            val syncIso = isoUtcNow(nowMs())
            val live = if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.fetchAnalytics(_uiState.value.analyticsDateRange) }
                    .getOrElse { err ->
                        AnalyticsSourceState.Error(DevCenterRedactor.redact(err.message ?: "Analytics load failed."), syncIso)
                    }
            } else {
                loadAnalyticsWithAutoRefresh(v)
            }
            val finalState = resolveAnalyticsWithCache(v, live, syncIso)
            if (finalState is AnalyticsSourceState.Ready && !finalState.snapshot.isCached) {
                vault.saveCachedAnalyticsJson(
                    DevCenterAnalyticsClient.encodeSnapshot(finalState.snapshot),
                    syncIso
                )
            }
            _uiState.update {
                it.copy(
                    isRefreshingAnalytics = false,
                    analyticsState = finalState
                )
            }
        }
    }

    private suspend fun loadAnalyticsWithAutoRefresh(v: DevCenterVaultState): AnalyticsSourceState {
        if (!EXPERIMENTAL_GA4_API_ENABLED) {
            return AnalyticsSourceState.Unavailable(
                summary = "In-app GA4 reporting is unavailable. Open Firebase Console for live metrics.",
                hasPropertyId = v.ga4PropertyId.isNotBlank(),
                hasToken = !v.encryptedAnalyticsToken.isNullOrBlank() || !v.encryptedAnalyticsRefreshToken.isNullOrBlank(),
                canSignInWithGoogle = false
            )
        }
        val clientId = effectiveOAuthClientId(v)
        val hasSavedCreds = !v.encryptedAnalyticsToken.isNullOrBlank() || !v.encryptedAnalyticsRefreshToken.isNullOrBlank()
        return analyticsClient.loadAnalytics(
            propertyId = v.ga4PropertyId,
            analyticsAccessToken = vault.readAnalyticsToken(),
            dateRange = _uiState.value.analyticsDateRange,
            syncedAtIso = isoUtcNow(nowMs()),
            hasSavedCredentials = hasSavedCreds,
            canSignInWithGoogle = DevCenterValidators.isValidGoogleOAuthClientId(clientId),
            tokenResolver = { forceRefresh -> resolveValidAnalyticsToken(forceRefresh) }
        )
    }

    private fun effectiveOAuthClientId(v: DevCenterVaultState): String =
        v.ga4OAuthClientId.trim().ifBlank { BuildConfig.DEV_ANALYTICS_OAUTH_CLIENT_ID.trim() }

    internal suspend fun resolveValidAnalyticsToken(forceRefresh: Boolean = false): String? {
        val v = vault.getState()
        val accessToken = vault.readAnalyticsToken()
        val refreshToken = vault.readAnalyticsRefreshToken()
        val clientId = effectiveOAuthClientId(v)
        val now = nowMs()
        val isExpiring = v.analyticsTokenExpiresAtMs > 0L && now >= v.analyticsTokenExpiresAtMs - 60_000L

        if (!refreshToken.isNullOrBlank() &&
            DevCenterValidators.isValidGoogleOAuthClientId(clientId) &&
            (forceRefresh || accessToken.isNullOrBlank() || isExpiring)
        ) {
            val refreshed = runCatching {
                analyticsOAuth.refreshAccessToken(clientId = clientId, refreshToken = refreshToken)
            }.getOrNull() ?: return accessToken
            val newExpiry = now + refreshed.expiresInSeconds * 1_000L
            vault.updateRefreshedAnalyticsToken(
                accessToken = refreshed.accessToken,
                rotatedRefreshToken = refreshed.refreshToken,
                expiresAtMs = newExpiry
            )
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-$now",
                    timestampIso = isoUtcNow(now),
                    actor = "oauth_refresh",
                    action = "analytics.token_refreshed",
                    target = v.ga4PropertyId.ifBlank { "google_oauth" },
                    status = "ok",
                    summary = "Refreshed short-lived Google OAuth access token silently."
                ),
                extraSecrets = listOfNotNull(refreshed.accessToken, refreshed.refreshToken, refreshToken)
            )
            return refreshed.accessToken
        }
        return accessToken
    }

    fun startAnalyticsGoogleSignIn(
        propertyId: String? = null,
        oauthClientId: String? = null,
        scopes: List<String> = DevCenterAnalyticsOAuth.DEFAULT_MISSION_CONTROL_SCOPES,
        onLaunchBrowser: (String) -> Unit
    ) {
        authManager.recordUserActivity()
        authManager.suppressNextBackgroundLockForPicker(120_000L)
        if (!EXPERIMENTAL_GA4_API_ENABLED) {
            _uiState.update { it.copy(errorBanner = "Google OAuth integration is disabled.") }
            return
        }
        scope.launch {
            val v = vault.getState()
            val cleanProp = propertyId?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanProp != null && !DevCenterValidators.isValidGa4PropertyId(cleanProp)) {
                _uiState.update { it.copy(errorBanner = "GA4 Property ID must be 6 to 15 digits.") }
                return@launch
            }
            val cleanClientId = oauthClientId?.trim()?.takeIf { it.isNotEmpty() } ?: effectiveOAuthClientId(v)
            if (!DevCenterValidators.isValidGoogleOAuthClientId(cleanClientId)) {
                _uiState.update {
                    it.copy(errorBanner = "Android OAuth Client ID is missing or invalid. Enter it in Developer Settings.")
                }
                return@launch
            }
            vault.saveAnalyticsConfig(
                propertyId = cleanProp ?: v.ga4PropertyId,
                rawAnalyticsToken = null,
                oauthClientId = cleanClientId
            )
            val session = analyticsOAuth.generatePkceSession(
                clientId = cleanClientId,
                scopes = scopes,
                nowMs = nowMs()
            )
            DevAnalyticsOAuthBus.pendingSession = session
            val authUrl = analyticsOAuth.buildAuthorizationUrl(session)
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "google.oauth_started",
                    target = (cleanProp ?: v.ga4PropertyId).ifBlank { "google_oauth" },
                    status = "ok",
                    summary = "Started Google OAuth PKCE authorization (${session.scopes.size} scopes)."
                ),
                extraSecrets = listOf(session.codeVerifier, session.state)
            )
            onLaunchBrowser(authUrl)
        }
    }

    fun handleAnalyticsOAuthRedirect(rawUri: String) {
        authManager.recordUserActivity()
        if (!EXPERIMENTAL_GA4_API_ENABLED) {
            DevAnalyticsOAuthBus.clear()
            return
        }
        val session = DevAnalyticsOAuthBus.pendingSession
        DevAnalyticsOAuthBus.pendingSession = null
        if (session == null) {
            _uiState.update { it.copy(errorBanner = "No active Google sign-in request was found.") }
            return
        }
        val secrets = mutableListOf(session.codeVerifier, session.state)
        when (val outcome = analyticsOAuth.parseCallbackUri(rawUri, session.state)) {
            is OAuthCallbackOutcome.Error -> {
                _uiState.update { it.copy(errorBanner = DevCenterRedactor.redact(outcome.message, secrets)) }
            }
            is OAuthCallbackOutcome.AuthorizationCode -> {
                secrets += outcome.code
                _uiState.update { it.copy(isRefreshingAnalytics = true, errorBanner = null) }
                scope.launch {
                    runCatching {
                        val tokenRes = analyticsOAuth.exchangeAuthorizationCode(
                            clientId = session.clientId,
                            code = outcome.code,
                            codeVerifier = session.codeVerifier,
                            redirectUri = session.redirectUri
                        )
                        secrets += tokenRes.accessToken
                        tokenRes.refreshToken?.let { secrets += it }
                        val expiry = nowMs() + tokenRes.expiresInSeconds * 1_000L
                        vault.saveAnalyticsOAuthSession(
                            oauthClientId = session.clientId,
                            accessToken = tokenRes.accessToken,
                            refreshToken = tokenRes.refreshToken,
                            expiresAtMs = expiry,
                            grantedScopes = session.scopes.toSet()
                        )
                        vault.appendAuditEvent(
                            AuditLogEntry(
                                id = "aud-${nowMs()}",
                                timestampIso = isoUtcNow(nowMs()),
                                actor = "operator",
                                action = "google.oauth_connected",
                                target = vault.getState().ga4PropertyId.ifBlank { "google_oauth" },
                                status = "ok",
                                summary = "Connected Google OAuth PKCE (${session.scopes.size} scopes, refresh=${tokenRes.refreshToken != null})."
                            ),
                            extraSecrets = secrets
                        )
                    }.onSuccess {
                        _uiState.update {
                            it.copy(
                                isRefreshingAnalytics = false,
                                statusBanner = "Google Mission Control access connected.",
                                errorBanner = null
                            )
                        }
                        refreshAll()
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isRefreshingAnalytics = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "OAuth token exchange failed.", secrets)
                            )
                        }
                    }
                }
            }
        }
    }

    fun saveAnalyticsConfig(
        propertyId: String,
        rawAnalyticsToken: String?,
        oauthClientId: String? = null
    ) {
        authManager.recordUserActivity()
        val cleanId = propertyId.trim()
        if (cleanId.isNotEmpty() && !DevCenterValidators.isValidGa4PropertyId(cleanId)) {
            _uiState.update { it.copy(errorBanner = "GA4 Property ID must be 6 to 15 digits.") }
            return
        }
        val cleanClientId = oauthClientId?.trim()
        if (!cleanClientId.isNullOrEmpty() && !DevCenterValidators.isValidGoogleOAuthClientId(cleanClientId)) {
            _uiState.update { it.copy(errorBanner = "Android OAuth Client ID must end in .apps.googleusercontent.com.") }
            return
        }
        val cleanToken = rawAnalyticsToken?.trim().orEmpty()
        val secretList = buildList { if (cleanToken.isNotEmpty()) add(cleanToken) }
        scope.launch {
            runCatching {
                vault.saveAnalyticsConfig(cleanId, cleanToken, cleanClientId)
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = "operator",
                        action = "credentials.analytics_saved",
                        target = cleanId.ifBlank { "ga4_property" },
                        status = "ok",
                        summary = "Saved Keystore-sealed GA4 config."
                    ),
                    extraSecrets = secretList
                )
            }.onSuccess {
                _uiState.update { it.copy(statusBanner = "Analytics configuration updated.", errorBanner = null) }
                refreshAnalytics()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorBanner = DevCenterRedactor.redact(err.message ?: "Save failed.", secretList))
                }
            }
        }
    }

    fun clearAnalyticsSource() {
        authManager.recordUserActivity()
        DevAnalyticsOAuthBus.clear()
        scope.launch {
            val active = vault.readAnalyticsToken()
            if (!active.isNullOrBlank()) {
                runCatching { analyticsOAuth.revokeToken(active) }
            }
            vault.clearAnalyticsSource()
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "credentials.analytics_cleared",
                    target = "google_oauth",
                    status = "ok",
                    summary = "Cleared and revoked Google OAuth & GA4 configuration."
                )
            )
            _uiState.update {
                it.copy(
                    analyticsState = AnalyticsSourceState.Unavailable(
                        summary = "Google credentials cleared. Sign in again in Settings to reload.",
                        hasPropertyId = false,
                        hasToken = false
                    ),
                    remoteConfigState = RemoteConfigSourceState.Unavailable("Google session cleared.", false),
                    statusBanner = "Google OAuth & Analytics cleared."
                )
            }
        }
    }

    // --- Remote Config Management ---

    fun refreshRemoteConfig() {
        authManager.recordUserActivity()
        _uiState.update { it.copy(remoteConfigState = RemoteConfigSourceState.Loading) }
        scope.launch {
            val v = vault.getState()
            val syncIso = isoUtcNow(nowMs())
            val state = loadRemoteConfigInternal(v, syncIso)
            val edited = if (state is RemoteConfigSourceState.Ready) {
                state.parameters.associate { it.key to it.defaultValue }
            } else _uiState.value.editedRemoteParams
            _uiState.update { it.copy(remoteConfigState = state, editedRemoteParams = edited) }
        }
    }

    private suspend fun loadRemoteConfigInternal(v: DevCenterVaultState, syncIso: String): RemoteConfigSourceState {
        val client = backendClient
        if (client != null && v.isBackendEnrolled) {
            return runCatching { client.fetchRemoteConfig() }
                .getOrElse { err -> RemoteConfigSourceState.Error(DevCenterRedactor.redact(err.message ?: "Remote Config failed."), syncIso) }
        }
        val clientId = effectiveOAuthClientId(v)
        return remoteConfigClient.fetchTemplateAndVersions(
            projectId = v.firebaseProjectId,
            syncedAtIso = syncIso,
            canSignInWithGoogle = DevCenterValidators.isValidGoogleOAuthClientId(clientId),
            tokenResolver = { force -> resolveValidAnalyticsToken(force) }
        )
    }

    fun updatePendingRemoteParam(key: String, newValue: String) {
        authManager.recordUserActivity()
        _uiState.update {
            it.copy(editedRemoteParams = it.editedRemoteParams + (key to newValue))
        }
    }

    fun publishRemoteConfig(confirmationPhrase: String, description: String) {
        authManager.recordUserActivity()
        val ready = _uiState.value.remoteConfigState as? RemoteConfigSourceState.Ready ?: return
        val check = DevCenterValidators.validateRemoteConfigConfirmation(
            isRollback = false,
            confirmationPhrase = confirmationPhrase,
            rawReason = description
        )
        if (check is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = check.errors.first()) }
            return
        }
        val cleanDesc = (check as ValidationResult.Valid).value
        if (_uiState.value.isPublishingRemoteConfig) return

        val pendingMap = _uiState.value.editedRemoteParams
        val nextParams = buildList {
            val seen = mutableSetOf<String>()
            for (p in ready.parameters) {
                seen += p.key
                add(p.copy(defaultValue = pendingMap[p.key] ?: p.defaultValue))
            }
            for ((k, v) in pendingMap) {
                if (k !in seen) {
                    val vType = when {
                        v.equals("true", true) || v.equals("false", true) -> "BOOLEAN"
                        v.toLongOrNull() != null -> "NUMBER"
                        else -> "STRING"
                    }
                    add(RemoteConfigParam(key = k, valueType = vType, defaultValue = v))
                }
            }
        }

        _uiState.update { it.copy(isPublishingRemoteConfig = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.publishRemoteConfig(ready.etag, nextParams) }
                    .onSuccess { msg ->
                        _uiState.update { it.copy(isPublishingRemoteConfig = false, statusBanner = "$msg ($cleanDesc)") }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isPublishingRemoteConfig = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "Remote Config publish failed.")
                            )
                        }
                    }
                return@launch
            }
            val res = remoteConfigClient.publishTemplate(
                projectId = v.firebaseProjectId,
                etag = ready.etag,
                parameters = nextParams,
                conditions = ready.conditions,
                description = cleanDesc,
                syncedAtIso = isoUtcNow(nowMs()),
                tokenResolver = { force -> resolveValidAnalyticsToken(force) }
            )
            res.onSuccess { updated ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = updated.syncedAtIso,
                        actor = "operator",
                        action = "remote_config.published",
                        target = "v${updated.versionNumber}",
                        status = "ok",
                        summary = "Published Remote Config template v${updated.versionNumber}: $cleanDesc"
                    )
                )
                _uiState.update {
                    it.copy(
                        isPublishingRemoteConfig = false,
                        remoteConfigState = updated,
                        editedRemoteParams = updated.parameters.associate { p -> p.key to p.defaultValue },
                        statusBanner = "Published Remote Config v${updated.versionNumber}."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isPublishingRemoteConfig = false,
                        errorBanner = err.message ?: "Remote Config publish failed."
                    )
                }
            }
        }
    }

    fun emergencyDisableRemoteConfig(confirmationPhrase: String) {
        authManager.recordUserActivity()
        val ready = _uiState.value.remoteConfigState as? RemoteConfigSourceState.Ready ?: return
        val disabledParams = remoteConfigClient.buildEmergencyDisableParameters(ready.parameters)
        _uiState.update {
            it.copy(editedRemoteParams = disabledParams.associate { p -> p.key to p.defaultValue })
        }
        publishRemoteConfig(confirmationPhrase, "Emergency disable of non-essential remote flags")
    }

    fun rollbackRemoteConfig(versionNumber: String, confirmationPhrase: String) {
        authManager.recordUserActivity()
        val ready = _uiState.value.remoteConfigState as? RemoteConfigSourceState.Ready ?: return
        val check = DevCenterValidators.validateRemoteConfigConfirmation(
            isRollback = true,
            confirmationPhrase = confirmationPhrase,
            rawReason = "Rollback to v$versionNumber"
        )
        if (check is ValidationResult.Invalid) {
            _uiState.update { it.copy(errorBanner = check.errors.first()) }
            return
        }
        if (_uiState.value.isPublishingRemoteConfig) return
        _uiState.update { it.copy(isPublishingRemoteConfig = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.rollbackRemoteConfig(versionNumber) }
                    .onSuccess { msg ->
                        _uiState.update { it.copy(isPublishingRemoteConfig = false, statusBanner = msg) }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isPublishingRemoteConfig = false,
                                errorBanner = DevCenterRedactor.redact(err.message ?: "Remote Config rollback failed.")
                            )
                        }
                    }
                return@launch
            }
            val res = remoteConfigClient.rollbackToVersion(
                projectId = v.firebaseProjectId,
                versionNumber = versionNumber,
                etag = ready.etag,
                syncedAtIso = isoUtcNow(nowMs()),
                tokenResolver = { force -> resolveValidAnalyticsToken(force) }
            )
            res.onSuccess { updated ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = updated.syncedAtIso,
                        actor = "operator",
                        action = "remote_config.rollback",
                        target = "v$versionNumber",
                        status = "ok",
                        summary = "Rolled back Firebase Remote Config to version v$versionNumber."
                    )
                )
                _uiState.update {
                    it.copy(
                        isPublishingRemoteConfig = false,
                        remoteConfigState = updated,
                        editedRemoteParams = updated.parameters.associate { p -> p.key to p.defaultValue },
                        statusBanner = "Rolled back Remote Config to v$versionNumber."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isPublishingRemoteConfig = false,
                        errorBanner = err.message ?: "Remote Config rollback failed."
                    )
                }
            }
        }
    }

    // --- GitHub Management ---

    fun selectGitHubRepo(repoSlug: String) {
        authManager.recordUserActivity()
        if (_uiState.value.selectedGitHubRepo == repoSlug) return
        _uiState.update { it.copy(selectedGitHubRepo = repoSlug) }
        refreshGitHubRepo()
    }

    fun refreshGitHubRepo() {
        authManager.recordUserActivity()
        _uiState.update { it.copy(isGitHubMutating = true) }
        scope.launch {
            val v = vault.getState()
            val repo = _uiState.value.selectedGitHubRepo
            if (backendClient != null && v.isBackendEnrolled) {
                val snap = runCatching { backendClient.fetchGitHubOverview(repo) }
                    .getOrElse { err ->
                        GitHubRepoSnapshot(repoSlug = repo, error = DevCenterRedactor.redact(err.message ?: "GitHub load failed."))
                    }
                _uiState.update { it.copy(isGitHubMutating = false, gitHubRepoSnapshot = snap) }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val snap = githubManager.fetchRepoSnapshot(pat, repo)
            _uiState.update {
                it.copy(isGitHubMutating = false, gitHubRepoSnapshot = snap)
            }
        }
    }

    fun inspectWorkflowJobs(repoSlug: String, runId: String) {
        authManager.recordUserActivity()
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.fetchWorkflowRunJobs(repoSlug, runId) }
                    .onSuccess { jobs ->
                        _uiState.update { it.copy(inspectedWorkflowJobs = it.inspectedWorkflowJobs + (runId to jobs)) }
                    }
                    .onFailure { err -> _uiState.update { it.copy(errorBanner = err.message) } }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val res = githubManager.fetchWorkflowJobs(pat, repoSlug, runId)
            res.onSuccess { jobs ->
                _uiState.update {
                    it.copy(inspectedWorkflowJobs = it.inspectedWorkflowJobs + (runId to jobs))
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    fun createGitHubIssue(repoSlug: String, title: String, body: String, labels: List<String>) {
        authManager.recordUserActivity()
        if (_uiState.value.isGitHubMutating) return
        _uiState.update { it.copy(isGitHubMutating = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.createGitHubIssue(repoSlug, title, body) }
                    .onSuccess { num ->
                        _uiState.update { it.copy(isGitHubMutating = false, statusBanner = "Created $repoSlug #$num.") }
                        refreshGitHubRepo()
                    }
                    .onFailure { err -> _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) } }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val res = githubManager.createIssue(pat, repoSlug, title, body, labels)
            res.onSuccess { created ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = _uiState.value.dashboard.health.authenticatedUser ?: "operator",
                        action = "github.issue_created",
                        target = "$repoSlug#${created.number}",
                        status = "ok",
                        summary = "Created issue #${created.number}: ${created.title}"
                    )
                )
                _uiState.update {
                    it.copy(
                        isGitHubMutating = false,
                        statusBanner = "Created $repoSlug #${created.number}."
                    )
                }
                refreshGitHubRepo()
            }.onFailure { err ->
                _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) }
            }
        }
    }

    fun toggleGitHubIssueState(repoSlug: String, issueNumber: Int, open: Boolean) {
        authManager.recordUserActivity()
        if (_uiState.value.isGitHubMutating) return
        _uiState.update { it.copy(isGitHubMutating = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.updateGitHubIssueState(repoSlug, issueNumber, open) }
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isGitHubMutating = false,
                                statusBanner = "Issue #$issueNumber ${if (open) "reopened" else "closed"}."
                            )
                        }
                        refreshGitHubRepo()
                    }
                    .onFailure { err -> _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) } }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val res = githubManager.updateIssueState(pat, repoSlug, issueNumber, open)
            res.onSuccess { updated ->
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = _uiState.value.dashboard.health.authenticatedUser ?: "operator",
                        action = if (open) "github.issue_reopened" else "github.issue_closed",
                        target = "$repoSlug#${updated.number}",
                        status = "ok",
                        summary = "Set issue #${updated.number} state=${updated.state}."
                    )
                )
                _uiState.update {
                    it.copy(
                        isGitHubMutating = false,
                        statusBanner = "Issue #$issueNumber ${if (open) "reopened" else "closed"}."
                    )
                }
                refreshGitHubRepo()
            }.onFailure { err ->
                _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) }
            }
        }
    }

    fun commentOnGitHubIssue(repoSlug: String, issueNumber: Int, commentBody: String) {
        authManager.recordUserActivity()
        if (_uiState.value.isGitHubMutating) return
        _uiState.update { it.copy(isGitHubMutating = true, errorBanner = null) }
        scope.launch {
            val v = vault.getState()
            if (backendClient != null && v.isBackendEnrolled) {
                runCatching { backendClient.commentOnGitHubIssue(repoSlug, issueNumber, commentBody) }
                    .onSuccess {
                        _uiState.update {
                            it.copy(isGitHubMutating = false, statusBanner = "Added comment to $repoSlug #$issueNumber.")
                        }
                        refreshGitHubRepo()
                    }
                    .onFailure { err -> _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) } }
                return@launch
            }
            val pat = vault.readGithubPat().orEmpty()
            val res = githubManager.addIssueComment(pat, repoSlug, issueNumber, commentBody)
            res.onSuccess {
                _uiState.update {
                    it.copy(isGitHubMutating = false, statusBanner = "Added comment to $repoSlug #$issueNumber.")
                }
                refreshGitHubRepo()
            }.onFailure { err ->
                _uiState.update { it.copy(isGitHubMutating = false, errorBanner = err.message) }
            }
        }
    }

    private fun DevCenterVault.getStateSyncOrNull(): DevCenterVaultState? = _uiState.value.vault
}
