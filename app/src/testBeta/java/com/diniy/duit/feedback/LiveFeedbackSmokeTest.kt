package com.diniy.duit.feedback

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sends real, clearly marked test feedback. Runs only when DUIT_FEEDBACK_LIVE=1 is set. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveFeedbackSmokeTest {
    @Test
    fun allThreeCategories_andOptionalEmail_reachTheSheet() = runBlocking {
        assumeTrue(System.getenv("DUIT_FEEDBACK_LIVE") == "1")
        val sender = AppsScriptFeedbackSender(ApplicationProvider.getApplicationContext())
        val stamp = System.currentTimeMillis()
        assertEquals(FeedbackResult.Sent, sender.send(FeedbackCategory.BUG, "[TEST $stamp] Bug report from the duit. app test. Please delete.", ""))
        assertEquals(FeedbackResult.Sent, sender.send(FeedbackCategory.FEATURE, "[TEST $stamp] Feature request from the duit. app test. Please delete.", ""))
        assertEquals(FeedbackResult.Sent, sender.send(FeedbackCategory.GENERAL, "[TEST $stamp] General feedback with email. Please delete.", "test@example.com"))
    }
}
