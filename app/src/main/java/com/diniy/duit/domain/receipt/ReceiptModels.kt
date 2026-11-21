package com.diniy.duit.domain.receipt

import java.util.Locale

/**
 * Optional personal organization tags for tax-season sorting.
 * Tax tags are user notes only and never represent confirmed or eligible tax claims.
 */
enum class ReceiptTaxTag(val label: String) {
    NONE("Not tagged"),
    MEDICAL("Medical"),
    LIFESTYLE("Lifestyle"),
    EDUCATION("Education"),
    CHILDCARE("Childcare"),
    INSURANCE("Insurance & EPF"),
    DONATION("Donation"),
    OTHER("Other tax note");

    companion object {
        fun fromStored(raw: String?): ReceiptTaxTag =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class ReceiptFilterTab(val label: String) {
    ALL("All"),
    LINKED("Linked"),
    STANDALONE("Standalone"),
    TAX_TAGGED("Tax-tagged")
}

data class ReceiptRecord(
    val id: Long = 0L,
    val transactionId: Long? = null,
    val merchant: String,
    val dateEpochDay: Long,
    /** Optional receipt amount in sen (Long). Never affects wallet balances. */
    val amountSen: Long? = null,
    val category: String = "",
    val note: String = "",
    val taxTag: ReceiptTaxTag = ReceiptTaxTag.NONE,
    /** Filename relative to app-private filesDir/receipts/, or blank if metadata-only. */
    val fileRelativePath: String = "",
    val mimeType: String = "",
    val originalFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    val isLinked: Boolean get() = transactionId != null
    val isTaxTagged: Boolean get() = taxTag != ReceiptTaxTag.NONE
    val hasAttachment: Boolean get() = fileRelativePath.isNotBlank()
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true)
}

/** Pure offline search and tab filter for Receipt Vault. */
object ReceiptSearchFilter {
    fun filter(
        receipts: List<ReceiptRecord>,
        tab: ReceiptFilterTab,
        query: String
    ): List<ReceiptRecord> {
        val tabFiltered = when (tab) {
            ReceiptFilterTab.ALL -> receipts
            ReceiptFilterTab.LINKED -> receipts.filter { it.isLinked }
            ReceiptFilterTab.STANDALONE -> receipts.filter { !it.isLinked }
            ReceiptFilterTab.TAX_TAGGED -> receipts.filter { it.isTaxTagged }
        }
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return tabFiltered
        return tabFiltered.filter { r ->
            r.merchant.lowercase(Locale.ROOT).contains(q) ||
                r.category.lowercase(Locale.ROOT).contains(q) ||
                r.note.lowercase(Locale.ROOT).contains(q) ||
                r.originalFileName.lowercase(Locale.ROOT).contains(q) ||
                (r.isTaxTagged && r.taxTag.label.lowercase(Locale.ROOT).contains(q)) ||
                (r.amountSen != null && formatAmountDigits(r.amountSen).contains(q))
        }
    }

    private fun formatAmountDigits(sen: Long): String {
        val abs = kotlin.math.abs(sen)
        return String.format(Locale.ROOT, "%d.%02d", abs / 100L, abs % 100L)
    }
}
