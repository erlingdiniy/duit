package com.diniy.duit.diagnostics

import android.content.Context
import com.diniy.duit.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Beta/public + private-dev Crashlytics controller.
 * Analytics remains disabled; crash collection starts OFF by default until explicit user consent.
 */
object CrashReporting {
    const val isSupported: Boolean = true

    @Volatile
    private var collectionEnabled: Boolean = false

    fun isCollectionEnabled(): Boolean = collectionEnabled

    fun initialize(context: Context, enabled: Boolean) {
        applyConsent(context, enabled)
    }

    fun applyConsent(context: Context, enabled: Boolean) {
        collectionEnabled = enabled
        runCatching {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext) ?: return
            }
            val crashlytics = FirebaseCrashlytics.getInstance()
            // Clear unsent reports whenever disabled or before enabling so opted-out sessions are never sent retroactively.
            crashlytics.deleteUnsentReports()
            crashlytics.setCrashlyticsCollectionEnabled(enabled)
            if (enabled) {
                val keys = CustomKeysAndValues.Builder()
                    .putString(
                        "release_channel",
                        CrashMetadataSanitizer.resolveReleaseChannel(
                            versionName = BuildConfig.VERSION_NAME,
                            isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION
                        )
                    )
                    .putString("app_version", BuildConfig.VERSION_NAME)
                    .putInt("version_code", BuildConfig.VERSION_CODE)
                    .putString("edition_type", if (BuildConfig.PRIVATE_DEV_EDITION) "private_dev" else "public")
                    .build()
                crashlytics.setCustomKeys(keys)
            }
        }
    }

    fun recordNonFatal(category: CrashDiagnosticCategory, throwable: Throwable) {
        if (!collectionEnabled) return
        runCatching {
            val sanitized = CrashMetadataSanitizer.sanitizeThrowable(category, throwable)
            FirebaseCrashlytics.getInstance().recordException(sanitized)
        }
    }

    fun logEvent(event: CrashBreadcrumbEvent) {
        if (!collectionEnabled) return
        runCatching {
            FirebaseCrashlytics.getInstance().log(event.tag)
        }
    }

    fun sendUnsentReportsIfEnabled() {
        if (!collectionEnabled) return
        runCatching {
            FirebaseCrashlytics.getInstance().sendUnsentReports()
        }
    }
}
