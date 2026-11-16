package com.diniy.duit

import android.content.Intent
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

        setContent {
            val appSettings by container.userPreferencesRepository
                .observeAppSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettingsSnapshot())
            val themeMode = appSettings.themeMode
            val appLockState by lockManager.uiState.collectAsStateWithLifecycle()

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

            LaunchedEffect(appLockState.isLocked, appLockState.shouldPromptBiometricAutomatically) {
                if (appLockState.isLocked &&
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
                        reduceMotion = appSettings.reduceMotion,
                        animationIntensity = appSettings.animationIntensity,
                        hapticFeedbackEnabled = appSettings.hapticFeedbackEnabled
                    )
                ) {
                    DuitAppShell(
                        container = container,
                        appLockState = appLockState,
                        onTriggerBiometric = triggerBiometricPrompt
                    )
                }
            }
        }
    }

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
