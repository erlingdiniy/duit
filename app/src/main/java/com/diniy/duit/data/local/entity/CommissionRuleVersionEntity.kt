package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.CommissionRuleVersion

@Entity(
    tableName = "commission_rule_versions",
    indices = [Index(value = ["effectiveFromEpochDay"])]
)
data class CommissionRuleVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ruleName: String,
    val effectiveFromEpochDay: Long,
    val basicSalarySen: Long,
    val minMonthlySalesSen: Long,
    val employerCostRateBps: Int,
    val standardRateBps: Int,
    val incentiveBaselineThresholdSen: Long,
    val incentiveRateBps: Int,
    val createdAtEpochMs: Long
) {
    fun toDomain(): CommissionRuleVersion = CommissionRuleVersion(
        id = id,
        ruleName = ruleName,
        effectiveFromEpochDay = effectiveFromEpochDay,
        basicSalarySen = basicSalarySen,
        minMonthlySalesSen = minMonthlySalesSen,
        employerCostRateBps = employerCostRateBps,
        standardRateBps = standardRateBps,
        incentiveBaselineThresholdSen = incentiveBaselineThresholdSen,
        incentiveRateBps = incentiveRateBps,
        createdAtEpochMs = createdAtEpochMs
    )
}

fun CommissionRuleVersion.toEntity(): CommissionRuleVersionEntity = CommissionRuleVersionEntity(
    id = id,
    ruleName = ruleName,
    effectiveFromEpochDay = effectiveFromEpochDay,
    basicSalarySen = basicSalarySen,
    minMonthlySalesSen = minMonthlySalesSen,
    employerCostRateBps = employerCostRateBps,
    standardRateBps = standardRateBps,
    incentiveBaselineThresholdSen = incentiveBaselineThresholdSen,
    incentiveRateBps = incentiveRateBps,
    createdAtEpochMs = createdAtEpochMs
)
