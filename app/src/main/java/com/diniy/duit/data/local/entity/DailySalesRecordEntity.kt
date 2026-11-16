package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.DailySalesRecord

@Entity(
    tableName = "daily_sales_records",
    indices = [Index(value = ["recordDateEpochDay"])]
)
data class DailySalesRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordDateEpochDay: Long,
    val grossSalesSen: Long,
    val netSalesSen: Long,
    val unitsSold: Int,
    val notes: String,
    val createdAtEpochMs: Long,
    val grossProfitSen: Long = 0L
) {
    fun toDomain(): DailySalesRecord = DailySalesRecord(
        id = id,
        recordDateEpochDay = recordDateEpochDay,
        grossSalesSen = grossSalesSen,
        netSalesSen = netSalesSen,
        unitsSold = unitsSold,
        notes = notes,
        createdAtEpochMs = createdAtEpochMs,
        grossProfitSen = grossProfitSen
    )
}

fun DailySalesRecord.toEntity(): DailySalesRecordEntity = DailySalesRecordEntity(
    id = id,
    recordDateEpochDay = recordDateEpochDay,
    grossSalesSen = grossSalesSen,
    netSalesSen = netSalesSen,
    unitsSold = unitsSold,
    notes = notes,
    createdAtEpochMs = createdAtEpochMs,
    grossProfitSen = grossProfitSen
)
