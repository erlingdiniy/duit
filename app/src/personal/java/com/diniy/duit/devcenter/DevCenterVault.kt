package com.diniy.duit.devcenter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.diniy.duit.security.AndroidKeyStoreSecretProtector
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class DevCenterVaultState(
    val isPinConfigured: Boolean = false,
    val pinEnvelope: String? = null,
    val isBiometricEnabled: Boolean = true,
    val autoLockTimeout: DevAutoLockTimeout = DevAutoLockTimeout.FIVE_MINUTES,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val encryptedGithubPat: String? = null,
    val githubPatHint: String = "Not configured",
    val distributionRepo: String = DEFAULT_DISTRIBUTION_REPO,
    val consoleRepo: String = DEFAULT_CONSOLE_REPO,
    val privateUpdateRepo: String = DEFAULT_PRIVATE_UPDATE_REPO,
    val sourceRepo: String = DEFAULT_SOURCE_REPO,
    val firebaseProjectId: String = DEFAULT_FIREBASE_PROJECT_ID,
    val feedbackSheetId: String = "",
    val feedbackSheetRange: String = DEFAULT_FEEDBACK_RANGE,
    val encryptedSheetsToken: String? = null,
    val sheetsTokenHint: String = "Not configured",
    val ga4PropertyId: String = "",
    val ga4OAuthClientId: String = "",
    val analyticsAuthMode: String = "none",
    val encryptedAnalyticsToken: String? = null,
    val analyticsTokenHint: String = "Not configured",
    val encryptedAnalyticsRefreshToken: String? = null,
    val analyticsTokenExpiresAtMs: Long = 0L,
    val googleGrantedScopes: Set<String> = emptySet(),
    val importedCsvRaw: String? = null,
    val importedCsvLabel: String? = null,
    val feedbackTriageMap: Map<String, FeedbackTriageRecord> = emptyMap(),
    val seenFeedbackIds: Set<String> = emptySet(),
    val feedbackAlertsEnabled: Boolean = false,
    val customHistory: List<BroadcastHistoryEntry> = emptyList(),
    val broadcastTemplates: List<BroadcastTemplate> = defaultBroadcastTemplates(),
    val broadcastDrafts: List<BroadcastDraft> = emptyList(),
    val scheduledBroadcasts: List<ScheduledBroadcast> = emptyList(),
    val cachedAnalyticsJson: String? = null,
    val lastFullSyncIso: String? = null,
    val usedBroadcastIds: Set<String> = emptySet(),
    val lastLiveDispatchMs: Long = 0L,
    val auditLog: List<AuditLogEntry> = emptyList()
)

private fun defaultBroadcastTemplates(): List<BroadcastTemplate> = listOf(
    BroadcastTemplate(
        id = "tpl-release-note",
        name = "Release Highlight",
        category = BroadcastCategory.UPDATE,
        audience = BroadcastAudience.STABLE,
        route = BroadcastDestination.HOME,
        title = "New duit. update available",
        body = "Tap to review the latest improvements and update in-app."
    ),
    BroadcastTemplate(
        id = "tpl-beta-qa",
        name = "Beta Test Call",
        category = BroadcastCategory.ANNOUNCEMENT,
        audience = BroadcastAudience.BETA,
        route = BroadcastDestination.INSIGHTS,
        title = "New beta build ready for testing",
        body = "Try the latest beta changes and share feedback under Settings -> Help & Feedback."
    ),
    BroadcastTemplate(
        id = "tpl-maintenance",
        name = "Service Notice",
        category = BroadcastCategory.MAINTENANCE,
        audience = BroadcastAudience.BOTH,
        route = BroadcastDestination.HOME,
        title = "Scheduled service maintenance",
        body = "Local wallets and records work normally offline; cloud updates may pause briefly."
    )
)

interface DevCenterStorage {
    fun observe(): Flow<DevCenterVaultState>
    suspend fun read(): DevCenterVaultState
    suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState)
}

private val Context.devCenterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "duit_dev_center_prefs"
)

/**
 * Isolated DataStore backing only Developer Center state & Keystore envelopes.
 * Completely separate from financial Room database and .duitbackup exports.
 */
class DataStoreDevCenterStorage(context: Context) : DevCenterStorage {
    private val dataStore = context.applicationContext.devCenterDataStore

    override fun observe(): Flow<DevCenterVaultState> = dataStore.data
        .catch { err -> if (err is IOException) emit(emptyPreferences()) else throw err }
        .map(::mapPreferences)

    override suspend fun read(): DevCenterVaultState = observe().first()

    override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
        dataStore.edit { prefs ->
            val current = mapPreferences(prefs)
            val next = transform(current)
            writePreferences(prefs, next)
        }
    }

    private fun mapPreferences(prefs: Preferences): DevCenterVaultState {
        val pinEnv = prefs[KEY_PIN_ENVELOPE]
        val patEnv = prefs[KEY_GITHUB_PAT]
        val sheetsEnv = prefs[KEY_SHEETS_TOKEN]
        val analyticsEnv = prefs[KEY_ANALYTICS_TOKEN]
        val analyticsRefreshEnv = prefs[KEY_ANALYTICS_REFRESH_TOKEN]
        return DevCenterVaultState(
            isPinConfigured = !pinEnv.isNullOrBlank(),
            pinEnvelope = pinEnv,
            isBiometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: true,
            autoLockTimeout = DevAutoLockTimeout.fromTimeoutMs(
                prefs[KEY_AUTO_LOCK_MS] ?: DevAutoLockTimeout.FIVE_MINUTES.timeoutMs
            ),
            failedAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilEpochMs = prefs[KEY_LOCKOUT_UNTIL_MS] ?: 0L,
            encryptedGithubPat = patEnv,
            githubPatHint = prefs[KEY_GITHUB_PAT_HINT] ?: if (patEnv.isNullOrBlank()) "Not configured" else "Configured",
            distributionRepo = prefs[KEY_DIST_REPO] ?: DEFAULT_DISTRIBUTION_REPO,
            consoleRepo = prefs[KEY_CONSOLE_REPO] ?: DEFAULT_CONSOLE_REPO,
            privateUpdateRepo = prefs[KEY_PRIVATE_UPDATE_REPO] ?: DEFAULT_PRIVATE_UPDATE_REPO,
            sourceRepo = prefs[KEY_SOURCE_REPO] ?: DEFAULT_SOURCE_REPO,
            firebaseProjectId = prefs[KEY_FIREBASE_PROJECT_ID] ?: DEFAULT_FIREBASE_PROJECT_ID,
            feedbackSheetId = prefs[KEY_FEEDBACK_SHEET_ID] ?: "",
            feedbackSheetRange = prefs[KEY_FEEDBACK_RANGE] ?: DEFAULT_FEEDBACK_RANGE,
            encryptedSheetsToken = sheetsEnv,
            sheetsTokenHint = prefs[KEY_SHEETS_TOKEN_HINT] ?: if (sheetsEnv.isNullOrBlank()) "Not configured" else "Configured",
            ga4PropertyId = prefs[KEY_GA4_PROPERTY_ID] ?: "",
            ga4OAuthClientId = prefs[KEY_GA4_OAUTH_CLIENT_ID] ?: "",
            analyticsAuthMode = prefs[KEY_ANALYTICS_AUTH_MODE] ?: if (!analyticsEnv.isNullOrBlank()) "manual" else "none",
            encryptedAnalyticsToken = analyticsEnv,
            analyticsTokenHint = prefs[KEY_ANALYTICS_TOKEN_HINT] ?: if (analyticsEnv.isNullOrBlank()) "Not configured" else "Configured",
            encryptedAnalyticsRefreshToken = analyticsRefreshEnv,
            analyticsTokenExpiresAtMs = prefs[KEY_ANALYTICS_EXPIRES_AT_MS] ?: 0L,
            googleGrantedScopes = prefs[KEY_GOOGLE_GRANTED_SCOPES] ?: emptySet(),
            importedCsvRaw = prefs[KEY_IMPORTED_CSV],
            importedCsvLabel = prefs[KEY_IMPORTED_CSV_LABEL],
            feedbackTriageMap = decodeFeedbackTriage(prefs[KEY_FEEDBACK_TRIAGE_JSON]),
            seenFeedbackIds = prefs[KEY_SEEN_FEEDBACK_IDS] ?: emptySet(),
            feedbackAlertsEnabled = prefs[KEY_FEEDBACK_ALERTS_ENABLED] ?: false,
            customHistory = decodeHistory(prefs[KEY_CUSTOM_HISTORY_JSON]),
            broadcastTemplates = prefs[KEY_BROADCAST_TEMPLATES_JSON]?.let(::decodeTemplates) ?: defaultBroadcastTemplates(),
            broadcastDrafts = decodeDrafts(prefs[KEY_BROADCAST_DRAFTS_JSON]),
            scheduledBroadcasts = decodeScheduled(prefs[KEY_SCHEDULED_BROADCASTS_JSON]),
            cachedAnalyticsJson = prefs[KEY_CACHED_ANALYTICS_JSON],
            lastFullSyncIso = prefs[KEY_LAST_FULL_SYNC_ISO],
            usedBroadcastIds = prefs[KEY_USED_BROADCAST_IDS] ?: emptySet(),
            lastLiveDispatchMs = prefs[KEY_LAST_LIVE_MS] ?: 0L,
            auditLog = decodeAuditLog(prefs[KEY_AUDIT_LOG_JSON])
        )
    }

    private fun writePreferences(prefs: androidx.datastore.preferences.core.MutablePreferences, next: DevCenterVaultState) {
        if (next.pinEnvelope.isNullOrBlank()) prefs.remove(KEY_PIN_ENVELOPE) else prefs[KEY_PIN_ENVELOPE] = next.pinEnvelope
        prefs[KEY_BIOMETRIC_ENABLED] = next.isBiometricEnabled
        prefs[KEY_AUTO_LOCK_MS] = next.autoLockTimeout.timeoutMs
        prefs[KEY_FAILED_ATTEMPTS] = next.failedAttempts
        prefs[KEY_LOCKOUT_UNTIL_MS] = next.lockoutUntilEpochMs
        if (next.encryptedGithubPat.isNullOrBlank()) {
            prefs.remove(KEY_GITHUB_PAT)
            prefs.remove(KEY_GITHUB_PAT_HINT)
        } else {
            prefs[KEY_GITHUB_PAT] = next.encryptedGithubPat
            prefs[KEY_GITHUB_PAT_HINT] = next.githubPatHint
        }
        prefs[KEY_DIST_REPO] = next.distributionRepo
        prefs[KEY_CONSOLE_REPO] = next.consoleRepo
        prefs[KEY_PRIVATE_UPDATE_REPO] = next.privateUpdateRepo
        prefs[KEY_SOURCE_REPO] = next.sourceRepo
        prefs[KEY_FIREBASE_PROJECT_ID] = next.firebaseProjectId
        prefs[KEY_FEEDBACK_SHEET_ID] = next.feedbackSheetId
        prefs[KEY_FEEDBACK_RANGE] = next.feedbackSheetRange
        if (next.encryptedSheetsToken.isNullOrBlank()) {
            prefs.remove(KEY_SHEETS_TOKEN)
            prefs.remove(KEY_SHEETS_TOKEN_HINT)
        } else {
            prefs[KEY_SHEETS_TOKEN] = next.encryptedSheetsToken
            prefs[KEY_SHEETS_TOKEN_HINT] = next.sheetsTokenHint
        }
        if (next.ga4PropertyId.isBlank()) prefs.remove(KEY_GA4_PROPERTY_ID) else prefs[KEY_GA4_PROPERTY_ID] = next.ga4PropertyId
        if (next.ga4OAuthClientId.isBlank()) prefs.remove(KEY_GA4_OAUTH_CLIENT_ID) else prefs[KEY_GA4_OAUTH_CLIENT_ID] = next.ga4OAuthClientId
        prefs[KEY_ANALYTICS_AUTH_MODE] = next.analyticsAuthMode
        if (next.encryptedAnalyticsToken.isNullOrBlank()) {
            prefs.remove(KEY_ANALYTICS_TOKEN)
            prefs.remove(KEY_ANALYTICS_TOKEN_HINT)
        } else {
            prefs[KEY_ANALYTICS_TOKEN] = next.encryptedAnalyticsToken
            prefs[KEY_ANALYTICS_TOKEN_HINT] = next.analyticsTokenHint
        }
        if (next.encryptedAnalyticsRefreshToken.isNullOrBlank()) {
            prefs.remove(KEY_ANALYTICS_REFRESH_TOKEN)
        } else {
            prefs[KEY_ANALYTICS_REFRESH_TOKEN] = next.encryptedAnalyticsRefreshToken
        }
        if (next.analyticsTokenExpiresAtMs <= 0L) {
            prefs.remove(KEY_ANALYTICS_EXPIRES_AT_MS)
        } else {
            prefs[KEY_ANALYTICS_EXPIRES_AT_MS] = next.analyticsTokenExpiresAtMs
        }
        if (next.googleGrantedScopes.isEmpty()) prefs.remove(KEY_GOOGLE_GRANTED_SCOPES) else prefs[KEY_GOOGLE_GRANTED_SCOPES] = next.googleGrantedScopes
        if (next.importedCsvRaw.isNullOrBlank()) prefs.remove(KEY_IMPORTED_CSV) else prefs[KEY_IMPORTED_CSV] = next.importedCsvRaw
        if (next.importedCsvLabel.isNullOrBlank()) prefs.remove(KEY_IMPORTED_CSV_LABEL) else prefs[KEY_IMPORTED_CSV_LABEL] = next.importedCsvLabel
        prefs[KEY_FEEDBACK_TRIAGE_JSON] = encodeFeedbackTriage(next.feedbackTriageMap)
        prefs[KEY_SEEN_FEEDBACK_IDS] = next.seenFeedbackIds
        prefs[KEY_FEEDBACK_ALERTS_ENABLED] = next.feedbackAlertsEnabled
        prefs[KEY_CUSTOM_HISTORY_JSON] = encodeHistory(next.customHistory.take(MAX_HISTORY_ITEMS))
        prefs[KEY_BROADCAST_TEMPLATES_JSON] = encodeTemplates(next.broadcastTemplates.take(30))
        prefs[KEY_BROADCAST_DRAFTS_JSON] = encodeDrafts(next.broadcastDrafts.take(30))
        prefs[KEY_SCHEDULED_BROADCASTS_JSON] = encodeScheduled(next.scheduledBroadcasts.take(30))
        if (next.cachedAnalyticsJson.isNullOrBlank()) prefs.remove(KEY_CACHED_ANALYTICS_JSON) else prefs[KEY_CACHED_ANALYTICS_JSON] = next.cachedAnalyticsJson
        if (next.lastFullSyncIso.isNullOrBlank()) prefs.remove(KEY_LAST_FULL_SYNC_ISO) else prefs[KEY_LAST_FULL_SYNC_ISO] = next.lastFullSyncIso
        prefs[KEY_USED_BROADCAST_IDS] = next.usedBroadcastIds
        prefs[KEY_LAST_LIVE_MS] = next.lastLiveDispatchMs
        prefs[KEY_AUDIT_LOG_JSON] = encodeAuditLog(next.auditLog.take(MAX_AUDIT_ITEMS))
    }

    private fun encodeHistory(list: List<BroadcastHistoryEntry>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("kind", item.kind)
                    put("category", item.category)
                    put("broadcastId", item.broadcastId)
                    put("title", item.title)
                    put("body", item.body)
                    put("audience", item.audience)
                    put("topics", JSONArray(item.topics))
                    put("route", item.route)
                    put("status", item.status)
                    put("statusLabel", item.statusLabel)
                    put("timestampIso", item.timestampIso)
                    put("note", item.note)
                    if (item.workflowUrl != null) put("workflowUrl", item.workflowUrl)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeHistory(raw: String?): List<BroadcastHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val topicsArr = obj.optJSONArray("topics") ?: JSONArray()
                    val topics = List(topicsArr.length()) { idx -> topicsArr.optString(idx) }.filter { it.isNotBlank() }
                    add(
                        BroadcastHistoryEntry(
                            id = obj.optString("id"),
                            kind = obj.optString("kind", "custom"),
                            category = obj.optString("category", "announcement"),
                            broadcastId = obj.optString("broadcastId"),
                            title = obj.optString("title"),
                            body = obj.optString("body"),
                            audience = obj.optString("audience", "beta"),
                            topics = topics,
                            route = obj.optString("route", "home"),
                            status = obj.optString("status", "unknown"),
                            statusLabel = obj.optString("statusLabel", "Unknown"),
                            timestampIso = obj.optString("timestampIso"),
                            note = obj.optString("note"),
                            workflowUrl = obj.optString("workflowUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeAuditLog(list: List<AuditLogEntry>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("timestampIso", item.timestampIso)
                    put("actor", item.actor)
                    put("action", item.action)
                    put("target", item.target)
                    put("status", item.status)
                    put("summary", item.summary)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeAuditLog(raw: String?): List<AuditLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        AuditLogEntry(
                            id = obj.optString("id"),
                            timestampIso = obj.optString("timestampIso"),
                            actor = obj.optString("actor"),
                            action = obj.optString("action"),
                            target = obj.optString("target"),
                            status = obj.optString("status"),
                            summary = obj.optString("summary")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFeedbackTriage(map: Map<String, FeedbackTriageRecord>): String {
        val arr = JSONArray()
        for (rec in map.values.take(300)) {
            arr.put(
                JSONObject().apply {
                    put("feedbackId", rec.feedbackId)
                    put("isRead", rec.isRead)
                    put("priority", rec.priority.name)
                    put("status", rec.status.name)
                    put("notes", rec.notes)
                    rec.linkedIssueNumber?.let { put("linkedIssueNumber", it) }
                    rec.linkedIssueUrl?.let { put("linkedIssueUrl", it) }
                    put("updatedAtIso", rec.updatedAtIso)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeFeedbackTriage(raw: String?): Map<String, FeedbackTriageRecord> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("feedbackId").takeIf { it.isNotBlank() } ?: continue
                    put(
                        id,
                        FeedbackTriageRecord(
                            feedbackId = id,
                            isRead = obj.optBoolean("isRead", false),
                            priority = FeedbackPriority.fromStored(obj.optString("priority")),
                            status = FeedbackTriageStatus.fromStored(obj.optString("status")),
                            notes = obj.optString("notes"),
                            linkedIssueNumber = obj.takeIf { it.has("linkedIssueNumber") && !it.isNull("linkedIssueNumber") }?.optInt("linkedIssueNumber"),
                            linkedIssueUrl = obj.optString("linkedIssueUrl").takeIf { it.isNotBlank() },
                            updatedAtIso = obj.optString("updatedAtIso")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeTemplates(list: List<BroadcastTemplate>): String = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("category", t.category.apiValue)
                put("audience", t.audience.apiValue); put("route", t.route.apiValue)
                put("title", t.title); put("body", t.body)
            })
        }
    }.toString()

    private fun decodeTemplates(raw: String?): List<BroadcastTemplate> {
        if (raw.isNullOrBlank()) return defaultBroadcastTemplates()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(BroadcastTemplate(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        category = BroadcastCategory.fromApiValue(o.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                        audience = BroadcastAudience.fromApiValue(o.optString("audience")) ?: BroadcastAudience.BETA,
                        route = BroadcastDestination.fromApiValue(o.optString("route")) ?: BroadcastDestination.HOME,
                        title = o.optString("title"),
                        body = o.optString("body")
                    ))
                }
            }.ifEmpty { defaultBroadcastTemplates() }
        }.getOrDefault(defaultBroadcastTemplates())
    }

    private fun encodeDrafts(list: List<BroadcastDraft>): String = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("id", d.id); put("category", d.category.apiValue); put("audience", d.audience.apiValue)
                put("route", d.route.apiValue); put("title", d.title); put("body", d.body)
                put("updatedAtIso", d.updatedAtIso)
            })
        }
    }.toString()

    private fun decodeDrafts(raw: String?): List<BroadcastDraft> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(BroadcastDraft(
                        id = o.optString("id"),
                        category = BroadcastCategory.fromApiValue(o.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                        audience = BroadcastAudience.fromApiValue(o.optString("audience")) ?: BroadcastAudience.BETA,
                        route = BroadcastDestination.fromApiValue(o.optString("route")) ?: BroadcastDestination.HOME,
                        title = o.optString("title"),
                        body = o.optString("body"),
                        updatedAtIso = o.optString("updatedAtIso")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeScheduled(list: List<ScheduledBroadcast>): String = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("category", s.category.apiValue); put("audience", s.audience.apiValue)
                put("route", s.route.apiValue); put("title", s.title); put("body", s.body)
                put("scheduledEpochMs", s.scheduledEpochMs); put("status", s.status)
                put("createdAtIso", s.createdAtIso)
            })
        }
    }.toString()

    private fun decodeScheduled(raw: String?): List<ScheduledBroadcast> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(ScheduledBroadcast(
                        id = o.optString("id"),
                        category = BroadcastCategory.fromApiValue(o.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                        audience = BroadcastAudience.fromApiValue(o.optString("audience")) ?: BroadcastAudience.BETA,
                        route = BroadcastDestination.fromApiValue(o.optString("route")) ?: BroadcastDestination.HOME,
                        title = o.optString("title"),
                        body = o.optString("body"),
                        scheduledEpochMs = o.optLong("scheduledEpochMs"),
                        status = o.optString("status", "scheduled"),
                        createdAtIso = o.optString("createdAtIso")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 100
        private const val MAX_AUDIT_ITEMS = 300
        private val KEY_PIN_ENVELOPE = stringPreferencesKey("dev_pin_envelope")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("dev_biometric_enabled")
        private val KEY_AUTO_LOCK_MS = longPreferencesKey("dev_auto_lock_ms")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("dev_failed_attempts")
        private val KEY_LOCKOUT_UNTIL_MS = longPreferencesKey("dev_lockout_until_ms")
        private val KEY_GITHUB_PAT = stringPreferencesKey("dev_github_pat_env")
        private val KEY_GITHUB_PAT_HINT = stringPreferencesKey("dev_github_pat_hint")
        private val KEY_DIST_REPO = stringPreferencesKey("dev_dist_repo")
        private val KEY_CONSOLE_REPO = stringPreferencesKey("dev_console_repo")
        private val KEY_PRIVATE_UPDATE_REPO = stringPreferencesKey("dev_private_update_repo")
        private val KEY_SOURCE_REPO = stringPreferencesKey("dev_source_repo")
        private val KEY_FIREBASE_PROJECT_ID = stringPreferencesKey("dev_firebase_project_id")
        private val KEY_FEEDBACK_SHEET_ID = stringPreferencesKey("dev_feedback_sheet_id")
        private val KEY_FEEDBACK_RANGE = stringPreferencesKey("dev_feedback_range")
        private val KEY_SHEETS_TOKEN = stringPreferencesKey("dev_sheets_token_env")
        private val KEY_SHEETS_TOKEN_HINT = stringPreferencesKey("dev_sheets_token_hint")
        private val KEY_GA4_PROPERTY_ID = stringPreferencesKey("dev_ga4_property_id")
        private val KEY_GA4_OAUTH_CLIENT_ID = stringPreferencesKey("dev_ga4_oauth_client_id")
        private val KEY_ANALYTICS_AUTH_MODE = stringPreferencesKey("dev_analytics_auth_mode")
        private val KEY_ANALYTICS_TOKEN = stringPreferencesKey("dev_analytics_token_env")
        private val KEY_ANALYTICS_TOKEN_HINT = stringPreferencesKey("dev_analytics_token_hint")
        private val KEY_ANALYTICS_REFRESH_TOKEN = stringPreferencesKey("dev_analytics_refresh_token_env")
        private val KEY_ANALYTICS_EXPIRES_AT_MS = longPreferencesKey("dev_analytics_expires_at_ms")
        private val KEY_GOOGLE_GRANTED_SCOPES = stringSetPreferencesKey("dev_google_granted_scopes")
        private val KEY_IMPORTED_CSV = stringPreferencesKey("dev_imported_csv")
        private val KEY_IMPORTED_CSV_LABEL = stringPreferencesKey("dev_imported_csv_label")
        private val KEY_FEEDBACK_TRIAGE_JSON = stringPreferencesKey("dev_feedback_triage_json")
        private val KEY_SEEN_FEEDBACK_IDS = stringSetPreferencesKey("dev_seen_feedback_ids")
        private val KEY_FEEDBACK_ALERTS_ENABLED = booleanPreferencesKey("dev_feedback_alerts_enabled")
        private val KEY_CUSTOM_HISTORY_JSON = stringPreferencesKey("dev_custom_history_json")
        private val KEY_BROADCAST_TEMPLATES_JSON = stringPreferencesKey("dev_broadcast_templates_json")
        private val KEY_BROADCAST_DRAFTS_JSON = stringPreferencesKey("dev_broadcast_drafts_json")
        private val KEY_SCHEDULED_BROADCASTS_JSON = stringPreferencesKey("dev_scheduled_broadcasts_json")
        private val KEY_CACHED_ANALYTICS_JSON = stringPreferencesKey("dev_cached_analytics_json")
        private val KEY_LAST_FULL_SYNC_ISO = stringPreferencesKey("dev_last_full_sync_iso")
        private val KEY_USED_BROADCAST_IDS = stringSetPreferencesKey("dev_used_broadcast_ids")
        private val KEY_LAST_LIVE_MS = longPreferencesKey("dev_last_live_ms")
        private val KEY_AUDIT_LOG_JSON = stringPreferencesKey("dev_audit_log_json")
    }
}

/**
 * Android Keystore-backed Developer Center vault handling encrypted GitHub/Sheets tokens,
 * separate Developer PIN envelopes, custom broadcast deduplication/cooldown, and redacted audit events.
 */
class DevCenterVault(
    private val storage: DevCenterStorage,
    private val tokenProtector: SecretProtector = AndroidKeyStoreSecretProtector("duit_dev_center_pat_v1"),
    private val pinHashEngine: PinHashEngine = PinHashEngine(AndroidKeyStoreSecretProtector("duit_dev_center_pin_v1"))
) {
    fun observeState(): Flow<DevCenterVaultState> = storage.observe()

    suspend fun getState(): DevCenterVaultState = storage.read()

    suspend fun saveDeveloperPin(pin: String) {
        val chars = pin.toCharArray()
        try {
            val envelope = pinHashEngine.createEncryptedEnvelope(chars)
            storage.update {
                it.copy(
                    isPinConfigured = true,
                    pinEnvelope = envelope,
                    failedAttempts = 0,
                    lockoutUntilEpochMs = 0L
                )
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun verifyDeveloperPin(pin: String): Boolean {
        val envelope = storage.read().pinEnvelope
        val chars = pin.toCharArray()
        return try {
            pinHashEngine.verifyPin(chars, envelope)
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun updateAuthSecuritySettings(
        biometricEnabled: Boolean? = null,
        autoLockTimeout: DevAutoLockTimeout? = null,
        failedAttempts: Int? = null,
        lockoutUntilEpochMs: Long? = null
    ) {
        storage.update { cur ->
            cur.copy(
                isBiometricEnabled = biometricEnabled ?: cur.isBiometricEnabled,
                autoLockTimeout = autoLockTimeout ?: cur.autoLockTimeout,
                failedAttempts = failedAttempts ?: cur.failedAttempts,
                lockoutUntilEpochMs = lockoutUntilEpochMs ?: cur.lockoutUntilEpochMs
            )
        }
    }

    suspend fun saveGithubPat(rawPat: String) {
        val clean = rawPat.trim()
        require(clean.isNotEmpty()) { "GitHub token cannot be blank." }
        val sealed = tokenProtector.seal(clean)
        val hint = DevCenterRedactor.maskToken(clean)
        storage.update { it.copy(encryptedGithubPat = sealed, githubPatHint = hint) }
    }

    suspend fun readGithubPat(): String? {
        val sealed = storage.read().encryptedGithubPat ?: return null
        return tokenProtector.unseal(sealed)?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun removeGithubPat() {
        storage.update { it.copy(encryptedGithubPat = null, githubPatHint = "Not configured") }
    }

    suspend fun saveRepositories(
        distributionRepo: String,
        consoleRepo: String,
        privateUpdateRepo: String = DEFAULT_PRIVATE_UPDATE_REPO
    ) {
        val dist = distributionRepo.trim()
        val console = consoleRepo.trim()
        val priv = privateUpdateRepo.trim()
        require(DevCenterValidators.isValidRepoSlug(dist)) { "Invalid distribution repo slug." }
        require(DevCenterValidators.isValidRepoSlug(console)) { "Invalid console repo slug." }
        require(DevCenterValidators.isValidPrivateUpdateRepo(priv)) { "Invalid private updates repo slug." }
        storage.update { it.copy(distributionRepo = dist, consoleRepo = console, privateUpdateRepo = priv) }
    }

    suspend fun saveFeedbackConfig(sheetId: String, range: String, rawSheetsToken: String?) {
        val cleanSheetId = sheetId.trim()
        val cleanRange = range.trim().ifBlank { DEFAULT_FEEDBACK_RANGE }
        val cleanToken = rawSheetsToken?.trim().orEmpty()
        storage.update { cur ->
            val (sealedToken, hint) = when {
                cleanToken.isNotEmpty() -> tokenProtector.seal(cleanToken) to DevCenterRedactor.maskToken(cleanToken)
                else -> cur.encryptedSheetsToken to cur.sheetsTokenHint
            }
            cur.copy(
                feedbackSheetId = cleanSheetId,
                feedbackSheetRange = cleanRange,
                encryptedSheetsToken = sealedToken,
                sheetsTokenHint = hint
            )
        }
    }

    suspend fun readSheetsToken(): String? {
        val sealed = storage.read().encryptedSheetsToken ?: return null
        return tokenProtector.unseal(sealed)?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun saveImportedCsv(rawCsv: String, label: String) {
        storage.update { it.copy(importedCsvRaw = rawCsv, importedCsvLabel = label) }
    }

    suspend fun clearFeedbackSource() {
        storage.update {
            it.copy(
                feedbackSheetId = "",
                feedbackSheetRange = DEFAULT_FEEDBACK_RANGE,
                encryptedSheetsToken = null,
                sheetsTokenHint = "Not configured",
                importedCsvRaw = null,
                importedCsvLabel = null
            )
        }
    }

    suspend fun clearAllCredentials() {
        storage.update {
            it.copy(
                encryptedGithubPat = null,
                githubPatHint = "Not configured",
                encryptedSheetsToken = null,
                sheetsTokenHint = "Not configured",
                analyticsAuthMode = "none",
                encryptedAnalyticsToken = null,
                analyticsTokenHint = "Not configured",
                encryptedAnalyticsRefreshToken = null,
                analyticsTokenExpiresAtMs = 0L,
                googleGrantedScopes = emptySet(),
                cachedAnalyticsJson = null
            )
        }
    }

    suspend fun saveAnalyticsConfig(
        propertyId: String,
        rawAnalyticsToken: String?,
        oauthClientId: String? = null
    ) {
        val cleanId = propertyId.trim()
        val cleanToken = rawAnalyticsToken?.trim().orEmpty()
        val cleanClientId = oauthClientId?.trim()
        storage.update { cur ->
            val (sealedToken, hint) = when {
                cleanToken.isNotEmpty() -> tokenProtector.seal(cleanToken) to DevCenterRedactor.maskToken(cleanToken)
                else -> cur.encryptedAnalyticsToken to cur.analyticsTokenHint
            }
            val nextMode = when {
                cleanToken.isNotEmpty() -> "manual"
                else -> cur.analyticsAuthMode
            }
            cur.copy(
                ga4PropertyId = cleanId,
                ga4OAuthClientId = cleanClientId ?: cur.ga4OAuthClientId,
                analyticsAuthMode = nextMode,
                encryptedAnalyticsToken = sealedToken,
                analyticsTokenHint = hint,
                encryptedAnalyticsRefreshToken = if (cleanToken.isNotEmpty()) null else cur.encryptedAnalyticsRefreshToken,
                analyticsTokenExpiresAtMs = if (cleanToken.isNotEmpty()) 0L else cur.analyticsTokenExpiresAtMs
            )
        }
    }

    suspend fun saveAnalyticsOAuthSession(
        oauthClientId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
        propertyId: String? = null,
        grantedScopes: Set<String> = emptySet()
    ) {
        val cleanAccess = accessToken.trim()
        val cleanRefresh = refreshToken?.trim()?.takeIf { it.isNotEmpty() }
        val sealedAccess = tokenProtector.seal(cleanAccess)
        val sealedRefresh = cleanRefresh?.let { tokenProtector.seal(it) }
        val hint = DevCenterRedactor.maskToken(cleanAccess)
        storage.update { cur ->
            cur.copy(
                ga4PropertyId = propertyId?.trim()?.takeIf { it.isNotEmpty() } ?: cur.ga4PropertyId,
                ga4OAuthClientId = oauthClientId.trim(),
                analyticsAuthMode = "oauth",
                encryptedAnalyticsToken = sealedAccess,
                analyticsTokenHint = hint,
                encryptedAnalyticsRefreshToken = sealedRefresh ?: cur.encryptedAnalyticsRefreshToken,
                analyticsTokenExpiresAtMs = expiresAtMs,
                googleGrantedScopes = if (grantedScopes.isNotEmpty()) grantedScopes else cur.googleGrantedScopes
            )
        }
    }

    suspend fun updateRefreshedAnalyticsToken(
        accessToken: String,
        rotatedRefreshToken: String?,
        expiresAtMs: Long
    ) {
        val cleanAccess = accessToken.trim()
        val sealedAccess = tokenProtector.seal(cleanAccess)
        val sealedRotated = rotatedRefreshToken?.trim()?.takeIf { it.isNotEmpty() }?.let { tokenProtector.seal(it) }
        val hint = DevCenterRedactor.maskToken(cleanAccess)
        storage.update { cur ->
            cur.copy(
                analyticsAuthMode = "oauth",
                encryptedAnalyticsToken = sealedAccess,
                analyticsTokenHint = hint,
                encryptedAnalyticsRefreshToken = sealedRotated ?: cur.encryptedAnalyticsRefreshToken,
                analyticsTokenExpiresAtMs = expiresAtMs
            )
        }
    }

    suspend fun readAnalyticsToken(): String? {
        val sealed = storage.read().encryptedAnalyticsToken ?: return null
        return tokenProtector.unseal(sealed)?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun readAnalyticsRefreshToken(): String? {
        val sealed = storage.read().encryptedAnalyticsRefreshToken ?: return null
        return tokenProtector.unseal(sealed)?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun clearAnalyticsSource() {
        storage.update {
            it.copy(
                ga4PropertyId = "",
                ga4OAuthClientId = "",
                analyticsAuthMode = "none",
                encryptedAnalyticsToken = null,
                analyticsTokenHint = "Not configured",
                encryptedAnalyticsRefreshToken = null,
                analyticsTokenExpiresAtMs = 0L,
                googleGrantedScopes = emptySet(),
                cachedAnalyticsJson = null
            )
        }
    }

    suspend fun saveSourceAndFirebaseConfig(sourceRepo: String, firebaseProjectId: String) {
        val src = sourceRepo.trim().ifBlank { DEFAULT_SOURCE_REPO }
        val proj = firebaseProjectId.trim().ifBlank { DEFAULT_FIREBASE_PROJECT_ID }
        require(DevCenterValidators.isValidRepoSlug(src)) { "Invalid source repository slug." }
        storage.update { it.copy(sourceRepo = src, firebaseProjectId = proj) }
    }

    suspend fun saveFeedbackTriage(record: FeedbackTriageRecord) {
        storage.update { cur ->
            val next = cur.feedbackTriageMap.toMutableMap()
            next[record.feedbackId] = record
            cur.copy(feedbackTriageMap = next)
        }
    }

    suspend fun markFeedbackSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        storage.update { cur ->
            cur.copy(seenFeedbackIds = (cur.seenFeedbackIds + ids).take(500).toSet())
        }
    }

    suspend fun setFeedbackAlertsEnabled(enabled: Boolean) {
        storage.update { it.copy(feedbackAlertsEnabled = enabled) }
    }

    suspend fun saveBroadcastDraft(draft: BroadcastDraft) {
        storage.update { cur ->
            val filtered = cur.broadcastDrafts.filterNot { it.id == draft.id }
            cur.copy(broadcastDrafts = (listOf(draft) + filtered).take(30))
        }
    }

    suspend fun deleteBroadcastDraft(id: String) {
        storage.update { cur ->
            cur.copy(broadcastDrafts = cur.broadcastDrafts.filterNot { it.id == id })
        }
    }

    suspend fun saveBroadcastTemplate(template: BroadcastTemplate) {
        storage.update { cur ->
            val filtered = cur.broadcastTemplates.filterNot { it.id == template.id }
            cur.copy(broadcastTemplates = (listOf(template) + filtered).take(30))
        }
    }

    suspend fun deleteBroadcastTemplate(id: String) {
        storage.update { cur ->
            cur.copy(broadcastTemplates = cur.broadcastTemplates.filterNot { it.id == id })
        }
    }

    suspend fun saveScheduledBroadcast(item: ScheduledBroadcast) {
        storage.update { cur ->
            val filtered = cur.scheduledBroadcasts.filterNot { it.id == item.id }
            cur.copy(scheduledBroadcasts = (listOf(item) + filtered).sortedBy { it.scheduledEpochMs }.take(30))
        }
    }

    suspend fun updateScheduledBroadcastStatus(id: String, status: String) {
        storage.update { cur ->
            cur.copy(
                scheduledBroadcasts = cur.scheduledBroadcasts.map {
                    if (it.id == id) it.copy(status = status) else it
                }
            )
        }
    }

    suspend fun saveCachedAnalyticsJson(rawJson: String?, syncedAtIso: String?) {
        storage.update { cur ->
            cur.copy(
                cachedAnalyticsJson = rawJson ?: cur.cachedAnalyticsJson,
                lastFullSyncIso = syncedAtIso ?: cur.lastFullSyncIso
            )
        }
    }

    suspend fun saveLastFullSyncIso(syncedAtIso: String) {
        storage.update { it.copy(lastFullSyncIso = syncedAtIso) }
    }

    suspend fun recordBroadcast(entry: BroadcastHistoryEntry, isLiveDispatch: Boolean, nowMs: Long = System.currentTimeMillis()) {
        storage.update { cur ->
            val nextHistory = (listOf(entry) + cur.customHistory).take(100)
            val nextUsed = cur.usedBroadcastIds + entry.broadcastId
            val nextLastLive = if (isLiveDispatch) maxOf(cur.lastLiveDispatchMs, nowMs) else cur.lastLiveDispatchMs
            cur.copy(
                customHistory = nextHistory,
                usedBroadcastIds = nextUsed,
                lastLiveDispatchMs = nextLastLive
            )
        }
    }

    suspend fun appendAuditEvent(entry: AuditLogEntry, extraSecrets: Collection<String> = emptyList()) {
        val cleanEntry = entry.copy(
            summary = DevCenterRedactor.redact(entry.summary, extraSecrets),
            actor = DevCenterRedactor.redact(entry.actor, extraSecrets)
        )
        storage.update { cur ->
            cur.copy(auditLog = (listOf(cleanEntry) + cur.auditLog).take(150))
        }
    }
}
