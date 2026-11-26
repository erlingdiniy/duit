package com.diniy.duit.devcenter

import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.PinHashEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DevLockStage {
    LOADING,
    NEEDS_PIN_SETUP,
    LOCKED,
    UNLOCKED
}

data class DevAuthUiState(
    val stage: DevLockStage = DevLockStage.LOADING,
    val isBiometricEnabled: Boolean = true,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val autoLockTimeout: DevAutoLockTimeout = DevAutoLockTimeout.FIVE_MINUTES,
    val failedAttempts: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val isVerifying: Boolean = false,
    val errorMessage: String? = null,
    val shouldAutoPromptBiometric: Boolean = false
) {
    val canUseBiometricNow: Boolean
        get() = stage == DevLockStage.LOCKED &&
            isBiometricEnabled &&
            biometricAvailability == BiometricAvailability.AVAILABLE

    val isThrottled: Boolean get() = lockoutRemainingSeconds > 0
}

/**
 * Coordinates Developer Center authentication independently from financial App Lock.
 * Supports separate 6-digit PIN setup/verification, progressive lockout, optional biometric
 * authentication, background locking, and foreground inactivity auto-lock.
 */
class DevCenterAuthManager(
    private val vault: DevCenterVault,
    private val biometricGateway: BiometricAuthGateway,
    private val scope: CoroutineScope,
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _uiState = MutableStateFlow(DevAuthUiState())
    val uiState: StateFlow<DevAuthUiState> = _uiState.asStateFlow()

    private var latestVaultState: DevCenterVaultState = DevCenterVaultState()
    private var lastActivityEpochMs: Long = 0L
    private var backgroundedAtEpochMs: Long = 0L
    private var ignoreNextBackgroundUntilMs: Long = 0L
    private var countdownJob: Job? = null

    init {
        scope.launch {
            vault.observeState().collectLatest { v ->
                latestVaultState = v
                val availability = biometricGateway.checkAvailability()
                val now = currentTimeMs()
                val remaining = computeRemainingSeconds(v.lockoutUntilEpochMs, now)

                _uiState.update { cur ->
                    val nextStage = when {
                        !v.isPinConfigured -> DevLockStage.NEEDS_PIN_SETUP
                        cur.stage == DevLockStage.UNLOCKED -> DevLockStage.UNLOCKED
                        else -> DevLockStage.LOCKED
                    }
                    cur.copy(
                        stage = nextStage,
                        isBiometricEnabled = v.isBiometricEnabled,
                        biometricAvailability = availability,
                        autoLockTimeout = v.autoLockTimeout,
                        failedAttempts = v.failedAttempts,
                        lockoutRemainingSeconds = remaining
                    )
                }

                if (remaining > 0) {
                    startLockoutCountdown(v.lockoutUntilEpochMs)
                } else {
                    countdownJob?.cancel()
                }
            }
        }
    }

    fun onEnterDeveloperCenter() {
        val v = latestVaultState
        val availability = biometricGateway.checkAvailability()
        val now = currentTimeMs()
        val remaining = computeRemainingSeconds(v.lockoutUntilEpochMs, now)
        if (!v.isPinConfigured) {
            _uiState.update {
                it.copy(
                    stage = DevLockStage.NEEDS_PIN_SETUP,
                    biometricAvailability = availability,
                    lockoutRemainingSeconds = 0,
                    errorMessage = null,
                    shouldAutoPromptBiometric = false
                )
            }
            return
        }

        // Check whether unlocked session expired while away or closed.
        val isUnlocked = _uiState.value.stage == DevLockStage.UNLOCKED
        val timeout = v.autoLockTimeout
        val shouldRelock = !isUnlocked ||
            timeout == DevAutoLockTimeout.IMMEDIATE ||
            (lastActivityEpochMs > 0L && (now - lastActivityEpochMs) >= timeout.timeoutMs)

        if (shouldRelock) {
            val autoBio = v.isBiometricEnabled &&
                availability == BiometricAvailability.AVAILABLE &&
                remaining == 0
            _uiState.update {
                it.copy(
                    stage = DevLockStage.LOCKED,
                    biometricAvailability = availability,
                    lockoutRemainingSeconds = remaining,
                    errorMessage = null,
                    shouldAutoPromptBiometric = autoBio
                )
            }
        } else {
            lastActivityEpochMs = now
            _uiState.update {
                it.copy(
                    biometricAvailability = availability,
                    shouldAutoPromptBiometric = false
                )
            }
        }
    }

    fun suppressNextBackgroundLockForPicker() {
        ignoreNextBackgroundUntilMs = currentTimeMs() + 30_000L
    }

    fun clearPickerBackgroundSuppression() {
        ignoreNextBackgroundUntilMs = 0L
    }

    fun recordUserActivity() {
        if (_uiState.value.stage == DevLockStage.UNLOCKED) {
            lastActivityEpochMs = currentTimeMs()
        }
    }

    fun checkInactivityTimeout() {
        val cur = _uiState.value
        if (cur.stage != DevLockStage.UNLOCKED) return
        val timeoutMs = when (cur.autoLockTimeout) {
            DevAutoLockTimeout.IMMEDIATE -> 60_000L // Foreground 1-min idle fallback when Immediate is chosen
            else -> cur.autoLockTimeout.timeoutMs
        }
        val now = currentTimeMs()
        if (lastActivityEpochMs > 0L && (now - lastActivityEpochMs) >= timeoutMs) {
            lockNow("Locked after inactivity.")
        }
    }

    fun onAppBackgrounded() {
        val now = currentTimeMs()
        if (now < ignoreNextBackgroundUntilMs) {
            return
        }
        backgroundedAtEpochMs = now
        if (_uiState.value.stage == DevLockStage.UNLOCKED) {
            if (_uiState.value.autoLockTimeout == DevAutoLockTimeout.IMMEDIATE) {
                lockNow()
            }
        }
    }

    fun onAppForegrounded() {
        ignoreNextBackgroundUntilMs = 0L
        if (_uiState.value.stage == DevLockStage.UNLOCKED && backgroundedAtEpochMs > 0L) {
            val timeoutMs = _uiState.value.autoLockTimeout.timeoutMs
            val now = currentTimeMs()
            if (timeoutMs == 0L || (now - backgroundedAtEpochMs) >= timeoutMs) {
                lockNow()
            }
        }
        backgroundedAtEpochMs = 0L
    }

    fun lockNow(reason: String? = null) {
        if (!latestVaultState.isPinConfigured) return
        lastActivityEpochMs = 0L
        _uiState.update {
            it.copy(
                stage = DevLockStage.LOCKED,
                shouldAutoPromptBiometric = false,
                errorMessage = reason
            )
        }
    }

    fun consumeAutoBiometricPrompt() {
        _uiState.update { it.copy(shouldAutoPromptBiometric = false) }
    }

    fun setupNewPin(pin: String, confirmPin: String) {
        if (!isValidPin(pin)) {
            _uiState.update { it.copy(errorMessage = "Enter a 6-digit numeric Developer PIN.") }
            return
        }
        if (pin != confirmPin) {
            _uiState.update { it.copy(errorMessage = "Developer PIN confirmation does not match.") }
            return
        }
        if (_uiState.value.isVerifying) return
        _uiState.update { it.copy(isVerifying = true, errorMessage = null) }

        scope.launch {
            withContext(cryptoDispatcher) {
                vault.saveDeveloperPin(pin)
            }
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${currentTimeMs()}",
                    timestampIso = isoUtcNow(currentTimeMs()),
                    actor = "operator",
                    action = "dev_auth.pin_configured",
                    target = "developer_pin",
                    status = "ok",
                    summary = "Configured separate Developer Center 6-digit PIN."
                )
            )
            lastActivityEpochMs = currentTimeMs()
            _uiState.update {
                it.copy(
                    stage = DevLockStage.UNLOCKED,
                    isVerifying = false,
                    failedAttempts = 0,
                    lockoutRemainingSeconds = 0,
                    errorMessage = null
                )
            }
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmPin: String, onDone: (Result<Unit>) -> Unit) {
        if (!isValidPin(newPin)) {
            onDone(Result.failure(IllegalArgumentException("New Developer PIN must be 6 digits.")))
            return
        }
        if (newPin != confirmPin) {
            onDone(Result.failure(IllegalArgumentException("PIN confirmation does not match.")))
            return
        }
        scope.launch {
            val valid = withContext(cryptoDispatcher) { vault.verifyDeveloperPin(currentPin) }
            if (!valid) {
                onDone(Result.failure(IllegalArgumentException("Current Developer PIN is incorrect.")))
                return@launch
            }
            withContext(cryptoDispatcher) { vault.saveDeveloperPin(newPin) }
            vault.appendAuditEvent(
                AuditLogEntry(
                    id = "aud-${currentTimeMs()}",
                    timestampIso = isoUtcNow(currentTimeMs()),
                    actor = "operator",
                    action = "dev_auth.pin_changed",
                    target = "developer_pin",
                    status = "ok",
                    summary = "Updated Developer Center 6-digit PIN."
                )
            )
            onDone(Result.success(Unit))
        }
    }

    fun verifyPinAndUnlock(rawPin: String) {
        if (_uiState.value.isVerifying) return
        val v = latestVaultState
        val now = currentTimeMs()
        val remaining = computeRemainingSeconds(v.lockoutUntilEpochMs, now)
        if (remaining > 0) {
            _uiState.update {
                it.copy(
                    lockoutRemainingSeconds = remaining,
                    errorMessage = "Too many attempts. Try again in ${remaining}s."
                )
            }
            return
        }
        if (!isValidPin(rawPin)) {
            _uiState.update { it.copy(errorMessage = "Enter a 6-digit numeric PIN.") }
            return
        }

        _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
        scope.launch {
            val valid = withContext(cryptoDispatcher) { vault.verifyDeveloperPin(rawPin) }
            val verifyDoneMs = currentTimeMs()
            if (valid) {
                countdownJob?.cancel()
                vault.updateAuthSecuritySettings(failedAttempts = 0, lockoutUntilEpochMs = 0L)
                lastActivityEpochMs = verifyDoneMs
                _uiState.update {
                    it.copy(
                        stage = DevLockStage.UNLOCKED,
                        isVerifying = false,
                        failedAttempts = 0,
                        lockoutRemainingSeconds = 0,
                        errorMessage = null
                    )
                }
            } else {
                val nextAttempts = v.failedAttempts + 1
                val lockoutMs = lockoutDurationMsFor(nextAttempts)
                val untilMs = if (lockoutMs > 0L) verifyDoneMs + lockoutMs else 0L
                vault.updateAuthSecuritySettings(failedAttempts = nextAttempts, lockoutUntilEpochMs = untilMs)
                val lockoutSecs = computeRemainingSeconds(untilMs, verifyDoneMs)
                val msg = if (lockoutSecs > 0) {
                    "Incorrect Developer PIN. Locked for ${lockoutSecs}s."
                } else {
                    "Incorrect Developer PIN."
                }
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        failedAttempts = nextAttempts,
                        lockoutRemainingSeconds = lockoutSecs,
                        errorMessage = msg
                    )
                }
                if (lockoutSecs > 0) startLockoutCountdown(untilMs)
            }
        }
    }

    fun onBiometricSucceeded() {
        countdownJob?.cancel()
        scope.launch {
            vault.updateAuthSecuritySettings(failedAttempts = 0, lockoutUntilEpochMs = 0L)
        }
        lastActivityEpochMs = currentTimeMs()
        _uiState.update {
            it.copy(
                stage = DevLockStage.UNLOCKED,
                shouldAutoPromptBiometric = false,
                failedAttempts = 0,
                lockoutRemainingSeconds = 0,
                errorMessage = null
            )
        }
    }

    fun onBiometricCancelled() {
        _uiState.update {
            it.copy(
                shouldAutoPromptBiometric = false,
                errorMessage = "Biometric cancelled. Enter your 6-digit Developer PIN."
            )
        }
    }

    fun onBiometricError(message: String) {
        val availability = biometricGateway.checkAvailability()
        _uiState.update {
            it.copy(
                biometricAvailability = availability,
                shouldAutoPromptBiometric = false,
                errorMessage = if (message.isNotBlank()) "$message Use Developer PIN." else "Use Developer PIN."
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        scope.launch {
            vault.updateAuthSecuritySettings(biometricEnabled = enabled)
        }
    }

    fun setAutoLockTimeout(timeout: DevAutoLockTimeout) {
        scope.launch {
            vault.updateAuthSecuritySettings(autoLockTimeout = timeout)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startLockoutCountdown(lockoutUntilEpochMs: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val secs = computeRemainingSeconds(lockoutUntilEpochMs, currentTimeMs())
                _uiState.update { it.copy(lockoutRemainingSeconds = secs) }
                if (secs <= 0) {
                    _uiState.update {
                        if (it.errorMessage?.startsWith("Incorrect") == true || it.errorMessage?.startsWith("Too many") == true) {
                            it.copy(errorMessage = null)
                        } else {
                            it
                        }
                    }
                    break
                }
                delay(1_000L)
            }
        }
    }

    companion object {
        fun isValidPin(pin: String): Boolean =
            pin.length == PinHashEngine.PIN_LENGTH && pin.all { it in '0'..'9' }

        fun lockoutDurationMsFor(attempts: Int): Long = when {
            attempts >= 10 -> 300_000L
            attempts >= 8 -> 120_000L
            attempts >= 5 -> 30_000L
            else -> 0L
        }

        fun computeRemainingSeconds(lockoutUntilEpochMs: Long, nowMs: Long): Int {
            if (lockoutUntilEpochMs <= nowMs) return 0
            return (((lockoutUntilEpochMs - nowMs) + 999L) / 1000L).toInt().coerceAtLeast(1)
        }
    }
}

internal fun isoUtcNow(epochMs: Long = System.currentTimeMillis()): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(epochMs))
}
