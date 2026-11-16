package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: WalletType,
    val balanceSen: Long,
    val isExcludedFromSafeToSpend: Boolean,
    val accentToken: PrismaticAccentToken,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val isIncludedInTotalBalance: Boolean = true,
    val isArchived: Boolean = false
) {
    fun toDomain(): Wallet = Wallet(
        id = id,
        name = name,
        type = type,
        balanceSen = balanceSen,
        isExcludedFromSafeToSpend = isExcludedFromSafeToSpend,
        accentToken = accentToken,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        isIncludedInTotalBalance = isIncludedInTotalBalance,
        isArchived = isArchived
    )
}

fun Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    name = name,
    type = type,
    balanceSen = balanceSen,
    isExcludedFromSafeToSpend = isExcludedFromSafeToSpend,
    accentToken = accentToken,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    isIncludedInTotalBalance = isIncludedInTotalBalance,
    isArchived = isArchived
)
