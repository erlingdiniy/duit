package com.diniy.duit.devcenter

/**
 * Scrubs tokens and bearer credentials from any error message, diagnostic summary, or audit string
 * so credentials are never surfaced in UI banners, history logs, or crash/system traces.
 */
object DevCenterRedactor {
    private val KNOWN_TOKEN_PATTERN = Regex(
        """\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{8,}\b|\bgithub_pat_[A-Za-z0-9_]{12,}\b|\bya29\.[A-Za-z0-9._-]{12,}\b"""
    )
    private val BEARER_PATTERN = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]{6,}""")

    fun redact(text: String?, extraSecrets: Collection<String> = emptyList()): String {
        if (text.isNullOrBlank()) return ""
        var scrubbed: String = text
        for (secret in extraSecrets) {
            val trimmed = secret.trim()
            if (trimmed.length >= 6) {
                scrubbed = scrubbed.replace(trimmed, "[REDACTED]")
            }
        }
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
