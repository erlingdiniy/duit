package com.diniy.duit.devcenter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterFeedbackClientTest {

    @Test
    fun feedbackClient_returnsHonestUnconfiguredWhenNoSourceOrCsv() = runTest {
        val client = DevCenterFeedbackClient(HttpsUrlConnectionTransport())
        val state = client.loadFeedback(
            sheetId = "",
            range = DEFAULT_FEEDBACK_RANGE,
            sheetsAccessToken = null,
            importedCsvRaw = null,
            importedCsvLabel = null
        )
        assertTrue(state is FeedbackSourceState.Unconfigured)
    }

    @Test
    fun feedbackClient_parsesCsvWithCodeGsFormulaEscapesCleanly() = runTest {
        val csv = """
            Timestamp,Category,Message,Email,App Version,Status
            2026-11-25T10:00:00Z,Bug Report,"'=SUM(1,2) Receipt preview clip, fixed?",user@example.com,1.2.0,New
            2026-11-24T09:00:00Z,Feature Request,"Export yearly summary", ,1.2.0-beta.2,Reviewed
        """.trimIndent()

        val client = DevCenterFeedbackClient(HttpsUrlConnectionTransport())
        val state = client.loadFeedback(
            sheetId = "",
            range = DEFAULT_FEEDBACK_RANGE,
            sheetsAccessToken = null,
            importedCsvRaw = csv,
            importedCsvLabel = "feedback.csv"
        )
        assertTrue(state is FeedbackSourceState.Ready)
        val ready = state as FeedbackSourceState.Ready
        assertEquals(2, ready.items.size)
        assertEquals("Bug Report", ready.items[0].category)
        assertEquals("=SUM(1,2) Receipt preview clip, fixed?", ready.items[0].message)
        assertEquals("Feature Request", ready.items[1].category)
        assertEquals("Reviewed", ready.items[1].status)
    }

    @Test
    fun feedbackClient_fetchesReadOnlyGoogleSheetsApi() = runTest {
        val transport = object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                assertTrue(url.startsWith("https://sheets.googleapis.com/v4/spreadsheets/sheet123/values/"))
                return DevHttpResponse(
                    200,
                    """
                    {
                      "values": [
                        ["Timestamp", "Category", "Message", "Email", "App Version", "Status"],
                        ["2026-11-26T01:00:00Z", "General Feedback", "Clean UI!", "", "1.2.0", "New"]
                      ]
                    }
                    """.trimIndent()
                )
            }
        }
        val client = DevCenterFeedbackClient(transport)
        val state = client.loadFeedback(
            sheetId = "sheet123",
            range = DEFAULT_FEEDBACK_RANGE,
            sheetsAccessToken = "ya29.readOnlyTokenCheck",
            importedCsvRaw = null,
            importedCsvLabel = null
        )
        assertTrue(state is FeedbackSourceState.Ready)
        val ready = state as FeedbackSourceState.Ready
        assertEquals(1, ready.items.size)
        assertEquals("Clean UI!", ready.items[0].message)
    }
}
