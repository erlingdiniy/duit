package com.diniy.duit.backup

import com.diniy.duit.data.local.dao.LedgerDatabaseSnapshot
import com.diniy.duit.data.local.entity.BudgetEntity
import com.diniy.duit.data.local.entity.CategoryEntity
import com.diniy.duit.data.local.entity.CommissionEntity
import com.diniy.duit.data.local.entity.CommissionPaymentEntity
import com.diniy.duit.data.local.entity.CommissionRuleVersionEntity
import com.diniy.duit.data.local.entity.CommissionSourceEntity
import com.diniy.duit.data.local.entity.CommitmentEntity
import com.diniy.duit.data.local.entity.DailySalesRecordEntity
import com.diniy.duit.data.local.entity.DebtEntity
import com.diniy.duit.data.local.entity.NotificationHistoryEntity
import com.diniy.duit.data.local.entity.ObligationPaymentEntity
import com.diniy.duit.data.local.entity.PayLaterPlanEntity
import com.diniy.duit.data.local.entity.SalaryCycleEntity
import com.diniy.duit.data.local.entity.SavingsAllocationEntity
import com.diniy.duit.data.local.entity.SavingsGoalEntity
import com.diniy.duit.data.local.entity.SavingsMovementEntity
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import org.json.JSONArray
import org.json.JSONObject

data class PortablePrivacyPreferences(
    val hideSensitiveBalances: Boolean = false,
    val privacyModeEnabled: Boolean = false,
    val alwaysMaskWidgetAmounts: Boolean = false
)

data class PortablePreferencesBackup(
    val appSettings: AppSettingsSnapshot,
    val notificationSettings: NotificationSettingsSnapshot,
    val privacyPreferences: PortablePrivacyPreferences
)

data class BackupPreviewSummary(
    val formatVersion: Int,
    val dbSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val walletCount: Int,
    val transactionCount: Int,
    val commitmentCount: Int,
    val payLaterPlanCount: Int,
    val debtCount: Int,
    val savingsGoalCount: Int,
    val budgetCount: Int,
    val commissionCount: Int,
    val salaryCycleCount: Int
)

data class DecryptedBackupBundle(
    val header: BackupEnvelopeHeader,
    val ledger: LedgerDatabaseSnapshot,
    val preferences: PortablePreferencesBackup,
    val preview: BackupPreviewSummary
)

data class LedgerStorageSummary(
    val walletCount: Int = 0,
    val transactionCount: Int = 0,
    val commitmentCount: Int = 0,
    val payLaterPlanCount: Int = 0,
    val debtCount: Int = 0,
    val savingsGoalCount: Int = 0,
    val budgetCount: Int = 0,
    val commissionCount: Int = 0,
    val salaryCycleCount: Int = 0,
    val notificationHistoryCount: Int = 0
) {
    val totalFinancialRecords: Int
        get() = walletCount + transactionCount + commitmentCount + payLaterPlanCount +
            debtCount + savingsGoalCount + budgetCount + commissionCount + salaryCycleCount
}

/**
 * Lossless JSON serializer for Room ledger records and portable preferences.
 * Device-bound security credentials (PIN envelopes, biometric enrollment, lockout state) are
 * intentionally excluded so restoring never overwrites or bricks target device authentication.
 */
object BackupSerializer {

    fun encodePayload(
        ledger: LedgerDatabaseSnapshot,
        preferences: PortablePreferencesBackup,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        dbSchemaVersion: Int = BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION
    ): String {
        return JSONObject().apply {
            put("dbSchemaVersion", dbSchemaVersion)
            put("createdAtEpochMs", createdAtEpochMs)
            put("ledger", encodeLedger(ledger))
            put("preferences", encodePreferences(preferences))
        }.toString()
    }

    fun decodePayload(
        header: BackupEnvelopeHeader,
        plaintextJson: String
    ): DecryptedBackupBundle {
        val root = runCatching { JSONObject(plaintextJson) }.getOrElse {
            throw BackupFormatException("Malformed decrypted backup payload.", it)
        }
        val payloadSchema = root.optInt("dbSchemaVersion", header.dbSchemaVersion)
        if (payloadSchema !in 1..BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION) {
            throw BackupVersionException("Unsupported backup schema version: $payloadSchema.")
        }
        val ledgerJson = root.optJSONObject("ledger")
            ?: throw BackupFormatException("Backup payload is missing ledger section.")
        val prefsJson = root.optJSONObject("preferences") ?: JSONObject()

        val ledger = decodeLedger(ledgerJson)
        validateRelationalIntegrity(ledger)
        val preferences = decodePreferences(prefsJson)
        val preview = BackupPreviewSummary(
            formatVersion = header.formatVersion,
            dbSchemaVersion = payloadSchema,
            createdAtEpochMs = root.optLong("createdAtEpochMs", header.createdAtEpochMs),
            walletCount = ledger.wallets.size,
            transactionCount = ledger.transactions.size,
            commitmentCount = ledger.commitments.size,
            payLaterPlanCount = ledger.payLaterPlans.size,
            debtCount = ledger.debts.size,
            savingsGoalCount = ledger.savingsGoals.size,
            budgetCount = ledger.budgets.size,
            commissionCount = ledger.commissions.size,
            salaryCycleCount = ledger.salaryCycles.size
        )

        return DecryptedBackupBundle(
            header = header,
            ledger = ledger,
            preferences = preferences,
            preview = preview
        )
    }

    private fun validateRelationalIntegrity(ledger: LedgerDatabaseSnapshot) {
        val walletIds = ledger.wallets.map { it.id }.toSet()
        val goalIds = ledger.savingsGoals.map { it.id }.toSet()

        for (tx in ledger.transactions) {
            if (tx.walletId !in walletIds) {
                throw BackupFormatException("Backup transaction ${tx.id} references missing wallet ${tx.walletId}.")
            }
            if (tx.targetWalletId != null && tx.targetWalletId !in walletIds) {
                throw BackupFormatException("Backup transaction ${tx.id} references missing target wallet ${tx.targetWalletId}.")
            }
        }
        for (alloc in ledger.savingsAllocations) {
            if (alloc.walletId !in walletIds || alloc.goalId !in goalIds) {
                throw BackupFormatException("Backup savings allocation ${alloc.id} has invalid references.")
            }
        }
    }

    private fun encodeLedger(s: LedgerDatabaseSnapshot): JSONObject = JSONObject().apply {
        put("wallets", JSONArray().apply {
            s.wallets.forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("type", w.type.name)
                    put("balanceSen", w.balanceSen)
                    put("isExcludedFromSafeToSpend", w.isExcludedFromSafeToSpend)
                    put("accentToken", w.accentToken.name)
                    put("createdAtEpochMs", w.createdAtEpochMs)
                    put("updatedAtEpochMs", w.updatedAtEpochMs)
                    put("isIncludedInTotalBalance", w.isIncludedInTotalBalance)
                    put("isArchived", w.isArchived)
                })
            }
        })
        put("categories", JSONArray().apply {
            s.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("type", c.type.name)
                    put("iconKey", c.iconKey)
                    put("accentToken", c.accentToken.name)
                    put("isDefault", c.isDefault)
                    put("isArchived", c.isArchived)
                    put("sortOrder", c.sortOrder)
                })
            }
        })
        put("salaryCycles", JSONArray().apply {
            s.salaryCycles.forEach { sc ->
                put(JSONObject().apply {
                    put("id", sc.id)
                    put("label", sc.label)
                    put("startDateEpochDay", sc.startDateEpochDay)
                    put("endDateEpochDay", sc.endDateEpochDay)
                    put("paydayEpochDay", sc.paydayEpochDay)
                    put("baseSalarySen", sc.baseSalarySen)
                    put("committedBudgetSen", sc.committedBudgetSen)
                    put("isActive", sc.isActive)
                    put("receivedSalarySen", sc.receivedSalarySen)
                    putNullable("salaryTransactionId", sc.salaryTransactionId)
                    put("isOpenEnded", sc.isOpenEnded)
                })
            }
        })
        put("commitments", JSONArray().apply {
            s.commitments.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("amountSen", c.amountSen)
                    put("dueDateEpochDay", c.dueDateEpochDay)
                    put("status", c.status.name)
                    putNullable("walletId", c.walletId)
                    put("isRecurring", c.isRecurring)
                    putNullable("salaryCycleId", c.salaryCycleId)
                    put("category", c.category)
                    putNullable("categoryId", c.categoryId)
                    put("frequency", c.frequency.name)
                    putNullable("customIntervalDays", c.customIntervalDays)
                    putNullable("anchorDayOfMonth", c.anchorDayOfMonth)
                    put("paidSen", c.paidSen)
                    putNullable("nextDueDateEpochDay", c.nextDueDateEpochDay)
                    put("note", c.note)
                    put("isArchived", c.isArchived)
                    put("createdAtEpochMs", c.createdAtEpochMs)
                    put("updatedAtEpochMs", c.updatedAtEpochMs)
                })
            }
        })
        put("commissionSources", JSONArray().apply {
            s.commissionSources.forEach { cs ->
                put(JSONObject().apply {
                    put("id", cs.id)
                    put("name", cs.name)
                    put("accentToken", cs.accentToken.name)
                    put("isDefault", cs.isDefault)
                    put("isArchived", cs.isArchived)
                    put("sortOrder", cs.sortOrder)
                })
            }
        })
        put("commissions", JSONArray().apply {
            s.commissions.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("sourceLabel", c.sourceLabel)
                    put("amountSen", c.amountSen)
                    put("status", c.status.name)
                    put("earnedDateEpochDay", c.earnedDateEpochDay)
                    putNullable("payoutDateEpochDay", c.payoutDateEpochDay)
                    putNullable("linkedTransactionId", c.linkedTransactionId)
                    put("notes", c.notes)
                    putNullable("sourceId", c.sourceId)
                    put("yearMonth", c.yearMonth)
                    putNullable("estimatedGrossSen", c.estimatedGrossSen)
                    put("actualGrossSen", c.actualGrossSen)
                    put("receivedNetSen", c.receivedNetSen)
                    put("adjustmentNote", c.adjustmentNote)
                })
            }
        })
        put("commissionPayments", JSONArray().apply {
            s.commissionPayments.forEach { cp ->
                put(JSONObject().apply {
                    put("id", cp.id)
                    put("commissionId", cp.commissionId)
                    putNullable("sourceId", cp.sourceId)
                    put("sourceName", cp.sourceName)
                    put("walletId", cp.walletId)
                    putNullable("linkedTransactionId", cp.linkedTransactionId)
                    put("receivedAtEpochMs", cp.receivedAtEpochMs)
                    put("actualGrossSen", cp.actualGrossSen)
                    putNullable("epfDeductionSen", cp.epfDeductionSen)
                    putNullable("socsoDeductionSen", cp.socsoDeductionSen)
                    putNullable("eisDeductionSen", cp.eisDeductionSen)
                    put("actualNetSen", cp.actualNetSen)
                    put("notes", cp.notes)
                    putNullable("submissionId", cp.submissionId)
                })
            }
        })
        put("commissionRuleVersions", JSONArray().apply {
            s.commissionRuleVersions.forEach { rv ->
                put(JSONObject().apply {
                    put("id", rv.id)
                    put("ruleName", rv.ruleName)
                    put("effectiveFromEpochDay", rv.effectiveFromEpochDay)
                    put("basicSalarySen", rv.basicSalarySen)
                    put("minMonthlySalesSen", rv.minMonthlySalesSen)
                    put("employerCostRateBps", rv.employerCostRateBps)
                    put("standardRateBps", rv.standardRateBps)
                    put("incentiveBaselineThresholdSen", rv.incentiveBaselineThresholdSen)
                    put("incentiveRateBps", rv.incentiveRateBps)
                    put("createdAtEpochMs", rv.createdAtEpochMs)
                })
            }
        })
        put("dailySalesRecords", JSONArray().apply {
            s.dailySalesRecords.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("recordDateEpochDay", d.recordDateEpochDay)
                    put("grossSalesSen", d.grossSalesSen)
                    put("netSalesSen", d.netSalesSen)
                    put("unitsSold", d.unitsSold)
                    put("notes", d.notes)
                    put("createdAtEpochMs", d.createdAtEpochMs)
                    put("grossProfitSen", d.grossProfitSen)
                })
            }
        })
        put("savingsGoals", JSONArray().apply {
            s.savingsGoals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("targetAmountSen", g.targetAmountSen)
                    putNullable("targetDateEpochDay", g.targetDateEpochDay)
                    put("description", g.description)
                    put("iconKey", g.iconKey)
                    put("accentToken", g.accentToken.name)
                    put("status", g.status.name)
                    put("createdAtEpochMs", g.createdAtEpochMs)
                    put("updatedAtEpochMs", g.updatedAtEpochMs)
                })
            }
        })
        put("savingsAllocations", JSONArray().apply {
            s.savingsAllocations.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("goalId", a.goalId)
                    put("walletId", a.walletId)
                    put("allocatedSen", a.allocatedSen)
                    put("updatedAtEpochMs", a.updatedAtEpochMs)
                })
            }
        })
        put("savingsMovements", JSONArray().apply {
            s.savingsMovements.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("goalId", m.goalId)
                    putNullable("sourceWalletId", m.sourceWalletId)
                    putNullable("targetWalletId", m.targetWalletId)
                    putNullable("relatedGoalId", m.relatedGoalId)
                    put("type", m.type.name)
                    put("amountSen", m.amountSen)
                    putNullable("linkedTransactionId", m.linkedTransactionId)
                    put("note", m.note)
                    put("occurredAtEpochMs", m.occurredAtEpochMs)
                    putNullable("submissionId", m.submissionId)
                })
            }
        })
        put("budgets", JSONArray().apply {
            s.budgets.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("scope", b.scope.name)
                    put("periodType", b.periodType.name)
                    put("yearMonth", b.yearMonth)
                    putNullable("salaryCycleId", b.salaryCycleId)
                    putNullable("categoryId", b.categoryId)
                    put("categoryName", b.categoryName)
                    put("limitSen", b.limitSen)
                    put("accentToken", b.accentToken.name)
                    put("note", b.note)
                    put("isArchived", b.isArchived)
                    put("createdAtEpochMs", b.createdAtEpochMs)
                    put("updatedAtEpochMs", b.updatedAtEpochMs)
                })
            }
        })
        put("payLaterPlans", JSONArray().apply {
            s.payLaterPlans.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("providerName", p.providerName)
                    put("purchaseAmountSen", p.purchaseAmountSen)
                    put("feeOrInterestSen", p.feeOrInterestSen)
                    put("paidSen", p.paidSen)
                    put("instalmentCount", p.instalmentCount)
                    put("firstDueDateEpochDay", p.firstDueDateEpochDay)
                    put("nextDueDateEpochDay", p.nextDueDateEpochDay)
                    put("accentToken", p.accentToken.name)
                    put("note", p.note)
                    put("isArchived", p.isArchived)
                    put("createdAtEpochMs", p.createdAtEpochMs)
                    put("updatedAtEpochMs", p.updatedAtEpochMs)
                })
            }
        })
        put("debts", JSONArray().apply {
            s.debts.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("direction", d.direction.name)
                    put("counterpartyName", d.counterpartyName)
                    put("title", d.title)
                    put("originalAmountSen", d.originalAmountSen)
                    put("paidSen", d.paidSen)
                    putNullable("dueDateEpochDay", d.dueDateEpochDay)
                    put("accentToken", d.accentToken.name)
                    put("note", d.note)
                    put("isArchived", d.isArchived)
                    put("createdAtEpochMs", d.createdAtEpochMs)
                    put("updatedAtEpochMs", d.updatedAtEpochMs)
                    putNullable("scheduledRepaymentSen", d.scheduledRepaymentSen)
                })
            }
        })
        put("obligationPayments", JSONArray().apply {
            s.obligationPayments.forEach { op ->
                put(JSONObject().apply {
                    put("id", op.id)
                    put("obligationType", op.obligationType.name)
                    put("obligationId", op.obligationId)
                    put("walletId", op.walletId)
                    putNullable("linkedTransactionId", op.linkedTransactionId)
                    put("amountSen", op.amountSen)
                    putNullable("occurrenceEpochDay", op.occurrenceEpochDay)
                    put("paidAtEpochMs", op.paidAtEpochMs)
                    put("note", op.note)
                    putNullable("submissionId", op.submissionId)
                })
            }
        })
        put("transactions", JSONArray().apply {
            s.transactions.forEach { tx ->
                put(JSONObject().apply {
                    put("id", tx.id)
                    put("walletId", tx.walletId)
                    putNullable("targetWalletId", tx.targetWalletId)
                    put("type", tx.type.name)
                    put("amountSen", tx.amountSen)
                    put("category", tx.category)
                    put("title", tx.title)
                    put("note", tx.note)
                    putNullable("commitmentId", tx.commitmentId)
                    put("occurredAtEpochMs", tx.occurredAtEpochMs)
                    put("createdAtEpochMs", tx.createdAtEpochMs)
                    putNullable("categoryId", tx.categoryId)
                    putNullable("submissionId", tx.submissionId)
                })
            }
        })
        put("notificationHistory", JSONArray().apply {
            s.notificationHistory.forEach { nh ->
                put(JSONObject().apply {
                    put("id", nh.id)
                    put("dedupKey", nh.dedupKey)
                    put("notificationId", nh.notificationId)
                    put("category", nh.category)
                    put("entityId", nh.entityId)
                    put("targetEpochDay", nh.targetEpochDay)
                    put("title", nh.title)
                    put("message", nh.message)
                    putNullable("amountSen", nh.amountSen)
                    put("isOverdue", nh.isOverdue)
                    put("isRead", nh.isRead)
                    put("deepLink", nh.deepLink)
                    put("deliveredAtEpochMs", nh.deliveredAtEpochMs)
                })
            }
        })
    }

    private fun decodeLedger(json: JSONObject): LedgerDatabaseSnapshot = LedgerDatabaseSnapshot(
        wallets = json.optArrayObjects("wallets").map { o ->
            WalletEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = o.optEnum("type", WalletType.BANK),
                balanceSen = o.getLong("balanceSen"),
                isExcludedFromSafeToSpend = o.optBoolean("isExcludedFromSafeToSpend", false),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.AQUA),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L),
                isIncludedInTotalBalance = o.optBoolean("isIncludedInTotalBalance", true),
                isArchived = o.optBoolean("isArchived", false)
            )
        },
        categories = json.optArrayObjects("categories").map { o ->
            CategoryEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = o.optEnum("type", TransactionType.EXPENSE),
                iconKey = o.optString("iconKey", "tag"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.SKY_BLUE),
                isDefault = o.optBoolean("isDefault", false),
                isArchived = o.optBoolean("isArchived", false),
                sortOrder = o.optInt("sortOrder", 0)
            )
        },
        salaryCycles = json.optArrayObjects("salaryCycles").map { o ->
            SalaryCycleEntity(
                id = o.getLong("id"),
                label = o.getString("label"),
                startDateEpochDay = o.getLong("startDateEpochDay"),
                endDateEpochDay = o.getLong("endDateEpochDay"),
                paydayEpochDay = o.getLong("paydayEpochDay"),
                baseSalarySen = o.getLong("baseSalarySen"),
                committedBudgetSen = o.optLong("committedBudgetSen", 0L),
                isActive = o.optBoolean("isActive", false),
                receivedSalarySen = o.optLong("receivedSalarySen", 0L),
                salaryTransactionId = o.optNullableLong("salaryTransactionId"),
                isOpenEnded = o.optBoolean("isOpenEnded", true)
            )
        },
        commitments = json.optArrayObjects("commitments").map { o ->
            CommitmentEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                amountSen = o.getLong("amountSen"),
                dueDateEpochDay = o.getLong("dueDateEpochDay"),
                status = o.optEnum("status", CommitmentStatus.UPCOMING),
                walletId = o.optNullableLong("walletId"),
                isRecurring = o.optBoolean("isRecurring", false),
                salaryCycleId = o.optNullableLong("salaryCycleId"),
                category = o.optString("category", "Bills & Utilities"),
                categoryId = o.optNullableLong("categoryId"),
                frequency = o.optEnum("frequency", CommitmentFrequency.MONTHLY),
                customIntervalDays = o.optNullableInt("customIntervalDays"),
                anchorDayOfMonth = o.optNullableInt("anchorDayOfMonth"),
                paidSen = o.optLong("paidSen", 0L),
                nextDueDateEpochDay = o.optNullableLong("nextDueDateEpochDay"),
                note = o.optString("note", ""),
                isArchived = o.optBoolean("isArchived", false),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
            )
        },
        commissionSources = json.optArrayObjects("commissionSources").map { o ->
            CommissionSourceEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.LAVENDER),
                isDefault = o.optBoolean("isDefault", false),
                isArchived = o.optBoolean("isArchived", false),
                sortOrder = o.optInt("sortOrder", 0)
            )
        },
        commissions = json.optArrayObjects("commissions").map { o ->
            CommissionEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                sourceLabel = o.optString("sourceLabel", ""),
                amountSen = o.getLong("amountSen"),
                status = o.optEnum("status", CommissionStatus.PENDING),
                earnedDateEpochDay = o.getLong("earnedDateEpochDay"),
                payoutDateEpochDay = o.optNullableLong("payoutDateEpochDay"),
                linkedTransactionId = o.optNullableLong("linkedTransactionId"),
                notes = o.optString("notes", ""),
                sourceId = o.optNullableLong("sourceId"),
                yearMonth = o.optString("yearMonth", ""),
                estimatedGrossSen = o.optNullableLong("estimatedGrossSen"),
                actualGrossSen = o.optLong("actualGrossSen", 0L),
                receivedNetSen = o.optLong("receivedNetSen", 0L),
                adjustmentNote = o.optString("adjustmentNote", "")
            )
        },
        commissionPayments = json.optArrayObjects("commissionPayments").map { o ->
            CommissionPaymentEntity(
                id = o.getLong("id"),
                commissionId = o.getLong("commissionId"),
                sourceId = o.optNullableLong("sourceId"),
                sourceName = o.optString("sourceName", ""),
                walletId = o.getLong("walletId"),
                linkedTransactionId = o.optNullableLong("linkedTransactionId"),
                receivedAtEpochMs = o.getLong("receivedAtEpochMs"),
                actualGrossSen = o.getLong("actualGrossSen"),
                epfDeductionSen = o.optNullableLong("epfDeductionSen"),
                socsoDeductionSen = o.optNullableLong("socsoDeductionSen"),
                eisDeductionSen = o.optNullableLong("eisDeductionSen"),
                actualNetSen = o.getLong("actualNetSen"),
                notes = o.optString("notes", ""),
                submissionId = o.optNullableString("submissionId")
            )
        },
        commissionRuleVersions = json.optArrayObjects("commissionRuleVersions").map { o ->
            CommissionRuleVersionEntity(
                id = o.getLong("id"),
                ruleName = o.getString("ruleName"),
                effectiveFromEpochDay = o.getLong("effectiveFromEpochDay"),
                basicSalarySen = o.getLong("basicSalarySen"),
                minMonthlySalesSen = o.getLong("minMonthlySalesSen"),
                employerCostRateBps = o.getInt("employerCostRateBps"),
                standardRateBps = o.getInt("standardRateBps"),
                incentiveBaselineThresholdSen = o.getLong("incentiveBaselineThresholdSen"),
                incentiveRateBps = o.getInt("incentiveRateBps"),
                createdAtEpochMs = o.getLong("createdAtEpochMs")
            )
        },
        dailySalesRecords = json.optArrayObjects("dailySalesRecords").map { o ->
            DailySalesRecordEntity(
                id = o.getLong("id"),
                recordDateEpochDay = o.getLong("recordDateEpochDay"),
                grossSalesSen = o.getLong("grossSalesSen"),
                netSalesSen = o.getLong("netSalesSen"),
                unitsSold = o.optInt("unitsSold", 0),
                notes = o.optString("notes", ""),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                grossProfitSen = o.optLong("grossProfitSen", 0L)
            )
        },
        savingsGoals = json.optArrayObjects("savingsGoals").map { o ->
            SavingsGoalEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                targetAmountSen = o.getLong("targetAmountSen"),
                targetDateEpochDay = o.optNullableLong("targetDateEpochDay"),
                description = o.optString("description", ""),
                iconKey = o.optString("iconKey", "shield"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.AQUA),
                status = o.optEnum("status", SavingsGoalStatus.ACTIVE),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
            )
        },
        savingsAllocations = json.optArrayObjects("savingsAllocations").map { o ->
            SavingsAllocationEntity(
                id = o.getLong("id"),
                goalId = o.getLong("goalId"),
                walletId = o.getLong("walletId"),
                allocatedSen = o.getLong("allocatedSen"),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
            )
        },
        savingsMovements = json.optArrayObjects("savingsMovements").map { o ->
            SavingsMovementEntity(
                id = o.getLong("id"),
                goalId = o.getLong("goalId"),
                sourceWalletId = o.optNullableLong("sourceWalletId"),
                targetWalletId = o.optNullableLong("targetWalletId"),
                relatedGoalId = o.optNullableLong("relatedGoalId"),
                type = o.optEnum("type", SavingsMovementType.ALLOCATE),
                amountSen = o.getLong("amountSen"),
                linkedTransactionId = o.optNullableLong("linkedTransactionId"),
                note = o.optString("note", ""),
                occurredAtEpochMs = o.getLong("occurredAtEpochMs"),
                submissionId = o.optNullableString("submissionId")
            )
        },
        budgets = json.optArrayObjects("budgets").map { o ->
            BudgetEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                scope = o.optEnum("scope", BudgetScope.OVERALL),
                periodType = o.optEnum("periodType", BudgetPeriodType.MONTHLY),
                yearMonth = o.optString("yearMonth", ""),
                salaryCycleId = o.optNullableLong("salaryCycleId"),
                categoryId = o.optNullableLong("categoryId"),
                categoryName = o.optString("categoryName", ""),
                limitSen = o.getLong("limitSen"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.SKY_BLUE),
                note = o.optString("note", ""),
                isArchived = o.optBoolean("isArchived", false),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
            )
        },
        payLaterPlans = json.optArrayObjects("payLaterPlans").map { o ->
            PayLaterPlanEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                providerName = o.getString("providerName"),
                purchaseAmountSen = o.getLong("purchaseAmountSen"),
                feeOrInterestSen = o.optLong("feeOrInterestSen", 0L),
                paidSen = o.optLong("paidSen", 0L),
                instalmentCount = o.optInt("instalmentCount", 1),
                firstDueDateEpochDay = o.getLong("firstDueDateEpochDay"),
                nextDueDateEpochDay = o.getLong("nextDueDateEpochDay"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.PEACH),
                note = o.optString("note", ""),
                isArchived = o.optBoolean("isArchived", false),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
            )
        },
        debts = json.optArrayObjects("debts").map { o ->
            DebtEntity(
                id = o.getLong("id"),
                direction = o.optEnum("direction", DebtDirection.I_OWE),
                counterpartyName = o.getString("counterpartyName"),
                title = o.optString("title", ""),
                originalAmountSen = o.getLong("originalAmountSen"),
                paidSen = o.optLong("paidSen", 0L),
                dueDateEpochDay = o.optNullableLong("dueDateEpochDay"),
                accentToken = o.optEnum("accentToken", PrismaticAccentToken.SKY_BLUE),
                note = o.optString("note", ""),
                isArchived = o.optBoolean("isArchived", false),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L),
                scheduledRepaymentSen = o.optNullableLong("scheduledRepaymentSen")
            )
        },
        obligationPayments = json.optArrayObjects("obligationPayments").map { o ->
            ObligationPaymentEntity(
                id = o.getLong("id"),
                obligationType = o.optEnum("obligationType", ObligationType.COMMITMENT),
                obligationId = o.getLong("obligationId"),
                walletId = o.getLong("walletId"),
                linkedTransactionId = o.optNullableLong("linkedTransactionId"),
                amountSen = o.getLong("amountSen"),
                occurrenceEpochDay = o.optNullableLong("occurrenceEpochDay"),
                paidAtEpochMs = o.getLong("paidAtEpochMs"),
                note = o.optString("note", ""),
                submissionId = o.optNullableString("submissionId")
            )
        },
        transactions = json.optArrayObjects("transactions").map { o ->
            TransactionEntity(
                id = o.getLong("id"),
                walletId = o.getLong("walletId"),
                targetWalletId = o.optNullableLong("targetWalletId"),
                type = o.optEnum("type", TransactionType.EXPENSE),
                amountSen = o.getLong("amountSen"),
                category = o.optString("category", "General"),
                title = o.optString("title", ""),
                note = o.optString("note", ""),
                commitmentId = o.optNullableLong("commitmentId"),
                occurredAtEpochMs = o.getLong("occurredAtEpochMs"),
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                categoryId = o.optNullableLong("categoryId"),
                submissionId = o.optNullableString("submissionId")
            )
        },
        notificationHistory = json.optArrayObjects("notificationHistory").map { o ->
            NotificationHistoryEntity(
                id = o.getLong("id"),
                dedupKey = o.getString("dedupKey"),
                notificationId = o.getInt("notificationId"),
                category = o.optString("category", NotificationCategory.COMMITMENTS.name),
                entityId = o.getLong("entityId"),
                targetEpochDay = o.getLong("targetEpochDay"),
                title = o.getString("title"),
                message = o.getString("message"),
                amountSen = o.optNullableLong("amountSen"),
                isOverdue = o.optBoolean("isOverdue", false),
                isRead = o.optBoolean("isRead", false),
                deepLink = o.optString("deepLink", "PLANNING_COMMITMENTS"),
                deliveredAtEpochMs = o.getLong("deliveredAtEpochMs")
            )
        }
    )

    private fun encodePreferences(p: PortablePreferencesBackup): JSONObject = JSONObject().apply {
        val a = p.appSettings
        put("themeMode", a.themeMode.name)
        put("glassIntensity", a.glassIntensity.name)
        put("hapticFeedbackEnabled", a.hapticFeedbackEnabled)
        put("reduceMotion", a.reduceMotion)
        put("animationIntensity", a.animationIntensity.name)
        put("hiddenDashboardSections", JSONArray(a.hiddenDashboardSections.map { it.name }))
        put("dashboardSectionOrder", JSONArray(a.dashboardSectionOrder.map { it.name }))
        putNullable("defaultWalletId", a.defaultWalletId)
        put("defaultBudgetPeriodType", a.defaultBudgetPeriodType.name)
        put("autoAdvanceRecurringCommitments", a.autoAdvanceRecurringCommitments)
        put("commissionTrackingEnabled", a.commissionTrackingEnabled)
        put("commissionFormula", JSONObject().apply {
            put("formulaName", a.commissionFormulaConfig.formulaName)
            put("isConfigured", a.commissionFormulaConfig.isConfigured)
            put("isEnabled", a.commissionFormulaConfig.isEnabled)
            put("minimumSalesThresholdSen", a.commissionFormulaConfig.minimumSalesThresholdSen)
            put("rateBps", a.commissionFormulaConfig.rateBps)
            put("flatBonusSen", a.commissionFormulaConfig.flatBonusSen)
        })

        val n = p.notificationSettings
        put("notifications", JSONObject().apply {
            put("masterEnabled", n.masterEnabled)
            put("enabledCategories", JSONArray(n.enabledCategories.map { it.name }))
            put("enabledOffsets", JSONArray(n.enabledOffsets.map { it.name }))
            put("enabledBudgetThresholds", JSONArray(n.enabledBudgetThresholds.map { it.name }))
            put("overdueEnabled", n.overdueEnabled)
            put("reminderHour", n.reminderHour)
            put("reminderMinute", n.reminderMinute)
            put("hideLockScreenAmounts", n.hideLockScreenAmounts)
        })

        val sec = p.privacyPreferences
        put("privacy", JSONObject().apply {
            put("hideSensitiveBalances", sec.hideSensitiveBalances)
            put("privacyModeEnabled", sec.privacyModeEnabled)
            put("alwaysMaskWidgetAmounts", sec.alwaysMaskWidgetAmounts)
        })
    }

    private fun decodePreferences(json: JSONObject): PortablePreferencesBackup {
        val hiddenSet = json.optStringList("hiddenDashboardSections")
            .mapNotNull { name -> DashboardSectionKey.entries.firstOrNull { it.name == name } }
            .toSet()
        val parsedOrder = json.optStringList("dashboardSectionOrder")
            .mapNotNull { name -> DashboardSectionKey.entries.firstOrNull { it.name == name } }
            .distinct()
        val fullOrder = parsedOrder + DashboardSectionKey.entries.filterNot { it in parsedOrder }

        val cfJson = json.optJSONObject("commissionFormula")
        val formulaConfig = if (cfJson == null) {
            CommissionFormulaConfig()
        } else {
            CommissionFormulaConfig(
                formulaName = cfJson.optString("formulaName", ""),
                isConfigured = cfJson.optBoolean("isConfigured", false),
                isEnabled = cfJson.optBoolean("isEnabled", false),
                minimumSalesThresholdSen = cfJson.optLong("minimumSalesThresholdSen", 0L),
                rateBps = cfJson.optInt("rateBps", 0),
                flatBonusSen = cfJson.optLong("flatBonusSen", 0L)
            )
        }

        val appSettings = AppSettingsSnapshot(
            themeMode = json.optEnum("themeMode", ThemeMode.SYSTEM),
            glassIntensity = json.optEnum("glassIntensity", GlassIntensity.BALANCED),
            hapticFeedbackEnabled = json.optBoolean("hapticFeedbackEnabled", true),
            reduceMotion = json.optBoolean("reduceMotion", false),
            animationIntensity = json.optEnum("animationIntensity", AnimationIntensity.BALANCED),
            hiddenDashboardSections = hiddenSet,
            dashboardSectionOrder = fullOrder,
            defaultWalletId = json.optNullableLong("defaultWalletId"),
            defaultBudgetPeriodType = json.optEnum("defaultBudgetPeriodType", BudgetPeriodType.MONTHLY),
            autoAdvanceRecurringCommitments = json.optBoolean("autoAdvanceRecurringCommitments", true),
            commissionTrackingEnabled = json.optBoolean("commissionTrackingEnabled", true),
            commissionFormulaConfig = formulaConfig
        )

        val nJson = json.optJSONObject("notifications")
        val notifSettings = if (nJson == null) {
            NotificationSettingsSnapshot()
        } else {
            val cats = if (nJson.has("enabledCategories")) {
                nJson.optStringList("enabledCategories")
                    .mapNotNull { name -> NotificationCategory.entries.firstOrNull { it.name == name } }
                    .toSet()
            } else NotificationCategory.DEFAULT_ENABLED
            val offsets = if (nJson.has("enabledOffsets")) {
                nJson.optStringList("enabledOffsets")
                    .mapNotNull { name -> ReminderOffset.entries.firstOrNull { it.name == name } }
                    .toSet()
            } else ReminderOffset.entries.toSet()
            val thresholds = if (nJson.has("enabledBudgetThresholds")) {
                nJson.optStringList("enabledBudgetThresholds")
                    .mapNotNull { name -> BudgetAlertThreshold.entries.firstOrNull { it.name == name } }
                    .toSet()
            } else BudgetAlertThreshold.entries.toSet()

            NotificationSettingsSnapshot(
                masterEnabled = nJson.optBoolean("masterEnabled", true),
                enabledCategories = cats,
                enabledOffsets = offsets,
                enabledBudgetThresholds = thresholds,
                overdueEnabled = nJson.optBoolean("overdueEnabled", true),
                reminderHour = nJson.optInt("reminderHour", 9).coerceIn(0, 23),
                reminderMinute = nJson.optInt("reminderMinute", 0).coerceIn(0, 59),
                hideLockScreenAmounts = nJson.optBoolean("hideLockScreenAmounts", true)
            )
        }

        val pJson = json.optJSONObject("privacy")
        val privacyPrefs = if (pJson == null) {
            PortablePrivacyPreferences()
        } else {
            PortablePrivacyPreferences(
                hideSensitiveBalances = pJson.optBoolean("hideSensitiveBalances", false),
                privacyModeEnabled = pJson.optBoolean("privacyModeEnabled", false),
                alwaysMaskWidgetAmounts = pJson.optBoolean("alwaysMaskWidgetAmounts", false)
            )
        }

        return PortablePreferencesBackup(
            appSettings = appSettings,
            notificationSettings = notifSettings,
            privacyPreferences = privacyPrefs
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T {
        val raw = optNullableString(key) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private fun JSONObject.optArrayObjects(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                add(arr.getJSONObject(i))
            }
        }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                add(arr.getString(i))
            }
        }
    }
}
