package com.diniy.duit.feature.receipts

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.receipt.ReceiptFilterTab
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptSearchFilter
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptVaultViewModelTest {

    @get:Rule val rule = createEmptyComposeRule()

    private val sampleReceipts = listOf(
        ReceiptRecord(
            id = 1L,
            transactionId = 10L,
            merchant = "Klinik Mediviron",
            dateEpochDay = 20400L,
            amountSen = 8550L,
            category = "Medical",
            note = "Annual checkup",
            taxTag = ReceiptTaxTag.MEDICAL,
            fileRelativePath = "receipt_1.jpg",
            mimeType = "image/jpeg"
        ),
        ReceiptRecord(
            id = 2L,
            transactionId = null,
            merchant = "IKEA Damansara",
            dateEpochDay = 20401L,
            amountSen = 12000L,
            category = "Home",
            note = "Desk lamp warranty",
            taxTag = ReceiptTaxTag.NONE,
            fileRelativePath = "receipt_2.pdf",
            mimeType = "application/pdf"
        )
    )

    @Test
    fun searchAndFilter_handlesTabsMerchantTaxTagAndAmount() {
        assertEquals(2, ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.ALL, "").size)
        assertEquals(listOf(1L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.LINKED, "").map { it.id })
        assertEquals(listOf(2L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.STANDALONE, "").map { it.id })
        assertEquals(listOf(1L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.TAX_TAGGED, "").map { it.id })
        assertEquals(listOf(1L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.ALL, "85.50").map { it.id })
        assertEquals(listOf(2L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.ALL, "warranty").map { it.id })
        assertEquals(listOf(1L), ReceiptSearchFilter.filter(sampleReceipts, ReceiptFilterTab.ALL, "medical").map { it.id })
    }

    @Test
    fun parseOptionalRinggitToSen_usesExactIntegerMath() {
        assertNull(parseOptionalRinggitToSen(""))
        assertNull(parseOptionalRinggitToSen("abc"))
        assertNull(parseOptionalRinggitToSen("-5.00"))
        assertEquals(1250L, parseOptionalRinggitToSen("12.5"))
        assertEquals(1205L, parseOptionalRinggitToSen("12.05"))
        assertEquals(10000L, parseOptionalRinggitToSen("100"))
        assertEquals(1999L, parseOptionalRinggitToSen("19.99"))
    }

    @Test
    fun screen_masksAmountsInPrivacyMode_andRendersInDarkLargeFont() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val storage = ReceiptFileStorage(File(activity.cacheDir, "ui_receipt_test"))
        val state = ReceiptVaultUiState(
            isLoading = false,
            allReceipts = sampleReceipts,
            filteredReceipts = sampleReceipts
        )
        activity.setContent {
            CompositionLocalProvider(
                LocalPrivacySettings provides PrivacySettings(privacyModeEnabled = true),
                LocalDensity provides Density(density = 2f, fontScale = 2f)
            ) {
                DuitTheme(themeMode = ThemeMode.DARK) {
                    ReceiptVaultScreen(
                        state = state,
                        fileStorage = storage,
                        onSelectTab = {},
                        onSearchQueryChange = {},
                        onSaveReceipt = { _, _, _ -> },
                        onLinkTransaction = { _, _ -> },
                        onDeleteReceipt = { _, _ -> },
                        onDismissError = {},
                        onClose = {}
                    )
                }
            }
        }
        rule.onNodeWithText("Receipt Vault").assertIsDisplayed()
        rule.onNodeWithText("Klinik Mediviron").assertExists()
        rule.onNodeWithText(PrivacyMask.MASKED_MYR, useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Add receipt").assertExists()
        rule.onNodeWithText("RM 85.50").assertDoesNotExist()
    }
}
