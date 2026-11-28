package com.diniy.duit.devcenter

/**
 * Scrubs tokens and bearer credentials from any error message, diagnostic summary, or audit string
 * so credentials are never surfaced in UI banners, history logs, or crash/system traces.
 */
object DevCenterRedactor {
    private val KNOWN_TOKEN_PATTERN = Regex(
        """\b(?:[g]hp|[g]ho|[g]hu|[g]hs|[g]hr)_[A-Za-z0-9_]{8,}\b|\b[g]ithub_pat_[A-Za-z0-9_]{12,}\b|\b[y]a29\.[A-Za-z0-9._-]{12,}\b|\b1//[A-Za-z0-9._-]{12,}|\b[d]rc_[A-Za-z0-9_-]{12,}\b|\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b|[A-Za-z0-9_-]{18,}:APA91b[A-Za-z0-9_-]{16,}"""
    )
    private val BEARER_PATTERN = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]{6,}""")
    private val PRESIGNED_URL_PATTERN = Regex(
        """https://(?:objects|release-assets|github-releases)\.githubusercontent\.com/\S+""",
        RegexOption.IGNORE_CASE
    )
    private val SENSITIVE_QUERY_PARAM_PATTERN = Regex(
        """([?&])(?:token|sig|signature|x-amz-[^=&]+|auth|key)=[^&\s]+""",
        RegexOption.IGNORE_CASE
    )

    fun redact(text: String?, extraSecrets: Collection<String> = emptyList()): String {
        if (text.isNullOrBlank()) return ""
        var scrubbed: String = text
        for (secret in extraSecrets) {
            val trimmed = secret.trim()
            if (trimmed.length >= 6) {
                scrubbed = scrubbed.replace(trimmed, "[REDACTED]")
            }
        }
        scrubbed = PRESIGNED_URL_PATTERN.replace(scrubbed, "[REDACTED_URL]")
        scrubbed = SENSITIVE_QUERY_PARAM_PATTERN.replace(scrubbed) { "${it.groupValues[1]}[REDACTED_PARAM]" }
        scrubbed = KNOWN_TOKEN_PATTERN.replace(scrubbed, "[REDACTED]")
        scrubbed = BEARER_PATTERN.replace(scrubbed, "Bearer [REDACTED]")
        return scrubbed
    }

    fun maskToken(token: String?): String {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isEmpty()) return "Not configured"
        if (trimmed.length <= 6) return "Configured"
        return "••••${trimmed.takeLast(4)}"
    }
}
