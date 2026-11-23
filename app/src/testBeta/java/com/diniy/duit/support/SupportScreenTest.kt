package com.diniy.duit.support

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun supportPage_isAvailable_andPersonalQrIsNotBundled() {
        assertTrue(isSupportPageAvailable(app))
        assertEquals(0, app.resources.getIdentifier("support_duitnow_qr", "raw", app.packageName))
        assertFalse(File("src/beta/res/raw/support_duitnow_qr.png").exists())
    }

    @Test
    fun supportScreen_showsTemporarilyUnavailableMessage_withoutQrOrRecipientDetails_inLightAndDark() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var closed = 0
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            activity.setContent { DuitTheme(themeMode = mode) { SupportContent(onClose = { closed++ }) } }
            compose.onNodeWithText("Support duit. 💛").assertExists()
            compose.onNodeWithText("Donations are temporarily unavailable").assertExists()
            compose.onNodeWithText("100% optional. duit. stays free!").assertExists()
            compose.onNodeWithText("Close").assertExists()
            compose.onAllNodesWithText("DuitNow", substring = true, ignoreCase = true).assertCountEquals(0)
            compose.onAllNodesWithText("TNG", substring = true, ignoreCase = true).assertCountEquals(0)
            compose.onAllNodesWithText("recipient", substring = true, ignoreCase = true).assertCountEquals(0)
            compose.onAllNodesWithText("Save QR", substring = true, ignoreCase = true).assertCountEquals(0)
        }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        compose.onNodeWithText("Close").performClick()
        assertEquals(1, closed)
    }
}
