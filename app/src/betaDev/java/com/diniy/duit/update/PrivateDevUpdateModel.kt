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
import com.diniy.duit.devcenter.DEFAULT_PRIVATE_UPDATE_REPO
import com.diniy.duit.devcenter.DevCenterRedactor
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

internal enum class PrivateUpdateProblem(val canRetry: Boolean) {
    AUTH(false),
    NOT_PRIVATE_REPO(false),
    NETWORK(true),
    TIMEOUT(true),
    INVALID_APK(true),
    STORAGE(true),
    SIGNATURE(false),
    INSTALL(true),
    GENERIC(true)
}

internal fun classifyPrivateUpdateFailure(error: Throwable): PrivateUpdateProblem {
    val msg = error.message.orEmpty()
    return when {
        error is AuthRejectedException || "HTTP 401" in msg || "HTTP 403" in msg -> PrivateUpdateProblem.AUTH
        error is NotPrivateRepoException || "not private" in msg || "public repository" in msg -> PrivateUpdateProblem.NOT_PRIVATE_REPO
        error is java.net.SocketTimeoutException -> PrivateUpdateProblem.TIMEOUT
        error is java.net.UnknownHostException || error is java.net.ConnectException || error is javax.net.ssl.SSLException -> PrivateUpdateProblem.NETWORK
        "ENOSPC" in msg || "No space" in msg -> PrivateUpdateProblem.STORAGE
        "signing certificate" in msg || "signer" in msg -> PrivateUpdateProblem.SIGNATURE
        "SHA-256" in msg || "Invalid APK" in msg || "exceeds" in msg || "metadata" in msg || "Package ID" in msg || "versionCode" in msg || "private developer build" in msg -> PrivateUpdateProblem.INVALID_APK
        error is java.io.IOException -> PrivateUpdateProblem.NETWORK
        else -> PrivateUpdateProblem.GENERIC
    }
}

internal sealed interface PrivateUpdateState {
    data object Idle : PrivateUpdateState
    data class Offer(val release: PrivateDevRelease, val cancelled: Boolean = false) : PrivateUpdateState
    data class Permission(val release: PrivateDevRelease) : PrivateUpdateState
    data class Downloading(val release: PrivateDevRelease, val progress: Float = -1f) : PrivateUpdateState
    data class AwaitingInstaller(val release: PrivateDevRelease) : PrivateUpdateState
    data class Confirm(val release: PrivateDevRelease, val intent: Intent) : PrivateUpdateState
    data class Installing(val release: PrivateDevRelease) : PrivateUpdateState
    data class Error(val release: PrivateDevRelease, val problem: PrivateUpdateProblem = PrivateUpdateProblem.GENERIC) : PrivateUpdateState
}

internal data class PrivateWhatsNewState(
    val versionName: String,
    val versionCode: Long,
    val notes: String? = null,
    val unavailable: Boolean = false
)

internal class PrivateDevUpdateModel(private val app: Application) : AndroidViewModel(app) {
    private val updates = PrivateDevUpdates(app)
    private val memory = PrivateDevUpdateMemory(app)
    private val sessionInstaller = app.packageManager.packageInstaller
    private val mutableState = MutableStateFlow<PrivateUpdateState>(PrivateUpdateState.Idle)
    val state = mutableState.asStateFlow()

    val installedVersionName: String = updates.installedVersionName()
    private val mutableWhatsNew = MutableStateFlow<PrivateWhatsNewState?>(null)
    val whatsNew = mutableWhatsNew.asStateFlow()
    private val mutableLastCheck = MutableStateFlow(memory.lastCheckEpochMs to memory.lastCheckResult)
    val lastCheck = mutableLastCheck.asStateFlow()
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()
    private val mutableRepo = MutableStateFlow(DEFAULT_PRIVATE_UPDATE_REPO)
    val repo = mutableRepo.asStateFlow()

    private val lease = Any()
    private var checked = false
    private var downloadJob: Job? = null
    private var timeout: Job? = null
    private var attempt: InstallAttempt? = null

    init {
        prepareWhatsNew()
        viewModelScope.launch {
            mutableRepo.value = updates.currentRepo()
        }
    }

    private fun prepareWhatsNew() {
        val code = runCatching { updates.installedVersionCode() }.getOrNull() ?: return
        val fresh = runCatching { updates.isFreshInstall() }.getOrDefault(true)
        val acknowledged = memory.acknowledgedVersionCode
        if (!shouldShowPrivateWhatsNew(acknowledged, code, fresh)) {
            if (acknowledged == null) memory.acknowledgedVersionCode = code
            return
        }
        val cached = memory.cachedNotes(code)
        mutableWhatsNew.value = PrivateWhatsNewState(installedVersionName, code, notes = cached)
        if (cached != null) return
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

    fun acknowledgeWhatsNew() {
        val shown = mutableWhatsNew.value ?: return
        memory.acknowledgedVersionCode = shown.versionCode
        mutableWhatsNew.value = null
    }

    fun check() {
        if (checked) return
        checked = true
        runCheck(manual = false)
    }

    fun checkNow() {
        if (mutableChecking.value || mutableState.value !is PrivateUpdateState.Idle) return
        checked = true
        runCheck(manual = true)
    }

    private fun runCheck(manual: Boolean) {
        mutableChecking.value = true
        viewModelScope.launch {
            try {
                mutableRepo.value = updates.currentRepo()
                when (val result = updates.check()) {
                    is PrivateCheckResult.Available -> {
                        mutableRepo.value = result.repo
                        recordCheck("Update ready: v${result.release.versionName}")
                        com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("available")
                        mutableState.value = PrivateUpdateState.Offer(result.release)
                    }
                    is PrivateCheckResult.UpToDate -> {
                        mutableRepo.value = result.repo
                        recordCheck("Up to date")
                    }
                    PrivateCheckResult.AuthRequired -> {
                        recordCheck(
                            if (manual) "Add GitHub PAT in Developer Settings"
                            else "Private channel requires GitHub PAT"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Exception) {
                val safeMsg = when (classifyPrivateUpdateFailure(err)) {
                    PrivateUpdateProblem.AUTH -> "GitHub PAT lacks access to private repo"
                    PrivateUpdateProblem.NOT_PRIVATE_REPO -> DevCenterRedactor.redact(err.message ?: "Repo must be private")
                    PrivateUpdateProblem.NETWORK -> "Offline — couldn't check updates"
                    PrivateUpdateProblem.TIMEOUT -> "Timed out checking private updates"
                    else -> "Private update check unavailable"
                }
                recordCheck(safeMsg)
            } finally {
                mutableChecking.value = false
            }
        }
    }

    private fun recordCheck(result: String) {
        val now = System.currentTimeMillis()
        memory.lastCheckEpochMs = now
        memory.lastCheckResult = result
        mutableLastCheck.value = now to result
    }

    fun refreshPermission() {
        val waiting = mutableState.value as? PrivateUpdateState.Permission ?: return
        if (updates.canInstall()) mutableState.value = PrivateUpdateState.Offer(waiting.release)
    }

    fun settingsUnavailable() {
        val waiting = mutableState.value as? PrivateUpdateState.Permission ?: return
        mutableState.value = PrivateUpdateState.Error(waiting.release)
    }

    fun dismiss() {
        when (mutableState.value) {
            is PrivateUpdateState.Downloading -> {
                downloadJob?.cancel()
                releaseLease()
                mutableState.value = PrivateUpdateState.Idle
            }
            is PrivateUpdateState.Offer, is PrivateUpdateState.Permission, is PrivateUpdateState.Error -> {
                if (mutableState.value is PrivateUpdateState.Offer) {
                    com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("dismissed")
                }
                mutableState.value = PrivateUpdateState.Idle
            }
            else -> Unit
        }
    }

    fun download() {
        val release = when (val current = mutableState.value) {
            is PrivateUpdateState.Offer -> current.release
            is PrivateUpdateState.Permission -> current.release
            is PrivateUpdateState.Error -> current.release.takeIf { current.problem.canRetry } ?: return
            else -> return
        }
        com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("started")
        if (!updates.canInstall()) {
            mutableState.value = PrivateUpdateState.Permission(release)
            return
        }
        if (PrivateDevInstallCallbacks.owner != null) {
            mutableState.value = PrivateUpdateState.Error(release)
            return
        }
        PrivateDevInstallCallbacks.owner = lease
        mutableState.value = PrivateUpdateState.Downloading(release)
        downloadJob = viewModelScope.launch {
            try {
                updates.downloadAndRequestInstall(release, onProgress = { progress ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        if (mutableState.value is PrivateUpdateState.Downloading) {
                            mutableState.value = PrivateUpdateState.Downloading(release, progress)
                        }
                    }
                }) { sessionId ->
                    withContext(Dispatchers.Main.immediate) {
                        awaitInstaller(release, sessionId).intentSender
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Exception) {
                val failure = if (updates.canInstall()) {
                    PrivateUpdateState.Error(release, classifyPrivateUpdateFailure(err))
                } else {
                    PrivateUpdateState.Permission(release)
                }
                finish(failure, abandon = true)
            }
        }
    }

    fun confirmationLaunched() {
        val current = mutableState.value as? PrivateUpdateState.Confirm ?: return
        mutableState.value = PrivateUpdateState.Installing(current.release)
        waitForInstaller(current.release, 10.minutes.inWholeMilliseconds)
    }

    fun confirmationFailed() {
        val release = when (val current = mutableState.value) {
            is PrivateUpdateState.Confirm -> current.release
            is PrivateUpdateState.Installing -> current.release
            else -> return
        }
        finish(PrivateUpdateState.Error(release), abandon = true)
    }

    private fun awaitInstaller(release: PrivateDevRelease, sessionId: Int): PendingIntent {
        val callbackUri = Uri.Builder()
            .scheme(INSTALL_URI_SCHEME)
            .authority(app.packageName)
            .appendPath(sessionId.toString())
            .appendPath(UUID.randomUUID().toString())
            .build()
        val callbackIntent = Intent(app, PrivateDevInstallStatusReceiver::class.java)
            .setAction(PRIVATE_INSTALL_STATUS_ACTION)
            .setData(callbackUri)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(app, 0, callbackIntent, flags)
        attempt = InstallAttempt(sessionId, callbackUri, callback)
        PrivateDevInstallCallbacks.callbacks[callbackUri] = { handleStatus(release, it) }
        mutableState.value = PrivateUpdateState.AwaitingInstaller(release)
        waitForInstaller(release, 2.minutes.inWholeMilliseconds)
        return callback
    }

    private fun handleStatus(release: PrivateDevRelease, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (mutableState.value !is PrivateUpdateState.AwaitingInstaller) return
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation == null) {
                    finish(PrivateUpdateState.Error(release), abandon = true)
                } else {
                    mutableState.value = PrivateUpdateState.Confirm(release, confirmation)
                    waitForInstaller(release, 10.minutes.inWholeMilliseconds)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                com.diniy.duit.analytics.UsageAnalytics.logUpdatePromptAction("installed")
                finish(PrivateUpdateState.Idle)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> finish(PrivateUpdateState.Offer(release, cancelled = true))
            PackageInstaller.STATUS_FAILURE_STORAGE -> finish(PrivateUpdateState.Error(release, PrivateUpdateProblem.STORAGE))
            PackageInstaller.STATUS_FAILURE_INVALID -> finish(PrivateUpdateState.Error(release, PrivateUpdateProblem.INVALID_APK))
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> finish(PrivateUpdateState.Error(release, PrivateUpdateProblem.SIGNATURE))
            else -> finish(PrivateUpdateState.Error(release, PrivateUpdateProblem.INSTALL))
        }
    }

    private fun waitForInstaller(release: PrivateDevRelease, millis: Long) {
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(millis)
            finish(PrivateUpdateState.Error(release), abandon = true)
        }
    }

    private fun finish(next: PrivateUpdateState, abandon: Boolean = false) {
        timeout?.cancel()
        timeout = null
        attempt?.let { current ->
            PrivateDevInstallCallbacks.callbacks.remove(current.uri)
            current.callback.cancel()
            if (abandon) runCatching { sessionInstaller.abandonSession(current.sessionId) }
        }
        attempt = null
        releaseLease()
        mutableState.value = next
    }

    private fun releaseLease() {
        if (PrivateDevInstallCallbacks.owner === lease) PrivateDevInstallCallbacks.owner = null
    }

    override fun onCleared() {
        finish(PrivateUpdateState.Idle, abandon = mutableState.value !is PrivateUpdateState.Installing)
        super.onCleared()
    }

    private data class InstallAttempt(val sessionId: Int, val uri: Uri, val callback: PendingIntent)
}

private object PrivateDevInstallCallbacks {
    var owner: Any? = null
    val callbacks = mutableMapOf<Uri, (Intent) -> Unit>()
}

class PrivateDevInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PRIVATE_INSTALL_STATUS_ACTION) return
        val uri = intent.data ?: return
        if (uri.scheme != INSTALL_URI_SCHEME || uri.authority != context.packageName) return
        val sessionId = uri.pathSegments.firstOrNull()?.toIntOrNull() ?: return
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != sessionId) return
        val callback = PrivateDevInstallCallbacks.callbacks[uri]
        if (callback != null) {
            callback(intent)
            return
        }
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) ==
            PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            val installer = context.packageManager.packageInstaller
            runCatching {
                if (installer.getSessionInfo(sessionId)?.appPackageName == context.packageName) {
                    installer.abandonSession(sessionId)
                }
            }
        }
    }
}

private const val PRIVATE_INSTALL_STATUS_ACTION = "com.diniy.duit.PRIVATE_DEV_INSTALL_STATUS"
private const val INSTALL_URI_SCHEME = "duit-dev-install"
