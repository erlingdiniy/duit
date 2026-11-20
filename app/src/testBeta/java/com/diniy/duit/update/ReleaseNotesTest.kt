package com.diniy.duit.update

import java.io.IOException
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ReleaseNotesTest {

    @Test
    fun untitledNotes_stayTogetherUnderWhatsNew_withoutInventedCategories() {
        val sections = parseReleaseNotes("Second beta.\n\nIn this version:\n- Faster widgets\n- Safer backups")
        assertEquals(1, sections.size)
        assertEquals("What's New", sections[0].title)
        assertEquals(listOf("Second beta.", "In this version:", "Faster widgets", "Safer backups"), sections[0].items.map { it.text })
        assertEquals(listOf(false, false, true, true), sections[0].items.map { it.bullet })
    }

    @Test
    fun authorHeadings_mapToFriendlyCategories_orKeepTheirOwnTitle() {
        val notes = "## New features\n- Widgets\n## Improvements\n* Speed\n### Bug fixes\n1. Crash on launch\n## Thanks\n- Testers"
        val sections = parseReleaseNotes(notes)
        assertEquals(listOf("What's New", "Improvements", "Bug Fixes", "Thanks"), sections.map { it.title })
        assertEquals("Crash on launch", sections[2].items.single().text)
    }

    @Test
    fun introBeforeTitledSections_hasNoDuplicateHeading() {
        val sections = parseReleaseNotes("Third beta.\n\n## New Features\n- Popup\n## Improvements\n- Progress")
        assertEquals(listOf("", "What's New", "Improvements"), sections.map { it.title })
        assertEquals("Third beta.", sections[0].items.single().text)
    }

    @Test
    fun checksumsAreHidden_andSameCategoryHeadingsMergeIntoOne() {
        val sections = parseReleaseNotes(
            "## What's New\n- Popup\n## New Features\n- Welcome screen\n## Bug Fixes\n- Crash\n\nSHA-256 APK: `abc123`"
        )
        assertEquals(listOf("What's New", "Bug Fixes"), sections.map { it.title })
        assertEquals(listOf("Popup", "Welcome screen"), sections[0].items.map { it.text })
        assertTrue(sections.flatMap { it.items }.toString(), sections.flatMap { it.items }.none { "abc123" in it.text || "SHA" in it.text })
    }

    @Test
    fun untrustedMarkup_isReducedToPlainText() {
        val text = parseReleaseNotes("- **Bold** [link](https://evil.example) <script>alert(1)</script> ![x](http://img) `code`")
            .single().items.single().text
        assertEquals("Bold link alert(1)  code", text)
        assertFalse(text.contains("http"))
        assertFalse(text.contains("<"))
    }

    @Test
    fun missingAndHugeNotes_areHandledSafely() {
        assertTrue(parseReleaseNotes("").isEmpty())
        assertTrue(parseReleaseNotes("   \n\n").isEmpty())
        val long = parseReleaseNotes("- " + "a".repeat(5_000)).single().items.single().text
        assertEquals(500, long.length)
    }

    @Test
    fun releaseMetadata_carriesDisplayFields_fromTheValidatedRelease() {
        val repo = "erlingdiniy/duit-beta"
        val release = JSONObject()
            .put("tag_name", "beta-2").put("draft", false).put("prerelease", true)
            .put("name", "duit. Beta 0.1.0-beta.2").put("body", "Notes here")
            .put("published_at", "2026-11-19T08:09:00Z")
            .put("assets", JSONArray().put(JSONObject()
                .put("name", "duit-beta-2.apk").put("state", "uploaded").put("size", 7_348_950)
                .put("browser_download_url", "https://github.com/$repo/releases/download/beta-2/duit-beta-2.apk")
                .put("digest", "sha256:" + "a".repeat(64))))
        val selected = selectBetaRelease(JSONArray().put(release), repo, 1)!!
        assertEquals("0.1.0-beta.2", selected.versionName)
        assertEquals("Notes here", selected.notes)
        assertEquals("2026-11-19", selected.publishedDate)
    }

    @Test
    fun failures_areClassified_andSignatureNeverOffersRetry() {
        assertEquals(UpdateProblem.NETWORK, classifyUpdateFailure(UnknownHostException("api.github.com")))
        assertEquals(UpdateProblem.TIMEOUT, classifyUpdateFailure(java.net.SocketTimeoutException("Read timed out")))
        assertTrue(UpdateProblem.TIMEOUT.canRetry)
        assertEquals(UpdateProblem.SIGNATURE, classifyUpdateFailure(IOException("APK package, version or signer does not match this beta update")))
        assertEquals(UpdateProblem.INVALID_APK, classifyUpdateFailure(IOException("APK integrity check failed")))
        assertEquals(UpdateProblem.SERVER, classifyUpdateFailure(IOException("GitHub update request failed (HTTP 503)")))
        assertEquals(UpdateProblem.STORAGE, classifyUpdateFailure(IOException("write failed: ENOSPC (No space left on device)")))
        assertFalse(UpdateProblem.SIGNATURE.canRetry)
        assertTrue(UpdateProblem.NETWORK.canRetry)
    }
}
