package com.diniy.duit.devcenter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diniy.duit.DuitContainer
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassSection
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.theme.LocalVisualTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.ui.DevCenterAuthScreen
import com.diniy.duit.devcenter.ui.DeveloperCenterShell
import com.diniy.duit.domain.model.VisualTheme
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class DevCenterRuntime(
    context: Context,
    val biometricGateway: BiometricAuthGateway
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val vault = DevCenterVault(DataStoreDevCenterStorage(context))
    val transport: DevHttpTransport = HttpsUrlConnectionTransport()
    val githubClient = DevCenterGitHubClient(transport)
    val feedbackClient = DevCenterFeedbackClient(transport)
    val analyticsClient = DevCenterAnalyticsClient(transport)
    val analyticsOAuth = DevCenterAnalyticsOAuth(transport)
    val remoteConfigClient = DevCenterRemoteConfigClient(transport)
    val githubManager = DevCenterGitHubManager(transport, githubClient)
    val singleDeviceFcmClient = DevCenterSingleDeviceFcmClient(transport)
    val infraMonitor = DevCenterInfraMonitor(transport)
    val authManager = DevCenterAuthManager(
        vault = vault,
        biometricGateway = biometricGateway,
        scope = scope
    )
    val viewModel = DevCenterViewModel(
        vault = vault,
        githubClient = githubClient,
        feedbackClient = feedbackClient,
        analyticsClient = analyticsClient,
        analyticsOAuth = analyticsOAuth,
        authManager = authManager,
        scope = scope,
        remoteConfigClient = remoteConfigClient,
        githubManager = githubManager,
        singleDeviceFcmClient = singleDeviceFcmClient,
        infraMonitor = infraMonitor,
        apkIntegrityProvider = { DevApkIntegrityChecker.inspectInstalled(context) },
        onScheduleBroadcast = { item -> DevScheduledBroadcastWorker.scheduleReminder(context, item) },
        onCancelScheduledBroadcast = { id -> DevScheduledBroadcastWorker.cancelReminder(context, id) },
        onSyncFeedbackAlertsWorker = { enabled -> DevFeedbackAlertWorker.syncSchedule(context, enabled) },
        onPostUnseenFeedbackAlert = { count ->
            DevAlertsNotifier.postDeveloperAlert(
                context = context,
                notificationId = 810_002,
                title = if (count == 1) "1 new feedback item" else "$count new feedback items",
                body = "Open Developer Center Feedback Inbox to review."
            )
        }
    )
}

/**
 * Personal Edition compile-time entry point for the integrated Developer Center.
 * Excluded completely from beta and benchmark builds via source-set isolation.
 */
object DeveloperCenterEntryPoint {
    const val isAvailable: Boolean = true

    fun privacySummaryOrNull(fcmEnabled: Boolean): String = if (fcmEnabled) {
        "Your wallets, transactions, plans and saved receipts are stored only on this phone with Android cloud backup disabled. duit. goes online for Firebase developer broadcasts, optional opt-in crash reports and anonymous usage analytics (both off by default, advertising IDs permanently disabled), feedback you submit, and inside your PIN-locked Developer Center."
    } else {
        "Your wallets, transactions, plans and saved receipts are stored only on this phone with Android cloud backup disabled. Firebase Analytics and public telemetry remain excluded; internet access is used only inside your PIN-locked Developer Center."
    }

    @Volatile
    private var runtime: DevCenterRuntime? = null

    private fun obtainRuntime(container: DuitContainer, context: Context): DevCenterRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: DevCenterRuntime(
                context = context.applicationContext,
                biometricGateway = container.biometricGateway
            ).also { runtime = it }
        }
    }

    fun onAppBackgrounded() {
        runtime?.authManager?.onAppBackgrounded()
    }

    @Composable
    fun SettingsDeveloperSection(onOpenDeveloperCenter: () -> Unit) {
        val colors = SpatialTheme.colors
        val typography = SpatialTheme.typography
        val isLiquidGlass = LocalVisualTheme.current == VisualTheme.LIQUID_GLASS
        val accent = colors.lavender

        SpatialGlassSection(title = "Developer") {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(if (isLiquidGlass) 14.dp else 20.dp))
                        .candyPressable(onClick = onOpenDeveloperCenter, onClickLabel = "Open Developer Center")
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLiquidGlass) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = colors.readableOn(accent),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .rotate(-6f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = colors.onCoral,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(6f)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Center",
                            style = typography.transactionDescription,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Releases, broadcasts & remote switches",
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                    if (isLiquidGlass) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.secondaryText.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.baseSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DeveloperCenterHost(
        container: DuitContainer,
        isAppLockEnabled: Boolean,
        onBack: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context.findActivity()
        val rt = obtainRuntime(container, context)
        val authState by rt.authManager.uiState.collectAsStateWithLifecycle()
        val uiState by rt.viewModel.uiState.collectAsStateWithLifecycle()
        val pendingOAuthUri by DevAnalyticsOAuthBus.pendingCallbackUri.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current

        // Enforce FLAG_SECURE whenever Developer Center is active so screenshots/Recents never capture secrets.
        DisposableEffect(activity, isAppLockEnabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (!isAppLockEnabled) {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> rt.authManager.onAppForegrounded()
                    Lifecycle.Event.ON_STOP -> rt.authManager.onAppBackgrounded()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            rt.authManager.onEnterDeveloperCenter()
        }

        LaunchedEffect(authState.stage) {
            if (authState.stage == DevLockStage.UNLOCKED) {
                rt.viewModel.refreshAll()
            }
        }

        LaunchedEffect(authState.stage, pendingOAuthUri) {
            if (authState.stage == DevLockStage.UNLOCKED && pendingOAuthUri != null) {
                DevAnalyticsOAuthBus.consumeCallbackUri()?.let { callbackUri ->
                    rt.viewModel.handleAnalyticsOAuthRedirect(callbackUri)
                }
            }
        }

        val triggerBiometric: () -> Unit = {
            val fragActivity = activity as? FragmentActivity
            if (fragActivity != null && authState.canUseBiometricNow) {
                rt.biometricGateway.authenticate(
                    activity = fragActivity,
                    title = "Developer Center",
                    subtitle = "Verify identity for remote controls",
                    negativeButtonText = "Use PIN",
                    onResult = { result ->
                        when (result) {
                            BiometricAuthResult.Succeeded -> rt.authManager.onBiometricSucceeded()
                            BiometricAuthResult.Cancelled -> rt.authManager.onBiometricCancelled()
                            is BiometricAuthResult.Error -> rt.authManager.onBiometricError(result.message)
                        }
                    }
                )
            }
        }

        LaunchedEffect(authState.stage, authState.shouldAutoPromptBiometric) {
            if (authState.stage == DevLockStage.LOCKED && authState.shouldAutoPromptBiometric) {
                rt.authManager.consumeAutoBiometricPrompt()
                triggerBiometric()
            }
        }

        if (authState.stage != DevLockStage.UNLOCKED) {
            DevCenterAuthScreen(
                authState = authState,
                onBack = onBack,
                onSetupPin = rt.authManager::setupNewPin,
                onSubmitPin = rt.authManager::verifyPinAndUnlock,
                onTriggerBiometric = triggerBiometric,
                onDismissError = rt.authManager::clearError
            )
            return
        }

        DeveloperCenterShell(
            uiState = uiState,
            authState = authState,
            viewModel = rt.viewModel,
            onBack = {
                if (authState.autoLockTimeout == DevAutoLockTimeout.IMMEDIATE) {
                    rt.authManager.lockNow()
                }
                onBack()
            },
            onLockNow = { rt.authManager.lockNow() }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
