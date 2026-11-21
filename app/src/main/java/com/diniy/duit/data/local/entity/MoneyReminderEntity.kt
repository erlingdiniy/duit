package com.diniy.duit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder

/** Schema v10. No links to wallets or transactions: reminders never move money. */
@Entity(
    tableName = "money_reminders",
    indices = [Index(value = ["dateEpochDay"])]
)
data class MoneyReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val dateEpochDay: Long,
    @ColumnInfo(defaultValue = "'ONE_TIME'") val repeatFrequency: CommitmentFrequency = CommitmentFrequency.ONE_TIME,
    @ColumnInfo(defaultValue = "NULL") val customIntervalDays: Int? = null,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

fun MoneyReminderEntity.toDomain() = MoneyReminder(
    id = id, title = title, dateEpochDay = dateEpochDay, repeat = repeatFrequency,
    customIntervalDays = customIntervalDays, note = note,
    createdAtEpochMs = createdAtEpochMs, updatedAtEpochMs = updatedAtEpochMs
)

fun MoneyReminder.toEntity() = MoneyReminderEntity(
    id = id, title = title, dateEpochDay = dateEpochDay, repeatFrequency = repeat,
    customIntervalDays = customIntervalDays, note = note,
    createdAtEpochMs = createdAtEpochMs, updatedAtEpochMs = updatedAtEpochMs
)
