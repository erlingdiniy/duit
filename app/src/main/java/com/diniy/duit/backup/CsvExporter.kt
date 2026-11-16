package com.diniy.duit.backup

import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.data.local.dao.LedgerDatabaseSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Unencrypted RFC-4180 CSV exporter for transactions and financial records.
 */
object CsvExporter {

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun exportTransactionsCsv(
        snapshot: LedgerDatabaseSnapshot,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val walletsById = snapshot.wallets.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine(
            "ID,Date,Time,Type,Title,Category,Amount_MYR,Amount_Sen,Wallet,Target_Wallet,Commitment_ID,Note"
        )
        snapshot.transactions
            .sortedByDescending { it.occurredAtEpochMs }
            .forEach { tx ->
                val zdt = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zoneId)
                val walletName = walletsById[tx.walletId]?.name ?: "Wallet #${tx.walletId}"
                val targetName = tx.targetWalletId?.let { walletsById[it]?.name ?: "Wallet #$it" } ?: ""
                sb.appendRow(
                    tx.id.toString(),
                    zdt.toLocalDate().format(DATE_FMT),
                    zdt.toLocalTime().format(TIME_FMT),
                    tx.type.name,
                    tx.title,
                    tx.category,
                    MyrFormatter.format(tx.amountSen),
                    tx.amountSen.toString(),
                    walletName,
                    targetName,
                    tx.commitmentId?.toString().orEmpty(),
                    tx.note
                )
            }
        return sb.toString()
    }

    fun exportFullLedgerCsv(
        snapshot: LedgerDatabaseSnapshot,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val walletsById = snapshot.wallets.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine(
            "Record_Type,ID,Date,Time,Title_Or_Name,Category_Or_Provider,Status_Or_Type,Amount_MYR,Amount_Sen,Secondary_Amount_MYR,Wallet,Note"
        )

        snapshot.wallets.forEach { w ->
            sb.appendRow(
                "WALLET",
                w.id.toString(),
                "",
                "",
                w.name,
                w.type.name,
                if (w.isArchived) "ARCHIVED" else if (w.isExcludedFromSafeToSpend) "PROTECTED" else "ACTIVE",
                MyrFormatter.format(w.balanceSen),
                w.balanceSen.toString(),
                "",
                w.name,
                ""
            )
        }

        snapshot.transactions.sortedByDescending { it.occurredAtEpochMs }.forEach { tx ->
            val zdt = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zoneId)
            val walletName = walletsById[tx.walletId]?.name ?: "Wallet #${tx.walletId}"
            sb.appendRow(
                "TRANSACTION",
                tx.id.toString(),
                zdt.toLocalDate().format(DATE_FMT),
                zdt.toLocalTime().format(TIME_FMT),
                tx.title,
                tx.category,
                tx.type.name,
                MyrFormatter.format(tx.amountSen),
                tx.amountSen.toString(),
                "",
                walletName,
                tx.note
            )
        }

        snapshot.commitments.forEach { c ->
            sb.appendRow(
                "COMMITMENT",
                c.id.toString(),
                LocalDate.ofEpochDay(c.dueDateEpochDay).format(DATE_FMT),
                "",
                c.title,
                c.category,
                c.status.name,
                MyrFormatter.format(c.amountSen),
                c.amountSen.toString(),
                MyrFormatter.format(c.paidSen),
                c.walletId?.let { walletsById[it]?.name.orEmpty() }.orEmpty(),
                c.note
            )
        }

        snapshot.payLaterPlans.forEach { p ->
            val total = p.purchaseAmountSen + p.feeOrInterestSen
            sb.appendRow(
                "PAYLATER",
                p.id.toString(),
                LocalDate.ofEpochDay(p.nextDueDateEpochDay).format(DATE_FMT),
                "",
                p.title,
                p.providerName,
                if (p.isArchived) "ARCHIVED" else "${p.instalmentCount}_INSTALMENTS",
                MyrFormatter.format(total),
                total.toString(),
                MyrFormatter.format(p.paidSen),
                "",
                p.note
            )
        }

        snapshot.debts.forEach { d ->
            val dateStr = d.dueDateEpochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FMT) }.orEmpty()
            sb.appendRow(
                "DEBT",
                d.id.toString(),
                dateStr,
                "",
                d.counterpartyName,
                d.title,
                d.direction.name,
                MyrFormatter.format(d.originalAmountSen),
                d.originalAmountSen.toString(),
                MyrFormatter.format(d.paidSen),
                "",
                d.note
            )
        }

        snapshot.savingsGoals.forEach { g ->
            val dateStr = g.targetDateEpochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FMT) }.orEmpty()
            val saved = snapshot.savingsAllocations.filter { it.goalId == g.id }.sumOf { it.allocatedSen }
            sb.appendRow(
                "SAVINGS_GOAL",
                g.id.toString(),
                dateStr,
                "",
                g.name,
                "",
                g.status.name,
                MyrFormatter.format(g.targetAmountSen),
                g.targetAmountSen.toString(),
                MyrFormatter.format(saved),
                "",
                g.description
            )
        }

        snapshot.budgets.forEach { b ->
            sb.appendRow(
                "BUDGET",
                b.id.toString(),
                b.yearMonth,
                "",
                b.name,
                b.categoryName,
                "${b.scope.name}_${b.periodType.name}",
                MyrFormatter.format(b.limitSen),
                b.limitSen.toString(),
                "",
                "",
                b.note
            )
        }

        snapshot.commissions.forEach { c ->
            sb.appendRow(
                "COMMISSION",
                c.id.toString(),
                LocalDate.ofEpochDay(c.earnedDateEpochDay).format(DATE_FMT),
                "",
                c.title,
                c.sourceLabel,
                c.status.name,
                MyrFormatter.format(c.amountSen),
                c.amountSen.toString(),
                MyrFormatter.format(c.receivedNetSen),
                "",
                c.notes
            )
        }

        return sb.toString()
    }

    private fun StringBuilder.appendRow(vararg cells: String) {
        appendLine(cells.joinToString(",") { escapeCsv(it) })
    }

    fun escapeCsv(raw: String): String {
        val needsQuoting = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        val escaped = raw.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }
}
