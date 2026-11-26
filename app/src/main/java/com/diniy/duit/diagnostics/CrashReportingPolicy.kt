package com.diniy.duit.diagnostics

/** Allowed diagnostic subsystem labels for non-fatal error reports (never free-form financial labels). */
enum class CrashDiagnosticCategory(val code: String) {
    BACKUP_ENGINE("BACKUP_ENGINE"),
    RESTORE_ENGINE("RESTORE_ENGINE"),
    RECEIPT_STORAGE("RECEIPT_STORAGE"),
    WIDGET_REFRESH("WIDGET_REFRESH"),
    REMINDER_SYNC("REMINDER_SYNC"),
    UPDATE_CHECK("UPDATE_CHECK"),
    BROADCAST_PARSE("BROADCAST_PARSE"),
    PREFERENCES_IO("PREFERENCES_IO"),
    QA_VERIFICATION("QA_VERIFICATION")
}

/** Fixed breadcrumb event tags for Crashlytics diagnostics (zero dynamic user/money values). */
enum class CrashBreadcrumbEvent(val tag: String) {
    APP_START("app_start"),
    BACKUP_STARTED("backup_started"),
    RESTORE_STARTED("restore_started"),
    UPDATE_CHECK_STARTED("update_check_started"),
    WIDGET_SYNC_STARTED("widget_sync_started")
}

/**
 * Redacted wrapper exception sent for selected non-fatal errors so raw exception causes,
 * file paths, balances, receipts, tokens, PINs, or emails cannot leak into Crashlytics.
 */
class SanitizedDiagnosticException(
    val category: CrashDiagnosticCategory,
    val originalType: String,
    safeMessage: String,
    originalStackTrace: Array<StackTraceElement>
) : Exception("${category.code}:$originalType:$safeMessage") {
    init {
        stackTrace = originalStackTrace
    }
}

/** Strips potentially sensitive values before any diagnostic metadata or non-fatal report is recorded. */
object CrashMetadataSanitizer {

    val ALLOWED_CUSTOM_KEYS: Set<String> = setOf(
        "release_channel",
        "app_version",
        "version_code",
        "edition_type"
    )

    private val CURRENCY_PATTERN = Regex("""\b(?:RM|MYR)\s*-?\d[\d,]*(?:\.\d+)?""", RegexOption.IGNORE_CASE)
    private val TOKEN_PATTERN = Regex("""\b(?:github_pat_[A-Za-z0-9_]+|gh[pou]_[A-Za-z0-9_]+|ya29\.[0-9A-Za-z\-_]+|AIza[0-9A-Za-z\-_]{20,})\b""")
    private val BEARER_PATTERN = Regex("""Bearer\s+[A-Za-z0-9._\-~+/]+=*""", RegexOption.IGNORE_CASE)
    private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val PRIVATE_PATH_PATTERN = Regex("""(?:/data/user/\S+|/data/data/\S+|/storage/\S+|/sdcard/\S+|(?:filesDir)?/receipts/\S+)""")
    private val LONG_DIGITS_PATTERN = Regex("""\b\d{4,}\b""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

    fun resolveReleaseChannel(versionName: String, isPrivateDev: Boolean): String = when {
        isPrivateDev -> "private_dev"
        versionName.contains("-beta.") -> "beta"
        else -> "stable"
    }

    fun redactMessage(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "unexpected_error"
        return trimmed
            .replace(TOKEN_PATTERN, "[REDACTED_TOKEN]")
            .replace(BEARER_PATTERN, "Bearer [REDACTED_TOKEN]")
            .replace(URL_PATTERN, "[REDACTED_URL]")
            .replace(EMAIL_PATTERN, "[REDACTED_EMAIL]")
            .replace(PRIVATE_PATH_PATTERN, "[REDACTED_PATH]")
            .replace(CURRENCY_PATTERN, "[REDACTED_AMOUNT]")
            .replace(LONG_DIGITS_PATTERN, "[REDACTED_NUM]")
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
            .ifEmpty { "unexpected_error" }
            .take(100)
    }

    fun sanitizeThrowable(
        category: CrashDiagnosticCategory,
        throwable: Throwable
    ): SanitizedDiagnosticException {
        val originalType = throwable::class.java.simpleName.ifBlank { "Throwable" }
        val safeMsg = redactMessage(throwable.message)
        return SanitizedDiagnosticException(
            category = category,
            originalType = originalType,
            safeMessage = safeMsg,
            originalStackTrace = throwable.stackTrace
        )
    }
}
