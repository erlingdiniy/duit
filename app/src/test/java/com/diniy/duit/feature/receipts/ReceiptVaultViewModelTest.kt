package com.diniy.duit.feature.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory

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

    @Test
    fun detailSheet_displaysStoredJpegPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val dir = File(activity.cacheDir, "ui_receipt_preview_${System.nanoTime()}").apply { mkdirs() }
        try {
            val storage = ReceiptFileStorage(dir)
            val tempCam = File(dir, "cam_preview.jpg")
            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            try {
                tempCam.outputStream().use { out -> assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)) }
            } finally {
                bmp.recycle()
            }
            val stored = storage.importCameraFile(tempCam)
            val jpegReceipt = sampleReceipts.first().copy(fileRelativePath = stored.relativePath, mimeType = stored.mimeType)
            activity.setContent {
                DuitTheme(themeMode = ThemeMode.LIGHT) {
                    ReceiptDetailSheet(
                        receipt = jpegReceipt,
                        linkedTransaction = null,
                        linkableTransactions = emptyList(),
                        fileStorage = storage,
                        onEdit = {},
                        onLinkTransaction = {},
                        onDelete = {},
                        onDismiss = {}
                    )
                }
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                rule.onAllNodesWithContentDescription("Klinik Mediviron receipt preview")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithContentDescription("Klinik Mediviron receipt preview").assertIsDisplayed()
            rule.onNodeWithText("Attached file isn't available on this device.").assertDoesNotExist()
        } finally {
            controller.pause().stop().destroy()
            dir.deleteRecursively()
        }
    }

    @Test
    fun detailSheet_showsFallbackForMissingPdfAndCorruptJpeg() {
        val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "ui_receipt_fallback_${System.nanoTime()}")
            .apply { mkdirs() }
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        try {
            val storage = ReceiptFileStorage(dir)
            assertTrue(storage.writeRestoredFile("corrupt_receipt.jpg", "corrupted-jpeg-bytes".toByteArray()))
            val missingPdfReceipt = sampleReceipts[1].copy(fileRelativePath = "missing_doc.pdf", mimeType = "application/pdf")
            val corruptJpegReceipt = ReceiptRecord(
                id = 3L,
                transactionId = null,
                merchant = "Corrupt Kedai",
                dateEpochDay = 20402L,
                amountSen = 2500L,
                category = "Food",
                note = "",
                taxTag = ReceiptTaxTag.NONE,
                fileRelativePath = "corrupt_receipt.jpg",
                mimeType = "image/jpeg"
            )

            for (target in listOf(missingPdfReceipt, corruptJpegReceipt)) {
                val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
                try {
                    controller.get().setContent {
                        DuitTheme(themeMode = ThemeMode.LIGHT) {
                            ReceiptDetailSheet(
                                receipt = target,
                                linkedTransaction = null,
                                linkableTransactions = emptyList(),
                                fileStorage = storage,
                                onEdit = {},
                                onLinkTransaction = {},
                                onDelete = {},
                                onDismiss = {}
                            )
                        }
                    }
                    rule.waitUntil(timeoutMillis = 5_000) {
                        Shadows.shadowOf(Looper.getMainLooper()).idle()
                        rule.onAllNodesWithText("Attached file isn't available on this device.")
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                    rule.onNodeWithText("Attached file isn't available on this device.").assertIsDisplayed()
                } finally {
                    controller.pause().stop().destroy()
                }
            }
        } finally {
            ShadowBitmapFactory.setAllowInvalidImageData(true)
            dir.deleteRecursively()
        }
    }
}
