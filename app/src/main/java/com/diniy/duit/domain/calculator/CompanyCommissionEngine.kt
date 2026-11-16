package com.diniy.duit.domain.calculator

import com.diniy.duit.domain.model.CommissionRuleVersion
import com.diniy.duit.domain.model.DailySalesRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Output of the Company Commission Memo calculation for a given sales & GP month.
 * All monetary values use integer minor units (sen) and BigDecimal HALF_UP rounding.
 */
data class CompanyCommissionEstimate(
    val ruleVersion: CommissionRuleVersion,
    val monthlySalesSen: Long,
    val monthlyGrossProfitSen: Long,
    val gpMarginPercentDisplay: String,
    val salaryCostSen: Long,
    val baselineSen: Long,
    val isSalesThresholdMet: Boolean,
    val isIncentiveRateApplied: Boolean,
    val appliedRateBps: Int,
    val estimatedGrossCommissionSen: Long,
    val isNegativeBaselineAssumptionApplied: Boolean
)

/**
 * Company Commission Engine implementing the Company Memo effective 1 March 2024
 * using exact BigDecimal arithmetic (never Float or Double):
 *
 * - Salary Cost = Basic Salary * (1 + Employer Cost Factor)
 * - Baseline = Monthly GP - Salary Cost
 * - If Monthly Sales < Min Monthly Sales (RM 35,000): Estimated Commission = RM 0.00
 * - Else if Baseline <= 0: Estimated Commission = RM 0.00 (provisional assumption)
 * - Else if Baseline > Incentive Threshold (RM 5,000): Estimated Gross = Baseline * 12.5%
 * - Else: Estimated Gross = Baseline * 10%
 */
object CompanyCommissionEngine {

    private val TEN_THOUSAND = BigDecimal(10_000)
    private val ONE_HUNDRED = BigDecimal(100)

    val DEFAULT_MEMO_EFFECTIVE_DAY: Long = LocalDate.of(2024, 3, 1).toEpochDay()

    fun defaultMemoRule(createdAtEpochMs: Long = 0L): CommissionRuleVersion =
        CommissionRuleVersion(
            id = 0L,
            ruleName = "Company Memo (Eff. 1 Mar 2024)",
            effectiveFromEpochDay = DEFAULT_MEMO_EFFECTIVE_DAY,
            basicSalarySen = 170_000L, // RM 1,700.00
            minMonthlySalesSen = 3_500_000L, // RM 35,000.00
            employerCostRateBps = 1600, // 16.00%
            standardRateBps = 1000, // 10.00%
            incentiveBaselineThresholdSen = 500_000L, // RM 5,000.00
            incentiveRateBps = 1250, // 12.50%
            createdAtEpochMs = createdAtEpochMs
        )

    /**
     * Resolves the effective rule version for [yearMonth] without rewriting historical estimates
     * when newer effective-dated rules are added.
     */
    fun resolveRuleForMonth(
        yearMonth: YearMonth,
        versions: List<CommissionRuleVersion>
    ): CommissionRuleVersion {
        if (versions.isEmpty()) return defaultMemoRule()
        val monthEndEpochDay = yearMonth.atEndOfMonth().toEpochDay()
        val applicable = versions
            .filter { it.effectiveFromEpochDay <= monthEndEpochDay }
            .maxWithOrNull(compareBy<CommissionRuleVersion> { it.effectiveFromEpochDay }.thenBy { it.id })
        return applicable ?: versions.minBy { it.effectiveFromEpochDay }
    }

    fun calculateMonthly(
        monthlySalesSen: Long,
        monthlyGrossProfitSen: Long,
        rule: CommissionRuleVersion
    ): CompanyCommissionEstimate {
        val multiplierNumerator = BigDecimal(10_000 + rule.employerCostRateBps)
        val salaryCostExactSen = BigDecimal(rule.basicSalarySen)
            .multiply(multiplierNumerator)
            .divide(TEN_THOUSAND, 4, RoundingMode.HALF_UP)

        val salaryCostRoundedSen = salaryCostExactSen
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

        val baselineExactSen = BigDecimal(monthlyGrossProfitSen).subtract(salaryCostExactSen)
        val baselineRoundedSen = baselineExactSen
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

        val gpMarginDisplay = formatGpMargin(monthlySalesSen, monthlyGrossProfitSen)
        val salesThresholdMet = monthlySalesSen >= rule.minMonthlySalesSen

        if (!salesThresholdMet) {
            return CompanyCommissionEstimate(
                ruleVersion = rule,
                monthlySalesSen = monthlySalesSen,
                monthlyGrossProfitSen = monthlyGrossProfitSen,
                gpMarginPercentDisplay = gpMarginDisplay,
                salaryCostSen = salaryCostRoundedSen,
                baselineSen = baselineRoundedSen,
                isSalesThresholdMet = false,
                isIncentiveRateApplied = false,
                appliedRateBps = 0,
                estimatedGrossCommissionSen = 0L,
                isNegativeBaselineAssumptionApplied = false
            )
        }

        if (baselineExactSen <= BigDecimal.ZERO) {
            // Provisional estimator assumption: negative or zero baseline yields RM 0.00 rather than negative commission.
            return CompanyCommissionEstimate(
                ruleVersion = rule,
                monthlySalesSen = monthlySalesSen,
                monthlyGrossProfitSen = monthlyGrossProfitSen,
                gpMarginPercentDisplay = gpMarginDisplay,
                salaryCostSen = salaryCostRoundedSen,
                baselineSen = baselineRoundedSen,
                isSalesThresholdMet = true,
                isIncentiveRateApplied = false,
                appliedRateBps = 0,
                estimatedGrossCommissionSen = 0L,
                isNegativeBaselineAssumptionApplied = baselineExactSen < BigDecimal.ZERO
            )
        }

        val thresholdSenBD = BigDecimal(rule.incentiveBaselineThresholdSen)
        val isIncentive = baselineExactSen > thresholdSenBD
        val rateBps = if (isIncentive) rule.incentiveRateBps else rule.standardRateBps

        val estimatedGrossSen = baselineExactSen
            .multiply(BigDecimal(rateBps))
            .divide(TEN_THOUSAND, 0, RoundingMode.HALF_UP)
            .longValueExact()

        return CompanyCommissionEstimate(
            ruleVersion = rule,
            monthlySalesSen = monthlySalesSen,
            monthlyGrossProfitSen = monthlyGrossProfitSen,
            gpMarginPercentDisplay = gpMarginDisplay,
            salaryCostSen = salaryCostRoundedSen,
            baselineSen = baselineRoundedSen,
            isSalesThresholdMet = true,
            isIncentiveRateApplied = isIncentive,
            appliedRateBps = rateBps,
            estimatedGrossCommissionSen = estimatedGrossSen,
            isNegativeBaselineAssumptionApplied = false
        )
    }

    fun aggregateMonthSales(
        records: List<DailySalesRecord>,
        yearMonth: YearMonth
    ): Pair<Long, Long> {
        val startDay = yearMonth.atDay(1).toEpochDay()
        val endDay = yearMonth.atEndOfMonth().toEpochDay()
        val monthRecords = records.filter { it.recordDateEpochDay in startDay..endDay }
        val totalSales = monthRecords.sumOf { it.grossSalesSen }
        val totalGp = monthRecords.sumOf { it.grossProfitSen }
        return totalSales to totalGp
    }

    fun formatGpMargin(salesSen: Long, gpSen: Long): String {
        if (salesSen <= 0L) return "0.00%"
        val pct = BigDecimal(gpSen)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal(salesSen), 2, RoundingMode.HALF_UP)
        return "${pct.toPlainString()}%"
    }
}
