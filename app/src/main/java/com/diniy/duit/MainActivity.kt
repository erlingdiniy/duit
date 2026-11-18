package com.diniy.duit

import android.content.Intent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diniy.duit.core.ui.components.LocalMotionPreferences
import com.diniy.duit.core.ui.components.MotionPreferences
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.feature.navigation.DuitAppShell
import com.diniy.duit.notifications.AndroidNotificationHelper
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.widgets.WidgetDeepLink

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DuitApplication).container
        val lockManager = container.appLockManager
        extractNotificationDeepLink(intent)?.let { target ->
            container.setPendingDeepLink(target)
        }
        WidgetDeepLink.extract(intent)?.let { target ->
            container.setPendingWidgetDeepLink(target)
        }

        // Phase 10B.3: mirror the saved theme into the per-app night mode (Android 12+) so the
        // system splash and launch window use the right theme from the very first frame, even
        // when the app is set to Dark while the system is Light. Only real stored values apply.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                container.userPreferencesRepository.observeAppSettings()
                    .map { it.themeMode }
                    .distinctUntilChanged()
                    .collect { mode ->
                        val night = when (mode) {
                            ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                            ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
                            ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                        }
                        getSystemService(android.app.UiModeManager::class.java)?.setApplicationNightMode(night)
                    }
            }
        }

        setContent {
            val appSettings by container.userPreferencesRepository
                .observeAppSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettingsSnapshot())
            val themeMode = appSettings.themeMode
            val appLockState by lockManager.uiState.collectAsStateWithLifecycle()
            // Phase 10B.2: play the animated splash only for a fresh launch. Skip it when the activity
            // is restored (config change / process restore) or reopened from Recents. Resuming a
            // live activity (Home, Recents, screen unlock) never re-runs setContent, so it skips too.
            var splashDone by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(!isFreshLaunch(savedInstanceState))
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DisposableEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
                onDispose {}
            }

            // Prevent recent-app switcher previews & screen capture leaks when app lock is active.
            DisposableEffect(appLockState.isAppLockEnabled) {
                if (appLockState.isAppLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {}
            }

            val triggerBiometricPrompt: () -> Unit = {
                lockManager.refreshBiometricStatus()
                if (lockManager.uiState.value.canUseBiometricNow) {
                    container.biometricGateway.authenticate(
                        activity = this@MainActivity,
                        title = "Unlock duit.",
                        subtitle = "Verify your identity to access your finances",
                        negativeButtonText = "Use PIN",
                        onResult = { result ->
                            when (result) {
                                BiometricAuthResult.Succeeded -> lockManager.onBiometricSucceeded()
                                BiometricAuthResult.Cancelled -> lockManager.onBiometricCancelled()
                                is BiometricAuthResult.Error -> lockManager.onBiometricError(result.message)
                            }
                        }
                    )
                }
            }

            // The automatic biometric prompt waits for the splash so it never covers the animation.
            LaunchedEffect(appLockState.isLocked, appLockState.shouldPromptBiometricAutomatically, splashDone) {
                if (splashDone &&
                    appLockState.isLocked &&
                    appLockState.shouldPromptBiometricAutomatically &&
                    appLockState.canUseBiometricNow
                ) {
                    lockManager.consumeAutomaticBiometricPrompt()
                    triggerBiometricPrompt()
                }
            }

            DuitTheme(
                themeMode = themeMode,
                glassIntensity = appSettings.glassIntensity
            ) {
                CompositionLocalProvider(
                    LocalPrivacySettings provides appLockState.privacySettings,
                    LocalMotionPreferences provides MotionPreferences(
                        // Phase 11J: in-app motion controls removed. Approved defaults apply; stored values
                        // are kept untouched, and Android's own animation scale is still respected.
                        reduceMotion = false,
                        animationIntensity = com.diniy.duit.domain.model.AnimationIntensity.BALANCED,
                        hapticFeedbackEnabled = appSettings.hapticFeedbackEnabled
                    )
                ) {
                    androidx.compose.foundation.layout.Box {
                        com.diniy.duit.core.ui.components.CandySuccessHost {
                        DuitAppShell(
                            container = container,
                            appLockState = appLockState,
                            onTriggerBiometric = triggerBiometricPrompt
                        )
                        }
                        // App Lock / Home renders underneath, so the splash never delays startup.
                        if (!splashDone) {
                            com.diniy.duit.core.ui.components.CandySplash(onFinished = { splashDone = true }, isDark = isDark)
                        }
                    }
                }
            }
        }
    }

    /** New task / first activity creation, not a restore and not a Recents re-entry. */
    private fun isFreshLaunch(savedInstanceState: Bundle?): Boolean =
        savedInstanceState == null &&
            (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as DuitApplication).container
        extractNotificationDeepLink(intent)?.let { target ->
            container.setPendingDeepLink(target)
        }
        WidgetDeepLink.extract(intent)?.let { target ->
            container.setPendingWidgetDeepLink(target)
        }
    }

    private fun extractNotificationDeepLink(intent: Intent?): NotificationDeepLink? {
        val raw = intent?.getStringExtra(AndroidNotificationHelper.EXTRA_DEEP_LINK_TARGET) ?: return null
        return NotificationDeepLink.entries.firstOrNull { it.name == raw }
    }
}
