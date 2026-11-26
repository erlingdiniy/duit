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
    val feedbackSheetId: String = "",
    val feedbackSheetRange: String = DEFAULT_FEEDBACK_RANGE,
    val encryptedSheetsToken: String? = null,
    val sheetsTokenHint: String = "Not configured",
    val importedCsvRaw: String? = null,
    val importedCsvLabel: String? = null,
    val customHistory: List<BroadcastHistoryEntry> = emptyList(),
    val usedBroadcastIds: Set<String> = emptySet(),
    val lastLiveDispatchMs: Long = 0L,
    val auditLog: List<AuditLogEntry> = emptyList()
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
            feedbackSheetId = prefs[KEY_FEEDBACK_SHEET_ID] ?: "",
            feedbackSheetRange = prefs[KEY_FEEDBACK_RANGE] ?: DEFAULT_FEEDBACK_RANGE,
            encryptedSheetsToken = sheetsEnv,
            sheetsTokenHint = prefs[KEY_SHEETS_TOKEN_HINT] ?: if (sheetsEnv.isNullOrBlank()) "Not configured" else "Configured",
            importedCsvRaw = prefs[KEY_IMPORTED_CSV],
            importedCsvLabel = prefs[KEY_IMPORTED_CSV_LABEL],
            customHistory = decodeHistory(prefs[KEY_CUSTOM_HISTORY_JSON]),
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
        prefs[KEY_FEEDBACK_SHEET_ID] = next.feedbackSheetId
        prefs[KEY_FEEDBACK_RANGE] = next.feedbackSheetRange
        if (next.encryptedSheetsToken.isNullOrBlank()) {
            prefs.remove(KEY_SHEETS_TOKEN)
            prefs.remove(KEY_SHEETS_TOKEN_HINT)
        } else {
            prefs[KEY_SHEETS_TOKEN] = next.encryptedSheetsToken
            prefs[KEY_SHEETS_TOKEN_HINT] = next.sheetsTokenHint
        }
        if (next.importedCsvRaw.isNullOrBlank()) prefs.remove(KEY_IMPORTED_CSV) else prefs[KEY_IMPORTED_CSV] = next.importedCsvRaw
        if (next.importedCsvLabel.isNullOrBlank()) prefs.remove(KEY_IMPORTED_CSV_LABEL) else prefs[KEY_IMPORTED_CSV_LABEL] = next.importedCsvLabel
        prefs[KEY_CUSTOM_HISTORY_JSON] = encodeHistory(next.customHistory.take(MAX_HISTORY_ITEMS))
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

    companion object {
        private const val MAX_HISTORY_ITEMS = 100
        private const val MAX_AUDIT_ITEMS = 150
        private val KEY_PIN_ENVELOPE = stringPreferencesKey("dev_pin_envelope")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("dev_biometric_enabled")
        private val KEY_AUTO_LOCK_MS = longPreferencesKey("dev_auto_lock_ms")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("dev_failed_attempts")
        private val KEY_LOCKOUT_UNTIL_MS = longPreferencesKey("dev_lockout_until_ms")
        private val KEY_GITHUB_PAT = stringPreferencesKey("dev_github_pat_env")
        private val KEY_GITHUB_PAT_HINT = stringPreferencesKey("dev_github_pat_hint")
        private val KEY_DIST_REPO = stringPreferencesKey("dev_dist_repo")
        private val KEY_CONSOLE_REPO = stringPreferencesKey("dev_console_repo")
        private val KEY_FEEDBACK_SHEET_ID = stringPreferencesKey("dev_feedback_sheet_id")
        private val KEY_FEEDBACK_RANGE = stringPreferencesKey("dev_feedback_range")
        private val KEY_SHEETS_TOKEN = stringPreferencesKey("dev_sheets_token_env")
        private val KEY_SHEETS_TOKEN_HINT = stringPreferencesKey("dev_sheets_token_hint")
        private val KEY_IMPORTED_CSV = stringPreferencesKey("dev_imported_csv")
        private val KEY_IMPORTED_CSV_LABEL = stringPreferencesKey("dev_imported_csv_label")
        private val KEY_CUSTOM_HISTORY_JSON = stringPreferencesKey("dev_custom_history_json")
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

    suspend fun saveRepositories(distributionRepo: String, consoleRepo: String) {
        val dist = distributionRepo.trim()
        val console = consoleRepo.trim()
        require(DevCenterValidators.isValidRepoSlug(dist)) { "Invalid distribution repo slug." }
        require(DevCenterValidators.isValidRepoSlug(console)) { "Invalid console repo slug." }
        storage.update { it.copy(distributionRepo = dist, consoleRepo = console) }
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
                sheetsTokenHint = "Not configured"
            )
        }
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
