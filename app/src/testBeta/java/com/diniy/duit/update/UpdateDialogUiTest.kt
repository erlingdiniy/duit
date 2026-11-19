package com.diniy.duit.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.robolectric.Robolectric
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.diniy.duit.core.ui.theme.DuitTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mock GitHub feed -> real release selection -> the dialog the update checker shows. Nothing is published. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDialogUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    // Built directly so no test activity has to be merged into the shipped beta manifest.
    private fun show(content: @Composable () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent { DuitTheme { content() } }
    }

    private val repo = "erlingdiniy/duit-beta"

    private fun feed(body: String) = JSONArray().put(
        JSONObject()
            .put("tag_name", "beta-4").put("draft", false).put("prerelease", true)
            .put("name", "duit. Beta 0.1.0-beta.4").put("body", body)
            .put("published_at", "2026-11-20T08:00:00Z")
            .put("assets", JSONArray().put(JSONObject()
                .put("name", "duit-beta-4.apk").put("state", "uploaded").put("size", 7_400_000)
                .put("browser_download_url", "https://github.com/$repo/releases/download/beta-4/duit-beta-4.apk")
                .put("digest", "sha256:" + "b".repeat(64))))
    )

    @Test
    fun detectedUpdate_showsCandyDialog_withRealVersionsAndCategorisedNotes() {
        val release = selectBetaRelease(feed("## New Features\n- Candy popup\n## Bug Fixes\n- Fixed a crash"), repo, installedCode = 3)!!
        var updates = 0
        var later = 0
        show { UpdateOfferDialog(BetaUpdateState.Offer(release), "0.1.0-beta.3", onUpdate = { updates++ }, onLater = { later++ }) }
        compose.onNodeWithText("A little update for you!").assertIsDisplayed()
        compose.onNodeWithText("Fresh improvements for duit.").assertIsDisplayed()
        compose.onNodeWithText("v0.1.0-beta.3").assertIsDisplayed()
        compose.onNodeWithText("v0.1.0-beta.4").assertExists()
        compose.onNodeWithText("Released 2026-11-20  ·  7.1 MB").assertIsDisplayed()
        compose.onNodeWithText("✨  What's New").assertExists()
        compose.onNodeWithText("🛠  Bug Fixes").assertExists()
        compose.onNodeWithText("•  Fixed a crash").assertExists()
        // Buttons ignore taps in their first 600 ms of uptime (double-submit guard); move the clock past it.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        compose.onNodeWithText("Update Now").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("Maybe Later").performClick()
        assertEquals("uptime=${android.os.SystemClock.uptimeMillis()}", 1, updates)
        assertEquals(1, later)
    }

    @Test
    fun releaseWithoutNotes_showsNeutralFallback_notInventedEntries() {
        val release = selectBetaRelease(feed(""), repo, installedCode = 3)!!
        show { UpdateOfferDialog(BetaUpdateState.Offer(release), "0.1.0-beta.3", {}, {}) }
        compose.onNodeWithText("This update doesn't include release notes.").assertExists()
    }
}
