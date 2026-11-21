package com.diniy.duit.domain.tax

import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Review status for a user-logged tax relief item.
 * Only [READY] items count toward the "Ready (capped)" summary; [NEEDS_REVIEW] stays separate
 * and [EXCLUDED] is omitted from totals while remaining saved for reference.
 */
enum class TaxReliefReviewStatus(val label: String) {
    NEEDS_REVIEW("Needs review"),
    READY("Ready"),
    EXCLUDED("Excluded");

    companion object {
        fun fromStored(raw: String?): TaxReliefReviewStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NEEDS_REVIEW
    }
}

/**
 * Verified LHDN Malaysia Resident Individual tax relief category for a specific Year of Assessment.
 * All limits are stored in sen (Long) and never represent confirmed claims or tax advice.
 */
data class TaxReliefCategorySpec(
    val key: String,
    val title: String,
    val shortRule: String,
    val detailRule: String,
    val maxLimitSen: Long,
    val suggestedReceiptTags: Set<ReceiptTaxTag> = emptySet(),
    val accentGroup: TaxAccentGroup = TaxAccentGroup.MINT
)

enum class TaxAccentGroup {
    GOLD, MINT, SKY, CORAL
}

/**
 * User-logged tax relief item in My Tax Pocket.
 * Completely independent of wallet balances, budgets, and Safe to Spend.
 */
data class TaxReliefEntry(
    val id: Long = 0L,
    val assessmentYear: Int,
    val categoryKey: String,
    val title: String,
    val dateEpochDay: Long,
    /** Optional full purchase/receipt amount in sen (Long). */
    val totalSpentSen: Long? = null,
    /** User-verified eligible portion in sen (Long, must be > 0). */
    val eligibleAmountSen: Long,
    /** Optional link to Receipt Vault item; null for manual entries or after receipt deletion. */
    val receiptId: Long? = null,
    val reviewStatus: TaxReliefReviewStatus = TaxReliefReviewStatus.NEEDS_REVIEW,
    val note: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

data class TaxCategorySummary(
    val spec: TaxReliefCategorySpec,
    val entries: List<TaxReliefEntry>,
    val readyRawSen: Long,
    val readyCappedSen: Long,
    val pendingRawSen: Long,
    val excludedCount: Int,
    val remainingCapSen: Long,
    val overCapSen: Long
) {
    val progressRatio: Float
        get() = if (spec.maxLimitSen <= 0L) 0f else (readyCappedSen.toFloat() / spec.maxLimitSen.toFloat()).coerceIn(0f, 1f)
    val entryCount: Int get() = entries.size
}

data class TaxYearSummary(
    val assessmentYear: Int,
    val categories: List<TaxCategorySummary>,
    val totalReadyCappedSen: Long,
    val totalPendingSen: Long,
    val totalCapSen: Long,
    val totalEntryCount: Int,
    val needsReviewCount: Int,
    val unlinkedTaggedReceipts: List<ReceiptRecord>
)

/**
 * Verified LHDN Malaysia Resident Individual tax relief catalog per Year of Assessment (YA 2024–2026).
 * Source: LHDN (hasil.gov.my) & Malaysia National Budgets 2024, 2025, 2026.
 * Personal/automatic reliefs (e.g. Individual RM9,000) are declared directly in LHDN MyTax.
 */
object TaxReliefCatalog {
    val supportedYears: List<Int> = listOf(2024, 2025, 2026)

    fun defaultYear(today: LocalDate = LocalDate.now()): Int =
        today.year.coerceIn(supportedYears.first(), supportedYears.last())

    fun categoriesForYear(ya: Int): List<TaxReliefCategorySpec> {
        val list = mutableListOf(
            TaxReliefCategorySpec(
                key = "LIFESTYLE",
                title = "Lifestyle",
                shortRule = "Books, devices, internet, courses",
                detailRule = "Books, journals, PC, smartphone, tablet, internet subscription, or self-enhancement courses for self, spouse, or child.",
                maxLimitSen = 250_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.LIFESTYLE),
                accentGroup = TaxAccentGroup.CORAL
            ),
            TaxReliefCategorySpec(
                key = "SPORTS",
                title = "Sports & Gym",
                shortRule = "Sports gear, gym, events",
                detailRule = "Separate RM 1,000 lifestyle relief for sports equipment, facility rental/entry, competition fees, and gym or sports training.",
                maxLimitSen = 100_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.LIFESTYLE),
                accentGroup = TaxAccentGroup.CORAL
            ),
            TaxReliefCategorySpec(
                key = "MEDICAL_SELF_FAMILY",
                title = "Medical Expenses",
                shortRule = if (ya >= 2026) "Medical, dental, NPRA vaccines" else "Medical, dental, check-up, vaccines",
                detailRule = if (ya >= 2026) {
                    "Serious diseases, fertility, dental (sub-cap RM 1,000), NPRA-registered vaccines (sub-cap RM 1,000), health check-up/mental health/self-test (sub-cap RM 1,000), and child learning disability assessment/treatment (expanded to RM 10,000 within medical limit)."
                } else if (ya == 2025) {
                    "Serious diseases, fertility, dental (sub-cap RM 1,000), eligible vaccines (sub-cap RM 1,000), health check-up/mental health/self-test (sub-cap RM 1,000), and child learning disability treatment (sub-cap RM 6,000)."
                } else {
                    "Serious diseases, fertility, dental (sub-cap RM 1,000), eligible vaccines (sub-cap RM 1,000), health check-up/mental health/self-test (sub-cap RM 1,000), and child learning disability treatment (sub-cap RM 4,000)."
                },
                maxLimitSen = 1_000_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.MEDICAL),
                accentGroup = TaxAccentGroup.MINT
            ),
            TaxReliefCategorySpec(
                key = "PARENTS_MEDICAL",
                title = "Parents Medical",
                shortRule = if (ya >= 2025) "Parents & grandparents medical" else "Parents medical & carer",
                detailRule = if (ya >= 2025) {
                    "Certified medical treatment, dental, special needs, carer expenses, and complete medical exam (sub-cap RM 1,000) for parents or grandparents."
                } else {
                    "Certified medical treatment, special needs, carer expenses, and complete medical exam (sub-cap RM 1,000) for parents."
                },
                maxLimitSen = 800_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.MEDICAL),
                accentGroup = TaxAccentGroup.MINT
            ),
            TaxReliefCategorySpec(
                key = "EDUCATION_SELF",
                title = "Self Education",
                shortRule = "Approved degrees & upskilling",
                detailRule = "Approved diploma/degree/Master/PhD fees for self; includes recognised upskilling or self-enhancement courses capped at RM 2,000 within RM 7,000.",
                maxLimitSen = 700_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.EDUCATION),
                accentGroup = TaxAccentGroup.SKY
            ),
            TaxReliefCategorySpec(
                key = "CHILDCARE",
                title = "Childcare Fees",
                shortRule = if (ya >= 2026) "Registered care up to age 12" else "Registered TASKA/TADIKA age ≤6",
                detailRule = if (ya >= 2026) {
                    "Fees paid to registered childcare centres, kindergartens, or JKM-registered after-school transit centres for children aged 12 and below (claimed by one parent)."
                } else {
                    "Fees paid to registered childcare centres (TASKA) or kindergartens (TADIKA) for children aged 6 and below (claimed by one parent)."
                },
                maxLimitSen = 300_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.CHILDCARE),
                accentGroup = TaxAccentGroup.GOLD
            ),
            TaxReliefCategorySpec(
                key = "SSPN",
                title = "SSPN Savings",
                shortRule = "Net annual SSPN deposit",
                detailRule = "Net annual savings deposited into Skim Simpanan Pendidikan Nasional (SSPN) for children (deposits minus withdrawals within the YA).",
                maxLimitSen = 800_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.EDUCATION),
                accentGroup = TaxAccentGroup.GOLD
            ),
            TaxReliefCategorySpec(
                key = "BREASTFEEDING",
                title = "Breastfeeding Gear",
                shortRule = "Mothers, child ≤2, every 2 YAs",
                detailRule = "Purchase of breastfeeding equipment for own use by working mothers with a child aged 2 and below (claimable once every 2 YAs).",
                maxLimitSen = 100_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.CHILDCARE),
                accentGroup = TaxAccentGroup.GOLD
            ),
            TaxReliefCategorySpec(
                key = "EDUCATION_MEDICAL_INSURANCE",
                title = "Medical Insurance",
                shortRule = "Education & medical policies",
                detailRule = if (ya >= 2025) {
                    "Education and medical insurance or Takaful premiums for self, spouse, or child. Limit raised from RM 3,000 to RM 4,000 starting YA 2025."
                } else {
                    "Education and medical insurance or Takaful premiums for self, spouse, or child (YA 2024 limit: RM 3,000)."
                },
                maxLimitSen = if (ya >= 2025) 400_000L else 300_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.INSURANCE),
                accentGroup = TaxAccentGroup.SKY
            ),
            TaxReliefCategorySpec(
                key = "LIFE_INSURANCE_EPF",
                title = "EPF & Life Insurance",
                shortRule = "EPF up to RM4k, life RM3k",
                detailRule = if (ya >= 2026) {
                    "Combined RM 7,000 cap (EPF/approved scheme up to RM 4,000 + Life Insurance/Family Takaful up to RM 3,000; YA 2026 expands life/Takaful to children). Check civil-servant rules on LHDN MyTax."
                } else {
                    "Combined RM 7,000 cap (EPF/approved scheme up to RM 4,000 + Life Insurance/Family Takaful up to RM 3,000). Check civil-servant rules on LHDN MyTax."
                },
                maxLimitSen = 700_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.INSURANCE),
                accentGroup = TaxAccentGroup.SKY
            ),
            TaxReliefCategorySpec(
                key = "PRS_ANNUITY",
                title = "PRS & Annuity",
                shortRule = "Deferred annuity & PRS",
                detailRule = "Contributions to Private Retirement Schemes (PRS) or deferred annuity schemes approved by Securities Commission Malaysia.",
                maxLimitSen = 300_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.INSURANCE),
                accentGroup = TaxAccentGroup.SKY
            ),
            TaxReliefCategorySpec(
                key = "SOCSO",
                title = "SOCSO / PERKESO",
                shortRule = "Statutory SOCSO & EIS",
                detailRule = "Employee contributions made to Social Security Organisation (PERKESO / SOCSO) and Employment Insurance System (EIS).",
                maxLimitSen = 35_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.INSURANCE),
                accentGroup = TaxAccentGroup.SKY
            ),
            TaxReliefCategorySpec(
                key = "EV_ECO_HOME",
                title = "EV & Eco Home",
                shortRule = when {
                    ya >= 2026 -> "EV charger, composter, home CCTV"
                    ya == 2025 -> "EV charger & food composter"
                    else -> "EV charging equipment"
                },
                detailRule = when {
                    ya >= 2026 -> "EV charging facilities, domestic food waste composting/shredding machine, or home CCTV installation (subject to LHDN YA frequency limits)."
                    ya == 2025 -> "EV charging facilities (installation, rental, hire-purchase) or domestic food waste composting machine for personal use."
                    else -> "Purchase, installation, rental, or hire-purchase of EV charging facilities for personal use."
                },
                maxLimitSen = 250_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.LIFESTYLE, ReceiptTaxTag.OTHER),
                accentGroup = TaxAccentGroup.MINT
            ),
            TaxReliefCategorySpec(
                key = "DISABLED_SUPPORT_EQUIPMENT",
                title = "Support Equipment",
                shortRule = "Disabled self or family gear",
                detailRule = "Basic supporting equipment purchased for disabled self, spouse, child, or parent.",
                maxLimitSen = 600_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.MEDICAL),
                accentGroup = TaxAccentGroup.MINT
            )
        )

        if (ya >= 2025) {
            list += TaxReliefCategorySpec(
                key = "FIRST_HOME_LOAN_INTEREST",
                title = "First Home Interest",
                shortRule = "RM7k (≤RM500k) or RM5k",
                detailRule = "Housing loan interest for first residential home (SPA signed 1 Jan 2025–31 Dec 2027, 3 consecutive YAs): up to RM 7,000 if home ≤RM 500,000, or RM 5,000 if >RM 500,000 to RM 750,000.",
                maxLimitSen = 700_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.OTHER),
                accentGroup = TaxAccentGroup.GOLD
            )
        }

        if (ya >= 2026) {
            list += TaxReliefCategorySpec(
                key = "DOMESTIC_TOURISM",
                title = "Local Tourism",
                shortRule = "Local attractions & culture",
                detailRule = "YA 2026 domestic tourism relief: entrance fees to local tourist attractions and tickets for local arts or cultural programmes.",
                maxLimitSen = 100_000L,
                suggestedReceiptTags = setOf(ReceiptTaxTag.LIFESTYLE, ReceiptTaxTag.OTHER),
                accentGroup = TaxAccentGroup.CORAL
            )
        }

        return list
    }

    fun categoryFor(ya: Int, key: String): TaxReliefCategorySpec? =
        categoriesForYear(ya).firstOrNull { it.key == key }

    /** Maps a Receipt Vault note tag to a sensible starting category suggestion (user must confirm). */
    fun suggestedCategoryKeyForTag(ya: Int, tag: ReceiptTaxTag): String {
        val cats = categoriesForYear(ya)
        return cats.firstOrNull { tag in it.suggestedReceiptTags }?.key ?: cats.first().key
    }
}

object TaxReliefCalculator {
    fun summarize(
        assessmentYear: Int,
        entries: List<TaxReliefEntry>,
        receipts: List<ReceiptRecord>
    ): TaxYearSummary {
        val specs = TaxReliefCatalog.categoriesForYear(assessmentYear)
        val yearEntries = entries.filter { it.assessmentYear == assessmentYear }
        val byCategory = yearEntries.groupBy { it.categoryKey }

        val categorySummaries = specs.map { spec ->
            val catEntries = byCategory[spec.key].orEmpty()
                .sortedWith(compareByDescending<TaxReliefEntry> { it.dateEpochDay }.thenByDescending { it.id })
            val readyRaw = catEntries
                .filter { it.reviewStatus == TaxReliefReviewStatus.READY }
                .sumOf { it.eligibleAmountSen.coerceAtLeast(0L) }
            val pendingRaw = catEntries
                .filter { it.reviewStatus == TaxReliefReviewStatus.NEEDS_REVIEW }
                .sumOf { it.eligibleAmountSen.coerceAtLeast(0L) }
            val excludedCount = catEntries.count { it.reviewStatus == TaxReliefReviewStatus.EXCLUDED }
            val readyCapped = minOf(readyRaw, spec.maxLimitSen)
            val remainingCap = (spec.maxLimitSen - readyCapped).coerceAtLeast(0L)
            val overCap = (readyRaw - spec.maxLimitSen).coerceAtLeast(0L)
            TaxCategorySummary(
                spec = spec,
                entries = catEntries,
                readyRawSen = readyRaw,
                readyCappedSen = readyCapped,
                pendingRawSen = pendingRaw,
                excludedCount = excludedCount,
                remainingCapSen = remainingCap,
                overCapSen = overCap
            )
        }

        val linkedReceiptIds = yearEntries.mapNotNull { it.receiptId }.toSet()
        val unlinkedTagged = receipts
            .filter { r ->
                r.isTaxTagged &&
                    r.id !in linkedReceiptIds &&
                    LocalDate.ofEpochDay(r.dateEpochDay).year == assessmentYear
            }
            .sortedByDescending { it.dateEpochDay }

        return TaxYearSummary(
            assessmentYear = assessmentYear,
            categories = categorySummaries,
            totalReadyCappedSen = categorySummaries.sumOf { it.readyCappedSen },
            totalPendingSen = categorySummaries.sumOf { it.pendingRawSen },
            totalCapSen = specs.sumOf { it.maxLimitSen },
            totalEntryCount = yearEntries.size,
            needsReviewCount = yearEntries.count { it.reviewStatus == TaxReliefReviewStatus.NEEDS_REVIEW },
            unlinkedTaggedReceipts = unlinkedTagged
        )
    }
}

/** Pure offline CSV generator for My Tax Pocket summary export. */
object TaxSummaryCsvBuilder {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildCsv(
        summary: TaxYearSummary,
        receiptsById: Map<Long, ReceiptRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Assessment Year,Category,LHDN Limit (RM),Item Title,Date,Total Spent (RM),Eligible Amount (RM),Review Status,Receipt Linked,Receipt File,Note")
        summary.categories.forEach { cat ->
            cat.entries.forEach { e ->
                val receipt = e.receiptId?.let { receiptsById[it] }
                val dateStr = LocalDate.ofEpochDay(e.dateEpochDay).format(dateFmt)
                sb.appendLine(
                    listOf(
                        "YA ${summary.assessmentYear}",
                        cat.spec.title,
                        formatSen(cat.spec.maxLimitSen),
                        e.title,
                        dateStr,
                        e.totalSpentSen?.let(::formatSen) ?: "",
                        formatSen(e.eligibleAmountSen),
                        e.reviewStatus.label,
                        if (e.receiptId != null) "Yes" else "No",
                        receipt?.originalFileName.orEmpty(),
                        e.note
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        }
        sb.appendLine()
        sb.appendLine("Summary Metric,Amount (RM)")
        sb.appendLine("Ready to file (capped by LHDN category limits),${formatSen(summary.totalReadyCappedSen)}")
        sb.appendLine("Needs review (not included in Ready total),${formatSen(summary.totalPendingSen)}")
        sb.appendLine("Note,\"Personal record only. Verify eligibility with LHDN MyTax before filing. Keep receipts for 7 years.\"")
        return sb.toString()
    }

    private fun formatSen(sen: Long): String {
        val abs = kotlin.math.abs(sen)
        val sign = if (sen < 0L) "-" else ""
        return String.format(Locale.ROOT, "%s%d.%02d", sign, abs / 100L, abs % 100L)
    }

    private fun csvEscape(raw: String): String {
        val cleaned = raw.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
