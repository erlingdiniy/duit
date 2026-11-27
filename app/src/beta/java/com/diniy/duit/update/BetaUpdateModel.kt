package com.diniy.duit.update

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** Why an update stopped. Drives friendly copy and whether a retry is offered. */
internal enum class UpdateProblem(val canRetry: Boolean) {
    NETWORK(true), TIMEOUT(true), SERVER(true), INVALID_APK(true), STORAGE(true), INSTALL(true),
    SIGNATURE(false), // a different signer can never install over this app; retrying cannot help
    GENERIC(true)
}

internal fun classifyUpdateFailure(error: Throwable): UpdateProblem {
    val message = error.message.orEmpty()
    return when {
        error is java.net.SocketTimeoutException -> UpdateProblem.TIMEOUT
        error is java.net.UnknownHostException || error is java.net.ConnectException ||
            error is javax.net.ssl.SSLException -> UpdateProblem.NETWORK
        "ENOSPC" in message || "No space" in message -> UpdateProblem.STORAGE
        "signer does not match" in message -> UpdateProblem.SIGNATURE
        "integrity" in message || "Invalid APK" in message || "exceeds its declared size" in message -> UpdateProblem.INVALID_APK
        "HTTP" in message || "update server" in message || "redirect" in message -> UpdateProblem.SERVER
        error is java.io.IOException -> UpdateProblem.NETWORK
        else -> UpdateProblem.GENERIC
    }
}

internal sealed interface BetaUpdateState {
    data object Idle : BetaUpdateState
    data class Offer(val release: BetaRelease, val cancelled: Boolean = false) : BetaUpdateState
    data class Permission(val release: BetaRelease) : BetaUpdateState
    /** [progress] is 0..1 from real bytes received; below 0 until the first bytes arrive. */
    data class Downloading(val release: BetaRelease, val progress: Float = -1f) : BetaUpdateState
    data class AwaitingInstaller(val release: BetaRelease) : BetaUpdateState
    data class Confirm(val release: BetaRelease, val intent: Intent) : BetaUpdateState
    data class Installing(val release: BetaRelease) : BetaUpdateState
    data class Error(val release: BetaRelease, val problem: UpdateProblem = UpdateProblem.GENERIC) : BetaUpdateState
}

/** Post-update welcome. [notes] is null while loading; [unavailable] when offline or the release has none. */
internal data class WhatsNewState(
    val versionName: String,
    val versionCode: Long,
    val notes: String? = null,
    val unavailable: Boolean = false
)

internal class BetaUpdateModel(private val app: Application) : AndroidViewModel(app) {
    private val updates = BetaUpdates(app)
    private val memory = UpdateMemory(app)
    private val sessionInstaller = app.packageManager.packageInstaller
    private val mutableState = MutableStateFlow<BetaUpdateState>(BetaUpdateState.Idle)
    val state = mutableState.asStateFlow()
    val installedVersionName: String = updates.installedVersionName()

    private val mutableWhatsNew = MutableStateFlow<WhatsNewState?>(null)
    val whatsNew = mutableWhatsNew.asStateFlow()
    private val mutableLastCheck = MutableStateFlow(memory.lastCheckEpochMs to memory.lastCheckResult)
    val lastCheck = mutableLastCheck.asStateFlow()
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()

    init {
        prepareWhatsNew()
    }

    private fun prepareWhatsNew() {
        val code = runCatching { updates.installedVersionCode() }.getOrNull() ?: return
        val fresh = runCatching { updates.isFreshInstall() }.getOrDefault(true)
        val acknowledged = memory.acknowledgedVersionCode
        if (!shouldShowWhatsNew(acknowledged, code, fresh)) {
            // Fresh install: remember this build so the welcome only follows a real upgrade.
            if (acknowledged == null) memory.acknowledgedVersionCode = code
            return
        }
        val cached = memory.cachedNotes(code)
        mutableWhatsNew.value = WhatsNewState(installedVersionName, code, notes = cached)
        if (cached != null) return
        // Fetched in the background; the welcome shows at once and fills in when notes arrive.
        viewModelScope.launch {
            val notes = try {
                updates.notesForInstalled()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val current = mutableWhatsNew.value ?: return@launch
            mutableWhatsNew.value = if (notes == null) {
                current.copy(unavailable = true)
            } else {
                memory.cacheNotes(code, notes)
                current.copy(notes = notes)
            }
        }
    }

    /** Persisted only when the user dismisses, so process death simply shows it again. */
    fun acknowledgeWhatsNew() {
        val shown = mutableWhatsNew.value ?: return
        memory.acknowledgedVersionCode = shown.versionCode
        mutableWhatsNew.value = null
    }

    /** Manual "Check for updates" from Settings. */
    fun checkNow() {
        if (mutableChecking.value || mutableState.value !is BetaUpdateState.Idle) return
        checked = true
        runCheck()
    }

    private fun recordCheck(result: String) {
        val now = System.currentTimeMillis()
        memory.lastCheckEpochMs = now
        memory.lastCheckResult = result
        mutableLastCheck.value = now to result
    }

    private val lease = Any()
    private var checked = false
    private var downloadJob: Job? = null
    private var timeout: Job? = null
    private var attempt: InstallAttempt? = null

    fun check() {
        if (checked) return
        checked = true
        runCheck()
    }

    private fun runCheck() {
        mutableChecking.value = true
        viewModelScope.launch {
            try {
                val release = updates.check()
                if (release != null) {
                    recordCheck("Update available: v${release.versionName.ifBlank { "build ${release.versionCode}" }}")
                    com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("available")
                    mutableState.value = BetaUpdateState.Offer(release)
                } else {
                    recordCheck("You're up to date")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed check must leave the app usable.
                recordCheck(
                    when (classifyUpdateFailure(error)) {
                        UpdateProblem.NETWORK -> "Couldn't check: you're offline"
                        UpdateProblem.TIMEOUT -> "Couldn't check: the connection timed out"
                        else -> "Couldn't check: GitHub is unavailable"
                    }
                )
            } finally {
                mutableChecking.value = false
            }
        }
    }

    fun refreshPermission() {
        val waiting = mutableState.value as? BetaUpdateState.Permission ?: return
        if (updates.canInstall()) mutableState.value = BetaUpdateState.Offer(waiting.release)
    }

    fun settingsUnavailable() {
        val waiting = mutableState.value as? BetaUpdateState.Permission ?: return
        mutableState.value = BetaUpdateState.Error(waiting.release)
    }

    fun dismiss() {
        when (mutableState.value) {
            is BetaUpdateState.Downloading -> {
                downloadJob?.cancel()
                releaseLease()
                mutableState.value = BetaUpdateState.Idle
            }
            is BetaUpdateState.Offer, is BetaUpdateState.Permission, is BetaUpdateState.Error -> {
                if (mutableState.value is BetaUpdateState.Offer) {
                    com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("dismissed")
                }
                mutableState.value = BetaUpdateState.Idle
            }
            else -> Unit
        }
    }

    fun download() {
        val release = when (val current = mutableState.value) {
            is BetaUpdateState.Offer -> current.release
            is BetaUpdateState.Permission -> current.release
            is BetaUpdateState.Error -> current.release.takeIf { current.problem.canRetry } ?: return
            else -> return
        }
        com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("started")
        if (!updates.canInstall()) {
            mutableState.value = BetaUpdateState.Permission(release)
            return
        }
        if (BetaInstallCallbacks.owner != null) {
            mutableState.value = BetaUpdateState.Error(release)
            return
        }
        BetaInstallCallbacks.owner = lease
        mutableState.value = BetaUpdateState.Downloading(release)
        downloadJob = viewModelScope.launch {
            try {
                updates.downloadAndRequestInstall(release, onProgress = { progress ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        if (mutableState.value is BetaUpdateState.Downloading) {
                            mutableState.value = BetaUpdateState.Downloading(release, progress)
                        }
                    }
                }) { sessionId ->
                    withContext(Dispatchers.Main.immediate) {
                        awaitInstaller(release, sessionId).intentSender
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = if (updates.canInstall()) BetaUpdateState.Error(release, classifyUpdateFailure(error))
                else BetaUpdateState.Permission(release)
                finish(failure, abandon = true)
            }
        }
    }

    fun confirmationLaunched() {
        val current = mutableState.value as? BetaUpdateState.Confirm ?: return
        mutableState.value = BetaUpdateState.Installing(current.release)
        waitForInstaller(current.release, 10.minutes.inWholeMilliseconds)
    }

    fun confirmationFailed() {
        val release = when (val current = mutableState.value) {
            is BetaUpdateState.Confirm -> current.release
            is BetaUpdateState.Installing -> current.release
            else -> return
        }
        finish(BetaUpdateState.Error(release), abandon = true)
    }

    private fun awaitInstaller(release: BetaRelease, sessionId: Int): PendingIntent {
        val callbackUri = Uri.Builder()
            .scheme("duit-beta-install")
            .authority(app.packageName)
            .appendPath(sessionId.toString())
            .appendPath(UUID.randomUUID().toString())
            .build()
        val callbackIntent = Intent(app, BetaInstallStatusReceiver::class.java)
            .setAction(INSTALL_STATUS_ACTION)
            .setData(callbackUri)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(app, 0, callbackIntent, flags)
        attempt = InstallAttempt(sessionId, callbackUri, callback)
        BetaInstallCallbacks.callbacks[callbackUri] = { handleStatus(release, it) }
        mutableState.value = BetaUpdateState.AwaitingInstaller(release)
        waitForInstaller(release, 2.minutes.inWholeMilliseconds)
        return callback
    }

    private fun handleStatus(release: BetaRelease, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (mutableState.value !is BetaUpdateState.AwaitingInstaller) return
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation == null) {
                    finish(BetaUpdateState.Error(release), abandon = true)
                } else {
                    mutableState.value = BetaUpdateState.Confirm(release, confirmation)
                    waitForInstaller(release, 10.minutes.inWholeMilliseconds)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("installed")
                finish(BetaUpdateState.Idle)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> finish(BetaUpdateState.Offer(release, cancelled = true))
            PackageInstaller.STATUS_FAILURE_STORAGE -> finish(BetaUpdateState.Error(release, UpdateProblem.STORAGE))
            PackageInstaller.STATUS_FAILURE_INVALID -> finish(BetaUpdateState.Error(release, UpdateProblem.INVALID_APK))
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> finish(BetaUpdateState.Error(release, UpdateProblem.SIGNATURE))
            else -> finish(BetaUpdateState.Error(release, UpdateProblem.INSTALL))
        }
    }

    private fun waitForInstaller(release: BetaRelease, millis: Long) {
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(millis)
            finish(BetaUpdateState.Error(release), abandon = true)
        }
    }

    private fun finish(next: BetaUpdateState, abandon: Boolean = false) {
        timeout?.cancel()
        timeout = null
        attempt?.let { current ->
            BetaInstallCallbacks.callbacks.remove(current.uri)
            current.callback.cancel()
            if (abandon) runCatching { sessionInstaller.abandonSession(current.sessionId) }
        }
        attempt = null
        releaseLease()
        mutableState.value = next
    }

    private fun releaseLease() {
        if (BetaInstallCallbacks.owner === lease) BetaInstallCallbacks.owner = null
    }

    override fun onCleared() {
        // The installer activity can outlive this screen after handoff. Keep its session alive
        // while removing our status binding and cancelling any further UI callbacks.
        finish(BetaUpdateState.Idle, abandon = mutableState.value !is BetaUpdateState.Installing)
        super.onCleared()
    }

    private data class InstallAttempt(val sessionId: Int, val uri: Uri, val callback: PendingIntent)
}

// Registration and delivery both run on the main thread.
private object BetaInstallCallbacks {
    var owner: Any? = null
    val callbacks = mutableMapOf<Uri, (Intent) -> Unit>()
}

class BetaInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INSTALL_STATUS_ACTION) return
        val uri = intent.data ?: return
        if (uri.scheme != "duit-beta-install" || uri.authority != context.packageName) return
        val sessionId = uri.pathSegments.firstOrNull()?.toIntOrNull() ?: return
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != sessionId) return
        val callback = BetaInstallCallbacks.callbacks[uri]
        if (callback != null) {
            callback(intent)
            return
        }
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) ==
            PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            // The app process died before confirmation. Drop the session and offer a fresh retry.
            val installer = context.packageManager.packageInstaller
            runCatching {
                if (installer.getSessionInfo(sessionId)?.appPackageName == context.packageName) {
                    installer.abandonSession(sessionId)
                }
            }
        }
    }
}

private const val INSTALL_STATUS_ACTION = "com.diniy.duit.BETA_INSTALL_STATUS"
