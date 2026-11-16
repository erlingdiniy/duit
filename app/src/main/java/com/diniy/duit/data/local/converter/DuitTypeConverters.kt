package com.diniy.duit.data.local.converter

import androidx.room.TypeConverter
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.WalletType

class DuitTypeConverters {
    @TypeConverter
    fun fromWalletType(value: WalletType): String = value.name

    @TypeConverter
    fun toWalletType(raw: String): WalletType =
        WalletType.entries.firstOrNull { it.name == raw } ?: WalletType.BANK

    @TypeConverter
    fun fromAccentToken(value: PrismaticAccentToken): String = value.name

    @TypeConverter
    fun toAccentToken(raw: String): PrismaticAccentToken =
        PrismaticAccentToken.entries.firstOrNull { it.name == raw } ?: PrismaticAccentToken.AQUA

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(raw: String): TransactionType =
        TransactionType.entries.firstOrNull { it.name == raw } ?: TransactionType.EXPENSE

    @TypeConverter
    fun fromCommitmentStatus(value: CommitmentStatus): String = value.name

    @TypeConverter
    fun toCommitmentStatus(raw: String): CommitmentStatus =
        CommitmentStatus.entries.firstOrNull { it.name == raw } ?: CommitmentStatus.UPCOMING

    @TypeConverter
    fun fromCommissionStatus(value: CommissionStatus): String = value.name

    @TypeConverter
    fun toCommissionStatus(raw: String): CommissionStatus = when (raw) {
        "CONFIRMED" -> CommissionStatus.PENDING
        "PAID" -> CommissionStatus.RECEIVED
        else -> CommissionStatus.entries.firstOrNull { it.name == raw } ?: CommissionStatus.UNKNOWN
    }

    @TypeConverter
    fun fromSavingsGoalStatus(value: SavingsGoalStatus): String = value.name

    @TypeConverter
    fun toSavingsGoalStatus(raw: String): SavingsGoalStatus =
        SavingsGoalStatus.entries.firstOrNull { it.name == raw } ?: SavingsGoalStatus.ACTIVE

    @TypeConverter
    fun fromSavingsMovementType(value: SavingsMovementType): String = value.name

    @TypeConverter
    fun toSavingsMovementType(raw: String): SavingsMovementType =
        SavingsMovementType.entries.firstOrNull { it.name == raw } ?: SavingsMovementType.ALLOCATE

    @TypeConverter
    fun fromBudgetScope(value: BudgetScope): String = value.name

    @TypeConverter
    fun toBudgetScope(raw: String): BudgetScope =
        BudgetScope.entries.firstOrNull { it.name == raw } ?: BudgetScope.CATEGORY

    @TypeConverter
    fun fromBudgetPeriodType(value: BudgetPeriodType): String = value.name

    @TypeConverter
    fun toBudgetPeriodType(raw: String): BudgetPeriodType =
        BudgetPeriodType.entries.firstOrNull { it.name == raw } ?: BudgetPeriodType.MONTHLY

    @TypeConverter
    fun fromCommitmentFrequency(value: CommitmentFrequency): String = value.name

    @TypeConverter
    fun toCommitmentFrequency(raw: String): CommitmentFrequency =
        CommitmentFrequency.entries.firstOrNull { it.name == raw } ?: CommitmentFrequency.MONTHLY

    @TypeConverter
    fun fromDebtDirection(value: DebtDirection): String = value.name

    @TypeConverter
    fun toDebtDirection(raw: String): DebtDirection =
        DebtDirection.entries.firstOrNull { it.name == raw } ?: DebtDirection.I_OWE

    @TypeConverter
    fun fromObligationType(value: ObligationType): String = value.name

    @TypeConverter
    fun toObligationType(raw: String): ObligationType =
        ObligationType.entries.firstOrNull { it.name == raw } ?: ObligationType.COMMITMENT
}
