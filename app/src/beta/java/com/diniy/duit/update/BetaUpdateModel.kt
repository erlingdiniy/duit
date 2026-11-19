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

internal sealed interface BetaUpdateState {
    data object Idle : BetaUpdateState
    data class Offer(val release: BetaRelease) : BetaUpdateState
    data class Permission(val release: BetaRelease) : BetaUpdateState
    data class Downloading(val release: BetaRelease) : BetaUpdateState
    data class AwaitingInstaller(val release: BetaRelease) : BetaUpdateState
    data class Confirm(val release: BetaRelease, val intent: Intent) : BetaUpdateState
    data class Installing(val release: BetaRelease) : BetaUpdateState
    data class Error(val release: BetaRelease) : BetaUpdateState
}

internal class BetaUpdateModel(private val app: Application) : AndroidViewModel(app) {
    private val updates = BetaUpdates(app)
    private val sessionInstaller = app.packageManager.packageInstaller
    private val mutableState = MutableStateFlow<BetaUpdateState>(BetaUpdateState.Idle)
    val state = mutableState.asStateFlow()

    private val lease = Any()
    private var checked = false
    private var downloadJob: Job? = null
    private var timeout: Job? = null
    private var attempt: InstallAttempt? = null

    fun check() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            try {
                updates.check()?.let { mutableState.value = BetaUpdateState.Offer(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed background check must leave the app usable.
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
            is BetaUpdateState.Offer, is BetaUpdateState.Permission, is BetaUpdateState.Error ->
                mutableState.value = BetaUpdateState.Idle
            else -> Unit
        }
    }

    fun download() {
        val release = when (val current = mutableState.value) {
            is BetaUpdateState.Offer -> current.release
            is BetaUpdateState.Permission -> current.release
            is BetaUpdateState.Error -> current.release
            else -> return
        }
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
                updates.downloadAndRequestInstall(release) { sessionId ->
                    withContext(Dispatchers.Main.immediate) {
                        awaitInstaller(release, sessionId).intentSender
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failure = if (updates.canInstall()) BetaUpdateState.Error(release)
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
            PackageInstaller.STATUS_SUCCESS -> finish(BetaUpdateState.Idle)
            PackageInstaller.STATUS_FAILURE_ABORTED -> finish(BetaUpdateState.Offer(release))
            else -> finish(BetaUpdateState.Error(release))
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
