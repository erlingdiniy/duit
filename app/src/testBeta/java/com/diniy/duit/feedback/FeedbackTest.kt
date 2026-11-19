package com.diniy.duit.feedback

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test
    fun viewModelFactory_canCreateFeedbackModel_likeTheRealScreen() {
        // Regression: a default-argument constructor made viewModel() fail at runtime in release builds.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            .create(FeedbackModel::class.java)
        assertEquals(FeedbackUiState.Editing, model.state.value)
    }

    @Test
    fun payload_hasOnlyTheDocumentedFields_withBackendCategoryValues() {
        val json = JSONObject(buildFeedbackPayload(FeedbackCategory.FEATURE, "  Dark widgets please!  ", " me@example.com ", "1.1.0", "id-123"))
        assertEquals(setOf("category", "message", "email", "appVersion", "installId", "website"), json.keys().asSequence().toSet())
        assertEquals("Feature Request", json.getString("category"))
        assertEquals("Dark widgets please!", json.getString("message"))
        assertEquals("me@example.com", json.getString("email"))
        assertEquals("", json.getString("website"))
        assertEquals(listOf("Bug Report", "Feature Request", "General Feedback"), FeedbackCategory.entries.map { it.apiValue })
    }

    @Test
    fun onlyOkTrueCountsAsSent() {
        assertEquals(FeedbackResult.Sent, parseFeedbackResponse("""{"ok":true}"""))
        assertEquals(FeedbackResult.Rejected("DUPLICATE"), parseFeedbackResponse("""{"ok":false,"error":"DUPLICATE"}"""))
        assertEquals(FeedbackResult.Failed(offline = false), parseFeedbackResponse("<html>Sign in</html>"))
        assertEquals(FeedbackResult.Rejected("UNKNOWN"), parseFeedbackResponse("""{"status":"fine"}"""))
    }

    @Test
    fun redirectsOnlyToGoogleScriptHosts() {
        assertTrue(isAllowedFeedbackHost(URI("https://script.google.com/macros/s/abc/exec"), first = true))
        assertTrue(isAllowedFeedbackHost(URI("https://script.googleusercontent.com/macros/echo?x=1"), first = false))
        assertFalse(isAllowedFeedbackHost(URI("http://script.google.com/macros/s/abc/exec"), first = true))
        assertFalse(isAllowedFeedbackHost(URI("https://evil.example/exec"), first = false))
        assertFalse(isAllowedFeedbackHost(URI("https://script.googleusercontent.com/x"), first = true))
    }

    @Test
    fun clientValidation_matchesBackendRules() {
        assertNotNull(FeedbackRules.messageError(""))
        assertNotNull(FeedbackRules.messageError("   short   "))
        assertNull(FeedbackRules.messageError("Ten chars!"))
        assertNotNull(FeedbackRules.messageError("x".repeat(2001)))
        assertNull(FeedbackRules.emailError(""))
        assertNull(FeedbackRules.emailError("me@example.com"))
        assertNotNull(FeedbackRules.emailError("me@x"))
    }

    @Test
    fun installId_isRandomAndPersistent() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = installId(context)
        assertEquals(first, installId(context))
        assertTrue(Regex("[0-9a-f-]{36}").matches(first))
    }

    @Test
    fun repeatedTaps_sendOnce_andInvalidInputSendsNothing() = runTest {
        val gate = CompletableDeferred<FeedbackResult>()
        var calls = 0
        val sender = object : FeedbackSender {
            override suspend fun send(category: FeedbackCategory, message: String, email: String): FeedbackResult {
                calls++
                return gate.await()
            }
        }
        val model = FeedbackModel(ApplicationProvider.getApplicationContext(), sender)
        model.submit(FeedbackCategory.BUG, "short", "")
        assertEquals(0, calls)
        repeat(5) { model.submit(FeedbackCategory.BUG, "[TEST] widget does not refresh", "") }
        assertEquals(1, calls)
        assertEquals(FeedbackUiState.Sending, model.state.value)
        gate.complete(FeedbackResult.Sent)
        assertEquals(FeedbackUiState.Sent, model.state.value)
    }

    @Test
    fun offlineAndRejections_mapToFriendlyRetryableStates() = runTest {
        var next: FeedbackResult = FeedbackResult.Failed(offline = true)
        val sender = object : FeedbackSender {
            override suspend fun send(category: FeedbackCategory, message: String, email: String) = next
        }
        val model = FeedbackModel(ApplicationProvider.getApplicationContext(), sender)
        model.submit(FeedbackCategory.GENERAL, "[TEST] offline check", "")
        val offline = model.state.value as FeedbackUiState.Error
        assertEquals("You're offline", offline.title)
        assertTrue(offline.canRetry)
        model.backToForm()
        next = FeedbackResult.Rejected("DUPLICATE")
        model.submit(FeedbackCategory.GENERAL, "[TEST] offline check", "")
        assertFalse((model.state.value as FeedbackUiState.Error).canRetry)
    }
}
