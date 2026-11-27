package com.diniy.duit.devcenter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    BETA("Beta")
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
        "Import a CSV export or configure Google Sheets read-only access in Settings."
    ),
    val feedbackSearchQuery: String = "",
    val feedbackCategoryFilter: String = "All",
    val analyticsDateRange: AnalyticsDateRange = AnalyticsDateRange.THIRTY_DAYS,
    val analyticsState: AnalyticsSourceState = AnalyticsSourceState.Unavailable(
        summary = "GA4 Data API credentials are optional and not configured yet. Use Firebase Console or add Property ID and read-only token in Settings.",
        hasPropertyId = false,
        hasToken = false
    ),
    val isRefreshingAnalytics: Boolean = false,
    val pendingKillSwitchModal: PendingKillSwitchModal? = null
) {
    val combinedBroadcastHistory: List<BroadcastHistoryEntry>
        get() = (vault.customHistory + dashboard.releaseBroadcasts)
            .sortedByDescending { it.timestampIso }
}

/**
 * Coordinates Developer Center remote telemetry, custom FCM workflow dispatches,
 * release monitoring, analytics insights, feedback viewing, kill switches, and security settings.
 */
class DevCenterViewModel(
    private val vault: DevCenterVault,
    private val githubClient: DevCenterGitHubClient,
    private val feedbackClient: DevCenterFeedbackClient,
    private val analyticsClient: DevCenterAnalyticsClient = DevCenterAnalyticsClient(HttpsUrlConnectionTransport()),
    val authManager: DevCenterAuthManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val _uiState = MutableStateFlow(DevCenterUiState())
    val uiState: StateFlow<DevCenterUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            vault.observeState().collectLatest { v ->
                _uiState.update { cur ->
                    cur.copy(
                        vault = v,
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
        _uiState.update { it.copy(statusBanner = null, errorBanner = null) }
    }

    fun refreshAll() {
        authManager.recordUserActivity()
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorBanner = null) }

        scope.launch {
            val v = vault.getState()
            val pat = vault.readGithubPat().orEmpty()
            val snapshot = githubClient.fetchDashboardSnapshot(
                pat = pat,
                distributionRepo = v.distributionRepo,
                consoleRepo = v.consoleRepo
            )
            val customRun = if (pat.isNotEmpty()) {
                githubClient.fetchLatestCustomWorkflowRun(pat, v.consoleRepo)
            } else null
            val sheetsToken = vault.readSheetsToken()
            val fbState = feedbackClient.loadFeedback(
                sheetId = v.feedbackSheetId,
                range = v.feedbackSheetRange,
                sheetsAccessToken = sheetsToken,
                importedCsvRaw = v.importedCsvRaw,
                importedCsvLabel = v.importedCsvLabel
            )
            val analyticsToken = vault.readAnalyticsToken()
            val gaState = analyticsClient.loadAnalytics(
                propertyId = v.ga4PropertyId,
                analyticsAccessToken = analyticsToken,
                dateRange = _uiState.value.analyticsDateRange,
                syncedAtIso = isoUtcNow(nowMs())
            )
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    dashboard = snapshot,
                    latestCustomRun = customRun,
                    feedbackState = fbState,
                    analyticsState = gaState,
                    errorBanner = snapshot.degradedNotice
                )
            }
        }
    }

    fun saveGithubCredentials(
        rawPat: String,
        distributionRepo: String,
        consoleRepo: String,
        privateUpdateRepo: String = _uiState.value.vault.privateUpdateRepo
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
                            "Updated GitHub repository configuration."
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
            val pat = vault.readGithubPat().orEmpty()
            val health = githubClient.checkConnectionHealth(pat, v.distributionRepo, v.consoleRepo)
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
                    statusBanner = if (health.connected) health.statusSummary else null,
                    errorBanner = if (!health.connected) health.statusSummary else null
                )
            }
        }
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
            vault.clearAllCredentials()
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "credentials.cleared_all",
                    target = "vault",
                    status = "ok",
                    summary = "Cleared all GitHub & Sheets credentials from Developer Center vault."
                )
            )
            _uiState.update {
                it.copy(
                    statusBanner = "Developer credentials wiped (financial data untouched).",
                    errorBanner = null,
                    dashboard = it.dashboard.copy(
                        health = ConnectionHealth(connected = false),
                        killSwitches = AutomationKillSwitches()
                    )
                )
            }
            refreshAll()
        }
    }

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
                        "Workflow custom-broadcast.yml requested (${req.reason}). Check workflow run status; FCM acceptance does not confirm device delivery."
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
                            "Dry-run dispatched to GitHub Actions (validate_only=true). Polling workflow status..."
                        } else {
                            "Live broadcast request sent to GitHub Actions relay. Verify workflow outcome in History."
                        },
                        composer = it.composer.copy(
                            broadcastId = DevCenterValidators.generateBroadcastId(nowMs())
                        )
                    )
                }
                delay(2_500L)
                val latestRun = githubClient.fetchLatestCustomWorkflowRun(pat, v.consoleRepo)
                val updatedSnapshot = githubClient.fetchDashboardSnapshot(pat, v.distributionRepo, v.consoleRepo)
                _uiState.update {
                    it.copy(latestCustomRun = latestRun, dashboard = updatedSnapshot)
                }
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

    fun updateFeedbackSearch(query: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackSearchQuery = query) }
    }

    fun updateFeedbackCategoryFilter(category: String) {
        authManager.recordUserActivity()
        _uiState.update { it.copy(feedbackCategoryFilter = category) }
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
                        "Feedback source cleared. Import a CSV or configure read-only Sheets in Settings."
                    ),
                    statusBanner = "Feedback source cleared."
                )
            }
        }
    }

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
            val token = vault.readAnalyticsToken()
            val gaState = analyticsClient.loadAnalytics(
                propertyId = v.ga4PropertyId,
                analyticsAccessToken = token,
                dateRange = _uiState.value.analyticsDateRange,
                syncedAtIso = isoUtcNow(nowMs())
            )
            _uiState.update {
                it.copy(
                    isRefreshingAnalytics = false,
                    analyticsState = gaState
                )
            }
        }
    }

    fun saveAnalyticsConfig(propertyId: String, rawAnalyticsToken: String?) {
        authManager.recordUserActivity()
        val cleanId = propertyId.trim()
        if (cleanId.isNotEmpty() && !DevCenterValidators.isValidGa4PropertyId(cleanId)) {
            _uiState.update { it.copy(errorBanner = "GA4 Property ID must be 6 to 15 digits.") }
            return
        }
        val cleanToken = rawAnalyticsToken?.trim().orEmpty()
        val secretList = buildList { if (cleanToken.isNotEmpty()) add(cleanToken) }
        scope.launch {
            runCatching {
                vault.saveAnalyticsConfig(cleanId, cleanToken)
                vault.appendAuditEvent(
                    AuditLogEntry(
                        id = "aud-${nowMs()}",
                        timestampIso = isoUtcNow(nowMs()),
                        actor = "operator",
                        action = "credentials.analytics_saved",
                        target = cleanId.ifBlank { "ga4_property" },
                        status = "ok",
                        summary = "Saved Keystore-sealed GA4 read-only config."
                    ),
                    extraSecrets = secretList
                )
            }.onSuccess {
                _uiState.update { it.copy(statusBanner = "Analytics source updated.", errorBanner = null) }
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
        scope.launch {
            vault.clearAnalyticsSource()
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${nowMs()}",
                    timestampIso = isoUtcNow(nowMs()),
                    actor = "operator",
                    action = "credentials.analytics_cleared",
                    target = "ga4_property",
                    status = "ok",
                    summary = "Cleared GA4 Property ID and read-only Analytics token."
                )
            )
            _uiState.update {
                it.copy(
                    analyticsState = AnalyticsSourceState.Unavailable(
                        summary = "Analytics credentials cleared. Add Property ID and read-only token in Settings to reload.",
                        hasPropertyId = false,
                        hasToken = false
                    ),
                    statusBanner = "Analytics source cleared."
                )
            }
        }
    }

    private fun DevCenterVault.getStateSyncOrNull(): DevCenterVaultState? = _uiState.value.vault
}
