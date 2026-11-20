package com.diniy.duit.feature.activity

import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySearchTest {
    private val today = LocalDate.of(2026, 3, 3)
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val wallets = listOf(
        Wallet(1, "Maybank", WalletType.BANK, 0, false, PrismaticAccentToken.AQUA, 0, 0),
        Wallet(2, "TNG eWallet", WalletType.EWALLET, 0, false, PrismaticAccentToken.AQUA, 0, 0)
    ).associateBy { it.id }
    private fun tx(id: Long, type: TransactionType, amount: Long, category: String, title: String, date: LocalDate,
                   wallet: Long = 1, target: Long? = null, note: String = "") =
        FinanceTransaction(id, wallet, target, type, amount, category, title, note, null, at(date), at(date))

    private val all = listOf(
        tx(1, TransactionType.EXPENSE, 2_480, "Food & Dining", "Kopitiam lunch", today),
        tx(2, TransactionType.INCOME, 520_000, "Salary", "Monthly salary", today.minusDays(2), note = "March pay"),
        tx(3, TransactionType.TRANSFER, 30_000, "Transfer", "Top up", today.minusDays(6), wallet = 1, target = 2),
        tx(4, TransactionType.EXPENSE, 9_900, "food & dining", "Groceries", LocalDate.of(2026, 2, 28)),
        tx(5, TransactionType.EXPENSE, 1_000, "Transport", "Parking", today.minusDays(7), wallet = 2)
    )
    private fun ids(type: ActivityFilter = ActivityFilter.ALL, q: String = "", extra: ActivityExtraFilters = ActivityExtraFilters()) =
        filterActivity(all, type, q, extra, wallets, today).map { it.id }

    @Test fun noFilters_returnsEverythingInOrder() = assertEquals(listOf(1L, 2, 3, 4, 5), ids())

    @Test fun typeFilter() {
        assertEquals(listOf(2L), ids(ActivityFilter.INCOME))
        assertEquals(listOf(3L), ids(ActivityFilter.TRANSFER))
    }

    @Test fun keyword_matchesTitleNoteWalletAndAmount_caseInsensitive() {
        assertEquals(listOf(1L), ids(q = "KOPITIAM"))
        assertEquals(listOf(2L), ids(q = "march"))
        assertEquals(listOf(3L, 5L), ids(q = "tng"))      // transfer target wallet and source wallet
        // Amount matching exists only in the public edition; the personal edition keeps its original search.
        val amountHits = com.diniy.duit.BuildConfig.PUBLIC_EDITION
        assertEquals(if (amountHits) listOf(1L) else emptyList(), ids(q = "24.80"))
        assertEquals(if (amountHits) listOf(2L) else emptyList(), ids(q = "5200"))
        assertEquals(emptyList<Long>(), ids(q = "nothing like this"))
    }

    @Test fun category_isCaseInsensitiveExactMatch() {
        assertEquals(listOf(1L, 4L), ids(extra = ActivityExtraFilters(category = "Food & Dining")))
        assertEquals(emptyList<Long>(), ids(extra = ActivityExtraFilters(category = "Food")))
    }

    @Test fun wallet_includesBothSidesOfATransfer() {
        assertEquals(listOf(3L, 5L), ids(extra = ActivityExtraFilters(walletId = 2)))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(extra = ActivityExtraFilters(walletId = 1)))
    }

    @Test fun dateRanges_respectMonthAndWindowBoundaries() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(extra = ActivityExtraFilters(dateRange = ActivityDateRange.LAST_7_DAYS))) // 25 Feb..3 Mar inclusive; 24 Feb excluded
        assertEquals(listOf(1L, 2L), ids(extra = ActivityExtraFilters(dateRange = ActivityDateRange.THIS_MONTH))) // 1 and 3 March
        assertEquals(listOf(3L, 4L, 5L), ids(extra = ActivityExtraFilters(dateRange = ActivityDateRange.LAST_MONTH))) // 24, 25 and 28 Feb
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(extra = ActivityExtraFilters(dateRange = ActivityDateRange.LAST_90_DAYS)))
        assertTrue(ActivityDateRange.LAST_MONTH.matches(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 15))) // year boundary
        assertFalse(ActivityDateRange.LAST_7_DAYS.matches(today.plusDays(1), today))
    }

    @Test fun filtersCombine_andNeverChangeRecords() {
        val before = all.toList()
        assertEquals(listOf(1L), ids(ActivityFilter.EXPENSE, "kopi", ActivityExtraFilters("Food & Dining", 1, ActivityDateRange.THIS_MONTH)))
        assertEquals(before, all)
        assertFalse(ActivityExtraFilters().isActive)
        assertTrue(ActivityExtraFilters(walletId = 1).isActive)
    }

    @Test fun staleWalletOrCategory_isClearedInsteadOfHidingEverything() {
        val stale = ActivityExtraFilters(category = "Gone", walletId = 99, dateRange = ActivityDateRange.THIS_MONTH)
        val kept = stale.sanitized(walletIds = wallets.keys, categoryOptions = listOf("Food & Dining"))
        assertEquals(ActivityExtraFilters(dateRange = ActivityDateRange.THIS_MONTH), kept)
        assertEquals("food & dining", ActivityExtraFilters(category = "food & dining").sanitized(wallets.keys, listOf("Food & Dining")).category)
    }

    @Test fun amountSearch_ignoresDeviceLocaleDigits() {
        if (!com.diniy.duit.BuildConfig.PUBLIC_EDITION) return
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG")) // Arabic-Indic digits
            assertEquals(listOf(1L), ids(q = "24.80"))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
