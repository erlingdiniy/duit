package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.SalaryCycle

@Entity(
    tableName = "salary_cycles",
    indices = [Index(value = ["isActive"])]
)
data class SalaryCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val paydayEpochDay: Long,
    val baseSalarySen: Long,
    val committedBudgetSen: Long,
    val isActive: Boolean,
    val receivedSalarySen: Long = 0L,
    val salaryTransactionId: Long? = null,
    val isOpenEnded: Boolean = true
) {
    fun toDomain(): SalaryCycle = SalaryCycle(
        id = id,
        label = label,
        startDateEpochDay = startDateEpochDay,
        endDateEpochDay = endDateEpochDay,
        paydayEpochDay = paydayEpochDay,
        baseSalarySen = baseSalarySen,
        committedBudgetSen = committedBudgetSen,
        isActive = isActive,
        receivedSalarySen = receivedSalarySen,
        salaryTransactionId = salaryTransactionId,
        isOpenEnded = isOpenEnded
    )
}

fun SalaryCycle.toEntity(): SalaryCycleEntity = SalaryCycleEntity(
    id = id,
    label = label,
    startDateEpochDay = startDateEpochDay,
    endDateEpochDay = endDateEpochDay,
    paydayEpochDay = paydayEpochDay,
    baseSalarySen = baseSalarySen,
    committedBudgetSen = committedBudgetSen,
    isActive = isActive,
    receivedSalarySen = receivedSalarySen,
    salaryTransactionId = salaryTransactionId,
    isOpenEnded = isOpenEnded
)
