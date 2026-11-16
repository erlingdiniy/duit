package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.DailySalesRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CompanyCommissionEngineTest {

    private val memoRule = CompanyCommissionEngine.defaultMemoRule()

    @Test
    fun `may regression example computes exact RM489_01`() {
        val result = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 9_234_000L, // RM 92,340.00
            monthlyGrossProfitSen = 686_212L, // RM 6,862.12
            rule = memoRule
        )
        assertTrue(result.isSalesThresholdMet)
        assertFalse(result.isIncentiveRateApplied)
        assertEquals(197_200L, result.salaryCostSen) // 1700 * 1.16 = 1972.00
        assertEquals(489_012L, result.baselineSen) // 4890.12
        assertEquals(48_901L, result.estimatedGrossCommissionSen) // RM 489.01
    }

    @Test
    fun `august regression example computes exact RM663_63`() {
        val result = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 10_123_300L, // RM 101,233.00
            monthlyGrossProfitSen = 728_100L, // RM 7,281.00
            rule = memoRule
        )
        assertTrue(result.isSalesThresholdMet)
        assertTrue(result.isIncentiveRateApplied)
        assertEquals(530_900L, result.baselineSen) // 5309.00 (> 5000)
        assertEquals(66_363L, result.estimatedGrossCommissionSen) // 5309 * 12.5% = 663.625 -> RM 663.63
    }

    @Test
    fun `september regression example computes exact RM929_25`() {
        val result = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 13_815_700L, // RM 138,157.00
            monthlyGrossProfitSen = 940_600L, // RM 9,406.00
            rule = memoRule
        )
        assertTrue(result.isSalesThresholdMet)
        assertTrue(result.isIncentiveRateApplied)
        assertEquals(743_400L, result.baselineSen) // 7434.00
        assertEquals(92_925L, result.estimatedGrossCommissionSen) // RM 929.25
    }

    @Test
    fun `sales threshold and incentive baseline boundaries are exact`() {
        // Just below RM 35,000 minimum sales
        val belowMin = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 3_499_999L,
            monthlyGrossProfitSen = 800_000L,
            rule = memoRule
        )
        assertFalse(belowMin.isSalesThresholdMet)
        assertEquals(0L, belowMin.estimatedGrossCommissionSen)

        // Exactly RM 35,000 sales and Baseline == RM 5,000.00 (not > 5,000 -> standard 10%)
        val exact5kBaseline = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 3_500_000L,
            monthlyGrossProfitSen = 197_200L + 500_000L,
            rule = memoRule
        )
        assertTrue(exact5kBaseline.isSalesThresholdMet)
        assertFalse(exact5kBaseline.isIncentiveRateApplied)
        assertEquals(1000, exact5kBaseline.appliedRateBps)
        assertEquals(50_000L, exact5kBaseline.estimatedGrossCommissionSen)

        // Baseline == RM 5,000.01 (> 5,000 -> 12.5% on FULL baseline)
        val justAbove5k = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 3_500_000L,
            monthlyGrossProfitSen = 197_200L + 500_001L,
            rule = memoRule
        )
        assertTrue(justAbove5k.isIncentiveRateApplied)
        assertEquals(1250, justAbove5k.appliedRateBps)
        assertEquals(62_500L, justAbove5k.estimatedGrossCommissionSen)
    }

    @Test
    fun `negative baseline applies documented provisional RM0 assumption`() {
        val result = CompanyCommissionEngine.calculateMonthly(
            monthlySalesSen = 4_000_000L,
            monthlyGrossProfitSen = 150_000L, // RM 1,500 GP < RM 1,972 Salary Cost
            rule = memoRule
        )
        assertTrue(result.isSalesThresholdMet)
        assertTrue(result.isNegativeBaselineAssumptionApplied)
        assertEquals(-47_200L, result.baselineSen)
        assertEquals(0L, result.estimatedGrossCommissionSen)
    }

    @Test
    fun `historical effective-dated rule versions preserve past month estimates`() {
        val futureRule = memoRule.copy(
            id = 2L,
            ruleName = "Revised 2026 Rule",
            effectiveFromEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(),
            basicSalarySen = 220_000L,
            standardRateBps = 800
        )
        val versions = listOf(memoRule.copy(id = 1L), futureRule)

        val resolvedAug = CompanyCommissionEngine.resolveRuleForMonth(YearMonth.of(2026, 8), versions)
        val resolvedDec = CompanyCommissionEngine.resolveRuleForMonth(YearMonth.of(2026, 12), versions)

        assertEquals(1L, resolvedAug.id)
        assertEquals(2L, resolvedDec.id)
    }

    @Test
    fun `daily sales and gp aggregate cleanly per month with margin percentage`() {
        val records = listOf(
            DailySalesRecord(1L, LocalDate.of(2026, 11, 2).toEpochDay(), 500_000L, 500_000L, 0, "", 1L, grossProfitSen = 40_000L),
            DailySalesRecord(2L, LocalDate.of(2026, 11, 15).toEpochDay(), 300_000L, 300_000L, 0, "", 2L, grossProfitSen = 20_000L),
            DailySalesRecord(3L, LocalDate.of(2026, 10, 31).toEpochDay(), 900_000L, 900_000L, 0, "", 3L, grossProfitSen = 90_000L)
        )
        val (novSales, novGp) = CompanyCommissionEngine.aggregateMonthSales(records, YearMonth.of(2026, 11))
        assertEquals(800_000L, novSales)
        assertEquals(60_000L, novGp)
        assertEquals("7.50%", CompanyCommissionEngine.formatGpMargin(novSales, novGp))
    }
}
