package com.diniy.duit.feature.activity

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityEmptyStateTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: ActivityUiState) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            DuitTheme {
                ActivityScreen(
                    uiState = state,
                    contentPadding = PaddingValues(),
                    onSelectFilter = { events += "type:$it" },
                    onSearchQueryChange = { events += "query:$it" },
                    onExtraFiltersChange = { events += "extra:${it.isActive}" },
                    onOpenNewTransaction = { events += "new:$it" },
                    onSelectTransaction = {},
                    onOpenCategories = {},
                    onEnablePreview = {},
                    onExitPreview = {},
                    onDismissError = {}
                )
            }
        }
    }

    @Test
    fun loading_neverShowsAnEmptyState() {
        show(ActivityUiState(isLoading = true))
        compose.onNodeWithText("No activity yet").assertDoesNotExist()
        compose.onNodeWithText("No matching transactions").assertDoesNotExist()
    }

    @Test
    fun trulyEmpty_offersAddTransaction() {
        show(ActivityUiState(isLoading = false))
        compose.onNodeWithText("No activity yet").assertExists()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        compose.onNodeWithText("Add Transaction").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("new:${TransactionType.EXPENSE}"), events)
    }

    @Test
    fun noMatches_offersOneTapReset() {
        show(ActivityUiState(isLoading = false, searchQuery = "zzz", extraFilters = ActivityExtraFilters(dateRange = ActivityDateRange.LAST_MONTH)))
        compose.onNodeWithText("No matching transactions").assertExists()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        compose.onNodeWithText("Clear search & filters").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("query:", "type:ALL", "extra:false"), events)
    }
}
