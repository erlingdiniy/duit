package com.diniy.duit.security

import com.diniy.duit.core.ui.state.PrivacySettings
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

data class AppLockUiState(
    val isReady: Boolean = false,
    val isLocked: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
    val hideSensitiveBalances: Boolean = false,
    val privacyModeEnabled: Boolean = false,
    val alwaysMaskWidgetAmounts: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val shouldPromptBiometricAutomatically: Boolean = false,
    val isVerifyingPin: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val authErrorMessage: String? = null
) {
    val privacySettings: PrivacySettings
        get() = PrivacySettings(
            hideSensitiveBalances = hideSensitiveBalances,
            privacyModeEnabled = privacyModeEnabled
        )

    val canUseBiometricNow: Boolean
        get() = isAppLockEnabled && isBiometricEnabled && biometricAvailability == BiometricAvailability.AVAILABLE

    val isThrottled: Boolean get() = lockoutRemainingSeconds > 0
}

/**
 * Stateful offline-first security & app-lock coordinator.
 * Keeps authentication state strictly isolated from financial Room records and UI navigation.
 */
class AppLockManager(
    private val securityRepository: SecurityRepository,
    private val pinHashEngine: PinHashEngine,
    private val biometricGateway: BiometricAuthGateway,
    private val scope: CoroutineScope,
    private val onFactoryResetFinancialData: suspend () -> Unit,
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var latestSnapshot: SecurityConfigSnapshot? = null
    private var initialHydrationDone = false
    private var hasUnlockedInCurrentSession = false
    private var suppressAutoBiometricOnNextLock = false
    private var countdownJob: Job? = null

    init {
        scope.launch {
            securityRepository.observeSecurityConfig().collectLatest { snapshot ->
                latestSnapshot = snapshot
                val availability = biometricGateway.checkAvailability()
                val now = currentTimeMs()
                val remainingSecs = computeRemainingSeconds(snapshot.lockoutUntilEpochMs, now)

                val shouldLock = if (!snapshot.isProtectionActive) {
                    hasUnlockedInCurrentSession = false
                    false
                } else if (!initialHydrationDone) {
                    // Cold start or process recreation with protection enabled: always require auth!
                    true
                } else if (!hasUnlockedInCurrentSession) {
                    true
                } else {
                    _uiState.value.isLocked
                }

                val triggerBiometric = !initialHydrationDone &&
                    shouldLock &&
                    snapshot.isBiometricEnabled &&
                    availability == BiometricAvailability.AVAILABLE

                initialHydrationDone = true

                _uiState.update { cur ->
                    cur.copy(
                        isReady = true,
                        isLocked = shouldLock,
                        isAppLockEnabled = snapshot.isAppLockEnabled,
                        isBiometricEnabled = snapshot.isBiometricEnabled,
                        autoLockTimeout = snapshot.autoLockTimeout,
                        hideSensitiveBalances = snapshot.hideSensitiveBalances,
                        privacyModeEnabled = snapshot.privacyModeEnabled,
                        alwaysMaskWidgetAmounts = snapshot.alwaysMaskWidgetAmounts,
                        biometricAvailability = availability,
                        shouldPromptBiometricAutomatically = if (triggerBiometric) true else cur.shouldPromptBiometricAutomatically && shouldLock,
                        failedAttempts = snapshot.failedAttempts,
                        lockoutRemainingSeconds = remainingSecs
                    )
                }

                if (remainingSecs > 0) {
                    startLockoutCountdown(snapshot.lockoutUntilEpochMs)
                } else {
                    countdownJob?.cancel()
                }
            }
        }
    }

    fun refreshBiometricStatus() {
        val availability = biometricGateway.checkAvailability()
        _uiState.update { cur ->
            val invalidatedMessage = if (
                cur.isLocked &&
                cur.isBiometricEnabled &&
                cur.biometricAvailability == BiometricAvailability.AVAILABLE &&
                availability != BiometricAvailability.AVAILABLE
            ) {
                "Biometrics changed or unavailable. Unlock with your 6-digit PIN."
            } else {
                cur.authErrorMessage
            }
            cur.copy(
                biometricAvailability = availability,
                shouldPromptBiometricAutomatically = cur.shouldPromptBiometricAutomatically && availability == BiometricAvailability.AVAILABLE,
                authErrorMessage = invalidatedMessage
            )
        }
    }

    fun onAppBackgrounded() {
        val snapshot = latestSnapshot ?: return
        if (!snapshot.isProtectionActive) return
        val now = currentTimeMs()
        scope.launch {
            securityRepository.recordBackgroundedAt(now)
        }
        if (snapshot.autoLockTimeout == AutoLockTimeout.IMMEDIATE) {
            hasUnlockedInCurrentSession = false
            _uiState.update {
                it.copy(
                    isLocked = true,
                    shouldPromptBiometricAutomatically = false,
                    authErrorMessage = null
                )
            }
        }
    }

    fun onAppForegrounded() {
        val snapshot = latestSnapshot ?: return
        val availability = biometricGateway.checkAvailability()
        if (!snapshot.isProtectionActive) {
            _uiState.update { it.copy(biometricAvailability = availability) }
            return
        }

        val now = currentTimeMs()
        val lastBg = snapshot.lastBackgroundedEpochMs
        val timedOut = when {
            !hasUnlockedInCurrentSession -> true
            _uiState.value.isLocked -> true
            lastBg <= 0L -> false
            now < lastBg -> true // Defensive lock if system clock moved backward
            else -> (now - lastBg) >= snapshot.autoLockTimeout.durationMs
        }

        if (timedOut) {
            hasUnlockedInCurrentSession = false
            val allowAutoBio = !suppressAutoBiometricOnNextLock &&
                snapshot.isBiometricEnabled &&
                availability == BiometricAvailability.AVAILABLE &&
                computeRemainingSeconds(snapshot.lockoutUntilEpochMs, now) == 0
            suppressAutoBiometricOnNextLock = false

            val biometricWarning = if (
                snapshot.isBiometricEnabled &&
                availability != BiometricAvailability.AVAILABLE
            ) {
                when (availability) {
                    BiometricAvailability.NONE_ENROLLED -> "No device biometrics enrolled. Enter your 6-digit PIN."
                    BiometricAvailability.CHANGED_OR_INVALIDATED -> "Biometric security changed. Enter your 6-digit PIN."
                    BiometricAvailability.UNAVAILABLE -> "Biometrics unavailable. Enter your 6-digit PIN."
                    BiometricAvailability.AVAILABLE -> null
                }
            } else {
                null
            }

            _uiState.update { cur ->
                cur.copy(
                    isLocked = true,
                    biometricAvailability = availability,
                    shouldPromptBiometricAutomatically = allowAutoBio,
                    authErrorMessage = cur.authErrorMessage ?: biometricWarning
                )
            }
        } else {
            _uiState.update { it.copy(biometricAvailability = availability) }
        }
    }

    fun lockNow() {
        val snapshot = latestSnapshot ?: return
        if (!snapshot.isProtectionActive) return
        hasUnlockedInCurrentSession = false
        suppressAutoBiometricOnNextLock = true
        _uiState.update {
            it.copy(
                isLocked = true,
                shouldPromptBiometricAutomatically = false,
                authErrorMessage = null
            )
        }
    }

    fun consumeAutomaticBiometricPrompt() {
        _uiState.update { it.copy(shouldPromptBiometricAutomatically = false) }
    }

    fun onBiometricSucceeded() {
        hasUnlockedInCurrentSession = true
        suppressAutoBiometricOnNextLock = false
        scope.launch {
            securityRepository.clearFailedAttempts()
            securityRepository.recordBackgroundedAt(0L)
        }
        _uiState.update {
            it.copy(
                isLocked = false,
                shouldPromptBiometricAutomatically = false,
                failedAttempts = 0,
                lockoutRemainingSeconds = 0,
                authErrorMessage = null
            )
        }
    }

    fun onBiometricCancelled() {
        _uiState.update {
            it.copy(
                shouldPromptBiometricAutomatically = false,
                authErrorMessage = "Biometric prompt cancelled. Enter your 6-digit PIN."
            )
        }
    }

    fun onBiometricError(message: String) {
        val availability = biometricGateway.checkAvailability()
        _uiState.update {
            it.copy(
                biometricAvailability = availability,
                shouldPromptBiometricAutomatically = false,
                authErrorMessage = if (message.isNotBlank()) "$message Use your 6-digit PIN." else "Use your 6-digit PIN."
            )
        }
    }

    fun verifyPinAndUnlock(rawPin: String) {
        val snapshot = latestSnapshot ?: return
        val now = currentTimeMs()
        val remaining = computeRemainingSeconds(snapshot.lockoutUntilEpochMs, now)
        if (remaining > 0) {
            _uiState.update {
                it.copy(
                    lockoutRemainingSeconds = remaining,
                    authErrorMessage = "Too many attempts. Try again in ${remaining}s."
                )
            }
            return
        }
        if (!pinHashEngine.isValidPinFormat(rawPin)) {
            _uiState.update { it.copy(authErrorMessage = "Enter a 6-digit numeric PIN.") }
            return
        }

        _uiState.update { it.copy(isVerifyingPin = true, authErrorMessage = null) }
        val chars = rawPin.toCharArray()
        scope.launch {
            val valid = withContext(cryptoDispatcher) {
                try {
                    pinHashEngine.verifyPin(chars, snapshot.encryptedPinEnvelope)
                } finally {
                    chars.fill('\u0000')
                }
            }
            if (valid) {
                hasUnlockedInCurrentSession = true
                suppressAutoBiometricOnNextLock = false
                latestSnapshot = latestSnapshot?.copy(
                    failedAttempts = 0,
                    lockoutUntilEpochMs = 0L,
                    lastBackgroundedEpochMs = 0L
                )
                securityRepository.clearFailedAttempts()
                securityRepository.recordBackgroundedAt(0L)
                _uiState.update {
                    it.copy(
                        isLocked = false,
                        isVerifyingPin = false,
                        shouldPromptBiometricAutomatically = false,
                        failedAttempts = 0,
                        lockoutRemainingSeconds = 0,
                        authErrorMessage = null
                    )
                }
            } else {
                val nextAttempts = snapshot.failedAttempts + 1
                val cooldownMs = cooldownDurationForAttempts(nextAttempts)
                val lockoutUntil = if (cooldownMs > 0L) currentTimeMs() + cooldownMs else 0L
                latestSnapshot = latestSnapshot?.copy(
                    failedAttempts = nextAttempts,
                    lockoutUntilEpochMs = lockoutUntil
                )
                securityRepository.recordFailedAttempt(nextAttempts, lockoutUntil)
                val nextRemaining = computeRemainingSeconds(lockoutUntil, currentTimeMs())
                val errorText = if (nextRemaining > 0) {
                    "Too many incorrect attempts. Try again in ${nextRemaining}s."
                } else {
                    val left = (THROTTLE_THRESHOLD - nextAttempts).coerceAtLeast(1)
                    "Incorrect PIN. $left attempt${if (left == 1) "" else "s"} before temporary lockout."
                }
                _uiState.update {
                    it.copy(
                        isVerifyingPin = false,
                        failedAttempts = nextAttempts,
                        lockoutRemainingSeconds = nextRemaining,
                        authErrorMessage = errorText
                    )
                }
                if (nextRemaining > 0) {
                    startLockoutCountdown(lockoutUntil)
                }
            }
        }
    }

    suspend fun setupPinAndEnableAppLock(pin: String): Result<Unit> {
        if (!pinHashEngine.isValidPinFormat(pin)) {
            return Result.failure(IllegalArgumentException("PIN must be exactly 6 digits."))
        }
        val chars = pin.toCharArray()
        val envelope = withContext(cryptoDispatcher) {
            try {
                pinHashEngine.createEncryptedEnvelope(chars)
            } finally {
                chars.fill('\u0000')
            }
        }
        hasUnlockedInCurrentSession = true
        latestSnapshot = (latestSnapshot ?: SecurityConfigSnapshot()).copy(
            encryptedPinEnvelope = envelope,
            isAppLockEnabled = true,
            failedAttempts = 0,
            lockoutUntilEpochMs = 0L
        )
        securityRepository.savePinAndEnableAppLock(envelope)
        return Result.success(Unit)
    }

    suspend fun verifyCurrentPin(pin: String): Boolean {
        val snapshot = latestSnapshot ?: return false
        if (!pinHashEngine.isValidPinFormat(pin)) return false
        val chars = pin.toCharArray()
        return withContext(cryptoDispatcher) {
            try {
                pinHashEngine.verifyPin(chars, snapshot.encryptedPinEnvelope)
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    suspend fun changePin(currentPin: String, newPin: String): Result<Unit> {
        if (!verifyCurrentPin(currentPin)) {
            return Result.failure(IllegalArgumentException("Current PIN is incorrect."))
        }
        if (!pinHashEngine.isValidPinFormat(newPin)) {
            return Result.failure(IllegalArgumentException("New PIN must be exactly 6 digits."))
        }
        val chars = newPin.toCharArray()
        val envelope = withContext(cryptoDispatcher) {
            try {
                pinHashEngine.createEncryptedEnvelope(chars)
            } finally {
                chars.fill('\u0000')
            }
        }
        latestSnapshot = (latestSnapshot ?: SecurityConfigSnapshot()).copy(
            encryptedPinEnvelope = envelope,
            failedAttempts = 0,
            lockoutUntilEpochMs = 0L
        )
        securityRepository.updatePinEnvelope(envelope)
        return Result.success(Unit)
    }

    suspend fun disableAppLockWithPin(currentPin: String): Result<Unit> {
        if (!verifyCurrentPin(currentPin)) {
            return Result.failure(IllegalArgumentException("Current PIN is incorrect."))
        }
        latestSnapshot = (latestSnapshot ?: SecurityConfigSnapshot()).copy(
            isAppLockEnabled = false,
            isBiometricEnabled = false,
            encryptedPinEnvelope = null,
            failedAttempts = 0,
            lockoutUntilEpochMs = 0L,
            lastBackgroundedEpochMs = 0L
        )
        securityRepository.disableAppLock()
        hasUnlockedInCurrentSession = false
        _uiState.update { it.copy(isLocked = false, isAppLockEnabled = false, authErrorMessage = null) }
        return Result.success(Unit)
    }

    suspend fun setBiometricEnabled(enabled: Boolean): Result<Unit> {
        if (enabled) {
            val availability = biometricGateway.checkAvailability()
            if (availability != BiometricAvailability.AVAILABLE) {
                val msg = when (availability) {
                    BiometricAvailability.NONE_ENROLLED ->
                        "No fingerprint or face unlock is enrolled on this device. Set it up in Android Settings first."
                    else -> "Biometric hardware is unavailable on this device."
                }
                return Result.failure(IllegalStateException(msg))
            }
        }
        securityRepository.setBiometricEnabled(enabled)
        return Result.success(Unit)
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        securityRepository.setAutoLockTimeout(timeout)
    }

    suspend fun setHideSensitiveBalances(hidden: Boolean) {
        securityRepository.setHideSensitiveBalances(hidden)
    }

    suspend fun setPrivacyModeEnabled(enabled: Boolean) {
        securityRepository.setPrivacyModeEnabled(enabled)
    }

    suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
        securityRepository.setAlwaysMaskWidgetAmounts(alwaysMask)
    }

    suspend fun toggleBalanceVisibilityFromHome() {
        val current = _uiState.value
        if (current.privacySettings.isAnyMaskActive) {
            securityRepository.setHideSensitiveBalances(false)
            securityRepository.setPrivacyModeEnabled(false)
        } else {
            securityRepository.setHideSensitiveBalances(true)
        }
    }

    suspend fun resetProtectedAccessAndWipeData() {
        onFactoryResetFinancialData()
        securityRepository.clearAllSecurityState()
        hasUnlockedInCurrentSession = false
        suppressAutoBiometricOnNextLock = false
        _uiState.update {
            AppLockUiState(
                isReady = true,
                isLocked = false,
                biometricAvailability = biometricGateway.checkAvailability()
            )
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    private fun startLockoutCountdown(lockoutUntilEpochMs: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val rem = computeRemainingSeconds(lockoutUntilEpochMs, currentTimeMs())
                _uiState.update { cur ->
                    cur.copy(
                        lockoutRemainingSeconds = rem,
                        authErrorMessage = if (rem > 0) "Too many incorrect attempts. Try again in ${rem}s." else null
                    )
                }
                if (rem <= 0) break
                delay(1_000L)
            }
        }
    }

    companion object {
        const val THROTTLE_THRESHOLD = 5

        fun cooldownDurationForAttempts(attempts: Int): Long = when {
            attempts < THROTTLE_THRESHOLD -> 0L
            attempts == 5 -> 30_000L
            attempts == 6 -> 60_000L
            else -> 300_000L
        }

        fun computeRemainingSeconds(lockoutUntilEpochMs: Long, nowEpochMs: Long): Int {
            val diff = lockoutUntilEpochMs - nowEpochMs
            if (diff <= 0L) return 0
            return ((diff + 999L) / 1000L).toInt()
        }
    }
}
