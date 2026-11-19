package com.diniy.duit.update

/**
 * Release notes come from GitHub and are untrusted. They are reduced to plain text (no HTML, no
 * links, no images) and shown with Text only, so nothing in them can run or open anything.
 */
internal data class NotesSection(val icon: String, val title: String, val items: List<NoteItem>)

internal data class NoteItem(val text: String, val bullet: Boolean)

internal fun parseReleaseNotes(raw: String): List<NotesSection> {
    val sections = mutableListOf<NotesSection>()
    var heading: String? = null
    var items = mutableListOf<NoteItem>()
    fun flush() {
        if (items.isNotEmpty()) sections += sectionFor(heading, items.toList())
        items = mutableListOf()
    }
    raw.take(12_000).replace("\r\n", "\n").lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        // Checksums belong to the release page and the updater's verification, not the welcome screen.
        if (CHECKSUM.containsMatchIn(trimmed)) return@forEach
        HEADING.matchEntire(trimmed)?.let {
            flush()
            heading = plainText(it.groupValues[1])
            return@forEach
        }
        val bullet = BULLET.matchEntire(trimmed)
        val text = plainText(bullet?.groupValues?.get(1) ?: trimmed)
        if (text.isNotBlank()) items += NoteItem(text, bullet != null)
    }
    flush()
    // An untitled intro before titled sections is just a lead-in: show it without a heading.
    if (sections.size > 1 && sections.first().title == "What's New" && sections.first().icon == "✨" && !startsWithHeading(raw)) {
        sections[0] = sections[0].copy(icon = "", title = "")
    }
    // Two headings that map to the same category (e.g. "New Features" and "What's New") become one section.
    val merged = mutableListOf<NotesSection>()
    for (section in sections) {
        val previous = merged.lastOrNull()
        if (previous != null && previous.title == section.title) {
            merged[merged.lastIndex] = previous.copy(items = previous.items + section.items)
        } else {
            merged += section
        }
    }
    sections.clear()
    sections += merged
    return sections
}

/** Only headings the author wrote are categorised; untitled notes stay together under "What's New". */
private fun sectionFor(heading: String?, items: List<NoteItem>): NotesSection {
    val h = heading?.lowercase().orEmpty()
    return when {
        heading == null -> NotesSection("✨", "What's New", items)
        listOf("fix", "bug", "pembetulan", "baiki").any { it in h } -> NotesSection("🛠", "Bug Fixes", items)
        listOf("improve", "enhance", "performance", "penambahbaikan", "prestasi").any { it in h } ->
            NotesSection("🚀", "Improvements", items)
        listOf("new", "feature", "baru", "ciri").any { it in h } -> NotesSection("✨", "What's New", items)
        else -> NotesSection("📝", heading, items)
    }
}

internal fun plainText(input: String): String = input
    .replace(IMAGE, "")
    .replace(LINK) { it.groupValues[1] }
    .replace(HTML_TAG, "")
    .replace(EMPHASIS, "")
    .filter { it == '\t' || !it.isISOControl() }
    .trim()
    .take(500)

private fun startsWithHeading(raw: String): Boolean =
    raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !CHECKSUM.containsMatchIn(it) }
        ?.let { HEADING.matches(it) } == true

private val CHECKSUM = Regex("sha-?256", RegexOption.IGNORE_CASE)
private val HEADING = Regex("""#{1,6}\s+(.+?)\s*#*""")
private val BULLET = Regex("""(?:[-*+•]|\d+[.)])\s+(.+)""")
private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val HTML_TAG = Regex("""<[^>]*>""")
private val EMPHASIS = Regex("""\*\*|__|~~|`""")
