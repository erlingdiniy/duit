package com.diniy.duit.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.feature.settings.AboutLicensesPage
import com.diniy.duit.feature.settings.PrivacyDataScreen
import com.diniy.duit.feature.settings.SettingsHubRoute
import com.diniy.duit.feature.settings.activeLicenseNotices
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
class PrivacyDataScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val headings = listOf(
        "Where your data lives", "Backup & restore", "PIN & fingerprint", "When you send feedback",
        "Update checks", "Developer broadcasts", "Supporting duit.", "Managing & deleting your data"
    )

    @Test
    fun allSectionsRender_inLightAndDark_andLinksNavigate() {
        assertTrue(BuildConfig.PUBLIC_EDITION)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val routes = mutableListOf<SettingsHubRoute>()
            activity.setContent { DuitTheme(themeMode = mode) { PrivacyDataScreen(onBack = {}, onOpenRoute = { routes += it }) } }
            // Same activity for both themes keeps the saved scroll position, so scroll to each node first.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Privacy & Your Data"))
            compose.onNodeWithText("Privacy & Your Data").assertExists()
            headings.forEach { title ->
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
                compose.onNodeWithText(title).assertExists()
            }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Data Management"))
            compose.onNodeWithText("Data Management").performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(listOf(SettingsHubRoute.DATA_MANAGEMENT), routes)
        }
    }

    @Test
    fun publicSummary_doesNotClaimTheAppIsOffline() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DuitTheme { PrivacyDataScreen(onBack = {}, onOpenRoute = {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Privacy & Security summary"))
        // Buttons ignore taps in their first 600 ms of uptime (double-submit guard).
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        compose.onNodeWithText("Privacy & Security summary").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("goes online only to check for app updates", substring = true))
        compose.onNodeWithText("goes online only to check for app updates", substring = true).assertExists()
        compose.onNodeWithText("developer broadcast", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("saved receipts", substring = true, ignoreCase = true).assertExists()
        compose.onAllNodesWithText("doesn't ask for internet access", substring = true).assertCountEquals(0)
    }

    @Test
    fun licensesPage_includesFirebaseOnlyInPublicBuilds() {
        val publicNotices = activeLicenseNotices(publicEdition = true)
        val personalNotices = activeLicenseNotices(publicEdition = false)
        assertTrue(publicNotices.any { it.name == "Firebase Cloud Messaging SDK" })
        assertFalse(personalNotices.any { it.name.contains("Firebase", ignoreCase = true) })

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DuitTheme { AboutLicensesPage(onBack = {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Firebase Cloud Messaging SDK"))
        compose.onNodeWithText("Firebase Cloud Messaging SDK").assertExists()
    }
}
