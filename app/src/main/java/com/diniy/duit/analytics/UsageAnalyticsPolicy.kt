package com.diniy.duit.analytics

import com.diniy.duit.diagnostics.CrashMetadataSanitizer

/**
 * Strictly allowlisted product feature identifiers for privacy-first usage analytics.
 * Never add dynamic financial categories, wallet names, or free-form user strings here.
 */
enum class AnalyticsFeature(val key: String) {
    HOME("home"),
    WALLETS("wallets"),
    TRANSACTIONS("transactions"),
    SAFE_TO_SPEND("safe_to_spend"),
    PLANNING("planning"),
    SAVINGS_GOALS("savings_goals"),
    BUDGETS("budgets"),
    BILLS_AND_DEBT("bills_and_debt"),
    COMMISSION_TRACKING("commission_tracking"),
    MONEY_CALENDAR("money_calendar"),
    RECEIPT_VAULT("receipt_vault"),
    MY_TAX_POCKET("my_tax_pocket"),
    MONEY_PERSONALITY("money_personality"),
    SAVINGS_CHALLENGES("savings_challenges"),
    LITTLE_WINS_CLUB("little_wins_club"),
    INSIGHTS("insights"),
    BACKUP_AND_RESTORE("backup_and_restore"),
    WIDGETS("widgets"),
    MONEY_REMINDERS("money_reminders");

    companion object {
        val ALL_KEYS: Set<String> = entries.map { it.key }.toSet()
    }
}

/**
 * Strictly allowlisted analytics event names.
 */
enum class AnalyticsEventName(val wireName: String) {
    FEATURE_USED("feature_used"),
    ONBOARDING_COMPLETED("onboarding_completed"),
    APP_UPDATED("app_updated"),
    UPDATE_PROMPT_ACTION("update_prompt_action")
}

/**
 * Enforces zero-PII, strictly allowlisted parameter construction and validation
 * before any event leaves [UsageAnalytics].
 */
object UsageAnalyticsPolicy {
    val ALLOWED_PARAM_KEYS: Set<String> = setOf(
        "feature",
        "release_channel",
        "app_version",
        "traffic_type",
        "result"
    )
    val ALLOWED_USER_PROPERTIES: Set<String> = setOf(
        "release_channel",
        "edition_type"
    )
    val ALLOWED_CHANNELS: Set<String> = setOf("stable", "beta", "private_dev")
    val ALLOWED_RESULTS: Set<String> = setOf("completed", "skipped", "started", "installed", "dismissed", "available")

    private val APP_VERSION_REGEX = Regex("""^\d+\.\d+\.\d+(?:-(?:beta|dev)(?:\.\d+)?)?$""")
    private val SENSITIVE_VALUE_REGEX = Regex(
        """(?:RM|MYR|\$|\b\d{4,}\b|@|://|/|github_pat_|gh[pou]_|ya29\.|AIza)""",
        RegexOption.IGNORE_CASE
    )

    fun resolveReleaseChannel(versionName: String, isPrivateDev: Boolean): String =
        CrashMetadataSanitizer.resolveReleaseChannel(versionName = versionName, isPrivateDev = isPrivateDev)

    fun resolveTrafficType(isPrivateDev: Boolean, isQaTest: Boolean): String? =
        if (isPrivateDev || isQaTest) "internal" else null

    fun resolveEditionType(isPrivateDev: Boolean): String =
        if (isPrivateDev) "private_dev" else "public"

    /**
     * Filters and validates event parameters against strict allowlists and length/sensitivity rules.
     */
    fun sanitizeParams(raw: Map<String, String>): Map<String, String> = buildMap {
        for ((key, value) in raw) {
            if (key !in ALLOWED_PARAM_KEYS) continue
            val clean = value.trim()
            if (clean.isEmpty() || clean.length > 40) continue
            if (SENSITIVE_VALUE_REGEX.containsMatchIn(clean)) continue
            val valid = when (key) {
                "feature" -> clean in AnalyticsFeature.ALL_KEYS
                "release_channel" -> clean in ALLOWED_CHANNELS
                "result" -> clean in ALLOWED_RESULTS
                "traffic_type" -> clean == "internal"
                "app_version" -> APP_VERSION_REGEX.matches(clean)
                else -> false
            }
            if (valid) put(key, clean)
        }
    }

    fun buildFeatureUsedParams(
        feature: AnalyticsFeature,
        versionName: String,
        isPrivateDev: Boolean,
        isQaTest: Boolean
    ): Map<String, String> = sanitizeParams(
        buildMap {
            put("feature", feature.key)
            put("release_channel", resolveReleaseChannel(versionName, isPrivateDev))
            resolveTrafficType(isPrivateDev, isQaTest)?.let { put("traffic_type", it) }
        }
    )

    fun buildOnboardingCompletedParams(
        skipped: Boolean,
        versionName: String,
        isPrivateDev: Boolean,
        isQaTest: Boolean
    ): Map<String, String> = sanitizeParams(
        buildMap {
            put("result", if (skipped) "skipped" else "completed")
            put("release_channel", resolveReleaseChannel(versionName, isPrivateDev))
            resolveTrafficType(isPrivateDev, isQaTest)?.let { put("traffic_type", it) }
        }
    )

    fun buildAppUpdatedParams(
        versionName: String,
        isPrivateDev: Boolean,
        isQaTest: Boolean
    ): Map<String, String> = sanitizeParams(
        buildMap {
            put("release_channel", resolveReleaseChannel(versionName, isPrivateDev))
            put("app_version", versionName)
            resolveTrafficType(isPrivateDev, isQaTest)?.let { put("traffic_type", it) }
        }
    )

    fun buildUpdatePromptActionParams(
        result: String,
        versionName: String,
        isPrivateDev: Boolean,
        isQaTest: Boolean
    ): Map<String, String> = sanitizeParams(
        buildMap {
            put("result", result)
            put("release_channel", resolveReleaseChannel(versionName, isPrivateDev))
            resolveTrafficType(isPrivateDev, isQaTest)?.let { put("traffic_type", it) }
        }
    )

    fun buildDefaultEventParams(
        versionName: String,
        isPrivateDev: Boolean,
        isQaTest: Boolean
    ): Map<String, String> = sanitizeParams(
        buildMap {
            put("release_channel", resolveReleaseChannel(versionName, isPrivateDev))
            resolveTrafficType(isPrivateDev, isQaTest)?.let { put("traffic_type", it) }
        }
    )
}
