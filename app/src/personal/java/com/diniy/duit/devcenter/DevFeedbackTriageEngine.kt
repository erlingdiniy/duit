package com.diniy.duit.devcenter

import kotlin.math.max
import kotlin.math.min

/**
 * Developer Feedback 2.0 triage, filtering, duplicate clustering, and safe issue-export helper.
 * User email addresses are kept private inside Developer Center and stripped from public issue bodies.
 */
object DevFeedbackTriageEngine {

    private val NON_ALNUM = Regex("""[^a-z0-9\s]+""")
    private val MULTI_SPACE = Regex("""\s+""")

    fun mergeAndCluster(
        entries: List<FeedbackEntry>,
        triageMap: Map<String, FeedbackTriageRecord>
    ): List<TriagedFeedbackItem> {
        val tokensById = entries.associate { it.id to tokenize(it.message) }
        val normalizedById = entries.associate { it.id to normalize(it.message) }
        val groupOf = mutableMapOf<String, String>()
        val relatedOf = mutableMapOf<String, MutableList<String>>()
        var groupSeq = 1

        for (i in entries.indices) {
            val a = entries[i]
            val normA = normalizedById[a.id].orEmpty()
            val tokA = tokensById[a.id].orEmpty()
            for (j in i + 1 until entries.size) {
                val b = entries[j]
                val normB = normalizedById[b.id].orEmpty()
                val tokB = tokensById[b.id].orEmpty()
                val isExact = normA.isNotEmpty() && normA == normB
                val isSimilar = tokA.size >= 4 && tokB.size >= 4 &&
                    (jaccard(tokA, tokB) >= 0.55 || overlapCoefficient(tokA, tokB) >= 0.70)
                if (isExact || isSimilar) {
                    val gid = groupOf[a.id] ?: groupOf[b.id] ?: "dup-${groupSeq++}"
                    groupOf[a.id] = gid
                    groupOf[b.id] = gid
                    relatedOf.getOrPut(a.id) { mutableListOf() }.add(b.id)
                    relatedOf.getOrPut(b.id) { mutableListOf() }.add(a.id)
                }
            }
        }

        return entries.map { entry ->
            val rec = triageMap[entry.id] ?: FeedbackTriageRecord(
                feedbackId = entry.id,
                isRead = !entry.status.equals("New", ignoreCase = true),
                priority = if (entry.category == "Bug Report") FeedbackPriority.HIGH else FeedbackPriority.NORMAL,
                status = FeedbackTriageStatus.fromStored(null, fallbackSheetStatus = entry.status)
            )
            TriagedFeedbackItem(
                entry = entry,
                triage = rec,
                duplicateGroupId = groupOf[entry.id],
                duplicateOfIds = relatedOf[entry.id]?.distinct().orEmpty()
            )
        }
    }

    fun filterAndSort(
        items: List<TriagedFeedbackItem>,
        searchQuery: String,
        categoryFilter: String,
        statusFilter: FeedbackTriageStatus?,
        priorityFilter: FeedbackPriority?,
        unreadOnly: Boolean,
        sortOrder: FeedbackSortOrder
    ): List<TriagedFeedbackItem> {
        val cleanQuery = searchQuery.trim().lowercase()
        val filtered = items.filter { item ->
            val catOk = categoryFilter == "All" || item.entry.category.equals(categoryFilter, ignoreCase = true)
            val statusOk = statusFilter == null || item.triage.status == statusFilter
            val prioOk = priorityFilter == null || item.triage.priority == priorityFilter
            val unreadOk = !unreadOnly || item.isUnread
            val queryOk = cleanQuery.isEmpty() ||
                item.entry.message.lowercase().contains(cleanQuery) ||
                item.entry.appVersion.lowercase().contains(cleanQuery) ||
                item.triage.notes.lowercase().contains(cleanQuery) ||
                item.entry.category.lowercase().contains(cleanQuery)
            catOk && statusOk && prioOk && unreadOk && queryOk
        }

        return when (sortOrder) {
            FeedbackSortOrder.NEWEST -> filtered.sortedByDescending { it.entry.timestampIso }
            FeedbackSortOrder.OLDEST -> filtered.sortedBy { it.entry.timestampIso }
            FeedbackSortOrder.PRIORITY -> filtered.sortedWith(
                compareByDescending<TriagedFeedbackItem> { it.triage.priority.rank }
                    .thenByDescending { it.entry.timestampIso }
            )
            FeedbackSortOrder.UNREAD_FIRST -> filtered.sortedWith(
                compareByDescending<TriagedFeedbackItem> { it.isUnread }
                    .thenByDescending { it.entry.timestampIso }
            )
        }
    }

    /**
     * Generates a sanitized GitHub issue payload without any private contact email address.
     */
    fun buildSanitizedGitHubIssue(item: TriagedFeedbackItem): Triple<String, String, List<String>> {
        val prefix = when (item.entry.category) {
            "Bug Report" -> "[Bug]"
            "Feature Request" -> "[Feature]"
            else -> "[Feedback]"
        }
        val firstLine = item.entry.message.lines().firstOrNull()?.trim().orEmpty().take(72)
        val title = "$prefix $firstLine".trim()
        val scrubbedMsg = item.entry.email.trim().takeIf { it.isNotEmpty() }?.let { email ->
            item.entry.message.replace(email, "[redacted-contact]")
        } ?: item.entry.message
        val body = buildString {
            appendLine("## Summary")
            appendLine(scrubbedMsg)
            appendLine()
            appendLine("## Context")
            appendLine("- Category: ${item.entry.category}")
            appendLine("- App version: ${item.entry.appVersion}")
            appendLine("- Submitted: ${item.entry.timestampIso.ifBlank { "unknown" }}")
            appendLine("- Triage priority: ${item.triage.priority.label}")
            if (item.duplicateOfIds.isNotEmpty()) {
                appendLine("- Duplicate cluster count: ${item.duplicateOfIds.size + 1}")
            }
            appendLine()
            appendLine("_Created from Developer Center 2.0 Feedback Inbox (contact details omitted for privacy)._")
        }
        val labels = when (item.entry.category) {
            "Bug Report" -> listOf("bug", "triage")
            "Feature Request" -> listOf("enhancement", "triage")
            else -> listOf("feedback", "triage")
        }
        return Triple(title, body, labels)
    }

    private fun normalize(raw: String): String = raw
        .lowercase()
        .replace(NON_ALNUM, " ")
        .replace(MULTI_SPACE, " ")
        .trim()

    private fun tokenize(raw: String): Set<String> = normalize(raw)
        .split(' ')
        .filter { it.length >= 2 }
        .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val union = (a + b).size
        val intersect = a.intersect(b).size
        return intersect.toDouble() / max(1, union).toDouble()
    }

    private fun overlapCoefficient(a: Set<String>, b: Set<String>): Double {
        val smaller = min(a.size, b.size)
        if (smaller < 5) return 0.0
        return a.intersect(b).size.toDouble() / smaller.toDouble()
    }
}
