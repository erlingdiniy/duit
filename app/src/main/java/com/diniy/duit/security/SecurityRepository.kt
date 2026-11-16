package com.diniy.duit.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class AutoLockTimeout(val label: String, val durationMs: Long) {
    IMMEDIATE("Immediately", 0L),
    SECONDS_30("After 30 seconds", 30_000L),
    MINUTE_1("After 1 minute", 60_000L),
    MINUTES_5("After 5 minutes", 300_000L)
}

data class SecurityConfigSnapshot(
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
    val hideSensitiveBalances: Boolean = false,
    val privacyModeEnabled: Boolean = false,
    val alwaysMaskWidgetAmounts: Boolean = false,
    val encryptedPinEnvelope: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val lastBackgroundedEpochMs: Long = 0L
) {
    val hasPinConfigured: Boolean get() = !encryptedPinEnvelope.isNullOrBlank()
    val isProtectionActive: Boolean get() = isAppLockEnabled && hasPinConfigured
}

interface SecurityRepository {
    fun observeSecurityConfig(): Flow<SecurityConfigSnapshot>
    suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String)
    suspend fun updatePinEnvelope(encryptedPinEnvelope: String)
    suspend fun disableAppLock()
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout)
    suspend fun setHideSensitiveBalances(hidden: Boolean)
    suspend fun setPrivacyModeEnabled(enabled: Boolean)
    suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean)
    suspend fun resetPrivacyDefaults()
    suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long)
    suspend fun clearFailedAttempts()
    suspend fun recordBackgroundedAt(epochMs: Long)
    suspend fun clearAllSecurityState()
}

private val Context.duitSecurityDataStore: DataStore<Preferences> by preferencesDataStore(name = "duit_security_prefs")

class DataStoreSecurityRepository(context: Context) : SecurityRepository {

    private val dataStore = context.applicationContext.duitSecurityDataStore

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val HIDE_SENSITIVE_BALANCES = booleanPreferencesKey("hide_sensitive_balances")
        val PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
        val ALWAYS_MASK_WIDGET_AMOUNTS = booleanPreferencesKey("always_mask_widget_amounts")
        val ENCRYPTED_PIN_ENVELOPE = stringPreferencesKey("encrypted_pin_envelope")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_EPOCH_MS = longPreferencesKey("lockout_until_epoch_ms")
        val LAST_BACKGROUNDED_EPOCH_MS = longPreferencesKey("last_backgrounded_epoch_ms")
    }

    override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = dataStore.data
        .catch { err ->
            if (err is IOException) emit(emptyPreferences()) else throw err
        }
        .map { prefs ->
            val rawTimeout = prefs[Keys.AUTO_LOCK_TIMEOUT]
            val timeout = AutoLockTimeout.entries.firstOrNull { it.name == rawTimeout } ?: AutoLockTimeout.IMMEDIATE
            SecurityConfigSnapshot(
                isAppLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
                isBiometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
                autoLockTimeout = timeout,
                hideSensitiveBalances = prefs[Keys.HIDE_SENSITIVE_BALANCES] ?: false,
                privacyModeEnabled = prefs[Keys.PRIVACY_MODE_ENABLED] ?: false,
                alwaysMaskWidgetAmounts = prefs[Keys.ALWAYS_MASK_WIDGET_AMOUNTS] ?: false,
                encryptedPinEnvelope = prefs[Keys.ENCRYPTED_PIN_ENVELOPE],
                failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
                lockoutUntilEpochMs = prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] ?: 0L,
                lastBackgroundedEpochMs = prefs[Keys.LAST_BACKGROUNDED_EPOCH_MS] ?: 0L
            )
        }

    override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_PIN_ENVELOPE] = encryptedPinEnvelope
            prefs[Keys.APP_LOCK_ENABLED] = true
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] = 0L
        }
    }

    override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_PIN_ENVELOPE] = encryptedPinEnvelope
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] = 0L
        }
    }

    override suspend fun disableAppLock() {
        dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs.remove(Keys.ENCRYPTED_PIN_ENVELOPE)
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] = 0L
            prefs[Keys.LAST_BACKGROUNDED_EPOCH_MS] = 0L
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_TIMEOUT] = timeout.name
        }
    }

    override suspend fun setHideSensitiveBalances(hidden: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HIDE_SENSITIVE_BALANCES] = hidden
        }
    }

    override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PRIVACY_MODE_ENABLED] = enabled
        }
    }

    override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ALWAYS_MASK_WIDGET_AMOUNTS] = alwaysMask
        }
    }

    override suspend fun resetPrivacyDefaults() {
        dataStore.edit { prefs ->
            prefs[Keys.HIDE_SENSITIVE_BALANCES] = false
            prefs[Keys.PRIVACY_MODE_ENABLED] = false
            prefs[Keys.ALWAYS_MASK_WIDGET_AMOUNTS] = false
        }
    }

    override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = failedAttempts
            prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] = lockoutUntilEpochMs
        }
    }

    override suspend fun clearFailedAttempts() {
        dataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] = 0L
        }
    }

    override suspend fun recordBackgroundedAt(epochMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_BACKGROUNDED_EPOCH_MS] = epochMs
        }
    }

    override suspend fun clearAllSecurityState() {
        dataStore.edit { it.clear() }
    }
}
