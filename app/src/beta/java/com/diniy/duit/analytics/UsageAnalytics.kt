package com.diniy.duit.analytics

import android.content.Context
import android.os.Bundle
import com.diniy.duit.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import java.util.concurrent.ConcurrentHashMap

/**
 * Beta/public + private-dev Firebase Analytics controller.
 * Collection starts OFF by default until explicit user consent in Security & Privacy.
 * Ad storage, ad personalization, ad user data, ADID, and Firebase user IDs are permanently disabled.
 */
object UsageAnalytics {
    const val isSupported: Boolean = true
    private const val PREFS_NAME = "duit_analytics_state"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    private const val FEATURE_DEBOUNCE_MS = 2_000L

    @Volatile
    private var collectionEnabled: Boolean = false

    @Volatile
    private var appContext: Context? = null

    private val lastFeatureEventMs = ConcurrentHashMap<AnalyticsFeature, Long>()

    fun isCollectionEnabled(): Boolean = collectionEnabled

    fun initialize(context: Context, enabled: Boolean) {
        applyConsent(context, enabled)
    }

    fun applyConsent(context: Context, enabled: Boolean) {
        val ctx = context.applicationContext
        appContext = ctx
        collectionEnabled = enabled
        runCatching {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx) ?: return
            }
            val analytics = FirebaseAnalytics.getInstance(ctx)
            // Ad IDs, ad storage, and personalization remain permanently denied regardless of analytics opt-in.
            analytics.setConsent(
                mapOf(
                    ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                    ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                    ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
                    ConsentType.ANALYTICS_STORAGE to if (enabled) ConsentStatus.GRANTED else ConsentStatus.DENIED
                )
            )
            if (!enabled) {
                lastFeatureEventMs.clear()
                analytics.setDefaultEventParameters(null)
                analytics.setAnalyticsCollectionEnabled(false)
                analytics.resetAnalyticsData()
                return
            }
            val defaultParams = UsageAnalyticsPolicy.buildDefaultEventParams(
                versionName = BuildConfig.VERSION_NAME,
                isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
                isQaTest = BuildConfig.ANALYTICS_QA_TEST
            )
            analytics.setDefaultEventParameters(defaultParams.toBundle())
            analytics.setAnalyticsCollectionEnabled(true)
            analytics.setUserProperty(
                "release_channel",
                UsageAnalyticsPolicy.resolveReleaseChannel(BuildConfig.VERSION_NAME, BuildConfig.PRIVATE_DEV_EDITION)
            )
            analytics.setUserProperty(
                "edition_type",
                UsageAnalyticsPolicy.resolveEditionType(BuildConfig.PRIVATE_DEV_EDITION)
            )
            checkAndLogAppUpdatedIfNeeded(ctx, analytics)
        }
    }

    fun logFeatureUsed(feature: AnalyticsFeature, nowMs: Long = System.currentTimeMillis()) {
        if (!collectionEnabled) return
        val prev = lastFeatureEventMs[feature] ?: 0L
        if (nowMs - prev < FEATURE_DEBOUNCE_MS) return
        lastFeatureEventMs[feature] = nowMs
        val params = UsageAnalyticsPolicy.buildFeatureUsedParams(
            feature = feature,
            versionName = BuildConfig.VERSION_NAME,
            isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
            isQaTest = BuildConfig.ANALYTICS_QA_TEST
        )
        dispatchEvent(AnalyticsEventName.FEATURE_USED, params)
    }

    fun logOnboardingCompleted(skipped: Boolean) {
        if (!collectionEnabled) return
        val params = UsageAnalyticsPolicy.buildOnboardingCompletedParams(
            skipped = skipped,
            versionName = BuildConfig.VERSION_NAME,
            isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
            isQaTest = BuildConfig.ANALYTICS_QA_TEST
        )
        dispatchEvent(AnalyticsEventName.ONBOARDING_COMPLETED, params)
    }

    fun logUpdatePromptAction(result: String) {
        if (!collectionEnabled) return
        val params = UsageAnalyticsPolicy.buildUpdatePromptActionParams(
            result = result,
            versionName = BuildConfig.VERSION_NAME,
            isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
            isQaTest = BuildConfig.ANALYTICS_QA_TEST
        )
        if (params.containsKey("result")) {
            dispatchEvent(AnalyticsEventName.UPDATE_PROMPT_ACTION, params)
        }
    }

    fun logThemeSelected(themeKey: String) {
        if (!collectionEnabled) return
        val params = UsageAnalyticsPolicy.buildThemeSelectedParams(
            themeKey = themeKey,
            versionName = BuildConfig.VERSION_NAME,
            isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
            isQaTest = BuildConfig.ANALYTICS_QA_TEST
        )
        val validTheme = params["theme"] ?: return
        appContext?.let { ctx ->
            runCatching {
                FirebaseAnalytics.getInstance(ctx).setUserProperty("visual_theme", validTheme)
            }
        }
        dispatchEvent(AnalyticsEventName.THEME_SELECTED, params)
    }

    /** Only invoked behind BuildConfig.ANALYTICS_QA_TEST during isolated emulator QA verification. */
    fun logQaVerificationBatch(context: Context) {
        appContext = context.applicationContext
        if (!BuildConfig.ANALYTICS_QA_TEST || !collectionEnabled) return
        lastFeatureEventMs.clear()
        logFeatureUsed(AnalyticsFeature.HOME)
        logFeatureUsed(AnalyticsFeature.WALLETS)
        logOnboardingCompleted(skipped = false)
        logUpdatePromptAction("available")
        dispatchEvent(
            AnalyticsEventName.APP_UPDATED,
            UsageAnalyticsPolicy.buildAppUpdatedParams(
                versionName = BuildConfig.VERSION_NAME,
                isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
                isQaTest = true
            )
        )
    }

    private fun checkAndLogAppUpdatedIfNeeded(ctx: Context, analytics: FirebaseAnalytics) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        val prevCode = prefs.getLong(KEY_LAST_SEEN_VERSION_CODE, 0L)
        if (prevCode in 1L until currentCode) {
            val params = UsageAnalyticsPolicy.buildAppUpdatedParams(
                versionName = BuildConfig.VERSION_NAME,
                isPrivateDev = BuildConfig.PRIVATE_DEV_EDITION,
                isQaTest = BuildConfig.ANALYTICS_QA_TEST
            )
            analytics.logEvent(AnalyticsEventName.APP_UPDATED.wireName, params.toBundle())
        }
        if (prevCode != currentCode) {
            prefs.edit().putLong(KEY_LAST_SEEN_VERSION_CODE, currentCode).apply()
        }
    }

    private fun dispatchEvent(event: AnalyticsEventName, params: Map<String, String>) {
        val ctx = appContext ?: return
        if (!collectionEnabled) return
        runCatching {
            FirebaseAnalytics.getInstance(ctx).logEvent(event.wireName, params.toBundle())
        }
    }

    private fun Map<String, String>.toBundle(): Bundle = Bundle().apply {
        for ((k, v) in this@toBundle) {
            putString(k, v)
        }
    }
}
