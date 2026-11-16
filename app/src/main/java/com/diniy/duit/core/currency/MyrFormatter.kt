package com.diniy.duit.core.currency

import java.text.NumberFormat
import java.util.Locale

/**
 * Formats and parses Malaysian Ringgit (MYR) strictly using integer minor units (sen).
 * 1 RM = 100 sen. Never converts monetary amounts to Float or Double.
 */
object MyrFormatter {

    private val integerFormatter: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    /**
     * Formats [amountSen] into a human-readable MYR string such as "RM 1,250.00" or "-RM 45.50".
     */
    fun format(
        amountSen: Long,
        showCurrencyCode: Boolean = false,
        includePlusSign: Boolean = false
    ): String {
        val prefix = if (showCurrencyCode) "MYR" else "RM"
        val isNegative = amountSen < 0L
        val absSen = if (amountSen == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountSen)
        val ringgit = absSen / 100L
        val sen = absSen % 100L
        val formattedRinggit = synchronized(integerFormatter) {
            integerFormatter.format(ringgit)
        }
        val formattedSen = sen.toString().padStart(2, '0')
        val sign = when {
            isNegative -> "-"
            includePlusSign && amountSen > 0L -> "+"
            else -> ""
        }
        return "$sign$prefix $formattedRinggit.$formattedSen"
    }

    /**
     * Formats [amountSen] without a currency prefix for input fields (e.g. "1250.00").
     */
    fun formatInputValue(amountSen: Long): String {
        val absSen = if (amountSen == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountSen)
        val ringgit = absSen / 100L
        val sen = absSen % 100L
        return "$ringgit.${sen.toString().padStart(2, '0')}"
    }

    /**
     * Parses a user-entered MYR amount string into integer sen, or returns null if invalid.
     * Rejects negative values and inputs with more than 2 fractional digits.
     */
    fun parseToSen(rawInput: String): Long? {
        val cleaned = rawInput
            .trim()
            .removePrefix("MYR")
            .removePrefix("RM")
            .replace(",", "")
            .replace(" ", "")

        if (cleaned.isEmpty() || cleaned.startsWith("-")) return null

        val parts = cleaned.split(".")
        if (parts.size > 2) return null

        val ringgitPart = parts[0].ifEmpty { "0" }
        val senPart = if (parts.size == 2) parts[1] else ""

        if (!ringgitPart.all { it.isDigit() } || !senPart.all { it.isDigit() }) return null
        if (senPart.length > 2) return null

        val paddedSen = senPart.padEnd(2, '0')
        return try {
            val ringgit = ringgitPart.toLong()
            val sen = paddedSen.toLong()
            Math.addExact(Math.multiplyExact(ringgit, 100L), sen)
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Parses a percentage string (up to 2 decimal places, 0%..100%) into integer basis points.
     * 1.00% = 100 bps; 100.00% = 10_000 bps.
     */
    fun parsePercentToBps(rawPercent: String): Int? {
        val cleaned = rawPercent.trim().removeSuffix("%").replace(" ", "")
        if (cleaned.isEmpty() || cleaned.startsWith("-")) return null

        val parts = cleaned.split(".")
        if (parts.size > 2) return null

        val wholePart = parts[0].ifEmpty { "0" }
        val fracPart = if (parts.size == 2) parts[1] else ""

        if (!wholePart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return null
        if (fracPart.length > 2) return null

        val whole = wholePart.toIntOrNull() ?: return null
        val frac = fracPart.padEnd(2, '0').toIntOrNull() ?: return null
        val bps = whole * 100 + frac
        return if (bps in 0..10_000) bps else null
    }

    /**
     * Formats integer basis points into a percentage string such as "3.50%".
     */
    fun formatBpsAsPercent(bps: Int): String {
        val clamped = bps.coerceAtLeast(0)
        val whole = clamped / 100
        val frac = clamped % 100
        return "$whole.${frac.toString().padStart(2, '0')}%"
    }
}
