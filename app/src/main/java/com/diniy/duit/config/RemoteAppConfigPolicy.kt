package com.diniy.duit.config

/**
 * Hardened domain policy for safe, non-financial Firebase Remote Config parameters.
 * Remote Config may only govern lightweight product notices/flags and must never touch
 * wallets, transactions, balances, receipts, backups, restore, export, or PIN locks.
 */
data class RemoteAppConfigSnapshot(
    val maintenanceBannerEnabled: Boolean = false,
    val maintenanceBannerTitle: String = "",
    val maintenanceBannerMessage: String = "",
    val experimentalInsightsTipsEnabled: Boolean = false,
    val gradualRolloutNoticeEnabled: Boolean = false,
    val gradualRolloutPercent: Int = 0,
    val minRecommendedVersionCode: Long = 0L,
    val lastFetchEpochMs: Long = 0L,
    val source: String = "defaults"
)

object RemoteAppConfigPolicy {
    const val KEY_MAINTENANCE_BANNER_ENABLED = "maintenance_banner_enabled"
    const val KEY_MAINTENANCE_BANNER_TITLE = "maintenance_banner_title"
    const val KEY_MAINTENANCE_BANNER_MESSAGE = "maintenance_banner_message"
    const val KEY_EXPERIMENTAL_INSIGHTS_TIPS = "experimental_insights_tips_enabled"
    const val KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED = "gradual_rollout_notice_enabled"
    const val KEY_GRADUAL_ROLLOUT_PERCENT = "gradual_rollout_percent"
    const val KEY_MIN_RECOMMENDED_VERSION_CODE = "min_recommended_version_code"

    val ALLOWED_KEYS: Set<String> = setOf(
        KEY_MAINTENANCE_BANNER_ENABLED,
        KEY_MAINTENANCE_BANNER_TITLE,
        KEY_MAINTENANCE_BANNER_MESSAGE,
        KEY_EXPERIMENTAL_INSIGHTS_TIPS,
        KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED,
        KEY_GRADUAL_ROLLOUT_PERCENT,
        KEY_MIN_RECOMMENDED_VERSION_CODE
    )

    val FORBIDDEN_DOMAIN_KEYWORDS: Set<String> = setOf(
        "wallet",
        "transaction",
        "balance",
        "receipt",
        "backup",
        "restore",
        "export",
        "database",
        "room",
        "pin",
        "biometric",
        "debt",
        "saving",
        "salary",
        "commission",
        "ledger"
    )

    private val CONTROL_CHARS = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]""")

    fun isSafeParameterKey(key: String): Boolean {
        val normalized = key.trim().lowercase()
        if (normalized.isEmpty() || normalized !in ALLOWED_KEYS) return false
        return FORBIDDEN_DOMAIN_KEYWORDS.none { normalized.contains(it) }
    }

    fun defaultStringValues(): Map<String, Any> = mapOf(
        KEY_MAINTENANCE_BANNER_ENABLED to false,
        KEY_MAINTENANCE_BANNER_TITLE to "",
        KEY_MAINTENANCE_BANNER_MESSAGE to "",
        KEY_EXPERIMENTAL_INSIGHTS_TIPS to false,
        KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED to false,
        KEY_GRADUAL_ROLLOUT_PERCENT to 0L,
        KEY_MIN_RECOMMENDED_VERSION_CODE to 0L
    )

    fun sanitizeText(raw: String?, maxLength: Int): String =
        raw.orEmpty()
            .replace(CONTROL_CHARS, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxLength)

    fun sanitizeSnapshot(
        maintenanceBannerEnabled: Boolean,
        maintenanceBannerTitle: String,
        maintenanceBannerMessage: String,
        experimentalInsightsTipsEnabled: Boolean,
        gradualRolloutNoticeEnabled: Boolean,
        gradualRolloutPercent: Int,
        minRecommendedVersionCode: Long,
        lastFetchEpochMs: Long,
        source: String
    ): RemoteAppConfigSnapshot = RemoteAppConfigSnapshot(
        maintenanceBannerEnabled = maintenanceBannerEnabled,
        maintenanceBannerTitle = sanitizeText(maintenanceBannerTitle, 80),
        maintenanceBannerMessage = sanitizeText(maintenanceBannerMessage, 240),
        experimentalInsightsTipsEnabled = experimentalInsightsTipsEnabled,
        gradualRolloutNoticeEnabled = gradualRolloutNoticeEnabled,
        gradualRolloutPercent = gradualRolloutPercent.coerceIn(0, 100),
        minRecommendedVersionCode = minRecommendedVersionCode.coerceAtLeast(0L),
        lastFetchEpochMs = lastFetchEpochMs.coerceAtLeast(0L),
        source = sanitizeText(source, 32).ifBlank { "defaults" }
    )
}
