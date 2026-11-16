package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionFormulaConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommissionCalculatorTest {

    @Test
    fun `default config is unconfigured and never activates an invented formula`() {
        val defaultConfig = CommissionFormulaConfig()
        assertFalse(defaultConfig.isConfigured)
        assertFalse(defaultConfig.isEnabled)

        val result = CommissionCalculator.calculate(
            salesAmountSen = 500_000L,
            config = defaultConfig
        )
        assertTrue(result is CommissionCalculationResult.NotConfigured)
    }

    @Test
    fun `configured but paused formula stays inactive`() {
        val pausedConfig = CommissionFormulaConfig(
            formulaName = "Custom Rule",
            isConfigured = true,
            isEnabled = false,
            minimumSalesThresholdSen = 100_000L,
            rateBps = 500,
            flatBonusSen = 10_000L
        )
        val result = CommissionCalculator.calculate(300_000L, pausedConfig)
        assertTrue(result is CommissionCalculationResult.NotConfigured)
    }

    @Test
    fun `user-configured active formula computes exact integer sen commission`() {
        val activeConfig = CommissionFormulaConfig(
            formulaName = "Custom Q4",
            isConfigured = true,
            isEnabled = true,
            minimumSalesThresholdSen = 100_000L, // RM 1,000.00
            rateBps = 350, // 3.50%
            flatBonusSen = 15_000L // RM 150.00
        )

        val belowThreshold = CommissionCalculator.calculate(80_000L, activeConfig)
        assertTrue(belowThreshold is CommissionCalculationResult.Calculated)
        belowThreshold as CommissionCalculationResult.Calculated
        assertFalse(belowThreshold.thresholdMet)
        assertEquals(0L, belowThreshold.totalEstimatedCommissionSen)

        val aboveThreshold = CommissionCalculator.calculate(200_000L, activeConfig) // RM 2,000.00
        assertTrue(aboveThreshold is CommissionCalculationResult.Calculated)
        aboveThreshold as CommissionCalculationResult.Calculated
        assertTrue(aboveThreshold.thresholdMet)
        // 3.50% of 200,000 sen = 7,000 sen + 15,000 sen bonus = 22,000 sen (RM 220.00)
        assertEquals(7_000L, aboveThreshold.rateCommissionSen)
        assertEquals(15_000L, aboveThreshold.flatBonusAppliedSen)
        assertEquals(22_000L, aboveThreshold.totalEstimatedCommissionSen)
    }
}
