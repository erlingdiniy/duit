package com.diniy.duit.core.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.diniy.duit.core.currency.MyrFormatter

data class PrivacySettings(
    val hideSensitiveBalances: Boolean = false,
    val privacyModeEnabled: Boolean = false
) {
    val isAnyMaskActive: Boolean get() = hideSensitiveBalances || privacyModeEnabled
}

val LocalPrivacySettings = compositionLocalOf { PrivacySettings() }

object PrivacyMask {
    const val MASKED_MYR = "RM •••••"
    const val ACCESSIBLE_HIDDEN_AMOUNT = "Amount hidden for privacy"

    fun formatBalance(
        amountSen: Long,
        settings: PrivacySettings,
        includePlusSign: Boolean = false
    ): String {
        return if (settings.hideSensitiveBalances || settings.privacyModeEnabled) {
            MASKED_MYR
        } else {
            MyrFormatter.format(amountSen, includePlusSign = includePlusSign)
        }
    }

    fun formatAmount(
        amountSen: Long,
        settings: PrivacySettings,
        includePlusSign: Boolean = false
    ): String {
        return if (settings.privacyModeEnabled) {
            MASKED_MYR
        } else {
            MyrFormatter.format(amountSen, includePlusSign = includePlusSign)
        }
    }

    fun maskLiteralIfHidden(
        rawValue: String,
        hide: Boolean
    ): String = if (hide) MASKED_MYR else rawValue

    /**
     * Produces TalkBack-friendly description that never leaks masked monetary values.
     */
    fun accessibleBalanceDescription(
        label: String,
        amountSen: Long,
        settings: PrivacySettings,
        includePlusSign: Boolean = false
    ): String {
        val cleanLabel = label.trim()
        return if (settings.isAnyMaskActive) {
            if (cleanLabel.isEmpty()) ACCESSIBLE_HIDDEN_AMOUNT else "$cleanLabel, amount hidden for privacy"
        } else {
            val formatted = MyrFormatter.format(amountSen, includePlusSign = includePlusSign)
            if (cleanLabel.isEmpty()) formatted else "$cleanLabel, $formatted"
        }
    }

    fun accessibleAmountDescription(
        label: String,
        amountSen: Long,
        settings: PrivacySettings,
        includePlusSign: Boolean = false
    ): String {
        val cleanLabel = label.trim()
        return if (settings.privacyModeEnabled) {
            if (cleanLabel.isEmpty()) ACCESSIBLE_HIDDEN_AMOUNT else "$cleanLabel, amount hidden for privacy"
        } else {
            val formatted = MyrFormatter.format(amountSen, includePlusSign = includePlusSign)
            if (cleanLabel.isEmpty()) formatted else "$cleanLabel, $formatted"
        }
    }
}

@Composable
fun maskedBalance(
    amountSen: Long,
    includePlusSign: Boolean = false
): String = PrivacyMask.formatBalance(
    amountSen = amountSen,
    settings = LocalPrivacySettings.current,
    includePlusSign = includePlusSign
)

@Composable
fun maskedAmount(
    amountSen: Long,
    includePlusSign: Boolean = false
): String = PrivacyMask.formatAmount(
    amountSen = amountSen,
    settings = LocalPrivacySettings.current,
    includePlusSign = includePlusSign
)

/**
 * Replaces raw bullet-character semantics with a clean TalkBack announcement when masked.
 */
fun Modifier.privacySafeAmountSemantics(
    label: String,
    isMasked: Boolean,
    visibleFormattedAmount: String
): Modifier = clearAndSetSemantics {
    val cleanLabel = label.trim()
    contentDescription = if (isMasked) {
        if (cleanLabel.isEmpty()) PrivacyMask.ACCESSIBLE_HIDDEN_AMOUNT else "$cleanLabel, amount hidden for privacy"
    } else {
        if (cleanLabel.isEmpty()) visibleFormattedAmount else "$cleanLabel, $visibleFormattedAmount"
    }
}
