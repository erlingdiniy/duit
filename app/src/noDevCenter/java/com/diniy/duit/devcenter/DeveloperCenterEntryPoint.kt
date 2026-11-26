package com.diniy.duit.devcenter

import androidx.compose.runtime.Composable
import com.diniy.duit.DuitContainer

/**
 * Compile-time no-op stub for non-Personal builds (beta and benchmark).
 * Keeps public Stable and Beta APKs completely free of Developer Center code, routes, and secrets.
 */
object DeveloperCenterEntryPoint {
    const val isAvailable: Boolean = false

    fun privacySummaryOrNull(fcmEnabled: Boolean): String? = null

    fun onAppBackgrounded() = Unit

    @Composable
    fun SettingsDeveloperSection(onOpenDeveloperCenter: () -> Unit) = Unit

    @Composable
    fun DeveloperCenterHost(
        container: DuitContainer,
        isAppLockEnabled: Boolean,
        onBack: () -> Unit
    ) = Unit
}
