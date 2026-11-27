package com.diniy.duit.diagnostics

import android.content.Context

/** Offline builds (debug/fluid/release/benchmark) exclude Firebase Crashlytics completely. */
object CrashReporting {
    const val isSupported: Boolean = false

    fun isCollectionEnabled(): Boolean = false

    fun initialize(context: Context, enabled: Boolean) = Unit

    fun applyConsent(context: Context, enabled: Boolean) = Unit

    fun recordNonFatal(category: CrashDiagnosticCategory, throwable: Throwable) = Unit

    fun logEvent(event: CrashBreadcrumbEvent) = Unit

    fun sendUnsentReportsIfEnabled() = Unit
}
