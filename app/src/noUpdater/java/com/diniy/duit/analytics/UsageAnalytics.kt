package com.diniy.duit.analytics

import android.content.Context

/** Offline builds (debug/fluid/release/benchmark) exclude Firebase Analytics completely. */
object UsageAnalytics {
    const val isSupported: Boolean = false

    fun isCollectionEnabled(): Boolean = false

    fun initialize(context: Context, enabled: Boolean) = Unit

    fun applyConsent(context: Context, enabled: Boolean) = Unit

    fun logFeatureUsed(feature: AnalyticsFeature, nowMs: Long = 0L) = Unit

    fun logOnboardingCompleted(skipped: Boolean) = Unit

    fun logUpdatePromptAction(result: String) = Unit

    fun logThemeSelected(themeKey: String) = Unit

    fun logQaVerificationBatch(context: Context) = Unit
}
