package com.diniy.duit.devcenter

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ValidatedBroadcastRequest(
    val category: BroadcastCategory,
    val audience: BroadcastAudience,
    val route: BroadcastDestination,
    val title: String,
    val body: String,
    val broadcastId: String,
    val validateOnly: Boolean,
    val confirmationPhrase: String,
    val reason: String
)

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val errors: List<String>) : ValidationResult<Nothing>
}

object DevCenterValidators {
    const val LIVE_COOLDOWN_MS: Long = 30_000L
    private val CONTROL_CHARS = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]""")
    private val WHITESPACE = Regex("""\s+""")
    val CUSTOM_BROADCAST_ID_REGEX = Regex("""^duit-custom-[a-z0-9._-]{4,50}$""")
    private val GITHUB_SLUG_REGEX = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
    private val GA4_PROPERTY_ID_REGEX = Regex("""^\d{6,15}$""")
    private val GOOGLE_OAUTH_CLIENT_ID_REGEX = Regex("""^\d{6,20}-[A-Za-z0-9_-]{8,80}\.apps\.googleusercontent\.com$""")
    val FORBIDDEN_PUBLIC_UPDATE_REPOS = setOf("erlingdiniy/duit-beta", "erlingdiniy/duit")
    private val random = SecureRandom()

    fun cleanInputText(raw: String?, maxLen: Int): String {
        if (raw.isNullOrEmpty()) return ""
        return raw
            .replace(CONTROL_CHARS, "")
            .replace(WHITESPACE, " ")
            .trim()
            .take(maxLen)
    }

    fun isValidRepoSlug(slug: String?): Boolean =
        !slug.isNullOrBlank() && GITHUB_SLUG_REGEX.matches(slug.trim())

    fun isValidGa4PropertyId(propertyId: String?): Boolean =
        !propertyId.isNullOrBlank() && GA4_PROPERTY_ID_REGEX.matches(propertyId.trim())

    fun isValidGoogleOAuthClientId(clientId: String?): Boolean =
        !clientId.isNullOrBlank() && GOOGLE_OAUTH_CLIENT_ID_REGEX.matches(clientId.trim())

    fun isValidPrivateUpdateRepo(slug: String?): Boolean =
        isValidRepoSlug(slug) && slug!!.trim().lowercase(Locale.US) !in FORBIDDEN_PUBLIC_UPDATE_REPOS

    fun generateBroadcastId(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val suffixBytes = ByteArray(2).also { random.nextBytes(it) }
        val hex = suffixBytes.joinToString("") { "%02x".format(it) }
        return "duit-custom-${sdf.format(Date(nowMs))}-$hex"
    }

    fun cooldownRemainingSeconds(lastLiveDispatchMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        if (lastLiveDispatchMs <= 0L) return 0
        val elapsed = nowMs - lastLiveDispatchMs
        if (elapsed >= LIVE_COOLDOWN_MS) return 0
        val remainingMs = LIVE_COOLDOWN_MS - elapsed
        return ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun validateBroadcastDraft(
        category: BroadcastCategory,
        audience: BroadcastAudience,
        route: BroadcastDestination,
        rawTitle: String,
        rawBody: String,
        rawBroadcastId: String,
        validateOnly: Boolean,
        confirmationPhrase: String,
        rawReason: String,
        usedBroadcastIds: Set<String>,
        lastLiveDispatchMs: Long,
        customKillSwitchEnabled: Boolean?,
        nowMs: Long = System.currentTimeMillis()
    ): ValidationResult<ValidatedBroadcastRequest> {
        val errors = mutableListOf<String>()

        val title = cleanInputText(rawTitle, 150)
        if (title.length !in 3..100) {
            errors += "Title must be 3 to 100 characters."
        }

        val body = cleanInputText(rawBody, 300)
        if (body.length !in 8..220) {
            errors += "Message must be 8 to 220 characters."
        }

        val broadcastId = rawBroadcastId.trim().lowercase(Locale.US)
        if (!CUSTOM_BROADCAST_ID_REGEX.matches(broadcastId)) {
            errors += "Broadcast ID must start with duit-custom- (4 to 50 slug chars)."
        } else if (broadcastId in usedBroadcastIds) {
            errors += "Broadcast ID \"$broadcastId\" was already used. Generate a new ID."
        }

        var cleanReason = ""
        if (!validateOnly) {
            if (customKillSwitchEnabled != true) {
                errors += "Custom public dispatch is paused by kill switch (ENABLE_CUSTOM_FCM_DISPATCH)."
            }
            val cooldownSecs = cooldownRemainingSeconds(lastLiveDispatchMs, nowMs)
            if (cooldownSecs > 0) {
                errors += "Cooldown active: wait ${cooldownSecs}s before sending another live broadcast."
            }
            if (confirmationPhrase.trim() != audience.confirmationPhrase) {
                errors += "Type exact confirmation phrase \"${audience.confirmationPhrase}\"."
            }
            cleanReason = cleanInputText(rawReason, 260)
            if (cleanReason.length !in 5..240) {
                errors += "Operator reason must be 5 to 240 characters."
            }
        } else {
            cleanReason = cleanInputText(rawReason, 240).ifBlank { "Mobile dry-run validation" }
        }

        if (errors.isNotEmpty()) {
            return ValidationResult.Invalid(errors)
        }

        return ValidationResult.Valid(
            ValidatedBroadcastRequest(
                category = category,
                audience = audience,
                route = route,
                title = title,
                body = body,
                broadcastId = broadcastId,
                validateOnly = validateOnly,
                confirmationPhrase = if (validateOnly) "" else confirmationPhrase.trim(),
                reason = cleanReason
            )
        )
    }

    fun validateKillSwitchConfirmation(
        target: KillSwitchTarget,
        enable: Boolean,
        confirmationPhrase: String,
        rawReason: String
    ): ValidationResult<String> {
        val errors = mutableListOf<String>()
        val expected = target.confirmationPhrase(enable)
        if (confirmationPhrase.trim() != expected) {
            errors += "Type exact phrase \"$expected\"."
        }
        val cleanReason = cleanInputText(rawReason, 260)
        if (cleanReason.length !in 5..240) {
            errors += "Reason must be 5 to 240 characters."
        }
        return if (errors.isNotEmpty()) ValidationResult.Invalid(errors) else ValidationResult.Valid(cleanReason)
    }
}
