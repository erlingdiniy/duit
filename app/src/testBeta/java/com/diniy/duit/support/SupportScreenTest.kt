package com.diniy.duit.support

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupportScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun realQr_isBundledByteForByte_andThePageIsAvailable() {
        val res = supportQrResource(app)
        assertNotEquals(0, res)
        assertTrue(isSupportPageAvailable(app))
        val bundled = app.resources.openRawResource(res).use { it.readBytes() }
        val source = File("src/beta/res/raw/support_duitnow_qr.png").readBytes()
        assertArrayEquals(source, bundled) // res/raw is never re-encoded, so the QR stays scannable
    }

    @Test
    fun withRealQr_saveAndShareAreEnabled_inLightAndDark() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val qr = supportQrResource(app)
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            activity.setContent { DuitTheme(themeMode = mode) { SupportContent(qrRes = qr, onSave = { true }, onShare = {}, onClose = {}) } }
            compose.onNodeWithText("Support duit. 💛").assertExists()
            compose.onNodeWithContentDescription("DuitNow QR code for supporting duit.").assertExists()
            compose.onNodeWithText("Save QR").assertIsEnabled()
            compose.onNodeWithText("Share").assertIsEnabled()
        }
    }

    @Test
    fun placeholder_withoutQr_disablesSaveAndShare() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var saves = 0
        activity.setContent { DuitTheme { SupportContent(qrRes = 0, onSave = { saves++; true }, onShare = {}, onClose = {}) } }
        compose.onNodeWithText("QR code coming soon").assertExists()
        compose.onNodeWithText("100% optional. duit. stays free!").assertExists()
        compose.onNodeWithText("Save QR").assertIsNotEnabled()
        compose.onNodeWithText("Share").assertIsNotEnabled()
        assertTrue(saves == 0 && !saveSupportQr(app, 0))
    }
}
