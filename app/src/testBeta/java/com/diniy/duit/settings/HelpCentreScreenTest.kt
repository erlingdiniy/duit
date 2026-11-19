package com.diniy.duit.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.feature.settings.HelpCentreScreen
import com.diniy.duit.feature.settings.HelpTopics
import com.diniy.duit.feature.settings.SettingsHubRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpCentreScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun tenGuides_render_expand_andLinkToSettings_inLightAndDark() {
        assertEquals(10, HelpTopics.size)
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            // Fresh activity per theme so saved expand/scroll state from the previous pass can't leak in.
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = controller.get()
            val routes = mutableListOf<SettingsHubRoute>()
            activity.setContent { DuitTheme(themeMode = mode) { HelpCentreScreen(onBack = {}, onOpenRoute = { routes += it }) } }
            HelpTopics.forEach { topic ->
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(topic.title))
                compose.onNodeWithText(topic.title).assertExists()
            }
            // Expand "Backup & Restore" and follow its link.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Backup & Restore"))
            compose.onNodeWithText("Backup & Restore").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open Backup & Restore"))
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            compose.onNodeWithText("Open Backup & Restore").performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(listOf(SettingsHubRoute.BACKUP_RESTORE), routes)
            controller.pause().stop().destroy()
        }
    }
}
