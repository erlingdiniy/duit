package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionFormulaConfig
import java.math.BigInteger

/**
 * Result of evaluating a user-configured company commission formula.
 */
sealed interface CommissionCalculationResult {
    /** Returned when no formula has been configured or the user has not enabled it. */
    data class NotConfigured(val reason: String) : CommissionCalculationResult

    /** Returned when the formula is configured, enabled, and evaluated in integer sen. */
    data class Calculated(
        val formulaName: String,
        val qualifyingSalesSen: Long,
        val rateCommissionSen: Long,
        val flatBonusAppliedSen: Long,
        val totalEstimatedCommissionSen: Long,
        val thresholdMet: Boolean
    ) : CommissionCalculationResult
}

/**
 * Configurable Company Commission Calculator module.
 *
 * Intentionally contains NO invented or default company commission formula.
 * Calculations remain inactive until the user explicitly configures and activates a rule.
 * All arithmetic uses integer minor units (sen) and basis points (1% = 100 bps).
 */
object CommissionCalculator {

    private val BPS_DIVISOR = BigInteger.valueOf(10_000L)
    private val HALF_ROUND = BigInteger.valueOf(5_000L)

    fun calculate(
        salesAmountSen: Long,
        config: CommissionFormulaConfig
    ): CommissionCalculationResult {
        if (!config.isConfigured) {
            return CommissionCalculationResult.NotConfigured(
                reason = "No company commission formula has been configured yet."
            )
        }
        if (!config.isEnabled) {
            return CommissionCalculationResult.NotConfigured(
                reason = "The configured formula is currently paused. Activate it to run estimates."
            )
        }
        if (salesAmountSen < 0L) {
            return CommissionCalculationResult.NotConfigured(
                reason = "Sales amount cannot be negative."
            )
        }

        val thresholdMet = salesAmountSen >= config.minimumSalesThresholdSen
        if (!thresholdMet) {
            return CommissionCalculationResult.Calculated(
                formulaName = config.formulaName,
                qualifyingSalesSen = 0L,
                rateCommissionSen = 0L,
                flatBonusAppliedSen = 0L,
                totalEstimatedCommissionSen = 0L,
                thresholdMet = false
            )
        }

        val rateCommissionSen = BigInteger.valueOf(salesAmountSen)
            .multiply(BigInteger.valueOf(config.rateBps.toLong()))
            .add(HALF_ROUND)
            .divide(BPS_DIVISOR)
            .longValueExact()

        val bonusSen = config.flatBonusSen.coerceAtLeast(0L)
        val totalEstimatedSen = Math.addExact(rateCommissionSen, bonusSen)

        return CommissionCalculationResult.Calculated(
            formulaName = config.formulaName,
            qualifyingSalesSen = salesAmountSen,
            rateCommissionSen = rateCommissionSen,
            flatBonusAppliedSen = bonusSen,
            totalEstimatedCommissionSen = totalEstimatedSen,
            thresholdMet = true
        )
    }
}
