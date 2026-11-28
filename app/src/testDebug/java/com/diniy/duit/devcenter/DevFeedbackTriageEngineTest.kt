package com.diniy.duit.devcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevFeedbackTriageEngineTest {

    @Test
    fun detectsDuplicatesFiltersSortsAndStripsEmailForGitHubIssue() {
        val entries = listOf(
            FeedbackEntry("fb-1", "2026-11-27T09:00:00Z", "Bug Report", "Receipt photo crop clips bottom margin when exporting backup receipt@private.test", "user1@example.com", "1.3.0", "New", 2),
            FeedbackEntry("fb-2", "2026-11-26T09:00:00Z", "Bug Report", "Receipt photo crop clips bottom margin during export", "", "1.3.0", "Reviewed", 3),
            FeedbackEntry("fb-3", "2026-11-27T10:00:00Z", "Feature Request", "Add dark OLED mode toggle", "", "1.2.0", "New", 4)
        )
        val triageMap = mapOf(
            "fb-2" to FeedbackTriageRecord("fb-2", isRead = true, priority = FeedbackPriority.CRITICAL, status = FeedbackTriageStatus.IN_PROGRESS)
        )

        val clustered = DevFeedbackTriageEngine.mergeAndCluster(entries, triageMap)
        val first = clustered.first { it.entry.id == "fb-1" }
        val second = clustered.first { it.entry.id == "fb-2" }
        assertNotNull(first.duplicateGroupId)
        assertEquals(first.duplicateGroupId, second.duplicateGroupId)
        assertTrue("fb-2" in first.duplicateOfIds)

        val byPriority = DevFeedbackTriageEngine.filterAndSort(
            items = clustered,
            searchQuery = "",
            categoryFilter = "All",
            statusFilter = null,
            priorityFilter = null,
            unreadOnly = false,
            sortOrder = FeedbackSortOrder.PRIORITY
        )
        assertEquals("fb-2", byPriority.first().entry.id)

        val (issueTitle, issueBody, labels) = DevFeedbackTriageEngine.buildSanitizedGitHubIssue(first)
        assertTrue(issueTitle.startsWith("[Bug]"))
        assertFalse(issueBody.contains("user1@example.com"))
        assertTrue("bug" in labels)
    }
}
